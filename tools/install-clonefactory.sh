#!/usr/bin/env bash
# Install the teacher's unchanged sources; never read/copy another project's settings.
set -euo pipefail
COMMIT=854d0e09e250bc5462267f63ecacdcff32161104
URL=https://gitee.com/mingqcn/spring-fundamentals.git
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
CACHE=${CLONEFACTORY_CACHE:-${HOME}/.cache/ooad-clonefactory}
mkdir -p "$CACHE"
CACHE=$(cd "$CACHE" && pwd)
case "$CACHE/" in "$ROOT/"*) echo 'Cache must be outside this repository' >&2; exit 1;; esac
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 installation}"
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq 'version "21\.' || { echo 'JDK 21 required' >&2; exit 1; }
if [[ -n ${CLONEFACTORY_SOURCE:-} ]]; then
  SOURCE=$(git -C "$CLONEFACTORY_SOURCE" rev-parse --show-toplevel)
else
  SOURCE="$CACHE/reference"
  if [[ ! -d "$SOURCE/.git" ]]; then
    git clone --no-checkout "$URL" "$SOURCE"
    git -C "$SOURCE" checkout --detach "$COMMIT"
  fi
fi
ACTUAL=$(git -C "$SOURCE" rev-parse HEAD)
[[ "$ACTUAL" == "$COMMIT" ]] || { echo "Commit mismatch: $ACTUAL" >&2; exit 1; }
git -C "$SOURCE" diff --exit-code "$COMMIT" -- clonefactory
# Export only tracked files from the verified commit, not credentials, settings or local artifacts.
BUILD=$(mktemp -d "$CACHE/build-XXXXXXXX")
trap 'rm -rf "$BUILD"' EXIT
git -C "$SOURCE" archive "$COMMIT" clonefactory | tar -xf - -C "$BUILD"
TREE=$(git -C "$SOURCE" rev-parse "$COMMIT:clonefactory")
[[ "$TREE" == 47f7cd9d170f37421962ff26d187cc880b29c44e ]] || { echo "Source tree mismatch: $TREE" >&2; exit 1; }
echo "Verified source commit: $ACTUAL"
echo "clonefactory tree: $TREE"
# Explicit credential-free settings override both user and global Maven configuration.
cat > "$BUILD/settings.xml" <<'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors><mirror><id>clonefactory-central</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors>
</settings>
XML
bash "$ROOT/mvnw" -B -ntp -s "$BUILD/settings.xml" -gs "$BUILD/settings.xml" \
  -Dproject.build.sourceEncoding=UTF-8 \
  -f "$BUILD/clonefactory/pom.xml" clean install "$@"
echo 'Installed cn.edu.xmu:clonefactory:0.0.1-SNAPSHOT from verified teacher source.'
