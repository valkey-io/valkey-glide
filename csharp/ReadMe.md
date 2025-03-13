# Developer Guide

This document describes how to set up your development environment to build and test the Valkey GLIDE C# wrapper.

### Development Overview

We're excited to share that the GLIDE C# client is currently in development! However, it's important to note that this client is a work in progress and is not yet complete or fully tested. Your contributions and feedback are highly encouraged as we work towards refining and improving this implementation. Thank you for your interest and understanding as we continue to develop this C# wrapper.

The C# client contains the following parts:

1. Rust part of the C# client located in `lib/src`; it communicates with [GLIDE core rust library](../glide-core/README.md).
2. C# part of the client located in `lib`; it translates Rust async API into .Net async API.
3. Integration tests for the C# client located in `tests` directory.
4. A dedicated benchmarking tool designed to evaluate and compare the performance of Valkey GLIDE and other .Net clients. It is located in `<repo root>/benchmarks/csharp`.

TODO: examples, UT, design docs

### Build from source

Software Dependencies:

- .Net SDK 6 or later
- git
- rustup
- valkey

Please also install the following packages to build [GLIDE core rust library](../glide-core/README.md):

- GCC
- protoc (protobuf compiler)
- pkg-config
- openssl
- openssl-dev

#### Prerequisites

**.Net**

It is recommended to visit https://dotnet.microsoft.com/en-us/download/dotnet to download .Net installer.
You can also use a package manager to install the .Net SDK:

```bash
brew install dotnet@6         # MacOS
sudo apt-get install dotnet6  # Linux
```

**Protoc installation**

Download a binary matching your system from the [official release page](https://github.com/protocolbuffers/protobuf/releases/tag/v25.1) and make it accessible in your $PATH by moving it or creating a symlink.
For example, on Linux you can copy it to `/usr/bin`:

```bash
sudo cp protoc /usr/bin/
```

**Valkey installation**

See the [Valkey installation guide](https://valkey.io/topics/installation/) to install the Valkey server and CLI.


**Dependencies installation for Ubuntu**

```bash
sudo apt-get update -y
sudo apt-get install -y openssl openssl-dev gcc
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

**Dependencies installation for MacOS**

```bash
brew update
brew install git gcc pkgconfig openssl
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Building and installation steps

Before starting this step, make sure you've installed all software requirements.

1. Clone the repository

```bash
git clone https://github.com/valkey-io/valkey-glide.git
cd valkey-glide
```

2. Build the C# wrapper

```bash
dotnet build
```

3. Run tests

Run test suite from `csharp` directory:

```bash
dotnet test
```

You can also specify which framework version to use for testing (by defaults it runs on net6.0 and net8.0) by adding `--framework net8.0` or `--framework net6.0` accordingly.

By default, `dotnet test` produces no reporting and does not display the test results.  To log the test results to the console and/or produce a test report, you can use the `--logger` attribute with the test command.  For example:

- `dotnet test --logger "html;LogFileName=TestReport.html"` (HTML reporting) or
- `dotnet test --logger "console;verbosity=detailed"` (console reporting)

To filter tests by class name or method name add the following expression to the command line: `--filter "FullyQualifiedName~<test or class name>"` (see the [.net testing documentation](https://learn.microsoft.com/en-us/dotnet/core/testing/selective-unit-tests?pivots=xunit) for more details).

A command line may contain all listed above parameters, for example:

```bash
dotnet test --framework net8.0 --logger "html;LogFileName=TestReport.html" --logger "console;verbosity=detailed" --filter "FullyQualifiedName~GetReturnsNull" --results-directory .
```

4. Run benchmark

    1. Ensure that you have installed `valkey-server` and `valkey-cli` on your host. You can find the valkey installation guide above.
    2. Execute the following command from the root project folder:

    ```bash
    cd <repo root>/benchmarks/csharp
    dotnet run --framework net8.0 --dataSize 1024 --resultsFile test.json --concurrentTasks 4 --clients all --host localhost --clientCount 4
    ```

    3. Use a [helper script](../benchmarks/README.md) which runs end-to-end benchmarking workflow:

    ```bash
    cd <repo root>/benchmarks
    ./install_and_test.sh -csharp
    ```

    Run benchmarking script with `-h` flag to get list and help about all command line parameters.

5. Lint the code

Before making a contribution, ensure that all new user APIs and non-obvious code is well documented, and run the code linters and analyzers.

C# linter:

```bash
dotnet format --verify-no-changes --verbosity diagnostic
```

C# code analyzer:

```bash
dotnet build --configuration Lint
```

Rust linter:

```bash
cargo clippy --all-features --all-targets -- -D warnings
cargo fmt --all -- --check
```

6. Test framework and style

The CSharp Valkey-Glide client uses xUnit v3 for testing code. The test code styles are defined in `.editorcofing` (see `dotnet_diagnostic.xUnit..` rules). The xUnit rules are enforced by the [xUnit analyzers](https://github.com/xunit/xunit.analyzers) referenced in the main xunit.v3 NuGet package. If you choose to use xunit.v3.core instead, you can reference xunit.analyzers explicitly. For additional info, please, refer to https://xunit.net and https://github.com/xunit/xunit
