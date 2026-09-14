#!/usr/bin/env bash
set -e

TARGET_IP=${1:-"10.42.42.1"}
TARGET_PORT=${2:-"5555"}

echo "Connecting to ADB target at ${TARGET_IP}:${TARGET_PORT}..."
adb connect "${TARGET_IP}:${TARGET_PORT}"

echo "Installing debug APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Starting Midgley Companion Activity..."
adb shell am start -n net.n2yti.midgley.auto.debug/net.n2yti.midgley.auto.ui.MainActivity

echo "Deployment complete!"
