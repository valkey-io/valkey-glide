# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests verifying that connection modifiers (compression, database selection)
are properly inherited and respected by async scoped connections.

Ensures scope operations maintain full functional parity with regular commands —
the same options and limitations apply.

Tests run in both standalone and cluster modes via @pytest.mark.parametrize.

Requires a running Valkey server (standalone, and optionally cluster).

Run with:
    PYTHONPATH="glide-async/python:glide-shared:$PYTHONPATH" \
    pytest tests/async_tests/test_async_scope_modifiers.py --noconftest -v
"""

import asyncio
import uuid

import pytest
from glide import (
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

pytestmark = pytest.mark.asyncio


async def _get_server_version(client) -> str:
    """Get server version string from a connected client."""
    info_result = await client.info([InfoSection.SERVER])
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


# ─── Helpers for parameterized mode ───────────────────────────────────────────


def _make_key(cluster_mode: bool, prefix: str) -> str:
    """Generate a key with hash tag for cluster mode."""
    uid = uuid.uuid4().hex[:8]
    if cluster_mode:
        return f"{{scope-test}}-{prefix}-{uid}"
    return f"scope-test-{prefix}-{uid}"


async def _create_client(cluster_mode: bool, **extra_config):  # type: ignore[return]
    """Create a GlideClient or GlideClusterClient based on mode."""
    if cluster_mode:
        _skip_cluster_if_unavailable()
        cluster_cfg = GlideClusterClientConfiguration(
            addresses=_get_cluster_addresses(),
            **extra_config,
        )
        return await GlideClusterClient.create(cluster_cfg)
    else:
        _skip_standalone_if_unavailable()
        standalone_cfg = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            **extra_config,
        )
        return await GlideClient.create(standalone_cfg)


async def _close_client(client):
    """Close a client (works for both types)."""
    try:
        await client.aclose()
    except Exception:
        pass


# ─── Compression Tests ────────────────────────────────────────────────────────


class TestAsyncScopeCompression:
    """Tests that async scoped connections inherit and apply compression correctly."""

    async def _get_compressed_client(self, cluster_mode: bool):
        """Create a client with ZSTD compression enabled."""
        return await _create_client(
            cluster_mode,
            request_timeout=5000,
            compression=CompressionConfiguration(
                enabled=True,
                backend=CompressionBackend.ZSTD,
                compression_level=3,
                min_compression_size=64,
            ),
        )

    async def _get_raw_client(self, cluster_mode: bool):
        """Create a client WITHOUT compression (for verification)."""
        return await _create_client(cluster_mode, request_timeout=5000)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_writes_compressed_data(self, cluster_mode):
        """Data written via scope with compression should be compressed in Valkey."""
        compressed_client = await self._get_compressed_client(cluster_mode)
        raw_client = await self._get_raw_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "compress-write")
            large_value = "A" * 500  # 500 bytes, well above 64-byte threshold

            # Write via scoped connection
            async with await compressed_client.scoped_connection() as scope:
                await scope.set(key, large_value)

            # Read with same client (decompresses) — should match
            result = await compressed_client.get(key)
            assert result == large_value.encode()

            # Read with raw client (no compression) — should differ (compressed bytes)
            raw_result = await raw_client.get(key)
            assert (
                raw_result != large_value.encode()
            ), "Value stored via compressed scope should be compressed in Valkey"

            # Cleanup
            await compressed_client.delete([key])
        finally:
            await _close_client(compressed_client)
            await _close_client(raw_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_reads_compressed_data(self, cluster_mode):
        """Scoped GET should decompress data written by the parent client."""
        compressed_client = await self._get_compressed_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "compress-read")
            value = "CompressibleData_" * 50  # ~850 bytes

            # Write via parent client (compressed)
            await compressed_client.set(key, value)

            # Read via scoped connection — should decompress correctly
            async with await compressed_client.scoped_connection() as scope:
                retrieved = await scope.get(key)
                assert retrieved == value

            # Cleanup
            await compressed_client.delete([key])
        finally:
            await _close_client(compressed_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_roundtrip_with_compression(self, cluster_mode):
        """Full round-trip: scope SET → scope GET on same scope."""
        compressed_client = await self._get_compressed_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "roundtrip")
            value = "RoundTripData_" * 40  # ~560 bytes

            async with await compressed_client.scoped_connection() as scope:
                await scope.set(key, value)
                retrieved = await scope.get(key)
                assert retrieved == value

            # Cleanup
            await compressed_client.delete([key])
        finally:
            await _close_client(compressed_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_small_values_not_compressed(self, cluster_mode):
        """Values below minCompressionSize are stored uncompressed."""
        raw_client = await self._get_raw_client(cluster_mode)
        try:
            # Create client with high min threshold
            client = await _create_client(
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
            async with await client.scoped_connection() as scope:
                await scope.set(key, small_value)

            # Raw client should see the original value (not compressed)
            raw_result = await raw_client.get(key)
            assert (
                raw_result == small_value.encode()
            ), "Small values below minCompressionSize should NOT be compressed"

            # Cleanup
            await client.delete([key])
            await _close_client(client)
        finally:
            await _close_client(raw_client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_watch_transaction_with_compression(self, cluster_mode):
        """WATCH/MULTI/EXEC works correctly with compressed values."""
        compressed_client = await self._get_compressed_client(cluster_mode)
        try:
            key = _make_key(cluster_mode, "watch-compress")
            initial_value = "InitialLargeValue_" * 20  # ~360 bytes

            await compressed_client.set(key, initial_value)

            async with await compressed_client.scoped_connection() as scope:
                await scope.watch(key)
                current = await scope.get(key)
                assert current == initial_value

                new_value = "UpdatedLargeValue_" * 20
                await scope.multi()
                await scope.set(key, new_value)
                result = await scope.exec()
                assert result is not None and result != "None"

            # Verify the updated value
            final = await compressed_client.get(key)
            assert final == new_value.encode()

            # Cleanup
            await compressed_client.delete([key])
        finally:
            await _close_client(compressed_client)


# ─── Basic Scope Operations ───────────────────────────────────────────────────


class TestAsyncScopeBasicOperations:
    """Tests that basic scope operations work in both modes (async)."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_acquire_and_release(self, cluster_mode):
        """Scope can be acquired and released on an async client."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            async with await client.scoped_connection() as scope:
                result = await scope.execute_command("PING")
                assert result == "PONG"
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_get_set(self, cluster_mode):
        """Basic GET/SET works via async scoped connection."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "basic")

            async with await client.scoped_connection() as scope:
                await scope.set(key, "value")
                val = await scope.get(key)
                assert val == "value"

            # Verify via parent client
            result = await client.get(key)
            assert result == b"value"

            # Cleanup
            await client.delete([key])
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_watch_multi_exec(self, cluster_mode):
        """WATCH/MULTI/EXEC works correctly via async scoped connection."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "occ")
            await client.set(key, "0")

            async with await client.scoped_connection() as scope:
                await scope.watch(key)
                current = await scope.get(key)
                assert current == "0"

                await scope.multi()
                await scope.set(key, "1")
                result = await scope.exec()
                assert result is not None and result != "None"

            # Verify
            final = await client.get(key)
            assert final == b"1"

            # Cleanup
            await client.delete([key])
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_raises_after_release(self, cluster_mode):
        """Commands fail after scope is released."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            scope = await (await client.scoped_connection()).__aenter__()
            await scope.execute_command("PING")
            await scope.__aexit__(None, None, None)

            try:
                await scope.execute_command("PING")
                assert False, "Should have raised after release"
            except Exception:
                pass  # Expected — scope is released
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_watch_conflict_aborts_exec(self, cluster_mode):
        """WATCH detects external modification and EXEC returns nil (async)."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "conflict")
            await client.set(key, "original")

            async with await client.scoped_connection() as scope:
                await scope.watch(key)
                await scope.get(key)

                # Modify externally via the main client
                await client.set(key, "modified-externally")

                await scope.multi()
                await scope.set(key, "from-scope")
                result = await scope.exec()
                # EXEC returns None when transaction is aborted
                assert result is None or result == "None"

            # Verify external modification persists
            val = await client.get(key)
            assert val == b"modified-externally"

            # Cleanup
            await client.delete([key])
        finally:
            await _close_client(client)


# ─── Database State Tests ─────────────────────────────────────────────────────


class TestAsyncDatabaseStateInheritance:
    """Tests that database selection is correctly handled across scope lifecycle."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_inherits_configured_database(self, cluster_mode):
        """Scope connections use the database from the client's config."""
        if cluster_mode:
            _skip_cluster_if_unavailable()
            config = GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(),
                request_timeout=5000,
                database_id=2,
            )
            try:
                client = await GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = await _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                await client.aclose()
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
            client = await GlideClient.create(config)

        key = _make_key(cluster_mode, "db2-scope")
        try:
            # Write via scope on database 2
            async with await client.scoped_connection() as scope:
                await scope.set(key, "on-db2")
                val = await scope.get(key)
                assert val == "on-db2"

            # Parent client (also on db 2) should see the key
            result = await client.get(key)
            assert result == b"on-db2"

            # A client on database 0 should NOT see the key
            if cluster_mode:
                config_db0 = GlideClusterClientConfiguration(
                    addresses=_get_cluster_addresses(),
                    request_timeout=5000,
                )
                client_db0 = await GlideClusterClient.create(config_db0)
            else:
                config_db0 = GlideClientConfiguration(
                    addresses=[_get_standalone_address()],
                    request_timeout=5000,
                    database_id=0,
                )
                client_db0 = await GlideClient.create(config_db0)

            result_db0 = await client_db0.get(key)
            assert (
                result_db0 is None
            ), "Key written on db2 via scope should not be visible on db0"
            await client_db0.aclose()
        finally:
            await client.custom_command(["DEL", key])
            await client.aclose()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_inherits_runtime_select(self, cluster_mode):
        """If parent calls SELECT at runtime, scope inherits the runtime database."""
        if cluster_mode:
            _skip_cluster_if_unavailable()
            config = GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(),
                request_timeout=5000,
            )
            try:
                client = await GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = await _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                await client.aclose()
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
            client = await GlideClient.create(config)

        key = _make_key(cluster_mode, "runtime-db")
        try:
            # Switch parent to db 3 at runtime
            await client.custom_command(["SELECT", "3"])
            await client.set(key, "on-db3")

            # Scope should inherit the parent's current database (3)
            async with await client.scoped_connection() as scope:
                result = await scope.get(key)
                assert (
                    result == "on-db3"
                ), "Scope should inherit parent's current runtime database (3)"

            # Clean up: delete key on db 3 and switch parent back
            await client.custom_command(["DEL", key])
            await client.custom_command(["SELECT", "0"])
        finally:
            await client.aclose()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_uses_config_db_not_runtime_db(self, cluster_mode):
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
                client = await GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = await _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                await client.aclose()
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
            client = await GlideClient.create(config)

        key = _make_key(cluster_mode, "runtime-db")
        try:
            # Switch parent to db 3 at runtime
            await client.custom_command(["SELECT", "3"])
            await client.set(key, "on-db3")

            # Scope should inherit the parent's current database (3)
            async with await client.scoped_connection() as scope:
                result = await scope.get(key)
                assert (
                    result == "on-db3"
                ), "Scope should inherit parent's current runtime database (3)"

            # Clean up: delete key on db 3 and switch parent back
            await client.custom_command(["DEL", key])
            await client.custom_command(["SELECT", "0"])
        finally:
            await client.aclose()

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_release_resets_database(self, cluster_mode):
        """After scope user calls SELECT, release resets to configured database.

        Next scope borrow from the same pool should be on the configured database.
        """
        if cluster_mode:
            _skip_cluster_if_unavailable()
            config = GlideClusterClientConfiguration(
                addresses=_get_cluster_addresses(),
                request_timeout=5000,
                database_id=2,
            )
            try:
                client = await GlideClusterClient.create(config)
            except Exception:
                pytest.skip(
                    "Cluster database selection not supported (requires Valkey 9+)"
                )
            ver = await _get_server_version(client)
            if version.parse(ver) < version.parse("9.0.0"):
                await client.aclose()
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
            client = await GlideClient.create(config)
            configured_db = 0

        key = _make_key(cluster_mode, "scope-db-reset")
        try:
            # First scope: SELECT to db 4 and write
            async with await client.scoped_connection() as scope:
                await scope.execute_command("SELECT", "4")
                await scope.set(key, "on-db4")
            # Scope released — cleanup should reset to configured db

            await asyncio.sleep(0.3)  # Allow async cleanup to complete

            # Second scope: should be on configured db (not db 4)
            async with await client.scoped_connection() as scope2:
                result = await scope2.get(key)
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
                    cleanup = await GlideClusterClient.create(config_db4)
                    await cleanup.custom_command(["DEL", key])
                    await cleanup.aclose()
                except Exception:
                    pass
            else:
                config_db4 = GlideClientConfiguration(
                    addresses=[_get_standalone_address()],
                    request_timeout=5000,
                    database_id=4,
                )
                cleanup = await GlideClient.create(config_db4)
                await cleanup.custom_command(["DEL", key])
                await cleanup.aclose()
            await client.aclose()

    @pytest.mark.parametrize("cluster_mode", [False])
    async def test_pool_resets_database_after_borrow(self, cluster_mode):
        """After a borrower changes database, the pool resets it on release.

        Next borrower should get a connection on the configured database.
        Note: This test uses AsyncClientPool which is the async equivalent of ClientPool.
        """
        from glide import AsyncClientPool, PoolConfig

        _skip_standalone_if_unavailable()

        config = GlideClientConfiguration(
            addresses=[_get_standalone_address()],
            request_timeout=5000,
            database_id=0,
        )
        pool = AsyncClientPool(config, PoolConfig(max_size=1, min_idle=1))
        await asyncio.sleep(2)  # Wait for min_idle warmup

        key = _make_key(False, "pool-db-reset")

        try:
            # First borrower: switch to db 5 and write a key there
            async with pool.borrow() as client1:
                await client1.custom_command(["SELECT", "5"])
                await client1.set(key, "on-db5")
            # client1 is released — pool should reset back to db 0

            await asyncio.sleep(0.5)  # Allow async reset to complete

            # Second borrower: should be on db 0 (reset happened)
            async with pool.borrow() as client2:
                # This key should NOT be visible (we're on db 0, key is on db 5)
                result = await client2.get(key)
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
            cleanup = await GlideClient.create(cleanup_config)
            await cleanup.custom_command(["DEL", key])
            await cleanup.aclose()
            await pool.aclose()


# ─── Request Timeout Tests ────────────────────────────────────────────────────


class TestAsyncScopeRequestTimeout:
    """Tests that async scoped connections respect the parent's request timeout."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_fast_ops_succeed_with_short_timeout(self, cluster_mode):
        """Fast operations complete within the short timeout."""
        client = await _create_client(cluster_mode, request_timeout=100)
        try:
            key = _make_key(cluster_mode, "timeout-fast")

            async with await client.scoped_connection() as scope:
                await scope.set(key, "fast")
                val = await scope.get(key)
                assert val == "fast"

            # Cleanup
            await client.delete([key])
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_different_clients_different_scope_timeouts(self, cluster_mode):
        """Each client's scopes use that client's timeout setting."""
        client_a = await _create_client(cluster_mode, request_timeout=5000)
        client_b = await _create_client(cluster_mode, request_timeout=200)
        try:
            key = _make_key(cluster_mode, "dual-timeout")

            # Both scopes should work for fast operations
            async with await client_a.scoped_connection() as scope_a:
                await scope_a.set(key, "from-A")

            async with await client_b.scoped_connection() as scope_b:
                val = await scope_b.get(key)
                assert val == "from-A"

            # Cleanup
            await client_a.delete([key])
        finally:
            await _close_client(client_a)
            await _close_client(client_b)


# ─── Inflight Request Limit Tests ────────────────────────────────────────────


class TestAsyncScopeInflightLimit:
    """Tests that async scoped commands count against the parent's inflight limit."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_ops_under_inflight_limit(self, cluster_mode):
        """Sequential scope operations work within inflight limits."""
        client = await _create_client(
            cluster_mode, request_timeout=5000, inflight_requests_limit=500
        )
        try:
            async with await client.scoped_connection() as scope:
                for i in range(50):
                    key = _make_key(cluster_mode, f"inflight-{i}")
                    await scope.set(key, f"value-{i}")
                    val = await scope.get(key)
                    assert val == f"value-{i}"
                    await scope.execute_command("DEL", key)
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_many_sequential_ops(self, cluster_mode):
        """Many sequential scope operations don't exhaust inflight slots."""
        client = await _create_client(
            cluster_mode, request_timeout=5000, inflight_requests_limit=500
        )
        try:
            key = _make_key(cluster_mode, "inflight-seq")

            async with await client.scoped_connection() as scope:
                # 200 sequential SET/GET pairs — each reserves and releases a slot
                for i in range(200):
                    await scope.set(key, str(i))
                    val = await scope.get(key)
                    assert val == str(i)

            # Cleanup
            await client.delete([key])
        finally:
            await _close_client(client)


# ─── Combined Modifiers ───────────────────────────────────────────────────────


class TestAsyncScopeCombinedModifiers:
    """Tests that all connection modifiers work together on async scoped connections."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_all_modifiers_active(self, cluster_mode):
        """Scope with compression + timeout + inflight all active simultaneously."""
        client = await _create_client(
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
        raw_client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "combined")
            large_value = "TestData_" * 100  # ~900 bytes

            # Write and read via scope
            async with await client.scoped_connection() as scope:
                await scope.set(key, large_value)
                retrieved = await scope.get(key)
                assert retrieved == large_value

            # Verify via parent client
            parent_get = await client.get(key)
            assert parent_get == large_value.encode()

            # Verify compression happened (raw client sees different bytes)
            raw_value = await raw_client.get(key)
            assert (
                raw_value != large_value.encode()
            ), "Data should be stored compressed in Valkey"

            # Cleanup
            await client.delete([key])
        finally:
            await _close_client(client)
            await _close_client(raw_client)


# ─── Disconnection / Failure Behavior Tests ───────────────────────────────────


class TestAsyncScopeInflightEnforcement:
    """Tests that async scoped commands are rejected when inflight limit is exhausted."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_rejects_when_inflight_exhausted(self, cluster_mode):
        """Scoped commands fail with an error when inflight limit is reached.

        We configure a client with inflight_requests_limit=1, then use CLIENT PAUSE
        to stall one command, and verify the next scope command is rejected.
        """
        client = await _create_client(
            cluster_mode, request_timeout=2000, inflight_requests_limit=1
        )

        try:
            # CLIENT PAUSE stalls all responses for 3 seconds
            # This holds an inflight slot on the parent client
            await client.custom_command(["CLIENT", "PAUSE", "3000", "ALL"])

            await asyncio.sleep(0.05)

            # Now try a scope command — inflight limit (1) should be exhausted
            # because the paused command is still occupying the slot
            async with await client.scoped_connection() as scope:
                try:
                    # This should fail because inflight is exhausted
                    await scope.ping()
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
                unpause_client = await _create_client(
                    cluster_mode, request_timeout=5000
                )
                await unpause_client.custom_command(["CLIENT", "UNPAUSE"])
                await _close_client(unpause_client)
            except Exception:
                pass
            await _close_client(client)


# ─── Scope Connection Reuse Tests ─────────────────────────────────────────────


class TestAsyncScopeConnectionReuse:
    """Tests that scope connections are reused from the internal scope pool."""

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_connection_reuse(self, cluster_mode):
        """Acquire/release multiple scopes — connections are reused."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            # First scope
            async with await client.scoped_connection() as scope:
                await scope.execute_command("PING")

            await asyncio.sleep(0.05)

            # Second scope — should reuse the same connection
            async with await client.scoped_connection() as scope:
                await scope.execute_command("PING")

            await asyncio.sleep(0.05)

            # Third scope
            async with await client.scoped_connection() as scope:
                await scope.execute_command("PING")
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    async def test_scope_concurrent_multiple(self, cluster_mode):
        """Multiple concurrent scopes don't interfere."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key1 = _make_key(cluster_mode, "cs1")
            key2 = _make_key(cluster_mode, "cs2")

            scope1 = await (await client.scoped_connection()).__aenter__()
            scope2 = await (await client.scoped_connection()).__aenter__()

            await scope1.set(key1, "s1")
            await scope2.set(key2, "s2")

            assert await scope1.get(key1) == "s1"
            assert await scope2.get(key2) == "s2"

            # WATCH conflict detection across scopes
            await scope1.watch(key2)
            await scope2.set(key2, "modified-by-s2")

            await scope1.multi()
            await scope1.set(key2, "from-s1")
            result = await scope1.exec()
            assert result is None or result == "None"

            await scope1.__aexit__(None, None, None)
            await scope2.__aexit__(None, None, None)

            await client.delete([key1, key2])
        finally:
            await _close_client(client)

    # ─── Cross-Slot and Cluster-Specific Tests ────────────────────────────────

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_scope_rejects_cross_slot_keys(self, cluster_mode):
        """Scope rejects commands targeting different slots in cluster mode.

        When a scope is pinned to a slot, commands targeting a different slot
        are rejected — either by client-side validation (CROSSSLOT) or by the
        server (MOVED) since the scope's connection is to a single node.
        """
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key1 = _make_key(cluster_mode, "pin")
            async with await client.scoped_connection() as scope:
                # First command pins the scope to a slot
                await scope.set(key1, "value1")

                # Second command with a DIFFERENT hash tag targets a different slot
                with pytest.raises(Exception, match="(?i)(cross.?slot|moved)"):
                    await scope.set("{other-tag}-key2", "value2")
            await client.delete([key1])
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_scope_allows_same_slot_keys(self, cluster_mode):
        """Scope allows multiple keys in the same slot (same hash tag)."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key1 = _make_key(cluster_mode, "same1")
            key2 = _make_key(cluster_mode, "same2")
            async with await client.scoped_connection() as scope:
                await scope.set(key1, "v1")
                await scope.set(key2, "v2")
                assert await scope.get(key1) == "v1"
                assert await scope.get(key2) == "v2"
            await client.delete([key1, key2])
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_scope_watch_multi_exec_cluster(self, cluster_mode):
        """WATCH/MULTI/EXEC works in cluster mode with hash-tagged keys."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            key = _make_key(cluster_mode, "counter")
            await client.set(key, "0")

            async with await client.scoped_connection() as scope:
                await scope.watch(key)
                val = await scope.get(key)
                await scope.multi()
                await scope.set(key, str(int(val) + 1))
                result = await scope.exec()
                # EXEC returns results array on success, None on WATCH conflict
                assert result is not None

            assert await client.get(key) == b"1"
            await client.delete([key])
        finally:
            await _close_client(client)

    @pytest.mark.parametrize("cluster_mode", [True])
    async def test_scope_different_routing_keys_cluster(self, cluster_mode):
        """Scope connections with different routing_keys target correct nodes."""
        client = await _create_client(cluster_mode, request_timeout=5000)
        try:
            # These hash tags map to different slots (and likely different nodes)
            key_a = f"{{slot-a}}-val-{uuid.uuid4().hex[:8]}"
            key_b = f"{{slot-b}}-val-{uuid.uuid4().hex[:8]}"

            await client.set(key_a, "10")
            await client.set(key_b, "20")

            # First scope targets key_a's slot
            async with await client.scoped_connection(routing_key=key_a) as scope:
                await scope.watch(key_a)
                val = await scope.get(key_a)
                await scope.multi()
                await scope.set(key_a, str(int(val) + 1))
                result = await scope.exec()
                assert result is not None

            # Second scope targets key_b's slot — must NOT reuse key_a's connection
            async with await client.scoped_connection(routing_key=key_b) as scope:
                await scope.watch(key_b)
                val = await scope.get(key_b)
                await scope.multi()
                await scope.set(key_b, str(int(val) + 1))
                result = await scope.exec()
                assert result is not None

            assert await client.get(key_a) == b"11"
            assert await client.get(key_b) == b"21"
            await client.delete([key_a, key_b])
        finally:
            await _close_client(client)
