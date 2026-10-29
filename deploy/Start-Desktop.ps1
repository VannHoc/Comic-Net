# One-click launcher for Comic Net Desktop Swing application
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot

# Locate JDK 21
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

$jarPath = Join-Path $projectDirectory 'desktop-swing\target\comic-net-desktop-swing-0.1.0-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $jarPath)) {
    Write-Host 'Building desktop-swing package first...' -ForegroundColor Cyan
    Push-Location $projectDirectory
    try {
        & .\mvnw.cmd -o -B -ntp -pl desktop-swing -am -DskipTests package
    } finally {
        Pop-Location
    }
}

Write-Host "Launching Comic Net Desktop Swing (JDK 21: $javaDirectory)..." -ForegroundColor Green
$javaExe = Join-Path $javaDirectory 'bin\java.exe'
Push-Location (Join-Path $projectDirectory 'desktop-swing')
try {
    & $javaExe -jar $jarPath
} finally {
    Pop-Location
}
