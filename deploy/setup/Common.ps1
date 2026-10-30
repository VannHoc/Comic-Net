$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$ConfigPath = Join-Path $ProjectRoot '.local/setup/connection.xml'
function Get-SetupConfig {
    if (!(Test-Path -LiteralPath $ConfigPath)) { throw 'Chưa cấu hình máy này. Chạy .\deploy\setup\Setup.ps1 trước.' }
    $config = Import-Clixml -LiteralPath $ConfigPath
    if ($config.Owner -ne [Security.Principal.WindowsIdentity]::GetCurrent().User.Value) {
        throw 'Cấu hình thuộc tài khoản Windows khác. Chạy lại Setup.ps1.'
    }
    return $config
}
function Set-DatabaseEnvironment($config) {
    $env:DB_HOST = $config.HostName
    $env:DB_PORT = [string]$config.Port
    $env:DB_NAME = $config.Database
    $env:DB_USER = $config.Runtime.UserName
    $env:DB_PASSWORD = $config.Runtime.GetNetworkCredential().Password
    $env:DB_MIGRATION_USER = $config.Migrator.UserName
    $env:DB_MIGRATION_PASSWORD = $config.Migrator.GetNetworkCredential().Password
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:APP_PORT = '8080'
}
function Test-SqlLogin([string]$server,[string]$database,[pscredential]$credential) {
    $old = $env:SQLCMDPASSWORD
    try {
        $env:SQLCMDPASSWORD = $credential.GetNetworkCredential().Password
        $result = & sqlcmd -S $server -d $database -U $credential.UserName -b -l 5 -h -1 -W -Q 'SET NOCOUNT ON; SELECT DB_NAME();' 2>&1
        if ($LASTEXITCODE -ne 0) { throw "Không đăng nhập được SQL Server $server/$database với $($credential.UserName): $result" }
    } finally { $env:SQLCMDPASSWORD = $old }
}
