# AGENTS: Python Client Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Python client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Python implementation.

## Repository Overview

This is the Python client binding for Valkey GLIDE, providing both async and sync client implementations. The Python wrapper consists of three components: async client (PyO3/Maturin), sync client (CFFI/setuptools), and shared logic.

**Primary Languages:** Python, Rust (PyO3 and CFFI bindings)
**Build System:** Custom dev.py CLI with Maturin (async) and setuptools (sync)
**Architecture:** Hybrid Python/Rust with PyO3 (async) and CFFI (sync) bindings

**Key Components:**
- `glide-async/` - Async client using PyO3 bindings and Unix Domain Socket communication
- `glide-sync/` - Sync client using CFFI bindings and direct FFI communication  
- `glide-shared/` - Shared Python logic used by both clients
- `tests/` - Shared test suite for both clients
- `dev.py` - CLI utility for build, test, and development tasks

## Architecture Quick Facts

**Core Implementation:** Python wrappers around glide-core Rust library
**Client Types:** GlideClient/GlideClusterClient (async), GlideClient/GlideClusterClient (sync)
**API Styles:** Async with asyncio/trio support, Sync with blocking calls
**Communication:** UDS (async), Direct FFI (sync)

**Supported Platforms:**
- Linux: Ubuntu 20+, Amazon Linux 2/2023 (x86_64, aarch64)
- macOS: 13.7+ (x86_64), 14.7+ (aarch64)
- **Note:** Alpine Linux/MUSL not supported

**Python Versions:** 3.9, 3.10, 3.11, 3.12, 3.13
**Async Frameworks:** asyncio, trio, uvloop

**Packages:** `valkey-glide` (async), `valkey-glide-sync` (sync)

## Build and Test Rules (Agents)

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

# Testing
python3 dev.py test                                    # Run all tests
python3 dev.py test --args -k <test_name>             # Run specific test
python3 dev.py test --args --async-backend=trio       # Test with trio backend
python3 dev.py test --args --async-backend=asyncio    # Test with asyncio backend

# Linting and Formatting
python3 dev.py lint                                    # Auto-fix formatting (isort, black)
python3 dev.py lint --check                           # Check formatting only

# Protobuf generation
python3 dev.py protobuf                               # Regenerate protobuf files

# Help
python3 dev.py --help                                 # Show all available commands
```

### Raw Equivalents
```bash
# Manual async client build (from glide-async/)
source ../.env/bin/activate
maturin develop --release

# Manual sync client build (from glide-sync/)
source ../.env/bin/activate
pip install -e ../glide-shared
python setup.py build_ext --inplace

# Manual test execution (from python/)
source .env/bin/activate
pytest -v

# Manual linting
source .env/bin/activate
isort . && black . && flake8 . && mypy .

# Manual protobuf generation
protoc --python_out=glide-shared/glide_shared/protobuf --pyi_out=glide-shared/glide_shared/protobuf ../glide-core/src/protobuf/*.proto
```

### Test Execution Options
```bash
# Run with existing endpoints
python3 dev.py test --args --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with TLS
python3 dev.py test --args --tls --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with specific async backend
python3 dev.py test --args --async-backend=trio --async-backend=asyncio

# Run specific test pattern
python3 dev.py test --args -k "test_set_and_get"
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
```bash
# From python/ directory
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

## Guardrails & Policies

### Generated Outputs (Never Commit)
- `.env/` - Python virtual environment
- `glide-async/target/` - Rust build artifacts (async)
- `glide-sync/target/` - Rust build artifacts (sync)
- `glide-shared/glide_shared/protobuf/` - Generated protobuf files
- `build/` - Build artifacts
- `dist/` - Distribution artifacts
- `*.egg-info/` - Package metadata
- `__pycache__/` - Python bytecode
- `.pytest_cache/` - Pytest cache
- `.mypy_cache/` - MyPy cache
- `docs/_build/` - Sphinx documentation build

### Python-Specific Rules
- **Python 3.9+ Required:** Minimum runtime version
- **Virtual Environment:** Always use `.env/` for development
- **Dual Client Support:** Maintain compatibility between async and sync clients
- **Shared Logic:** Keep common code in `glide-shared/` package
- **Async Framework Support:** Test with both asyncio and trio
- **Protobuf Updates:** Run `python3 dev.py protobuf` after proto changes
- **Documentation Style:** Follow Google Style Python Docstrings format

### Package Structure Rules
- **Async Client:** `import glide` (PyPI: `valkey-glide`)
- **Sync Client:** `import glide_sync` (PyPI: `valkey-glide-sync`)
- **Shared Logic:** `import glide_shared` (local install only)
- **Independent Packaging:** Each client has separate pyproject.toml and release cycle

## Project Structure (Essential)

```
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
**Key Features:** Dual client architecture, shared logic, multi-async framework support
**Testing:** pytest with async backend selection, shared test suite
**Platforms:** Linux (Ubuntu, AL2/AL2023), macOS (Intel/Apple Silicon)
**Dependencies:** Python 3.9+, Rust toolchain, protobuf compiler

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/python/](../examples/python/)
- **API Documentation:** [Valkey GLIDE Python docs](https://valkey.io/valkey-glide/)
- **Wiki:** [Python wrapper wiki](https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper)
- **Test Suites:** [tests/](./tests/) directory
- **Async Client:** [glide-async/](./glide-async/) directory
- **Sync Client:** [glide-sync/](./glide-sync/) directory
- **Shared Logic:** [glide-shared/](./glide-shared/) directory
- **CLI Utility:** [dev.py](./dev.py) script
