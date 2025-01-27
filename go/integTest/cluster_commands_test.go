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

func (suite *GlideTestSuite) TestTimeWithoutRoute() {
	client := suite.defaultClusterClient()
	options := options.ClusterTimeOptions{Route: nil}
	result, err := client.TimeWithOptions(options)

	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.NotEmpty(suite.T(), result.Value())
	assert.IsType(suite.T(), "", result.Value()[0])
	assert.Equal(suite.T(), 2, len(result.Value()))
}

func (suite *GlideTestSuite) TestTimeWithAllNodesRoute() {
	client := suite.defaultClusterClient()
	route := config.Route(config.AllNodes)
	options := options.ClusterTimeOptions{Route: &route}
	result, err := client.TimeWithOptions(options)

	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.NotEmpty(suite.T(), result.Value())
	assert.Greater(suite.T(), len(result.Value()), 1)

	for _, timeStr := range result.Value() {
		assert.IsType(suite.T(), "", timeStr)
	}
}

func (suite *GlideTestSuite) TestTimeWithRandomRoute() {
	client := suite.defaultClusterClient()
	route := config.Route(config.RandomRoute)
	options := options.ClusterTimeOptions{Route: &route}
	result, err := client.TimeWithOptions(options)

	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.NotEmpty(suite.T(), result.Value())
	assert.IsType(suite.T(), "", result.Value()[0])
	assert.Equal(suite.T(), 2, len(result.Value()))
}

func (suite *GlideTestSuite) TestTimeWithInvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.Route(config.NewByAddressRoute("invalidHost", 9999))
	options := options.ClusterTimeOptions{Route: &invalidRoute}
	result, err := client.TimeWithOptions(options)

	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), result.Value())
}
