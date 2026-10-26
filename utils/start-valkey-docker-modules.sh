#!/usr/bin/env bash
# Starts standalone + cluster Valkey servers with modules (Search, JSON,
# Bloom, LDAP) via Docker, on dedicated ports, and verifies MODULE LIST on
# every node. Shared by .github/actions/start-valkey-docker/action.yml (CI)
# and utils/run-modules-ci-locally.sh (local reproduction) so the two paths
# cannot silently drift apart.
#
# Every container runs with --network host, which Docker Desktop on macOS
# requires enabling separately (Settings > Resources > Network > "Enable
# host networking") -- without it, containers start but 127.0.0.1:<port>
# is never reachable from the host, which looks identical to the server
# itself failing (CodeRabbit review #7335; see macos_host_network_hint
# below, which fires on that exact failure shape). Not an issue on CI's
# Linux runners, where --network host needs no extra toggle.
#
# Required env vars:
#   IMAGE               Docker image tag, e.g. valkey/valkey-bundle:9.1.3
#   ENGINE_VERSION      Valkey engine version, e.g. 9.0 (controls EXTRA_ARGS)
# Optional env vars:
#   CLUSTER_PORT_START  First of 6 consecutive cluster ports (default 8001)
#   STANDALONE_PORT     Standalone port (default 6389)
#   SEARCH_MODULE_ARGS  Extra args appended to the search module's loadmodule
#                       line, e.g. "--emulate-release 1.3.0". CI never sets
#                       this (empty by default) --
#                       it exists so the local runner's non-default
#                       emulate-release path can still call this exact
#                       script instead of hand-rolling its own bring-up --
#                       an earlier version split those into two separate
#                       bring-up implementations that could drift. The arg
#                       name must be the BARE
#                       config name (e.g. "emulate-release", not
#                       "search.emulate-release" -- that longer form only
#                       exists at the CONFIG GET/SET surface, where Valkey
#                       core namespaces every module config by module name;
#                       a .conf file's loadmodule directive does not do that
#                       stripping, confirmed empirically against both a
#                       local build and this image). SCRATCH_DIR (below)
#                       controls where the generated .conf files are staged.
#   SCRATCH_DIR         Directory for generated .conf files. MUST be under
#                       $HOME on macOS Docker Desktop (shares /Users by
#                       default, not /tmp -- a bind-mounted file staged
#                       under /tmp silently becomes an empty directory in
#                       the container, confirmed the hard way). Defaults to
#                       a dir next to this script.
#
# On success, exports (via GITHUB_ENV if set, else writes export lines to
# $SCRATCH_DIR/endpoints.env for the caller to source) these variables:
#   MODULES_STANDALONE_ENDPOINT
#   MODULES_CLUSTER_ENDPOINTS

set -euo pipefail

: "${IMAGE:?IMAGE must be set, e.g. valkey/valkey-bundle:9.1.3}"
: "${ENGINE_VERSION:?ENGINE_VERSION must be set, e.g. 9.0}"
CLUSTER_PORT_START="${CLUSTER_PORT_START:-8001}"
STANDALONE_PORT="${STANDALONE_PORT:-6389}"
SEARCH_MODULE_ARGS="${SEARCH_MODULE_ARGS:-}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH_DIR="${SCRATCH_DIR:-${REPO_ROOT}/.module-ci-local-scratch}"
mkdir -p "$SCRATCH_DIR"
FALLBACK_ENV_FILE="${SCRATCH_DIR}/endpoints.env"
if [ -z "${GITHUB_ENV:-}" ]; then
    # Fresh file per run, not an append target across runs (CodeRabbit
    # review #7335, start-valkey-docker-modules.sh:39): the earlier version
    # of this fallback wrote export lines to stdout, interleaved with every
    # other echo'd progress line in this script, making it unsafe for a
    # caller to `eval` cleanly. A dedicated file the caller sources instead
    # mirrors the GITHUB_ENV path's own append-to-a-file contract.
    : > "$FALLBACK_ENV_FILE"
fi

export_var() {
    # Mirrors GITHUB_ENV's "KEY=value" append-line convention so this
    # function behaves the same whether running under GitHub Actions or a
    # plain local shell (where GITHUB_ENV is unset).
    local key="$1" value="$2"
    if [ -n "${GITHUB_ENV:-}" ]; then
        echo "${key}=${value}" >> "$GITHUB_ENV"
    else
        echo "export ${key}=${value}" >> "$FALLBACK_ENV_FILE"
    fi
}

echo "Pulling ${IMAGE}..."
docker pull "$IMAGE"

# Every node (standalone + each cluster node) gets its own generated .conf
# file naming ONLY the search module's loadmodule line explicitly (so
# SEARCH_MODULE_ARGS can be appended to it); every other module the bundle
# image ships (json, bf, ldap) is left to the image's own auto-discovery,
# exactly as before this file started generating conf files at all. This
# is the one code path for both the empty-SEARCH_MODULE_ARGS (CI) and
# non-empty (local emulate-release override) cases -- an
# earlier version of this split into two bring-up implementations that
# could drift.
#
# IMPORTANT, found during a later audit (QA round 2 follow-up): naming
# EVERY module explicitly in the conf (an earlier version of this function
# did, listing both search and json) is NOT equivalent to the original
# CLI-flags-only invocation this replaced -- confirmed empirically that the
# original relied on the bundle image's auto-discovery loading all FOUR
# shipped modules (search, json, bf/bloom, ldap), and a conf that lists
# only two of them drops the other two from every run, CI included. The
# bundle image's entrypoint (bundle-docker-entrypoint.sh) skips
# auto-appending a module already named in the conf but still appends
# every OTHER discovered .so -- confirmed empirically that naming only
# search here still yields all 5 modules (search, json, bf, ldap, lua) on
# MODULE LIST, matching the original mechanism exactly when
# SEARCH_MODULE_ARGS is empty.
macos_host_network_hint() {
    # Both readiness-timeout failure sites below call this: a container
    # that starts but is never reachable at 127.0.0.1:<port> looks
    # identical whether the server itself failed, or Docker Desktop's
    # separate "Enable host networking" setting is off (--network host
    # requires it explicitly on macOS; this script relies on every node
    # being reachable at 127.0.0.1 from the host, so there is no fallback
    # path without that setting). CodeRabbit review #7335.
    if [[ "$(uname -s)" == "Darwin" ]]; then
        echo "HINT: this script uses --network host, which Docker Desktop on" >&2
        echo "macOS requires enabling separately (Settings > Resources >" >&2
        echo "Network > 'Enable host networking') -- if that setting is off," >&2
        echo "the container starts but the port above is never reachable from" >&2
        echo "this host, which looks exactly like the readiness timeout above." >&2
    fi
}

gen_conf() {
    local port="$1" extra_lines="$2" conf_path="$3"
    {
        echo "port ${port}"
        # Every endpoint this script (and its callers) ever addresses a
        # node by is 127.0.0.1 -- the cluster's own CLUSTER MEET traffic
        # between nodes stays on loopback too, since all nodes run on the
        # same host via --network host. Binding here closes the exposure
        # protected-mode no otherwise leaves open: with --network host and
        # no bind directive, the server listens on every interface the
        # host has, so any machine that can reach this host's ports could
        # issue commands against a protected-mode-disabled, debug-commands-
        # enabled test server (CodeRabbit review #7335).
        echo "bind 127.0.0.1 -::1"
        echo "protected-mode no"
        echo "save \"\""
        echo "enable-debug-command yes"
        if [ -n "$extra_lines" ]; then
            echo "$extra_lines"
        fi
        echo "loadmodule /usr/lib/valkey/libsearch.so ${SEARCH_MODULE_ARGS}"
    } > "$conf_path"
}

echo "=== Starting standalone Valkey server with modules on port ${STANDALONE_PORT} ==="
docker rm -f valkey-modules-standalone >/dev/null 2>&1 || true
STANDALONE_CONF="${SCRATCH_DIR}/standalone.conf"
gen_conf "$STANDALONE_PORT" "" "$STANDALONE_CONF"
docker run -d --name valkey-modules-standalone --network host \
    -v "${STANDALONE_CONF}:/tmp/valkey.conf" \
    "$IMAGE" valkey-server /tmp/valkey.conf

READY=false
for _ in $(seq 1 30); do
    if valkey-cli -p "$STANDALONE_PORT" PING 2>/dev/null | grep -q PONG; then
        echo "Standalone server with modules ready on port ${STANDALONE_PORT}"
        READY=true
        break
    fi
    sleep 0.5
done
if [ "$READY" != "true" ]; then
    echo "ERROR: Standalone module server on port ${STANDALONE_PORT} failed to start"
    docker logs valkey-modules-standalone 2>&1 || true
    macos_host_network_hint
    exit 1
fi
export_var MODULES_STANDALONE_ENDPOINT "127.0.0.1:${STANDALONE_PORT}"

PORT_START=$CLUSTER_PORT_START
PORT_END=$((PORT_START + 5))
echo "=== Starting Valkey cluster with modules on ports ${PORT_START}-${PORT_END} ==="

MAJOR=$(echo "$ENGINE_VERSION" | cut -d. -f1)
EXTRA_ARGS=""
if [ "$MAJOR" -ge 9 ]; then
    EXTRA_ARGS="cluster-databases 16"
fi

for port in $(seq "$PORT_START" "$PORT_END"); do
    docker rm -f "valkey-modules-cluster-${port}" >/dev/null 2>&1 || true
    NODE_CONF="${SCRATCH_DIR}/cluster-${port}.conf"
    gen_conf "$port" "cluster-enabled yes
cluster-config-file nodes-${port}.conf
cluster-node-timeout 5000
appendonly no
${EXTRA_ARGS}" "$NODE_CONF"
    docker run -d --name "valkey-modules-cluster-${port}" --network host \
        -v "${NODE_CONF}:/tmp/valkey.conf" \
        "$IMAGE" valkey-server /tmp/valkey.conf
done

for port in $(seq "$PORT_START" "$PORT_END"); do
    READY=false
    for _ in $(seq 1 30); do
        if valkey-cli -p "$port" PING 2>/dev/null | grep -q PONG; then
            echo "  Cluster node $port: ready"
            READY=true
            break
        fi
        sleep 0.5
    done
    if [ "$READY" != "true" ]; then
        echo "ERROR: Cluster module node on port $port failed to start"
        docker logs "valkey-modules-cluster-${port}" 2>&1 || true
        macos_host_network_hint
        exit 1
    fi
done

ENDPOINTS=""
for port in $(seq "$PORT_START" "$PORT_END"); do
    if [ -n "$ENDPOINTS" ]; then
        ENDPOINTS="${ENDPOINTS},"
    fi
    ENDPOINTS="${ENDPOINTS}127.0.0.1:${port}"
done

P1=$PORT_START
P2=$((PORT_START + 1))
P3=$((PORT_START + 2))
P4=$((PORT_START + 3))
P5=$((PORT_START + 4))
P6=$((PORT_START + 5))
valkey-cli --cluster create \
    127.0.0.1:$P1 127.0.0.1:$P2 127.0.0.1:$P3 \
    127.0.0.1:$P4 127.0.0.1:$P5 127.0.0.1:$P6 \
    --cluster-replicas 1 \
    --cluster-yes

export_var MODULES_CLUSTER_ENDPOINTS "$ENDPOINTS"
echo "Cluster with modules ready on ports ${PORT_START}-${PORT_END}"

echo "=== Module server verification ==="
# PING proves the server is up; MODULE LIST proves the modules this workflow
# exists for are actually loaded (a reachable server is
# not evidence of a loaded module, and a module-absent test run must never
# look identical to a green run). Checks the FULL set the bundle image
# ships (search, json, bf, ldap), not just search+json -- a QA round 2
# follow-up audit found that an earlier version of this script's conf-file
# generation silently dropped bf and ldap from every run by naming only
# some modules explicitly in the conf (which suppresses the image's own
# auto-discovery for whichever modules ARE named, but a mistake there
# wouldn't have failed any currently-shipped test, since nothing exercises
# bf or ldap -- only this assertion would have caught it). This way a
# future regression of the same shape fails loudly here instead of staying
# latent.
check_modules() {
    local label="$1"
    local port="$2"
    local versions
    versions=$(valkey-cli -p "$port" MODULE LIST | awk '
        $0=="name" { getline name }
        $0=="ver"  { getline ver; print name, ver }
    ')
    echo "${label} (port ${port}) loaded modules (name version):"
    echo "${versions}"

    for mod in search json bf ldap; do
        if ! echo "${versions}" | grep -q "^${mod} "; then
            echo "ERROR: ${label} (port ${port}) is missing the '${mod}' module"
            exit 1
        fi
    done
}

echo "Standalone with modules (${STANDALONE_PORT}): $(valkey-cli -p "$STANDALONE_PORT" PING)"
check_modules "Standalone" "$STANDALONE_PORT"

for port in $(seq "$PORT_START" "$PORT_END"); do
    echo "Cluster with modules node $port: $(valkey-cli -p "$port" PING)"
    check_modules "Cluster node" "$port"
done
