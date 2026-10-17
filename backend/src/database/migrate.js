const fs = require('fs');
const path = require('path');
const pool = require('./pool');

async function migrate() {
  const client=await pool.connect();
  try {
  await client.query('SELECT pg_advisory_lock(754202)');
  await client.query('CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())');
  const dir = path.join(__dirname, 'migrations');
  for (const name of fs.readdirSync(dir).filter((x) => x.endsWith('.sql')).sort()) {
    const seen = await client.query('SELECT 1 FROM schema_migrations WHERE name = $1', [name]);
    if (!seen.rowCount) {
      await client.query('BEGIN');
      try { await client.query(fs.readFileSync(path.join(dir, name), 'utf8')); await client.query('INSERT INTO schema_migrations(name) VALUES($1)', [name]); await client.query('COMMIT'); }
      catch (error) { await client.query('ROLLBACK'); throw error; }
    }
  }
  }finally {await client.query('SELECT pg_advisory_unlock(754202)');client.release();}
}
if (require.main === module) migrate().then(() => pool.end()).catch((e) => { console.error(e.message); process.exit(1); });
module.exports = { migrate };
