@echo off
:: Uninstall Windows Monitor Windows Service
:: Requires administrator privileges

set SERVICE_NAME=WindowsMonitor
set SCRIPT_DIR=%~dp0

echo Stopping Windows Monitor service...
net stop %SERVICE_NAME%

echo Uninstalling Windows Monitor service...
"%SCRIPT_DIR%windowMonitor.exe" uninstall
if %errorlevel% neq 0 (
    echo Failed to uninstall service. Make sure you run this script as Administrator
    echo and that windowMonitor.exe (WinSW) is present in the same directory.
    pause
    goto END
)
echo Windows Monitor service uninstalled successfully.

:END
pause
