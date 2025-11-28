#!/bin/bash

# WSL Server Monitor - runs valkey servers and monitors resources

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Resource monitoring thresholds
CPU_THRESHOLD=80
MEMORY_THRESHOLD=80
MONITOR_PID=""
CURRENT_TEST_FILE="/tmp/current_test.txt"

echo -e "${YELLOW}=== WSL Server Monitor ===${NC}"

# Check dependencies
if ! command -v bc &> /dev/null; then
    echo -e "${RED}Error: 'bc' calculator is required${NC}"
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
monitor_resources() {
    while true; do
        if [ -f "$CURRENT_TEST_FILE" ]; then
            current_test=$(cat "$CURRENT_TEST_FILE" 2>/dev/null || echo "")
            if [ -n "$current_test" ]; then
                # Get CPU usage
                cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//' || echo "0")
                
                # Get memory usage
                memory_info=$(free | grep Mem)
                total_mem=$(echo $memory_info | awk '{print $2}')
                used_mem=$(echo $memory_info | awk '{print $3}')
                memory_usage=$(echo "scale=1; $used_mem * 100 / $total_mem" | bc)
                
                log_resource_usage "$current_test" "$cpu_usage" "$memory_usage"
            fi
        fi
        sleep 2
    done
}

# Function to clear server contents
clear_servers() {
    echo "Clearing server contents..."
    
    # Clear standalone servers
    timeout 5 valkey-cli -h 127.0.0.1 -p 6379 FLUSHALL > /dev/null 2>&1 || true
    timeout 5 valkey-cli -h 127.0.0.1 -p 6380 --tls --cert utils/tls_crts/server.crt --key utils/tls_crts/server.key --cacert utils/tls_crts/ca.crt FLUSHALL > /dev/null 2>&1 || true
    
    # Clear cluster servers
    for port in 7000 7001 7002 7003 7004 7005; do
        if [ $port -ge 7003 ]; then
            timeout 5 valkey-cli -h 127.0.0.1 -p $port --tls --cert utils/tls_crts/server.crt --key utils/tls_crts/server.key --cacert utils/tls_crts/ca.crt FLUSHALL > /dev/null 2>&1 || true
        else
            timeout 5 valkey-cli -h 127.0.0.1 -p $port FLUSHALL > /dev/null 2>&1 || true
        fi
    done
}

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Stopping servers and monitoring...${NC}"
    rm -f "$CURRENT_TEST_FILE"
    
    if [ -n "$MONITOR_PID" ]; then
        kill $MONITOR_PID 2>/dev/null || true
    fi
    
    cd java
    ./gradlew :integTest:stopAllAfterTests || true
    cd ..
    
    echo -e "${BLUE}Resource monitoring summary:${NC}"
    if [ -f resource_alerts.log ]; then
        alert_count=$(wc -l < resource_alerts.log)
        echo "Total resource alerts: $((alert_count - 1))"
    fi
}
trap cleanup EXIT

# Start servers
echo -e "${YELLOW}Starting valkey servers...${NC}"
cd java
./gradlew :integTest:beforeTests
cd ..

# Start monitoring
echo -e "${BLUE}Starting resource monitoring...${NC}"
echo "# Resource Monitor Log - Started $(date)" > resource_monitor.log
echo "# Resource Alerts Log - Started $(date)" > resource_alerts.log

monitor_resources &
MONITOR_PID=$!

echo "WSL resource monitoring started (PID: $MONITOR_PID)"
echo -e "${GREEN}Servers running. Monitoring active.${NC}"
echo -e "${YELLOW}Server endpoints:${NC}"
echo "  Standalone: 127.0.0.1:6379"
echo "  Standalone TLS: 127.0.0.1:6380"
echo "  Cluster: 127.0.0.1:7000,127.0.0.1:7001,127.0.0.1:7002"
echo "  Cluster TLS: 127.0.0.1:7003,127.0.0.1:7004,127.0.0.1:7005"

# API for Windows to communicate with this script
echo -e "\n${BLUE}Listening for commands...${NC}"
echo "Commands: set_test <name>, clear_servers, stop"

while true; do
    if [ -f "/tmp/wsl_command.txt" ]; then
        command=$(cat "/tmp/wsl_command.txt")
        rm -f "/tmp/wsl_command.txt"
        
        case "$command" in
            set_test\ *)
                test_name="${command#set_test }"
                echo "$test_name" > "$CURRENT_TEST_FILE"
                echo "Set current test: $test_name"
                ;;
            clear_servers)
                clear_servers
                echo "Servers cleared"
                ;;
            stop)
                echo "Stop command received"
                break
                ;;
        esac
    fi
    sleep 0.1
done
