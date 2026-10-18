// Generates only the application's JWT secret, never organizer-issued salts.
const fs=require('fs'),crypto=require('crypto'),cp=require('child_process');
if(cp.spawnSync('git',['check-ignore','-q','.env']).status!==0)throw Error('Credential file is not ignored.');
const existing=fs.existsSync('.env')?fs.readFileSync('.env','utf8'):'';
const values=require('node:util').parseEnv(existing);
if(values.JWT_SECRET&&values.JWT_SECRET.length<32)throw Error('Existing JWT_SECRET is too short.');
if(!values.JWT_SECRET){const fd=fs.openSync('.env','a',0o600);fs.fchmodSync(fd,0o600);fs.writeSync(fd,(existing&&!existing.endsWith('\n')?'\n':'')+'JWT_SECRET='+crypto.randomBytes(48).toString('hex')+'\n');fs.closeSync(fd);}
fs.chmodSync('.env',0o600);
console.log('Private JWT secret configured without displaying its value. Organizer salts were not generated or substituted.');
