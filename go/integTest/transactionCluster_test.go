// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func (suite *GlideTestSuite) TestExecCluster() {
	client := suite.defaultClusterClient()
	tx := api.NewClusterTransaction(client)
	cmd := tx.GlideClusterClient
	var simpleRoute config.Route = config.RandomRoute
	pingOpts := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "Hello Valkey",
		},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}
	cmd.PingWithOptions(pingOpts)
	echoOpts := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "Echo command",
		},
		RouteOption: &options.RouteOption{
			Route: simpleRoute,
		},
	}
	cmd.EchoWithOptions(echoOpts)
	result, errTx := tx.Exec()
	assert.NoError(suite.T(), errTx)
	resultString := anyToString(result)
	strings.Contains(resultString, "Hello Valkey Echo command")
}
