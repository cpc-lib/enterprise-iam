# PowerShell Script: Check Enterprise IAM Infrastructure Status and Ports
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Resolve-Path (Join-Path $ScriptDir "..")

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Enterprise IAM Infrastructure Health Check" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

Push-Location $DeployDir
try {
    Write-Host "`n[1. Docker Compose Services Status]" -ForegroundColor Yellow
    docker compose ps -a

    Write-Host "`n[2. Local TCP Port Probing]" -ForegroundColor Yellow
    $ports = @(
        @{ Name = "MySQL 8.4"; Port = 3306 },
        @{ Name = "Redis 7"; Port = 6379 },
        @{ Name = "RabbitMQ AMQP"; Port = 5672 },
        @{ Name = "RabbitMQ Web UI"; Port = 15672 },
        @{ Name = "MinIO API"; Port = 9000 },
        @{ Name = "MinIO Console"; Port = 9001 },
        @{ Name = "Nacos Console"; Port = 8848 },
        @{ Name = "Seata Server RPC"; Port = 8091 },
        @{ Name = "Seata Console"; Port = 7091 }
    )

    foreach ($p in $ports) {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        try {
            $asyncResult = $tcpClient.BeginConnect("127.0.0.1", $p.Port, $null, $null)
            $success = $asyncResult.AsyncWaitHandle.WaitOne(800)
            if ($success -and $tcpClient.Connected) {
                Write-Host "  [OK]   $($p.Name.PadRight(18)) Port $($p.Port) connected successfully" -ForegroundColor Green
                $tcpClient.EndConnect($asyncResult)
            } else {
                Write-Host "  [WAIT] $($p.Name.PadRight(18)) Port $($p.Port) no response (starting or initializing)" -ForegroundColor DarkGray
            }
        } catch {
            Write-Host "  [DOWN] $($p.Name.PadRight(18)) Port $($p.Port) connection refused" -ForegroundColor Red
        } finally {
            $tcpClient.Close()
        }
    }
} finally {
    Pop-Location
}
