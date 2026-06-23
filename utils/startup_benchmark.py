#!/usr/bin/env python3
"""Benchmark: measure valkey-server startup times via WSL, 10 runs per config."""
import subprocess
import time
import statistics

SCRIPT = "/mnt/c/dev/valkey-glide/utils/cluster_manager.py"
RUNS = 10

configs = [
    ("standalone, 0 replicas", ["start", "-r", "0", "-n", "1"]),
    ("standalone, 1 replica",  ["start", "-r", "1", "-n", "1"]),
    ("cluster 3 shards 0 replicas", ["start", "-r", "0", "-n", "3", "--cluster-mode"]),
    ("cluster 3 shards 1 replica",  ["start", "-r", "1", "-n", "3", "--cluster-mode"]),
]


def run_once(args):
    t0 = time.time()
    result = subprocess.run(
        ["wsl", "python3", SCRIPT] + args,
        capture_output=True, text=True, timeout=180
    )
    elapsed = time.time() - t0
    if result.returncode != 0:
        return None, None
    folder = next((l for l in result.stdout.splitlines() if l.startswith("CLUSTER_FOLDER=")), None)
    return elapsed, folder.split("=", 1)[1] if folder else None


def stop(folder):
    if folder:
        subprocess.run(
            ["wsl", "python3", SCRIPT, "stop", "--cluster-folder", folder],
            capture_output=True, timeout=60
        )


def percentile(data, p):
    data = sorted(data)
    idx = (p / 100) * (len(data) - 1)
    lo, hi = int(idx), min(int(idx) + 1, len(data) - 1)
    return data[lo] + (idx - lo) * (data[hi] - data[lo])


for label, args in configs:
    print(f"\n=== {label} ({RUNS} runs) ===", flush=True)
    times = []
    for i in range(RUNS):
        elapsed, folder = run_once(args)
        stop(folder)
        if elapsed is None:
            print(f"  run {i+1}: FAILED")
        else:
            times.append(elapsed)
            print(f"  run {i+1}: {elapsed:.1f}s", flush=True)
        time.sleep(1)
    if times:
        print(f"  min={min(times):.1f}s  median={statistics.median(times):.1f}s  p90={percentile(times,90):.1f}s  p99={percentile(times,99):.1f}s  max={max(times):.1f}s")

print("\nDone.")
