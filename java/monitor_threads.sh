#!/bin/bash
# monitor_threads.sh
LOG_FILE=$1
OUTPUT_FILE="${LOG_FILE%.log}_threads.log"

if [ -z "$LOG_FILE" ]; then
    echo "Usage: ./monitor_threads.sh <log_file>"
    exit 1
fi

echo "Watching $LOG_FILE for DEL STUCK..."
echo "Output: $OUTPUT_FILE"

tail -f "$LOG_FILE" | while read line; do
    if echo "$line" | grep -q "DEL STUCK"; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') DEL STUCK DETECTED" | tee "$OUTPUT_FILE"

        # Capture dmesg immediately
        echo "--- DMESG AT DETECTION ---" >> "$OUTPUT_FILE"
        sudo dmesg -T | tail -50 >> "$OUTPUT_FILE"

        for i in $(seq 1 300); do
            echo "========== $(date '+%Y-%m-%d %H:%M:%S.%N') ==========" >> "$OUTPUT_FILE"

            echo "--- SYSTEM MEMORY ---" >> "$OUTPUT_FILE"
            free -h >> "$OUTPUT_FILE"

            echo "--- PROCESS MEMORY (top 10) ---" >> "$OUTPUT_FILE"
            printf "%-8s %-20s %6s %6s %10s %10s\n" "PID" "COMMAND" "%CPU" "%MEM" "RSS(KB)" "VSZ(KB)" >> "$OUTPUT_FILE"
            ps -eo pid,comm,pcpu,pmem,rss,vsz --sort=-pmem | head -11 | tail -10 | \
                awk '{printf "%-8s %-20s %6s %6s %10s %10s\n", $1, $2, $3, $4, $5, $6}' >> "$OUTPUT_FILE"

            echo "--- GLIDE THREADS ---" >> "$OUTPUT_FILE"
            printf "%-8s %-8s %-20s %4s %4s %4s %6s %4s\n" "PID" "TID" "NAME" "NI" "CLS" "PRI" "%CPU" "STAT" >> "$OUTPUT_FILE"
            ps -eLo pid,tid,comm,ni,cls,pri,pcpu,stat --sort=-pcpu | grep -E "glide-worker|glide-jni" | \
                awk '{printf "%-8s %-8s %-20s %4s %4s %4s %6s %4s\n", $1, $2, $3, $4, $5, $6, $7, $8}' >> "$OUTPUT_FILE"

            echo "--- GLIDE-WORKER KERNEL STACKS ---" >> "$OUTPUT_FILE"
            for tid in $(ps -eLo tid,comm | grep glide-worker | awk '{print $1}'); do
                echo "--- TID $tid ---" >> "$OUTPUT_FILE"
                echo "stack:" >> "$OUTPUT_FILE"
                sudo cat /proc/$tid/stack 2>/dev/null >> "$OUTPUT_FILE"
                echo "syscall:" >> "$OUTPUT_FILE"
                sudo cat /proc/$tid/syscall 2>/dev/null >> "$OUTPUT_FILE"
                echo "" >> "$OUTPUT_FILE"
            done

            echo "--- GC THREADS ---" >> "$OUTPUT_FILE"
            printf "%-8s %-8s %-20s %6s %4s\n" "PID" "TID" "NAME" "%CPU" "STAT" >> "$OUTPUT_FILE"
            ps -eLo pid,tid,comm,pcpu,stat --sort=-pcpu | grep "GC Thread" | \
                awk '{printf "%-8s %-8s %-20s %6s %4s\n", $1, $2, $3, $4, $5}' >> "$OUTPUT_FILE"

            echo "--- POOL THREADS (top 10) ---" >> "$OUTPUT_FILE"
            printf "%-8s %-8s %-20s %6s %4s\n" "PID" "TID" "NAME" "%CPU" "STAT" >> "$OUTPUT_FILE"
            ps -eLo pid,tid,comm,pcpu,stat --sort=-pcpu | grep "pool-1" | head -10 | \
                awk '{printf "%-8s %-8s %-20s %6s %4s\n", $1, $2, $3, $4, $5}' >> "$OUTPUT_FILE"

            echo "--- LOAD ---" >> "$OUTPUT_FILE"
            cat /proc/loadavg >> "$OUTPUT_FILE"

            # Capture dmesg every 30 seconds for OOM events
            if [ $((i % 30)) -eq 0 ]; then
                echo "--- DMESG CHECK ---" >> "$OUTPUT_FILE"
                sudo dmesg -T | tail -20 >> "$OUTPUT_FILE"
            fi

            echo "" >> "$OUTPUT_FILE"
            sleep 1
        done

        echo "Done - saved to $OUTPUT_FILE"
        break
    fi
done