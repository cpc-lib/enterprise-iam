# ==============================================================================
# Enterprise IAM Platform - 停止全量微服务与网关
# 释放端口: 8080 - 8088
# ==============================================================================

$ports = 8080..8088

Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host "  Enterprise IAM Backend - 停止微服务及网关进程" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow

foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($p in $pids) {
            try {
                Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                Write-Host "已终止端口 $port 上的进程 (PID: $p)" -ForegroundColor Green
            } catch {
                Write-Host "无法终止端口 $port 上的进程 (PID: $p)" -ForegroundColor Red
            }
        }
    } else {
        Write-Host "端口 $port 未被监听。" -ForegroundColor DarkGray
    }
}

Write-Host "`n所有微服务已停止。" -ForegroundColor Green
