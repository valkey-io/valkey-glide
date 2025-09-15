# JNI Migration Status - Valkey GLIDE Java Client

## Quick Context & Priorities
- Goal: Replace UDS transport with a direct JNI path for Windows compatibility and improved performance while preserving the existing Java API and protobuf flow.
- Sources of truth (in order): 1) Code + tests, 2) CLAUDE.md, 3) This file. The `memory-bank/*` is legacy context and not authoritative.
- Branch focus right now: finalize response conversion and routing fidelity, then stabilize observability and error mapping.

### Open Issues (Prioritized)
- TYPE response conversion: ensure String is returned (not GlideString) where required by API and tests.
- Binary/String conversion: correct context-aware conversion pipeline (binary-safe GlideString vs UTF-8 String).
- AZ affinity routing: fix cluster routing to honor availability-zone preferences.
- OpenTelemetry export: ensure spans write to the expected path (e.g., `/tmp/spans.json`) during tests.
- Error mapping: return the correct Java exception types (e.g., `ClosingException` vs generic `RuntimeException`).
- Script show/invocation: verify EVALSHA→SCRIPT LOAD→retry flow and consistent routing; ensure `scriptShow` passes.

### Next Actions Checklist
- [ ] Reproduce and isolate TYPE conversion failures; add/adjust conversion in JNI response pipeline.
- [ ] Harden binary-safe vs UTF-8 response mapping and large-payload DirectByteBuffer path (>16KB).
- [ ] Validate AZ affinity route selection in cluster mode; add focused integ test if missing.
- [ ] Fix OpenTelemetry file export; confirm CI and local paths match test expectations.
- [ ] Align error type mapping to expected Java exceptions across connection and shutdown paths.
- [ ] Re-verify script EVALSHA fallback and routing consistency; confirm `scriptShow` integ tests pass.
  - Implemented: ScriptInvocationPointers handling in JNI (UTF-8 and binary paths) to mirror UDS large-args behavior.

## Script Lifecycle (UDS Reference)

### Registration (Java → Core)
- `Script` construction stores code by hash in core:
  - Java: `client/src/main/java/glide/api/models/Script.java` calls `ScriptResolver.storeScript(bytes)`.
  - Native: `ScriptResolver.storeScript` → core `scripts_container::add_script(code)` returns SHA1.
  - Core: `glide-core/src/scripts_container.rs` keeps code in a ref-counted map keyed by hash.

### Invocation (Java → Core → Server)
- Java builds `CommandRequest.ScriptInvocation` with `hash`, `keys`, `args`:
  - `client/src/main/java/glide/managers/CommandManager.java#prepareScript(...)`.
- Core receives invocation and runs fallback flow with preserved routing:
  - `glide-core/src/socket_listener.rs` → `invoke_script(hash, keys, args, client, routes)`.
  - `glide-core/src/client/mod.rs::invoke_script`: `EVALSHA` → on `NoScriptError` → fetch code via `get_script(hash)` → `SCRIPT LOAD` with the same `routing` → retry `EVALSHA`.

### Show/Exists/Flush/Kill
- Java submits `RequestType::ScriptShow` or standard commands via `submitNewCommand`.
- Core routes them like normal single commands using provided `Routes`.

### Drop (Java → Core)
- `Script#close()` calls `ScriptResolver.dropScript(hash)` → core `scripts_container::remove_script(hash)` decrements refcount and removes when zero.

## Mapping To JNI (What We Must Mirror)
- Provide JNI natives for `storeScript`/`dropScript` that call `add_script`/`remove_script`.
- For execution, call core `invoke_script` (not manual `EVALSHA`) and pass through the original `routing`.
- Ensure oversized key/arg handling parity: UDS uses `ScriptInvocationPointers` when payload > threshold; JNI must use equivalent zero-copy or pointer-backed path.
- Keep `ScriptShow` et al. as single-command requests through the same routing path.

## Gaps To Address In JNI
- Verify JNI path actually stores scripts in core before invocation (constructor-side `storeScript`).
- Ensure `SCRIPT LOAD` uses the same route as the failing `EVALSHA` (already corrected in core; confirm JNI passes route).
- Implement large payload path analogous to UDS pointers to avoid excessive copying.

### Quick Repro Commands
```bash
cd java
./gradlew build
./gradlew :client:test
./gradlew :integTest:test -Dtls=true
RUST_LOG=debug ./gradlew :integTest:test --tests "*scriptShow_test*"
```

### Key Paths
- Java API: `java/client/src/main/java/glide/api/...`
- JNI bridge (Rust): `java/src/lib.rs`
- Core logic: `glide-core/src/...`
- Tests: `java/client/src/test/java/...`, `java/integTest/src/test/java/...`

### Decision Log (append as we proceed)
- [ ] YYYY-MM-DD: Summary of change/decision and rationale.

## Executive Summary
This document provides a comprehensive overview of the ongoing JNI migration for the Valkey GLIDE Java client. We are migrating from a Unix Domain Socket (UDS) based implementation to a direct JNI (Java Native Interface) implementation for improved Windows compatibility and performance.

## Project Architecture Overview

### Core Components
1. **glide-core** (`/glide-core/`): Shared Rust implementation containing all Redis/Valkey protocol logic, connection management, clustering, and script handling
2. **Java Client** (`/java/`): Java SDK with JNI bindings to the Rust core
3. **FFI Layer** (`/java/src/lib.rs`): Rust code implementing JNI native methods
4. **Reference UDS Implementation** (`/Users/avifen/valkey-glide/java/`): Original working implementation using Unix Domain Sockets

### Architecture Flow
```
Java Application
    ↓
GlideClient/GlideClusterClient (Public API)
    ↓
BaseClient (Command implementations)
    ↓
JniCommandManager (Protobuf serialization)
    ↓
GlideNativeBridge (JNI native method declarations)
    ↓
lib.rs (Rust JNI implementation)
    ↓
glide-core (Rust core logic)
    ↓
Redis/Valkey Server
```

## Migration Context

### Why JNI?
- **Windows Compatibility**: UDS doesn't work on Windows
- **Performance**: Direct memory access via DirectByteBuffer for large responses (>16KB)
- **Simplified Architecture**: Removes the socket communication layer

### Key Differences from UDS
1. **Communication**: Direct JNI calls instead of socket messages
2. **Memory Management**: Uses Java `long` for native handles
3. **Async Handling**: CompletableFuture callbacks managed by AsyncRegistry
4. **Binary Safety**: Supports both String and GlideString (binary-safe) operations

## Work Completed

### Phase 1: Core Infrastructure ✅
- JNI bridge implementation
- Async callback system (AsyncRegistry)
- Protobuf command serialization
- Basic command execution

### Phase 2: Timeout Handling ✅
- **Initial Problem**: Binary timeout tests failing (BLPOP, BRPOP, etc.)
- **Root Cause**: Java-side timeout competing with Rust-side timeout
- **Solution**: Removed all Java-side timeout logic, trust Rust completely
- **Result**: All 20 binary timeout tests passing

### Phase 3: Script Management Fix ✅
- **Problem**: Script tests failing with "NoScriptError: No matching script"
- **Root Cause**: Binary ScriptInvocation handler was manually building EVALSHA commands
- **Solution**: Changed to use `client.invoke_script()` with proper EVALSHA → EVAL fallback
- **Files Modified**: `/java/src/lib.rs` lines 1524-1546
- **Result**: Core script functionality restored

## Current Challenge: scriptShow Command ⚠️ IN PROGRESS

### The Problem
The `scriptShow` command tests are failing with "NoScriptError: No matching script". The error occurs during `invokeScript()` call, not during `scriptShow()` itself.

### Fixes Applied (Session Date: September 14, 2025)

#### Fix 1: Binary ScriptInvocation Handler ✅
**Location**: `/java/src/lib.rs` lines 1524-1556
**Change**: Modified binary mode ScriptInvocation to use `client.invoke_script()` instead of manually building EVALSHA commands
```rust
// BEFORE: Manual EVALSHA command building
let mut cmd = redis::cmd("EVALSHA");
cmd.arg(script.hash.as_bytes());
client.send_command(&cmd, routing).await

// AFTER: Use invoke_script with proper fallback
client.invoke_script(&script.hash, &keys, &args, routing).await
```

#### Fix 2: Script Routing in glide-core ✅
**Location**: `/glide-core/src/client/mod.rs` line 717
**Change**: Fixed routing bug where SCRIPT LOAD was sent to different node than EVALSHA
```rust
// BEFORE: SCRIPT LOAD with no routing
self.send_command(&load, None).await?;

// AFTER: SCRIPT LOAD with same routing as EVALSHA
self.send_command(&load, routing.clone()).await?;
```

### Current Test Results
- **Status**: Tests still failing after both fixes
- **Error**: "Script invocation failed: NoScriptError: No matching script"
- **Failure Point**: Line 5279 in SharedCommandTests.java - `client.invokeScript(script).get()`

### Analysis of the Issue
The problem appears to be deeper than initially thought:

1. **Expected Flow**:
   - `invokeScript()` → tries EVALSHA
   - Gets NoScriptError → loads script with SCRIPT LOAD
   - Retries EVALSHA → should succeed

2. **Actual Behavior**:
   - `invokeScript()` is failing with NoScriptError being propagated to Java
   - This suggests the fallback mechanism isn't working properly

3. **Key Questions**:
   - Why is the NoScriptError being propagated instead of handled internally?
   - Is the script actually being stored in the glide-core container?
   - Is there a mismatch between how scripts are handled in binary vs UTF-8 mode?

### Differences from UDS Implementation
The UDS implementation works correctly with the same test. Need to investigate:
- How UDS handles script storage and retrieval
- Whether there's a difference in script lifecycle management
- If there's a timing or initialization issue specific to JNI

## Next Steps

### Immediate Investigation Needed
1. **Compare Script Loading**: How does UDS handle `invokeScript` vs JNI?
   - Check if UDS uses `SCRIPT LOAD` command
   - Check if JNI is missing a script registration step

2. **Trace Script Flow**:
   - Follow a script from `invokeScript()` to execution in both implementations
   - Identify where scripts get loaded into Redis server cache

3. **Potential Solutions**:
   - Ensure `invokeScript` also loads script into Redis cache (not just glide-core)
   - Modify `scriptShow` to check glide-core container first
   - Add explicit `SCRIPT LOAD` when scripts are added

## Testing Status

### Passing ✅
- All binary timeout tests (BLPOP, BRPOP, etc.)
- Basic script execution (invokeScript)
- Script management commands (scriptExists, scriptFlush, scriptKill)
- Most unit tests

### Failing ❌
- SharedCommandTests.scriptShow_test (4 variants)
- Some JedisClusterTest tests (unrelated to current work)

## Important Implementation Rules

### Memory Management
- Native handles stored as `long` in Java
- DirectByteBuffer for zero-copy large transfers
- Proper cleanup of native resources

### Error Handling
- Rust errors propagated as Java exceptions
- Connection errors trigger reconnection
- Cluster redirections handled transparently

### Code Style
- Match existing patterns in codebase
- No unnecessary comments
- Follow Java naming conventions
- Preserve exact indentation when editing

## File Locations

### Current JNI Implementation
- `/Users/avifen/valkey-glide-1/java/` - JNI-based Java client
- `/Users/avifen/valkey-glide-1/java/src/lib.rs` - Rust JNI implementation
- `/Users/avifen/valkey-glide-1/glide-core/` - Shared Rust core

### Reference UDS Implementation
- `/Users/avifen/valkey-glide/java/` - Original UDS-based implementation

### Key Files for Current Issue
- `java/integTest/src/test/java/glide/SharedCommandTests.java` - Failing test
- `java/src/lib.rs` - JNI command handlers
- `glide-core/src/client/mod.rs` - Core client implementation
- `glide-core/src/scripts_container.rs` - Script storage

## Build & Test Commands

```bash
# Build
cd java
./gradlew build

# Run specific failing test
./gradlew :integTest:test --tests "*scriptShow_test*"

# Run with debug logging
RUST_LOG=debug ./gradlew :integTest:test --tests "*scriptShow_test*"

# Run all tests
./gradlew test integTest:test
```

## Current Session Context
We have successfully fixed the binary ScriptInvocation issue but discovered a separate problem with `scriptShow`. The next step is to understand why the UDS implementation successfully loads scripts into Redis's cache while JNI doesn't, and implement the missing piece to ensure compatibility.
