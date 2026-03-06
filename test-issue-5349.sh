#!/bin/bash

echo "Testing IAM credential refresh issue #5349"
echo "Current time: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo "EC2 credentials expire at: 2026-03-04T17:31:35Z"
echo ""
echo "This test will run PING every 30 seconds until it fails"
echo "Expected to fail after EC2 credential refresh if bug exists"
echo ""

cd /home/ubuntu/Dev/valkey-glide/glide-core

cargo test --features iam_tests test_iam_token_auto_refresh_short_interval -- --nocapture --test-threads=1

echo ""
echo "Test ended at: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
