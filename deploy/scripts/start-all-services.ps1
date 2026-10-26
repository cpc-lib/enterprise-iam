# ==============================================================================
# Enterprise IAM Platform - 启动全量微服务与网关
# 端口规划:
# 8080 -> iam-gateway
# 8081 -> iam-auth-service
# 8082 -> iam-identity-service
# 8083 -> iam-organization-service
# 8084 -> iam-authorization-service
# 8085 -> iam-sharing-service
# 8086 -> iam-file-service
# 8087 -> iam-audit-service
# 8088 -> iam-job-service
# ==============================================================================

$RootPath = Resolve-Path (Join-Path $PSScriptRoot "..\..")

$services = @(
    @{ Name = "iam-auth-service"; Port = 8081 },
    @{ Name = "iam-identity-service"; Port = 8082 },
    @{ Name = "iam-organization-service"; Port = 8083 },
    @{ Name = "iam-authorization-service"; Port = 8084 },
    @{ Name = "iam-sharing-service"; Port = 8085 },
    @{ Name = "iam-file-service"; Port = 8086 },
    @{ Name = "iam-audit-service"; Port = 8087 },
    @{ Name = "iam-job-service"; Port = 8088 },
    @{ Name = "iam-gateway"; Port = 8080 }
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Enterprise IAM Backend - 启动 8 大微服务及网关" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

foreach ($svc in $services) {
    $name = $svc.Name
    $port = $svc.Port
    
    # 检查端口是否已被监听
    $existing = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "[$name] 端口 $port 正在运行中 (PID: $($existing.OwningProcess[0]))，跳过启动。" -ForegroundColor Green
        continue
    }

    Write-Host "正在启动 [$name] (Port: $port)..." -ForegroundColor Yellow
    $pomPath = Join-Path $RootPath "backend\$name\pom.xml"
    $proc = Start-Process -FilePath "mvn" -ArgumentList "-f", $pomPath, "spring-boot:run" -WorkingDirectory $RootPath -WindowStyle Hidden -PassThru
    Write-Host "[$name] 进程已在后台启动 (PID: $($proc.Id))" -ForegroundColor Cyan
    Start-Sleep -Seconds 2
}

Write-Host "`n所有微服务启动指令已分发，可通过网关 http://localhost:8080 访问。" -ForegroundColor Green
