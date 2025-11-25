# Orchestrated Test Runner - Runs WSL servers, monitoring, and gradle tests with delays
param(
    [string]$WSLDistro = "Ubuntu",
    [int]$TestDelay = 500
)

# Colors
$Red = "Red"
$Green = "Green"
$Yellow = "Yellow"
$Blue = "Cyan"

Write-Host "=== Orchestrated Test Runner ===" -ForegroundColor $Yellow
Write-Host "WSL Distro: $WSLDistro" -ForegroundColor $Blue
Write-Host "Test Delay: ${TestDelay}ms" -ForegroundColor $Blue

# Check WSL availability
try {
    wsl -d $WSLDistro -e echo "WSL test" | Out-Null
    Write-Host "WSL connection successful" -ForegroundColor $Green
} catch {
    Write-Host "Error: WSL is not available" -ForegroundColor $Red
    exit 1
}

# Step 1: Start WSL monitoring and servers
Write-Host "`nStep 1: Starting WSL monitoring and servers..." -ForegroundColor $Yellow
$WSLJob = Start-Job -ScriptBlock {
    param($distro, $workDir)
    wsl -d $distro -e bash -c "cd '$workDir' && ./wsl-server-monitor.sh"
} -ArgumentList $WSLDistro, "/mnt/c/$($PWD.Path.Replace('\', '/').Replace(':', ''))"

Write-Host "Waiting for servers to initialize..."
Start-Sleep 15

# Step 2: Start Windows resource monitoring
Write-Host "`nStep 2: Starting Windows resource monitoring..." -ForegroundColor $Yellow
$MonitorJob = Start-Job -ScriptBlock {
    while ($true) {
        try {
            $cpu = Get-Counter '\Processor(_Total)\% Processor Time' | Select-Object -ExpandProperty CounterSamples | Select-Object -ExpandProperty CookedValue
            $mem = Get-CimInstance Win32_OperatingSystem
            $memPct = [math]::Round((($mem.TotalVisibleMemorySize - $mem.FreePhysicalMemory) / $mem.TotalVisibleMemorySize) * 100, 1)
            $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
            
            if ($cpu -gt 80 -or $memPct -gt 80) {
                Write-Host "[$timestamp] Windows Alert - CPU: $cpu%, Memory: $memPct%" -ForegroundColor Red
            }
        } catch {
            # Ignore monitoring errors
        }
        Start-Sleep 2
    }
}

Write-Host "Resource monitoring started" -ForegroundColor $Green

# Step 3: Run main tests (excluding lazy connection tests)
Write-Host "`nStep 3: Running main integration tests (excluding lazy connection tests)..." -ForegroundColor $Yellow

Push-Location java

Write-Host "Running gradle test with ${TestDelay}ms delays and lazy connection test exclusion..." -ForegroundColor $Blue

$mainTestResult = & .\gradlew.bat :integTest:test -PskipClusterShutdown -PskipLazyConnectionTests -PtestDelay=$TestDelay `
    "-Dtest.server.standalone=127.0.0.1:6379" `
    "-Dtest.server.standalone.tls=127.0.0.1:6380" `
    "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" `
    "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005" `
    2>&1

$mainTestExitCode = $LASTEXITCODE

if ($mainTestExitCode -eq 0) {
    Write-Host "Main tests completed successfully" -ForegroundColor $Green
} else {
    Write-Host "Some main tests failed" -ForegroundColor $Yellow
}

# Step 4: Clear servers before lazy connection tests
Write-Host "`nStep 4: Clearing servers before lazy connection tests..." -ForegroundColor $Yellow
"clear_servers" | Out-File -FilePath "\\wsl$\$WSLDistro\tmp\wsl_command.txt" -Encoding UTF8 -NoNewline
Start-Sleep 2

# Step 5: Run lazy connection tests at the end
Write-Host "`nStep 5: Running lazy connection tests..." -ForegroundColor $Yellow

$lazyTestResult = & .\gradlew.bat :integTest:lazyConnectionTest -PskipClusterShutdown `
    "-Dtest.server.standalone=127.0.0.1:6379" `
    "-Dtest.server.standalone.tls=127.0.0.1:6380" `
    "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" `
    "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005" `
    2>&1

$lazyTestExitCode = $LASTEXITCODE

if ($lazyTestExitCode -eq 0) {
    Write-Host "Lazy connection tests completed successfully" -ForegroundColor $Green
} else {
    Write-Host "Lazy connection tests failed" -ForegroundColor $Red
}

Pop-Location

# Step 6: Cleanup
Write-Host "`nStep 6: Cleanup..." -ForegroundColor $Yellow

# Stop WSL monitoring
"stop" | Out-File -FilePath "\\wsl$\$WSLDistro\tmp\wsl_command.txt" -Encoding UTF8 -NoNewline

# Stop Windows monitoring
Stop-Job $MonitorJob -ErrorAction SilentlyContinue
Remove-Job $MonitorJob -ErrorAction SilentlyContinue

# Stop WSL job
Stop-Job $WSLJob -ErrorAction SilentlyContinue
Remove-Job $WSLJob -ErrorAction SilentlyContinue

# Summary
Write-Host "`n=== Test Summary ===" -ForegroundColor $Yellow
if ($mainTestExitCode -eq 0 -and $lazyTestExitCode -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor $Green
    exit 0
} elseif ($mainTestExitCode -eq 0) {
    Write-Host "Main tests passed, but lazy connection tests failed" -ForegroundColor $Yellow
    exit 1
} elseif ($lazyTestExitCode -eq 0) {
    Write-Host "Lazy connection tests passed, but main tests failed" -ForegroundColor $Yellow
    exit 1
} else {
    Write-Host "Both main tests and lazy connection tests failed" -ForegroundColor $Red
    exit 1
}
