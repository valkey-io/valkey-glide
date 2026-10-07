# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Shared FFI helper utilities for converting Python arguments to C-compatible arrays."""

import contextvars
import os
import threading
import weakref
from enum import IntEnum
from typing import Any

from glide_shared._glide_ffi import GlideFFI as _GlideFFI_singleton


class _AddressResolverCallbackOwner:
    """Keep a user resolver alive without making its CFFI callback own it."""

    def __init__(self, resolver: Any) -> None:
        self.resolver = resolver


class _CredentialProviderCallbackOwner:
    """Keep provider state alive separately from its CFFI trampoline."""

    def __init__(
        self,
        provider: Any,
        *,
        event_loop: Any = None,
        trio_token: Any = None,
        allow_async: bool = False,
        provider_owner: Any = None,
    ) -> None:
        from glide_shared.config import _is_async_callable

        self.provider = provider
        self.is_async_callable = _is_async_callable(provider)
        self.event_loop_ref = (
            weakref.ref(event_loop) if event_loop is not None else None
        )
        self.trio_token = trio_token
        self.allow_async = allow_async
        try:
            self.provider_owner_ref = (
                weakref.ref(provider_owner) if provider_owner is not None else None
            )
        except TypeError:
            # Reentrancy tracking does not justify retaining a non-weakrefable
            # owner through a native callback graph.
            self.provider_owner_ref = None

    def event_loop(self) -> Any:
        return self.event_loop_ref() if self.event_loop_ref is not None else None

    def provider_owner(self) -> Any:
        owner = (
            self.provider_owner_ref() if self.provider_owner_ref is not None else None
        )
        # Standalone helper users and a create worker whose caller disappeared
        # can use this detached owner as their reentrancy marker.
        return owner if owner is not None else self


class _NativeClientOwner:
    """Own a native client pointer and its callbacks without owning its client."""

    def __init__(
        self,
        lib: Any,
        core_client: Any,
        callback_refs: tuple[Any, ...],
        creation_pid: int,
    ) -> None:
        self._lib = lib
        self._core_client = core_client
        self._callback_refs = callback_refs
        self._creation_pid = creation_pid
        self._lock = threading.Lock()

    def close(self) -> None:
        """Consume ownership, closing only in the process that created it."""
        with self._lock:
            core_client, self._core_client = self._core_client, None
            if core_client is None:
                return
        try:
            if os.getpid() == self._creation_pid:
                self._lib.close_client(core_client)
        finally:
            # Keep every callback alive through native close, then release it.
            self._callback_refs = ()

    def disarm(self) -> None:
        """Drop inherited ownership without touching the parent runtime pointer."""
        with self._lock:
            self._core_client = None
            self._callback_refs = ()


def _finalize_native_client(owner: _NativeClientOwner) -> None:
    """Best-effort ordinary-GC cleanup; explicit close still reports errors."""
    try:
        owner.close()
    except BaseException:
        # Finalizer exceptions cannot be delivered to an application caller.
        pass


def _create_native_client_finalizer(
    client: Any,
    lib: Any,
    core_client: Any,
    callback_refs: tuple[Any, ...],
    creation_pid: int,
) -> tuple[_NativeClientOwner, weakref.finalize]:
    """Create a native owner whose finalizer never strongly retains ``client``.

    Interpreter-exit execution is disabled because Python modules and the native
    runtime can be torn down in either order. The operating system reclaims
    process resources at exit; ordinary garbage collection still closes the
    native client promptly.
    """
    owner = _NativeClientOwner(lib, core_client, callback_refs, creation_pid)
    finalizer = weakref.finalize(client, _finalize_native_client, owner)
    finalizer.atexit = False  # type: ignore[misc]
    return owner, finalizer


ENCODING = "utf-8"


def encode_arg(arg):
    """Encode a single argument to bytes."""
    if isinstance(arg, str):
        return arg.encode(ENCODING)
    if isinstance(arg, (bytes, bytearray, memoryview)):
        return bytes(arg) if isinstance(arg, (bytearray, memoryview)) else arg
    raise TypeError(f"Unsupported argument type: {type(arg)}")


def to_c_strings(ffi, args):
    """Convert Python arguments to C-compatible (pointers_array, lengths_array, buffers).

    The returned `buffers` list must be kept alive for the duration of the FFI call.
    """
    buffers = [encode_arg(a) for a in args]
    c_strings = ffi.new(
        "size_t[]", [ffi.cast("size_t", ffi.from_buffer(b)) for b in buffers]
    )
    c_lengths = ffi.new("unsigned long[]", [len(b) for b in buffers])
    return c_strings, c_lengths, buffers


def to_c_route_ptr_and_len(ffi, route):
    """Convert a Route to C-compatible (route_ptr, route_len, route_bytes).

    The returned `route_bytes` must be kept alive for the duration of the FFI call.
    """
    if route is None:
        return ffi.NULL, 0, None

    from glide_shared.routes import build_protobuf_route

    proto_route = build_protobuf_route(route)
    if proto_route:
        route_bytes = proto_route.SerializeToString()
        route_ptr = ffi.from_buffer(route_bytes)
        route_len = len(route_bytes)
    else:
        route_bytes = None
        route_ptr = ffi.NULL
        route_len = 0
    return route_ptr, route_len, route_bytes


_route_type_map = _GlideFFI_singleton.ffi.typeof("RouteType").relements


class _RouteType(IntEnum):
    ALL_NODES = _route_type_map["AllNodes"]
    ALL_PRIMARIES = _route_type_map["AllPrimaries"]
    RANDOM = _route_type_map["Random"]
    SLOT_ID = _route_type_map["SlotId"]
    SLOT_KEY = _route_type_map["SlotKey"]
    BY_ADDRESS = _route_type_map["ByAddress"]


def to_c_route_info(ffi, route):
    """Convert a Route to a C RouteInfo* for batch operations.

    Returns (route_info_ptr, refs) where refs must be kept alive
    for the duration of the FFI call.
    """
    if route is None:
        return ffi.NULL, []

    from glide_shared.routes import (
        AllNodes,
        AllPrimaries,
        ByAddressRoute,
        RandomNode,
        SlotIdRoute,
        SlotKeyRoute,
        SlotType,
    )

    refs = []
    slot_key_ptr = ffi.NULL
    hostname_ptr = ffi.NULL
    route_type = _RouteType.RANDOM
    slot_id = 0
    slot_type = 0  # Primary
    port = 0

    if isinstance(route, AllNodes):
        route_type = _RouteType.ALL_NODES
    elif isinstance(route, AllPrimaries):
        route_type = _RouteType.ALL_PRIMARIES
    elif isinstance(route, RandomNode):
        route_type = _RouteType.RANDOM
    elif isinstance(route, SlotIdRoute):
        route_type = _RouteType.SLOT_ID
        slot_id = route.slot_id
        slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
    elif isinstance(route, SlotKeyRoute):
        route_type = _RouteType.SLOT_KEY
        slot_key_bytes = route.slot_key.encode(ENCODING) + b"\0"
        refs.append(slot_key_bytes)
        slot_key_ptr = ffi.from_buffer(slot_key_bytes)
        slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
    elif isinstance(route, ByAddressRoute):
        route_type = _RouteType.BY_ADDRESS
        hostname_bytes = route.host.encode(ENCODING) + b"\0"
        refs.append(hostname_bytes)
        hostname_ptr = ffi.from_buffer(hostname_bytes)
        port = route.port if route.port is not None else 0

    route_info = ffi.new(
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
    refs.append(route_info)
    return route_info, refs


# ═══════════════════════════════════════════════════════════════════════════════
# Shared constants and helpers used by both async and sync clients
# ═══════════════════════════════════════════════════════════════════════════════


class FFIClientTypeEnum:
    """Client type enum matching the Rust ClientType repr."""

    Async = 0
    Sync = 1


PUSH_KIND_MAP = {
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


def parse_push_notification(
    ffi,
    kind,
    message_ptr,
    message_len,
    channel_ptr,
    channel_len,
    pattern_ptr,
    pattern_len,
):
    """Parse raw FFI push notification data into (message_kind, message_bytes, channel_bytes, pattern_bytes).

    Returns (kind_str, message, channel, pattern) where pattern may be None.
    """
    message = ffi.buffer(message_ptr, message_len)[:]
    channel = ffi.buffer(channel_ptr, channel_len)[:]
    pattern = (
        ffi.buffer(pattern_ptr, pattern_len)[:] if pattern_ptr != ffi.NULL else None
    )
    message_kind = PUSH_KIND_MAP.get(kind)
    return message_kind, message, channel, pattern


def convert_commands_to_c_batch_info(ffi, commands, is_atomic):
    """Convert a list of (request_type, args) tuples to a C BatchInfo*.

    Returns (batch_info, refs) where refs must be kept alive during the FFI call.
    """
    all_refs = []
    cmd_infos = []

    for request_type, args in commands:
        arg_buffers = []
        arg_ptrs = []
        arg_lengths = []

        for arg in args:
            arg_bytes = encode_arg(arg)
            arg_buffers.append(arg_bytes)
            arg_ptrs.append(ffi.from_buffer(arg_bytes))
            arg_lengths.append(len(arg_bytes))

        c_arg_array = ffi.new("const uint8_t*[]", arg_ptrs)
        c_lengths = ffi.new("size_t[]", arg_lengths)

        cmd_info = ffi.new(
            "CmdInfo*",
            {
                "request_type": request_type,
                "args": c_arg_array,
                "arg_count": len(args),
                "args_len": c_lengths,
            },
        )

        cmd_infos.append(cmd_info)
        all_refs.extend(arg_buffers + [c_arg_array, c_lengths])

    cmd_info_array = ffi.new("const CmdInfo*[]", cmd_infos)
    all_refs.extend(cmd_infos + [cmd_info_array])

    batch_info = ffi.new(
        "BatchInfo*",
        {
            "cmd_count": len(commands),
            "cmds": cmd_info_array,
            "is_atomic": is_atomic,
        },
    )

    return batch_info, all_refs + [batch_info]


def create_c_batch_options(
    ffi, route, retry_server_error=False, retry_connection_error=False, timeout=None
):
    """Create a C BatchOptionsInfo* from Python parameters.

    Returns (batch_options, refs) where refs must be kept alive during the FFI call.
    """
    route_info, route_refs = to_c_route_info(ffi, route)

    batch_options = ffi.new(
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


def create_address_resolver_callback(ffi, resolver_fn):
    """Create a resolver trampoline and its separately retained callable owner.

    The CFFI callback captures only a weak reference to the owner. Callers must
    keep the returned owner alive for as long as native code may call the
    trampoline. If native close invokes it after owner collection, it safely
    returns 0 so Rust falls back to the original address.
    """
    if resolver_fn is None:
        return ffi.cast("AddressResolverCallback", ffi.NULL), None

    callback_owner = _AddressResolverCallbackOwner(resolver_fn)
    callback_owner_ref = weakref.ref(callback_owner)

    def _address_resolver_callback(
        client_id,
        host_ptr,
        host_len,
        port,
        resolved_host_buf,
        resolved_host_buf_len,
        resolved_host_len_ptr,
    ):
        owner = callback_owner_ref()
        if owner is None:
            return 0
        try:
            host = ffi.buffer(host_ptr, host_len)[:].decode(ENCODING)
            resolved_host, resolved_port = owner.resolver(host, port)
            encoded_host = resolved_host.encode(ENCODING)
            write_len = min(len(encoded_host), resolved_host_buf_len)
            ffi.memmove(resolved_host_buf, encoded_host, write_len)
            resolved_host_len_ptr[0] = write_len
            return resolved_port
        except Exception as error:
            # Return 0 (original port) to signal failure to the Rust layer,
            # which will fall back to the original address. We cannot propagate
            # exceptions across the FFI callback boundary.
            from glide_shared.logger import Level, Logger

            Logger.log(Level.WARN, "address_resolver", f"Resolver failed: {error}")
            return 0

    callback = ffi.callback("AddressResolverCallback", _address_resolver_callback)
    return callback, callback_owner


_CREDENTIAL_CALLBACK_FAILURE = 0


# A provider may execute synchronously on a native callback thread or
# asynchronously on its client's owner runtime. Thread-local state identifies
# the former without affecting other threads; ContextVar state identifies only
# the owner task (and its children), so an unrelated owner-runtime task may
# still close the client safely.
_credential_provider_thread_state = threading.local()
_credential_provider_task_state: contextvars.ContextVar[tuple[Any, ...]] = (
    contextvars.ContextVar("glide_credential_provider_owners", default=())
)


def _push_thread_provider_owner(owner) -> None:
    owners = getattr(_credential_provider_thread_state, "owners", ())
    _credential_provider_thread_state.owners = owners + (owner,)


def _pop_thread_provider_owner() -> None:
    owners = _credential_provider_thread_state.owners
    _credential_provider_thread_state.owners = owners[:-1]


def _is_credential_provider_executing(owner) -> bool:
    """Return whether this thread/task is inside ``owner``'s provider."""
    thread_owners: tuple[Any, ...] = getattr(
        _credential_provider_thread_state, "owners", ()
    )
    task_owners: tuple[Any, ...] = _credential_provider_task_state.get()
    return any(item is owner for item in thread_owners) or any(
        item is owner for item in task_owners
    )


async def _run_in_provider_task_context(owner, async_fn, *args):
    """Run provider-owned awaitable work with a task-local reentrancy marker."""
    token = _credential_provider_task_state.set(
        _credential_provider_task_state.get() + (owner,)
    )
    try:
        return await async_fn(*args)
    finally:
        _credential_provider_task_state.reset(token)


_CREDENTIAL_CALLBACK_SUCCESS = 1
_CREDENTIAL_CALLBACK_BUFFER_TOO_SMALL = 2
_MAX_CREDENTIAL_BYTES = 1024 * 1024
_CREDENTIAL_INNER_TIMEOUT_SECONDS = 8
_CREDENTIAL_BRIDGE_TIMEOUT_SECONDS = 9


class _AwaitableSchedulingError(RuntimeError):
    """An owner runtime rejected work before the awaitable was scheduled."""


async def _await_credential_result(awaitable, provider_owner):
    """Await one provider result with a deadline below the Rust deadline."""
    import anyio

    async def await_result():
        with anyio.fail_after(_CREDENTIAL_INNER_TIMEOUT_SECONDS):
            return await awaitable

    return await _run_in_provider_task_context(provider_owner, await_result)


async def _call_async_credential_provider(provider, provider_owner):
    """Call an async provider on its owning async runtime."""
    import inspect

    import anyio

    async def call_provider():
        result = provider()
        if not inspect.isawaitable(result):
            return result
        with anyio.fail_after(_CREDENTIAL_INNER_TIMEOUT_SECONDS):
            return await result

    return await _run_in_provider_task_context(provider_owner, call_provider)


def _consume_bridge_completion(future) -> None:
    """Retrieve a bridge result so late task failures are never unobserved."""
    import concurrent.futures

    try:
        future.exception()
    except concurrent.futures.CancelledError:
        pass


def _run_coroutine_on_asyncio_loop(coroutine, event_loop):
    """Run a coroutine from the native callback thread on an asyncio loop."""
    import asyncio
    import concurrent.futures

    if event_loop is None or event_loop.is_closed():
        _dispose_awaitable(coroutine)
        raise _AwaitableSchedulingError(
            "The credential provider's asyncio loop is unavailable"
        )

    try:
        future = asyncio.run_coroutine_threadsafe(coroutine, event_loop)
    except BaseException as error:
        _dispose_awaitable(coroutine)
        raise _AwaitableSchedulingError(
            "The credential provider could not be scheduled on its asyncio loop"
        ) from error

    future.add_done_callback(_consume_bridge_completion)
    try:
        return future.result(timeout=_CREDENTIAL_BRIDGE_TIMEOUT_SECONDS)
    except concurrent.futures.TimeoutError:
        # The coroutine belongs to the event loop now. Request cancellation on
        # that loop; never cancel or close it directly from this callback thread.
        event_loop.call_soon_threadsafe(future.cancel)
        raise


class _TrioBridgeState:
    """Coordinate one foreign-thread callback with its owning Trio run."""

    def __init__(self, bridge, abandoned_cleanup=None) -> None:
        self.bridge = bridge
        self.abandoned_cleanup = abandoned_cleanup
        self.lock = threading.Lock()
        self.cancel_scope = None
        self.abandoned = False
        self.provider_started = False
        self.cleanup_done = False

    def abandon(self) -> None:
        """Mark the waiter gone before requesting owner-thread cancellation."""
        with self.lock:
            self.abandoned = True

    def should_schedule(self) -> bool:
        """Return whether owner-side scheduling may still create a task."""
        with self.lock:
            return not self.abandoned

    def begin_provider(self, cancel_scope) -> bool:
        """Transfer work to a started provider task unless already abandoned."""
        with self.lock:
            self.cancel_scope = cancel_scope
            if self.abandoned:
                return False
            self.provider_started = True
            return True

    def cancel_on_owner(self) -> None:
        """Cancel started work; a not-yet-started runner observes abandonment."""
        with self.lock:
            cancel_scope = self.cancel_scope if self.provider_started else None
        if cancel_scope is not None:
            cancel_scope.cancel()

    def dispose_abandoned_on_owner(self) -> None:
        """Dispose never-started awaitable input exactly once on the Trio thread."""
        with self.lock:
            if self.cleanup_done:
                return
            self.cleanup_done = True
            cleanup = self.abandoned_cleanup
        if cleanup is not None:
            cleanup()

    def set_result(self, result) -> None:
        if not self.bridge.done():
            self.bridge.set_result(result)

    def set_exception(self, error) -> None:
        if not self.bridge.done():
            self.bridge.set_exception(error)

    def cancel_bridge(self) -> None:
        if not self.bridge.done():
            self.bridge.cancel()


def _run_coroutine_on_trio_loop(  # noqa: C901
    async_fn, args, trio_token, *, abandoned_cleanup=None
):
    """Schedule one credential task nonblockingly on its captured Trio run."""
    import concurrent.futures

    import trio

    bridge = concurrent.futures.Future()
    state = _TrioBridgeState(bridge, abandoned_cleanup)

    async def runner():
        try:
            with trio.CancelScope() as cancel_scope:
                if not state.begin_provider(cancel_scope):
                    state.dispose_abandoned_on_owner()
                    state.cancel_bridge()
                    return
                result = await async_fn(*args)
            if cancel_scope.cancelled_caught:
                state.cancel_bridge()
            else:
                state.set_result(result)
        except BaseException as error:
            state.set_exception(error)

    def schedule() -> None:
        # This callback executes on the Trio thread. It must never raise: Trio
        # treats exceptions from run_sync_soon callbacks as internal failures.
        try:
            if not state.should_schedule():
                state.dispose_abandoned_on_owner()
                state.cancel_bridge()
                return
            trio.lowlevel.spawn_system_task(runner)
        except BaseException as error:
            state.dispose_abandoned_on_owner()
            state.set_exception(error)

    bridge.add_done_callback(_consume_bridge_completion)
    try:
        # This returns as soon as the callback is queued and never waits for a
        # stalled owner run to execute it.
        trio_token.run_sync_soon(schedule)
    except BaseException as error:
        raise _AwaitableSchedulingError(
            "The credential provider could not be scheduled on its Trio run"
        ) from error

    try:
        return bridge.result(timeout=_CREDENTIAL_BRIDGE_TIMEOUT_SECONDS)
    except concurrent.futures.TimeoutError:
        state.abandon()
        try:
            trio_token.run_sync_soon(state.cancel_on_owner)
        except trio.RunFinishedError:
            # An accepted schedule callback is guaranteed to have run before
            # Trio exits, and runner completion is observed by bridge's callback.
            pass
        raise


def _run_async_credential_provider(provider, provider_owner, event_loop, trio_token):
    """Invoke a known async provider on its captured asyncio or Trio runtime."""
    if event_loop is not None:
        return _run_coroutine_on_asyncio_loop(
            _call_async_credential_provider(provider, provider_owner), event_loop
        )
    if trio_token is not None:
        return _run_coroutine_on_trio_loop(
            _call_async_credential_provider,
            (provider, provider_owner),
            trio_token,
        )
    raise RuntimeError(
        "Async credential providers require a running asyncio or Trio context"
    )


def _run_awaitable_result(awaitable, provider_owner, event_loop, trio_token):
    """Await a result returned by a nominally synchronous provider."""
    if event_loop is not None:
        try:
            return _run_coroutine_on_asyncio_loop(
                _await_credential_result(awaitable, provider_owner), event_loop
            )
        except _AwaitableSchedulingError:
            _dispose_awaitable(awaitable)
            raise
    if trio_token is not None:
        try:
            return _run_coroutine_on_trio_loop(
                _await_credential_result,
                (awaitable, provider_owner),
                trio_token,
                abandoned_cleanup=lambda: _dispose_awaitable(awaitable),
            )
        except _AwaitableSchedulingError:
            _dispose_awaitable(awaitable)
            raise
    _dispose_awaitable(awaitable)
    raise RuntimeError(
        "Credential provider returned an awaitable without an async client context"
    )


def _dispose_awaitable(awaitable) -> None:
    """Cancel or close an awaitable only before it belongs to an owner runtime."""
    cancel = getattr(awaitable, "cancel", None)
    if callable(cancel):
        cancel()
    close = getattr(awaitable, "close", None)
    if callable(close):
        close()


def create_credential_provider_callback(
    ffi,
    credential_provider_fn,
    *,
    event_loop=None,
    trio_token=None,
    allow_async=False,
    provider_owner=None,
):
    """Create a native callback for a custom AWS credential provider.

    The callback follows the native stateless tri-state protocol: 0 means
    failure, 1 means success, and 2 requests larger buffers. A provider can be
    called twice during large-value negotiation; no result is cached between
    those calls.

    Async clients must call this function in their owning async context and
    pass the running asyncio loop or Trio token. ``allow_async`` also permits a
    nominally synchronous provider to return an awaitable. Sync clients leave
    it false; rejected awaitables are closed or cancelled before failure.

    Returns:
        A ``(callback, owner)`` pair. The callback is typed NULL and the owner
        is None when no provider is configured. Otherwise callers must retain
        the owner while native callbacks are allowed; the trampoline itself
        retains only a weak reference to it.
    """
    if credential_provider_fn is None:
        return ffi.cast("CredentialProviderCallback", ffi.NULL), None

    import inspect

    callback_owner = _CredentialProviderCallbackOwner(
        credential_provider_fn,
        event_loop=event_loop,
        trio_token=trio_token,
        allow_async=allow_async,
        provider_owner=provider_owner,
    )
    callback_owner_ref = weakref.ref(callback_owner)

    def _credential_provider_callback(
        client_id,
        access_key_id_buf,
        access_key_id_buf_len,
        access_key_id_len_ptr,
        secret_access_key_buf,
        secret_access_key_buf_len,
        secret_access_key_len_ptr,
        session_token_buf,
        session_token_buf_len,
        session_token_len_ptr,
        expires_at_millis_ptr,
    ):
        owner = callback_owner_ref()
        if owner is None:
            return _CREDENTIAL_CALLBACK_FAILURE
        provider = owner.provider
        provider_marker = owner.provider_owner()
        _push_thread_provider_owner(provider_marker)
        try:
            owner_loop = owner.event_loop()
            if owner.is_async_callable:
                if not owner.allow_async:
                    raise TypeError(
                        "The sync client does not support async credential providers"
                    )
                credentials = _run_async_credential_provider(
                    provider,
                    provider_marker,
                    owner_loop,
                    owner.trio_token,
                )
            else:
                credentials = provider()
                if inspect.isawaitable(credentials):
                    if not owner.allow_async:
                        _dispose_awaitable(credentials)
                        raise TypeError(
                            "The sync credential provider returned an awaitable; "
                            "use a synchronous provider or the async client"
                        )
                    credentials = _run_awaitable_result(
                        credentials,
                        provider_marker,
                        owner_loop,
                        owner.trio_token,
                    )

            return _write_credentials_to_buffers(
                ffi,
                credentials,
                access_key_id_buf,
                access_key_id_buf_len,
                access_key_id_len_ptr,
                secret_access_key_buf,
                secret_access_key_buf_len,
                secret_access_key_len_ptr,
                session_token_buf,
                session_token_buf_len,
                session_token_len_ptr,
                expires_at_millis_ptr,
            )
        except BaseException as error:
            import logging

            logging.getLogger(__name__).warning(
                "IAM credential provider failed: %s", error
            )
            return _CREDENTIAL_CALLBACK_FAILURE
        finally:
            _pop_thread_provider_owner()

    callback = ffi.callback("CredentialProviderCallback", _credential_provider_callback)
    return callback, callback_owner


def _write_credentials_to_buffers(
    ffi,
    credentials,
    access_key_id_buf,
    access_key_id_buf_len,
    access_key_id_len_ptr,
    secret_access_key_buf,
    secret_access_key_buf_len,
    secret_access_key_len_ptr,
    session_token_buf,
    session_token_buf_len,
    session_token_len_ptr,
    expires_at_millis_ptr,
):
    """Validate, pre-encode, and atomically write one credential result."""
    from glide_shared.config import AwsCredentials

    if not isinstance(credentials, AwsCredentials):
        raise TypeError("credential_provider must return AwsCredentials")

    access_key_id = credentials.access_key_id
    secret_access_key = credentials.secret_access_key
    session_token = credentials.session_token
    expires_at = credentials.expires_at_epoch_millis

    if not isinstance(access_key_id, str) or not access_key_id.strip():
        raise ValueError("access_key_id must be a nonblank string")
    if not isinstance(secret_access_key, str) or not secret_access_key.strip():
        raise ValueError("secret_access_key must be a nonblank string")
    if session_token is not None and not isinstance(session_token, str):
        raise ValueError("session_token must be a string or None")
    if expires_at is not None and (
        not isinstance(expires_at, int)
        or isinstance(expires_at, bool)
        or expires_at < 0
        or expires_at > 2**63 - 1
    ):
        raise ValueError(
            "expires_at_epoch_millis must be a nonnegative signed 64-bit integer"
        )

    encoded_access_key_id = access_key_id.encode(ENCODING)
    encoded_secret_access_key = secret_access_key.encode(ENCODING)
    encoded_session_token = (
        session_token.encode(ENCODING) if session_token is not None else b""
    )
    encoded_fields = (
        encoded_access_key_id,
        encoded_secret_access_key,
        encoded_session_token,
    )
    lengths = tuple(len(field) for field in encoded_fields)

    if any(length > _MAX_CREDENTIAL_BYTES for length in lengths):
        raise ValueError("Each credential field must not exceed 1 MiB")
    if sum(lengths) > _MAX_CREDENTIAL_BYTES:
        raise ValueError("Aggregate credential fields must not exceed 1 MiB")

    access_key_id_len_ptr[0] = lengths[0]
    secret_access_key_len_ptr[0] = lengths[1]
    session_token_len_ptr[0] = lengths[2]

    capacities = (
        access_key_id_buf_len,
        secret_access_key_buf_len,
        session_token_buf_len,
    )
    if any(required > capacity for required, capacity in zip(lengths, capacities)):
        # Status 2 is metadata-only: do not touch credential bytes or expiry.
        return _CREDENTIAL_CALLBACK_BUFFER_TOO_SMALL

    # All validation and capacity checks completed before the first write.
    if encoded_access_key_id:
        ffi.memmove(access_key_id_buf, encoded_access_key_id, lengths[0])
    if encoded_secret_access_key:
        ffi.memmove(secret_access_key_buf, encoded_secret_access_key, lengths[1])
    if encoded_session_token:
        ffi.memmove(session_token_buf, encoded_session_token, lengths[2])
    expires_at_millis_ptr[0] = expires_at or 0
    return _CREDENTIAL_CALLBACK_SUCCESS


def handle_command_result(ffi, lib, command_result, response_handler):
    """Handle a synchronous CommandResult* from FFI.

    Args:
        ffi: The CFFI instance.
        lib: The FFI library.
        command_result: The CommandResult* pointer from FFI.
        response_handler: A callable that takes a response pointer and returns the parsed result.

    Returns:
        The parsed response value.

    Raises:
        ClosingError: If result is NULL.
        RequestError subclass: If the result contains an error.
    """
    from glide_shared.exceptions import ClosingError, get_request_error_class

    try:
        if command_result == ffi.NULL:
            raise ClosingError("Internal error: Received NULL as a command result")
        if command_result.command_error != ffi.NULL:
            error = ffi.cast("CommandError*", command_result.command_error)
            error_message = ffi.string(error.command_error_message).decode(ENCODING)
            error_class = get_request_error_class(error.command_error_type)
            raise error_class(error_message)
        else:
            return response_handler(command_result.response)
    finally:
        lib.free_command_result(command_result)


# Pubsub inline frame parsing
_PUBSUB_KIND_MAP = {0: "Disconnection", 3: "Message", 4: "PMessage", 5: "SMessage"}


def parse_inline_pubsub(payload: bytes):
    """Parse inline pubsub payload from pipe.

    Format: kind(4) msg_len(4) msg(...) ch_len(4) ch(...) pat_len(4) pat(...)
    """
    import sys

    off = 0
    kind = int.from_bytes(payload[off : off + 4], sys.byteorder, signed=True)
    off += 4
    msg_len = int.from_bytes(payload[off : off + 4], sys.byteorder, signed=False)
    off += 4
    message = payload[off : off + msg_len]
    off += msg_len
    ch_len = int.from_bytes(payload[off : off + 4], sys.byteorder, signed=False)
    off += 4
    channel = payload[off : off + ch_len]
    off += ch_len
    pat_len = int.from_bytes(payload[off : off + 4], sys.byteorder, signed=False)
    off += 4
    pattern = payload[off : off + pat_len] if pat_len > 0 else None
    kind_str = _PUBSUB_KIND_MAP.get(kind)
    return kind_str, message, channel, pattern
