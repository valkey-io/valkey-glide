// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson"
)

func (suite *GlideTestSuite) TestModuleVerifyJsonLoaded() {
	client := suite.defaultClusterClient()
	result, err := client.InfoWithOptions(
		api.ClusterInfoOptions{InfoOptions: &api.InfoOptions{Sections: []api.Section{api.Server}}, Route: nil},
	)

	assert.Nil(suite.T(), err)
	for _, value := range result.MultiValue() {
		assert.True(suite.T(), strings.Contains(value, "# json_core_metrics"))
	}
}

func (suite *GlideTestSuite) TestModuleSetCommand() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	jsonValue := "{\"a\": 1.0,\"b\": 2}"

	jsonSetResult, err := glidejson.Set(client, key, "$", jsonValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult)
}
