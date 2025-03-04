// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api/config"
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
