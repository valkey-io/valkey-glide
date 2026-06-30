# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""FFI wrappers that inject the package's shared FFI instance into glide_shared
base classes. Uses the single _ASYNC_FFI from _ffi_instance (same one glide_client uses).
"""

from glide._ffi_instance import _ASYNC_FFI
from glide_shared.cluster_scan_cursor import ClusterScanCursor as _ClusterScanCursorBase
from glide_shared.script import Script as _ScriptBase


class Script(_ScriptBase):
    """Script using the async package's FFI instance."""

    def __init__(self, code):
        super().__init__(code, _ffi=_ASYNC_FFI.ffi, _lib=_ASYNC_FFI.lib)


class ClusterScanCursor(_ClusterScanCursorBase):
    """ClusterScanCursor using the async package's FFI instance."""

    def __init__(self, new_cursor=None):
        super().__init__(new_cursor, _ffi=_ASYNC_FFI.ffi, _lib=_ASYNC_FFI.lib)
