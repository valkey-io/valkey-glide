# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from typing import Union

from glide_shared.exceptions import RequestError

from ._glide_ffi import _GlideFFI

ENCODING = "utf-8"


class Script:
    """
    Represents a Lua script that can be executed on Redis/Valkey servers.

    Each Script instance is independent, but they all use the shared GlideFFI singleton
    for FFI operations. The script is automatically stored in the script cache when created
    and removed when the Script object is garbage collected.
    """

    def __init__(self, code: Union[str, bytes]):
        """
        Create a new Script instance and store it in the script cache.

        Args:
            code: The Lua script code as a string or bytes

        Raises:
            TypeError: If code is not a string or bytes
            RequestError: If script storage fails
        """
        self._glide_ffi = _GlideFFI()
        self._ffi = self._glide_ffi.ffi
        self._lib = self._glide_ffi.lib
        self._hash = None

        # Convert code to bytes if it's a string
        if isinstance(code, str):
            script_bytes = code.encode(ENCODING)
        elif isinstance(code, bytes):
            script_bytes = code
        else:
            raise TypeError("code must be either a string or bytes")

        # Store script in cache
        script_buffer = self._ffi.from_buffer(script_bytes)
        hash_buffer_ptr = self._lib.store_script(script_buffer, len(script_bytes))

        if hash_buffer_ptr == self._ffi.NULL:
            raise RequestError(
                "Failed to store script - got null pointer from the `store script` function"
            )

        try:
            hash_buffer = self._ffi.cast("ScriptHashBuffer*", hash_buffer_ptr)

            # Extract the hash string from the buffer§
            hash_bytes = self._ffi.buffer(hash_buffer.ptr, hash_buffer.len)[:]
            self.hash = hash_bytes.decode(ENCODING)
        finally:
            self._lib.free_script_hash_buffer(hash_buffer_ptr)

    def get_hash(self) -> str:
        """
        Get the SHA1 hash of the script.

        Returns:
            str: The SHA1 hash of the script
        """
        return self.hash

    def __del__(self):
        """
        Clean up the script from the cache when the object is garbage collected.
        """
        try:
            hash_bytes = self.hash.encode(ENCODING)
            hash_buffer = self._ffi.from_buffer(hash_bytes)
            error_ptr = self._lib.drop_script(hash_buffer, len(hash_bytes))

            if error_ptr != self._ffi.NULL:
                try:
                    error_msg = self._ffi.string(error_ptr).decode(ENCODING)
                    print(f"Error dropping script: {error_msg}")
                finally:
                    self._lib.free_drop_script_error(error_ptr)

        except Exception as e:
            print(f"Error during script cleanup: {e}")

    def __str__(self) -> str:
        """String representation showing the script hash."""
        if self._hash is None:
            return "Script(destroyed)"
        return f"Script(hash={self._hash})"
