const crypto=require('crypto');
const QRCode=require('qrcode');
const PDFDocument=require('pdfkit');
const {z,id,int,parse}=require('../../utils/validation');
const {fail}=require('../../utils/errors');
const {canonical}=require('../../utils/canonical');
const {signDay2}=require('../../utils/day2-sign');
const {tx,players}=require('../../services/day2-db');
const {getState}=require('../../services/state');
const pool=require('../../database/pool');
const origin='http://localhost:8080';
async function entity(c,s,entity_id){
  if(s.matches[entity_id]){const status=require('../scoring').project(s,entity_id).status;return {type:'match',status,route:`/web/matches/${entity_id}/`+({upcoming:'preview',in_progress:'live',completed:'vod'}[status]??'notice?state='+status)};}
  if((s.clubs??[]).some(p=>p.club_id===entity_id))return {type:'club',status:'active',route:'/web/clubs/'+entity_id};
  if(players(s).some(p=>p.player_id===entity_id))return {type:'player',status:'active',route:'/web/players/'+entity_id};
  const session=(await c.query('SELECT status FROM day2_costream_sessions WHERE session_id=$1',[entity_id])).rows[0];
  if(session)return {type:'costream',status:session.status,route:'/web/costream/'+entity_id};
  fail(404,'QR_NOT_FOUND','Entity has no QR mapping.');
}
async function resolve(entity_id,inspect=false,userAgent=''){
  parse(id,entity_id);return tx(null,async c=>{
    const e=await entity(c,await getState(c),entity_id);
    const short_code=e.type.slice(0,1)+'-'+crypto.createHash('sha256').update(entity_id).digest('hex').slice(0,20);
    await c.query('INSERT INTO day2_qr_links(entity_id,entity_type,short_code) VALUES($1,$2,$3) ON CONFLICT(entity_id) DO NOTHING',[entity_id,e.type,short_code]);
    let row;
    if(inspect)row=(await c.query('SELECT * FROM day2_qr_links WHERE entity_id=$1',[entity_id])).rows[0];
    else{
      row=(await c.query('UPDATE day2_qr_links SET scan_count=scan_count+1 WHERE entity_id=$1 RETURNING *',[entity_id])).rows[0];
      await c.query("INSERT INTO day2_qr_scan_buckets(entity_id,utc_bucket,device_family,scans) VALUES($1,date_trunc('hour',now()),$2,1) ON CONFLICT(entity_id,utc_bucket,device_family) DO UPDATE SET scans=day2_qr_scan_buckets.scans+1",[entity_id,/mobile|android|iphone|ipad/i.test(userAgent)?'mobile':'desktop']);
    }
    const short_url=origin+'/q/'+row.short_code,target_route=e.route;
    return {entity_id,entity_type:e.type,entity_status:e.status,short_url,target_route,deep_link_uri:'nascon://entity/'+entity_id,scan_count:Number(row.scan_count),export_formats:Object.fromEntries(['svg','png','pdf'].map(fmt=>[fmt,`${origin}/audit/v1/qr/${entity_id}/export.${fmt}`])),signature:signDay2(entity_id,{entity_id,short_url,target_route})};
  });
}
async function redirect(code,ua){const row=(await pool.query('SELECT entity_id FROM day2_qr_links WHERE short_code=$1',[code])).rows[0];if(!row)fail(404,'QR_NOT_FOUND','Short code not found.');return resolve(row.entity_id,false,ua);}
async function exportQR(entity_id,format){if(!['svg','png','pdf'].includes(format))fail(400,'VALIDATION_ERROR','Unsupported export.',{fields:['format']});const meta=await resolve(entity_id,true);
  if(format==='svg')return {type:'image/svg+xml',data:await QRCode.toString(meta.short_url,{type:'svg',errorCorrectionLevel:'M'})};
  const png=await QRCode.toBuffer(meta.short_url,{type:'png',width:256,margin:4});if(format==='png')return {type:'image/png',data:png};
  const data=await new Promise((yes,no)=>{const doc=new PDFDocument(),chunks=[];doc.on('data',b=>chunks.push(b));doc.on('end',()=>yes(Buffer.concat(chunks)));doc.on('error',no);doc.fontSize(20).text(entity_id).image(png,60,100,{width:300}).fontSize(11).text(meta.short_url,60,430);doc.end();});return {type:'application/pdf',data};
}
const mergeSchema=z.object({guest_token:z.string().min(1).max(256).startsWith('guest_'),registered_user_id:id,guest_cart:z.array(z.object({variant_id:id,quantity:int.min(1)})).min(1).max(10),test_write_block:z.boolean().default(true)});
async function merge(input,actor){const b=parse(mergeSchema,input);return tx('guest:'+b.guest_token,async c=>{
  const s=await getState(c);if(!players(s).some(p=>p.player_id===b.registered_user_id)&&!s.users.some(u=>u.user_id===b.registered_user_id&&!u.anonymized))fail(404,'PLAYER_NOT_FOUND','Registered user not found.');
  const ids=[...new Set(b.guest_cart.map(i=>i.variant_id))].sort();const variants=new Map((s.products??[]).flatMap(p=>p.variants??[]).map(v=>[v.variant_id,v]));
  const stock=(await c.query('SELECT variant_id,stock FROM inventory WHERE variant_id=ANY($1) ORDER BY variant_id FOR UPDATE',[ids])).rows;
  for(const variant_id of ids)if(!variants.has(variant_id)||!stock.some(r=>r.variant_id===variant_id))fail(404,'VARIANT_NOT_FOUND','Variant not found.',{variant_id});
  if(actor){const user=s.users.find(u=>u.user_id===actor.sub&&!u.anonymized&&u.role===actor.role&&u.tenant_id===actor.tenant_id);if(!user)fail(401,'UNAUTHENTICATED','Account session is invalid.');if(![user.user_id,user.player_id].includes(b.registered_user_id))fail(403,'FORBIDDEN','Cart must belong to the authenticated account.');}
  const previous=(await c.query('SELECT request,response FROM day2_cart_merges WHERE guest_token=$1',[b.guest_token])).rows[0];
  const identity={guest_token:b.guest_token,registered_user_id:b.registered_user_id,guest_cart:b.guest_cart};
  if(previous){if(canonical(previous.request)!==canonical(identity))fail(409,'GUEST_TOKEN_ALREADY_MERGED','Guest token was already merged.');return previous.response;}
  const token=(await c.query('SELECT * FROM day2_guest_tokens WHERE token=$1 FOR UPDATE',[b.guest_token])).rows[0];
  if(!token||token.invalidated_at||new Date(token.expires_at)<=new Date())fail(403,'GUEST_WRITE_FORBIDDEN','Guest token is invalid.');
  await c.query('INSERT INTO day2_carts(user_id) VALUES($1) ON CONFLICT DO NOTHING',[b.registered_user_id]);
  await c.query('SELECT user_id FROM day2_carts WHERE user_id=$1 FOR UPDATE',[b.registered_user_id]);
  const current=(await c.query('SELECT variant_id,quantity FROM day2_cart_items WHERE user_id=$1',[b.registered_user_id])).rows;
  const totals=new Map(current.map(i=>[i.variant_id,i.quantity]));for(const i of b.guest_cart)totals.set(i.variant_id,(totals.get(i.variant_id)??0)+i.quantity);
  for(const variant_id of ids)if(totals.get(variant_id)>stock.find(r=>r.variant_id===variant_id).stock)fail(409,'OUT_OF_STOCK','Merged quantity exceeds stock.',{variant_id});
  let write_block_verified=false,write_rejection_code=0;
  if(b.test_write_block){require('../../middleware/guest').guestWriteGuard({method:'POST',path:'/api/v1/chat',headers:{authorization:'Bearer '+b.guest_token}},null,e=>{write_block_verified=e?.code==='GUEST_WRITE_FORBIDDEN';write_rejection_code=e?.status??0;});if(!write_block_verified)throw new Error('Guest write guard failed.');}
  for(const variant_id of ids)await c.query('INSERT INTO day2_cart_items(user_id,variant_id,quantity) VALUES($1,$2,$3) ON CONFLICT(user_id,variant_id) DO UPDATE SET quantity=EXCLUDED.quantity',[b.registered_user_id,variant_id,totals.get(variant_id)]);
  const items=[...totals].sort(([a],[b])=>a.localeCompare(b)).map(([variant_id,quantity])=>({variant_id,quantity,unit_price_minor:variants.get(variant_id)?.unit_price_minor??variants.get(variant_id)?.price_minor??0}));
  const total_items=items.reduce((n,i)=>n+i.quantity,0),body={guest_token:b.guest_token,write_block_verified,write_rejection_code,cart_merged:true,active_cart:{user_id:b.registered_user_id,currency:'PKR',total_items,subtotal_minor:items.reduce((n,i)=>n+i.quantity*i.unit_price_minor,0),items}};
  body.signature=signDay2(b.guest_token,{cart_merged:true,guest_token:b.guest_token,registered_user_id:b.registered_user_id,total_items,write_rejection_code});
  await c.query('INSERT INTO day2_cart_merges(guest_token,user_id,request,response) VALUES($1,$2,$3,$4)',[b.guest_token,b.registered_user_id,identity,body]);
  await c.query('UPDATE day2_guest_tokens SET registered_user_id=$2,invalidated_at=now() WHERE token=$1',[b.guest_token,b.registered_user_id]);return body;
});}
module.exports={resolve,redirect,exportQR,merge};
