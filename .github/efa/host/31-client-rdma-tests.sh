#!/bin/bash
# Client. Runs valkey-glide's RDMA transfer tests from the installed wheel
# against the server. --standalone-endpoints stops the harness from trying to
# launch a local valkey-server; -k replaces the repo's default -k filter.
set -euo pipefail

: "${SERVER_IP:?}"
PORT=${PORT:-7380}
SRC=/opt/rdma-test
. "$SRC/venv/bin/activate"
cd "$SRC/valkey-glide/python"

export GLIDE_RDMA_SERVER="$SERVER_IP:$PORT"
export GLIDE_RDMA_PROVIDER=${GLIDE_RDMA_PROVIDER:-efa-direct}
pytest tests/sync_tests/test_sync_rdma.py -k "${TEST_FILTER:-TestRdmaTransfers}" \
  --standalone-endpoints="$SERVER_IP:$PORT" --timeout=120 -p no:cacheprovider -rA -v
