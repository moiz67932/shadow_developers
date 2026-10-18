const pool=require('../database/pool');
async function transaction(key,fn){const c=await pool.connect();try{await c.query('BEGIN');await c.query('SELECT pg_advisory_xact_lock_shared(754201)');if(key)await c.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',['day2:'+key]);const result=await fn(c);await c.query('COMMIT');return result;}catch(e){await c.query('ROLLBACK');throw e;}finally{c.release();}}
// Queue same-entity work BEFORE borrowing a connection. PostgreSQL advisory locks
// remain the cross-process authority; one busy chat room cannot exhaust the pool.
const lanes=new Map();
function tx(key,fn){if(!key)return transaction(key,fn);const previous=lanes.get(key)??Promise.resolve(),run=previous.catch(()=>{}).then(()=>transaction(key,fn));lanes.set(key,run);const clean=()=>{if(lanes.get(key)===run)lanes.delete(key);};run.then(clean,clean);return run;}
function players(s){return [...(s.players??[]),...Object.values(s.rosters??{}).flatMap(r=>r.players??[])];}
module.exports={tx,players};
