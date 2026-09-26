#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
gradle connectedDebugAndroidTest --no-daemon
test_status=$?
mkdir -p build/screenshots
for image in dashboard-home dashboard-controls dashboard-about; do
    adb pull "/data/local/tmp/$image.png" build/screenshots/ || test_status=1
done
exit "$test_status"
