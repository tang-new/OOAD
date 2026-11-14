#!/usr/bin/env bash
# Source trusted local .env without overriding any explicitly exported variable.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"
if [[ -f .env ]]; then
 snapshot=$(mktemp); chmod 600 "$snapshot"
 export -p > "$snapshot"
 set -a; source .env; set +a
 source "$snapshot"; rm -f "$snapshot"
fi
source deploy/backend-module.sh
select_backend_module
export STACK=${STACK:-rbac1-acceptance} TAG=${TAG:-local} REGISTRY=${REGISTRY:-}
[[ "$STACK" == rbac1-acceptance ]] || { echo 'Only the isolated rbac1-acceptance stack is authorized' >&2; exit 1; }
export RUNTIME_DIR=${RUNTIME_DIR:-$ROOT/.runtime/full}
export COURSE_BUILD=${COURSE_BUILD:-$ROOT/.cache/course-source/prepared}
export RBAC_PORT=${RBAC_PORT:-8081} COURSE_PORT=${COURSE_PORT:-8080} GATEWAY_PORT=${GATEWAY_PORT:-8090}
export RBAC_TLS_PORT=${RBAC_TLS_PORT:-8443} COURSE_TLS_PORT=${COURSE_TLS_PORT:-9443}
export JWT_ISSUER=${JWT_ISSUER:-https://identity.rbac1.local}
export JWT_KID=${JWT_KID:-rbac1-acceptance-1}
export SECURITY_LOG_LEVEL=${SECURITY_LOG_LEVEL:-INFO}
