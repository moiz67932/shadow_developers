const crypto = require('crypto');
const config = require('../config');

function canonical(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  const compare=(a,b)=>{const x=Array.from(a,c=>c.codePointAt(0)),y=Array.from(b,c=>c.codePointAt(0));for(let i=0;i<Math.min(x.length,y.length);i++)if(x[i]!==y[i])return x[i]-y[i];return x.length-y.length;};
  return `{${Object.keys(value).sort(compare).map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
}
function sign(id, payload, salt = config.day1Salt) {
  return crypto.createHmac('sha256', Buffer.from(salt, 'utf8')).update(Buffer.from(`${id}|${canonical(payload)}`, 'utf8')).digest('hex');
}
module.exports = { canonical, sign };
