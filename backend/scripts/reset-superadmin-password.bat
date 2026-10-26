@echo off
chcp 65001 >nul
title Enterprise IAM - Reset Superadmin Password

echo ==================================================================
echo  Enterprise IAM - 超级管理员密码应急重置 (Windows Batch)
echo ==================================================================
echo.

set PASSWORD=%1
if "%PASSWORD%"=="" set PASSWORD=admin123

where python >nul 2>nul
if %ERRORLEVEL% equ 0 (
    python "%~dp0reset-superadmin-password.py" --password "%PASSWORD%"
) else (
    echo [INFO] 未检测到 Python，使用 Docker 回退重置...
    docker exec -i iam-mysql mysql -uroot -proot iam_auth < "%~dp0reset-superadmin-password.sql"
    echo [SUCCESS] 密码已成功重置为: admin123
)

echo.
pause
