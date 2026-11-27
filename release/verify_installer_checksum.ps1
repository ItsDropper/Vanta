param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [Parameter(Mandatory = $true)][string]$Checksum
)

$ErrorActionPreference = 'Stop'
try {
    if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) {
        throw "Downloaded archive does not exist: $Archive"
    }
    if (-not (Test-Path -LiteralPath $Checksum -PathType Leaf)) {
        throw "Checksum sidecar does not exist: $Checksum"
    }

    # Read the sidecar as text explicitly. Strip a possible UTF-8 BOM and
    # accept both sha256sum's basename and path-prefixed filename forms.
    $checksumText = [System.IO.File]::ReadAllText($Checksum).Trim().TrimStart([char]0xFEFF)
    $match = [regex]::Match(
        $checksumText,
        '^(?<hash>[a-fA-F0-9]{64})\s+(?<name>.+?)\s*$',
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    if (-not $match.Success) {
        throw 'Malformed SHA-256 checksum file; expected a 64-character hash and filename.'
    }

    $declaredName = $match.Groups['name'].Value.Trim().TrimStart('*').Replace('/', '\\')
    $declaredBaseName = [System.IO.Path]::GetFileName($declaredName)
    $archiveName = [System.IO.Path]::GetFileName($Archive)
    if (-not [string]::Equals(
        $declaredBaseName,
        $archiveName,
        [System.StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Checksum filename '$declaredBaseName' does not match downloaded archive '$archiveName'."
    }

    $actual = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash
    $expected = $match.Groups['hash'].Value
    if (-not [string]::Equals($actual, $expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Downloaded archive SHA-256 mismatch (expected $expected, got $actual)."
    }

    exit 0
}
catch {
    Write-Error "Checksum verification failed: $($_.Exception.Message)"
    exit 1
}
