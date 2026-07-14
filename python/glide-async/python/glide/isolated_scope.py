# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Async Isolated Execution Scope for the GLIDE async client (Feature 2).

Provides a dedicated connection for operations requiring per-connection state:
WATCH/MULTI/EXEC, CLIENT TRACKING, blocking commands. Commands bypass the
multiplexer and execute on a single TCP connection.

Usage:
    from glide import GlideClient
    from glide.config import GlideClientConfiguration, NodeAddress

    client = await GlideClient.create(GlideClientConfiguration([NodeAddress("localhost", 6379)]))

    async with client.scoped_connection() as scope:
        await scope.watch("counter")
        val = await scope.get("counter")
        await scope.multi()
        await scope.set("counter", str(int(val) + 1))
        result = await scope.exec()
"""

import asyncio
import struct
from typing import Optional

from glide_shared._glide_ffi import _GlideFFI


class AsyncIsolatedScope:
    """
    Async wrapper around a dedicated scoped connection (Feature 2).

    Commands are dispatched to a thread pool executor (since the underlying
    FFI call blocks on Tokio), keeping the asyncio event loop free.

    Thread Safety: A single AsyncIsolatedScope should only be used from one
    coroutine at a time. Multiple coroutines should each acquire their own scope.

    Use as an async context manager:
        async with client.scoped_connection() as scope:
            await scope.watch("key")
            ...
    """

    __slots__ = (
        "_scope_id",
        "_client_id",
        "_ffi",
        "_lib",
        "_released",
        "_response_parser",
        "_executor",
    )

    def __init__(
        self,
        scope_id: int,
        client_id: int,
        ffi_instance: _GlideFFI,
        response_parser,
        executor=None,
    ):
        self._scope_id = scope_id
        self._client_id = client_id
        self._ffi = ffi_instance.ffi
        self._lib = ffi_instance.lib
        self._released = False
        self._response_parser = response_parser
        self._executor = executor  # ThreadPoolExecutor or None (uses default)

    @property
    def scope_id(self) -> int:
        if self._released:
            raise RuntimeError("Scope already released")
        return self._scope_id

    @property
    def is_released(self) -> bool:
        return self._released

    # ═══════════════════════════════════════════════════════════════════════════
    # COMMANDS (all async)
    # ═══════════════════════════════════════════════════════════════════════════

    async def watch(self, *keys: str) -> Optional[str]:
        """WATCH one or more keys for optimistic locking."""
        return await self._cmd("WATCH", *keys)

    async def unwatch(self) -> Optional[str]:
        """Discard all watched keys."""
        return await self._cmd("UNWATCH")

    async def multi(self) -> Optional[str]:
        """Begin a transaction block."""
        return await self._cmd("MULTI")

    async def exec(self) -> Optional[str]:
        """Execute the transaction (returns None if WATCH detected a conflict)."""
        return await self._cmd("EXEC")

    async def discard(self) -> Optional[str]:
        """Discard the transaction."""
        return await self._cmd("DISCARD")

    async def get(self, key: str) -> Optional[str]:
        """GET a key value."""
        return await self._cmd("GET", key)

    async def set(self, key: str, value: str) -> Optional[str]:
        """SET a key to a value."""
        return await self._cmd("SET", key, value)

    async def incr(self, key: str) -> Optional[str]:
        """Increment a key's integer value by 1."""
        return await self._cmd("INCR", key)

    async def ping(self, message: Optional[str] = None) -> Optional[str]:
        """PING the server."""
        if message:
            return await self._cmd("PING", message)
        return await self._cmd("PING")

    async def select(self, db: int) -> Optional[str]:
        """SELECT a database by index."""
        return await self._cmd("SELECT", str(db))

    async def execute_command(self, command: str, *args: str) -> Optional[str]:
        """Execute an arbitrary command on this scope."""
        return await self._cmd(command, *args)

    # ═══════════════════════════════════════════════════════════════════════════
    # INTERNAL
    # ═══════════════════════════════════════════════════════════════════════════

    async def _cmd(self, command: str, *args: str) -> Optional[str]:
        """Serialize and execute a command on the scoped connection (async)."""
        if self._released:
            raise RuntimeError("Scope already released")

        # Serialize in wire format
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

        # Execute in thread pool to avoid blocking the event loop
        loop = asyncio.get_running_loop()
        result_ptr = await loop.run_in_executor(
            self._executor, self._execute_sync, payload
        )

        if result_ptr == self._ffi.NULL:
            raise RuntimeError(f"Scope execute failed: invalid scope {self._scope_id}")

        try:
            return self._parse_result(result_ptr)
        finally:
            self._lib.free_command_result(result_ptr)

    def _execute_sync(self, payload: bytes):
        """Synchronous FFI call (runs in executor thread)."""
        buf = self._ffi.from_buffer(payload)
        return self._lib.glide_scope_execute(
            self._scope_id,
            self._ffi.cast("const uint8_t*", buf),
            len(payload),
        )

    def _parse_result(self, result_ptr) -> Optional[str]:
        """Parse a CommandResult pointer into a Python value."""
        result = result_ptr

        if result.command_error != self._ffi.NULL:
            err = result.command_error
            msg = self._ffi.string(err.command_error_message).decode("utf-8")
            raise RuntimeError(f"Scope command error: {msg}")

        if result.response == self._ffi.NULL:
            return None

        return self._response_parser(result.response)

    # ═══════════════════════════════════════════════════════════════════════════
    # ASYNC CONTEXT MANAGER
    # ═══════════════════════════════════════════════════════════════════════════

    async def close(self):
        """Release the scope back to the pool."""
        if not self._released:
            self._released = True
            self._lib.glide_scope_release(self._scope_id, self._client_id)

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.close()
        return False

    def __del__(self):
        """Safety net — release on GC if not explicitly closed."""
        if not self._released:
            try:
                self._released = True
                self._lib.glide_scope_release(self._scope_id, self._client_id)
            except Exception:
                pass
