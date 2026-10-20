@echo off
title FORGE Launcher
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" -Action Run

if errorlevel 1 (
    echo.
    echo FORGE could not start. Check the error above.
    pause
)