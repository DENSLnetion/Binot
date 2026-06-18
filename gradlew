#!/usr/bin/env sh
# Minimal Gradle Wrapper launcher
set -e

# Resolve script dir
SCRIPT_DIR="$(cd "$(dirname "$0")" >/dev/null 2>&1 && pwd)"

WRAPPER_JAR="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: gradle-wrapper.jar tidak ditemukan di $SCRIPT_DIR/gradle/wrapper/"
  echo "Silakan download gradle-wrapper.jar dari distribusi Gradle dan tempatkan di gradle/wrapper/"
  exit 1
fi

exec java -jar "$WRAPPER_JAR" "$@"
