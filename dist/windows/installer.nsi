; Infinite Conquest Alpha 0.5.0 — Windows installer
; Built with NSIS on Linux. Bundles a trimmed Temurin 17 JRE; no Java install needed.

!include "MUI2.nsh"

Name "Infinite Conquest"
OutFile "InfiniteConquest-Alpha-0.5.0-Setup.exe"
InstallDir "$PROGRAMFILES64\InfiniteConquest"
InstallDirRegKey HKLM "Software\InfiniteConquest" "InstallDir"
RequestExecutionLevel admin
SetCompressor /SOLID lzma

!define MUI_ABORTWARNING
!define MUI_ICON "icon.ico"
!define MUI_UNICON "icon.ico"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\jre\bin\javaw.exe -jar $INSTDIR\app\infinite-conquest-gui.jar"
!define MUI_FINISHPAGE_RUN_TEXT "Launch Infinite Conquest"
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\README.txt"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "Game" SecGame
  SetOutPath "$INSTDIR"
  File "icon.ico"
  File "README.txt"
  SetOutPath "$INSTDIR\app"
  File /r "app\*.*"
  SetOutPath "$INSTDIR\jre"
  File /r "jre\*.*"

  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\InfiniteConquest" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteConquest" \
                   "DisplayName" "Infinite Conquest (Alpha 0.5.0)"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteConquest" \
                   "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteConquest" \
                   "DisplayIcon" "$INSTDIR\icon.ico"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteConquest" \
                   "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteConquest" \
                   "NoRepair" 1

  CreateDirectory "$SMPROGRAMS\Infinite Conquest"
  CreateShortcut "$SMPROGRAMS\Infinite Conquest\Infinite Conquest.lnk" \
                 "$INSTDIR\jre\bin\javaw.exe" \
                 '-jar "$INSTDIR\app\infinite-conquest-gui.jar"' \
                 "$INSTDIR\icon.ico" 0
  CreateShortcut "$SMPROGRAMS\Infinite Conquest\Uninstall.lnk" "$INSTDIR\Uninstall.exe" "" "$INSTDIR\icon.ico" 0
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\Infinite Conquest\Infinite Conquest.lnk"
  Delete "$SMPROGRAMS\Infinite Conquest\Uninstall.lnk"
  RMDir "$SMPROGRAMS\Infinite Conquest"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\jre"
  Delete "$INSTDIR\icon.ico"
  Delete "$INSTDIR\README.txt"
  RMDir "$INSTDIR"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\InfiniteConquest"
  DeleteRegKey HKLM "Software\InfiniteConquest"
SectionEnd
