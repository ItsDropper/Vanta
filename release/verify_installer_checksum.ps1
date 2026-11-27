param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [string]$Checksum,
    [string]$ExpectedSha256,
    [string]$ExpectedSha256File,
    [string]$LogPath
)
$ErrorActionPreference = 'Stop'
function Write-VerificationLog([string]$Message) {
    if (-not [string]::IsNullOrWhiteSpace($LogPath)) {
        try { [System.IO.File]::AppendAllText($LogPath, $Message + [Environment]::NewLine, [System.Text.Encoding]::UTF8) }
        catch { Write-Warning "Could not append checksum diagnostic to installer log: $($_.Exception.Message)" }
    }
}
try {
    if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) { throw "Downloaded archive does not exist: $Archive" }
    $expected = ''
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSha256File) -and (Test-Path -LiteralPath $ExpectedSha256File -PathType Leaf)) {
        $fileDigest = [System.IO.File]::ReadAllText($ExpectedSha256File).Trim().TrimStart([char]0xFEFF)
        if (-not [string]::IsNullOrWhiteSpace($fileDigest)) {
            if ($fileDigest -notmatch '^[a-fA-F0-9]{64}$') { throw 'GitHub release metadata file contains an invalid SHA-256 digest.' }
            $expected = $fileDigest
        }
    }
    if ([string]::IsNullOrWhiteSpace($expected) -and -not [string]::IsNullOrWhiteSpace($ExpectedSha256)) {
        if ($ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'GitHub release metadata contains an invalid SHA-256 digest.' }
        $expected = $ExpectedSha256
    }
    if (-not [string]::IsNullOrWhiteSpace($expected)) {
        $source = 'GitHub release asset metadata'
        Write-Output 'Using SHA-256 digest from GitHub release asset metadata.'
    } else {
        if ([string]::IsNullOrWhiteSpace($Checksum) -or -not (Test-Path -LiteralPath $Checksum -PathType Leaf)) {
            throw "No GitHub asset digest or checksum sidecar is available: $Checksum"
        }
        $checksumText = [System.IO.File]::ReadAllText($Checksum).Trim().TrimStart([char]0xFEFF)
        $match = [regex]::Match($checksumText, '^(?<hash>[a-fA-F0-9]{64})\s+(?<name>.+?)\s*$', [System.Text.RegularExpressions.RegexOptions]::Singleline)
        if (-not $match.Success) { throw 'Malformed SHA-256 checksum file; expected a 64-character hash and filename.' }
        $declaredName = $match.Groups['name'].Value.Trim().TrimStart('*').Replace('/', '\')
        $declaredBaseName = [System.IO.Path]::GetFileName($declaredName)
        $archiveName = [System.IO.Path]::GetFileName($Archive)
        if (-not [string]::Equals($declaredBaseName, $archiveName, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Checksum filename '$declaredBaseName' does not match downloaded archive '$archiveName'."
        }
        $expected = $match.Groups['hash'].Value
        $source = 'legacy SHA-256 sidecar'
        Write-Output 'Using SHA-256 checksum sidecar (legacy release fallback).'
    }
    # Get-FileHash is unavailable in Windows PowerShell 2.0/3.0, which can be
    # present on older Windows installations. Use the .NET crypto API instead.
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $stream = $null
    try {
        $stream = [System.IO.File]::OpenRead($Archive)
        $hashBytes = $sha256.ComputeHash($stream)
        $actual = [System.BitConverter]::ToString($hashBytes).Replace('-', '')
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
        if ($null -ne $sha256) { $sha256.Dispose() }
    }
    if (-not [string]::Equals($actual, $expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Downloaded archive SHA-256 mismatch (source: $source; expected: $expected; actual: $actual)."
    }
    $success = "Verified SHA-256 for $([System.IO.Path]::GetFileName($Archive)) using " + $source + ": " + $actual
    Write-VerificationLog $success
    Write-Output $success
    exit 0
} catch {
    $message = "Checksum verification failed: $($_.Exception.Message)"
    Write-VerificationLog $message
    Write-Error $message
    exit 1
}
