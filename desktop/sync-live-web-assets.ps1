param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$client = Join-Path $RepositoryRoot "desktop\BisnorDesktop\wwwroot\js\data.js"
if (-not (Test-Path -LiteralPath $client)) { throw "Live catalog client not found: $client" }

$forbidden = @((Join-Path $RepositoryRoot "desktop\BisnorDesktop\wwwroot\js\real_catalog_seed.json"))

foreach ($path in $forbidden) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Force
    }
}

Write-Output "Verified the API-only catalog client used by Windows and the PWA."
