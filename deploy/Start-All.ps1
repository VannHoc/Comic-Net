# One-click startup for the entire Comic Net system:
# 1. Local services (RustFS, Mailpit)
# 2. Spring Boot Backend (port 8080)
# 3. Desktop Swing GUI
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "         COMIC NET - HE THONG KHOI DONG 1-CLICK          " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Start / Verify local services (RustFS & Mailpit)
Write-Host "`n[1/3] Kiem tra dich vu luu tru (RustFS:9000) va email (Mailpit:8025)..." -ForegroundColor Yellow
& (Join-Path $PSScriptRoot 'Start-LocalServices.ps1')

# 2. Start Backend if not already running
Write-Host "`n[2/3] Kiem tra Backend Spring Boot (port 8080)..." -ForegroundColor Yellow
$backendActive = $false
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2 -ErrorAction SilentlyContinue
    if ($health.status -eq 'UP') {
        $backendActive = $true
        Write-Host "Backend da san sang tai http://localhost:8080 (Status: UP)." -ForegroundColor Green
    }
} catch {}

if (!$backendActive) {
    Write-Host "Khoi dong Backend Spring Boot trong cua so rieng..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$projectDirectory'; .\deploy\Start-Backend.ps1 -Mode Run -UseLocalConfiguration"
    Write-Host "Dang cho Backend khoi dong xong..." -NoNewline
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        Write-Host "." -NoNewline
        try {
            $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 1 -ErrorAction SilentlyContinue
            if ($health.status -eq 'UP') {
                $backendActive = $true
                break
            }
        } catch {}
    }
    Write-Host ""
    if (!$backendActive) {
        throw "Backend khong the khoi dong sau 30 giay. Kiem tra cua so console Backend."
    }
    Write-Host "Backend da san sang tai http://localhost:8080!" -ForegroundColor Green
}

# 3. Launch Desktop Swing application
Write-Host "`n[3/3] Khoi chay ung dung Desktop Swing..." -ForegroundColor Yellow
& (Join-Path $PSScriptRoot 'Start-Desktop.ps1')
