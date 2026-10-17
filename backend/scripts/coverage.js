const fs=require('fs');const path=require('path');
const summary=JSON.parse(fs.readFileSync(path.join(__dirname,'../coverage/coverage-summary.json'),'utf8'));
const groups={fixtures:'fixtures',scoring:'scoring',standings:'standings',store:'store',roster:'rosters',rbac:'rbac'},output={};
for(const [name,dir] of Object.entries(groups)){const files=Object.entries(summary).filter(([file])=>file.includes(`/src/domain/${dir}/`));let total=0,covered=0;for(const [,value] of files){total+=value.lines.total;covered+=value.lines.covered;}output[name]=total?Math.floor(covered/total*10000)/100:0;}
fs.writeFileSync(path.join(__dirname,'../../COVERAGE.json'),JSON.stringify(output,null,2)+'\n');console.log('Measured domain line coverage:',output);
