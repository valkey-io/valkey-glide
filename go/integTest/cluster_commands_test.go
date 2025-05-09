// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"math/rand"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/errors"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func (suite *GlideTestSuite) TestClusterCustomCommandInfo() {
	client := suite.defaultClusterClient()
	result, err := client.CustomCommand([]string{"INFO"})

	assert.Nil(suite.T(), err)
	// INFO is routed to all primary nodes by default
	for _, value := range result.MultiValue() {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandEcho() {
	client := suite.defaultClusterClient()
	result, err := client.CustomCommand([]string{"ECHO", "GO GLIDE GO"})

	assert.Nil(suite.T(), err)
	// ECHO is routed to a single random node
	assert.Equal(suite.T(), "GO GLIDE GO", result.SingleValue().(string))
}

func (suite *GlideTestSuite) TestClusterCustomCommandDbSize() {
	client := suite.defaultClusterClient()
	// DBSIZE result is always a single number regardless of route
	result, err := client.CustomCommand([]string{"dbsize"})
	assert.NoError(suite.T(), err)
	assert.GreaterOrEqual(suite.T(), result.SingleValue().(int64), int64(0))

	result, err = client.CustomCommandWithRoute([]string{"dbsize"}, config.AllPrimaries)
	assert.NoError(suite.T(), err)
	assert.GreaterOrEqual(suite.T(), result.SingleValue().(int64), int64(0))

	result, err = client.CustomCommandWithRoute([]string{"dbsize"}, config.RandomRoute)
	assert.NoError(suite.T(), err)
	assert.GreaterOrEqual(suite.T(), result.SingleValue().(int64), int64(0))
}

func (suite *GlideTestSuite) TestClusterCustomCommandConfigGet() {
	client := suite.defaultClusterClient()

	// CONFIG GET returns a map, but with a single node route it is handled as a single value
	result, err := client.CustomCommandWithRoute([]string{"CONFIG", "GET", "*file"}, config.RandomRoute)
	assert.NoError(suite.T(), err)
	assert.Greater(suite.T(), len(result.SingleValue().(map[string]any)), 0)

	result, err = client.CustomCommandWithRoute([]string{"CONFIG", "GET", "*file"}, config.AllPrimaries)
	assert.NoError(suite.T(), err)
	assert.Greater(suite.T(), len(result.MultiValue()), 0)
	for _, val := range result.MultiValue() {
		assert.Greater(suite.T(), len(val.(map[string]any)), 0)
	}
}

func (suite *GlideTestSuite) TestInfoCluster() {
	DEFAULT_INFO_SECTIONS := []string{
		"Server",
		"Clients",
		"Memory",
		"Persistence",
		"Stats",
		"Replication",
		"CPU",
		"Modules",
		"Errorstats",
		"Cluster",
		"Keyspace",
	}

	client := suite.defaultClusterClient()
	t := suite.T()

	// info without options
	data, err := client.Info()
	assert.NoError(t, err)
	for _, info := range data {
		for _, section := range DEFAULT_INFO_SECTIONS {
			assert.Contains(t, info, "# "+section, "Section "+section+" is missing")
		}
	}

	// info with option or with multiple options without route
	sections := []options.Section{options.Cpu}
	if suite.serverVersion >= "7.0.0" {
		sections = append(sections, options.Memory)
	}
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: nil,
	}
	response, err := client.InfoWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
	for _, info := range response.MultiValue() {
		for _, section := range sections {
			assert.Contains(t, strings.ToLower(info), strings.ToLower("# "+string(section)), "Section "+section+" is missing")
		}
	}

	// same sections with random route
	opts = options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: &options.RouteOption{Route: config.RandomRoute},
	}
	response, err = client.InfoWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
	for _, section := range sections {
		assert.Contains(
			t,
			strings.ToLower(response.SingleValue()),
			strings.ToLower("# "+string(section)),
			"Section "+section+" is missing",
		)
	}

	// default sections, multi node route
	opts = options.ClusterInfoOptions{
		InfoOptions: nil,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	response, err = client.InfoWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
	for _, info := range response.MultiValue() {
		for _, section := range DEFAULT_INFO_SECTIONS {
			assert.Contains(t, info, "# "+section, "Section "+section+" is missing")
		}
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Info() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.AllPrimaries)
	result, err := client.CustomCommandWithRoute([]string{"INFO"}, route)
	assert.Nil(suite.T(), err)
	assert.True(suite.T(), result.IsMultiValue())
	multiValue := result.MultiValue()
	for _, value := range multiValue {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Echo() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.RandomRoute)
	result, err := client.CustomCommandWithRoute([]string{"ECHO", "GO GLIDE GO"}, route)
	assert.Nil(suite.T(), err)
	assert.True(suite.T(), result.IsSingleValue())
	assert.Equal(suite.T(), "GO GLIDE GO", result.SingleValue().(string))
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.NewByAddressRoute("invalidHost", 9999)
	result, err := client.CustomCommandWithRoute([]string{"PING"}, invalidRoute)
	assert.NotNil(suite.T(), err)
	assert.True(suite.T(), result.IsEmpty())
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_AllNodes() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.AllNodes)
	result, err := client.CustomCommandWithRoute([]string{"PING"}, route)
	assert.Nil(suite.T(), err)
	assert.True(suite.T(), result.IsSingleValue())
	assert.Equal(suite.T(), "PONG", result.SingleValue())
}

func (suite *GlideTestSuite) TestPingWithOptions_NoRoute() {
	client := suite.defaultClusterClient()
	options := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "hello",
		},
		RouteOption: nil,
	}
	result, err := client.PingWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestPingWithOptions_WithRoute() {
	client := suite.defaultClusterClient()
	options := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "hello",
		},
		RouteOption: &options.RouteOption{Route: config.AllNodes},
	}
	result, err := client.PingWithOptions(options)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestPingWithOptions_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.Route(config.NewByAddressRoute("invalidHost", 9999))
	options := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "hello",
		},
		RouteOption: &options.RouteOption{Route: invalidRoute},
	}
	result, err := client.PingWithOptions(options)
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestTimeWithoutRoute() {
	client := suite.defaultClusterClient()
	options := options.RouteOption{Route: nil}
	result, err := client.TimeWithOptions(options)
	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.False(suite.T(), result.IsEmpty())
	assert.True(suite.T(), result.IsSingleValue())
	assert.NotEmpty(suite.T(), result.SingleValue())
	assert.IsType(suite.T(), "", result.SingleValue()[0])
	assert.Equal(suite.T(), 2, len(result.SingleValue()))
}

func (suite *GlideTestSuite) TestTimeWithAllNodesRoute() {
	client := suite.defaultClusterClient()
	options := options.RouteOption{Route: config.AllNodes}
	result, err := client.TimeWithOptions(options)
	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.False(suite.T(), result.IsEmpty())
	assert.True(suite.T(), result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.Greater(suite.T(), len(multiValue), 1)

	for nodeName, timeStrings := range multiValue {
		assert.NotEmpty(suite.T(), timeStrings, "Node %s should have time values", nodeName)
		for _, timeStr := range timeStrings {
			assert.IsType(suite.T(), "", timeStr)
		}
	}
}

func (suite *GlideTestSuite) TestTimeWithRandomRoute() {
	client := suite.defaultClusterClient()
	route := config.Route(config.RandomRoute)
	options := options.RouteOption{Route: route}
	result, err := client.TimeWithOptions(options)
	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.False(suite.T(), result.IsEmpty())
	assert.True(suite.T(), result.IsSingleValue())
	assert.NotEmpty(suite.T(), result.SingleValue())
	assert.IsType(suite.T(), "", result.SingleValue()[0])
	assert.Equal(suite.T(), 2, len(result.SingleValue()))
}

func (suite *GlideTestSuite) TestTimeWithInvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.Route(config.NewByAddressRoute("invalidHost", 9999))
	options := options.RouteOption{Route: invalidRoute}
	result, err := client.TimeWithOptions(options)
	assert.NotNil(suite.T(), err)
	assert.True(suite.T(), result.IsEmpty())
	assert.Empty(suite.T(), result.SingleValue())
}

func (suite *GlideTestSuite) TestDBSizeRandomRoute() {
	client := suite.defaultClusterClient()
	route := config.Route(config.RandomRoute)
	options := options.RouteOption{Route: route}
	result, err := client.DBSizeWithOptions(options)
	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.GreaterOrEqual(suite.T(), result, int64(0))
}

func (suite *GlideTestSuite) TestEchoCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// echo with option or with multiple options without route
	opts := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "hello",
		},
		RouteOption: &options.RouteOption{Route: nil},
	}
	response, err := client.EchoWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// same sections with random route
	route := options.RouteOption{Route: *config.RandomRoute.ToPtr()}
	opts = options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "hello",
		},
		RouteOption: &route,
	}
	response, err = client.EchoWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// default sections, multi node route
	route = options.RouteOption{Route: *config.AllPrimaries.ToPtr()}
	opts = options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "hello",
		},
		RouteOption: &route,
	}
	response, err = client.EchoWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
	for _, messages := range response.MultiValue() {
		assert.Contains(t, strings.ToLower(messages), strings.ToLower("hello"))
	}
}

func (suite *GlideTestSuite) TestBasicClusterScan() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	// Iterate over all keys in the cluster
	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err = client.MSet(keysToSet)
	assert.NoError(t, err)

	cursor := *options.NewClusterScanCursor()
	allKeys := make([]string, 0, len(keysToSet))
	var keys []string

	for !cursor.HasFinished() {
		cursor, keys, err = client.Scan(cursor)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, []string{"key1", "key2", "key3"})

	// Ensure clean start
	_, err = client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	expectedKeys := make([]string, 0, 100)
	// Test bigger example
	for i := 0; i < 100; i++ {
		key := uuid.NewString()

		expectedKeys = append(expectedKeys, key)

		_, err := client.Set(key, "value")
		assert.NoError(t, err)
	}

	cursor = *options.NewClusterScanCursor()
	allKeys = make([]string, 0, 100)

	for !cursor.HasFinished() {
		cursor, keys, err = client.Scan(cursor)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, expectedKeys)
}

func (suite *GlideTestSuite) TestBasicClusterScanWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	// Iterate over all keys in the cluster
	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err = client.MSet(keysToSet)
	assert.NoError(t, err)

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetCount(10)
	allKeys := []string{}
	var keys []string

	for !cursor.HasFinished() {
		cursor, keys, err = client.ScanWithOptions(cursor, *opts)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, []string{"key1", "key2", "key3"})

	// Iterate over keys matching a pattern
	keysToSet = map[string]string{
		"key1":          "value1",
		"key2":          "value2",
		"notMykey":      "value3",
		"somethingElse": "value4",
	}

	_, err = client.MSet(keysToSet)
	assert.NoError(t, err)

	cursor = *options.NewClusterScanCursor()
	opts = options.NewClusterScanOptions().SetCount(10).SetMatch("*key*")
	matchedKeys := []string{}

	for !cursor.HasFinished() {
		cursor, keys, err = client.ScanWithOptions(cursor, *opts)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		matchedKeys = append(matchedKeys, keys...)
	}

	assert.ElementsMatch(t, matchedKeys, []string{"key1", "key2", "key3", "notMykey"})
	assert.NotContains(t, matchedKeys, "somethingElse")

	// Iterate over keys of a specific type
	keysToSet = map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}
	_, err = client.MSet(keysToSet)
	assert.NoError(t, err)

	_, err = client.SAdd("thisIsASet", []string{"someValue"})
	assert.NoError(t, err)

	cursor = *options.NewClusterScanCursor()
	opts = options.NewClusterScanOptions().SetType(options.ObjectTypeSet)
	matchedTypeKeys := []string{}

	for !cursor.HasFinished() {
		cursor, keys, err = client.ScanWithOptions(cursor, *opts)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		matchedTypeKeys = append(matchedTypeKeys, keys...)
	}

	assert.ElementsMatch(t, matchedTypeKeys, []string{"thisIsASet"})
	assert.NotContains(t, matchedTypeKeys, "key1")
	assert.NotContains(t, matchedTypeKeys, "key2")
	assert.NotContains(t, matchedTypeKeys, "key3")
}

func (suite *GlideTestSuite) TestBasicClusterScanWithNonUTF8Pattern() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	// Iterate over all keys in the cluster
	keysToSet := map[string]string{
		"key\xc0\xc1-1": "value1",
		"key-2":         "value2",
		"key\xf9\xc1-3": "value3",
		"someKey":       "value4",
		"\xc0\xc1key-5": "value5",
	}

	_, err = client.MSet(keysToSet)
	assert.NoError(t, err)

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetMatch("key\xc0\xc1-*")
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(cursor, *opts)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, []string{"key\xc0\xc1-1"})
}

func (suite *GlideTestSuite) TestClusterScanWithObjectTypeAndPattern() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	expectedKeys := make([]string, 0, 100)
	unexpectedTypeKeys := make([]string, 0, 100)
	unexpectedPatternKeys := make([]string, 0, 100)

	for i := 0; i < 100; i++ {
		key := "key-" + uuid.NewString()
		unexpectedTypeKey := "key-" + uuid.NewString()
		unexpectedPatternKey := uuid.NewString()

		expectedKeys = append(expectedKeys, key)
		unexpectedTypeKeys = append(unexpectedTypeKeys, unexpectedTypeKey)
		unexpectedPatternKeys = append(unexpectedPatternKeys, unexpectedPatternKey)

		_, err := client.Set(key, "value")
		assert.NoError(t, err)

		_, err = client.SAdd(unexpectedTypeKey, []string{"value"})
		assert.NoError(t, err)

		_, err = client.Set(unexpectedPatternKey, "value")
		assert.NoError(t, err)
	}

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetMatch("key-*").SetType(options.ObjectTypeString)
	allKeys := make([]string, 0, 100)

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(cursor, *opts)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, expectedKeys)
	for _, elem := range unexpectedTypeKeys {
		assert.NotContains(t, allKeys, elem)
	}
	for _, elem := range unexpectedPatternKeys {
		assert.NotContains(t, allKeys, elem)
	}
}

func (suite *GlideTestSuite) TestClusterScanWithCount() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	expectedKeys := make([]string, 0, 100)

	for i := 0; i < 100; i++ {
		key := "key-" + uuid.NewString()
		expectedKeys = append(expectedKeys, key)
		_, err := client.Set(key, "value")
		assert.NoError(t, err)
	}

	cursor := *options.NewClusterScanCursor()
	allKeys := make([]string, 0, 100)
	successfulScans := 0

	for !cursor.HasFinished() {
		keysOf1 := []string{}
		keysOf100 := []string{}

		var keys []string
		cursor, keys, err = client.ScanWithOptions(cursor, *options.NewClusterScanOptions().SetCount(1))
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		keysOf1 = append(keysOf1, keys...)
		allKeys = append(allKeys, keysOf1...)

		if cursor.HasFinished() {
			break
		}

		cursor, keys, err = client.ScanWithOptions(cursor, *options.NewClusterScanOptions().SetCount(100))
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}
		keysOf100 = append(keysOf100, keys...)
		allKeys = append(allKeys, keysOf100...)

		if len(keysOf1) < len(keysOf100) {
			successfulScans += 1
		}
	}

	assert.ElementsMatch(t, allKeys, expectedKeys)
	assert.Greater(t, successfulScans, 0)
}

func (suite *GlideTestSuite) TestClusterScanWithMatch() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	expectedKeys := []string{}
	unexpectedKeys := []string{}

	for i := 0; i < 10; i++ {
		key := "key-" + uuid.NewString()
		unexpectedKey := uuid.NewString()

		expectedKeys = append(expectedKeys, key)
		unexpectedKeys = append(unexpectedKeys, unexpectedKey)

		_, err := client.Set(key, "value")
		assert.NoError(t, err)

		_, err = client.Set(unexpectedKey, "value")
		assert.NoError(t, err)
	}

	cursor := *options.NewClusterScanCursor()
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(cursor, *options.NewClusterScanOptions().SetMatch("key-*"))
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}

		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, expectedKeys)
	for _, elem := range unexpectedKeys {
		assert.NotContains(t, allKeys, elem)
	}
}

func (suite *GlideTestSuite) TestClusterScanWithDifferentTypes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.CustomCommand([]string{"FLUSHALL"})
	assert.NoError(t, err)

	stringKeys := []string{}
	setKeys := []string{}
	hashKeys := []string{}
	listKeys := []string{}
	zsetKeys := []string{}
	streamKeys := []string{}

	for i := 0; i < 10; i++ {
		key := "key-" + uuid.NewString()
		stringKeys = append(stringKeys, key)

		setKey := "{setKey}-" + uuid.NewString()
		setKeys = append(setKeys, setKey)

		hashKey := "{hashKey}-" + uuid.NewString()
		hashKeys = append(hashKeys, hashKey)

		listKey := "{listKey}-" + uuid.NewString()
		listKeys = append(listKeys, listKey)

		zsetKey := "{zsetKey}-" + uuid.NewString()
		zsetKeys = append(zsetKeys, zsetKey)

		streamKey := "{streamKey}-" + uuid.NewString()
		streamKeys = append(streamKeys, streamKey)

		_, err := client.Set(key, "value")
		assert.NoError(t, err)

		_, err = client.SAdd(setKey, []string{"value"})
		assert.NoError(t, err)

		_, err = client.HSet(hashKey, map[string]string{"field": "value"})
		assert.NoError(t, err)

		_, err = client.LPush(listKey, []string{"value"})
		assert.NoError(t, err)

		_, err = client.ZAdd(zsetKey, map[string]float64{"value": 1})
		assert.NoError(t, err)

		_, err = client.XAdd(streamKey, [][]string{{"field", "value"}})
		assert.NoError(t, err)
	}

	cursor := *options.NewClusterScanCursor()
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(
			cursor,
			*options.NewClusterScanOptions().SetType(options.ObjectTypeList),
		)
		if err != nil {
			assert.NoError(t, err) // Use this to print error statement
			break                  // prevent infinite loop
		}

		allKeys = append(allKeys, keys...)
	}

	assert.ElementsMatch(t, allKeys, listKeys)
	for _, elem := range stringKeys {
		assert.NotContains(t, allKeys, elem)
	}
	for _, elem := range setKeys {
		assert.NotContains(t, allKeys, elem)
	}
	for _, elem := range hashKeys {
		assert.NotContains(t, allKeys, elem)
	}
	for _, elem := range zsetKeys {
		assert.NotContains(t, allKeys, elem)
	}
	for _, elem := range streamKeys {
		assert.NotContains(t, allKeys, elem)
	}
}

func (suite *GlideTestSuite) TestFlushDB_Success() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(key, "test-value")
	assert.NoError(suite.T(), err)

	result, err := client.FlushDB()
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushDB_Failure() {
	client := suite.defaultClusterClient()
	client.Close()

	result, err := client.FlushDB()
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &errors.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestFlushAll_Success() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(key, "test-value")
	assert.NoError(suite.T(), err)

	result, err := client.FlushAll()
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushAll_Failure() {
	client := suite.defaultClusterClient()
	client.Close()

	result, err := client.FlushAll()
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &errors.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestFlushAllWithOptions_AllNodes() {
	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	_, err := client.Set(key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllNodes,
	}
	asyncMode := options.FlushMode(options.ASYNC)
	result, err := client.FlushAllWithOptions(options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})

	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "ReadOnly: You can't write against a read only replica")
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestFlushAllWithOptions_AllPrimaries() {
	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	_, err := client.Set(key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}
	asyncMode := options.FlushMode(options.ASYNC)
	result, err := client.FlushAllWithOptions(options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})

	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val1, err := client.Get(key1)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val1.Value())

	val2, err := client.Get(key2)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val2.Value())
}

func (suite *GlideTestSuite) TestFlushAllWithOptions_InvalidRoute() {
	client := suite.defaultClusterClient()

	invalidRoute := config.NewByAddressRoute("invalidHost", 9999)
	routeOption := &options.RouteOption{
		Route: invalidRoute,
	}
	syncMode := options.SYNC
	result, err := client.FlushAllWithOptions(options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	})

	assert.Error(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestFlushAllWithOptions_AsyncMode() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(key, "value5")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}

	asyncMode := options.FlushMode(options.ASYNC)
	result, err := client.FlushAllWithOptions(options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})

	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_AllNodes() {
	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	_, err := client.Set(key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllNodes,
	}
	asyncMode := options.ASYNC
	result, err := client.FlushDBWithOptions(options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "ReadOnly: You can't write against a read only replica")
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_AllPrimaries() {
	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	_, err := client.Set(key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}
	asyncMode := options.ASYNC
	result, err := client.FlushDBWithOptions(options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val1, err := client.Get(key1)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val1.Value())

	val2, err := client.Get(key2)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val2.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_InvalidRoute() {
	client := suite.defaultClusterClient()

	invalidRoute := config.Route(config.NewByAddressRoute("invalidHost", 9999))
	routeOption := &options.RouteOption{
		Route: invalidRoute,
	}
	syncMode := options.SYNC
	result, err := client.FlushDBWithOptions(options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	})
	assert.Error(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_AsyncMode() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(key, "value5")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}
	syncMode := options.SYNC
	result, err := client.FlushDBWithOptions(options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	})
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster() {
	suite.T().Skip("Skipping update connection password cluster test")
	// Create admin client
	adminClient := suite.defaultClusterClient()
	defer adminClient.Close()

	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	// Generate random password
	pwd := uuid.NewString()

	// Validate that we can use the test client
	_, err := testClient.Info()
	assert.NoError(suite.T(), err)

	// Update password without re-authentication
	_, err = testClient.UpdateConnectionPassword(pwd, false)
	assert.NoError(suite.T(), err)

	// Verify client still works with old auth
	_, err = testClient.Info()
	assert.NoError(suite.T(), err)

	// Update server password and kill all other clients to force reconnection
	_, err = adminClient.CustomCommand([]string{"CONFIG", "SET", "requirepass", pwd})
	assert.NoError(suite.T(), err)

	_, err = adminClient.CustomCommand([]string{"CLIENT", "KILL", "TYPE", "NORMAL"})
	assert.NoError(suite.T(), err)

	// Verify client auto-reconnects with new password
	_, err = testClient.Info()
	assert.NoError(suite.T(), err)

	// test reset connection password
	_, err = testClient.ResetConnectionPassword()
	assert.NoError(suite.T(), err)

	// Cleanup: config set reset password
	_, err = adminClient.CustomCommand([]string{"CONFIG", "SET", "requirepass", ""})
	assert.NoError(suite.T(), err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster_InvalidParameters() {
	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	// Test empty password
	_, err := testClient.UpdateConnectionPassword("", true)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &errors.RequestError{}, err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster_NoServerAuth() {
	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	// Validate that we can use the client
	_, err := testClient.Info()
	assert.NoError(suite.T(), err)

	// Test immediate re-authentication fails when no server password is set
	pwd := uuid.NewString()
	_, err = testClient.UpdateConnectionPassword(pwd, true)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &errors.RequestError{}, err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster_LongPassword() {
	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	// Generate long random password (1000 chars)
	const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
	pwd := make([]byte, 1000)
	for i := range pwd {
		pwd[i] = letters[rand.Intn(len(letters))]
	}

	// Validate that we can use the client
	_, err := testClient.Info()
	assert.NoError(suite.T(), err)

	// Test replacing connection password with a long password string
	_, err = testClient.UpdateConnectionPassword(string(pwd), false)
	assert.NoError(suite.T(), err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster_ImmediateAuthWrongPassword() {
	// Create admin client
	adminClient := suite.defaultClusterClient()
	defer adminClient.Close()

	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	pwd := uuid.NewString()
	notThePwd := uuid.NewString()

	// Validate that we can use the client
	_, err := testClient.Info()
	assert.NoError(suite.T(), err)

	// Set the password to something else
	_, err = adminClient.CustomCommand([]string{"CONFIG", "SET", "requirepass", notThePwd})
	assert.NoError(suite.T(), err)

	// Test that re-authentication fails when using wrong password
	_, err = testClient.UpdateConnectionPassword(pwd, true)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &errors.RequestError{}, err)

	// But using correct password returns OK
	_, err = testClient.UpdateConnectionPassword(notThePwd, true)
	assert.NoError(suite.T(), err)

	// Cleanup: Reset password
	_, err = adminClient.CustomCommand([]string{"CONFIG", "SET", "requirepass", ""})
	assert.NoError(suite.T(), err)
}

func (suite *GlideTestSuite) TestClusterLolwut() {
	client := suite.defaultClusterClient()

	result, err := client.Lolwut()
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)
	assert.Contains(suite.T(), result, "Redis ver.")
}

func (suite *GlideTestSuite) TestLolwutWithOptions_WithAllNodes() {
	client := suite.defaultClusterClient()
	options := options.ClusterLolwutOptions{
		LolwutOptions: &options.LolwutOptions{
			Version: 6,
			Args:    &[]int{10, 20},
		},
		RouteOption: &options.RouteOption{Route: config.AllNodes},
	}
	result, err := client.LolwutWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.True(suite.T(), result.IsMultiValue())
	multiValue := result.MultiValue()

	for _, value := range multiValue {
		assert.Contains(suite.T(), value, "Redis ver.")
	}
}

func (suite *GlideTestSuite) TestLolwutWithOptions_WithAllPrimaries() {
	client := suite.defaultClusterClient()
	options := options.ClusterLolwutOptions{
		LolwutOptions: &options.LolwutOptions{
			Version: 6,
		},
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	result, err := client.LolwutWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.True(suite.T(), result.IsMultiValue())
	multiValue := result.MultiValue()

	for _, value := range multiValue {
		assert.Contains(suite.T(), value, "Redis ver.")
	}
}

func (suite *GlideTestSuite) TestLolwutWithOptions_WithRandomRoute() {
	client := suite.defaultClusterClient()
	options := options.ClusterLolwutOptions{
		LolwutOptions: &options.LolwutOptions{
			Version: 6,
		},
		RouteOption: &options.RouteOption{Route: config.RandomRoute},
	}
	result, err := client.LolwutWithOptions(options)
	assert.NoError(suite.T(), err)

	assert.True(suite.T(), result.IsSingleValue())
	singleValue := result.SingleValue()
	assert.Contains(suite.T(), singleValue, "Redis ver.")
}

func (suite *GlideTestSuite) TestClientIdCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	response, err := client.ClientId()
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestClientIdWithOptionsCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// ClientId with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	response, err := client.ClientIdWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientIdWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientIdWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
}

func (suite *GlideTestSuite) TestLastSaveCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	response, err := client.LastSave()
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestLastSaveWithOptionCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	opts := options.RouteOption{Route: nil}
	response, err := client.LastSaveWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestConfigResetStatCluster() {
	client := suite.defaultClusterClient()

	// ConfigResetStat with option or with multiple options without route
	suite.verifyOK(client.ConfigResetStat())
}

func (suite *GlideTestSuite) TestConfigResetStatWithOptions() {
	client := suite.defaultClusterClient()

	// ConfigResetStat with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	suite.verifyOK(client.ConfigResetStatWithOptions(opts))

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigResetStatWithOptions(opts))

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigResetStatWithOptions(opts))
}

func (suite *GlideTestSuite) TestConfigSetGet() {
	client := suite.defaultClusterClient()
	t := suite.T()
	configParam := map[string]string{"timeout": "1000"}
	suite.verifyOK(client.ConfigSet(configParam))
	configGetParam := []string{"timeout"}
	resp, err := client.ConfigGet(configGetParam)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))
}

func (suite *GlideTestSuite) TestConfigSetGetWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()
	// ConfigResetStat with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	configParam := map[string]string{"timeout": "1000"}
	suite.verifyOK(client.ConfigSetWithOptions(configParam, opts))
	configGetParam := []string{"timeout"}
	resp, err := client.ConfigGetWithOptions(configGetParam, opts)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigSetWithOptions(configParam, opts))
	resp, err = client.ConfigGetWithOptions(configGetParam, opts)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigSetWithOptions(configParam, opts))
	resp, err = client.ConfigGetWithOptions(configGetParam, opts)
	assert.NoError(t, err)
	assert.True(t, resp.IsMultiValue())
	for _, messages := range resp.MultiValue() {
		mapString := fmt.Sprint(messages)
		assert.Contains(t, strings.ToLower(mapString), strings.ToLower("timeout"))
	}
}

func (suite *GlideTestSuite) TestClientSetGetName() {
	client := suite.defaultClusterClient()
	t := suite.T()
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(connectionName)
	response, err := client.ClientGetName()
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestClientSetGetNameWithRoute() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// ClientGetName with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	connectionName := "ConnectionName-" + uuid.NewString()
	response, err := client.ClientSetNameWithOptions(connectionName, opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
	response, err = client.ClientGetNameWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// same sections with random route
	connectionName = "ConnectionName-" + uuid.NewString()
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientSetNameWithOptions(connectionName, opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
	response, err = client.ClientGetNameWithOptions(opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestConfigRewriteCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Server}},
	}
	res, err := client.InfoWithOptions(opts)
	assert.NoError(t, err)
	for _, data := range res.MultiValue() {
		lines := strings.Split(data, "\n")
		var configFile string
		for _, line := range lines {
			if strings.HasPrefix(line, "config_file:") {
				configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
				break
			}
		}
		if len(configFile) > 0 {
			responseRewrite, err := client.ConfigRewrite()
			assert.NoError(t, err)
			assert.Equal(t, "OK", responseRewrite)
		}
	}
}

func (suite *GlideTestSuite) TestConfigRewriteWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()
	sections := []options.Section{options.Server}

	// info with option or with multiple options without route
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: nil,
	}
	response, err := client.InfoWithOptions(opts)
	assert.NoError(t, err)
	for _, data := range response.MultiValue() {
		lines := strings.Split(data, "\n")
		var configFile string
		for _, line := range lines {
			if strings.HasPrefix(line, "config_file:") {
				configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
				break
			}
		}
		if len(configFile) > 0 {
			responseRewrite, err := client.ConfigRewrite()
			assert.NoError(t, err)
			assert.Equal(t, "OK", responseRewrite)
			break
		}
	}

	// same sections with random route
	opts = options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: &options.RouteOption{Route: config.RandomRoute},
	}
	response, err = client.InfoWithOptions(opts)
	assert.NoError(t, err)
	lines := strings.Split(response.SingleValue(), "\n")
	var configFile string
	for _, line := range lines {
		if strings.HasPrefix(line, "config_file:") {
			configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
			break
		}
	}
	if len(configFile) > 0 {
		responseRewrite, err := client.ConfigRewrite()
		assert.NoError(t, err)
		assert.Equal(t, "OK", responseRewrite)
	}

	// default sections, multi node route
	opts = options.ClusterInfoOptions{
		InfoOptions: nil,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	response, err = client.InfoWithOptions(opts)
	assert.NoError(t, err)
	for _, data := range response.MultiValue() {
		lines := strings.Split(data, "\n")
		var configFile string
		for _, line := range lines {
			if strings.HasPrefix(line, "config_file:") {
				configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
				break
			}
		}
		if len(configFile) > 0 {
			responseRewrite, err := client.ConfigRewrite()
			assert.NoError(t, err)
			assert.Equal(t, "OK", responseRewrite)
			break
		}
	}
}

func (suite *GlideTestSuite) TestClusterRandomKey() {
	client := suite.defaultClusterClient()
	// Test 1: Check if the command return random key
	t := suite.T()
	result, err := client.RandomKey()
	assert.Nil(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) TestRandomKeyWithRoute() {
	client := suite.defaultClusterClient()
	// Test 1: Check if the command return random key
	t := suite.T()
	route := config.Route(config.RandomRoute)
	options := options.RouteOption{Route: route}
	result, err := client.RandomKeyWithRoute(options)
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) TestFunctionCommandsWithRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	t := suite.T()

	// Test with single node route
	libName := "mylib1c_single"
	funcName := "myfunc1c_single"
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, true)

	// Flush all functions with SYNC option and single node route
	route := options.RouteOption{Route: config.NewSlotKeyRoute(config.SlotTypePrimary, "1")}
	result, err := client.FunctionFlushSyncWithRoute(route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Load function with single node route
	result, err = client.FunctionLoadWithRoute(code, false, route)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Test FCALL with single node route
	functionResult, err := client.FCallWithArgsWithRoute(funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FCALL_RO with single node route
	functionResult, err = client.FCallReadOnlyWithArgsWithRoute(funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FunctionList with WithCode and query for all libraries
	query := api.FunctionListQuery{
		WithCode: true,
	}
	functionList, err := client.FunctionListWithRoute(query, route)
	assert.NoError(t, err)
	assert.True(t, functionList.IsSingleValue())

	// Check results from each node
	lib := functionList.SingleValue()[0]
	assert.Equal(t, libName, lib.Name)
	assert.Equal(t, "LUA", lib.Engine)
	assert.Contains(t, lib.Code, libName)

	assert.Len(t, lib.Functions, 1)
	funcInfo := lib.Functions[0]
	assert.Equal(t, funcName, funcInfo.Name)
	assert.Empty(t, funcInfo.Description)
	assert.Contains(t, funcInfo.Flags, "no-writes")

	// load new lib and delete it with single node route - first lib remains loaded
	anotherLib := GenerateLuaLibCode("anotherLib", map[string]string{"anotherFunc": ""}, false)
	result, err = client.FunctionLoadWithRoute(anotherLib, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "anotherLib", result)

	deleteResult, err := client.FunctionDeleteWithRoute("anotherLib", route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleteResult)

	// delete missing lib returns a error
	_, err = client.FunctionDeleteWithRoute("anotherLib", route)
	assert.IsType(suite.T(), &errors.RequestError{}, err)

	// Test with all primaries route
	libName = "mylib1c_all"
	funcName = "myfunc1c_all"
	functions = map[string]string{
		funcName: "return args[1]",
	}
	code = GenerateLuaLibCode(libName, functions, true)

	// Flush all functions with SYNC option and all primaries route
	route = options.RouteOption{Route: config.AllPrimaries}
	result, err = client.FunctionFlushSyncWithRoute(route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Load function with all primaries route
	result, err = client.FunctionLoadWithRoute(code, false, route)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Test FCALL with all primaries route
	functionResult, err = client.FCallWithArgsWithRoute(funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FCALL_RO with all primaries route
	functionResult, err = client.FCallReadOnlyWithArgsWithRoute(funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FunctionList with WithCode and query for all libraries
	functionList, err = client.FunctionListWithRoute(query, route)
	assert.NoError(t, err)
	assert.False(t, functionList.IsSingleValue())

	// Check results from each node
	for _, libs := range functionList.MultiValue() {
		assert.Len(t, libs, 1)
		libInfo := libs[0]
		assert.Equal(t, libName, libInfo.Name)
		assert.Equal(t, "LUA", libInfo.Engine)
		assert.Contains(t, libInfo.Code, libName)

		assert.Len(t, libInfo.Functions, 1)
		funcInfo := libInfo.Functions[0]
		assert.Equal(t, funcName, funcInfo.Name)
		assert.Empty(t, funcInfo.Description)
		assert.Contains(t, funcInfo.Flags, "no-writes")
	}

	// load new lib and delete it with all primaries route - first lib remains loaded
	anotherLib = GenerateLuaLibCode("anotherLib", map[string]string{"anotherFunc": ""}, false)
	result, err = client.FunctionLoadWithRoute(anotherLib, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "anotherLib", result)

	deleteResult, err = client.FunctionDeleteWithRoute("anotherLib", route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleteResult)

	// delete missing lib returns a error
	_, err = client.FunctionDeleteWithRoute("anotherLib", route)
	assert.IsType(suite.T(), &errors.RequestError{}, err)
}

func (suite *GlideTestSuite) TestFunctionCommandsWithoutKeysAndWithoutRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	t := suite.T()

	// Flush all functions with SYNC option
	result, err := client.FunctionFlushSync()
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Create function that returns first argument
	libName := "mylib1c"
	funcName := "myfunc1c"
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, true)

	// Load function
	result, err = client.FunctionLoad(code, false)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Test FCALL
	functionResult, err := client.FCallWithArgs(funcName, []string{"one", "two"})
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FCALL_RO
	functionResult, err = client.FCallReadOnlyWithArgs(funcName, []string{"one", "two"})
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// load new lib and delete it - first lib remains loaded
	anotherLib := GenerateLuaLibCode("anotherLib", map[string]string{"anotherFunc": ""}, false)
	result, err = client.FunctionLoad(anotherLib, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "anotherLib", result)

	deleteResult, err := client.FunctionDelete("anotherLib")
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleteResult)

	// delete missing lib returns a error
	_, err = client.FunctionDelete("anotherLib")
	assert.IsType(suite.T(), &errors.RequestError{}, err)
}

func (suite *GlideTestSuite) TestFunctionStatsWithoutRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	t := suite.T()

	// Flush all functions with SYNC option
	result, err := client.FunctionFlushSync()
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Load first function
	libName := "functionStats_without_route"
	funcName := libName
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, false)
	result, err = client.FunctionLoad(code, true)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Check stats after loading first function
	stats, err := client.FunctionStats()
	assert.NoError(t, err)
	for _, nodeStats := range stats {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(1), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(1), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Load second function with multiple functions
	libName2 := libName + "_2"
	functions2 := map[string]string{
		funcName + "_2": "return 'OK'",
		funcName + "_3": "return 42",
	}
	code2 := GenerateLuaLibCode(libName2, functions2, false)
	result, err = client.FunctionLoad(code2, true)
	assert.NoError(t, err)
	assert.Equal(t, libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStats()
	assert.NoError(t, err)
	for _, nodeStats := range stats {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(3), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(2), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSync()
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Check stats after flushing
	stats, err = client.FunctionStats()
	assert.NoError(t, err)
	for _, nodeStats := range stats {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].LibraryCount)
	}
}

func (suite *GlideTestSuite) TestFunctionStatsWithRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	t := suite.T()

	// Test with single node route
	libName := "functionStats_with_route_single"
	funcName := libName
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, false)

	// Flush all functions with SYNC option and single node route
	route := options.RouteOption{Route: config.NewSlotKeyRoute(config.SlotTypePrimary, "1")}
	result, err := client.FunctionFlushSyncWithRoute(route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Load function with single node route
	result, err = client.FunctionLoadWithRoute(code, true, route)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Check stats with single node route
	stats, err := client.FunctionStatsWithRoute(route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(1), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(1), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Load second function with multiple functions
	libName2 := libName + "_2"
	functions2 := map[string]string{
		funcName + "_2": "return 'OK'",
		funcName + "_3": "return 42",
	}
	code2 := GenerateLuaLibCode(libName2, functions2, false)
	result, err = client.FunctionLoadWithRoute(code2, true, route)
	assert.NoError(t, err)
	assert.Equal(t, libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStatsWithRoute(route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(3), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(2), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSyncWithRoute(route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Check stats after flushing
	stats, err = client.FunctionStatsWithRoute(route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Test with all primaries route
	libName = "functionStats_with_route_all"
	funcName = libName
	functions = map[string]string{
		funcName: "return args[1]",
	}
	code = GenerateLuaLibCode(libName, functions, false)

	// Flush all functions with SYNC option and all primaries route
	route = options.RouteOption{Route: config.AllPrimaries}
	result, err = client.FunctionFlushSyncWithRoute(route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Load function with all primaries route
	result, err = client.FunctionLoadWithRoute(code, true, route)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Check stats with all primaries route
	stats, err = client.FunctionStatsWithRoute(route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(1), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(1), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Load second function with multiple functions
	libName2 = libName + "_2"
	functions2 = map[string]string{
		funcName + "_2": "return 'OK'",
		funcName + "_3": "return 42",
	}
	code2 = GenerateLuaLibCode(libName2, functions2, false)
	result, err = client.FunctionLoadWithRoute(code2, true, route)
	assert.NoError(t, err)
	assert.Equal(t, libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStatsWithRoute(route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(3), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(2), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSyncWithRoute(route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Check stats after flushing
	stats, err = client.FunctionStatsWithRoute(route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].LibraryCount)
	}
}

func (suite *GlideTestSuite) TestFunctionKilWithoutRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()

	// Flush before setup
	result, err := client.FunctionFlushSync()
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing loaded, nothing to kill
	_, err = client.FunctionKill()
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestFunctionKillWithRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()

	// key for routing to a primary node
	randomKey := uuid.NewString()
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, randomKey),
	}

	// Flush all functions with route
	result, err := client.FunctionFlushSyncWithRoute(route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKillWithRoute(route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestFunctionKillNoWriteWithoutRoute() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}
	suite.testFunctionKillNoWrite(false)
}

func (suite *GlideTestSuite) TestFunctionKillNoWriteWithRoute() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}
	suite.testFunctionKillNoWrite(true)
}

func (suite *GlideTestSuite) testFunctionKillNoWrite(withRoute bool) {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	libName := "functionKill_no_write"
	funcName := "deadlock"
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, true)

	// key for routing to a primary node
	randomKey := uuid.NewString()
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, randomKey),
	}

	// Flush all functions with route
	var result string
	var err error
	if withRoute {
		result, err = client.FunctionFlushSyncWithRoute(route)
	} else {
		result, err = client.FunctionFlushSync()
	}
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	if withRoute {
		_, err = client.FunctionKillWithRoute(route)
	} else {
		_, err = client.FunctionKill()
	}
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	if withRoute {
		result, err = client.FunctionLoadWithRoute(code, true, route)
	} else {
		result, err = client.FunctionLoad(code, true)
	}
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10000)
	testClient := suite.clusterClient(testConfig)
	defer testClient.Close()

	// Channel to signal when function is killed
	killed := make(chan bool)

	// Start a goroutine to kill the function
	checkInterval := 100 * time.Millisecond
	if withRoute {
		checkInterval = 500 * time.Millisecond
	}
	go func() {
		defer close(killed)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(checkInterval)
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				killed <- false
				return
			case <-killTicker.C:
				if withRoute {
					result, err = client.FunctionKillWithRoute(route)
				} else {
					result, err = client.FunctionKill()
				}
				if err == nil {
					// successful kill
					return
				}
			}
		}
	}()

	// Call the function - blocking until killed and return a script kill error
	if withRoute {
		_, err = testClient.FCallWithRoute(funcName, route)
	} else {
		_, err = testClient.FCall(funcName)
	}
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "script killed"))

	// Wait for function kill to return not busy
	notBusyTimeout := time.After(2 * time.Second)
	notBusyTicker := time.NewTicker(100 * time.Millisecond)
	defer notBusyTicker.Stop()

	for {
		select {
		case <-notBusyTimeout:
			suite.T().Fatal("Timed out waiting for function to be not busy")
			return
		case <-notBusyTicker.C:
			if withRoute {
				_, err = client.FunctionKillWithRoute(route)
			} else {
				_, err = client.FunctionKill()
			}
			if err != nil && strings.Contains(strings.ToLower(err.Error()), "notbusy") {
				return
			}
		}
	}
}

func (suite *GlideTestSuite) TestFunctionKillKeyBasedWriteFunction() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}

	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	libName := "functionKill_key_based_write_function"
	funcName := "deadlock_write_function_with_key_based_route"
	key := libName
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, false)

	// Create route using the key
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, key),
	}

	// Flush all functions with route
	result, err := client.FunctionFlushSyncWithRoute(route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKillWithRoute(route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoadWithRoute(code, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10000)
	testClient := suite.clusterClient(testConfig)
	defer testClient.Close()

	// Channel to signal when unkillable error is found
	unkillable := make(chan bool)

	// Start a goroutine to attempt killing the function
	go func() {
		defer close(unkillable)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(500 * time.Millisecond) // 500ms interval
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				unkillable <- false
				return
			case <-killTicker.C:
				_, err = client.FunctionKillWithRoute(route)
				// Look for unkillable error
				if err != nil && strings.Contains(strings.ToLower(err.Error()), "unkillable") {
					unkillable <- true
					return
				}
			}
		}
	}()

	// Call the function with the key - this will block until completion
	testClient.FCallWithKeysAndArgs(funcName, []string{key}, []string{})
	// Function completed as expected

	// Wait for unkillable confirmation
	foundUnkillable := <-unkillable
	assert.True(suite.T(), foundUnkillable, "Function should be unkillable")
}
