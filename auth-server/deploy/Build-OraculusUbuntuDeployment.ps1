[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptDirectory = Split-Path -Parent $PSCommandPath
$authRoot = Split-Path -Parent $scriptDirectory
$publishRoot = Join-Path $authRoot 'publish'
$outputRoot = Join-Path $publishRoot 'OraculusAuth-Ubuntu'
$archivePath = Join-Path $publishRoot 'OraculusAuth-Ubuntu.tar.gz'

$resolvedAuthRoot = [IO.Path]::GetFullPath($authRoot).TrimEnd('\', '/')
$resolvedOutputRoot = [IO.Path]::GetFullPath($outputRoot).TrimEnd('\', '/')
if (-not $resolvedOutputRoot.StartsWith($resolvedAuthRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to write outside auth-server: $resolvedOutputRoot"
}

$payload = [ordered]@{
    'server.js'                       = Join-Path $authRoot 'node-server\server.js'
    'install.sh'                      = Join-Path $authRoot 'ubuntu\install.sh'
    'server.ubuntu.json.template'     = Join-Path $authRoot 'ubuntu\server.ubuntu.json.template'
    'oraculus-auth.service'           = Join-Path $authRoot 'ubuntu\oraculus-auth.service'
    'oraculus-auth-renew.service'     = Join-Path $authRoot 'ubuntu\oraculus-auth-renew.service'
    'oraculus-auth-renew.timer'       = Join-Path $authRoot 'ubuntu\oraculus-auth-renew.timer'
    'sync-certificate.sh'             = Join-Path $authRoot 'ubuntu\sync-certificate.sh'
    'README_ZH.md'                    = Join-Path $authRoot 'ubuntu\README_ZH.md'
}

foreach ($source in $payload.Values) {
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Missing deployment source file: $source"
    }
}

if (Test-Path -LiteralPath $outputRoot) {
    Remove-Item -LiteralPath $outputRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$utf8WithoutBom = [Text.UTF8Encoding]::new($false)
foreach ($entry in $payload.GetEnumerator()) {
    $content = [IO.File]::ReadAllText($entry.Value)
    $content = $content.Replace("`r`n", "`n").Replace("`r", "`n")
    [IO.File]::WriteAllText((Join-Path $outputRoot $entry.Key), $content, $utf8WithoutBom)
}

$manifestLines = foreach ($name in $payload.Keys) {
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $outputRoot $name)).Hash.ToLowerInvariant()
    "$hash  $name"
}
[IO.File]::WriteAllText(
    (Join-Path $outputRoot 'payload-manifest.sha256'),
    ($manifestLines -join "`n") + "`n",
    $utf8WithoutBom
)

if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}

& tar.exe -czf $archivePath -C $outputRoot .
if ($LASTEXITCODE -ne 0) {
    throw "tar.exe failed with exit code $LASTEXITCODE"
}

$archive = Get-Item -LiteralPath $archivePath
$archiveHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()

Write-Host 'Ubuntu deployment package created.'
Write-Host "Directory: $outputRoot"
Write-Host "Archive:   $archivePath"
Write-Host "Size:      $($archive.Length) bytes"
Write-Host "SHA-256:   $archiveHash"
