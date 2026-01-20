# CLAUDE.md

Valkey GLIDE is an official open-source client library for Valkey and Redis OSS. It uses a core driver written in Rust with language-specific wrappers for Python, Java, Node.js, and Go, and other languages in seperate repositories.

## Hard Constraints (non-negotiable)
- NO task completion without tests covering it and passing
- NO PR creation without all subagent feedback addressed
- NEVER assume - always verify with tests and benchmarks
- NEVER ignore bugs, even out of scope - open an issue

## Rules
### Always
- Direct and concise, no compliments or apologies
- Ask if unsure, stop and reassess if looping
- Fetch web resources fresh, don't rely on cached data
- If asked to do X a certain way, do it that way. Disagree? Raise it, but don't change without approval

### When Writing
- Commit frequently with meaningful messages - git is our diary
- Focus on one language + its core bindings. Core changes = consider all bindings
- AI agent files: context-efficient, use XML for structured data in Markdown
- Keep PRs small and focused, split if too large

### Before Task Completion
- Tests cover it and pass
- Linter/formatter ran for changed languages

### Before Push
- `git pull --rebase upstream main`, resolve conflicts
- Exception: if on a feature branch rebasing onto another feature branch, rebase onto that branch instead
- Run tests for relevant scope

### Before PR Creation/Update
- Run subagents in parallel, address all feedback:
    - performance analysis
    - code quality, style, best practices
    - test coverage
    - documentation clarity
    - security vulnerabilities and edge cases

## What the user cares about (all equally important):
- Performance - low latency, high throughput
- Reliability - robust error handling, edge cases
- Usability - clear APIs, good documentation, best DX
- Maintainability - clean code, modular design, tests, simplicity
- Correctness - verify with tests and benchmarks, not assumptions

---

## Project Structure

```
glide-core/     # Rust core driver - handles connection, protocol, clustering
python/         # Python client wrapper
java/           # Java client wrapper
node/           # Node.js client wrapper
go/             # Go client wrapper
ffi/            # Foreign function interface for language bindings
logger_core/    # Rust logging infrastructure
utils/          # Test utilities and cluster management scripts
benchmarks/     # Performance benchmarks
examples/       # Usage examples for each language
docs/           # Documentation
```

## Architecture: Language Bindings to Core

| Language     | Mechanism | Native Library              | Communication      |
|--------------|-----------|-----------------------------|--------------------|
| Python Async | PyO3      | valkey-glide (python/glide-async/) | Unix socket IPC    |
| Python Sync  | CFFI      | glide-ffi (ffi/)            | FFI calls          |
| Java         | JNI       | glide-rs (java/)            | JNI calls          |
| Go           | CGO       | glide-ffi (ffi/)            | FFI calls          |
| Node.js      | NAPI v2   | rust-client (node/rust-client/)  | Unix socket IPC    |

Socket IPC wrappers → `socket_listener` → `glide-core` → Valkey/Redis
FFI wrappers → `glide-core` → Valkey/Redis

## Context Retrieval

<context-sources>
  <language name="python-async">
    <triggers>python async, PyO3, glide-async, socket IPC python</triggers>
    <start-with>python/glide-async/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>python/glide-async/Cargo.toml</entry>
    <bindings>python/glide-async/src/lib.rs</bindings>
    <client>python/glide-async/python/glide/glide_client.py</client>
    <commands>python/glide-shared/glide_shared/commands/</commands>
    <tests>python/tests/</tests>
  </language>
  <language name="python-sync">
    <triggers>python sync, CFFI, glide-sync, synchronous python</triggers>
    <start-with>python/glide-sync/glide_sync/_glide_ffi.py</start-with>
    <depends-on>ffi</depends-on>
    <entry>python/glide-sync/setup.py</entry>
    <bindings>python/glide-sync/glide_sync/_glide_ffi.py</bindings>
    <client>python/glide-sync/glide_sync/glide_client.py</client>
    <commands>python/glide-sync/glide_sync/sync_commands/</commands>
    <tests>python/tests/</tests>
  </language>
  <language name="java">
    <triggers>java, JNI, glide-rs, Java client</triggers>
    <start-with>java/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>java/Cargo.toml</entry>
    <bindings>java/src/lib.rs</bindings>
    <client>java/client/src/main/java/glide/api/</client>
    <commands>java/client/src/main/java/glide/api/commands/</commands>
    <tests>java/client/src/test/java/glide/</tests>
  </language>
  <language name="node">
    <triggers>node, nodejs, NAPI, typescript, rust-client node</triggers>
    <start-with>node/rust-client/src/lib.rs</start-with>
    <depends-on>core</depends-on>
    <entry>node/rust-client/Cargo.toml</entry>
    <bindings>node/rust-client/src/lib.rs</bindings>
    <client>node/src/BaseClient.ts</client>
    <commands>node/src/Commands.ts</commands>
    <tests>node/tests/</tests>
  </language>
  <language name="go">
    <triggers>go, golang, CGO, go client</triggers>
    <start-with>go/base_client.go</start-with>
    <depends-on>ffi</depends-on>
    <entry>go/Makefile</entry>
    <bindings>go/callbacks.go</bindings>
    <client>go/base_client.go</client>
    <commands>go/internal/interfaces/</commands>
    <tests>go/integTest/</tests>
  </language>
  <language name="core">
    <triggers>glide-core, rust core, socket listener, protocol, clustering</triggers>
    <start-with>glide-core/src/client/mod.rs</start-with>
    <entry>glide-core/Cargo.toml</entry>
    <client>glide-core/src/client/mod.rs</client>
    <socket>glide-core/src/socket_listener.rs</socket>
    <protobuf>glide-core/src/protobuf/</protobuf>
    <tests>glide-core/tests/</tests>
  </language>
  <language name="ffi">
    <triggers>ffi, foreign function interface, C bindings, libglide</triggers>
    <start-with>ffi/src/lib.rs</start-with>
    <entry>ffi/Cargo.toml</entry>
    <bindings>ffi/src/lib.rs</bindings>
  </language>
</context-sources>
