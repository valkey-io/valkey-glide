# AGENTS: Node.js Client Context for Agentic Tools

This file provides AI agents and developers with the minimum but sufficient context to work productively with the Valkey GLIDE Node.js client. It covers build commands, testing, contribution requirements, and essential guardrails specific to the Node.js/TypeScript implementation.

## Repository Overview

This is the Node.js/TypeScript client binding for Valkey GLIDE, providing both standalone and cluster client implementations. The Node.js wrapper communicates with the Rust core via direct NAPI function calls (not socket IPC or protobuf for command flow).

**Primary Languages:** TypeScript, Rust (NAPI bindings)
**Build System:** npm with TypeScript compiler
**Architecture:** TypeScript wrapper around glide-core Rust library via direct NAPI-RS bindings

**Key Components:**

- `src/` - Main TypeScript client library
- `rust-client/` - Rust NAPI bindings (`src/lib.rs` client handle, `src/pool.rs` client pool and isolated scopes)
- `build-ts/` - Generated: compiled TypeScript, protobuf JS, NAPI `native.js`/`native.d.ts`, and the copied `.node` binary
- `tests/` - Jest test suites and utilities
- `pm-and-types-tests/` - Package manager and TypeScript types testing
- `hybrid-node-tests/` - CommonJS/ESM/pnpm consumer smoke tests
- `npm/` - Per-platform native binary packages (`@valkey/valkey-glide-<platform>`)

## Architecture Quick Facts

**Core Implementation:** TypeScript wrapper around glide-core Rust library via NAPI-RS
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster), GlideMonitorClient (`MONITOR` stream), `ClientPool` (client-instance pool), `IsolatedScope` (dedicated connection for WATCH/MULTI/SELECT state); the last two are backed by `rust-client/src/pool.rs`
**API Style:** Async-first with Promise return types
**Command Protocol:** Direct NAPI function calls on `GlideClientHandle` (not protobuf for command flow)

### Command Flow (JS to Rust)

1. JS calls `createLeakedStringVec(args)` to allocate args on the Rust heap, receiving a split pointer `[low, high]`
2. JS calls `clientHandle.sendCommand(callbackIdx, requestType, argsPointerHigh, argsPointerLow, routeBytes?)` synchronously
3. Rust reclaims the args via `Box::from_raw`, checks inflight limits, builds a `redis::Cmd`, and sends a `WorkerMessage` to the pinned worker thread via `mpsc::unbounded_channel`
4. The worker thread's `spawn_local` task executes the command against glide-core's `Client`
5. Response is pushed to a per-client `ResponseBuffer` (parking_lot mutex-protected `Vec<CommandResponse>`)
6. If the buffer was empty, a `ThreadsafeFunction` wake callback fires to JS (one wake per batch, not per response)
7. JS `handleResponsesAvailable` calls `clientHandle.drainResponses()` to get all pending `CommandResponse` objects
8. For responses with value pointers, JS calls `valueFromSplitPointer(high, low, stringDecoder)` to reclaim and decode the `redis::Value`

Other NAPI entry points on the handle: `sendBatch`, `invokeScript`, `clusterScan`, `updateConnectionPassword`, `refreshIamToken`. Free functions: `CreateDirectClient`, `createMonitorClient`, `registerAddressResolver`, `InitOpenTelemetry`, `InitInternalLogger`; the `createPool`/`pool*` and `scope*` functions live in `rust-client/src/pool.rs`.

### Worker Pool Architecture

- A global `LocalPoolHandle` (from `tokio_util`) manages `num_cpus` worker threads
- Each client is pinned to a single worker thread via `spawn_pinned` for thread-local command execution
- Commands within a client run concurrently via `task::spawn_local` on that thread
- The pool is reference-counted: created on first client, dropped when last client closes (enables clean Node.js exit)

### Inflight Request Tracking

- An `AtomicIsize` counter initialized to the inflight limit (default 1000)
- `sendCommand` atomically decrements before sending; returns `false` if limit exceeded
- Worker increments the counter after each response is buffered

### Where Protobuf Is Still Used

- **Connection request**: serialized as protobuf bytes, parsed by Rust (`ProtobufConnectionRequest`)
- **Cluster routing**: `Routes` protobuf encoded to bytes in JS, parsed by Rust via `parse_route_bytes`
- **Command type enums**: `RequestType` protobuf enum maps command names to numeric IDs

**Supported Platforms:** see [README.md: System Requirements](./README.md#system-requirements). Native binaries are built for the `napi.triples` list in `package.json` and published as `npm/<platform>/` packages.

**Node.js:** `>=20` (`engines` in `package.json`); npm 11+ recommended on Linux for libc-aware optional-dependency download.

**Package:** `@valkey/valkey-glide` on npm

## Build and Test Rules (Agents)

### Preferred (npm Scripts)

```bash
# Build commands (each runs `npm ci` first; `build` also runs `clean:build` via the prebuild hook)
npm run build                    # Development build (unoptimized, testing_utilities feature)
npm run build:release           # Release build (optimized, stripped)
npm run build:benchmark         # Release build with the testing_utilities feature (what benchmarks/ expects)

# Incremental builds
npm run build:ts                # TypeScript only (tsc)
npm run build:rust-client       # Rust NAPI module only (dev profile)
npm run build-protobuf          # Protobuf JS/d.ts generation only

# Testing (each builds ../utils first; the suites start their own servers)
npm test                        # All suites except ServerModules, parallel
npm run test:debug              # All suites including ServerModules, serial (--runInBand)
npm run test:minimum            # Serial, skips GlideJson/GlideFt/pubsub/kill tests
npm run test:modules            # Serial, only GlideJson/GlideFt tests (needs a modules-enabled server)

# Linting and Formatting (lint runs `npm ci` in the repo root first)
npm run lint                    # ESLint + Prettier check
npm run lint:fix                # ESLint --fix + Prettier write
npm run prettier:check          # Prettier check only
npm run prettier:format         # Prettier write only

# Development utilities
npm run repl                    # ts-node REPL against src/
npm run clean:build             # Remove build-ts/, rust-client/target/, .node binaries
npm run clean                   # Also removes node_modules/ and the tracked lock files
```

### Raw Equivalents

```bash
# Manual TypeScript compilation
npx tsc

# Manual protobuf generation (same three steps `npm run build-protobuf` runs; do not drop the `replace`)
mkdir -p build-ts
npx pbjs -t static-module -w commonjs --no-verify --no-convert -o build-ts/ProtobufMessage.js ../glide-core/src/protobuf/*.proto
npx pbts -o build-ts/ProtobufMessage.d.ts build-ts/ProtobufMessage.js
npx replace "this\.encode\(message, writer\)\.ldelim" "this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim" build-ts/ProtobufMessage.js

# Manual Rust build (from rust-client/; produces target/release/libvalkey_glide.dylib|.so, not the .node file napi builds)
cargo build --release

# Manual test execution (unfiltered: starts servers per suite and runs everything, including ServerModules)
npx jest --verbose

# Manual linting
npx eslint -c ../eslint.config.mjs
npx prettier --check .
```

### Test Execution Options

```bash
# Run with existing endpoints
npm run test:debug -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# Run with TLS
npm run test:debug -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379 --tls=true

# Run specific test pattern (test:debug includes ServerModules.test.ts, which fails without --cluster-endpoints)
npm run test:debug -- --testPathIgnorePatterns=ServerModules --testNamePattern="batch"

# Run server modules tests
npm run test:modules -- --cluster-endpoints=localhost:7000
```

### Testing Connection Establishment (Avoid Count-Based CLIENT LIST Assertions)

Do not assert on `CLIENT LIST` connection _counts_ to detect whether a specific
client connected (e.g. baseline + 1). Since the socket-IPC-to-NAPI change,
`close()` only drops the JS handle and the Rust worker exits later, so
connections from a preceding test can linger in `CLIENT LIST` when the baseline
is taken and disappear one poll later, producing a net-zero delta and a flaky
test. Polling until the count "stabilizes" does not fix this: two matching
readings only prove stability for one poll window.

Instead, identify the client deterministically by a unique `clientName`
(passed in the client configuration) and assert `CLIENT LIST` excludes that name
before the first command and includes it afterwards. See the
`lazy connection establishes only on first command` test in
`tests/GlideClient.test.ts` and the pattern in `tests/NodeDiscoveryMode.test.ts`.

## Contribution Requirements

### Developer Certificate of Origin (DCO) Signoff REQUIRED

All commits must include a `Signed-off-by` line:

```bash
# Add signoff to new commits
git commit -s -m "feat(node): add new command implementation"

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

**Example:** `feat(node): implement cluster scan with routing options`

### Code Quality Requirements

**ESLint + Prettier:**

```bash
npm run lint                    # Must pass before commit
npm run lint:fix                # Fix linting and formatting issues
```

**TypeScript Compilation:**

```bash
npm run build:ts                # Must compile without errors
```

**Rust Components:**

```bash
# From rust-client/ directory
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

## Guardrails & Policies

### Generated Outputs (Never Commit)

- `build-ts/` - Compiled TypeScript, generated protobuf and NAPI glue, copied `.node` binary
- `node_modules/`, `rust-client/node_modules/` - Dependencies
- `rust-client/target/` - Rust build artifacts
- `rust-client/index.*`, `rust-client/valkey-glide.*.node`, `src/valkey-glide.*.node` - NAPI outputs
- `test-report*.html`, `coverage/` - Jest outputs
- `glide-logs/` - Runtime logs
- `yarn.lock` - Project uses npm; `package-lock.json` and `rust-client/package-lock.json` ARE committed (`npm ci` needs them)

### Node.js-Specific Rules

- **Node.js 20+ Required:** Minimum runtime version
- **npm 11+ Recommended:** For Linux users (better libc support)
- **Promise-based APIs:** All client methods return Promises
- **Resource Management:** Call `client.close()` to cleanup connections
- **Protobuf Updates:** Run `npm run build-protobuf` after proto changes
- **NAPI Bindings:** Native module built per platform/architecture

### TypeScript Guidelines

- Maintain strict TypeScript configuration
- Export all public APIs through `index.ts`
- Use proper type definitions for all public interfaces
- Test TypeScript declarations with package manager tests

## Project Structure (Essential)

```
node/
├── src/                        # TypeScript client implementation
│   ├── BaseClient.ts           # Core client logic: sendCommand, drainResponses, handleResponse
│   ├── GlideClient.ts          # Standalone client
│   ├── GlideClusterClient.ts   # Cluster client
│   ├── GlideMonitorClient.ts   # MONITOR stream client
│   ├── ClientPool.ts           # Client-instance pool
│   ├── IsolatedScope.ts        # Dedicated-connection scopes (WATCH/MULTI/SELECT)
│   ├── Commands.ts             # Command builders (createGet, createSet, etc.)
│   ├── server-modules/         # GlideJson, GlideFt
│   └── index.ts                # Main exports
├── rust-client/                # Rust NAPI bindings
│   ├── src/lib.rs              # GlideClientHandle, ResponseBuffer, worker pool, NAPI exports
│   ├── src/pool.rs             # Client pool and isolated-scope NAPI exports
│   └── Cargo.toml              # Rust dependencies
├── build-ts/                   # Generated outputs (see Guardrails)
├── tests/                      # Jest test suites
├── pm-and-types-tests/         # Package manager compatibility tests
├── hybrid-node-tests/          # CommonJS/ESM/pnpm consumer tests
├── npm/                        # Per-platform native binary packages
├── package.json                # npm configuration and scripts
├── tsconfig.json               # TypeScript configuration
└── jest.config.ts              # Jest test configuration
```

## Quality Gates (Agent Checklist)

- [ ] Build passes: `npm run build:release` succeeds
- [ ] All tests pass: `npm test` succeeds
- [ ] Linting passes: `npm run lint` succeeds
- [ ] TypeScript compiles: `npm run build:ts` succeeds
- [ ] Rust components build: `cd rust-client && cargo build --release` succeeds
- [ ] No generated outputs committed (check `.gitignore`)
- [ ] DCO signoff present: `git log --format="%B" -n 1 | grep "Signed-off-by"`
- [ ] Conventional commit format used
- [ ] TypeScript types properly exported
- [ ] Package manager compatibility maintained
- [ ] Native binaries not committed

## Quick Facts for Reasoners

**Package:** `@valkey/valkey-glide` on npm registry
**API Style:** Promise-based async, modern ES modules support
**Client Types:** GlideClient (standalone), GlideClusterClient (cluster)
**Key Features:** TypeScript support, NAPI-RS native bindings, direct NAPI command calls with response buffering
**Testing:** Jest test framework, interactive REPL, package manager compatibility tests
**Platforms:** Linux (glibc/musl), macOS (Intel/Apple Silicon)
**Dependencies:** Node.js 20+, protobufjs (connection/routing only), long, platform-specific native binaries

## If You Need More

- **Getting Started:** [README.md](./README.md)
- **Development Setup:** [DEVELOPER.md](./DEVELOPER.md)
- **Examples:** [../examples/node/](../examples/node/)
- **API Documentation:** [Valkey GLIDE Node.js docs](https://valkey.io/valkey-glide/node/)
- **Documentation:** [GLIDE Docs](https://glide.valkey.io/)
- **Test Suites:** [tests/](./tests/) directory
- **Package Manager Tests:** [pm-and-types-tests/](./pm-and-types-tests/)
- **Rust Client:** [rust-client/](./rust-client/) directory
