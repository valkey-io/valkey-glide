// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
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
