@echo off
:: Windows Monitor Service - Uninstall Script
:: Requires administrator privileges and WinSW (windowmonitor-service.exe)

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo This script requires administrator privileges.
    echo Please right-click and select "Run as administrator".
    pause
    exit /b 1
)

if not exist "%~dp0windowmonitor-service.exe" (
    echo Error: windowmonitor-service.exe not found.
    pause
    exit /b 1
)

echo Stopping Windows Monitor service...
"%~dp0windowmonitor-service.exe" stop
echo Uninstalling Windows Monitor service...
"%~dp0windowmonitor-service.exe" uninstall
if %errorlevel% == 0 (
    echo Windows Monitor service uninstalled successfully.
) else (
    echo Failed to uninstall Windows Monitor service.
)
pause
