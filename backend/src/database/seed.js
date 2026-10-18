const { getState, reset,transaction,baseline } = require('../services/state');
const pool=require('./pool');
async function seed(){const existing=await getState();if(!existing)await reset();else {
  // Upgrade missing seed records only: never reset existing matches, carts or inventory.
  await transaction(s=>{if(!s.users){const base=baseline(s.seed);for(const key of ['users','tenants','clubs','players','products','discounts'])s[key]=base[key];for(const [id,league] of Object.entries(base.leagues))if(!Object.hasOwn(s.leagues,id))s.leagues[id]=league;}require('../services/phase2').extendState(s);});
  await require('../services/phase2').seedTables(pool);
}}
if(require.main===module) seed().then(()=>process.exit(0)).catch((e)=>{console.error(e.message);process.exit(1);});
module.exports={seed};
