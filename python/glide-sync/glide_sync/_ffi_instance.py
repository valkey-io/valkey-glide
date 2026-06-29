# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Single FFI instance shared by all modules in the sync package."""

from glide_shared._glide_ffi import _GlideFFI

_SYNC_FFI = _GlideFFI()
