const { Worker }=require('bullmq'); const config=require('../config');
const connection={url:config.redisUrl}; const worker=new Worker('domain-events',async job=>{console.log(JSON.stringify({event:'processed',id:job.id,type:job.name}));},{connection});
worker.on('error',e=>console.error(e.message)); ['SIGTERM','SIGINT'].forEach(s=>process.on(s,()=>worker.close().finally(()=>process.exit(0))));
