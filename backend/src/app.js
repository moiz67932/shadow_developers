const express = require('express');
const audit = require('./routes/audit');
const pool = require('./database/pool');
const { getState } = require('./services/state');
const { errorHandler, requestLog } = require('./middleware/http');
function createApp() {
  const app=express(); app.disable('x-powered-by'); app.use(requestLog); app.use(express.json({limit:'1mb'}));
  app.get('/health', async (req,res,next)=>{try{await pool.query('SELECT 1');if(!await getState()) return res.status(503).json({error:{code:'NOT_READY',message:'Baseline data is not seeded.',details:{}}});res.json({status:'ok'});}catch(e){next(e);}});
  app.use('/audit/v1',audit);
  const hits=new Map(); app.get('/api/v1/ping',(req,res)=>{const key=req.ip,now=Date.now(),recent=(hits.get(key)||[]).filter(t=>t>now-10000);if(recent.length>=100){res.set('Retry-After','10');return res.status(429).json({error:{code:'RATE_LIMITED',message:'Rate limit exceeded.',details:{}}});}recent.push(now);hits.set(key,recent);res.json({pong:true});});
  app.use((req,res)=>res.status(404).json({error:{code:'NOT_FOUND',message:'Route not found.',details:{}}})); app.use(errorHandler); return app;
}
module.exports={createApp};
