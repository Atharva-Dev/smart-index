#!/usr/bin/env bash
# Creates the 60 MB sparse PDF on the device (never committed; QA Scope 3 P2, > 50 MB).
set -euo pipefail
adb shell 'printf "%%PDF-1.4\n" > /sdcard/Download/sidx-huge-60mb.pdf && truncate -s 60M /sdcard/Download/sidx-huge-60mb.pdf'
echo "Created /sdcard/Download/sidx-huge-60mb.pdf. Clean up with:"
echo "  adb shell rm /sdcard/Download/sidx-huge-60mb.pdf"
