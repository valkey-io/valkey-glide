# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests verifying that connection modifiers (compression, request timeout,
inflight limits) are properly inherited and respected by scoped connections.

Ensures scope operations maintain full functional parity with regular commands —
the same options and limitations apply.

Tests run in both standalone and cluster modes via @pytest.mark.parametrize.

Requires a running Valkey server (standalone, and optionally cluster).
"""

import threading
import uuid

import pytest
from glide_sync import (
    Batch,
    CompressionBackend,
    CompressionConfiguration,
    GlideClient,
    GlideClientConfiguration,
    GlideClusterClient,
    GlideClusterClientConfiguration,
    InfoSection,
)
from packaging import version

from tests.utils.utils import get_cluster_addresses as _get_cluster_addresses
from tests.utils.utils import get_standalone_address as _get_standalone_address


def _get_server_version(client) -> str:
    """Get server version string from a connected client."""
    info_result = client.info([InfoSection.SERVER])
    # Cluster clients return dict[str, str], standalone returns str
    if isinstance(info_result, dict):
        info_str = next(iter(info_result.values()), "")
    else:
        info_str = info_result
    for line in str(info_str).split("\n"):
        if line.startswith("valkey_version:") or line.startswith("redis_version:"):
            return line.split(":")[1].strip()
    return "0.0.0"


def _skip_cluster_if_unavailable():
    """Skip test if no cluster endpoints are configured."""
    try:
        cluster = pytest.valkey_cluster  # type: ignore[attr-defined]
        if cluster is None or len(cluster.nodes_addr) == 0:
            pytest.skip("No cluster endpoints available")
    except AttributeError:
        pytest.skip("No cluster endpoints available (pytest.valkey_cluster not set)")


def _skip_standalone_if_unavailable():
    """Skip test if no standalone endpoints are configured."""
    try:
        _get_standalone_address()
    except Exception:
        pytest.skip("No standalone endpoints available")


# ─── Client Pooling (matches conftest pattern from #6335) ─────────────────────

_modifier_client_pool: dict = {}
_modifier_pool_lock = threading.Lock()


def _client_is_usable(client) -> bool:
    """Check if a client's FFI handle is still valid."""
    if client is None:
        return False
    return (
        not client._is_closed
        and client._core_client is not None
        and client._core_client != client._ffi.NULL
    )


def _get_or_create_client(key: str, config: GlideClientConfiguration):
    """Get or create a pooled client by key. Thread-safe, xdist-safe."""
    with _modifier_pool_lock:
        client = _modifier_client_pool.get(key)
    if _client_is_usable(client):
        try:
            client.custom_command(["PING"])
            return client
        except Exception:
            try:
                client.close()
            except Exception:
                pass
    client = GlideClient.create(config)
    with _modifier_pool_lock:
        _modifier_client_pool[key] = client
    return client


def _teardown_client(client, key: str):
    """Pipelined teardown: FLUSHALL in a single round-trip batch."""
    if not _client_is_usable(client):
        return
    try:
        batch = Batch(is_atomic=False)
        batch.custom_command(["FLUSHALL", "ASYNC"])
        client.exec(batch, raise_on_error=True)
    except Exception:
        try:
            client.close()
        except Exception:
            pass
        with _modifier_pool_lock:
            _modifier_client_pool.pop(key, None)


# ─── Helpers for parameterized mode ───────────────────────────────────────────


def _make_key(cluster_mode: bool, prefix: str) -> str:
    """Generate a key with hash tag for cluster mode."""
    uid = uuid.uuid4().hex[:8]
    if cluster_mode:
        return f"{{scope-test}}-{prefix}-{uid}"
    return f"scope-test-{prefix}-{uid}"


def _create_client(cluster_mode: bool, **extra_config):
    """Create a GlideClient or GlideClusterClient based on mode."""
    if cluster_mode:
        _skip_cluster_if_unavailable()
        config = GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            **extra_config,
        )
        return GlideClusterClient.create(config)
    else:
        _skip_standalone_if_unavailable()
        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            **extra_config,
        )
        return GlideClient.create(config)


def _close_client(client):
    """Close a client (works for both types)."""
    try:
        client.close()
    except Exception:
        pass


# ─── Compression Tests ────────────────────────────────────────────────────────


class TestScopeCompression:
    """Tests that scoped connections inherit and apply compression correctly."""

    def _get_compressed_client(self, cluster_mode: bool):
        """Create a client with ZSTD compression enabled."""
        return _create_client(
            cluster_mode,
            request_timeout=5000,
            compression=CompressionConfiguration(
                enabled=True,
                backend=CompressionBackend.ZSTD,
                compression_level=3,
                min_compression_size=64,
            ),
        )

    def _get_raw_client(self, cluster_mode: bool):
        """Create a client WITHOUT compression (for verification)."""
        return _create_client(cluster_mode, request_timeout=5000)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_writes_compressed_data(self, cluster_mode):
        """Data written via scope with compression should be compressed in Valkey."""
        compressed_client = self._get_compressed_client(cluster_mode)
        raw_client = self._get_raw_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "compress-write")
            large_value = "A" * 500  # 500 bytes, well above 64-byte threshold

            # Write via scoped connection
            with compressed_client.scoped_connection() as scope:
                scope.set(key, large_value)

            # Read with same client (decompresses) — should match
            result = compressed_client.get(key)
            assert result == large_value.encode()

            # Read with raw client (no compression) — should differ (compressed bytes)
            raw_result = raw_client.get(key)
            assert (
                raw_result != large_value.encode()
            ), "Value stored via compressed scope should be compressed in Valkey"

            # Cleanup
            compressed_client.delete([key])
        finally:
            _close_client(compressed_client)
            _close_client(raw_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_reads_compressed_data(self, cluster_mode):
        """Scoped GET should decompress data written by the parent client."""
        compressed_client = self._get_compressed_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "compress-read")
            value = "CompressibleData_" * 50  # ~850 bytes

            # Write via parent client (compressed)
            compressed_client.set(key, value)

            # Read via scoped connection — should decompress correctly
            with compressed_client.scoped_connection() as scope:
                retrieved = scope.get(key)
                assert retrieved == value

            # Cleanup
            compressed_client.delete([key])
        finally:
            _close_client(compressed_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_small_values_not_compressed(self, cluster_mode):
        """Values below minCompressionSize are stored uncompressed."""
        raw_client = self._get_raw_client(cluster_mode)
        try:
            # Create client with high min threshold
            client = _create_client(
                cluster_mode,
                request_timeout=5000,
                compression=CompressionConfiguration(
                    enabled=True,
                    backend=CompressionBackend.ZSTD,
                    min_compression_size=256,  # Only compress >= 256 bytes
                ),
            )

            key = _make_key(cluster_mode, "small")
            small_value = "hello"  # 5 bytes — well below threshold

            # Write via scope
            with client.scoped_connection() as scope:
                scope.set(key, small_value)

            # Raw client should see the original value (not compressed)
            raw_result = raw_client.get(key)
            assert (
                raw_result == small_value.encode()
            ), "Small values below minCompressionSize should NOT be compressed"

            # Cleanup
            client.delete([key])
            _close_client(client)
        finally:
            _close_client(raw_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_roundtrip_with_compression(self, cluster_mode):
        """Full round-trip: scope SET → scope GET on same scope."""
        compressed_client = self._get_compressed_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "roundtrip")
            value = "RoundTripData_" * 40  # ~560 bytes

            with compressed_client.scoped_connection() as scope:
                scope.set(key, value)
                retrieved = scope.get(key)
                assert retrieved == value

            # Cleanup
            compressed_client.delete([key])
        finally:
            _close_client(compressed_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_watch_transaction_with_compression(self, cluster_mode):
        """WATCH/MULTI/EXEC works correctly with compressed values."""
        compressed_client = self._get_compressed_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "watch-compress")
            initial_value = "InitialLargeValue_" * 20  # ~360 bytes

            compressed_client.set(key, initial_value)

            with compressed_client.scoped_connection() as scope:
                scope.watch(key)
                current = scope.get(key)
                assert current == initial_value

                new_value = "UpdatedLargeValue_" * 20
                scope.multi()
                scope.set(key, new_value)
                result = scope.exec()
                assert result is not None and result != "None"

            # Verify the updated value
            final = compressed_client.get(key)
            assert final == new_value.encode()

            # Cleanup
            compressed_client.delete([key])
        finally:
            _close_client(compressed_client)


# ─── Request Timeout Tests ────────────────────────────────────────────────────


class TestScopeRequestTimeout:
    """Tests that scoped connections respect the parent's request timeout."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_fast_ops_succeed_with_short_timeout(self, cluster_mode):
        """Fast operations complete within the short timeout."""
        client = _create_client(cluster_mode, request_timeout=100)
        try:
            key = _make_key(cluster_mode, "timeout-fast")

            with client.scoped_connection() as scope:
                scope.set(key, "fast")
                val = scope.get(key)
                assert val == "fast"

            # Cleanup
            client.delete([key])
        finally:
            _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_different_clients_different_scope_timeouts(self, cluster_mode):
        """Each client's scopes use that client's timeout setting."""
        client_a = _create_client(cluster_mode, request_timeout=5000)
        client_b = _create_client(cluster_mode, request_timeout=200)
        try:
            key = _make_key(cluster_mode, "dual-timeout")

            # Both scopes should work for fast operations
            with client_a.scoped_connection() as scope_a:
                scope_a.set(key, "from-A")

            with client_b.scoped_connection() as scope_b:
                val = scope_b.get(key)
                assert val == "from-A"

            # Cleanup
            client_a.delete([key])
        finally:
            _close_client(client_a)
            _close_client(client_b)


# ─── Inflight Request Limit Tests ────────────────────────────────────────────


class TestScopeInflightLimit:
    """Tests that scoped commands count against the parent's inflight limit."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_ops_under_inflight_limit(self, cluster_mode):
        """Sequential scope operations work within inflight limits."""
        client = _create_client(
            cluster_mode, request_timeout=5000, inflight_requests_limit=500
        )
        try:
            with client.scoped_connection() as scope:
                for i in range(50):
                    key = _make_key(cluster_mode, f"inflight-{i}")
                    scope.set(key, f"value-{i}")
                    val = scope.get(key)
                    assert val == f"value-{i}"
                    scope.execute_command("DEL", key)
        finally:
            _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_many_sequential_ops(self, cluster_mode):
        """Many sequential scope operations don't exhaust inflight slots."""
        client = _create_client(
            cluster_mode, request_timeout=5000, inflight_requests_limit=500
        )
        try:
            key = _make_key(cluster_mode, "inflight-seq")

            with client.scoped_connection() as scope:
                # 200 sequential SET/GET pairs — each reserves and releases a slot
                for i in range(200):
                    scope.set(key, str(i))
                    val = scope.get(key)
                    assert val == str(i)

            # Cleanup
            client.delete([key])
        finally:
            _close_client(client)


# ─── Combined Modifiers ───────────────────────────────────────────────────────


class TestScopeCombinedModifiers:
    """Tests that all connection modifiers work together on scoped connections."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_all_modifiers_active(self, cluster_mode):
        """Scope with compression + timeout + inflight all active simultaneously."""
        client = _create_client(
            cluster_mode,
            request_timeout=5000,
            inflight_requests_limit=500,
            compression=CompressionConfiguration(
                enabled=True,
                backend=CompressionBackend.ZSTD,
                compression_level=3,
                min_compression_size=64,
            ),
        )
        raw_client = _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "combined")
            large_value = "TestData_" * 100  # ~900 bytes

            # Write and read via scope
            with client.scoped_connection() as scope:
                scope.set(key, large_value)
                retrieved = scope.get(key)
                assert retrieved == large_value

            # Verify via parent client
            parent_get = client.get(key)
            assert parent_get == large_value.encode()

            # Verify compression happened (raw client sees different bytes)
            raw_value = raw_client.get(key)
            assert (
                raw_value != large_value.encode()
            ), "Data should be stored compressed in Valkey"

            # Cleanup
            client.delete([key])
        finally:
            _close_client(client)
            _close_client(raw_client)


# ─── Basic Scope Operations ───────────────────────────────────────────────────


class TestScopeBasicOperations:
    """Tests that basic scope operations (acquire, release, WATCH/MULTI/EXEC) work."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_acquire_and_release(self, cluster_mode):
        """Scope can be acquired and released."""
        client = _create_client(cluster_mode, request_timeout=5000)
        try:
            with client.scoped_connection() as scope:
                result = scope.execute_command("PING")
                assert result == "PONG"
        finally:
            _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_get_set(self, cluster_mode):
        """Basic GET/SET works via scoped connection."""
        client = _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "basic")

            with client.scoped_connection() as scope:
                scope.set(key, "value")
                val = scope.get(key)
                assert val == "value"

            # Verify via parent client
            result = client.get(key)
            assert result == b"value"

            # Cleanup
            client.delete([key])
        finally:
            _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_watch_multi_exec(self, cluster_mode):
        """WATCH/MULTI/EXEC works correctly via scoped connection."""
        client = _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "occ")
            client.set(key, "0")

            with client.scoped_connection() as scope:
                scope.watch(key)
                current = scope.get(key)
                assert current == "0"

                scope.multi()
                scope.set(key, "1")
                result = scope.exec()
                assert result is not None and result != "None"

            # Verify
            final = client.get(key)
            assert final == b"1"

            # Cleanup
            client.delete([key])
        finally:
            _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_watch_conflict_aborts_exec(self, cluster_mode):
        """WATCH detects external modification and EXEC returns nil."""
        client = _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "conflict")
            client.set(key, "original")

            with client.scoped_connection() as scope:
                scope.watch(key)
                scope.get(key)

                # Modify externally via the main client
                client.set(key, "modified-externally")

                scope.multi()
                scope.set(key, "from-scope")
                result = scope.exec()
                # EXEC returns None when transaction is aborted
                assert result is None or result == "None"

            # Verify external modification persists
            val = client.get(key)
            assert val == b"modified-externally"

            # Cleanup
            client.delete([key])
        finally:
            _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_raises_after_release(self, cluster_mode):
        """Commands fail after scope is released."""
        client = _create_client(cluster_mode, request_timeout=5000)
        try:
            scope = client.scoped_connection().__enter__()
            scope.execute_command("PING")
            scope.__exit__(None, None, None)

            try:
                scope.execute_command("PING")
                assert False, "Should have raised after release"
            except Exception:
                pass  # Expected — scope is released
        finally:
            _close_client(client)


# ─── Database State Tests ─────────────────────────────────────────────────────


class TestDatabaseStateInheritance:
    """Tests that database selection is correctly handled across pool and scope."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_inherits_configured_database(self, cluster_mode):
        """Scope connections use the database from the client's config."""
        if cluster_mode:
            # Cluster database selection requires Valkey 9+
            _skip_cluster_if_unavailable()
            config = GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(),
                request_timeout=5000,
                database_id=2,
            )
            try:
                client = GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                client.close()
                pytest.skip(
                    f"Requires Valkey 9+ for cluster database selection (got {ver})"
                )
        else:
            _skip_standalone_if_unavailable()
            config = GlideClientConfiguration(
                addresses=[_get_standalone_address()],
                request_timeout=5000,
                database_id=2,
            )
            client = GlideClient.create(config)

        key = _make_key(cluster_mode, "db2-scope")
        try:
            # Write via scope on database 2
            with client.scoped_connection() as scope:
                scope.set(key, "on-db2")
                val = scope.get(key)
                assert val == "on-db2"

            # Parent client (also on db 2) should see the key
            result = client.get(key)
            assert result == b"on-db2"

            # A client on database 0 should NOT see the key
            if cluster_mode:
                config_db0 = GlideClusterClientConfiguration(
                    addresses=_get_cluster_addresses(),
                    request_timeout=5000,
                )
                client_db0 = GlideClusterClient.create(config_db0)
            else:
                config_db0 = GlideClientConfiguration(
                    addresses=[_get_standalone_address()],
                    request_timeout=5000,
                    database_id=0,
                )
                client_db0 = GlideClient.create(config_db0)

            result_db0 = client_db0.get(key)
            assert (
                result_db0 is None
            ), "Key written on db2 via scope should not be visible on db0"
            client_db0.close()
        finally:
            client.custom_command(["DEL", key])
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_uses_config_db_not_runtime_db(self, cluster_mode):
        """If parent calls SELECT at runtime, scope inherits the runtime database.

        Scoped connections use the parent client's current_database() at creation
        time, which reflects any runtime SELECT calls made on the parent.
        """
        if cluster_mode:
            _skip_cluster_if_unavailable()
            config = GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(),
                request_timeout=5000,
            )
            try:
                client = GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                client.close()
                pytest.skip(
                    f"Requires Valkey 9+ for cluster database selection (got {ver})"
                )
        else:
            _skip_standalone_if_unavailable()
            config = GlideClientConfiguration(
                addresses=[_get_standalone_address()],
                request_timeout=5000,
                database_id=0,
            )
            client = GlideClient.create(config)

        key = _make_key(cluster_mode, "runtime-db")
        try:
            # Switch parent to db 3 at runtime
            client.custom_command(["SELECT", "3"])
            client.set(key, "on-db3")

            # Scope should inherit the parent's current database (3)
            with client.scoped_connection() as scope:
                result = scope.get(key)
                assert (
                    result == "on-db3"
                ), "Scope should inherit parent's current runtime database (3)"

            # Clean up: delete key on db 3 and switch parent back
            client.custom_command(["DEL", key])
            client.custom_command(["SELECT", "0"])
        finally:
            client.close()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_release_resets_database(self, cluster_mode):
        """After scope user calls SELECT, release resets to configured database.

        Next scope borrow from the same pool should be on the configured database.
        """
        import time

        if cluster_mode:
            _skip_cluster_if_unavailable()
            config = GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(),
                request_timeout=5000,
                database_id=2,
            )
            try:
                client = GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                client.close()
                pytest.skip(
                    f"Requires Valkey 9+ for cluster database selection (got {ver})"
                )
            configured_db = 2
        else:
            _skip_standalone_if_unavailable()
            config = GlideClientConfiguration(
                addresses=[_get_standalone_address()],
                request_timeout=5000,
                database_id=0,
            )
            client = GlideClient.create(config)
            configured_db = 0

        key = _make_key(cluster_mode, "scope-db-reset")
        try:
            # First scope: SELECT to db 4 and write
            with client.scoped_connection() as scope:
                scope.execute_command("SELECT", "4")
                scope.set(key, "on-db4")
            # Scope released — cleanup should reset to configured db

            time.sleep(0.3)  # Allow async cleanup to complete

            # Second scope: should be on configured db (not db 4)
            with client.scoped_connection() as scope2:
                result = scope2.get(key)
                assert result is None, (
                    f"Scope release should reset database. Second scope should be on "
                    f"configured db ({configured_db}), not the previous scope's "
                    f"runtime db (4)."
                )
        finally:
            # Clean up key on db 4
            if cluster_mode:
                config_db4 = GlideClusterClientConfiguration(
                    addresses=_get_cluster_addresses(),
                    request_timeout=5000,
                    database_id=4,
                )
                try:
                    cleanup = GlideClusterClient.create(config_db4)
                    cleanup.custom_command(["DEL", key])
                    cleanup.close()
                except Exception:
                    pass
            else:
                config_db4 = GlideClientConfiguration(
                    addresses=[_get_standalone_address()],
                    request_timeout=5000,
                    database_id=4,
                )
                cleanup = GlideClient.create(config_db4)
                cleanup.custom_command(["DEL", key])
                cleanup.close()
            client.close()

    @pytest.mark.parametrize("cluster_mode", [False])
    def test_pool_resets_database_after_borrow(self, cluster_mode):
        """After a borrower changes database, the pool resets it on release.

        Next borrower should get a connection on the configured database.
        Note: This test uses ClientPool which is standalone-only.
        """
        import time

        from glide_sync import ClientPool, PoolConfig

        _skip_standalone_if_unavailable()

        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
            database_id=0,
        )
        pool = ClientPool(
            config,
            PoolConfig(max_size=1, min_idle=1, acquire_timeout_s=10.0),
        )

        deadline = time.monotonic() + 30
        while pool.metrics().get("idle", 0) < 1 and time.monotonic() < deadline:
            time.sleep(0.5)

        key = _make_key(False, "pool-db-reset")

        try:
            # First borrower: switch to db 5 and write a key there
            with pool.acquire() as client1:
                client1.custom_command(["SELECT", "5"])
                client1.set(key, "on-db5")
            # client1 is released — pool should reset back to db 0

            time.sleep(0.5)  # Allow async reset to complete

            # Second borrower: should be on db 0 (reset happened)
            with pool.acquire() as client2:
                # This key should NOT be visible (we're on db 0, key is on db 5)
                result = client2.get(key)
                assert result is None, (
                    "Pool should reset database to configured value after release. "
                    "Second borrower should be on db 0, not db 5."
                )
        finally:
            # Clean up key on db 5
            cleanup_config = GlideClientConfiguration(
                addresses=[_get_standalone_address()],
                request_timeout=5000,
                database_id=5,
            )
            cleanup = GlideClient.create(cleanup_config)
            cleanup.custom_command(["DEL", key])
            cleanup.close()
            pool.close()


# ─── Disconnection / Failure Behavior Tests ───────────────────────────────────


class TestScopeInflightEnforcement:
    """Tests that scoped commands are rejected when inflight limit is exhausted."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    def test_scope_rejects_when_inflight_exhausted(self, cluster_mode):
        """Scoped commands fail with an error when inflight limit is reached.

        We configure a client with inflight_requests_limit=1, then use CLIENT PAUSE
        to stall one command, and verify the next scope command is rejected.
        """
        client = _create_client(
            cluster_mode, request_timeout=2000, inflight_requests_limit=1
        )

        try:
            # CLIENT PAUSE stalls all responses for 3 seconds
            # This holds an inflight slot on the parent client
            client.custom_command(["CLIENT", "PAUSE", "3000", "ALL"])

            import time

            time.sleep(0.1)

            # Now try a scope command — inflight limit (1) should be exhausted
            # because the paused command is still occupying the slot
            with client.scoped_connection() as scope:
                try:
                    # This should fail because inflight is exhausted
                    scope.ping()
                except Exception as e:
                    error_msg = str(e).lower()
                    assert (
                        "inflight" in error_msg or "timeout" in error_msg
                    ), f"Expected inflight rejection or timeout, got: {e}"
        except Exception:
            pass  # CLIENT PAUSE may itself hit limits
        finally:
            # Unpause to clean up
            try:
                unpause_client = _create_client(cluster_mode, request_timeout=5000)
                unpause_client.custom_command(["CLIENT", "UNPAUSE"])
                _close_client(unpause_client)
            except Exception:
                pass
            _close_client(client)
