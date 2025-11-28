#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0
CURRENT_TEST=""

# Test results
FAILED_TESTS=()
LAZY_CONNECTION_TEST="glide.ConnectionTests.test_lazy_connection_establishes_on_first_command"

# Resource monitoring thresholds
CPU_THRESHOLD=80    # CPU percentage
MEMORY_THRESHOLD=80 # Memory percentage
MONITOR_PID=""

# Detect environment
IS_WSL=false
IS_WINDOWS=false

if grep -qi microsoft /proc/version 2>/dev/null; then
    IS_WSL=true
elif [[ "$OS" == "Windows_NT" ]]; then
    IS_WINDOWS=true
fi

echo -e "${YELLOW}=== Isolated Test Runner with Resource Monitoring ===${NC}"
echo "Environment: $([ "$IS_WSL" = true ] && echo "WSL" || ([ "$IS_WINDOWS" = true ] && echo "Windows" || echo "Linux"))"

# Check dependencies
if ! command -v bc &> /dev/null; then
    echo -e "${RED}Error: 'bc' calculator is required but not installed${NC}"
    echo "Install with: sudo apt-get install bc (Ubuntu/Debian) or brew install bc (macOS)"
    exit 1
fi

# Function to log resource usage
log_resource_usage() {
    local test_name="$1"
    local cpu_usage="$2"
    local memory_usage="$3"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    echo "[$timestamp] Test: $test_name | CPU: ${cpu_usage}% | Memory: ${memory_usage}%" >> resource_monitor.log
    
    # Check thresholds
    if (( $(echo "$cpu_usage > $CPU_THRESHOLD" | bc -l) )); then
        echo -e "${RED}WARNING: HIGH CPU: ${cpu_usage}% during $test_name${NC}"
        echo "[$timestamp] HIGH CPU ALERT: ${cpu_usage}% during $test_name" >> resource_alerts.log
    fi
    
    if (( $(echo "$memory_usage > $MEMORY_THRESHOLD" | bc -l) )); then
        echo -e "${RED}WARNING: HIGH MEMORY: ${memory_usage}% during $test_name${NC}"
        echo "[$timestamp] HIGH MEMORY ALERT: ${memory_usage}% during $test_name" >> resource_alerts.log
    fi
}

# WSL resource monitoring function
monitor_resources_wsl() {
    while true; do
        if [ -n "$CURRENT_TEST" ]; then
            # Get CPU usage (1-second average)
            cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//')
            
            # Get memory usage
            memory_info=$(free | grep Mem)
            total_mem=$(echo $memory_info | awk '{print $2}')
            used_mem=$(echo $memory_info | awk '{print $3}')
            memory_usage=$(echo "scale=1; $used_mem * 100 / $total_mem" | bc)
            
            log_resource_usage "$CURRENT_TEST" "$cpu_usage" "$memory_usage"
        fi
        sleep 2
    done
}

# Windows resource monitoring function (via WSL calling PowerShell)
monitor_resources_windows() {
    while true; do
        if [ -n "$CURRENT_TEST" ]; then
            # Get Windows CPU and memory via PowerShell from WSL
            cpu_usage=$(powershell.exe -Command "Get-Counter '\\Processor(_Total)\\% Processor Time' | Select-Object -ExpandProperty CounterSamples | Select-Object -ExpandProperty CookedValue" 2>/dev/null | tr -d '\r' || echo "0")
            
            memory_usage=$(powershell.exe -Command "
                \$mem = Get-CimInstance Win32_OperatingSystem;
                \$total = \$mem.TotalVisibleMemorySize;
                \$free = \$mem.FreePhysicalMemory;
                \$used = \$total - \$free;
                [math]::Round((\$used / \$total) * 100, 1)
            " 2>/dev/null | tr -d '\r' || echo "0")
            
            log_resource_usage "$CURRENT_TEST" "$cpu_usage" "$memory_usage"
        fi
        sleep 2
    done
}

# Start resource monitoring in background
start_monitoring() {
    echo -e "${BLUE}Starting resource monitoring...${NC}"
    echo "# Resource Monitor Log - Started $(date)" > resource_monitor.log
    echo "# Resource Alerts Log - Started $(date)" > resource_alerts.log
    
    if [ "$IS_WSL" = true ]; then
        monitor_resources_wsl &
        MONITOR_PID=$!
        echo "WSL resource monitoring started (PID: $MONITOR_PID)"
    elif [ "$IS_WINDOWS" = true ]; then
        monitor_resources_windows &
        MONITOR_PID=$!
        echo "Windows resource monitoring started (PID: $MONITOR_PID)"
    else
        # Linux monitoring
        monitor_resources_wsl &
        MONITOR_PID=$!
        echo "Linux resource monitoring started (PID: $MONITOR_PID)"
    fi
}

# Stop resource monitoring
stop_monitoring() {
    if [ -n "$MONITOR_PID" ]; then
        kill $MONITOR_PID 2>/dev/null || true
        echo -e "${BLUE}Resource monitoring stopped${NC}"
    fi
}

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Cleaning up...${NC}"
    CURRENT_TEST=""
    stop_monitoring
    
    echo -e "\n${BLUE}Resource monitoring summary:${NC}"
    if [ -f resource_alerts.log ]; then
        alert_count=$(wc -l < resource_alerts.log)
        echo "Total resource alerts: $((alert_count - 1))" # Subtract header line
        if [ $alert_count -gt 1 ]; then
            echo "Check resource_alerts.log for details"
        fi
    fi
    
    cd java
    ./gradlew :integTest:stopAllAfterTests || true
    cd ..
}
trap cleanup EXIT

# Function to clear server contents
clear_servers() {
    echo "Clearing server contents..."
    
    # Clear standalone servers
    if [ -n "$STANDALONE_HOSTS" ]; then
        for host in $(echo $STANDALONE_HOSTS | tr ',' ' '); do
            IFS=':' read -r ip port <<< "$host"
            timeout 5 valkey-cli -h "$ip" -p "$port" FLUSHALL > /dev/null 2>&1 || true
        done
    fi
    
    # Clear TLS standalone servers  
    if [ -n "$STANDALONE_TLS_HOSTS" ]; then
        for host in $(echo $STANDALONE_TLS_HOSTS | tr ',' ' '); do
            IFS=':' read -r ip port <<< "$host"
            timeout 5 valkey-cli -h "$ip" -p "$port" --tls --cert utils/tls_crts/server.crt --key utils/tls_crts/server.key --cacert utils/tls_crts/ca.crt FLUSHALL > /dev/null 2>&1 || true
        done
    fi
    
    # Clear cluster servers
    if [ -n "$CLUSTER_HOSTS" ]; then
        for host in $(echo $CLUSTER_HOSTS | tr ',' ' '); do
            IFS=':' read -r ip port <<< "$host"
            timeout 5 valkey-cli -h "$ip" -p "$port" FLUSHALL > /dev/null 2>&1 || true
        done
    fi
    
    # Clear TLS cluster servers
    if [ -n "$CLUSTER_TLS_HOSTS" ]; then
        for host in $(echo $CLUSTER_TLS_HOSTS | tr ',' ' '); do
            IFS=':' read -r ip port <<< "$host"
            timeout 5 valkey-cli -h "$ip" -p "$port" --tls --cert utils/tls_crts/server.crt --key utils/tls_crts/server.key --cacert utils/tls_crts/ca.crt FLUSHALL > /dev/null 2>&1 || true
        done
    fi
}

# Function to run a single test
run_test() {
    local test_name="$1"
    local test_display="${test_name##*.}" # Get just the method name for display
    
    echo -e "\n${YELLOW}Running: ${test_display}${NC}"
    
    # Set current test for monitoring
    CURRENT_TEST="$test_display"
    
    cd java
    
    # Run the specific test with existing clusters
    if ./gradlew :integTest:test --tests "$test_name" -PskipClusterShutdown \
        -Dtest.server.standalone="$STANDALONE_HOSTS" \
        -Dtest.server.standalone.tls="$STANDALONE_TLS_HOSTS" \
        -Dtest.server.cluster="$CLUSTER_HOSTS" \
        -Dtest.server.cluster.tls="$CLUSTER_TLS_HOSTS" \
        > "/tmp/test_${TOTAL_COUNT}.log" 2>&1; then
        
        echo -e "${GREEN}PASS: ${test_display}${NC}"
        ((PASS_COUNT++))
    else
        echo -e "${RED}FAIL: ${test_display}${NC}"
        FAILED_TESTS+=("$test_name")
        ((FAIL_COUNT++))
        
        # Show last few lines of error for context
        echo "  Error context:"
        tail -5 "/tmp/test_${TOTAL_COUNT}.log" | sed 's/^/    /'
    fi
    
    cd ..
    ((TOTAL_COUNT++))
    
    # Clear servers after each test
    clear_servers
    
    # Clear current test
    CURRENT_TEST=""
    
    # Small delay between tests
    sleep 0.5
}

# Setup clusters once
echo -e "${YELLOW}Setting up clusters...${NC}"
cd java

# Start resource monitoring
start_monitoring

# Start all clusters and capture their endpoints
echo "Starting clusters..."
./gradlew :integTest:beforeTests

# Extract cluster endpoints from gradle properties
echo "Extracting cluster endpoints..."

# Run a simple gradle task to get the system properties
GRADLE_OUTPUT=$(./gradlew :integTest:properties -q | grep -E "test\.server\.")

# Extract endpoints (fallback to defaults if not found)
STANDALONE_HOSTS=$(echo "$GRADLE_OUTPUT" | grep "test.server.standalone:" | cut -d: -f2- | tr -d ' ' || echo "127.0.0.1:6379")
STANDALONE_TLS_HOSTS=$(echo "$GRADLE_OUTPUT" | grep "test.server.standalone.tls:" | cut -d: -f2- | tr -d ' ' || echo "127.0.0.1:6380")
CLUSTER_HOSTS=$(echo "$GRADLE_OUTPUT" | grep "test.server.cluster:" | cut -d: -f2- | tr -d ' ' || echo "127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002")
CLUSTER_TLS_HOSTS=$(echo "$GRADLE_OUTPUT" | grep "test.server.cluster.tls:" | cut -d: -f2- | tr -d ' ' || echo "127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005")

# If still empty, use defaults
[ -z "$STANDALONE_HOSTS" ] && STANDALONE_HOSTS="127.0.0.1:6379"
[ -z "$STANDALONE_TLS_HOSTS" ] && STANDALONE_TLS_HOSTS="127.0.0.1:6380"
[ -z "$CLUSTER_HOSTS" ] && CLUSTER_HOSTS="127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002"
[ -z "$CLUSTER_TLS_HOSTS" ] && CLUSTER_TLS_HOSTS="127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"

echo "Cluster endpoints configured:"
echo "  Standalone: $STANDALONE_HOSTS"
echo "  Standalone TLS: $STANDALONE_TLS_HOSTS" 
echo "  Cluster: $CLUSTER_HOSTS"
echo "  Cluster TLS: $CLUSTER_TLS_HOSTS"

cd ..

# Get list of all test methods using gradle's test discovery
echo -e "\n${YELLOW}Discovering tests...${NC}"

# Use gradle to list all tests, then filter out the lazy connection test
TEST_LIST=$(./gradlew :integTest:test --dry-run 2>/dev/null | grep -E "Test.*>" | sed 's/.*> Test \(.*\)/\1/' | sort -u)

TEST_METHODS=()
while IFS= read -r test; do
    if [[ -n "$test" && "$test" != *"$LAZY_CONNECTION_TEST"* ]]; then
        TEST_METHODS+=("$test")
    fi
done <<< "$TEST_LIST"

# If gradle test discovery didn't work, fall back to manual discovery
if [ ${#TEST_METHODS[@]} -eq 0 ]; then
    echo "Gradle test discovery failed, using manual method discovery..."
    
    # Find all @Test methods in integration test files
    for file in $(find integTest/src/test/java -name "*.java"); do
        class_name=$(echo "$file" | sed 's|integTest/src/test/java/||' | sed 's|\.java||' | tr '/' '.')
        
        # Extract method names that have @Test annotation (more robust)
        while IFS= read -r line; do
            if [[ "$line" =~ @(Test|ParameterizedTest) ]]; then
                # Read next few lines to find the method declaration
                read -r next_line
                if [[ "$next_line" =~ public[[:space:]]+void[[:space:]]+([a-zA-Z_][a-zA-Z0-9_]*) ]]; then
                    method_name="${BASH_REMATCH[1]}"
                    full_test_name="${class_name}.${method_name}"
                    
                    # Skip the problematic lazy connection test for now
                    if [[ "$full_test_name" != *"$LAZY_CONNECTION_TEST"* ]]; then
                        TEST_METHODS+=("$full_test_name")
                    fi
                fi
            fi
        done < "$file"
    done
fi

echo "Found ${#TEST_METHODS[@]} tests to run"

# Run all tests except lazy connection test
echo -e "\n${YELLOW}Running individual tests...${NC}"
for test in "${TEST_METHODS[@]}"; do
    run_test "$test"
done

# Run the problematic lazy connection test last
echo -e "\n${YELLOW}Running lazy connection test last...${NC}"
run_test "$LAZY_CONNECTION_TEST"

# Print summary
echo -e "\n${YELLOW}=== Test Summary ===${NC}"
echo -e "Total tests: $TOTAL_COUNT"
echo -e "${GREEN}Passed: $PASS_COUNT${NC}"
echo -e "${RED}Failed: $FAIL_COUNT${NC}"

if [ ${#FAILED_TESTS[@]} -gt 0 ]; then
    echo -e "\n${RED}Failed tests:${NC}"
    for failed_test in "${FAILED_TESTS[@]}"; do
        echo "  - $failed_test"
    done
fi

# Exit with appropriate code
if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "\n${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}Some tests failed.${NC}"
    exit 1
fi
