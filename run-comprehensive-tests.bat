@echo off
setlocal enabledelayedexpansion

REM Comprehensive Test Runner - Single script for Developer Command Prompt
REM Runs WSL servers + Windows monitoring + Windows tests

echo === Comprehensive Test Runner ===
echo Starting WSL monitoring, servers, Windows monitoring, and tests...

REM Initialize counters
set PASS_COUNT=0
set FAIL_COUNT=0
set TOTAL_COUNT=0
set FAILED_TESTS=

REM Colors (using echo with color codes)
set RED=[91m
set GREEN=[92m
set YELLOW=[93m
set BLUE=[94m
set NC=[0m

REM Check if WSL is available
wsl -e echo "WSL test" >nul 2>&1
if errorlevel 1 (
    echo %RED%Error: WSL is not available%NC%
    exit /b 1
)

echo %GREEN%WSL connection successful%NC%

REM Step 1: Start WSL monitoring and servers in background
echo %YELLOW%Step 1: Starting WSL monitoring and servers...%NC%
start "WSL Monitor" wsl -e bash -c "cd /mnt/c/%CD:\=/% && ./wsl-server-monitor.sh"

REM Wait for servers to start
echo Waiting for servers to initialize...
timeout /t 10 /nobreak >nul

REM Step 2: Start Windows resource monitoring in background
echo %YELLOW%Step 2: Starting Windows resource monitoring...%NC%
start "Windows Monitor" powershell -WindowStyle Minimized -Command "& { while($true) { $cpu = Get-Counter '\Processor(_Total)\%% Processor Time' | Select -ExpandProperty CounterSamples | Select -ExpandProperty CookedValue; $mem = Get-CimInstance Win32_OperatingSystem; $memPct = [math]::Round((($mem.TotalVisibleMemorySize - $mem.FreePhysicalMemory) / $mem.TotalVisibleMemorySize) * 100, 1); $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'; if($cpu -gt 80 -or $memPct -gt 80) { Write-Host \"[$timestamp] Windows Alert - CPU: $cpu%%, Memory: $memPct%%\" } Start-Sleep 2 } }"

REM Step 3: Get list of all tests
echo %YELLOW%Step 3: Discovering tests...%NC%
cd java

REM Get test list using gradle (simplified approach)
echo Discovering integration tests...

REM Define test classes and methods (you can enhance this with dynamic discovery)
set TEST_METHODS=^
glide.ConnectionTests.test_client_name ^
glide.ConnectionTests.test_select_standalone_database_id ^
glide.ConnectionTests.test_custom_command_info ^
glide.ConnectionTests.test_request_timeout ^
glide.SharedCommandTests.test_ping ^
glide.SharedCommandTests.test_info_server_and_cluster ^
glide.SharedCommandTests.test_config_get_and_config_set ^
glide.SharedCommandTests.test_echo ^
glide.SharedCommandTests.test_set_and_get ^
glide.SharedCommandTests.test_del ^
glide.SharedCommandTests.test_exists ^
glide.SharedCommandTests.test_expire_and_ttl ^
glide.SharedCommandTests.test_mget ^
glide.SharedCommandTests.test_mset ^
glide.SharedCommandTests.test_incr_and_decr ^
glide.SharedCommandTests.test_hash_commands ^
glide.SharedCommandTests.test_list_commands ^
glide.SharedCommandTests.test_set_commands ^
glide.SharedCommandTests.test_sorted_set_commands

REM Lazy connection test (run last)
set LAZY_TEST=glide.ConnectionTests.test_lazy_connection_establishes_on_first_command

echo Found tests to run

REM Step 4: Run each test individually
echo %YELLOW%Step 4: Running individual tests...%NC%

for %%t in (%TEST_METHODS%) do (
    call :run_test "%%t"
)

REM Step 5: Run problematic lazy connection test last
echo %YELLOW%Step 5: Running lazy connection test last...%NC%
call :run_test "%LAZY_TEST%"

REM Step 6: Cleanup and summary
echo %YELLOW%Step 6: Cleanup and summary...%NC%

REM Stop WSL monitoring
echo stop > \\wsl$\Ubuntu\tmp\wsl_command.txt

REM Kill Windows monitoring
taskkill /f /im powershell.exe /fi "WINDOWTITLE eq Windows Monitor" >nul 2>&1

REM Print summary
echo.
echo %YELLOW%=== Test Summary ===%NC%
echo Total tests: %TOTAL_COUNT%
echo %GREEN%Passed: %PASS_COUNT%%NC%
echo %RED%Failed: %FAIL_COUNT%%NC%

if not "%FAILED_TESTS%"=="" (
    echo.
    echo %RED%Failed tests:%NC%
    echo %FAILED_TESTS%
)

REM Exit with appropriate code
if %FAIL_COUNT% equ 0 (
    echo %GREEN%All tests passed!%NC%
    exit /b 0
) else (
    echo %RED%Some tests failed.%NC%
    exit /b 1
)

REM Function to run a single test
:run_test
set TEST_NAME=%~1
set TEST_DISPLAY=%TEST_NAME%

REM Extract just the method name for display
for /f "tokens=* delims=." %%a in ("%TEST_NAME%") do set TEST_DISPLAY=%%a
for %%a in (%TEST_NAME:.= %) do set TEST_DISPLAY=%%a

echo.
echo %YELLOW%Running: %TEST_DISPLAY%%NC%

REM Notify WSL of current test
echo set_test %TEST_DISPLAY% > \\wsl$\Ubuntu\tmp\wsl_command.txt
timeout /t 1 /nobreak >nul

REM Run the test in its own gradle process
gradlew.bat :integTest:test --tests %TEST_NAME% -PskipClusterShutdown ^
    "-Dtest.server.standalone=127.0.0.1:6379" ^
    "-Dtest.server.standalone.tls=127.0.0.1:6380" ^
    "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" ^
    "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005" ^
    >test_output.log 2>&1

if errorlevel 1 (
    echo %RED%FAIL: %TEST_DISPLAY%%NC%
    set /a FAIL_COUNT+=1
    set FAILED_TESTS=!FAILED_TESTS! %TEST_NAME%
    
    REM Show error context
    echo   Error context:
    powershell -Command "Get-Content test_output.log | Select-Object -Last 5 | ForEach-Object { Write-Host '    ' + $_ }"
) else (
    echo %GREEN%PASS: %TEST_DISPLAY%%NC%
    set /a PASS_COUNT+=1
)

set /a TOTAL_COUNT+=1

REM Clear servers after each test
echo clear_servers > \\wsl$\Ubuntu\tmp\wsl_command.txt
timeout /t 1 /nobreak >nul

REM Small delay between tests
timeout /t 1 /nobreak >nul

goto :eof
