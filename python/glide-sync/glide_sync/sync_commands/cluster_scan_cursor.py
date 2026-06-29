# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from glide_shared.cluster_scan_cursor import ClusterScanCursor as _ClusterScanCursorBase
from glide_sync._ffi_instance import _SYNC_FFI


class ClusterScanCursor(_ClusterScanCursorBase):
    """ClusterScanCursor using the sync package's FFI instance."""

    def __init__(self, new_cursor=None):
        super().__init__(new_cursor, _ffi=_SYNC_FFI.ffi, _lib=_SYNC_FFI.lib)
