# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Shared FFI helper utilities for converting Python arguments to C-compatible arrays."""

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
    route_type = 2  # Random
    slot_id = 0
    slot_type = 0  # Primary
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
        slot_key_bytes = route.slot_key.encode(ENCODING) + b"\0"
        refs.append(slot_key_bytes)
        slot_key_ptr = ffi.from_buffer(slot_key_bytes)
        slot_type = 0 if route.slot_type == SlotType.PRIMARY else 1
    elif isinstance(route, ByAddressRoute):
        route_type = 5
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
    """Create an AddressResolverCallback for the FFI from a Python resolver function.

    Args:
        ffi: The CFFI instance.
        resolver_fn: A callable(host: str, port: int) -> (resolved_host: str, resolved_port: int).

    Returns:
        The CFFI callback object. Caller must keep a reference to prevent GC.
    """
    if resolver_fn is None:
        return ffi.NULL

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
            host = ffi.buffer(host_ptr, host_len)[:].decode(ENCODING)
            resolved_host, resolved_port = resolver_fn(host, port)
            encoded_host = resolved_host.encode(ENCODING)
            write_len = min(len(encoded_host), resolved_host_buf_len)
            ffi.memmove(resolved_host_buf, encoded_host, write_len)
            resolved_host_len_ptr[0] = write_len
            return resolved_port
        except Exception:
            return 0

    return ffi.callback("AddressResolverCallback", _address_resolver_callback)


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
