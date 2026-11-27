#!/bin/bash
# Both hosts. Re-checks what the stack's bootstrap signalled, from a process
# started by the SSM agent, which is how every later script runs.
set -euo pipefail

echo "host: $(hostname)  kernel: $(uname -r)"
/opt/amazon/efa/bin/fi_info --version | head -1
lsmod | grep -q '^efa' || { echo "efa kernel module not loaded"; exit 1; }
/opt/amazon/efa/bin/fi_info -p efa -t FI_EP_RDM | head -8
echo "memlock limit for SSM-started processes: $(ulimit -l)"
[ "$(ulimit -l)" = unlimited ] || { echo "memlock is not unlimited; RDMA registration will fail"; exit 1; }
tail -n 2 /var/log/rdma-bootstrap.log
