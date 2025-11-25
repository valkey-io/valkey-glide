@echo off
setlocal enabledelayedexpansion

REM Orchestrated Test Runner for Developer Command Prompt
REM Usage: run-orchestrated-tests.bat [WSL_DISTRO] [TEST_DELAY_MS]

set WSL_DISTRO=%1
if "%WSL_DISTRO%"=="" set WSL_DISTRO=Ubuntu

set TEST_DELAY=%2
if "%TEST_DELAY%"=="" set TEST_DELAY=500

echo === Orchestrated Test Runner ===
echo WSL Distro: %WSL_DISTRO%
echo Test Delay: %TEST_DELAY%ms

REM Check WSL availability
wsl -d %WSL_DISTRO% -e echo "WSL test" >nul 2>&1
if errorlevel 1 (
    echo ERROR: WSL distro '%WSL_DISTRO%' is not available
    exit /b 1
)
echo WSL connection successful

REM Step 1: Start WSL monitoring and servers
echo.
echo Step 1: Starting WSL monitoring and servers...
set WSL_PATH=/mnt/c/%CD:\=/%
start "WSL Monitor" wsl -d %WSL_DISTRO% -e bash -c "cd '%WSL_PATH%' && ./wsl-server-monitor.sh"

echo Waiting for servers to initialize...
timeout /t 15 /nobreak >nul

REM Step 2: Start Windows resource monitoring
echo.
echo Step 2: Starting Windows resource monitoring...
start "Windows Monitor" /min cmd /c "
:monitor_loop
for /f \"tokens=2 delims==\" %%a in ('wmic cpu get loadpercentage /value ^| find \"LoadPercentage\"') do set cpu=%%a
for /f \"skip=1 tokens=4\" %%a in ('wmic OS get TotalVisibleMemorySize^,FreePhysicalMemory /format:table') do set free=%%a& goto :got_free
:got_free
for /f \"skip=1 tokens=3\" %%a in ('wmic OS get TotalVisibleMemorySize^,FreePhysicalMemory /format:table') do set total=%%a& goto :got_total
:got_total
set /a mem_used=total-free
set /a mem_pct=mem_used*100/total
if !cpu! gtr 80 echo [%date% %time%] Windows Alert - CPU: !cpu!%%, Memory: !mem_pct!%%
if !mem_pct! gtr 80 echo [%date% %time%] Windows Alert - CPU: !cpu!%%, Memory: !mem_pct!%%
timeout /t 2 /nobreak >nul
goto monitor_loop
"

echo Resource monitoring started

REM Step 3: Run main tests (excluding lazy connection tests)
echo.
echo Step 3: Running main integration tests (excluding lazy connection tests)...

cd java

echo Running gradle test with %TEST_DELAY%ms delays and lazy connection test exclusion...

gradlew.bat :integTest:test -PskipClusterShutdown -PskipLazyConnectionTests -PtestDelay=%TEST_DELAY% ^
    "-Dtest.server.standalone=127.0.0.1:6379" ^
    "-Dtest.server.standalone.tls=127.0.0.1:6380" ^
    "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" ^
    "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"

set MAIN_TEST_RESULT=%errorlevel%

if %MAIN_TEST_RESULT% equ 0 (
    echo Main tests completed successfully
) else (
    echo Some main tests failed
)

REM Step 4: Clear servers before lazy connection tests
echo.
echo Step 4: Clearing servers before lazy connection tests...
echo clear_servers > \\wsl$\%WSL_DISTRO%\tmp\wsl_command.txt
timeout /t 2 /nobreak >nul

REM Step 5: Run lazy connection tests at the end
echo.
echo Step 5: Running lazy connection tests...

gradlew.bat :integTest:lazyConnectionTest -PskipClusterShutdown ^
    "-Dtest.server.standalone=127.0.0.1:6379" ^
    "-Dtest.server.standalone.tls=127.0.0.1:6380" ^
    "-Dtest.server.cluster=127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002" ^
    "-Dtest.server.cluster.tls=127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"

set LAZY_TEST_RESULT=%errorlevel%

if %LAZY_TEST_RESULT% equ 0 (
    echo Lazy connection tests completed successfully
) else (
    echo Lazy connection tests failed
)

cd ..

REM Step 6: Cleanup
echo.
echo Step 6: Cleanup...

REM Stop WSL monitoring
echo stop > \\wsl$\%WSL_DISTRO%\tmp\wsl_command.txt

REM Stop Windows monitoring
taskkill /f /fi "WINDOWTITLE eq Windows Monitor" >nul 2>&1

REM Summary
echo.
echo === Test Summary ===
if %MAIN_TEST_RESULT% equ 0 if %LAZY_TEST_RESULT% equ 0 (
    echo All tests passed!
    exit /b 0
) else if %MAIN_TEST_RESULT% equ 0 (
    echo Main tests passed, but lazy connection tests failed
    exit /b 1
) else if %LAZY_TEST_RESULT% equ 0 (
    echo Lazy connection tests passed, but main tests failed
    exit /b 1
) else (
    echo Both main tests and lazy connection tests failed
    exit /b 1
)
