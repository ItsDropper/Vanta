param(
    [Parameter(Mandatory = $true)][string]$ReleaseMetadata,
    [Parameter(Mandatory = $true)][string]$OutputPath
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
    exit 0
}
catch {
    Write-Error $_
    exit 1
}
