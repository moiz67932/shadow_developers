const crypto=require('crypto');
const roles=['super_admin','league_organizer','club_manager','player','viewer'];
const emails=['admin','organizer','manager','player','viewer'];
function identities(){return {tenants:[{tenant_id:'tenant-alpha',name:'Alpha Sports'},{tenant_id:'tenant-beta',name:'Beta Athletics'}],clubs:['alpha-1','alpha-2','beta-1'].map(x=>({club_id:`club-${x}`,tenant_id:`tenant-${x.split('-')[0]}`,name:`Club ${x}`})),players:['alpha-1','alpha-2','beta-1'].map(x=>({player_id:`player-${x}`,club_id:`club-${x}`,tenant_id:`tenant-${x.split('-')[0]}`})),users:roles.map((role,i)=>({user_id:`user-${emails[i]}`,email:`${emails[i]}@nascon.test`,role,tenant_id:'tenant-alpha',password_hash:crypto.scryptSync('Nascon@26',`nascon-demo-${role}`,32).toString('hex'),password_salt:`nascon-demo-${role}`}))};}
function verify(user,password){const value=crypto.scryptSync(password,user.password_salt,32);return crypto.timingSafeEqual(value,Buffer.from(user.password_hash,'hex'));}
module.exports={identities,verify};
