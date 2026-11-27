#!/bin/bash
# Server. Starts valkey-server with the large-object module as a transient
# systemd unit, so it outlives the SSM command that started it. Re-running
# replaces a running instance. DRAM_MAXMEMORY and RDMA_REGISTERED override the
# memory knobs. No password: the security group, which admits only the client
# host, is the access boundary, and protected-mode is turned off to say so.
set -euo pipefail

SRC=/opt/rdma-test
PORT=${PORT:-7380}
# Load-time module args are parsed as plain integers (the `16gb` notation the
# README describes only works through CONFIG SET); these are bytes.
GIB=$((1024 * 1024 * 1024))
DRAM_MAXMEMORY=${DRAM_MAXMEMORY:-$((16 * GIB))}
RDMA_REGISTERED=${RDMA_REGISTERED:-$((32 * GIB))}
SEGMENT_SIZE=$GIB
UNIT=rdma-valkey
bind_ip=$(ip -4 -o addr show scope global | awk '{print $4}' | cut -d/ -f1 | head -1)

systemctl stop "$UNIT" 2>/dev/null || true
systemctl reset-failed "$UNIT" 2>/dev/null || true
systemd-run --unit "$UNIT" --working-directory="$SRC" \
  -p LimitMEMLOCK=infinity -p LimitNOFILE=65536 \
  "$SRC/valkey/src/valkey-server" --port "$PORT" --bind "$bind_ip" \
    --protected-mode no --save "" --appendonly no --loglevel notice \
    --loadmodule "$SRC/valkey-large-object/target/release/libvalkey_large_object.so" \
      operating-mode Dram fabric-provider EfaDirect \
      dram-maxmemory "$DRAM_MAXMEMORY" segment-size "$SEGMENT_SIZE" \
      max-rdma-registered-bytes "$RDMA_REGISTERED"
sleep 3
systemctl is-active --quiet "$UNIT" || { journalctl -u "$UNIT" --no-pager | tail -n 40; exit 1; }

cli="$SRC/valkey/src/valkey-cli -h $bind_ip -p $PORT"
# valkey-cli exits 0 on an error reply, so judge the reply text.
reply=$($cli PING)
[ "$reply" = PONG ] || { echo "PING on $bind_ip:$PORT answered: $reply"; exit 1; }
$cli MODULE LIST
journalctl -u "$UNIT" --no-pager | tail -n 15
echo "server listening on $bind_ip:$PORT"
