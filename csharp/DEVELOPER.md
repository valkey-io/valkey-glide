# Developer Guide

This document describes how to set up your development environment to build and test the GLIDE for Redis C# wrapper.

### Development Overview

The GLIDE C# wrapper consists of both C# and Rust code.

### Build from source

#### Prerequisites

Software Dependencies

-   .net sdk 6 or 8
-   git
-   GCC
-   pkg-config
-   protoc (protobuf compiler)
-   openssl
-   openssl-dev
-   rustup

**Dependencies installation for MacOS**

visit https://dotnet.microsoft.com/en-us/download/dotnet
to download .net 6 and 8 installer
```bash
brew update
brew install git gcc pkgconfig protobuf openssl
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source "$HOME/.cargo/env"
```

#### Building and installation steps

Before starting this step, make sure you've installed all software requirments.

1.  Clone the repository:
```bash
VERSION=0.1.0 # You can modify this to other released version or set it to "main" to get the unstable branch
git clone --branch ${VERSION} https://github.com/aws/glide-for-redis.git
cd glide-for-redis
```
2.  Initialize git submodule:
```bash
git submodule update --init --recursive
```
3.  Build the c# wrapper:
    Choose a build option from the following and run it from the `csharp` folder:

    1. Build in release mode, stripped from all debug symbols:

        ```bash
        dotnet build
        ```

    2. Build with debug symbols:

        ```bash
        dotnet build --debug
        ```

4.  Run tests:

    1. Ensure that you have installed redis-server and redis-cli on your host. You can find the Redis installation guide at the following link: [Redis Installation Guide](https://redis.io/docs/install/install-redis/install-redis-on-linux/).

    2. Execute the following command from the root project folder:
        ```bash
        docker run --name some-redis -d redis -p 6379:6379
        cd benchmarks/csharp
        dotnet run --framework net8.0  --dataSize 1024 --resultsFile test.json --concurrentTasks 4 --clients all --host localhost --clientCount 4
        ```

### Submodules

After pulling new changes, ensure that you update the submodules by running the following command:

```bash
git submodule update
```
