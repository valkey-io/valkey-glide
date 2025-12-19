# GET Command Benchmark

This benchmark measures the performance of GET commands using the Valkey GLIDE Rust client.

## Features

- **Configurable threading**: Set the number of concurrent threads
- **Configurable iterations**: Set the number of GET operations per test
- **Sync/Async modes**: Choose between synchronous and asynchronous execution patterns
- **Direct vs Protobuf modes**: Choose between direct Redis calls or protobuf command requests
- **Setup phase**: Automatically sets up a test key before benchmarking
- **Multiple test scenarios**: Single-threaded and multi-threaded benchmarks
- **TPS output**: Displays transactions per second for each test

## Prerequisites

1. **Redis/Valkey server**: Ensure a Redis or Valkey server is running on `127.0.0.1:6379` (default)
2. **Rust toolchain**: Make sure you have Rust and Cargo installed

## Usage

### Quick Start

```bash
# Run with default settings (4 threads, 1000 iterations, async mode, protobuf)
./run_get_benchmark.sh

# Run with custom settings
./run_get_benchmark.sh --threads 8 --iterations 5000 --sync --direct
```

### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `-t, --threads NUM` | Maximum number of concurrent threads to test | 4 |
| `-i, --iterations NUM` | Number of GET operations per test | 1000 |
| `-s, --sync` | Run in synchronous mode (sequential operations) | false |
| `-a, --async` | Run in asynchronous mode (concurrent operations) | true |
| `-d, --direct` | Use direct Redis calls (bypass protobuf) | false |
| `-p, --protobuf` | Use protobuf command requests | true |
| `--host HOST` | Redis/Valkey host | 127.0.0.1 |
| `--port PORT` | Redis/Valkey port | 6379 |
| `-h, --help` | Show help message | - |

### Environment Variables

You can also configure the benchmark using environment variables:

```bash
export BENCH_THREADS=8
export BENCH_ITERATIONS=5000
export BENCH_SYNC_MODE=true
export BENCH_DIRECT_MODE=true
cargo bench --bench get_benchmark
```

## Benchmark Structure

The benchmark includes the following test scenarios:

1. **Single-threaded tests**:
   - `single_thread_sync_protobuf`: Sequential GET operations using protobuf commands
   - `single_thread_sync_direct`: Sequential GET operations using direct Redis calls
   - `single_thread_async_protobuf`: Concurrent GET operations using protobuf commands
   - `single_thread_async_direct`: Concurrent GET operations using direct Redis calls

2. **Multi-threaded tests**:
   - `multi_thread_sync_protobuf`: Multiple threads running sequential protobuf GETs
   - `multi_thread_sync_direct`: Multiple threads running sequential direct GETs
   - `multi_thread_async_protobuf`: Multiple threads running concurrent protobuf GETs
   - `multi_thread_async_direct`: Multiple threads running concurrent direct GETs

**Note**: Each test creates fresh client connections to avoid borrowing complications and provide realistic performance measurements.

## Output

Results are generated using Criterion and saved to:
- `target/criterion/get_commands/`: HTML reports and detailed statistics
- Console output with timing information, statistical analysis, and **TPS (Transactions Per Second)**

### TPS Output Format
```
Single thread sync_direct TPS: 45231.67
Multi thread (4) async_protobuf TPS: 123456.78
```

## Examples

```bash
# High-throughput async test with direct Redis calls
./run_get_benchmark.sh --threads 16 --iterations 10000 --async --direct

# Low-latency sync test with protobuf commands
./run_get_benchmark.sh --threads 1 --iterations 100 --sync --protobuf

# Compare direct vs protobuf performance
./run_get_benchmark.sh --threads 4 --iterations 1000 --async --direct
./run_get_benchmark.sh --threads 4 --iterations 1000 --async --protobuf

# Custom Redis instance
./run_get_benchmark.sh --host redis.example.com --port 6380 --threads 4 --direct
```

## Interpreting Results

- **Throughput**: Higher operations/second indicates better throughput
- **Latency**: Lower mean/median times indicate better latency
- **Consistency**: Lower standard deviation indicates more consistent performance
- **Async vs Sync**: Async mode typically shows higher throughput due to concurrent connections, sync mode shows more predictable latency patterns
- **Direct vs Protobuf**: Direct mode uses GLIDE's RequestType enum, protobuf mode uses manual command construction

## Implementation Details

- Each benchmark iteration creates a new client connection
- The benchmark key "benchmark_key" is set to "test_value" during setup
- Connection timeout is set to 250ms for faster failure detection
- Uses RESP2 protocol for compatibility
- **Direct mode**: Uses `RequestType::Get.get_command()` (bypasses manual protobuf creation)
- **Protobuf mode**: Uses `redis::Cmd::new()` with manual argument construction

## Troubleshooting

1. **Connection errors**: Ensure Redis/Valkey is running and accessible
2. **Permission errors**: Make sure the script is executable (`chmod +x run_get_benchmark.sh`)
3. **Build errors**: Run `cargo build` first to ensure dependencies are resolved
4. **Timeout errors**: Increase connection timeout or check server responsiveness
