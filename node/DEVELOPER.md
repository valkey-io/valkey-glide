# Developer Guide

This document describes how to set up your development environment to build and test Valkey GLIDE Node wrapper.

### Development Overview

The GLIDE Node wrapper consists of both TypeScript and Rust code. Rust bindings for Node.js are implemented using [napi-rs](https://github.com/napi-rs/napi-rs). The Node and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.

### Build from source

#### Prerequisites

Software Dependencies

##### **Note:** Nodejs Supported Version

If your Nodejs version is below the supported version specified in the client's [documentation](https://github.com/valkey-io/valkey-glide/blob/main/node/README.md#nodejs-supported-version), you can upgrade it using [NVM](https://github.com/nvm-sh/nvm?tab=readme-ov-file#install--update-script).

- npm
- git
- GCC
- pkg-config
- cmake
- protoc (protobuf compiler)
- openssl
- openssl-dev
- rustup

**Dependencies installation for Ubuntu**

```bash
sudo apt update -y
sudo apt install -y nodejs npm git gcc pkg-config protobuf-compiler openssl libssl-dev cmake
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
# Check the installed node version
node -v
```

> **Note:** Ensure that you installed a supported Node.js version. For Ubuntu 22.04 or earlier, please refer to the instructions [here](#note-nodejs-supported-version) to upgrade your Node.js version.

**Dependencies installation for CentOS**

```bash
sudo yum update -y
sudo yum install -y nodejs git gcc pkgconfig protobuf-compiler openssl openssl-devel gettext cmake
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for MacOS**

```bash
brew update
brew install nodejs git gcc pkgconfig protobuf openssl cmake
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Valkey Server and CLI**
See the [Valkey installation guide](https://valkey.io/topics/installation/) to install the Valkey server and CLI.

#### Building and installation steps

Before starting this step, make sure you've installed all software requirments.

1. Clone the repository:
    ```bash
    git clone https://github.com/valkey-io/valkey-glide.git
    cd valkey-glide
    ```
2. Install all node dependencies:

    ```bash
    cd node
    npm i
    cd rust-client
    npm i
    cd ..
    ```

3. Build the Node wrapper (Choose a build option from the following and run it from the `node` folder):
    1. Build in release mode, stripped from all debug symbols (optimized and minimized binary size):

    ```bash
    npm run build:release
    ```

    2. Build in release mode with debug symbols (optimized but large binary size):

    ```bash
    npm run build:benchmark
    ```

    3. For testing purposes, you can execute an unoptimized but fast build using:

    ```bash
    npm run build
    ```

    Once building completed, you'll find the compiled JavaScript code in the`./build-ts` folder.

4. Run tests:
    1. Ensure that you have installed server and valkey-cli on your host. You can download Valkey at the following link: [Valkey Download page](https://valkey.io/download/).
    2. Execute the following commands from the node folder:

        ```bash
        # Build first (required before testing)
        npm run build

        # Run standard tests (excluding server modules)
        npm test

        # Run tests with debugging options
        npm run test:debug

        # Run minimal tests (faster subset of tests)
        npm run test:minimum

        # Run only server module tests (requires valkey modules)
        npm run test:modules
        ```

5. Integrating the built GLIDE package into your project:
   Add the package to your project using the folder path with the command `npm install <path to GLIDE>/node`.

- For a fast build, execute `npm run build`. This will perform a full, unoptimized build, which is suitable for developing tests. Keep in mind that performance is significantly affected in an unoptimized build, so it's required to build with the `build:release` or `build:benchmark` option when measuring performance.
- If your modifications are limited to the TypeScript code, run `npm run build:ts` to build only TypeScript code without rebuilding the Rust client.
- If your modifications are limited to the Rust code, execute `npm run build:rust-client` to build only the Rust client.
- To generate Node's protobuf files, execute `npm run build-protobuf`. Keep in mind that protobuf files are generated as part of full builds (e.g., `build`, `build:release`, etc.).

> Note: Once building completed, you'll find the compiled JavaScript code in the `node/build-ts` folder. The index.ts file re-exports all APIs for a cleaner module structure.

### Troubleshooting

- If the build fails after running `npx tsc` because `glide-rs` isn't found, check if your npm version is in the range 9.0.0-9.4.1, and if so, upgrade it. 9.4.2 contains a fix to a change introduced in 9.0.0 that is required in order to build the library.

### Test

To run tests, use the following command:

```bash
npm test
```

Simplified test suite skips few time consuming tests and runs faster:

```bash
npm test-minimum
```

To execute a specific test, use the [`testNamePattern`](https://jestjs.io/docs/cli#--testnamepatternregex) option with `test-dbg` script. For example:

```bash
npm run test-dbg -- --testNamePattern="batch"
```

IT suite starts the server for testing - standalone and cluster installation using `cluster_manager` script.
To run the integration tests with existing servers, run the following command:

```bash
npm run test-dbg -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379

# If those endpoints use TLS, add `--tls=true` (applies to both endpoints)
npm run test-dbg -- --cluster-endpoints=localhost:7000 --standalone-endpoints=localhost:6379 --tls=true
```

Parameters `cluster-endpoints`, `standalone-endpoints` and `tls` could be used with all test suites.

By default, the server modules tests do not run using `npm run test`. This test suite also does not start the server.
In order to run these tests, use:

```bash
npm run test-modules -- --cluster-endpoints=<address>:<port>
```

Note: these tests don't run with standalone server as of now.

### Package Manager and TypeScript Types Testing

The project includes tests for TypeScript types and package manager compatibility:

- Tests proper TypeScript declaration file generation for downstream packages
- Verifies compatibility with Yarn package manager in addition to npm
- Tests library use in both direct consumer packages and transitive dependencies

The test structure in `node/pm-and-types-tests/` includes:

- `depend-on-glide-package/`: A library that depends on valkey-glide
- `depend-on-glide-dependent/`: An application that depends on depend-on-glide-package (transitive dependency)

### REPL (interactive shell)

It is possible to run an interactive shell synced with the currect client code to test and debug it:

```bash
npx ts-node --project tsconfig.json
```

This shell allows executing typescript and javascript code line by line:

```typescript
import { GlideClient, GlideClusterClient } from ".";
let client = await GlideClient.createClient({
    addresses: [{ host: "localhost", port: 6379 }],
});
let clusterClient = await GlideClusterClient.createClient({
    addresses: [{ host: "localhost", port: 7000 }],
});
await client.ping();
```

After applying changes in client code you need to restart the shell.

It has command history and bash-like search (`Ctrl+R`).

Shell hangs on exit (`Ctrl+D`) if you don't close the clients. Use `Ctrl+C` to kill it and/or close clients before exit.

### Developer Utility Scripts

Development on the Node wrapper involves various scripts for linting, formatting, and managing the development environment.

#### Development Environment Scripts

```bash
# Launch a TypeScript REPL for interactive testing
npm run repl

# Clean only build artifacts
npm run clean:build

# Complete cleanup including node_modules
npm run clean
```

#### Linters and Formatters

Development on the Node wrapper may involve changes in either the TypeScript or Rust code. Each language has distinct linter tests that must be passed before committing changes.

##### TypeScript and general files

```bash
# Run from the node folder
# Check code quality with ESLint
npm run lint

# Automatically fix linting issues
npm run lint:fix

# Check code formatting only
npm run prettier:check

# Format code automatically
npm run prettier:format
```

##### Rust code

```bash
# Run from the `node/rust-client` folder
# Format all files (Rust and others)
npm run format

# Format only Rust files
npm run format:rs

# Format only non-Rust files with Prettier
npm run format:prettier

# Alternatively, use Rust tools directly:
rustup component add clippy rustfmt
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --manifest-path ./Cargo.toml --all
```

### Recommended extensions for VS Code

- [Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) - JavaScript / TypeScript formatter.
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) - linter.
- [Jest Runner](https://marketplace.visualstudio.com/items?itemName=firsttris.vscode-jest-runner) - in-editor test runner.
- [Jest Test Explorer](https://marketplace.visualstudio.com/items?itemName=kavod-io.vscode-jest-test-adapter) - adapter to the VSCode testing UI.
- [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language support for VSCode.
