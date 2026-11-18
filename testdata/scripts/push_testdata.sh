#!/usr/bin/env bash
# Pushes the committed test files to the device so MediaStore indexes them (FUSE via /sdcard).
set -euo pipefail
cd "$(dirname "$0")/.."
adb push docs other bad us002 i18n /sdcard/Download/sidx-testdata/
echo "Pushed. Clean up with:"
echo "  adb shell rm -r /sdcard/Download/sidx-testdata"
