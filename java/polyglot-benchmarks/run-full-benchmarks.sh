#!/usr/bin/env bash
set -euo pipefail

# Automated JNI vs UDS benchmark runs.
# Execute from repo root or this script's directory.

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
JAVA_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
cd "$JAVA_ROOT"

JDK_BIN=${JAVA_HOME:-}/bin
JAVA_CMD=${JDK_BIN:+$JDK_BIN/}java
GRADLE_CMD=./gradlew
RESULTS_DIR=${RESULTS_DIR:-bench-results}
JNI_NATIVE_DIR=client/build/native-libs
JNI_JAR_GLOB="client/build/libs/valkey-glide-*.jar"
POLYGLOT_JAR=polyglot-benchmarks/build/libs/benchmarks.jar
MAVEN_COORD=${MAVEN_COORD:-io.valkey:valkey-glide:2.0.1:jar:linux-aarch_64}
MAVEN_REPO=${MAVEN_REPO:-$HOME/.m2/repository}
ELASTICACHE_HOST=${ELASTICACHE_HOST:-"clustercfg.testing-cluster.ey5v7d.use2.cache.amazonaws.com"}
HOST=${HOST:-$ELASTICACHE_HOST}
PORT=${PORT:-6379}
TLS_FLAG=${TLS_FLAG:-}
DURATION_DEFAULT=120

scenarios=(
  "100 100 $DURATION_DEFAULT 10000,20000,30000,40000,50000,60000,70000,80000,90000"
  "4000 100 $DURATION_DEFAULT 10000,20000,30000,40000,50000,60000,70000"
  "26214400 20 $DURATION_DEFAULT 10000"
)

require_cmd() {
  local cmd="$1"
  if [[ "$cmd" == */* ]]; then
    if [[ ! -x "$cmd" ]]; then
      echo "Missing executable: $cmd" >&2
      exit 1
    fi
  else
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "Missing dependency: $cmd" >&2
      exit 1
    fi
  fi
}

require_cmd "$JAVA_CMD"
require_cmd jq
require_cmd mvn
require_cmd $GRADLE_CMD

mkdir -p "$RESULTS_DIR/jni" "$RESULTS_DIR/uds"

$GRADLE_CMD :client:buildRust :client:jar :polyglotBenchmarks:shadowJar

JNI_JAR=$(ls $JNI_JAR_GLOB 2>/dev/null | head -n1 || true)
if [[ -z "$JNI_JAR" ]]; then
  echo "Unable to locate JNI jar ($JNI_JAR_GLOB)" >&2
  exit 1
fi
if [[ ! -f "$POLYGLOT_JAR" ]]; then
  echo "Benchmark jar missing: $POLYGLOT_JAR" >&2
  exit 1
fi

IFS=':' read -r -a MAVEN_COORD_PARTS <<< "$MAVEN_COORD"
if (( ${#MAVEN_COORD_PARTS[@]} < 3 )); then
  echo "Invalid MAVEN_COORD '$MAVEN_COORD' (expected group:artifact:version[:packaging[:classifier]])" >&2
  exit 1
fi
UDS_GROUP=${MAVEN_COORD_PARTS[0]}
UDS_ARTIFACT=${MAVEN_COORD_PARTS[1]}
UDS_VERSION=${MAVEN_COORD_PARTS[2]}
UDS_PACKAGING=${MAVEN_COORD_PARTS[3]:-jar}
UDS_CLASSIFIER=${MAVEN_COORD_PARTS[4]:-}

UDS_JAR_NAME="$UDS_ARTIFACT-$UDS_VERSION"
if [[ -n "$UDS_CLASSIFIER" && "$UDS_CLASSIFIER" != "-" ]]; then
  UDS_JAR_NAME+="-$UDS_CLASSIFIER"
fi
UDS_JAR_NAME+=".$UDS_PACKAGING"

UDS_JAR="$MAVEN_REPO/${UDS_GROUP//.//}/$UDS_ARTIFACT/$UDS_VERSION/$UDS_JAR_NAME"
if [[ ! -f "$UDS_JAR" ]]; then
  mvn dependency:get -Dartifact="$MAVEN_COORD"
fi
if [[ ! -f "$UDS_JAR" ]]; then
  echo "UDS jar still missing: $UDS_JAR" >&2
  exit 1
fi

run_matrix() {
  local impl=$1
  local cp=$2
  local jni_path=${3:-}
  local out_dir=$4

  mkdir -p "$out_dir"

  for scenario in "${scenarios[@]}"; do
    IFS=' ' read -r data concurrency duration qps_csv <<< "$scenario"
    IFS=',' read -ra qps_list <<< "$qps_csv"
    for qps in "${qps_list[@]}"; do
      local label="${data}b_q${qps}_c${concurrency}"
      local result="$out_dir/${label}.json"
      local log="$out_dir/${label}.log"
      if [[ -f "$result" ]]; then
        echo "[$impl] Skipping $label (already exists)"
        continue
      fi
      echo "[$impl] data=${data}B qps=$qps concurrency=$concurrency duration=$duration"
      timeout --foreground -k 30 $((duration + 300)) \
        "$JAVA_CMD" -Xms4g -Xmx4g -XX:+UseG1GC \
        ${jni_path:+-Djava.library.path=$jni_path} \
        -cp "$cp" \
        polyglot.benchmark.ValkeyClientBenchmark \
        --host "$HOST" \
        --port "$PORT" \
        --data-size "$data" \
        --duration "$duration" \
        --concurrency "$concurrency" \
        --qps "$qps" \
        --output "$result" \
        ${TLS_FLAG:+$TLS_FLAG} \
        2>&1 | tee "$log"
    done
  done
}

run_matrix "JNI" "$POLYGLOT_JAR:$JNI_JAR" "$JNI_NATIVE_DIR" "$RESULTS_DIR/jni"
run_matrix "UDS" "$POLYGLOT_JAR:$UDS_JAR" "" "$RESULTS_DIR/uds"

summarize() {
  local impl=$1 dir=$2
  echo "\n=== $impl summary ==="
  for file in "$dir"/*.json; do
    [[ -f "$file" ]] || continue
    base=$(basename "$file")
    commands=$(jq -r '.finalSnapshot.totalCommands // .results.totalCommands // 0' "$file")
    qps=$(jq -r '.finalMetrics.actualQps // .results.actualQps // 0' "$file")
    p99=$(jq -r '.finalMetrics.getHitLatencies.p99 // .results.latency.p99 // 0' "$file")
    cpu=$(jq -r '.peakResources.processCpuLoad // .results.resources.cpuUsage // 0' "$file")
    printf "%s\tcommands=%s\tactualQps=%s\tp99=%s\tcpu=%s\n" "$base" "$commands" "$qps" "$p99" "$cpu"
  done | sort
}

summarize JNI "$RESULTS_DIR/jni"
summarize UDS "$RESULTS_DIR/uds"
