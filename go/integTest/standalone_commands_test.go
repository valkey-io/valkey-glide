// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"math/rand"
	"strconv"
	"strings"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func (suite *GlideTestSuite) TestCustomCommandInfo() {
	client := suite.defaultClient()
	result, err := client.CustomCommand(context.Background(), []string{"INFO"})

	suite.NoError(err)
	assert.IsType(suite.T(), "", result)
	strResult := result.(string)
	assert.True(suite.T(), strings.Contains(strResult, "# Stats"))
}

func (suite *GlideTestSuite) TestCustomCommandPing_StringResponse() {
	client := suite.defaultClient()
	result, err := client.CustomCommand(context.Background(), []string{"PING"})

	suite.NoError(err)
	assert.Equal(suite.T(), "PONG", result.(string))
}

func (suite *GlideTestSuite) TestCustomCommandClientInfo() {
	clientName := "TEST_CLIENT_NAME"
	config := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithClientName(clientName)
	client, err := suite.client(config)
	require.NoError(suite.T(), err)

	result, err := client.CustomCommand(context.Background(), []string{"CLIENT", "INFO"})

	suite.NoError(err)
	assert.IsType(suite.T(), "", result)
	strResult := result.(string)
	assert.True(suite.T(), strings.Contains(strResult, fmt.Sprintf("name=%s", clientName)))
}

func (suite *GlideTestSuite) TestCustomCommandGet_NullResponse() {
	client := suite.defaultClient()
	key := uuid.New().String()
	result, err := client.CustomCommand(context.Background(), []string{"GET", key})

	suite.NoError(err)
	assert.Equal(suite.T(), nil, result)
}

func (suite *GlideTestSuite) TestCustomCommandDel_LongResponse() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, "value"))
	result, err := client.CustomCommand(context.Background(), []string{"DEL", key})

	suite.NoError(err)
	assert.Equal(suite.T(), int64(1), result.(int64))
}

func (suite *GlideTestSuite) TestCustomCommandHExists_BoolResponse() {
	client := suite.defaultClient()
	fields := map[string]string{"field1": "value1"}
	key := uuid.New().String()

	res1, err := client.HSet(context.Background(), key, fields)
	suite.NoError(err)
	assert.Equal(suite.T(), int64(1), res1)

	result, err := client.CustomCommand(context.Background(), []string{"HEXISTS", key, "field1"})

	suite.NoError(err)
	assert.Equal(suite.T(), true, result.(bool))
}

func (suite *GlideTestSuite) TestCustomCommandIncrByFloat_FloatResponse() {
	client := suite.defaultClient()
	key := uuid.New().String()

	result, err := client.CustomCommand(context.Background(), []string{"INCRBYFLOAT", key, fmt.Sprintf("%f", 0.1)})

	suite.NoError(err)
	assert.Equal(suite.T(), float64(0.1), result.(float64))
}

func (suite *GlideTestSuite) TestCustomCommandMGet_ArrayResponse() {
	clientName := "TEST_CLIENT_NAME"
	config := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithClientName(clientName)
	client, err := suite.client(config)
	require.NoError(suite.T(), err)

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	key3 := uuid.New().String()
	oldValue := uuid.New().String()
	value := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key1, oldValue))
	keyValueMap := map[string]string{
		key1: value,
		key2: value,
	}
	suite.verifyOK(client.MSet(context.Background(), keyValueMap))
	keys := []string{key1, key2, key3}
	values := []any{value, value, nil}
	result, err := client.CustomCommand(context.Background(), append([]string{"MGET"}, keys...))

	suite.NoError(err)
	assert.Equal(suite.T(), values, result.([]any))
}

func (suite *GlideTestSuite) TestCustomCommandConfigGet_MapResponse() {
	client := suite.defaultClient()

	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	configMap := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	suite.verifyOK(client.ConfigSet(context.Background(), configMap))

	result2, err := client.CustomCommand(context.Background(), []string{"CONFIG", "GET", "timeout", "maxmemory"})
	suite.NoError(err)
	assert.Equal(suite.T(), map[string]any{"timeout": "1000", "maxmemory": "1073741824"}, result2)
}

func (suite *GlideTestSuite) TestCustomCommandConfigSMembers_SetResponse() {
	client := suite.defaultClient()
	key := uuid.NewString()
	members := []string{"member1", "member2", "member3"}

	res1, err := client.SAdd(context.Background(), key, members)
	suite.NoError(err)
	assert.Equal(suite.T(), int64(3), res1)

	result2, err := client.CustomCommand(context.Background(), []string{"SMEMBERS", key})
	suite.NoError(err)
	assert.Equal(suite.T(), map[string]struct{}{"member1": {}, "member2": {}, "member3": {}}, result2)
}

func (suite *GlideTestSuite) TestCustomCommand_invalidCommand() {
	client := suite.defaultClient()
	_, err := client.CustomCommand(context.Background(), []string{"pewpew"})

	suite.Error(err)
}

func (suite *GlideTestSuite) TestCustomCommand_invalidArgs() {
	client := suite.defaultClient()
	_, err := client.CustomCommand(context.Background(), []string{"ping", "pang", "pong"})

	suite.Error(err)
}

func (suite *GlideTestSuite) TestCustomCommand_closedClient() {
	client := suite.defaultClient()
	client.Close()

	_, err := client.CustomCommand(context.Background(), []string{"ping"})

	suite.Error(err)
}

func (suite *GlideTestSuite) TestConfigSetAndGet_multipleArgs() {
	client := suite.defaultClient()

	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	configMap := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	resultConfigMap := map[string]string{"timeout": "1000", "maxmemory": "1073741824"}
	suite.verifyOK(client.ConfigSet(context.Background(), configMap))

	result2, err := client.ConfigGet(context.Background(), []string{"timeout", "maxmemory"})
	suite.NoError(err)
	assert.Equal(suite.T(), resultConfigMap, result2)
}

func (suite *GlideTestSuite) TestConfigSetAndGet_noArgs() {
	client := suite.defaultClient()

	configMap := map[string]string{}

	_, err := client.ConfigSet(context.Background(), configMap)
	suite.Error(err)

	_, err = client.ConfigGet(context.Background(), []string{})
	suite.Error(err)
}

func (suite *GlideTestSuite) TestConfigSetAndGet_invalidArgs() {
	client := suite.defaultClient()

	configMap := map[string]string{"time": "1000"}

	_, err := client.ConfigSet(context.Background(), configMap)
	suite.Error(err)

	result2, err := client.ConfigGet(context.Background(), []string{"time"})
	suite.Equal(map[string]string{}, result2)
	suite.NoError(err)
}

func (suite *GlideTestSuite) TestSelect_WithValidIndex() {
	client := suite.defaultClient()
	index := int64(1)
	suite.verifyOK(client.Select(context.Background(), index))

	key := uuid.New().String()
	value := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, value))

	res, err := client.Get(context.Background(), key)
	suite.NoError(err)
	assert.Equal(suite.T(), value, res.Value())
}

func (suite *GlideTestSuite) TestSelect_InvalidIndex_OutOfBounds() {
	client := suite.defaultClient()

	result, err := client.Select(context.Background(), -1)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)

	result, err = client.Select(context.Background(), 1000)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
}

func (suite *GlideTestSuite) TestSelect_SwitchBetweenDatabases() {
	client := suite.defaultClient()

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

func (suite *GlideTestSuite) TestSortReadOnlyWithOptions_ExternalWeights() {
	client := suite.defaultClient()
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	key := uuid.New().String()
	client.LPush(context.Background(), key, []string{"item1", "item2", "item3"})

	client.Set(context.Background(), "weight_item1", "3")
	client.Set(context.Background(), "weight_item2", "1")
	client.Set(context.Background(), "weight_item3", "2")

	options := options.NewSortOptions().
		SetByPattern("weight_*").
		SetOrderBy(options.ASC).
		SetIsAlpha(false)

	sortResult, err := client.SortReadOnlyWithOptions(context.Background(), key, *options)

	suite.NoError(err)
	resultList := []models.Result[string]{
		models.CreateStringResult("item2"),
		models.CreateStringResult("item3"),
		models.CreateStringResult("item1"),
	}
	assert.Equal(suite.T(), resultList, sortResult)
}

func (suite *GlideTestSuite) TestSortReadOnlyWithOptions_GetPatterns() {
	client := suite.defaultClient()
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	key := uuid.New().String()
	client.LPush(context.Background(), key, []string{"item1", "item2", "item3"})

	client.Set(context.Background(), "object_item1", "Object_1")
	client.Set(context.Background(), "object_item2", "Object_2")
	client.Set(context.Background(), "object_item3", "Object_3")

	options := options.NewSortOptions().
		SetByPattern("weight_*").
		SetOrderBy(options.ASC).
		SetIsAlpha(false).
		AddGetPattern("object_*")

	sortResult, err := client.SortReadOnlyWithOptions(context.Background(), key, *options)

	suite.NoError(err)

	resultList := []models.Result[string]{
		models.CreateStringResult("Object_1"),
		models.CreateStringResult("Object_2"),
		models.CreateStringResult("Object_3"),
	}

	assert.Equal(suite.T(), resultList, sortResult)
}

func (suite *GlideTestSuite) TestSortReadOnlyWithOptions_SuccessfulSortByWeightAndGet() {
	client := suite.defaultClient()
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	key := uuid.New().String()
	client.LPush(context.Background(), key, []string{"item1", "item2", "item3"})

	client.Set(context.Background(), "weight_item1", "10")
	client.Set(context.Background(), "weight_item2", "5")
	client.Set(context.Background(), "weight_item3", "15")

	client.Set(context.Background(), "object_item1", "Object 1")
	client.Set(context.Background(), "object_item2", "Object 2")
	client.Set(context.Background(), "object_item3", "Object 3")

	options := options.NewSortOptions().
		SetOrderBy(options.ASC).
		SetIsAlpha(false).
		SetByPattern("weight_*").
		AddGetPattern("object_*").
		AddGetPattern("#")

	sortResult, err := client.SortReadOnlyWithOptions(context.Background(), key, *options)

	suite.NoError(err)

	resultList := []models.Result[string]{
		models.CreateStringResult("Object 2"),
		models.CreateStringResult("item2"),
		models.CreateStringResult("Object 1"),
		models.CreateStringResult("item1"),
		models.CreateStringResult("Object 3"),
		models.CreateStringResult("item3"),
	}

	assert.Equal(suite.T(), resultList, sortResult)

	objectItem2, err := client.Get(context.Background(), "object_item2")
	suite.NoError(err)
	assert.Equal(suite.T(), "Object 2", objectItem2.Value())

	objectItem1, err := client.Get(context.Background(), "object_item1")
	suite.NoError(err)
	assert.Equal(suite.T(), "Object 1", objectItem1.Value())

	objectItem3, err := client.Get(context.Background(), "object_item3")
	suite.NoError(err)
	assert.Equal(suite.T(), "Object 3", objectItem3.Value())

	assert.Equal(suite.T(), "item2", sortResult[1].Value())
	assert.Equal(suite.T(), "item1", sortResult[3].Value())
	assert.Equal(suite.T(), "item3", sortResult[5].Value())
}

func (suite *GlideTestSuite) TestInfoStandalone() {
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

	client := suite.defaultClient()
	t := suite.T()

	// info without options
	info, err := client.Info(context.Background())
	assert.NoError(t, err)
	for _, section := range DEFAULT_INFO_SECTIONS {
		assert.Contains(t, info, "# "+section, "Section "+section+" is missing")
	}

	// info with option or with multiple options
	sections := []constants.Section{constants.Cpu}
	if suite.serverVersion >= "7.0.0" {
		sections = append(sections, constants.Memory)
	}
	info, err = client.InfoWithOptions(context.Background(), options.InfoOptions{Sections: sections})
	assert.NoError(t, err)
	for _, section := range sections {
		assert.Contains(t, strings.ToLower(info), strings.ToLower("# "+string(section)), "Section "+section+" is missing")
	}
}

func (suite *GlideTestSuite) TestDBSize() {
	client := suite.defaultClient()
	result, err := client.DBSize(context.Background())
	suite.NoError(err)
	assert.GreaterOrEqual(suite.T(), result, int64(0))
}

func (suite *GlideTestSuite) TestPing_NoArgument() {
	client := suite.defaultClient()

	result, err := client.Ping(context.Background())
	suite.NoError(err)
	assert.Equal(suite.T(), "PONG", result)
}

func (suite *GlideTestSuite) TestEcho() {
	client := suite.defaultClient()
	// Test 1: Check if Echo command return the message
	value := "Hello world"
	t := suite.T()
	resultEcho, err := client.Echo(context.Background(), value)
	assert.Nil(t, err)
	assert.Equal(t, value, resultEcho.Value())
}

func (suite *GlideTestSuite) TestPing_ClosedClient() {
	client := suite.defaultClient()
	client.Close()

	result, err := client.Ping(context.Background())

	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestPingWithOptions_WithMessage() {
	client := suite.defaultClient()
	options := options.PingOptions{
		Message: "hello",
	}

	result, err := client.PingWithOptions(context.Background(), options)
	suite.NoError(err)
	assert.Equal(suite.T(), "hello", result)
}

func (suite *GlideTestSuite) TestPingWithOptions_ClosedClient() {
	client := suite.defaultClient()
	client.Close()

	options := options.PingOptions{
		Message: "hello",
	}

	result, err := client.PingWithOptions(context.Background(), options)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestTime_Success() {
	client := suite.defaultClient()
	results, err := client.Time(context.Background())

	suite.NoError(err)
	assert.Len(suite.T(), results, 2)

	now := time.Now().Unix() - 1

	timestamp, err := strconv.ParseInt(results[0], 10, 64)
	suite.NoError(err)
	assert.Greater(suite.T(), timestamp, now)

	microseconds, err := strconv.ParseInt(results[1], 10, 64)
	suite.NoError(err)
	assert.Less(suite.T(), microseconds, int64(1000000))
}

func (suite *GlideTestSuite) TestTime_Error() {
	client := suite.defaultClient()

	// Disconnect the client or simulate an error condition
	client.Close()

	results, err := client.Time(context.Background())

	assert.NotNil(suite.T(), err)
	assert.Nil(suite.T(), results)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestFlushAll() {
	client := suite.defaultClient()
	key1 := uuid.New().String()
	key2 := uuid.New().String()

	_, err := client.Set(context.Background(), key1, "value1")
	suite.NoError(err)
	_, err = client.Set(context.Background(), key2, "value2")
	suite.NoError(err)

	result, err := client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Equal(suite.T(), "value1", result.Value())

	response, err := client.FlushAll(context.Background())
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", response)

	result, err = client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Empty(suite.T(), result.Value())
}

func (suite *GlideTestSuite) TestFlushAll_Sync() {
	client := suite.defaultClient()
	key1 := uuid.New().String()
	key2 := uuid.New().String()

	_, err := client.Set(context.Background(), key1, "value1")
	suite.NoError(err)
	_, err = client.Set(context.Background(), key2, "value2")
	suite.NoError(err)

	result, err := client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Equal(suite.T(), "value1", result.Value())

	response, err := client.FlushAllWithOptions(context.Background(), options.SYNC)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", response)

	result, err = client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Empty(suite.T(), result.Value())
}

func (suite *GlideTestSuite) TestFlushAll_Async() {
	client := suite.defaultClient()
	key1 := uuid.New().String()
	key2 := uuid.New().String()

	_, err := client.Set(context.Background(), key1, "value1")
	suite.NoError(err)
	_, err = client.Set(context.Background(), key2, "value2")
	suite.NoError(err)

	response, err := client.FlushAllWithOptions(context.Background(), options.ASYNC)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", response)

	result, err := client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Empty(suite.T(), result.Value())
}

func (suite *GlideTestSuite) TestFlushAll_ClosedClient() {
	client := suite.defaultClient()
	client.Close()

	response, err := client.FlushAllWithOptions(context.Background(), options.SYNC)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", response)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestFlushAll_MultipleFlush() {
	client := suite.defaultClient()
	key1 := uuid.New().String()

	response, err := client.FlushAllWithOptions(context.Background(), options.SYNC)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", response)

	_, err = client.Set(context.Background(), key1, "value1")
	suite.NoError(err)

	response, err = client.FlushAllWithOptions(context.Background(), options.ASYNC)
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", response)

	result, err := client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Empty(suite.T(), result.Value())
}

func (suite *GlideTestSuite) TestFlushDB() {
	client := suite.defaultClient()
	key1 := uuid.New().String()
	key2 := uuid.New().String()

	_, err := client.Set(context.Background(), key1, "value1")
	suite.NoError(err)
	_, err = client.Set(context.Background(), key2, "value2")
	suite.NoError(err)

	result, err := client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Equal(suite.T(), "value1", result.Value())

	response, err := client.FlushDB(context.Background())
	suite.NoError(err)
	assert.Equal(suite.T(), "OK", response)

	result, err = client.Get(context.Background(), key1)
	suite.NoError(err)
	assert.Empty(suite.T(), result.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_SYNC() {
	client := suite.defaultClient()

	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "value1")
	assert.NoError(suite.T(), err)

	result, err := client.FlushDBWithOptions(context.Background(), options.SYNC)
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_ASYNC() {
	client := suite.defaultClient()

	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "value1")
	assert.NoError(suite.T(), err)

	result, err := client.FlushDBWithOptions(context.Background(), options.ASYNC)
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_MultipleKeys() {
	client := suite.defaultClient()

	key1 := uuid.New().String()
	key2 := uuid.New().String()

	_, err := client.Set(context.Background(), key1, "value1")
	assert.NoError(suite.T(), err)
	_, err = client.Set(context.Background(), key2, "value2")
	assert.NoError(suite.T(), err)

	result, err := client.FlushDBWithOptions(context.Background(), options.SYNC)
	assert.NoError(suite.T(), err)
	assert.NotEmpty(suite.T(), result)

	val1, err := client.Get(context.Background(), key1)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val1.Value())

	val2, err := client.Get(context.Background(), key2)
	assert.NoError(suite.T(), err)
	assert.Empty(suite.T(), val2.Value())
}

func (suite *GlideTestSuite) TestFlushDBWithOptions_ClosedClient() {
	client := suite.defaultClient()

	client.Close()

	result, err := client.FlushDBWithOptions(context.Background(), options.SYNC)
	assert.NotNil(suite.T(), err)
	assert.Equal(suite.T(), "", result)
	assert.IsType(suite.T(), &glide.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPasswordAuthNonValidPass() {
	// Create test client
	testClient := suite.defaultClient()
	defer testClient.Close()

	// Test empty password
	_, err := testClient.UpdateConnectionPassword(context.Background(), "", true)
	suite.Error(err)

	// Test with no password parameter
	_, err = testClient.UpdateConnectionPassword(context.Background(), "", true)
	suite.Error(err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPassword_NoServerAuth() {
	// Create test client
	testClient := suite.defaultClient()
	defer testClient.Close()

	// Validate that we can use the client
	_, err := testClient.Info(context.Background())
	suite.NoError(err)

	// Test immediate re-authentication fails when no server password is set
	pwd := uuid.NewString()
	_, err = testClient.UpdateConnectionPassword(context.Background(), pwd, true)
	suite.Error(err)
}

func (suite *GlideTestSuite) TestUpdateConnectionPassword_LongPassword() {
	// Create test client
	testClient := suite.defaultClient()
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

func (suite *GlideTestSuite) TestUpdateConnectionPassword_ImmediateAuthWrongPassword() {
	// Create admin client
	adminClient := suite.defaultClient()
	defer adminClient.Close()

	// Create test client
	testClient := suite.defaultClient()
	defer testClient.Close()

	pwd := uuid.NewString()
	notThePwd := uuid.NewString()

	// Validate that we can use the client
	_, err := testClient.Info(context.Background())
	suite.NoError(err)

	// Set the password to something else
	_, err = adminClient.ConfigSet(context.Background(), map[string]string{"requirepass": notThePwd})
	suite.NoError(err)

	// Test that re-authentication fails when using wrong password
	_, err = testClient.UpdateConnectionPassword(context.Background(), pwd, true)
	suite.Error(err)

	// But using correct password returns OK
	_, err = testClient.UpdateConnectionPassword(context.Background(), notThePwd, true)
	suite.NoError(err)

	// Cleanup: Reset password
	_, err = adminClient.ConfigSet(context.Background(), map[string]string{"requirepass": ""})
	suite.NoError(err)
}

func (suite *GlideTestSuite) TestLolwutWithOptions_WithVersion() {
	client := suite.defaultClient()
	options := options.NewLolwutOptions(8)
	res, err := client.LolwutWithOptions(context.Background(), *options)
	assert.NoError(suite.T(), err)
	// Check for version string in LOLWUT output (dual contains approach)
	hasVer := strings.Contains(res, "ver")
	hasVersion := strings.Contains(res, suite.serverVersion)
	assert.True(suite.T(), hasVer && hasVersion,
		"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, res)
}

func (suite *GlideTestSuite) TestLolwutWithOptions_WithVersionAndArgs() {
	client := suite.defaultClient()
	opts := options.NewLolwutOptions(8).SetArgs([]int{10, 20})
	res, err := client.LolwutWithOptions(context.Background(), *opts)
	assert.NoError(suite.T(), err)
	// Check for version string in LOLWUT output (dual contains approach)
	hasVer := strings.Contains(res, "ver")
	hasVersion := strings.Contains(res, suite.serverVersion)
	assert.True(suite.T(), hasVer && hasVersion,
		"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, res)
}

func (suite *GlideTestSuite) TestLolwutWithOptions_EmptyArgs() {
	client := suite.defaultClient()
	opts := options.NewLolwutOptions(6).SetArgs([]int{})
	res, err := client.LolwutWithOptions(context.Background(), *opts)
	assert.NoError(suite.T(), err)
	// Check for version string in LOLWUT output (dual contains approach)
	hasVer := strings.Contains(res, "ver")
	hasVersion := strings.Contains(res, suite.serverVersion)
	assert.True(suite.T(), hasVer && hasVersion,
		"Expected output to contain 'ver' and version '%s', got: %s", suite.serverVersion, res)
}

func (suite *GlideTestSuite) TestClientId() {
	client := suite.defaultClient()
	result, err := client.ClientId(context.Background())
	suite.NoError(err)
	assert.Greater(suite.T(), result, int64(0))
}

func (suite *GlideTestSuite) TestLastSave() {
	client := suite.defaultClient()
	t := suite.T()
	result, err := client.LastSave(context.Background())
	assert.Nil(t, err)
	assert.Greater(t, result, int64(0))
}

func (suite *GlideTestSuite) TestConfigResetStat() {
	client := suite.defaultClient()
	suite.verifyOK(client.ConfigResetStat(context.Background()))
}

func (suite *GlideTestSuite) TestClientGetName() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.ClientGetName(context.Background())
	assert.Nil(t, err)
	assert.True(t, result.IsNil())
}

func (suite *GlideTestSuite) TestClientGetSetName() {
	client := suite.defaultClient()
	t := suite.T()

	suite.verifyOK(client.ClientSetName(context.Background(), "ConnectionName"))
	result, err := client.ClientGetName(context.Background())
	assert.Nil(t, err)
	assert.Equal(t, result.Value(), "ConnectionName")
}

func (suite *GlideTestSuite) TestMove() {
	client := suite.defaultClient()
	t := suite.T()
	key := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, "hello"))
	result, err := client.Move(context.Background(), key, 2)
	assert.Nil(t, err)
	assert.True(suite.T(), result)
}

func (suite *GlideTestSuite) TestScan() {
	client := suite.defaultClient()
	t := suite.T()
	key := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, "Hello"))
	result, err := client.Scan(context.Background(), models.NewCursor())
	assert.Nil(t, err)
	assert.GreaterOrEqual(t, len(result.Cursor.String()), 1)
	assert.GreaterOrEqual(t, len(result.Data), 1)
}

func (suite *GlideTestSuite) TestScanWithOption() {
	client := suite.defaultClient()
	t := suite.T()

	// Test TestScanWithOption SetCount
	key := uuid.New().String()
	suite.verifyOK(client.Set(context.Background(), key, "Hello"))
	opts := options.NewScanOptions().SetCount(10)
	result, err := client.ScanWithOptions(context.Background(), models.NewCursor(), *opts)
	assert.Nil(t, err)
	assert.GreaterOrEqual(t, len(result.Cursor.String()), 1)
	assert.GreaterOrEqual(t, len(result.Data), 1)

	// Test TestScanWithOption SetType
	opts = options.NewScanOptions().SetType(constants.ObjectTypeString)
	result, err = client.ScanWithOptions(context.Background(), models.NewCursor(), *opts)
	assert.Nil(t, err)
	assert.GreaterOrEqual(t, len(result.Cursor.String()), 1)
	assert.GreaterOrEqual(t, len(result.Data), 1)
}

func (suite *GlideTestSuite) TestConfigRewrite() {
	client := suite.defaultClient()
	t := suite.T()
	opts := options.InfoOptions{Sections: []constants.Section{constants.Server}}
	response, err := client.InfoWithOptions(context.Background(), opts)
	assert.NoError(t, err)
	lines := strings.Split(response, "\n")
	var configFile string
	for _, line := range lines {
		if strings.HasPrefix(line, "config_file:") {
			configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
			break
		}
	}
	if len(configFile) > 0 {
		suite.verifyOK(client.ConfigRewrite(context.Background()))
	}
}

func (suite *GlideTestSuite) TestRandomKey() {
	client := suite.defaultClient()
	// Test 1: Check if the command return random key
	t := suite.T()
	result, err := client.RandomKey(context.Background())
	assert.Nil(t, err)
	assert.NotNil(t, result)
}

func (suite *GlideTestSuite) TestFunctionCommandsStandalone() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClient()

	// Flush all functions with SYNC option
	result, err := client.FunctionFlushSync(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Generate and load function
	libName := "mylib1c"
	funcName := "myfunc1c"
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, true)
	result, err = client.FunctionLoad(context.Background(), code, false)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	// Test FCALL
	functionResult, err := client.FCallWithKeysAndArgs(context.Background(), funcName, []string{}, []string{"one", "two"})
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "one", functionResult)

	// Test FCALL_RO
	functionResult, err = client.FCallReadOnlyWithKeysAndArgs(
		context.Background(),
		funcName,
		[]string{},
		[]string{"one", "two"},
	)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "one", functionResult)

	// Test FunctionList
	query := models.FunctionListQuery{
		LibraryName: libName,
		WithCode:    false,
	}
	functionList, err := client.FunctionList(context.Background(), query)
	assert.NoError(suite.T(), err)
	assert.Len(suite.T(), functionList, 1)

	// Check library info
	libInfo := functionList[0]
	assert.Equal(suite.T(), libName, libInfo.Name)
	assert.Equal(suite.T(), "LUA", libInfo.Engine)

	// Check function info
	assert.Len(suite.T(), libInfo.Functions, 1)
	funcInfo := libInfo.Functions[0]
	assert.Equal(suite.T(), funcName, funcInfo.Name)
	assert.Empty(suite.T(), funcInfo.Description)
	assert.Contains(suite.T(), funcInfo.Flags, "no-writes")

	// Test FunctionList with WithCode and query for all libraries
	query = models.FunctionListQuery{
		WithCode: true,
	}
	functionList, err = client.FunctionList(context.Background(), query)
	assert.NoError(suite.T(), err)
	assert.Len(suite.T(), functionList, 1)

	// Check library info
	libInfo = functionList[0]
	assert.Equal(suite.T(), libName, libInfo.Name)
	assert.Equal(suite.T(), "LUA", libInfo.Engine)
	assert.Contains(suite.T(), libInfo.Code, libName) // Code should be present

	// load new lib and delete it - first lib remains loaded
	anotherLib := GenerateLuaLibCode("anotherLib", map[string]string{"anotherFunc": ""}, false)
	result, err = client.FunctionLoad(context.Background(), anotherLib, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "anotherLib", result)

	deleteResult, err := client.FunctionDelete(context.Background(), "anotherLib")
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", deleteResult)

	// delete missing lib returns a error
	_, err = client.FunctionDelete(context.Background(), "anotherLib")
	suite.Error(err)
}

func (suite *GlideTestSuite) TestFunctionStats() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClient()

	// Flush all functions with SYNC option
	result, err := client.FunctionFlushSync(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Load first function
	libName := "functionStats"
	funcName := libName
	functions := map[string]string{
		funcName: "return args[1]",
	}
	code := GenerateLuaLibCode(libName, functions, false)
	result, err = client.FunctionLoad(context.Background(), code, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	// Check stats after loading first function
	stats, err := client.FunctionStats(context.Background())
	assert.NoError(suite.T(), err)
	for _, nodeStats := range stats {
		assert.Empty(suite.T(), nodeStats.RunningScript.Name)
		assert.Equal(suite.T(), 1, len(nodeStats.Engines))
		assert.Equal(suite.T(), int64(1), nodeStats.Engines["LUA"].LibraryCount)
		assert.Equal(suite.T(), int64(1), nodeStats.Engines["LUA"].FunctionCount)
	}

	// Load second function with multiple functions
	libName2 := libName + "_2"
	functions2 := map[string]string{
		funcName + "_2": "return 'OK'",
		funcName + "_3": "return 42",
	}
	code2 := GenerateLuaLibCode(libName2, functions2, false)
	result, err = client.FunctionLoad(context.Background(), code2, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName2, result)

	// Check stats after loading second function
	stats, err = client.FunctionStats(context.Background())
	assert.NoError(suite.T(), err)
	for _, nodeStats := range stats {
		assert.Empty(suite.T(), nodeStats.RunningScript.Name)
		assert.Equal(suite.T(), 1, len(nodeStats.Engines))
		assert.Equal(suite.T(), int64(2), nodeStats.Engines["LUA"].LibraryCount)
		assert.Equal(suite.T(), int64(3), nodeStats.Engines["LUA"].FunctionCount)
	}

	// Flush all functions
	result, err = client.FunctionFlushSync(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Check stats after flushing
	stats, err = client.FunctionStats(context.Background())
	assert.NoError(suite.T(), err)
	for _, nodeStats := range stats {
		assert.Empty(suite.T(), nodeStats.RunningScript.Name)
		assert.Equal(suite.T(), 1, len(nodeStats.Engines))
		assert.Equal(suite.T(), int64(0), nodeStats.Engines["LUA"].LibraryCount)
		assert.Equal(suite.T(), int64(0), nodeStats.Engines["LUA"].FunctionCount)
	}
}

func (suite *GlideTestSuite) TestFunctionKill() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClient()

	// Flush all functions
	result, err := client.FunctionFlushSync(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}

func (suite *GlideTestSuite) testFunctionKill(readOnly bool) {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	client := suite.defaultClient()
	libName := "functionKill_no_write"
	funcName := "deadlock"
	key := libName
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, readOnly)

	// Flush all functions
	result, err := client.FunctionFlushSync(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoad(context.Background(), code, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClientConfig().WithRequestTimeout(10 * time.Second)
	testClient, err := suite.client(testConfig)
	require.NoError(suite.T(), err)
	defer testClient.Close()

	// Channel to signal when function is killed
	killed := make(chan bool)

	// Start a goroutine to kill the function
	go func() {
		defer close(killed)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(100 * time.Millisecond) // interval of 100ms for kill attempts
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				killed <- false
				return
			case <-killTicker.C:
				if readOnly {
					result, err = client.FunctionKill(context.Background())
					if err == nil {
						return
					}
				} else {
					result, err = client.FunctionKill(context.Background())
					if err != nil && strings.Contains(strings.ToLower(err.Error()), "unkillable") {
						return
					}
				}

			}
		}
	}()

	// Call the function - this should block until killed and return a script kill error
	_, err = testClient.FCallWithKeysAndArgs(context.Background(), funcName, []string{key}, []string{})
	if readOnly {
		assert.Error(suite.T(), err)
		assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "script killed"))
	} else {
		assert.NoError(suite.T(), err)
	}

	// Wait for the function to complete
	time.Sleep(6 * time.Second)
}

func (suite *GlideTestSuite) TestLongTimeoutFunctionKillNoWrite() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}
	suite.testFunctionKill(true)
}

func (suite *GlideTestSuite) TestLongTimeoutFunctionKillWrite() {
	if !*longTimeoutTests {
		suite.T().Skip("Timeout tests are disabled")
	}
	suite.testFunctionKill(false)
}

func (suite *GlideTestSuite) TestFunctionDumpAndRestore() {
	client := suite.defaultClient()

	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	// Flush all functions first
	suite.verifyOK(client.FunctionFlushSync(context.Background()))

	// Dumping an empty lib
	emptyDump, err := client.FunctionDump(context.Background())
	suite.NoError(err)
	assert.NotNil(suite.T(), emptyDump)
	assert.Greater(suite.T(), len(emptyDump), 0)

	name1 := "Foster"
	name2 := "Dogster"

	// function name1 returns first argument
	// function name2 returns argument array len
	code := GenerateLuaLibCode(name1, map[string]string{
		name1: "return args[1]",
		name2: "return #args",
	}, false)

	// Load the functions
	loadResult, err := client.FunctionLoad(context.Background(), code, true)
	suite.NoError(err)
	assert.Equal(suite.T(), name1, loadResult)

	// Verify functions work
	result1, err := client.FCallWithKeysAndArgs(context.Background(), name1, []string{}, []string{"meow", "woem"})
	suite.NoError(err)
	assert.Equal(suite.T(), "meow", result1)

	result2, err := client.FCallWithKeysAndArgs(context.Background(), name2, []string{}, []string{"meow", "woem"})
	suite.NoError(err)
	assert.Equal(suite.T(), int64(2), result2)

	// Dump the library
	dump, err := client.FunctionDump(context.Background())
	suite.NoError(err)

	// Restore without cleaning the lib and/or overwrite option causes an error
	_, err = client.FunctionRestore(context.Background(), dump)
	assert.NotNil(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Library "+name1+" already exists")

	// APPEND policy also fails for the same reason (name collision)
	_, err = client.FunctionRestoreWithPolicy(context.Background(), dump, constants.AppendPolicy)
	assert.NotNil(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Library "+name1+" already exists")

	// REPLACE policy succeeds
	suite.verifyOK(client.FunctionRestoreWithPolicy(context.Background(), dump, constants.ReplacePolicy))

	// Functions still work the same after replace
	result1, err = client.FCallWithKeysAndArgs(context.Background(), name1, []string{}, []string{"meow", "woem"})
	suite.NoError(err)
	assert.Equal(suite.T(), "meow", result1)

	result2, err = client.FCallWithKeysAndArgs(context.Background(), name2, []string{}, []string{"meow", "woem"})
	suite.NoError(err)
	assert.Equal(suite.T(), int64(2), result2)

	// create lib with another name, but with the same function names
	suite.verifyOK(client.FunctionFlushSync(context.Background()))
	code = GenerateLuaLibCode(name2, map[string]string{
		name1: "return args[1]",
		name2: "return #args",
	}, false)
	loadResult, err = client.FunctionLoad(context.Background(), code, true)
	suite.NoError(err)
	assert.Equal(suite.T(), name2, loadResult)

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

	// Original functions work again
	result1, err = client.FCallWithKeysAndArgs(context.Background(), name1, []string{}, []string{"meow", "woem"})
	suite.NoError(err)
	assert.Equal(suite.T(), "meow", result1)

	result2, err = client.FCallWithKeysAndArgs(context.Background(), name2, []string{}, []string{"meow", "woem"})
	suite.NoError(err)
	assert.Equal(suite.T(), int64(2), result2)
}

func (suite *GlideTestSuite) TestScriptExists() {
	client := suite.defaultClient()

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

func (suite *GlideTestSuite) TestScriptKill() {
	invokeClient, err := suite.client(suite.defaultClientConfig())
	require.NoError(suite.T(), err)
	killClient := suite.defaultClient()

	// Ensure no script is running at the beginning
	_, err = killClient.ScriptKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Kill Running Code
	code := CreateLongRunningLuaScript(5, true)
	script := options.NewScript(code)

	go invokeClient.InvokeScript(context.Background(), *script)

	time.Sleep(1 * time.Second)

	result, err := killClient.ScriptKill(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)
	script.Close()

	time.Sleep(1 * time.Second)

	// Ensure no script is running at the end
	_, err = killClient.ScriptKill(context.Background())
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))
}
