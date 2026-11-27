param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [string]$Checksum,
    [string]$ExpectedSha256
)

$ErrorActionPreference = 'Stop'
try {
    if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) {
        throw "Downloaded archive does not exist: $Archive"
    }

    $expected = ''
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSha256)) {
        if ($ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$') {
            throw 'GitHub release metadata contains an invalid SHA-256 digest.'
        }
        $expected = $ExpectedSha256
        Write-Output 'Using SHA-256 digest from GitHub release asset metadata.'
    } else {
        if ([string]::IsNullOrWhiteSpace($Checksum) -or
            -not (Test-Path -LiteralPath $Checksum -PathType Leaf)) {
            throw "Checksum sidecar does not exist: $Checksum"
        }

        # Read UTF-8/ASCII sidecars, including an optional BOM, and accept
        # standard sha256sum output with either basename or path-prefixed names.
        $checksumText = [System.IO.File]::ReadAllText($Checksum).Trim().TrimStart([char]0xFEFF)
        $match = [regex]::Match(
            $checksumText,
            '^(?<hash>[a-fA-F0-9]{64})\s+(?<name>.+?)\s*$',
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
        if (-not $match.Success) {
            throw 'Malformed SHA-256 checksum file; expected a 64-character hash and filename.'
        }

        $declaredName = $match.Groups['name'].Value.Trim().TrimStart('*').Replace('/', '\')
        $declaredBaseName = [System.IO.Path]::GetFileName($declaredName)
        $archiveName = [System.IO.Path]::GetFileName($Archive)
        if (-not [string]::Equals(
            $declaredBaseName,
            $archiveName,
            [System.StringComparison]::OrdinalIgnoreCase
        )) {
            throw "Checksum filename '$declaredBaseName' does not match downloaded archive '$archiveName'."
        }
        $expected = $match.Groups['hash'].Value
        Write-Output 'Using SHA-256 checksum sidecar (legacy release fallback).'
    }

    $actual = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash
    if (-not [string]::Equals($actual, $expected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Downloaded archive SHA-256 mismatch (expected $expected, got $actual)."
    }

    Write-Output "Verified SHA-256 for $([System.IO.Path]::GetFileName($Archive)): $actual"
    exit 0
}
catch {
    Write-Error "Checksum verification failed: $($_.Exception.Message)"
    exit 1
}
