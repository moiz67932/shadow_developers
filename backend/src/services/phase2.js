const {append}=require('../domain/scoring');
const tables=['day2_cart_merges','day2_cart_items','day2_carts','day2_guest_tokens','day2_stream_keys','day2_chat_messages','day2_moderator_actions','day2_chat_rooms','day2_costream_participants','day2_costream_sessions','day2_qr_scan_buckets','day2_qr_links','day2_notification_dlq','day2_notifications','day2_follows','day2_vods','day2_private_cache_refs'];
const demoOwnership={
  'user-admin':{},
  'user-organizer':{league_id:'league-alpha-1',league_ids:['league-alpha-1']},
  'user-manager':{club_id:'club-alpha-1'},
  'user-player':{player_id:'player-alpha-1',club_id:'club-alpha-1'},
  'user-viewer':{}
};
function extendState(state){
  for(const user of state.users??[])for(const [key,value] of Object.entries(demoOwnership[user.user_id]??{}))if(user[key]===undefined)user[key]=value;
  const demo=state.leagues['demo-alpha-cup'];
  if(demo?.tenant_id==='tenant-alpha'&&!demo.owner_user_id)demo.owner_user_id='user-organizer';
  if(!state.players.some(p=>p.player_id==='player-beta-2'))state.players.push({player_id:'player-beta-2',club_id:'club-beta-1',tenant_id:'tenant-beta'});
  if(!state.matches['match-m1']){
    const league=state.leagues['league-alpha-1'];
    for(const club of ['club-alpha-1','club-alpha-2'])if(!league.teams.includes(club))league.teams.push(club);
    if(!league.fixtures.some(f=>f.match_id==='match-m1'))league.fixtures.push({match_id:'match-m1',home:'club-alpha-1',away:'club-alpha-2'});
    append(state,{event_id:'day2-seed-m1-init',match_id:'match-m1',type:'match_init',payload:{sport_id:'football',league_id:'league-alpha-1',home_team_id:'club-alpha-1',away_team_id:'club-alpha-2'}});
    append(state,{event_id:'day2-seed-m1-start',match_id:'match-m1',type:'match_start',payload:{}});
    append(state,{event_id:'day2-seed-m1-goal',match_id:'match-m1',type:'score',payload:{team_id:'club-alpha-1',event_type:'goal'}});
  }
  return state;
}
async function seedTables(client){
  const links=[['match-m1','match','m1-live'],['player-alpha-1','player','p-alpha-1'],['club-alpha-1','club','c-alpha-1']];
  for(const [id,type,code] of links)await client.query('INSERT INTO day2_qr_links(entity_id,entity_type,short_code) VALUES($1,$2,$3) ON CONFLICT DO NOTHING',[id,type,code]);
  await client.query("INSERT INTO day2_guest_tokens(token,expires_at) VALUES('guest_anon_tok_7721',(date_trunc('day',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')+interval '1 day') ON CONFLICT DO NOTHING");
  await client.query("INSERT INTO day2_carts(user_id,currency) VALUES('player-alpha-1','PKR') ON CONFLICT DO NOTHING");
}
module.exports={extendState,seedTables,tables};
