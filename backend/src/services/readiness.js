const Redis=require('ioredis');
const config=require('../config');
let redis;
async function checkDependencies(){
  if(!config.strictReadiness)return;
  if(!redis){redis=new Redis(config.redisUrl,{lazyConnect:true,connectTimeout:800,commandTimeout:800,maxRetriesPerRequest:0,retryStrategy:()=>1000});redis.on('error',()=>{});}
  const checks=[redis.ping()];
  for(const url of [config.mediaHealthUrl,config.minioHealthUrl]){
    if(!url)throw new Error('Readiness dependency is not configured.');
    checks.push(fetch(url,{signal:AbortSignal.timeout(1000)}).then(r=>{if(!r.ok)throw new Error('Dependency unavailable.');}));
  }
  await Promise.all(checks);
}
module.exports={checkDependencies};
