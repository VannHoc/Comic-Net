param([ValidateSet('Fresh','Restore')][string]$Mode='Restore')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
if ($env:OS -ne 'Windows_NT') { throw 'Chỉ hỗ trợ Windows 11.' }
if (!(Test-Path -LiteralPath (Join-Path $ProjectRoot 'mvnw.cmd'))) { throw 'Thiếu Maven Wrapper mvnw.cmd.' }
if (!(Get-Command sqlcmd -ErrorAction SilentlyContinue)) { throw 'Thiếu sqlcmd. Cài SQL Server Command Line Utilities hoặc SSMS có sqlcmd.' }
$java = Get-Command java -ErrorAction SilentlyContinue
if (!$java -or ((& java -version 2>&1) -join ' ') -notmatch 'version "21\.|openjdk 21') { throw 'Cần JDK 21 trong PATH/JAVA_HOME.' }
$services = @(Get-Service -Name 'MSSQLSERVER','MSSQL$*' -ErrorAction SilentlyContinue)
if (!$services -or !($services | Where-Object Status -eq 'Running')) { throw 'Không thấy SQL Server đang chạy. Mở SQL Server Configuration Manager và bật dịch vụ SQL Server.' }
Write-Host 'Nhập địa chỉ TCP từ SQL Server Configuration Manager > SQL Server Network Configuration > TCP/IP > IPAll.'
$hostName = Read-Host 'Server/IP [localhost]'; if (!$hostName) { $hostName='localhost' }
$portText = Read-Host 'TCP Port (không đoán cổng)'; $port=0
if (![int]::TryParse($portText,[ref]$port) -or $port -lt 1 -or $port -gt 65535) { throw 'Port TCP không hợp lệ.' }
$database = Read-Host 'Database [ComicNetDev]'; if (!$database) { $database='ComicNetDev' }
if ($database -ne 'ComicNetDev') { throw 'Các SQL grant hiện cố định ComicNetDev; bản Setup này chỉ hỗ trợ đúng tên đó để tránh cấp quyền sai DB.' }
$server = "tcp:$hostName,$port"
if (!(Test-NetConnection -ComputerName $hostName -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue)) { throw "Không kết nối được TCP $hostName`:$port. Bật TCP/IP và khởi động lại dịch vụ SQL Server." }
$admin = Get-Credential -Message 'SQL login quản trị để tạo/restore database và cấp quyền (không lưu)'
$runtime = Get-Credential -Message 'SQL login comicnet_app (chỉ dùng khi đã được tạo)'
$migrator = Get-Credential -Message 'SQL login comicnet_migrator (chỉ dùng khi đã được tạo)'
if ($runtime.UserName -ne 'comicnet_app' -or $migrator.UserName -ne 'comicnet_migrator') { throw 'Nhập đúng login comicnet_app và comicnet_migrator theo template hiện có.' }
Test-SqlLogin $server 'master' $admin
$old=$env:SQLCMDPASSWORD
try {
    $env:SQLCMDPASSWORD=$admin.GetNetworkCredential().Password
    $exists = (& sqlcmd -S $server -d master -U $admin.UserName -b -l 5 -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID(N'ComicNetDev') IS NULL THEN 0 ELSE 1 END;" 2>&1) -join ''
    if ($LASTEXITCODE -ne 0) { throw "Không kiểm tra được database: $exists" }
    if ($Mode -eq 'Fresh') {
        if ($exists.Trim() -ne '0') {
            if (!(Test-Path -LiteralPath $ConfigPath)) { throw 'ComicNetDev đã tồn tại nhưng máy này chưa có cấu hình Setup. Không ghi đè dữ liệu.' }
            Write-Host 'Database đã tồn tại; bỏ qua tạo mới, tiếp tục xác minh/migration an toàn.'
        } else {
        $template = Get-Content -LiteralPath (Join-Path $ProjectRoot 'deploy/sql/01_provision_development.template.sql') -Raw
        $text = $template.Replace('REPLACE_LOCALLY_RUNTIME_PASSWORD',$runtime.GetNetworkCredential().Password.Replace("'","''")).Replace('REPLACE_LOCALLY_MIGRATION_PASSWORD',$migrator.GetNetworkCredential().Password.Replace("'","''"))
        $temp = Join-Path $ProjectRoot '.local/setup/provision.tmp.sql'
        New-Item -ItemType Directory -Force -Path (Split-Path $temp) | Out-Null
        try {
            [IO.File]::WriteAllText($temp,$text)
            & sqlcmd -S $server -d master -U $admin.UserName -b -i $temp
            if ($LASTEXITCODE -ne 0) { throw 'Provision SQL thất bại. Kiểm tra login/quyền và chạy lại sau khi xử lý DB còn dang dở.' }
        } finally { if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Force }; $text=$null }
        }
    } else {
        if ($exists.Trim() -ne '1') { throw 'Chưa thấy ComicNetDev. Hãy restore file .bak bằng SSMS trên máy này trước, rồi chạy lại Setup -Mode Restore.' }
        Write-Host 'Đã thấy ComicNetDev do bạn restore. Setup chỉ cấu hình kết nối, không restore hoặc ghi đè dữ liệu.'
        $runtimePassword=$runtime.GetNetworkCredential().Password.Replace("'","''''")
        $migrationPassword=$migrator.GetNetworkCredential().Password.Replace("'","''''")
        $loginSql=@"
USE master;
IF SUSER_ID(N'comicnet_app') IS NULL EXEC(N'CREATE LOGIN [comicnet_app] WITH PASSWORD=N''$runtimePassword'', CHECK_POLICY=ON;');
IF SUSER_ID(N'comicnet_migrator') IS NULL EXEC(N'CREATE LOGIN [comicnet_migrator] WITH PASSWORD=N''$migrationPassword'', CHECK_POLICY=ON;');
USE [ComicNetDev];
IF USER_ID(N'comicnet_app') IS NULL CREATE USER [comicnet_app] FOR LOGIN [comicnet_app];
ELSE ALTER USER [comicnet_app] WITH LOGIN=[comicnet_app];
IF USER_ID(N'comicnet_migrator') IS NULL CREATE USER [comicnet_migrator] FOR LOGIN [comicnet_migrator];
ELSE ALTER USER [comicnet_migrator] WITH LOGIN=[comicnet_migrator];
IF DATABASE_PRINCIPAL_ID(N'comicnet_runtime') IS NULL CREATE ROLE [comicnet_runtime];
IF DATABASE_PRINCIPAL_ID(N'comicnet_migration') IS NULL CREATE ROLE [comicnet_migration];
IF IS_ROLEMEMBER(N'comicnet_runtime',N'comicnet_app') <> 1 ALTER ROLE [comicnet_runtime] ADD MEMBER [comicnet_app];
IF IS_ROLEMEMBER(N'comicnet_migration',N'comicnet_migrator') <> 1 ALTER ROLE [comicnet_migration] ADD MEMBER [comicnet_migrator];
GRANT CONNECT TO [comicnet_runtime];
GRANT CONNECT, CREATE TABLE TO [comicnet_migration];
GRANT ALTER, REFERENCES, INSERT, UPDATE, DELETE, SELECT ON SCHEMA::dbo TO [comicnet_migration];
GRANT VIEW DEFINITION TO [comicnet_migration];
"@
        $temp=Join-Path $ProjectRoot '.local/setup/restore-logins.tmp.sql'
        New-Item -ItemType Directory -Force -Path (Split-Path $temp) | Out-Null
        try {
            [IO.File]::WriteAllText($temp,$loginSql)
            & sqlcmd -S $server -d master -U $admin.UserName -b -i $temp
            if($LASTEXITCODE -ne 0) { throw 'Không remap được SQL logins sau restore.' }
        } finally { if(Test-Path $temp){Remove-Item -LiteralPath $temp -Force};$loginSql=$null }
    }
} finally { $env:SQLCMDPASSWORD=$old }
Test-SqlLogin $server $database $runtime
Test-SqlLogin $server $database $migrator
$config=[pscustomobject]@{Owner=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value;HostName=$hostName;Port=$port;Database=$database;Runtime=$runtime;Migrator=$migrator}
$config | Export-Clixml -LiteralPath $ConfigPath
Set-DatabaseEnvironment $config
Push-Location $ProjectRoot
try {
    & .\mvnw.cmd -B -ntp -pl backend -am -DskipTests compile
    if ($LASTEXITCODE -ne 0) { throw 'Build backend thất bại; cấu hình đã lưu để chạy lại.' }
    & .\mvnw.cmd -B -ntp -f backend/pom.xml '-Dexec.mainClass=com.comicnet.infrastructure.database.MigrationCommand' exec:java
    if ($LASTEXITCODE -ne 0) { throw 'Flyway thất bại; không cấp grants hoặc khởi động backend.' }
} finally { Pop-Location }
$old=$env:SQLCMDPASSWORD
try {
    $env:SQLCMDPASSWORD=$admin.GetNetworkCredential().Password
    $grants=Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'deploy/sql') -Filter '*grant*runtime.sql' | Sort-Object Name
    foreach ($script in $grants) {
        & sqlcmd -S $server -d $database -U $admin.UserName -b -i $script.FullName
        if ($LASTEXITCODE -ne 0) { throw "Cấp quyền thất bại tại $($script.Name). Không khởi chạy backend cho tới khi xử lý xong." }
    }
} finally { $env:SQLCMDPASSWORD=$old }
Test-SqlLogin $server $database $runtime
Write-Host 'Đã lưu cấu hình mã hóa theo tài khoản Windows trong .local/setup/connection.xml.'
Write-Host 'Flyway/grants đã hoàn tất. Chạy .\deploy\setup\Start.ps1.'
