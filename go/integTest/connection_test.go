// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"github.com/aws/glide-for-redis/go/glide/api"
	"github.com/stretchr/testify/assert"
)

func (suite *GlideTestSuite) TestStandaloneConnect() {
	config := api.NewRedisClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.standalonePorts[0]})
	client, err := api.CreateClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect() {
	config := api.NewRedisClusterClientConfiguration()
	for _, port := range suite.clusterPorts {
		config.WithAddress(&api.NodeAddress{Port: port})
	}

	client, err := api.CreateClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect_singlePort() {
	config := api.NewRedisClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Port: suite.clusterPorts[0]})

	client, err := api.CreateClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestConnectWithInvalidAddress() {
	config := api.NewRedisClientConfiguration().
		WithAddress(&api.NodeAddress{Host: "invalid-host"})
	client, err := api.CreateClient(config)

	assert.Nil(suite.T(), client)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.ConnectionError{}, err)
}
