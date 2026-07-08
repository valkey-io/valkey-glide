# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Single FFI instance shared by all modules in the async package.
Avoids creating redundant FFI instances while keeping glide_client and
_ffi_wrappers decoupled (no circular imports)."""

from glide_shared._glide_ffi import _GlideFFI

_ASYNC_FFI = _GlideFFI()
