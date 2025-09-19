# Redis Windows Startup Issue Fix

## Problem Analysis

The Redis 7.2.8 MSYS2 build is experiencing performance issues:
- **Slow startup/shutdown** causing integration test timeouts
- **POSIX emulation overhead** from MSYS2 runtime
- **DLL dependency issues** (msys-2.0.dll, msys-crypto-3.dll)

## Recommended Solutions

### Solution 1: Try Cygwin Build (Immediate)

Switch from MSYS2 to Cygwin build which may have better compatibility:

```yaml
# In .github/workflows/java-windows-triage.yml
# Change line 52 from:
$url = "https://github.com/redis-windows/redis-windows/releases/download/7.2.8/Redis-7.2.8-Windows-x64-msys2.zip"

# To:
$url = "https://github.com/redis-windows/redis-windows/releases/download/7.2.8/Redis-7.2.8-Windows-x64-cygwin.zip"
```

Then update extraction path:
```yaml
# Change line 63 from:
Move-Item -Path "C:\temp\Redis-7.2.8-Windows-x64-msys2\*" -Destination "C:\redis\" -Force

# To:
Move-Item -Path "C:\temp\Redis-7.2.8-Windows-x64-cygwin\*" -Destination "C:\redis\" -Force
```

### Solution 2: Use Redis 7.0 (More Stable)

Try Redis 7.0.15 which may have better Windows stability:

```yaml
$url = "https://github.com/redis-windows/redis-windows/releases/download/7.0.15/Redis-7.0.15-Windows-x64-msys2.zip"

# Update extraction:
Move-Item -Path "C:\temp\Redis-7.0.15-Windows-x64-msys2\*" -Destination "C:\redis\" -Force
```

### Solution 3: Add Timeout Extensions

Increase timeouts for Windows-specific slowness:

```yaml
# In cluster_manager_windows_patch.py, increase timeouts:
# Change line with timeout=3 to:
timeout=10  # Give Windows more time

# Add retry logic for ping operations
max_retries = 5
for attempt in range(max_retries):
    try:
        # ping operation
        break
    except TimeoutError:
        if attempt < max_retries - 1:
            time.sleep(2)
        else:
            raise
```

### Solution 4: Optimize Redis Configuration

Add Windows-specific Redis configuration to improve performance:

```yaml
# Start Redis with optimized settings:
$redis = Start-Process -FilePath "C:\redis\redis-server.exe" `
    -ArgumentList @(
        "--port", "6379",
        "--protected-mode", "no",
        "--save", "",  # Disable persistence for tests
        "--appendonly", "no",  # Disable AOF
        "--databases", "1",  # Reduce database count
        "--tcp-backlog", "128",  # Smaller backlog for Windows
        "--timeout", "0",  # No timeout
        "--tcp-keepalive", "60"  # Keep connections alive
    ) `
    -PassThru -WindowStyle Hidden
```

## Implementation Priority

1. **Try Cygwin build first** (5 minutes) - Simple URL change
2. **Add timeout extensions** (10 minutes) - Helps with slow POSIX emulation
3. **Optimize Redis config** (10 minutes) - Reduce overhead
4. **Fallback to Redis 7.0** if 7.2 continues to fail

## Expected Results

- Cygwin build may have better registry handling than MSYS2
- Increased timeouts will handle Windows-specific slowness
- Optimized config reduces Redis overhead on Windows
- Redis 7.0 is an older, more tested version on Windows

## Testing Strategy

After implementing fix:
1. Run basic connectivity test
2. Run unit tests
3. Run limited integration tests (1-2 tests only)
4. Gradually increase test coverage

This approach prioritizes getting a working Windows CI over full feature parity.