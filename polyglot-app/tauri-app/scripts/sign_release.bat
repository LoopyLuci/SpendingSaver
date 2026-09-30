@echo off
setlocal
set SIGNTOOL=signtool
set CERT_THUMBPRINT=%~1
set TIMESTAMP_SERVER=%~2

if "%CERT_THUMBPRINT%"=="" (
  echo Usage: sign_release.bat [cert-thumbprint] [timestamp-server]
  echo Example: sign_release.bat ABC123DEF456 http://timestamp.digicert.com
  exit /b 1
)

if "%TIMESTAMP_SERVER%"=="" set TIMESTAMP_SERVER=http://timestamp.digicert.com

echo Signing release binary...
%SIGNTOOL% sign /sha1 %CERT_THUMBPRINT% /tr %TIMESTAMP_SERVER% /td sha256 /fd sha256 /v src-tauri\target\release\tauri-app.exe
if %ERRORLEVEL% neq 0 (
  echo Failed to sign binary
  exit /b 1
)

echo Verifying signature...
%SIGNTOOL% verify /pa src-tauri\target\release\tauri-app.exe
if %ERRORLEVEL% neq 0 (
  echo Signature verification failed
  exit /b 1
)

echo.
echo Code signing completed successfully.
endlocal
