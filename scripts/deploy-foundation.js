// Deploy only API/worker; never recreate DB/Redis, remove volumes or reset data.
const cp=require('child_process');
const fs=require('fs');
function command(args,env){const result=cp.spawnSync('docker',args,{env,stdio:'inherit'});if(result.status!==0)throw new Error('Docker operation failed.');}
async function main(){
  const previous=JSON.parse(cp.execFileSync('docker',['inspect','shadow_developers-api-1'],{encoding:'utf8'}))[0];
  const current=Object.fromEntries(previous.Config.Env.map(e=>{const i=e.indexOf('=');return [e.slice(0,i),e.slice(i+1)];}));
  const local=fs.existsSync('.env')?require('node:util').parseEnv(fs.readFileSync('.env','utf8')):{};
  const env={...process.env,...local,DAY1_SALT:current.DAY1_SALT};
  if(!env.DAY2_SALT)throw new Error('Official DAY2_SALT is absent; deployment was not attempted.');
  const config=JSON.parse(cp.execFileSync('docker',['compose','config','--format','json'],{env,encoding:'utf8'}));
  if(config.services.api.environment.DAY2_SALT!==env.DAY2_SALT)throw new Error('Credential forwarding verification failed.');
  console.log('Both salt variables are present; values are not displayed.');
  command(['compose','up','-d','--no-deps','--build','api','worker'],env);
  const deadline=Date.now()+110000;
  while(Date.now()<deadline){try{const r=await fetch('http://localhost:8080/health',{signal:AbortSignal.timeout(2000)});if(r.ok){console.log('Corrected API ready on port 8080. DB/Redis containers were preserved.');return;}}catch{}await new Promise(r=>setTimeout(r,1000));}
  throw new Error('API did not become ready within the verification deadline.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
