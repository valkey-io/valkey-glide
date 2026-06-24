# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Package-specific FFI wrappers. Each package maintains its own CFFI instance
to isolate Python-side CFFI state (type cache, callbacks) between async and sync
clients. The underlying Rust shared library is process-global."""

from glide_shared._glide_ffi import _GlideFFI
from glide_shared.cluster_scan_cursor import ClusterScanCursor as _ClusterScanCursorBase
from glide_shared.script import Script as _ScriptBase

_module_ffi = _GlideFFI()


class Script(_ScriptBase):
    """Script using the async module's FFI instance."""

    def __init__(self, code):
        super().__init__(code, _ffi=_module_ffi.ffi, _lib=_module_ffi.lib)


class ClusterScanCursor(_ClusterScanCursorBase):
    """ClusterScanCursor using the async module's FFI instance."""

    def __init__(self, new_cursor=None):
        super().__init__(new_cursor, _ffi=_module_ffi.ffi, _lib=_module_ffi.lib)
