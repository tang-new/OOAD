#!/usr/bin/env bash
set -euo pipefail
# 固定 Google Java Format 版本及校验值；只格式化当前 DDD 模块源码。
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/rbac-ddd-formatter"
JAR="$CACHE/google-java-format-1.24.0-all-deps.jar"
mkdir -p "$CACHE"
if [[ ! -f "$JAR" ]]; then
  curl -fL --retry 2 'https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar' -o "$JAR"
fi
printf '%s  %s\n' '812f805f58112460edf01bf202a8e61d0fd1f35c0d4fabd54220640776ec57a1' "$JAR" | sha256sum -c -
cd "$ROOT"
find src/main/java src/test/java -name '*.java' -print0 | xargs -0 "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar "$JAR" --aosp --replace
