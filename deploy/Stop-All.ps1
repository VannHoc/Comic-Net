# Dung toan bo he thong Comic Net: Backend (8080), RustFS (9000), Mailpit (8025)
$ErrorActionPreference = 'SilentlyContinue'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$taskData = Join-Path $projectDirectory '.local/data'

Write-Host "Dang dung toan bo he thong Comic Net..." -ForegroundColor Yellow

# 1. Stop Backend on port 8080
$backendConn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($backendConn) {
    Stop-Process -Id $backendConn.OwningProcess -Force -ErrorAction SilentlyContinue
    Write-Host "Da dung Backend (PID $($backendConn.OwningProcess))." -ForegroundColor Green
}

# 2. Stop RustFS
$rustPidFile = Join-Path $taskData 'rustfs.pid'
if (Test-Path $rustPidFile) {
    $pidText = (Get-Content $rustPidFile).Trim()
    if ($pidText) {
        Stop-Process -Id ([int]$pidText) -Force -ErrorAction SilentlyContinue
        Remove-Item $rustPidFile -ErrorAction SilentlyContinue
        Write-Host "Da dung RustFS (PID $pidText)." -ForegroundColor Green
    }
}
$rustConn = Get-NetTCPConnection -LocalPort 9000 -State Listen -ErrorAction SilentlyContinue
if ($rustConn) {
    Stop-Process -Id $rustConn.OwningProcess -Force -ErrorAction SilentlyContinue
    Write-Host "Da dung RustFS tren cong 9000." -ForegroundColor Green
}

# 3. Stop Mailpit
$mailPidFile = Join-Path $taskData 'mailpit.pid'
if (Test-Path $mailPidFile) {
    $pidText = (Get-Content $mailPidFile).Trim()
    if ($pidText) {
        Stop-Process -Id ([int]$pidText) -Force -ErrorAction SilentlyContinue
        Remove-Item $mailPidFile -ErrorAction SilentlyContinue
        Write-Host "Da dung Mailpit (PID $pidText)." -ForegroundColor Green
    }
}
$mailConn = Get-NetTCPConnection -LocalPort 8025 -State Listen -ErrorAction SilentlyContinue
if ($mailConn) {
    Stop-Process -Id $mailConn.OwningProcess -Force -ErrorAction SilentlyContinue
    Write-Host "Da dung Mailpit tren cong 8025." -ForegroundColor Green
}

Write-Host "Toan bo he thong Comic Net da duoc tat an toan." -ForegroundColor Cyan
