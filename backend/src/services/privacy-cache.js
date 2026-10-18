const Redis=require('ioredis');
async function eraseKeys(keys){if(!keys.length)return;
  const url=process.env.RUN_INTEGRATION==='1'?process.env.TEST_REDIS_URL:require('../config').redisUrl;
  if(!url)throw new Error('Private cache erasure requires configured Redis.');
  const redis=new Redis(url,{connectTimeout:300,commandTimeout:300,maxRetriesPerRequest:0,retryStrategy:()=>null});redis.on('error',()=>{});
  try{await redis.del(...keys);}finally{redis.disconnect();}
}
module.exports={eraseKeys};
