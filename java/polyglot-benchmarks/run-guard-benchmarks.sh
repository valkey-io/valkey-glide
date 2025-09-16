#!/bin/bash
set -euo pipefail

# Guard benchmarks: validate JNI performance after core changes
# Scenarios (each 40s by default):
#  - 100B data: 10k, 60k, 90k QPS
#  - 4KB data: 10k, 40k, 70k QPS

ELASTICACHE_HOST=${ELASTICACHE_HOST:-"clustercfg.testing-cluster.ey5v7d.use2.cache.amazonaws.com"}
DURATION=${DURATION:-120}
CONCURRENCY=${CONCURRENCY:-100}
# Large-data (25KB) specific defaults
CONCURRENCY_25KB=${CONCURRENCY_25KB:-20}

cd "$(dirname "$0")"

QPS_100B=(10000 60000 90000)
QPS_4KB=(10000 40000 70000)
# Add 25KB guard (above 16KB threshold) at 10k QPS
QPS_25KB=(10000)

timestamp=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="jni_guard_${timestamp}"
mkdir -p "$RESULTS_DIR"

echo "Building benchmarks..."
../gradlew :benchmarks:shadowJar --quiet

run_case() {
  local qps=$1
  local data_size=$2
  local name=$3
  local conc_override=${4:-}

  local out_dir="$RESULTS_DIR/${name}_qps${qps}_d${data_size}"
  mkdir -p "$out_dir"

  # Skip if results already exist (idempotent reruns)
  if [[ -f "$out_dir/results.json" ]]; then
    echo "Skipping $name @ $qps (results exist)"
    return 0
  fi

  # Choose concurrency (special-case larger payloads)
  local conc="$CONCURRENCY"
  if [[ -n "$conc_override" ]]; then
    conc="$conc_override"
  elif [[ "$data_size" -ge 25600 ]]; then
    conc="$CONCURRENCY_25KB"
  fi

  echo "Running $name: QPS=$qps, data=$data_size, conc=$conc, duration=${DURATION}s"
  # Guard against non-terminating JVMs: enforce timeout (duration + grace)
  # Use -k to SIGKILL after grace if still running. Capture exit of java (first cmd in pipeline).
  set +o pipefail
  timeout -k 15 $((DURATION + 120)) \
    java -Xms4g -Xmx4g -XX:+UseG1GC -Djava.library.path=build/libs/native \
      -cp build/libs/benchmarks.jar \
      polyglot.benchmark.ValkeyClientBenchmark \
      --qps "$qps" \
      --duration "$DURATION" \
      --data-size "$data_size" \
      --concurrency "$conc" \
      --output "$out_dir/results.json" \
      --checkpoint-interval 30 \
      2>&1 | tee "$out_dir/benchmark.log"
  status=${PIPESTATUS[0]}
  set -o pipefail
  if [[ $status -eq 0 || -f "$out_dir/results.json" ]]; then
    echo "Completed: $name @ $qps"
  else
    echo "WARNING: $name @ $qps did not exit cleanly (timeout or error). Continuing..."
  fi
}

export ELASTICACHE_HOST

for q in "${QPS_100B[@]}"; do
  run_case "$q" 100 "100B"
done

for q in "${QPS_4KB[@]}"; do
  run_case "$q" 4000 "4KB"
done

# 25KB large-data guard (reduced concurrency by default)
for q in "${QPS_25KB[@]}"; do
  run_case "$q" 25600 "25KB" "$CONCURRENCY_25KB"
done

echo "Guard benchmarks complete. Results in $RESULTS_DIR"
echo "Summary (name, p50, p99, cpu%, heapMB):"
for f in "$RESULTS_DIR"/*/results.json; do
  name=$(basename "$(dirname "$f")")
  read -r p50 p99 cpu heap < <(jq -r '[.finalMetrics.getHitLatencies.p50, .finalMetrics.getHitLatencies.p99, .peakResources.processCpuLoad, .peakResources.heapUsed] | @tsv' "$f")
  printf "%s\t%.3f\t%.3f\t%.1f\t%d\n" "$name" "$p50" "$p99" "$cpu" "$heap"
done | sort

