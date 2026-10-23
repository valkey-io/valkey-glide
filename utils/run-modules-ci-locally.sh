#!/usr/bin/env bash
# Reproduces the "Modules Tests (Linux)" job from each language's own
# <lang>-modules.yml on this machine, as closely as possible, against an
# overridable module image (proves 1.3 support via a local run of the SAME
# job against a 1.3-bearing image, rather than pinning CI to a new one).
# Shared across every language's port branch, selected via
# MODULES_TEST_LANG, so this file is a single source of truth per
# language, rather than N language branches each overwriting the whole
# script and colliding with each other and with Python's own CI-sync guard
# (tests/test_ci_workflow_sync.py pins this file's Python invocation
# verbatim).
#
# Shares the docker start/verify logic with
# .github/actions/start-valkey-docker/action.yml via
# utils/start-valkey-docker-modules.sh on EVERY path, including the
# emulate-release override.
#
# Usage: three image/release combinations are used across this file's
# proof runs:
#   utils/run-modules-ci-locally.sh                                       # 1: 9.2.0-rc2, emulate-release 1.3.0 (default; run-proof)
#   IMAGE=valkey/valkey-bundle:9.1.3 EMULATE_RELEASE="" utils/run-modules-ci-locally.sh   # 2: 9.1.3, search 1.2.1 (skip-proof)
#   EMULATE_RELEASE="" utils/run-modules-ci-locally.sh                    # 3: 9.2.0-rc2, module's own default emulate-release (1.0.0)
#
# MODULES_TEST_LANG selects which language's test tail runs (default
# "python", preserving every existing caller's behaviour byte-for-byte):
#   MODULES_TEST_LANG=python utils/run-modules-ci-locally.sh   # default
#   MODULES_TEST_LANG=node utils/run-modules-ci-locally.sh
#
# Mac-vs-CI-runner differences: Docker Desktop on macOS shares /Users by
# default, not /tmp -- any file this script bind-mounts into a container
# MUST live under a /Users path or the mount silently becomes an empty
# directory instead of the file, with no error.
#
# Requires, per MODULES_TEST_LANG:
#   python (default): the Python client already built (python3 dev.py
#     build --client async && --client sync) -- this script does not
#     rebuild the wrapper, matching the CI job's "Build Python wrapper"
#     step only in effect, not in mechanism.
#   node: the Node client already built (npm ci && npm run build) --
#     this script does not rebuild the native addon.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Scratch dir for any file this script needs to bind-mount into a container.
# MUST stay under $HOME (which lives under /Users on macOS and is shared
# with Docker Desktop by default) -- see the /tmp warning above.
SCRATCH_DIR="${REPO_ROOT}/.module-ci-local-scratch"
mkdir -p "$SCRATCH_DIR"

IMAGE="${IMAGE:-valkey/valkey-bundle:9.2.0-rc2}"
ENGINE_VERSION="${ENGINE_VERSION:-9.0}"
CLUSTER_PORT_START="${CLUSTER_PORT_START:-8001}"
STANDALONE_PORT="${STANDALONE_PORT:-6389}"
# Module load arg name is "emulate-release" (NOT "search.emulate-release" --
# that longer form only exists at the CONFIG GET/SET surface, where Valkey
# core namespaces every module config by module name; the module's own argv
# parser registers the bare name and does not know about the prefix).
# Confirmed by source (src/valkey_search_options.cc:883,
# vmsdk/src/module_config.cc:179) and empirically against both a local
# macOS build and the valkey/valkey-bundle:9.2.0-rc2 Docker image.
# Baseline is 1.3.0. Set to an empty string to start at the module's own
# default instead (useful for the 9.1.3 skip-proof run, where the module is
# too old to accept this arg at all).
EMULATE_RELEASE="${EMULATE_RELEASE-1.3.0}"

# A newline (or anything else outside X.Y.Z) in EMULATE_RELEASE would be
# interpolated into a generated .conf line. This script runs locally as the
# operator against the operator's own containers (not a cross-trust-boundary
# input), but the check is one line and closes it regardless.
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
        # Single-sourced from .github/workflows/python-modules.yml's "Run modules
        # tests" step -- these must not drift independently; see
        # tests/test_ci_workflow_sync.py, which greps the workflow file for this
        # exact arg list and fails the test suite if it ever changes there without
        # a matching update here).
        python3 dev.py test --args \
            --standalone-endpoints="${MODULES_STANDALONE_ENDPOINT}" \
            --cluster-endpoints="${MODULES_CLUSTER_ENDPOINTS}" \
            -k server_modules \
            --async-backend=asyncio \
            --html=pytest_report.html \
            --self-contained-html \
            -rs
        ;;
    node)
        cd "${REPO_ROOT}/node"
        # Single-sourced from .github/workflows/node-modules.yml's "Run modules
        # tests" step -- these must not drift independently; see
        # tests/ServerModulesCiSync.test.ts, which greps the workflow file for
        # this exact arg list and fails the suite if it ever changes there without
        # a matching update here. Assumes a dev checkout that's already built
        # (npm ci && npm run build), matching the Python arm's own assumption.
        npm run test:modules -- \
            --standalone-endpoints="${MODULES_STANDALONE_ENDPOINT}" \
            --cluster-endpoints="${MODULES_CLUSTER_ENDPOINTS}"
        ;;
    *)
        echo "ERROR: unknown MODULES_TEST_LANG '${MODULES_TEST_LANG}' (expected python or node)" >&2
        exit 1
        ;;
esac
