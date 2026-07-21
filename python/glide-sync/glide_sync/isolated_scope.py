# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Isolated Execution Scope for the synchronous GLIDE client.

Provides a dedicated connection for operations requiring per-connection state:
WATCH/MULTI/EXEC, CLIENT TRACKING, blocking commands, and pub/sub without
interference from other callers on the shared multiplexed connection.

Usage:
    from glide_sync import GlideClient
    from glide_sync.config import GlideClientConfiguration, NodeAddress

    client = GlideClient.create(GlideClientConfiguration([NodeAddress("localhost", 6379)]))

    with client.scoped_connection() as scope:
        scope.watch("counter")
        val = scope.get("counter")
        scope.multi()
        scope.set("counter", str(int(val) + 1))
        result = scope.exec()
"""

import struct
import threading
from typing import Optional

from glide_shared._glide_ffi import _GlideFFI


class IsolatedScope:
    """
    A borrowed dedicated connection for operations requiring per-connection state.

    Commands bypass the multiplexer and execute on a single TCP connection.
    Enables WATCH/MULTI/EXEC, CLIENT TRACKING, blocking commands, and pub/sub
    without interference from other callers.

    Thread Safety: A single IsolatedScope is NOT thread-safe (serial execution).
    Multiple threads should each acquire their own scope via client.scoped_connection().

    Use as a context manager for automatic release:
        with client.scoped_connection() as scope:
            scope.watch("key")
            ...
    """

    __slots__ = (
        "_scope_id",
        "_client_id",
        "_ffi",
        "_lib",
        "_released",
        "_response_parser",
        "_owner_thread",
    )

    def __init__(
        self, scope_id: int, client_id: int, ffi_instance: _GlideFFI, response_parser
    ):
        self._scope_id = scope_id
        self._client_id = client_id
        self._ffi = ffi_instance.ffi
        self._lib = ffi_instance.lib
        self._released = False
        self._response_parser = response_parser
        self._owner_thread = threading.current_thread().ident

    @property
    def scope_id(self) -> int:
        """The scope identifier handle."""
        if self._released:
            raise RuntimeError("Scope already released")
        return self._scope_id

    @property
    def is_released(self) -> bool:
        """Whether the scope has been returned to the pool."""
        return self._released

    # ═══════════════════════════════════════════════════════════════════════════
    # COMMANDS
    # ═══════════════════════════════════════════════════════════════════════════

    def watch(self, *keys: str) -> Optional[str]:
        """WATCH one or more keys for optimistic locking."""
        return self._cmd("WATCH", *keys)

    def unwatch(self) -> Optional[str]:
        """Discard all watched keys."""
        return self._cmd("UNWATCH")

    def multi(self) -> Optional[str]:
        """Begin a transaction block."""
        return self._cmd("MULTI")

    def exec(self) -> Optional[str]:
        """Execute the transaction (returns None if WATCH detected a conflict)."""
        return self._cmd("EXEC")

    def discard(self) -> Optional[str]:
        """Discard the transaction."""
        return self._cmd("DISCARD")

    def get(self, key: str) -> Optional[str]:
        """GET a key value."""
        return self._cmd("GET", key)

    def set(self, key: str, value: str) -> Optional[str]:
        """SET a key to a value."""
        return self._cmd("SET", key, value)

    def incr(self, key: str) -> Optional[str]:
        """Increment a key's integer value by 1."""
        return self._cmd("INCR", key)

    def ping(self, message: Optional[str] = None) -> Optional[str]:
        """PING the server."""
        if message:
            return self._cmd("PING", message)
        return self._cmd("PING")

    def select(self, db: int) -> Optional[str]:
        """SELECT a database by index."""
        return self._cmd("SELECT", str(db))

    def execute_command(self, command: str, *args: str) -> Optional[str]:
        """Execute an arbitrary command on this scope."""
        return self._cmd(command, *args)

    # ═══════════════════════════════════════════════════════════════════════════
    # INTERNAL
    # ═══════════════════════════════════════════════════════════════════════════

    def _cmd(self, command: str, *args: str) -> Optional[str]:
        """Serialize and execute a command on the scoped connection."""
        if self._released:
            raise RuntimeError("Scope already released")

        # Detect cross-thread usage — scopes are NOT thread-safe
        current = threading.current_thread().ident
        if current != self._owner_thread:
            raise RuntimeError(
                f"IsolatedScope used from thread {current} but was acquired on thread "
                f"{self._owner_thread}. Each thread must acquire its own scope."
            )

        # Serialize command in wire format (little-endian):
        # [4:cmd_len][cmd_bytes][4:num_args][4:arg1_len][arg1_bytes]...
        cmd_bytes = command.encode("utf-8")
        parts = [
            struct.pack("<I", len(cmd_bytes)),
            cmd_bytes,
            struct.pack("<I", len(args)),
        ]
        for arg in args:
            arg_bytes = arg.encode("utf-8")
            parts.append(struct.pack("<I", len(arg_bytes)))
            parts.append(arg_bytes)

        payload = b"".join(parts)

        # Call FFI
        buf = self._ffi.from_buffer(payload)
        result_ptr = self._lib.glide_scope_execute(
            self._scope_id,
            self._ffi.cast("const uint8_t*", buf),
            len(payload),
        )

        if result_ptr == self._ffi.NULL:
            raise RuntimeError(f"Scope execute failed: invalid scope {self._scope_id}")

        try:
            return self._parse_result(result_ptr)
        finally:
            self._lib.free_command_result(result_ptr)

    def _parse_result(self, result_ptr) -> Optional[str]:
        """Parse a CommandResult pointer into a Python value."""
        result = result_ptr

        # Check for error
        if result.command_error != self._ffi.NULL:
            err = result.command_error
            msg = self._ffi.string(err.command_error_message).decode("utf-8")
            raise RuntimeError(f"Scope command error: {msg}")

        # Parse response
        if result.response == self._ffi.NULL:
            return None

        return self._response_parser(result.response)

    # ═══════════════════════════════════════════════════════════════════════════
    # CONTEXT MANAGER
    # ═══════════════════════════════════════════════════════════════════════════

    def close(self):
        """Release the scope back to the pool."""
        if not self._released:
            self._released = True
            self._lib.glide_scope_release(self._scope_id, self._client_id)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False

    def __del__(self):
        """Safety net — release on GC if not explicitly closed."""
        if not self._released:
            try:
                self.close()
            except Exception:
                pass
