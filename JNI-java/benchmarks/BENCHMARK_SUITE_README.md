# 🚀 Comprehensive Benchmark Suite

## 📊 Overview

This benchmark suite provides comprehensive performance testing across different data sizes and QPS levels to validate the JNI client performance under various conditions.

## 🎯 Available Benchmark Tasks

### 1. **100-Byte Data Size Benchmark**

```bash
./gradlew :benchmarks:runComprehensiveBenchmark100B
```

- **QPS Range**: 10,000 → 90,000 (9 test points)
- **Data Size**: 100 bytes
- **Duration**: 120 seconds per test
- **Total Time**: ~18-30 minutes

### 2. **4KB Data Size Benchmark**

```bash
./gradlew :benchmarks:runComprehensiveBenchmark4KB
```

- **QPS Range**: 10,000 → 80,000 (8 test points)
- **Data Size**: 4,000 bytes
- **Total Time**: ~16-26 minutes

### 3. **Complete Benchmark Suite**

```bash
./gradlew :benchmarks:runFullBenchmarkSuite
```

- **Coverage**: Both 100B and 4KB data sizes
- **Total Tests**: 17 comprehensive benchmark tests
- **Total Time**: ~34-56 minutes

## 🔧 Configuration

### **Environment Variables**

```bash
# Set custom ElastiCache host (optional)
export ELASTICACHE_HOST="your-cluster.cache.amazonaws.com"

# Run benchmark with custom host
ELASTICACHE_HOST="custom-host.com" ./gradlew :benchmarks:runFullBenchmarkSuite
```

### **Default Configuration**

- **Host**: `clustercfg.testing-cluster.ey5v7d.use2.cache.amazonaws.com`
- **Duration**: 120 seconds per test
- **Implementation**: JNI only
- **Output**: JSON result files

## 📈 Test Matrix

### **100-Byte Data Size Tests**

| Test # | QPS Target | Expected CPU | Expected Latency |
| ------ | ---------- | ------------ | ---------------- |
| 1      | 10,000     | ~3%          | <1ms             |
| 2      | 20,000     | ~6%          | <1ms             |
| 3      | 30,000     | ~9%          | <2ms             |
| 4      | 40,000     | ~12%         | <2ms             |
| 5      | 50,000     | ~15%         | <3ms             |
| 6      | 60,000     | ~18%         | <3ms             |
| 7      | 70,000     | ~21%         | <4ms             |
| 8      | 80,000     | ~24%         | <5ms             |
| 9      | 90,000     | ~27%         | <6ms             |

### **4KB Data Size Tests**

| Test # | QPS Target | Expected CPU | Expected Latency |
| ------ | ---------- | ------------ | ---------------- |
| 1      | 10,000     | ~4%          | <2ms             |
| 2      | 20,000     | ~8%          | <2ms             |
| 3      | 30,000     | ~12%         | <3ms             |
| 4      | 40,000     | ~16%         | <4ms             |
| 5      | 50,000     | ~20%         | <5ms             |
| 6      | 60,000     | ~24%         | <6ms             |
| 7      | 70,000     | ~28%         | <7ms             |
| 8      | 80,000     | ~32%         | <8ms             |

## 📁 Output Files

### **Result File Naming**

```
comprehensive_100B_10000qps_results.json
comprehensive_100B_20000qps_results.json
...
comprehensive_4KB_10000qps_results.json
comprehensive_4KB_20000qps_results.json
...
```

### **Result File Contents**

Each JSON file contains:

```json
{
  "configuration": {
    "targetQPS": 50000,
    "dataSize": 100,
    "duration": 120,
    "implementation": "jni"
  },
  "results": {
    "actualQPS": 49983,
    "totalCommands": 2999398,
    "latency": {
      "setMean": 0.86,
      "setP99": 2.31,
      "getMean": 0.85,
      "getP99": 2.73
    },
    "resources": {
      "cpuUsage": 15.5,
      "heapMemory": 617,
      "totalMemory": 621
    }
  }
}
```

## 🔍 Analysis Commands

### **View Results Summary**

```bash
# List all result files
ls -la benchmarks/comprehensive_*_results.json

# Quick performance summary
grep -h "actualQPS\|cpuUsage" benchmarks/comprehensive_*_results.json
```

### **Performance Analysis**

```bash
# Compare 100B vs 4KB performance
echo "=== 100B Data Size Performance ==="
grep "actualQPS" benchmarks/comprehensive_100B_*_results.json

echo "=== 4KB Data Size Performance ==="
grep "actualQPS" benchmarks/comprehensive_4KB_*_results.json
```

## 🎯 Success Criteria

### **Performance Targets**

- **100B Data**:
  - 50k QPS with <16% CPU
  - P99 latency <3ms
  - > 99.5% success rate

- **4KB Data**:
  - 30k QPS with <20% CPU
  - P99 latency <5ms
  - > 99.0% success rate

### **Quality Metrics**

- **Zero crashes** during extended testing
- **Memory stability** (no leaks over 17 tests)
- **Consistent performance** across QPS levels
- **Graceful degradation** at high loads

## 🚨 Troubleshooting

### **Common Issues**

1. **Connection Timeouts**: Check ElastiCache host accessibility
2. **High Memory Usage**: Monitor heap with `-Xmx4g` setting
3. **Failed Tests**: Review individual JSON files for error details

### **Debug Commands**

```bash
# Check JNI library
ls -la build/libs/native/

# Verify ElastiCache connectivity
telnet $ELASTICACHE_HOST 6379

# Monitor system resources
top -p $(pgrep -f "ValkeyClientBenchmark")
```

## 📊 Expected Timeline

### **Execution Time Estimates**

- **Single 100B test**: ~2-3 minutes
- **Single 4KB test**: ~2-3 minutes
- **Full 100B suite**: ~18-30 minutes
- **Full 4KB suite**: ~16-26 minutes
- **Complete suite**: ~34-56 minutes

### **Progress Monitoring**

The benchmark provides real-time progress updates:

```
🚀 Starting Comprehensive Benchmark Suite - 100B Data Size
📊 QPS Levels: [10000, 20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000]
📦 Data Size: 100 bytes
⏱️  Duration: 120 seconds each

🔥 Running benchmark: 10000 QPS, 100B data...
✅ Completed 10000 QPS benchmark in 75.2s

🔥 Running benchmark: 20000 QPS, 100B data...
✅ Completed 20000 QPS benchmark in 72.8s
...
```

## 🏆 Production Readiness Validation

This benchmark suite validates:

- **Scalability**: Performance across wide QPS range
- **Data Size Impact**: 100B vs 4KB payload handling
- **Resource Efficiency**: CPU and memory usage patterns
- **Stability**: Extended operation reliability
- **Real-world Performance**: AWS ElastiCache integration

**Status**: Production-grade performance testing complete! 🚀
