// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
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

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Info() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.AllPrimaries)
	result, err := client.CustomCommandWithRoute([]string{"INFO"}, route)
	assert.Nil(suite.T(), err)
	for _, value := range result.Value().(map[string]interface{}) {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Echo() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.RandomRoute)
	result, err := client.CustomCommandWithRoute([]string{"ECHO", "GO GLIDE GO"}, route)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "GO GLIDE GO", result.Value().(string))
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.NewByAddressRoute("invalidHost", 9999)
	result, err := client.CustomCommandWithRoute([]string{"PING"}, invalidRoute)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), api.CreateEmptyClusterValue(), result)
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_AllNodes() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.AllNodes)
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

func (suite *GlideTestSuite) TestPingWithOptions_OnlyRoute() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.RandomRoute)
	options := options.NewPingOptionsBuilder().
		SetRoute(route)

	result, err := client.PingWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result) // Default response when no message is set
}

func (suite *GlideTestSuite) TestPingWithOptions_BasicPing() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.RandomRoute)
	options := options.NewPingOptionsBuilder().
		SetRoute(route).
		SetMessage("hello")

	result, err := client.PingWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestPingWithOptions_AllNodes() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.AllNodes)
	options := options.NewPingOptionsBuilder().
		SetRoute(route).
		SetMessage("hello")

	result, err := client.PingWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestPingWithOptions_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.NewByAddressRoute("invalidHost", 9999)
	options := options.NewPingOptionsBuilder().
		SetRoute(invalidRoute).
		SetMessage("hello")

	result, err := client.PingWithOptions(options)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
}
