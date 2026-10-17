const required = (name, fallback) => process.env[name] || fallback;

module.exports = {
  port: Number(required('PORT', '8080')),
  databaseUrl: required('DATABASE_URL', 'postgresql://nascon:nascon@db:5432/nascon'),
  redisUrl: required('REDIS_URL', 'redis://redis:6379'),
  jwtSecret: required('JWT_SECRET', 'development-only-jwt-secret'),
  day1Salt: required('DAY1_SALT', 'demo-salt'),
};
