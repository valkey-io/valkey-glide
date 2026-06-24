# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from glide_shared._glide_ffi import _GlideFFI
from glide_shared.cluster_scan_cursor import ClusterScanCursor as _ClusterScanCursorBase

_module_ffi = _GlideFFI()


class ClusterScanCursor(_ClusterScanCursorBase):
    """ClusterScanCursor wrapper using the sync module's FFI instance."""

    def __init__(self, new_cursor=None):
        super().__init__(new_cursor, _ffi=_module_ffi.ffi, _lib=_module_ffi.lib)
