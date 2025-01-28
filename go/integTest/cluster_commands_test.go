// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api/config"
	"github.com/valkey-io/valkey-glide/go/glide/api/options"
)

func (suite *GlideTestSuite) TestClusterCustomCommandInfo() {
	client := suite.defaultClusterClient()
	result, err := client.CustomCommand([]string{"INFO"})

	assert.Nil(suite.T(), err)
	// INFO is routed to all primary nodes by default
	for _, value := range result.Value().(map[string]interface{}) {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandEcho() {
	client := suite.defaultClusterClient()
	result, err := client.CustomCommand([]string{"ECHO", "GO GLIDE GO"})

	assert.Nil(suite.T(), err)
	// ECHO is routed to a single random node
	assert.Equal(suite.T(), "GO GLIDE GO", result.Value().(string))
}
func (suite *GlideTestSuite) TestEchoWithOptionsNoRoute() {
	client := suite.defaultClusterClient()
	options := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "hello",
		},
		Route: nil,
	}

	result, err := client.EchoWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestEchoWithOptionsWithRoute() {
	client := suite.defaultClusterClient()
	route := config.Route(config.AllNodes)
	options := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "hello",
		},
		Route: &route,
	}

	result, err := client.EchoWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestEchoWithOptionsInvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.Route(config.NewByAddressRoute("invalidHost", 9999))
	options := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "hello",
		},
		Route: &invalidRoute,
	}

	result, err := client.EchoWithOptions(options)
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), result)
}
