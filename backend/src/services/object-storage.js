const crypto=require('crypto');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const hmac=(key,b)=>crypto.createHmac('sha256',key).update(b).digest();
async function object(method,key,body=Buffer.alloc(0)){
 const endpoint=process.env.MINIO_URL||'http://minio:9000',url=new URL(endpoint),bucket='nascon-vods',uri='/'+bucket+(key?'/'+key.split('/').map(encodeURIComponent).join('/'):'');
 const date=new Date().toISOString().replace(/[:-]|\.\d{3}/g,''),day=date.slice(0,8),payload=hash(body),scope=day+'/us-east-1/s3/aws4_request';
 const headers={'host':url.host,'x-amz-content-sha256':payload,'x-amz-date':date},signed=Object.keys(headers).sort().join(';'),canonical=method+'\n'+uri+'\n\n'+Object.keys(headers).sort().map(k=>k+':'+headers[k]+'\n').join('')+'\n'+signed+'\n'+payload;
 const keyDate=hmac('AWS4'+(process.env.MINIO_ROOT_PASSWORD||'change-this-minio-password'),day),signing=hmac(hmac(hmac(keyDate,'us-east-1'),'s3'),'aws4_request');
 const signature=crypto.createHmac('sha256',signing).update('AWS4-HMAC-SHA256\n'+date+'\n'+scope+'\n'+hash(canonical)).digest('hex');
 headers.Authorization='AWS4-HMAC-SHA256 Credential='+(process.env.MINIO_ROOT_USER||'nasconminio')+'/'+scope+', SignedHeaders='+signed+', Signature='+signature;
 return fetch(endpoint+uri,{method,headers,body:['PUT','POST'].includes(method)?body:undefined,signal:AbortSignal.timeout(10000)});
}
async function put(key,data){const bucket=await object('PUT','');if(!bucket.ok&&bucket.status!==409)throw Error('VOD bucket unavailable.');const r=await object('PUT',key,data);if(!r.ok)throw Error('VOD upload failed: '+r.status);}
module.exports={put,get:key=>object('GET',key),remove:key=>object('DELETE',key)};
