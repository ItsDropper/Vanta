param(
    [Parameter(Mandatory = $true)][string]$ReleaseMetadata,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [string]$ChecksumOutputPath
)

$ErrorActionPreference = 'Stop'
try {
    $metadata = Get-Content -LiteralPath $ReleaseMetadata -Raw | ConvertFrom-Json
    $tag = [string]$metadata.tag_name
    if ($tag -notmatch '^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
        throw 'GitHub returned an invalid stable release tag.'
    }

    $version = $tag.TrimStart('v')
    [System.IO.File]::WriteAllText($OutputPath, $version, [System.Text.Encoding]::ASCII)

    # GitHub's release API exposes a SHA-256 digest for uploaded assets.
    # Prefer that digest because it is tied to the exact release asset
    # metadata returned alongside the tag, rather than a separately fetched
    # checksum sidecar. Older releases without this field use the sidecar.
    if (-not [string]::IsNullOrWhiteSpace($ChecksumOutputPath)) {
        $archiveName = "Vanta-$version.zip"
        $asset = @($metadata.assets | Where-Object { $_.name -ceq $archiveName } | Select-Object -First 1)
        $digest = if ($asset.Count -gt 0) { [string]$asset[0].digest } else { '' }
        if ($digest -match '^sha256:([a-fA-F0-9]{64})$') {
            [System.IO.File]::WriteAllText($ChecksumOutputPath, $Matches[1].ToLowerInvariant(), [System.Text.Encoding]::ASCII)
        } else {
            [System.IO.File]::WriteAllText($ChecksumOutputPath, '', [System.Text.Encoding]::ASCII)
        }
    }

    exit 0
}
catch {
    Write-Error $_
    exit 1
}
