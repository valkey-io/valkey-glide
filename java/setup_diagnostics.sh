#!/bin/bash
#
# GLIDE Diagnostic Tools Setup
# =============================
# Run this inside your container/pod to install all dependencies
# needed by capture_stuck_state.sh
#
# Usage:
#   kubectl cp setup_diagnostics.sh <pod>:/tmp/
#   kubectl cp capture_stuck_state.sh <pod>:/tmp/
#   kubectl exec -it <pod> -- bash /tmp/setup_diagnostics.sh
#

set +e

echo "=============================================="
echo "GLIDE Diagnostic Tools Setup"
echo "=============================================="

# 1. Install system packages
echo ""
echo "[1/3] Installing system packages (python3, procps, iproute2)..."
if command -v apt-get &>/dev/null; then
    apt-get update -qq && apt-get install -y --no-install-recommends python3 procps iproute2 curl 2>&1 | tail -1
elif command -v yum &>/dev/null; then
    yum install -y python3 procps iproute curl 2>&1 | tail -1
elif command -v apk &>/dev/null; then
    apk add python3 procps iproute2 curl 2>&1 | tail -1
else
    echo "WARNING: Unknown package manager. Please install python3, procps, iproute2, curl manually."
fi

# 2. Install grpcurl
echo ""
echo "[2/3] Installing grpcurl..."
if command -v grpcurl &>/dev/null; then
    echo "Already installed: $(grpcurl --version 2>&1)"
else
    ARCH=$(uname -m)
    case $ARCH in
        x86_64)  GRPC_ARCH="x86_64" ;;
        aarch64) GRPC_ARCH="arm64" ;;
        *) echo "ERROR: Unsupported architecture $ARCH"; exit 1 ;;
    esac
    curl -sSL "https://github.com/fullstorydev/grpcurl/releases/download/v1.9.1/grpcurl_1.9.1_linux_${GRPC_ARCH}.tar.gz" | tar -xz -C /usr/local/bin grpcurl
    if command -v grpcurl &>/dev/null; then
        echo "Installed: $(grpcurl --version 2>&1)"
    else
        echo "ERROR: Failed to install grpcurl"
    fi
fi

# 3. Download console-api proto files
echo ""
echo "[3/3] Downloading console-api proto files..."
PROTO_DIR="/app/proto"
mkdir -p "$PROTO_DIR/google/protobuf"
PROTO_BASE="https://raw.githubusercontent.com/tokio-rs/console/main/console-api/proto"
FAILED=0
for f in instrument.proto common.proto tasks.proto resources.proto async_ops.proto trace.proto; do
    curl -sSL "$PROTO_BASE/$f" -o "$PROTO_DIR/$f" || FAILED=1
done
curl -sSL "$PROTO_BASE/google/protobuf/timestamp.proto" -o "$PROTO_DIR/google/protobuf/timestamp.proto" || FAILED=1
curl -sSL "$PROTO_BASE/google/protobuf/duration.proto" -o "$PROTO_DIR/google/protobuf/duration.proto" || FAILED=1

if [ $FAILED -eq 0 ]; then
    echo "Proto files saved to $PROTO_DIR/"
else
    echo "ERROR: Failed to download some proto files"
fi

# Verify
echo ""
echo "=============================================="
echo "Verification"
echo "=============================================="
echo "python3:  $(command -v python3 && echo OK || echo MISSING)"
echo "grpcurl:  $(command -v grpcurl && echo OK || echo MISSING)"
echo "protos:   $(ls $PROTO_DIR/instrument.proto 2>/dev/null && echo OK || echo MISSING)"
echo "top:      $(command -v top && echo OK || echo MISSING)"
echo "ss:       $(command -v ss && echo OK || echo MISSING)"
echo "jstack:   $(command -v jstack && echo OK || echo MISSING)"
echo ""
echo "=============================================="
echo "Setup complete!"
echo "=============================================="
echo ""
echo "To capture diagnostics when the issue occurs:"
echo "  /tmp/capture_stuck_state.sh <java_pid>"
