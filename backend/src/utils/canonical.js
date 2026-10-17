const crypto = require('crypto');
const config = require('../config');

function canonical(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
}
function sign(id, payload, salt = config.day1Salt) {
  return crypto.createHmac('sha256', Buffer.from(salt, 'utf8')).update(Buffer.from(`${id}|${canonical(payload)}`, 'utf8')).digest('hex');
}
module.exports = { canonical, sign };
