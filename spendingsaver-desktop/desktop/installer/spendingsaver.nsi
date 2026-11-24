; SpendingSaver Windows installer (NSIS). Installs for the current user only, so it does not need
; administrator rights. Uninstalling removes the program but never your data.
Unicode true
!include "MUI2.nsh"

!ifndef VERSION
  !define VERSION "0.0.0"
!endif
!ifndef EXE
  !error "pass -DEXE=<path to spendingsaver.exe>"
!endif
!ifndef OUT
  !define OUT "SpendingSaver-Setup.exe"
!endif

Name "SpendingSaver"
OutFile "${OUT}"
InstallDir "$LOCALAPPDATA\Programs\SpendingSaver"
InstallDirRegKey HKCU "Software\SpendingSaver" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
BrandingText "SpendingSaver ${VERSION}"

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "SpendingSaver"
VIAddVersionKey "FileDescription" "SpendingSaver installer"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "MIT License"

!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\spendingsaver.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Start SpendingSaver"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Install"
  SetOutPath "$INSTDIR"
  File "${EXE}"
!ifdef AGENT
  File "${AGENT}"
!endif
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\SpendingSaver"
  CreateShortCut "$SMPROGRAMS\SpendingSaver\SpendingSaver.lnk" "$INSTDIR\spendingsaver.exe"
  CreateShortCut "$SMPROGRAMS\SpendingSaver\Uninstall SpendingSaver.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortCut "$DESKTOP\SpendingSaver.lnk" "$INSTDIR\spendingsaver.exe"
  WriteRegStr HKCU "Software\SpendingSaver" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "DisplayName" "SpendingSaver"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "Publisher" "SpendingSaver contributors"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "DisplayIcon" "$INSTDIR\spendingsaver.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver" "NoRepair" 1
SectionEnd

Section "Uninstall"
  Delete "$INSTDIR\spendingsaver.exe"
  Delete "$INSTDIR\ss-link-agent.exe"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  Delete "$SMPROGRAMS\SpendingSaver\SpendingSaver.lnk"
  Delete "$SMPROGRAMS\SpendingSaver\Uninstall SpendingSaver.lnk"
  RMDir "$SMPROGRAMS\SpendingSaver"
  Delete "$DESKTOP\SpendingSaver.lnk"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SpendingSaver"
  DeleteRegKey HKCU "Software\SpendingSaver"
  ; Your data (%APPDATA%\SpendingSaver) is left in place on purpose.
SectionEnd
