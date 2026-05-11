// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"

	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func (suite *GlideTestSuite) TestAddressResolverWithFakeAddress_Standalone() {
	// Get the actual server address from test configuration
	actualHost := suite.standaloneHosts[0].Host
	actualPort := suite.standaloneHosts[0].Port

	// Use a fake/placeholder address in configuration
	fakeHost := "fake-host-that-does-not-exist.invalid"
	fakePort := 9999

	// Create resolver that translates fake address to real server address
	resolver := func(host string, port int) (string, int) {
		if host == fakeHost && port == fakePort {
			return actualHost, actualPort
		}
		return host, port
	}

	// Configure client with fake address - connection should succeed because
	// resolver translates it to the real address
	clientConfig := defaultClientConfig().
		WithAddress(&config.NodeAddress{Host: fakeHost, Port: fakePort}).
		WithAddressResolver(resolver)

	client, err := glide.NewClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	// Verify the client works - this proves the resolved address was used
	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)

	setResult, err := client.Set(ctx, "resolver_test_key", "resolver_test_value")
	suite.Require().NoError(err)
	suite.Equal("OK", setResult)

	getResult, err := client.Get(ctx, "resolver_test_key")
	suite.Require().NoError(err)
	suite.Equal("resolver_test_value", getResult.Value())
}

func (suite *GlideTestSuite) TestAddressResolverWithFakeAddress_Cluster() {
	// Get the actual server address from test configuration
	actualHost := suite.clusterHosts[0].Host
	actualPort := suite.clusterHosts[0].Port

	// Use a fake/placeholder address in configuration
	fakeHost := "fake-cluster-host.invalid"
	fakePort := 9999

	// Create resolver that translates fake address to real server address
	resolver := func(host string, port int) (string, int) {
		if host == fakeHost && port == fakePort {
			return actualHost, actualPort
		}
		return host, port
	}

	// Configure client with fake address - connection should succeed because
	// resolver translates it to the real address
	clientConfig := defaultClusterClientConfig().
		WithAddress(&config.NodeAddress{Host: fakeHost, Port: fakePort}).
		WithAddressResolver(resolver)

	client, err := glide.NewClusterClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	// Verify the client works - this proves the resolved address was used
	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)

	setResult, err := client.Set(ctx, "cluster_resolver_test_key", "cluster_resolver_test_value")
	suite.Require().NoError(err)
	suite.Equal("OK", setResult)

	getResult, err := client.Get(ctx, "cluster_resolver_test_key")
	suite.Require().NoError(err)
	suite.Equal("cluster_resolver_test_value", getResult.Value())
}

func (suite *GlideTestSuite) TestAddressResolverExceptionFallsBackToOriginal_Standalone() {
	// Get the actual server address from test configuration
	actualHost := suite.standaloneHosts[0].Host
	actualPort := suite.standaloneHosts[0].Port

	// Create resolver that always panics
	resolver := func(host string, port int) (string, int) {
		panic("test-exception")
	}

	// Configure client with real address - connection should still succeed
	// because the fallback uses the original address when resolver panics
	clientConfig := defaultClientConfig().
		WithAddress(&config.NodeAddress{Host: actualHost, Port: actualPort}).
		WithAddressResolver(resolver)

	client, err := glide.NewClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)
}

func (suite *GlideTestSuite) TestAddressResolverExceptionFallsBackToOriginal_Cluster() {
	// Get the actual server address from test configuration
	actualHost := suite.clusterHosts[0].Host
	actualPort := suite.clusterHosts[0].Port

	// Create resolver that always panics
	resolver := func(host string, port int) (string, int) {
		panic("test-exception")
	}

	// Configure client with real address - connection should still succeed
	// because the fallback uses the original address when resolver panics
	clientConfig := defaultClusterClientConfig().
		WithAddress(&config.NodeAddress{Host: actualHost, Port: actualPort}).
		WithAddressResolver(resolver)

	client, err := glide.NewClusterClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)
}

func (suite *GlideTestSuite) TestAddressResolverReturnsEmptyFallsBackToOriginal_Standalone() {
	// Get the actual server address from test configuration
	actualHost := suite.standaloneHosts[0].Host
	actualPort := suite.standaloneHosts[0].Port

	// Create resolver that returns empty host (signals fallback)
	resolver := func(host string, port int) (string, int) {
		return "", 0
	}

	// Configure client with real address - connection should still succeed
	// because returning empty/zero signals fallback to original address
	clientConfig := defaultClientConfig().
		WithAddress(&config.NodeAddress{Host: actualHost, Port: actualPort}).
		WithAddressResolver(resolver)

	client, err := glide.NewClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)
}

func (suite *GlideTestSuite) TestAddressResolverReturnsEmptyFallsBackToOriginal_Cluster() {
	// Get the actual server address from test configuration
	actualHost := suite.clusterHosts[0].Host
	actualPort := suite.clusterHosts[0].Port

	// Create resolver that returns empty host (signals fallback)
	resolver := func(host string, port int) (string, int) {
		return "", 0
	}

	// Configure client with real address - connection should still succeed
	// because returning empty/zero signals fallback to original address
	clientConfig := defaultClusterClientConfig().
		WithAddress(&config.NodeAddress{Host: actualHost, Port: actualPort}).
		WithAddressResolver(resolver)

	client, err := glide.NewClusterClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)
}

func (suite *GlideTestSuite) TestAddressResolverNil_Standalone() {
	// Get the actual server address from test configuration
	actualHost := suite.standaloneHosts[0].Host
	actualPort := suite.standaloneHosts[0].Port

	// Configure client with nil resolver - should work normally
	clientConfig := defaultClientConfig().
		WithAddress(&config.NodeAddress{Host: actualHost, Port: actualPort}).
		WithAddressResolver(nil)

	client, err := glide.NewClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)
}

func (suite *GlideTestSuite) TestAddressResolverNil_Cluster() {
	// Get the actual server address from test configuration
	actualHost := suite.clusterHosts[0].Host
	actualPort := suite.clusterHosts[0].Port

	// Configure client with nil resolver - should work normally
	clientConfig := defaultClusterClientConfig().
		WithAddress(&config.NodeAddress{Host: actualHost, Port: actualPort}).
		WithAddressResolver(nil)

	client, err := glide.NewClusterClient(clientConfig)
	suite.Require().NoError(err)
	defer client.Close()

	ctx := context.Background()
	result, err := client.Ping(ctx)
	suite.Require().NoError(err)
	suite.Equal("PONG", result)
}
