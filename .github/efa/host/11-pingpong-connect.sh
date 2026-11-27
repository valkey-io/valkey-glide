#!/bin/bash
# Client. Drives fi_pingpong against the listener started by 10-pingpong-listen.sh.
set -euo pipefail

: "${SERVER_IP:?}"
/opt/amazon/efa/bin/fi_pingpong -p efa -e rdm "$SERVER_IP"
