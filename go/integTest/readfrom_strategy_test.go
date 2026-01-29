// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func (suite *GlideTestSuite) TestRoutingWithAzAffinityStrategyTo1Replica() {
	suite.SkipIfServerVersionLowerThan("8.0.0", suite.T())
	az := "us-east-1a"
	const GET_CALLS = 3
	getCmdStat := "cmdstat_get:calls=" + fmt.Sprint(GET_CALLS)

	clientForConfigSet, err := suite.clusterClient(suite.defaultClusterClientConfig().WithRequestTimeout(2 * time.Second))
	require.NoError(suite.T(), err)

	// Reset the availability zone for all nodes
	_, err = clientForConfigSet.ConfigSetWithOptions(context.Background(),
		map[string]string{"availability-zone": ""}, options.RouteOption{Route: config.AllNodes})
	assert.NoError(suite.T(), err)
	suite.verifyOK(clientForConfigSet.ConfigResetStat(context.Background()))

	// 12182 is the slot of "foo"
	_, err = clientForConfigSet.ConfigSetWithOptions(
		context.Background(),
		map[string]string{
			"availability-zone": az,
		},
		options.RouteOption{Route: config.NewSlotIdRoute(config.SlotTypeReplica, 12182)},
	)
	assert.NoError(suite.T(), err)

	clientForTestingAz, err := suite.clusterClient(suite.defaultClusterClientConfig().
		WithRequestTimeout(2 * time.Second).
		WithReadFrom(config.AzAffinity).
		WithClientAZ(az))
	require.NoError(suite.T(), err)

	for i := 0; i < GET_CALLS; i++ {
		_, err = clientForTestingAz.Get(context.Background(), "foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.Server, constants.Commandstats}},
			RouteOption: &options.RouteOption{Route: config.AllNodes},
		},
	)
	assert.NoError(suite.T(), err)

	// Check that only the replica with az has all the GET calls
	matchingEntriesCount := 0
	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, getCmdStat) && strings.Contains(value, az) {
			matchingEntriesCount++
		}
	}
	assert.Equal(suite.T(), 1, matchingEntriesCount)

	// Check that the other replicas have no availability zone set
	changedAzCount := 0
	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, "availability_zone:"+az) {
			changedAzCount++
		}
	}
	assert.Equal(suite.T(), 1, changedAzCount)

	clientForTestingAz.Close()
	clientForConfigSet.Close()
}

func (suite *GlideTestSuite) TestRoutingBySlotToReplicaWithAzAffinityStrategyToAllReplicas() {
	suite.SkipIfServerVersionLowerThan("8.0.0", suite.T())
	az := "us-east-1a"

	clientForConfigSet, err := suite.clusterClient(suite.defaultClusterClientConfig().WithRequestTimeout(2 * time.Second))
	require.NoError(suite.T(), err)

	// Reset the availability zone for all nodes
	_, err = clientForConfigSet.ConfigSetWithOptions(context.Background(),
		map[string]string{"availability-zone": ""}, options.RouteOption{Route: config.AllNodes})
	assert.NoError(suite.T(), err)
	suite.verifyOK(clientForConfigSet.ConfigResetStat(context.Background()))

	// Get Replica Count for current cluster
	clusterInfo, err := clientForConfigSet.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			RouteOption: &options.RouteOption{Route: config.NewSlotKeyRoute(config.SlotTypePrimary, "key")},
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.Replication}},
		})
	assert.NoError(suite.T(), err)
	nReplicas := 0
	for _, line := range strings.Split(clusterInfo.SingleValue(), "\n") {
		parts := strings.SplitN(line, ":", 2)
		if len(parts) == 2 && strings.TrimSpace(parts[0]) == "connected_slaves" {
			nReplicas, err = strconv.Atoi(strings.TrimSpace(parts[1]))
			assert.NoError(suite.T(), err)
			break
		}
	}
	nGetCalls := 3 * nReplicas
	getCmdStat := fmt.Sprintf("cmdstat_get:calls=%d", 3)

	// Setting AZ for all Nodes
	_, err = clientForConfigSet.ConfigSetWithOptions(context.Background(),
		map[string]string{"availability-zone": az}, options.RouteOption{Route: config.AllNodes})
	assert.NoError(suite.T(), err)
	clientForConfigSet.Close()

	// Creating Client with AZ configuration for testing
	clientForTestingAz, err := suite.clusterClient(suite.defaultClusterClientConfig().
		WithRequestTimeout(2 * time.Second).
		WithReadFrom(config.AzAffinity).
		WithClientAZ(az))
	require.NoError(suite.T(), err)

	azGetResult, err := clientForTestingAz.ConfigGetWithOptions(context.Background(),
		[]string{"availability-zone"}, options.RouteOption{Route: config.AllNodes})
	assert.NoError(suite.T(), err)
	for _, value := range azGetResult.MultiValue() {
		assert.Equal(suite.T(), az, value["availability-zone"])
	}

	// Execute GET commands
	for i := 0; i < nGetCalls; i++ {
		_, err := clientForTestingAz.Get(context.Background(), "foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.All}},
			RouteOption: &options.RouteOption{Route: config.AllNodes},
		},
	)
	assert.NoError(suite.T(), err)

	// Check that all replicas have the same number of GET calls
	matchingEntriesCount := 0
	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, getCmdStat) && strings.Contains(value, az) {
			matchingEntriesCount++
		}
	}
	assert.Equal(suite.T(), nReplicas, matchingEntriesCount)

	clientForTestingAz.Close()
}

func (suite *GlideTestSuite) TestAzAffinityNonExistingAz() {
	suite.SkipIfServerVersionLowerThan("8.0.0", suite.T())

	const nGetCalls = 3
	const nReplicaCalls = 1
	getCmdStat := fmt.Sprintf("cmdstat_get:calls=%d", nReplicaCalls)

	clientForTestingAz, err := suite.clusterClient(config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2 * time.Second).
		WithReadFrom(config.AzAffinity).
		WithClientAZ("non-existing-az"))
	require.NoError(suite.T(), err)

	// Reset stats
	suite.verifyOK(
		clientForTestingAz.ConfigResetStatWithOptions(context.Background(), options.RouteOption{Route: config.AllNodes}),
	)

	// Execute GET commands
	for i := 0; i < nGetCalls; i++ {
		_, err := clientForTestingAz.Get(context.Background(), "foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.Commandstats}},
			RouteOption: &options.RouteOption{Route: config.AllNodes},
		},
	)
	assert.NoError(suite.T(), err)

	// We expect the calls to be distributed evenly among the replicas
	matchingEntriesCount := 0
	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, getCmdStat) {
			matchingEntriesCount++
		}
	}
	assert.Equal(suite.T(), 3, matchingEntriesCount)

	clientForTestingAz.Close()
}

func (suite *GlideTestSuite) TestAzAffinityReplicasAndPrimaryRoutesToPrimary() {
	suite.SkipIfServerVersionLowerThan("8.0.0", suite.T())

	az := "us-east-1a"
	otherAz := "us-east-1b"
	const nGetCalls = 4
	getCmdStat := fmt.Sprintf("cmdstat_get:calls=%d", nGetCalls)

	// Create client for setting the configs
	clientForConfigSet, err := suite.clusterClient(config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2 * time.Second))
	require.NoError(suite.T(), err)

	// Reset stats and set all nodes to otherAz
	suite.verifyOK(
		clientForConfigSet.ConfigResetStatWithOptions(context.Background(), options.RouteOption{Route: config.AllNodes}),
	)

	_, err = clientForConfigSet.ConfigSetWithOptions(context.Background(),
		map[string]string{"availability-zone": otherAz}, options.RouteOption{Route: config.AllNodes})
	assert.NoError(suite.T(), err)

	// Set primary for slot 12182 to az
	_, err = clientForConfigSet.ConfigSetWithOptions(
		context.Background(),
		map[string]string{
			"availability-zone": az,
		},
		options.RouteOption{Route: config.NewSlotIdRoute(config.SlotTypePrimary, 12182)},
	)
	assert.NoError(suite.T(), err)

	// Verify primary AZ
	primaryAzResult, err := clientForConfigSet.ConfigGetWithOptions(context.Background(),
		[]string{"availability-zone"}, options.RouteOption{Route: config.NewSlotIdRoute(config.SlotTypePrimary, 12182)})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), az, primaryAzResult.SingleValue()["availability-zone"])

	clientForConfigSet.Close()

	// Create test client with AZ_AFFINITY_REPLICAS_AND_PRIMARY configuration
	clientForTestingAz, err := suite.clusterClient(config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2 * time.Second).
		WithReadFrom(config.AzAffinityReplicaAndPrimary).
		WithClientAZ(az))
	require.NoError(suite.T(), err)

	// Execute GET commands
	for i := 0; i < nGetCalls; i++ {
		_, err = clientForTestingAz.Get(context.Background(), "foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.All}},
			RouteOption: &options.RouteOption{Route: config.AllNodes},
		},
	)
	assert.NoError(suite.T(), err)

	// Check that only the primary in the specified AZ handled all GET calls
	matchingEntriesCount := 0
	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, getCmdStat) && strings.Contains(value, az) && strings.Contains(value, "role:master") {
			matchingEntriesCount++
		}
	}
	assert.Equal(suite.T(), 1, matchingEntriesCount)

	// Verify total GET calls
	totalGetCalls := 0
	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, "cmdstat_get:calls=") {
			startIndex := strings.Index(value, "cmdstat_get:calls=") + len("cmdstat_get:calls=")
			endIndex := strings.Index(value[startIndex:], ",") + startIndex
			calls, err := strconv.Atoi(value[startIndex:endIndex])
			assert.NoError(suite.T(), err)
			totalGetCalls += calls
		}
	}
	assert.Equal(suite.T(), nGetCalls, totalGetCalls)

	clientForTestingAz.Close()
}

func (suite *GlideTestSuite) TestAllNodesRoutesToPrimaryAndReplicas() {
	suite.SkipIfServerVersionLowerThan("8.0.0", suite.T())

	const nGetCalls = 100

	client, err := suite.clusterClient(config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2 * time.Second).
		WithReadFrom(config.ReadFromAllNodes))
	require.NoError(suite.T(), err)
	defer client.Close()

	suite.verifyOK(
		client.ConfigResetStatWithOptions(context.Background(), options.RouteOption{Route: config.AllNodes}),
	)

	for i := 0; i < nGetCalls; i++ {
		_, err = client.Get(context.Background(), "foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := client.InfoWithOptions(context.Background(),
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.All}},
			RouteOption: &options.RouteOption{Route: config.AllNodes},
		},
	)
	assert.NoError(suite.T(), err)

	nodesWithGets := 0
	primaryReceivedGets := false
	replicaReceivedGets := false

	for _, value := range infoResult.MultiValue() {
		if strings.Contains(value, "cmdstat_get:calls=") {
			nodesWithGets++
			if strings.Contains(value, "role:master") {
				primaryReceivedGets = true
			}
			if strings.Contains(value, "role:slave") {
				replicaReceivedGets = true
			}
		}
	}

	assert.Greater(suite.T(), nodesWithGets, 1)
	assert.True(suite.T(), primaryReceivedGets)
	assert.True(suite.T(), replicaReceivedGets)
}
