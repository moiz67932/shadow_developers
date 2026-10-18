// Read-only acceptance probes. Only the generated evidence report is written.
const fs=require('fs');
const cp=require('child_process');
const crypto=require('crypto');
function inspect(name){return JSON.parse(cp.execFileSync('docker',['inspect',name],{encoding:'utf8'}))[0];}
async function json(url,options){const r=await fetch(url,{...options,signal:AbortSignal.timeout(5000)});return {status:r.status,body:await r.json()};}
async function main(){
  const api='http://127.0.0.1:18080';
  const health=await json(api+'/health');
  const login=await json(api+'/api/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'manager@nascon.test',password:'Nascon@26'})});
  const claims=JSON.parse(Buffer.from(login.body.token.split('.')[1],'base64url'));
  const guest=[];
  for(const path of ['/api/v1/chat','/audit/v1/checkout','/audit/v1/events']){
    const r=await json(api+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer guest_anon_tok_7721'},body:'{}'});
    guest.push({path,status:r.status,code:r.body.error?.code});
  }
  const grid=await json('http://localhost:4444/status');
  const names=['api','worker','db','redis','minio','mediamtx','selenium-hub','node-chrome-1','node-chrome-2','node-firefox'];
  const services=Object.fromEntries(names.map(name=>{const c=inspect('shadow_developers-'+name+'-1');return [name,{running:c.State.Running,health:c.State.Health?.Status??'not-configured-in-existing-container'}];}));
  const worker=inspect('nascon-phase1-worker-test');
  const legacy=inspect('shadow_developers-api-1');
  const backend=JSON.parse(fs.readFileSync('tests/reports/phase1-backend.json','utf8'));
  const artifact=fs.readFileSync('infrastructure/vendor/minio-linux-amd64.gz');
  const changed=[...new Set((cp.execFileSync('git',['diff','--name-only','23f265b2c7c5badb7501969ee523006db5d7a0a3'],{encoding:'utf8'})+'\n'+cp.execFileSync('git',['ls-files','--others','--exclude-standard'],{encoding:'utf8'})).split('\n'))].filter(p=>/^(backend\/|infrastructure\/|scripts\/|tests\/|docs\/PHASE1_FOUNDATION\.md$|docker-compose\.yml$|README\.md$|TRACEABILITY\.md$|ADR\.md$|\.env\.example$|\.gitignore$)/.test(p));
  const report={timestamp:new Date().toISOString(),backend:{passed:backend.numPassedTests,failed:backend.numFailedTests,skipped:backend.numPendingTests,success:backend.success},strictIsolatedApiHealth:health,accountIdentity:{status:login.status,sub:claims.sub,role:claims.role,tenant_id:claims.tenant_id,club_id:claims.club_id,player_id:claims.player_id},guestWrites:guest,grid:{ready:grid.body.value.ready,nodes:grid.body.value.nodes.map(n=>({version:n.version,availability:n.availability,browser:n.slots[0].stereotype.browserName}))},services,newIsolatedWorkerHealth:worker.State.Health?.Status,officialDay2AvailableInLiveApi:legacy.Config.Env.some(e=>e.startsWith('DAY2_SALT=')&&e.length>'DAY2_SALT='.length),productionApiRolloutComplete:false,coldFullStackStartupVerified:false,minioArtifact:{bytes:artifact.length,sha256:crypto.createHash('sha256').update(artifact).digest('hex')},changedFiles:changed};
  fs.writeFileSync('tests/reports/phase1-infrastructure.json',JSON.stringify(report,null,2)+'\n');
  const ok=backend.success&&health.status===200&&claims.sub==='user-manager'&&claims.club_id==='club-alpha-1'&&guest.every(r=>r.status===403&&r.code==='GUEST_WRITE_FORBIDDEN')&&report.grid.ready&&report.grid.nodes.filter(n=>n.availability==='UP').length>=3&&report.newIsolatedWorkerHealth==='healthy';
  console.log(JSON.stringify({acceptanceProbesPassed:ok,backend:report.backend,strictHealth:health.status,guestDenials:guest.length,gridNodes:report.grid.nodes.length,worker:report.newIsolatedWorkerHealth,productionRollout:false}));
  if(!ok)process.exitCode=1;
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
