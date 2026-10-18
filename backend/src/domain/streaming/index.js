const crypto=require('crypto');
const {z,id,parse}=require('../../utils/validation');
const {fail}=require('../../utils/errors');
const {getState}=require('../../services/state');
const {tx,players}=require('../../services/day2-db');
const {signDay2}=require('../../utils/day2-sign');
const schema=z.object({action:z.enum(['generate','rotate','revoke']),player_id:id,label:z.string().min(1).max(64).default('default'),stream_key_override:z.string().min(1).max(256).optional()});
async function key(input){const b=parse(schema,input);return tx('key:'+b.player_id,async c=>{
  const s=await getState(c);if(!players(s).some(p=>p.player_id===b.player_id))fail(404,'PLAYER_NOT_FOUND','Player not found.');
  const old=(await c.query("SELECT * FROM day2_stream_keys WHERE player_id=$1 ORDER BY created_at DESC,stream_key DESC",[b.player_id])).rows;
  const active=old.find(k=>k.status==='active');let stream_key,status;
  if(b.action==='revoke'){
    const record=active??old[0];if(!record)fail(404,'STREAM_NOT_FOUND','Player has no stream key.');
    stream_key=record.stream_key;status='revoked';
    await c.query("UPDATE day2_stream_keys SET status='revoked',revoked_at=COALESCE(revoked_at,now()) WHERE player_id=$1",[b.player_id]);
  }else{
    stream_key=b.stream_key_override??'sk_live_'+crypto.randomBytes(8).toString('hex');status='active';
    const duplicate=(await c.query('SELECT * FROM day2_stream_keys WHERE stream_key=$1',[stream_key])).rows[0];
    if(duplicate&&(duplicate.player_id!==b.player_id||duplicate.status==='revoked'))fail(409,'KEY_REVOKED','Stream key is unavailable.');
    await c.query("UPDATE day2_stream_keys SET status='revoked',revoked_at=now() WHERE player_id=$1 AND status='active' AND stream_key<>$2",[b.player_id,stream_key]);
    await c.query("INSERT INTO day2_stream_keys(stream_key,player_id,label,status) VALUES($1,$2,$3,'active') ON CONFLICT(stream_key) DO UPDATE SET label=EXCLUDED.label",[stream_key,b.player_id,b.label]);
  }
  const payload={action:b.action,player_id:b.player_id,status,stream_key};
  const body={player_id:b.player_id,label:b.label,stream_key,status,ingest_endpoints:status==='active'?{rtmp:`rtmp://localhost:1935/live/${stream_key}`,webrtc:`http://localhost:8889/live/${stream_key}/whip`}:null,playback_url:status==='active'?`http://localhost:8888/live/${stream_key}/index.m3u8`:null,signature:signDay2(stream_key,payload)};
  return {status:b.action==='generate'?201:200,body,invalidated:old.filter(k=>k.status==='active'&&(status==='revoked'||k.stream_key!==stream_key)).map(k=>k.stream_key)};
});}
async function publishAllowed(path){const stream_key=path.replace(/^live\//,'');const row=(await require('../../database/pool').query('SELECT status FROM day2_stream_keys WHERE stream_key=$1',[stream_key])).rows[0];if(!row)fail(404,'STREAM_NOT_FOUND','Stream key not found.');if(row.status!=='active')fail(409,'KEY_REVOKED','Stream key has been revoked.');return true;}
module.exports={key,publishAllowed};
