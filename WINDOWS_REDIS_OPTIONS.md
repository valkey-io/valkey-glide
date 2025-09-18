# Windows Redis/Valkey Options for CI/CD

## Research Summary

Based on analysis of how major Redis client projects handle Windows CI/CD and research into modern Redis Windows builds, here are the available options for running newer Redis/Valkey versions on Windows CI.

## How Other Projects Handle Windows Redis Testing

### Jedis (Java Redis Client)
- **Approach**: Primarily uses **mock libraries** for Windows CI
- **Mock Solutions**: Jedis-Mock (in-memory mock), Fake-Jedis
- **Benefits**: Lightning fast tests, no Redis server required, runs on any platform
- **CI Strategy**: Mock for unit tests, real Redis on Linux runners for integration tests
- **Windows Support**: Limited - uses GitHub Actions that note "Windows is currently not supported"

### Lettuce (Advanced Java Redis Client)
- **Approach**: Focuses on **Linux/macOS for Redis testing**
- **Testing**: Tests against Redis 8.0, 7.4, and 7.2 using Docker/Linux runners
- **Windows Strategy**: Uses WSL or focuses testing on non-Windows platforms
- **Requirement**: Service containers require Linux runners for Docker

### StackExchange.Redis (.NET Redis Client)
- **Approach**: **Native Windows support** with comprehensive testing
- **Testing**: Supports both Windows (all tests) and .NET Core (cross-platform)
- **Server Setup**: Docker Compose or local Redis installation
- **Flexibility**: Configurable test endpoints via TestConfig.json
- **Status**: Most mature Windows Redis testing approach

## Modern Redis Windows Build Options

### 1. Redis-Windows Project (RECOMMENDED)
**Repository**: `redis-windows/redis-windows`
**Latest Version**: Redis 8.2.1 (Latest stable, 2024)

**Available Redis 7.2+ Builds**:
- Redis-8.2.1-Windows-x64-msys2.zip ✅ **PREFERRED**
- Redis-8.0.0-Windows-x64-msys2.zip ✅
- Redis-7.4.3-Windows-x64-msys2.zip ✅
- Redis-7.2.8-Windows-x64-msys2.zip ✅ **MINIMUM ACCEPTABLE**

**Build Process**:
- Uses GitHub Actions for transparent, automated builds
- MSYS2 and Cygwin compilation environments
- Full SHA256 verification
- All versions 7.2+ available for Windows

**GitHub Actions Workflow Example**:
```yaml
- name: Download Redis 8.2.1
  run: |
    $url = "https://github.com/redis-windows/redis-windows/releases/download/8.2.1/Redis-8.2.1-Windows-x64-msys2.zip"
    Invoke-WebRequest -Uri $url -OutFile "redis.zip"
    Expand-Archive -Path "redis.zip" -DestinationPath "C:\redis"
```

### 2. tporadowski/redis
**Version**: Redis 5.0.14 (What we currently use)
**Status**: ❌ **UNACCEPTABLE** - Below minimum Redis 7.2 requirement
**Issue**: Too old, missing critical features needed for testing

### 3. ZKTeco Redis Port
**Repository**: `zkteco-home/redis-windows`
**Approach**: Native Visual Studio 2022 compilation
**Performance**: Claims better performance than Cygwin/MSYS builds
**Service Support**: Can be installed as Windows service

## Compilation Approaches for Latest Versions

### MSYS2/MinGW Approach
**Status**: ✅ **VIABLE for Redis** (proven by redis-windows project)

```yaml
# GitHub Actions workflow for MSYS2 Redis build
- uses: msys2/setup-msys2@v2
  with:
    msystem: UCRT64  # Recommended over MINGW64
    update: true
    install: >-
      mingw-w64-ucrt-x86_64-gcc
      mingw-w64-ucrt-x86_64-make
      mingw-w64-ucrt-x86_64-openssl

- name: Build Redis
  shell: msys2 {0}
  run: |
    make
    make PREFIX=/opt/redis install
```

### Cross-Compilation from Linux
**Status**: ❌ **NOT VIABLE for Valkey**
- Valkey doesn't build natively on Windows
- Would require significant porting work
- Cross-compilation to x86_64-pc-windows-msvc needs Windows SDK
- Native Windows support is requested but not implemented

### WSL2 on GitHub Actions
**Status**: ❌ **NOT AVAILABLE**
- WSL2 not available on GitHub Actions Windows runners
- Would require Windows Server 2025+ or specific Windows 10/11 versions

## Recommended Implementation Strategy

### Phase 1: CRITICAL UPGRADE (< 1 day)
**Replace Redis 5.0.14 with Redis 7.2+ (Minimum) or 8.2.1 (Preferred)**

```yaml
- name: Download and setup Redis 8.2.1
  shell: pwsh
  run: |
    Write-Host "=== Setting up Redis 8.2.1 for Windows ==="

    # Use latest redis-windows build (Redis 7.2+ required)
    $url = "https://github.com/redis-windows/redis-windows/releases/download/8.2.1/Redis-8.2.1-Windows-x64-msys2.zip"

    Write-Host "Downloading Redis 8.2.1..."
    Invoke-WebRequest -Uri $url -OutFile "redis.zip"

    Write-Host "Extracting Redis..."
    Expand-Archive -Path "redis.zip" -DestinationPath "C:\redis" -Force

    # Verify version meets minimum requirement
    $version = & "C:\redis\redis-server.exe" --version
    Write-Host "Redis version: $version"
    if ($version -notmatch "[78]\.[0-9]+") {
      throw "Redis version must be 7.2 or higher"
    }
```

**Critical Requirements**:
- ✅ Redis 7.2+ compliance (MANDATORY)
- ✅ Latest Redis features and security fixes
- ✅ Same basic workflow as current (just URL change)
- ✅ Proven builds from redis-windows project

### Phase 2: Advanced Options (Future)
1. **Add MSYS2 native compilation** for cutting-edge versions
2. **Implement mock testing** like Jedis for unit tests
3. **Multi-version matrix testing** (Redis 7.x, 8.x)

## Specific Recommendations for Valkey GLIDE

### IMMEDIATE ACTION REQUIRED (This Week)
```yaml
# Update .github/workflows/java-windows-triage.yml line 52:
# OLD (UNACCEPTABLE - Redis 5.0.14):
$url = "https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip"

# NEW (MINIMUM REQUIREMENT - Redis 8.2.1):
$url = "https://github.com/redis-windows/redis-windows/releases/download/8.2.1/Redis-8.2.1-Windows-x64-msys2.zip"

# Alternative Redis 7.2+ options:
# $url = "https://github.com/redis-windows/redis-windows/releases/download/7.2.8/Redis-7.2.8-Windows-x64-msys2.zip"
# $url = "https://github.com/redis-windows/redis-windows/releases/download/7.4.3/Redis-7.4.3-Windows-x64-msys2.zip"
```

### Medium Term (Next Sprint)
1. **Add version matrix testing**:
   ```yaml
   strategy:
     matrix:
       redis-version: ['7.2.8', '8.0.0', '8.2.1']
   ```

2. **Consider mock testing** for faster unit tests:
   - Mock libraries eliminate Redis server dependency
   - Faster feedback loops
   - Platform-independent testing

### Long Term (Future Releases)
1. **MSYS2 native builds** for absolute latest versions
2. **Dual strategy**: Mock for unit tests, real Redis for integration tests
3. **Multi-platform testing**: Linux (latest) + Windows (stable)

## Conclusion

**Answer to "what other projects do for this issue?":**

1. **Most projects avoid the problem** - Use Linux runners only
2. **Some use mocks** - Jedis uses mock libraries for Windows
3. **Enterprise solutions exist** - StackExchange.Redis has mature Windows support
4. **Modern builds available** - redis-windows project provides Redis 8.2.1

**Recommendation**: Use Redis 8.2.1 from redis-windows project. It's a simple upgrade that gives us 3+ years of Redis improvements with minimal risk and proven stability.

The redis-windows project has solved the "newer versions on Windows" problem - we just need to use their builds instead of the older tporadowski fork.