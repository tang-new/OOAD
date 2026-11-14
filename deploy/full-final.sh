#!/usr/bin/env bash
# Final LOCAL two-module Swarm acceptance using already-tested artifacts. Never clean/build Maven,
# kill test JVMs, drop databases, remove volumes, operate remote Docker, or claim external deployment.
set -euo pipefail
source "$(dirname "$0")/full-env.sh"
: "${SHARED_REPORT:?Set SHARED_REPORT to the completed 25+25+T21 JSON report}"
: "${REACTOR_LOG:?Set REACTOR_LOG to the completed full-reactor verify log}"
export FINAL_LOG_DIR=${FINAL_LOG_DIR:-$(python -c 'from pathlib import Path; print((Path.home()/"Downloads/rbac1-context-extracted").as_posix())')}
mkdir -p "$FINAL_LOG_DIR"
if [[ "$TAG" == local ]]; then export TAG="local-final-$(date +%Y%m%d-%H%M%S)"; fi
[[ "$TAG" == local-final-* ]] || { echo 'Final-local acceptance requires a fresh local-final-* tag' >&2; exit 1; }
export FINAL_RUN_DIR="$FINAL_LOG_DIR/m9-$TAG"
mkdir -p "$FINAL_RUN_DIR"
# This must succeed before any image is built or any service is changed.
python deploy/full-final-gate.py --shared-report "$SHARED_REPORT" --reactor-log "$REACTOR_LOG" --output "$FINAL_RUN_DIR/prerequisites.json"
# Every final-local tag is immutable: never silently overwrite a prior attempt's images.
for image in rbac-structured rbac-ddd rbac-gateway rbac-web course-backend course-web; do
 if docker image inspect "${REGISTRY}${image}:${TAG}" >/dev/null 2>&1; then echo 'Final-local tag already exists; choose a fresh local-final-* tag' >&2; exit 1; fi
done
# Canonical Dockerfile selection is deliberately exact, not a wildcard.
for module in rbac-structured rbac-ddd rbac-gateway; do
 docker build --build-arg MODULE="$module" -t "${REGISTRY}${module}:${TAG}" . > "$FINAL_RUN_DIR/$module-image.log" 2>&1
done
docker build -t "${REGISTRY}rbac-web:${TAG}" rbac-web > "$FINAL_RUN_DIR/rbac-web-image.log" 2>&1
docker build -f deploy/full-course-backend.Dockerfile -t "${REGISTRY}course-backend:${TAG}" "$COURSE_BUILD/backend" > "$FINAL_RUN_DIR/course-backend-image.log" 2>&1
docker build -f deploy/full-course-web.Dockerfile -t "${REGISTRY}course-web:${TAG}" . > "$FINAL_RUN_DIR/course-web-image.log" 2>&1
# Detect an owner rebuilding/replacing jars during image construction. Never bless mixed artifacts.
python - <<'PY'
import hashlib,json,os,pathlib
root=pathlib.Path.cwd();run=pathlib.Path(os.environ['FINAL_RUN_DIR'])
for name,info in json.loads((run/'prerequisites.json').read_text())['canonicalArtifacts'].items():
 if hashlib.sha256((root/info['path']).read_bytes()).hexdigest()!=info['sha256']:
  raise SystemExit('Artifact changed during final-local image construction: '+name+'; no service updated')
PY
for module in rbac-structured rbac-ddd; do
 if docker service inspect "${STACK}_rbac-backend" >/dev/null 2>&1; then
  MODULE="$module" CONFIRM_STOP_WRITES=yes SWITCH_EVIDENCE_FILE="$FINAL_RUN_DIR/$module-switch.json" bash deploy/full-stack.sh switch > "$FINAL_RUN_DIR/$module-switch.log" 2>&1
 else
  MODULE="$module" bash deploy/full-stack.sh up > "$FINAL_RUN_DIR/$module-up.log" 2>&1
 fi
 MODULE="$module" DEPLOYMENT_STAGE=local-final EVIDENCE_FILE="$FINAL_RUN_DIR/$module-runtime.json" bash deploy/full-verify.sh > "$FINAL_RUN_DIR/$module-verify.log" 2>&1
done
python - <<'PY'
import json,os,pathlib,time
root=pathlib.Path.cwd();run=pathlib.Path(os.environ['FINAL_RUN_DIR']);rounds={}
for module in ['rbac-structured','rbac-ddd']:
 r=json.loads((run/(module+'-runtime.json')).read_text())
 if r['status']!='local-final-runtime-passed': raise SystemExit('Local runtime verification did not pass: '+module)
 rounds[module]=r
# Both rounds use identical frontend images; only the selected center image changes.
for frontend in ['rbac-web','course-web']:
 images=[next(s['imageId'] for s in rounds[m]['services'] if s['name'].endswith('_'+frontend)) for m in rounds]
 if len(set(images))!=1: raise SystemExit('Frontend image changed between modules: '+frontend)
volumes=[next(s['persistentVolumes'] for s in r['services'] if s['name'].endswith('_mysql')) for r in rounds.values()]
if volumes[0]!=volumes[1]: raise SystemExit('Database volume changed during module switch')
switches={m:json.loads((run/(m+'-switch.json')).read_text()) for m in rounds if (run/(m+'-switch.json')).exists()}
result={'format':'rbac1-local-final-dual-swarm-v1','timestamp':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'status':'local-final-dual-runtime-passed','localFinalDeploymentAccepted':True,'finalProjectAcceptance':'Main must combine with remaining project/browser acceptance; this evidence does not assert those ran','externalDeployment':{'performed':False,'reason':'Only the authorized isolated local Docker Desktop single-manager stack was deployed; no remote server or external CI run asserted'},'stack':os.environ['STACK'],'tag':os.environ['TAG'],'activeModule':'rbac-ddd','prerequisites':json.loads((run/'prerequisites.json').read_text()),'sameFrontendImageIds':True,'samePersistentVolumes':True,'controlledSwitchEvidence':switches,'moduleSwitch':'Controlled stop-write barrier; all five old application/edge containers stopped before next deploy; no strict rolling single-writer claim','rounds':rounds,'externalLogDirectory':str(run)}
text=json.dumps(result,indent=2)+'\n';(root/'deploy/deployment-evidence.json').write_text(text,encoding='utf-8');(run/'deployment-evidence.json').write_text(text,encoding='utf-8')
print('Local final dual-module runtime acceptance passed; DDD remains active. No external deployment claimed.')
print('Evidence: '+str(run/'deployment-evidence.json'))
PY
