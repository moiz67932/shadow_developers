const { fail } = require('../../utils/errors');
function roundRobin(teams, params = {}) {
  const legs = params.legs ?? 1;
  if (!Array.isArray(teams) || teams.length < 2 || teams.length > 40 || new Set(teams).size !== teams.length || ![1,2].includes(legs)) fail(400, 'VALIDATION_ERROR', 'Invalid fixture request.', { fields: ['teams'] });
  const rotating = teams.slice(); if (rotating.length % 2) rotating.push(null);
  const perLeg = rotating.length - 1, rounds = [], withdrawals = new Map((params.withdrawals || []).map((w) => [w.team_id, w.after_round]));
  for (let leg = 1; leg <= legs; leg++) for (let local = 0; local < perLeg; local++) {
    const global = (leg - 1) * perLeg + local + 1, matches = [], byes = [];
    for (let i = 0; i < rotating.length / 2; i++) { let a = rotating[i], b = rotating[rotating.length - 1 - i]; if (!a || !b) { byes.push(a || b); continue; } if ((local + leg) % 2) [a,b] = [b,a]; if (leg === 2) [a,b] = [b,a]; const wa = withdrawals.get(a), wb = withdrawals.get(b); let status='scheduled', awarded_to=null; if (params.withdrawal_policy === 'void_all' && (wa !== undefined || wb !== undefined)) status='voided'; else if ((wa !== undefined && global > wa) || (wb !== undefined && global > wb)) { status='forfeit'; awarded_to = (wa !== undefined && global > wa && !(wb !== undefined && global > wb)) ? b : (wb !== undefined && global > wb ? a : null); if (!awarded_to) status='voided'; } matches.push({ match_id: `RR-L${leg}-R${global}-M${matches.length+1}`, leg, home:a, away:b, status, awarded_to }); }
    rounds.push({ round:global, matches, byes }); rotating.splice(1,0,rotating.pop());
  }
  const all = rounds.flatMap((r) => r.matches); return { format:'round_robin', legs, team_count:teams.length, round_count:rounds.length, rounds, summary:{ total:all.length, scheduled:all.filter(x=>x.status==='scheduled').length, forfeit:all.filter(x=>x.status==='forfeit').length, voided:all.filter(x=>x.status==='voided').length } };
}
module.exports = { roundRobin };
