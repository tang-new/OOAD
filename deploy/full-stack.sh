#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/full-env.sh"
# Never operate against a remote context, even if Docker CLI was reconfigured.
endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
case "$endpoint" in npipe:*|unix:*) ;; *) echo 'Local Docker socket required; remote endpoint rejected' >&2; exit 1;; esac
old_service_guard() {
 for name in "${STACK}_rbac-structured" "${STACK}_rbac-ddd"; do
  if docker service inspect "$name" >/dev/null 2>&1; then
   echo '发现旧服务；需人工维护迁移，拒绝自动删除/prune' >&2; exit 1
  fi
 done
}
case ${1:-} in
 init)
  [[ $(docker info --format '{{.Swarm.LocalNodeState}}') == inactive ]] || { echo 'Swarm already active; no reinitialization'; exit 0; }
  # Only initialize this authorized Desktop manager; do not print join tokens.
  docker swarm init > /dev/null
  docker info --format 'Local swarm: {{.Swarm.LocalNodeState}} manager={{.Swarm.ControlAvailable}}'
  docker node ls
  ;;
 up)
  old_service_guard
  [[ $(docker info --format '{{.Swarm.ControlAvailable}}') == true ]] || { echo 'Run full-stack.sh init on local Desktop first' >&2; exit 1; }
  [[ $(docker info --format '{{.Swarm.Nodes}}') == 1 ]] || { echo 'Only an isolated single-node local Swarm is authorized' >&2; exit 1; }
  # No partial deployment when build artifacts/images are not ready.
  for image in "$MODULE" rbac-web course-backend course-web; do
   docker image inspect "${REGISTRY}${image}:${TAG}" >/dev/null 2>&1 || { echo "Missing tested image: ${REGISTRY}${image}:${TAG}; run full-build.sh first" >&2; exit 1; }
  done
  bash deploy/full-secrets.sh
  if docker service inspect "${STACK}_rbac-backend" >/dev/null 2>&1; then
   active=$(docker service inspect "${STACK}_rbac-backend" --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}')
   if [[ "$active" != "${REGISTRY}${MODULE}:"* ]]; then
    echo 'Backend module change requires full-stack.sh switch with CONFIRM_STOP_WRITES=yes; no automatic competing writers' >&2; exit 1
   fi
  fi
  docker stack deploy --resolve-image never -c docker-compose.yml "$STACK"
  echo 'Submitted, NOT yet verified. Run full-verify.sh; no deployment claim until successful.'
  ;;
 switch)
  [[ ${CONFIRM_STOP_WRITES:-} == yes ]] || { echo 'Requires maintenance window: CONFIRM_STOP_WRITES=yes; no strict rolling single-writer claim' >&2; exit 1; }
  old_service_guard
  for image in "$MODULE" rbac-web course-backend course-web; do
   docker image inspect "${REGISTRY}${image}:${TAG}" >/dev/null 2>&1 || { echo "Missing tested image: ${REGISTRY}${image}:${TAG}" >&2; exit 1; }
  done
  if [[ -n ${SWITCH_EVIDENCE_FILE:-} ]]; then python deploy/full-switch-evidence.py --phase before --file "$SWITCH_EVIDENCE_FILE"; fi
  docker service scale "${STACK}_rbac-web=0" "${STACK}_course-web=0" "${STACK}_course-backend=0" "${STACK}_rbac-backend=0"
  for ((i=0;i<90;i++)); do
   running=''
   for service in rbac-web course-web course-backend rbac-backend; do
    running+=$(docker ps -q --filter "label=com.docker.stack.namespace=$STACK" --filter "label=com.docker.swarm.service.name=${STACK}_${service}")
   done
   [[ -z "$running" ]] && break
   sleep 2
  done
  [[ -z "$running" ]] || { echo 'An old application/edge task did not stop; refusing switch' >&2; exit 1; }
  if [[ -n ${SWITCH_EVIDENCE_FILE:-} ]]; then python deploy/full-switch-evidence.py --phase stopped --file "$SWITCH_EVIDENCE_FILE"; fi
  echo "Controlled stop-write barrier passed: application/edge containers stopped; MySQL/Redis/volumes retained. Next MODULE=$MODULE TAG=$TAG"
  docker stack deploy --resolve-image never -c docker-compose.yml "$STACK"
  ;;
 ps)
  docker node ls
  docker stack services "$STACK"
  docker stack ps --no-trunc "$STACK"
  ;;
 logs) docker service logs --tail 100 "${STACK}_${2:-rbac-backend}" ;;
 down)
  [[ ${CONFIRM_STOP_STACK:-} == yes ]] || { echo 'Requires CONFIRM_STOP_STACK=yes. Data volumes are always retained.' >&2; exit 1; }
  docker stack rm "$STACK"
  echo 'Only the isolated stack removed. ALL persistent volumes retained; no volume-delete/prune step.'
  ;;
 *) echo 'Usage: full-stack.sh init|up|switch|ps|logs [service]|down' >&2; exit 2;;
esac
