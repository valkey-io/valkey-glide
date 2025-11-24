# Valkey Performance Test Suite

A comprehensive performance testing application for comparing Valkey-Glide and Jedis Redis clients.

## Features

- **Multiple Client Support**: Test Jedis, Valkey-Glide, and Jedis Compatibility Layer clients
- **Jedis Compatibility Layer**: Test both Jedis and UnifiedJedis from the compatibility layer
- **Configurable Parameters**: Adjust connections, request rates, data sizes, and test duration
- **Concurrent Testing**: Support for multiple concurrent connections (default: 20)
- **Rate Limiting**: Configurable requests per minute (default: 10,000)
- **Variable Data Sizes**: Random data between 10KB-50KB
- **Mixed Workloads**: Configurable read/write ratios
- **Comprehensive Metrics**: Latency percentiles, throughput, success rates
- **Warmup Period**: Exclude warmup time from measurements
- **Real-time Monitoring**: Progress reporting during test execution

## Prerequisites

- Java 11 or higher
- Redis/Valkey server running (default: localhost:6379)
- Gradle (wrapper included)

## Quick Start

1. **Clone and build the project:**
   ```bash
   cd valkey-performance-test
   ./gradlew build
   ```

2. **Run with default settings (both clients):**
   ```bash
   ./gradlew run
   ```

3. **Run specific client tests:**
   ```bash
   # Test only Jedis
   ./gradlew runJedisTest
   
   # Test only Valkey-Glide
   ./gradlew runValkeyTest
   
   # Test only Compatibility Layer clients
   ./gradlew runCompatibilityTest
   
   # Test all clients
   ./gradlew runAllTests
   
   # Test both Jedis and Valkey-Glide
   ./gradlew runBothTests
   ```

## Configuration Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--client` | `both` | Client type: `jedis`, `valkey`, `compatibility`, `all`, `both` |
| `--connections` | `20` | Number of concurrent connections |
| `--rpm` | `10000` | Requests per minute |
| `--min-data-size` | `10240` | Minimum data size in bytes (10KB) |
| `--max-data-size` | `51200` | Maximum data size in bytes (50KB) |
| `--duration` | `60` | Test duration in seconds |
| `--host` | `localhost` | Redis server hostname |
| `--port` | `6379` | Redis server port |
| `--read-ratio` | `50` | Percentage of read operations (0-100) |
| `--warmup` | `10` | Warmup duration in seconds |
| `--key-prefix` | `perf_test_` | Prefix for generated keys |
| `--cluster` | `false` | Enable Redis cluster mode |
| `--tls` | `false` | Enable TLS/SSL encryption |

## Usage Examples

### Basic Usage
```bash
# Default test (20 connections, 10K RPM, both clients)
./gradlew run

# Test with custom parameters
./gradlew run --args="--connections=50 --rpm=20000 --duration=120"
```

### Advanced Configuration
```bash
# High-throughput test
./gradlew run --args="--connections=100 --rpm=50000 --duration=300 --read-ratio=80"

# Large data test
./gradlew run --args="--min-data-size=51200 --max-data-size=102400 --connections=10"

# Redis Cluster with TLS
./gradlew run --args="--cluster --tls --host=my-cluster.amazonaws.com --port=6380"

# ElastiCache Cluster
./gradlew run --args="--cluster --host=my-cluster.abc123.cache.amazonaws.com --port=6379"

# Write-heavy workload with TLS
./gradlew run --args="--read-ratio=20 --connections=30 --rpm=15000 --tls"
```

### Client-Specific Tests
```bash
# Compare Jedis configurations
./gradlew run --args="--client=jedis --connections=25"

# Test Valkey-Glide only
./gradlew run --args="--client=valkey --connections=25"

# Test Compatibility Layer clients
./gradlew run --args="--client=compatibility --connections=25"

# Test all available clients
./gradlew run --args="--client=all --connections=25"
```

## Output Metrics

The test provides comprehensive metrics including:

- **Throughput**: Requests per second (RPS)
- **Latency**: Average, minimum, maximum, P95, P99
- **Success Rate**: Percentage of successful operations
- **Operation Breakdown**: SET vs GET operation counts and latencies
- **Real-time Progress**: Updates every 5 seconds during test execution

### Sample Output
```
================================================================================
Performance Test Results for Jedis
================================================================================
Final Results: Total: 10000, Success: 9998 (99.9%), Failed: 2, RPS: 166.6, 
Latency - Avg: 2.1ms, Min: 1ms, Max: 45ms, P95: 4.2ms, P99: 8.1ms, 
SET: 5001 (avg: 2.3ms), GET: 4997 (avg: 1.9ms)
================================================================================
```

## Performance Tuning

### For High Throughput
- Increase `--connections` (e.g., 50-100)
- Increase `--rpm` proportionally
- Consider using `--read-ratio=80` for read-heavy workloads
- Reduce `--warmup` time for shorter tests

### For Latency Testing
- Use fewer connections (e.g., 5-10)
- Lower request rate to avoid saturation
- Increase test duration for stable measurements
- Monitor P95/P99 latencies

### For Large Data Testing
- Increase `--min-data-size` and `--max-data-size`
- Reduce connection count to avoid memory pressure
- Lower request rate to account for larger payloads

## Troubleshooting

### Connection Issues
- Verify Redis server is running: `redis-cli ping`
- Check host/port configuration
- Ensure firewall allows connections

### Performance Issues
- Monitor Redis server resources (CPU, memory)
- Check network latency to Redis server
- Adjust connection pool settings if needed

### Memory Issues
- Reduce concurrent connections
- Decrease data size ranges
- Monitor JVM heap usage

## Architecture

The application uses a multi-threaded architecture:

- **Main Thread**: Coordinates test execution and reporting
- **Worker Threads**: Each connection runs in its own thread
- **Rate Limiting**: Each worker maintains its target request rate
- **Metrics Collection**: Thread-safe metrics aggregation

## Building and Development

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Create distribution
./gradlew distTar
```

## Dependencies

- **Jedis**: 6.0.0 - Java Redis client
- **Valkey-Glide**: 2.0.1 - High-performance Redis client
- **Jedis Compatibility Layer**: 2.1.0-rc9 - Migration layer for Jedis to Valkey-Glide
- **Apache Commons Lang**: Utility functions
- **SLF4J + Logback**: Logging framework

## License

This project is provided as-is for performance testing purposes.
