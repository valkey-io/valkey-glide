# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Self-sampling profiler: writes PID, waits for signal, then runs.

Usage:
    cd ~/gh/jduo/valkey-glide/python && source .env/bin/activate
    python ../benchmarks/python/profile_native.py <sync_set|redis_sync_set>
"""

import os
import signal
import sys
import time

N = 50_000
HOST = "localhost"
PORT = 6379


def run_sync_set():
    from glide_sync import GlideClient, GlideClientConfiguration, NodeAddress

    c = GlideClient.create(GlideClientConfiguration([NodeAddress(HOST, PORT)]))
    for _ in range(500):
        c.set("k", "v")
    with open("/tmp/profile_pid", "w") as f:
        f.write(str(os.getpid()))
    print(f"PID {os.getpid()} ready, waiting for SIGUSR1...", flush=True)
    ready = [False]
    signal.signal(signal.SIGUSR1, lambda *_: ready.__setitem__(0, True))
    while not ready[0]:
        time.sleep(0.01)
    print("Running...", flush=True)
    t = time.perf_counter()
    for _ in range(N):
        c.set("k", "v")
    print(f"Done in {time.perf_counter()-t:.2f}s", flush=True)
    c.close()


def run_redis_sync_set():
    import redis

    c = redis.Redis(HOST, PORT)
    for _ in range(500):
        c.set("k", "v")
    with open("/tmp/profile_pid", "w") as f:
        f.write(str(os.getpid()))
    print(f"PID {os.getpid()} ready, waiting for SIGUSR1...", flush=True)
    ready = [False]
    signal.signal(signal.SIGUSR1, lambda *_: ready.__setitem__(0, True))
    while not ready[0]:
        time.sleep(0.01)
    print("Running...", flush=True)
    t = time.perf_counter()
    for _ in range(N):
        c.set("k", "v")
    print(f"Done in {time.perf_counter()-t:.2f}s", flush=True)
    c.close()


if __name__ == "__main__":
    modes = {
        "sync_set": run_sync_set,
        "redis_sync_set": run_redis_sync_set,
    }
    fn = modes.get(sys.argv[1])
    if fn is None:
        print(f"Usage: python profile_native.py <{'|'.join(modes)}>")
        sys.exit(1)
    fn()
