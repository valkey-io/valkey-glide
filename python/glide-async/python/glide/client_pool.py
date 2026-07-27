# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Async client-instance pool — FFI-based, backed by shared Rust pool.

Same architecture as Python sync, Go, and Java pools. Uses the unified
glide_pool_create with AsyncClient type. Responses route through the
shared async pipe (same as normal GlideClient.create() clients).
"""

import asyncio
import os
import threading
from dataclasses import dataclass
from typing import Optional

from glide_shared.config import (
    BaseClientConfiguration,
    GlideClusterClientConfiguration,
)

from .glide_client import (
    _ASYNC_FFI,
    BaseClient,
    GlideClient,
    GlideClusterClient,
    _async_pipe_lock,
    _client_registry,
)


@dataclass
class PoolConfig:
    """Configuration for the async client-instance pool."""

    max_size: int = 10
    min_idle: int = 1
    idle_timeout_ms: int = 300_000
    request_timeout_ms: int = 5_000
    acquire_timeout_s: float = 5.0
    test_on_borrow: bool = False


class AsyncClientPool:
    """FFI-based async client pool. Same Rust pool as all other languages."""

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
        "_is_cluster",
    )

    @classmethod
    async def create(
        cls,
        client_config: BaseClientConfiguration,
        pool_config: Optional[PoolConfig] = None,
    ) -> "AsyncClientPool":
        """Create a pool and validate connectivity with a probe client.

        Creates the pool infrastructure, then attempts to connect a single
        probe client using the same config. If the probe fails, the pool is
        destroyed and the actual connection error is propagated (not a timeout).

        Args:
            client_config: Client configuration (standalone or cluster).
            pool_config: Optional pool sizing/timeout configuration.

        Returns:
            A ready-to-use AsyncClientPool instance.

        Raises:
            The same exception that GlideClient.create() / GlideClusterClient.create()
            would raise on connection failure.
        """
        pool = cls(client_config, pool_config)
        try:
            ClientClass = GlideClusterClient if pool._is_cluster else GlideClient
            probe = await ClientClass.create(client_config)
            await probe.close()
        except Exception:
            pool.close()
            raise
        return pool

    def __init__(
        self,
        client_config: BaseClientConfiguration,
        pool_config: Optional[PoolConfig] = None,
    ):
        # Reject pubsub subscriptions — pool state reset doesn't UNSUBSCRIBE
        if (
            hasattr(client_config, "pubsub_subscriptions")
            and client_config.pubsub_subscriptions
        ):
            raise ValueError(
                "Pool clients cannot have pubsub subscriptions configured. "
                "Use the main client's pubsub API for subscriptions."
            )

        ffi_instance = _ASYNC_FFI
        self._ffi = ffi_instance.ffi
        self._lib = ffi_instance.lib
        self._client_config = client_config
        self._pool_config = pool_config or PoolConfig()
        self._closed = False
        self._client_cache: dict = {}
        self._cache_lock = threading.Lock()
        self._is_cluster = isinstance(client_config, GlideClusterClientConfiguration)

        # Serialize connection request
        conn_req = client_config._create_a_protobuf_conn_request(
            cluster_mode=self._is_cluster
        )
        conn_req.lib_name = "GlidePyAsync"
        self._conn_req_bytes = conn_req.SerializeToString()

        # Initialize the shared async pipe BEFORE creating pool clients.
        # This ensures ASYNC_PIPE is set so pooled AsyncClient adapters
        # write responses to the pipe (not the callback path).
        import glide.glide_client as _gc

        with _async_pipe_lock:
            if _gc._async_pipe_read_fd < 0:
                try:
                    r, w = os.pipe()
                    os.set_blocking(r, False)
                    self._lib.init_async_pipe(w)
                    _gc._async_pipe_read_fd = r
                except OSError:
                    pass

        # Create pool with AsyncClient type (no-op callbacks — pipe handles responses)
        client_type = self._ffi.new("ClientType*")
        client_type._type = 0  # AsyncClient
        client_type.async_client.success_callback = self._lib.noop_success_callback
        client_type.async_client.failure_callback = self._lib.noop_failure_callback
        client_type.async_client.allow_stack_response = False

        buf = self._ffi.from_buffer(self._conn_req_bytes)
        pool_id = self._lib.glide_pool_create(
            self._pool_config.max_size,
            self._pool_config.min_idle,
            self._pool_config.idle_timeout_ms,
            self._pool_config.request_timeout_ms,
            self._ffi.cast("const uint8_t*", buf),
            len(self._conn_req_bytes),
            client_type,
        )
        if pool_id < 0:
            raise RuntimeError(f"Failed to create pool: error code {pool_id}")
        self._pool_id = pool_id

    async def acquire(self, timeout: Optional[float] = None) -> int:
        """Acquire a client_id using non-blocking try_acquire with async backoff.

        Uses glide_pool_try_acquire (non-blocking) instead of
        glide_pool_acquire_blocking to avoid blocking thread pool threads.
        Blocking threads prevents the event loop from draining the async pipe,
        which deadlocks the pool's release state-reset (DISCARD + SELECT).
        """
        if self._closed:
            raise RuntimeError("Pool is closed")
        timeout = timeout or self._pool_config.acquire_timeout_s

        import time

        deadline = time.monotonic() + timeout
        backoff = 0.005  # Start at 5ms

        while True:
            client_id = self._lib.glide_pool_try_acquire(self._pool_id)
            if client_id >= 0:
                return client_id
            if client_id == -2:
                raise RuntimeError("Invalid pool_id — pool was destroyed")

            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError(
                    f"Pool exhausted: could not acquire client within {timeout}s"
                )

            # Yield to event loop — allows pipe reader to process release responses
            await asyncio.sleep(min(backoff, remaining))
            backoff = min(backoff * 2, 0.05)  # Cap at 50ms

    def release(self, client_id: int) -> None:
        """Release a borrowed client back to the pool."""
        self._lib.glide_pool_release(self._pool_id, client_id)

    def borrow(self, timeout: Optional[float] = None):
        """Async context manager."""
        return _AsyncBorrowContext(self, timeout)

    def _get_or_create_client(self, client_id: int) -> BaseClient:
        """Get/create a client wrapper with pipe registration."""
        cached = self._client_cache.get(client_id)
        if cached is not None:
            return cached

        with self._cache_lock:
            cached = self._client_cache.get(client_id)
            if cached is not None:
                return cached

            adapter_ptr = self._lib.glide_pool_get_client_ptr(client_id)
            if adapter_ptr == 0:
                raise RuntimeError(
                    f"Pool client_id {client_id} has no associated ClientAdapter"
                )

            ClientClass = GlideClusterClient if self._is_cluster else GlideClient
            client = object.__new__(ClientClass)
            client.config = self._client_config
            client._is_closed = False
            client._ffi = self._ffi
            client._lib = self._lib
            client._pending_futures = {}
            client._pending_push_notifications = []
            client._pubsub_futures = []
            client._pubsub_lock = threading.Lock()
            client._pubsub_callback_ref = None
            client._callback_id_gen = __import__("itertools").count(1)
            client._lock = threading.Lock()
            client._is_asyncio = True
            client._core_client = self._ffi.cast("void*", adapter_ptr)
            client._conn_req_bytes = self._conn_req_bytes

            # The pool created this client with pipe_client_id = client_id
            # (set in create_client_internal via the pre-assigned ID).
            # Register so the pipe reader routes responses here.
            client._pipe_client_id = client_id
            try:
                client._loop = asyncio.get_running_loop()
            except RuntimeError:
                client._loop = None

            _client_registry[client_id] = client
            client._setup_pipe()

            self._client_cache[client_id] = client
            return client

    @property
    def is_closed(self) -> bool:
        return self._closed

    @property
    def pool_id(self) -> int:
        return self._pool_id

    def metrics(self) -> dict:
        """Get pool metrics: idle, active, total counts."""
        idle = self._ffi.new("uint32_t*")
        active = self._ffi.new("uint32_t*")
        total = self._ffi.new("uint32_t*")
        result = self._lib.glide_pool_metrics(self._pool_id, idle, active, total)
        if result != 0:
            return {"idle": 0, "active": 0, "total": 0}
        return {"idle": idle[0], "active": active[0], "total": total[0]}

    @property
    def idle_count(self) -> int:
        idle = self._ffi.new("uint32_t*")
        self._lib.glide_pool_metrics(
            self._pool_id, idle, self._ffi.NULL, self._ffi.NULL
        )
        return idle[0]

    @property
    def active_count(self) -> int:
        active = self._ffi.new("uint32_t*")
        self._lib.glide_pool_metrics(
            self._pool_id, self._ffi.NULL, active, self._ffi.NULL
        )
        return active[0]

    @property
    def total_count(self) -> int:
        total = self._ffi.new("uint32_t*")
        self._lib.glide_pool_metrics(
            self._pool_id, self._ffi.NULL, self._ffi.NULL, total
        )
        return total[0]

    def close(self):
        if not self._closed:
            self._closed = True
            for cid in list(self._client_cache.keys()):
                _client_registry.pop(cid, None)
            self._lib.glide_pool_destroy(self._pool_id)
            self._client_cache.clear()

    async def aclose(self):
        self.close()

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_):
        self.close()
        return False

    def __del__(self):
        # Do NOT call close() from __del__. The FFI glide_pool_destroy call
        # can race with active callbacks from the Rust side, causing segfaults.
        # Users must call close() explicitly or use async context manager.
        pass


class _AsyncBorrowContext:
    __slots__ = ("_pool", "_timeout", "_client_id")

    def __init__(self, pool: AsyncClientPool, timeout):
        self._pool = pool
        self._timeout = timeout
        self._client_id = -1

    async def __aenter__(self):
        self._client_id = await self._pool.acquire(self._timeout)
        return self._pool._get_or_create_client(self._client_id)

    async def __aexit__(self, *_):
        self._pool.release(self._client_id)
        # Yield to event loop so the async release (DISCARD + SELECT) can
        # complete via the pipe reader before the next acquire attempt.
        await asyncio.sleep(0)
        return False
