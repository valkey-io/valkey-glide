# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Comprehensive benchmark: all client variants including redis-py baseline.
Reproduces the scenarios from issues #5083 and #5624.

Usage:
    valkey-server --save '' --port 6379 --logfile '' --daemonize yes
    cd ~/gh/jduo/valkey-glide/python && source .env/bin/activate
    python ../benchmarks/python/benchmark_comprehensive.py
"""

import asyncio
import sys
import time

import redis
import redis.asyncio as redis_async

N = 10_000
KEY = "bench_key"
VALUE = "bench_value"
HOST = "localhost"
PORT = 6379


def fmt(name, elapsed, n=N):
    return f"  {name:<35s} {elapsed:.4f}s  ({n/elapsed:>8.0f} ops/s)"


# ==================== SET benchmarks ====================


def redis_sync_set():
    with redis.Redis(HOST, PORT) as c:
        t = time.perf_counter()
        for _ in range(N):
            c.set(KEY, VALUE)
        return time.perf_counter() - t


async def redis_async_set():
    c = await redis_async.from_url(f"redis://{HOST}:{PORT}")
    t = time.perf_counter()
    for _ in range(N):
        await c.set(KEY, VALUE)
    elapsed = time.perf_counter() - t
    await c.aclose()
    return elapsed


def glide_sync_set():
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    c = GlideClient.create(GlideClientConfiguration([NodeAddress(HOST, PORT)]))
    t = time.perf_counter()
    for _ in range(N):
        c.set(KEY, VALUE)
    elapsed = time.perf_counter() - t
    c.close()
    return elapsed


async def glide_async_set():
    from glide import GlideClient, GlideClientConfiguration, NodeAddress

    c = await GlideClient.create(
        GlideClientConfiguration([NodeAddress(HOST, PORT)])
    )
    t = time.perf_counter()
    for _ in range(N):
        await c.set(KEY, VALUE)
    elapsed = time.perf_counter() - t
    await c.close()
    return elapsed


# ==================== GET benchmarks ====================


def redis_sync_get():
    with redis.Redis(HOST, PORT) as c:
        c.set(KEY, VALUE)
        t = time.perf_counter()
        for _ in range(N):
            c.get(KEY)
        return time.perf_counter() - t


async def redis_async_get():
    c = await redis_async.from_url(f"redis://{HOST}:{PORT}")
    await c.set(KEY, VALUE)
    t = time.perf_counter()
    for _ in range(N):
        await c.get(KEY)
    elapsed = time.perf_counter() - t
    await c.aclose()
    return elapsed


def glide_sync_get():
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    c = GlideClient.create(GlideClientConfiguration([NodeAddress(HOST, PORT)]))
    c.set(KEY, VALUE)
    t = time.perf_counter()
    for _ in range(N):
        c.get(KEY)
    elapsed = time.perf_counter() - t
    c.close()
    return elapsed


async def glide_async_get():
    from glide import GlideClient, GlideClientConfiguration, NodeAddress

    c = await GlideClient.create(
        GlideClientConfiguration([NodeAddress(HOST, PORT)])
    )
    await c.set(KEY, VALUE)
    t = time.perf_counter()
    for _ in range(N):
        await c.get(KEY)
    elapsed = time.perf_counter() - t
    await c.close()
    return elapsed


async def main():
    print(f"Python {sys.version}")
    print(f"Iterations: {N}")
    print(f"Server: {HOST}:{PORT}")

    print("\n=== SET benchmark ===")
    print(fmt("redis-py sync", redis_sync_set()))
    print(fmt("redis-py async", await redis_async_set()))
    print(fmt("glide sync (FFI)", glide_sync_set()))
    print(fmt("glide async (FFI)", await glide_async_set()))

    print("\n=== GET benchmark ===")
    print(fmt("redis-py sync", redis_sync_get()))
    print(fmt("redis-py async", await redis_async_get()))
    print(fmt("glide sync (FFI)", glide_sync_get()))
    print(fmt("glide async (FFI)", await glide_async_get()))


if __name__ == "__main__":
    asyncio.run(main())
