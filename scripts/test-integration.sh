#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
# This database is isolated from the application database and may be reset.
if [ -z "${DATABASE_URL:-}" ]; then
  test_db_container=$(docker ps --filter label=com.docker.compose.project=shadow_developers --filter label=com.docker.compose.service=db --format '{{.ID}}')
  test_db_ip=$(docker inspect "$test_db_container" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
  test_db_exists=$(docker exec "$test_db_container" psql -U nascon -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='nascon_test'")
  if [ "$test_db_exists" != "1" ]; then docker exec "$test_db_container" createdb -U nascon nascon_test; fi
  export DATABASE_URL="postgresql://nascon:nascon@$test_db_ip:5432/nascon_test"
fi
case "$DATABASE_URL" in
  */nascon_test) ;;
  *) echo 'Refusing integration tests: DATABASE_URL must end in /nascon_test.' >&2; exit 1 ;;
esac
export DAY1_SALT="${DAY1_SALT:-integration-test-only}"
export DAY2_SALT="${DAY2_SALT:-integration-test-only}"
export JWT_SECRET="${JWT_SECRET:-integration-test-only}"
export RUN_INTEGRATION=1
export STRICT_READINESS=0
if [ -z "${TEST_REDIS_URL:-}" ]; then
  if docker inspect nascon-phase1-redis-test >/dev/null 2>&1; then
    docker start nascon-phase1-redis-test >/dev/null
  else
    docker run -d --name nascon-phase1-redis-test redis:7-alpine >/dev/null
  fi
  test_redis_ip=$(docker inspect nascon-phase1-redis-test --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
  export TEST_REDIS_URL="redis://$test_redis_ip:6379"
fi
# No coverage-file generation or competition data reset.
npm --prefix backend test -- --cache=false --silent "$@"
