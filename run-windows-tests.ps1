# Windows Test Runner - runs tests on Windows while using WSL servers

param(
    [string]$WSLDistro = "Ubuntu"
)

# Colors for PowerShell
$Red = "Red"
$Green = "Green"
$Yellow = "Yellow"
$Blue = "Cyan"

# Counters
$PassCount = 0
$FailCount = 0
$TotalCount = 0
$FailedTests = @()
$LazyConnectionTest = "glide.ConnectionTests.test_lazy_connection_establishes_on_first_command"

Write-Host "=== Windows Test Runner with WSL Servers ===" -ForegroundColor $Yellow

# Function to send command to WSL
function Send-WSLCommand {
    param([string]$Command)
    
    $Command | Out-File -FilePath "\\wsl$\$WSLDistro\tmp\wsl_command.txt" -Encoding UTF8
    Start-Sleep -Milliseconds 100
}

# Function to run a single test
function Run-Test {
    param([string]$TestName)
    
    $TestDisplay = ($TestName -split '\.')[-1]
    Write-Host "`nRunning: $TestDisplay" -ForegroundColor $Yellow
    
    # Notify WSL of current test
    Send-WSLCommand "set_test $TestDisplay"
    
    # Run the test
    $TestPassed = $false
    try {
        Push-Location java
        
        $result = & .\gradlew.bat :integTest:test --tests $TestName -PskipClusterShutdown `
            "-Dtest.server.standalone=127.0.0.1:6379" `
            "-Dtest.server.standalone.tls=127.0.0.1:6380" `
            "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" `
            "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005" `
            2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "PASS: $TestDisplay" -ForegroundColor $Green
            $script:PassCount++
            $TestPassed = $true
        } else {
            Write-Host "FAIL: $TestDisplay" -ForegroundColor $Red
            $script:FailedTests += $TestName
            $script:FailCount++
            
            # Show error context
            Write-Host "  Error context:" -ForegroundColor $Red
            $result | Select-Object -Last 5 | ForEach-Object { Write-Host "    $_" -ForegroundColor $Red }
        }
    }
    catch {
        Write-Host "FAIL: $TestDisplay (Exception: $($_.Exception.Message))" -ForegroundColor $Red
        $script:FailedTests += $TestName
        $script:FailCount++
    }
    finally {
        Pop-Location
        $script:TotalCount++
        
        # Clear servers after each test
        Send-WSLCommand "clear_servers"
        Start-Sleep -Milliseconds 500
    }
}

# Check if WSL is available
try {
    wsl -d $WSLDistro -e echo "WSL connection test" | Out-Null
    Write-Host "WSL connection successful" -ForegroundColor $Green
}
catch {
    Write-Host "Error: Cannot connect to WSL distribution '$WSLDistro'" -ForegroundColor $Red
    Write-Host "Make sure WSL is installed and the distribution exists" -ForegroundColor $Red
    exit 1
}

# Get list of tests (simplified - you may want to enhance this)
Write-Host "`nDiscovering tests..." -ForegroundColor $Yellow

# For now, use a predefined list of common tests
# You can enhance this by parsing Java files or using gradle test discovery
$TestMethods = @(
    "glide.ConnectionTests.test_client_name",
    "glide.ConnectionTests.test_select_standalone_database_id",
    "glide.ConnectionTests.test_custom_command_info",
    "glide.ConnectionTests.test_request_timeout",
    "glide.SharedCommandTests.test_ping",
    "glide.SharedCommandTests.test_info_server_and_cluster",
    "glide.SharedCommandTests.test_config_get_and_config_set",
    "glide.SharedCommandTests.test_echo"
    # Add more tests as needed
)

# Filter out lazy connection test for now
$TestsToRun = $TestMethods | Where-Object { $_ -notlike "*$LazyConnectionTest*" }

Write-Host "Found $($TestsToRun.Count) tests to run" -ForegroundColor $Blue

# Run all tests except lazy connection test
Write-Host "`nRunning individual tests..." -ForegroundColor $Yellow
foreach ($test in $TestsToRun) {
    Run-Test $test
}

# Run the problematic lazy connection test last
Write-Host "`nRunning lazy connection test last..." -ForegroundColor $Yellow
Run-Test $LazyConnectionTest

# Stop WSL monitoring
Send-WSLCommand "stop"

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
