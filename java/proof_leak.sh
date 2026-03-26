PID=57373

echo "=== Memory Leak Proof: Forced GC + Object Count ==="
echo "Time | Longs (post-GC) | Bytes | RSS KB"
echo "----------------------------------------------"

while true; do
  # Force a full GC
  jcmd $PID GC.run

  # Wait for GC to complete
  sleep 2

  # Count Longs immediately after GC
  LONGS=$(jmap -histo $PID | grep "java.lang.Long " | awk '{print $2}')
  BYTES=$(jmap -histo $PID | grep "java.lang.Long " | awk '{print $3}')
  RSS=$(cat /proc/$PID/status | grep VmRSS | awk '{print $2}')

  echo "$(date '+%H:%M:%S') | Longs: $LONGS | Bytes: $BYTES | RSS: $RSS KB"

  # Wait 1 minute before next cycle
  sleep 60
done