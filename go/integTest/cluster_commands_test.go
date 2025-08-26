// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"math/rand"
	"strings"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func (suite *GlideTestSuite) TestClusterCustomCommandInfo() {
	client := suite.defaultClusterClient()
	result, err := client.CustomCommand(context.Background(), []string{"INFO"})

	suite.NoError(err)
	// INFO is routed to all primary nodes by default
	for _, value := range result.MultiValue() {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandEcho() {
	client := suite.defaultClusterClient()
	result, err := client.CustomCommand(context.Background(), []string{"ECHO", "GO GLIDE GO"})

	suite.NoError(err)
	// ECHO is routed to a single random node
	assert.Equal(suite.T(), "GO GLIDE GO", result.SingleValue().(string))
}

func (suite *GlideTestSuite) TestClusterCustomCommandDbSize() {
	client := suite.defaultClusterClient()
	// DBSIZE result is always a single number regardless of route
	result, err := client.CustomCommand(context.Background(), []string{"dbsize"})
	assert.NoError(suite.T(), err)
	assert.GreaterOrEqual(suite.T(), result.SingleValue().(int64), int64(0))

	result, err = client.CustomCommandWithRoute(context.Background(), []string{"dbsize"}, config.AllPrimaries)
	assert.NoError(suite.T(), err)
	assert.GreaterOrEqual(suite.T(), result.SingleValue().(int64), int64(0))

	result, err = client.CustomCommandWithRoute(context.Background(), []string{"dbsize"}, config.RandomRoute)
	assert.NoError(suite.T(), err)
	assert.GreaterOrEqual(suite.T(), result.SingleValue().(int64), int64(0))
}

func (suite *GlideTestSuite) TestClusterCustomCommandConfigGet() {
	client := suite.defaultClusterClient()

	// CONFIG GET returns a map, but with a single node route it is handled as a single value
	result, err := client.CustomCommandWithRoute(context.Background(), []string{"CONFIG", "GET", "*file"}, config.RandomRoute)
	assert.NoError(suite.T(), err)
	assert.Greater(suite.T(), len(result.SingleValue().(map[string]any)), 0)

	result, err = client.CustomCommandWithRoute(context.Background(), []string{"CONFIG", "GET", "*file"}, config.AllPrimaries)
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
	data, err := client.Info(context.Background())
	assert.NoError(t, err)
	for _, info := range data {
		for _, section := range DEFAULT_INFO_SECTIONS {
			assert.Contains(t, info, "# "+section, "Section "+section+" is missing")
		}
	}

	// info with option or with multiple options without route
	sections := []constants.Section{constants.Cpu}
	if suite.serverVersion >= "7.0.0" {
		sections = append(sections, constants.Memory)
	}
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: nil,
	}
	response, err := client.InfoWithOptions(context.Background(), opts)
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
	response, err = client.InfoWithOptions(context.Background(), opts)
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
	response, err = client.InfoWithOptions(context.Background(), opts)
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
	result, err := client.CustomCommandWithRoute(context.Background(), []string{"INFO"}, route)
	suite.NoError(err)
	assert.True(suite.T(), result.IsMultiValue())
	multiValue := result.MultiValue()
	for _, value := range multiValue {
		assert.True(suite.T(), strings.Contains(value.(string), "# Stats"))
	}
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_Echo() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.RandomRoute)
	result, err := client.CustomCommandWithRoute(context.Background(), []string{"ECHO", "GO GLIDE GO"}, route)
	suite.NoError(err)
	assert.True(suite.T(), result.IsSingleValue())
	assert.Equal(suite.T(), "GO GLIDE GO", result.SingleValue().(string))
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_InvalidRoute() {
	client := suite.defaultClusterClient()
	invalidRoute := config.NewByAddressRoute("invalidHost", 9999)
	result, err := client.CustomCommandWithRoute(context.Background(), []string{"PING"}, invalidRoute)
	assert.NotNil(suite.T(), err)
	assert.True(suite.T(), result.IsEmpty())
}

func (suite *GlideTestSuite) TestClusterCustomCommandWithRoute_AllNodes() {
	client := suite.defaultClusterClient()
	route := config.SimpleNodeRoute(config.AllNodes)
	result, err := client.CustomCommandWithRoute(context.Background(), []string{"PING"}, route)
	suite.NoError(err)
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
	result, err := client.PingWithOptions(context.Background(), options)
	suite.NoError(err)
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
	result, err := client.PingWithOptions(context.Background(), options)
	suite.NoError(err)
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
	result, err := client.PingWithOptions(context.Background(), options)
	assert.NotNil(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestTimeWithoutRoute() {
	client := suite.defaultClusterClient()
	options := options.RouteOption{Route: nil}
	result, err := client.TimeWithOptions(context.Background(), options)
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
	result, err := client.TimeWithOptions(context.Background(), options)
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
	result, err := client.TimeWithOptions(context.Background(), options)
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
	result, err := client.TimeWithOptions(context.Background(), options)
	assert.NotNil(suite.T(), err)
	assert.True(suite.T(), result.IsEmpty())
	assert.Empty(suite.T(), result.SingleValue())
}

func (suite *GlideTestSuite) TestDBSizeRandomRoute() {
	client := suite.defaultClusterClient()
	route := config.Route(config.RandomRoute)
	options := options.RouteOption{Route: route}
	result, err := client.DBSizeWithOptions(context.Background(), options)
	assert.NoError(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.GreaterOrEqual(suite.T(), result, int64(0))
}

func (suite *GlideTestSuite) TestEchoCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Echo with random route
	route := options.RouteOption{Route: config.RandomRoute}
	response, err := client.EchoWithOptions(context.Background(), "hello", route)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// Echo with multi node route
	route = options.RouteOption{Route: config.AllPrimaries}
	response, err = client.EchoWithOptions(context.Background(), "hello", route)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
	for _, messages := range response.MultiValue() {
		assert.Contains(t, strings.ToLower(messages), strings.ToLower("hello"))
	}

	// Ensure no error when using an empty message
	_, err = client.EchoWithOptions(context.Background(), "", route)
	assert.NoError(t, err, "EchoWithOptions with empty message should not return an error")
}

func (suite *GlideTestSuite) TestBasicClusterScan() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	assert.NoError(t, err)

	// Iterate over all keys in the cluster
	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err = client.MSet(context.Background(), keysToSet)
	assert.NoError(t, err)

	cursor := models.NewClusterScanCursor()
	allKeys := make([]string, 0, len(keysToSet))

	for !cursor.IsFinished() {
		result, err := client.Scan(context.Background(), cursor)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
	}

	assert.ElementsMatch(t, allKeys, []string{"key1", "key2", "key3"})

	// Ensure clean start
	_, err = client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	assert.NoError(t, err)

	expectedKeys := make([]string, 0, 100)
	// Test bigger example
	for i := 0; i < 100; i++ {
		key := uuid.NewString()

		expectedKeys = append(expectedKeys, key)

		_, err := client.Set(context.Background(), key, "value")
		assert.NoError(t, err)
	}

	cursor = models.NewClusterScanCursor()
	allKeys = make([]string, 0, 100)

	for !cursor.IsFinished() {
		result, err := client.Scan(context.Background(), cursor)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
	}

	assert.ElementsMatch(t, allKeys, expectedKeys)
}

func (suite *GlideTestSuite) TestBasicClusterScanWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	assert.NoError(t, err)

	// Iterate over all keys in the cluster
	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err = client.MSet(context.Background(), keysToSet)
	assert.NoError(t, err)

	cursor := models.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetCount(10)
	allKeys := []string{}

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(context.Background(), cursor, *opts)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
	}

	assert.ElementsMatch(t, allKeys, []string{"key1", "key2", "key3"})

	// Iterate over keys matching a pattern
	keysToSet = map[string]string{
		"key1":          "value1",
		"key2":          "value2",
		"notMykey":      "value3",
		"somethingElse": "value4",
	}

	_, err = client.MSet(context.Background(), keysToSet)
	assert.NoError(t, err)

	cursor = models.NewClusterScanCursor()
	opts = options.NewClusterScanOptions().SetCount(10).SetMatch("*key*")
	matchedKeys := []string{}

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(context.Background(), cursor, *opts)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		matchedKeys = append(matchedKeys, result.Keys...)
		cursor = result.Cursor
	}

	assert.ElementsMatch(t, matchedKeys, []string{"key1", "key2", "key3", "notMykey"})
	assert.NotContains(t, matchedKeys, "somethingElse")

	// Iterate over keys of a specific type
	keysToSet = map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}
	_, err = client.MSet(context.Background(), keysToSet)
	assert.NoError(t, err)

	_, err = client.SAdd(context.Background(), "thisIsASet", []string{"someValue"})
	assert.NoError(t, err)

	cursor = models.NewClusterScanCursor()
	opts = options.NewClusterScanOptions().SetType(constants.ObjectTypeSet)
	matchedTypeKeys := []string{}

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(context.Background(), cursor, *opts)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		matchedTypeKeys = append(matchedTypeKeys, result.Keys...)
		cursor = result.Cursor
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
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	assert.NoError(t, err)

	// Iterate over all keys in the cluster
	keysToSet := map[string]string{
		"key\xc0\xc1-1": "value1",
		"key-2":         "value2",
		"key\xf9\xc1-3": "value3",
		"someKey":       "value4",
		"\xc0\xc1key-5": "value5",
	}

	_, err = client.MSet(context.Background(), keysToSet)
	assert.NoError(t, err)

	cursor := models.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetMatch("key\xc0\xc1-*")
	allKeys := []string{}

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(context.Background(), cursor, *opts)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
	}

	assert.ElementsMatch(t, allKeys, []string{"key\xc0\xc1-1"})
}

func (suite *GlideTestSuite) TestClusterScanWithObjectTypeAndPattern() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Ensure clean start
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
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

		_, err := client.Set(context.Background(), key, "value")
		assert.NoError(t, err)

		_, err = client.SAdd(context.Background(), unexpectedTypeKey, []string{"value"})
		assert.NoError(t, err)

		_, err = client.Set(context.Background(), unexpectedPatternKey, "value")
		assert.NoError(t, err)
	}

	cursor := models.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetMatch("key-*").SetType(constants.ObjectTypeString)
	allKeys := make([]string, 0, 100)

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(context.Background(), cursor, *opts)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
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
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	assert.NoError(t, err)

	expectedKeys := make([]string, 0, 100)

	for i := 0; i < 100; i++ {
		key := "key-" + uuid.NewString()
		expectedKeys = append(expectedKeys, key)
		suite.verifyOK(client.Set(context.Background(), key, "value"))
	}

	cursor := models.NewClusterScanCursor()
	allKeys := make([]string, 0, 100)
	successfulScans := 0

	for !cursor.IsFinished() {
		keysOf1 := []string{}
		keysOf100 := []string{}

		result, err := client.ScanWithOptions(context.Background(), cursor, *options.NewClusterScanOptions().SetCount(1))
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		keysOf1 = append(keysOf1, result.Keys...)
		allKeys = append(allKeys, keysOf1...)
		cursor = result.Cursor

		if cursor.IsFinished() {
			break
		}

		result, err = client.ScanWithOptions(
			context.Background(),
			cursor,
			*options.NewClusterScanOptions().SetCount(100),
		)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}
		keysOf100 = append(keysOf100, result.Keys...)
		allKeys = append(allKeys, keysOf100...)
		cursor = result.Cursor

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
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	assert.NoError(t, err)

	expectedKeys := []string{}
	unexpectedKeys := []string{}

	for i := 0; i < 10; i++ {
		key := "key-" + uuid.NewString()
		unexpectedKey := uuid.NewString()

		expectedKeys = append(expectedKeys, key)
		unexpectedKeys = append(unexpectedKeys, unexpectedKey)

		_, err := client.Set(context.Background(), key, "value")
		assert.NoError(t, err)

		_, err = client.Set(context.Background(), unexpectedKey, "value")
		assert.NoError(t, err)
	}

	cursor := models.NewClusterScanCursor()
	allKeys := []string{}

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(
			context.Background(),
			cursor,
			*options.NewClusterScanOptions().SetMatch("key-*"),
		)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}

		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
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
	_, err := client.FlushAllWithOptions(
		context.Background(),
		options.FlushClusterOptions{RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
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

		_, err := client.Set(context.Background(), key, "value")
		assert.NoError(t, err)

		_, err = client.SAdd(context.Background(), setKey, []string{"value"})
		assert.NoError(t, err)

		_, err = client.HSet(context.Background(), hashKey, map[string]string{"field": "value"})
		assert.NoError(t, err)

		_, err = client.LPush(context.Background(), listKey, []string{"value"})
		assert.NoError(t, err)

		_, err = client.ZAdd(context.Background(), zsetKey, map[string]float64{"value": 1})
		assert.NoError(t, err)

		_, err = client.XAdd(context.Background(), streamKey, []models.FieldValue{{Field: "field", Value: "value"}})
		assert.NoError(t, err)
	}

	cursor := models.NewClusterScanCursor()
	allKeys := []string{}

	for !cursor.IsFinished() {
		result, err := client.ScanWithOptions(context.Background(),
			cursor,
			*options.NewClusterScanOptions().SetType(constants.ObjectTypeList),
		)
		if !assert.NoError(t, err) {
			break // prevent infinite loop
		}

		allKeys = append(allKeys, result.Keys...)
		cursor = result.Cursor
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
	_, err := client.Set(context.Background(), key, "test-value")
	assert.NoError(suite.T(), err)

	result, err := client.FlushDB(context.Background())
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushDB_Failure() {
	client := suite.defaultClusterClient()
	client.Close()

	result, err := client.FlushDB(context.Background())
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestFlushAll_Success() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "test-value")
	assert.NoError(suite.T(), err)

	result, err := client.FlushAll(context.Background())
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushAll_Failure() {
	client := suite.defaultClusterClient()
	client.Close()

	result, err := client.FlushAll(context.Background())
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestFlushAllWithOptions_AllNodes() {
	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	_, err := client.Set(context.Background(), key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(context.Background(), key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllNodes,
	}
	asyncMode := options.FlushMode(options.ASYNC)
	result, err := client.FlushAllWithOptions(context.Background(), options.FlushClusterOptions{
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
	_, err := client.Set(context.Background(), key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(context.Background(), key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}
	asyncMode := options.FlushMode(options.ASYNC)
	result, err := client.FlushAllWithOptions(context.Background(), options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})

	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val1, err := client.Get(context.Background(), key1)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val1.Value())

	val2, err := client.Get(context.Background(), key2)
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
	result, err := client.FlushAllWithOptions(context.Background(), options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	})

	assert.Error(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestFlushAllWithOptions_AsyncMode() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "value5")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}

	asyncMode := options.FlushMode(options.ASYNC)
	result, err := client.FlushAllWithOptions(context.Background(), options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})

	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_AllNodes() {
	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	_, err := client.Set(context.Background(), key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(context.Background(), key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllNodes,
	}
	asyncMode := options.ASYNC
	result, err := client.FlushDBWithOptions(context.Background(), options.FlushClusterOptions{
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
	_, err := client.Set(context.Background(), key1, "value3")
	assert.NoError(suite.T(), err)
	_, err = client.Set(context.Background(), key2, "value4")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}
	asyncMode := options.ASYNC
	result, err := client.FlushDBWithOptions(context.Background(), options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	})
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val1, err := client.Get(context.Background(), key1)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val1.Value())

	val2, err := client.Get(context.Background(), key2)
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
	result, err := client.FlushDBWithOptions(context.Background(), options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	})
	assert.Error(suite.T(), err)
	assert.Empty(suite.T(), result)
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_AsyncMode() {
	client := suite.defaultClusterClient()

	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "value5")
	assert.NoError(suite.T(), err)

	routeOption := &options.RouteOption{
		Route: config.AllPrimaries,
	}
	syncMode := options.SYNC
	result, err := client.FlushDBWithOptions(context.Background(), options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	})
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(context.Background(), key)
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
	_, err := testClient.Info(context.Background())
	assert.NoError(suite.T(), err)

	// Update password without re-authentication
	_, err = testClient.UpdateConnectionPassword(context.Background(), pwd, false)
	assert.NoError(suite.T(), err)

	// Verify client still works with old auth
	_, err = testClient.Info(context.Background())
	assert.NoError(suite.T(), err)

	// Update server password and kill all other clients to force reconnection
	_, err = adminClient.CustomCommand(context.Background(), []string{"CONFIG", "SET", "requirepass", pwd})
	assert.NoError(suite.T(), err)

	_, err = adminClient.CustomCommand(context.Background(), []string{"CLIENT", "KILL", "TYPE", "NORMAL"})
	assert.NoError(suite.T(), err)

	// Verify client auto-reconnects with new password
	_, err = testClient.Info(context.Background())
	assert.NoError(suite.T(), err)

	// test reset connection password
	_, err = testClient.ResetConnectionPassword(context.Background())
	assert.NoError(suite.T(), err)

	// Cleanup: config set reset password
	_, err = adminClient.CustomCommand(context.Background(), []string{"CONFIG", "SET", "requirepass", ""})
	assert.NoError(suite.T(), err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster_InvalidParameters() {
	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	// Test empty password
	_, err := testClient.UpdateConnectionPassword(context.Background(), "", true)
	suite.Error(err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordCluster_NoServerAuth() {
	// Create test client
	testClient := suite.defaultClusterClient()
	defer testClient.Close()

	// Validate that we can use the client
	_, err := testClient.Info(context.Background())
	assert.NoError(suite.T(), err)

	// Test immediate re-authentication fails when no server password is set
	pwd := uuid.NewString()
	_, err = testClient.UpdateConnectionPassword(context.Background(), pwd, true)
	suite.Error(err)
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
	_, err := testClient.Info(context.Background())
	assert.NoError(suite.T(), err)

	// Test replacing connection password with a long password string
	_, err = testClient.UpdateConnectionPassword(context.Background(), string(pwd), false)
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
	_, err := testClient.Info(context.Background())
	assert.NoError(suite.T(), err)

	// Set the password to something else
	_, err = adminClient.CustomCommand(context.Background(), []string{"CONFIG", "SET", "requirepass", notThePwd})
	assert.NoError(suite.T(), err)

	// Test that re-authentication fails when using wrong password
	_, err = testClient.UpdateConnectionPassword(context.Background(), pwd, true)
	suite.Error(err)

	// But using correct password returns OK
	_, err = testClient.UpdateConnectionPassword(context.Background(), notThePwd, true)
	suite.NoError(err)

	// Cleanup: Reset password
	_, err = adminClient.CustomCommand(context.Background(), []string{"CONFIG", "SET", "requirepass", ""})
	suite.NoError(err)
}

func (suite *GlideTestSuite) TestClusterLolwut() {
	client := suite.defaultClusterClient()

	result, err := client.Lolwut(context.Background())
	suite.NoError(err)
	suite.NotEmpty(result)
	// Check for version string in LOLWUT output (dual contains approach)
	hasVer := strings.Contains(result, "ver")
	hasVersion := strings.Contains(result, suite.serverVersion)
	suite.True(hasVer && hasVersion,
		"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, result)
}

func (suite *GlideTestSuite) TestLolwutWithOptions_WithAllNodes() {
	client := suite.defaultClusterClient()
	options := options.ClusterLolwutOptions{
		LolwutOptions: &options.LolwutOptions{
			Version: 6,
			Args:    []int{10, 20},
		},
		RouteOption: &options.RouteOption{Route: config.AllNodes},
	}
	result, err := client.LolwutWithOptions(context.Background(), options)
	suite.NoError(err)

	suite.True(result.IsMultiValue())
	multiValue := result.MultiValue()

	for _, value := range multiValue {
		// Check for version string in LOLWUT output (dual contains approach)
		hasVer := strings.Contains(value, "ver")
		hasVersion := strings.Contains(value, suite.serverVersion)
		assert.True(suite.T(), hasVer && hasVersion,
			"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, value)
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
	result, err := client.LolwutWithOptions(context.Background(), options)
	suite.NoError(err)

	suite.True(result.IsMultiValue())
	multiValue := result.MultiValue()

	for _, value := range multiValue {
		// Check for version string in LOLWUT output (dual contains approach)
		hasVer := strings.Contains(value, "ver")
		hasVersion := strings.Contains(value, suite.serverVersion)
		assert.True(suite.T(), hasVer && hasVersion,
			"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, value)
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
	result, err := client.LolwutWithOptions(context.Background(), options)
	assert.NoError(suite.T(), err)

	assert.True(suite.T(), result.IsSingleValue())
	singleValue := result.SingleValue()
	// Check for version string in LOLWUT output (dual contains approach)
	hasVer := strings.Contains(singleValue, "ver")
	hasVersion := strings.Contains(singleValue, suite.serverVersion)
	assert.True(suite.T(), hasVer && hasVersion,
		"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, singleValue)
}

func (suite *GlideTestSuite) TestClientIdCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	response, err := client.ClientId(context.Background())
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestClientIdWithOptionsCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// ClientId with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	response, err := client.ClientIdWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientIdWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientIdWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
}

func (suite *GlideTestSuite) TestLastSaveCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	response, err := client.LastSave(context.Background())
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestLastSaveWithOptionCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	opts := options.RouteOption{Route: nil}
	response, err := client.LastSaveWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
}

func (suite *GlideTestSuite) TestConfigResetStatCluster() {
	client := suite.defaultClusterClient()

	// ConfigResetStat with option or with multiple options without route
	suite.verifyOK(client.ConfigResetStat(context.Background()))
}

func (suite *GlideTestSuite) TestConfigResetStatWithOptions() {
	client := suite.defaultClusterClient()

	// ConfigResetStat with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	suite.verifyOK(client.ConfigResetStatWithOptions(context.Background(), opts))

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigResetStatWithOptions(context.Background(), opts))

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigResetStatWithOptions(context.Background(), opts))
}

func (suite *GlideTestSuite) TestConfigSetGet() {
	client := suite.defaultClusterClient()
	t := suite.T()
	configParam := map[string]string{"timeout": "1000"}
	suite.verifyOK(client.ConfigSet(context.Background(), configParam))
	configGetParam := []string{"timeout"}
	resp, err := client.ConfigGet(context.Background(), configGetParam)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))
}

func (suite *GlideTestSuite) TestConfigSetGetWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()
	// ConfigResetStat with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	configParam := map[string]string{"timeout": "1000"}
	suite.verifyOK(client.ConfigSetWithOptions(context.Background(), configParam, opts))
	configGetParam := []string{"timeout"}
	resp, err := client.ConfigGetWithOptions(context.Background(), configGetParam, opts)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigSetWithOptions(context.Background(), configParam, opts))
	resp, err = client.ConfigGetWithOptions(context.Background(), configGetParam, opts)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigSetWithOptions(context.Background(), configParam, opts))
	resp, err = client.ConfigGetWithOptions(context.Background(), configGetParam, opts)
	assert.NoError(t, err)
	assert.True(t, resp.IsMultiValue())
	for _, messages := range resp.MultiValue() {
		mapString := fmt.Sprint(messages)
		assert.Contains(t, strings.ToLower(mapString), strings.ToLower("timeout"))
	}
}

func (suite *GlideTestSuite) TestClusterClientGetName() {
	client := suite.defaultClusterClient()
	t := suite.T()

	response, err := client.ClientGetName(context.Background())
	assert.NoError(t, err)
	assert.True(t, response.IsNil())
}

func (suite *GlideTestSuite) TestClusterClientGetNameWithRoute() {
	client := suite.defaultClusterClient()
	t := suite.T()

	route := config.Route(config.RandomRoute)
	opts := options.RouteOption{Route: route}

	response, err := client.ClientGetNameWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response.IsSingleValue())
	assert.True(t, response.SingleValue().IsNil())
}

func (suite *GlideTestSuite) TestClusterClientGetNameWithMultiNodeRoutes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{Route: route}

	response, err := client.ClientGetNameWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response.IsMultiValue())
	for _, value := range response.MultiValue() {
		assert.True(t, value.IsNil())
	}
}

func (suite *GlideTestSuite) TestClientSetGetName() {
	client := suite.defaultClusterClient()
	t := suite.T()
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.Background(), connectionName)
	response, err := client.ClientGetName(context.Background())
	assert.NoError(t, err)
	assert.Equal(t, connectionName, response.Value())
}

func (suite *GlideTestSuite) TestClientSetGetNameWithRoute() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// ClientGetName with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	connectionName := "ConnectionName-" + uuid.NewString()
	response, err := client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	suite.verifyOK(response, err)
	response2, err := client.ClientGetNameWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response2.IsSingleValue())

	// same sections with random route
	connectionName = "ConnectionName-" + uuid.NewString()
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	suite.verifyOK(response, err)
	response2, err = client.ClientGetNameWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	assert.True(t, response2.IsSingleValue())

	// same sections with multinode routes
	connectionName = "ConnectionName-" + uuid.NewString()
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	response, err = client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	suite.verifyOK(response, err)
	response2, err = client.ClientGetNameWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	for _, data := range response2.MultiValue() {
		assert.Equal(t, connectionName, data.Value())
	}
}

func (suite *GlideTestSuite) TestConfigRewriteCluster() {
	client := suite.defaultClusterClient()
	t := suite.T()
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []constants.Section{constants.Server}},
	}
	res, err := client.InfoWithOptions(context.Background(), opts)
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
			responseRewrite, err := client.ConfigRewrite(context.Background())
			assert.NoError(t, err)
			assert.Equal(t, "OK", responseRewrite)
		}
	}
}

func (suite *GlideTestSuite) TestConfigRewriteWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()
	sections := []constants.Section{constants.Server}

	// info with option or with multiple options without route
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: nil,
	}
	response, err := client.InfoWithOptions(context.Background(), opts)
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
			responseRewrite, err := client.ConfigRewrite(context.Background())
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
	response, err = client.InfoWithOptions(context.Background(), opts)
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
		responseRewrite, err := client.ConfigRewrite(context.Background())
		assert.NoError(t, err)
		assert.Equal(t, "OK", responseRewrite)
	}

	// default sections, multi node route
	opts = options.ClusterInfoOptions{
		InfoOptions: nil,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	response, err = client.InfoWithOptions(context.Background(), opts)
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
			responseRewrite, err := client.ConfigRewrite(context.Background())
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
	result, err := client.RandomKey(context.Background())
	assert.Nil(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) TestRandomKeyWithRoute() {
	client := suite.defaultClusterClient()
	// Test 1: Check if the command return random key
	t := suite.T()
	route := config.Route(config.RandomRoute)
	options := options.RouteOption{Route: route}
	result, err := client.RandomKeyWithRoute(context.Background(), options)
	assert.NoError(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) TestFunctionCommandsWithRoute() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

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
	result, err := client.FunctionFlushSyncWithRoute(context.Background(), route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Load function with single node route
	result, err = client.FunctionLoadWithRoute(context.Background(), code, false, route)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Test FCALL with single node route
	functionResult, err := client.FCallWithArgsWithRoute(context.Background(), funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FCALL_RO with single node route
	functionResult, err = client.FCallReadOnlyWithArgsWithRoute(context.Background(), funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FunctionList with WithCode and query for all libraries
	query := models.FunctionListQuery{
		WithCode: true,
	}
	functionList, err := client.FunctionListWithRoute(context.Background(), query, route)
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
	result, err = client.FunctionLoadWithRoute(context.Background(), anotherLib, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "anotherLib", result)

	deleteResult, err := client.FunctionDeleteWithRoute(context.Background(), "anotherLib", route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleteResult)

	// delete missing lib returns a error
	_, err = client.FunctionDeleteWithRoute(context.Background(), "anotherLib", route)
	suite.Error(err)

	// Test with all primaries route
	libName = "mylib1c_all"
	funcName = "myfunc1c_all"
	functions = map[string]string{
		funcName: "return args[1]",
	}
	code = GenerateLuaLibCode(libName, functions, true)

	// Flush all functions with SYNC option and all primaries route
	route = options.RouteOption{Route: config.AllPrimaries}
	result, err = client.FunctionFlushSyncWithRoute(context.Background(), route)
	suite.verifyOK(result, err)

	// Load function with all primaries route
	result, err = client.FunctionLoadWithRoute(context.Background(), code, false, route)
	suite.NoError(err)
	suite.Equal(libName, result)

	// Test FCALL with all primaries route
	functionResult, err = client.FCallWithArgsWithRoute(context.Background(), funcName, []string{"one", "two"}, route)
	suite.NoError(err)
	if functionResult.IsSingleValue() {
		suite.Equal("one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			suite.Equal("one", value)
		}
	}

	// Test FCALL_RO with all primaries route
	functionResult, err = client.FCallReadOnlyWithArgsWithRoute(context.Background(), funcName, []string{"one", "two"}, route)
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FunctionList with WithCode and query for all libraries
	functionList, err = client.FunctionListWithRoute(context.Background(), query, route)
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
	result, err = client.FunctionLoadWithRoute(context.Background(), anotherLib, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "anotherLib", result)

	deleteResult, err = client.FunctionDeleteWithRoute(context.Background(), "anotherLib", route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleteResult)

	// delete missing lib returns a error
	_, err = client.FunctionDeleteWithRoute(context.Background(), "anotherLib", route)
	suite.Error(err)
}

func (suite *GlideTestSuite) TestFunctionCommandsWithoutKeysAndWithoutRoute() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClusterClient()
	t := suite.T()

	// Flush all functions with SYNC option
	result, err := client.FunctionFlushSync(context.Background())
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
	result, err = client.FunctionLoad(context.Background(), code, false)
	assert.NoError(t, err)
	assert.Equal(t, libName, result)

	// Test FCALL
	functionResult, err := client.FCallWithArgs(context.Background(), funcName, []string{"one", "two"})
	assert.NoError(t, err)
	if functionResult.IsSingleValue() {
		assert.Equal(t, "one", functionResult.SingleValue())
	} else {
		for _, value := range functionResult.MultiValue() {
			assert.Equal(t, "one", value)
		}
	}

	// Test FCALL_RO
	functionResult, err = client.FCallReadOnlyWithArgs(context.Background(), funcName, []string{"one", "two"})
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
	result, err = client.FunctionLoad(context.Background(), anotherLib, true)
	suite.NoError(err)
	suite.Equal("anotherLib", result)

	deleteResult, err := client.FunctionDelete(context.Background(), "anotherLib")
	suite.verifyOK(deleteResult, err)

	// delete missing lib returns a error
	_, err = client.FunctionDelete(context.Background(), "anotherLib")
	suite.Error(err)
}

func (suite *GlideTestSuite) TestFunctionStatsWithoutRoute() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClusterClient()
	t := suite.T()

	// Flush all functions with SYNC option
	result, err := client.FunctionFlushSync(context.Background())
	suite.verifyOK(result, err)

	// Load first function
	libName := "functionStats_without_route"
	funcName := libName
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, false)
	result, err = client.FunctionLoad(context.Background(), code, true)
	suite.NoError(err)
	suite.Equal(libName, result)

	// Check stats after loading first function
	stats, err := client.FunctionStats(context.Background())
	suite.NoError(err)
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
	result, err = client.FunctionLoad(context.Background(), code2, true)
	suite.NoError(err)
	suite.Equal(libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStats(context.Background())
	suite.NoError(err)
	for _, nodeStats := range stats {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(3), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(2), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSync(context.Background())
	suite.verifyOK(result, err)

	// Check stats after flushing
	stats, err = client.FunctionStats(context.Background())
	suite.NoError(err)
	for _, nodeStats := range stats {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].LibraryCount)
	}
}

func (suite *GlideTestSuite) TestFunctionStatsWithRoute() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

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
	result, err := client.FunctionFlushSyncWithRoute(context.Background(), route)
	suite.verifyOK(result, err)

	// Load function with single node route
	result, err = client.FunctionLoadWithRoute(context.Background(), code, true, route)
	suite.NoError(err)
	suite.Equal(libName, result)

	// Check stats with single node route
	stats, err := client.FunctionStatsWithRoute(context.Background(), route)
	suite.NoError(err)
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
	result, err = client.FunctionLoadWithRoute(context.Background(), code2, true, route)
	suite.NoError(err)
	suite.Equal(libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStatsWithRoute(context.Background(), route)
	suite.NoError(err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(3), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(2), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSyncWithRoute(context.Background(), route)
	suite.verifyOK(result, err)

	// Check stats after flushing
	stats, err = client.FunctionStatsWithRoute(context.Background(), route)
	suite.NoError(err)
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
	result, err = client.FunctionFlushSyncWithRoute(context.Background(), route)
	suite.verifyOK(result, err)

	// Load function with all primaries route
	result, err = client.FunctionLoadWithRoute(context.Background(), code, true, route)
	suite.NoError(err)
	suite.Equal(libName, result)

	// Check stats with all primaries route
	stats, err = client.FunctionStatsWithRoute(context.Background(), route)
	suite.NoError(err)
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
	result, err = client.FunctionLoadWithRoute(context.Background(), code2, true, route)
	assert.NoError(t, err)
	assert.Equal(t, libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStatsWithRoute(context.Background(), route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(3), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(2), nodeStats.Engines["LUA"].LibraryCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSyncWithRoute(context.Background(), route)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Check stats after flushing
	stats, err = client.FunctionStatsWithRoute(context.Background(), route)
	assert.NoError(t, err)
	for _, nodeStats := range stats.MultiValue() {
		assert.Empty(t, nodeStats.RunningScript.Name)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].FunctionCount)
		assert.Equal(t, int64(0), nodeStats.Engines["LUA"].LibraryCount)
	}
}

func (suite *GlideTestSuite) TestFunctionKilWithoutRoute() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClusterClient()

	// Flush before setup
	result, err := client.FunctionFlushSync(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing loaded, nothing to kill
	_, err = client.FunctionKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestFunctionKillWithRoute() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClusterClient()

	// key for routing to a primary node
	randomKey := uuid.NewString()
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, randomKey),
	}

	// Flush all functions with route
	result, err := client.FunctionFlushSyncWithRoute(context.Background(), route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestLongTimeoutFunctionKillNoWriteWithoutRoute() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}
	suite.testFunctionKillNoWrite(false)
}

func (suite *GlideTestSuite) TestLongTimeoutFunctionKillNoWriteWithRoute() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}
	suite.testFunctionKillNoWrite(true)
}

func (suite *GlideTestSuite) testFunctionKillNoWrite(withRoute bool) {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

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
		result, err = client.FunctionFlushSyncWithRoute(context.Background(), route)
	} else {
		result, err = client.FunctionFlushSync(context.Background())
	}
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	if withRoute {
		_, err = client.FunctionKillWithRoute(context.Background(), route)
	} else {
		_, err = client.FunctionKill(context.Background())
	}
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	if withRoute {
		result, err = client.FunctionLoadWithRoute(context.Background(), code, true, route)
	} else {
		result, err = client.FunctionLoad(context.Background(), code, true)
	}
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10 * time.Second)
	testClient, err := suite.clusterClient(testConfig)
	require.NoError(suite.T(), err)
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
					result, err = client.FunctionKillWithRoute(context.Background(), route)
				} else {
					result, err = client.FunctionKill(context.Background())
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
		_, err = testClient.FCallWithRoute(context.Background(), funcName, route)
	} else {
		_, err = testClient.FCall(context.Background(), funcName)
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
				_, err = client.FunctionKillWithRoute(context.Background(), route)
			} else {
				_, err = client.FunctionKill(context.Background())
			}
			if err != nil && strings.Contains(strings.ToLower(err.Error()), "notbusy") {
				return
			}
		}
	}
}

func (suite *GlideTestSuite) TestLongTimeoutFunctionKillKeyBasedWriteFunction() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}

	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

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
	result, err := client.FunctionFlushSyncWithRoute(context.Background(), route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoadWithRoute(context.Background(), code, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10 * time.Second)
	testClient, err := suite.clusterClient(testConfig)
	require.NoError(suite.T(), err)
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
				_, err = client.FunctionKillWithRoute(context.Background(), route)
				// Look for unkillable error
				if err != nil && strings.Contains(strings.ToLower(err.Error()), "unkillable") {
					unkillable <- true
					return
				}
			}
		}
	}()

	// Call the function with the key - this will block until completion
	testClient.FCallWithKeysAndArgs(context.Background(), funcName, []string{key}, []string{})
	// Function completed as expected

	// Wait for unkillable confirmation
	foundUnkillable := <-unkillable
	assert.True(suite.T(), foundUnkillable, "Function should be unkillable")
}

func (suite *GlideTestSuite) TestFunctionDumpAndRestoreCluster() {
	client := suite.defaultClusterClient()

	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	// Flush all functions first
	suite.verifyOK(client.FunctionFlushSync(context.Background()))

	// Dumping an empty lib
	emptyDump, err := client.FunctionDump(context.Background())
	suite.NoError(err)
	assert.NotNil(suite.T(), emptyDump)
	assert.Greater(suite.T(), len(emptyDump), 0)

	name1 := "Foster"
	libname1 := "FosterLib"
	name2 := "Dogster"
	libname2 := "DogsterLib"

	// function name1 returns first argument
	// function name2 returns argument array len
	code := GenerateLuaLibCode(libname1, map[string]string{
		name1: "return args[1]",
		name2: "return #args",
	}, true)

	// Load the functions
	loadResult, err := client.FunctionLoad(context.Background(), code, true)
	suite.NoError(err)
	assert.Equal(suite.T(), libname1, loadResult)

	// Dump the library
	dump, err := client.FunctionDump(context.Background())
	suite.NoError(err)

	// Restore without cleaning the lib and/or overwrite option causes an error
	_, err = client.FunctionRestore(context.Background(), dump)
	assert.NotNil(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Library "+libname1+" already exists")

	// APPEND policy also fails for the same reason (name collision)
	_, err = client.FunctionRestoreWithPolicy(context.Background(), dump, constants.AppendPolicy)
	assert.NotNil(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Library "+libname1+" already exists")

	// REPLACE policy succeeds
	suite.verifyOK(client.FunctionRestoreWithPolicy(context.Background(), dump, constants.ReplacePolicy))

	// Verify functions still work after replace
	result1, err := client.FCallReadOnlyWithArgs(context.Background(), name1, []string{"meow", "woem"})
	suite.NoError(err)
	if result1.IsSingleValue() {
		assert.Equal(suite.T(), "meow", result1.SingleValue())
	} else {
		for _, value := range result1.MultiValue() {
			assert.Equal(suite.T(), "meow", value)
		}
	}

	result2, err := client.FCallReadOnlyWithArgs(context.Background(), name2, []string{"meow", "woem"})
	suite.NoError(err)
	if result2.IsSingleValue() {
		assert.Equal(suite.T(), int64(2), result2.SingleValue())
	} else {
		for _, value := range result2.MultiValue() {
			assert.Equal(suite.T(), int64(2), value)
		}
	}

	// create lib with another name, but with the same function names
	suite.verifyOK(client.FunctionFlushSync(context.Background()))
	code = GenerateLuaLibCode(libname2, map[string]string{
		name1: "return args[1]",
		name2: "return #args",
	}, true)
	loadResult, err = client.FunctionLoad(context.Background(), code, true)
	suite.NoError(err)
	assert.Equal(suite.T(), libname2, loadResult)

	// REPLACE policy now fails due to a name collision
	_, err = client.FunctionRestoreWithPolicy(context.Background(), dump, constants.ReplacePolicy)
	assert.NotNil(suite.T(), err)
	errMsg := err.Error()
	// valkey checks names in random order and blames on first collision
	assert.True(suite.T(),
		strings.Contains(errMsg, "Function "+name1+" already exists") ||
			strings.Contains(errMsg, "Function "+name2+" already exists"))

	// FLUSH policy succeeds, but deletes the second lib
	suite.verifyOK(client.FunctionRestoreWithPolicy(context.Background(), dump, constants.FlushPolicy))

	// Verify original functions work again
	result1, err = client.FCallReadOnlyWithArgs(context.Background(), name1, []string{"meow", "woem"})
	suite.NoError(err)
	if result1.IsSingleValue() {
		assert.Equal(suite.T(), "meow", result1.SingleValue())
	} else {
		for _, value := range result1.MultiValue() {
			assert.Equal(suite.T(), "meow", value)
		}
	}

	result2, err = client.FCallReadOnlyWithArgs(context.Background(), name2, []string{"meow", "woem"})
	suite.NoError(err)
	if result2.IsSingleValue() {
		assert.Equal(suite.T(), int64(2), result2.SingleValue())
	} else {
		for _, value := range result2.MultiValue() {
			assert.Equal(suite.T(), int64(2), value)
		}
	}
}

func (suite *GlideTestSuite) TestInvokeScript() {
	clusterClient := suite.defaultClusterClient()
	key1 := uuid.New().String()
	key2 := uuid.New().String()

	script1 := options.NewScript("return 'Hello'")
	routeOption := options.RouteOption{Route: config.AllPrimaries}
	// Test simple script that returns a string
	clusterResponse, err := clusterClient.InvokeScriptWithRoute(context.Background(), *script1, routeOption)
	suite.NoError(err)
	for _, value := range clusterResponse.MultiValue() {
		assert.Equal(suite.T(), "Hello", value)
	}
	script1.Close()

	// Test script that sets a key with value
	script2 := options.NewScript("return redis.call('SET', KEYS[1], ARGV[1])")

	// Create ClusterScriptOptions for setting key1
	scriptOptions := options.NewScriptOptions()
	scriptOptions.WithKeys([]string{key1}).WithArgs([]string{"value1"})
	setResponse, err := clusterClient.InvokeScriptWithOptions(context.Background(), *script2, *scriptOptions)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", setResponse)

	// Set another key with the same script
	scriptOptions2 := options.NewScriptOptions()
	scriptOptions2.WithKeys([]string{key2}).WithArgs([]string{"value2"})
	setResponse2, err := clusterClient.InvokeScriptWithOptions(context.Background(), *script2, *scriptOptions2)
	assert.Equal(suite.T(), "OK", setResponse2)
	suite.NoError(err)
	script2.Close()

	// Test script that gets a key's value
	script3 := options.NewScript("return redis.call('GET', KEYS[1])")

	// Create ClusterScriptOptions for getting key1
	scriptOptions3 := options.NewScriptOptions()
	scriptOptions3.WithKeys([]string{key1})
	getResponse1, err := clusterClient.InvokeScriptWithOptions(context.Background(), *script3, *scriptOptions3)
	suite.NoError(err)
	assert.Equal(suite.T(), "value1", getResponse1)

	// Get another key's value
	scriptOptions4 := options.NewScriptOptions()
	scriptOptions4.WithKeys([]string{key2})
	getResponse2, err := clusterClient.InvokeScriptWithOptions(context.Background(), *script3, *scriptOptions4)
	assert.Equal(suite.T(), "value2", getResponse2)
	suite.NoError(err)
	script3.Close()
}

func (suite *GlideTestSuite) TestScriptExistsWithoutRoute() {
	client := suite.defaultClusterClient()

	script1 := options.NewScript("return 'Hello'")
	script2 := options.NewScript("return 'World'")

	// Load script1
	client.InvokeScript(context.Background(), *script1)

	expected := []bool{true, false, false}

	// Get the SHA1 digests of the scripts
	sha1_1 := script1.GetHash()
	sha1_2 := script2.GetHash()
	nonExistentSha1 := strings.Repeat("0", 40)

	// Ensure scripts exist
	response, err := client.ScriptExists(context.Background(), []string{sha1_1, sha1_2, nonExistentSha1})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), expected, response)

	script1.Close()
	script2.Close()
}

func (suite *GlideTestSuite) TestScriptExistsWithRoute() {
	client := suite.defaultClusterClient()
	route := options.RouteOption{Route: config.NewSlotKeyRoute(config.SlotTypePrimary, uuid.New().String())}

	script1 := options.NewScript("return 'Hello'")
	script2 := options.NewScript("return 'World'")
	script3 := options.NewScript("return 'Hello World'")

	// Load script1 and script3
	client.InvokeScript(context.Background(), *script1)
	client.InvokeScriptWithRoute(context.Background(), *script3, route)

	expected := []bool{true, false, true, false}

	// Get the SHA1 digests of the scripts
	sha1_1 := script1.GetHash()
	sha1_2 := script2.GetHash()
	sha1_3 := script3.GetHash()
	nonExistentSha1 := strings.Repeat("0", 40)

	// Ensure scripts exist
	response, err := client.ScriptExists(context.Background(), []string{sha1_1, sha1_2, sha1_3, nonExistentSha1})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), expected, response)

	routeResponse, err := client.ScriptExistsWithRoute(
		context.Background(),
		[]string{sha1_1, sha1_2, sha1_3, nonExistentSha1},
		route,
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), expected, routeResponse)

	script1.Close()
	script2.Close()
	script3.Close()
}

func (suite *GlideTestSuite) TestScriptFlushClusterClient() {
	client := suite.defaultClusterClient()

	// Create a script
	script := options.NewScript("return 'Hello'")

	// Load script
	_, err := client.InvokeScript(context.Background(), *script)
	suite.NoError(err)

	// Check existence of script
	scriptHash := script.GetHash()
	result, err := client.ScriptExists(context.Background(), []string{scriptHash})
	suite.NoError(err)
	assert.Equal(suite.T(), []bool{true}, result)

	// Flush the script cache
	flushResult, err := client.ScriptFlush(context.Background())
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", flushResult)

	// Create a script
	script = options.NewScript("return 'Hello'")
	routeOption := options.RouteOption{Route: config.AllPrimaries}

	// Load script
	_, err = client.InvokeScriptWithRoute(context.Background(), *script, routeOption)
	suite.NoError(err)

	// Check existence of script
	scriptHash = script.GetHash()
	result, err = client.ScriptExistsWithRoute(context.Background(), []string{scriptHash}, routeOption)
	suite.NoError(err)
	assert.Equal(suite.T(), []bool{true}, result)

	// Create ScriptFlushOptions with default mode (SYNC) and route
	scriptFlushOptions := options.NewScriptFlushOptions().WithRoute(&routeOption)

	// Flush the script cache
	flushResult, err = client.ScriptFlushWithOptions(context.Background(), *scriptFlushOptions)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", flushResult)

	// Check that the script no longer exists
	result, err = client.ScriptExistsWithRoute(context.Background(), []string{scriptHash}, routeOption)
	suite.NoError(err)
	assert.Equal(suite.T(), []bool{false}, result)

	// Test with ASYNC mode
	_, err = client.InvokeScriptWithRoute(context.Background(), *script, routeOption)
	suite.NoError(err)

	// Create ScriptFlushOptions with ASYNC mode and route
	scriptFlushOptions = options.NewScriptFlushOptions().
		WithMode(options.ASYNC).
		WithRoute(&routeOption)

	flushResult, err = client.ScriptFlushWithOptions(context.Background(), *scriptFlushOptions)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", flushResult)

	result, err = client.ScriptExistsWithRoute(context.Background(), []string{scriptHash}, routeOption)
	suite.NoError(err)
	assert.Equal(suite.T(), []bool{false}, result)

	script.Close()
}

func (suite *GlideTestSuite) TestScriptKillWithoutRoute() {
	invokeClient, err := suite.clusterClient(suite.defaultClusterClientConfig())
	require.NoError(suite.T(), err)
	killClient := suite.defaultClusterClient()

	// Flush before setup
	result, err := invokeClient.ScriptFlush(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing loaded, nothing to kill
	_, err = killClient.ScriptKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestScriptKillWithRoute() {
	suite.T().Skip("Flaky Test: Wait until #2277 is resolved")

	invokeClient, err := suite.clusterClient(suite.defaultClusterClientConfig())
	require.NoError(suite.T(), err)
	killClient := suite.defaultClusterClient()

	// key for routing to a primary node
	randomKey := uuid.NewString()
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, randomKey),
	}

	// Ensure no script is running at the beginning
	_, err = killClient.ScriptKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Kill Running Code
	code := CreateLongRunningLuaScript(6, true)
	script := options.NewScript(code)

	go invokeClient.InvokeScriptWithRoute(context.Background(), *script, route)

	time.Sleep(1 * time.Second)

	result, err := killClient.ScriptKillWithRoute(context.Background(), route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)
	script.Close()

	time.Sleep(1 * time.Second)

	// Ensure no script is running at the end
	_, err = killClient.ScriptKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestScriptKillUnkillableWithoutRoute() {
	key := uuid.NewString()
	invokeClient, err := suite.clusterClient(suite.defaultClusterClientConfig())
	require.NoError(suite.T(), err)
	killClient := suite.defaultClusterClient()

	// Ensure no script is running at the beginning
	_, err = killClient.ScriptKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	code := CreateLongRunningLuaScript(7, false)
	script := options.NewScript(code)

	go invokeClient.InvokeScriptWithOptions(context.Background(), *script, *options.NewScriptOptions().WithKeys([]string{key}))

	time.Sleep(3 * time.Second)

	_, err = killClient.ScriptKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "unkillable"))
	script.Close()

	// Wait until script finishes
	time.Sleep(4 * time.Second)

	// Ensure no script is running at the end
	_, err = killClient.ScriptKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestScriptKillUnkillableWithRoute() {
	suite.T().Skip("Flaky Test: Wait until #2277 is resolved")

	key := uuid.NewString()
	invokeClient, err := suite.clusterClient(suite.defaultClusterClientConfig())
	require.NoError(suite.T(), err)
	killClient := suite.defaultClusterClient()

	// key for routing to a primary node
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, key),
	}

	// Ensure no script is running at the beginning
	_, err = killClient.ScriptKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Kill Running Code
	code := CreateLongRunningLuaScript(7, false)
	script := options.NewScript(code)

	go invokeClient.InvokeScriptWithOptions(context.Background(), *script, *options.NewScriptOptions().WithKeys([]string{key}))

	time.Sleep(1 * time.Second)

	_, err = killClient.ScriptKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "unkillable"))
	script.Close()

	// Wait until script finishes
	time.Sleep(6 * time.Second)

	// Ensure no script is running at the end
	_, err = killClient.ScriptKillWithRoute(context.Background(), route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) TestRetryStrategyIsNotSupportedForTransactions() {
	_, err := suite.defaultClusterClient().ExecWithOptions(
		context.Background(),
		*pipeline.NewClusterBatch(true),
		true,
		*pipeline.NewClusterBatchOptions().WithRetryStrategy(*pipeline.NewClusterBatchRetryStrategy()),
	)
	suite.Error(err)
}

func (suite *GlideTestSuite) TestBatchWithSingleNodeRoute() {
	client := suite.defaultClusterClient()
	opts := pipeline.NewClusterBatchOptions()

	for _, isAtomic := range []bool{true, false} {
		batch := pipeline.NewClusterBatch(isAtomic).
			InfoWithOptions(options.InfoOptions{Sections: []constants.Section{"replication"}})

		res, err := client.ExecWithOptions(
			context.Background(),
			*batch,
			true,
			*opts.WithRoute(config.NewSlotKeyRoute(config.SlotTypePrimary, "abc")),
		)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), res[0], "role:master", "isAtomic = %v", isAtomic)

		res, err = client.ExecWithOptions(
			context.Background(),
			*batch,
			true,
			*opts.WithRoute(config.NewSlotKeyRoute(config.SlotTypeReplica, "abc")),
		)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), res[0], "role:slave", "isAtomic = %v", isAtomic)

		res, err = client.ExecWithOptions(
			context.Background(),
			*batch,
			true,
			*opts.WithRoute(config.NewSlotIdRoute(config.SlotTypePrimary, 42)),
		)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), res[0], "role:master", "isAtomic = %v", isAtomic)

		res, err = client.ExecWithOptions(
			context.Background(),
			*batch,
			true,
			*opts.WithRoute(config.NewSlotIdRoute(config.SlotTypeReplica, 42)),
		)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), res[0], "role:slave", "isAtomic = %v", isAtomic)

		res, err = client.ExecWithOptions(
			context.Background(),
			*batch,
			true,
			*opts.WithRoute(config.NewByAddressRoute(suite.clusterHosts[0].Host, int32(suite.clusterHosts[0].Port))),
		)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), res[0], "# Replication", "isAtomic = %v", isAtomic)
	}
}

func (suite *GlideTestSuite) TestClusterSelect_WithValidIndex() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	client := suite.defaultClusterClient()
	index := int64(1)
	suite.verifyOK(client.Select(context.Background(), index))

	key := uuid.New().String()
	value := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, value))

	res, err := client.Get(context.Background(), key)
	suite.NoError(err)
	assert.Equal(suite.T(), value, res.Value())
}

func (suite *GlideTestSuite) TestClusterSelect_InvalidIndex_OutOfBounds() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	client := suite.defaultClusterClient()

	result, err := client.Select(context.Background(), -1)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)

	result, err = client.Select(context.Background(), 1000)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
}

func (suite *GlideTestSuite) TestClusterSelect_SwitchBetweenDatabases() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	client := suite.defaultClusterClient()

	key1 := uuid.New().String()
	value1 := uuid.New().String()
	suite.verifyOK(client.Select(context.Background(), 0))
	suite.verifyOK(client.Set(context.Background(), key1, value1))

	key2 := uuid.New().String()
	value2 := uuid.New().String()
	suite.verifyOK(client.Select(context.Background(), 1))
	suite.verifyOK(client.Set(context.Background(), key2, value2))

	result, err := client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Equal(suite.T(), "", result.Value())

	suite.verifyOK(client.Select(context.Background(), 0))
	result, err = client.Get(context.Background(), key2)
	suite.NoError(err)
	assert.Equal(suite.T(), "", result.Value())

	suite.verifyOK(client.Select(context.Background(), 1))
	result, err = client.Get(context.Background(), key2)
	suite.NoError(err)
	assert.Equal(suite.T(), value2, result.Value())
}

func (suite *GlideTestSuite) TestClusterSelectWithOptions_AllPrimaries() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	client := suite.defaultClusterClient()
	opts := options.RouteOption{Route: config.AllPrimaries}

	result, err := client.SelectWithOptions(context.Background(), 1, opts)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", result)

	// Verify the database was selected on all nodes by setting and getting a key
	key := uuid.New().String()
	value := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, value))

	res, err := client.Get(context.Background(), key)
	suite.NoError(err)
	assert.Equal(suite.T(), value, res.Value())
}

func (suite *GlideTestSuite) TestClusterSelectWithOptions_RandomRoute() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	client := suite.defaultClusterClient()
	opts := options.RouteOption{Route: config.RandomRoute}

	result, err := client.SelectWithOptions(context.Background(), 0, opts)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", result)
}
