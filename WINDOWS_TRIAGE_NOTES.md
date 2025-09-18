# Windows Triage Notes

## Current Status
Testing minimal Windows support for Java CI/CD with a triage approach.

## What We've Done

### 1. Created Windows Compatibility Patch
**File:** `utils/cluster_manager_windows_patch.py`

Key fixes:
- Replaced Unix `which` command with `shutil.which()` (cross-platform)
- Removed `--daemonize yes` flag (not supported on Windows Redis)
- Used Windows process creation flags (`DETACHED_PROCESS`, `CREATE_NEW_PROCESS_GROUP`)
- Fixed path separators (/ vs \)
- Direct PID tracking instead of reading from log files

### 2. Created Triage Workflow
**File:** `.github/workflows/java-windows-triage.yml`

Focuses on:
- Basic Redis connectivity test
- Java client build verification
- Minimal unit tests
- Simple integration test (standalone only, no cluster)
- Comprehensive artifact collection

### 3. Test Compatibility Script
**File:** `utils/test_windows_compat.py`

Tests:
- Platform detection
- Command finding with shutil.which
- Windows process flags
- Path conversion
- Redis version detection

## Known Issues

### Immediate Blockers (Fixed)
1. ✅ `which` command not available on Windows → Using `shutil.which()`
2. ✅ `--daemonize yes` not supported → Using Windows process flags
3. ✅ Path separator issues → Converting paths appropriately

### Current Challenges
1. ⚠️ Redis/Valkey doesn't have native Windows builds
   - Using tporadowski/redis fork (5.0.14) as workaround
   - Not the latest version but functional

2. ⚠️ Cluster mode complexity
   - Starting with standalone mode only
   - Cluster requires multiple coordinated processes

3. ⚠️ Integration test infrastructure
   - cluster_manager.py needs more Windows adaptations
   - Gradle tasks assume Unix commands

## Triage Results (In Progress)

### What Works
- ✅ Redis 5.0.14 downloads and runs on Windows
- ✅ Basic Redis connectivity (ping/pong)
- ✅ Python environment setup
- ✅ Rust toolchain installation
- 🔄 Java client build (in progress)

### What Needs Work
- ❓ Unit tests
- ❓ Integration tests
- ❓ Cluster manager compatibility
- ❓ Full test suite

## Next Steps

### Phase 1: Get Basics Working (Current)
1. Verify Java client builds on Windows
2. Get unit tests passing
3. Fix cluster_manager for standalone mode

### Phase 2: Expand Testing
1. Enable more integration tests
2. Fix test discovery issues
3. Handle Windows-specific test failures

### Phase 3: Production Ready
1. Add Windows to main CI matrix
2. Update CD workflow for Windows artifacts
3. Document Windows support officially

## Commands for Local Testing

```bash
# Test Windows compatibility
python utils/test_windows_compat.py

# Run cluster manager with patch
python utils/cluster_manager_windows_patch.py --help

# Start standalone Redis
python utils/cluster_manager_windows_patch.py start --cluster-mode false

# Build Java client on Windows
cd java
gradlew.bat :client:build -x test -x javadoc

# Run minimal tests
gradlew.bat :client:test --tests "*ConfigurationTest*"
```

## Alternative Approaches Considered

1. **WSL2**: Requires Windows Server 2025 or specific Windows 10/11 versions
2. **Docker**: Not available on GitHub Actions Windows runners
3. **Memurai**: Commercial Redis-compatible solution for Windows
4. **Cross-compilation**: Build Windows artifacts on Linux

## Recommendations

1. **Short term**: Use this triage approach to get basic Windows support
2. **Medium term**: Consider cross-compilation for Windows artifacts
3. **Long term**: Evaluate if full Windows support is worth the maintenance burden

## Resources

- [Redis Windows Fork](https://github.com/tporadowski/redis)
- [Python subprocess on Windows](https://docs.python.org/3/library/subprocess.html#windows-popen-helpers)
- [GitHub Actions Windows Runners](https://docs.github.com/en/actions/using-github-hosted-runners/about-github-hosted-runners#supported-runners-and-hardware-resources)