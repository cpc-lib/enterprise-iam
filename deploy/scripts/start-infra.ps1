# PowerShell Script: Start Enterprise IAM Infrastructure
param (
    [switch]$ForceRecreate
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Resolve-Path (Join-Path $ScriptDir "..")

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Enterprise IAM Infrastructure Startup" -ForegroundColor Cyan
Write-Host " Directory: $DeployDir" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Check Docker Engine daemon connection
try {
    docker info > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Docker daemon not running. Please start Docker Desktop."
        exit 1
    }
} catch {
    Write-Warning "Docker command failed. Please verify Docker installation."
    exit 1
}

Push-Location $DeployDir
try {
    $composeArgs = @("up", "-d")
    if ($ForceRecreate) {
        $composeArgs += "--force-recreate"
    }

    Write-Host "Starting all infrastructure services (MySQL, Redis, RabbitMQ, MinIO, Nacos, Seata)..." -ForegroundColor Yellow
    docker compose @composeArgs

    Write-Host "`nContainers startup completed. Current status:" -ForegroundColor Green
    docker compose ps -a

    Write-Host "`n[Service Endpoints & Credentials]" -ForegroundColor Green
    Write-Host "  - MySQL 8.4:         localhost:3306 (user: root, pass: root)"
    Write-Host "  - Redis 7:           localhost:6379"
    Write-Host "  - RabbitMQ AMQP:     localhost:5672 (user: guest, pass: guest)"
    Write-Host "  - RabbitMQ Web UI:   http://localhost:15672"
    Write-Host "  - MinIO API:         http://localhost:9000 (user: minioadmin, pass: minioadmin)"
    Write-Host "  - MinIO Console:     http://localhost:9001"
    Write-Host "  - Nacos Console:     http://localhost:8848/nacos (user: nacos, pass: nacos)"
    Write-Host "  - Seata Server RPC:  localhost:8091"
    Write-Host "  - Seata Console:     http://localhost:7091"
    Write-Host "`nAll 8 IAM microservice databases and Seata database are initialized and ready." -ForegroundColor Cyan
} finally {
    Pop-Location
}
