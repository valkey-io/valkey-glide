# Jedis Compatibility Testing Framework

This document describes the comprehensive testing framework for validating the GLIDE Jedis compatibility layer against the actual Jedis implementation.

## Overview

The compatibility testing framework provides:

1. **Functional Compatibility Tests** - Verify identical behavior between GLIDE and actual Jedis
2. **Performance Benchmarks** - Compare performance characteristics
3. **Stress Testing** - Validate stability under load
4. **Dual Test Runner** - Side-by-side comparison tool
5. **Automated Reporting** - Comprehensive test result analysis

## Test Files Structure

```
integTest/
├── src/test/java/glide/
│   ├── JedisCompatibilityTests.java      # Main compatibility test suite
│   ├── JedisPerformanceBenchmarkTest.java # Performance benchmarks
│   └── DualJedisTestRunner.java          # Dual implementation runner
├── compatibility-test.gradle              # Enhanced build configuration
├── run-compatibility-tests.sh            # Test execution script
└── COMPATIBILITY_TESTING.md              # This documentation
```

## Quick Start

### Prerequisites

1. **Redis Server** running on localhost:6379 (or configure custom host/port)
2. **Java 11+** installed
3. **GLIDE project** built and available

### Basic Usage

```bash
# Run all compatibility tests
./run-compatibility-tests.sh

# Run only compatibility tests
./run-compatibility-tests.sh compatibility

# Run performance benchmarks
./run-compatibility-tests.sh performance

# Run with custom Redis server
./run-compatibility-tests.sh -r redis.example.com -p 6380 all
```

### Using Gradle

```bash
# Run compatibility tests
./gradlew compatibilityTest

# Run performance benchmarks
./gradlew performanceBenchmark

# Run stress tests
./gradlew stressTest

# Run full compatibility suite
./gradlew fullCompatibilityTest
```

## Test Categories

### 1. Functional Compatibility Tests

**File**: `JedisCompatibilityTests.java`

Tests identical behavior between GLIDE and actual Jedis:

- **Basic Operations**: GET, SET, PING
- **String Handling**: Unicode, special characters, large values
- **Connection Management**: isClosed(), close()
- **Configuration**: DefaultJedisClientConfig, builder pattern
- **Pool Operations**: JedisPool functionality
- **Exception Handling**: Exception hierarchy and messages

**Example Output**:
```
✓ GLIDE Jedis PING: PONG
✓ Actual Jedis PING: PONG
✓ PING compatibility verified
✓ Basic operations compatibility verified
✓ String compatibility verified for: unicode_测试_🚀
```

### 2. Performance Benchmarks

**File**: `JedisPerformanceBenchmarkTest.java`

Compares performance characteristics:

- **Single-threaded Operations**: Basic GET/SET performance
- **Multi-threaded Operations**: Concurrent client performance
- **Large Value Handling**: Performance with 1KB, 10KB, 100KB values
- **Connection Pool Overhead**: Pool vs direct connection comparison
- **Memory Usage**: Memory footprint analysis

**Example Output**:
```
=== Single-threaded GET/SET Performance ===
GLIDE Jedis: 10000 ops in 1250.45ms (7997.12 ops/sec)
  SET avg: 45.23μs, GET avg: 79.87μs
Actual Jedis: 10000 ops in 1456.78ms (6864.32 ops/sec)
  SET avg: 52.14μs, GET avg: 93.21μs
Single-threaded GET/SET Comparison:
  Performance ratio (GLIDE/Actual): 1.16
  🚀 GLIDE is significantly faster!
```

### 3. Stress Testing

Tests stability and reliability under load:

- **Concurrent Clients**: Multiple simultaneous connections
- **Long-running Operations**: Extended test duration
- **Resource Cleanup**: Memory leak detection
- **Error Recovery**: Handling of connection failures

### 4. Dual Test Runner

**File**: `DualJedisTestRunner.java`

Standalone tool for side-by-side comparison:

- **Dynamic Class Loading**: Loads both implementations separately
- **Real-time Comparison**: Shows results side-by-side
- **Flexible Configuration**: Customizable test parameters
- **Detailed Reporting**: Comprehensive result analysis

## Configuration Options

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REDIS_HOST` | Redis server host | localhost |
| `REDIS_PORT` | Redis server port | 6379 |
| `JEDIS_VERSION` | Jedis version for comparison | 5.1.0 |
| `ITERATIONS` | Test iterations | 1000 |
| `CONCURRENT_CLIENTS` | Concurrent clients | 10 |
| `BENCHMARK_ITERATIONS` | Benchmark iterations | 10000 |
| `BENCHMARK_THREADS` | Benchmark threads | 4 |
| `STRESS_DURATION` | Stress test duration (seconds) | 300 |

### System Properties

```bash
# Test configuration
-Dcompatibility.test.iterations=5000
-Dcompatibility.test.concurrent.clients=20

# Benchmark configuration
-Dbenchmark.iterations=50000
-Dbenchmark.warmup=2000
-Dbenchmark.threads=8

# Stress test configuration
-Dstress.duration=600
-Dstress.clients=100
-Dstress.operations=2000

# Jedis JAR location
-Djedis.jar.path=/path/to/jedis.jar
```

## Test Execution Examples

### Basic Compatibility Testing

```bash
# Test with default settings
./run-compatibility-tests.sh compatibility

# Test with custom iterations
./run-compatibility-tests.sh compatibility -i 5000

# Test with custom Redis server
./run-compatibility-tests.sh compatibility -r redis-cluster.local -p 7000
```

### Performance Benchmarking

```bash
# Standard benchmark
./run-compatibility-tests.sh performance

# High-intensity benchmark
./run-compatibility-tests.sh performance -b 100000 -t 8

# Memory-focused benchmark
./run-compatibility-tests.sh performance -b 10000 --benchmark-threads 1
```

### Stress Testing

```bash
# Standard stress test (5 minutes)
./run-compatibility-tests.sh stress

# Extended stress test (30 minutes)
./run-compatibility-tests.sh stress -s 1800

# High-concurrency stress test
./run-compatibility-tests.sh stress --stress-clients 200
```

## Understanding Test Results

### Compatibility Test Results

- **✅ Green checkmarks**: Tests passed, behavior is identical
- **⚠️ Yellow warnings**: Tests passed with minor differences
- **❌ Red errors**: Tests failed, incompatible behavior detected

### Performance Comparison

- **Ratio < 0.8**: GLIDE is significantly faster
- **Ratio 0.8-1.2**: Performance is comparable
- **Ratio > 1.2**: GLIDE is slower (investigate)

### Stress Test Results

- **Success Rate**: Percentage of operations that completed successfully
- **Error Rate**: Percentage of operations that failed
- **Memory Usage**: Peak memory consumption during test
- **Connection Stability**: Connection failure/recovery statistics

## Troubleshooting

### Common Issues

1. **Redis Connection Failed**
   ```bash
   # Check Redis is running
   redis-cli ping
   
   # Use custom Redis server
   ./run-compatibility-tests.sh -r your-redis-host -p 6379
   ```

2. **Jedis JAR Not Found**
   ```bash
   # Download manually
   wget https://repo1.maven.org/maven2/redis/clients/jedis/5.1.0/jedis-5.1.0.jar
   
   # Specify path
   ./run-compatibility-tests.sh -Djedis.jar.path=/path/to/jedis.jar
   ```

3. **Out of Memory Errors**
   ```bash
   # Increase heap size
   export GRADLE_OPTS="-Xmx4g"
   
   # Reduce test iterations
   ./run-compatibility-tests.sh -i 1000 -b 5000
   ```

4. **Test Timeouts**
   ```bash
   # Reduce concurrent clients
   ./run-compatibility-tests.sh -c 5
   
   # Shorter stress test duration
   ./run-compatibility-tests.sh stress -s 60
   ```

### Debug Mode

Enable detailed logging:

```bash
# Gradle debug mode
./gradlew compatibilityTest --debug --stacktrace

# Script debug mode
DEBUG=1 ./run-compatibility-tests.sh compatibility
```

## Interpreting Results

### Compatibility Assessment

- **90%+ identical behavior**: Excellent compatibility
- **80-90% identical behavior**: Good compatibility with minor gaps
- **70-80% identical behavior**: Acceptable with known limitations
- **<70% identical behavior**: Significant compatibility issues

### Performance Assessment

- **GLIDE faster**: Excellent - leveraging Rust performance benefits
- **Comparable performance**: Good - no significant regression
- **GLIDE 2x slower**: Acceptable - sync wrapper overhead expected
- **GLIDE >3x slower**: Investigate - potential optimization needed

### Stress Test Assessment

- **>95% success rate**: Excellent stability
- **90-95% success rate**: Good stability
- **80-90% success rate**: Acceptable with monitoring
- **<80% success rate**: Stability issues need investigation

## Continuous Integration

### GitHub Actions Example

```yaml
name: Compatibility Tests
on: [push, pull_request]

jobs:
  compatibility:
    runs-on: ubuntu-latest
    services:
      redis:
        image: redis:7
        ports:
          - 6379:6379
    
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '11'
      
      - name: Run Compatibility Tests
        run: |
          cd java/valkey-glide/java/integTest
          ./run-compatibility-tests.sh all
      
      - name: Upload Test Reports
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: compatibility-test-reports
          path: java/valkey-glide/java/integTest/build/reports/
```

## Contributing

### Adding New Tests

1. **Compatibility Tests**: Add to `JedisCompatibilityTests.java`
2. **Performance Tests**: Add to `JedisPerformanceBenchmarkTest.java`
3. **Stress Tests**: Create new `*StressTest.java` files

### Test Guidelines

- **Isolation**: Each test should be independent
- **Cleanup**: Always clean up test data
- **Assertions**: Use descriptive assertion messages
- **Documentation**: Document test purpose and expected behavior
- **Performance**: Consider test execution time

### Example Test Addition

```java
@Test
@Order(10)
@DisplayName("Test New Feature Compatibility")
void testNewFeatureCompatibility() {
    if (!hasGlideJedis) {
        System.out.println("Skipping new feature test - GLIDE Jedis not available");
        return;
    }

    try {
        // Test GLIDE implementation
        String glideResult = (String) invokeMethod(glideJedis, "newFeature", "param");
        assertEquals("expected", glideResult, "GLIDE should handle new feature");

        // Test actual Jedis (if available)
        if (hasActualJedis) {
            String actualResult = (String) invokeMethod(actualJedis, "newFeature", "param");
            assertEquals(glideResult, actualResult, "Results should be identical");
            System.out.println("✓ New feature compatibility verified");
        }
    } catch (Exception e) {
        fail("New feature test failed: " + e.getMessage());
    }
}
```

## Conclusion

This comprehensive testing framework ensures that the GLIDE Jedis compatibility layer maintains high fidelity with the original Jedis implementation while potentially offering superior performance characteristics. Regular execution of these tests helps maintain compatibility and catch regressions early in the development process.
