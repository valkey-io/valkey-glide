# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Client-Instance Pooling (Python sync client).
Requires a Valkey server (uses test infrastructure endpoints).

Parameterized over cluster_mode (True/False) to ensure both standalone and
cluster deployments behave identically.
"""

import threading
import time
import uuid

import pytest
from glide_shared.config import (
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_shared.routes import AllNodes
from glide_sync.client_pool import ClientPool, PoolConfig
from glide_sync.glide_client import GlideClusterClient

from tests.utils.utils import get_cluster_addresses as _get_cluster_addresses
from tests.utils.utils import get_standalone_address as _get_standalone_address
from tests.utils.utils import sync_check_if_server_version_lt

# ─── Helpers ──────────────────────────────────────────────────────────────────


def _skip_cluster_if_unavailable():
    """Skip test if no cluster endpoints are configured."""
    try:
        cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        if cluster is None or len(cluster.nodes_addr) == 0:
            pytest.skip("No cluster endpoints available")
    except AttributeError:
        pytest.skip("No cluster endpoints available (pytest.valkey_cluster not set)")


def _get_pool_client_config(cluster_mode: bool):
    """Build a client configuration for standalone or cluster mode."""
    if cluster_mode:
        _skip_cluster_if_unavailable()
        return GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            request_timeout=5000,
        )
    else:
        return GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
        )


def _get_lib_name_config(cluster_mode: bool, lib_name, client_info_tag, client_name):
    """Build a client configuration carrying lib_name / client_info_tag."""
    kwargs = dict(request_timeout=5000, client_name=client_name)
    if lib_name is not None:
        kwargs["lib_name"] = lib_name
    if client_info_tag is not None:
        kwargs["client_info_tag"] = client_info_tag
    if cluster_mode:
        _skip_cluster_if_unavailable()
        return GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(), **kwargs
        )
    return GlideClientConfiguration(addresses=[_get_standalone_address()], **kwargs)


def _make_key(cluster_mode: bool, prefix: str) -> str:
    """Generate a key with hash tag for cluster mode to avoid cross-slot issues."""
    uid = uuid.uuid4().hex[:8]
    if cluster_mode:
        return f"{{pool-test}}-{prefix}-{uid}"
    return f"{prefix}-{uid}"


def _wait_for_pool_ready(pool, min_idle=1, timeout_s=30):
    """Poll until pool has at least min_idle clients ready."""
    deadline = time.monotonic() + timeout_s
    while pool.metrics().get("idle", 0) < min_idle and time.monotonic() < deadline:
        time.sleep(0.05)


class TestClientPool:
    """Tests for the ClientPool class backed by glide-core::pool via FFI."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_create_and_metrics(self, cluster_mode):
        """Pool creates successfully and reports metrics."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        _wait_for_pool_ready(pool, 1)

        try:
            metrics = pool.metrics()
            assert metrics["idle"] >= 1, f"Expected at least 1 idle, got {metrics}"
            assert metrics["total"] >= 1, f"Expected total >= 1, got {metrics}"
            assert not pool.is_closed
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_borrow_and_commands(self, cluster_mode):
        """Borrow a client, run commands, auto-release."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        _wait_for_pool_ready(pool, 1)

        try:
            key = _make_key(cluster_mode, "borrow")
            with pool.borrow() as client:
                client.set(key, "hello")
                result = client.get(key)
                assert result == b"hello" or result == "hello"
                client.delete([key])
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_reuse(self, cluster_mode):
        """LIFO reuse: same client_id returned after release."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        _wait_for_pool_ready(pool, 1)

        try:
            id1 = pool.acquire()
            pool.release(id1)
            time.sleep(0.05)
            id2 = pool.acquire()
            pool.release(id2)
            assert id1 == id2, f"Expected LIFO reuse: {id1} != {id2}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_close_rejects_acquire(self, cluster_mode):
        """Closed pool raises on acquire."""
        config = _get_pool_client_config(cluster_mode)
        p = ClientPool.create(config, PoolConfig(max_size=2, min_idle=0))
        p.close()
        with pytest.raises(RuntimeError, match="closed"):
            p.acquire()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_concurrent_access(self, cluster_mode):
        """Multiple threads can borrow and use clients concurrently."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool(
            config, PoolConfig(max_size=5, min_idle=1, acquire_timeout_s=10.0)
        )
        _wait_for_pool_ready(pool, 1)

        try:
            num_threads = 4
            results = [None] * num_threads
            errors = []

            def worker(idx):
                try:
                    with pool.borrow() as client:
                        key = _make_key(cluster_mode, f"concurrent-{idx}")
                        client.set(key, f"thread-{idx}")
                        val = client.get(key)
                        assert val == f"thread-{idx}".encode() or val == f"thread-{idx}"
                        client.delete([key])
                        results[idx] = True
                except Exception as e:
                    errors.append((idx, e))
                    results[idx] = False

            threads = [
                threading.Thread(target=worker, args=(i,)) for i in range(num_threads)
            ]
            for t in threads:
                t.start()
            for t in threads:
                t.join(timeout=30)

            assert not errors, f"Thread errors: {errors}"
            assert all(results), f"Not all threads succeeded: {results}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_timeout_on_exhaustion(self, cluster_mode):
        """Pool raises TimeoutError when exhausted within timeout."""
        config = _get_pool_client_config(cluster_mode)
        p = ClientPool.create(
            config, PoolConfig(max_size=1, min_idle=1, acquire_timeout_s=1.0)
        )
        _wait_for_pool_ready(p, 1)

        try:
            # Acquire the only client
            client_id = p.acquire()

            # Second acquire should time out
            with pytest.raises(TimeoutError):
                p.acquire(timeout=0.5)

            p.release(client_id)
        finally:
            p.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_publish_concurrent(self, cluster_mode):
        """Multiple threads publishing through pooled clients concurrently."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool.create(
            config, PoolConfig(max_size=4, min_idle=4, acquire_timeout_s=15.0)
        )
        _wait_for_pool_ready(pool, 1)

        try:
            channel = f"pool-pub-{uuid.uuid4().hex[:8]}"
            errors = []

            def publisher(thread_id):
                for i in range(5):
                    try:
                        with pool.borrow() as client:
                            client.custom_command(
                                ["PUBLISH", channel, f"msg-{thread_id}-{i}"]
                            )
                    except Exception as e:
                        errors.append(f"Thread {thread_id} msg {i}: {e}")

            threads = [threading.Thread(target=publisher, args=(t,)) for t in range(4)]
            for t in threads:
                t.start()
            for t in threads:
                t.join(timeout=30)

            assert not errors, f"Publish errors: {errors}"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pool_blocking_cmd_isolation(self, cluster_mode):
        """BLPOP on one client doesn't stall GET/SET on another."""
        if cluster_mode:
            return  # BLPOP test is standalone only
        config = _get_pool_client_config(False)
        pool = ClientPool.create(
            config, PoolConfig(max_size=2, min_idle=2, acquire_timeout_s=10.0)
        )
        _wait_for_pool_ready(pool, 2)

        try:
            key = _make_key(False, "blocking")
            result_holder = [None]
            elapsed_holder = [0.0]

            def blocking_worker():
                with pool.borrow() as client:
                    result_holder[0] = client.custom_command(["BLPOP", key, "30"])

            def fast_worker():
                start = time.monotonic()
                with pool.borrow() as client:
                    k = _make_key(False, "nonblocking")
                    client.set(k, "fast")
                    val = client.get(k)
                    client.delete([k])
                    assert val == b"fast" or val == "fast"
                    # Unblock the BLPOP by pushing to its key
                    client.custom_command(["LPUSH", key, "unblock"])
                elapsed_holder[0] = time.monotonic() - start

            t_blocking = threading.Thread(target=blocking_worker)
            t_fast = threading.Thread(target=fast_worker)
            t_blocking.start()
            time.sleep(0.05)  # let BLPOP start
            t_fast.start()
            t_fast.join(timeout=5)
            t_blocking.join(timeout=5)

            assert (
                elapsed_holder[0] < 1.0
            ), f"Fast ops took {elapsed_holder[0]:.2f}s (should be <1s)"
        finally:
            pool.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_scope_on_pool_borrowed_client(self, cluster_mode):
        """Scoped connection works on a pool-borrowed client (not just direct clients)."""
        config = _get_pool_client_config(cluster_mode)
        pool = ClientPool.create(config, PoolConfig(max_size=3, min_idle=1))
        _wait_for_pool_ready(pool, 1)
        try:
            key = _make_key(cluster_mode, "scope-on-pool")
            with pool.borrow() as client:
                client.set(key, "0")

                # Open a scope on the pool-borrowed client
                with client.scoped_connection() as scope:
                    scope.watch(key)
                    val = scope.get(key)
                    scope.multi()
                    scope.set(key, str(int(val) + 1))
                    result = scope.exec()
                    assert result is not None  # No conflict

                assert client.get(key) == b"1"
                client.delete([key])
        finally:
            pool.close()


def _has_client_info_field(client_info: str, field: str, expected: str) -> bool:
    """Return True if a whitespace-delimited ``field=expected`` token is present."""
    return f"{field}={expected}" in client_info.split()


class TestSyncPoolLibName:
    """Pooled clients must report the same lib-name as direct clients.

    Mirrors Java's ClientPoolIntegrationTest lib-name coverage: default,
    user-configured lib_name, client_info_tag only, and the two combined.
    Requires a Valkey server >= 7.2.0 (CLIENT SETINFO / lib-name reporting).
    """

    def _assert_pooled_lib_name(
        self, cluster_mode, lib_name, client_info_tag, expected_lib_name
    ):
        client_name = f"pool-lib-name-{uuid.uuid4().hex[:8]}"
        config = _get_lib_name_config(
            cluster_mode, lib_name, client_info_tag, client_name
        )
        pool = ClientPool.create(config, PoolConfig(max_size=3, min_idle=1))
        _wait_for_pool_ready(pool, 1)
        try:
            with pool.borrow() as client:
                if sync_check_if_server_version_lt(client, "7.2.0"):
                    pytest.skip("lib-name reporting requires Valkey >= 7.2.0")

                if cluster_mode:
                    self._assert_cluster_pool_lib_name(client_name, expected_lib_name)
                else:
                    client_info = client.custom_command(["CLIENT", "INFO"])
                    info_str = (
                        client_info.decode()
                        if isinstance(client_info, (bytes, bytearray))
                        else str(client_info)
                    )
                    assert _has_client_info_field(
                        info_str, "lib-name", expected_lib_name
                    ), (
                        f"Expected pooled client lib-name={expected_lib_name}, "
                        f"but CLIENT INFO returned: {info_str}"
                    )
        finally:
            pool.close()

    def _assert_cluster_pool_lib_name(self, client_name, expected_lib_name):
        """Inspect CLIENT LIST across all nodes for the pooled connection."""
        observer = GlideClusterClient.create(
            GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(), request_timeout=5000
            )
        )
        try:
            responses = observer.custom_command(["CLIENT", "LIST"], AllNodes())
            values = (
                list(responses.values()) if isinstance(responses, dict) else [responses]
            )
            pooled_connections = 0
            for value in values:
                text = (
                    value.decode()
                    if isinstance(value, (bytes, bytearray))
                    else str(value)
                )
                for line in text.splitlines():
                    if not _has_client_info_field(line, "name", client_name):
                        continue
                    pooled_connections += 1
                    assert _has_client_info_field(
                        line, "lib-name", expected_lib_name
                    ), (
                        f"Expected pooled connection to report "
                        f"lib-name={expected_lib_name}, but CLIENT LIST returned: {line}"
                    )
            assert (
                pooled_connections > 0
            ), "Expected at least one pooled cluster connection in CLIENT LIST"
        finally:
            observer.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pooled_client_reports_default_lib_name(self, cluster_mode):
        self._assert_pooled_lib_name(cluster_mode, None, None, "GlidePySync")

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pooled_client_reports_configured_lib_name(self, cluster_mode):
        self._assert_pooled_lib_name(
            cluster_mode, "custom-client", None, "custom-client"
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pooled_client_reports_client_info_tag(self, cluster_mode):
        self._assert_pooled_lib_name(
            cluster_mode, None, "framework:1.2", "GlidePySync(framework:1.2)"
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_pooled_client_reports_combined_library_metadata(self, cluster_mode):
        self._assert_pooled_lib_name(
            cluster_mode,
            "custom-client",
            "framework:1.2",
            "custom-client(framework:1.2)",
        )


class TestPoolPubsubRejection:
    """Pool creation should reject configs with pubsub subscriptions."""

    def test_pool_rejects_pubsub_config(self):
        """Pool creation with pubsub subscriptions raises ValueError."""
        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
            pubsub_subscriptions=GlideClientConfiguration.PubSubSubscriptions(
                callback=None,
                context=None,
                channels_and_patterns={
                    GlideClientConfiguration.PubSubChannelModes.Exact: {"test-channel"}
                },
            ),
        )
        with pytest.raises(ValueError, match="pubsub"):
            ClientPool(config, PoolConfig(max_size=2, min_idle=1))

    def test_pool_rejects_cluster_pubsub_config(self):
        """Pool creation with cluster pubsub subscriptions raises ValueError."""
        _skip_cluster_if_unavailable()
        config = GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            request_timeout=5000,
            pubsub_subscriptions=GlideClusterClientConfiguration.PubSubSubscriptions(
                callback=None,
                context=None,
                channels_and_patterns={
                    GlideClusterClientConfiguration.PubSubChannelModes.Exact: {
                        "test-channel"
                    }
                },
            ),
        )
        with pytest.raises(ValueError, match="pubsub"):
            ClientPool(config, PoolConfig(max_size=2, min_idle=1))


class TestPoolErrorHandling:
    """Negative tests — pool creation surfaces connectivity errors."""

    def test_pool_bad_address_create_fails(self, request):
        """Pool with unreachable address: create should raise immediately."""
        from glide_shared.config import GlideClientConfiguration, NodeAddress

        config = GlideClientConfiguration(
            addresses=[NodeAddress("192.0.2.1", 1)],  # RFC 5737 TEST-NET
            request_timeout=2000,
        )
        with pytest.raises(Exception):
            ClientPool.create(config, PoolConfig(max_size=1, min_idle=1))
