#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/full-env.sh"
bash tools/install-clonefactory.sh
python tools/generate-rbac1-contract.py --check
python deploy/full-config-test.py
# package runs normal JUnit and produces executable jars; verify gates wait for real HTTP coverage.
bash mvnw -B -ntp -s deploy/maven-settings-huawei.xml clean package
python tools/generate-rbac1-contract-test.py
node --test tools/*.test.mjs diagrams/phase1/tools/gen-mdj.test.mjs
source deploy/full-jacoco-agent.sh -s deploy/maven-settings-huawei.xml
(cd tools/acceptance && npm ci && npm test)
_http_status=0
(cd tools/acceptance && npm run acceptance) || _http_status=$?
# Never clean here: merge JUnit + target/jacoco-acceptance/*.exec at the unchanged gate.
# Still report the original coverage gate after an HTTP RED; neither failure is waived.
_verify_status=0
bash mvnw -B -ntp -s deploy/maven-settings-huawei.xml verify -DskipTests || _verify_status=$?
if (( _http_status != 0 || _verify_status != 0 )); then
 echo "No images built: shared HTTP exit=$_http_status, original coverage verify exit=$_verify_status" >&2
 exit 1
fi
(cd rbac-web && npm ci --no-audit --no-fund && npm run typecheck && npm run test:coverage && npm run build)
(cd rbac-web && npx playwright install chromium && E2E_MODULE=rbac-structured npm run e2e && E2E_MODULE=rbac-ddd npm run e2e)
bash tools/prepare-course.sh --original --build
# Course integration owns its frontend and backend artifact layout; fail rather than fabricate.
[[ -f "$COURSE_BUILD/backend/pom.xml" && -f course-integration/frontend/package.json ]]
docker build --build-arg MODULE="$MODULE" -t "${REGISTRY}${MODULE}:${TAG}" .
docker build -t "${REGISTRY}rbac-web:${TAG}" rbac-web
docker build -f deploy/full-course-backend.Dockerfile -t "${REGISTRY}course-backend:${TAG}" "$COURSE_BUILD/backend"
docker build -f deploy/full-course-web.Dockerfile -t "${REGISTRY}course-web:${TAG}" .
if [[ ${1:-} == push ]]; then
 [[ -n "$REGISTRY" ]] || { echo 'REGISTRY required for push' >&2; exit 1; }
 for image in "$MODULE" rbac-web course-backend course-web; do docker push "${REGISTRY}${image}:${TAG}"; done
fi
