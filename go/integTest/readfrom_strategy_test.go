// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func (suite *GlideTestSuite) TestRoutingWithAzAffinityStrategyTo1Replica() {
	suite.SkipIfServerVersionLowerThanBy("8.0.0")
	az := "us-east-1a"
	const GET_CALLS = 3
	getCmdStat := "cmdstat_get:calls=" + fmt.Sprint(GET_CALLS)

	clientForConfigSet := suite.clusterClient(suite.defaultClusterClientConfig().WithRequestTimeout(2000))

	// Reset the availability zone for all nodes
	_, err := clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "SET", "availability-zone", ""}, config.AllNodes)
	assert.NoError(suite.T(), err)
	resetStatResult, err := clientForConfigSet.CustomCommand([]string{"CONFIG", "RESETSTAT"})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", resetStatResult.SingleValue())

	// 12182 is the slot of "foo"
	_, err = clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "SET", "availability-zone", az}, config.NewSlotIdRoute(config.SlotTypeReplica, 12182))
	assert.NoError(suite.T(), err)

	clientForTestingAz := suite.clusterClient(suite.defaultClusterClientConfig().
		WithRequestTimeout(2000).
		WithReadFrom(api.AzAffinity).
		WithClientAZ(az))

	for i := 0; i < GET_CALLS; i++ {
		_, err := clientForTestingAz.Get("foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Server, options.Commandstats}},
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
	suite.SkipIfServerVersionLowerThanBy("8.0.0")
	az := "us-east-1a"

	clientForConfigSet := suite.clusterClient(suite.defaultClusterClientConfig().WithRequestTimeout(2000))

	// Reset the availability zone for all nodes
	_, err := clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "SET", "availability-zone", ""}, config.AllNodes)
	assert.NoError(suite.T(), err)
	resetStatResult, err := clientForConfigSet.CustomCommand([]string{"CONFIG", "RESETSTAT"})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", resetStatResult.SingleValue())

	// Get Replica Count for current cluster
	clusterInfo, err := clientForConfigSet.CustomCommandWithRoute(
		[]string{"INFO", "REPLICATION"}, config.NewSlotKeyRoute(config.SlotTypePrimary, "key"))
	assert.NoError(suite.T(), err)
	nReplicas := 0
	if clusterInfoStr, ok := clusterInfo.SingleValue().(string); ok {
		for _, line := range strings.Split(clusterInfoStr, "\n") {
			parts := strings.SplitN(line, ":", 2)
			if len(parts) == 2 && strings.TrimSpace(parts[0]) == "connected_slaves" {
				nReplicas, err = strconv.Atoi(strings.TrimSpace(parts[1]))
				assert.NoError(suite.T(), err)
				break
			}
		}
	}
	nGetCalls := 3 * nReplicas
	getCmdStat := fmt.Sprintf("cmdstat_get:calls=%d", 3)

	// Setting AZ for all Nodes
	_, err = clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "SET", "availability-zone", az}, config.AllNodes)
	assert.NoError(suite.T(), err)
	clientForConfigSet.Close()

	// Creating Client with AZ configuration for testing
	clientForTestingAz := suite.clusterClient(suite.defaultClusterClientConfig().
		WithRequestTimeout(2000).
		WithReadFrom(api.AzAffinity).
		WithClientAZ(az))

	azGetResult, err := clientForTestingAz.CustomCommandWithRoute(
		[]string{"CONFIG", "GET", "availability-zone"}, config.AllNodes)
	assert.NoError(suite.T(), err)
	for _, value := range azGetResult.MultiValue() {
		if valueMap, ok := value.(map[string]interface{}); ok {
			if azValue, ok := valueMap["availability-zone"].(string); ok {
				assert.Equal(suite.T(), az, azValue)
			}
		}
	}

	// Execute GET commands
	for i := 0; i < nGetCalls; i++ {
		_, err := clientForTestingAz.Get("foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []options.Section{options.All}},
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
	suite.SkipIfServerVersionLowerThanBy("8.0.0")

	const nGetCalls = 3
	const nReplicaCalls = 1
	getCmdStat := fmt.Sprintf("cmdstat_get:calls=%d", nReplicaCalls)

	clientForTestingAz := suite.clusterClient(api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2000).
		WithReadFrom(api.AzAffinity).
		WithClientAZ("non-existing-az"))

	// Reset stats
	resetStatResult, err := clientForTestingAz.CustomCommandWithRoute(
		[]string{"CONFIG", "RESETSTAT"}, config.AllNodes)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", resetStatResult.SingleValue())

	// Execute GET commands
	for i := 0; i < nGetCalls; i++ {
		_, err := clientForTestingAz.Get("foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Commandstats}},
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
	suite.SkipIfServerVersionLowerThanBy("8.0.0")

	az := "us-east-1a"
	otherAz := "us-east-1b"
	const nGetCalls = 4
	getCmdStat := fmt.Sprintf("cmdstat_get:calls=%d", nGetCalls)

	// Create client for setting the configs
	clientForConfigSet := suite.clusterClient(api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2000))

	// Reset stats and set all nodes to otherAz
	resetStatResult, err := clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "RESETSTAT"}, config.AllNodes)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", resetStatResult.SingleValue())

	_, err = clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "SET", "availability-zone", otherAz}, config.AllNodes)
	assert.NoError(suite.T(), err)

	// Set primary for slot 12182 to az
	_, err = clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "SET", "availability-zone", az}, config.NewSlotIdRoute(config.SlotTypePrimary, 12182))
	assert.NoError(suite.T(), err)

	// Verify primary AZ
	primaryAzResult, err := clientForConfigSet.CustomCommandWithRoute(
		[]string{"CONFIG", "GET", "availability-zone"}, config.NewSlotIdRoute(config.SlotTypePrimary, 12182))
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), az, primaryAzResult.MultiValue()["availability-zone"])

	clientForConfigSet.Close()

	// Create test client with AZ_AFFINITY_REPLICAS_AND_PRIMARY configuration
	clientForTestingAz := suite.clusterClient(api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(2000).
		WithReadFrom(api.AzAffinityReplicaAndPrimary).
		WithClientAZ(az))

	// Execute GET commands
	for i := 0; i < nGetCalls; i++ {
		_, err := clientForTestingAz.Get("foo")
		assert.NoError(suite.T(), err)
	}

	infoResult, err := clientForTestingAz.InfoWithOptions(
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []options.Section{options.All}},
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
