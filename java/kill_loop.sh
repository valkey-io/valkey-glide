#!/bin/bash
# Kill random GlideJava connections every 500ms to keep pressure on reconnection
NODES=(
  shohame-global-data-store-iad-0001-001.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0001-002.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0001-003.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0002-001.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0002-002.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0002-003.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0003-001.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0003-002.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
  shohame-global-data-store-iad-0003-003.shohame-global-data-store-iad.ime4qh.use1.cache.amazonaws.com
)

COUNT=0
echo "[$(date)] Starting kill loop..."
while true; do
  # Pick a random node
  NODE=${NODES[$((RANDOM % ${#NODES[@]}))]}
  KILLED=$(redis-cli -h "$NODE" -p 6379 --tls --insecure CLIENT KILL TYPE normal 2>/dev/null)
  COUNT=$((COUNT + 1))
  if [ "$KILLED" != "0" ] && [ -n "$KILLED" ]; then
    echo "[$(date +%H:%M:%S.%3N)] Killed $KILLED on $NODE (total kills: $COUNT)"
  fi
  sleep 0.5
done
