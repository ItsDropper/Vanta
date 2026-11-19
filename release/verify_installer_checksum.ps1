param(
    [Parameter(Mandatory = $true)][string]$Archive,
    [Parameter(Mandatory = $true)][string]$Checksum
)

$ErrorActionPreference = 'Stop'
try {
    $fields = (Get-Content -LiteralPath $Checksum -Raw).Trim() -split '\s+'
    if ($fields.Count -ne 2 -or $fields[0] -notmatch '^[a-fA-F0-9]{64}$') {
        throw 'Malformed SHA-256 checksum file.'
    }

    $declaredName = $fields[1].TrimStart('*')
    if ($declaredName -ne [System.IO.Path]::GetFileName($Archive)) {
        throw 'Checksum filename does not match the downloaded archive.'
    }

    $actual = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash
    if (-not [string]::Equals($actual, $fields[0], [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Downloaded archive SHA-256 does not match the published checksum.'
    }

    exit 0
}
catch {
    Write-Error $_
    exit 1
}
