#!/usr/bin/env bash
echo "Forwarding Car App port 5277..."
adb forward tcp:5277 tcp:5277

DHU_PATH="${ANDROID_HOME:-$HOME/Android/Sdk}/extras/google/auto/desktop-head-unit"
if [ -f "$DHU_PATH" ]; then
    "$DHU_PATH"
else
    echo "Desktop Head Unit executable not found at $DHU_PATH"
fi
