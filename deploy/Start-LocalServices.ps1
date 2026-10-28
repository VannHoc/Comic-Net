param([switch]$Install)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskLocal=Join-Path $taskRoot '.local'
$taskSecurity=Join-Path $taskLocal 'security'
$taskData=Join-Path $taskLocal 'data'
New-Item -ItemType Directory -Force -Path $taskSecurity,$taskData | Out-Null

function Install-VerifiedService([string]$Name,[string]$Url,[string]$Sha256,[string]$Executable){
    $taskDirectory=Join-Path $taskLocal ('services/'+$Name)
    $taskBinary=Join-Path $taskDirectory $Executable
    if(Test-Path -LiteralPath $taskBinary){return $taskBinary}
    if(!$Install){throw "Missing $Name. Run deploy/Start-LocalServices.ps1 -Install once to download the pinned official release."}
    New-Item -ItemType Directory -Force -Path $taskDirectory | Out-Null
    $taskArchive=Join-Path $taskDirectory 'release.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $taskArchive
    if((Get-FileHash -LiteralPath $taskArchive -Algorithm SHA256).Hash -ne $Sha256){throw "$Name release checksum mismatch. The executable will not be started."}
    Expand-Archive -LiteralPath $taskArchive -DestinationPath $taskDirectory -Force
    if(!(Test-Path -LiteralPath $taskBinary)){throw "$Name archive did not contain its expected executable."}
    return $taskBinary
}
function Confirm-LocalService([string]$Name,[string]$Binary,[string]$Health,[string]$Arguments){
    $taskPidFile=Join-Path $taskData ($Name+'.pid')
    $taskProcess=$null
    if(Test-Path -LiteralPath $taskPidFile){$taskSavedId=0;if([int]::TryParse(([IO.File]::ReadAllText($taskPidFile)).Trim(),[ref]$taskSavedId)){$taskProcess=Get-Process -Id $taskSavedId -ErrorAction SilentlyContinue}}
    if($taskProcess -and $taskProcess.Path -ne $Binary){
        Write-Host "$Name stale PID ignored; the unrelated process will not be stopped."
        $taskProcess=$null
    }
    if(!$taskProcess){
        try{$taskOccupied=Invoke-WebRequest -UseBasicParsing -Uri $Health -TimeoutSec 2 -ErrorAction Stop;if($taskOccupied.StatusCode -eq 200){throw 'PORT_OCCUPIED'}}catch{if($_.Exception.Message -eq 'PORT_OCCUPIED'){throw "$Name endpoint is already in use by an unmanaged service."}}
        $taskProcess=Start-Process -FilePath $Binary -ArgumentList $Arguments -WorkingDirectory $taskLocal -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskData ($Name+'.log')) -RedirectStandardError (Join-Path $taskData ($Name+'.error.log')) -PassThru
        [IO.File]::WriteAllText($taskPidFile,[string]$taskProcess.Id)
    }
    for($taskAttempt=0;$taskAttempt -lt 30;$taskAttempt++){
        try{if((Invoke-WebRequest -UseBasicParsing -Uri $Health -TimeoutSec 2).StatusCode -eq 200){Write-Host "$Name ready: $Health";return}}catch{}
        if($taskProcess.HasExited){throw "$Name stopped during startup. Inspect .local/data/$Name.error.log."}
        Start-Sleep -Milliseconds 300
    }
    throw "$Name did not become ready. Inspect its local logs."
}
$taskRust=Install-VerifiedService 'rustfs' 'https://github.com/rustfs/rustfs/releases/download/1.0.4/rustfs-windows-x86_64-v1.0.4.zip' '2e3af0b187dd70435dfdefe5f61aae7eede5a21fc9a2a2f07e289f727adbcfa6' 'rustfs.exe'
$taskMail=Install-VerifiedService 'mailpit' 'https://github.com/axllent/mailpit/releases/download/v1.32.0/mailpit-windows-amd64.zip' '16915767a6c95dca569336e8f561f91cf355e9a33c57b91deedcc10a4bba7fd4' 'mailpit.exe'
$taskStorageFile=Join-Path $taskSecurity 'storage.json'
if(!(Test-Path -LiteralPath $taskStorageFile)){
    $taskRandom=[Security.Cryptography.RandomNumberGenerator]::Create();$taskAccess=New-Object byte[] 16;$taskSecret=New-Object byte[] 32
    try{$taskRandom.GetBytes($taskAccess);$taskRandom.GetBytes($taskSecret)}finally{$taskRandom.Dispose()}
    $taskCredentials=@{accessKey=[BitConverter]::ToString($taskAccess).Replace('-','');secretKey=[BitConverter]::ToString($taskSecret).Replace('-','')}
    [IO.File]::WriteAllText($taskStorageFile,($taskCredentials|ConvertTo-Json));$taskCredentials=$null
    [Array]::Clear($taskAccess,0,$taskAccess.Length);[Array]::Clear($taskSecret,0,$taskSecret.Length)
}
$taskStorage=Get-Content -LiteralPath $taskStorageFile -Raw|ConvertFrom-Json
$taskOriginal=@{};foreach($taskName in @('RUSTFS_ACCESS_KEY','RUSTFS_SECRET_KEY','RUSTFS_REGION')){$taskOriginal[$taskName]=[Environment]::GetEnvironmentVariable($taskName,'Process')}
try{
    $env:RUSTFS_ACCESS_KEY=$taskStorage.accessKey;$env:RUSTFS_SECRET_KEY=$taskStorage.secretKey;$env:RUSTFS_REGION='us-east-1'
    Confirm-LocalService 'rustfs' $taskRust 'http://127.0.0.1:9000/health' 'server --address 127.0.0.1:9000 --console-enable --console-address 127.0.0.1:9001 data/rustfs'
}finally{foreach($taskName in $taskOriginal.Keys){[Environment]::SetEnvironmentVariable($taskName,$taskOriginal[$taskName],'Process')};$taskStorage=$null}
$taskMailArguments='--smtp 127.0.0.1:1025 --listen 127.0.0.1:8025 --database "'+(Join-Path $taskData 'mailpit.db')+'" --disable-version-check'
Confirm-LocalService 'mailpit' $taskMail 'http://127.0.0.1:8025/api/v1/info' $taskMailArguments
Write-Host 'Development services only. SMTP inbox: http://127.0.0.1:8025. Keep .local/security private.'
