#!/usr/bin/env bash
# Usage: run_python_test.sh <test_path::class::method> <max_iterations> <output_dir>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_NAME="$1"
MAX_ITER="${2:-1000}"
OUT_DIR="$3"
mkdir -p "$OUT_DIR"

cleanup() { rm -f "$OUT_DIR"/tmp_*.log; }
trap cleanup EXIT

# Build pytest args for dev.py test --args
if [[ "$TEST_NAME" == *"::"* ]]; then
  TEST_FILE="${TEST_NAME%%::*}"
  TEST_METHOD="${TEST_NAME##*::}"
  DEV_ARGS="--args $TEST_FILE -k $TEST_METHOD -x --timeout=120 -q"
else
  DEV_ARGS="--args -k $TEST_NAME -x --timeout=120 -q"
fi

FAIL_COUNT=0
for i in $(seq 1 "$MAX_ITER"); do
  printf "\r[iteration %d/%d] %s" "$i" "$MAX_ITER" "$TEST_NAME" >&2
  if ! python3 dev.py test $DEV_ARGS 2>"$OUT_DIR/tmp_stderr.log" >"$OUT_DIR/tmp_stdout.log"; then
    if grep -q "no tests ran\|no tests collected" "$OUT_DIR/tmp_stdout.log" "$OUT_DIR/tmp_stderr.log" 2>/dev/null; then
      echo "" >&2
      echo "SKIP — no tests matched: $TEST_NAME"
      echo "{\"test\":\"$TEST_NAME\",\"language\":\"python\",\"iterations\":0,\"first_failure\":null,\"status\":\"skipped\",\"reason\":\"no tests matched\"}" > "$OUT_DIR/summary.json"
      exit 0
    fi
    FAIL_COUNT=$((FAIL_COUNT + 1))
    cat "$OUT_DIR/tmp_stdout.log" "$OUT_DIR/tmp_stderr.log" > "$OUT_DIR/iteration_$(printf '%04d' $i).log"
    echo "" >&2
    echo "FAILED at iteration $i (total failures: $FAIL_COUNT)"
    # Continue running to get failure rate — don't stop on first failure
  fi
done

echo "" >&2
if [ "$FAIL_COUNT" -gt 0 ]; then
  echo "{\"test\":\"$TEST_NAME\",\"language\":\"python\",\"iterations\":$MAX_ITER,\"failures\":$FAIL_COUNT,\"status\":\"flaky\"}" > "$OUT_DIR/summary.json"
  echo "FLAKY — $FAIL_COUNT/$MAX_ITER iterations failed"
  exit 1
else
  echo "{\"test\":\"$TEST_NAME\",\"language\":\"python\",\"iterations\":$MAX_ITER,\"failures\":0,\"status\":\"passed\"}" > "$OUT_DIR/summary.json"
  echo "PASSED all $MAX_ITER iterations"
fi
