<#
.SYNOPSIS
  Enterprise IAM - 超级系统管理员 (superadmin) 密码应急重置脚本 (PowerShell)

.DESCRIPTION
  当超级系统管理员忘记密码或被安全锁定时，运行本脚本可一键安全重置密码。
  默认重置为: admin123
  自动计算 Argon2id (m=19456, t=2, p=1) 哈希，更新 iam_auth 凭据表，解除账户锁定，并强制吊销历史活跃会话。

.EXAMPLE
  .\reset-superadmin-password.ps1
  默认一键重置为: admin123

.EXAMPLE
  .\reset-superadmin-password.ps1 -Password "YourCustomPassword!"
#>

param (
    [string]$Password = "admin123",
    [string]$DbHost = "localhost",
    [int]$DbPort = 3306,
    [string]$DbUser = "root",
    [string]$DbPassword = "root"
)

# 确保 UTF-8 控制台字符显示正常
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$PyScript = Join-Path $ScriptDir "reset-superadmin-password.py"
$SqlScript = Join-Path $ScriptDir "reset-superadmin-password.sql"

$PythonCmd = Get-Command python -ErrorAction SilentlyContinue

if ($PythonCmd -and (Test-Path $PyScript)) {
    python $PyScript --password $Password --host $DbHost --port $DbPort --user $DbUser --db-password $DbPassword
} else {
    Write-Host "[INFO] 未检测到 Python 环境，正在使用 Docker 执行 SQL 重置通道..." -ForegroundColor Cyan
    if (Test-Path $SqlScript) {
        Get-Content $SqlScript | docker exec -i iam-mysql mysql -uroot -proot iam_auth
        Write-Host "==================================================================" -ForegroundColor Green
        Write-Host " [SUCCESS] 超级管理员密码已通过 SQL 应急重置为: admin123" -ForegroundColor Green
        Write-Host " 租户编码: system" -ForegroundColor Green
        Write-Host " 登录账号: superadmin@enterprise.com 或 superadmin" -ForegroundColor Green
        Write-Host " 登录密码: admin123" -ForegroundColor Green
        Write-Host "==================================================================" -ForegroundColor Green
    } else {
        Write-Error "找不到 SQL 重置脚本: $SqlScript"
    }
}
