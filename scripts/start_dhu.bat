@echo off
echo ========================================================
echo Starting Android Auto Desktop Head Unit (DHU) Bridge
echo ========================================================
echo Forwarding Car App port 5277 to connected device/emulator...
adb forward tcp:5277 tcp:5277

echo Launching Desktop Head Unit...
if exist "%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe" (
    "%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
) else (
    echo Note: desktop-head-unit.exe not found at standard SDK path.
    echo Ensure Android Auto Desktop Head Unit is installed via SDK Manager.
)
