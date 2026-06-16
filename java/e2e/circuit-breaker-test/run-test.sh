#!/bin/bash
# Run client-wide circuit breaker e2e test.
#
# Usage:
#   ./run-test.sh       # Quick benchmark (30s per phase)
#   ./run-test.sh long  # Long-running stability test (1 hour, inject every 5 min)
#   DURATION_SECS=3600 INJECT_INTERVAL_SECS=300 ./run-test.sh long

set -e

cd "$(dirname "$0")"

if [ "$1" = "long" ]; then
    export DURATION_SECS=${DURATION_SECS:-3600}
    export INJECT_INTERVAL_SECS=${INJECT_INTERVAL_SECS:-300}
    echo "Long-running mode: ${DURATION_SECS}s duration, inject every ${INJECT_INTERVAL_SECS}s"
else
    export DURATION_SECS=${DURATION_SECS:-30}
    export INJECT_INTERVAL_SECS=0
    echo "Benchmark mode: ${DURATION_SECS}s per phase"
fi

echo "Building and publishing valkey-glide locally..."
cd ../.. && ./gradlew publishToMavenLocal
cd e2e/circuit-breaker-test

echo "Building test JAR..."
../../gradlew shadowJar

echo "Starting test environment..."
docker compose up -d --build

echo "Waiting for test to complete (live output)..."
docker compose logs -f test-runner &
LOGS_PID=$!

# Wait for completion
TIMEOUT=$((DURATION_SECS * 3 + 120))
if timeout ${TIMEOUT}s bash -c 'while true; do
    LOGS=$(docker compose logs test-runner 2>&1)
    if echo "$LOGS" | grep -q "PASSED\|FAILED"; then
        break
    fi
    sleep 5
done'; then
    echo "Test completed"
else
    echo "Test timed out"
fi

kill $LOGS_PID 2>/dev/null || true

echo ""
echo "=== Final Results ==="
docker compose logs test-runner | tail -20

if docker compose logs test-runner | grep -q "PASSED"; then
    echo "TEST PASSED"
    EXIT_CODE=0
else
    echo "TEST FAILED"
    EXIT_CODE=1
fi

echo "Cleaning up..."
docker compose down -v

exit $EXIT_CODE
