# Simple Redis startup script for Windows CI
# Bypasses cluster_manager complexity

param(
    [string]$Action = "start",
    [int]$Port = 6379
)

$redisPath = "C:\redis"
$logFile = "C:\redis\redis.log"
$pidFile = "C:\redis\redis.pid"

function Start-RedisServer {
    Write-Host "Starting Redis on port $Port..."

    # Start Redis with minimal config for testing
    $process = Start-Process -FilePath "$redisPath\redis-server.exe" `
        -ArgumentList @(
            "--port", $Port,
            "--bind", "127.0.0.1",
            "--protected-mode", "no",
            "--save", "",
            "--appendonly", "no",
            "--databases", "16",
            "--timeout", "0",
            "--tcp-keepalive", "60",
            "--loglevel", "notice",
            "--logfile", $logFile
        ) `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardError "$redisPath\error.log"

    # Save PID
    $process.Id | Out-File -FilePath $pidFile -Force

    # Wait for Redis to start
    $attempts = 0
    while ($attempts -lt 30) {
        Start-Sleep -Milliseconds 200
        try {
            $result = & "$redisPath\redis-cli.exe" -p $Port ping 2>$null
            if ($result -eq "PONG") {
                Write-Host "✅ Redis started successfully on port $Port (PID: $($process.Id))"
                return $true
            }
        } catch {
            # Ignore and retry
        }
        $attempts++
    }

    Write-Host "❌ Failed to start Redis after 6 seconds"
    return $false
}

function Stop-RedisServer {
    Write-Host "Stopping Redis..."

    # Try graceful shutdown first
    try {
        & "$redisPath\redis-cli.exe" -p $Port shutdown nosave 2>$null
        Start-Sleep -Seconds 2
    } catch {
        # Ignore errors
    }

    # Force kill if PID file exists
    if (Test-Path $pidFile) {
        $pid = Get-Content $pidFile
        try {
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            Write-Host "✅ Redis stopped (PID: $pid)"
        } catch {
            Write-Host "Process $pid already stopped"
        }
        Remove-Item $pidFile -Force
    }

    # Clean up any remaining redis-server processes on the port
    Get-Process redis-server -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like "*--port $Port*"
    } | Stop-Process -Force -ErrorAction SilentlyContinue
}

function Test-RedisConnection {
    try {
        $result = & "$redisPath\redis-cli.exe" -p $Port ping 2>$null
        if ($result -eq "PONG") {
            Write-Host "✅ Redis is responding on port $Port"
            return $true
        }
    } catch {
        Write-Host "❌ Redis is not responding on port $Port"
        return $false
    }
    return $false
}

# Main execution
switch ($Action.ToLower()) {
    "start" {
        Start-RedisServer
    }
    "stop" {
        Stop-RedisServer
    }
    "test" {
        Test-RedisConnection
    }
    "restart" {
        Stop-RedisServer
        Start-Sleep -Seconds 1
        Start-RedisServer
    }
    default {
        Write-Host "Usage: simple_redis_windows.ps1 -Action [start|stop|test|restart] -Port [port_number]"
    }
}