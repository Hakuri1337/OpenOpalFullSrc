$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataRoot = 'C:\ProgramData\OraculusWebsite'
$settings = Get-Content -LiteralPath (Join-Path $root 'site.settings.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$domain = [string]$settings.Domain
$nginxRoot = Join-Path $root 'nginx'; $nginx = Join-Path $nginxRoot 'nginx.exe'
$acmeRoot = Join-Path $dataRoot 'acme'; $lego = Join-Path $root 'acme\lego.exe'
$logRoot = Join-Path $dataRoot 'logs'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$stdout = Join-Path $logRoot 'renew.stdout.log'; $stderr = Join-Path $logRoot 'renew.stderr.log'

try {
    $process = Start-Process -FilePath $lego -ArgumentList @('--accept-tos', '--email', "admin@$domain", '--domains', $domain, '--http', '--http.webroot', (Join-Path $nginxRoot 'html'), '--path', $acmeRoot, 'renew', '--days', '30') -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    if ($process.ExitCode -ne 0) { throw "lego renew failed with exit code $($process.ExitCode)" }
    & $nginx -p $nginxRoot -s reload
    if ($LASTEXITCODE -ne 0) { throw 'Nginx reload failed after certificate renewal' }
} catch {
    $_ | Out-File -LiteralPath (Join-Path $logRoot 'renew.failure.log') -Encoding UTF8
    exit 1
}
