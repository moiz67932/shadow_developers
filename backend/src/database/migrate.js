const fs = require('fs');
const path = require('path');
const pool = require('./pool');

async function migrate() {
  await pool.query('CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())');
  const dir = path.join(__dirname, 'migrations');
  for (const name of fs.readdirSync(dir).filter((x) => x.endsWith('.sql')).sort()) {
    const seen = await pool.query('SELECT 1 FROM schema_migrations WHERE name = $1', [name]);
    if (!seen.rowCount) {
      await pool.query('BEGIN');
      try { await pool.query(fs.readFileSync(path.join(dir, name), 'utf8')); await pool.query('INSERT INTO schema_migrations(name) VALUES($1)', [name]); await pool.query('COMMIT'); }
      catch (error) { await pool.query('ROLLBACK'); throw error; }
    }
  }
}
if (require.main === module) migrate().then(() => pool.end()).catch((e) => { console.error(e.message); process.exit(1); });
module.exports = { migrate };
