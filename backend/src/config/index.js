const required = (name, fallback) => process.env[name] || fallback;

module.exports = {
  port: Number(required('PORT', '8080')),
  databaseUrl: required('DATABASE_URL', 'postgresql://nascon:nascon@db:5432/nascon'),
  redisUrl: required('REDIS_URL', 'redis://redis:6379'),
  jwtSecret: process.env.JWT_SECRET,
  day1Salt: process.env.DAY1_SALT,
  day2Salt: process.env.DAY2_SALT,
  mediaHealthUrl: process.env.MEDIA_HEALTH_URL,
  minioHealthUrl: process.env.MINIO_HEALTH_URL,
  strictReadiness: process.env.STRICT_READINESS === '1',
};
