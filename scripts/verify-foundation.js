// Read-only live checks: no reset, cart mutation, media key rotation or secrets output.
const cp=require('child_process');
const fs=require('fs');
async function main(){
 const containers=JSON.parse(cp.execFileSync('docker',['inspect','shadow_developers-api-1','shadow_developers-db-1','shadow_developers-redis-1'],{encoding:'utf8'}));
 const configured=containers[0].Config.Env.some(x=>x.startsWith('DAY2_SALT=')&&x.length>'DAY2_SALT='.length);
 const health=await fetch('http://localhost:8080/health');
 const login=await fetch('http://localhost:8080/api/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'manager@nascon.test',password:'Nascon@26'})});
 const body=await login.json();if(login.status!==200)throw Error('Live account login failed.');
 const claims=JSON.parse(Buffer.from(body.token.split('.')[1],'base64url'));
 if(claims.purpose!=='member_session'||claims.sub!=='user-manager'||claims.user_id!==claims.sub||claims.club_id!=='club-alpha-1'||claims.tenant_id!=='tenant-alpha')throw Error('Live account identity/ownership claims are incorrect.');
 const guestWrites=[];
 for(const route of ['/api/v1/chat','/audit/v1/checkout','/audit/v1/events']){
  const response=await fetch('http://localhost:8080'+route,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer guest_anon_tok_7721'},body:'{}'}),data=await response.json();
  if(response.status!==403||data.error?.code!=='GUEST_WRITE_FORBIDDEN')throw Error('Guest boundary failed: '+route);
  guestWrites.push({route,status:response.status,code:data.error.code});
 }
 const orders=cp.execFileSync('docker',['exec','shadow_developers-db-1','psql','-U','nascon','-d','nascon','-Atc','SELECT order_id,md5(response::text) FROM orders ORDER BY order_id'],{encoding:'utf8'}).trim().split('\n');
 const report={measured_at:new Date().toISOString(),healthStatus:health.status,officialSaltPresentInAPI:configured,accountClaimsVerified:true,guestWrites,postgresContainer:containers[1].Id,redisContainer:containers[2].Id,orders};
 fs.writeFileSync('tests/reports/phase2-live-foundation.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
