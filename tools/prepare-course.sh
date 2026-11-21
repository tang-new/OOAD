#!/usr/bin/env bash
# Export only approved tracked directories; never export the upstream root roster.
set -euo pipefail
variant=prepared
build=false
for flag in "$@"; do
 case "$flag" in
  --original) variant=original ;;
  --build) build=true ;;
  *) echo "Unknown course preparation option: $flag" >&2; exit 2 ;;
 esac
done
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
LOCK="$ROOT/course-integration/UPSTREAM.lock"
HASH=$(sed -n 's/^commit=//p' "$LOCK")
URL=$(sed -n 's/^url=//p' "$LOCK")
[[ "$HASH" =~ ^[0-9a-f]{40}$ && "$URL" == https://gitee.com/mingqcn/course-project-mgt.git ]] ||
 { echo 'Invalid course source lock' >&2; exit 1; }
CACHE=${COURSE_CACHE:-$ROOT/.cache/course-source}
mkdir -p "$CACHE"
if [[ -n ${COURSE_SOURCE:-} ]]; then SOURCE=$COURSE_SOURCE; else
 SOURCE="$CACHE/reference"
 # Fetch only the pinned tree, not every branch/history asset. Retry transport only;
 # successful retrieval still must pass the exact commit check below.
 if [[ ! -d "$SOURCE/.git" ]]; then
  git init -q "$SOURCE"
  git -C "$SOURCE" remote add origin "$URL"
 fi
 fetched=false
 for attempt in 1 2 3; do
  if git -c http.version=HTTP/1.1 -C "$SOURCE" fetch --depth=1 origin "$HASH"; then fetched=true; break; fi
  echo "Pinned course fetch transport attempt $attempt/3 failed" >&2
  [[ $attempt == 3 ]] || sleep "$attempt"
 done
 [[ $fetched == true ]] || { echo 'Pinned course source unavailable after bounded transport retries' >&2; exit 128; }
fi
SOURCE=$(cd "$SOURCE" && pwd)
CACHE=$(cd "$CACHE" && pwd)
git -C "$SOURCE" cat-file -e "$HASH^{commit}"
BUILD="$CACHE/$variant"
case "$BUILD/" in "$SOURCE/"*) echo 'Build cache must not modify the reference checkout' >&2; exit 1;; esac
rm -rf "$BUILD"; mkdir -p "$BUILD"
git -C "$SOURCE" archive "$HASH" backend api prototype deploy/stack.yml LICENSE | tar -xf - -C "$BUILD"
# No roster, screenshot, uploaded fixture, local environment or upstream node_modules enters build context.
rm -rf "$BUILD/prototype/screenshots" "$BUILD/prototype/node_modules" "$BUILD/prototype/dist"
find "$BUILD" -type f \( -iname '*.xlsx' -o -name '.env*' \) -delete
if [[ "$variant" == prepared ]]; then
 for patch in "$ROOT"/course-integration/patches/*.patch; do [[ ! -f "$patch" ]] || patch --batch --forward -p1 -d "$BUILD" < "$patch"; done
 cp -R "$ROOT/course-integration/overlay/." "$BUILD/"
fi
printf '%s\n' "$HASH" > "$BUILD/UPSTREAM_COMMIT"
echo "Prepared $BUILD from $HASH (MIT LICENSE retained)"
if [[ "$build" == true ]]; then
 : "${JAVA_HOME:?JDK 21 is required}"
 export PATH="$JAVA_HOME/bin:$PATH"
 "$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "21\.'
 bash "$ROOT/mvnw" -B -ntp -s "$ROOT/course-integration/maven-settings.xml" -gs "$ROOT/course-integration/maven-settings.xml" -f "$BUILD/backend/pom.xml" clean verify
 (cd "$ROOT/course-integration/frontend" && npm ci && npm run build)
fi
