# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import json
import threading
import time
from typing import Callable, List, Optional

from glide_shared._glide_ffi import GlideFFI
from glide_shared.commands.core_options import MonitorMsg
from glide_shared.config import GlideClientConfiguration


class MonitorClient:
    """
    A client that streams all commands processed by the server via the MONITOR command.

    Must be used with a standalone (non-cluster) configuration.

    Warning: MONITOR is a debugging tool with performance implications.
    Do not use in production environments.
    """

    def __init__(self) -> None:
        self._ffi = GlideFFI.ffi
        self._lib = GlideFFI.lib
        self._core_client = self._ffi.NULL
        self._callback_ref = None
        self._queue: List[MonitorMsg] = []
        self._lock = threading.Lock()
        self._condition = threading.Condition(self._lock)
        self._is_closed = False
        self._user_callback: Optional[Callable[[MonitorMsg], None]] = None

    @classmethod
    def create(
        cls,
        config: GlideClientConfiguration,
        callback: Optional[Callable[[MonitorMsg], None]] = None,
    ) -> "MonitorClient":
        """
        Create a new MonitorClient connected to the server.

        Args:
            config: Standalone client configuration (must be GlideClientConfiguration).
            callback: Optional callback invoked for each MonitorMsg. If None, messages
                      are queued and retrievable via get_monitor_message().

        Returns:
            A MonitorClient instance.
        """
        if not isinstance(config, GlideClientConfiguration):
            raise TypeError(
                "MonitorClient requires a GlideClientConfiguration (standalone only)"
            )
        instance = cls()
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
                else:
                    with instance._condition:
                        instance._queue.append(msg)
                        instance._condition.notify()
            except Exception:
                pass  # Suppress callback errors to avoid crashing the Rust FFI layer

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

    def get_monitor_message(
        self, timeout: Optional[float] = None
    ) -> Optional[MonitorMsg]:
        """
        Block until a MonitorMsg is available, then return it.

        Args:
            timeout: Optional timeout in seconds. Returns None on timeout or if closed.
        """
        deadline = time.monotonic() + timeout if timeout is not None else None
        with self._condition:
            while not self._queue and not self._is_closed:
                remaining = None
                if deadline is not None:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        break
                self._condition.wait(timeout=remaining)
            return self._queue.pop(0) if self._queue else None

    def try_get_monitor_message(self) -> Optional[MonitorMsg]:
        """Non-blocking retrieval. Returns None if no message is available."""
        with self._condition:
            return self._queue.pop(0) if self._queue else None

    def close(self) -> None:
        """Stop monitoring and release resources."""
        with self._condition:
            if self._is_closed:
                return
            self._is_closed = True
            self._condition.notify_all()
        if self._core_client != self._ffi.NULL:
            client = self._core_client
            self._core_client = self._ffi.NULL
            self._lib.close_monitor_client(client)
        self._callback_ref = None

    def stop(self) -> None:
        """Alias for close(). Stop monitoring and release resources."""
        self.close()

    def __enter__(self) -> "MonitorClient":
        return self

    def __exit__(self, *args) -> None:
        self.close()
