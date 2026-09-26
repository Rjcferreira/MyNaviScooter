#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
gradle connectedDebugAndroidTest --no-daemon
test_status=$?
mkdir -p build/screenshots
adb pull /sdcard/Android/data/com.mynavisccooter.capture/files/. build/screenshots/
exit "$test_status"
