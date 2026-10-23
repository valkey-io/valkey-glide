# Developer Guide

## Tests

The crate has two test configurations: one without libfabric and one with it.

To run the tests that don't need libfabric:

```bash
cargo test
```

To also run the tests that open a fabric and register memory:

```bash
cargo test --features libfabric
```

These tests need libfabric installed (see below). They use only the `tcp`
provider, so they don't need RDMA hardware or a running server.

To run specific tests:

```bash
cargo test --features libfabric <pattern>
```

## Installing libfabric

The installed libfabric must be version 2.x and **no older than the vendored
headers**, which are libfabric 2.4 (`FI_MAJOR_VERSION` and `FI_MINOR_VERSION`
in [`glide-libfabric-sys/src/bindings.rs`](glide-libfabric-sys/src/bindings.rs)).
An older library may lay out its structs differently from what this crate reads,
so `ensure_loaded` refuses it with `RdmaError::LibfabricUnavailable`, and the
tests that open a fabric fail.

### macOS

```bash
brew install libfabric
```

### Linux

Most distributions still package libfabric 1.x (Ubuntu's `libfabric-dev`, for
example), which is too old. Build it from source instead, as
[CI](../.github/workflows/glide-rdma.yml) does:

```bash
LIBFABRIC_VERSION=2.6.0
curl -fsSLO "https://github.com/ofiwg/libfabric/releases/download/v${LIBFABRIC_VERSION}/libfabric-${LIBFABRIC_VERSION}.tar.bz2"
tar xjf "libfabric-${LIBFABRIC_VERSION}.tar.bz2"
cd "libfabric-${LIBFABRIC_VERSION}"
# The tests only use the tcp provider.
./configure --prefix="$HOME/libfabric" --enable-only --enable-tcp
make -j"$(nproc)" install
```

Then point the tests at it:

```bash
export GLIDE_LIBFABRIC_PATH="$HOME/libfabric/lib/libfabric.so.1"
```

On EC2 hosts with EFA, the AWS EFA installer puts its own libfabric under
`/opt/amazon/efa`, which is found automatically. Check that its version is 2.4
or newer with `/opt/amazon/efa/bin/fi_info --version`.

## How libfabric is found

glide-rdma never links libfabric. It loads the library with `dlopen` the first
time a fabric is opened (or `ensure_loaded` is called), so a program built with
the `libfabric` feature still starts on a host without libfabric.

If `GLIDE_LIBFABRIC_PATH` is set, only that path is tried. Otherwise these are
tried in order, and the first that loads at a compatible version is used:

| Linux                                  | macOS                                                |
|----------------------------------------|------------------------------------------------------|
| `/opt/amazon/efa/lib64/libfabric.so.1` | `libfabric.1.dylib`                                  |
| `/opt/amazon/efa/lib/libfabric.so.1`   | `libfabric.dylib`                                    |
| `libfabric.so.1`                       | `/opt/homebrew/opt/libfabric/lib/libfabric.1.dylib`  |
| `/usr/local/lib64/libfabric.so.1`      | `/usr/local/opt/libfabric/lib/libfabric.1.dylib`     |
| `/usr/local/lib/libfabric.so.1`        |                                                      |
| `libfabric.so`                         |                                                      |

Bare names are resolved by the system loader, so they follow `LD_LIBRARY_PATH`
on Linux and `DYLD_LIBRARY_PATH` on macOS. The list is `SEARCH_PATHS` in
[`src/libfabric_dl.rs`](src/libfabric_dl.rs).

If no candidate works, the error names every path tried and why each was
rejected.

## Updating the libfabric headers

See "Regenerating the bindings" in
[`glide-libfabric-sys/README.md`](glide-libfabric-sys/README.md).
