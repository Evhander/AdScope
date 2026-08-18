#!/usr/bin/env sh
set -eu
GRADLE_VERSION="8.13"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_DIR="${HOME}/.gradle/wrapper/dists/adscope-gradle-${GRADLE_VERSION}"
GRADLE_HOME="${DIST_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${DIST_DIR}/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${DIST_DIR}"
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${ZIP_FILE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "curl or wget is required." >&2
    exit 1
  fi
  unzip -q -o "${ZIP_FILE}" -d "${DIST_DIR}"
fi

exec "${GRADLE_HOME}/bin/gradle" -p "${PROJECT_DIR}" "$@"
