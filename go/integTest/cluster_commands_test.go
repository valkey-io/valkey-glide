// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
)

// TODO: Add more tests with route after the implementation of custom commands.
func (suite *GlideTestSuite) TestPingWithRoute() {
	client := suite.defaultClusterClient()
	result, err := client.PingWithRoute(api.SimpleNodeRouteAllPrimaries)
	assert.Equal(suite.T(), "PONG", result.Value())
	assert.Nil(suite.T(), err)
}

func (suite *GlideTestSuite) TestPingWithRouteAndMessage() {
	client := suite.defaultClusterClient()
	result, err := client.PingWithRouteAndMessage("Hello", api.SimpleNodeRouteAllPrimaries)
	assert.Equal(suite.T(), "Hello", result.Value())
	assert.Nil(suite.T(), err)
}

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
