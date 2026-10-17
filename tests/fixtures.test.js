const { roundRobin } = require('../backend/src/domain/fixtures/roundRobin');
test('round robin gives each odd team one bye and every pair once',()=>{const result=roundRobin(['a','b','c','d','e']);expect(result.round_count).toBe(5);expect(result.rounds.every(r=>r.byes.length===1)).toBe(true);expect(result.summary.total).toBe(10);});
