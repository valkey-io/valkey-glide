# Comprehensive Test Runner - Single script for Developer Command Prompt
# Runs WSL servers + Windows monitoring + Windows tests

param(
    [string]$WSLDistro = "Ubuntu"
)

# Initialize counters
$PassCount = 0
$FailCount = 0
$TotalCount = 0
$FailedTests = @()

# Colors
$Red = "Red"
$Green = "Green" 
$Yellow = "Yellow"
$Blue = "Cyan"

Write-Host "=== Comprehensive Test Runner ===" -ForegroundColor $Yellow
Write-Host "Starting WSL monitoring, servers, Windows monitoring, and tests..." -ForegroundColor $Blue

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
Start-Sleep 10

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

# Step 3: Define test methods
Write-Host "`nStep 3: Discovering tests..." -ForegroundColor $Yellow

$TestMethods = @(
    "glide.ConnectionTests.test_client_name",
    "glide.ConnectionTests.test_select_standalone_database_id", 
    "glide.ConnectionTests.test_custom_command_info",
    "glide.ConnectionTests.test_request_timeout",
    "glide.SharedCommandTests.test_ping",
    "glide.SharedCommandTests.test_info_server_and_cluster",
    "glide.SharedCommandTests.test_config_get_and_config_set",
    "glide.SharedCommandTests.test_echo",
    "glide.SharedCommandTests.test_set_and_get",
    "glide.SharedCommandTests.test_del",
    "glide.SharedCommandTests.test_exists",
    "glide.SharedCommandTests.test_expire_and_ttl",
    "glide.SharedCommandTests.test_mget",
    "glide.SharedCommandTests.test_mset",
    "glide.SharedCommandTests.test_incr_and_decr",
    "glide.SharedCommandTests.test_hash_commands",
    "glide.SharedCommandTests.test_list_commands",
    "glide.SharedCommandTests.test_set_commands",
    "glide.SharedCommandTests.test_sorted_set_commands"
)

$LazyTest = "glide.ConnectionTests.test_lazy_connection_establishes_on_first_command"

Write-Host "Found $($TestMethods.Count) tests to run" -ForegroundColor $Blue

# Function to send WSL command
function Send-WSLCommand {
    param([string]$Command)
    $Command | Out-File -FilePath "\\wsl$\$WSLDistro\tmp\wsl_command.txt" -Encoding UTF8 -NoNewline
    Start-Sleep -Milliseconds 500
}

# Function to run a single test
function Run-Test {
    param([string]$TestName)
    
    $TestDisplay = ($TestName -split '\.')[-1]
    Write-Host "`nRunning: $TestDisplay" -ForegroundColor $Yellow
    
    # Notify WSL of current test
    Send-WSLCommand "set_test $TestDisplay"
    
    try {
        Push-Location java
        
        # Run test in its own gradle process
        $result = & .\gradlew.bat :integTest:test --tests $TestName -PskipClusterShutdown `
            "-Dtest.server.standalone=127.0.0.1:6379" `
            "-Dtest.server.standalone.tls=127.0.0.1:6380" `
            "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" `
            "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005" `
            2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "PASS: $TestDisplay" -ForegroundColor $Green
            $script:PassCount++
        } else {
            Write-Host "FAIL: $TestDisplay" -ForegroundColor $Red
            $script:FailedTests += $TestName
            $script:FailCount++
            
            # Show error context
            Write-Host "  Error context:" -ForegroundColor $Red
            $result | Select-Object -Last 3 | ForEach-Object { Write-Host "    $_" -ForegroundColor $Red }
        }
    } catch {
        Write-Host "FAIL: $TestDisplay (Exception: $($_.Exception.Message))" -ForegroundColor $Red
        $script:FailedTests += $TestName
        $script:FailCount++
    } finally {
        Pop-Location
        $script:TotalCount++
        
        # Clear servers after each test
        Send-WSLCommand "clear_servers"
        Start-Sleep 1
    }
}

# Step 4: Run each test individually
Write-Host "`nStep 4: Running individual tests..." -ForegroundColor $Yellow

foreach ($test in $TestMethods) {
    Run-Test $test
}

# Step 5: Run problematic lazy connection test last
Write-Host "`nStep 5: Running lazy connection test last..." -ForegroundColor $Yellow
Run-Test $LazyTest

# Step 6: Cleanup and summary
Write-Host "`nStep 6: Cleanup and summary..." -ForegroundColor $Yellow

# Stop WSL monitoring
Send-WSLCommand "stop"

# Stop Windows monitoring
Stop-Job $MonitorJob -ErrorAction SilentlyContinue
Remove-Job $MonitorJob -ErrorAction SilentlyContinue

# Stop WSL job
Stop-Job $WSLJob -ErrorAction SilentlyContinue  
Remove-Job $WSLJob -ErrorAction SilentlyContinue

# Print summary
Write-Host "`n=== Test Summary ===" -ForegroundColor $Yellow
Write-Host "Total tests: $TotalCount"
Write-Host "Passed: $PassCount" -ForegroundColor $Green
Write-Host "Failed: $FailCount" -ForegroundColor $Red

if ($FailedTests.Count -gt 0) {
    Write-Host "`nFailed tests:" -ForegroundColor $Red
    foreach ($failedTest in $FailedTests) {
        Write-Host "  - $failedTest" -ForegroundColor $Red
    }
}

# Exit with appropriate code
if ($FailCount -eq 0) {
    Write-Host "`nAll tests passed!" -ForegroundColor $Green
    exit 0
} else {
    Write-Host "`nSome tests failed." -ForegroundColor $Red
    exit 1
}
