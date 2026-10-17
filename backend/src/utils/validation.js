const { z } = require('zod');
const { fail } = require('./errors');
const id = z.string().regex(/^[A-Za-z0-9_.:-]{1,64}$/);
const int = z.number().int();
const date = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(v => !Number.isNaN(Date.parse(v)) && new Date(v).toISOString().slice(0,10) === v);
const ties = ['h2h_points','h2h_diff','overall_diff','scored','fair_play','draw_lots'];
const tieOrder = z.array(z.enum(ties)).refine(v => new Set(v).size === v.length);
function parse(schema, body) { const r=schema.safeParse(body); if(!r.success) fail(400,'VALIDATION_ERROR','Invalid request.',{fields:[...new Set(r.error.issues.map(x=>x.path.join('.')))]}); return r.data; }
function check(ok, field) { if(!ok) fail(400,'VALIDATION_ERROR','Invalid request.',{fields:[field]}); }
module.exports={z,id,int,date,ties,tieOrder,parse,check};
