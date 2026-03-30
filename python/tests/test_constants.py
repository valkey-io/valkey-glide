# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Shared test constants.
"""

# Host names and addresses for tests.
# See 'cluster_manager.py' for details.
HOSTNAME_TLS: str = "valkey.glide.test.tls.com"
HOSTNAME_NO_TLS: str = "valkey.glide.test.no_tls.com"
HOST_ADDRESS_IPV4: str = "127.0.0.1"
HOST_ADDRESS_IPV6: str = "::1"

# IAM authentication test constants
IAM_USERNAME: str = "default"
IAM_TEST_CLUSTER_NAME: str = "test-cluster"
IAM_TEST_REGION_US_EAST_1: str = "us-east-1"
IAM_DEFAULT_REFRESH_INTERVAL_SECONDS: int = 5
