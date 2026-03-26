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
# 6. TOKIO CONSOLE TASK DUMP (if console-subscriber is enabled)
# ==========================================
echo "[6/8] Capturing tokio task dumps (3 snapshots, 30s apart)..."

CONSOLE_PORT=${TOKIO_CONSOLE_PORT:-6669}
if timeout 1 bash -c "echo >/dev/tcp/localhost/$CONSOLE_PORT" 2>/dev/null; then
    echo "  tokio-console port $CONSOLE_PORT is open, capturing tasks..."

    # Find console-api proto files
    # Look for console-api proto files: first in /app/proto (container), then in cargo registry (dev machine)
    PROTO_DIR=""
    if [ -d "/app/proto" ]; then
        PROTO_DIR="/app/proto"
    else
        PROTO_DIR=$(find /home -path "*/console-api-*/proto" -type d 2>/dev/null | sort -V | tail -1)
    fi
    if [ -d "$PROTO_DIR" ] && command -v grpcurl &>/dev/null; then
        # Capture 3 snapshots, 30 seconds apart
        for SNAP in 1 2 3; do
            echo "  Snapshot $SNAP/3 at $(date -u '+%H:%M:%S')..."
            timeout 10 grpcurl -plaintext -max-msg-sz 50000000 \
                -import-path "$PROTO_DIR" \
                -proto instrument.proto \
                localhost:$CONSOLE_PORT rs.tokio.console.instrument.Instrument/WatchUpdates \
                > "$OUTPUT_DIR/tokio_tasks_raw_${SNAP}.json" 2>&1
            if [ $SNAP -lt 3 ]; then
                sleep 30
            fi
        done

        # Parse all 3 snapshots into human-readable summaries
        python3 << 'PYEOF' > "$OUTPUT_DIR/tokio_tasks.txt" 2>&1
import json, sys
raw = open("OUTPUTDIR/tokio_tasks_raw.json").read().replace("OUTPUTDIR", "OUTPUT_DIR_PLACEHOLDER")
PYEOF

        python3 - "$OUTPUT_DIR" << 'PYEOF' > "$OUTPUT_DIR/tokio_tasks.txt" 2>&1
import json, sys, glob, os

def parse_duration(d):
    """Parse protobuf Duration to milliseconds"""
    if not d or not isinstance(d, (dict, str)):
        return 0
    if isinstance(d, str):
        return int(float(d.rstrip('s')) * 1000) if d != '0s' else 0
    secs = int(d.get("seconds", 0))
    nanos = int(d.get("nanos", 0))
    return secs * 1000 + nanos // 1_000_000

def parse_raw_file(path):
    """Parse a tokio_tasks_raw JSON file into tasks and stats dicts"""
    raw = open(path).read()
    depth = 0; start = 0; objects = []
    for i, ch in enumerate(raw):
        if ch == '{':
            if depth == 0: start = i
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                objects.append(raw[start:i+1])
    tasks = {}; stats = {}
    for obj_str in objects:
        try: data = json.loads(obj_str)
        except: continue
        tu = data.get("taskUpdate", {})
        for t in tu.get("newTasks", []):
            tid = str(t.get("id", {}).get("id", "?"))
            loc = t.get("location", {})
            tasks[tid] = {"file": loc.get("file", "?"), "line": loc.get("line", "?")}
        for tid_str, st in tu.get("statsUpdate", {}).items():
            if isinstance(st, dict):
                ps = st.get("pollStats", {})
                stats[tid_str] = {
                    "polls": int(ps.get("polls", 0)),
                    "busy_ms": parse_duration(ps.get("busyTime")),
                    "idle_ms": parse_duration(st.get("scheduledTime")),
                    "created": st.get("createdAt", ""),
                    "last_poll": ps.get("lastPollStarted", ""),
                    "wakes": int(st.get("wakes", 0)),
                }
    for tid in tasks:
        if tid in stats: tasks[tid].update(stats[tid])
        else: tasks[tid].update({"polls": 0, "wakes": 0, "busy_ms": 0, "idle_ms": 0, "last_poll": "", "created": ""})
    return tasks

def fmt_ms(ms):
    if ms < 1000: return f"{ms}ms"
    if ms < 60000: return f"{ms/1000:.1f}s"
    if ms < 3600000: return f"{ms/60000:.1f}m"
    return f"{ms/3600000:.1f}h"

def shorten(loc):
    if "/" not in loc: return loc
    parts = loc.split("/")
    for prefix in ("glide-core", "redis-rs", "src"):
        if prefix in parts:
            return "/".join(parts[parts.index(prefix):])
    return parts[-1]

def print_snapshot(tasks, label):
    locations = {}
    for tid, info in tasks.items():
        key = f"{info['file']}:{info['line']}"
        if key not in locations:
            locations[key] = {"count": 0, "total_polls": 0, "max_polls": 0, "zero_poll_tasks": 0, "total_busy_ms": 0, "max_busy_ms": 0, "total_idle_ms": 0}
        l = locations[key]
        l["count"] += 1
        p = info.get("polls", 0)
        l["total_polls"] += p
        l["max_polls"] = max(l["max_polls"], p)
        l["total_busy_ms"] += info.get("busy_ms", 0)
        l["max_busy_ms"] = max(l["max_busy_ms"], info.get("busy_ms", 0))
        l["total_idle_ms"] += info.get("idle_ms", 0)
        if p == 0: l["zero_poll_tasks"] += 1

    print(f"=== {label} (tasks: {len(tasks)}) ===")
    print(f"{'COUNT':>6} {'0POLL':>6} {'MAX_POLLS':>10} {'TOTAL_BUSY':>11} {'MAX_BUSY':>9} {'TOTAL_IDLE':>11}  SPAWN LOCATION")
    print("-" * 105)
    for loc, info in sorted(locations.items(), key=lambda x: -x[1]["total_busy_ms"]):
        print(f"{info['count']:>6} {info['zero_poll_tasks']:>6} {info['max_polls']:>10} {fmt_ms(info['total_busy_ms']):>11} {fmt_ms(info['max_busy_ms']):>9} {fmt_ms(info['total_idle_ms']):>11}  {shorten(loc)}")
    print()

output_dir = sys.argv[1]
snapshots = []
for i in range(1, 4):
    path = f"{output_dir}/tokio_tasks_raw_{i}.json"
    if os.path.exists(path):
        snapshots.append(parse_raw_file(path))

# Print each snapshot
for i, tasks in enumerate(snapshots):
    print_snapshot(tasks, f"SNAPSHOT {i+1}/3")

# Compare snapshots: detect tasks stuck across all 3
if len(snapshots) >= 2:
    first = snapshots[0]
    last = snapshots[-1]
    print("=== COMPARISON: SNAPSHOT 1 vs SNAPSHOT 3 ===")
    # Check key task groups
    for snap_label, snap in [("snapshot_1", first), ("snapshot_3", last)]:
        locs = {}
        for tid, info in snap.items():
            key = shorten(f"{info['file']}:{info['line']}")
            if key not in locs: locs[key] = {"count": 0, "total_polls": 0}
            locs[key]["count"] += 1
            locs[key]["total_polls"] += info.get("polls", 0)
        for loc, info in sorted(locs.items(), key=lambda x: -x[1]["count"]):
            print(f"  {snap_label}: {loc}: count={info['count']} total_polls={info['total_polls']}")
    print()

# Flag stuck tasks from the last snapshot
if snapshots:
    last = snapshots[-1]
    print("=== POTENTIALLY STUCK TASKS (polls<=1 or busy>10s with no progress) ===")
    found = False
    for tid, info in sorted(last.items(), key=lambda x: -x[1].get("busy_ms", 0)):
        p = info.get("polls", 0)
        b = info.get("busy_ms", 0)
        if p <= 1 or (b > 10000 and p < 10):
            short = info["file"].split("/")[-1] if "/" in info["file"] else info["file"]
            created = info.get("created", "?")[:19]
            last_poll = info.get("last_poll", "?")[:19]
            print(f"  task_id={tid} polls={p} busy={fmt_ms(b)} wakes={info.get('wakes',0)} created={created} last_poll={last_poll} at={short}:{info['line']}")
            found = True
    if not found:
        print("  (none found)")

    # Top 20 by busy time from last snapshot
    print()
    print("=== TOP 20 TASKS BY BUSY TIME (SNAPSHOT 3) ===")
    print(f"{'ID':>22} {'POLLS':>8} {'BUSY':>9} {'IDLE':>9} {'WAKES':>6} {'LAST_POLL':>20}  LOCATION")
    print("-" * 110)
    for tid, info in sorted(last.items(), key=lambda x: -x[1].get("busy_ms", 0))[:20]:
        short = info["file"].split("/")[-1] if "/" in info["file"] else info["file"]
        last_poll = info.get("last_poll", "")[:19]
        print(f"{tid:>22} {info.get('polls',0):>8} {fmt_ms(info.get('busy_ms',0)):>9} {fmt_ms(info.get('idle_ms',0)):>9} {info.get('wakes',0):>6} {last_poll:>20}  {short}:{info['line']}")
PYEOF
        echo "  3 task snapshots captured"
    else
        echo "  grpcurl or proto files not found, skipping task dump"
    fi
else
    echo "  tokio-console port $CONSOLE_PORT not open, skipping"
fi

# ==========================================
# 7. CONTINUED MONITORING (30 seconds)
# ==========================================
echo "[7/8] Monitoring for 30 seconds..."

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
echo "[8/8] Capturing final thread dump..."

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
echo "  - tokio_tasks.txt       Tokio task dump: all async tasks, poll counts, spawn locations"
echo "  - monitoring.txt        30s of per-thread CPU monitoring (top -H)"
