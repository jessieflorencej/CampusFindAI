@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" %*
set "launchExitCode=%errorlevel%"
if not "%launchExitCode%"=="0" pause
exit /b %launchExitCode%
