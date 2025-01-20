// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
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

func (suite *GlideTestSuite) TestPingWithRoute_ValidRoute() {
	client := suite.defaultClusterClient()
	route := api.SimpleNodeRoute(api.RandomRoute)
	result, err := client.PingWithRoute(route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result)
}

func (suite *GlideTestSuite) TestPingWithRoute_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := api.NewByAddressRoute("invalidHost", 9999)

	result, err := client.PingWithRoute(invalidRoute)

	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestPingWithMessageRoute_ValidRoute_ValidMessage() {
	client := suite.defaultClusterClient()
	route := api.SimpleNodeRoute(api.RandomRoute)

	customMessage := "Hello Glide"

	result, err := client.PingWithMessageRoute(customMessage, route)

	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), customMessage, result)
}

func (suite *GlideTestSuite) TestPingWithMessageRoute_EmptyMessage() {
	client := suite.defaultClusterClient()
	route := api.SimpleNodeRoute(api.RandomRoute)

	customMessage := ""

	result, err := client.PingWithMessageRoute(customMessage, route)

	assert.NoError(suite.T(), err)

	assert.Equal(suite.T(), customMessage, result)
}

func (suite *GlideTestSuite) TestPingWithMessageRoute_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := api.NewByAddressRoute("invalidHost", 9999)

	customMessage := "Hello Glide"

	result, err := client.PingWithMessageRoute(customMessage, invalidRoute)

	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Info() {
	client := suite.defaultClusterClient()
	route := api.SimpleNodeRoute(api.AllPrimaries)
	result, err := client.CustomCommandWithRoute([]string{"INFO"}, route)
	assert.Nil(suite.T(), err)
	for _, value := range result.Value().(map[string]interface{}) {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Echo() {
	client := suite.defaultClusterClient()
	route := api.SimpleNodeRoute(api.RandomRoute)
	result, err := client.CustomCommandWithRoute([]string{"ECHO", "GO GLIDE GO"}, route)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "GO GLIDE GO", result.Value().(string))
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := api.NewByAddressRoute("invalidHost", 9999)
	result, err := client.CustomCommandWithRoute([]string{"PING"}, invalidRoute)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), api.CreateEmptyClusterValue(), result)
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_AllNodes() {
	client := suite.defaultClusterClient()
	route := api.SimpleNodeRoute(api.AllNodes)
	result, err := client.CustomCommandWithRoute([]string{"PING"}, route)

	assert.Nil(suite.T(), err)
	value := result.Value()

	fmt.Printf("Value type: %T\n", value)

	if result.IsMultiValue() {
		responses := value.(map[string]interface{})
		assert.Greater(suite.T(), len(responses), 0)
		for _, response := range responses {
			assert.Equal(suite.T(), "PONG", response.(string))
		}
	} else {
		assert.Equal(suite.T(), "PONG", value.(string))
	}
}
