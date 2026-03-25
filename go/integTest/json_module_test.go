// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/servermodules/glidejson"
)

func (suite *GlideTestSuite) TestModuleJsonSetAndGet_Standalone() {
	client := suite.defaultClient()
	ctx := context.Background()
	key := "{json-key}-1-" + suite.T().Name()

	// Set a JSON value
	result, err := glidejson.JsonSet(client, ctx, key, "$", `{"a": 1.0, "b": 2}`)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Get the JSON value
	getResult, err := glidejson.JsonGet(client, ctx, key)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), getResult, `"a"`)
	assert.Contains(suite.T(), getResult, `"b"`)

	// Get with specific paths
	getPathResult, err := glidejson.JsonGetWithPaths(client, ctx, key, []string{"$.a", "$.b"})
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), getPathResult, "$.a")
	assert.Contains(suite.T(), getPathResult, "$.b")

	// Get non-existing key
	getResult, err = glidejson.JsonGet(client, ctx, "non_existing_key")
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "", getResult)
}

func (suite *GlideTestSuite) TestModuleJsonSetWithCondition_Standalone() {
	client := suite.defaultClient()
	ctx := context.Background()
	key := "{json-key}-2-" + suite.T().Name()

	// Set with NX (only if does not exist) - should succeed
	result, err := glidejson.JsonSetWithCondition(
		client, ctx, key, "$", `{"a": 1.0}`, constants.OnlyIfDoesNotExist,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Set with NX again - should fail (key already exists)
	result, err = glidejson.JsonSetWithCondition(
		client, ctx, key, "$", `{"a": 2.0}`, constants.OnlyIfDoesNotExist,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "", result)

	// Set with XX (only if exists) - should succeed
	result, err = glidejson.JsonSetWithCondition(
		client, ctx, key, "$", `{"a": 3.0}`, constants.OnlyIfExists,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)
}

func (suite *GlideTestSuite) TestModuleJsonGetWithOptions_Standalone() {
	client := suite.defaultClient()
	ctx := context.Background()
	key := "{json-key}-3-" + suite.T().Name()

	_, err := glidejson.JsonSet(client, ctx, key, "$", `{"a": 1, "b": 2}`)
	assert.NoError(suite.T(), err)

	opts := options.NewJsonGetOptions().SetIndent("  ").SetNewline("\n").SetSpace(" ")
	result, err := glidejson.JsonGetWithOptions(client, ctx, key, []string{"$"}, opts)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), result, "\n")
}

func (suite *GlideTestSuite) TestModuleJsonSetAndGet_Cluster() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	key := "{json-key}-4-" + suite.T().Name()

	// Set a JSON value
	result, err := glidejson.ClusterJsonSet(client, ctx, key, "$", `{"a": 1.0, "b": 2}`)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Get the JSON value
	getResult, err := glidejson.ClusterJsonGet(client, ctx, key)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), getResult, `"a"`)
	assert.Contains(suite.T(), getResult, `"b"`)

	// Get with specific paths
	getPathResult, err := glidejson.ClusterJsonGetWithPaths(client, ctx, key, []string{"$.a", "$.b"})
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), getPathResult, "$.a")
	assert.Contains(suite.T(), getPathResult, "$.b")

	// Get non-existing key
	getResult, err = glidejson.ClusterJsonGet(client, ctx, "non_existing_key")
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "", getResult)
}

func (suite *GlideTestSuite) TestModuleJsonSetWithCondition_Cluster() {
	client := suite.defaultClusterClient()
	ctx := context.Background()
	key := "{json-key}-5-" + suite.T().Name()

	// Set with NX - should succeed
	result, err := glidejson.ClusterJsonSetWithCondition(
		client, ctx, key, "$", `{"a": 1.0}`, constants.OnlyIfDoesNotExist,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Set with NX again - should fail
	result, err = glidejson.ClusterJsonSetWithCondition(
		client, ctx, key, "$", `{"a": 2.0}`, constants.OnlyIfDoesNotExist,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "", result)

	// Set with XX - should succeed
	result, err = glidejson.ClusterJsonSetWithCondition(
		client, ctx, key, "$", `{"a": 3.0}`, constants.OnlyIfExists,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)
}
