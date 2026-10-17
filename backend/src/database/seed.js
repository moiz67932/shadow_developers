const { getState, reset } = require('../services/state');
async function seed(){if(!await getState()) await reset();}
if(require.main===module) seed().then(()=>process.exit(0)).catch((e)=>{console.error(e.message);process.exit(1);});
module.exports={seed};
