; Vanta per-user installer. The versioned legacy ZIP layout remains compatible
; with existing Vanta updater releases: Vanta.exe, app/, runtime/, updater/.
!include "MUI2.nsh"

!ifndef APP_VERSION
  !error "APP_VERSION must be supplied by the release workflow"
!endif

Name "Vanta"
Caption "Vanta"
BrandingText "Vanta Minecraft Launcher"
OutFile "VantaSetup.exe"
InstallDir "$LOCALAPPDATA\Vanta"
InstallDirRegKey HKCU "Software\Vanta" "InstallDir"
RequestExecutionLevel user
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${APP_VERSION}.0"
VIFileVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName" "Vanta"
VIAddVersionKey "CompanyName" "ItsDropper"
VIAddVersionKey "FileDescription" "Vanta Minecraft Launcher"
VIAddVersionKey "LegalCopyright" "Copyright © 2026 ItsDropper"

!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "Welcome to Vanta"
!define MUI_WELCOMEPAGE_TEXT "Vanta is a Minecraft launcher focused on reliable instances and a clean desktop experience.$\r$\n$\r$\nThis setup installs Vanta for the current Windows user. No administrator access is required."
!define MUI_FINISHPAGE_TITLE "Vanta is ready"
!define MUI_FINISHPAGE_TEXT "Vanta has been installed successfully."
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Launch Vanta"
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchVanta
!define MUI_DIRECTORYPAGE_TEXT_TOP "Vanta will be installed in:$\r$\n$\r$\n$INSTDIR"
!define MUI_DIRECTORYPAGE_TEXT_DESTINATION "Installation folder"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_LANGUAGE "English"

Var APP_VERSION
Var WORK_DIR
Var ZIP_PATH
Var HASH_PATH
Var VERSION_PATH

Section "Vanta"
  StrCpy $WORK_DIR "$TEMP\VantaInstaller"
  CreateDirectory "$WORK_DIR"
  StrCpy $VERSION_PATH "$WORK_DIR\version.txt"
  StrCpy $ZIP_PATH "$WORK_DIR\Vanta-release.zip"
  StrCpy $HASH_PATH "$WORK_DIR\Vanta-release.zip.sha256"

  IfFileExists "$SYSDIR\curl.exe" 0 CurlMissing
  DetailPrint "Checking the latest Vanta release..."
  nsExec::ExecToLog '"$SYSDIR\curl.exe" -L -sS --fail --retry 3 --connect-timeout 15 --max-time 60 -o "$WORK_DIR\release.json" "https://api.github.com/repos/ItsDropper/Vanta/releases/latest"'
  Pop $0
  StrCmp $0 "0" +2
    Goto DownloadError

  ; Parse JSON with the Windows inbox PowerShell JSON parser instead of
  ; depending on line formatting in GitHub's API response.
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "(Get-Content -LiteralPath ''$WORK_DIR\release.json'' -Raw | ConvertFrom-Json).tag_name.TrimStart(''v'') | Set-Content -LiteralPath ''$VERSION_PATH'' -NoNewline"'
  Pop $0
  StrCmp $0 "0" +2
    Goto InvalidVersion
  IfFileExists "$VERSION_PATH" 0 InvalidVersion
  FileOpen $1 "$VERSION_PATH" r
  FileRead $1 $APP_VERSION
  FileClose $1
  StrCmp $APP_VERSION "" InvalidVersion

  ; Reject URL/path metacharacters before interpolating the tag into a URL.
  Push $APP_VERSION
  Call ValidateVersion
  Pop $0
  StrCmp $0 "1" +2
    Goto InvalidVersion

  StrCpy $ZIP_PATH "$WORK_DIR\Vanta-$APP_VERSION.zip"
  StrCpy $HASH_PATH "$ZIP_PATH.sha256"
  DetailPrint "Downloading Vanta $APP_VERSION..."
  nsExec::ExecToLog '"$SYSDIR\curl.exe" -L -sS --fail --retry 3 --retry-delay 2 --connect-timeout 15 --max-time 300 -o "$ZIP_PATH" "https://github.com/ItsDropper/Vanta/releases/download/v$APP_VERSION/Vanta-$APP_VERSION.zip"'
  Pop $0
  StrCmp $0 "0" +2
    Goto DownloadError

  DetailPrint "Downloading SHA-256 checksum..."
  nsExec::ExecToLog '"$SYSDIR\curl.exe" -L -sS --fail --retry 3 --connect-timeout 15 --max-time 60 -o "$HASH_PATH" "https://github.com/ItsDropper/Vanta/releases/download/v$APP_VERSION/Vanta-$APP_VERSION.zip.sha256"'
  Pop $0
  StrCmp $0 "0" +2
    Goto DownloadError

  DetailPrint "Verifying downloaded package..."
  SetOutPath "$PLUGINSDIR"
  File /oname=Verify-VantaChecksum.ps1 "release\verify_installer_checksum.ps1"
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\Verify-VantaChecksum.ps1" -Archive "$ZIP_PATH" -Checksum "$HASH_PATH"'
  Pop $0
  StrCmp $0 "0" +2
    Goto ChecksumFailed

  RMDir /r "$WORK_DIR\staged"
  CreateDirectory "$WORK_DIR\staged"
  DetailPrint "Extracting Vanta..."
  nsExec::ExecToLog '"$SYSDIR\tar.exe" -xf "$ZIP_PATH" -C "$WORK_DIR\staged"'
  Pop $0
  StrCmp $0 "0" +2
    Goto InvalidPackage

  ; Validate every component before touching the installed application.
  IfFileExists "$WORK_DIR\staged\Vanta.exe" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\runtime\lib\modules" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\app\Vanta.cfg" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\updater\VantaUpdater.jar" 0 InvalidPackage

  ; Keep a rollback copy until the new installation passes final validation.
  RMDir /r "$WORK_DIR\previous"
  IfFileExists "$INSTDIR\Vanta.exe" 0 NoPreviousInstall
  Rename "$INSTDIR" "$WORK_DIR\previous"
  IfErrors InstallFailure
NoPreviousInstall:
  CreateDirectory "$INSTDIR"
  nsExec::ExecToLog '"$SYSDIR\tar.exe" -xf "$ZIP_PATH" -C "$INSTDIR"'
  Pop $0
  StrCmp $0 "0" +2
    Goto RestorePrevious
  IfFileExists "$INSTDIR\Vanta.exe" 0 RestorePrevious
  IfFileExists "$INSTDIR\runtime\lib\modules" 0 RestorePrevious
  IfFileExists "$INSTDIR\app\Vanta.cfg" 0 RestorePrevious
  IfFileExists "$INSTDIR\updater\VantaUpdater.jar" 0 RestorePrevious

  WriteRegStr HKCU "Software\Vanta" "InstallDir" "$INSTDIR"
  CreateDirectory "$SMPROGRAMS\Vanta"
  CreateShortcut "$SMPROGRAMS\Vanta\Vanta.lnk" "$INSTDIR\Vanta.exe" "" "$INSTDIR\Vanta.exe" 0
  CreateShortcut "$DESKTOP\Vanta.lnk" "$INSTDIR\Vanta.exe" "" "$INSTDIR\Vanta.exe" 0
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  RMDir /r "$WORK_DIR\previous"
  RMDir /r "$WORK_DIR"
  Goto InstallComplete

CurlMissing:
  MessageBox MB_ICONSTOP "Windows curl.exe was not found."
  Goto InstallFailure
InvalidVersion:
  MessageBox MB_ICONSTOP "GitHub returned an invalid Vanta release version."
  Goto InstallFailure
DownloadError:
  MessageBox MB_ICONSTOP "Vanta could not download the latest release."
  Goto InstallFailure
ChecksumFailed:
  MessageBox MB_ICONSTOP "The downloaded Vanta package failed SHA-256 verification."
  Goto InstallFailure
InvalidPackage:
  MessageBox MB_ICONSTOP "The downloaded Vanta package is incomplete or has an unexpected layout."
  Goto InstallFailure
RestorePrevious:
  RMDir /r "$INSTDIR"
  IfFileExists "$WORK_DIR\previous\Vanta.exe" 0 InstallFailure
  Rename "$WORK_DIR\previous" "$INSTDIR"
  MessageBox MB_ICONSTOP "Vanta installation failed. The previous installation was restored."
  Goto InstallFailure
InstallFailure:
  Delete "$WORK_DIR\release.json"
  Delete "$VERSION_PATH"
  Delete "$ZIP_PATH"
  Delete "$HASH_PATH"
  RMDir /r "$WORK_DIR\staged"
  RMDir "$WORK_DIR"
  Abort
InstallComplete:
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\Vanta\Vanta.lnk"
  RMDir "$SMPROGRAMS\Vanta"
  Delete "$DESKTOP\Vanta.lnk"
  DeleteRegKey HKCU "Software\Vanta"
  ; Instance data in roaming AppData is intentionally never removed.
  RMDir /r "$INSTDIR"
SectionEnd

Function LaunchVanta
  Exec '"$INSTDIR\Vanta.exe"'
FunctionEnd

Function ValidateVersion
  Exch $0
  Push $1
  Push $2
  Push $3
  Push $4
  StrCpy $1 0
  StrCpy $2 0
  StrLen $3 $0
VersionLoop:
  IntCmp $1 $3 VersionEnd VersionBody VersionEnd
VersionBody:
  StrCpy $4 $0 1 $1
  StrCmp $4 "." VersionDot
  StrCmp $4 "0" VersionDigit
  StrCmp $4 "1" VersionDigit
  StrCmp $4 "2" VersionDigit
  StrCmp $4 "3" VersionDigit
  StrCmp $4 "4" VersionDigit
  StrCmp $4 "5" VersionDigit
  StrCmp $4 "6" VersionDigit
  StrCmp $4 "7" VersionDigit
  StrCmp $4 "8" VersionDigit
  StrCmp $4 "9" VersionDigit VersionInvalid
VersionDot:
  IntOp $2 $2 + 1
VersionDigit:
  IntOp $1 $1 + 1
  Goto VersionLoop
VersionEnd:
  StrCmp $2 "2" 0 VersionInvalid
  StrCpy $0 "1"
  Goto VersionDone
VersionInvalid:
  StrCpy $0 "0"
VersionDone:
  Pop $4
  Pop $3
  Pop $2
  Pop $1
  Exch $0
FunctionEnd
' -or $actual -ne $expected) { exit 1 }"'
  Pop $0
  StrCmp $0 "0" +2
    Goto ChecksumFailed

  RMDir /r "$WORK_DIR\staged"
  CreateDirectory "$WORK_DIR\staged"
  DetailPrint "Extracting Vanta..."
  nsExec::ExecToLog '"$SYSDIR\tar.exe" -xf "$ZIP_PATH" -C "$WORK_DIR\staged"'
  Pop $0
  StrCmp $0 "0" +2
    Goto InvalidPackage

  ; Validate every component before touching the installed application.
  IfFileExists "$WORK_DIR\staged\Vanta.exe" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\runtime\lib\modules" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\app\Vanta.cfg" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\updater\VantaUpdater.jar" 0 InvalidPackage

  ; Keep a rollback copy until the new installation passes final validation.
  RMDir /r "$WORK_DIR\previous"
  IfFileExists "$INSTDIR\Vanta.exe" 0 NoPreviousInstall
  Rename "$INSTDIR" "$WORK_DIR\previous"
  IfErrors InstallFailure
NoPreviousInstall:
  CreateDirectory "$INSTDIR"
  nsExec::ExecToLog '"$SYSDIR\tar.exe" -xf "$ZIP_PATH" -C "$INSTDIR"'
  Pop $0
  StrCmp $0 "0" +2
    Goto RestorePrevious
  IfFileExists "$INSTDIR\Vanta.exe" 0 RestorePrevious
  IfFileExists "$INSTDIR\runtime\lib\modules" 0 RestorePrevious
  IfFileExists "$INSTDIR\app\Vanta.cfg" 0 RestorePrevious
  IfFileExists "$INSTDIR\updater\VantaUpdater.jar" 0 RestorePrevious

  WriteRegStr HKCU "Software\Vanta" "InstallDir" "$INSTDIR"
  CreateDirectory "$SMPROGRAMS\Vanta"
  CreateShortcut "$SMPROGRAMS\Vanta\Vanta.lnk" "$INSTDIR\Vanta.exe" "" "$INSTDIR\Vanta.exe" 0
  CreateShortcut "$DESKTOP\Vanta.lnk" "$INSTDIR\Vanta.exe" "" "$INSTDIR\Vanta.exe" 0
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  RMDir /r "$WORK_DIR\previous"
  RMDir /r "$WORK_DIR"
  Goto InstallComplete

CurlMissing:
  MessageBox MB_ICONSTOP "Windows curl.exe was not found."
  Goto InstallFailure
InvalidVersion:
  MessageBox MB_ICONSTOP "GitHub returned an invalid Vanta release version."
  Goto InstallFailure
DownloadError:
  MessageBox MB_ICONSTOP "Vanta could not download the latest release."
  Goto InstallFailure
ChecksumFailed:
  MessageBox MB_ICONSTOP "The downloaded Vanta package failed SHA-256 verification."
  Goto InstallFailure
InvalidPackage:
  MessageBox MB_ICONSTOP "The downloaded Vanta package is incomplete or has an unexpected layout."
  Goto InstallFailure
RestorePrevious:
  RMDir /r "$INSTDIR"
  IfFileExists "$WORK_DIR\previous\Vanta.exe" 0 InstallFailure
  Rename "$WORK_DIR\previous" "$INSTDIR"
  MessageBox MB_ICONSTOP "Vanta installation failed. The previous installation was restored."
  Goto InstallFailure
InstallFailure:
  Delete "$WORK_DIR\release.json"
  Delete "$VERSION_PATH"
  Delete "$ZIP_PATH"
  Delete "$HASH_PATH"
  RMDir /r "$WORK_DIR\staged"
  RMDir "$WORK_DIR"
  Abort
InstallComplete:
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\Vanta\Vanta.lnk"
  RMDir "$SMPROGRAMS\Vanta"
  Delete "$DESKTOP\Vanta.lnk"
  DeleteRegKey HKCU "Software\Vanta"
  ; Instance data in roaming AppData is intentionally never removed.
  RMDir /r "$INSTDIR"
SectionEnd

Function LaunchVanta
  Exec '"$INSTDIR\Vanta.exe"'
FunctionEnd

Function ValidateVersion
  Exch $0
  Push $1
  Push $2
  Push $3
  Push $4
  StrCpy $1 0
  StrCpy $2 0
  StrLen $3 $0
VersionLoop:
  IntCmp $1 $3 VersionEnd VersionBody VersionEnd
VersionBody:
  StrCpy $4 $0 1 $1
  StrCmp $4 "." VersionDot
  StrCmp $4 "0" VersionDigit
  StrCmp $4 "1" VersionDigit
  StrCmp $4 "2" VersionDigit
  StrCmp $4 "3" VersionDigit
  StrCmp $4 "4" VersionDigit
  StrCmp $4 "5" VersionDigit
  StrCmp $4 "6" VersionDigit
  StrCmp $4 "7" VersionDigit
  StrCmp $4 "8" VersionDigit
  StrCmp $4 "9" VersionDigit VersionInvalid
VersionDot:
  IntOp $2 $2 + 1
VersionDigit:
  IntOp $1 $1 + 1
  Goto VersionLoop
VersionEnd:
  StrCmp $2 "2" 0 VersionInvalid
  StrCpy $0 "1"
  Goto VersionDone
VersionInvalid:
  StrCpy $0 "0"
VersionDone:
  Pop $4
  Pop $3
  Pop $2
  Pop $1
  Exch $0
FunctionEnd
