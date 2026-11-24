#!/usr/bin/env bash
# Runs the "Modules Tests (Linux)" job from a language's <lang>-modules.yml on this
# machine, against the module server image in IMAGE.
#
# Usage:
#   utils/run-modules-ci-locally.sh
#   IMAGE=valkey/valkey-bundle:9.1.3 EMULATE_RELEASE="" utils/run-modules-ci-locally.sh
#   EMULATE_RELEASE="" utils/run-modules-ci-locally.sh
#   MODULES_TEST_LANG=go utils/run-modules-ci-locally.sh
#
# Environment variables:
#   IMAGE               Module server image (default valkey/valkey-bundle:9.2.0-rc2).
#                       valkey/valkey-bundle:9.1.3 requires EMULATE_RELEASE="" because
#                       its module rejects --emulate-release.
#   EMULATE_RELEASE     Search module emulate-release value, X.Y.Z (default 1.3.0).
#                       Set to an empty string to use the module's own default.
#   ENGINE_VERSION      Valkey engine version (default 9.0).
#   CLUSTER_PORT_START  First of six consecutive cluster ports (default 8001).
#   STANDALONE_PORT     Standalone server port (default 6389).
#   MODULES_TEST_LANG   Language test step to run: python (default) or go.
#
# Requirements:
#   python: docker, valkey-cli (Homebrew: brew install valkey), and the Python clients
#           already built with python3 dev.py build --client async and --client sync.
#   go:     docker, valkey-cli, the Rust toolchain with cbindgen (cargo install cbindgen),
#           and protoc-gen-go (go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.33.0).
#           The script runs make build-glide-client generate-protobuf before testing.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH_DIR="${REPO_ROOT}/.module-ci-local-scratch"
mkdir -p "$SCRATCH_DIR"

IMAGE="${IMAGE:-valkey/valkey-bundle:9.2.0-rc2}"
ENGINE_VERSION="${ENGINE_VERSION:-9.0}"
CLUSTER_PORT_START="${CLUSTER_PORT_START:-8001}"
STANDALONE_PORT="${STANDALONE_PORT:-6389}"
EMULATE_RELEASE="${EMULATE_RELEASE-1.3.0}"

if [ -n "$EMULATE_RELEASE" ] && ! [[ "$EMULATE_RELEASE" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: EMULATE_RELEASE must be X.Y.Z (e.g. 1.3.0) or empty, got: ${EMULATE_RELEASE}" >&2
    exit 1
fi

cleanup() {
    echo "=== Tearing down module containers ==="
    docker rm -f valkey-modules-standalone >/dev/null 2>&1 || true
    for port in $(seq "$CLUSTER_PORT_START" "$((CLUSTER_PORT_START + 5))"); do
        docker rm -f "valkey-modules-cluster-${port}" >/dev/null 2>&1 || true
    done
}
trap cleanup EXIT

echo "=== Starting module servers (IMAGE=${IMAGE}, EMULATE_RELEASE=${EMULATE_RELEASE:-<module default>}) ==="
export IMAGE ENGINE_VERSION CLUSTER_PORT_START STANDALONE_PORT SCRATCH_DIR
if [ -n "$EMULATE_RELEASE" ]; then
    export SEARCH_MODULE_ARGS="--emulate-release ${EMULATE_RELEASE}"
else
    export SEARCH_MODULE_ARGS=""
fi
ENV_CAPTURE="${SCRATCH_DIR}/env-capture"
GITHUB_ENV="$ENV_CAPTURE" "${REPO_ROOT}/utils/start-valkey-docker-modules.sh"
# shellcheck disable=SC1090
source "$ENV_CAPTURE"
rm -f "$ENV_CAPTURE"

echo "=== Liveness: valkey_version, MODULE LIST, search.emulate-release ==="
for port in "$STANDALONE_PORT" $(seq "$CLUSTER_PORT_START" "$((CLUSTER_PORT_START + 5))"); do
    echo "--- port $port ---"
    valkey-cli -p "$port" INFO server | grep -i valkey_version
    valkey-cli -p "$port" MODULE LIST
    valkey-cli -p "$port" CONFIG GET search.emulate-release
done

echo "=== Running modules tests ==="
MODULES_TEST_LANG="${MODULES_TEST_LANG:-python}"
case "$MODULES_TEST_LANG" in
    python)
        cd "${REPO_ROOT}/python"
        python3 dev.py test --args \
            --standalone-endpoints="${MODULES_STANDALONE_ENDPOINT}" \
            --cluster-endpoints="${MODULES_CLUSTER_ENDPOINTS}" \
            -k server_modules \
            --async-backend=asyncio \
            --html=pytest_report.html \
            --self-contained-html \
            -rs
        ;;
    go)
        cd "${REPO_ROOT}/go"
        export PATH="${HOME}/.cargo/bin:$(go env GOPATH)/bin:${PATH}"
        make build-glide-client generate-protobuf
        GO_ARCH="$(uname -m)"
        case "$(uname)-$GO_ARCH" in
            Darwin-arm64) GO_TARGET_TRIPLET="aarch64-apple-darwin" ;;
            Darwin-x86_64) GO_TARGET_TRIPLET="x86_64-apple-darwin" ;;
            *) GO_TARGET_TRIPLET="" ;;
        esac
        if [ -n "$GO_TARGET_TRIPLET" ]; then
            ( cd "${REPO_ROOT}/ffi" && cargo clean -p glide-ffi --release && cargo rustc --crate-type staticlib --release )
            cp "${REPO_ROOT}/ffi/target/release/libglide_ffi.a" \
                "${REPO_ROOT}/go/rustbin/${GO_TARGET_TRIPLET}/libglide_ffi.a"
            "$(rustc --print sysroot)/lib/rustlib/$(rustc -vV | sed -n 's/^host: //p')/bin/rust-objcopy" \
                --remove-section=__LLVM,__bitcode --remove-section=__LLVM,__cmdline \
                "${REPO_ROOT}/go/rustbin/${GO_TARGET_TRIPLET}/libglide_ffi.a"
        fi
        CC="gcc -fsanitize=address" go test -v ./integTest/... \
            -run TestGlideTestSuite \
            -testify.m "^TestModule" \
            --standalone-endpoints="${MODULES_STANDALONE_ENDPOINT}" \
            --cluster-endpoints="${MODULES_CLUSTER_ENDPOINTS}"
        ;;
    *)
        echo "ERROR: unknown MODULES_TEST_LANG '${MODULES_TEST_LANG}' (expected python or go)" >&2
        exit 1
        ;;
esac
