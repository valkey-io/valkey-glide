// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"log"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/valkey-io/valkey-glide/go/api"
)

// PubSubTestSuite is a test suite specifically for PubSub functionality
type PubSubTestSuite struct {
	suite.Suite
	standaloneHosts     []api.NodeAddress
	clusterHosts        []api.NodeAddress
	tls                 bool
	serverVersion       string
	namedClients        map[string]api.GlideClientCommands
	namedClusterClients map[string]api.GlideClusterClientCommands
}

// Implement PubSubServerVersionGetter interface
func (suite *PubSubTestSuite) GetStandaloneHosts() []api.NodeAddress {
	return suite.standaloneHosts
}

func (suite *PubSubTestSuite) GetClusterHosts() []api.NodeAddress {
	return suite.clusterHosts
}

func (suite *PubSubTestSuite) GetTLS() bool {
	return suite.tls
}

func (suite *PubSubTestSuite) SetupSuite() {
	// Initialize the test suite directly instead of trying to reuse GlideTestSuite
	// Stop cluster in case previous test run was interrupted or crashed and didn't stop.
	// If an error occurs, we ignore it in case the servers actually were stopped before running this.
	pubsubRunClusterManager(suite, []string{"stop", "--prefix", "cluster"}, true)

	// Delete dirs if stop failed due to https://github.com/valkey-io/valkey-glide/issues/849
	err := os.RemoveAll("../../utils/clusters")
	if err != nil && !os.IsNotExist(err) {
		log.Fatal(err)
	}

	cmd := []string{}

	suite.tls = false
	if *tls {
		cmd = append(cmd, "--tls")
		suite.tls = true
	}

	suite.T().Logf("TLS = %t", suite.tls)

	// Note: code does not start standalone if cluster hosts are given and vice versa
	startServer := true

	if *standaloneHosts != "" {
		suite.standaloneHosts = pubsubParseHosts(suite, *standaloneHosts)
		startServer = false
	}
	if *clusterHosts != "" {
		suite.clusterHosts = pubsubParseHosts(suite, *clusterHosts)
		startServer = false
	}
	if startServer {
		// Start standalone instance
		startCmd := append(cmd, "start", "-r", "3")
		clusterManagerOutput := pubsubRunClusterManager(suite, startCmd, false)
		suite.standaloneHosts = pubsubExtractAddresses(suite, clusterManagerOutput)

		// Start cluster (with cluster-mode flag)
		clusterCmd := append(cmd, "start", "--cluster-mode", "-r", "3")
		clusterManagerOutput = pubsubRunClusterManager(suite, clusterCmd, false)
		suite.clusterHosts = pubsubExtractAddresses(suite, clusterManagerOutput)
	}

	suite.T().Logf("Standalone hosts = %s", fmt.Sprint(suite.standaloneHosts))
	suite.T().Logf("Cluster hosts = %s", fmt.Sprint(suite.clusterHosts))

	// Get server version
	suite.serverVersion = pubsubGetServerVersion(suite)
	suite.T().Logf("Detected server version = %s", suite.serverVersion)

	// Initialize client maps
	suite.namedClients = make(map[string]api.GlideClientCommands)
	suite.namedClusterClients = make(map[string]api.GlideClusterClientCommands)
}

func (suite *PubSubTestSuite) TearDownSuite() {
	pubsubRunClusterManager(suite, []string{"stop", "--prefix", "cluster", "--keep-folder"}, false)
}

func (suite *PubSubTestSuite) TearDownTest() {
	for _, client := range suite.namedClients {
		client.Close()
	}
	suite.namedClients = make(map[string]api.GlideClientCommands)

	for _, client := range suite.namedClusterClients {
		client.Close()
	}
	suite.namedClusterClients = make(map[string]api.GlideClusterClientCommands)
}

func (suite *PubSubTestSuite) defaultClientConfig() *api.GlideClientConfiguration {
	return api.NewGlideClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5000)
}

func (suite *PubSubTestSuite) createNamedClient(name string, config *api.GlideClientConfiguration) api.GlideClientCommands {
	client, err := api.NewGlideClient(config)
	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	suite.namedClients[name] = client
	return client
}

func (suite *PubSubTestSuite) createDefaultNamedClient(name string) api.GlideClientCommands {
	config := suite.defaultClientConfig()
	return suite.createNamedClient(name, config)
}

func (suite *PubSubTestSuite) createDefaultNamedClientWithSubscriptions(
	name string,
	subscriptionConfig *api.StandaloneSubscriptionConfig,
) api.GlideClientCommands {
	config := suite.defaultClientConfig().
		WithSubscriptionConfig(subscriptionConfig)
	return suite.createNamedClient(name, config)
}

func TestPubSubTestSuite(t *testing.T) {
	suite.Run(t, new(PubSubTestSuite))
}
