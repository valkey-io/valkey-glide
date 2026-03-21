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
