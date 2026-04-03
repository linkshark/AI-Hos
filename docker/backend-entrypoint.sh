#!/usr/bin/env sh
set -eu

JAVA_OPTS="${JAVA_OPTS:-}"

exec sh -c "java ${JAVA_OPTS} -jar /app/app.jar"
