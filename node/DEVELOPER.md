# Developer Guide

This document describes how to set up your development environment to build and test the **Valkey GLIDE Node wrapper**, part of the polyglot `valkey-glide` project.

## Project Structure

- `valkey-glide/`: The polyglot root directory.
- `valkey-glide/node/`: Contains the Node.js binding (TypeScript + Rust napi-rs).
    - `src/`: TypeScript source code with modular file organization.
        - `index.ts`: Main entry point that re-exports all APIs.
        - `*.ts`: TypeScript files for client classes, commands, batching, etc..
        - `server-modules/`: Server module implementations (e.g., JSON, FT).
        - `native.js`, `native.d.ts`: Auto-generated bindings by napi-rs.
    - `build-ts/`: Compiled TypeScript output.
    - `tests/`: Jest tests.
    - `rust-client/`: napi-rs Rust crate (binds to `glide-core`).
    - `npm/`: Platform-specific packages for CD.
        - Platform-specific directories (e.g., `linux-arm64-gnu/`, `darwin-arm64/`) containing package.json and native binaries.

## Development Overview

The Node.js wrapper consists of:

- TypeScript: User-facing API organized in logical modules.
- Rust: Native module built with [`napi-rs`](https://github.com/napi-rs/napi-rs).
- Communication: Protocol Buffers for efficient data exchange.

### Build scripts overview

The build process consists of multiple steps:

1. `clean:build` - Removes previous build artifacts
2. `build-protobuf` - Generates optimized protobuf code (43% smaller than default)
3. `build:rust-client` - Builds the native Rust client binding
4. `build:ts` - Compiles TypeScript code into the build-ts directory
    - When using `build:ts:release` - Applies additional optimizations with `--stripInternal`

These steps are orchestrated by npm scripts in package.json.

## TypeScript Build Options

The TypeScript build uses different settings based on the build mode:

- **Standard Build**: Uses default TypeScript settings from `tsconfig.json`
- **Release Build**: Adds the following flags:
    - `--stripInternal`: Removes documentation marked with @internal from declarations while preserving public documentation
    - `--pretty`: Formats error messages for better readability
    - `--declaration`: Ensures type declaration files are generated with full JSDoc/TSDoc documentation

These settings optimize the code for production use while maintaining full TypeScript type information.

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

#### On Ubuntu/Debian based systems

```bash
sudo apt install -y snap
sudo snap install zig --classic --beta
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

Supported node version for development is minimum 18, but it is recommended to use one of the LTS versions, or latest.

## Building & Running

1. Clone and set up:

    ```bash
    git clone https://github.com/valkey-io/valkey-glide.git
    cd valkey-glide/node
    npm install
    ```

2. Build:

- **Fast Dev Build:**
  Quickly compiles without optimization. Best for testing and development.
  Command:

    ```bash
    npm run build
    ```

- **Benchmark Build:**
  Compiles optimized build, but install like dev build.
  Command:

    ```bash
    npm run build:benchmark
    ```

- **Release Build:**
  Fully optimized, stripped - This mimic production build.
  Command:

    ```bash
    npm run build:release
    ```

## Local Development Notes

- Rust builds are located in `node/rust-client/`. Scripts automatically `cd` into this directory when running `napi build`.
- TypeScript files are compiled into the `build-ts/` directory, maintaining the modular structure.
- The `index.ts` file re-exports all public APIs to maintain a clean interface.

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

- Jest configuration is in `node/jest.config.ts`.
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

The project includes different test groups:

```bash
# Run standard tests (excluding modules)
npm test

# Run tests with debugging options
npm run test:debug

# Run minimal tests (excludes modules and special features)
npm run test:minimum

# Run only module-specific tests
npm run test:modules
```

You can run these tests with custom flags for cluster and standalone endpoints by passing environment variables.

### Package Manager and TypeScript Types Testing

The CI workflow also tests TypeScript types and package manager compatibility:

- Tests proper TypeScript declaration file generation for downstream packages
- Verifies compatibility with Yarn package manager in addition to npm
- Tests library use in both direct consumer packages and transitive dependencies
- Ensures type definitions work correctly in multi-package environments

The test structure in `node/pm-and-types-tests/` includes:

- `package1/`: A library that depends on valkey-glide
- `package2/`: An application that depends on package1 (transitive dependency on valkey-glide)

This helps verify that:

1. Types are correctly exported and available to downstream consumers
2. The library works properly when used through different package managers
3. TypeScript type declarations are correctly passed through dependent packages

## REPL

You can experiment with the client in a live TypeScript REPL:

```bash
npx ts-node --project tsconfig.json
```

## Recommended VS Code Extensions

- Prettier
- ESLint
- Jest Runner
- rust-analyzer
