#!/bin/bash
# monitor_always.sh
OUTPUT_FILE="system_monitor.log"

echo "$(date '+%Y-%m-%d %H:%M:%S') Starting continuous monitoring (every 10s)" | tee "$OUTPUT_FILE"

while true; do
    echo "========== $(date '+%Y-%m-%d %H:%M:%S.%N') ==========" >> "$OUTPUT_FILE"

    echo "--- SYSTEM MEMORY ---" >> "$OUTPUT_FILE"
    free -h >> "$OUTPUT_FILE"

    echo "--- PROCESS MEMORY (top 10) ---" >> "$OUTPUT_FILE"
    printf "%-8s %-20s %6s %6s %10s %10s\n" "PID" "COMMAND" "%CPU" "%MEM" "RSS(KB)" "VSZ(KB)" >> "$OUTPUT_FILE"
    ps -eo pid,comm,pcpu,pmem,rss,vsz --sort=-pmem | head -11 | tail -10 | \
        awk '{printf "%-8s %-20s %6s %6s %10s %10s\n", $1, $2, $3, $4, $5, $6}' >> "$OUTPUT_FILE"

    echo "--- GLIDE THREADS (PID TID NAME NI CLS PRI %CPU STAT) ---" >> "$OUTPUT_FILE"
    ps -eLo pid,tid,comm,ni,cls,pri,pcpu,stat --sort=-pcpu | grep -E "glide-worker|glide-jni" | \
        awk '{printf "%-8s %-8s %-20s %4s %4s %4s %6s %4s\n", $1, $2, $3, $4, $5, $6, $7, $8}' >> "$OUTPUT_FILE"

    echo "--- GC THREADS ---" >> "$OUTPUT_FILE"
    ps -eLo pid,tid,comm,pcpu,stat --sort=-pcpu | grep "GC Thread" | head -5 | \
        awk '{printf "%-8s %-8s %-20s %6s %4s\n", $1, $2, $3, $4, $5}' >> "$OUTPUT_FILE"

    echo "--- LOAD ---" >> "$OUTPUT_FILE"
    cat /proc/loadavg >> "$OUTPUT_FILE"

    echo "" >> "$OUTPUT_FILE"
    sleep 10
done