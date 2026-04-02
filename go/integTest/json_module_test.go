// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/servermodules/glidejson"
)

const (
	jsonTestPath      = "$"
	jsonTestValue     = `{"a": 1.0, "b": 2}`
	jsonTestKeyPrefix = "{json-key}-"
)

// jsonSetGetOps abstracts the JSON set/get operations for both standalone and cluster clients.
type jsonSetGetOps struct {
	set              func(ctx context.Context, key, path, value string) (string, error)
	setWithCondition func(ctx context.Context, key, path, value string, c constants.ConditionalSet) (string, error)
	get              func(ctx context.Context, key string) (string, error)
	getWithPaths     func(ctx context.Context, key string, paths []string) (string, error)
	getWithOptions   func(ctx context.Context, key string, paths []string, o *options.JsonGetOptions) (string, error)
}

func (suite *GlideTestSuite) standaloneJsonOps() jsonSetGetOps {
	client := suite.defaultClient()
	return jsonSetGetOps{
		set: func(ctx context.Context, key, path, value string) (string, error) {
			return glidejson.JsonSet(client, ctx, key, path, value)
		},
		setWithCondition: func(ctx context.Context, key, path, value string, c constants.ConditionalSet) (string, error) {
			return glidejson.JsonSetWithCondition(client, ctx, key, path, value, c)
		},
		get: func(ctx context.Context, key string) (string, error) {
			return glidejson.JsonGet(client, ctx, key)
		},
		getWithPaths: func(ctx context.Context, key string, paths []string) (string, error) {
			return glidejson.JsonGetWithPaths(client, ctx, key, paths)
		},
		getWithOptions: func(ctx context.Context, key string, paths []string, o *options.JsonGetOptions) (string, error) {
			return glidejson.JsonGetWithOptions(client, ctx, key, paths, o)
		},
	}
}

func (suite *GlideTestSuite) clusterJsonOps() jsonSetGetOps {
	client := suite.defaultClusterClient()
	return jsonSetGetOps{
		set: func(ctx context.Context, key, path, value string) (string, error) {
			return glidejson.ClusterJsonSet(client, ctx, key, path, value)
		},
		setWithCondition: func(ctx context.Context, key, path, value string, c constants.ConditionalSet) (string, error) {
			return glidejson.ClusterJsonSetWithCondition(client, ctx, key, path, value, c)
		},
		get: func(ctx context.Context, key string) (string, error) {
			return glidejson.ClusterJsonGet(client, ctx, key)
		},
		getWithPaths: func(ctx context.Context, key string, paths []string) (string, error) {
			return glidejson.ClusterJsonGetWithPaths(client, ctx, key, paths)
		},
		getWithOptions: func(ctx context.Context, key string, paths []string, o *options.JsonGetOptions) (string, error) {
			return glidejson.ClusterJsonGetWithOptions(client, ctx, key, paths, o)
		},
	}
}

func (suite *GlideTestSuite) verifyJsonSetAndGet(ops jsonSetGetOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	result, err := ops.set(ctx, key, jsonTestPath, jsonTestValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	getResult, err := ops.get(ctx, key)
	assert.NoError(t, err)
	assert.Contains(t, getResult, `"a"`)
	assert.Contains(t, getResult, `"b"`)

	getPathResult, err := ops.getWithPaths(ctx, key, []string{"$.a", "$.b"})
	assert.NoError(t, err)
	assert.Contains(t, getPathResult, "$.a")
	assert.Contains(t, getPathResult, "$.b")

	getResult, err = ops.get(ctx, "non_existing_key")
	assert.NoError(t, err)
	assert.Equal(t, "", getResult)
}

func (suite *GlideTestSuite) verifyJsonSetWithCondition(ops jsonSetGetOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	// NX - should succeed on new key
	result, err := ops.setWithCondition(ctx, key, jsonTestPath, `{"a": 1.0}`, constants.OnlyIfDoesNotExist)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// NX again - should fail (key exists)
	result, err = ops.setWithCondition(ctx, key, jsonTestPath, `{"a": 2.0}`, constants.OnlyIfDoesNotExist)
	assert.NoError(t, err)
	assert.Equal(t, "", result)

	// XX - should succeed (key exists)
	result, err = ops.setWithCondition(ctx, key, jsonTestPath, `{"a": 3.0}`, constants.OnlyIfExists)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestModuleJsonSetAndGet_Standalone() {
	suite.verifyJsonSetAndGet(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonSetWithCondition_Standalone() {
	suite.verifyJsonSetWithCondition(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) verifyJsonGetWithOptions(ops jsonSetGetOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, jsonTestPath, `{"a": 1, "b": 2}`)
	assert.NoError(t, err)

	opts := options.NewJsonGetOptions().SetIndent("  ").SetNewline("\n").SetSpace(" ")
	result, err := ops.getWithOptions(ctx, key, []string{jsonTestPath}, opts)
	assert.NoError(t, err)
	assert.Contains(t, result, "\n")
}

func (suite *GlideTestSuite) TestModuleJsonGetWithOptions_Standalone() {
	suite.verifyJsonGetWithOptions(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonSetAndGet_Cluster() {
	suite.verifyJsonSetAndGet(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonSetWithCondition_Cluster() {
	suite.verifyJsonSetWithCondition(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonGetWithOptions_Cluster() {
	suite.verifyJsonGetWithOptions(suite.clusterJsonOps())
}
