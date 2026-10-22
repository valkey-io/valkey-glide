# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import collections
import os
import sys
import threading
import warnings
import weakref
from dataclasses import dataclass
from types import TracebackType
from typing import TYPE_CHECKING, Any, Callable, List, Optional, Tuple, Union

if TYPE_CHECKING:
    from .isolated_scope import IsolatedScope

from glide_shared._fast_response import parse_response as _fast_parse_response
from glide_shared.commands.command_args import ObjectType
from glide_shared.commands.core_options import PubSubMsg
from glide_shared.config import (
    BaseClientConfiguration,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    RdmaSetChecksum,
    _is_async_callable,
)
from glide_shared.connection_request import _create_sync_connection_request
from glide_shared.constants import OK, TEncodable, TResult
from glide_shared.exceptions import (
    ClosingError,
    ConfigurationError,
    RdmaError,
    RequestError,
    get_request_error_class,
)
from glide_shared.ffi_helpers import (
    _is_credential_provider_executing,
    create_credential_provider_callback,
    handle_command_result,
)
from glide_shared.opentelemetry import _create_batch_span, _create_command_span
from glide_shared.protobuf.command_request_pb2 import RequestType
from glide_shared.routes import (
    AllNodes,
    AllPrimaries,
    ByAddressRoute,
    RandomNode,
    Route,
    SlotIdRoute,
    SlotKeyRoute,
    SlotType,
    build_protobuf_route,
)
from glide_sync._ffi_instance import _SYNC_FFI

from .logger import Level, Logger
from .sync_commands.cluster_commands import ClusterCommands
from .sync_commands.cluster_scan_cursor import ClusterScanCursor
from .sync_commands.core import CoreCommands
from .sync_commands.standalone_commands import StandaloneCommands

if sys.version_info >= (3, 11):
    from typing import Self
else:
    from typing_extensions import Self

# Pre-allocated null-terminated span name for the EVALSHA (`_execute_script`)
# path. Kept at module scope so we do not re-allocate a `char[]` per sampled
# call. `_SYNC_FFI.ffi` is a process-wide singleton so this buffer is safe to
# share across clients.
_EVALSHA_SPAN_NAME = _SYNC_FFI.ffi.new("char[]", b"EVALSHA")

ENCODING = "utf-8"
# Beginning of the message of a transfer cancelled by closing the client.
# Must match glide-core's `rdma::protocol::CANCELLED`.
_RDMA_CANCELLED = "RDMA transfer cancelled"


# Enum values must match the Rust definition
class FFIClientTypeEnum:
    Async = 0
    Sync = 1


def _slot_for_key(key: bytes) -> int:
    """Compute the Redis cluster hash slot for a key (CRC16 mod 16384).

    Handles hash tags: if the key contains {...}, only the content between the
    first { and first } after it is hashed.
    """
    start = key.find(b"{")
    if start != -1:
        end = key.find(b"}", start + 1)
        if end != -1 and end != start + 1:
            key = key[start + 1 : end]

    crc = 0
    for b in key:
        crc ^= b << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = (crc << 1) ^ 0x1021
            else:
                crc <<= 1
            crc &= 0xFFFF
    return crc % 16384


@dataclass(frozen=True)
class RdmaReadReceipt:
    """
    What the server reported about a completed read.

    Attributes:
        bytes_written (int): Bytes the server moved into the window.
        checksum (Optional[int]): CRC-32c the server computed over those bytes.
            The landed bytes have already been verified against it.
    """

    bytes_written: int
    checksum: Optional[int] = None


@dataclass(frozen=True)
class RdmaWindow:
    """
    The span of a registered region one transfer uses.

    Attributes:
        region (RdmaRegion): The region the window belongs to.
        offset (int): Where the window starts within the region.
        length (int): How many bytes it covers.
    """

    region: "RdmaRegion"
    offset: int
    length: int

    def memoryview(self) -> memoryview:
        """
        A view of just this window for reading what a transfer landed or
        staging what one will send.

        Raises:
            RdmaError: If the region has been closed.
        """
        return self.region.memoryview()[self.offset : self.offset + self.length]


def _memory_span(memory: Any) -> Tuple[int, int]:
    """The address range ``[start, end)`` that `memory`'s bytes occupy."""
    ffi = _SYNC_FFI.ffi
    view = memoryview(memory)
    start = int(ffi.cast("uintptr_t", ffi.from_buffer(view)))
    return start, start + view.nbytes


class _RetainedRdmaMemory:
    """
    Caller memory the fabric still holds after its region was closed.

    That happens when a transfer ended before the server was known to be done
    with the memory on a provider where a write could still land (tcp), or when
    the region failed to close.
    The memory is kept referenced here so Python cannot free it until the
    fabric reports it released. For a region that failed to close, that is never.
    """

    def __init__(self):
        self._lock = threading.Lock()
        # (lib, hold, memory, view, span) for each region whose memory is still
        # held, where span is the memory's address range.
        self._held: List[Tuple[Any, Any, Any, Any, Tuple[int, int]]] = []
        # Entries kept by a region's finalizer, not yet moved into `_held`. A
        # finalizer can run during any allocation, including one made while this
        # thread holds `_lock`, so it must not take the lock. Appending to a
        # deque is atomic, and every method below that reads `_held` moves these
        # in first, under the lock.
        self._pending: (
            "collections.deque[Tuple[Any, Any, Any, Any, Tuple[int, int]]]"
        ) = collections.deque()

    def keep(self, lib, hold, memory, view, span: Tuple[int, int]) -> None:
        with self._lock:
            self._held.append((lib, hold, memory, view, span))

    def keep_from_finalizer(
        self, lib, hold, memory, view, span: Tuple[int, int]
    ) -> None:
        """:meth:`keep`, without taking the lock; see `_pending`."""
        self._pending.append((lib, hold, memory, view, span))

    def _take_pending_locked(self) -> None:
        """Move what finalizers kept into `_held`. Call with `_lock` held."""
        while True:
            try:
                self._held.append(self._pending.popleft())
            except IndexError:
                return

    def release_what_is_free(self) -> None:
        """Drop the references to memory the fabric has let go of."""
        with self._lock:
            self._take_pending_locked()
            still_held = []
            for entry in self._held:
                lib, hold = entry[0], entry[1]
                if lib.rdma_memory_released(hold):
                    lib.free_rdma_memory_hold(hold)
                else:
                    still_held.append(entry)
            self._held = still_held

    def holds(self, memory: Any) -> bool:
        """Whether `memory` is being kept here."""
        with self._lock:
            self._take_pending_locked()
            return any(entry[2] is memory for entry in self._held)

    def overlaps(self, span: Tuple[int, int]) -> bool:
        """
        Whether any byte of the address range `span` is in memory kept here.

        Compares addresses, not objects, so a different view of a kept buffer,
        or a slice of one, counts too.
        """
        self.release_what_is_free()
        start, end = span
        with self._lock:
            self._take_pending_locked()
            return any(
                start < kept_end and kept_start < end
                for (kept_start, kept_end) in (entry[4] for entry in self._held)
            )

    def released(self, hold) -> bool:
        """Whether the fabric has let go of the memory `hold` kept."""
        self.release_what_is_free()
        with self._lock:
            self._take_pending_locked()
            return not any(entry[1] is hold for entry in self._held)


_RETAINED_RDMA_MEMORY = _RetainedRdmaMemory()


def _free_rdma_region(lib, ffi, region, memory, view, span: Tuple[int, int]):
    """
    Free `region` and return the hold the fabric hands back, or None.

    A hold means the fabric still keeps the memory, so `memory` and `view` go to
    ``_RETAINED_RDMA_MEMORY`` to keep Python from freeing it until the fabric
    lets go.
    """
    hold = lib.free_rdma_region(region)
    if hold == ffi.NULL:
        hold = None
    else:
        _RETAINED_RDMA_MEMORY.keep(lib, hold, memory, view, span)
    # Closing this region may have been what let the fabric go of others'.
    _RETAINED_RDMA_MEMORY.release_what_is_free()
    return hold


def _finalize_rdma_region(lib, ffi, region, memory, view, span) -> None:
    """
    Free a region that was garbage-collected without being closed.

    Unlike :func:`_free_rdma_region`, this takes no lock: a finalizer can run
    while this thread already holds one, and would then wait on itself.
    """
    warnings.warn(
        "an RdmaRegion was garbage-collected without being closed; close it, "
        "or use it in a with block, to release its pinned pages promptly",
        ResourceWarning,
        stacklevel=2,
    )
    hold = lib.free_rdma_region(region)
    if hold != ffi.NULL:
        _RETAINED_RDMA_MEMORY.keep_from_finalizer(lib, hold, memory, view, span)


class RdmaRegion:
    """
    Memory registered with the server for RDMA transfers.

    The memory is the caller's: this holds a reference to it and registers it
    with the client's fabric, but never copies or owns it. Register one
    large region at start-up and address windows of it per transfer rather than
    registering per operation.

    The pages stay pinned until :meth:`close` is called, and the buffer must not
    be resized or freed before then. The server writes into it at times the
    caller does not control, so a transfer must not overlap any other use of the
    same window.

    Only one transfer may use a region at a time, even when each transfer uses
    a different window. Threads that transfer concurrently each need their own
    region. A transfer that finds the region already in use by another raises
    ``RdmaError``, and so does :meth:`close` while a transfer is using it.

    Create one with :meth:`BaseClient.register_rdma_region`. Close it with
    :meth:`close`, or use it as a context manager. A region garbage-collected
    without being closed is freed then, with a ``ResourceWarning``; until
    then its pages stay pinned. It can be used as a context manager::

        with client.register_rdma_region(bytearray(1 << 20)) as region:
            client.rdma_set(b"key", region.window(0, 4096))

    Handling a failed transfer: some failures leave the region usable, such as
    a value too large for its window, a checksum mismatch, or a refusal from
    the server. Others close it, because the server might still be using the
    memory:

    - the connection was lost while the transfer was in flight;
    - the client was closed to cancel the transfer;
    - the server's reply was neither a result nor an error, such as the
      ``+QUEUED`` a server sends when another thread has a ``MULTI`` open on
      this client, which means the server may still run the transfer on
      ``EXEC``.

    The error's class does not say which happened. For example, a handshake
    that leaves the connection unusable raises ``ConnectionError`` but keeps
    the region open, since the transfer was never sent, and the client
    reconnects for the next one. So check :attr:`closed` after any error, and
    register the memory again if the client is still open::

        try:
            receipt = client.rdma_get(key, region.window(offset, length))
        except GlideError:
            if region.closed and not client.closed:
                region = client.register_rdma_region(slab)
            raise

    Whether to retry is left to the caller, since only the caller knows whether
    sending a value again is safe. On efa-direct the same memory can be
    registered again at once. GLIDE refuses it, raising ``RdmaError``, only
    while it still holds the memory: on the tcp provider, which is for
    development only, or after a failed deregistration. See
    :attr:`memory_released`.
    """

    def __init__(self, client: "BaseClient", memory: Any, region):
        # Both references keep the caller's allocation alive for as long as the
        # server may write to it: the object itself, and the cdata view CFFI
        # made of it.
        self._memory = memory
        self._view = client._ffi.from_buffer(memory)
        self._span = _memory_span(memory)
        self._client = client
        self._region = region
        self._capacity = client._lib.rdma_region_capacity(region)
        # Set by `close` when the fabric still kept the memory.
        self._hold = None
        # Guards `_region` and `_busy`. Held only briefly, never for the length
        # of a transfer, so closing the client can still cancel one.
        self._lock = threading.Lock()
        # Whether a transfer is using the region. The FFI frees the region in
        # `free_rdma_region` and takes it exclusively for each transfer, so a
        # second transfer or a close while this is set would corrupt memory.
        self._busy = False
        # Frees the region if this object is collected without `close`. Its
        # arguments keep the caller's memory alive until then, so the memory is
        # never freed while still registered. A transfer in flight keeps this
        # object reachable, so the finalizer never runs under one. Skipped at
        # interpreter exit, when the library may already be gone and the
        # process's teardown releases the memory anyway.
        self._finalizer = weakref.finalize(
            self,
            _finalize_rdma_region,
            client._lib,
            client._ffi,
            region,
            memory,
            self._view,
            self._span,
        )
        # A documented writable property that typeshed's stub leaves out.
        self._finalizer.atexit = False  # type: ignore[misc]

    @property
    def capacity(self) -> int:
        """How many bytes were registered."""
        return self._capacity

    @property
    def closed(self) -> bool:
        """
        Whether the region has been deregistered, by :meth:`close` or by a
        transfer that ended before the server was known to be done with the
        memory; see :class:`RdmaRegion`.
        """
        return self._region is None

    @property
    def memory_released(self) -> bool:
        """
        Whether the caller may reuse or free the registered memory.

        False while the region is open. After it closes, False while GLIDE
        still holds the memory because a write could still land in it: after
        a transfer closed the region on the tcp provider, until the client and
        all its regions are closed, or after a failed deregistration, for good.
        True otherwise, which on efa-direct is as soon as the region closes.

        Registering memory GLIDE still holds is refused, so a caller who only
        wants to register the same memory again can try that and handle the
        error rather than check this first.

        Example:
            >>> region.close()
            >>> if not region.memory_released:
            ...     slab = bytearray(len(slab))  # the old one may still be written
        """
        if not self.closed:
            return False
        return self._hold is None or _RETAINED_RDMA_MEMORY.released(self._hold)

    def window(self, offset: int = 0, length: Optional[int] = None) -> RdmaWindow:
        """
        The span of this region a transfer should use.

        Args:
            offset (int): Where the window starts. Defaults to 0.
            length (Optional[int]): How many bytes it covers. If not set, the
                rest of the region from ``offset``.

        Returns:
            RdmaWindow: The window, to pass to ``rdma_get`` or ``rdma_set``.

        Raises:
            RdmaError: If the region is closed or the window runs outside it.

        Example:
            >>> region.window()            # the whole region
            >>> region.window(4096, 1024)  # 1 KiB starting 4 KiB in
        """
        self._require_open()

        if offset < 0:
            raise RdmaError(f"offset must not be negative, got {offset}")
        if offset > self._capacity:
            raise RdmaError(
                f"offset {offset} is past the end of a region of "
                f"{self._capacity} bytes"
            )

        if length is None:
            length = self._capacity - offset
        elif length < 0:
            raise RdmaError(f"length must not be negative, got {length}")
        elif offset + length > self._capacity:
            raise RdmaError(
                f"window [{offset}, {offset + length}) runs past the end of a "
                f"region of {self._capacity} bytes"
            )

        return RdmaWindow(self, offset, length)

    def memoryview(self) -> memoryview:
        """
        A view of the registered memory as a span of bytes.

        Raises:
            RdmaError: If the region has been closed.
        """
        self._require_open()
        return memoryview(self._memory).cast("B")

    def close(self) -> None:
        """
        Deregister the memory and release the pinned pages.

        The caller's buffer is not freed. Usually it can be reused or freed once
        this returns. The exception is a region a transfer closed on the tcp
        provider, or whose deregistration failed: a write may still land in it,
        so GLIDE keeps the buffer referenced until the fabric is done with it,
        and the caller should not reuse it either. On tcp that is once the
        client and all its regions are closed; after a failed deregistration,
        which is logged as an error, it is never. :attr:`memory_released` says
        which. Closing twice is a no-op, so this is safe in a ``finally``.

        This is not a way to cancel a transfer. To cancel one in flight, close
        the client from another thread: the transfer then closes its region on
        the way out, so closing it again is a no-op.

        Raises:
            RdmaError: If a transfer is using the region. Closing it then would
                free the region under the transfer.
        """
        with self._lock:
            if self._busy:
                raise RdmaError(
                    "this RDMA region is in use by a transfer, so it cannot be "
                    "closed yet; close the client to cancel the transfer, which "
                    "then closes the region itself"
                )
            self._close_locked()

    def _close_locked(self) -> None:
        """:meth:`close`, with `_lock` held and no transfer using the region."""
        self._region = None
        # `detach` hands back the finalizer's arguments only the first time,
        # so the region is freed exactly once, by this or by the finalizer.
        detached = self._finalizer.detach()
        if detached is not None:
            _, _, arguments, _ = detached
            self._hold = _free_rdma_region(*arguments)
        else:
            _RETAINED_RDMA_MEMORY.release_what_is_free()
        self._view = None
        self._memory = None

    def _require_open(self):
        """The region pointer, or an error naming what went wrong."""
        if self._region is None:
            raise RdmaError("this RDMA region is closed")
        return self._region

    def _begin_transfer(self):
        """
        Mark the region in use by a transfer and return its pointer. Every call
        must be paired with :meth:`_end_transfer`.
        """
        with self._lock:
            if self._busy:
                raise RdmaError(
                    "this RDMA region is in use by another transfer; only one "
                    "transfer may use a region at a time"
                )
            region = self._require_open()
            self._busy = True
            return region

    def _end_transfer(self, close: bool) -> None:
        """
        Mark the transfer done, first closing the region if the transfer left
        it unusable, so no other call can use the region in between.
        """
        with self._lock:
            self._busy = False
            if close:
                self._close_locked()

    def __enter__(self) -> "RdmaRegion":
        return self

    def __exit__(
        self,
        exc_type: Optional[type[BaseException]],
        exc: Optional[BaseException],
        tb: Optional[TracebackType],
    ) -> None:
        self.close()

    def __repr__(self) -> str:
        state = "closed" if self.closed else f"{self._capacity} bytes"
        return f"RdmaRegion({state})"


class BaseClient(CoreCommands):

    def __init__(self, config: BaseClientConfiguration):
        """
        To create a new client, use the `create` classmethod
        """
        _glide_ffi = _SYNC_FFI
        self._ffi = _glide_ffi.ffi
        self._lib = _glide_ffi.lib
        self._config: BaseClientConfiguration = config
        self._pubsub_queue: List[PubSubMsg] = []
        self._pubsub_lock = threading.Lock()
        self._pubsub_condition = threading.Condition(self._pubsub_lock)
        self._pubsub_callback_ref = None  # Keep callback alive
        self._credential_provider_callback_ref = None
        self._credential_provider_callback_owner = None
        self._core_client = self._ffi.NULL
        # Lock protecting _core_client and _is_closed for free-threading safety.
        # Under GIL builds this is a no-op (GIL serializes access).
        # Under free-threaded builds this prevents use-after-free on concurrent close.
        self._client_lock = threading.Lock()

        self._is_closed: bool = False

    @classmethod
    def create(cls, config: BaseClientConfiguration) -> Self:
        if not isinstance(
            config, (GlideClientConfiguration, GlideClusterClientConfiguration)
        ):
            raise ConfigurationError(
                "Configuration must be an instance of the sync version of GlideClientConfiguration or GlideClusterClientConfiguration, imported from glide_sync.config."
            )
        self = cls(config)
        self._config = config
        self._is_closed = False

        os.register_at_fork(after_in_child=self._create_core_client)

        self._create_core_client()

        return self

    def _create_core_client(self):  # noqa: C901
        previous_owner = self._credential_provider_callback_owner
        if previous_owner is not None:
            previous_owner.close()
        self._credential_provider_callback_owner = None
        self._credential_provider_callback_ref = None
        credential_provider = None
        if (
            self._config.credentials is not None
            and self._config.credentials.iam_config is not None
        ):
            credential_provider = (
                self._config.credentials.iam_config.credential_provider
            )
        if credential_provider is not None and _is_async_callable(credential_provider):
            raise ConfigurationError(
                "The sync client does not support async credential providers; use the async client"
            )

        # This check is needed in case a fork happens after the client already closed
        # In that case the registered fork function will kick in even if the
        # client already closed, and recreate it anyway.
        if self._is_closed:
            return
        conn_req = _create_sync_connection_request(self._config)
        conn_req_bytes = conn_req.SerializeToString()
        # Store for scoped_connection
        self._conn_req_bytes = conn_req_bytes
        client_type = self._ffi.new(
            "ClientType*",
            {
                "_type": self._ffi.cast("ClientTypeEnum", FFIClientTypeEnum.Sync),
            },
        )

        # Always create pubsub callback to support dynamic subscriptions
        # This ensures messages are always handled by the wrapper, whether they originate
        # from configured subscriptions or from dynamic subscriptions added at runtime
        python_callback = self._create_push_handle_callback()
        pubsub_callback = self._ffi.callback("PubSubCallback", python_callback)
        # Store reference to prevent garbage collection
        self._pubsub_callback_ref = pubsub_callback

        # Create address resolver callback if configured
        address_resolver_callback = self._ffi.NULL
        if self._config.address_resolver is not None:
            resolver_fn = self._config.address_resolver

            def _address_resolver_callback(
                client_id,
                host_ptr,
                host_len,
                port,
                resolved_host_buf,
                resolved_host_buf_len,
                resolved_host_len_ptr,
            ):
                try:
                    host = self._ffi.buffer(host_ptr, host_len)[:].decode("utf-8")
                    resolved_host, resolved_port = resolver_fn(host, port)
                    encoded_host = resolved_host.encode("utf-8")
                    write_len = min(len(encoded_host), resolved_host_buf_len)
                    self._ffi.memmove(resolved_host_buf, encoded_host, write_len)
                    resolved_host_len_ptr[0] = write_len
                    return resolved_port
                except Exception:
                    # On error, return 0 to signal fallback to original address
                    return 0

            address_resolver_callback = self._ffi.callback(
                "AddressResolverCallback", _address_resolver_callback
            )
            # Store reference to prevent garbage collection
            self._address_resolver_callback_ref = address_resolver_callback

        if credential_provider is None:
            # Preserve the ordinary latest-main creation path exactly.
            client_response_ptr = self._lib.create_client(
                conn_req_bytes,
                len(conn_req_bytes),
                client_type,
                pubsub_callback,
                address_resolver_callback,
                0,  # client_id is not used by the Python client
            )

            Logger.log(Level.INFO, "connection info", "new connection established")

            # Handle the connection response
            if client_response_ptr != self._ffi.NULL:
                client_response = self._try_ffi_cast(
                    "ConnectionResponse*", client_response_ptr
                )
                if client_response.conn_ptr != self._ffi.NULL:
                    self._core_client = client_response.conn_ptr
                else:
                    error_message = (
                        self._ffi.string(
                            client_response.connection_error_message
                        ).decode(ENCODING)
                        if client_response.connection_error_message != self._ffi.NULL
                        else "Unknown error"
                    )
                    raise ClosingError(error_message)

                # Free the connection response to avoid memory leaks
                self._lib.free_connection_response(client_response_ptr)

                # Note: scope prewarm is deferred to first scoped_connection() call
                # to avoid creating extra connections at client startup (which breaks
                # lazy connection tests and connection count assertions).
            else:
                raise ClosingError("Failed to create client, response pointer is NULL.")
            return

        credential_callback, credential_owner = create_credential_provider_callback(
            self._ffi,
            credential_provider,
            allow_async=False,
            provider_owner=self,
        )
        try:
            client_response_ptr = self._lib.create_client_with_credential_provider(
                conn_req_bytes,
                len(conn_req_bytes),
                client_type,
                pubsub_callback,
                address_resolver_callback,
                credential_owner.callback_id,
                credential_callback,
            )
        except BaseException:
            credential_owner.close()
            raise

        Logger.log(Level.INFO, "connection info", "new connection established")

        if client_response_ptr == self._ffi.NULL:
            credential_owner.close()
            raise ClosingError("Failed to create client, response pointer is NULL.")

        client_response = self._try_ffi_cast("ConnectionResponse*", client_response_ptr)
        if client_response.conn_ptr == self._ffi.NULL:
            error_message = (
                self._ffi.string(client_response.connection_error_message).decode(
                    ENCODING
                )
                if client_response.connection_error_message != self._ffi.NULL
                else "Unknown error"
            )
            credential_owner.close()
            self._lib.free_connection_response(client_response_ptr)
            raise ClosingError(error_message)

        core_client = client_response.conn_ptr
        self._lib.free_connection_response(client_response_ptr)
        with self._client_lock:
            if self._is_closed:
                adopt_client = False
            else:
                self._core_client = core_client
                self._credential_provider_callback_ref = credential_callback
                self._credential_provider_callback_owner = credential_owner
                adopt_client = True
        if not adopt_client:
            credential_owner.close()
            self._lib.close_client(core_client)
            raise ClosingError("Client was closed during native creation.")

    def _create_push_handle_callback(self):
        """Create the FFI pubsub callback function"""

        def _pubsub_callback(
            client_ptr,
            kind,
            message_ptr,
            message_len,
            channel_ptr,
            channel_len,
            pattern_ptr,
            pattern_len,
        ):
            try:
                # Convert C pointers to Python bytes using ffi.buffer
                message = self._ffi.buffer(message_ptr, message_len)[:]
                channel = self._ffi.buffer(channel_ptr, channel_len)[:]
                pattern = (
                    self._ffi.buffer(pattern_ptr, pattern_len)[:]
                    if pattern_ptr != self._ffi.NULL
                    else None
                )

                push_kind_map = {
                    0: "Disconnection",
                    1: "Other",
                    2: "Invalidate",
                    3: "Message",
                    4: "PMessage",
                    5: "SMessage",
                    6: "Unsubscribe",
                    7: "PUnsubscribe",
                    8: "SUnsubscribe",
                    9: "Subscribe",
                    10: "PSubscribe",
                    11: "SSubscribe",
                }

                message_kind = push_kind_map.get(kind)

                if message_kind == "Disconnection":
                    Logger.log(
                        Level.WARN,
                        "disconnect notification",
                        "Transport disconnected, messages might be lost",
                    )
                elif message_kind in ["Message", "PMessage", "SMessage"]:
                    pubsub_msg = PubSubMsg(
                        message=message, channel=channel, pattern=pattern
                    )

                    # This aquires the underlying `_pubsub_lock` and allows for calling `notify()` on the variable
                    # If a callback is registered, call it with the message and the provided context
                    # Otherwise, append the message to the queue and notify threads that are waiting for a message.
                    with self._pubsub_condition:
                        user_callback, context = (
                            self._config._get_pubsub_callback_and_context()
                        )
                        if user_callback:
                            user_callback(pubsub_msg, context)
                        else:
                            self._pubsub_queue.append(pubsub_msg)
                            self._pubsub_condition.notify()
                elif message_kind in [
                    "PSubscribe",
                    "Subscribe",
                    "SSubscribe",
                    "Unsubscribe",
                    "PUnsubscribe",
                    "SUnsubscribe",
                ]:
                    pass  # Ignore subscription confirmations
                else:
                    Logger.log(
                        Level.WARN,
                        "unknown notification",
                        f"Unknown notification message: '{message_kind}'",
                    )

            except Exception as e:
                Logger.log(
                    Level.ERROR, "pubsub_callback", f"Error in pubsub callback: {e}"
                )

        return _pubsub_callback

    def _handle_response(self, message):
        if message == self._ffi.NULL:
            raise RequestError("Received NULL message.")
        addr = int(self._ffi.cast("uintptr_t", message))
        result, _arena_ptr = _fast_parse_response(addr)
        # Arena is freed by free_command_result in _handle_cmd_result's finally block
        return result

    def _handle_command_response(self, msg):
        """Handle a CommandResponse message based on its response type."""
        handlers = {
            0: self._handle_null_response,
            1: self._handle_int_response,
            2: self._handle_float_response,
            3: self._handle_bool_response,
            4: self._handle_string_response,
            5: self._handle_array_response,
            6: self._handle_map_response,
            7: self._handle_set_response,
            8: self._handle_ok_response,
            9: self._handle_error_response,
        }

        handler = handlers.get(msg.response_type)
        if handler is None:
            raise RequestError(f"Unhandled response type = {msg.response_type}")

        return handler(msg)

    def _handle_null_response(self, msg):
        return None

    def _handle_int_response(self, msg):
        return msg.int_value

    def _handle_float_response(self, msg):
        return msg.float_value

    def _handle_bool_response(self, msg):
        return bool(msg.bool_value)

    def _handle_string_response(self, msg):
        try:
            return self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
        except Exception as e:
            raise RequestError(f"Error decoding string value: {e}")

    def _handle_array_response(self, msg):
        array = []
        for i in range(msg.array_value_len):
            element = self._try_ffi_cast("struct CommandResponse*", msg.array_value + i)
            array.append(self._handle_response(element))
        return array

    def _handle_map_response(self, msg):
        map_dict = {}
        for i in range(msg.array_value_len):
            element = self._try_ffi_cast("struct CommandResponse*", msg.array_value + i)
            key = self._try_ffi_cast("struct CommandResponse*", element.map_key)
            value = self._try_ffi_cast("struct CommandResponse*", element.map_value)
            map_dict[self._handle_response(key)] = self._handle_response(value)
        return map_dict

    def _handle_set_response(self, msg):
        result_set = set()
        sets_array = self._try_ffi_cast(
            f"struct CommandResponse[{msg.sets_value_len}]", msg.sets_value
        )
        for i in range(msg.sets_value_len):
            element = sets_array[i]
            result_set.add(self._handle_response(element))
        return result_set

    def _handle_ok_response(self, msg):
        return OK

    def _handle_error_response(self, msg):
        try:
            error_msg = self._ffi.buffer(msg.string_value, msg.string_value_len)[:]
            return RequestError(f"{error_msg}")
        except Exception as e:
            raise RequestError(f"Error decoding error message: {e}")

    def _try_ffi_cast(self, type, source):
        try:
            return self._ffi.cast(type, source)
        except Exception as e:
            raise ClosingError(f"FFI casting failed: {e}")

    def _to_c_strings(self, args):
        """Convert Python arguments to C-compatible pointers and lengths."""
        c_strings = []
        string_lengths = []
        buffers = []  # Keep a reference to prevent premature garbage collection

        for arg in args:
            if isinstance(arg, str):
                arg_bytes = arg.encode(ENCODING)
            elif isinstance(arg, (bytes, bytearray, memoryview)):
                arg_bytes = arg
            else:
                raise TypeError(f"Unsupported argument type: {type(arg)}")

            # Use ffi.from_buffer for zero-copy conversion
            buffers.append(arg_bytes)  # Keep the byte buffer alive
            c_strings.append(
                self._try_ffi_cast("size_t", self._ffi.from_buffer(arg_bytes))
            )
            string_lengths.append(len(arg_bytes))
        # Return C-compatible arrays and keep buffers alive
        return (
            self._ffi.new("size_t[]", c_strings),
            self._ffi.new("unsigned long[]", string_lengths),
            buffers,  # Ensure buffers stay alive
        )

    # `route_bytes` must remain alive for the duration of the FFI call that consumes `route_ptr`
    def _to_c_route_ptr_and_len(self, route: Optional[Route]):
        proto_route = build_protobuf_route(route)
        if proto_route:
            route_bytes = proto_route.SerializeToString()
            route_ptr = self._ffi.from_buffer(route_bytes)
            route_len = len(route_bytes)
        else:
            route_bytes = None
            route_ptr = self._ffi.NULL
            route_len = 0

        return route_ptr, route_len, route_bytes

    def _handle_cmd_result(self, command_result):
        try:
            if command_result == self._ffi.NULL:
                raise ClosingError("Internal error: Received NULL as a command result")
            if command_result.command_error != self._ffi.NULL:
                # Handle the error case
                error = self._try_ffi_cast(
                    "CommandError*", command_result.command_error
                )
                error_message = self._ffi.string(error.command_error_message).decode(
                    ENCODING
                )
                error_class = get_request_error_class(error.command_error_type)
                # Free the error message to avoid memory leaks
                raise error_class(error_message)
            else:
                return self._handle_response(command_result.response)
                # Free the error message to avoid memory leaks
        finally:
            self._lib.free_command_result(command_result)

    @staticmethod
    def _validate_response_buffers(response_buffers: List[memoryview]) -> None:
        """Each buffer for a multi-value read must be writable and contiguous."""
        for mv in response_buffers:
            if mv.readonly:
                raise TypeError("response_buffers entries must be writable")
            if not mv.c_contiguous:
                raise TypeError("response_buffers entries must be C-contiguous")

    def _execute_command(
        self,
        request_type: RequestType.ValueType,  # type: ignore[override]
        args: List[TEncodable],
        route: Optional[Route] = None,
        response_buffer: Optional[memoryview] = None,
        response_buffers: Optional[List[memoryview]] = None,
    ) -> TResult:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")
        if response_buffer:
            if response_buffer.readonly:
                raise TypeError("response_buffer must be writable")
            if not response_buffer.c_contiguous:
                raise TypeError("response_buffer must be C-contiguous")
        if response_buffers is not None:
            self._validate_response_buffers(response_buffers)

        # Sample before reading the caller's context. A sampled command span uses
        # the active OTel span as its parent when one is available.
        from .opentelemetry import OpenTelemetry

        span = 0
        span_name_cstr = None
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            command_name = RequestType.Name(request_type)
            span_name_cstr = self._ffi.new("char[]", command_name.encode())
            span = _create_command_span(
                self._ffi, self._lib, span_name_cstr, parent_ctx
            )

        try:
            # Convert the arguments to C-compatible pointers
            c_args, c_lengths, buffers = self._to_c_strings(args)

            # Route bytes should be kept alive in the scope of the FFI call
            route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

            if response_buffers is not None:
                # One writable buffer per top-level array element (e.g. mget):
                # each value is copied straight into its caller-owned buffer.
                # The from_buffer cdata must stay alive for the call, so keep
                # the list referenced until command_with_buffers returns.
                target_ptrs = [self._ffi.from_buffer(mv) for mv in response_buffers]
                target_bufs = self._ffi.new("uint8_t*[]", target_ptrs)
                target_lens = self._ffi.new(
                    "size_t[]", [mv.nbytes for mv in response_buffers]
                )
                result = self._lib.command_with_buffers(
                    client_adapter_ptr,
                    0,
                    request_type,
                    len(args),
                    c_args,
                    c_lengths,
                    route_ptr,
                    route_len,
                    target_bufs,
                    target_lens,
                    len(response_buffers),
                    span,
                )
            else:
                buf_ptr = (
                    self._ffi.from_buffer(response_buffer)
                    if response_buffer
                    else self._ffi.NULL
                )
                # Capacity must be expressed in bytes, not elements. ``len()`` on
                # a memoryview returns the element count (``shape[0]``), which
                # equals the byte count only for itemsize-1 formats (e.g. "B").
                buf_len = response_buffer.nbytes if response_buffer else 0
                result = self._lib.command_with_buffer(
                    client_adapter_ptr,
                    0,
                    request_type,
                    len(args),
                    c_args,
                    c_lengths,
                    route_ptr,
                    route_len,
                    buf_ptr,
                    buf_len,
                    span,
                )
        finally:
            # Drop span if it was created
            if span != 0:
                self._lib.drop_otel_span(span)
        return self._handle_cmd_result(result)

    def _update_connection_password(
        self,
        password: Optional[str],
        immediate_auth: bool = False,
    ) -> TResult:
        """
        Update the current connection password with a new password.

        Note:
            This method updates the client's internal password configuration and does
            not perform password rotation on the server side.

        This method is useful in scenarios where the server password has changed or when
        utilizing short-lived passwords for enhanced security. It allows the client to
        update its password to reconnect upon disconnection without the need to recreate
        the client instance. This ensures that the internal reconnection mechanism can
        handle reconnection seamlessly, preventing the loss of in-flight commands.

        Args:
            password (`Optional[str]`): The new password to use for the connection,
                if `None` the password will be removed.
            immediate_auth (`bool`):
                `True`: The client will authenticate immediately with the new password against all connections, Using `AUTH`
                command. If password supplied is an empty string, auth will not be performed and warning will be returned.
                The default is `False`.

        Returns:
            TOK: A simple OK response. If `immediate_auth=True` returns OK if the reauthenticate succeed.

        Example:
            >>> client.update_connection_password("new_password", immediate_auth=True)
            'OK'
        """
        if self._is_closed:
            raise ClosingError("Client is closed.")
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Prepare C string for password
        c_password = (
            self._ffi.new("char[]", password.encode(ENCODING))
            if password is not None
            else self._ffi.new("char[]", b"")
        )

        result = self._lib.update_connection_password(
            client_adapter_ptr,
            0,  # Request ID (0 for sync use)
            c_password,
            immediate_auth,
        )
        return self._handle_cmd_result(result)

    def _refresh_iam_token(self) -> TResult:
        if self._is_closed:
            raise ClosingError("Client is closed.")
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        result = self._lib.refresh_iam_token(
            client_adapter_ptr,
            0,  # Request ID (0 for sync use)
        )
        return self._handle_cmd_result(result)

    def _execute_batch(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],  # type: ignore[override]
        is_atomic: bool,
        raise_on_error: bool,
        retry_server_error: bool = False,
        retry_connection_error: bool = False,
        route: Optional[Route] = None,
        timeout: Optional[int] = None,
    ) -> List[TResult]:
        """
        Execute a batch of commands synchronously using the FFI batch function.
        Accepts pre-extracted parameters from exec().
        """

        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Create span if OpenTelemetry is configured and sampling indicates we should trace
        from .opentelemetry import OpenTelemetry

        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span = _create_batch_span(self._ffi, self._lib, parent_ctx)

        try:
            # Note: batch_refs and option_refs must remain in scope
            # throughout this entire function call to prevent garbage collection of Python objects
            # that have C pointers pointing to them via ffi.from_buffer().

            # Convert commands + atomic flag to C BatchInfo
            batch_info, batch_refs = self._convert_commands_to_c_batch_info(
                commands, is_atomic
            )

            # Create batch options from extracted parameters
            batch_options, option_refs = self._create_c_batch_options_from_params(
                retry_server_error, retry_connection_error, route, timeout
            )

            result = self._lib.batch(
                client_adapter_ptr,
                0,  # callback_index (0 for sync)
                batch_info,
                raise_on_error,
                batch_options,
                span,  # span_ptr for tracing
            )
            return self._handle_cmd_result(result)
        finally:
            # Drop span if it was created
            if span != 0:
                self._lib.drop_otel_span(span)

    def _convert_commands_to_c_batch_info(
        self,
        commands: List[Tuple[RequestType.ValueType, List[TEncodable]]],
        is_atomic: bool,
    ) -> Tuple[Any, List[Any]]:
        """
        Convert commands directly to C BatchInfo (no intermediate _to_c_strings).
        Returns a tuple of (batch_info, refs) where refs contains all Python objects
        that must be kept alive to prevent garbage collection while C code uses pointers to them.
        """
        # all_refs keeps Python objects alive while C pointers reference their memory.
        # ffi.from_buffer() creates C pointers to Python object memory, and ffi.new() creates
        # FFI-managed memory with a Python reference controlling its lifetime. In both cases,
        # if Python references are garbage collected, the underlying memory may be freed,
        # creating dangling C pointers.

        all_refs = []
        cmd_infos = []

        for request_type, args in commands:
            args_buffers = []
            arg_ptrs = []
            arg_lengths = []

            for arg in args:
                if isinstance(arg, str):
                    arg_bytes = arg.encode(ENCODING)
                elif isinstance(arg, (bytes, bytearray, memoryview)):
                    arg_bytes = arg
                else:
                    raise TypeError(f"Unsupported argument type: {type(arg)}")

                args_buffers.append(arg_bytes)
                arg_ptrs.append(self._ffi.from_buffer(arg_bytes))
                arg_lengths.append(len(arg_bytes))

            c_arg_array = self._ffi.new("const uint8_t*[]", arg_ptrs)
            c_lengths = self._ffi.new("size_t[]", arg_lengths)

            cmd_info = self._ffi.new(
                "CmdInfo*",
                {
                    "request_type": request_type,
                    "args": c_arg_array,
                    "arg_count": len(args),
                    "args_len": c_lengths,
                },
            )

            cmd_infos.append(cmd_info)
            all_refs.extend(args_buffers + [c_arg_array, c_lengths])

        cmd_info_array = self._ffi.new("const CmdInfo*[]", cmd_infos)
        all_refs.append(cmd_info_array)
        all_refs.extend(cmd_infos)

        batch_info = self._ffi.new(
            "BatchInfo*",
            {
                "cmd_count": len(commands),
                "cmds": cmd_info_array,
                "is_atomic": is_atomic,
            },
        )

        return batch_info, all_refs + [batch_info]

    def _create_c_batch_options_from_params(
        self,
        retry_server_error: bool,
        retry_connection_error: bool,
        route: Optional[Route],
        timeout: Optional[int],
    ) -> Tuple[Any, List[Any]]:
        """
        Create BatchOptionsInfo from params, with refs.
        Returns a tuple of (batch_options, refs) where refs contains all Python objects
        that must be kept alive while C code accesses pointers to them.
        """

        route_info, route_refs = self._convert_route_to_c_format(route)

        batch_options = self._ffi.new(
            "BatchOptionsInfo*",
            {
                "retry_server_error": retry_server_error,
                "retry_connection_error": retry_connection_error,
                "has_timeout": timeout is not None,
                "timeout": timeout or 0,
                "route_info": route_info,
            },
        )

        return batch_options, route_refs + [batch_options]

    def _convert_route_to_c_format(
        self, route: Optional[Route]
    ) -> Tuple[Any, List[Any]]:
        """
        Convert a Route object to C RouteInfo format.

        Returns a tuple of (route_info, refs) where refs contains all Python objects
        that must be kept alive while C code uses pointers to them.
        """
        if route is None:
            return self._ffi.NULL, []

        refs = []

        slot_key_ptr = self._ffi.NULL
        hostname_ptr = self._ffi.NULL
        route_type = 2  # Default to Random
        slot_id = 0
        slot_type = 0  # Primary by default
        port = 0

        if isinstance(route, AllNodes):
            route_type = 0
        elif isinstance(route, AllPrimaries):
            route_type = 1
        elif isinstance(route, RandomNode):
            route_type = 2
        elif isinstance(route, SlotIdRoute):
            route_type = 3
            slot_id = route.slot_id
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, SlotKeyRoute):
            route_type = 4
            # Null termination needed for safety instructions of the FFI layer's `ptr_to_str` call.
            slot_key_bytes = route.slot_key.encode(ENCODING) + b"\0"
            refs.append(slot_key_bytes)
            slot_key_ptr = self._ffi.from_buffer(slot_key_bytes)
            slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
        elif isinstance(route, ByAddressRoute):
            route_type = 5
            # Null termination needed for safety instructions of the FFI layer's `ptr_to_str` call.
            hostname_bytes = route.host.encode(ENCODING) + b"\0"
            refs.append(hostname_bytes)
            hostname_ptr = self._ffi.from_buffer(hostname_bytes)
            port = route.port if route.port is not None else 0
        else:
            raise RequestError(f"Invalid route type: {type(route)}")

        route_info = self._ffi.new(
            "RouteInfo*",
            {
                "route_type": route_type,
                "slot_id": slot_id,
                "slot_key": slot_key_ptr,
                "slot_type": slot_type,
                "hostname": hostname_ptr,
                "port": port,
            },
        )

        return route_info, refs + [route_info]

    def _execute_script(
        self,
        script_hash: str,
        keys: Optional[List[TEncodable]] = None,
        args: Optional[List[TEncodable]] = None,
        route: Optional[Route] = None,
    ) -> TResult:

        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Default to empty lists if None provided
        if keys is None:
            keys = []
        if args is None:
            args = []

        # Convert keys to C-compatible format
        keys_c_args, keys_c_lengths, keys_buffers = self._to_c_strings(keys)

        # Convert args to C-compatible format
        args_c_args, args_c_lengths, args_buffers = self._to_c_strings(args)

        # Convert script hash to C string
        hash_bytes = script_hash.encode(ENCODING) + b"\0"
        hash_buffer = self._ffi.from_buffer(hash_bytes)

        # Route bytes should be kept alive in the scope of the FFI call
        route_ptr, route_len, route_bytes = self._to_c_route_ptr_and_len(route)

        # Create span if OpenTelemetry is configured and sampling
        from .opentelemetry import OpenTelemetry

        span = 0
        if OpenTelemetry.is_tracing_enabled() and OpenTelemetry.should_sample():
            parent_ctx = OpenTelemetry._get_parent_span_context()
            span = _create_command_span(
                self._ffi, self._lib, _EVALSHA_SPAN_NAME, parent_ctx
            )

        try:
            result = self._lib.invoke_script(
                client_adapter_ptr,
                0,  # Request ID - placeholder for sync clients
                hash_buffer,
                len(keys),
                keys_c_args,
                keys_c_lengths,
                len(args),
                args_c_args,
                args_c_lengths,
                route_ptr,
                route_len,
                span,
            )
            return self._handle_cmd_result(result)
        finally:
            if span != 0:
                self._lib.drop_otel_span(span)

    def try_get_pubsub_message(self) -> Optional[PubSubMsg]:
        """Try to get a pubsub message without blocking"""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never succeed since messages will be passed to the configured callback."
            )

        with self._pubsub_condition:
            if self._pubsub_queue:
                return self._pubsub_queue.pop(0)
            else:
                return None

    def get_pubsub_message(self) -> PubSubMsg:
        """Get a pubsub message, blocking until one is available"""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        if self._config._get_pubsub_callback_and_context()[0] is not None:
            raise ConfigurationError(
                "The operation will never complete since messages will be passed to the configured callback."
            )

        with self._pubsub_condition:
            while not self._pubsub_queue:
                if self._is_closed:
                    raise ClosingError("Client was closed while waiting for message")

                # Block indefinitely until notify() is called
                self._pubsub_condition.wait()

            return self._pubsub_queue.pop(0)

    def get_statistics(self) -> dict:
        """
        Get compression and connection statistics for this client.

        Returns:
            dict: A dictionary containing statistics with integer values:
                - total_connections: Total number of connections
                - total_clients: Total number of clients
                - total_values_compressed: Number of values successfully compressed
                - total_values_decompressed: Number of values successfully decompressed
                - total_original_bytes: Total bytes of original data before compression
                - total_bytes_compressed: Total bytes after compression
                - total_bytes_decompressed: Total bytes after decompression
                - compression_skipped_count: Number of times compression was skipped
                - subscription_out_of_sync_count: Failed reconciliation attempts
                - subscription_last_sync_timestamp: Last successful sync (milliseconds since epoch)
        """
        # Call the C FFI get_statistics function (returns by value, no manual free needed)
        stats = self._lib.get_statistics()

        # Access the struct fields and convert to a dictionary
        return {
            "total_connections": stats.total_connections,
            "total_clients": stats.total_clients,
            "total_values_compressed": stats.total_values_compressed,
            "total_values_decompressed": stats.total_values_decompressed,
            "total_original_bytes": stats.total_original_bytes,
            "total_bytes_compressed": stats.total_bytes_compressed,
            "total_bytes_decompressed": stats.total_bytes_decompressed,
            "compression_skipped_count": stats.compression_skipped_count,
            "subscription_out_of_sync_count": stats.subscription_out_of_sync_count,
            "subscription_last_sync_timestamp": stats.subscription_last_sync_timestamp,
        }

    def get_subscriptions(self):
        """Get subscription state (desired vs actual)."""
        result = self._execute_command(RequestType.GetSubscriptions, [])
        return self._parse_pubsub_state(
            result, is_cluster=isinstance(self, GlideClusterClient)
        )

    def _parse_pubsub_state(self, result, is_cluster):
        """Parse subscription state from Rust response."""
        if not isinstance(result, list) or len(result) != 4:
            raise RequestError("Invalid response format from GetSubscriptions")

        desired_dict = result[1]
        actual_dict = result[3]

        if is_cluster:
            from glide_shared.config import GlideClusterClientConfiguration

            PubSubChannelModes = GlideClusterClientConfiguration.PubSubChannelModes
            StateClass = GlideClusterClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
                "Sharded": PubSubChannelModes.Sharded,
            }
        else:
            from glide_shared.config import GlideClientConfiguration

            PubSubChannelModes = GlideClientConfiguration.PubSubChannelModes
            StateClass = GlideClientConfiguration.PubSubState
            mode_map = {
                "Exact": PubSubChannelModes.Exact,
                "Pattern": PubSubChannelModes.Pattern,
            }

        desired_subscriptions = {}
        actual_subscriptions = {}

        for key_bytes, value_list in desired_dict.items():
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                desired_subscriptions[mode_map[key]] = values

        for key_bytes, value_list in actual_dict.items():
            key = key_bytes.decode() if isinstance(key_bytes, bytes) else key_bytes
            if key in mode_map:
                values = {v.decode() if isinstance(v, bytes) else v for v in value_list}
                actual_subscriptions[mode_map[key]] = values

        return StateClass(
            desired_subscriptions=desired_subscriptions,
            actual_subscriptions=actual_subscriptions,
        )

    def _get_cache_metrics(self, metrics_type: int) -> TResult:
        """
        Get cache metrics.

        Args:
            metrics_type: Type of metric to retrieve (e.g., hit rate, miss rate).

        Returns:
            The requested cache metric.

        Raises:
            RequestError: If client-side caching is not enabled or metrics tracking is disabled.
        """
        if self._is_closed:
            raise ClosingError("Client is closed.")
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        result = self._lib.get_cache_metrics(
            client_adapter_ptr,
            0,  # Request ID (0 for sync use)
            metrics_type,
        )
        return self._handle_cmd_result(result)

    @staticmethod
    def rdma_available() -> bool:
        """
        Whether this build of GLIDE has RDMA compiled in.

        A build from source without the RDMA feature returns false.

        This says nothing about whether libfabric is on the machine.
        For whether RDMA can actually happen here, use :meth:`rdma_usable`.

        Returns:
            bool: True if RDMA support is present in this build.

        Example:
            >>> BaseClient.rdma_available()
            True
        """
        return bool(_SYNC_FFI.lib.rdma_available())

    @staticmethod
    def rdma_usable() -> bool:
        """
        Whether this build has RDMA support and can load a compatible libfabric
        on this machine.

        It does not look for fabric hardware, so creating an RDMA client can
        still fail, for example with ``RdmaProvider.EfaDirect()`` on a machine
        without an EFA device.

        Returns:
            bool: True if libfabric loads and this build supports RDMA.

        Example:
            >>> BaseClient.rdma_usable()
            False
        """
        return bool(_SYNC_FFI.lib.rdma_usable())

    def register_rdma_region(self, memory: Any) -> RdmaRegion:
        """
        Register memory so the server can transfer into or out of it directly.

        The memory stays the caller's. Anything supporting the writable buffer
        protocol works: a ``bytearray``, an ``mmap``, a NumPy array, a tensor's
        backing store. It must not be resized or freed until the returned region
        is closed.

        Register one large region and address windows of it per transfer rather
        than registering per operation.

        Memory that GLIDE still holds from a closed region, which
        :attr:`RdmaRegion.memory_released` reports, is refused, because a write
        from that region's last transfer could still land in it. That includes
        any other view or slice of the same bytes. On efa-direct this does not
        happen.

        Args:
            memory (Any): A writable, C-contiguous buffer to register.

        Returns:
            RdmaRegion: The registered region. Close when done with it.

        Raises:
            RdmaError: If this build has no RDMA support, the client was not
                configured for RDMA, the memory is still held from a closed
                region, or the fabric refused the registration.
            ClosingError: If the client is closed.
            TypeError: If the buffer is not writable and C-contiguous.

        Example:
            >>> region = client.register_rdma_region(bytearray(1 << 20))
            >>> region.capacity
            1048576
        """
        client_adapter_ptr = self._require_open_client()

        view = memoryview(memory)
        if view.readonly:
            raise TypeError("memory must be writable")
        if not view.c_contiguous:
            raise TypeError("memory must be C-contiguous")
        if _RETAINED_RDMA_MEMORY.overlaps(_memory_span(view)):
            raise RdmaError(
                "this memory belongs to a closed RDMA region whose last transfer "
                "could still write into it; register different memory"
            )

        buffer = self._ffi.from_buffer(view)
        registration = self._lib.register_rdma_region(
            client_adapter_ptr, buffer, view.nbytes
        )
        if registration == self._ffi.NULL:
            raise RdmaError("Internal error: received NULL from register_rdma_region")
        try:
            if registration.error_message != self._ffi.NULL:
                raise RdmaError(
                    self._ffi.string(registration.error_message).decode(ENCODING)
                )
            return RdmaRegion(self, memory, registration.region)
        finally:
            self._lib.free_rdma_registration(registration)

    def rdma_get(
        self,
        key: TEncodable,
        window: RdmaWindow,
    ) -> Optional[RdmaReadReceipt]:
        """
        Read a value directly into a window of registered memory.

        Waits until the transfer completes. The bytes are in the window by the
        time this returns. The value never travels in the RESP reply.

        There is no timeout. The server moves the payload with a remote memory
        operation that nothing can call off once it is posted, so giving up
        early would leave the server writing into the window while the caller
        believed it was free.

        To cancel a transfer, call :meth:`close` on the client from another
        thread. That first cuts the server off from every region the client
        registered, then this call returns raising ``ClosingError``, unless
        the server's reply was already arriving, in which case it returns as
        usual. Bytes that landed before the close stay, so the window then
        holds an unknown mix of old and new bytes. See :meth:`close` for the
        rare case where the close cannot cancel the transfer.

        A transfer that ends before the server is known to be done with the
        memory closes the region, for example one cancelled that way or one
        whose connection was lost. See :class:`RdmaRegion` for every case and
        how to recover.

        Args:
            key (TEncodable): The key to read.
            window (RdmaWindow): Where the value should land, from
                :meth:`RdmaRegion.window`. The server is told the window's
                length and refuses a larger value before writing anything, so
                the bytes around the window are never touched.

        Returns:
            Optional[RdmaReadReceipt]: What the server transferred, or None if the
            key does not exist. The landed bytes have already been verified
            against the receipt's ``checksum``.

        Raises:
            RdmaError: If the region is closed or belongs to another client, the
                value was larger than the window (the region stays usable), the
                landed bytes did not match the checksum, or the server refused
                the transfer or its handshake.
            ConnectionError: If the connection failed, or has to be replaced
                because its handshake left it unusable. Check
                ``region.closed``: the region stays open when the transfer was
                never sent.
            ClosingError: If the client is closed, including when it is closed
                during the transfer to cancel it.

        Example:
            >>> receipt = client.rdma_get(b"key", region.window(4096, 4096))
            >>> receipt.bytes_written if receipt else "missing"
        """
        c_key = self._to_c_key(key)
        return self._rdma_transfer(
            window,
            lambda client, region: self._lib.rdma_get(
                client, *c_key, region, window.offset, window.length
            ),
        )

    def rdma_set(
        self,
        key: TEncodable,
        window: RdmaWindow,
        checksum: Union[
            RdmaSetChecksum.Skip, RdmaSetChecksum.Compute, RdmaSetChecksum.Known
        ] = RdmaSetChecksum.Skip(),
    ) -> None:
        """
        Store a value the server reads directly out of a window of registered
        memory.

        As with ``rdma_get``, there is no timeout, closing the client from
        another thread cancels the call, raising ``ClosingError``, and a
        transfer that ends before the server is known to be done with the
        memory closes the region. See :class:`RdmaRegion` for how to recover.

        Args:
            key (TEncodable): The key to write.
            window (RdmaWindow): The bytes to send, from
                :meth:`RdmaRegion.window`.
            checksum: Whether the server checks the bytes it read against a
                CRC-32c before storing them; see :class:`RdmaSetChecksum`.
                Defaults to ``RdmaSetChecksum.Skip()``, which sends none.

        Returns:
            None. The server acknowledges without reporting a length, having
            read exactly the number of bytes the command named. A short read
            fails the transfer and raises instead.

        Raises:
            RdmaError: If the region is closed or belongs to another client, or
                the server refused the write or its handshake, including when
                the bytes it read do not match the checksum. A refused write
                stores nothing and leaves the region usable. If the region
                closed instead, the outcome is unknown: for example, a write
                the server queued inside another thread's ``MULTI`` may still
                be stored on ``EXEC``.
            ConnectionError: As for ``rdma_get``.
            ClosingError: If the client is closed, including when it is closed
                during the transfer to cancel it.
            TypeError: If ``checksum`` is not an ``RdmaSetChecksum`` choice.
            ValueError: If a known checksum is not an unsigned 32-bit integer.

        Example:
            >>> window = region.window(0, 11)
            >>> window.memoryview()[:] = b"hello world"
            >>> client.rdma_set(b"key", window, checksum=RdmaSetChecksum.Compute())
        """
        checksum_mode, checksum_value = self._rdma_set_checksum(checksum)
        c_key = self._to_c_key(key)
        self._rdma_transfer(
            window,
            lambda client, region: self._lib.rdma_set(
                client,
                *c_key,
                region,
                window.offset,
                window.length,
                checksum_mode,
                checksum_value,
            ),
        )

    @staticmethod
    def rdma_checksum(data: Any) -> int:
        """
        CRC-32c of ``data``, the integrity check the RDMA protocol carries.

        The same value the server computes, so a caller can verify what landed
        after a read, or compute one before sending to pass to ``rdma_set`` as
        ``RdmaSetChecksum.Known``. ``rdma_get`` verifies every read against the
        checksum the server sends with its reply.

        Args:
            data (Any): Any bytes-like object.

        Returns:
            int: The CRC-32c, as an unsigned 32-bit integer.

        Raises:
            RdmaError: If this build has no RDMA support.

        Example:
            >>> GlideClient.rdma_checksum(b"123456789")
            3808858755
        """
        ffi, lib = _SYNC_FFI.ffi, _SYNC_FFI.lib
        view = memoryview(data)
        out = ffi.new("uint32_t*")
        buffer = ffi.from_buffer(view) if view.nbytes else ffi.NULL
        if not lib.rdma_checksum(buffer, view.nbytes, out):
            raise RdmaError(
                "this build of GLIDE has no RDMA support compiled in, so it "
                "cannot compute a transfer checksum"
            )
        return int(out[0])

    def _require_open_client(self):
        """The client pointer, or an error naming what went wrong."""
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )
        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")
        return client_adapter_ptr

    def _to_c_key(self, key: TEncodable) -> Tuple[Any, int]:
        """A key as a pointer and a length, with the bytes kept alive by CFFI."""
        key_bytes = key.encode(ENCODING) if isinstance(key, str) else bytes(key)
        return self._ffi.from_buffer(key_bytes), len(key_bytes)

    def _rdma_set_checksum(self, checksum: Any) -> Tuple[Any, int]:
        """The checksum mode and value ``rdma_set`` passes across the FFI."""
        if isinstance(checksum, RdmaSetChecksum.Skip):
            return self._lib.RDMA_SET_CHECKSUM_SKIP, 0
        if isinstance(checksum, RdmaSetChecksum.Compute):
            return self._lib.RDMA_SET_CHECKSUM_COMPUTE, 0
        if isinstance(checksum, RdmaSetChecksum.Known):
            value = checksum.value
            if (
                not isinstance(value, int)
                or isinstance(value, bool)
                or not 0 <= value <= 0xFFFFFFFF
            ):
                raise ValueError(
                    "a known checksum must be an unsigned 32-bit integer, "
                    f"got {value!r}"
                )
            return self._lib.RDMA_SET_CHECKSUM_KNOWN, value
        raise TypeError(
            "checksum must be RdmaSetChecksum.Skip(), .Compute() or .Known(value), "
            f"got {type(checksum).__name__}"
        )

    def _rdma_transfer(
        self, window: RdmaWindow, call: Callable[[Any, Any], Any]
    ) -> Optional[RdmaReadReceipt]:
        """
        Run one transfer through `window`'s region with `call`, which takes the
        client and region pointers and makes the FFI call.

        Refuses a window that is not one, or whose region belongs to another
        client, then a closed client, then a region that is closed or already
        in use. The region stays marked in use until the FFI call returns, and
        is closed before it is released if the transfer left it unusable, so
        ``region.closed`` says so.
        """
        if not isinstance(window, RdmaWindow):
            raise TypeError(
                "window must be an RdmaWindow from region.window(), got "
                f"{type(window).__name__}"
            )
        region = window.region
        if region._client is not self:
            raise RdmaError(
                "this region is registered with a different client, so this "
                "client cannot transfer with it"
            )
        client_ptr = self._require_open_client()
        region_ptr = region._begin_transfer()
        region_closed = False
        try:
            result = call(client_ptr, region_ptr)
            if result == self._ffi.NULL:
                raise RdmaError("Internal error: received NULL as an RDMA result")
            region_closed = bool(result.region_closed)
            return self._handle_rdma_result(result)
        finally:
            region._end_transfer(close=region_closed)

    def _handle_rdma_result(self, result) -> Optional[RdmaReadReceipt]:
        """
        Turn a non-null transfer result into a receipt and free it, raising on
        failure.

        None means the key was absent, meaning nothing was transferred and
        the window is untouched.

        A transfer cancelled by closing the client raises ``ClosingError``.
        Any other failure keeps its own class even if the client was closed,
        so a reply larger than the window or a checksum mismatch that happened
        just before the close is still reported.
        """
        try:
            if result.error_message != self._ffi.NULL:
                message = self._ffi.string(result.error_message).decode(ENCODING)
                if message.startswith(_RDMA_CANCELLED):
                    raise ClosingError(message)
                error_class = get_request_error_class(result.error_type)
                if error_class is RequestError:
                    error_class = RdmaError
                raise error_class(message)
            if not result.found:
                return None
            return RdmaReadReceipt(
                bytes_written=int(result.bytes_written),
                checksum=int(result.checksum) if result.has_checksum else None,
            )
        finally:
            self._lib.free_rdma_result(result)

    @property
    def closed(self) -> bool:
        """
        Whether :meth:`close` has been called. A closed client refuses every
        later command, registration, and transfer, so a new client is needed.
        After a failed RDMA transfer this says whether the region can be
        registered again on this client; see :class:`RdmaRegion`.

        Example:
            >>> client.close()
            >>> client.closed
            True
        """
        return self._is_closed

    def close(self) -> None:
        """
        Close the client. Closing twice is a no-op.

        With RDMA, this also cancels every transfer in flight on another thread:
        it cuts the server off from every region the client registered, and
        each blocked ``rdma_get`` or ``rdma_set`` then raises ``ClosingError``,
        unless its reply was already arriving, in which case it returns as
        usual.

        This returns without waiting for those calls. Close a region only after
        every transfer call using it has returned; closing it while one is
        still running corrupts memory. A cancelled transfer closes its own
        region on the way out, so there is usually nothing left to close.

        If deregistering a region fails, which takes a device or driver error
        and is logged as an error, a transfer using it is not cancelled. It
        keeps waiting until the server replies or the connection fails, since
        the server could still reach the memory, and that memory is never
        released; see :attr:`RdmaRegion.memory_released`.
        """
        if _is_credential_provider_executing(self):
            raise RuntimeError(
                "Cannot close a client from its own credential provider callback"
            )
        with self._client_lock:
            if not self._is_closed:
                self._is_closed = True
                with self._pubsub_condition:
                    self._pubsub_condition.notify_all()
                core_client = getattr(self, "_core_client", self._ffi.NULL)
                if core_client != self._ffi.NULL:
                    self._lib.close_client(core_client)
                    self._core_client = self._ffi.NULL
                self._pubsub_callback_ref = None
                owner = getattr(self, "_credential_provider_callback_owner", None)
                if owner is not None:
                    owner.close()
                self._credential_provider_callback_owner = None
                self._credential_provider_callback_ref = None
        # Closing the client may be what lets the fabric go of memory it held.
        _RETAINED_RDMA_MEMORY.release_what_is_free()

    def __enter__(self) -> Self:
        return self

    def __exit__(
        self,
        exc_type: Optional[type[BaseException]],
        exc: Optional[BaseException],
        tb: Optional[TracebackType],
    ) -> None:
        self.close()

    def scoped_connection(
        self, timeout: float = 5.0, routing_key: Optional[str] = None
    ) -> "IsolatedScope":
        """
        Acquire an isolated execution scope — a dedicated connection for operations
        requiring per-connection state (WATCH/MULTI/EXEC, CLIENT TRACKING, blocking
        commands).

        The scope bypasses the multiplexer, executing commands on its own TCP
        connection. Use as a context manager for automatic release:

            with client.scoped_connection(routing_key="my-key") as scope:
                scope.watch("my-key")
                val = scope.get("my-key")
                scope.multi()
                scope.set("my-key", str(int(val or "0") + 1))
                result = scope.exec()

        Args:
            timeout: Maximum seconds to wait for a scope connection (default 5.0).
            routing_key: In cluster mode, the key whose hash slot determines which
                node the scope connects to, and against which the scope's first
                keyed command is validated (a command for a different slot is
                rejected locally with CROSSSLOT). If None, the scope starts
                unconstrained (slot 0 selects only the initial connection target):
                the first keyed command may target any slot and pins the scope to
                it, after which later keyed commands for a different slot receive
                CROSSSLOT. In standalone mode this argument is ignored.

        Returns:
            An IsolatedScope instance.

        Raises:
            TimeoutError: If no scope is available within the timeout.
            ConnectionError: If the client is closed while waiting.
            RequestError: If the client's configuration cannot produce a
                scoped connection.
            ClosingError: If the client is already closed.
        """
        from .isolated_scope import IsolatedScope

        if self._is_closed:
            raise ClosingError("Client is closed.")

        # Use the pointer address as client_id for the scope pool
        client_id = int(self._ffi.cast("uintptr_t", self._core_client))
        conn_req_bytes = self._conn_req_bytes

        # Compute routing slot from key. Presence is carried separately so a
        # cluster scope with no routing key stays unconstrained (any primary)
        # rather than being pinned to slot 0, which is a real slot.
        has_routing_slot = routing_key is not None
        routing_slot = (
            _slot_for_key(routing_key.encode("utf-8")) if routing_key is not None else 0
        )

        buf = self._ffi.from_buffer(conn_req_bytes)
        result = self._lib.glide_scope_acquire_blocking(
            client_id,
            self._ffi.cast("const uint8_t*", buf),
            len(conn_req_bytes),
            has_routing_slot,
            routing_slot,
            # uint64_t on the FFI side: a negative value would raise OverflowError
            # instead of the documented TimeoutError. Negative means no wait.
            max(0, int(timeout * 1000)),
        )
        scope_id = handle_command_result(
            self._ffi, self._lib, result, lambda resp: resp.int_value
        )
        return IsolatedScope(
            scope_id,
            client_id,
            _SYNC_FFI,
            self._parse_scope_response,
        )

    def _parse_scope_response(self, response_ptr) -> Optional[str]:  # noqa: C901
        """Parse a CommandResponse pointer from a scope execution into a Python string."""
        if response_ptr == self._ffi.NULL:
            return None

        resp = response_ptr
        resp_type = resp.response_type

        # Null response
        if resp_type == 0:  # ResponseType::Null
            return None
        # Int
        elif resp_type == 1:  # ResponseType::Int
            return str(resp.int_value)
        # Float
        elif resp_type == 2:  # ResponseType::Float
            return str(resp.float_value)
        # Bool
        elif resp_type == 3:  # ResponseType::Bool
            return str(resp.bool_value)
        # String
        elif resp_type == 4:  # ResponseType::String
            if resp.string_value == self._ffi.NULL:
                return None
            return self._ffi.buffer(resp.string_value, resp.string_value_len)[:].decode(
                "utf-8"
            )
        # Array (for EXEC results, LRANGE, etc.)
        elif resp_type == 5:  # ResponseType::Array
            if resp.array_value == self._ffi.NULL or resp.array_value_len == 0:
                return None
            # For simple scope usage, return a string repr
            results = []
            for i in range(resp.array_value_len):
                elem = self._parse_scope_response(resp.array_value + i)
                results.append(elem)
            return str(results)
        # Ok
        elif resp_type == 8:  # ResponseType::Ok
            return "OK"
        # Error
        elif resp_type == 9:  # ResponseType::Error
            if resp.string_value != self._ffi.NULL:
                msg = self._ffi.string(resp.string_value).decode("utf-8")
                raise RuntimeError(f"Server error: {msg}")
            raise RuntimeError("Server error (unknown)")
        else:
            # Fallback for Map, Sets, etc.
            return None


class GlideClusterClient(BaseClient, ClusterCommands):
    """
    Client used for connection to cluster servers.
    For full documentation, see
    https://glide.valkey.io/how-to/client-initialization/#cluster
    """

    def _build_cluster_scan_args(self, match, count, type, allow_non_covered_slots):
        args = []
        if match is not None:
            # Inline _encode_arg logic
            if isinstance(match, str):
                encoded_match = match.encode(ENCODING)
            else:
                encoded_match = match
            args.extend([b"MATCH", encoded_match])

        if count is not None:
            args.extend([b"COUNT", str(count).encode(ENCODING)])
        if type is not None:
            args.extend([b"TYPE", type.value.encode(ENCODING)])
        if allow_non_covered_slots:
            args.extend([b"ALLOW_NON_COVERED_SLOTS"])

        return args

    def _cluster_scan(
        self,
        cursor: ClusterScanCursor,
        match: Optional[TEncodable] = None,
        count: Optional[int] = None,
        type: Optional[ObjectType] = None,
        allow_non_covered_slots: bool = False,
    ) -> List[Union[ClusterScanCursor, List[bytes]]]:
        if self._is_closed:
            raise ClosingError(
                "Unable to execute requests; the client is closed. Please create a new client."
            )

        client_adapter_ptr = self._core_client
        if client_adapter_ptr == self._ffi.NULL:
            raise ValueError("Invalid client pointer.")

        # Use helper method to build args
        args = self._build_cluster_scan_args(
            match, count, type, allow_non_covered_slots
        )
        # Convert cursor to C string
        cursor_string = cursor.get_cursor()
        cursor_bytes = cursor_string.encode(ENCODING) + b"\0"  # Null terminate for C

        # Keep references to prevent GC
        temp_buffers: List[Any] = [cursor_bytes]
        cursor_buffer = self._ffi.from_buffer(cursor_bytes)

        # Prepare FFI arguments
        if args:
            args_array, args_len_array, arg_buffers = self._to_c_strings(args)
            temp_buffers.extend(arg_buffers)  # Keep references alive
            arg_count = len(args)
        else:
            args_array = self._ffi.NULL
            args_len_array = self._ffi.NULL
            arg_count = 0

        result_ptr = self._lib.request_cluster_scan(
            client_adapter_ptr,
            0,
            cursor_buffer,
            arg_count,
            args_array,
            args_len_array,
        )

        response_data = self._handle_cmd_result(result_ptr)

        if not isinstance(response_data, list) or len(response_data) != 2:
            raise RequestError("Unexpected cluster scan response format")

        new_cursor = response_data[0]
        if isinstance(new_cursor, bytes):
            new_cursor = new_cursor.decode(ENCODING)

        keys_list = response_data[1] if response_data[1] is not None else []

        return [ClusterScanCursor(new_cursor), keys_list]


class GlideClient(BaseClient, StandaloneCommands):
    """
    Client used for connection to standalone servers.
    For full documentation, see
    https://glide.valkey.io/how-to/client-initialization/#standalone
    """

    pass


TGlideClient = Union[GlideClient, GlideClusterClient]
