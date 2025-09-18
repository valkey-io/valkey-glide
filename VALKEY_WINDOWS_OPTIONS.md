# Valkey Windows CI/CD Options

## Context: Valkey Project Requirements

**Key Constraints**:
- ✅ Open source Redis 6.2-7.2 (pre-license change, BSD3)
- ✅ Valkey (open source Redis fork from 7.2.4+)
- ❌ **NO** closed-source Redis 7.4+ (RSALv2/SSPLv1 license)

## Current Situation

**Problem**: We're using Redis 5.0.14 which is below our minimum supported range (6.2-7.2)
**Goal**: Run Valkey (latest) or Redis 6.2-7.2 on Windows CI

## Available Options for Windows

### Option 1: Open Source Redis 6.2-7.2 (RECOMMENDED for immediate fix)
**Source**: `redis-windows/redis-windows` project
**License**: BSD3 (open source, pre-license change)

**Available builds**:
- ✅ Redis 7.2.8 (latest open source Redis)
- ✅ Redis 7.0.15
- ✅ Redis 6.2.18

**Implementation**:
```yaml
# Replace Redis 5.0.14 with Redis 7.2.8 (latest open source)
$url = "https://github.com/redis-windows/redis-windows/releases/download/7.2.8/Redis-7.2.8-Windows-x64-msys2.zip"
```

**Pros**:
- ✅ Fully compliant with Valkey project requirements
- ✅ Latest open source Redis features
- ✅ Proven stable builds
- ✅ Simple drop-in replacement

### Option 2: Native Valkey for Windows
**Status**: ❌ **NOT AVAILABLE**
**Reason**: Valkey doesn't build natively on Windows

**Technical challenges**:
- Requires POSIX systems (fork(), COW memory)
- Would need "new design using threads and locks"
- Windows lacks fundamental Unix primitives
- Maintainers closed Windows support request

**Workarounds explored**:
- Docker (not available on GitHub Actions Windows)
- WSL2 (not available on GitHub Actions Windows)
- MSYS2 compilation (would require major porting effort)

### Option 3: Memurai (Commercial)
**Status**: ✅ **AVAILABLE but commercial**
**Description**: Native Windows Redis/Valkey implementation

**Features**:
- Native Windows Redis 7.2.6 support
- Valkey-compatible edition (supports Valkey 7.2.5)
- Professional support and enterprise features

**Issues for Valkey project**:
- ❌ Commercial license (conflicts with open source nature)
- ❌ Not suitable for open source CI/CD
- ❌ Would create dependency on proprietary software

### Option 4: Cross-compilation
**Status**: ❌ **NOT VIABLE**
**Reason**: Valkey source code not Windows-compatible

**Challenges**:
- Valkey uses POSIX-specific code
- Missing Windows alternatives for fork(), signals, etc.
- Would require substantial code changes

## Recommended Solution

### Immediate Action (< 1 hour)
**Upgrade to Redis 7.2.8** - latest open source Redis version

```yaml
# In .github/workflows/java-windows-triage.yml, line 52:
# OLD (below minimum support):
$url = "https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip"

# NEW (compliant with Valkey requirements):
$url = "https://github.com/redis-windows/redis-windows/releases/download/7.2.8/Redis-7.2.8-Windows-x64-msys2.zip"
```

### Verification
```yaml
# Add version verification to ensure compliance
- name: Verify Redis version compliance
  shell: pwsh
  run: |
    $version = & "C:\redis\redis-server.exe" --version
    Write-Host "Redis version: $version"

    # Ensure it's open source Redis 6.2-7.2
    if ($version -match "v=([67])\.([0-9]+)\.([0-9]+)") {
      $major = [int]$Matches[1]
      $minor = [int]$Matches[2]

      if (($major -eq 6 -and $minor -ge 2) -or ($major -eq 7 -and $minor -le 2)) {
        Write-Host "✅ Redis version $version is compliant with Valkey project requirements"
      } else {
        throw "❌ Redis version $version is not in supported range (6.2-7.2)"
      }
    } else {
      throw "❌ Could not parse Redis version"
    }
```

### Matrix Testing (Future Enhancement)
```yaml
strategy:
  matrix:
    redis-version:
      - '6.2.18'  # Minimum supported
      - '7.0.15'  # LTS version
      - '7.2.8'   # Latest open source
```

## Alternative Approaches for Future

### 1. Linux-only Integration Testing
- Keep Windows for build testing only
- Run full integration tests on Linux with latest Valkey
- Most Redis/Valkey client projects follow this pattern

### 2. Mock Testing for Windows
- Use Redis-compatible mocks for Windows unit tests
- Real Valkey testing on Linux runners
- Faster CI, platform-independent unit tests

### 3. Community Valkey Windows Port
- Monitor Valkey community for Windows porting efforts
- Contribute to Windows support if it becomes available
- Current status: Not planned by maintainers

## Implementation Priority

**Phase 1 (This Week)**: Upgrade to Redis 7.2.8
- ✅ Meets all Valkey project requirements
- ✅ Simple 1-line change
- ✅ Proven stable builds

**Phase 2 (Next Sprint)**: Add matrix testing
- Test against Redis 6.2, 7.0, and 7.2
- Ensure compatibility across supported range

**Phase 3 (Future)**: Evaluate Valkey native options
- Monitor Valkey Windows support progress
- Consider mock testing strategies
- Assess Linux-only integration testing

## Conclusion

**Best immediate solution**: Upgrade to Redis 7.2.8 from redis-windows project.

This gives us:
- ✅ Compliance with open source requirements (BSD3 license)
- ✅ Latest features in supported range
- ✅ Windows native execution
- ✅ Minimal implementation effort
- ✅ Maintains current workflow structure

While native Valkey on Windows isn't available, Redis 7.2.8 provides the best balance of compliance, features, and practicality for Windows CI/CD testing.