# PowerShell Script: Stop Enterprise IAM Infrastructure
param (
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Resolve-Path (Join-Path $ScriptDir "..")

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Enterprise IAM Infrastructure Shutdown" -ForegroundColor Cyan
Write-Host " Directory: $DeployDir" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

Push-Location $DeployDir
try {
    $composeArgs = @("down")
    if ($RemoveVolumes) {
        Write-Warning "Flag -RemoveVolumes detected: All persistent database and storage volumes will be deleted!"
        $composeArgs += "-v"
    }

    Write-Host "Stopping all infrastructure containers..." -ForegroundColor Yellow
    docker compose @composeArgs

    Write-Host "`nAll infrastructure services stopped successfully." -ForegroundColor Green
} finally {
    Pop-Location
}
