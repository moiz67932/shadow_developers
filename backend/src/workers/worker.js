const {Worker,Queue}=require('bullmq');
const fs=require('fs');
const Redis=require('ioredis');const config=require('../config');const pool=require('../database/pool');
const connection=new Redis(config.redisUrl,{maxRetriesPerRequest:null});
const queue=new Queue('domain-events',{connection});
const worker=new Worker('domain-events',async job=>{console.log(JSON.stringify({event:'processed',id:job.id,type:job.name}));},{connection});
let stopped=false,timer;
async function publish(){if(stopped)return;try{const result=await pool.query('SELECT id,topic,payload FROM domain_events WHERE published_at IS NULL ORDER BY id LIMIT 100');await connection.ping();for(const row of result.rows){await queue.add(row.topic,row.payload,{jobId:'event-'+row.id,removeOnComplete:1000,removeOnFail:1000});await pool.query('UPDATE domain_events SET published_at=now() WHERE id=$1',[row.id]);}fs.writeFileSync('/tmp/nascon-worker-heartbeat',String(Date.now()));}catch(e){console.error(JSON.stringify({event:'outbox_retry',message:e.message}));}finally{if(!stopped)timer=setTimeout(publish,1000);}}
worker.on('error',e=>console.error(e.message));publish();
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,async()=>{stopped=true;clearTimeout(timer);await worker.close();await queue.close();await connection.quit();await pool.end();process.exit(0);});
