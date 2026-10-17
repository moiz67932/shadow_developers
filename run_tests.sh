#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
export DAY1_SALT="${DAY1_SALT:-demo-salt}"
# Tests use a dedicated database and never reset the running application's DB.
if [ -z "${DATABASE_URL:-}" ]; then
  db_id=$(docker compose ps -q db)
  test_db_exists=$(docker exec "$db_id" psql -U nascon -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='nascon_test'")
  if [ "$test_db_exists" != "1" ]; then docker exec "$db_id" createdb -U nascon nascon_test; fi
  db_ip=$(docker inspect "$db_id" --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
  export DATABASE_URL="postgresql://nascon:nascon@$db_ip:5432/nascon_test"
fi
case "$DATABASE_URL" in
  */nascon_test) ;;
  *) echo 'Tests require a dedicated database named nascon_test.' >&2; exit 1 ;;
esac
export RUN_INTEGRATION=1
cd backend
npm run coverage -- --silent
node scripts/coverage.js
