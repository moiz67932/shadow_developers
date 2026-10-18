// Public read-only inspection snapshots only. Writes/real scans never use this cache.
const entries=new Map(),pending=new Map();let generation=0;
function invalidate(){generation++;entries.clear();pending.clear();}
async function inspect(id,read){const existing=entries.get(id);if(existing&&existing.expires>Date.now())return existing.value;if(pending.has(id))return pending.get(id);const started=generation,promise=read().then(value=>{if(started===generation){if(entries.size>=1000)entries.delete(entries.keys().next().value);entries.set(id,{value,expires:Date.now()+25});}return value;}).finally(()=>{if(pending.get(id)===promise)pending.delete(id);});pending.set(id,promise);return promise;}
module.exports={inspect,invalidate};
