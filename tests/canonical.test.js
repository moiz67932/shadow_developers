const { canonical, sign } = require('../backend/src/utils/canonical');
test('canonical JSON and Appendix A HMAC vector', () => {
  const payload={team_id:'t1',event_type:'goal'};
  expect(canonical(payload)).toBe('{"event_type":"goal","team_id":"t1"}');
  expect(sign('evt-1',payload,'demo-salt')).toBe('972a8b558690b19121cc5bea84978f85e8eb15a3735018db36616bab1d7ec501');
});
