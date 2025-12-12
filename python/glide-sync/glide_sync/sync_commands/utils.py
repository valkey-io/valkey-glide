# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from .._glide_ffi import _GlideFFI


def get_min_compressed_size() -> int:
    """
    Get the minimum compression size in bytes.

    Returns the minimum size threshold for compression. Values smaller than this
    will not be compressed.

    Returns:
        int: The minimum compression size in bytes (currently 6 bytes: 5-byte header + 1 byte data)
    """
    _glide_ffi = _GlideFFI()
    return _glide_ffi.lib.get_min_compressed_size()
