const required = (name, fallback) => process.env[name] || fallback;

module.exports = {
  port: Number(required('PORT', '8080')),
  databaseUrl: required('DATABASE_URL', 'postgresql://nascon:nascon@db:5432/nascon'),
  redisUrl: required('REDIS_URL', 'redis://redis:6379'),
  jwtSecret: process.env.JWT_SECRET,
  day1Salt: process.env.DAY1_SALT,
};
