"""Send RUNS distinct files to the server over RDMA and read each back, timing every leg.

Usage: large_object.py <server-ip> <port> <work-dir> <size-bytes> <runs> <batch>

Runs are processed in batches so disk and registered memory are bounded by
BATCH, not RUNS. Per batch:

1. write BATCH files of random bytes under <work-dir>;
2. register ONE region of 2 x BATCH x size bytes and give each run two
   windows into it, a source and a destination, no two runs sharing bytes;
3. read each file into its source window;
4. for each run: rdma_set from the source window, rdma_get into the
   destination window, compare the two in memory;
5. write each destination window to a read-back file, compare the SHA-256 of
   every source/read-back pair, delete the batch's files, close the region.

Only step 4's set and get are timed; the table and the medians cover every run.

One region per batch rather than two per run follows the API's advice
(register one large region, address windows per transfer); the pinned bytes
are the same either way. Registering a file mapping is not an option: Linux
refuses long-term pinning of file-backed pages on ordinary filesystems, which
EFA reports as `fi_mr_reg: Bad address`, so the files go through anonymous
memory.
"""

import ctypes
import ctypes.util
import hashlib
import os
import shutil
import statistics
import sys
import time

from glide_shared.config import GlideClientConfiguration, NodeAddress, RdmaConfiguration, RdmaProvider, RdmaSetChecksum
from glide_sync import GlideClient

MIB = 1024 * 1024
CHUNK = 64 * MIB

_libc = ctypes.CDLL(ctypes.util.find_library("c"))
_libc.memcmp.argtypes = (ctypes.c_void_p, ctypes.c_void_p, ctypes.c_size_t)
_libc.memcmp.restype = ctypes.c_int


def same_bytes(buf: bytearray, a: int, b: int, n: int) -> bool:
    """buf[a:a+n] == buf[b:b+n] without copying either slice.

    A memoryview comparison walks the bytes in Python (1.5 s per 512 MiB) and a
    bytearray slice comparison copies both halves first; memcmp does neither.
    """
    base = ctypes.addressof((ctypes.c_char * len(buf)).from_buffer(buf))
    return _libc.memcmp(base + a, base + b, n) == 0


def mib_per_s(nbytes: int, seconds: float) -> float:
    return nbytes / MIB / seconds


def mem_available() -> int:
    with open("/proc/meminfo") as f:
        for line in f:
            if line.startswith("MemAvailable:"):
                return int(line.split()[1]) * 1024
    raise RuntimeError("MemAvailable not in /proc/meminfo")


def write_random(path: str, size: int) -> None:
    with open(path, "wb") as f:
        left = size
        while left:
            n = min(CHUNK, left)
            f.write(os.urandom(n))
            left -= n


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(CHUNK), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    server, port, work = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    size, runs, batch = int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
    batch = min(batch, runs)
    footprint = 2 * batch * size

    available = mem_available()
    if footprint > available * 0.8:
        print(f"a batch pins 2 x {batch} x {size / MIB:.0f} MiB = {footprint / MIB:.0f} MiB, but only "
              f"{available / MIB:.0f} MiB is available; lower BATCH or SIZE_MB", file=sys.stderr)
        return 1
    free = shutil.disk_usage(work).free
    if footprint > free * 0.8:
        print(f"a batch writes {footprint / MIB:.0f} MiB of files, but only {free / MIB:.0f} MiB is free "
              f"under {work}; lower BATCH or SIZE_MB", file=sys.stderr)
        return 1

    client = GlideClient.create(
        GlideClientConfiguration(
            addresses=[NodeAddress(server, port)],
            rdma=RdmaConfiguration(provider=RdmaProvider.EfaDirect()),
            request_timeout=600_000,
        )
    )
    set_times, get_times, mismatches = [], [], []
    header = f"{'run':>4}  {'set (s)':>8}  {'set MiB/s':>9}  {'get (s)':>8}  {'get MiB/s':>9}  {'crc32c':>10}  {'verify (s)':>10}"
    try:
        first = 1
        while first <= runs:
            last = min(first + batch - 1, runs)
            numbers = range(first, last + 1)
            print(f"-- batch {first}-{last} of {runs}")

            t0 = time.perf_counter()
            for n in numbers:
                write_random(os.path.join(work, f"source-{n}.bin"), size)
            print(f"wrote {len(numbers)} files of {size / MIB:.0f} MiB in {time.perf_counter() - t0:.3f} s")

            memory = bytearray(2 * len(numbers) * size)
            t0 = time.perf_counter()
            region = client.register_rdma_region(memory)
            print(f"registered one region of {len(memory) / MIB:.0f} MiB in {time.perf_counter() - t0:.3f} s")
            try:
                windows = {}
                for i, n in enumerate(numbers):
                    src_off, dst_off = (2 * i) * size, (2 * i + 1) * size
                    windows[n] = (
                        src_off, region.window(src_off, size), memoryview(memory)[src_off:src_off + size],
                        dst_off, region.window(dst_off, size), memoryview(memory)[dst_off:dst_off + size],
                    )

                t0 = time.perf_counter()
                for n in numbers:
                    with open(os.path.join(work, f"source-{n}.bin"), "rb") as f:
                        if f.readinto(windows[n][2]) != size:
                            print(f"short read of source-{n}.bin", file=sys.stderr)
                            return 1
                print(f"read {len(numbers)} files into their windows in {time.perf_counter() - t0:.3f} s")

                print(header)
                for n in numbers:
                    src_off, src_win, _src_mem, dst_off, dst_win, _dst_mem = windows[n]
                    key = f"large-object-{os.getpid()}-{n}".encode()
                    t0 = time.perf_counter()
                    client.rdma_set(key, src_win, checksum=RdmaSetChecksum.Compute())
                    set_s = time.perf_counter() - t0

                    t0 = time.perf_counter()
                    receipt = client.rdma_get(key, dst_win)
                    get_s = time.perf_counter() - t0
                    client.delete([key])
                    if receipt is None or receipt.bytes_written != size:
                        print(f"run {n}: get returned {receipt}, expected {size} bytes", file=sys.stderr)
                        return 1

                    t0 = time.perf_counter()
                    same = same_bytes(memory, dst_off, src_off, size)
                    verify_s = time.perf_counter() - t0
                    if not same:
                        print(f"run {n}: read-back differs from source in memory", file=sys.stderr)
                        return 1

                    set_times.append(set_s)
                    get_times.append(get_s)
                    print(f"{n:>4}  {set_s:8.3f}  {mib_per_s(size, set_s):9.1f}  {get_s:8.3f}  "
                          f"{mib_per_s(size, get_s):9.1f}  {receipt.checksum:#010x}  {verify_s:10.3f}")

                t0 = time.perf_counter()
                for n in numbers:
                    with open(os.path.join(work, f"readback-{n}.bin"), "wb") as f:
                        f.write(windows[n][5])
                print(f"wrote {len(numbers)} read-back files in {time.perf_counter() - t0:.3f} s")
            finally:
                region.close()
                del windows, memory

            t0 = time.perf_counter()
            for n in numbers:
                src_path = os.path.join(work, f"source-{n}.bin")
                dst_path = os.path.join(work, f"readback-{n}.bin")
                src_sha, dst_sha = sha256_file(src_path), sha256_file(dst_path)
                if src_sha == dst_sha:
                    print(f"run {n:>4}: sha256 {src_sha[:16]}...  match")
                else:
                    print(f"run {n:>4}: sha256 MISMATCH source {src_sha} read-back {dst_sha}")
                    mismatches.append(n)
                os.remove(src_path)
                os.remove(dst_path)
            print(f"digests compared and files removed in {time.perf_counter() - t0:.3f} s")
            first = last + 1
    finally:
        client.close()

    def summary(name, times):
        med = statistics.median(times)
        print(f"{name}: median {med:.3f} s ({mib_per_s(size, med):.1f} MiB/s), "
              f"min {min(times):.3f} s, max {max(times):.3f} s over {len(times)} runs")

    print(f"-- all {runs} runs")
    summary("set", set_times)
    summary("get", get_times)
    if mismatches:
        print(f"{len(mismatches)} run(s) with a digest mismatch: {mismatches}")
        return 1
    print(f"all {runs} objects of {size / MIB:.0f} MiB round-tripped intact")
    return 0


if __name__ == "__main__":
    sys.exit(main())
