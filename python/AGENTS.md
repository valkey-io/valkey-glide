# AGENTS: Python Client Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Python client — build commands, testing, and Python-specific guardrails. It builds on [../AGENTS.md](../AGENTS.md) (repo-wide conventions, DCO, commit format, test servers) — read that first.

## Repository Overview

This is the Python client binding for Valkey GLIDE, providing both async and sync client implementations, as three packages: async client, sync client, and a shared package used by both. Command dispatch for **both** clients goes through CFFI calls into the `ffi/` crate; async response parsing goes through the PyO3 `_fast_response` module in `glide-shared/`.

**Primary Languages:** Python, Rust (CFFI dispatch + PyO3 response parsing)
**Build System:** Custom dev.py CLI driving Maturin (async, shared) and setuptools (sync)
**Architecture:** Hybrid Python/Rust — CFFI for command dispatch (both clients), PyO3 for async response parsing

**Key Components:**

- `glide-async/` - Async client (Maturin-packaged); responses arrive from Rust worker threads over an anonymous pipe
- `glide-sync/` - Sync client (setuptools-packaged); responses are direct FFI return values
- `glide-shared/` - Shared Python logic plus the PyO3 `_fast_response` parser (Maturin hybrid crate)
- `tests/` - Shared test suite for both clients
- `dev.py` - CLI utility for build, test, and development tasks

## Architecture Quick Facts

**Core Implementation:** Python wrappers around glide-core Rust library via `ffi/`
**Client Types:** GlideClient/GlideClusterClient (async), GlideClient/GlideClusterClient (sync)
**API Styles:** Async with asyncio/trio support, Sync with blocking calls
**Communication:** CFFI into `ffi/` for both clients; async responses via anonymous pipe, sync responses via FFI return values

**Supported Platforms:** see the System Requirements section of [README.md](./README.md). Alpine Linux/MUSL is not supported.

**Python Versions:** 3.9–3.14, plus 3.14t (free-threaded). CI's always-run set is 3.9, 3.13, 3.14t (`.github/json_matrices/supported-languages-versions.json`). Free-threaded 3.14t has extra concurrency rules — see "Python Free-Threading" below.
**Async Frameworks:** asyncio, trio, uvloop

**Packages:** `valkey-glide` (async), `valkey-glide-sync` (sync)

## Build and Test Rules (Agents)

<!-- These commands are intentionally mirrored in python/DEVELOPER.md (human guide). Keep both in sync when the build/test/lint commands change. -->

### Preferred (dev.py CLI)
```bash
# Build commands
python3 dev.py build --client async --mode release    # Build async client (optimized)
python3 dev.py build --client sync --mode release     # Build sync client (optimized)
python3 dev.py build --client async --mode debug      # Build async client (debug)
python3 dev.py build --client sync --mode debug       # Build sync client (debug)

# Build wheels for local testing
python3 dev.py build --client async --wheel           # Build async wheel
python3 dev.py build --client sync --wheel            # Build sync wheel

# Testing (pytest.ini excludes pubsub and server_modules tests by default, and sets --timeout=300 per test)
python3 dev.py test                                    # Run all tests except pubsub/server_modules
python3 dev.py test --args -k <test_name>             # Run specific test (-k replaces the default exclusion)
python3 dev.py test --args -k 'pubsub'                # Run the pubsub tests (CI's form)
python3 dev.py test --args --async-backend=trio       # Test with trio backend
python3 dev.py test --args --async-backend=asyncio    # Test with asyncio backend

# Linting and Formatting
python3 dev.py lint                                    # Auto-fix formatting (isort, black), then flake8 and mypy
python3 dev.py lint --check                           # Same four tools; isort/black report instead of rewriting

# Protobuf generation
python3 dev.py protobuf                               # Regenerate protobuf files

# Cleaning build artifacts
python3 dev.py clean                                  # Remove all client build artifacts
python3 dev.py clean --client async                   # Remove async client artifacts only
python3 dev.py clean --client sync                    # Remove sync client artifacts only

# Help
python3 dev.py --help                                 # Show all available commands
```

### Raw Equivalents

These are the steps `dev.py build` runs. `glide_shared` must be installed and `libglide_ffi` built before either client.

```bash
# Shared package + FFI library (from python/)
source .env/bin/activate
(cd glide-shared && maturin develop --release)
(cd ../ffi && cargo build --release)
cp ../ffi/target/release/libglide_ffi.dylib glide-shared/glide_shared/   # .so on Linux

# Manual async client build (from glide-async/)
source ../.env/bin/activate
maturin develop --release

# Manual sync client build (from glide-sync/) — setup.py vendors ffi/ + glide-shared/ into
# glide-sync/ and runs cargo on the copies, so this is a from-scratch Rust build each time
source ../.env/bin/activate
RELEASE_MODE=1 pip install .    # omit RELEASE_MODE for a debug build

# Manual test execution (from python/)
source .env/bin/activate
pytest -v

# Manual linting (from python/; dev.py runs mypy on the repo root, not just python/)
source .env/bin/activate
isort . && black . && flake8 . && mypy ..

# Manual protobuf generation (from python/)
source .env/bin/activate
protoc --plugin=protoc-gen-mypy=.env/bin/protoc-gen-mypy -Iprotobuf=../glide-core/src/protobuf \
  --python_out=glide-shared/glide_shared --mypy_out=glide-shared/glide_shared \
  ../glide-core/src/protobuf/*.proto
```

### Test Servers

`python3 dev.py test` **auto-starts** standalone + cluster servers via `utils/cluster_manager.py` and stops them afterward — you don't start anything manually for a normal run. The `--cluster-endpoints=`/`--standalone-endpoints=` flags below are only for pointing at a server you started yourself (ad-hoc/manual testing); see the root [../AGENTS.md](../AGENTS.md) "Test Servers" section for the `cluster_manager.py` commands. A `valkey-server`/`redis-server` on `PATH` is required either way.

### Test Execution Options

```bash
# Run with existing endpoints
python3 dev.py test --args --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with TLS (the harness starts its own TLS servers; this is the only mode that
# creates the *_tls_cluster and auth fixtures that test_tls.py, the DNS tests and
# the auth suite read)
python3 dev.py test --args --tls

# Run with TLS against existing endpoints: --tls applies to the main standalone and
# cluster fixtures only; tests that read the harness-created *_tls_cluster/auth
# fixtures raise AttributeError in this mode (see create_clusters() in tests/conftest.py)
python3 dev.py test --args --tls --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with specific async backend
python3 dev.py test --args --async-backend=trio --async-backend=asyncio

# Run specific test pattern
python3 dev.py test --args -k "test_socket_set_get"
```

## Contribution Requirements

DCO signoff and Conventional Commits are required repo-wide — see [../AGENTS.md](../AGENTS.md#contribution-requirements) for the exact commands. Use the `python` scope, e.g. `feat(python): implement async cluster scan with routing options`.

### Code Quality Requirements

**Python Linters (via dev.py):**

```bash
python3 dev.py lint                    # Must pass before commit (auto-fixes)
python3 dev.py lint --check            # Check-only mode
```

**Individual Tools:**

- `isort` - Import sorting
- `black` - Code formatting
- `flake8` - Style and error checking
- `mypy` - Type checking

**Rust Components** (the PyO3 glue — CI lints this from `python/glide-async/` via the shared Rust gate; there is no `python/Cargo.toml`):

```bash
# From python/glide-async/ — runs the full repo-wide Rust gate (fmt --check, clippy ×2, cargo deny, cargo doc).
# See ../AGENTS.md for the exact command list.
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
```

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `.env/` - Python virtual environment
- `glide-async/target/`, `glide-shared/target/`, `glide-sync/ffi/target/` - Rust build artifacts
- `glide-shared/glide_shared/protobuf/*_pb2.py*` - Generated protobuf files
- `glide-shared/glide_shared/_fast_response*.so`, `glide-shared/glide_shared/libglide_ffi.*` - Native modules copied in by the build
- `glide-async/python/glide/glide*.so` - PyO3 module built by maturin
- `glide-async/python/glide_shared/` - `glide_shared` vendored into the async wheel
- `glide-sync/glide_sync/libglide_ffi.so`, `glide-sync/{ffi,glide-core,glide-logger,glide-telemetry,glide_shared,glide-shared-rs}/` - FFI library and crates vendored by `setup.py` (removed after a successful build; left behind if it fails)
- `glide-async/README.md`, `glide-sync/README.md` - Copied from `python/README.md` at build time
- `build/`, `dist/`, `*.egg-info/` - Packaging artifacts
- `__pycache__/`, `.pytest_cache/`, `.mypy_cache/` - Caches
- `docs/` except `docs/index.rst` - Sphinx output and apidoc stubs

### Python-Specific Rules

- **Python 3.9+ Required:** Minimum runtime version
- **Virtual Environment:** Always use `.env/` for development
- **Dual Client Support:** Maintain compatibility between async and sync clients
- **Shared Logic:** Keep common code in `glide-shared/` package
- **Implement async with anyio:** Write all async code against the `anyio` API, not `asyncio` — this applies to client/shared implementation, not just tests. The async client must run unmodified on asyncio, trio, and uvloop. Use `anyio.sleep`, `anyio.fail_after`/`anyio.move_on_after`, `anyio` sync primitives and streams (`anyio.Event`, `anyio.Lock`, `anyio.create_memory_object_stream`), and `anyio.create_task_group()`. Avoid asyncio-only APIs (`asyncio.get_running_loop`, `asyncio.Queue`, `asyncio.gather`, `loop.run_in_executor`); use `anyio.to_thread`/`anyio.from_thread` for thread interop.
- **anyio in tests:** Never use `asyncio.sleep`, `asyncio.wait_for`, or `asyncio.Queue` directly in test code. Use `anyio.sleep`, `anyio.fail_after`, and the `wait_for()` helper from `tests/utils/utils.py` instead. Tests run under both asyncio and trio via pytest-anyio.
- **Prefer polling over sleeps:** Use `await wait_for(condition, message, timeout)` instead of fixed `await anyio.sleep(N)` delays. The condition should be an async callable returning bool. The timeout should be a reasonable small seconds.
- **Protobuf Updates:** Run `python3 dev.py protobuf` after proto changes
- **Stale `libglide_ffi.so` after core changes:** `install_glide_shared` (in `dev.py`) only (re)builds and copies the C-ABI `libglide_ffi.*` into `glide-shared/glide_shared/` when that destination file is **absent** — so once a copy exists, a later `dev.py build` keeps using the old one even after you change `ffi/`/`glide-core` (symptom: sync tests fail with `undefined symbol: glide_scope_...` against a stale `.so`). Rebuilding `ffi` alone does **not** fix it because the copy is skipped. To force a refresh, delete the stale lib (`rm glide-shared/glide_shared/libglide_ffi.*`) or run `python3 dev.py clean`, then rebuild. Not environment-specific. CI never hits this (ephemeral runners build from scratch).
- **Documentation Style:** Follow Google Style Python Docstrings format

### Python Free-Threading (3.14t) Concurrency Rules

The async client (`glide-async/python/glide/glide_client.py`) supports free-threaded CPython (`3.14t`, GIL disabled). The module declares `#[pymodule(gil_used = false)]` in `glide-async/src/lib.rs`, and when `_FREE_THREADED` is true, response parsing runs on a `ThreadPoolExecutor` so completions can arrive on a non-event-loop thread.

Cross-thread wakeups are the sharp edge. Under trio, `_CompatFuture._is_done` is an `anyio.Event` backed by a `trio.Event`, and trio primitives are **not thread-safe**: `.set()` from a foreign thread marks the event set but never reschedules the parked waiter, so the `await` hangs with no timeout firing (the main thread is parked in native code where Python signal handlers, including pytest-timeout's, cannot run). Any object completed from the response thread pool must hop back to its owning loop/run: asyncio uses `loop.call_soon_threadsafe`, trio uses the `trio.lowlevel.TrioToken.run_sync_soon` captured at future-creation time. When touching future/event completion, verify the wake path for both asyncio and trio.

Build and run against free-threaded Python locally (fast iteration beats CI):

```bash
# Install the free-threaded interpreter (Homebrew: python-freethreading -> python3.14t)
python3.14t -m venv .env   # dev.py reuses an existing .env
python3 dev.py build
# -k "" clears pytest.ini's default exclusion so the pubsub tests run too
python3 dev.py test --args tests/async_tests/test_async_client.py --async-backend=trio -k ""
# Confirm no-GIL: .env/bin/python -c "import sys; print(sys._is_gil_enabled())"  # -> False
```

Reproduce a suspected hang with a stack dump: `kill -ABRT <pid>` (faulthandler prints the parked Python stack) since py-spy needs root on macOS.

### Package Structure Rules

- **Async Client:** `import glide` (PyPI: `valkey-glide`)
- **Sync Client:** `import glide_sync` (PyPI: `valkey-glide-sync`)
- **Shared Logic:** `import glide_shared` (local install only)
- **Independent Packaging:** Each client has separate pyproject.toml and release cycle

## Project Structure (Essential)

```text
python/
├── glide-async/                # Async client (PyO3 + Maturin)
│   ├── Cargo.toml              # Rust dependencies
│   ├── pyproject.toml          # Python package config
│   ├── python/glide/           # Python async client code
│   └── src/                    # Rust PyO3 bindings
├── glide-sync/                 # Sync client (CFFI + setuptools)
│   ├── pyproject.toml          # Python package config
│   ├── glide_sync/             # Python sync client code
│   └── setup.py                # Build configuration
├── glide-shared/               # Shared logic for both clients
│   ├── pyproject.toml          # Shared package config
│   └── glide_shared/           # Shared Python code
├── tests/                      # Shared test suite
├── dev.py                      # CLI utility for development
├── dev_requirements.txt        # Development dependencies
└── docs/                       # Sphinx documentation (legacy)
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `python3 dev.py build --client async --mode release` succeeds
- [ ] Build passes: `python3 dev.py build --client sync --mode release` succeeds
- [ ] All tests pass: `python3 dev.py test` succeeds
- [ ] Linting passes: `python3 dev.py lint` succeeds
- [ ] Type checking passes: `mypy` runs clean
- [ ] Both async backends work: `--async-backend=trio --async-backend=asyncio`
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] Documentation follows Google Style format
- [ ] Shared logic properly isolated in `glide-shared/`

## Quick Facts for Reasoners

**Packages:** `valkey-glide` (async), `valkey-glide-sync` (sync) on PyPI
**API Styles:** Async (asyncio/trio), Sync (blocking)
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster) for both async/sync
**Key Features:** Dual client architecture, shared logic, multi-async framework support, OpenTelemetry observability
**Testing:** pytest with async backend selection, shared test suite
**Platforms:** Linux (Ubuntu, AL2/AL2023), macOS (Intel/Apple Silicon)
**Dependencies:** Python 3.9+, Rust toolchain, protobuf compiler

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/python/](../examples/python/)
- **API Documentation:** [Valkey GLIDE Python docs](https://valkey.io/valkey-glide/)
- **Documentation:** [GLIDE Documentation](https://glide.valkey.io/)
- **Test Suites:** [tests/](./tests/) directory
- **Async Client:** [glide-async/](./glide-async/) directory
- **Sync Client:** [glide-sync/](./glide-sync/) directory
- **Shared Logic:** [glide-shared/](./glide-shared/) directory
- **CLI Utility:** [dev.py](./dev.py) script
