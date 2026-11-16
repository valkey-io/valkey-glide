#!/bin/bash
# Client. RUNS distinct files of SIZE_MB random bytes, each sent to the server
# over RDMA and read back, verified in memory per run and by SHA-256 of the
# file pair per batch. Processed BATCH at a time so disk and pinned memory are
# bounded by BATCH, not RUNS. The per-leg timing is the throughput baseline.
# ships: large_object.py
set -euo pipefail

: "${SERVER_IP:?}"
PORT=${PORT:-7380}
SIZE_MB=${SIZE_MB:-256}
RUNS=${RUNS:-3}
BATCH=${BATCH:-10}
SRC=/opt/rdma-test
WORK=$SRC/large-object
. "$SRC/venv/bin/activate"
rm -rf "$WORK"; mkdir -p "$WORK"

python /var/log/rdma/large_object.py "$SERVER_IP" "$PORT" "$WORK" "$((SIZE_MB * 1024 * 1024))" "$RUNS" "$BATCH"
