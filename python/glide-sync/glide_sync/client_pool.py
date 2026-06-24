# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Client-instance pool for the synchronous GLIDE client (Feature 1).

Backed by the shared Rust pool in glide-core via FFI. The Rust pool owns
client lifecycle (creation, LIFO reuse, bounded size). This Python
class provides the acquire-with-timeout retry loop and maps client_id handles
to usable GlideClient wrappers for command dispatch.

Usage:
    from glide_sync import GlideClient
    from glide_sync.client_pool import ClientPool, PoolConfig
    from glide_shared.config import GlideClientConfiguration, NodeAddress

    config = GlideClientConfiguration([NodeAddress("localhost", 6379)])
    pool = ClientPool(config, PoolConfig(max_size=10, min_idle=2))

    # Borrow a client, use it, return it automatically
    with pool.borrow() as client:
        client.set("key", "value")
        result = client.get("key")

    pool.close()
"""

import threading
import time
from dataclasses import dataclass
from typing import Optional

from glide_shared._glide_ffi import _GlideFFI
from glide_shared.config import BaseClientConfiguration, GlideClusterClientConfiguration

from .glide_client import BaseClient, GlideClient


@dataclass
class PoolConfig:
    """Configuration for the client-instance pool."""

    max_size: int = 10
    """Maximum number of clients in the pool."""

    min_idle: int = 1
    """Minimum idle clients to pre-warm at creation."""

    idle_timeout_ms: int = 300_000
    """Evict idle clients after this duration (ms). Default: 5 minutes."""

    request_timeout_ms: int = 5_000
    """Request timeout for commands (ms)."""

    acquire_timeout_s: float = 5.0
    """Maximum time to wait when pool is exhausted (seconds)."""


class ClientPool:
    """
    Client-instance pool backed by the Rust core.

    The Rust pool owns client lifecycle (creation, bounded size).
    This Python class provides:
    - acquire-with-timeout retry loop (exponential backoff)
    - Cached GlideClient wrappers (one per client_id, reused across borrows)
    - Zero-overhead borrow() context manager
    """

    __slots__ = (
        "_ffi",
        "_lib",
        "_client_config",
        "_pool_config",
        "_closed",
        "_client_cache",
        "_conn_req_bytes",
        "_pool_id",
        "_cache_lock",
    )

    def __init__(
        self,
        client_config: BaseClientConfiguration,
        pool_config: Optional[PoolConfig] = None,
    ):
        ffi_instance = _GlideFFI()
        self._ffi = ffi_instance.ffi
        self._lib = ffi_instance.lib
        self._client_config = client_config
        self._pool_config = pool_config or PoolConfig()
        self._closed = False
        self._client_cache: dict = {}
        # Lock for _client_cache under free-threading (concurrent get_or_create_client)
        self._cache_lock = threading.Lock()

        # Serialize the connection request protobuf
        is_cluster = isinstance(client_config, GlideClusterClientConfiguration)
        conn_req = client_config._create_a_protobuf_conn_request(
            cluster_mode=is_cluster
        )
        self._conn_req_bytes = conn_req.SerializeToString()

        # Create the Rust pool via FFI
        pool_id = self._lib.glide_pool_create(
            self._pool_config.max_size,
            self._pool_config.min_idle,
            self._pool_config.idle_timeout_ms,
            self._pool_config.request_timeout_ms,
            self._conn_req_bytes,
            len(self._conn_req_bytes),
        )

        if pool_id == -1:
            raise ValueError("Invalid pool configuration")
        if pool_id < 0:
            raise RuntimeError(f"Failed to create pool (error code: {pool_id})")

        self._pool_id = pool_id

    @property
    def pool_id(self) -> int:
        return self._pool_id

    @property
    def is_closed(self) -> bool:
        return self._closed

    def acquire(self, timeout: Optional[float] = None) -> int:
        """
        Acquire a client_id from the Rust pool.

        Retries with exponential backoff until a client is available or timeout.

        Returns:
            client_id (positive integer) for use with borrow() or get_client().

        Raises:
            TimeoutError: If no client available within timeout.
            RuntimeError: If pool is closed or invalid.
        """
        if self._closed:
            raise RuntimeError("Pool is closed")

        timeout = (
            timeout if timeout is not None else self._pool_config.acquire_timeout_s
        )
        deadline = time.monotonic() + timeout
        backoff_ms = 1.0

        while time.monotonic() < deadline:
            client_id = self._lib.glide_pool_try_acquire(self._pool_id)

            if client_id >= 0:
                return client_id
            if client_id == -2:
                raise RuntimeError("Invalid pool_id — pool was destroyed")

            remaining = deadline - time.monotonic()
            sleep_s = min(backoff_ms / 1000.0, max(remaining, 0))
            if sleep_s <= 0:
                break
            time.sleep(sleep_s)
            backoff_ms = min(backoff_ms * 2, 50.0)

        raise TimeoutError(
            f"Pool exhausted: could not acquire client within {timeout}s"
        )

    def release(self, client_id: int) -> None:
        """Release a borrowed client back to the pool."""
        self._lib.glide_pool_release(self._pool_id, client_id)

    def borrow(self, timeout: Optional[float] = None):
        """
        Returns a context manager that acquires a client from the Rust pool.

        The returned client dispatches commands through the native ClientAdapter
        managed by the Rust pool. On exit, the client is released back to the pool.

        Usage:
            with pool.borrow() as client:
                client.set("key", "value")
                result = client.get("key")
        """
        return _BorrowContext(self, timeout)

    def _get_or_create_client(self, client_id: int) -> BaseClient:
        """
        Get a usable GlideClient wrapper for the given client_id.

        On first borrow of a client_id, creates a thin wrapper pointing to the
        Rust-managed ClientAdapter. Subsequent borrows of the same client_id
        reuse the cached wrapper (zero allocation).
        """
        cached = self._client_cache.get(client_id)
        if cached is not None:
            return cached

        with self._cache_lock:
            # Double-check after acquiring lock
            cached = self._client_cache.get(client_id)
            if cached is not None:
                return cached

            # Get the native ClientAdapter pointer from the Rust pool
            adapter_ptr = self._lib.glide_pool_get_client_ptr(client_id)
            if adapter_ptr == 0:
                raise RuntimeError(
                    f"Pool client_id {client_id} has no associated ClientAdapter"
                )

            # Create a GlideClient shell pointing to the pooled adapter.
            cast_ptr = self._ffi.cast("void*", adapter_ptr)

            client = GlideClient.__new__(GlideClient)  # type: ignore[type-abstract]
            client._ffi = self._ffi
            client._lib = self._lib
            client._config = self._client_config
            client._is_closed = False
            client._pubsub_queue = []
            client._pubsub_lock = threading.Lock()
            client._pubsub_condition = threading.Condition(client._pubsub_lock)
            client._pubsub_callback_ref = None
            client._client_lock = threading.Lock()
            client._core_client = cast_ptr

            self._client_cache[client_id] = client
            return client

    def metrics(self) -> dict:
        """Get pool metrics: idle, active, total counts."""
        idle = self._ffi.new("uint32_t*")
        active = self._ffi.new("uint32_t*")
        total = self._ffi.new("uint32_t*")
        result = self._lib.glide_pool_metrics(self._pool_id, idle, active, total)
        if result != 0:
            return {"idle": 0, "active": 0, "total": 0}
        return {"idle": idle[0], "active": active[0], "total": total[0]}

    def close(self) -> None:
        """Destroy the pool. All idle clients are closed."""
        if not self._closed:
            self._closed = True
            self._lib.glide_pool_destroy(self._pool_id)
            self._client_cache.clear()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def __del__(self):
        if not self._closed:
            try:
                self.close()
            except Exception:
                pass


class _BorrowContext:
    """Fast context manager for pool borrow/release (avoids generator overhead)."""

    __slots__ = ("_pool", "_timeout", "_client_id")

    def __init__(self, pool: ClientPool, timeout):
        self._pool = pool
        self._timeout = timeout
        self._client_id = -1

    def __enter__(self):
        self._client_id = self._pool.acquire(self._timeout)
        return self._pool._get_or_create_client(self._client_id)

    def __exit__(self, *_):
        self._pool.release(self._client_id)
        return False
