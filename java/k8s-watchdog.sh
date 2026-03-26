#!/bin/bash
#
# K8s Watchdog: monitors all benchmark pods and runs capture_stuck_state.sh
# when it detects DEL STUCK, NEVER COMPLETED, or TPS: 0
#
# Runs on the host, not inside pods.
#

set +e
KUBECTL="sudo /snap/bin/kubectl"
CHECK_INTERVAL=30
MAX_CAPTURES=3
CAPTURED=0
DIAG_DIR="/home/ubuntu/diagnostics"
mkdir -p "$DIAG_DIR"

echo "[$(date -u '+%H:%M:%S')] K8s watchdog started"

run_capture() {
    local POD=$1
    local REASON=$2
    CAPTURED=$((CAPTURED + 1))

    echo "[$(date -u '+%H:%M:%S')] !!! ISSUE DETECTED on $POD: $REASON"
    echo "[$(date -u '+%H:%M:%S')] Running capture #$CAPTURED on $POD..."

    # Run setup first (installs tools if missing)
    $KUBECTL exec $POD -- bash /app/setup_diagnostics.sh > "$DIAG_DIR/setup_${POD}.log" 2>&1

    # Run capture (PID 1 is the java process in the container)
    $KUBECTL exec $POD -- /app/capture_stuck_state.sh 1 > "$DIAG_DIR/capture_${POD}_${CAPTURED}.log" 2>&1 &
    CAPTURE_PID=$!
    echo "[$(date -u '+%H:%M:%S')] Capture running in background (PID=$CAPTURE_PID)"

    # Wait up to 5 minutes
    for w in $(seq 1 30); do
        if ! kill -0 $CAPTURE_PID 2>/dev/null; then
            echo "[$(date -u '+%H:%M:%S')] Capture #$CAPTURED on $POD completed"
            # Copy tarball out of the pod
            TARBALL=$($KUBECTL exec $POD -- bash -c 'ls /diagnostics/*.tar.gz 2>/dev/null | tail -1')
            if [ -n "$TARBALL" ]; then
                $KUBECTL cp "${POD}:${TARBALL}" "$DIAG_DIR/$(basename $TARBALL)" 2>/dev/null
                echo "[$(date -u '+%H:%M:%S')] Tarball copied to $DIAG_DIR/$(basename $TARBALL)"
            fi
            break
        fi
        sleep 10
    done

    if kill -0 $CAPTURE_PID 2>/dev/null; then
        echo "[$(date -u '+%H:%M:%S')] Capture timed out, killing"
        kill $CAPTURE_PID 2>/dev/null
    fi

    if [ $CAPTURED -ge $MAX_CAPTURES ]; then
        echo "[$(date -u '+%H:%M:%S')] Reached max captures ($MAX_CAPTURES). Stopping."
        exit 0
    fi

    echo "[$(date -u '+%H:%M:%S')] Cooldown: 2 minutes..."
    sleep 120
}

declare -A LAST_STUCK_COUNT

while true; do
    sleep $CHECK_INTERVAL

    for POD in $($KUBECTL get pods --no-headers 2>/dev/null | grep -v "Unknown\|Error\|Completed" | awk '{print $1}'); do
        # Check for STUCK / NEVER COMPLETED
        STUCK=$($KUBECTL logs $POD --tail=200 2>/dev/null | grep -ca "DEL STUCK\|DEL NEVER COMPLETED\|future never completed")
        PREV=${LAST_STUCK_COUNT[$POD]:-0}
        if [ "$STUCK" -gt "$PREV" ] 2>/dev/null; then
            LAST_STUCK_COUNT[$POD]=$STUCK
            run_capture "$POD" "DEL STUCK detected ($STUCK in last 200 lines)"
            continue
        fi

        # Check for TPS: 0
        LAST_TPS=$($KUBECTL logs $POD --tail=5 2>/dev/null | grep -a "TPS:" | tail -1)
        if echo "$LAST_TPS" | grep -q "TPS: 0 "; then
            run_capture "$POD" "TPS dropped to 0"
            continue
        fi
    done

    # Periodic status
    if [ $((SECONDS % 300)) -lt $CHECK_INTERVAL ]; then
        RUNNING=$($KUBECTL get pods --no-headers 2>/dev/null | grep "Running" | wc -l)
        echo "[$(date -u '+%H:%M:%S')] Watchdog OK | running=$RUNNING | captures=$CAPTURED/$MAX_CAPTURES"
    fi
done
