# ============================================================
# VANTA LAUNCHER - BUILD SCRIPT
# ============================================================

# ------------------------------------------------------------
# JAVA 21
# ------------------------------------------------------------

$env:JAVA_HOME="C:\Users\hugoh\AppData\Local\Programs\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:Path="$env:JAVA_HOME\bin;$env:Path"


# ------------------------------------------------------------
# CLEAN
# ------------------------------------------------------------

Write-Host ""
Write-Host "=== CLEANING ==="

.\gradlew clean

if ($LASTEXITCODE -ne 0) {
    Write-Host "Gradle clean failed." -ForegroundColor Red
    exit 1
}

Remove-Item -Recurse -Force .\build\jpackage -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\build\runtime -ErrorAction SilentlyContinue


# ------------------------------------------------------------
# SHADOW JAR
# ------------------------------------------------------------

Write-Host ""
Write-Host "=== BUILDING SHADOW JAR ==="

.\gradlew shadowJar

if ($LASTEXITCODE -ne 0) {
    Write-Host "Shadow JAR build failed." -ForegroundColor Red
    exit 1
}


# ------------------------------------------------------------
# FIND SHADOW JAR
# ------------------------------------------------------------

Write-Host ""
Write-Host "=== VERIFYING JAR ==="

$shadowJar =
    Get-ChildItem ".\build\libs\*-all.jar" |
    Select-Object -First 1

if (-not $shadowJar) {

    Write-Host ""
    Write-Host "ERROR: Shadow JAR was not created." -ForegroundColor Red
    Write-Host ""
    Write-Host "Files currently in build\libs:"

    Get-ChildItem ".\build\libs"

    exit 1
}

$jarName = $shadowJar.Name

Write-Host ""
Write-Host "Using Shadow JAR:"
Write-Host $jarName

Write-Host ""
Write-Host "Size:"
Write-Host (
    "{0:N2} MB" -f (
        $shadowJar.Length / 1MB
    )
)


# ------------------------------------------------------------
# JAVA TOOLS
# ------------------------------------------------------------

Write-Host ""
Write-Host "=== JAVA ==="

java --version

Write-Host ""
Write-Host "=== JLINK ==="

jlink --version

Write-Host ""
Write-Host "=== JPACKAGE ==="

jpackage --version


# ------------------------------------------------------------
# BUILD RUNTIME
# ------------------------------------------------------------

Write-Host ""
Write-Host "=== BUILDING RUNTIME ==="

jlink `
 --module-path "C:\Users\hugoh\AppData\Local\Programs\Eclipse Adoptium\jdk-21.0.11.10-hotspot\jmods;C:\Java\javafx-jmods-21.0.12\openjfx-21.0.12_windows-x64_bin-jmods\javafx-jmods-21.0.12" `
 --add-modules java.se,java.desktop,java.logging,java.naming,java.net.http,jdk.unsupported,jdk.zipfs,jdk.crypto.ec,jdk.naming.dns,jdk.management,javafx.controls,javafx.graphics `
 --output build\runtime

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "jlink failed." -ForegroundColor Red
    exit 1
}


# ------------------------------------------------------------
# PACKAGE
# ------------------------------------------------------------

Write-Host ""
Write-Host "=== PACKAGING VANTA ==="

jpackage `
 --type app-image `
 --dest build\jpackage `
 --input build\libs `
 --main-jar $jarName `
 --main-class org.example.Main `
 --name Vanta `
 --runtime-image build\runtime

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "jpackage failed." -ForegroundColor Red
    exit 1
}


# ------------------------------------------------------------
# VERIFY PACKAGE
# ------------------------------------------------------------

$exe = ".\build\jpackage\Vanta\Vanta.exe"

if (-not (Test-Path $exe)) {

    Write-Host ""
    Write-Host "ERROR: Vanta.exe was not created." -ForegroundColor Red
    exit 1
}


# ------------------------------------------------------------
# COMPLETE
# ------------------------------------------------------------

Write-Host ""
Write-Host "========================================"
Write-Host " VANTA BUILD COMPLETE"
Write-Host "========================================"
Write-Host ""

Write-Host "JAR:"
Write-Host "  $shadowJar"

Write-Host ""
Write-Host "Runtime:"
Write-Host "  .\build\runtime"

Write-Host ""
Write-Host "Application:"
Write-Host "  .\build\jpackage\Vanta"

Write-Host ""
Write-Host "Executable:"
Write-Host "  $exe"

Write-Host ""
Write-Host "Launching Vanta..."
Write-Host ""

& $exe