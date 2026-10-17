const {z,id,int,parse,check}=require('../../utils/validation');
const {fail}=require('../../utils/errors');
function roundRobin(teams,p){
 const legs=p.legs??1; check([1,2].includes(legs),'params.legs');
 const ring=[...teams]; if(ring.length%2)ring.push(null);
 const count=ring.length-1, rounds=[], withdrawals=p.withdrawals??[], policy=p.withdrawal_policy??'forfeit_remaining';
 check(['void_all','forfeit_remaining'].includes(policy),'params.withdrawal_policy');
 parse(z.array(z.object({team_id:id,after_round:int.min(0).max(count*legs)})),withdrawals);
 check(withdrawals.every(w=>teams.includes(w.team_id)),'params.withdrawals');
 const wd=new Map(withdrawals.map(w=>[w.team_id,w.after_round]));
 for(let l=1;l<=legs;l++)for(let r=0;r<count;r++){
  const round=(l-1)*count+r+1,matches=[],byes=[];
  for(let i=0;i<ring.length/2;i++){
   let a=ring[i],b=ring[ring.length-1-i];if(a===null||b===null){byes.push(a??b);continue;}
   // Orient each edge using a regular tournament on the original seed indices.
   const n=teams.length,ai=teams.indexOf(a),bi=teams.indexOf(b),d=(bi-ai+n)%n;
   if(d>n/2||(n%2===0&&d===n/2&&ai>bi))[a,b]=[b,a];
   if(l===2)[a,b]=[b,a];
   const x=wd.has(a),y=wd.has(b);let status='scheduled',awarded_to=null;
   if((x&&y)||(policy==='void_all'&&(x||y)))status='voided';
   else if((x&&round>wd.get(a))||(y&&round>wd.get(b))){status='forfeit';awarded_to=x?b:a;}
   matches.push({match_id:`RR-L${l}-R${round}-M${matches.length+1}`,leg:l,home:a,away:b,status,awarded_to});
  } rounds.push({round,matches,byes});ring.splice(1,0,ring.pop());
 }
 const all=rounds.flatMap(r=>r.matches),summary={total:all.length,scheduled:0,forfeit:0,voided:0};all.forEach(m=>summary[m.status]++);
 return {format:'round_robin',legs,team_count:teams.length,round_count:rounds.length,rounds,summary};
}
function swiss(teams,p){
 const n=teams.length,total=p.total_rounds??Math.ceil(Math.log2(n)),round=p.round??1;
 check(Number.isInteger(total)&&total>=1&&total<n,'params.total_rounds');check(Number.isInteger(round)&&round>=1&&round<=total,'params.round');
 const history=parse(z.array(z.object({round:int.min(1),bye:id.nullable(),matches:z.array(z.object({home:id,away:id,result:z.enum(['home','away','draw'])}))})),p.history??[]);
 check(history.length===round-1&&new Set(history.map(h=>h.round)).size===round-1&&history.every(h=>h.round<round),'params.history');
 const score=Object.fromEntries(teams.map(t=>[t,0])),op=Object.fromEntries(teams.map(t=>[t,[]])),byes=new Set();
 for(const h of history){const used=[];if(h.bye!==null){check(teams.includes(h.bye)&&!byes.has(h.bye),'params.history.bye');score[h.bye]++;byes.add(h.bye);used.push(h.bye);}
 for(const m of h.matches){check(teams.includes(m.home)&&teams.includes(m.away)&&m.home!==m.away&&!op[m.home].includes(m.away),'params.history.matches');used.push(m.home,m.away);op[m.home].push(m.away);op[m.away].push(m.home);score[m.home]+=m.result==='home'?1:m.result==='draw'?0.5:0;score[m.away]+=m.result==='away'?1:m.result==='draw'?0.5:0;}
 check(used.length===n&&new Set(used).size===n,'params.history');}
 const standings=teams.map(t=>({team_id:t,score:score[t],buchholz:op[t].reduce((s,o)=>s+score[o],0)})).sort((a,b)=>b.score-a.score||b.buchholz-a.buchholz||teams.indexOf(a.team_id)-teams.indexOf(b.team_id)).map((t,i)=>({rank:i+1,...t}));
 let bye=null,pairs;
 if(round===1){bye=n%2?teams[n-1]:null;pairs=teams.slice(0,Math.floor(n/2)).map((t,i)=>[t,teams[i+Math.floor(n/2)]]);}
 else{
 const candidates=n%2?teams.filter(t=>!byes.has(t)).sort((a,b)=>score[a]-score[b]||teams.indexOf(b)-teams.indexOf(a)):[null];
 for(const candidate of candidates){let best=Infinity,solution=null;const memo=new Map();
 function search(rest,cost,chosen){if(cost>=best)return;if(!rest.length){best=cost;solution=chosen;return;}const key=rest.join('|');if((memo.get(key)??Infinity)<=cost)return;memo.set(key,cost);
 const unseen=new Set(rest);while(unseen.size){const stack=[unseen.values().next().value];unseen.delete(stack[0]);let count=0;while(stack.length){const a=stack.pop();count++;for(const b of unseen)if(!op[a].includes(b)){unseen.delete(b);stack.push(b);}}if(count%2)return;}
 // A nearest-neighbour lower bound prunes without sacrificing optimality.
 let bound=0;for(const a of rest){const options=rest.filter(b=>b!==a&&!op[a].includes(b)).map(b=>Math.abs(score[a]-score[b]));if(!options.length)return;bound+=Math.min(...options);}if(cost+bound/2>=best)return;
 const a=rest.reduce((a,b)=>rest.filter(x=>x!==a&&!op[a].includes(x)).length<=rest.filter(x=>x!==b&&!op[b].includes(x)).length?a:b);
 for(const b of rest.filter(t=>t!==a&&!op[a].includes(t)).sort((x,y)=>Math.abs(score[a]-score[x])-Math.abs(score[a]-score[y])))search(rest.filter(t=>t!==a&&t!==b),cost+Math.abs(score[a]-score[b]),[...chosen,[a,b]]);
 }search(teams.filter(t=>t!==candidate),0,[]);if(solution){bye=candidate;pairs=solution;break;}}
 if(!pairs)fail(409,'NO_VALID_PAIRING','No complete pairing exists.',{round});}
 return {format:'swiss',round,total_rounds:total,pairings:pairs.map(([home,away],i)=>({match_id:`SW-R${round}-M${i+1}`,home,away})),bye,standings};
}
function elimination(teams,p,double){
 const B=2**Math.ceil(Math.log2(teams.length)),k=Math.log2(B),all=new Map();
 function match(match_id,bracket,round,index){const m={match_id,bracket,round,index,home:null,away:null,home_seed:null,away_seed:null,bye:false,winner:null,loser:null,winner_to:null,loser_to:null};if(double)Object.assign(m,{conditional:false,required:true});all.set(match_id,m);return m;}
 const prefix=double?'WR':'R',winners=Array.from({length:k},(_,r)=>({round:r+1,matches:Array.from({length:B/2**(r+1)},(_,i)=>match(`${prefix}${r+1}M${i+1}`,double?'winners':'main',r+1,i+1))}));
 const link=(m,key,target,slot)=>{m[key]={match_id:target.match_id,slot};};
 winners.forEach((r,ri)=>r.matches.forEach((m,i)=>{if(ri<k-1)link(m,'winner_to',winners[ri+1].matches[Math.floor(i/2)],i%2?'away':'home');}));
 let order=[1,2];for(let size=4;size<=B;size*=2)order=order.flatMap(s=>[s,size+1-s]);
 winners[0].matches.forEach((m,i)=>{const [a,b]=order.slice(i*2,i*2+2).sort((x,y)=>x-y);m.home=teams[a-1];m.home_seed=a;m.away=teams[b-1]??null;m.away_seed=m.away?b:null;m.bye=!m.away;});
 let losers=[],grand_final=[];
 if(double){const reset=p.bracket_reset??true;check(typeof reset==='boolean','params.bracket_reset');
 losers=Array.from({length:2*(k-1)},(_,r)=>({round:r+1,matches:Array.from({length:B/2**(Math.floor(r/2)+2)},(_,i)=>match(`LR${r+1}M${i+1}`,'losers',r+1,i+1))}));
 const gf=match('GF1','grand_final',1,1);grand_final.push(gf);link(winners[k-1].matches[0],'winner_to',gf,'home');
 winners.forEach((r,ri)=>r.matches.forEach((m,i)=>{if(ri===0)link(m,'loser_to',losers[0].matches[Math.floor(i/2)],i%2?'away':'home');else link(m,'loser_to',losers[2*ri-1].matches[i],'away');}));
 losers.forEach((r,ri)=>r.matches.forEach((m,i)=>{if(ri===losers.length-1)link(m,'winner_to',gf,'away');else if(ri%2===0)link(m,'winner_to',losers[ri+1].matches[i],'home');else link(m,'winner_to',losers[ri+1].matches[Math.floor(i/2)],i%2?'away':'home');}));
 if(reset){const gf2=match('GF2','grand_final',2,1);gf2.conditional=true;gf2.required=null;grand_final.push(gf2);link(gf,'winner_to',gf2,'home');link(gf,'loser_to',gf2,'away');}
 }else if(p.third_place_match){check(k>=2,'params.third_place_match');const tp=match('TP','third_place',k,1);winners.push({round:k,matches:[tp]});winners[k-2].matches.forEach((m,i)=>link(m,'loser_to',tp,i?'away':'home'));}
 const done=new Set();
 function propagate(m){done.add(m.match_id);for(const [key,value] of [['winner_to',m.winner],['loser_to',m.loser]])if(m[key]){const target=all.get(m[key].match_id);target[m[key].slot]=value;target[m[key].slot+'_seed']=value?teams.indexOf(value)+1:null;}}
 winners[0].matches.filter(m=>m.bye).forEach(m=>{m.winner=m.home;propagate(m);});
 function autoByes(){let changed;do{changed=false;for(const m of all.values()){if(done.has(m.match_id)||m.conditional||m.bracket!=='losers')continue;const sources=[...all.values()].filter(x=>[x.winner_to,x.loser_to].some(l=>l?.match_id===m.match_id));if(sources.length===2&&sources.every(x=>done.has(x.match_id))&&(!m.home||!m.away)){m.bye=true;m.winner=m.home??m.away;propagate(m);changed=true;}}}while(changed);}
 const results=parse(z.array(z.object({match_id:id,winner:id})),p.results??[]);check(new Set(results.map(x=>x.match_id)).size===results.length,'params.results');
 for(const result of results){autoByes();const m=all.get(result.match_id);check(m&&[m.home,m.away].includes(result.winner)&&(!m.conditional||m.required===true),'params.results');m.winner=result.winner;m.loser=m.home===result.winner?m.away:m.home;propagate(m);if(m.match_id==='GF1'&&grand_final[1]){Object.assign(grand_final[1],{home:m.home,away:m.away,home_seed:m.home_seed,away_seed:m.away_seed,required:m.winner===m.away});}}
 autoByes();return double?{format:'double_elim',bracket_size:B,bracket_reset:p.bracket_reset??true,winners,losers,grand_final}:{format:'single_elim',bracket_size:B,rounds:winners};
}
function fixtures(input){const b=parse(z.object({format:z.enum(['round_robin','swiss','single_elim','double_elim']),teams:z.array(id),params:z.object({}).passthrough().default({})}),input);const limits={round_robin:[2,40],swiss:[2,64],single_elim:[2,128],double_elim:[3,128]};const [lo,hi]=limits[b.format];check(b.teams.length>=lo&&b.teams.length<=hi&&new Set(b.teams).size===b.teams.length,'teams');return b.format==='round_robin'?roundRobin(b.teams,b.params):b.format==='swiss'?swiss(b.teams,b.params):elimination(b.teams,b.params,b.format==='double_elim');}
module.exports={fixtures,roundRobin,swiss,elimination};
