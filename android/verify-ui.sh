#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
gradle connectedDebugAndroidTest --no-daemon
test_status=$?
mkdir -p build/screenshots
for image in onboarding-terms onboarding-liability connection-home; do
    adb pull "/data/local/tmp/$image.png" build/screenshots/ || test_status=1
done
exit "$test_status"
