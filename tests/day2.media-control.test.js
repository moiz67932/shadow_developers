const http=require('http');
const {invalidate}=require('../backend/src/services/media-control');
describe('MediaMTX active publisher invalidation',()=>{
 let server,requests,mode;
 beforeAll(async()=>{server=http.createServer((req,res)=>{
   requests.push(req.method+' '+req.url);
   if(mode==='unavailable'){res.writeHead(503);return res.end();}
   if(req.url.endsWith('/list')){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({items:[{id:'old-publisher',path:'live/old-key'},{id:'unrelated',path:'live/other-key'}]}));}
   res.writeHead(mode==='gone'?404:mode==='kick-failed'?503:200);res.end();
 });await new Promise(r=>server.listen(0,'127.0.0.1',r));});
 beforeEach(()=>{requests=[];mode='ok';process.env.MEDIA_API_URL=`http://127.0.0.1:${server.address().port}`;});
 afterAll(async()=>{delete process.env.MEDIA_API_URL;await new Promise(r=>server.close(r));});
 test('kicks matching RTMP and WebRTC publishers without touching unrelated keys',async()=>{await invalidate(['old-key']);expect(requests.filter(x=>x.startsWith('POST'))).toEqual(expect.arrayContaining(['POST /v3/rtmpconns/kick/old-publisher','POST /v3/webrtcsessions/kick/old-publisher']));expect(requests).toHaveLength(4);});
 test('already disconnected publishers are harmless',async()=>{mode='gone';await expect(invalidate(['old-key'])).resolves.toBeUndefined();});
 test('unavailable control service fails explicitly',async()=>{mode='unavailable';await expect(invalidate(['old-key'])).rejects.toThrow('unavailable');});
 test('failed publisher termination is not reported as success',async()=>{mode='kick-failed';await expect(invalidate(['old-key'])).rejects.toThrow('invalidation failed');});
 test('empty invalidation is a no-op',async()=>{await invalidate([]);expect(requests).toHaveLength(0);});
});
