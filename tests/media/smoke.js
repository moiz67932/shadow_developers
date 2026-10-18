// Bounded real-media diagnostic. Uses internal service auth, NOT an organizer-salt substitute.
const cp=require('child_process'),crypto=require('crypto'),fs=require('fs');
async function main(){
 const info=JSON.parse(cp.execFileSync('docker',['inspect','shadow_developers-api-1'],{encoding:'utf8'}))[0],vars=Object.fromEntries(info.Config.Env.map(v=>{const i=v.indexOf('=');return[v.slice(0,i),v.slice(i+1)]}));
 const secret=crypto.createHmac('sha256',vars.DAY1_SALT).update('nascon/media/internal/v1').digest('hex'),name='qa-'+crypto.randomUUID(),file='/tmp/'+name+'.mp4',path='costream/'+name;
 const destination='rtmp://mediamtx:1935/'+path+'?user=internal&pass='+secret;
 const args=['exec','shadow_developers-api-1','ffmpeg','-hide_banner','-loglevel','error','-re','-f','lavfi','-i','testsrc=size=320x180:rate=20','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','14','-map','0:v','-map','1:a','-c:v','libx264','-preset','ultrafast','-tune','zerolatency','-threads','2','-pix_fmt','yuv420p','-g','20','-c:a','aac','-flags','+global_header','-f','tee',`[f=flv]${destination}|[f=mp4]${file}`];
 const producer=cp.spawn('docker',args,{stdio:['ignore','ignore','pipe']});let error='';producer.stderr.on('data',d=>error+=d.toString());
 const done=new Promise(resolve=>producer.once('exit',code=>resolve(code)));let segmentBytes=0,manifest=false;
 const webRtc=cp.spawn('python3',['tests/media/webrtc_browser.py',path],{stdio:['ignore','pipe','pipe']});let rtcOut='';webRtc.stdout.on('data',d=>rtcOut+=d.toString());const rtcDone=new Promise(resolve=>webRtc.once('exit',code=>resolve(code)));
 try{
  const deadline=Date.now()+12000;
  while(Date.now()<deadline){try{let url='http://localhost:8888/'+path+'/index.m3u8',r=await fetch(url,{signal:AbortSignal.timeout(1000)});if(r.ok){let text=await r.text();for(let i=0;i<3;i++){const nested=text.split('\n').find(l=>l.trim()&&!l.startsWith('#')&&l.includes('.m3u8'));if(!nested)break;url=new URL(nested,url).href;r=await fetch(url);text=await r.text();}manifest=text.startsWith('#EXTM3U');const segment=text.split('\n').find(l=>l.trim()&&!l.startsWith('#')&&!l.includes('.m3u8'));if(segment){const data=await fetch(new URL(segment,url));segmentBytes=(await data.arrayBuffer()).byteLength;if(segmentBytes>100)break;}}}catch{}await new Promise(r=>setTimeout(r,250));}
  const exit=await done;if(exit!==0)throw Error('Synthetic publishing failed: '+error.replaceAll(secret,'[REDACTED]'));
  if(!manifest||segmentBytes<100)throw Error('HLS manifest/segment verification failed.');
  const upload=cp.spawnSync('docker',['exec','shadow_developers-api-1','node','-e',`const fs=require('fs'),s=require('./src/services/object-storage');(async()=>{const b=fs.readFileSync(${JSON.stringify(file)});await s.put(${JSON.stringify(name+'.mp4')},b);const r=await s.get(${JSON.stringify(name+'.mp4')});if(!r.ok||Buffer.from(await r.arrayBuffer()).length!==b.length)throw Error('Stored recording differs');await s.remove(${JSON.stringify(name+'.mp4')});console.log(JSON.stringify({vodStoredAndRetrieved:true,bytes:b.length}));})().catch(e=>{console.error(e.message);process.exitCode=1;});`],{encoding:'utf8'});if(upload.status!==0)throw Error(upload.stderr);
  const layouts={};for(const [layout,count] of [['grid_2',2],['grid_4',4],['spotlight',4]]){const inputs=Array.from({length:count},()=>['-i',file]).flat(),graph=require('../../backend/src/services/media-sessions').filterGraph(count,layout),r=cp.spawnSync('docker',['exec','shadow_developers-api-1','ffmpeg','-hide_banner','-loglevel','error',...inputs,'-filter_complex',graph,'-map','[vout]','-map','[aout]','-t','1','-f','null','-'],{encoding:'utf8',timeout:10000});layouts[layout]=r.status===0;if(r.status!==0)layouts[layout+'_error']=r.stderr;}
  const rtcExit=await rtcDone;let webRtcPlayback={decoded:false};try{if(rtcExit===0)webRtcPlayback=JSON.parse(rtcOut);}catch{}
  const report={measured_at:new Date().toISOString(),actualRtmpPublished:true,hlsManifest:manifest,hlsSegmentBytes:segmentBytes,webRtcPlayback,vod:JSON.parse(upload.stdout),layouts,officialKeyLifecycleAtMediaBoundary:'not verified: official DAY2_SALT required'};
  fs.writeFileSync('tests/reports/media-smoke.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
  cp.spawnSync('docker',['exec','shadow_developers-api-1','node','-e',`require('fs').unlinkSync(${JSON.stringify(file)})`]);
 }finally{if(producer.exitCode===null)producer.kill('SIGTERM');}
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
