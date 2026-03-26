#!/bin/bash
# Diagnose stuck DEL commands in a running Java benchmark process
# Usage: ./diagnose_stuck.sh [PID]        — single snapshot
#        ./diagnose_stuck.sh [PID] watch   — continuous monitoring every 5s
#        ./diagnose_stuck.sh [PID] deep    — deep dive with strace + thread dump

MODE=${2:-snapshot}
PID=${1:-$(pgrep -f "glide.benchmarks.BenchmarkingApp" | head -1)}

if [ -z "$PID" ]; then
    echo "No benchmark process found. Usage: $0 <PID> [snapshot|watch|deep]"
    exit 1
fi

if [ ! -d /proc/$PID ]; then
    echo "ERROR: PID $PID does not exist"
    exit 1
fi

# ============================================================
# Helper functions
# ============================================================

find_tokio_tid() {
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        if [ "$(cat /proc/$PID/task/$tid/comm 2>/dev/null)" = "glide-worker" ]; then
            echo "$tid"
            return
        fi
    done
}

find_callback_tids() {
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        name=$(cat /proc/$PID/task/$tid/comm 2>/dev/null)
        if [[ "$name" == glide-jni-callb* ]]; then
            echo "$tid"
        fi
    done
}

get_thread_cpu() {
    local tid=$1
    local stat_fields=($(cat /proc/$PID/task/$tid/stat 2>/dev/null))
    echo "${stat_fields[13]:-0} ${stat_fields[14]:-0}"
}

get_thread_state() {
    local tid=$1
    grep "^State:" /proc/$PID/task/$tid/status 2>/dev/null | awk '{print $2$3}'
}

get_thread_wchan() {
    cat /proc/$PID/task/$tid/wchan 2>/dev/null
}

# Check if tokio thread is making progress over a short window
check_tokio_progress() {
    local tokio_tid=$1
    local duration=${2:-1}

    local before=($(get_thread_cpu $tokio_tid))
    sleep $duration
    local after=($(get_thread_cpu $tokio_tid))

    local du=$(( ${after[0]} - ${before[0]} ))
    local ds=$(( ${after[1]} - ${before[1]} ))

    if [ "$du" -eq 0 ] && [ "$ds" -eq 0 ]; then
        echo "STARVED"
    else
        echo "OK(+${du}u/+${ds}s)"
    fi
}

# Snapshot which threads are actively using CPU over 500ms
active_thread_snapshot() {
    declare -A bu bs bn
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        local f=($(cat /proc/$PID/task/$tid/stat 2>/dev/null))
        bu[$tid]=${f[13]:-0}
        bs[$tid]=${f[14]:-0}
        bn[$tid]=$(cat /proc/$PID/task/$tid/comm 2>/dev/null)
    done
    sleep 0.5
    local found=0
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        local f=($(cat /proc/$PID/task/$tid/stat 2>/dev/null))
        local du=$(( ${f[13]:-0} - ${bu[$tid]:-0} ))
        local ds=$(( ${f[14]:-0} - ${bs[$tid]:-0} ))
        if [ "$du" -gt 0 ] || [ "$ds" -gt 0 ]; then
            printf "  TID=%-8s %-20s +%du/+%ds\n" "$tid" "${bn[$tid]}" "$du" "$ds"
            found=1
        fi
    done
    if [ "$found" -eq 0 ]; then
        echo "  (no threads consumed CPU in 500ms — entire process parked)"
    fi
}

# Count network connections by state
connection_summary() {
    echo "  Connections to Valkey:"
    ss -tnp 2>/dev/null | grep "pid=$PID" | awk '{print $1}' | sort | uniq -c | sort -rn
    echo "  Total: $(ss -tnp 2>/dev/null | grep -c "pid=$PID")"
}

# ============================================================
# SNAPSHOT mode — run once, get full picture
# ============================================================
do_snapshot() {
    echo "=============================================="
    echo "DIAGNOSING PID=$PID at $(date)"
    echo "CMD: $(cat /proc/$PID/cmdline 2>/dev/null | tr '\0' ' ' | cut -c1-120)"
    echo "=============================================="
    echo ""

    # 1. System overview
    echo "=== SYSTEM ==="
    echo "  Load: $(cat /proc/loadavg)"
    grep -E "VmRSS|Threads" /proc/$PID/status 2>/dev/null | sed 's/^/  /'
    echo ""

    # 2. Tokio worker thread — the critical one
    local tokio_tid=$(find_tokio_tid)
    if [ -n "$tokio_tid" ]; then
        local state=$(get_thread_state $tokio_tid)
        local wchan=$(cat /proc/$PID/task/$tokio_tid/wchan 2>/dev/null)
        local cpu=($(get_thread_cpu $tokio_tid))
        echo "=== TOKIO WORKER (TID=$tokio_tid) ==="
        echo "  State: $state | wchan: $wchan | utime: ${cpu[0]} | stime: ${cpu[1]}"

        # 1-second CPU progress check
        local progress=$(check_tokio_progress $tokio_tid 1)
        echo "  1s progress: $progress"
        if [ "$progress" = "STARVED" ]; then
            echo "  *** TOKIO THREAD GOT ZERO CPU — STARVATION DETECTED ***"
        fi
        echo ""
    else
        echo "=== TOKIO WORKER: NOT FOUND ==="
        echo ""
    fi

    # 3. Callback workers
    echo "=== CALLBACK WORKERS ==="
    for tid in $(find_callback_tids); do
        local state=$(get_thread_state $tid)
        local wchan=$(cat /proc/$PID/task/$tid/wchan 2>/dev/null)
        local cpu=($(get_thread_cpu $tid))
        printf "  TID=%-8s state=%-8s wchan=%-15s cpu=%s/%s\n" "$tid" "$state" "$wchan" "${cpu[0]}" "${cpu[1]}"
    done
    echo ""

    # 4. Who is burning CPU right now?
    echo "=== ACTIVE THREADS (500ms snapshot) ==="
    active_thread_snapshot
    echo ""

    # 5. All threads sorted by CPU (top 15)
    echo "=== TOP THREADS BY CPU ==="
    printf "  %-8s %-20s %-8s %-8s %-8s %s\n" "TID" "NAME" "STATE" "UTIME" "STIME" "WCHAN"
    for tid in $(ls /proc/$PID/task/ 2>/dev/null); do
        local name=$(cat /proc/$PID/task/$tid/comm 2>/dev/null)
        local state=$(get_thread_state $tid)
        local cpu=($(get_thread_cpu $tid))
        local wchan=$(cat /proc/$PID/task/$tid/wchan 2>/dev/null)
        printf "  %-8s %-20s %-8s %-8s %-8s %s\n" "$tid" "$name" "$state" "${cpu[0]}" "${cpu[1]}" "$wchan"
    done 2>/dev/null | sort -k4 -rn | head -15
    echo ""

    # 6. Network
    echo "=== NETWORK ==="
    connection_summary
    echo ""

    # 7. FD count (high FD = possible leak)
    local fd_count=$(ls /proc/$PID/fd/ 2>/dev/null | wc -l)
    echo "=== FILE DESCRIPTORS: $fd_count ==="
    echo ""

    echo "=============================================="
    echo "DONE at $(date)"
    echo "=============================================="
}

# ============================================================
# WATCH mode — continuous monitoring, compact output
# ============================================================
do_watch() {
    echo "Watching PID=$PID every 5s (Ctrl+C to stop)"
    echo "---"

    local tokio_tid=$(find_tokio_tid)
    local last_utime=0 last_stime=0

    if [ -n "$tokio_tid" ]; then
        local cpu=($(get_thread_cpu $tokio_tid))
        last_utime=${cpu[0]}
        last_stime=${cpu[1]}
    fi

    while true; do
        if [ ! -d /proc/$PID ]; then
            echo "[$(date +%H:%M:%S)] Process $PID died!"
            exit 1
        fi

        # Re-find tokio TID in case it changed
        tokio_tid=$(find_tokio_tid)
        local load=$(cut -d' ' -f1-3 /proc/loadavg)
        local threads=$(grep "Threads:" /proc/$PID/status 2>/dev/null | awk '{print $2}')
        local rss=$(grep "VmRSS:" /proc/$PID/status 2>/dev/null | awk '{print $2}')
        local fds=$(ls /proc/$PID/fd/ 2>/dev/null | wc -l)
        local conns=$(ss -tnp 2>/dev/null | grep -c "pid=$PID")

        if [ -n "$tokio_tid" ]; then
            local cpu=($(get_thread_cpu $tokio_tid))
            local du=$(( ${cpu[0]} - last_utime ))
            local ds=$(( ${cpu[1]} - last_stime ))
            last_utime=${cpu[0]}
            last_stime=${cpu[1]}

            local tokio_state=$(get_thread_state $tokio_tid)
            local tokio_wchan=$(cat /proc/$PID/task/$tokio_tid/wchan 2>/dev/null)

            local status="OK"
            if [ "$du" -eq 0 ] && [ "$ds" -eq 0 ]; then
                status="*** STARVED ***"
            fi

            printf "[%s] load=%s | tokio=%s(+%du/+%ds) wchan=%-12s | thr=%s rss=%sKB fds=%s conns=%s | %s\n" \
                "$(date +%H:%M:%S)" "$load" "$tokio_state" "$du" "$ds" "$tokio_wchan" \
                "$threads" "$rss" "$fds" "$conns" "$status"
        else
            printf "[%s] load=%s | tokio=NOT_FOUND | thr=%s rss=%sKB fds=%s conns=%s\n" \
                "$(date +%H:%M:%S)" "$load" "$threads" "$rss" "$fds" "$conns"
        fi

        sleep 5
    done
}

# ============================================================
# DEEP mode — strace + thread dump + full analysis
# ============================================================
do_deep() {
    echo "=============================================="
    echo "DEEP DIAGNOSIS PID=$PID at $(date)"
    echo "=============================================="
    echo ""

    # First do a regular snapshot
    do_snapshot
    echo ""

    local tokio_tid=$(find_tokio_tid)

    # strace the tokio thread
    if [ -n "$tokio_tid" ]; then
        echo "=== TOKIO THREAD STRACE (3s syscall summary) ==="
        timeout 3 strace -p $tokio_tid -c 2>&1 || echo "(needs root/ptrace)"
        echo ""

        echo "=== TOKIO THREAD STRACE (3s live trace) ==="
        timeout 3 strace -p $tokio_tid -tt -T 2>&1 | tail -50 || echo "(needs root/ptrace)"
        echo ""
    fi

    # strace callback workers
    echo "=== CALLBACK WORKER STRACE (2s each) ==="
    for tid in $(find_callback_tids); do
        echo "--- TID=$tid ---"
        timeout 2 strace -p $tid -c 2>&1 || echo "(needs root/ptrace)"
    done
    echo ""

    # Java thread dump
    echo "=== JAVA THREAD DUMP ==="
    echo "(sending SIGQUIT — output goes to stderr/log of the process)"
    kill -3 $PID 2>/dev/null
    echo "Sent. Wait a moment then check the process stderr."
    echo ""

    # Check for epoll contention — is tokio stuck in epoll_wait or doing work?
    if [ -n "$tokio_tid" ]; then
        echo "=== TOKIO SYSCALL PATTERN (5 x 1s samples) ==="
        for i in 1 2 3 4 5; do
            local wchan=$(cat /proc/$PID/task/$tokio_tid/wchan 2>/dev/null)
            local state=$(get_thread_state $tokio_tid)
            local cpu=($(get_thread_cpu $tokio_tid))
            printf "  [sample $i] state=$state wchan=$wchan utime=${cpu[0]} stime=${cpu[1]}\n"
            sleep 1
        done
        echo ""
    fi

    echo "=============================================="
    echo "DEEP DIAGNOSIS DONE at $(date)"
    echo "=============================================="
}

# ============================================================
# Main
# ============================================================
case "$MODE" in
    snapshot) do_snapshot ;;
    watch)    do_watch ;;
    deep)     do_deep ;;
    *)
        echo "Usage: $0 [PID] [snapshot|watch|deep]"
        echo "  snapshot — single full diagnosis (default)"
        echo "  watch    — continuous compact monitoring every 5s"
        echo "  deep     — snapshot + strace + thread dump (needs root)"
        exit 1
        ;;
esac
