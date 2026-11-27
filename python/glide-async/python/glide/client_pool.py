# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Async client-instance pool — FFI-based, backed by shared Rust pool.

Same architecture as Python sync, Go, and Java pools. Uses the unified
glide_pool_create with AsyncClient type. Responses route through the
shared async pipe (same as normal GlideClient.create() clients).
"""

import asyncio
import functools
import itertools
import os
import threading
import weakref
from dataclasses import dataclass
from typing import Optional

import anyio
import sniffio
from glide_shared.config import (
    BaseClientConfiguration,
    GlideClusterClientConfiguration,
)
from glide_shared.connection_request import _create_async_connection_request
from glide_shared.exceptions import ClosingError

from .glide_client import (
    _ASYNC_FFI,
    BaseClient,
    GlideClient,
    GlideClusterClient,
    _async_pipe_lock,
    _client_registry,
)


def _release_adapter_ref(lib, adapter_ptr, create_pid: int) -> None:
    """Give back a borrowed client's adapter reference (same fork guard as a direct client)."""
    if create_pid == os.getpid():
        lib.glide_pool_release_client_ref(adapter_ptr)


@dataclass
class PoolConfig:
    """Configuration for the async client-instance pool."""

    max_size: int = 10
    min_idle: int = 1
    idle_timeout_ms: int = 300_000
    request_timeout_ms: int = 5_000
    acquire_timeout_s: float = 5.0
    abandon_timeout_ms: int = 300_000
    """Maximum inactivity time for a borrowed client before the pool reclaims it (ms).
    The timer resets on every command sent. The abandon monitor skips clients
    executing blocking commands (BLPOP, XREAD BLOCK, etc.).
    Set to 0 to disable abandon detection. Default: 5 minutes."""
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
        "_callback_ids",
        "_conn_req_bytes",
        "_pool_id",
        "_cache_lock",
        "_is_cluster",
        "_create_pid",
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
        # Custom providers are direct-client-only; pools may use the default AWS chain.
        credentials = getattr(client_config, "credentials", None)
        iam_config = getattr(credentials, "iam_config", None)
        if iam_config is not None and iam_config.credential_provider is not None:
            raise ValueError(
                "Pool clients cannot use a custom IAM credentials provider. "
                "Configure IAM without a credential_provider to use the default AWS credential chain."
            )

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
        # Request ids shared by every borrow, so a late reply to a released
        # borrow never matches a later borrow's request on the same connection.
        self._callback_ids = itertools.count(1)
        self._cache_lock = threading.Lock()
        self._is_cluster = isinstance(client_config, GlideClusterClientConfiguration)

        # Serialize connection request. Route through the shared helper so
        # pooled clients honour lib_name / client_info_tag exactly like direct
        # GlideClient.create() clients do (default "GlidePy", user-configured
        # lib_name, and the "(client_info_tag)" suffix).
        conn_req = _create_async_connection_request(client_config)
        self._conn_req_bytes = conn_req.SerializeToString()

        # Initialize the shared async pipe BEFORE creating pool clients.
        # This ensures ASYNC_PIPE is set so pooled AsyncClient adapters
        # write responses to the pipe (not the callback path).
        import glide.glide_client as _gc

        with _async_pipe_lock:
            _gc._detect_fork_and_reset()
            current_pid = os.getpid()

            if _gc._async_pipe_read_fd < 0:
                try:
                    r, w = os.pipe()
                    os.set_blocking(r, False)
                    if (
                        _gc._async_pipe_init_pid > 0
                        and current_pid != _gc._async_pipe_init_pid
                    ):
                        self._lib.reinit_async_pipe(w)
                    else:
                        self._lib.init_async_pipe(w)
                    _gc._async_pipe_read_fd = r
                    _gc._async_pipe_write_fd = w
                    _gc._async_pipe_init_pid = current_pid
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
            self._pool_config.abandon_timeout_ms,
            self._ffi.cast("const uint8_t*", buf),
            len(self._conn_req_bytes),
            client_type,
        )
        if pool_id < 0:
            raise RuntimeError(f"Failed to create pool: error code {pool_id}")
        self._pool_id = pool_id
        self._create_pid = os.getpid()

    def _check_same_process(self) -> None:
        if self._create_pid != os.getpid():
            raise ClosingError(
                "Cannot use a pool created before fork(). "
                "Create a new pool in the child process."
            )

    async def acquire(self, timeout: Optional[float] = None) -> int:
        """Acquire a borrow, returning its lease id for borrow-scoped calls.

        Each borrow gets a new lease id, even of the same connection.

        Uses glide_pool_try_acquire (non-blocking) instead of
        glide_pool_acquire_blocking to avoid blocking thread pool threads.
        Blocking threads prevents the event loop from draining the async pipe,
        which deadlocks the pool's release state reset.
        """
        self._check_same_process()
        if self._closed:
            raise RuntimeError("Pool is closed")
        timeout = (
            timeout if timeout is not None else self._pool_config.acquire_timeout_s
        )

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
            await anyio.sleep(min(backoff, remaining))
            backoff = min(backoff * 2, 0.05)  # Cap at 50ms

    def _release_borrow(self, client_id: int) -> None:
        if self._create_pid == os.getpid():
            self.release(client_id)

    def release(self, client_id: int) -> None:
        """Release a borrow back to the pool by its lease id.

        Its client raises ClosingError from then on. Releasing a borrow that
        has already ended does nothing.
        """
        self._check_same_process()
        with self._cache_lock:
            client = self._client_cache.pop(client_id, None)
            if client is not None:
                _detach(client)
            self._lib.glide_pool_release(self._pool_id, client_id)

    def borrow(self, timeout: Optional[float] = None):
        """Async context manager."""
        return _AsyncBorrowContext(self, timeout)

    def _get_or_create_client(self, client_id: int) -> BaseClient:
        """Get/create the client of a current borrow, with pipe registration."""
        cached = self._client_cache.get(client_id)
        if cached is not None:
            _bind_to_running_loop(cached)
            return cached

        with self._cache_lock:
            cached = self._client_cache.get(client_id)
            if cached is not None:
                _bind_to_running_loop(cached)
                return cached

            pooled_id = self._ffi.new("uint64_t *")
            adapter_ptr = self._lib.glide_pool_lease_client(
                self._pool_id, client_id, pooled_id
            )
            if adapter_ptr == 0:
                raise ClosingError("Pooled client was returned to the pool")
            connection_id = pooled_id[0]

            ClientClass = GlideClusterClient if self._is_cluster else GlideClient
            client = object.__new__(ClientClass)
            # The borrow holds its own adapter reference; give it back once the
            # client is gone, so a call racing a release never sees a freed adapter.
            finalizer = weakref.finalize(
                client, _release_adapter_ref, self._lib, adapter_ptr, os.getpid()
            )
            # typeshed gives finalize empty __slots__; atexit is settable at runtime.
            finalizer.atexit = False  # type: ignore[misc]
            # close() on a borrowed client ends the borrow rather than closing
            # the connection the pool lends on.
            client._pool_release = functools.partial(self._release_borrow, client_id)
            client.config = self._client_config
            client._is_closed = False
            client._ffi = self._ffi
            client._lib = self._lib
            client._pending_futures = {}
            client._pending_push_notifications = []
            client._pubsub_futures = []
            client._pubsub_lock = threading.Lock()
            client._pubsub_callback_ref = None
            client._callback_id_gen = self._callback_ids
            client._lock = threading.Lock()
            client._core_client = self._ffi.cast("void*", adapter_ptr)
            client._conn_req_bytes = self._conn_req_bytes
            client._create_pid = os.getpid()

            # The pool created this client with pipe_client_id = its pooled id
            # (set in create_client_internal via the pre-assigned ID).
            # Register so the pipe reader routes responses here.
            client._pipe_client_id = connection_id
            client._loop = None
            client._trio_token = None
            client._is_asyncio = True

            _client_registry[connection_id] = client
            _bind_to_running_loop(client)

            self._client_cache[client_id] = client
            return client

    @property
    def is_closed(self) -> bool:
        return self._closed

    @property
    def pool_id(self) -> int:
        return self._pool_id

    def metrics(self) -> dict:
        """Get pool metrics: idle, active, total counts.

        Zeros when the pool was destroyed.
        """
        m = self._lib.glide_pool_metrics(self._pool_id)
        return {"idle": m.idle, "active": m.active, "total": m.total}

    @property
    def idle_count(self) -> int:
        return self.metrics()["idle"]

    @property
    def active_count(self) -> int:
        return self.metrics()["active"]

    @property
    def total_count(self) -> int:
        return self.metrics()["total"]

    def close(self):
        if not self._closed:
            self._closed = True
            with self._cache_lock:
                for client in self._client_cache.values():
                    _detach(client)
                self._client_cache.clear()
            self._lib.glide_pool_destroy(self._pool_id)

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


def _detach(client: BaseClient) -> None:
    """End a borrow's client: later calls raise ClosingError, and requests it
    still has in flight fail rather than wait on a connection lent on."""
    with client._lock:
        if client._is_closed:
            return
        client._is_closed = True
        for fut in client._pending_futures.values():
            if not fut.done():
                fut.set_exception(
                    ClosingError("Pooled client was returned to the pool")
                )
        client._pending_futures.clear()
    if _client_registry.get(client._pipe_client_id) is client:
        _client_registry.pop(client._pipe_client_id, None)


def _bind_to_running_loop(client: BaseClient) -> None:
    """Bind a pooled client to the running event loop, registering the pipe reader.

    A cached client can outlive the run it was bound to, so re-bind on a new run.
    """
    try:
        is_asyncio = sniffio.current_async_library() == "asyncio"
    except sniffio.AsyncLibraryNotFoundError:
        is_asyncio = True
    if is_asyncio:
        loop = asyncio.get_running_loop()
        if client._is_asyncio and client._loop is loop:
            return
        client._loop = loop
    else:
        import trio

        if (
            not client._is_asyncio
            and client._trio_token is trio.lowlevel.current_trio_token()
        ):
            return
        client._loop = None
    client._is_asyncio = is_asyncio
    client._setup_pipe()


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
        if self._pool._create_pid != os.getpid():
            return False
        self._pool.release(self._client_id)
        # Yield to event loop so the async release (state reset) can
        # complete via the pipe reader before the next acquire attempt.
        await anyio.sleep(0)
        return False
