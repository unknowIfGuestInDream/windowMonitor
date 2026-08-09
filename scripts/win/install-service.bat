@echo off
:: Install Windows Monitor as a Windows Service
:: Requires administrator privileges and WinSW (windowMonitor.exe) in the same directory

set SERVICE_NAME=WindowsMonitor
set SCRIPT_DIR=%~dp0

echo Installing Windows Monitor service...
"%SCRIPT_DIR%windowMonitor.exe" install
if %errorlevel% neq 0 (
    echo Failed to install service. Make sure you run this script as Administrator
    echo and that windowMonitor.exe (WinSW) is present in the same directory.
    pause
    goto END
)
echo Windows Monitor service installed successfully.
echo Starting service...
net start %SERVICE_NAME%
if %errorlevel% neq 0 (
    echo Service installed but could not be started automatically.
    echo You can start it manually from Services (services.msc).
)

:END
pause
