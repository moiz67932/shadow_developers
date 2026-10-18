const request=require('../backend/node_modules/supertest');
const {WebSocket}=require('../backend/node_modules/ws');
const {createApp}=require('../backend/src/app');
const realtime=require('../backend/src/services/realtime');
const pool=require('../backend/src/database/pool');
const integration=process.env.RUN_INTEGRATION==='1'?describe:describe.skip;
integration('Real score WebSocket propagation',()=>{
 beforeAll(async()=>{await require('../backend/src/database/migrate').migrate();await require('../backend/src/services/state').reset();});afterAll(()=>pool.end());
 test('25 real clients receive committed score within p95 500 ms; replay does not mutate state',async()=>{
   const app=createApp(),server=app.listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));realtime.attach(server);
   const clients=[],address=`ws://127.0.0.1:${server.address().port}/ws/matches/match-m1`;
   try{
     await Promise.all(Array.from({length:25},()=>new Promise((resolve,reject)=>{const ws=new WebSocket(address);clients.push(ws);ws.once('error',reject);ws.once('message',()=>resolve());})));
     expect(realtime.connectionCount()).toBe(25);const start=Date.now();
     const deliveries=clients.map(ws=>new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('Score delivery timeout')),1000);ws.once('message',buffer=>{clearTimeout(timer);const score=JSON.parse(buffer.toString());expect(score.home.score).toBe(2);resolve(Date.now()-start);});}));
     const event={event_id:'realtime-goal',match_id:'match-m1',type:'score',payload:{team_id:'club-alpha-1',event_type:'goal'}};
     const response=await request(app).post('/audit/v1/events').send(event);expect(response.status).toBe(201);
     const latency=(await Promise.all(deliveries)).sort((a,b)=>a-b),p95=latency[Math.ceil(latency.length*.95)-1];expect(p95).toBeLessThan(500);
     require('fs').writeFileSync(require('path').join(__dirname,'reports/phase2-realtime.json'),JSON.stringify({measured_at:new Date().toISOString(),realtimeClients:25,p95_ms:p95,max_ms:latency.at(-1)},null,2)+'\n');
     expect((await request(app).post('/audit/v1/events').send(event)).status).toBe(200);
   }finally{await Promise.all(clients.map(ws=>new Promise(r=>{if(ws.readyState===WebSocket.CLOSED)return r();ws.once('close',r);ws.close();})));await new Promise(r=>server.close(r));}
   expect(realtime.connectionCount()).toBe(0);
 });
});
