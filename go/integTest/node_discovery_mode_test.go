// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func (suite *GlideTestSuite) TestStaticConnects() {
	cfg := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithNodeDiscoveryMode(config.NodeDiscoveryModeStatic)
	client, err := suite.client(cfg)
	require.NoError(suite.T(), err)

	result, err := client.Get(context.Background(), "nonexistent_key")
	require.NoError(suite.T(), err)
	require.True(suite.T(), result.IsNil())
}

func (suite *GlideTestSuite) TestStaticAllowsWrites() {
	cfg := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithNodeDiscoveryMode(config.NodeDiscoveryModeStatic)
	client, err := suite.client(cfg)
	require.NoError(suite.T(), err)

	key := "skip_info_test_" + uuid.New().String()
	result, err := client.Set(context.Background(), key, "value")
	require.NoError(suite.T(), err)
	require.Equal(suite.T(), "OK", result)

	getResult, err := client.Get(context.Background(), key)
	require.NoError(suite.T(), err)
	require.Equal(suite.T(), "value", getResult.Value())

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestReadOnlyRejectsDiscoverAll() {
	cfg := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithReadOnly(true).
		WithNodeDiscoveryMode(config.NodeDiscoveryModeDiscoverAll)
	_, err := glide.NewClient(cfg)
	require.Error(suite.T(), err)
	require.Contains(suite.T(), err.Error(), "read-only mode is not compatible with DISCOVER_ALL")
}

func (suite *GlideTestSuite) TestDiscoverAll() {
	output, err := startDedicatedValkeyServerWithReplicas(suite, false, 3)
	require.NoError(suite.T(), err)
	clusterFolder := extractClusterFolder(suite, output)
	defer stopDedicatedValkeyServer(suite, clusterFolder)
	addresses := extractAddresses(suite, output)
	require.GreaterOrEqual(suite.T(), len(addresses), 4)

	primary := addresses[0]
	replica0 := addresses[1]
	replica1 := addresses[2]
	replica2 := addresses[3]

	suite.Run("from_primary", func() {
		uniqueName := fmt.Sprintf("discovery_t4_%s", uuid.New().String())
		cfg := config.NewClientConfiguration().
			WithAddress(&primary).
			WithClientName(uniqueName).
			WithNodeDiscoveryMode(config.NodeDiscoveryModeDiscoverAll)
		discoveryClient, err := glide.NewClient(cfg)
		require.NoError(suite.T(), err)
		defer discoveryClient.Close()

		probeCfg := config.NewClientConfiguration().
			WithAddress(&replica0).
			WithReadOnly(true)
		probe, err := glide.NewClient(probeCfg)
		require.NoError(suite.T(), err)
		defer probe.Close()

		result, err := probe.CustomCommand(context.Background(), []string{"CLIENT", "LIST"})
		require.NoError(suite.T(), err)
		require.Contains(suite.T(), result.(string), uniqueName)
	})

	suite.Run("from_replica", func() {
		uniqueName := fmt.Sprintf("discovery_t5_%s", uuid.New().String())
		cfg := config.NewClientConfiguration().
			WithAddress(&replica0).
			WithClientName(uniqueName).
			WithNodeDiscoveryMode(config.NodeDiscoveryModeDiscoverAll)
		discoveryClient, err := glide.NewClient(cfg)
		require.NoError(suite.T(), err)
		defer discoveryClient.Close()

		key := "discovery_t5_" + uuid.New().String()
		setResult, err := discoveryClient.Set(context.Background(), key, "value")
		require.NoError(suite.T(), err)
		require.Equal(suite.T(), "OK", setResult)
		discoveryClient.Del(context.Background(), []string{key})

		probeCfg := config.NewClientConfiguration().
			WithAddress(&replica0).
			WithReadOnly(true)
		probe, err := glide.NewClient(probeCfg)
		require.NoError(suite.T(), err)
		defer probe.Close()

		result, err := probe.CustomCommand(context.Background(), []string{"CLIENT", "LIST"})
		require.NoError(suite.T(), err)
		require.Contains(suite.T(), result.(string), uniqueName)
	})

	suite.Run("partial_addresses", func() {
		uniqueName := fmt.Sprintf("discovery_t6_%s", uuid.New().String())
		cfg := config.NewClientConfiguration().
			WithAddress(&primary).
			WithAddress(&replica0).
			WithClientName(uniqueName).
			WithNodeDiscoveryMode(config.NodeDiscoveryModeDiscoverAll)
		discoveryClient, err := glide.NewClient(cfg)
		require.NoError(suite.T(), err)
		defer discoveryClient.Close()

		for _, replicaAddr := range []config.NodeAddress{replica1, replica2} {
			addr := replicaAddr
			probeCfg := config.NewClientConfiguration().
				WithAddress(&addr).
				WithReadOnly(true)
			probe, err := glide.NewClient(probeCfg)
			require.NoError(suite.T(), err)

			result, err := probe.CustomCommand(context.Background(), []string{"CLIENT", "LIST"})
			require.NoError(suite.T(), err)
			require.Contains(suite.T(), result.(string), uniqueName,
				"Discovery client should be connected to replica %s:%d", addr.Host, addr.Port)
			probe.Close()
		}
	})
}
