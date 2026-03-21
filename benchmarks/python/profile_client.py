# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Profiling script - run one client type at a time for clean profiles.

Usage:
    cd ~/gh/jduo/valkey-glide/python && source .env/bin/activate
    python ../benchmarks/python/profile_client.py <mode>

Modes: sync_set sync_get async_set async_get redis_sync_set redis_sync_get
"""

import sys
import time

N = 50_000
HOST = "localhost"
PORT = 6379
KEY = "pkey"
VALUE = "pvalue"


def profile_sync_set():
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    c = GlideClient.create(GlideClientConfiguration([NodeAddress(HOST, PORT)]))
    for _ in range(100):
        c.set(KEY, VALUE)
    time.sleep(0.1)
    for _ in range(N):
        c.set(KEY, VALUE)
    c.close()


def profile_sync_get():
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    c = GlideClient.create(GlideClientConfiguration([NodeAddress(HOST, PORT)]))
    c.set(KEY, VALUE)
    for _ in range(100):
        c.get(KEY)
    time.sleep(0.1)
    for _ in range(N):
        c.get(KEY)
    c.close()


def profile_async_set():
    import asyncio

    from glide import GlideClient, GlideClientConfiguration, NodeAddress

    async def run():
        c = await GlideClient.create(
            GlideClientConfiguration([NodeAddress(HOST, PORT)])
        )
        for _ in range(100):
            await c.set(KEY, VALUE)
        time.sleep(0.1)
        for _ in range(N):
            await c.set(KEY, VALUE)
        await c.close()

    asyncio.run(run())


def profile_async_get():
    import asyncio

    from glide import GlideClient, GlideClientConfiguration, NodeAddress

    async def run():
        c = await GlideClient.create(
            GlideClientConfiguration([NodeAddress(HOST, PORT)])
        )
        await c.set(KEY, VALUE)
        for _ in range(100):
            await c.get(KEY)
        time.sleep(0.1)
        for _ in range(N):
            await c.get(KEY)
        await c.close()

    asyncio.run(run())


def profile_redis_sync_set():
    import redis

    c = redis.Redis(HOST, PORT)
    for _ in range(100):
        c.set(KEY, VALUE)
    time.sleep(0.1)
    for _ in range(N):
        c.set(KEY, VALUE)
    c.close()


def profile_redis_sync_get():
    import redis

    c = redis.Redis(HOST, PORT)
    c.set(KEY, VALUE)
    for _ in range(100):
        c.get(KEY)
    time.sleep(0.1)
    for _ in range(N):
        c.get(KEY)
    c.close()


if __name__ == "__main__":
    modes = {
        "sync_set": profile_sync_set,
        "sync_get": profile_sync_get,
        "async_set": profile_async_set,
        "async_get": profile_async_get,
        "redis_sync_set": profile_redis_sync_set,
        "redis_sync_get": profile_redis_sync_get,
    }
    if len(sys.argv) < 2:
        print("Usage: python profile_client.py <mode>")
        print(f"Modes: {' '.join(modes)}")
        sys.exit(1)
    mode = sys.argv[1]
    fn = modes.get(mode)
    if fn is None:
        print(f"Unknown mode: {mode}")
        sys.exit(1)
    print(f"Profiling {mode} with {N} iterations...")
    t = time.perf_counter()
    fn()
    print(f"Done in {time.perf_counter()-t:.2f}s")
