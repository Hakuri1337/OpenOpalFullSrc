@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-OraculusWebsite.ps1"
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" (
  echo.
  echo Deployment failed. Exit code: %EXIT_CODE%
  pause
  exit /b %EXIT_CODE%
)
echo.
echo Deployment completed. Press any key to close.
pause >nul
exit /b 0
