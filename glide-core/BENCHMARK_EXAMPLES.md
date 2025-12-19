# GET Benchmark Usage Examples

## Direct Cargo Usage

```bash
# Run with default settings
cargo bench --bench get_benchmark

# Run with custom environment variables
BENCH_THREADS=8 BENCH_ITERATIONS=5000 BENCH_SYNC_MODE=true cargo bench --bench get_benchmark

# Run with Criterion options
cargo bench --bench get_benchmark -- --quick
cargo bench --bench get_benchmark -- --sample-size 200
```

## Script Usage

```bash
# Basic usage
./run_get_benchmark.sh

# Custom configuration
./run_get_benchmark.sh --threads 8 --iterations 5000 --sync

# Different server
./run_get_benchmark.sh --host localhost --port 6380

# Help
./run_get_benchmark.sh --help
```

## Configuration Matrix

| Scenario | Threads | Iterations | Mode | Description |
|----------|---------|------------|------|-------------|
| Quick Test | 1 | 100 | sync | Fast validation |
| Latency Test | 1 | 1000 | sync | Single-threaded latency |
| Throughput Test | 8 | 10000 | async | High-throughput concurrent |
| Stress Test | 16 | 50000 | async | Maximum load testing |

## Expected Output

The benchmark will produce:
- Console output with timing statistics
- HTML reports in `target/criterion/get_commands/`
- Performance graphs and detailed analysis

## Performance Baseline

Typical results on a local Redis instance:
- Sync mode: ~10,000-50,000 ops/sec
- Async mode: ~50,000-200,000 ops/sec
- Latency: 0.1-1ms per operation

Results vary significantly based on:
- Hardware specifications
- Redis/Valkey configuration
- Network latency
- System load
