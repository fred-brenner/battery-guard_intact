#!/bin/sh
set -eu

APP_HOME=$(cd "${0%/*}" >/dev/null 2>&1 && pwd -P)
GRADLE_VERSION=8.10.2
GRADLE_DIST_URL=https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
GRADLE_ROOT=$APP_HOME/.gradle-local
GRADLE_HOME=$GRADLE_ROOT/gradle-${GRADLE_VERSION}

if [ -z "${JAVA_HOME:-}" ] && [ -x "$APP_HOME/.jdk/current/bin/java" ]; then
    JAVA_HOME=$APP_HOME/.jdk/current
    export JAVA_HOME
    PATH=$JAVA_HOME/bin:$PATH
    export PATH
fi

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$GRADLE_ROOT"
    tmp_zip=$(mktemp)
    trap 'rm -f "$tmp_zip"' EXIT
    curl -L --fail --retry 3 -o "$tmp_zip" "$GRADLE_DIST_URL"
    python3 - "$tmp_zip" "$GRADLE_ROOT" <<'PY'
import sys
from pathlib import Path
from zipfile import ZipFile

zip_path = Path(sys.argv[1])
dest = Path(sys.argv[2])
with ZipFile(zip_path) as archive:
    archive.extractall(dest)
PY
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
