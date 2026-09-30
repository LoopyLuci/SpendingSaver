@echo off
setlocal
cd /d %~dp0..\tauri-app
call npx tauri build --bundles nsis
if not exist ..\release mkdir ..\release
copy /Y src-tauri\target\release\bundle\nsis\*.exe ..\release\ >nul
echo NSIS installer copied to ..\release
endlocal
