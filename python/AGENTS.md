# AGENTS: Python Client Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Python client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Python implementation.

## Repository Overview

This is the Python client binding for Valkey GLIDE, providing both async and sync client implementations. The Python wrapper consists of three packages: the async client, the sync client, and a shared package used by both. Command dispatch for both clients goes through CFFI calls into the `ffi/` crate; response parsing goes through the PyO3 `_fast_response` module in `glide-shared/`. See [DEVELOPER.md](./DEVELOPER.md) for the architecture description.

**Primary Languages:** Python, Rust (CFFI and PyO3 bindings)
**Build System:** Custom dev.py CLI driving Maturin (async, shared) and setuptools (sync)
**Architecture:** Hybrid Python/Rust: CFFI for command dispatch, PyO3 for response parsing

**Key Components:**

- `glide-async/` - Async client; packaged with Maturin, responses arrive over an anonymous pipe from Rust worker threads
- `glide-sync/` - Sync client; packaged with setuptools, responses are direct FFI return values
- `glide-shared/` - Shared Python logic plus the PyO3 `_fast_response` parser (Maturin hybrid crate)
- `tests/` - Shared test suite for both clients
- `dev.py` - CLI utility for build, test, and development tasks

## Architecture Quick Facts

**Core Implementation:** Python wrappers around glide-core Rust library
**Client Types:** GlideClient/GlideClusterClient (async), GlideClient/GlideClusterClient (sync)
**API Styles:** Async with asyncio/trio support, Sync with blocking calls
**Communication:** CFFI calls into `ffi/` for both clients; async responses via anonymous pipe, sync responses via FFI return values

**Supported Platforms:** see the System Requirements section of [README.md](./README.md). Alpine Linux/MUSL is not supported.

**Python Versions:** 3.9 through 3.14, plus free-threaded 3.14t (CI matrix: [`.github/json_matrices/supported-languages-versions.json`](../.github/json_matrices/supported-languages-versions.json))
**Async Frameworks:** asyncio, trio, uvloop

**Packages:** `valkey-glide` (async), `valkey-glide-sync` (sync)

## Build and Test Rules (Agents)

### Preferred (dev.py CLI)

```bash
# Build commands (debug is the default mode; "all" is the default client)
python3 dev.py build --client async --mode release    # Build async client (optimized)
python3 dev.py build --client sync --mode release     # Build sync client (optimized)
python3 dev.py build --client async --mode debug      # Build async client (debug)
python3 dev.py build --client sync --mode debug       # Build sync client (debug)

# Build wheels for local testing
python3 dev.py build --client async --wheel           # Build async wheel
python3 dev.py build --client sync --wheel            # Build sync wheel (python -m build in glide-sync/)

# Testing (pytest.ini excludes pubsub and server_modules tests by default and sets --timeout=300 per test)
python3 dev.py test                                    # Run all tests except pubsub/server_modules
python3 dev.py test --args -k <test_name>             # Run specific test (-k replaces the default exclusion)
python3 dev.py test --args -k 'pubsub'                # Run the pubsub tests (CI's form)
python3 dev.py test --args --async-backend=trio       # Test with trio backend
python3 dev.py test --args --async-backend=asyncio    # Test with asyncio backend

# Linting and Formatting
python3 dev.py lint                                    # Auto-fix formatting (isort, black), then flake8 and mypy
python3 dev.py lint --check                           # Check formatting only

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
# Shared package and FFI library (from python/)
source .env/bin/activate
(cd glide-shared && maturin develop --release)
(cd ../ffi && cargo build --release)
cp ../ffi/target/release/libglide_ffi.dylib glide-shared/glide_shared/   # .so on Linux

# Manual async client build (from glide-async/)
source ../.env/bin/activate
maturin develop --release

# Manual sync client build (from glide-sync/). setup.py vendors ffi/ and glide-shared/ into glide-sync/
# and runs cargo on the copies, so this is a from-scratch Rust build every time.
source ../.env/bin/activate
RELEASE_MODE=1 pip install .   # omit RELEASE_MODE for a debug build

# Manual test execution (from python/)
source .env/bin/activate
pytest -v

# Manual linting (from python/; dev.py runs mypy on the repo root, not just python/)
source .env/bin/activate
isort . && black . && flake8 . && mypy ..

# Manual protobuf generation (from python/)
source .env/bin/activate
protoc --plugin=protoc-gen-mypy=.env/bin/protoc-gen-mypy -Iprotobuf=../glide-core/src/protobuf --python_out=glide-shared/glide_shared --mypy_out=glide-shared/glide_shared ../glide-core/src/protobuf/*.proto
```

### Test Execution Options

```bash
# Run with existing endpoints
python3 dev.py test --args --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with TLS against existing endpoints (requires conftest.py to set the *_tls_cluster fixtures
# when endpoints are given; check create_clusters() before relying on this form)
python3 dev.py test --args --tls --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with specific async backend
python3 dev.py test --args --async-backend=trio --async-backend=asyncio

# Run specific test pattern
python3 dev.py test --args -k "test_socket_set_get"
```

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat(python): add new command implementation"

# Configure automatic signoff
git config --global format.signOff true

# Add signoff to existing commit
git commit --amend --signoff --no-edit

# Add signoff to multiple commits
git rebase -i HEAD~n --signoff
```

### Conventional Commits

Use conventional commit format:

```
<type>(<scope>): <description>

[optional body]
```

**Example:** `feat(python): implement async cluster scan with routing options`

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

**Rust Components:**

The Rust crates are `glide-async/` and `glide-shared/` (there is no `Cargo.toml` in `python/` itself). CI lints `glide-async/` with:

```bash
# From glide-async/ (repeat from glide-shared/)
rustup component add clippy rustfmt
cargo fmt --all -- --check
cargo clippy --all-features --all-targets -- -D warnings
cargo clippy --all-targets -- -D warnings
```

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `.env/` - Python virtual environment
- `glide-async/target/`, `glide-shared/target/`, `../ffi/target/` - Rust build artifacts
- `glide-shared/glide_shared/protobuf/*_pb2.py*` - Generated protobuf files
- `glide-shared/glide_shared/_fast_response*.so` and `glide-shared/glide_shared/libglide_ffi.*` - Native modules copied in by the build
- `glide-async/python/glide/glide*.so` - PyO3 module built by maturin
- `glide-async/python/glide_shared/` - `glide_shared` vendored into the async wheel
- `glide-sync/glide_sync/libglide_ffi.so` and `glide-sync/{ffi,glide-core,glide-logger,glide-telemetry,glide_shared,glide-shared-rs}/` - FFI library and crates vendored by setup.py (removed after a successful build; left behind if it fails)
- `glide-async/README.md`, `glide-sync/README.md` - Copied from `python/README.md` at build time
- `build/`, `dist/`, `*.egg-info/` - Packaging artifacts
- `__pycache__/`, `.pytest_cache/`, `.mypy_cache/` - Caches
- `docs/` except `docs/index.rst` - Sphinx output and apidoc stubs

### Python-Specific Rules

- **Python 3.9+ Required:** Minimum runtime version
- **Virtual Environment:** Always use `.env/` for development (`dev.py --venv <path>` overrides it)
- **Dual Client Support:** Maintain compatibility between async and sync clients
- **Shared Logic:** Keep common code in `glide-shared/` package
- **Implement async with anyio:** Write all async code against the `anyio` API, not `asyncio` — this applies to client/shared implementation, not just tests. The async client must run unmodified on asyncio, trio, and uvloop. Use `anyio.sleep`, `anyio.fail_after`/`anyio.move_on_after`, `anyio` sync primitives and streams (`anyio.Event`, `anyio.Lock`, `anyio.create_memory_object_stream`), and `anyio.create_task_group()`. Avoid asyncio-only APIs (`asyncio.get_running_loop`, `asyncio.Queue`, `asyncio.gather`, `loop.run_in_executor`); use `anyio.to_thread`/`anyio.from_thread` for thread interop.
- **anyio in tests:** Never use `asyncio.sleep`, `asyncio.wait_for`, or `asyncio.Queue` directly in test code. Use `anyio.sleep`, `anyio.fail_after`, and the `wait_for()` helper from `tests/utils/utils.py` instead. Tests run under both asyncio and trio via pytest-anyio.
- **Prefer polling over sleeps:** Use `await wait_for(condition, message, timeout)` instead of fixed `await anyio.sleep(N)` delays. The condition should be an async callable returning bool. The timeout should be a reasonable small seconds.
- **Protobuf Updates:** Run `python3 dev.py protobuf` after proto changes
- **Documentation Style:** Follow Google Style Python Docstrings format

### Python Free-Threading (3.14t) Concurrency Rules

The async client (`glide-async/python/glide/glide_client.py`) supports free-threaded CPython (`3.14t`, GIL disabled). The module declares `#[pymodule(gil_used = false)]` in `glide-async/src/lib.rs`, and when `_FREE_THREADED` is true response parsing runs on a `ThreadPoolExecutor`, so completions can arrive on a non-event-loop thread.

Cross-thread wakeups are the sharp edge. Under trio, `_CompatFuture._is_done` is an `anyio.Event` backed by a `trio.Event`, and trio primitives are not thread-safe: `.set()` from a foreign thread marks the event set but never reschedules the parked waiter, so the `await` hangs with no timeout firing (the main thread is parked in native code where Python signal handlers, including pytest-timeout's, cannot run). Any object completed from the response thread pool must hop back to its owning loop/run: asyncio uses `loop.call_soon_threadsafe`, trio uses the `trio.lowlevel.TrioToken.run_sync_soon` captured at future-creation time. When touching future/event completion, verify the wake path for both asyncio and trio.

To build and run the suite against free-threaded Python locally (fast iteration beats CI):

```bash
# Install the free-threaded interpreter (Homebrew: python-freethreading -> python3.14t)
python3.14t -m venv .env   # dev.py reuses an existing .env
python3 dev.py build
# -k "" clears pytest.ini's default exclusion so the pubsub tests run too
python3 dev.py test --args tests/async_tests/test_async_client.py --async-backend=trio -k ""
# Confirm no-GIL: .env/bin/python -c "import sys; print(sys._is_gil_enabled())"  # -> False
```

Reproduce a suspected hang with a stack dump: `kill -ABRT <pid>` (pytest's faulthandler prints the parked Python stack) since py-spy needs root on macOS.

### Package Structure Rules

- **Async Client:** `import glide` (PyPI: `valkey-glide`)
- **Sync Client:** `import glide_sync` (PyPI: `valkey-glide-sync`)
- **Shared Logic:** `import glide_shared` (bundled into both wheels, not published separately)
- **Independent Packaging:** Each client has separate pyproject.toml and release cycle

## Project Structure (Essential)

```text
python/
├── glide-async/                # Async client (Maturin)
│   ├── Cargo.toml              # Rust dependencies
│   ├── pyproject.toml          # Python package config
│   ├── python/glide/           # Python async client code
│   └── src/                    # Rust PyO3 bindings
├── glide-sync/                 # Sync client (CFFI + setuptools)
│   ├── pyproject.toml          # Python package config
│   ├── glide_sync/             # Python sync client code
│   └── setup.py                # Build configuration (vendors ffi/ and glide_shared for sdist)
├── glide-shared/               # Shared logic for both clients (Maturin hybrid)
│   ├── Cargo.toml              # Rust crate for the _fast_response parser
│   ├── pyproject.toml          # Shared package config
│   ├── src/                    # Rust PyO3 response parser
│   └── glide_shared/           # Shared Python code
├── tests/                      # Shared test suite
│   ├── async_tests/
│   ├── sync_tests/
│   └── utils/                  # wait_for(), cluster helpers
├── dev.py                      # CLI utility for development
├── dev_requirements.txt        # Development dependencies
├── pyproject.toml              # isort/black/mypy config
├── pytest.ini                  # default -k exclusion, --timeout=300
└── docs/                       # Sphinx config; CI docs-test still builds it (mkdocs migration pending)
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
