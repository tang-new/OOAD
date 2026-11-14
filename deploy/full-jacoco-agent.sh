#!/usr/bin/env bash
# Source this after Maven package. Honor the actual Maven local repository/settings.
# Caller supplies normal Maven options, e.g. -s deploy/maven-settings-huawei.xml.
set -euo pipefail
_jacoco_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
_jacoco_repo_file=$(mktemp)
_jacoco_output="$_jacoco_repo_file"
if [[ ${OS:-} == Windows_NT ]]; then _jacoco_output=$(cygpath -m "$_jacoco_repo_file"); fi
if ! bash "$_jacoco_root/mvnw" -B -ntp -q "$@" help:evaluate -Dexpression=settings.localRepository -Doutput="$_jacoco_output"; then
 rm -f "$_jacoco_repo_file"
 echo 'Could not resolve the Maven local repository for JaCoCo' >&2
 return 1 2>/dev/null || exit 1
fi
_jacoco_repo=$(tr -d '\r\n' < "$_jacoco_repo_file")
rm -f "$_jacoco_repo_file"
ACCEPTANCE_JACOCO_AGENT=$(python - "$_jacoco_repo" <<'PY'
import pathlib,sys
repo=pathlib.Path(sys.argv[1])
files=list((repo/'org/jacoco/org.jacoco.agent/0.8.12').glob('*runtime.jar'))
if len(files)!=1 or not files[0].is_file():
 raise SystemExit('Expected exactly one Maven-cached JaCoCo 0.8.12 runtime agent; run clean package first')
print(files[0].resolve().as_posix())
PY
)
export ACCEPTANCE_JACOCO_AGENT
# A forced Windows process termination does not run the JVM shutdown hook.
# Resolve the official dump client as well; fixture dumps the owned loopback
# agent before stopping Java, rather than claiming missing execution data.
bash "$_jacoco_root/mvnw" -N -B -ntp "$@" dependency:get -Dtransitive=false -Dartifact=org.jacoco:org.jacoco.cli:0.8.12:jar:nodeps
ACCEPTANCE_JACOCO_CLI=$(python - "$_jacoco_repo" <<'PY'
import pathlib,sys
cli=pathlib.Path(sys.argv[1])/'org/jacoco/org.jacoco.cli/0.8.12/org.jacoco.cli-0.8.12-nodeps.jar'
if not cli.is_file(): raise SystemExit('Official JaCoCo dump CLI was not resolved')
print(cli.resolve().as_posix())
PY
)
export ACCEPTANCE_JACOCO_CLI
unset _jacoco_root _jacoco_repo_file _jacoco_output _jacoco_repo
