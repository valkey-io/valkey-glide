# Welcome to Valkey GLIDE!

Valkey General Language Independent Driver for the Enterprise (GLIDE) is the official open-source Valkey client library, proudly part of the Valkey organization. Our mission is to make your experience with Valkey and Redis OSS seamless and enjoyable. Whether you're a seasoned developer or just starting out, Valkey GLIDE is here to support you every step of the way.

# Why Choose Valkey GLIDE?

- **Community and Open Source**: Join our vibrant community and contribute to the project. We are always here to respond, and the client is for the community.
- **Reliability**: Built with best practices learned from over a decade of operating Redis OSS-compatible services.
- **Performance**: Optimized for high performance and low latency.
- **High Availability**: Designed to ensure your applications are always up and running.
- **Cross-Language Support**: Implemented using a core driver framework written in Rust, with language-specific extensions to ensure consistency and reduce complexity.
- **Stability and Fault Tolerance**: We brought our years of experience to create a bulletproof client.
- **Backed and Supported by AWS and GCP**: Ensuring robust support and continuous improvement of the project.

## Documentation

See GLIDE's Go [documentation site](https://glide.valkey.io/languages/go).  

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - GO Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

Linux:

-   Ubuntu 20 (x86_64/amd64 and arm64/aarch64)
-   Amazon Linux 2 (AL2) and 2023 (AL2023) (x86_64)

**Note: Currently Alpine Linux / MUSL is NOT supported.**

macOS:

- macOS 14.7 (Apple silicon/aarch_64)
- macOS 13.7 (x86_64/amd64)

## GO supported versions

Valkey GLIDE Go supports Go version 1.22 and above.

## Installation and Setup

To install Valkey GLIDE in your Go project, follow these steps:

1. Open your terminal in your project directory.
2. Execute the commands below:
    ```bash
    $ go get github.com/valkey-io/valkey-glide/go/v2
    $ go mod tidy
    ```
3. After installation, you can start up a Valkey server and run one of the examples in [Basic Examples](#basic-examples).

### Alpine Linux / MUSL

If you are running on Alpine Linux or otherwise require a MUSL-based build, you must add the 'musl' tag to your build.

```
export GOFLAGS := -tags=musl
```

## Basic Examples


### Standalone Example:

```go
package main

import (
    "context"
    "fmt"

    glide "github.com/valkey-io/valkey-glide/go/v2"
    "github.com/valkey-io/valkey-glide/go/v2/config"
)

func main() {
    host := "localhost"
    port := 6379

    config := config.NewClientConfiguration().
        WithAddress(&config.NodeAddress{Host: host, Port: port})

    client, err := glide.NewClient(config)
    if err != nil {
        fmt.Println("There was an error: ", err)
        return
    }

    res, err := client.Ping(context.Background())
    if err != nil {
        fmt.Println("There was an error: ", err)
        return
    }
    fmt.Println(res) // PONG

    client.Close()
}
```

### Cluster Example:

```go
package main

import (
    "context"
    "fmt"

    glide "github.com/valkey-io/valkey-glide/go/v2"
    "github.com/valkey-io/valkey-glide/go/v2/config"
)

func main() {
    host := "localhost"
    port := 7001

    config := config.NewClusterClientConfiguration().
        WithAddress(&config.NodeAddress{Host: host, Port: port})

    client, err := glide.NewClusterClient(config)
    if err != nil {
        fmt.Println("There was an error: ", err)
        return
    }

    res, err := client.Ping(context.Background())
    if err != nil {
        fmt.Println("There was an error: ", err)
        return
    }
    fmt.Println(res) // PONG

    client.Close()
}
```

For more code examples please refer to [examples.md](examples/examples.md).

### Cluster Scan

The cluster scan feature allows you to iterate over all keys in a cluster. You can optionally filter by pattern, type, and control batch size.

#### Basic Cluster Scan

```go
cursor := models.NewClusterScanCursor()
allKeys := []string{}

for !cursor.IsFinished() {
    result, err := client.Scan(context.Background(), cursor)
    if err != nil {
        fmt.Println("Error:", err)
        break
    }
    allKeys = append(allKeys, result.Keys...)
    cursor = result.Cursor
}
```

#### Cluster Scan with Options

```go
opts := options.NewClusterScanOptions().
    SetMatch("user:*").              // Filter by pattern
    SetCount(100).                   // Batch size hint
    SetType(constants.StringType).   // Filter by key type
    SetAllowNonCoveredSlots(true)    // Allow scanning even if some slots are not covered

cursor := models.NewClusterScanCursor()
for !cursor.IsFinished() {
    result, err := client.ScanWithOptions(context.Background(), cursor, *opts)
    if err != nil {
        fmt.Println("Error:", err)
        break
    }
    // Process result.Keys
    cursor = result.Cursor
}
```

**Note**: The `AllowNonCoveredSlots` option is useful when the cluster is not fully configured or some nodes are down. It allows the scan to proceed even if some hash slots are not covered by any node.

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](DEVELOPER.md) file.

## Cross-Compilation Guide

### Cross-Compiling with Docker

Valkey GLIDE uses CGO to interface with its Rust-based core, shipped as pre-built static libraries for each target platform, which can make cross-compilation challenging. Docker provides a convenient solution for cross-compiling applications that use Valkey GLIDE.

#### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) installed on your system
- Your Go application using Valkey GLIDE

#### Cross-Compiling for Linux (AMD64/ARM64)

The official Golang Docker image includes the necessary tools for cross-compiling to Linux targets:

```bash
# Build for Linux AMD64
docker run --rm -v "$PWD":/app -w /app golang:1.22 \
  bash -c "CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go build -o myapp-linux-amd64 ./..."

# Build for Linux ARM64
docker run --rm -v "$PWD":/app -w /app golang:1.22 \
  bash -c "CGO_ENABLED=1 GOOS=linux GOARCH=arm64 CC=aarch64-linux-gnu-gcc go build -o myapp-linux-arm64 ./..."
```

#### Cross-Compiling for macOS

Due to Apple's platform restrictions, cross-compiling CGO code for macOS requires building on a macOS system:

```bash
# On a macOS system
CGO_ENABLED=1 GOOS=darwin GOARCH=amd64 go build -o myapp-darwin-amd64 ./...
CGO_ENABLED=1 GOOS=darwin GOARCH=arm64 go build -o myapp-darwin-arm64 ./...
```

### Setting Up GitHub Actions for Multi-Platform Builds

You can use GitHub Actions to automatically build your application for all supported platforms. Here's a sample workflow file:

```yaml
name: Multi-Platform Build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    name: Build ${{ matrix.os }}-${{ matrix.arch }}
    runs-on: ${{ matrix.runner }}
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: linux
            arch: amd64
            runner: ubuntu-latest
          - os: linux
            arch: arm64
            runner: ubuntu-latest
          - os: darwin
            arch: amd64
            runner: macos-latest
          - os: darwin
            arch: arm64
            runner: macos-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up Go
      uses: actions/setup-go@v5
      with:
        go-version: '1.22'

    - name: Build Linux
      if: matrix.os == 'linux'
      run: |
        if [ "${{ matrix.arch }}" = "amd64" ]; then
          CGO_ENABLED=1 GOOS=linux GOARCH=amd64 go build -o myapp-linux-amd64 ./...
        else
          CGO_ENABLED=1 GOOS=linux GOARCH=arm64 CC=aarch64-linux-gnu-gcc go build -o myapp-linux-arm64 ./...
        fi

    - name: Build macOS
      if: matrix.os == 'darwin'
      run: |
        CGO_ENABLED=1 GOOS=darwin GOARCH=${{ matrix.arch }} go build -o myapp-darwin-${{ matrix.arch }} ./...

    - name: Upload artifacts
      uses: actions/upload-artifact@v4
      with:
        name: myapp-${{ matrix.os }}-${{ matrix.arch }}
        path: myapp-${{ matrix.os }}-${{ matrix.arch }}
```

This workflow:

1. Runs on both push to main and pull requests
2. Creates a build matrix for all four supported platforms
3. Uses the appropriate runner for each OS
4. Sets the correct build flags for each platform
5. Uploads the compiled binaries as artifacts

You can customize this workflow to fit your specific application needs, such as adding tests, packaging steps, or deployment actions.

### Troubleshooting Cross-Compilation

If you encounter issues during cross-compilation:

1. **Missing Libraries**: Ensure Valkey GLIDE is properly installed and its pre-compiled libraries are available
2. **Linker Errors**: Check that CGO is enabled and the correct cross-compiler is being used
3. **Architecture Mismatch**: Verify that you're using the correct GOARCH value for your target platform
4. **Docker Issues**: Make sure your Docker container has sufficient resources and access to the source code

For more complex cross-compilation scenarios or if you encounter specific issues, please open a GitHub issue for assistance.
