// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
)

func (suite *GlideTestSuite) TestStandaloneConnect() {
	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.standalonePorts[0]})
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect() {
	config := api.NewGlideClusterClientConfiguration()
	for _, port := range suite.clusterPorts {
		config.WithAddress(&api.NodeAddress{Port: port})
	}

	client, err := api.NewGlideClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect_singlePort() {
	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.clusterPorts[0]})

	client, err := api.NewGlideClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestConnectWithInvalidAddress() {
	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Host: "invalid-host"})
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), client)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.ConnectionError{}, err)
}

func (suite *GlideTestSuite) TestConnectionTimeout() {
	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.standalonePorts[0]}).WithConnectionTimeout(2000)
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()


	clusterConfig := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.clusterPorts[0]}).WithConnectionTimeout(2000)

	clusterClient, clusterErr := api.NewGlideClusterClient(clusterConfig)

	assert.Nil(suite.T(), clusterErr)
	assert.NotNil(suite.T(), clusterClient)

	clusterClient.Close()
}
