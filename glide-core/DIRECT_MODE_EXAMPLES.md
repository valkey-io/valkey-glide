# Direct Mode vs Protobuf Mode Examples

## Quick Comparison

```bash
# Test protobuf mode (default GLIDE pipeline)
./run_get_benchmark.sh --threads 4 --iterations 1000 --async --protobuf

# Test direct mode (bypass protobuf, direct Redis calls)
./run_get_benchmark.sh --threads 4 --iterations 1000 --async --direct
```

## Expected TPS Output

```
# Protobuf Mode Output
Single thread async_protobuf TPS: 23456.78
Multi thread (4) async_protobuf TPS: 89012.34

# Direct Mode Output  
Single thread async_direct TPS: 34567.89
Multi thread (4) async_direct TPS: 123456.78
```

## Performance Scenarios

### Latency Comparison
```bash
# Low-latency sync comparison
./run_get_benchmark.sh --threads 1 --iterations 100 --sync --protobuf
./run_get_benchmark.sh --threads 1 --iterations 100 --sync --direct
```

### Throughput Comparison
```bash
# High-throughput async comparison
./run_get_benchmark.sh --threads 8 --iterations 10000 --async --protobuf
./run_get_benchmark.sh --threads 8 --iterations 10000 --async --direct
```

### Environment Variable Usage
```bash
# Protobuf mode
BENCH_DIRECT_MODE=false BENCH_THREADS=4 BENCH_ITERATIONS=1000 cargo bench --bench get_benchmark

# Direct mode
BENCH_DIRECT_MODE=true BENCH_THREADS=4 BENCH_ITERATIONS=1000 cargo bench --bench get_benchmark
```

## Use Cases

- **Direct Mode**: Measure GLIDE's optimized RequestType command creation performance
- **Protobuf Mode**: Measure manual command construction performance
- **Comparison**: Quantify the performance difference between RequestType enum and manual construction
