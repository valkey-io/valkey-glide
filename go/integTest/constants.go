// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Common constants for testing.
package integTest

const (
	// Host names and addresses for tests.
	// See 'cluster_manager.py' for details.
	HostnameTLS   = "valkey.glide.test.tls.com"
	HostnameNoTLS = "valkey.glide.test.no_tls.com"
	IPAddressV4   = "127.0.0.1"
	IPAddressV6   = "::1"

	// IAM authentication test constants
	TestClusterName   = "test-cluster"
	TestRegionUsEast1 = "us-east-1"
	TestIamUsername   = "default"
)
