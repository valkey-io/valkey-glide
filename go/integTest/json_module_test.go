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

// jsonOps abstracts all JSON operations for both standalone and cluster clients.
type jsonOps struct {
	set                 func(ctx context.Context, key, path, value string) (string, error)
	setWithCondition    func(ctx context.Context, key, path, value string, c constants.ConditionalSet) (string, error)
	get                 func(ctx context.Context, key string) (string, error)
	getWithPaths        func(ctx context.Context, key string, paths []string) (string, error)
	getWithOptions      func(ctx context.Context, key string, paths []string, o *options.JsonGetOptions) (string, error)
	del                 func(ctx context.Context, key string) (any, error)
	delWithPath         func(ctx context.Context, key, path string) (any, error)
	forget              func(ctx context.Context, key string) (any, error)
	forgetWithPath      func(ctx context.Context, key, path string) (any, error)
	clear               func(ctx context.Context, key string) (any, error)
	clearWithPath       func(ctx context.Context, key, path string) (any, error)
	mget                func(ctx context.Context, keys []string, path string) (any, error)
	jsonType            func(ctx context.Context, key string) (any, error)
	jsonTypeWithPath    func(ctx context.Context, key, path string) (any, error)
	arrAppend           func(ctx context.Context, key, path string, values []string) (any, error)
	arrInsert           func(ctx context.Context, key, path string, index int64, values []string) (any, error)
	arrIndex            func(ctx context.Context, key, path, scalar string) (any, error)
	arrIndexWithOptions func(ctx context.Context, key, path, scalar string, o *options.JsonArrIndexOptions) (any, error)
	arrLen              func(ctx context.Context, key string) (any, error)
	arrLenWithPath      func(ctx context.Context, key, path string) (any, error)
	arrPop              func(ctx context.Context, key string) (any, error)
	arrPopWithPath      func(ctx context.Context, key, path string) (any, error)
	arrPopWithPathIndex func(ctx context.Context, key, path string, index int64) (any, error)
	arrTrim             func(ctx context.Context, key, path string, start, end int64) (any, error)
	numIncrBy           func(ctx context.Context, key, path string, number float64) (string, error)
	numMultBy           func(ctx context.Context, key, path string, number float64) (string, error)
	toggle              func(ctx context.Context, key string) (any, error)
	toggleWithPath      func(ctx context.Context, key, path string) (any, error)
	strAppend           func(ctx context.Context, key, value string) (any, error)
	strAppendWithPath   func(ctx context.Context, key, path, value string) (any, error)
	strLen              func(ctx context.Context, key string) (any, error)
	strLenWithPath      func(ctx context.Context, key, path string) (any, error)
	objLen              func(ctx context.Context, key string) (any, error)
	objLenWithPath      func(ctx context.Context, key, path string) (any, error)
	objKeys             func(ctx context.Context, key string) (any, error)
	objKeysWithPath     func(ctx context.Context, key, path string) (any, error)
	resp                func(ctx context.Context, key string) (any, error)
	respWithPath        func(ctx context.Context, key, path string) (any, error)
	debugMemory         func(ctx context.Context, key string) (any, error)
	debugMemoryWithPath func(ctx context.Context, key, path string) (any, error)
	debugFields         func(ctx context.Context, key string) (any, error)
	debugFieldsWithPath func(ctx context.Context, key, path string) (any, error)
}

func (suite *GlideTestSuite) standaloneJsonOps() jsonOps {
	c := suite.defaultClient()
	return jsonOps{
		set: func(ctx context.Context, k, p, v string) (string, error) { return glidejson.JsonSet(c, ctx, k, p, v) },
		setWithCondition: func(ctx context.Context, k, p, v string, cond constants.ConditionalSet) (string, error) {
			return glidejson.JsonSetWithCondition(c, ctx, k, p, v, cond)
		},
		get: func(ctx context.Context, k string) (string, error) { return glidejson.JsonGet(c, ctx, k) },
		getWithPaths: func(ctx context.Context, k string, ps []string) (string, error) {
			return glidejson.JsonGetWithPaths(c, ctx, k, ps)
		},
		getWithOptions: func(ctx context.Context, k string, ps []string, o *options.JsonGetOptions) (string, error) {
			return glidejson.JsonGetWithOptions(c, ctx, k, ps, o)
		},
		del:            func(ctx context.Context, k string) (any, error) { return glidejson.JsonDel(c, ctx, k) },
		delWithPath:    func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonDelWithPath(c, ctx, k, p) },
		forget:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonForget(c, ctx, k) },
		forgetWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonForgetWithPath(c, ctx, k, p) },
		clear:          func(ctx context.Context, k string) (any, error) { return glidejson.JsonClear(c, ctx, k) },
		clearWithPath:  func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonClearWithPath(c, ctx, k, p) },
		mget: func(ctx context.Context, ks []string, p string) (any, error) {
			return glidejson.JsonMGet(c, ctx, ks, p)
		},
		jsonType:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonType(c, ctx, k) },
		jsonTypeWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonTypeWithPath(c, ctx, k, p) },
		arrAppend: func(ctx context.Context, k, p string, vs []string) (any, error) {
			return glidejson.JsonArrAppend(c, ctx, k, p, vs)
		},
		arrInsert: func(ctx context.Context, k, p string, i int64, vs []string) (any, error) {
			return glidejson.JsonArrInsert(c, ctx, k, p, i, vs)
		},
		arrIndex: func(ctx context.Context, k, p, s string) (any, error) { return glidejson.JsonArrIndex(c, ctx, k, p, s) },
		arrIndexWithOptions: func(ctx context.Context, k, p, s string, o *options.JsonArrIndexOptions) (any, error) {
			return glidejson.JsonArrIndexWithOptions(c, ctx, k, p, s, o)
		},
		arrLen:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonArrLen(c, ctx, k) },
		arrLenWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonArrLenWithPath(c, ctx, k, p) },
		arrPop:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonArrPop(c, ctx, k) },
		arrPopWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonArrPopWithPath(c, ctx, k, p) },
		arrPopWithPathIndex: func(ctx context.Context, k, p string, i int64) (any, error) {
			return glidejson.JsonArrPopWithPathAndIndex(c, ctx, k, p, i)
		},
		arrTrim: func(ctx context.Context, k, p string, s, e int64) (any, error) {
			return glidejson.JsonArrTrim(c, ctx, k, p, s, e)
		},
		numIncrBy: func(ctx context.Context, k, p string, n float64) (string, error) {
			return glidejson.JsonNumIncrBy(c, ctx, k, p, n)
		},
		numMultBy: func(ctx context.Context, k, p string, n float64) (string, error) {
			return glidejson.JsonNumMultBy(c, ctx, k, p, n)
		},
		toggle:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonToggle(c, ctx, k) },
		toggleWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonToggleWithPath(c, ctx, k, p) },
		strAppend:      func(ctx context.Context, k, v string) (any, error) { return glidejson.JsonStrAppend(c, ctx, k, v) },
		strAppendWithPath: func(ctx context.Context, k, p, v string) (any, error) {
			return glidejson.JsonStrAppendWithPath(c, ctx, k, p, v)
		},
		strLen:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonStrLen(c, ctx, k) },
		strLenWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonStrLenWithPath(c, ctx, k, p) },
		objLen:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonObjLen(c, ctx, k) },
		objLenWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonObjLenWithPath(c, ctx, k, p) },
		objKeys:        func(ctx context.Context, k string) (any, error) { return glidejson.JsonObjKeys(c, ctx, k) },
		objKeysWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.JsonObjKeysWithPath(c, ctx, k, p)
		},
		resp:         func(ctx context.Context, k string) (any, error) { return glidejson.JsonResp(c, ctx, k) },
		respWithPath: func(ctx context.Context, k, p string) (any, error) { return glidejson.JsonRespWithPath(c, ctx, k, p) },
		debugMemory:  func(ctx context.Context, k string) (any, error) { return glidejson.JsonDebugMemory(c, ctx, k) },
		debugMemoryWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.JsonDebugMemoryWithPath(c, ctx, k, p)
		},
		debugFields: func(ctx context.Context, k string) (any, error) { return glidejson.JsonDebugFields(c, ctx, k) },
		debugFieldsWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.JsonDebugFieldsWithPath(c, ctx, k, p)
		},
	}
}

func (suite *GlideTestSuite) clusterJsonOps() jsonOps {
	c := suite.defaultClusterClient()
	return jsonOps{
		set: func(ctx context.Context, k, p, v string) (string, error) {
			return glidejson.ClusterJsonSet(c, ctx, k, p, v)
		},
		setWithCondition: func(ctx context.Context, k, p, v string, cond constants.ConditionalSet) (string, error) {
			return glidejson.ClusterJsonSetWithCondition(c, ctx, k, p, v, cond)
		},
		get: func(ctx context.Context, k string) (string, error) { return glidejson.ClusterJsonGet(c, ctx, k) },
		getWithPaths: func(ctx context.Context, k string, ps []string) (string, error) {
			return glidejson.ClusterJsonGetWithPaths(c, ctx, k, ps)
		},
		getWithOptions: func(ctx context.Context, k string, ps []string, o *options.JsonGetOptions) (string, error) {
			return glidejson.ClusterJsonGetWithOptions(c, ctx, k, ps, o)
		},
		del: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonDel(c, ctx, k) },
		delWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonDelWithPath(c, ctx, k, p)
		},
		forget: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonForget(c, ctx, k) },
		forgetWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonForgetWithPath(c, ctx, k, p)
		},
		clear: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonClear(c, ctx, k) },
		clearWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonClearWithPath(c, ctx, k, p)
		},
		mget: func(ctx context.Context, ks []string, p string) (any, error) {
			return glidejson.ClusterJsonMGet(c, ctx, ks, p)
		},
		jsonType: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonType(c, ctx, k) },
		jsonTypeWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonTypeWithPath(c, ctx, k, p)
		},
		arrAppend: func(ctx context.Context, k, p string, vs []string) (any, error) {
			return glidejson.ClusterJsonArrAppend(c, ctx, k, p, vs)
		},
		arrInsert: func(ctx context.Context, k, p string, i int64, vs []string) (any, error) {
			return glidejson.ClusterJsonArrInsert(c, ctx, k, p, i, vs)
		},
		arrIndex: func(ctx context.Context, k, p, s string) (any, error) {
			return glidejson.ClusterJsonArrIndex(c, ctx, k, p, s)
		},
		arrIndexWithOptions: func(ctx context.Context, k, p, s string, o *options.JsonArrIndexOptions) (any, error) {
			return glidejson.ClusterJsonArrIndexWithOptions(c, ctx, k, p, s, o)
		},
		arrLen: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonArrLen(c, ctx, k) },
		arrLenWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonArrLenWithPath(c, ctx, k, p)
		},
		arrPop: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonArrPop(c, ctx, k) },
		arrPopWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonArrPopWithPath(c, ctx, k, p)
		},
		arrPopWithPathIndex: func(ctx context.Context, k, p string, i int64) (any, error) {
			return glidejson.ClusterJsonArrPopWithPathAndIndex(c, ctx, k, p, i)
		},
		arrTrim: func(ctx context.Context, k, p string, s, e int64) (any, error) {
			return glidejson.ClusterJsonArrTrim(c, ctx, k, p, s, e)
		},
		numIncrBy: func(ctx context.Context, k, p string, n float64) (string, error) {
			return glidejson.ClusterJsonNumIncrBy(c, ctx, k, p, n)
		},
		numMultBy: func(ctx context.Context, k, p string, n float64) (string, error) {
			return glidejson.ClusterJsonNumMultBy(c, ctx, k, p, n)
		},
		toggle: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonToggle(c, ctx, k) },
		toggleWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonToggleWithPath(c, ctx, k, p)
		},
		strAppend: func(ctx context.Context, k, v string) (any, error) {
			return glidejson.ClusterJsonStrAppend(c, ctx, k, v)
		},
		strAppendWithPath: func(ctx context.Context, k, p, v string) (any, error) {
			return glidejson.ClusterJsonStrAppendWithPath(c, ctx, k, p, v)
		},
		strLen: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonStrLen(c, ctx, k) },
		strLenWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonStrLenWithPath(c, ctx, k, p)
		},
		objLen: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonObjLen(c, ctx, k) },
		objLenWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonObjLenWithPath(c, ctx, k, p)
		},
		objKeys: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonObjKeys(c, ctx, k) },
		objKeysWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonObjKeysWithPath(c, ctx, k, p)
		},
		resp: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonResp(c, ctx, k) },
		respWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonRespWithPath(c, ctx, k, p)
		},
		debugMemory: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonDebugMemory(c, ctx, k) },
		debugMemoryWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonDebugMemoryWithPath(c, ctx, k, p)
		},
		debugFields: func(ctx context.Context, k string) (any, error) { return glidejson.ClusterJsonDebugFields(c, ctx, k) },
		debugFieldsWithPath: func(ctx context.Context, k, p string) (any, error) {
			return glidejson.ClusterJsonDebugFieldsWithPath(c, ctx, k, p)
		},
	}
}

// --- Verify helpers ---

func (suite *GlideTestSuite) verifyJsonSetAndGet(ops jsonOps) {
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

func (suite *GlideTestSuite) verifyJsonSetWithCondition(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	result, err := ops.setWithCondition(ctx, key, jsonTestPath, `{"a": 1.0}`, constants.OnlyIfDoesNotExist)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	result, err = ops.setWithCondition(ctx, key, jsonTestPath, `{"a": 2.0}`, constants.OnlyIfDoesNotExist)
	assert.NoError(t, err)
	assert.Equal(t, "", result)

	result, err = ops.setWithCondition(ctx, key, jsonTestPath, `{"a": 3.0}`, constants.OnlyIfExists)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) verifyJsonGetWithOptions(ops jsonOps) {
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

func (suite *GlideTestSuite) verifyJsonDel(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "nested": {"a": 2, "b": 3}}`)
	assert.NoError(t, err)

	result, err := ops.delWithPath(ctx, key, "$..a")
	assert.NoError(t, err)
	assert.Equal(t, int64(2), result)

	_, err = ops.set(ctx, key, "$", `{"x": 1}`)
	assert.NoError(t, err)
	result, err = ops.del(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, int64(1), result)

	result, err = ops.del(ctx, "non_existing_key")
	assert.NoError(t, err)
	assert.Equal(t, int64(0), result)
}

func (suite *GlideTestSuite) verifyJsonForget(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "nested": {"a": 2, "b": 3}}`)
	assert.NoError(t, err)

	result, err := ops.forgetWithPath(ctx, key, "$..a")
	assert.NoError(t, err)
	assert.Equal(t, int64(2), result)

	result, err = ops.forget(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, int64(1), result)
}

func (suite *GlideTestSuite) verifyJsonClear(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": [1, 2, 3]}`)
	assert.NoError(t, err)

	result, err := ops.clearWithPath(ctx, key, "$.*")
	assert.NoError(t, err)
	assert.Equal(t, int64(2), result)

	result, err = ops.clearWithPath(ctx, key, "$.*")
	assert.NoError(t, err)
	assert.Equal(t, int64(0), result)

	_, err = ops.set(ctx, key, "$", `{"a": 1}`)
	assert.NoError(t, err)
	result, err = ops.clear(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, int64(1), result)
}

func (suite *GlideTestSuite) verifyJsonMGet(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key1 := jsonTestKeyPrefix + t.Name() + "-1"
	key2 := jsonTestKeyPrefix + t.Name() + "-2"

	_, err := ops.set(ctx, key1, "$", `{"a": 1}`)
	assert.NoError(t, err)
	_, err = ops.set(ctx, key2, "$", `{"a": 2}`)
	assert.NoError(t, err)

	result, err := ops.mget(ctx, []string{key1, key2, "non_existing"}, "$.a")
	assert.NoError(t, err)
	arr, ok := result.([]any)
	assert.True(t, ok)
	assert.Len(t, arr, 3)
	assert.Equal(t, "[1]", arr[0])
	assert.Equal(t, "[2]", arr[1])
	assert.Nil(t, arr[2])
}

func (suite *GlideTestSuite) verifyJsonType(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": "hello", "c": [1, 2]}`)
	assert.NoError(t, err)

	result, err := ops.jsonTypeWithPath(ctx, key, "$.a")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	result, err = ops.jsonTypeWithPath(ctx, key, "$.c")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	_, err = ops.jsonTypeWithPath(ctx, "non_existing_key", "$")
	assert.NoError(t, err)
}

func (suite *GlideTestSuite) verifyJsonArrAppend(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": [1, 2]}`)
	assert.NoError(t, err)

	result, err := ops.arrAppend(ctx, key, "$.a", []string{"3", "4"})
	assert.NoError(t, err)
	assert.NotNil(t, result)

	getResult, err := ops.get(ctx, key)
	assert.NoError(t, err)
	assert.Contains(t, getResult, "3")
	assert.Contains(t, getResult, "4")
}

func (suite *GlideTestSuite) verifyJsonArrInsert(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": [1, 2, 3]}`)
	assert.NoError(t, err)

	result, err := ops.arrInsert(ctx, key, "$.a", 1, []string{`"x"`})
	assert.NoError(t, err)
	assert.NotNil(t, result)

	getResult, err := ops.get(ctx, key)
	assert.NoError(t, err)
	assert.Contains(t, getResult, `"x"`)
}

func (suite *GlideTestSuite) verifyJsonArrIndex(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": [1, 2, 3, 2]}`)
	assert.NoError(t, err)

	result, err := ops.arrIndex(ctx, key, "$.a", "2")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	result, err = ops.arrIndex(ctx, key, "$.a", "99")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	arrOpts := options.NewJsonArrIndexOptions(2)
	result, err = ops.arrIndexWithOptions(ctx, key, "$.a", "2", arrOpts)
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) verifyJsonArrLen(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `[1, 2, 3, 4, 5]`)
	assert.NoError(t, err)

	result, err := ops.arrLen(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, int64(5), result)

	_, err = ops.set(ctx, key, "$", `{"a": [1, 2, 3]}`)
	assert.NoError(t, err)
	result, err = ops.arrLenWithPath(ctx, key, "$.a")
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) verifyJsonArrPop(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `[1, 2, 3, "last"]`)
	assert.NoError(t, err)

	result, err := ops.arrPop(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, `"last"`, result)

	_, err = ops.set(ctx, key, "$", `{"a": [10, 20, 30]}`)
	assert.NoError(t, err)
	result, err = ops.arrPopWithPath(ctx, key, "$.a")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	_, err = ops.set(ctx, key, "$", `{"a": [10, 20, 30]}`)
	assert.NoError(t, err)
	result, err = ops.arrPopWithPathIndex(ctx, key, "$.a", 0)
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) verifyJsonArrTrim(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": [1, 2, 3, 4, 5]}`)
	assert.NoError(t, err)

	result, err := ops.arrTrim(ctx, key, "$.a", 1, 3)
	assert.NoError(t, err)
	assert.NotNil(t, result)

	getResult, err := ops.get(ctx, key)
	assert.NoError(t, err)
	assert.Contains(t, getResult, "2")
	assert.Contains(t, getResult, "3")
	assert.Contains(t, getResult, "4")
}

// --- Standalone tests ---

func (suite *GlideTestSuite) TestModuleJsonSetAndGet_Standalone() {
	suite.verifyJsonSetAndGet(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonSetWithCondition_Standalone() {
	suite.verifyJsonSetWithCondition(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonGetWithOptions_Standalone() {
	suite.verifyJsonGetWithOptions(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonDel_Standalone() {
	suite.verifyJsonDel(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonForget_Standalone() {
	suite.verifyJsonForget(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonClear_Standalone() {
	suite.verifyJsonClear(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonMGet_Standalone() {
	suite.verifyJsonMGet(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonType_Standalone() {
	suite.verifyJsonType(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrAppend_Standalone() {
	suite.verifyJsonArrAppend(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrInsert_Standalone() {
	suite.verifyJsonArrInsert(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrIndex_Standalone() {
	suite.verifyJsonArrIndex(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrLen_Standalone() {
	suite.verifyJsonArrLen(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrPop_Standalone() {
	suite.verifyJsonArrPop(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrTrim_Standalone() {
	suite.verifyJsonArrTrim(suite.standaloneJsonOps())
}

// --- Cluster tests ---

func (suite *GlideTestSuite) TestModuleJsonSetAndGet_Cluster() {
	suite.verifyJsonSetAndGet(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonSetWithCondition_Cluster() {
	suite.verifyJsonSetWithCondition(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonGetWithOptions_Cluster() {
	suite.verifyJsonGetWithOptions(suite.clusterJsonOps())
}
func (suite *GlideTestSuite) TestModuleJsonDel_Cluster() { suite.verifyJsonDel(suite.clusterJsonOps()) }
func (suite *GlideTestSuite) TestModuleJsonForget_Cluster() {
	suite.verifyJsonForget(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonClear_Cluster() {
	suite.verifyJsonClear(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonMGet_Cluster() {
	suite.verifyJsonMGet(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonType_Cluster() {
	suite.verifyJsonType(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrAppend_Cluster() {
	suite.verifyJsonArrAppend(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrInsert_Cluster() {
	suite.verifyJsonArrInsert(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrIndex_Cluster() {
	suite.verifyJsonArrIndex(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrLen_Cluster() {
	suite.verifyJsonArrLen(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrPop_Cluster() {
	suite.verifyJsonArrPop(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonArrTrim_Cluster() {
	suite.verifyJsonArrTrim(suite.clusterJsonOps())
}

// --- Verify helpers for remaining commands ---

func (suite *GlideTestSuite) verifyJsonNumIncrBy(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": 2.5}`)
	assert.NoError(t, err)

	result, err := ops.numIncrBy(ctx, key, "$.a", 10)
	assert.NoError(t, err)
	assert.Equal(t, "[11]", result)

	result, err = ops.numIncrBy(ctx, key, "$.b", 0.5)
	assert.NoError(t, err)
	assert.Equal(t, "[3]", result)
}

func (suite *GlideTestSuite) verifyJsonNumMultBy(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 2, "b": 3}`)
	assert.NoError(t, err)

	result, err := ops.numMultBy(ctx, key, "$.a", 3)
	assert.NoError(t, err)
	assert.Equal(t, "[6]", result)
}

func (suite *GlideTestSuite) verifyJsonToggle(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	// Toggle with path
	_, err := ops.set(ctx, key, "$", `{"bool": true, "nested": {"bool": false}}`)
	assert.NoError(t, err)

	result, err := ops.toggleWithPath(ctx, key, "$..bool")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	getResult, err := ops.get(ctx, key)
	assert.NoError(t, err)
	assert.Contains(t, getResult, "false")
	assert.Contains(t, getResult, "true")

	// Toggle without path (root boolean)
	_, err = ops.set(ctx, key, "$", `true`)
	assert.NoError(t, err)

	result, err = ops.toggle(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, false, result)
}

func (suite *GlideTestSuite) verifyJsonStrAppend(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	// StrAppend with path
	_, err := ops.set(ctx, key, "$", `{"a": "foo", "b": "bar"}`)
	assert.NoError(t, err)

	result, err := ops.strAppendWithPath(ctx, key, "$.a", `"baz"`)
	assert.NoError(t, err)
	assert.NotNil(t, result)

	getResult, err := ops.get(ctx, key)
	assert.NoError(t, err)
	assert.Contains(t, getResult, "foobaz")

	// StrAppend without path (root string)
	_, err = ops.set(ctx, key, "$", `"hello"`)
	assert.NoError(t, err)

	result, err = ops.strAppend(ctx, key, `" world"`)
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) verifyJsonStrLen(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": "hello", "b": "world!"}`)
	assert.NoError(t, err)

	result, err := ops.strLenWithPath(ctx, key, "$.a")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	// Non-existing key
	_, err = ops.strLen(ctx, "non_existing_key")
	assert.NoError(t, err)
}

func (suite *GlideTestSuite) verifyJsonObjLen(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": {"x": 1, "y": 2}}`)
	assert.NoError(t, err)

	result, err := ops.objLen(ctx, key)
	assert.NoError(t, err)
	assert.Equal(t, int64(2), result)

	result, err = ops.objLenWithPath(ctx, key, "$.b")
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) verifyJsonObjKeys(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": 2}`)
	assert.NoError(t, err)

	result, err := ops.objKeys(ctx, key)
	assert.NoError(t, err)
	assert.NotNil(t, result)

	result, err = ops.objKeysWithPath(ctx, key, "$")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	// Non-existing key
	result, err = ops.objKeys(ctx, "non_existing_key")
	assert.NoError(t, err)
	assert.Nil(t, result)
}

func (suite *GlideTestSuite) verifyJsonResp(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": [1, 2]}`)
	assert.NoError(t, err)

	result, err := ops.resp(ctx, key)
	assert.NoError(t, err)
	assert.NotNil(t, result)

	result, err = ops.respWithPath(ctx, key, "$.b")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	// Non-existing key
	result, err = ops.resp(ctx, "non_existing_key")
	assert.NoError(t, err)
	assert.Nil(t, result)
}

func (suite *GlideTestSuite) verifyJsonDebugMemory(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": "hello"}`)
	assert.NoError(t, err)

	result, err := ops.debugMemory(ctx, key)
	assert.NoError(t, err)
	assert.NotNil(t, result)

	result, err = ops.debugMemoryWithPath(ctx, key, "$.a")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	// Non-existing key
	result, err = ops.debugMemory(ctx, "non_existing_key")
	assert.NoError(t, err)
	assert.Nil(t, result)
}

func (suite *GlideTestSuite) verifyJsonDebugFields(ops jsonOps) {
	t := suite.T()
	ctx := context.Background()
	key := jsonTestKeyPrefix + t.Name()

	_, err := ops.set(ctx, key, "$", `{"a": 1, "b": [1, 2, 3]}`)
	assert.NoError(t, err)

	result, err := ops.debugFields(ctx, key)
	assert.NoError(t, err)
	assert.NotNil(t, result)

	result, err = ops.debugFieldsWithPath(ctx, key, "$.b")
	assert.NoError(t, err)
	assert.NotNil(t, result)

	// Non-existing key
	result, err = ops.debugFields(ctx, "non_existing_key")
	assert.NoError(t, err)
	assert.Nil(t, result)
}

// --- Standalone tests for remaining commands ---

func (suite *GlideTestSuite) TestModuleJsonNumIncrBy_Standalone() {
	suite.verifyJsonNumIncrBy(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonNumMultBy_Standalone() {
	suite.verifyJsonNumMultBy(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonToggle_Standalone() {
	suite.verifyJsonToggle(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonStrAppend_Standalone() {
	suite.verifyJsonStrAppend(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonStrLen_Standalone() {
	suite.verifyJsonStrLen(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonObjLen_Standalone() {
	suite.verifyJsonObjLen(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonObjKeys_Standalone() {
	suite.verifyJsonObjKeys(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonResp_Standalone() {
	suite.verifyJsonResp(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonDebugMemory_Standalone() {
	suite.verifyJsonDebugMemory(suite.standaloneJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonDebugFields_Standalone() {
	suite.verifyJsonDebugFields(suite.standaloneJsonOps())
}

// --- Cluster tests for remaining commands ---

func (suite *GlideTestSuite) TestModuleJsonNumIncrBy_Cluster() {
	suite.verifyJsonNumIncrBy(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonNumMultBy_Cluster() {
	suite.verifyJsonNumMultBy(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonToggle_Cluster() {
	suite.verifyJsonToggle(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonStrAppend_Cluster() {
	suite.verifyJsonStrAppend(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonStrLen_Cluster() {
	suite.verifyJsonStrLen(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonObjLen_Cluster() {
	suite.verifyJsonObjLen(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonObjKeys_Cluster() {
	suite.verifyJsonObjKeys(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonResp_Cluster() {
	suite.verifyJsonResp(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonDebugMemory_Cluster() {
	suite.verifyJsonDebugMemory(suite.clusterJsonOps())
}

func (suite *GlideTestSuite) TestModuleJsonDebugFields_Cluster() {
	suite.verifyJsonDebugFields(suite.clusterJsonOps())
}
