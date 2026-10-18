const express = require('express');
const audit = require('./routes/audit');
const pool = require('./database/pool');
const { getState } = require('./services/state');
const { errorHandler, requestLog } = require('./middleware/http');
function createApp() {
  const app=express(); app.disable('x-powered-by'); app.use(requestLog); app.use(express.json({limit:'1mb'}));
  app.use(require('./middleware/guest').guestWriteGuard);
  app.use(require('./routes/day2'));
  app.use(require('./routes/guest-web'));
  app.get('/health', async (req,res,next)=>{try{await pool.query('SELECT 1');const s=await getState(),config=require('./config');if(!s||config.strictReadiness&&(!s.matches['match-m1']||!config.day2Salt))return res.status(503).json({error:{code:'NOT_READY',message:'Baseline data or DAY2_SALT is not configured.',details:{}}});await require('./services/readiness').checkDependencies();res.json({status:'ok'});}catch(e){res.status(503).json({error:{code:'NOT_READY',message:'A required dependency is unavailable.',details:{}}});}});
  app.use('/audit/v1',audit);
  const hits=new Map(); app.use('/api/v1',(req,res,next)=>{const key=req.ip,now=Date.now(),recent=(hits.get(key)||[]).filter(t=>t>now-10000);if(recent.length>=100){res.set('Retry-After',String(Math.max(1,Math.ceil((recent[0]+10000-now)/1000))));return res.status(429).json({error:{code:'RATE_LIMITED',message:'Rate limit exceeded.',details:{}}});}recent.push(now);hits.set(key,recent);next();});
  app.get('/api/v1/ping',(req,res)=>res.json({pong:true}));
  app.use('/api/v1',(req,res,next)=>{if(['GET','HEAD','OPTIONS'].includes(req.method)||req.path==='/login')return next();try{require('./domain/rbac').authenticateSession(req.headers.authorization);next();}catch(e){next(e);}});
  app.use('/api/v1',require('./routes/mobile'));
  app.use((req,res)=>res.status(404).json({error:{code:'NOT_FOUND',message:'Route not found.',details:{}}})); app.use(errorHandler); return app;
}
module.exports={createApp};
