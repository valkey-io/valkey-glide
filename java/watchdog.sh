#!/bin/bash
#
# Watchdog: monitors a benchmark pod's log file and automatically
# runs capture_stuck_state.sh when it detects issues.
#
# Usage: ./watchdog.sh <log_file> <metrics_dir_pattern>
#
# Detection triggers:
#   1. "STUCK COMMAND" appears in the log
#   2. "maximum inflight requests" appears (and it's not the tight pod)
#   3. No new TPS line for 3 minutes (process hung)
#   4. TPS drops to 0
#

LOG_FILE=$1
METRICS_PATTERN=$2
SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
CAPTURE_SCRIPT="$SCRIPT_DIR/capture_stuck_state.sh"
CHECK_INTERVAL=30
NO_TPS_THRESHOLD=180  # 3 minutes with no TPS line = stuck
CAPTURED=0
MAX_CAPTURES=5

if [ -z "$LOG_FILE" ] || [ -z "$METRICS_PATTERN" ]; then
    echo "Usage: $0 <log_file> <metrics_dir_pattern>"
    echo "Example: $0 inflight_del1.log metrics_inflight_del1"
    exit 1
fi

echo "[$(date -u '+%H:%M:%S')] Watchdog started for $LOG_FILE (pattern=$METRICS_PATTERN)"

find_pid() {
    for p in $(pgrep -f "glide.benchmarks.BenchmarkingApp"); do
        if cat /proc/$p/cmdline 2>/dev/null | tr '\0' '\n' | grep -qa "$METRICS_PATTERN"; then
            echo $p
            return
        fi
    done
}

run_capture() {
    local REASON=$1
    CAPTURED=$((CAPTURED + 1))
    local PID=$(find_pid)

    echo "[$(date -u '+%H:%M:%S')] !!! ISSUE DETECTED: $REASON"
    echo "[$(date -u '+%H:%M:%S')] Running capture #$CAPTURED (PID=$PID)..."

    if [ -n "$PID" ] && [ -d "/proc/$PID" ]; then
        # Run capture in background so watchdog doesn't hang if jstack blocks
        $CAPTURE_SCRIPT $PID >> "$LOG_FILE.capture_$CAPTURED.log" 2>&1 &
        CAPTURE_PID=$!
        echo "[$(date -u '+%H:%M:%S')] Capture running in background (PID=$CAPTURE_PID)"
        # Wait up to 5 minutes for it to finish
        for w in $(seq 1 30); do
            if ! kill -0 $CAPTURE_PID 2>/dev/null; then
                echo "[$(date -u '+%H:%M:%S')] Capture #$CAPTURED completed"
                break
            fi
            sleep 10
        done
        # Kill if still running after 5 minutes
        if kill -0 $CAPTURE_PID 2>/dev/null; then
            echo "[$(date -u '+%H:%M:%S')] Capture #$CAPTURED timed out after 5 minutes, killing"
            kill $CAPTURE_PID 2>/dev/null
        fi
    else
        echo "[$(date -u '+%H:%M:%S')] WARNING: Could not find PID for $METRICS_PATTERN"
    fi

    if [ $CAPTURED -ge $MAX_CAPTURES ]; then
        echo "[$(date -u '+%H:%M:%S')] Reached max captures ($MAX_CAPTURES). Watchdog stopping."
        exit 0
    fi

    # Wait 2 minutes before allowing another capture
    echo "[$(date -u '+%H:%M:%S')] Cooldown: waiting 2 minutes before next capture..."
    sleep 120
}

LAST_TPS_TIME=$(date +%s)

while true; do
    sleep $CHECK_INTERVAL

    # Check if process still exists
    PID=$(find_pid)
    if [ -z "$PID" ]; then
        echo "[$(date -u '+%H:%M:%S')] Process for $METRICS_PATTERN not found. Waiting..."
        continue
    fi

    # Check for STUCK COMMAND in log
    if grep -qa "STUCK COMMAND\|STUCK COMMANDS\|DEL STUCK\|DEL NEVER COMPLETED" "$LOG_FILE" 2>/dev/null; then
        STUCK_COUNT=$(grep -ca "STUCK COMMAND\|DEL STUCK\|DEL NEVER COMPLETED" "$LOG_FILE" 2>/dev/null)
        # Only trigger if new stuck commands appeared since last check
        if [ "$STUCK_COUNT" -gt "${LAST_STUCK_COUNT:-0}" ]; then
            LAST_STUCK_COUNT=$STUCK_COUNT
            run_capture "STUCK COMMAND detected ($STUCK_COUNT total)"
            continue
        fi
    fi

    # Check for maximum inflight requests (not expected for limit=100 pods)
    INFLIGHT_ERRORS=$(grep -ca "maximum inflight requests" "$LOG_FILE" 2>/dev/null)
    INFLIGHT_ERRORS=${INFLIGHT_ERRORS:-0}
    if [ "$INFLIGHT_ERRORS" -gt "${LAST_INFLIGHT_ERRORS:-0}" ] 2>/dev/null; then
        LAST_INFLIGHT_ERRORS=$INFLIGHT_ERRORS
        run_capture "maximum inflight requests errors detected ($INFLIGHT_ERRORS total)"
        continue
    fi

    # Check for TPS line freshness
    LAST_TPS_LINE=$(grep "TPS:" "$LOG_FILE" 2>/dev/null | tail -1)
    if [ -n "$LAST_TPS_LINE" ]; then
        LAST_TPS_TIME=$(date +%s)

        # Check if TPS dropped to 0
        TPS_VALUE=$(echo "$LAST_TPS_LINE" | grep -oP 'TPS: \K[0-9]+')
        if [ "$TPS_VALUE" = "0" ]; then
            run_capture "TPS dropped to 0"
            continue
        fi
    else
        # No TPS line yet, might still be warming up
        NOW=$(date +%s)
        ELAPSED=$((NOW - LAST_TPS_TIME))
        if [ $ELAPSED -gt $NO_TPS_THRESHOLD ]; then
            run_capture "No TPS output for ${ELAPSED}s (threshold=${NO_TPS_THRESHOLD}s)"
            LAST_TPS_TIME=$(date +%s)  # Reset to avoid repeated triggers
            continue
        fi
    fi

    # Periodic status
    if [ $((SECONDS % 300)) -lt $CHECK_INTERVAL ]; then
        echo "[$(date -u '+%H:%M:%S')] Watchdog OK | PID=$PID | captures=$CAPTURED/$MAX_CAPTURES"
    fi
done
