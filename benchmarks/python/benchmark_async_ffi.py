# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Benchmark: Compare sync-FFI and async-FFI clients.

Usage:
    # Start a valkey server first:
    valkey-server --save '' --port 6379 --logfile ''

    # Run from the python/ directory with the venv activated:
    cd ~/gh/jduo/valkey-glide/python
    source .env/bin/activate
    python ../benchmarks/python/benchmark_async_ffi.py
"""

import asyncio
import sys
import time

N_ITERATIONS = 10_000
KEY = "bench_key"
VALUE = "bench_value"
HOST = "localhost"
PORT = 6379


async def bench_glide_async_set():
    """Async client (direct FFI)"""
    from glide import GlideClient, GlideClientConfiguration, NodeAddress

    config = GlideClientConfiguration([NodeAddress(HOST, PORT)])
    client = await GlideClient.create(config)

    start = time.perf_counter()
    for _ in range(N_ITERATIONS):
        await client.set(KEY, VALUE)
    elapsed = time.perf_counter() - start
    print(f"glide async (FFI) SET: {elapsed:.4f}s  ({N_ITERATIONS/elapsed:.0f} ops/s)")

    await client.close()


def bench_glide_sync_set():
    """Sync client (FFI blocking)"""
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    config = GlideClientConfiguration([NodeAddress(HOST, PORT)])
    client = GlideClient.create(config)

    start = time.perf_counter()
    for _ in range(N_ITERATIONS):
        client.set(KEY, VALUE)
    elapsed = time.perf_counter() - start
    print(f"glide sync (FFI)  SET: {elapsed:.4f}s  ({N_ITERATIONS/elapsed:.0f} ops/s)")

    client.close()


async def bench_glide_async_get():
    """Async FFI GET benchmark"""
    from glide import GlideClient, GlideClientConfiguration, NodeAddress

    config = GlideClientConfiguration([NodeAddress(HOST, PORT)])
    client = await GlideClient.create(config)

    await client.set(KEY, VALUE)

    start = time.perf_counter()
    for _ in range(N_ITERATIONS):
        await client.get(KEY)
    elapsed = time.perf_counter() - start
    print(f"glide async (FFI) GET: {elapsed:.4f}s  ({N_ITERATIONS/elapsed:.0f} ops/s)")

    await client.close()


def bench_glide_sync_get():
    """Sync FFI GET benchmark"""
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    config = GlideClientConfiguration([NodeAddress(HOST, PORT)])
    client = GlideClient.create(config)

    client.set(KEY, VALUE)

    start = time.perf_counter()
    for _ in range(N_ITERATIONS):
        client.get(KEY)
    elapsed = time.perf_counter() - start
    print(f"glide sync (FFI)  GET: {elapsed:.4f}s  ({N_ITERATIONS/elapsed:.0f} ops/s)")

    client.close()


async def main():
    print(f"Python {sys.version}")
    print(f"Iterations: {N_ITERATIONS}")
    print(f"Server: {HOST}:{PORT}")
    print()

    print("=== SET benchmark ===")
    bench_glide_sync_set()
    await bench_glide_async_set()

    print()
    print("=== GET benchmark ===")
    bench_glide_sync_get()
    await bench_glide_async_get()


if __name__ == "__main__":
    asyncio.run(main())
