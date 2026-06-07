# Shared GitHub Actions for valkey-glide Ecosystem

This directory contains composite actions shared across valkey-glide language repositories (valkey-glide-csharp, valkey-glide-php, valkey-glide-ruby). These actions provide consistent CI/CD infrastructure and reduce duplication.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Available Actions](#available-actions)
  - [install-shared-dependencies](#install-shared-dependencies)
  - [install-engine](#install-engine)
  - [start-valkey-docker](#start-valkey-docker)
  - [install-rust-and-protoc](#install-rust-and-protoc)
  - [install-zig](#install-zig)
- [Platform Support Matrix](#platform-support-matrix)
- [Submodule Configuration](#submodule-configuration)
- [Reusable Workflows](#reusable-workflows)
- [Version Pinning](#version-pinning)

## Prerequisites

Language repositories must add valkey-glide as a git submodule to use these shared actions:

```bash
# Add submodule (one-time setup)
git submodule add https://github.com/valkey-io/valkey-glide.git

# Initialize and update submodule
git submodule update --init --recursive
```

## Available Actions

### install-shared-dependencies

Installs platform-specific dependencies for valkey-glide builds. This is the primary action for setting up build environments across different platforms.

**Location:** `.github/actions/install-shared-dependencies/action.yml`

#### Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `os` | Yes | - | The current operating system (e.g., `ubuntu`, `macos`, `windows`, `amazon-linux`) |
| `target` | No | `''` | Rust target toolchain (e.g., `x86_64-unknown-linux-gnu`, `aarch64-apple-darwin`) |
| `engine-version` | No | `''` | Valkey engine version to install (optional) |
| `language` | No | - | The language being built (optional, for language-specific setup) |
| `github-token` | Yes | - | GitHub token for API access (typically `${{ secrets.GITHUB_TOKEN }}`) |

#### Behavior

- **Ubuntu/Debian:** Installs `git`, `gcc`, `pkg-config`, `openssl`, `libssl-dev` via `apt`
- **macOS:** Installs `openssl`, `coreutils` via `brew`
- **Amazon Linux:** Installs `gcc`, `pkgconfig`, `openssl`, `openssl-devel`, and other build tools via `yum`
- **Alpine/MUSL:** Installs `protobuf-dev`, `musl-dev`, `make`, `gcc`, and Rust via `apk`
- **Windows:** Sets up WSL with Ubuntu 22.04 and Python
- **Windows ARM64:** Skips Valkey server installation (platform limitation)

#### Example Usage

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install dependencies
        uses: ./valkey-glide/.github/actions/install-shared-dependencies
        with:
          os: ubuntu
          engine-version: '9.0'
          github-token: ${{ secrets.GITHUB_TOKEN }}
```

```yaml
# Cross-compilation example
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install dependencies for cross-compilation
        uses: ./valkey-glide/.github/actions/install-shared-dependencies
        with:
          os: ubuntu
          target: aarch64-unknown-linux-gnu
          engine-version: '9.0'
          github-token: ${{ secrets.GITHUB_TOKEN }}
```

---

### install-engine

Installs and caches a specific Valkey server version for testing. Uses intelligent caching based on version, git SHA, and platform.

**Location:** `.github/actions/install-engine/action.yml`

#### Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `engine-version` | Yes | - | Valkey version to install (e.g., `9.0`, `8.1`) |

#### Outputs

| Name | Description |
|------|-------------|
| `cache-key` | Cache key used for the installation |

#### Behavior

- Computes a cache key from version, git SHA, and platform
- Restores from cache if available, otherwise builds from source
- Creates backward-compatible symlinks (`redis-*` → `valkey-*`)
- Adds binaries to `PATH`
- Uses WSL shell on Windows, bash on other platforms

#### Cache Key Format

```
valkey-{version}-{git-sha-8chars}-{os}-{arch}
```

Example: `valkey-9.0-a1b2c3d4-Linux-X64`

#### Example Usage

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install Valkey
        uses: ./valkey-glide/.github/actions/install-engine
        with:
          engine-version: '9.0'

      - name: Verify installation
        run: valkey-server --version
```

---

### start-valkey-docker

Starts standalone and cluster Valkey servers with modules (Search, JSON, Bloom, LDAP) using Docker containers.

**Location:** `.github/actions/start-valkey-docker/action.yml`

> **Note:** This action requires Linux. It uses `--network host` which is not supported on macOS or Windows Docker.

#### Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `standalone-port` | No | `6389` | Port for standalone module server |
| `cluster-port-start` | No | `8001` | Starting port for cluster nodes (uses 6 consecutive ports) |

#### Outputs

| Name | Description |
|------|-------------|
| `standalone-endpoint` | Standalone server endpoint (e.g., `localhost:6389`) |
| `cluster-endpoints` | Comma-separated cluster node endpoints |

#### Environment Variables Set

| Variable | Example Value | Description |
|----------|---------------|-------------|
| `MODULES_STANDALONE_ENDPOINT` | `localhost:6389` | Standalone server endpoint |
| `MODULES_CLUSTER_ENDPOINTS` | `localhost:8001,localhost:8002,...` | Comma-separated cluster endpoints |

#### Behavior

- Pulls `valkey/valkey-bundle:9.1` Docker image
- Starts a standalone Valkey server with modules on the configured port
- Starts a 6-node Valkey cluster (3 primaries, 3 replicas) on consecutive ports
- Waits for all servers to be ready (PING returns PONG)
- Fails with container logs if servers don't start within 15 seconds

#### Example Usage

```yaml
jobs:
  integration-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Start Valkey servers
        uses: ./valkey-glide/.github/actions/start-valkey-docker

      - name: Run tests
        run: |
          echo "Standalone: $MODULES_STANDALONE_ENDPOINT"
          echo "Cluster: $MODULES_CLUSTER_ENDPOINTS"
          npm test
```

```yaml
# Custom ports example (to avoid conflicts)
jobs:
  integration-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Start Valkey servers on custom ports
        uses: ./valkey-glide/.github/actions/start-valkey-docker
        with:
          standalone-port: '7000'
          cluster-port-start: '7100'

      - name: Run tests
        run: |
          # Uses custom ports
          valkey-cli -p 7000 PING
          valkey-cli -p 7100 CLUSTER INFO
```

---

### install-rust-and-protoc

Installs Rust toolchain and protobuf compiler for non-MUSL targets.

**Location:** `.github/actions/install-rust-and-protoc/action.yml`

#### Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `rust-version` | No | `stable` | Rust version to install |
| `target` | No | `''` | Rust target triple to add (e.g., `x86_64-unknown-linux-gnu`) |

#### Example Usage

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install Rust and protoc
        uses: ./valkey-glide/.github/actions/install-rust-and-protoc
        with:
          rust-version: 'stable'
          target: 'x86_64-unknown-linux-gnu'
```

---

### install-zig

Installs Zig compiler for cross-compilation on linux-gnu targets.

**Location:** `.github/actions/install-zig/action.yml`

#### Inputs

| Name | Required | Default | Description |
|------|----------|---------|-------------|
| `zig-version` | No | `0.11.0` | Zig version to install |

#### Example Usage

```yaml
jobs:
  cross-compile:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install Zig
        uses: ./valkey-glide/.github/actions/install-zig
        with:
          zig-version: '0.11.0'
```

---

## Platform Support Matrix

| Action | Ubuntu | macOS | Windows | Amazon Linux | Alpine/MUSL |
|--------|:------:|:-----:|:-------:|:------------:|:-----------:|
| install-shared-dependencies | ✓ | ✓ | ✓ | ✓ | ✓ |
| install-engine | ✓ | ✓ | ✓ (WSL) | ✓ | ✓ |
| start-valkey-docker | ✓ | ✗ | ✗ | ✓ | ✗ |
| install-rust-and-protoc | ✓ | ✓ | ✓ | ✓ | ✓ |
| install-zig | ✓ | ✓ | ✓ | ✓ | ✓ |

**Notes:**
- **Windows:** Uses WSL (Windows Subsystem for Linux) for Valkey server operations
- **Windows ARM64:** Valkey server installation is skipped due to platform limitations
- **start-valkey-docker:** Requires Linux due to Docker host networking requirements
- **Alpine/MUSL:** Uses `apk` package manager; install-rust-and-protoc is skipped (handled differently)

---

## Submodule Configuration

### Adding the Submodule (Language Repos)

```bash
# Add valkey-glide as a submodule
git submodule add https://github.com/valkey-io/valkey-glide.git

# Commit the submodule addition
git add .gitmodules valkey-glide
git commit -m "Add valkey-glide submodule for shared CI/CD actions"
```

### Updating the Submodule

```bash
# Update to latest main branch
cd valkey-glide
git fetch origin main
git checkout origin/main
cd ..
git add valkey-glide
git commit -m "Update valkey-glide submodule"
```

### Referencing Actions from Language Repos

Language repos reference shared actions using relative paths from the submodule:

```yaml
# In language repo workflow file
steps:
  - uses: actions/checkout@v4
    with:
      submodules: recursive  # Important: checkout submodules!

  - uses: ./valkey-glide/.github/actions/install-shared-dependencies
    with:
      os: ubuntu
      github-token: ${{ secrets.GITHUB_TOKEN }}
```

### Workflow Checkout Configuration

Always checkout with submodules when using shared actions:

```yaml
- uses: actions/checkout@v4
  with:
    submodules: recursive
    # OR
    submodules: true  # For shallow submodule checkout
```

---

## Reusable Workflows

In addition to composite actions, the main repository provides reusable workflows that can be called from language repos:

| Workflow | Description | Location |
|----------|-------------|----------|
| `semgrep.yml` | Semgrep security scanning | `.github/workflows/semgrep.yml` |
| `git-secrets-scan.yml` | Git secrets scanning | `.github/workflows/git-secrets-scan.yml` |
| `stale-issues.yml` | Stale issue management | `.github/workflows/stale-issues.yml` |

### Calling Reusable Workflows

```yaml
# In language repo: .github/workflows/security.yml
name: Security Scanning

on: [push, pull_request]

jobs:
  semgrep:
    uses: valkey-io/valkey-glide/.github/workflows/semgrep.yml@main
    with:
      config: 'p/csharp'  # Language-specific config

  git-secrets:
    uses: valkey-io/valkey-glide/.github/workflows/git-secrets-scan.yml@main
```

---

## Version Pinning

All external GitHub Actions in shared actions are pinned to commit SHAs (not version tags) for supply chain security:

| Action | SHA | Version |
|--------|-----|---------|
| `actions/checkout` | `11bd71901bbe5b1630ceea73d27597364c9af683` | v4.2.2 |
| `actions/cache` | `0c907a75c2c80ebcb7f088228285e798b750cf8f` | v4.2.1 |
| `actions/setup-python` | `a26af69be951a213d495a4c3e4e4022e16d87065` | v5.6.0 |
| `actions/stale` | `28ca1036281a5e5922ead5184a1bbf96e5fc984e` | v9.0.0 |
| `dtolnay/rust-toolchain` | `d0592fe69e35bc8f12e3dbaf9ad2694d976cb8e3` | 1.80.0 |
| `arduino/setup-protoc` | `c65c819552d16ad3c9b72d9dfd5ba5237b9c906b` | v3.0.0 |
| `goto-bus-stop/setup-zig` | `7ab2955eb728f5440978d5824358023be3a2802d` | v2.2.0 |
| `Vampire/setup-wsl` | `887f39deb6c0976365e546926fe66f41b77d65ff` | v6.0.0 |

When updating external actions, always update both the SHA and the version comment.

---

## Complete Workflow Example

Here's a complete example showing how a language repo workflow might use multiple shared actions:

```yaml
# .github/workflows/ci.yml in valkey-glide-csharp
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    runs-on: ${{ matrix.os }}

    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install shared dependencies
        uses: ./valkey-glide/.github/actions/install-shared-dependencies
        with:
          os: ${{ matrix.os == 'ubuntu-latest' && 'ubuntu' || matrix.os == 'macos-latest' && 'macos' || 'windows' }}
          engine-version: '9.0'
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - name: Build
        run: dotnet build

      - name: Test
        run: dotnet test

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Install dependencies
        uses: ./valkey-glide/.github/actions/install-shared-dependencies
        with:
          os: ubuntu
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - name: Start Valkey servers with modules
        uses: ./valkey-glide/.github/actions/start-valkey-docker
        with:
          standalone-port: '6389'
          cluster-port-start: '8001'

      - name: Run integration tests
        run: dotnet test --filter "Category=Integration"
        env:
          VALKEY_STANDALONE: ${{ env.MODULES_STANDALONE_ENDPOINT }}
          VALKEY_CLUSTER: ${{ env.MODULES_CLUSTER_ENDPOINTS }}
```

---

## Troubleshooting

### Submodule Not Found

If you see errors like "Can't find 'action.yml' file":

1. Ensure submodules are checked out:
   ```yaml
   - uses: actions/checkout@v4
     with:
       submodules: recursive
   ```

2. Verify submodule is initialized locally:
   ```bash
   git submodule update --init --recursive
   ```

### Docker Action Fails on Non-Linux

The `start-valkey-docker` action only works on Linux runners. For other platforms, use a native Valkey installation via `install-engine`.

### Cache Not Working

If Valkey installation always rebuilds:

1. Check cache key format in action output
2. Ensure the version tag exists in the Valkey repository
3. Check GitHub Actions cache limits (10GB per repository)

### Windows ARM64 Issues

Valkey server installation is automatically skipped on Windows ARM64. The `VALKEY_SKIP_INSTALL` environment variable is set to `true` in this case.
