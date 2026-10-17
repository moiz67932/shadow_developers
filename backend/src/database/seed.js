const { getState, reset,transaction,baseline } = require('../services/state');
async function seed(){const existing=await getState();if(!existing)await reset();else if(!existing.users)await transaction(s=>{const base=baseline(s.seed);for(const key of ['users','tenants','clubs','players','products','discounts'])s[key]=base[key];for(const [id,league] of Object.entries(base.leagues))if(!Object.hasOwn(s.leagues,id))s.leagues[id]=league;});}
if(require.main===module) seed().then(()=>process.exit(0)).catch((e)=>{console.error(e.message);process.exit(1);});
module.exports={seed};
