# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import argparse
import asyncio
import socket
import threading
from contextlib import contextmanager
from typing import Iterator


def unused_local_port() -> int:
    """Return a currently unused loopback port for connection-failure tests."""
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


@contextmanager
def blackhole_server() -> Iterator[int]:
    """Accept TCP connections without responding, then close them on exit."""
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen()
    listener.settimeout(0.05)
    connections: list[socket.socket] = []
    stopped = threading.Event()

    def accept_connections() -> None:
        """Retain accepted sockets so client requests remain in flight."""
        while not stopped.is_set():
            try:
                connection, _ = listener.accept()
                connections.append(connection)
            except socket.timeout:
                continue
            except OSError:
                break

    thread = threading.Thread(target=accept_connections, daemon=True)
    thread.start()
    try:
        yield int(listener.getsockname()[1])
    finally:
        stopped.set()
        listener.close()
        for connection in connections:
            connection.close()
        thread.join(timeout=1)


def assert_command_response_layout() -> None:
    """Verify CFFI uses the same 64-bit CommandResponse ABI as Rust."""
    from glide_sync._ffi_instance import _SYNC_FFI

    ffi = _SYNC_FFI.ffi
    assert ffi.sizeof("CommandResponse") == 104
    assert ffi.offsetof("CommandResponse", "int_value") == 8
    assert ffi.offsetof("CommandResponse", "string_value_len") == 40
    assert ffi.offsetof("CommandResponse", "array_value_len") == 56
    assert ffi.offsetof("CommandResponse", "sets_value_len") == 88


def assert_statistics_layout() -> None:
    """Verify the statistics ABI preserves values above the Windows ULONG limit."""
    from glide_shared._glide_ffi import _GlideFFI

    ffi = _GlideFFI().ffi
    assert ffi.sizeof("Statistics") == 80
    statistics = ffi.new("Statistics*")
    for index, (name, field) in enumerate(ffi.typeof("Statistics").fields):
        assert ffi.sizeof(field.type) == 8
        assert ffi.offsetof("Statistics", name) == index * 8
        setattr(statistics, name, 2**32 + 123)
        assert getattr(statistics, name) == 2**32 + 123


def smoke_sync_command(port: int) -> None:
    """Execute a sync command and verify its expected connection error."""
    from glide_sync import (
        AdvancedGlideClientConfiguration,
        GlideClient,
        GlideClientConfiguration,
        NodeAddress,
        RequestError,
    )

    client = GlideClient.create(
        GlideClientConfiguration(
            [NodeAddress("127.0.0.1", port)],
            request_timeout=500,
            advanced_config=AdvancedGlideClientConfiguration(connection_timeout=100),
            lazy_connect=True,
        )
    )
    try:
        try:
            client.ping()
        except RequestError:
            pass
        else:
            raise AssertionError("Sync PING unexpectedly succeeded without a server")
    finally:
        client.close()


async def smoke_async_command(port: int) -> None:
    """Execute an async command and verify its expected connection error."""
    from glide import (
        AdvancedGlideClientConfiguration,
        GlideClient,
        GlideClientConfiguration,
        NodeAddress,
        RequestError,
    )

    client = await GlideClient.create(
        GlideClientConfiguration(
            [NodeAddress("127.0.0.1", port)],
            request_timeout=500,
            advanced_config=AdvancedGlideClientConfiguration(connection_timeout=100),
            lazy_connect=True,
        )
    )
    try:
        try:
            await client.ping()
        except RequestError:
            pass
        else:
            raise AssertionError("Async PING unexpectedly succeeded without a server")
    finally:
        await client.close()


async def smoke_async_close_with_pending_commands() -> None:
    """Close an async client while its Windows response thread has pending commands."""
    from glide import (
        AdvancedGlideClientConfiguration,
        GlideClient,
        GlideClientConfiguration,
        NodeAddress,
    )

    with blackhole_server() as port:
        client = await GlideClient.create(
            GlideClientConfiguration(
                [NodeAddress("127.0.0.1", port)],
                request_timeout=2000,
                advanced_config=AdvancedGlideClientConfiguration(
                    connection_timeout=2000
                ),
                lazy_connect=True,
            )
        )
        commands = [asyncio.create_task(client.ping()) for _ in range(8)]
        await asyncio.sleep(0.05)
        await client.close()
        results = await asyncio.gather(*commands, return_exceptions=True)
        assert all(isinstance(result, BaseException) for result in results)


def main() -> None:
    """Validate installed Windows wheels without requiring a Valkey server."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--client", choices=("async", "sync"), required=True)
    args = parser.parse_args()
    assert_statistics_layout()
    port = unused_local_port()
    if args.client == "sync":
        assert_command_response_layout()
        smoke_sync_command(port)
    else:
        asyncio.run(smoke_async_command(port))
        asyncio.run(smoke_async_close_with_pending_commands())
    print(f"Windows {args.client} wheel smoke test passed")


if __name__ == "__main__":
    main()
