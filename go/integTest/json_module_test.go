// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson"
	"github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson/options"
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

func (suite *GlideTestSuite) TestModuleGetSetCommand() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	jsonValue := "{\"a\": 1.0,\"b\": 2}"

	jsonSetResult, err := glidejson.Set(client, key, "$", jsonValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult)

	jsonGetResult, err := glidejson.Get(client, key)
	assert.NoError(t, err)
	assert.Equal(t, jsonValue, jsonGetResult)
	// String getResultWithMultiPaths = Json.get(client, key, new String[] {"$.a", "$.b"}).get();

	jsonGetResultWithMultiPaths, err := glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$.a", "$.b"}))
	assert.NoError(t, err)
	assert.Equal(t, "{\"$.a\":[1.0],\"$.b\":[2]}", jsonGetResultWithMultiPaths)

	jsonGetResult, err = glidejson.Get(
		client, "non_existing_key")
	assert.NoError(t, err)
	assert.Nil(t, jsonGetResult)

	jsonGetResult, err = glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$.d"}))
	assert.NoError(t, err)
	assert.Equal(t, "[]", jsonGetResult)
}
