# Copyright Valkey GLIDE Project Contributors - SPDX-Identifier: Apache-2.0

import asyncio
from typing import Callable, List, Optional

from glide.glide import close_monitor_client_external, create_monitor_client_external
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
        self._handle_id: Optional[int] = None
        self._queue: asyncio.Queue[MonitorMsg] = asyncio.Queue()
        self._is_closed = False
        self._stop_lock = asyncio.Lock()
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

        def _on_monitor_line(
            timestamp: float,
            db: int,
            client_addr: str,
            command: str,
            args: List[str],
        ) -> None:
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

        loop = asyncio.get_running_loop()
        handle_id = await loop.run_in_executor(
            None, create_monitor_client_external, conn_req_bytes, _on_monitor_line
        )
        instance._handle_id = handle_id
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
        async with self._stop_lock:
            if self._is_closed:
                return
            self._is_closed = True
        if self._handle_id is not None:
            handle_id = self._handle_id
            self._handle_id = None
            loop = asyncio.get_running_loop()
            await loop.run_in_executor(None, close_monitor_client_external, handle_id)

    async def aclose(self) -> None:
        """Alias for stop()."""
        await self.stop()

    async def __aenter__(self) -> "MonitorClient":
        return self

    async def __aexit__(self, *args) -> None:
        await self.stop()
