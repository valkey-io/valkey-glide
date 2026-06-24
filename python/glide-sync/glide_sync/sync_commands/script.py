# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from glide_shared._glide_ffi import _GlideFFI
from glide_shared.script import Script as _ScriptBase

_module_ffi = _GlideFFI()


class Script(_ScriptBase):
    """Script wrapper using the sync module's FFI instance."""

    def __init__(self, code):
        super().__init__(code, _ffi=_module_ffi.ffi, _lib=_module_ffi.lib)
