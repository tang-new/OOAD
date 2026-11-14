#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${COURSE_IMAGE_PREFIX:=ooad}"
[[ -f "$ROOT/.cache/course-source/prepared/backend/target/course-project-backend-0.0.1-SNAPSHOT.jar" ]] || { echo 'Run tools/prepare-course.sh --build first';exit 1; }
docker build -f "$ROOT/course-integration/Dockerfile.backend" -t "$COURSE_IMAGE_PREFIX/course-backend:m6" "$ROOT/.cache/course-source/prepared"
docker build -t "$COURSE_IMAGE_PREFIX/course-frontend:m6" "$ROOT/course-integration/frontend"
