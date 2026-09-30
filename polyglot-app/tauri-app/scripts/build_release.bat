@echo off
setlocal
cd /d %~dp0..\tauri-app
call npx tauri build --bundles nsis
if not exist ..\release mkdir ..\release
copy /Y src-tauri\target\release\tauri-app.exe ..\release\PolyglotApp.exe >nul
copy /Y src-tauri\target\release\bundle\nsis\*.exe ..\release\ >nul 2>&1 || echo no nsis output yet
copy /Y dist\* ..\release\ /S /Y >nul
echo Release artifacts copied to ..\release
endlocal
