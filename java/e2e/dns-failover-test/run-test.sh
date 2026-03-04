#!/bin/bash
# Run DNS failover e2e test

set -e

echo "Building and publishing valkey-glide locally..."
cd ../.. && ./gradlew publishToMavenLocal
cd e2e/dns-failover-test

echo "Building test JAR..."
../../gradlew shadowJar

echo "Starting test environment..."
docker compose up -d --build

echo "Waiting for test to complete..."
if timeout 120s bash -c 'while true; do
    LOGS=$(docker compose logs test-runner 2>&1)
    if echo "$LOGS" | grep -q "DNS Failover Test PASSED\|DNS Failover Test FAILED"; then
        break
    fi
    sleep 3
done'; then
    echo "Test completed"
else
    echo "Test timed out"
fi

echo "Checking test results..."
docker compose logs test-runner

if docker compose logs test-runner | grep -q "DNS Failover Test PASSED"; then
    echo "Test Passed!"
    EXIT_CODE=0
else
    echo "Test Failed!"
    EXIT_CODE=1
fi

echo "Cleaning up..."
# Stop the recreated DNS server before cleaning up rest of test environment
docker stop dns-server 2>/dev/null || true
docker rm dns-server 2>/dev/null || true
docker compose down -v

exit $EXIT_CODE
