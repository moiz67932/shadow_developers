const request=require('../backend/node_modules/supertest');
const {spawnSync}=require('child_process');
const {createApp}=require('../backend/src/app');
const pool=require('../backend/src/database/pool');
const {migrate}=require('../backend/src/database/migrate');
const {reset,transaction}=require('../backend/src/services/state');
const {canonical,sign}=require('../backend/src/utils/canonical');
const {signDay2}=require('../backend/src/utils/day2-sign');
const config=require('../backend/src/config');
const integration=process.env.RUN_INTEGRATION==='1'?describe:describe.skip;
function pythonSignature(id,payload,salt=config.day2Salt){const r=spawnSync('python3',['-c','import json,hmac,hashlib,sys; d=json.load(sys.stdin); print(hmac.new(d["salt"].encode(),(d["id"]+"|"+json.dumps(d["payload"],sort_keys=True,separators=(",",":"),ensure_ascii=False)).encode(),hashlib.sha256).hexdigest())'],{input:JSON.stringify({salt,id,payload}),encoding:'utf8'});if(r.status!==0)throw new Error('Independent Python signature reference failed.');return r.stdout.trim();}
test('published full Appendix A vector and recursive Unicode canonical reference',()=>{
 const p={action:'rotate',player_id:'player-alpha-1',status:'active',stream_key:'sk_live_9f83a1b2c3d4e5f6'},salt='T01D27ad7ffff12e';
 expect(sign(p.stream_key,p,salt)).toBe('9bd054eb746844b4701c6f158209c315ef2b16f392efd733bdf0715940610289');expect(sign(p.stream_key,p,salt)).toBe(pythonSignature(p.stream_key,p,salt));
 expect(sign('sk_live_101',{action:'rotate',player_id:'player-alpha-1'},salt)).toBe('830b24eb2c795c02c54c17516b0e96870f5b65d98d28c2f0a15619ec3e076188');
 const unicode={'😀':true,'é':null,a:[false,'پاکستان',{z:2,a:1}]};expect(signDay2('unicode',unicode)).toBe(pythonSignature('unicode',unicode));expect(signDay2('unicode',unicode)).not.toBe(sign('unicode',unicode,'different-salt'));
});
integration('Day 2 exact contracts',()=>{
 let app;const post=(path,body)=>request(app).post('/audit/v1'+path).send(body);
 beforeAll(()=>migrate());beforeEach(async()=>{await reset();app=createApp();});afterAll(()=>pool.end());
 const keyBody={action:'rotate',player_id:'player-alpha-1',stream_key_override:'sk_live_9f83a1b2c3d4e5f6'};
 test('stream generate/rotate/revoke fields, independent signatures and revoked persistence',async()=>{
   const generated=await post('/stream/key',{action:'generate',player_id:'player-alpha-1'});expect(generated.status).toBe(201);expect(generated.body.stream_key).toMatch(/^sk_live_[a-f0-9]{16}$/);
   const rotated=await post('/stream/key',keyBody);expect(rotated.status).toBe(200);expect(rotated.body.ingest_endpoints.rtmp).toBe('rtmp://localhost:1935/live/'+keyBody.stream_key_override);
   expect(rotated.body.signature).toBe(pythonSignature(rotated.body.stream_key,{action:'rotate',player_id:'player-alpha-1',status:'active',stream_key:rotated.body.stream_key}));
   expect((await pool.query('SELECT status FROM day2_stream_keys WHERE stream_key=$1',[generated.body.stream_key])).rows[0].status).toBe('revoked');
   const revoked=await post('/stream/key',{action:'revoke',player_id:'player-alpha-1'});expect(revoked.body).toMatchObject({status:'revoked',ingest_endpoints:null,playback_url:null});expect(revoked.body.signature).toBe(pythonSignature(revoked.body.stream_key,{action:'revoke',player_id:'player-alpha-1',status:'revoked',stream_key:revoked.body.stream_key}));
   expect((await post('/stream/key',keyBody)).body.error.code).toBe('KEY_REVOKED');
 });
 test('stream validation precedence and ten simultaneous rotations',async()=>{
   expect((await post('/stream/key',{action:'invalid',player_id:'missing'})).status).toBe(400);expect((await post('/stream/key',{action:'revoke',player_id:'missing'})).body.error.code).toBe('PLAYER_NOT_FOUND');expect((await post('/stream/key',{action:'revoke',player_id:'player-alpha-1'})).body.error.code).toBe('STREAM_NOT_FOUND');
   const r=await Promise.all(Array.from({length:10},()=>post('/stream/key',{action:'rotate',player_id:'player-alpha-1'})));expect(r.every(r=>r.status===200)).toBe(true);expect((await pool.query("SELECT * FROM day2_stream_keys WHERE player_id='player-alpha-1' AND status='active'")).rowCount).toBe(1);
 });
 const session={session_id:'cs-session-101',host_player_id:'player-alpha-1',participants:['player-alpha-1','player-alpha-2','player-beta-1','player-beta-2'],layout:'grid_4',simulate_host_drop:true};
 test('four-way handoff contract and simultaneous replay',async()=>{
   const r=await Promise.all([post('/costream/session',session),post('/costream/session',session)]);expect(r.map(x=>x.status).sort()).toEqual([200,201]);expect(r[0].body).toEqual(r[1].body);const b=r[0].body;
   expect(b).toMatchObject({active_host:'player-alpha-2',handoff_occurred:true,participant_count:3,active_participants:session.participants.slice(1),chat_room_id:'chat-cs-session-101',composite_playback_url:'http://localhost:8888/costream/cs-session-101/index.m3u8'});
   expect(b.signature).toBe(pythonSignature(b.session_id,{active_host:b.active_host,handoff_occurred:true,layout:'grid_4',participant_count:3,session_id:b.session_id,status:'active'}));
 });
 test('effective layout count, sole-host termination and existence-before-capacity',async()=>{
   const sole=await post('/costream/session',{...session,participants:['player-alpha-1'],layout:'solo'});expect(sole.status).toBe(201);expect(sole.body).toMatchObject({active_host:null,handoff_occurred:false,participant_count:0,status:'terminated'});
   expect((await post('/costream/session',{...session,session_id:'cs-two',participants:session.participants.slice(0,2),layout:'solo'})).status).toBe(201);
   expect((await post('/costream/session',{...session,session_id:'cs-bad',layout:'grid_2'})).body.error.code).toBe('UNSUPPORTED_LAYOUT');
   expect((await post('/costream/session',{...session,participants:[...session.participants,'missing']})).body.error.code).toBe('PLAYER_NOT_FOUND');
   expect((await post('/costream/session',{...session,host_player_id:'player-beta-2',participants:['player-alpha-1']})).status).toBe(400);
 });
 test('existing session host-drop transition is persistent and replays cannot reapply it',async()=>{
   const initial=await post('/costream/session',{...session,simulate_host_drop:false});expect(initial.status).toBe(201);
   const dropped=await post('/costream/session',session);expect(dropped.status).toBe(200);expect(dropped.body.participant_count).toBe(3);
   expect((await post('/costream/session',session)).body).toEqual(dropped.body);
   expect((await post('/costream/session',{...session,simulate_host_drop:false})).body).toEqual(initial.body);
   expect((await pool.query('SELECT active_host FROM day2_costream_sessions')).rows[0].active_host).toBe('player-alpha-2');
   await transaction(s=>s.players.push({player_id:'player-five',tenant_id:'tenant-alpha',club_id:'club-alpha-1'}));
   expect((await post('/costream/session',{...session,session_id:'cs-full',participants:[...session.participants,'player-five']})).body.error.code).toBe('COSTREAM_SESSION_FULL');
 });
 test('QR stable mapping, dynamic signature, private telemetry and short redirect',async()=>{
   const inspect=await request(app).get('/audit/v1/qr/match-m1?inspect=true');expect(inspect.body.scan_count).toBe(0);expect(inspect.body.target_route).toBe('/web/matches/match-m1/live');
   const r=await request(app).get('/audit/v1/qr/match-m1').set('User-Agent','PRIVATE-UA Android');expect(r.body.scan_count).toBe(1);
   expect(r.body.signature).toBe(pythonSignature('match-m1',{entity_id:'match-m1',short_url:r.body.short_url,target_route:r.body.target_route}));
   expect((await request(app).get('/q/m1-live')).headers.location).toBe('/web/matches/match-m1/live');
   await post('/events',{event_id:'end',match_id:'match-m1',type:'match_end',payload:{}});
   const ended=await request(app).get('/audit/v1/qr/match-m1?inspect=true');expect(ended.body.short_url).toBe(r.body.short_url);expect(ended.body.target_route).toBe('/web/matches/match-m1/vod');expect(ended.body.signature).not.toBe(r.body.signature);
   const buckets=(await pool.query('SELECT * FROM day2_qr_scan_buckets')).rows;expect(JSON.stringify(buckets)).not.toContain('PRIVATE-UA');expect(Object.keys(buckets[0]).sort()).toEqual(['device_family','entity_id','scans','utc_bucket']);
   expect((await request(app).get('/audit/v1/qr/no-such-entity')).body.error.code).toBe('QR_NOT_FOUND');
 });
 test('QR all entity types and valid SVG/PNG/PDF exports',async()=>{
   await post('/costream/session',session);
   for(const id of ['player-alpha-1','club-alpha-1','cs-session-101'])expect((await request(app).get('/audit/v1/qr/'+id+'?inspect=true')).status).toBe(200);
   const binary=(res,done)=>{const chunks=[];res.on('data',b=>chunks.push(b));res.on('end',()=>done(null,Buffer.concat(chunks)));};
   for(const [fmt,type,magic] of [['svg','image/svg+xml','<'],['png','image/png','89504e470d0a1a0a'],['pdf','application/pdf','%PDF-']]){
     const r=await request(app).get('/audit/v1/qr/match-m1/export.'+fmt).buffer(true).parse(binary);expect(r.status).toBe(200);expect(r.headers['content-type']).toContain(type);
     if(fmt==='png')expect(r.body.subarray(0,8).toString('hex')).toBe(magic);else expect(r.body.toString().startsWith(magic)).toBe(true);
     if(fmt==='svg')expect(r.body.toString()).toContain('<svg');if(fmt==='pdf'){const text=spawnSync('pdftotext',['-','-'],{input:r.body,encoding:'utf8'});expect(text.status).toBe(0);expect(text.stdout).toContain('match-m1');}
   }
 });
 test('postponed, cancelled and abandoned targets update through signed event transitions',async()=>{
   for(const [n,type,status] of [[1,'match_postpone','postponed'],[2,'match_resume','in_progress'],[3,'match_abandon','abandoned']]){
     expect((await post('/events',{event_id:'state-'+n,match_id:'match-m1',type,payload:{}})).status).toBe(201);
     const r=await request(app).get('/audit/v1/qr/match-m1?inspect=true');expect(r.body.entity_status).toBe(status);expect(r.body.short_url).toBe('http://localhost:8080/q/m1-live');expect(r.body.target_route).toBe(status==='in_progress'?'/web/matches/match-m1/live':'/web/matches/match-m1/notice?state='+status);
   }
   expect((await post('/events',{event_id:'invalid-resume',match_id:'match-m1',type:'match_resume',payload:{}})).body.error.code).toBe('ILLEGAL_TRANSITION');
 });
 const cart={guest_token:'guest_anon_tok_7721',registered_user_id:'player-alpha-1',guest_cart:[{variant_id:'var-jersey-001-L',quantity:1}],test_write_block:true};
 test('atomic cart lost-ACK replay, independent signature, currency and incompatible reuse',async()=>{
   const r=await Promise.all([post('/guest/cart-merge',cart),post('/guest/cart-merge',cart)]);expect(r.every(x=>x.status===200)).toBe(true);expect(r[0].body).toEqual(r[1].body);const b=r[0].body;
   expect(b).toMatchObject({write_block_verified:true,write_rejection_code:403,cart_merged:true,active_cart:{currency:'PKR',total_items:1,subtotal_minor:450000}});
   expect(b.signature).toBe(pythonSignature(cart.guest_token,{cart_merged:true,guest_token:cart.guest_token,registered_user_id:cart.registered_user_id,total_items:1,write_rejection_code:403}));
   expect((await post('/guest/cart-merge',{...cart,registered_user_id:'player-alpha-2'})).body.error.code).toBe('GUEST_TOKEN_ALREADY_MERGED');
   expect((await pool.query("SELECT stock,reserved FROM inventory WHERE variant_id='var-jersey-001-L'")).rows[0]).toEqual({stock:5,reserved:0});
 });
 test('cart aggregates existing quantities, rejects stock without partial commit and validates all references',async()=>{
   await pool.query("INSERT INTO day2_cart_items VALUES('player-alpha-1','var-jersey-001-L',2)");
   expect((await post('/guest/cart-merge',{...cart,guest_cart:[{variant_id:'var-jersey-001-L',quantity:4}]})).body.error.code).toBe('OUT_OF_STOCK');
   expect((await pool.query('SELECT quantity FROM day2_cart_items')).rows[0].quantity).toBe(2);
   const r=await post('/guest/cart-merge',cart);expect(r.body.active_cart.total_items).toBe(3);
   expect((await post('/guest/cart-merge',{...cart,guest_cart:[{variant_id:'missing',quantity:1}]})).body.error.code).toBe('VARIANT_NOT_FOUND');
   expect((await post('/guest/cart-merge',{...cart,registered_user_id:'missing'})).body.error.code).toBe('PLAYER_NOT_FOUND');
 });
 const messages=[{message_id:'clean',sender:'user-clean',text:'Clean sports commentary'},{message_id:'bad',sender:'user-spammer',text:'Watch this illegal stream b@d_w0rd now!'},{message_id:'flood',sender:'user-flooder',text:'GOAL! AAAA!!!'}];
 test('moderation exact example, masking, ten-thread idempotence, sequence and signature',async()=>{
   const input={chat_room_id:'chat-cs-session-101',incoming_messages:messages};const r=await Promise.all(Array.from({length:10},()=>post('/moderation/test',input)));expect(r.every(x=>x.status===200)).toBe(true);
   const b=r[0].body;expect(b.filtered_messages).toEqual([{...messages[0],flagged:false,reason:null},{...messages[1],text:'Watch this illegal stream *** now!',flagged:true,reason:'profanity_leetspeak'}]);expect(b.kicked_users).toEqual(['user-spammer']);expect(b.muted_users).toEqual(['user-flooder']);
   expect(b.signature).toBe(pythonSignature(input.chat_room_id,{chat_room_id:input.chat_room_id,flagged_count:1,kicked_users:['user-spammer'],queue_depth:0}));
   expect((await pool.query('SELECT * FROM day2_chat_messages')).rowCount).toBe(3);expect((await pool.query('SELECT * FROM day2_moderator_actions')).rowCount).toBe(2);
 });
 test('all documented obfuscations and flood thresholds',()=>{
   const {mask,filter}=require('../backend/src/domain/moderation_dlq');
   for(const word of ['b@d_w0rd','bad_word','p.0.r.n','s!c@m','f*u*c*k','a$$h0le','idiot_spam','s\u200bhit','bitch'])expect(mask('say '+word+' now')).toEqual({text:'say *** now',flagged:true});
   expect(mask('scam porn')).toEqual({text:'*** ***',flagged:true});expect(mask('Scamp is a name').flagged).toBe(false);
   for(const text of ['AAAA','!!!','???','aAaA'])expect(filter({text}).result).toBeNull();expect(filter({text:'Great goal!'}).result.flagged).toBe(false);
 });
 test('durable DLQ exhausts three attempts and replay cannot double-enqueue',async()=>{
   const b={chat_room_id:'chat-dlq',incoming_messages:[{message_id:'one',sender:'sender',text:'Okay'}],simulate_failed_notifications:2};
   const first=await post('/moderation/test',b);expect(first.body.dlq_status.queue_depth).toBe(2);expect((await post('/moderation/test',b)).body.dlq_status.queue_depth).toBe(2);
   expect((await pool.query('SELECT attempts,status FROM day2_notifications')).rows).toEqual([{attempts:3,status:'dead'},{attempts:3,status:'dead'}]);
 });
 test('sender sliding window blocks a fourth new message, without counting retries twice',async()=>{
   const r=await post('/moderation/test',{chat_room_id:'chat-rate',incoming_messages:[1,2,3,4].map(n=>({message_id:'rate-'+n,sender:'sender-rate',text:'Great play'}))});expect(r.status).toBe(200);expect(r.body.filtered_messages).toHaveLength(3);expect(r.body.muted_users).toEqual(['sender-rate']);expect(r.body.kicked_users).toEqual(['sender-rate']);
 });
 test('publish authentication allows only current keys and returns exact revoked error',async()=>{
   const active=await post('/stream/key',keyBody);const path='live/'+active.body.stream_key;
   expect((await request(app).post('/internal/media/auth').send({action:'publish',path})).status).toBe(200);
   await post('/stream/key',{action:'revoke',player_id:'player-alpha-1'});
   const denied=await request(app).post('/internal/media/auth').send({action:'publish',path});expect(denied.status).toBe(409);expect(denied.body.error.code).toBe('KEY_REVOKED');
 });
 test('Prometheus metrics reflect real QR and DLQ counters',async()=>{
   await request(app).get('/audit/v1/qr/match-m1');await post('/moderation/test',{chat_room_id:'chat',incoming_messages:[],simulate_failed_notifications:1});
   const r=await request(app).get('/metrics');expect(r.status).toBe(200);expect(r.headers['content-type']).toContain('text/plain; version=0.0.4');
   for(const name of ['nascon_active_ws_connections','nascon_active_streams_total','nascon_stream_bitrate_kbps','nascon_dlq_depth','nascon_qr_scans_total'])expect(r.text).toMatch(new RegExp('^'+name+' [0-9.]+$','m'));expect(r.text).toContain('nascon_dlq_depth 1');expect(r.text).toContain('nascon_qr_scans_total 1');
 });
 test('privacy wipes linked account, cart, tokens, queued notifications and private cache references',async()=>{
   await post('/guest/cart-merge',cart);await pool.query("INSERT INTO day2_notifications(notification_id,recipient_id,topic,payload) VALUES('private','user-player','private','{\"email\":\"private@example.test\"}'); INSERT INTO day2_private_cache_refs VALUES('user-player','private-key')");
   const redis=new (require('../backend/node_modules/ioredis'))(process.env.TEST_REDIS_URL);await redis.set('private-key','private@example.test');
   const started=Date.now(),r=await request(app).delete('/audit/v1/privacy/user/player-alpha-1');expect(r.body).toEqual({user_id:'player-alpha-1',status:'anonymized',pii_retained:false,tokens_wiped:true});expect(Date.now()-started).toBeLessThan(1000);
   expect((await pool.query('SELECT * FROM day2_guest_tokens')).rowCount).toBe(0);expect((await pool.query('SELECT * FROM day2_private_cache_refs')).rowCount).toBe(0);expect((await pool.query("SELECT * FROM day2_notifications WHERE recipient_id='user-player'")).rowCount).toBe(0);
   expect(await redis.get('private-key')).toBeNull();await redis.quit();
   expect((await request(app).post('/api/v1/login').send({email:'player@nascon.test',password:'Nascon@26'})).status).toBe(401);
   expect((await request(app).delete('/audit/v1/privacy/user/player-alpha-1')).status).toBe(200);
 });
 test('malformed JSON, field types, guest guards, body limits and audit rate exemptions',async()=>{
   for(const path of ['/stream/key','/costream/session','/guest/cart-merge','/moderation/test']){const r=await post(path,{});expect(r.status).toBe(400);expect(Array.isArray(r.body.error.details.fields)).toBe(true);}
   expect((await request(app).post('/audit/v1/stream/key').set('Content-Type','application/json').send('{bad')).status).toBe(400);
   expect((await post('/moderation/test',{huge:'x'.repeat(1024*1024)})).status).toBe(413);
   for(const path of ['/api/v1/chat','/audit/v1/checkout','/audit/v1/events'])expect((await request(app).post(path).set('Authorization','Bearer guest_anon_tok_7721').send({})).status).toBe(403);
   const burst=await Promise.all(Array.from({length:120},()=>request(app).get('/audit/v1/qr/match-m1?inspect=true')));expect(burst.every(r=>r.status===200)).toBe(true);
 });
});
