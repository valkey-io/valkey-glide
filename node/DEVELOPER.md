# Developer Guide

This document describes how to set up your development environment to build and test the **Valkey GLIDE Node wrapper**, part of the polyglot `valkey-glide` project.

## Project Structure

- `valkey-glide/`: The polyglot root directory.
- `valkey-glide/node/`: Contains the Node.js binding (TypeScript + Rust napi-rs).
  - `src/`: TypeScript source code.
  - `tests/`: Jest tests.
  - `rust-client/`: napi-rs Rust crate (binds to `glide-core`).
  - `scripts/`: Build helpers (e.g., `build-release.sh`).
- `valkey-glide/glide-core/`: The core Rust logic, shared across all language bindings.
- `.github/`: CI/CD workflows and composite actions.

## Development Overview

The Node.js wrapper consists of:

- TypeScript: User-facing API.
- Rust: Native module built with [`napi-rs`](https://github.com/napi-rs/napi-rs).
- Communication: Protocol Buffers for efficient data exchange.

## Build Modes

- **Fast Dev Build:**  
  Quickly compiles without optimization. Best for testing and development.  
  Command:  

  ```bash
  npm run build
  ```

- **Benchmark Build:**  
  Compiles unoptimized but installs like a release build.  
  Command:  

  ```bash
  npm run build:benchmark
  ```

- **Release Build:**  
  Fully optimized, stripped, and cross-compiled (glibc 2.17/musl support via Zig).  
  Command:  

  ```bash
  npm run build:release
  ```

## Prerequisites

- Node.js (see [supported version](https://github.com/valkey-io/valkey-glide/blob/main/node/README.md#nodejs-supported-version))
- npm
- Rust (via `rustup`)
- `protoc` (protobuf compiler)
- GCC, pkg-config, OpenSSL

### Installing Dependencies

#### On Ubuntu/Debian

```bash
sudo apt update
sudo apt install -y build-essential pkg-config libssl-dev protobuf-compiler
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

#### On macOS

```bash
brew install protobuf pkg-config openssl
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

### Installing Zig (for cross-compilation)

Zig is used to enable deterministic, static builds compatible with older glibc versions like 2.17.

#### On Ubuntu/Debian

```bash
sudo apt install -y unzip curl
curl -LO https://ziglang.org/download/0.11.0/zig-linux-x86_64-0.11.0.tar.xz
tar -xf zig-linux-x86_64-0.11.0.tar.xz
sudo mv zig-linux-x86_64-0.11.0/zig /usr/local/bin/
```

#### On macOS (with Homebrew)

```bash
brew install zig
```

Ensure `zig` is available in your PATH:
```bash
zig version
```

#### Node.js and npm

Install Node.js and npm from [nodejs.org](https://nodejs.org/) or via nvm:

```bash
nvm install --lts
nvm use --lts
```

## Building & Running

1. Clone and set up:

    ```bash
    git clone https://github.com/valkey-io/valkey-glide.git
    cd valkey-glide/node
    npm install
    ```

2. Build:
    - Fast dev build:

      ```bash
      npm run build
      ```

    - Benchmark build:

      ```bash
      npm run build:benchmark
      ```

    - Release build:

      ```bash
      npm run build:release
      ```

3. Run tests:

    ```bash
    npm test
    ```

## Local Development Notes

- Rust builds are located in `node/rust-client/`. Scripts automatically `cd` into this directory when running `napi build`.
- The `scripts/build-release.sh` script dynamically adjusts build flags based on the target platform.

## Linting & Formatting

### TypeScript

- Lint your TypeScript code using ESLint:

  ```bash
  npm run lint
  ```

- Automatically fix linting issues:

  ```bash
  npm run lint:fix
  ```

- Format code with Prettier:

  ```bash
  npm run format
  ```

### Rust

- Run Clippy linter:

  ```bash
  cd rust-client
  cargo clippy --all-features --all-targets -- -D warnings
  ```

- Format Rust code:

  ```bash
  cargo fmt
  ```

## Testing

- Jest configuration is in `node/jest.config.js`.
- Tests are located in `node/tests/`.

Run tests with:

```bash
npm run test
```

Run tests in watch mode:

```bash
npm run test:watch
```

### Integration and Module Tests

You can run integration and module tests with custom flags for cluster and standalone endpoints by passing environment variables or npm script arguments. Refer to the test files and package.json scripts for details.

## REPL

You can experiment with the client in a live TypeScript REPL:

```bash
npx ts-node --project tsconfig.json
```

Example:

```typescript
import { GlideClient } from ".";
const client = await GlideClient.createClient({ addresses: [{ host: "localhost", port: 6379 }] });
await client.ping();
```

```typescript
import { GlideClusterClient } from ".";
const cluster = await GlideClusterClient.createClient({ addresses: [{ host: "localhost", port: 7000 }] });
await cluster.ping();
```

## CI/CD Awareness

- Workflows live under `.github/workflows/`.
- Build system uses napi-rs CLI (`build`, `artifacts`, `prepublish`).
- Cross-compilation (glibc, musl, macOS) uses Zig in `build-release.sh`.
- Artifacts are prepared and published automatically via the GitHub Actions pipeline.

## Recommended VS Code Extensions

- Prettier
- ESLint
- Jest Runner
- rust-analyzer

## Troubleshooting

- **glide-rs not found:** Upgrade npm to >=9.4.2 if needed.
- **Build failures:** Check Rust and native build tools installation.
- **Protobuf errors:** Ensure `protoc` is installed and in PATH.
- **OpenSSL issues:** Verify OpenSSL development headers are installed (`libssl-dev` on Debian/Ubuntu).
