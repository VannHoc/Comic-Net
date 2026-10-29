param(
    [ValidateSet('VerifyAndRun', 'Verify', 'Run')]
    [string]$Mode = 'VerifyAndRun',
    [switch]$Debug,
    [switch]$FromSource,
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [string]$IntegrationTest,
    [switch]$UseLocalConfiguration
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$javaCandidates = @()
if ($env:JAVA_HOME) { $javaCandidates += $env:JAVA_HOME }
$javaCandidates += Get-ChildItem -LiteralPath "$env:ProgramFiles\Java" -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
$javaCandidates += "$env:ProgramFiles\Apache NetBeans\jdk"
$javaDirectory = $null
foreach ($candidate in $javaCandidates) {
    $javaCommand = Join-Path $candidate 'bin\java.exe'
    if (Test-Path -LiteralPath $javaCommand) {
        $versionOutput = & $javaCommand --version 2>&1
        if ($LASTEXITCODE -eq 0 -and ($versionOutput -join ' ') -match '(?:java|openjdk) 21[. ]') {
            $javaDirectory = $candidate
            break
        }
    }
}
if (!$javaDirectory) { throw 'JDK 21 not found. Set JAVA_HOME to your JDK 21 installation.' }

$variableNames = @('JAVA_HOME', 'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD',
    'DB_MIGRATION_USER', 'DB_MIGRATION_PASSWORD', 'SPRING_PROFILES_ACTIVE', 'APP_PORT',
    'JWT_PRIVATE_KEY_PATH','JWT_PUBLIC_KEY_PATH','ACCOUNT_MAIL_KEY_PATH',
    'S3_ACCESS_KEY','S3_SECRET_KEY','S3_ENDPOINT','S3_REGION','S3_BUCKET','S3_CREATE_BUCKET','SMTP_HOST','SMTP_PORT')
$previousValues = @{}
foreach ($key in $variableNames) { $previousValues[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$runtimeSecret = $null
$migrationSecret = $null
$exitStatus = 0
if ($IntegrationTest -and $Mode -ne 'Verify') { throw 'IntegrationTest requires -Mode Verify.' }
Push-Location -LiteralPath $projectDirectory
try {
    $env:JAVA_HOME = $javaDirectory
    Write-Host "Comic Net backend only | JDK 21 | SQL localhost:1433 / ComicNetDev"
    if(!$UseLocalConfiguration){
        Write-Host 'Passwords are entered locally, hidden, and are not saved. Do not paste them in chat.'
        $runtimeSecret = Read-Host '1/2 - Mat khau comicnet_app (da dat tai @RuntimePassword trong SSMS)' -AsSecureString
        $migrationSecret = Read-Host '2/2 - Mat khau comicnet_migrator (da dat tai @MigrationPassword trong SSMS)' -AsSecureString
        if ($runtimeSecret.Length -eq 0 -or $migrationSecret.Length -eq 0) { throw 'Passwords cannot be empty.' }
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $runtimeSecret).Password
        $env:DB_MIGRATION_PASSWORD = [System.Net.NetworkCredential]::new('', $migrationSecret).Password
    }else{
        # Explicit opt-in for the user's existing private local provisioning file; never echo it.
        $taskSql=[IO.File]::ReadAllText((Join-Path $projectDirectory 'deploy/sql/01_provision_development.sql'))
        foreach($taskPair in @(@('Runtime','DB_PASSWORD'),@('Migration','DB_MIGRATION_PASSWORD'))){
            $taskMatch=[regex]::Match($taskSql,"DECLARE @"+$taskPair[0]+"Password nvarchar\(128\) = N'((?:''|[^'])*)';")
            if(!$taskMatch.Success){throw 'Private local SQL configuration was not found.'}
            [Environment]::SetEnvironmentVariable($taskPair[1],$taskMatch.Groups[1].Value.Replace("''","'"),'Process')
        }
        $taskSql=$null;$taskMatch=$null
    }
    Write-Host 'Local SQL configuration loaded for backend process.'
    $env:DB_HOST = 'localhost'
    $env:DB_PORT = '1433'
    $env:DB_NAME = 'ComicNetDev'
    $env:DB_USER = 'comicnet_app'
    $env:DB_MIGRATION_USER = 'comicnet_migrator'
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:APP_PORT = [string]$Port
    Write-Host 'Checking RustFS and Mailpit.'
    & (Join-Path $PSScriptRoot 'Start-LocalServices.ps1')
    & (Join-Path $javaDirectory 'bin\java.exe') (Join-Path $PSScriptRoot 'Initialize-DevelopmentSecurity.java') (Join-Path $projectDirectory '.local/security')
    if($LASTEXITCODE -ne 0){throw 'Cannot prepare development key material.'}
    $env:JWT_PRIVATE_KEY_PATH=Join-Path $projectDirectory '.local/security/jwt-private.pem'
    $env:JWT_PUBLIC_KEY_PATH=Join-Path $projectDirectory '.local/security/jwt-public.pem'
    $env:ACCOUNT_MAIL_KEY_PATH=Join-Path $projectDirectory '.local/security/mail.key'
    $taskStorage=Get-Content -LiteralPath (Join-Path $projectDirectory '.local/security/storage.json') -Raw|ConvertFrom-Json
    $env:S3_ACCESS_KEY=$taskStorage.accessKey;$env:S3_SECRET_KEY=$taskStorage.secretKey;$taskStorage=$null
    $env:S3_ENDPOINT='http://127.0.0.1:9000';$env:S3_REGION='us-east-1';$env:S3_BUCKET='comicnet-dev';$env:S3_CREATE_BUCKET='true'
    $env:SMTP_HOST='127.0.0.1';$env:SMTP_PORT='1025'

    if ($Mode -ne 'Verify') {
        $taskListeners=@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
        foreach($taskListener in $taskListeners){
            $taskOwner=Get-CimInstance Win32_Process -Filter "ProcessId=$($taskListener.OwningProcess)" -ErrorAction SilentlyContinue
            $taskCommand=[string]$taskOwner.CommandLine
            $taskJar=Join-Path $projectDirectory 'backend\target\comic-net-backend-0.1.0-SNAPSHOT.jar'
            $taskArgMatch=[regex]::Match($taskCommand,'-cp @(?<path>\S+\.argfile)')
            $taskOwnedSource=$false
            if($taskCommand.Contains('com.comicnet.ComicNetApplication') -and $taskArgMatch.Success){
                $taskArgPath=$taskArgMatch.Groups['path'].Value
                if(Test-Path -LiteralPath $taskArgPath){
                    $taskExpectedClasses=(Join-Path $projectDirectory 'backend\target\classes').Replace('\','\\')
                    $taskOwnedSource=[IO.File]::ReadAllText($taskArgPath).Contains($taskExpectedClasses)
                }
            }
            $taskOwnedJar=$taskCommand.Contains($taskJar)
            if(!$taskOwner -or (!$taskOwnedSource -and !$taskOwnedJar)){
                throw "Port $Port is used by another program; it was not stopped."
            }
            Write-Host "Stopping previous Comic Net backend PID $($taskOwner.ProcessId) on port $Port."
            Stop-Process -Id $taskOwner.ProcessId -ErrorAction Stop
        }
        for($taskWait=0;$taskWait -lt 30;$taskWait++){
            if(!@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue).Count){break}
            Start-Sleep -Milliseconds 200
        }
        if(@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue).Count){
            throw "Port $Port did not become free after stopping the previous backend."
        }
    }
    if ($Mode -eq 'Run' -and !$FromSource) {
        Write-Host 'Building the current backend source before starting the JAR.'
        & .\mvnw.cmd -B -ntp -pl backend -am -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; an older JAR will not be started.' }
    }
    if ($Mode -ne 'Run') {
        if ($IntegrationTest) {
            & .\mvnw.cmd -B -ntp -pl backend -Psql-integration "-Dit.test=$IntegrationTest" test-compile failsafe:integration-test failsafe:verify
        } else {
            & .\mvnw.cmd -B -ntp -pl backend -Psql-integration verify
        }
        if ($LASTEXITCODE -ne 0) { throw 'SQL integration failed. Check backend/target/failsafe-reports; backend was not started.' }
        Write-Host 'SQL integration passed with the real database.' -ForegroundColor Green
    }
    if ($Mode -ne 'Verify') {
        Write-Host "Starting backend at http://localhost:$Port. Keep this window open. Ctrl+C stops it."
        $taskJavaArguments=@();if($Debug){$taskJavaArguments+='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005';Write-Host 'NetBeans: Debug > Attach Debugger > Java JPDA > localhost:5005.'}
        if ($FromSource) {
            $taskMavenArguments=@('-B','-ntp','-pl','backend','org.springframework.boot:spring-boot-maven-plugin:4.1.2:run')
            if ($Debug) { $taskMavenArguments+=('-Dspring-boot.run.jvmArguments=' + $taskJavaArguments[0]) }
            & .\mvnw.cmd @taskMavenArguments
        } else {
            $backendJar = Join-Path $projectDirectory 'backend\target\comic-net-backend-0.1.0-SNAPSHOT.jar'
            if (!(Test-Path -LiteralPath $backendJar)) { throw 'Backend JAR not found. Run this script in VerifyAndRun mode first.' }
            $taskJavaArguments+=@('-jar',$backendJar)
            & (Join-Path $javaDirectory 'bin\java.exe') @taskJavaArguments
        }
        $exitStatus = $LASTEXITCODE
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $exitStatus = 1
} finally {
    foreach ($key in $variableNames) { [Environment]::SetEnvironmentVariable($key, $previousValues[$key], 'Process') }
    if ($runtimeSecret) { $runtimeSecret.Dispose() }
    if ($migrationSecret) { $migrationSecret.Dispose() }
    Pop-Location
}
exit $exitStatus
