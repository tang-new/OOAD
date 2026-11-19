#!/usr/bin/env bash
# Install the teacher's unchanged sources; never read/copy another project's settings.
set -euo pipefail
COMMIT=e0c2b1e54b29207f13088b80eb7e49c22152fdcf
URL=https://gitee.com/mingqcn/spring-fundamentals.git
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CACHE=${CLONEFACTORY_CACHE:-${HOME}/.cache/ooad-clonefactory-0.0.2}
mkdir -p "$CACHE"
CACHE=$(cd "$CACHE" && pwd)
case "$CACHE/" in "$ROOT/"*) echo 'Cache must be outside this repository' >&2; exit 1;; esac
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 installation}"
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "21\.' || { echo 'JDK 21 required' >&2; exit 1; }
if [[ -n ${CLONEFACTORY_SOURCE:-} ]]; then
  SOURCE=$(git -C "$CLONEFACTORY_SOURCE" rev-parse --show-toplevel)
  ACTUAL=$(git -C "$SOURCE" rev-parse HEAD)
  [[ "$ACTUAL" == "$COMMIT" ]] || { echo "Commit mismatch: $ACTUAL" >&2; exit 1; }
  git -C "$SOURCE" diff --exit-code "$COMMIT" -- clonefactory
else
  SOURCE="$CACHE/reference"
  if [[ ! -d "$SOURCE/.git" ]]; then
    git init -q "$SOURCE"
    git -C "$SOURCE" remote add origin "$URL"
  fi
  if ! git -C "$SOURCE" cat-file -e "$COMMIT^{commit}" 2>/dev/null; then
    fetched=false
    for attempt in 1 2 3; do
      if git -c http.version=HTTP/1.1 -C "$SOURCE" fetch --depth=1 origin "$COMMIT"; then fetched=true; break; fi
      echo "Pinned CloneFactory fetch transport attempt $attempt/3 failed" >&2
      [[ $attempt == 3 ]] || sleep "$attempt"
    done
    [[ $fetched == true ]] || { echo 'Pinned CloneFactory source unavailable after bounded transport retries' >&2; exit 128; }
  fi
  ACTUAL=$(git -C "$SOURCE" rev-parse "$COMMIT^{commit}")
fi
# Export only tracked files from the verified commit, not credentials, settings or local artifacts.
BUILD=$(mktemp -d "$CACHE/build-XXXXXXXX")
trap 'rm -rf "$BUILD"' EXIT
git -C "$SOURCE" archive "$COMMIT" clonefactory | tar -xf - -C "$BUILD"
TREE=$(git -C "$SOURCE" rev-parse "$COMMIT:clonefactory")
[[ "$TREE" == 1a5158cb19357df59068af279875052bae1917d0 ]] || { echo "Source tree mismatch: $TREE" >&2; exit 1; }
echo "Verified source commit: $ACTUAL"
echo "clonefactory tree: $TREE"
# Explicit credential-free settings override both user and global Maven configuration.
cat > "$BUILD/settings.xml" <<'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors><mirror><id>central</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors>
</settings>
XML
bash "$ROOT/mvnw" -B -ntp -s "$BUILD/settings.xml" -gs "$BUILD/settings.xml" \
  -Dproject.build.sourceEncoding=UTF-8 \
  -f "$BUILD/clonefactory/pom.xml" clean install "$@"
echo 'Installed cn.edu.xmu:clonefactory:0.0.2 from verified teacher source.'
