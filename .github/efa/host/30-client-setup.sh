#!/bin/bash
# Client. Puts the GLIDE clients into a venv and checks the TCP path to the
# server before any RDMA is attempted. The clients come from one of:
#   GLIDE_WHEELS=<url>   presigned S3 URL for a tar of the two prebuilt wheels.
#        Build the wheel tar in an amazonlinux:2023 arm64 container so
#        the glibc version matches this host. GLIDE_SOURCE must also be set so
#        the test suite is checked out at the matching commit; no build happens
#        on this host. This optional mode is for manually supplied wheels.
#   GLIDE_SOURCE=<full commit sha|tag|branch>  both clients built from a
#        valkey-glide checkout at that ref, release mode, with RDMA. This is
#        the CodeBuild path: the code under test is the checkout.
#        refs/pull/N/head works too. GitHub refuses abbreviated SHAs.
#   GLIDE_VERSION=<pypi version>      the published wheels, with the test
#        suite checked out at the matching git tag (or GLIDE_REF).
# GLIDE_WHEELS wins when set, then GLIDE_SOURCE, then GLIDE_VERSION.
# The default is GLIDE_VERSION=2.6.0rc2.
# The async client is needed in all modes because the test harness
# (tests/utils) imports `glide`.
set -euo pipefail

: "${SERVER_IP:?}"
PORT=${PORT:-7380}
REPO=https://github.com/valkey-io/valkey-glide.git
SRC=/opt/rdma-test
mkdir -p "$SRC" /var/log/rdma
export HOME=${HOME:-/root}

dnf -y -q install git python3-pip
cd "$SRC"

# Fetching by name or by full commit SHA both work on GitHub at depth 1, so one
# path serves tags, branches and PR commits. Fetching by URL needs no named
# remote, so a clone with no `origin` is still usable.
checkout() {
  [ -d valkey-glide/.git ] || git init -q valkey-glide
  git -C valkey-glide fetch -q --depth 1 "$REPO" "$1"
  git -C valkey-glide checkout -q --detach FETCH_HEAD
  CHECKED_OUT=$1
}

build_from_source() {
  case "$GLIDE_SOURCE" in
    *[!0-9a-f]*|????????????????????????????????????????) ;;
    *) echo "GLIDE_SOURCE=$GLIDE_SOURCE looks like an abbreviated SHA; GitHub only serves full 40-character SHAs, tags and branches" >&2; exit 2 ;;
  esac
  checkout "$GLIDE_SOURCE"
  dnf -y -q groupinstall "Development Tools"
  dnf -y -q install openssl-devel pkgconf-pkg-config cmake unzip python3-devel
  if ! command -v cargo >/dev/null 2>&1 && [ ! -x "$HOME/.cargo/bin/cargo" ]; then
    curl -fsSL https://sh.rustup.rs | sh -s -- -y --profile minimal >/dev/null
  fi
  [ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"
  if ! command -v protoc >/dev/null 2>&1; then
    curl -fsSLo /tmp/protoc.zip \
      https://github.com/protocolbuffers/protobuf/releases/download/v3.20.3/protoc-3.20.3-linux-aarch_64.zip
    unzip -oq /tmp/protoc.zip -d /usr/local bin/protoc 'include/*'
  fi
  # ofi-libfabric-sys finds libfabric through pkg-config; the EFA installer's
  # libfabric-aws-devel puts the .pc file under /opt/amazon/efa.
  export PKG_CONFIG_PATH=/opt/amazon/efa/lib64/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}
  echo "rust: $(rustc --version)  protoc: $(protoc --version)"
  echo "building glide-async and glide-sync (rdma, release) at $(git -C valkey-glide rev-parse --short HEAD) on $(nproc) cores"
  # dev.py prints each child's stderr only on failure, as one block, so the
  # first compiler error can sit far above the end of the log; a plain tail
  # would show trailing warnings. PYTHONUNBUFFERED keeps dev.py's own lines
  # in order with its children's.
  local started=$SECONDS
  (cd valkey-glide/python && PYTHONUNBUFFERED=1 python3 dev.py --venv "$SRC/venv" build --client all --mode release --rdma \
     > /var/log/rdma/glide-build.log 2>&1) \
    || { grep -nE '^(error|❌|\[ERROR\])' -A25 /var/log/rdma/glide-build.log | head -120 \
           || tail -n 80 /var/log/rdma/glide-build.log; exit 1; }
  echo "built in $((SECONDS - started)) s"
  . venv/bin/activate
}

install_wheels() {
  GLIDE_VERSION=${GLIDE_VERSION:-2.6.0rc2}
  # The test suite is checked out at the tag matching the wheel: PyPI 2.6.0rc4
  # is git v2.6.0-rc4. The wheel does not record its commit, so a final wheel
  # published before the repo is tagged (2.6.0 with only v2.6.0-rc4 in git)
  # needs GLIDE_REF set by hand.
  GLIDE_REF=${GLIDE_REF:-v$(sed -E 's/(rc[0-9]+)$/-\1/' <<<"$GLIDE_VERSION")}
  if ! git ls-remote --exit-code --tags "$REPO" "refs/tags/$GLIDE_REF" >/dev/null 2>&1; then
    echo "no tag $GLIDE_REF in valkey-glide; nearest tags:"
    git ls-remote --tags "$REPO" "refs/tags/v${GLIDE_VERSION%%rc*}*" | awk '{print "  " $2}' | sed 's#refs/tags/##' | grep -v '\^{}'
    echo "pass GLIDE_REF=<tag> to pick one"
    exit 1
  fi
  checkout "$GLIDE_REF"
  [ -d venv ] || python3 -m venv venv
  . venv/bin/activate
  pip install -q --upgrade pip
  # A source build leaves valkey-glide-shared as an editable install pointing
  # into the checkout; the wheels carry their own copy, so the editable one
  # would shadow it in `pip list` and could in sys.path.
  pip uninstall -y -q valkey-glide-shared 2>/dev/null || true
  pip install -q "valkey-glide-sync==$GLIDE_VERSION" "valkey-glide==$GLIDE_VERSION" \
    pytest pytest-timeout pytest-html anyio
}

install_prebuilt_wheels() {
  : "${GLIDE_SOURCE:?GLIDE_WHEELS requires GLIDE_SOURCE to check out the test suite}"
  case "$GLIDE_SOURCE" in
    *[!0-9a-f]*|????????????????????????????????????????) ;;
    *) echo "GLIDE_SOURCE=$GLIDE_SOURCE looks like an abbreviated SHA; GitHub only serves full 40-character SHAs, tags and branches" >&2; exit 2 ;;
  esac
  checkout "$GLIDE_SOURCE"
  [ -d venv ] || python3 -m venv venv
  . venv/bin/activate
  pip install -q --upgrade pip
  pip uninstall -y -q valkey-glide-shared 2>/dev/null || true
  # Presigned S3 URLs expire in ~1 hour; --max-time 120 avoids a silent hang
  # on a stale URL.
  trap 'rm -rf /tmp/rdma-wheels' EXIT
  mkdir -p /tmp/rdma-wheels
  curl -fsSL --max-time 120 -o /tmp/rdma-wheels/wheels.tar "$GLIDE_WHEELS"
  tar -xf /tmp/rdma-wheels/wheels.tar -C /tmp/rdma-wheels
  whl_count=$(find /tmp/rdma-wheels -name '*.whl' | wc -l)
  [ "$whl_count" -gt 0 ] || { echo "no .whl files found in wheel tarball" >&2; exit 1; }
  find /tmp/rdma-wheels -name '*.whl' -print0 | xargs -0 pip install -q pytest pytest-timeout pytest-html anyio
}

if [ -n "${GLIDE_WHEELS:-}" ]; then
  install_prebuilt_wheels
elif [ -n "${GLIDE_SOURCE:-}" ]; then
  build_from_source
else
  install_wheels
fi
echo "python: $(python --version)  glide-sync: $(pip show valkey-glide-sync | awk '/^Version/ {print $2}')  checkout: $CHECKED_OUT ($(git -C valkey-glide rev-parse --short HEAD))"

python - <<EOF
from glide_sync import GlideClient
print("rdma_available:", GlideClient.rdma_available())
print("rdma_usable:   ", GlideClient.rdma_usable())
EOF

python - <<EOF
from glide_shared.config import GlideClientConfiguration, NodeAddress
from glide_sync import GlideClient
client = GlideClient.create(GlideClientConfiguration(addresses=[NodeAddress("$SERVER_IP", $PORT)]))
print("PING:        ", client.custom_command(["PING"]))
print("BLOB.TCP_SET:", client.custom_command(["BLOB.TCP_SET", "probe", "hello over tcp"]))
print("BLOB.TCP_GET:", client.custom_command(["BLOB.TCP_GET", "probe"]))
print("DEL:         ", client.custom_command(["DEL", "probe"]))
client.close()
EOF
