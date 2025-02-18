// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
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
	fmt.Println(result)
	for _, value := range result.MultiValue() {
		assert.True(suite.T(), strings.Contains(value, "# json_core_metrics"))
	}
}

func (suite *GlideTestSuite) TestModuleGetSetCommand() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	jsonValue := "{\"a\":1.0,\"b\":2}"

	jsonSetResult, err := glidejson.Set(client, key, "$", jsonValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult)

	jsonGetResult, err := glidejson.Get(client, key)
	assert.NoError(t, err)
	assert.Equal(t, jsonValue, jsonGetResult.Value())

	jsonGetResultWithMultiPaths, err := glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$.a", "$.b"}))
	assert.NoError(t, err)
	assert.Equal(t, "{\"$.a\":[1.0],\"$.b\":[2]}", jsonGetResultWithMultiPaths.Value())

	jsonGetResult, err = glidejson.Get(client, "non_existing_key")
	assert.NoError(t, err)
	assert.True(t, jsonGetResult.IsNil())

	jsonGetResult, err = glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$.d"}))
	assert.NoError(t, err)
	assert.Equal(t, "[]", jsonGetResult.Value())
}

func (suite *GlideTestSuite) TestModuleGetSetCommandMultipleValues() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	jsonValue := "{\"a\": {\"c\": 1, \"d\": 4}, \"b\": {\"c\": 2}, \"c\": true}"
	jsonSetResult, err := glidejson.Set(client, key, "$", jsonValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult)

	jsonGetResult, err := glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c"}))
	assert.NoError(t, err)
	assert.Equal(t, "[true,1,2]", jsonGetResult.Value())

	jsonGetResultWithMultiPaths, err := glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c", "$.c"}))
	assert.NoError(t, err)
	assert.Equal(t, "{\"$..c\":[true, 1,2],\"$.c\":[true]}", jsonGetResultWithMultiPaths.Value())

	jsonSetResult, err = glidejson.Set(client, key, "$..c", "\"new_value\"")
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult)

	jsonGetResult, err = glidejson.GetWithOptions(
		client, key, options.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c"}))
	assert.NoError(t, err)
	assert.Equal(t, "[\"new_value\",\"new_value\",\"new_value\"]", jsonGetResult.Value())
}
