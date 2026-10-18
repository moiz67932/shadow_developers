process.env.DAY1_SALT??='demo-salt';
process.env.JWT_SECRET??='test-only-jwt-secret';
process.env.DAY2_SALT??='test-day2-only';
// Every database suite calls reset; never allow it to target the competition DB.
if(process.env.RUN_INTEGRATION==='1'){
  const name=new URL(process.env.DATABASE_URL??'postgresql://invalid/invalid').pathname;
  if(name!=='/nascon_test')throw new Error('Integration tests require an isolated database named nascon_test.');
}
