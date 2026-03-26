#!/bin/bash
#
# Launch 1 writer + 6 DEL pods with watchdogs
# Each pod pinned to 2 cores, with automatic diagnostic capture
#
# Core allocation:
#   Writer:  12,13
#   DEL 1:   0,1
#   DEL 2:   2,3
#   DEL 3:   4,5
#   DEL 4:   6,7
#   DEL 5:   8,9
#   DEL 6:   10,11
#   Free:    14,15
#

cd /home/ubuntu/babushka/java
chmod +x watchdog.sh capture_stuck_state.sh

HOST="crr-cluster.glide.cross.region"
COMMON_ARGS="--port 6379 --tls --clusterModeEnabled --clients glide --clientCount 1 --concurrentTasks 60 --dataSize 100 --requestTimeout 50 --inflightLimit 100 --duration 216000 --metricsInterval 60"

echo "=============================================="
echo "Launching 1 writer + 6 DEL pods with watchdogs"
echo "$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "=============================================="

# Writer - cores 12,13
echo "Launching writer (cores 12,13)..."
nohup taskset -c 12,13 ./gradlew :benchmarks:run --args="--host $HOST $COMMON_ARGS --operations write --metricsDir ./metrics_inflight_writer" > inflight_writer.log 2>&1 &

# DEL pods
for i in 1 2 3 4 5 6; do
    CORES_START=$(( (i-1) * 2 ))
    CORES_END=$(( CORES_START + 1 ))
    CORES="${CORES_START},${CORES_END}"
    echo "Launching DEL $i (cores $CORES)..."
    nohup taskset -c $CORES ./gradlew :benchmarks:run --args="--host $HOST $COMMON_ARGS --operations delete --metricsDir ./metrics_inflight_del${i}" > inflight_del${i}.log 2>&1 &
done

echo ""
echo "Waiting for build and startup (5 minutes)..."
sleep 300

# Fix taskset on child JVMs (gradle daemon issue)
echo "Fixing taskset on benchmark JVMs..."
for pid in $(pgrep -f "glide.benchmarks.BenchmarkingApp"); do
    dir=$(cat /proc/$pid/cmdline 2>/dev/null | tr '\0' '\n' | grep -A1 "metricsDir" | tail -1)
    case "$dir" in
        *writer*) cores="12,13";;
        *del1*)   cores="0,1";;
        *del2*)   cores="2,3";;
        *del3*)   cores="4,5";;
        *del4*)   cores="6,7";;
        *del5*)   cores="8,9";;
        *del6*)   cores="10,11";;
        *)        cores=""; continue;;
    esac
    taskset -cp $cores $pid 2>/dev/null
    for tid in $(ls /proc/$pid/task/ 2>/dev/null); do
        taskset -cp $cores $tid 2>/dev/null
    done
    echo "  PID=$pid ($dir) → cores $cores"
done

echo ""
echo "Starting watchdogs..."
for i in 1 2 3 4 5 6; do
    nohup ./watchdog.sh inflight_del${i}.log metrics_inflight_del${i} > watchdog_del${i}.log 2>&1 &
    echo "  Watchdog for DEL $i started (PID=$!)"
done

echo ""
echo "=============================================="
echo "All launched at $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "=============================================="
echo ""
echo "Logs:      inflight_writer.log, inflight_del{1-6}.log"
echo "Watchdogs: watchdog_del{1-6}.log"
echo "Captures:  /tmp/glide_diagnostic_*.tar.gz (auto-created on issue)"
echo ""
echo "To check status:"
echo "  grep 'TPS:' inflight_del*.log | tail -7"
echo "  grep 'INFLIGHT MONITOR' inflight_del*.log | tail -6"
echo "  cat watchdog_del*.log | tail -20"
