const {z,id,parse,check}=require('../../utils/validation');
const {fail}=require('../../utils/errors');
const {canonical}=require('../../utils/canonical');
const {signDay2}=require('../../utils/day2-sign');
const {tx,players}=require('../../services/day2-db');
const {getState}=require('../../services/state');
const hash=b=>require('crypto').createHash('sha256').update(canonical(b)).digest('hex');
const schema=z.object({session_id:id,host_player_id:id,participants:z.array(id).min(1),layout:z.enum(['solo','grid_2','grid_4','spotlight']).default('grid_4'),simulate_host_drop:z.boolean().default(false)});
async function session(input){const b=parse(schema,input);check(new Set(b.participants).size===b.participants.length,'participants');return tx('session:'+b.session_id,async c=>{
  const s=await getState(c),existingPlayers=new Set(players(s).map(p=>p.player_id));
  for(const p of [...b.participants,b.host_player_id])if(!existingPlayers.has(p))fail(404,'PLAYER_NOT_FOUND','Player not found.',{player_id:p});
  check(b.participants.includes(b.host_player_id),'host_player_id');
  if(b.participants.length>4)fail(409,'COSTREAM_SESSION_FULL','At most four participants are supported.');
  const active=b.simulate_host_drop?b.participants.filter(p=>p!==b.host_player_id):b.participants;
  if((b.layout==='solo'&&active.length>1)||(b.layout==='grid_2'&&active.length>2))fail(400,'UNSUPPORTED_LAYOUT','Layout cannot fit active participants.');
  const previous=(await c.query('SELECT request,response FROM day2_costream_sessions WHERE session_id=$1',[b.session_id])).rows[0];
  const replay=(await c.query('SELECT response FROM day2_costream_replays WHERE session_id=$1 AND request_hash=$2',[b.session_id,hash(b)])).rows[0];
  if(replay)return {status:200,body:replay.response};
  if(previous&&canonical(previous.request)===canonical(b))return {status:200,body:previous.response};
  if(previous&&!(b.simulate_host_drop&&!previous.request.simulate_host_drop&&canonical({...previous.request,simulate_host_drop:true})===canonical(b)))fail(409,'COSTREAM_SESSION_CONFLICT','Session ID is already initialized with different parameters.');
  const status=active.length?'active':'terminated',active_host=active.length?(b.simulate_host_drop?active[0]:b.host_player_id):null,handoff_occurred=b.simulate_host_drop&&active.length>0;
  const payload={active_host,handoff_occurred,layout:b.layout,participant_count:active.length,session_id:b.session_id,status};
  const body={session_id:b.session_id,status,active_host,handoff_occurred,participant_count:active.length,active_participants:active,layout:b.layout,composite_playback_url:`http://localhost:8888/costream/${b.session_id}/index.m3u8`,chat_room_id:'chat-'+b.session_id,signature:signDay2(b.session_id,payload)};
  if(previous){
    await c.query('INSERT INTO day2_costream_replays(session_id,request_hash,response) VALUES($1,$2,$3) ON CONFLICT DO NOTHING',[b.session_id,hash(previous.request),previous.response]);
    await c.query('UPDATE day2_costream_sessions SET request=$2,response=$3,status=$4,active_host=$5,layout=$6 WHERE session_id=$1',[b.session_id,b,body,status,active_host,b.layout]);
  }else await c.query('INSERT INTO day2_costream_sessions(session_id,request,response,status,active_host,layout) VALUES($1,$2,$3,$4,$5,$6)',[b.session_id,b,body,status,active_host,b.layout]);
  for(let i=0;i<b.participants.length;i++)await c.query('INSERT INTO day2_costream_participants(session_id,player_id,position,active) VALUES($1,$2,$3,$4) ON CONFLICT(session_id,player_id) DO UPDATE SET active=EXCLUDED.active',[b.session_id,b.participants[i],i,active.includes(b.participants[i])]);
  await c.query('INSERT INTO day2_costream_replays(session_id,request_hash,response) VALUES($1,$2,$3)',[b.session_id,hash(b),body]);
  await c.query('INSERT INTO day2_chat_rooms(chat_room_id) VALUES($1) ON CONFLICT DO NOTHING',[body.chat_room_id]);
  return {status:previous?200:201,body};
});}
module.exports={session};
