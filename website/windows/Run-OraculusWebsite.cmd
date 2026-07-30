@echo off
setlocal
cd /d "%~dp0app"
set "NODE_ENV=production"
set "HOSTNAME=127.0.0.1"
set "PORT=3000"
"%~dp0runtime\node.exe" "%~dp0app\server.js"
