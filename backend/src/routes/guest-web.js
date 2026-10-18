const express=require('express'),crypto=require('crypto'),path=require('path');
const {getState,transaction}=require('../services/state'),pool=require('../database/pool');
const {z,id,parse}=require('../utils/validation'),{fail}=require('../utils/errors');
const router=express.Router(),wrap=fn=>(req,res,next)=>Promise.resolve().then(()=>fn(req,res)).catch(next);
function member(req){if(!req.headers.authorization||/^Bearer guest_/i.test(req.headers.authorization))fail(403,'GUEST_WRITE_FORBIDDEN','Sign in to perform this action.');return require('../domain/rbac').authenticateSession(req.headers.authorization);}
async function actor(req){const claims=member(req),s=await getState(),u=s.users.find(u=>u.user_id===claims.sub&&!u.anonymized&&u.role===claims.role&&u.tenant_id===claims.tenant_id);if(!u)fail(401,'UNAUTHENTICATED','Account session is no longer valid.');return u;}
router.use('/web/assets',express.static(path.join(__dirname,'../../web'),{maxAge:0}));
router.get('/web/api/session',wrap(async(req,res)=>{const token='guest_anon_tok_'+crypto.randomBytes(16).toString('hex');await pool.query("INSERT INTO day2_guest_tokens(token,expires_at) VALUES($1,now()+interval '2 hours')",[token]);res.set('Cache-Control','no-store').json({guest_token:token,expires_in:7200});}));
router.get('/web/api/context/:id',wrap(async(req,res)=>{
 const entity_id=parse(id,req.params.id),s=await getState();let match,session,player,club;
 if(s.matches[entity_id])match=require('../domain/scoring').project(s,entity_id);
 else if(entity_id.startsWith('cs-'))session=(await pool.query('SELECT response FROM day2_costream_sessions WHERE session_id=$1',[entity_id])).rows[0]?.response;
 else if(entity_id.startsWith('player-'))player=require('../services/day2-db').players(s).find(p=>p.player_id===entity_id&&!p.anonymized);
 else club=s.clubs.find(c=>c.club_id===entity_id);
 if(!match&&!session&&!player&&!club)fail(404,'NOT_FOUND','Entity not found.');
 const key=(await pool.query("SELECT stream_key FROM day2_stream_keys WHERE status='active' AND ($1::text IS NULL OR player_id=$1) ORDER BY created_at DESC LIMIT 1",[player?.player_id??session?.active_host??null])).rows[0];
 const media_path=session?'costream/'+entity_id:key?'live/'+key.stream_key:null;
 const inventory=(await pool.query('SELECT variant_id,stock,reserved FROM inventory')).rows;
 const products=s.products.filter(p=>p.tenant_id===(player?.tenant_id??club?.tenant_id??s.leagues[match?.league_id]?.tenant_id??'tenant-alpha')).map(p=>({product_id:p.product_id,name:p.name,variants:p.variants.map(v=>({...v,stock:inventory.find(i=>i.variant_id===v.variant_id)?.stock??0}))}));
 const vods=(await pool.query('SELECT vod_id,player_id,session_id,created_at FROM day2_vods WHERE ($1::text IS NULL OR player_id=$1) ORDER BY created_at DESC LIMIT 20',[player?.player_id??null])).rows;
 res.set('Cache-Control','no-store').json({entity_id,match,session,player:player&&{player_id:player.player_id,club_id:player.club_id,tenant_id:player.tenant_id},club,products,media_path,chat_room_id:session?.chat_room_id??'chat-'+entity_id,vods});
}));
router.post('/api/v1/register',wrap(async(req,res)=>{
 const b=parse(z.object({email:z.string().email().max(254),password:z.string().min(8).max(128)}),req.body);
 const user=await transaction(s=>{if(s.users.some(u=>u.email===b.email))fail(409,'ACCOUNT_EXISTS','Account already exists.');const password_salt=crypto.randomBytes(16).toString('hex'),u={user_id:'user-'+crypto.randomUUID(),email:b.email,role:'viewer',tenant_id:'tenant-alpha',password_salt,password_hash:crypto.scryptSync(b.password,password_salt,32).toString('hex')};s.users.push(u);return u;});
 res.status(201).json({token:require('../domain/rbac').sessionToken(user),user:{user_id:user.user_id,role:user.role,tenant_id:user.tenant_id}});
}));
router.post('/api/v1/cart/merge',wrap(async(req,res)=>{const u=await actor(req);res.json(await require('../domain/qr_guest').merge({...req.body,registered_user_id:u.player_id??u.user_id},require('../domain/rbac').authenticateSession(req.headers.authorization)));}));
router.get('/api/v1/players/:id/profile',wrap(async(req,res)=>{
 const s=await getState(),p=require('../services/day2-db').players(s).find(p=>p.player_id===req.params.id&&!p.anonymized);if(!p)fail(404,'PLAYER_NOT_FOUND','Player not found.');
 const matches=Object.keys(s.matches).map(mid=>require('../domain/scoring').project(s,mid)).filter(m=>[m.home.team_id,m.away.team_id].includes(p.club_id));
 const followers=Number((await pool.query('SELECT count(*) AS n FROM day2_follows WHERE athlete_id=$1',[p.player_id])).rows[0].n);
 const vods=(await pool.query('SELECT vod_id,created_at FROM day2_vods WHERE player_id=$1 ORDER BY created_at DESC',[p.player_id])).rows;
 res.json({player_id:p.player_id,club:s.clubs.find(c=>c.club_id===p.club_id),career:{club_match_records:matches.filter(m=>m.status==='completed'),record_scope:'club affiliation; individual attribution only when present in event payload'},upcoming:matches.filter(m=>m.status==='upcoming'),followers,vods});
}));
router.put('/api/v1/players/:id/follow',wrap(async(req,res)=>{const u=await actor(req),b=parse(z.object({following:z.boolean()}),req.body),p=require('../services/day2-db').players(await getState()).find(p=>p.player_id===req.params.id&&!p.anonymized);if(!p)fail(404,'PLAYER_NOT_FOUND','Player not found.');const result=await require('../services/day2-db').tx('follow:'+p.player_id,async c=>{if(b.following)await c.query('INSERT INTO day2_follows(follower_id,athlete_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[u.user_id,p.player_id]);else await c.query('DELETE FROM day2_follows WHERE follower_id=$1 AND athlete_id=$2',[u.user_id,p.player_id]);return Number((await c.query('SELECT count(*) AS n FROM day2_follows WHERE athlete_id=$1',[p.player_id])).rows[0].n);});res.json({athlete_id:p.player_id,following:b.following,followers:result});}));
router.get('/api/v1/chat/:room',wrap(async(req,res)=>{parse(id,req.params.room);const rows=(await pool.query("SELECT sequence,payload->'output'->'result' AS message FROM day2_chat_messages WHERE chat_room_id=$1 AND sequence>$2 ORDER BY sequence LIMIT 100",[req.params.room,Math.max(0,Number(req.query.after)||0)])).rows;res.json({messages:rows.filter(r=>r.message).map(r=>({...r.message,sequence:Number(r.sequence)}))});}));
router.post('/api/v1/chat',wrap(async(req,res)=>{const u=await actor(req),b=parse(z.object({chat_room_id:id,message_id:id,text:z.string().min(1).max(2000)}),req.body);const blocked=await pool.query("SELECT 1 FROM day2_moderator_actions WHERE chat_room_id=$1 AND sender=$2 AND action IN ('kick','mute')",[b.chat_room_id,u.user_id]);if(blocked.rowCount)fail(403,'FORBIDDEN','Sender is muted or kicked.');const result=await require('../domain/moderation_dlq').moderate({chat_room_id:b.chat_room_id,incoming_messages:[{message_id:b.message_id,sender:u.user_id,text:b.text}],check_dlq:false});res.status(201).json(result);}));
async function host(req){const u=await actor(req),row=(await pool.query('SELECT response FROM day2_costream_sessions WHERE session_id=$1',[parse(id,req.params.id)])).rows[0];if(!row)fail(404,'STREAM_NOT_FOUND','Session not found.');if(u.role!=='super_admin'&&u.player_id!==row.response.active_host)fail(403,'FORBIDDEN','Only the active host may control this stream.');return {u,session:row.response};}
router.post('/api/v1/costream/:id/start',wrap(async(req,res)=>{await host(req);res.json(await require('../services/media-sessions').start(req.params.id,req.body.layout));}));
router.post('/api/v1/costream/:id/stop',wrap(async(req,res)=>{await host(req);res.json({stopped:await require('../services/media-sessions').stop(req.params.id)});}));
router.post('/api/v1/streams/:id/record',wrap(async(req,res)=>{const u=await actor(req);if(u.role!=='super_admin'&&u.player_id!==req.params.id)fail(403,'FORBIDDEN','Own stream required.');res.json(await require('../services/media-sessions').record(parse(id,req.params.id)));}));
router.get('/api/v1/vods/:id/play',wrap(async(req,res)=>{const row=(await pool.query('SELECT object_key FROM day2_vods WHERE vod_id=$1',[parse(id,req.params.id)])).rows[0];if(!row)fail(404,'STREAM_NOT_FOUND','Recording not found.');const response=await require('../services/object-storage').get(row.object_key);if(!response.ok)fail(503,'NOT_READY','Recording storage unavailable.');res.set('Content-Type','video/mp4');require('stream').Readable.fromWeb(response.body).pipe(res);}));
router.get(['/web','/web/matches/:id/:state','/web/costream/:id','/web/players/:id','/web/clubs/:id'],(req,res)=>res.sendFile(path.join(__dirname,'../../web/index.html')));
module.exports=router;
