[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$WebsiteRoot = Split-Path -Parent $PSScriptRoot
$ProjectRoot = Split-Path -Parent $WebsiteRoot
$PublishRoot = Join-Path $WebsiteRoot 'publish'
$ReleaseRoot = Join-Path $PublishRoot 'OraculusWebsite-Windows2012R2'
$PayloadRoot = Join-Path $ReleaseRoot 'payload'
$ZipPath = Join-Path $PublishRoot 'OraculusWebsite-Windows2012R2.zip'
$BridgeSource = Join-Path $ProjectRoot 'auth-server\ubuntu\enable-remote-website-api.sh'

function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try { return ([BitConverter]::ToString(([Security.Cryptography.SHA256]::Create().ComputeHash($stream)))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose() }
}
function Copy-Required([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Source)) { throw "Build input is missing: $Source" }
    Copy-Item -LiteralPath $Source -Destination $Destination -Recurse -Force
}
function Copy-DirectoryContents([string]$Source, [string]$Destination) {
    if (-not (Test-Path -LiteralPath $Source)) { throw "Build input directory is missing: $Source" }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force }
}
function Compress-Release([string]$Source, [string]$Destination) {
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Force }
            Compress-Archive -Path (Join-Path $Source '*') -DestinationPath $Destination -CompressionLevel Optimal
            return
        } catch {
            if ($attempt -eq 5) { throw }
            Start-Sleep -Seconds 2
        }
    }
}

Push-Location $WebsiteRoot
try {
    Write-Host 'Building website standalone production output...' -ForegroundColor Cyan
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Next.js production build failed.' }
    $standalone = Join-Path $WebsiteRoot '.next\standalone'
    $static = Join-Path $WebsiteRoot '.next\static'
    if (-not (Test-Path -LiteralPath (Join-Path $standalone 'server.js'))) { throw 'Next standalone server.js was not generated.' }

    if (Test-Path -LiteralPath $ReleaseRoot) { Remove-Item -LiteralPath $ReleaseRoot -Recurse -Force }
    if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force }
    New-Item -ItemType Directory -Force -Path (Join-Path $PayloadRoot 'app') | Out-Null
    Copy-DirectoryContents $standalone (Join-Path $PayloadRoot 'app')
    New-Item -ItemType Directory -Force -Path (Join-Path $PayloadRoot 'app\.next') | Out-Null
    Copy-DirectoryContents $static (Join-Path $PayloadRoot 'app\.next\static')
    if (Test-Path -LiteralPath (Join-Path $WebsiteRoot 'public')) { Copy-DirectoryContents (Join-Path $WebsiteRoot 'public') (Join-Path $PayloadRoot 'app\public') }

    foreach ($file in @('Run-OraculusWebsite.cmd', 'Renew-OraculusWebsiteCertificate.ps1', 'nginx-http.conf.template', 'nginx-https.conf.template')) {
        Copy-Required (Join-Path $PSScriptRoot $file) (Join-Path $PayloadRoot $file)
    }
    foreach ($file in @('Deploy-OraculusWebsite.cmd', 'Install-OraculusWebsite.ps1', 'site.settings.json', 'README_ZH.md')) {
        Copy-Required (Join-Path $PSScriptRoot $file) (Join-Path $ReleaseRoot $file)
    }
    Copy-Required $BridgeSource (Join-Path $ReleaseRoot 'Auth-Bridge-Setup-Ubuntu.sh')

    $files = Get-ChildItem -LiteralPath $PayloadRoot -Recurse -File | Sort-Object FullName | ForEach-Object {
        [pscustomobject]@{ path = $_.FullName.Substring($PayloadRoot.Length).TrimStart('\').Replace('\', '/'); sha256 = Get-Sha256 $_.FullName; length = $_.Length }
    }
    [pscustomobject]@{ format = 1; createdAtUtc = [DateTime]::UtcNow.ToString('o'); files = @($files) } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $PayloadRoot 'payload-manifest.json') -Encoding UTF8
    Compress-Release $ReleaseRoot $ZipPath
    Write-Host "Release package created: $ZipPath" -ForegroundColor Green
} finally { Pop-Location }
