# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from glide_shared.script import Script as _ScriptBase
from glide_sync._ffi_instance import _SYNC_FFI


class Script(_ScriptBase):
    """Script using the sync package's FFI instance."""

    def __init__(self, code):
        super().__init__(code, _ffi=_SYNC_FFI.ffi, _lib=_SYNC_FFI.lib)
