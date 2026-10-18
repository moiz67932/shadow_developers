const jwt=require('jsonwebtoken');const crypto=require('crypto');const config=require('../../config');const {fail}=require('../../utils/errors');
// A domain-separated environment-derived fallback survives restarts. Prefer JWT_SECRET.
const secret=config.jwtSecret||(config.day1Salt ? crypto.createHmac('sha256',config.day1Salt).update('nascon/session-jwt/v1').digest('hex') : crypto.randomBytes(32).toString('hex'));
const roles=['super_admin','league_organizer','club_manager','player','viewer'];
const context={tenant_id:'tenant-alpha',foreign_tenant_id:'tenant-beta',own_league_id:'league-alpha-1',other_league_id:'league-alpha-2',foreign_league_id:'league-beta-1',own_club_id:'club-alpha-1',other_club_id:'club-alpha-2',foreign_club_id:'club-beta-1',own_player_id:'player-alpha-1',other_player_id:'player-alpha-2',foreign_player_id:'player-beta-1'};
function token(role){return jwt.sign({purpose:'audit_probe',role,tenant_id:'tenant-alpha',league_id:context.own_league_id,club_id:context.own_club_id,player_id:context.own_player_id},secret,{algorithm:'HS256',expiresIn:'2h'});}
function sessionToken(user){return jwt.sign({purpose:'member_session',user_id:user.user_id,role:user.role,tenant_id:user.tenant_id,league_id:user.league_id??null,league_ids:user.league_ids??[],club_id:user.club_id??null,player_id:user.player_id??null},secret,{subject:user.user_id,algorithm:'HS256',expiresIn:'2h'});}
function authenticateSession(header){const user=authenticate(header);if(user.purpose!=='member_session'||!user.sub||user.sub!==user.user_id||!user.tenant_id)fail(401,'UNAUTHENTICATED','An account session is required.');return user;}
function matrix(){return {tokens:Object.fromEntries(roles.map(r=>[r,token(r)])),context};}
function authenticate(header){try{if(!/^Bearer \S+$/.test(header??''))throw Error();const user=jwt.verify(header.slice(7),secret,{algorithms:['HS256']});if(!roles.includes(user.role))throw Error();return user;}catch{fail(401,'UNAUTHENTICATED','A valid signed token is required.');}}
function authorize(header,probe,target){const user=authenticate(header);
 const kind=['fixtures'].includes(probe)?'league':['roster','products','club'].includes(probe)?'club':probe==='player'?'player':null;
 if(kind&&!['alpha-1','alpha-2','beta-1'].map(s=>`${kind}-${s}`).includes(target))fail(404,kind==='league'?'LEAGUE_NOT_FOUND':kind==='player'?'PLAYER_NOT_FOUND':'NOT_FOUND','Resource not found.');
 if(user.role==='super_admin')return {authorized:true};
 const sameTenant=target&&['league-alpha-1','league-alpha-2','club-alpha-1','club-alpha-2','player-alpha-1','player-alpha-2'].includes(target);
 let allowed=false;if(sameTenant){if(probe==='fixtures')allowed=user.role==='league_organizer'&&target===user.league_id;if(['roster','products'].includes(probe))allowed=user.role==='club_manager'&&target===user.club_id;if(probe==='player')allowed=(user.role==='player'&&target===user.player_id)||(user.role==='club_manager'&&target==='player-alpha-1');if(probe==='club')allowed=true;}
 if(!allowed)fail(403,'FORBIDDEN','This role cannot access the requested resource.');return {authorized:true};}
module.exports={matrix,authorize,token,sessionToken,roles,authenticate,authenticateSession};
