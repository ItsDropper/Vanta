; Vanta per-user installer. The versioned legacy ZIP layout remains compatible
; with existing Vanta updater releases: Vanta.exe, app/, runtime/, updater/.
!include "MUI2.nsh"

!macro VantaInstallerError message
  IfSilent +2
  MessageBox MB_ICONSTOP "${message}"
!macroend

!ifndef APP_VERSION
  !error "APP_VERSION must be supplied by the release workflow"
!endif

Name "Vanta"
Caption "Vanta"
BrandingText "Vanta Minecraft Launcher"
OutFile "VantaSetup.exe"
Icon "build\jpackage\icons\vanta.ico"
UninstallIcon "build\jpackage\icons\vanta.ico"
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
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "LegalCopyright" "Copyright © 2026 ItsDropper"

!define MUI_ICON "build\jpackage\icons\vanta.ico"
!define MUI_UNICON "build\jpackage\icons\vanta.ico"
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
Var PREVIOUS_DIR
Var LOG_PATH

Section "Vanta"
  StrCpy $LOG_PATH "$TEMP\VantaInstaller.log"
  FileOpen $9 "$LOG_PATH" w
  FileWrite $9 "Vanta installer started.$\r$\n"
  FileClose $9
  ClearErrors
  StrCpy $WORK_DIR "$TEMP\VantaInstaller"
  CreateDirectory "$WORK_DIR"
  StrCpy $VERSION_PATH "$WORK_DIR\version.txt"
  StrCpy $ZIP_PATH "$WORK_DIR\Vanta-release.zip"
  StrCpy $HASH_PATH "$WORK_DIR\Vanta-release.zip.sha256"

  IfFileExists "$SYSDIR\curl.exe" 0 CurlMissing
  DetailPrint "Checking the latest Vanta release..."
  nsExec::ExecToLog '"$SYSDIR\curl.exe" -L -sS --fail --retry 3 --connect-timeout 15 --max-time 60 -o "$WORK_DIR\release.json" "https://api.github.com/repos/ItsDropper/Vanta/releases/latest"'
  Pop $0
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "GitHub metadata request exit: $0$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "GitHub release metadata request returned: $0"
  StrCmp $0 "0" +2
    Goto DownloadError

  ; Parse JSON with the Windows inbox PowerShell JSON parser instead of
  ; depending on line formatting in GitHub's API response.
  SetOutPath "$PLUGINSDIR"
  File /oname=Read-VantaReleaseVersion.ps1 "release\read_latest_release_version.ps1"
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\Read-VantaReleaseVersion.ps1" -ReleaseMetadata "$WORK_DIR\release.json" -OutputPath "$VERSION_PATH" -ChecksumOutputPath "$WORK_DIR\expected-sha256.txt"'
  Pop $0
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Release version parser exit: $0$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Release version parser returned: $0"
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
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Release version validation: $0$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Release version validation returned: $0"
  StrCmp $0 "1" +2
    Goto InvalidVersion

  StrCpy $ZIP_PATH "$WORK_DIR\Vanta-$APP_VERSION.zip"
  StrCpy $HASH_PATH "$ZIP_PATH.sha256"
  StrCpy $PREVIOUS_DIR "$INSTDIR.previous"
  DetailPrint "Downloading Vanta $APP_VERSION..."
  nsExec::ExecToLog '"$SYSDIR\curl.exe" -L -sS --fail --retry 3 --retry-delay 2 --connect-timeout 15 --max-time 300 -o "$ZIP_PATH" "https://github.com/ItsDropper/Vanta/releases/download/v$APP_VERSION/Vanta-$APP_VERSION.zip"'
  Pop $0
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Release archive download exit: $0$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Release asset request returned: $0"
  StrCmp $0 "0" +2
    Goto DownloadError

  ; Always fetch the small checksum sidecar as a fallback. The verifier
  ; prefers the exact GitHub API digest file when that digest is available.
  DetailPrint "Downloading SHA-256 checksum sidecar..."
  nsExec::ExecToLog '"$SYSDIR\curl.exe" -L -sS --fail --retry 3 --connect-timeout 15 --max-time 60 -o "$HASH_PATH" "https://github.com/ItsDropper/Vanta/releases/download/v$APP_VERSION/Vanta-$APP_VERSION.zip.sha256"'
  Pop $0
  StrCmp $0 "0" +2
    Goto DownloadError

VerifyDownloadedPackage:
  DetailPrint "Verifying downloaded package..."
  SetOutPath "$PLUGINSDIR"
  File /oname=Verify-VantaChecksum.ps1 "release\verify_installer_checksum.ps1"
  nsExec::ExecToLog '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\Verify-VantaChecksum.ps1" -Archive "$ZIP_PATH" -Checksum "$HASH_PATH" -ExpectedSha256File "$WORK_DIR\expected-sha256.txt" -LogPath "$LOG_PATH"'
  Pop $0
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Checksum verifier exit: $0$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Checksum verifier returned: $0"
  StrCmp $0 "0" +2
    Goto ChecksumFailed

  RMDir /r "$WORK_DIR\staged"
  CreateDirectory "$WORK_DIR\staged"
  DetailPrint "Extracting Vanta..."
  nsExec::ExecToLog '"$SYSDIR\tar.exe" -xf "$ZIP_PATH" -C "$WORK_DIR\staged"'
  Pop $0
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Staging archive extraction exit: $0$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Archive extraction returned: $0"
  StrCmp $0 "0" +2
    Goto InvalidPackage

  ; Validate every component before touching the installed application.
  IfFileExists "$WORK_DIR\staged\Vanta.exe" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\runtime\lib\modules" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\app\Vanta.cfg" 0 InvalidPackage
  IfFileExists "$WORK_DIR\staged\updater\VantaUpdater.jar" 0 InvalidPackage

  ; Keep a rollback copy until the new installation passes final validation.
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Install directory selected: $INSTDIR$\r$\n"
  FileClose $9
  ClearErrors
  RMDir /r "$PREVIOUS_DIR"
  IfFileExists "$INSTDIR\Vanta.exe" 0 NoPreviousInstall
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Existing Vanta.exe detected; moving the previous install.$\r$\n"
  FileClose $9
  ClearErrors
  ClearErrors
  Rename "$INSTDIR" "$PREVIOUS_DIR"
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
  RMDir /r "$PREVIOUS_DIR"
  RMDir /r "$WORK_DIR"
  Goto InstallComplete

CurlMissing:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Failure: curl.exe missing.$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Installer failure: curl.exe missing."
  !insertmacro VantaInstallerError "Windows curl.exe was not found."
  Goto InstallFailure
InvalidVersion:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Failure: release version invalid or parser failed.$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Installer failure: invalid release version or version parser failed."
  !insertmacro VantaInstallerError "GitHub returned an invalid Vanta release version."
  Goto InstallFailure
DownloadError:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Failure: release asset download failed.$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Installer failure: release download failed."
  !insertmacro VantaInstallerError "Vanta could not download the latest release."
  Goto InstallFailure
ChecksumFailed:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Failure: package checksum failed.$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Installer failure: checksum verification failed."
  !insertmacro VantaInstallerError "The downloaded Vanta package failed SHA-256 verification."
  Goto InstallFailure
InvalidPackage:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Failure: package archive layout invalid.$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Installer failure: archive contents invalid."
  !insertmacro VantaInstallerError "The downloaded Vanta package is incomplete or has an unexpected layout."
  Goto InstallFailure
RestorePrevious:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "Failure: installed payload validation failed.$\r$\n"
  FileClose $9
  ClearErrors
  DetailPrint "Installer failure: installation payload validation failed; restoring backup."
  RMDir /r "$INSTDIR"
  IfFileExists "$PREVIOUS_DIR\Vanta.exe" 0 InstallFailure
  Rename "$PREVIOUS_DIR" "$INSTDIR"
  !insertmacro VantaInstallerError "Vanta installation failed. The previous installation was restored."
  Goto InstallFailure
InstallFailure:
  FileOpen $9 "$LOG_PATH" a
  FileSeek $9 0 END
  FileWrite $9 "InstallFailure reached.$\r$\n"
  FileClose $9
  ClearErrors
  Delete "$WORK_DIR\release.json"
  Delete "$VERSION_PATH"
  Delete "$ZIP_PATH"
  Delete "$HASH_PATH"
  RMDir /r "$WORK_DIR\staged"
  RMDir "$WORK_DIR"
  SetErrorLevel 1
  Abort
InstallComplete:
SectionEnd

Function un.onInit
  ; Resolve the install root before any uninstall section runs. Keep a copy
  ; outside the install tree so diagnostics survive recursive removal.
  ReadRegStr $INSTDIR HKCU "Software\Vanta" "InstallDir"
  StrCmp $INSTDIR "" 0 +2
    StrCpy $INSTDIR "$LOCALAPPDATA\Vanta"
  SetOutPath "$TEMP"
  FileOpen $9 "$TEMP\VantaUninstaller.log" w
  FileWrite $9 "un.onInit reached. Target: $INSTDIR$\r$\n"
  FileClose $9
FunctionEnd

Section "Uninstall"
  ; The uninstaller must not have its working directory inside the tree being
  ; removed. NSIS otherwise cannot remove the current directory on Windows.
  SetOutPath "$TEMP"
  FileOpen $9 "$TEMP\VantaUninstaller.log" a
  FileWrite $9 "Uninstall section started. Target: $INSTDIR$\r$\n"
  FileClose $9

  Delete "$SMPROGRAMS\Vanta\Vanta.lnk"
  RMDir "$SMPROGRAMS\Vanta"
  Delete "$DESKTOP\Vanta.lnk"

  ; Instance data in roaming AppData is intentionally never removed.
  Delete "$INSTDIR\Uninstall.exe"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Vanta"

  FileOpen $9 "$TEMP\VantaUninstaller.log" a
  FileWrite $9 "Uninstall section completed.$\r$\n"
  FileClose $9
SectionEnd

Function LaunchVanta
  ; Silent installer smoke tests must not start the GUI on the CI runner.
  IfSilent LaunchVantaDone
  Exec '"$INSTDIR\Vanta.exe"'
LaunchVantaDone:
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
