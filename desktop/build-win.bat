@echo off
setlocal
cd /d "%~dp0"
call platforms\windows\build.bat
exit /b %errorlevel%
