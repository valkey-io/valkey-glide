# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import asyncio
import json
import threading
from typing import Callable, List, Optional

from glide_shared._glide_ffi import GlideFFI
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import GlideClientConfiguration


class MonitorClient:
    """
    An async client that streams all commands processed by the server via MONITOR.

    Must be used with a standalone (non-cluster) configuration.

    Warning: MONITOR is a debugging tool with performance implications.
    Do not use in production environments.
    """

    def __init__(self) -> None:
        self._ffi = GlideFFI.ffi
        self._lib = GlideFFI.lib
        self._core_client = self._ffi.NULL
        self._callback_ref = None
        self._queue: asyncio.Queue[MonitorMsg] = asyncio.Queue()
        self._is_closed = False
        self._stop_lock = threading.Lock()
        self._loop: Optional[asyncio.AbstractEventLoop] = None
        self._user_callback: Optional[Callable[[MonitorMsg], None]] = None

    @classmethod
    async def create(
        cls,
        config: GlideClientConfiguration,
        callback: Optional[Callable[[MonitorMsg], None]] = None,
    ) -> "MonitorClient":
        """
        Create a new async MonitorClient.

        Args:
            config: Standalone client configuration (must be GlideClientConfiguration).
            callback: Optional sync callback invoked for each MonitorMsg. If None,
                      messages are queued for get_monitor_message().

        Returns:
            A MonitorClient instance.
        """
        if not isinstance(config, GlideClientConfiguration):
            raise TypeError(
                "MonitorClient requires a GlideClientConfiguration (standalone only)"
            )
        instance = cls()
        instance._loop = asyncio.get_running_loop()
        instance._user_callback = callback
        conn_req = config._create_a_protobuf_conn_request(cluster_mode=False)
        conn_req_bytes = conn_req.SerializeToString()

        @instance._ffi.callback("MonitorCallback")
        def _monitor_callback(
            client_ptr,
            timestamp,
            db,
            client_addr_ptr,
            client_addr_len,
            command_ptr,
            command_len,
            args_json_ptr,
            args_json_len,
        ):
            try:
                client_addr = bytes(
                    instance._ffi.buffer(client_addr_ptr, client_addr_len)
                ).decode("utf-8", errors="replace")
                command = bytes(instance._ffi.buffer(command_ptr, command_len)).decode(
                    "utf-8", errors="replace"
                )
                args_json_str = bytes(
                    instance._ffi.buffer(args_json_ptr, args_json_len)
                ).decode("utf-8", errors="replace")
                try:
                    args: List[str] = (
                        json.loads(args_json_str) if args_json_len > 0 else []
                    )
                except (json.JSONDecodeError, ValueError):
                    args = []
                msg = MonitorMsg(
                    timestamp=timestamp,
                    db=db,
                    client_addr=client_addr,
                    command=command,
                    args=args,
                )
                if instance._user_callback is not None:
                    instance._user_callback(msg)
                elif instance._loop is not None and not instance._loop.is_closed():
                    instance._loop.call_soon_threadsafe(instance._queue.put_nowait, msg)
            except Exception:
                pass  # Suppress to avoid crashing the FFI layer

        instance._callback_ref = _monitor_callback
        client_response = instance._lib.create_monitor_client(
            conn_req_bytes, len(conn_req_bytes), _monitor_callback
        )
        if client_response == instance._ffi.NULL:
            raise RuntimeError("Failed to create monitor client: null response")
        if client_response.connection_error_message != instance._ffi.NULL:
            error = instance._ffi.string(
                client_response.connection_error_message
            ).decode()
            instance._lib.free_connection_response(client_response)
            raise RuntimeError(f"Failed to create monitor client: {error}")
        instance._core_client = client_response.conn_ptr
        instance._lib.free_connection_response(client_response)
        return instance

    async def get_monitor_message(self) -> MonitorMsg:
        """Wait for and return the next MonitorMsg."""
        return await self._queue.get()

    def try_get_monitor_message(self) -> Optional[MonitorMsg]:
        """Non-blocking retrieval. Returns None if queue is empty."""
        try:
            return self._queue.get_nowait()
        except asyncio.QueueEmpty:
            return None

    async def stop(self) -> None:
        """Stop monitoring and release resources."""
        with self._stop_lock:
            if self._is_closed:
                return
            self._is_closed = True
            core_client, self._core_client = self._core_client, self._ffi.NULL
        if core_client != self._ffi.NULL:
            self._lib.close_monitor_client(core_client)
        self._callback_ref = None

    async def aclose(self) -> None:
        """Alias for stop()."""
        await self.stop()

    async def __aenter__(self) -> "MonitorClient":
        return self

    async def __aexit__(self, *args) -> None:
        await self.stop()
