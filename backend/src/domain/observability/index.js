const pool=require('../../database/pool');
const {tx}=require('../../services/day2-db');
const {getState,putState}=require('../../services/state');
const {fail}=require('../../utils/errors');
const {id,parse}=require('../../utils/validation');
let previousSample;
async function metrics(){
  const row=(await pool.query('SELECT (SELECT count(*) FROM day2_notification_dlq)::int AS dlq, (SELECT COALESCE(sum(scan_count),0) FROM day2_qr_links)::bigint AS scans')).rows[0];
  let streams=0,bitrate=0;
  if(process.env.MEDIA_METRICS_URL){try{const r=await fetch(process.env.MEDIA_METRICS_URL,{signal:AbortSignal.timeout(400)});if(r.ok){const t=await r.text(),lines=t.split('\n');streams=lines.filter(l=>/^paths\{.*state="ready"/.test(l)).reduce((n,l)=>n+Number(l.split(/\s+/).at(-1)),0);const bytes=lines.filter(l=>/^(rtmp_conns|webrtc_sessions)_bytes_received(?:\{|\s)/.test(l)).reduce((n,l)=>n+Number(l.split(/\s+/).at(-1)),0),now=Date.now();if(previousSample&&now>previousSample.at)bitrate=Math.max(0,(bytes-previousSample.bytes)*8/(now-previousSample.at));previousSample={bytes,at:now};}}catch{}}
  const values={nascon_active_ws_connections:require('../../services/realtime').connectionCount(),nascon_active_streams_total:streams,nascon_stream_bitrate_kbps:bitrate,nascon_dlq_depth:row.dlq,nascon_qr_scans_total:Number(row.scans)};
  return Object.entries(values).map(([name,value])=>`# TYPE ${name} ${name==='nascon_qr_scans_total'?'counter':'gauge'}\n${name} ${value}`).join('\n')+'\n';
}
async function erase(user_id){parse(id,user_id);return tx('privacy:'+user_id,async c=>{
  // Same lock ordering as Phase I transactions: reset guard, then app_state row.
  await c.query("SELECT key FROM app_state WHERE key='state' FOR UPDATE");const s=await getState(c);
  const player=s.players?.find(p=>p.player_id===user_id),user=s.users?.find(u=>u.user_id===user_id||u.player_id===user_id);
  if(!player&&!user)fail(404,'PLAYER_NOT_FOUND','User not found.');
  const linked=[...new Set([user_id,user?.user_id,user?.player_id].filter(Boolean))];
  const userFields=new Set(['user_id','role','tenant_id','league_id','league_ids','club_id','player_id','anonymized']);
  for(const u of s.users??[])if(linked.includes(u.user_id)||linked.includes(u.player_id)){for(const field of Object.keys(u))if(!userFields.has(field))delete u[field];u.anonymized=true;}
  const playerFields=new Set(['player_id','club_id','tenant_id','position','squad_id','initial_squad_id','role','registration','deferred','anonymized']);
  const anonymize=p=>{if(linked.includes(p.player_id)){for(const field of Object.keys(p))if(!playerFields.has(field))delete p[field];p.anonymized=true;}};
  for(const p of s.players??[])anonymize(p);for(const r of Object.values(s.rosters??{})){for(const p of r.players??[])anonymize(p);for(const h of r.history??[]){if(linked.includes(h.who))h.who='anonymized';if(linked.includes(h.player_id))h.why='Erased';}for(const [key,entry] of Object.entries(r.requests??{}))if(linked.includes(entry.request?.player_id)||linked.includes(entry.request?.requested_by))delete r.requests[key];}
  await putState(s,c);
  const tokens=(await c.query('SELECT token FROM day2_guest_tokens WHERE registered_user_id=ANY($1)',[linked])).rows.map(r=>r.token);
  const cacheKeys=(await c.query('SELECT cache_key FROM day2_private_cache_refs WHERE user_id=ANY($1)',[linked])).rows.map(r=>r.cache_key);
  await require('../../services/privacy-cache').eraseKeys(cacheKeys);
  await c.query('DELETE FROM day2_cart_merges WHERE user_id=ANY($1) OR guest_token=ANY($2)',[linked,tokens]);
  await c.query('DELETE FROM day2_guest_tokens WHERE registered_user_id=ANY($1)',[linked]);
  await c.query('DELETE FROM day2_carts WHERE user_id=ANY($1)',[linked]);
  await c.query('DELETE FROM day2_cart_checkouts WHERE user_id=ANY($1)',[linked]);
  await c.query('DELETE FROM day2_stream_keys WHERE player_id=ANY($1)',[linked]);
  await c.query("DELETE FROM day2_notifications WHERE recipient_id=ANY($1) OR payload->>'user_id'=ANY($1) OR payload->>'recipient_id'=ANY($1)",[linked]);
  await c.query('DELETE FROM day2_private_cache_refs WHERE user_id=ANY($1)',[linked]);
  await c.query('DELETE FROM day2_follows WHERE follower_id=ANY($1)',[linked]);
  await c.query("UPDATE day2_chat_messages SET sender='anonymized',payload=jsonb_build_object('anonymized',true) WHERE sender=ANY($1)",[linked]);
  await c.query('DELETE FROM day2_moderator_actions WHERE sender=ANY($1)',[linked]);
  const orders=(await c.query('SELECT order_id,request,response FROM orders WHERE request->>\'customer_id\'=ANY($1) FOR UPDATE',[linked])).rows;
  for(const order of orders){order.request.customer_id='anonymized';order.response.receipt.payload.customer_id='anonymized';order.response.receipt.signature=require('../../utils/canonical').sign(order.order_id,order.response.receipt.payload);await c.query('UPDATE orders SET request=$2,response=$3 WHERE order_id=$1',[order.order_id,order.request,order.response]);}
  return {user_id,status:'anonymized',pii_retained:false,tokens_wiped:true};
});}
module.exports={metrics,erase};
