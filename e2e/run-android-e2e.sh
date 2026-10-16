#!/bin/bash
# Runs the app's instrumented tests against a local OpenBenches backend.
# Needs Docker (e.g. Colima) and a running Android emulator (see e2e/README.md).
set -euo pipefail
cd "$(dirname "$0")/.."

e2e/backend/up.sh

if ! adb get-state >/dev/null 2>&1; then
  echo "No Android device connected. Start an emulator first, e.g.: emulator -avd openbenches_e2e" >&2
  exit 1
fi

# 10.0.2.2 is the development machine as seen from the Android emulator.
./gradlew connectedDebugAndroidTest \
  -Popenbenches.baseUrl=http://10.0.2.2:8000/ \
  "$@"
