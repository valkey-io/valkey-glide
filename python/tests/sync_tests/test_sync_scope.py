# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Integration tests for Isolated Execution Scopes (Python sync).
Mirrors Java IsolatedScopeIntegrationTest for cross-language parity.

Requires a running Valkey server (standalone).
"""

import threading
import uuid

import pytest
from glide_sync import GlideClient, GlideClientConfiguration

from tests.utils.utils import get_standalone_address as _get_standalone_address


@pytest.fixture
def client():
    """Create a connected GlideClient."""
    config = GlideClientConfiguration(
        addresses=[_get_standalone_address()],
        request_timeout=5000,
    )
    c = GlideClient.create(config)
    yield c
    c.close()


class TestIsolatedScopeBasic:
    """Basic scope lifecycle tests."""

    def test_scope_acquire_ping_release(self, client):
        """Acquire a scope, execute PING, release it."""
        with client.scoped_connection() as scope:
            assert not scope.is_released
            result = scope.ping()
            assert result == "PONG" or result == "OK"
        assert scope.is_released

    def test_scope_get_set(self, client):
        """SET and GET on a scoped connection."""
        key = f"scope-test-{uuid.uuid4().hex[:8]}"
        with client.scoped_connection() as scope:
            scope.set(key, "hello")
            val = scope.get(key)
            assert val == "hello"
        # Cleanup via main client
        client.delete([key])

    def test_scope_returns_none_for_missing_key(self, client):
        """GET on non-existent key returns None."""
        with client.scoped_connection() as scope:
            val = scope.get(f"nonexistent-{uuid.uuid4().hex}")
            assert val is None

    def test_scope_close_is_idempotent(self, client):
        """Calling close() multiple times is safe."""
        scope = client.scoped_connection()
        scope.ping()
        scope.close()
        scope.close()  # Should not raise
        assert scope.is_released

    def test_scope_raises_after_release(self, client):
        """Commands on a released scope raise RuntimeError."""
        scope = client.scoped_connection()
        scope.close()
        with pytest.raises(RuntimeError, match="released"):
            scope.ping()


class TestIsolatedScopeOCC:
    """WATCH/MULTI/EXEC (Optimistic Concurrency Control) tests."""

    def test_watch_multi_exec_success(self, client):
        """Successful WATCH/MULTI/EXEC transaction."""
        key = f"occ-{uuid.uuid4().hex[:8]}"
        client.set(key, "0")

        with client.scoped_connection() as scope:
            scope.watch(key)
            val = scope.get(key)
            assert val == "0"
            scope.multi()
            scope.set(key, "1")
            result = scope.exec()
            # EXEC returns the array of results for queued commands
            assert result is not None

        # Verify the value was set
        assert client.get(key) == b"1"
        client.delete([key])

    def test_watch_conflict_aborts_exec(self, client):
        """EXEC aborts when a watched key is modified externally."""
        key = f"occ-conflict-{uuid.uuid4().hex[:8]}"
        client.set(key, "original")

        with client.scoped_connection() as scope:
            scope.watch(key)
            scope.get(key)

            # Modify the key externally (via the main multiplexed client)
            client.set(key, "modified-externally")

            # Now try to commit — should fail
            scope.multi()
            scope.set(key, "from-scope")
            result = scope.exec()
            # EXEC returns None/null when transaction is aborted
            assert result is None or result == "None"

        # Verify external modification persists
        assert client.get(key) == b"modified-externally"
        client.delete([key])

    def test_occ_concurrent_increment(self, client):
        """
        Multiple threads increment a counter using WATCH/MULTI/EXEC.
        OCC ensures the final value is exactly correct.
        """
        key = f"occ-counter-{uuid.uuid4().hex[:8]}"
        client.set(key, "0")

        num_threads = 4
        increments_per_thread = 10
        expected_final = num_threads * increments_per_thread
        errors = []

        def worker():
            try:
                for _ in range(increments_per_thread):
                    committed = False
                    while not committed:
                        with client.scoped_connection() as scope:
                            scope.watch(key)
                            val = scope.get(key)
                            current = int(val) if val else 0
                            scope.multi()
                            scope.set(key, str(current + 1))
                            result = scope.exec()
                            if result is not None and result != "None":
                                committed = True
            except Exception as e:
                errors.append(e)

        threads = [threading.Thread(target=worker) for _ in range(num_threads)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=30)

        assert not errors, f"Worker errors: {errors}"
        final_val = client.get(key)
        assert int(final_val) == expected_final
        client.delete([key])


class TestIsolatedScopePoolReuse:
    """Scope pool connection reuse tests."""

    def test_lifo_reuse(self, client):
        """Released scopes are reusable (connection returned to pool)."""
        scope1 = client.scoped_connection()
        scope1.ping()
        scope1.close()

        import time

        time.sleep(0.1)

        # After releasing, we should be able to acquire again
        scope2 = client.scoped_connection()
        result = scope2.ping()
        assert result == "PONG"
        scope2.close()

    def test_multiple_concurrent_scopes(self, client):
        """Multiple scopes can be held simultaneously."""
        scope1 = client.scoped_connection()
        scope2 = client.scoped_connection()

        assert scope1.scope_id != scope2.scope_id

        scope1.ping()
        scope2.ping()

        scope1.close()
        scope2.close()
