param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$source = Join-Path $RepositoryRoot "tizen-tv\js\data.js"
$destination = Join-Path $RepositoryRoot "desktop\BisnorDesktop\wwwroot\js\data.js"

if (-not (Test-Path -LiteralPath $source)) {
    throw "Canonical live catalog client not found: $source"
}

Copy-Item -LiteralPath $source -Destination $destination -Force

$forbidden = @(
    (Join-Path $RepositoryRoot "tizen-tv\js\real_catalog_seed.json"),
    (Join-Path $RepositoryRoot "desktop\BisnorDesktop\wwwroot\js\real_catalog_seed.json")
)

foreach ($path in $forbidden) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

Write-Output "Synced API-only catalog client to Windows desktop."
