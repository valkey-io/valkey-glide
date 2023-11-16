# Developer Guide

This document describes how to set up your development environment to build and test the Babushka Node wrapper.

### Development Overview

The Babushka Node wrapper consists of both TypeScript and Rust code. Rust bindings for Node.js are implemented using [napi-rs](https://github.com/napi-rs/napi-rs). The Node and Rust components communicate using the [protobuf](https://github.com/protocolbuffers/protobuf) protocol.


### Build

- Follow the building instructions for the Node wrapper in the [Build from source](https://github.com/aws/babushka/blob/main/node/README.md#build-from-source) section to clone the code and build the wrapper.
- For a fast build, execute `npm run build`. This will perform a full,  unoptimized build, which is suitable for developing tests. Keep in mind that performance is significantly affected in an unoptimized build, so it's required to build with the `build:release` or `build:benchmark` option when measuring performance.
- If your modifications are limited to the TypeScript code, run `npm run build-external` to build the external package without rebuilding the internal package.
- If your modifications are limited to the Rust code, execute `npm run build-internal` to build the internal package and generate TypeScript code.
- To generate Node's protobuf files, execute `npm run build-protobuf`. Keep in mind that protobuf files are generated as part of full builds (e.g., `build`, `build:release`, etc.).

> Note: Once building completed, you'll find the compiled JavaScript code in the `node/build-ts` folder.


### Test

To run tests, use the following command:

```bash
npm test
```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```

### Linters
Development on the Node wrapper may involve changes in either the TypeScript or Rust code. Each language has distinct linter tests that must be passed before committing changes.

#### Language-specific Linters

__TypeScript:__
- ESLint
- Prettier

__Rust:__
- clippy
- fmt

#### Running the linters
1. TypeScript
    ```bash
    # Run from the `node` folder
    npm install eslint-plugin-import@latest  @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-tsdoc eslint typescript eslint-plugin-import@latest eslint-config-prettier
    npm i
    npx eslint . --max-warnings=0
    ```
2. Rust
    ```bash
    # Run from the `node/rust-client` folder
    rustup component add clippy rustfmt
    cargo clippy --all-features --all-targets -- -D warnings
    cargo fmt --manifest-path ./Cargo.toml --all
    ```

### Recommended extensions for VS Code
- [Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) - JavaScript / TypeScript formatter.
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) - linter.
- [Jest Runner](https://marketplace.visualstudio.com/items?itemName=firsttris.vscode-jest-runner) - in-editor test runner.
- [Jest Test Explorer](https://marketplace.visualstudio.com/items?itemName=kavod-io.vscode-jest-test-adapter) - adapter to the VSCode testing UI.
-   [rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language support for VSCode.
