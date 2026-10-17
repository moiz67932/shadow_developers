const {createLeague,append}=require('../domain/scoring');const {setup}=require('../domain/rosters');
function prepare(s,tenant){
 const id='demo-alpha-cup';if(s.leagues[id])return {status:'ready',league_id:id};
 const teams=['Falcons','Titans','Strikers','Warriors'];
 createLeague(s,{league_id:id,sport_id:'football',teams,fixtures:[{match_id:'demo-final',home:teams[0],away:teams[1]},{match_id:'demo-live',home:teams[2],away:teams[3]},{match_id:'demo-next',home:teams[0],away:teams[2]}],qualification:{spots:2}});
 Object.assign(s.leagues[id],{tenant_id:tenant,name:'NaSCon Champions Cup'});
 let n=0;const event=(match_id,type,payload={})=>append(s,{event_id:`demo-seed-${++n}`,match_id,type,payload});
 event('demo-final','match_start');const rule=Object.keys(s.sports.football.scoring_events)[0];event('demo-final','score',{team_id:teams[0],event_type:rule});event('demo-final','match_end');event('demo-live','match_start');event('demo-live','score',{team_id:teams[2],event_type:rule});
 setup(s,{league:{league_id:id,tenant_id:tenant,sport_id:'football',transfer_window:{open:'2026-01-01',close:'2026-12-31'},max_squad_size:25,required_positions:{gk:1}},squads:[{squad_id:'demo-falcons',club_id:'club-alpha-1',tenant_id:tenant,name:'Falcons'},{squad_id:'demo-titans',club_id:'club-alpha-2',tenant_id:tenant,name:'Titans'}],players:[{player_id:'demo-ali',name:'Ali Hassan',position:'gk',squad_id:'demo-falcons',role:'captain'},{player_id:'demo-bilal',name:'Bilal Ahmed',position:'df',squad_id:'demo-falcons',role:'starter'},{player_id:'demo-sara',name:'Sara Khan',position:'fw',squad_id:'demo-falcons',role:'starter'},{player_id:'demo-omar',name:'Omar Malik',position:'gk',squad_id:'demo-titans',role:'captain'}]});
 const club=s.clubs.find(c=>c.club_id==='club-alpha-1');Object.assign(club,{name:'Falcons Sports Club',venue:'FAST Sports Arena',staff:['Ayesha • Head coach','Hamza • Team physician'],history:'NaSCon 2026 • Founding season'});return {status:'prepared',league_id:id};
}module.exports={prepare};
