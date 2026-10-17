const { createApp }=require('./app'); const { migrate }=require('./database/migrate'); const { seed }=require('./database/seed'); const config=require('./config'); const pool=require('./database/pool');
async function main(){await migrate();await seed();const server=createApp().listen(config.port,()=>console.log(`API listening on ${config.port}`));const shutdown=()=>server.close(()=>pool.end().finally(()=>process.exit(0)));process.on('SIGTERM',shutdown);process.on('SIGINT',shutdown);}
main().catch((e)=>{console.error(e.stack||e.message);process.exit(1);});
