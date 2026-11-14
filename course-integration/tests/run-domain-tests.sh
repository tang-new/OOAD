#!/usr/bin/env bash
# Never use the user's existing MySQL container or database.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${JAVA_HOME:?JDK 21 required}"
export PATH="$JAVA_HOME/bin:$PATH"
CONTAINER=m6-course-mysql
if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
 docker run -d --name "$CONTAINER" -p 127.0.0.1:13316:3306 -e MYSQL_ROOT_PASSWORD=m6-course-local-only -e MYSQL_DATABASE=course_integration -e MYSQL_USER=course_integration -e MYSQL_PASSWORD=m6-course-local-only mysql:8.0
fi
for attempt in {1..60}; do docker exec "$CONTAINER" mysqladmin ping -uroot -pm6-course-local-only --silent && break; sleep 2; done
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13316/course_integration?serverTimezone=Asia/Shanghai&sslMode=DISABLED&allowPublicKeyRetrieval=true' \
 DB_HOST=127.0.0.1 DB_PORT=13316 DB_NAME=course_integration DB_USER=course_integration DB_PASSWORD=m6-course-local-only \
 bash "$ROOT/mvnw" -B -ntp -s "$ROOT/course-integration/maven-settings.xml" -gs "$ROOT/course-integration/maven-settings.xml" -f "$ROOT/.cache/course-source/prepared/backend/pom.xml" -DargLine=-Xmx768m -Dspring.test.context.cache.maxSize=4 -Dspring.datasource.hikari.maximum-pool-size=2 verify "$@"
