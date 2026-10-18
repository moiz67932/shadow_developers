const request=require('../backend/node_modules/supertest');
const jwt=require('../backend/node_modules/jsonwebtoken');
const {spawnSync}=require('child_process');
const {createApp}=require('../backend/src/app');
const pool=require('../backend/src/database/pool');
const {migrate}=require('../backend/src/database/migrate');
const {seed}=require('../backend/src/database/seed');
const {reset,getState,transaction}=require('../backend/src/services/state');
const {sessionToken,matrix}=require('../backend/src/domain/rbac');
const {identities}=require('../backend/src/services/identities');
const integration=process.env.RUN_INTEGRATION==='1'?describe:describe.skip;
integration('Phase 1 foundation',()=>{
  let app;
  beforeAll(async()=>{await migrate();await migrate();});
  beforeEach(async()=>{await reset();app=createApp();});
  afterAll(()=>pool.end());
  const login=async(email='manager@nascon.test')=>(await request(app).post('/api/v1/login').send({email,password:'Nascon@26'})).body;
  test('real sessions contain account-derived identity and probes cannot become sessions',async()=>{
    for(const [email,id,scope] of [['manager','user-manager',{club_id:'club-alpha-1',player_id:null}],['organizer','user-organizer',{league_id:'league-alpha-1',club_id:null}],['viewer','user-viewer',{club_id:null,player_id:null}]]){
      const b=await login(email+'@nascon.test');expect(jwt.decode(b.token)).toMatchObject({sub:id,user_id:id,tenant_id:'tenant-alpha',purpose:'member_session',...scope});
    }
    expect((await request(app).get('/api/v1/bootstrap').set('Authorization','Bearer '+matrix().tokens.super_admin)).status).toBe(401);
    expect((await request(app).post('/api/v1/events').send({})).status).toBe(401);
  });
  test('all explicit guest writes fail at the server boundary without changing state',async()=>{
    const before=await getState();
    for(const path of ['/api/v1/chat','/audit/v1/checkout','/audit/v1/events','/api/v1/roster/transfer','/api/v1/offline-sync','/api/v1/follow']){
      const r=await request(app).post(path).set('Authorization','Bearer guest_anon_tok_7721').send({});
      expect(r.status).toBe(403);expect(r.body.error.code).toBe('GUEST_WRITE_FORBIDDEN');
    }
    expect(await getState()).toEqual(before);expect((await pool.query('SELECT count(*)::int AS n FROM orders')).rows[0].n).toBe(0);
    expect((await request(app).post('/audit/v1/rbac-matrix').send({})).status).toBe(200);
  });
  test('reset seeds exact Day 2 baseline and clears new state without breaking Day 1 response',async()=>{
    const a=await request(app).post('/audit/v1/reset').send({seed:42});
    expect(a.body).toEqual({status:'reset_complete',seed:42,sports:['football','basketball','cricket','esports'],tenants:['tenant-alpha','tenant-beta'],stock:{'var-jersey-001-M':1,'var-jersey-001-L':5}});
    const s=await getState();expect(s.players.find(p=>p.player_id==='player-beta-2')).toMatchObject({club_id:'club-beta-1'});
    const score=(await request(app).get('/audit/v1/score/match-m1')).body;expect(score).toMatchObject({status:'in_progress',league_id:'league-alpha-1',home:{team_id:'club-alpha-1',score:1},away:{score:0}});
    expect((await pool.query('SELECT entity_id,short_code,scan_count::int FROM day2_qr_links ORDER BY entity_id')).rows).toEqual([{entity_id:'club-alpha-1',short_code:'c-alpha-1',scan_count:0},{entity_id:'match-m1',short_code:'m1-live',scan_count:0},{entity_id:'player-alpha-1',short_code:'p-alpha-1',scan_count:0}]);
    expect((await pool.query("SELECT role,registered_user_id,invalidated_at FROM day2_guest_tokens WHERE token='guest_anon_tok_7721'")).rows[0]).toEqual({role:'guest',registered_user_id:null,invalidated_at:null});
    expect((await pool.query("SELECT * FROM day2_cart_items WHERE user_id='player-alpha-1'")).rowCount).toBe(0);
    await pool.query("UPDATE day2_qr_links SET scan_count=9; INSERT INTO day2_notifications(notification_id,topic,payload) VALUES('n','test','{}')");
    expect((await request(app).post('/audit/v1/reset').send({seed:42})).body).toEqual(a.body);
    expect(await getState()).toEqual(s);expect((await pool.query('SELECT * FROM day2_notifications')).rowCount).toBe(0);
  });
  test('startup seeding preserves existing scores, carts, inventory and session ownership',async()=>{
    await pool.query("UPDATE inventory SET stock=4 WHERE variant_id='var-jersey-001-L'; INSERT INTO day2_cart_items VALUES('player-alpha-1','var-jersey-001-L',2)");
    const before=await getState();await seed();await seed();expect(await getState()).toEqual(before);
    expect((await pool.query("SELECT stock FROM inventory WHERE variant_id='var-jersey-001-L'")).rows[0].stock).toBe(4);
    expect((await pool.query('SELECT quantity FROM day2_cart_items')).rows[0].quantity).toBe(2);
  });
  test('different accounts with the same role retain independent club and tenant authority',async()=>{
    await transaction(s=>{const manager=s.users.find(u=>u.user_id==='user-manager');s.users.push({...manager,user_id:'user-beta-manager',email:'beta@nascon.test',tenant_id:'tenant-beta',club_id:'club-beta-1'});});
    const b=await login('beta@nascon.test');expect(jwt.decode(b.token)).toMatchObject({sub:'user-beta-manager',tenant_id:'tenant-beta',club_id:'club-beta-1'});
    const r=await request(app).get('/api/v1/bootstrap').set('Authorization','Bearer '+b.token);expect(r.status).toBe(200);expect(r.body.clubs.map(c=>c.club_id)).toEqual(['club-beta-1']);expect(r.body.tenant.tenant_id).toBe('tenant-beta');expect(JSON.stringify(r.body)).not.toContain('club-alpha');
    const denied=await request(app).post('/api/v1/events').set('Authorization','Bearer '+b.token).send({event_id:'x',match_id:'match-m1',type:'score',payload:{team_id:'club-alpha-1',event_type:'goal'}});expect(denied.status).toBe(403);
  });
  test('organizer cannot mutate a different league in the same tenant',async()=>{
    await transaction(s=>{s.matches.other={sport_id:'football',league_id:'league-alpha-2',home:'a',away:'b',events:[]};});
    const b=await login('organizer@nascon.test');const r=await request(app).post('/api/v1/events').set('Authorization','Bearer '+b.token).send({event_id:'x',match_id:'other',type:'match_start',payload:{}});expect(r.status).toBe(403);
  });
  test('a second manager is authorized by account identity rather than first matching role',async()=>{
    const organizer=await login('organizer@nascon.test');
    await request(app).post('/api/v1/demo/prepare').set('Authorization','Bearer '+organizer.token).send({});
    await transaction(s=>{const manager=s.users.find(u=>u.user_id==='user-manager');s.users.push({...manager,user_id:'user-manager-two',email:'manager-two@nascon.test',club_id:'club-alpha-2'});});
    const b=await login('manager-two@nascon.test');
    const body={request_id:'second-manager-release',league_id:'demo-alpha-cup',player_id:'demo-omar',type:'release',from_squad_id:'demo-titans',to_squad_id:null,effective_date:'2026-10-18',loan_end_date:null,reason:'test',requested_by:'user-manager-two'};
    const result=await request(app).post('/api/v1/roster/transfer').set('Authorization','Bearer '+b.token).send(body);
    expect(result.status).toBe(409);expect(result.body.error.code).toBe('MIN_POSITION_VIOLATION');
    const foreign=await request(app).post('/api/v1/roster/transfer').set('Authorization','Bearer '+b.token).send({...body,from_squad_id:'demo-falcons',player_id:'demo-bilal'});expect(foreign.status).toBe(403);
  });
  test('session tokens verify in a fresh process and reject tampering/expiration',()=>{
    const token=sessionToken({...identities().users[0],club_id:null});
    const code="const {authenticateSession}=require('./backend/src/domain/rbac');process.exit(authenticateSession('Bearer '+process.env.CHECK_TOKEN).sub==='user-admin'?0:1)";
    expect(spawnSync(process.execPath,['-e',code],{cwd:require('path').join(__dirname,'..'),env:{...process.env,CHECK_TOKEN:token}}).status).toBe(0);
    expect(()=>require('../backend/src/domain/rbac').authenticateSession('Bearer '+token.slice(0,-5)+'xxxxx')).toThrow();
    const expired=jwt.sign({sub:'user-admin',user_id:'user-admin',purpose:'member_session',role:'super_admin',tenant_id:'tenant-alpha',exp:1},process.env.JWT_SECRET);
    expect(()=>require('../backend/src/domain/rbac').authenticateSession('Bearer '+expired)).toThrow();
  });
  test('environment-derived fallback signing key survives independent process restarts',()=>{
    const cwd=require('path').join(__dirname,'..'),env={...process.env,JWT_SECRET:''};
    const signed=spawnSync(process.execPath,['-e',"const r=require('./backend/src/domain/rbac');process.stdout.write(r.sessionToken({user_id:'u',role:'viewer',tenant_id:'tenant-beta'}))"],{cwd,env,encoding:'utf8'});
    expect(signed.status).toBe(0);
    const verified=spawnSync(process.execPath,['-e',"const r=require('./backend/src/domain/rbac');process.exit(r.authenticateSession('Bearer '+process.env.CHECK_TOKEN).sub==='u'?0:1)"],{cwd,env:{...env,CHECK_TOKEN:signed.stdout}});
    expect(verified.status).toBe(0);
  });
  test('relational uniqueness prevents concurrent duplicate active stream keys',async()=>{
    const results=await Promise.allSettled([1,2].map(n=>pool.query("INSERT INTO day2_stream_keys(stream_key,player_id,status) VALUES($1,'player-alpha-1','active')",['sk-'+n])));
    expect(results.filter(r=>r.status==='fulfilled')).toHaveLength(1);expect(results.find(r=>r.status==='rejected').reason.code).toBe('23505');
  });
  test('reset does not reuse outbox IDs retained by BullMQ',async()=>{
    const before=(await pool.query("INSERT INTO domain_events(topic,payload) VALUES('test','{}') RETURNING id")).rows[0].id;
    await reset();
    const after=(await pool.query("INSERT INTO domain_events(topic,payload) VALUES('test','{}') RETURNING id")).rows[0].id;
    expect(Number(after)).toBeGreaterThan(Number(before));
  });
});
