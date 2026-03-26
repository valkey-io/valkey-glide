#!/bin/bash
#
# GLIDE Stuck Thread Diagnostic Capture
# ======================================
# Run this script IMMEDIATELY when you observe threads getting stuck.
# It captures thread dumps, network state, and system info over 60 seconds.
#
# Usage (local):
#   ./capture_stuck_state.sh <java_pid>
#
# Usage (k8s):
#   kubectl exec -it <pod-name> -- bash -c 'cat > /tmp/capture.sh' < capture_stuck_state.sh
#   kubectl exec -it <pod-name> -- bash /tmp/capture.sh <java_pid>
#
# If you don't know the PID, the script will try to find it automatically.
#

set +e

PID=$1
DIAG_BASE="${GLIDE_DIAG_DIR:-$(pwd)/diagnostics}"
OUTPUT_DIR="$DIAG_BASE/glide_diagnostic_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUTPUT_DIR"

# Auto-detect PID if not provided
if [ -z "$PID" ]; then
    PID=$(pgrep -f "glide|GlideClient|BenchmarkingApp" | head -1)
    if [ -z "$PID" ]; then
        echo "ERROR: No Java PID provided and could not auto-detect."
        echo "Usage: $0 <java_pid>"
        exit 1
    fi
    echo "Auto-detected Java PID: $PID"
fi

# Verify PID exists
if [ ! -d "/proc/$PID" ]; then
    echo "ERROR: PID $PID does not exist"
    exit 1
fi

echo "=============================================="
echo "GLIDE Stuck Thread Diagnostic Capture"
echo "=============================================="
echo "PID:        $PID"
echo "Output dir: $OUTPUT_DIR"
echo "Time:       $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "=============================================="

# ==========================================
# 1. IMMEDIATE SNAPSHOT
# ==========================================
echo "[1/7] Capturing immediate snapshot..."

# System info
{
    echo "=== DATE ==="
    date -u '+%Y-%m-%d %H:%M:%S UTC'
    echo ""
    echo "=== UPTIME ==="
    uptime
    echo ""
    echo "=== MEMORY ==="
    free -h
    echo ""
    echo "=== CPU INFO ==="
    nproc
    echo ""
    echo "=== PROCESS INFO ==="
    ps -p $PID -o pid,ppid,user,%cpu,%mem,rss,vsz,stat,start,time,comm
    echo ""
    echo "=== PROCESS MEMORY ==="
    cat /proc/$PID/status 2>/dev/null | grep -E "VmSize|VmRSS|VmSwap|Threads"
} > "$OUTPUT_DIR/system_info.txt" 2>&1

# ==========================================
# 2. NETWORK CONNECTIONS
# ==========================================
echo "[2/7] Capturing network connections..."

{
    echo "=== $(date -u '+%Y-%m-%d %H:%M:%S UTC') ==="
    echo ""
    echo "=== CONNECTIONS TO PORT 6379 (VALKEY) ==="
    timeout 5 ss -tn 2>/dev/null | grep ":6379" || timeout 5 netstat -tn 2>/dev/null | grep ":6379" || echo "(failed to list connections)"
    echo ""
    echo "=== CONNECTION STATES SUMMARY ==="
    timeout 5 ss -tn 2>/dev/null | grep ":6379" | awk '{print $1}' | sort | uniq -c | sort -rn || true
} > "$OUTPUT_DIR/network.txt" 2>&1

# ==========================================
# 3. THREAD DUMPS (5 dumps, 10 seconds apart)
# ==========================================
echo "[3/7] Capturing 5 thread dumps (10s apart)..."

for i in 1 2 3 4 5; do
    DUMP_FILE="$OUTPUT_DIR/thread_dump_${i}.txt"
    {
        echo "=== THREAD DUMP #$i at $(date -u '+%Y-%m-%d %H:%M:%S.%3N UTC') ==="
        echo ""
    } > "$DUMP_FILE"

    # Try jstack first, fall back to jcmd, fall back to kill -3
    # Timeout after 30s in case JVM is stuck and jstack can't attach
    if command -v jstack &>/dev/null; then
        timeout 30 jstack -l $PID >> "$DUMP_FILE" 2>&1 || echo "WARNING: jstack timed out after 30s" >> "$DUMP_FILE"
    elif command -v jcmd &>/dev/null; then
        timeout 30 jcmd $PID Thread.print -l >> "$DUMP_FILE" 2>&1 || echo "WARNING: jcmd timed out after 30s" >> "$DUMP_FILE"
    else
        echo "WARNING: Neither jstack nor jcmd found. Sending kill -3 (check stdout of the Java process)." >> "$DUMP_FILE"
        kill -3 $PID 2>/dev/null
    fi

    echo "  Thread dump $i captured"
    if [ $i -lt 5 ]; then
        sleep 10
    fi
done

# ==========================================
# 4. PER-THREAD CPU USAGE
# ==========================================
echo "[4/7] Capturing per-thread CPU usage..."

{
    echo "=== $(date -u '+%Y-%m-%d %H:%M:%S UTC') ==="
    echo ""
    echo "=== TOP THREADS BY CPU (top -H) ==="
    top -b -H -p $PID -n 1 2>/dev/null
    echo ""
    echo "=== GLIDE/TOKIO THREADS ==="
    ps -eLo pid,tid,comm,pcpu,stat -p $PID | grep -iE "glide|tokio|worker" || echo "(none found)"
    echo ""
    echo "=== ALL THREADS (ps) ==="
    ps -eLo pid,tid,comm,pcpu,stat -p $PID
} > "$OUTPUT_DIR/thread_cpu.txt" 2>&1

# ==========================================
# 5. NATIVE THREAD INFO + TOKIO RUNTIME METRICS
# ==========================================
echo "[5/7] Capturing native thread info + tokio runtime metrics..."

{
    echo "=== $(date -u '+%Y-%m-%d %H:%M:%S UTC') ==="
    echo ""
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        COMM=$(cat /proc/$PID/task/$tid/comm 2>/dev/null || echo "unknown")
        # Only capture glide-related threads and a few Java threads
        if echo "$COMM" | grep -qiE "glide|tokio|worker|GC|Compiler|main"; then
            echo "--- TID=$tid COMM=$COMM ---"
            echo "stat: $(cat /proc/$PID/task/$tid/stat 2>/dev/null | awk '{print "state="$3, "utime="$14, "stime="$15, "priority="$18, "nice="$19}')"
            if [ -r "/proc/$PID/task/$tid/stack" ]; then
                echo "kernel stack:"
                cat /proc/$PID/task/$tid/stack 2>/dev/null
            fi
            echo ""
        fi
    done
} > "$OUTPUT_DIR/native_threads.txt" 2>&1

# Tokio runtime health: sample glide-worker CPU ticks 5 times, 2s apart
# If utime doesn't change between samples, the tokio event loop is stuck
{
    echo "=== TOKIO RUNTIME HEALTH CHECK ==="
    echo "Sampling glide-worker thread CPU ticks every 2s (5 samples)"
    echo "If utime stops incrementing, the tokio event loop is frozen."
    echo ""

    # Find glide-worker TID
    GLIDE_WORKER_TID=""
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        if [ "$(cat /proc/$PID/task/$tid/comm 2>/dev/null)" = "glide-worker" ]; then
            GLIDE_WORKER_TID=$tid
            break
        fi
    done

    # Find glide-jni-callback TIDs
    JNI_TIDS=""
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        COMM=$(cat /proc/$PID/task/$tid/comm 2>/dev/null)
        if echo "$COMM" | grep -q "glide-jni"; then
            JNI_TIDS="$JNI_TIDS $tid"
        fi
    done

    # Find glide-starvation TID
    STARV_TID=""
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        if [ "$(cat /proc/$PID/task/$tid/comm 2>/dev/null)" = "glide-starvatio" ]; then
            STARV_TID=$tid
            break
        fi
    done

    if [ -z "$GLIDE_WORKER_TID" ]; then
        echo "WARNING: Could not find glide-worker thread"
    else
        echo "glide-worker TID: $GLIDE_WORKER_TID"
        echo "glide-jni-callback TIDs: $JNI_TIDS"
        echo "glide-starvation TID: $STARV_TID"
        echo ""

        for i in 1 2 3 4 5; do
            TS=$(date -u '+%H:%M:%S.%3N')

            # glide-worker stats
            STAT=$(cat /proc/$PID/task/$GLIDE_WORKER_TID/stat 2>/dev/null)
            UTIME=$(echo "$STAT" | awk '{print $14}')
            STIME=$(echo "$STAT" | awk '{print $15}')
            STATE=$(echo "$STAT" | awk '{print $3}')
            SCHEDSTAT=$(cat /proc/$PID/task/$GLIDE_WORKER_TID/schedstat 2>/dev/null)
            IO=$(cat /proc/$PID/task/$GLIDE_WORKER_TID/io 2>/dev/null | grep -E "syscr|syscw|read_bytes|write_bytes" | tr '\n' ' ')

            echo "[$TS] sample=$i glide-worker: state=$STATE utime=$UTIME stime=$STIME schedstat=[$SCHEDSTAT] io=[$IO]"

            # glide-jni-callback stats
            for jtid in $JNI_TIDS; do
                JSTAT=$(cat /proc/$PID/task/$jtid/stat 2>/dev/null)
                JUTIME=$(echo "$JSTAT" | awk '{print $14}')
                JSTIME=$(echo "$JSTAT" | awk '{print $15}')
                JSTATE=$(echo "$JSTAT" | awk '{print $3}')
                echo "[$TS] sample=$i glide-jni($jtid): state=$JSTATE utime=$JUTIME stime=$JSTIME"
            done

            # FD count (proxy for connection count)
            FD_COUNT=$(ls /proc/$PID/fd 2>/dev/null | wc -l)
            SOCKET_COUNT=$(ls -la /proc/$PID/fd 2>/dev/null | grep socket | wc -l)
            echo "[$TS] sample=$i fd_count=$FD_COUNT socket_count=$SOCKET_COUNT"

            echo ""
            if [ $i -lt 5 ]; then
                sleep 2
            fi
        done
    fi
} > "$OUTPUT_DIR/tokio_health.txt" 2>&1

# ==========================================
# 6. CONTINUED MONITORING (30 seconds)
# ==========================================
echo "[6/7] Monitoring for 30 seconds..."

{
    for i in $(seq 1 6); do
        echo "=== SAMPLE $i at $(date -u '+%Y-%m-%d %H:%M:%S.%3N UTC') ==="

        echo "--- TOP THREADS BY CPU (top -H) ---"
        top -b -H -p $PID -n 1 2>/dev/null | head -20

        echo ""
        sleep 5
    done
} > "$OUTPUT_DIR/monitoring.txt" 2>&1

# ==========================================
# 7. FINAL THREAD DUMP
# ==========================================
echo "[7/7] Capturing final thread dump..."

{
    echo "=== FINAL THREAD DUMP at $(date -u '+%Y-%m-%d %H:%M:%S.%3N UTC') ==="
    echo ""
} > "$OUTPUT_DIR/thread_dump_final.txt"

if command -v jstack &>/dev/null; then
    timeout 30 jstack -l $PID >> "$OUTPUT_DIR/thread_dump_final.txt" 2>&1 || echo "WARNING: jstack timed out after 30s" >> "$OUTPUT_DIR/thread_dump_final.txt"
elif command -v jcmd &>/dev/null; then
    timeout 30 jcmd $PID Thread.print -l >> "$OUTPUT_DIR/thread_dump_final.txt" 2>&1 || echo "WARNING: jcmd timed out after 30s" >> "$OUTPUT_DIR/thread_dump_final.txt"
fi

# ==========================================
# PACKAGE RESULTS
# ==========================================
echo ""
echo "=============================================="
echo "Capture complete!"
echo "=============================================="
echo ""
echo "Files captured:"
ls -lh "$OUTPUT_DIR"/
echo ""

# Create tarball
TAR_FILE="$DIAG_BASE/glide_diagnostic_$(date +%Y%m%d_%H%M%S).tar.gz"
tar -czf "$TAR_FILE" -C "$DIAG_BASE" "$(basename $OUTPUT_DIR)"
echo ""
echo "=============================================="
echo "PACKAGED: $TAR_FILE"
echo "=============================================="
echo ""
echo "Please send this file to the GLIDE team."
echo ""
echo "What was captured:"
echo "  - system_info.txt       System memory, CPU, process info"
echo "  - network.txt           TCP connections to Valkey nodes"
echo "  - thread_dump_1-5.txt   5 Java thread dumps (10s apart)"
echo "  - thread_dump_final.txt Final thread dump after monitoring"
echo "  - thread_cpu.txt        Per-thread CPU usage (top -H)"
echo "  - native_threads.txt    Native/kernel thread state"
echo "  - tokio_health.txt      Tokio runtime health: glide-worker CPU ticks, FD/socket counts"
echo "  - monitoring.txt        30s of per-thread CPU monitoring (top -H)"
