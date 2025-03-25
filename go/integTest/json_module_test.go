// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api/options"
	"github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson"
	glideoptions "github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson/options"
)

func (suite *GlideTestSuite) TestModuleVerifyJsonLoaded() {
	client := suite.defaultClusterClient()
	result, err := client.InfoWithOptions(
		options.ClusterInfoOptions{
			InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Server}},
			RouteOption: nil,
		},
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
	jsonValue := "{\"a\":1.0,\"b\":2}"

	suite.verifyOK(glidejson.Set(client, key, "$", jsonValue))

	jsonGetResult, err := glidejson.Get(client, key)
	assert.NoError(t, err)
	assert.Equal(t, jsonValue, jsonGetResult.Value())

	jsonGetResultWithMultiPaths, err := glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$.a", "$.b"}))
	assert.NoError(t, err)
	assert.Equal(t, "{\"$.a\":[1.0],\"$.b\":[2]}", jsonGetResultWithMultiPaths.Value())

	jsonGetResult, err = glidejson.Get(client, "non_existing_key")
	assert.NoError(t, err)
	assert.True(t, jsonGetResult.IsNil())

	jsonGetResult, err = glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$.d"}))
	assert.NoError(t, err)
	assert.Equal(t, "[]", jsonGetResult.Value())
}

func (suite *GlideTestSuite) TestModuleGetSetCommandMultipleValues() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	jsonValue := "{\"a\": {\"c\": 1, \"d\": 4}, \"b\": {\"c\": 2}, \"c\": true}"
	suite.verifyOK(glidejson.Set(client, key, "$", jsonValue))

	jsonGetResult, err := glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c"}))
	assert.NoError(t, err)
	assert.Equal(t, "[true,1,2]", jsonGetResult.Value())

	jsonGetResultWithMultiPaths, err := glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c", "$.c"}))
	assert.NoError(t, err)
	assert.Equal(t, "{\"$..c\":[true,1,2],\"$.c\":[true]}", jsonGetResultWithMultiPaths.Value())
	suite.verifyOK(glidejson.Set(client, key, "$..c", "\"new_value\""))

	jsonGetResult, err = glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c"}))
	assert.NoError(t, err)
	assert.Equal(t, "[\"new_value\",\"new_value\",\"new_value\"]", jsonGetResult.Value())
}

func (suite *GlideTestSuite) TestModuleGetSetCommandConditionalSet() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	jsonValue := "{\"a\": 1.0, \"b\": 2}"

	jsonSetResult, err := glidejson.SetWithOptions(
		client,
		key,
		"$",
		jsonValue,
		*glideoptions.NewJsonSetOptionsBuilder().SetConditionalSet(options.OnlyIfExists),
	)
	assert.NoError(t, err)
	assert.True(t, jsonSetResult.IsNil())

	jsonSetResult, err = glidejson.SetWithOptions(
		client,
		key,
		"$",
		jsonValue,
		*glideoptions.NewJsonSetOptionsBuilder().SetConditionalSet(options.OnlyIfDoesNotExist),
	)
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult.Value())

	jsonSetResult, err = glidejson.SetWithOptions(
		client,
		key,
		"$.a",
		"4.5",
		*glideoptions.NewJsonSetOptionsBuilder().SetConditionalSet(options.OnlyIfDoesNotExist),
	)
	assert.NoError(t, err)
	assert.True(t, jsonSetResult.IsNil())

	jsonGetResult, err := glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{".a"}))
	assert.NoError(t, err)
	assert.Equal(t, "1.0", jsonGetResult.Value())

	jsonSetResult, err = glidejson.SetWithOptions(
		client,
		key,
		"$.a",
		"4.5",
		*glideoptions.NewJsonSetOptionsBuilder().SetConditionalSet(options.OnlyIfExists),
	)
	assert.NoError(t, err)
	assert.Equal(t, "OK", jsonSetResult.Value())

	jsonGetResult, err = glidejson.GetWithOptions(
		client, key, *glideoptions.NewJsonGetOptionsBuilder().SetPaths([]string{".a"}))
	assert.NoError(t, err)
	assert.Equal(t, "4.5", jsonGetResult.Value())
}

func (suite *GlideTestSuite) TestModuleGetSetCommandFormatting() {
	client := suite.defaultClusterClient()
	t := suite.T()
	key := uuid.New().String()
	suite.verifyOK(glidejson.Set(client, key, "$", "{\"a\": 1.0, \"b\": 2, \"c\": {\"d\": 3, \"e\": 4}}"))
	expectedGetResult := "[\n" +
		"  {\n" +
		"    \"a\": 1.0,\n" +
		"    \"b\": 2,\n" +
		"    \"c\": {\n" +
		"      \"d\": 3,\n" +
		"      \"e\": 4\n" +
		"    }\n" +
		"  }\n" +
		"]"

	actualGetResult, err := glidejson.GetWithOptions(
		client,
		key,
		*glideoptions.NewJsonGetOptionsBuilder().
			SetPaths([]string{"$"}).SetIndent("  ").SetNewline("\n").SetSpace(" "),
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedGetResult, actualGetResult.Value())

	expectedGetResult2 := "[\n~{\n~~\"a\":*1.0,\n~~\"b\":*2,\n~~\"c\":*{\n~~~\"d\":*3,\n~~~\"e\":*4\n~~}\n~}\n]"
	actualGetResult2, err := glidejson.GetWithOptions(
		client,
		key,
		*glideoptions.NewJsonGetOptionsBuilder().
			SetPaths([]string{"$"}).SetIndent("~").SetNewline("\n").SetSpace("*"),
	)
	assert.NoError(t, err)
	assert.Equal(t, expectedGetResult2, actualGetResult2.Value())
}
