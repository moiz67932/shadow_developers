const pool=require('../database/pool');
async function tx(key,fn){const c=await pool.connect();try{await c.query('BEGIN');await c.query('SELECT pg_advisory_xact_lock_shared(754201)');if(key)await c.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['day2:'+key]);const result=await fn(c);await c.query('COMMIT');return result;}catch(e){await c.query('ROLLBACK');throw e;}finally{c.release();}}
function players(s){return [...(s.players??[]),...Object.values(s.rosters??{}).flatMap(r=>r.players??[])];}
module.exports={tx,players};
