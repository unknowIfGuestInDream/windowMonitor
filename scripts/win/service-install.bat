@echo off
:: Windows Monitor Service - Install Script
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
    echo Please download WinSW from https://github.com/winsw/winsw and rename it to windowmonitor-service.exe.
    pause
    exit /b 1
)

echo Installing Windows Monitor service...
"%~dp0windowmonitor-service.exe" install
if %errorlevel% == 0 (
    echo Windows Monitor service installed successfully.
    echo Starting service...
    "%~dp0windowmonitor-service.exe" start
    echo Service started.
) else (
    echo Failed to install Windows Monitor service.
)
pause
