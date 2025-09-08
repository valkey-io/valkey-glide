# Valkey GLIDE Development Instructions

**ALWAYS follow these instructions first. Only use search or additional context gathering if the information here is incomplete or found to be in error.**

Valkey GLIDE is a multi-language client library for Valkey and Redis. This repository contains support for Java, Python, Node.js, and Go. The project uses a shared Rust core (`glide-core`) with Foreign Function Interface (`ffi`) bindings and language-specific wrappers.

**Additional Language Support** (in separate repositories):
- **C#**: [valkey-io/valkey-glide-csharp](https://github.com/valkey-io/valkey-glide-csharp) - Under active development
- **PHP**: [valkey-io/valkey-glide-php](https://github.com/valkey-io/valkey-glide-php) - Under active development  
- **Ruby**: [valkey-io/valkey-glide-ruby](https://github.com/valkey-io/valkey-glide-ruby) - Under active development
- **C++**: [valkey-io/valkey-glide-cpp](https://github.com/valkey-io/valkey-glide-cpp) - Under active development

## Working Effectively

**FOCUS ON SINGLE LANGUAGE**: Usually you're better off working per language, as the codebase is enormous. Unless required for similar use cases, focus only on the specific language you're working with plus the shared Glide Core when needed.

### Root-Level Build Orchestration

```bash
# See all available build targets
make help

# Check if Valkey/Redis server is available (required for tests)
make check-valkey-server

# Build all languages (NEVER CANCEL: Takes 15-30 minutes)  
make all  # TIMEOUT: Set 45+ minutes

# Build individual languages
make java      # TIMEOUT: Set 20+ minutes
make python    # TIMEOUT: Set 15+ minutes  
make node      # TIMEOUT: Set 10+ minutes
make go        # TIMEOUT: Set 10+ minutes

# Run all tests (NEVER CANCEL: Takes 60+ minutes)
make java-test python-test node-test go-test  # TIMEOUT: Set 90+ minutes

# Clean all build artifacts
make clean
```
**CRITICAL**: Install ALL dependencies before attempting any builds. Missing dependencies cause build failures.

```bash
# Install Protocol Buffers compiler (REQUIRED for all languages)
curl -LO https://github.com/protocolbuffers/protobuf/releases/download/v29.1/protoc-29.1-linux-x86_64.zip
unzip -o protoc-29.1-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
echo 'export PATH="$PATH:$HOME/.local/bin"' >> ~/.bashrc

# Verify protoc installation
protoc --version  # Should show: libprotoc 29.1

# Install Valkey server (REQUIRED for tests)
sudo apt-get update && sudo apt-get install -y valkey-server

# Verify server installation  
valkey-server --version
```

### Go Development

```bash
# Install Go build tools (run once)
cd go
make install-build-tools  # Installs protoc-gen-go and cbindgen (~1 minute)

# Install additional tools for cross-compilation
pip3 install ziglang
cargo install --locked cargo-zigbuild

# Add Go tools to PATH (CRITICAL)
export PATH="$PATH:$HOME/go/bin"
echo 'export PATH="$PATH:$HOME/go/bin"' >> ~/.bashrc

# Build Go client - NEVER CANCEL: First build takes 3-4 minutes, subsequent ~30 seconds
make build  # TIMEOUT: Set 5+ minutes for first build, 2+ minutes for subsequent

# Install dev tools for linting and testing (~30 seconds)
make install-dev-tools

# Run tests - NEVER CANCEL: Takes 15-25 minutes
make unit-test integ-test  # TIMEOUT: Set 30+ minutes

# Run linters (always run before committing) - takes ~30-60 seconds
make lint
```

### Node.js Development

```bash
# Install dependencies
cd node
npm install  # ~15-20 seconds
cd rust-client && npm install  # ~1 second
cd ..

# Build - NEVER CANCEL: Release build takes 3-4 minutes, dev build takes ~2 minutes
npm run build:release  # TIMEOUT: Set 5+ minutes (optimized for production)
# OR for development (faster, unoptimized build)
npm run build  # TIMEOUT: Set 3+ minutes (also works for standard builds)

# Run tests - NEVER CANCEL: Takes 10-15 minutes  
npm test  # TIMEOUT: Set 20+ minutes

# Run linters (always run before committing)
npm run lint
npm run prettier:check

# Fix linting and formatting issues
npm run lint:fix
npm run prettier:write
```

### Python Development

```bash
# Build Python client - NEVER CANCEL: Takes 7-10 minutes
cd python
python3 dev.py build --mode release  # TIMEOUT: Set 15+ minutes

# For development builds (faster)
python3 dev.py build --mode debug  # TIMEOUT: Set 10+ minutes

# Run tests - NEVER CANCEL: Takes 20-30 minutes
python3 dev.py test  # TIMEOUT: Set 45+ minutes

# Run linters (always run before committing)
python3 dev.py lint --check
```

### Java Development

```bash
# Build Java client - NEVER CANCEL: Takes 10-15 minutes including tests
cd java
./gradlew --build-cache --continue build -x javadoc  # TIMEOUT: Set 20+ minutes

# Run tests separately if needed - NEVER CANCEL: Takes 15-25 minutes
./gradlew :integTest:test  # TIMEOUT: Set 30+ minutes

# Run linters (always run before committing)
./gradlew spotlessApply
```

**Note**: C# support has been moved to a separate repository: [valkey-io/valkey-glide-csharp](https://github.com/valkey-io/valkey-glide-csharp)

## Build Time Expectations

**CRITICAL TIMING WARNINGS - NEVER CANCEL THESE COMMANDS:**

| Language | First Build | Subsequent Build | Tests | Timeout Setting |
|----------|------------|------------------|-------|----------------|
| Go | 3-4 minutes | 30 seconds | 15-25 minutes | 30+ minutes |
| Node.js | 3-4 minutes | 1-2 minutes | 10-15 minutes | 20+ minutes |
| Python | 7-10 minutes | 3-5 minutes | 20-30 minutes | 45+ minutes |
| Java | 10-15 minutes | 2-5 minutes | 15-25 minutes | 30+ minutes |

**Why builds are slow**: The first build compiles the entire Rust core (`glide-core`) and FFI layer from scratch. Subsequent builds are much faster due to caching.

## Validation Scenarios

**Always test these scenarios after making changes:**

### Basic Validation (Choose language you're working on)
```bash
# Go validation
cd go && make build && echo "Build successful"

# Node.js validation  
cd node && npm run build && echo "Build successful"

# Python validation
cd python && python3 dev.py build --mode debug && echo "Build successful"

# Java validation
cd java && ./gradlew build -x test -x javadoc && echo "Build successful"
```

### Integration Test Validation
**ONLY run if you have valkey-server installed (tests start servers themselves):**

```bash
# Test your language (examples)
cd go && make integ-test  # TIMEOUT: 30+ minutes
cd node && npm test      # TIMEOUT: 20+ minutes  
cd python && python3 dev.py test  # TIMEOUT: 45+ minutes
```

## Common Tasks and Troubleshooting

### Required PATH Configuration
```bash
# Essential PATH additions (add to ~/.bashrc)
export PATH="$PATH:$HOME/.local/bin:$HOME/go/bin"
```

### Dependency Installation Issues
- **"protoc not found"**: Install Protocol Buffers compiler as shown above
- **"cargo-zigbuild not found"**: Run `cargo install --locked cargo-zigbuild`
- **"protoc-gen-go not found"**: Ensure `$HOME/go/bin` is in PATH
- **Build fails with "zig not found"**: Run `pip3 install ziglang`

### Build Failures
- **"No valkey-server"**: Install valkey-server for tests
- **Rust compilation errors**: Ensure Rust toolchain is up to date
- **Permission errors**: Check file permissions in project directory

### Clean Builds
```bash
# Root level clean
make clean

# Language-specific clean  
cd go && make clean
cd node && npm run clean
cd java && ./gradlew clean
```

## Project Structure

### Key Directories
- `glide-core/` - Rust core library (shared by all languages)
- `ffi/` - Foreign Function Interface layer  
- `go/` - Go client implementation
- `node/` - Node.js client implementation
- `python/` - Python client implementation  
- `java/` - Java client implementation
- `benchmarks/` - Performance benchmarks
- `.github/workflows/` - CI/CD pipelines

**Note**: C# implementation has been moved to [valkey-io/valkey-glide-csharp](https://github.com/valkey-io/valkey-glide-csharp)

### Important Files
- `Makefile` - Root build orchestration
- `go/Makefile` - Go-specific builds
- `node/package.json` - Node.js configuration
- `python/dev.py` - Python build CLI utility
- `java/build.gradle` - Java build configuration

## Linting and Code Quality

**CRITICAL - ALWAYS run lints and tests before finishing and committing changes, including ALL the linters used by the library. VALIDATE that the code of each language is going to pass the CI.**

**ALWAYS run linters before committing - CI will fail otherwise:**

### All Languages Linting
```bash
# Root level linting (runs all)
make java-lint python-lint node-lint go-lint

# Individual language linting
cd go && make format
cd node && npm run lint:fix
cd python && python3 dev.py lint
cd java && ./gradlew spotlessApply
```

### Rust Code Linting
```bash
# For Go/FFI Rust code
cd ffi && cargo clippy --all-features --all-targets -- -D warnings
cd ffi && cargo fmt --all -- --check

# For other Rust components
cd glide-core && cargo clippy --all-features --all-targets -- -D warnings
cd glide-core && cargo fmt --all -- --check
```

## Performance Considerations

- **First builds are slow** due to Rust compilation (~3-10 minutes)
- **Subsequent builds are fast** due to caching (~30 seconds - 5 minutes)
- **Tests require server** and can take 10-45 minutes depending on language
- **Use debug builds** for development, release builds for performance testing
- **Parallel builds help** but require sufficient memory

## Emergency Procedures

### If Builds Hang or Fail
1. **Do NOT cancel long-running builds** - they're supposed to take time
2. Check dependencies are installed correctly
3. Verify PATH includes required tools
4. Try clean build: `make clean && <rebuild>`
5. Check disk space and memory availability

### If Tests Fail
1. Ensure valkey-server is installed (tests start their own servers)
2. Check no other Redis instances are running on default port
3. Verify network connectivity if using external endpoints
4. Run individual test suites to isolate issues

Remember: **This is a complex multi-language project. Builds take time. Be patient and follow the exact commands listed above.**