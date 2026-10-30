$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$config=Get-SetupConfig
Set-DatabaseEnvironment $config
$server="tcp:$($config.HostName),$($config.Port)"
if (!(Test-NetConnection -ComputerName $config.HostName -Port $config.Port -InformationLevel Quiet -WarningAction SilentlyContinue)) { throw 'SQL Server chưa mở cổng TCP đã cấu hình.' }
Test-SqlLogin $server $config.Database $config.Runtime
if (@(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue).Count) { throw 'Cổng 8080 đã được sử dụng. Dừng tiến trình đó trước; Start sẽ không thay thế tiến trình khác.' }
Push-Location $ProjectRoot
try {
    & (Join-Path $ProjectRoot 'deploy/Start-LocalServices.ps1')
    & java (Join-Path $ProjectRoot 'deploy/Initialize-DevelopmentSecurity.java') (Join-Path $ProjectRoot '.local/security')
    if ($LASTEXITCODE -ne 0) { throw 'Không tạo được khóa bảo mật cục bộ.' }
    $env:JWT_PRIVATE_KEY_PATH=Join-Path $ProjectRoot '.local/security/jwt-private.pem'
    $env:JWT_PUBLIC_KEY_PATH=Join-Path $ProjectRoot '.local/security/jwt-public.pem'
    $env:ACCOUNT_MAIL_KEY_PATH=Join-Path $ProjectRoot '.local/security/mail.key'
    $storage=Get-Content -LiteralPath (Join-Path $ProjectRoot '.local/security/storage.json') -Raw | ConvertFrom-Json
    $env:S3_ACCESS_KEY=$storage.accessKey; $env:S3_SECRET_KEY=$storage.secretKey
    $env:S3_ENDPOINT='http://127.0.0.1:9000'; $env:S3_REGION='us-east-1'; $env:S3_BUCKET='comicnet-dev'; $env:S3_CREATE_BUCKET='true'
    $env:SMTP_HOST='127.0.0.1'; $env:SMTP_PORT='1025'
    $backendJar=Join-Path $ProjectRoot 'backend/target/comic-net-backend-0.1.0-SNAPSHOT.jar'
    $desktopJar=Join-Path $ProjectRoot 'desktop-swing/target/comic-net-desktop-swing-0.1.0-SNAPSHOT.jar'
    if (!(Test-Path -LiteralPath $backendJar) -or !(Test-Path -LiteralPath $desktopJar)) {
        & .\mvnw.cmd -B -ntp -pl backend,desktop-swing -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Build thất bại.' }
    }
    $backend=Start-Process -FilePath (Get-Command java).Source -ArgumentList '-jar',('"'+$backendJar+'"') -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru
    $ready=$false
    for($i=0;$i -lt 60;$i++) {
        if($backend.HasExited) { throw 'Backend đã dừng trong lúc khởi động. Kiểm tra cấu hình SQL/Flyway.' }
        try { $health=Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health/readiness' -TimeoutSec 2; if($health.status -eq 'UP') { $ready=$true; break } } catch {}
        Start-Sleep -Seconds 1
    }
    if(!$ready) { throw 'Backend chưa sẵn sàng sau 60 giây.' }
    Write-Host 'Backend sẵn sàng. Đang mở Swing.' -ForegroundColor Green
    & (Join-Path $ProjectRoot 'deploy/Start-Desktop.ps1')
} finally { Pop-Location }
