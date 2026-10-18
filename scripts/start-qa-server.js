// Explicitly isolated browser/load fixture; never use this entry point for production.
const cp=require('child_process');
const db=JSON.parse(cp.execFileSync('docker',['inspect','shadow_developers-db-1'],{encoding:'utf8'}))[0];
const ip=Object.values(db.NetworkSettings.Networks)[0].IPAddress;
Object.assign(process.env,{DATABASE_URL:`postgresql://nascon:nascon@${ip}:5432/nascon_test`,DAY1_SALT:'isolated-qa-only',DAY2_SALT:'isolated-qa-only',JWT_SECRET:'isolated-qa-only',STRICT_READINESS:'0',PORT:'18080'});
require('../backend/src/server');
