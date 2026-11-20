#!/bin/bash
# Server. Builds valkey-server and the valkey-large-object module from source.
# Idempotent: an existing clone or binary is kept. Override the refs with
# VALKEY_REF=<tag|branch> VLO_REF=<tag|branch>; the defaults match the module's CI.
set -euo pipefail

export HOME=${HOME:-/root}
VALKEY_REF=${VALKEY_REF:-unstable}
VLO_REF=${VLO_REF:-main}
SRC=/opt/rdma-test
mkdir -p "$SRC"

dnf -y -q groupinstall "Development Tools"
dnf -y -q install clang clang-devel openssl-devel pkgconf-pkg-config git
if ! command -v cargo >/dev/null 2>&1 && [ ! -x "$HOME/.cargo/bin/cargo" ]; then
  curl -fsSL https://sh.rustup.rs | sh -s -- -y --profile minimal >/dev/null
fi
. "$HOME/.cargo/env"
echo "rust: $(rustc --version)"
# ofi-libfabric-sys finds libfabric through pkg-config; the EFA installer's
# libfabric-aws-devel puts the .pc file under /opt/amazon/efa.
export PKG_CONFIG_PATH=/opt/amazon/efa/lib64/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}

cd "$SRC"
if [ ! -d valkey ]; then
  git clone -q --depth 1 --branch "$VALKEY_REF" https://github.com/valkey-io/valkey.git
fi
if [ ! -x valkey/src/valkey-server ]; then
  echo "building valkey-server ($VALKEY_REF) on $(nproc) cores"
  (cd valkey && make -j"$(nproc)" > /var/log/rdma/valkey-build.log 2>&1) \
    || { tail -n 30 /var/log/rdma/valkey-build.log; exit 1; }
fi
valkey/src/valkey-server --version

if [ ! -d valkey-large-object ]; then
  git clone -q --depth 1 --branch "$VLO_REF" https://github.com/valkey-io/valkey-large-object.git
fi
echo "building valkey-large-object ($VLO_REF, $(git -C valkey-large-object rev-parse --short HEAD))"
(cd valkey-large-object && cargo build --release > /var/log/rdma/vlo-build.log 2>&1) \
  || { tail -n 40 /var/log/rdma/vlo-build.log; exit 1; }
ls -la valkey-large-object/target/release/libvalkey_large_object.so
