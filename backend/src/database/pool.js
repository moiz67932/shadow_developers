const { Pool } = require('pg');
const config = require('../config');
const pool = new Pool({ connectionString: config.databaseUrl, max: 20 });
module.exports = pool;
