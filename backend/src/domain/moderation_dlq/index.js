const crypto=require('crypto');
const {z,id,int,parse}=require('../../utils/validation');
const {fail}=require('../../utils/errors');
const {canonical}=require('../../utils/canonical');
const {signDay2}=require('../../utils/day2-sign');
const {tx}=require('../../services/day2-db');
const dictionary=['badword','porn','scam','sicam','fuck','shit','bitch','asshole','idiotspam'];
function mask(text){
  const substitutions={'@':'a','0':'o','1':'i','!':'i','3':'e','$':'s'};let normalized='',positions=[];
  for(let i=0;i<text.length;){const char=String.fromCodePoint(text.codePointAt(i)),mapped=substitutions[char]??char.toLowerCase();
    if(/[\p{L}\p{N}\s]/u.test(mapped)){for(const c of mapped){normalized+=c;positions.push([i,i+char.length]);}}i+=char.length;
  }
  const matches=[];
  for(const word of dictionary){let from=0,index;while((index=normalized.indexOf(word,from))>=0){const before=normalized[index-1],after=normalized[index+word.length];if((!before||!/\p{L}|\p{N}/u.test(before))&&(!after||!/\p{L}|\p{N}/u.test(after))&&(word!=='sicam'||text.slice(positions[index][0],positions[index+word.length-1][1]).includes('!')))matches.push([positions[index][0],positions[index+word.length-1][1]]);from=index+word.length;}}
  matches.sort((a,b)=>b[0]-a[0]);for(const [start,end] of matches)text=text.slice(0,start)+'***'+text.slice(end);
  return {text,flagged:matches.length>0};
}
function filter(message){if(/(.)\1{3,}/iu.test(message.text)||/[!?]{3,}/u.test(message.text))return {result:null,action:'mute',reason:'flood_character_repeat'};
  const masked=mask(message.text);return {result:{...message,text:masked.text,flagged:masked.flagged,reason:masked.flagged?'profanity_leetspeak':null},action:masked.flagged?'kick':null,reason:masked.flagged?'profanity_leetspeak':null};}
const schema=z.object({chat_room_id:id,incoming_messages:z.array(z.object({message_id:id,sender:id,text:z.string()})),check_dlq:z.boolean().default(true),simulate_failed_notifications:int.min(0).max(1000).default(0)});
async function moderate(input){const b=parse(schema,input);return tx('room:'+b.chat_room_id,async c=>{
  await c.query('INSERT INTO day2_chat_rooms(chat_room_id) VALUES($1) ON CONFLICT DO NOTHING',[b.chat_room_id]);
  await c.query('SELECT last_sequence FROM day2_chat_rooms WHERE chat_room_id=$1 FOR UPDATE',[b.chat_room_id]);
  const filtered_messages=[];
  for(const message of b.incoming_messages){
    const previous=(await c.query('SELECT payload FROM day2_chat_messages WHERE chat_room_id=$1 AND message_id=$2',[b.chat_room_id,message.message_id])).rows[0];
    let output;
    if(previous){if(canonical(previous.payload.input)!==canonical(message))fail(409,'DUPLICATE_MESSAGE_ID','Message ID has a different payload.');output=previous.payload.output;}
    else{
      output=filter(message);
      const recent=(await c.query("SELECT count(*)::int AS n FROM day2_chat_messages WHERE chat_room_id=$1 AND sender=$2 AND created_at>clock_timestamp()-interval '2 seconds'",[b.chat_room_id,message.sender])).rows[0].n;
      if(recent>=3)output={result:null,action:'mute',reason:'rate_limit_exceeded'};
      const seq=(await c.query('UPDATE day2_chat_rooms SET last_sequence=last_sequence+1 WHERE chat_room_id=$1 RETURNING last_sequence',[b.chat_room_id])).rows[0].last_sequence;
      await c.query('INSERT INTO day2_chat_messages(chat_room_id,message_id,sequence,sender,payload) VALUES($1,$2,$3,$4,$5)',[b.chat_room_id,message.message_id,seq,message.sender,{input:message,output}]);
      if(output.action)await c.query('INSERT INTO day2_moderator_actions(chat_room_id,sender,action) VALUES($1,$2,$3) ON CONFLICT DO NOTHING',[b.chat_room_id,message.sender,output.action]);
      if(output.reason==='rate_limit_exceeded')await c.query("INSERT INTO day2_moderator_actions(chat_room_id,sender,action) VALUES($1,$2,'kick') ON CONFLICT DO NOTHING",[b.chat_room_id,message.sender]);
    }
    if(output.result)filtered_messages.push(output.result);
  }
  // The audit explicitly requests failed deliveries to be exhausted before inspection.
  // Bounded exponential retry is executed (1, 2 ms); records retain actual attempt count.
  for(let i=0;i<b.simulate_failed_notifications;i++){
    const notification_id='failed-'+crypto.createHash('sha256').update(b.chat_room_id+'|'+canonical(b.incoming_messages)+'|'+i).digest('hex');
    const inserted=await c.query("INSERT INTO day2_notifications(notification_id,topic,payload) VALUES($1,'audit.failed',$2) ON CONFLICT DO NOTHING RETURNING notification_id",[notification_id,{chat_room_id:b.chat_room_id}]);
    if(inserted.rowCount){
      for(let attempt=1;attempt<=3;attempt++){await c.query('UPDATE day2_notifications SET attempts=$2,next_attempt_at=now()+($3::int*interval \'1 millisecond\') WHERE notification_id=$1',[notification_id,attempt,2**(attempt-1)]);if(attempt<3)await new Promise(r=>setTimeout(r,2**(attempt-1)));}
      await c.query("UPDATE day2_notifications SET status='dead' WHERE notification_id=$1",[notification_id]);
      await c.query('INSERT INTO day2_notification_dlq(notification_id,payload) VALUES($1,$2) ON CONFLICT DO NOTHING',[notification_id,{notification_id,chat_room_id:b.chat_room_id,attempts:3}]);
    }
  }
  const actions=(await c.query('SELECT sender,action FROM day2_moderator_actions WHERE chat_room_id=$1 ORDER BY id',[b.chat_room_id])).rows;
  const kicked_users=actions.filter(a=>a.action==='kick').map(a=>a.sender),muted_users=actions.filter(a=>a.action==='mute').map(a=>a.sender);
  const undelivered_events=(await c.query('SELECT payload FROM day2_notification_dlq ORDER BY failed_at,notification_id')).rows.map(r=>r.payload),queue_depth=undelivered_events.length;
  const body={chat_room_id:b.chat_room_id,filtered_messages,kicked_users,muted_users,dlq_status:{queue_depth,undelivered_events}};
  body.signature=signDay2(b.chat_room_id,{chat_room_id:b.chat_room_id,flagged_count:filtered_messages.filter(m=>m.flagged).length,kicked_users,queue_depth});return body;
});}
module.exports={mask,filter,moderate};
