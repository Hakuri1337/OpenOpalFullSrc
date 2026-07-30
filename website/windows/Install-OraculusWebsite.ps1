[CmdletBinding()]
param([string]$AuthBridgeSecret)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$PackageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PayloadRoot = Join-Path $PackageRoot 'payload'
$SettingsPath = Join-Path $PackageRoot 'site.settings.json'
$InstallRoot = 'C:\OraculusWebsite'
$DataRoot = 'C:\ProgramData\OraculusWebsite'
$TaskName = 'Oraculus Website'
$RenewTaskName = 'Oraculus Website Certificate Renewal'

function Write-Step([string]$Message) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $Message) -ForegroundColor Cyan }
function Fail([string]$Message) { throw $Message }
function Assert-Admin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        Write-Host 'Administrator rights are required. Requesting UAC elevation.' -ForegroundColor Yellow
        $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $MyInvocation.MyCommand.Path
        if ($AuthBridgeSecret) { $arguments += ' -AuthBridgeSecret "{0}"' -f $AuthBridgeSecret.Replace('"', '\"') }
        Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $arguments | Out-Null
        exit 0
    }
}
function Get-Sha256([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try { return ([BitConverter]::ToString(([Security.Cryptography.SHA256]::Create().ComputeHash($stream)))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose() }
}
function Test-Payload {
    $manifestPath = Join-Path $PayloadRoot 'payload-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath)) { Fail 'payload-manifest.json is missing.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in @($manifest.files)) {
        $file = Join-Path $PayloadRoot $entry.path
        if (-not (Test-Path -LiteralPath $file)) { Fail "Package file is missing: $($entry.path)" }
        if ((Get-Sha256 $file) -ne $entry.sha256) { Fail "Package integrity check failed: $($entry.path)" }
    }
}
function Invoke-Download([string]$Uri, [string]$Destination) {
    Write-Host "Downloading: $Uri"
    Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $Destination
    if (-not (Test-Path -LiteralPath $Destination) -or (Get-Item -LiteralPath $Destination).Length -lt 1024) { Fail "Download failed: $Uri" }
}
function Expand-Zip([string]$Archive, [string]$Destination) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($Archive, $Destination)
}
function Convert-SecureString([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
function Get-BridgeSecret {
    if ($AuthBridgeSecret) { return $AuthBridgeSecret.Trim() }
    $secure = Read-Host 'Enter the Auth website bridge secret' -AsSecureString
    return (Convert-SecureString $secure).Trim()
}
function Write-NginxConfig([string]$Template, [string]$Destination, [string]$Domain, [string]$WebRoot, [string]$Certificate, [string]$PrivateKey) {
    $content = Get-Content -LiteralPath $Template -Raw -Encoding UTF8
    $content = $content.Replace('__DOMAIN__', $Domain).Replace('__WEBROOT__', ($WebRoot -replace '\\', '/'))
    $content = $content.Replace('__CERTIFICATE__', ($Certificate -replace '\\', '/')).Replace('__PRIVATE_KEY__', ($PrivateKey -replace '\\', '/'))
    Set-Content -LiteralPath $Destination -Value $content -Encoding UTF8
}
function Invoke-Lego([string]$Lego, [string[]]$Arguments, [string]$LogDirectory) {
    New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
    $stdout = Join-Path $LogDirectory 'lego.stdout.log'; $stderr = Join-Path $LogDirectory 'lego.stderr.log'
    $process = Start-Process -FilePath $Lego -ArgumentList $Arguments -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    if (Test-Path $stdout) { Get-Content $stdout -Encoding UTF8 | Write-Host }
    if (Test-Path $stderr) { Get-Content $stderr -Encoding UTF8 | Write-Host }
    if ($process.ExitCode -ne 0) { Fail "ACME certificate operation failed (lego exit code $($process.ExitCode)). Check DNS, TCP 80, and the cloud firewall." }
}
function Register-Tasks([string]$Root) {
    foreach ($name in @($TaskName, $RenewTaskName)) { Unregister-ScheduledTask -TaskName $name -Confirm:$false -ErrorAction SilentlyContinue }
    $startAction = New-ScheduledTaskAction -Execute (Join-Path $Root 'Run-OraculusWebsite.cmd') -WorkingDirectory $Root
    $startTrigger = New-ScheduledTaskTrigger -AtStartup
    $system = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName $TaskName -Action $startAction -Trigger $startTrigger -Principal $system -Force | Out-Null
    $renewAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f (Join-Path $Root 'Renew-OraculusWebsiteCertificate.ps1')) -WorkingDirectory $Root
    $renewTrigger = New-ScheduledTaskTrigger -Daily -At 3:20AM
    Register-ScheduledTask -TaskName $RenewTaskName -Action $renewAction -Trigger $renewTrigger -Principal $system -Force | Out-Null
    Start-ScheduledTask -TaskName $TaskName
}
function Ensure-Firewall {
    foreach ($rule in @('Oraculus Website HTTP 80', 'Oraculus Website HTTPS 443')) { Get-NetFirewallRule -DisplayName $rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule }
    New-NetFirewallRule -DisplayName 'Oraculus Website HTTP 80' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 80 -Profile Any | Out-Null
    New-NetFirewallRule -DisplayName 'Oraculus Website HTTPS 443' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 443 -Profile Any | Out-Null
}

Assert-Admin
if ([Environment]::Is64BitOperatingSystem -eq $false) { Fail 'Only 64-bit Windows Server 2012 R2 is supported.' }
if ([Environment]::OSVersion.Version -lt [Version]'6.3') { Fail 'Windows Server 2012 R2 or newer is required.' }
if (-not (Test-Path -LiteralPath $SettingsPath)) { Fail 'site.settings.json is missing.' }
$settings = Get-Content -LiteralPath $SettingsPath -Raw -Encoding UTF8 | ConvertFrom-Json
$domain = [string]$settings.Domain; $expectedIp = [string]$settings.ExpectedIPv4
$secret = Get-BridgeSecret
if ($secret.Length -lt 32) { Fail 'The Auth bridge secret must be at least 32 characters.' }

Write-Step 'Checking package integrity'
Test-Payload
Write-Step "Checking DNS: $domain"
$resolved = [Net.Dns]::GetHostAddresses($domain) | ForEach-Object { $_.IPAddressToString }
if ($resolved -notcontains $expectedIp) { Fail "$domain does not resolve to expected IP $expectedIp. Current: $($resolved -join ', ')" }

$work = Join-Path $env:TEMP ('oraculus-website-' + [Guid]::NewGuid().ToString('N'))
$stage = Join-Path $work 'stage'; $backup = "$InstallRoot.backup-$(Get-Date -Format 'yyyyMMddHHmmss')"; $movedPrevious = $false
try {
    New-Item -ItemType Directory -Force -Path $stage, $DataRoot | Out-Null
    Write-Step 'Preparing the Node 18 runtime, Nginx, and ACME client'
    $nodeZip = Join-Path $work 'node.zip'; $nginxZip = Join-Path $work 'nginx.zip'; $legoZip = Join-Path $work 'lego.zip'
    Invoke-Download "https://nodejs.org/dist/v$($settings.NodeVersion)/node-v$($settings.NodeVersion)-win-x64.zip" $nodeZip
    Invoke-Download "https://nginx.org/download/nginx-$($settings.NginxVersion).zip" $nginxZip
    Invoke-Download 'https://github.com/go-acme/lego/releases/download/v4.16.1/lego_v4.16.1_Windows_x86_64.zip' $legoZip
    $nodeExtract = Join-Path $work 'node'; $nginxExtract = Join-Path $work 'nginx'; $legoExtract = Join-Path $work 'lego'
    Expand-Zip $nodeZip $nodeExtract; Expand-Zip $nginxZip $nginxExtract; Expand-Zip $legoZip $legoExtract
    $nodeFolder = Get-ChildItem -LiteralPath $nodeExtract -Directory | Select-Object -First 1
    $nginxFolder = Get-ChildItem -LiteralPath $nginxExtract -Directory | Select-Object -First 1
    $legoExe = Get-ChildItem -LiteralPath $legoExtract -Filter 'lego.exe' -Recurse | Select-Object -First 1
    if (-not $nodeFolder -or -not $nginxFolder -or -not $legoExe) { Fail 'The runtime archive format is invalid.' }
    New-Item -ItemType Directory -Force -Path (Join-Path $stage 'runtime'), (Join-Path $stage 'nginx'), (Join-Path $stage 'acme') | Out-Null
    Copy-Item -LiteralPath (Join-Path $nodeFolder.FullName 'node.exe') -Destination (Join-Path $stage 'runtime\node.exe')
    Get-ChildItem -LiteralPath $nginxFolder.FullName -Force | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $stage 'nginx') -Recurse -Force }
    Copy-Item -LiteralPath (Join-Path $PayloadRoot 'app') -Destination (Join-Path $stage 'app') -Recurse -Force
    Copy-Item -LiteralPath $legoExe.FullName -Destination (Join-Path $stage 'acme\lego.exe') -Force
    foreach ($file in @('Run-OraculusWebsite.cmd', 'Renew-OraculusWebsiteCertificate.ps1', 'nginx-http.conf.template', 'nginx-https.conf.template')) { Copy-Item -LiteralPath (Join-Path $PayloadRoot $file) -Destination $stage -Force }
    Copy-Item -LiteralPath $SettingsPath -Destination (Join-Path $stage 'site.settings.json') -Force
    $envFile = Join-Path $stage 'app\.env.production'
    @("NODE_ENV=production", "NEXT_PUBLIC_SITE_URL=https://$domain", "AUTH_INTERNAL_URL=$($settings.AuthInternalUrl)", "ORACULUS_WEBSITE_SECRET=$secret") | Set-Content -LiteralPath $envFile -Encoding ASCII

    $nginxRoot = Join-Path $stage 'nginx'; $webRoot = Join-Path $nginxRoot 'html'; $conf = Join-Path $nginxRoot 'conf\nginx.conf'
    Write-NginxConfig (Join-Path $stage 'nginx-http.conf.template') $conf $domain $webRoot '' ''
    & (Join-Path $nginxRoot 'nginx.exe') -p $nginxRoot -c 'conf\nginx.conf' -t
    if ($LASTEXITCODE -ne 0) { Fail 'Nginx HTTP configuration validation failed.' }

    Write-Step 'Stopping old tasks and switching website files'
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath (Join-Path $InstallRoot 'nginx\nginx.exe')) { & (Join-Path $InstallRoot 'nginx\nginx.exe') -p (Join-Path $InstallRoot 'nginx') -s quit 2>$null }
    Start-Sleep -Seconds 2
    if (Test-Path -LiteralPath $InstallRoot) { Move-Item -LiteralPath $InstallRoot -Destination $backup; $movedPrevious = $true }
    Move-Item -LiteralPath $stage -Destination $InstallRoot
    $stage = ''
    Ensure-Firewall

    Write-Step 'Starting HTTP validation and issuing or renewing the HTTPS certificate'
    $nginxRoot = Join-Path $InstallRoot 'nginx'; $nginx = Join-Path $nginxRoot 'nginx.exe'
    Start-Process -FilePath $nginx -ArgumentList @('-p', $nginxRoot, '-c', 'conf\nginx.conf') -WorkingDirectory $nginxRoot | Out-Null
    Start-Sleep -Seconds 1
    $acmeRoot = Join-Path $DataRoot 'acme'; $certificate = Join-Path $acmeRoot "certificates\$domain.crt"; $privateKey = Join-Path $acmeRoot "certificates\$domain.key"
    $baseLegoArgs = @('--accept-tos', '--email', "admin@$domain", '--domains', $domain, '--http', '--http.webroot', (Join-Path $nginxRoot 'html'), '--path', $acmeRoot)
    $legoArgs = if ((Test-Path $certificate) -and (Test-Path $privateKey)) { $baseLegoArgs + @('renew', '--days', '30') } else { $baseLegoArgs + @('run') }
    Invoke-Lego (Join-Path $InstallRoot 'acme\lego.exe') $legoArgs (Join-Path $DataRoot 'logs')
    Write-NginxConfig (Join-Path $InstallRoot 'nginx-https.conf.template') (Join-Path $nginxRoot 'conf\nginx.conf') $domain (Join-Path $nginxRoot 'html') $certificate $privateKey
    & $nginx -p $nginxRoot -s reload
    if ($LASTEXITCODE -ne 0) { Fail 'Nginx HTTPS configuration reload failed.' }

    Write-Step 'Registering startup and daily certificate renewal tasks'
    Register-Tasks $InstallRoot
    Start-Sleep -Seconds 2
    $local = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:3000/' -TimeoutSec 10
    if ($local.StatusCode -ne 200) { Fail 'The local website Node health check failed.' }
    $public = Invoke-WebRequest -UseBasicParsing -Uri "https://$domain/" -TimeoutSec 20
    if ($public.StatusCode -ne 200) { Fail 'The public HTTPS health check failed.' }
    Write-Host "`nDeployment succeeded: https://$domain/" -ForegroundColor Green
    Write-Host "Auth bridge: $($settings.AuthInternalUrl) (the Auth server must use the same secret and this website IP allowlist)" -ForegroundColor Yellow
} catch {
    $message = $_.Exception.Message
    Write-Host "Deployment failed: $message" -ForegroundColor Red
    if ($movedPrevious -and (Test-Path -LiteralPath $backup)) {
        if (Test-Path -LiteralPath $InstallRoot) { Move-Item -LiteralPath $InstallRoot -Destination "$InstallRoot.failed-$(Get-Date -Format 'yyyyMMddHHmmss')" }
        Move-Item -LiteralPath $backup -Destination $InstallRoot
        Write-Host 'The previous website files were restored.' -ForegroundColor Yellow
    }
    exit 1
} finally {
    if ($stage -and (Test-Path -LiteralPath $stage)) { Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}
