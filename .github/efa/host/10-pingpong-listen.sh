#!/bin/bash
# Server. Starts fi_pingpong listening on the EFA device and returns at once;
# the listener exits by itself after 180 s. Run 11-pingpong-connect.sh on the
# client next, then 12-pingpong-result.sh here for the server-side numbers.
set -euo pipefail

pkill -x fi_pingpong 2>/dev/null || true
nohup timeout 180 /opt/amazon/efa/bin/fi_pingpong -p efa -e rdm \
  > /var/log/rdma/pingpong-listen.log 2>&1 &
sleep 2
if pgrep -x fi_pingpong >/dev/null; then
  echo "fi_pingpong listening on $SERVER_IP for 180 s"
else
  cat /var/log/rdma/pingpong-listen.log
  exit 1
fi
