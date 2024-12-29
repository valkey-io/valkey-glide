// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"fmt"
	"strings"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/glide/api"

	"github.com/stretchr/testify/assert"
)

func (suite *GlideTestSuite) TestCustomCommandInfo() {
	client := suite.defaultClient()
	result, err := client.CustomCommand([]string{"INFO"})

	assert.Nil(suite.T(), err)
	assert.IsType(suite.T(), "", result)
	strResult := result.(string)
	assert.True(suite.T(), strings.Contains(strResult, "# Stats"))
}

func (suite *GlideTestSuite) TestCustomCommandPing_StringResponse() {
	client := suite.defaultClient()
	result, err := client.CustomCommand([]string{"PING"})

	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "PONG", result.(string))
}

func (suite *GlideTestSuite) TestCustomCommandClientInfo() {
	clientName := "TEST_CLIENT_NAME"
	config := api.NewGlideClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithClientName(clientName)
	client := suite.client(config)

	result, err := client.CustomCommand([]string{"CLIENT", "INFO"})

	assert.Nil(suite.T(), err)
	assert.IsType(suite.T(), "", result)
	strResult := result.(string)
	assert.True(suite.T(), strings.Contains(strResult, fmt.Sprintf("name=%s", clientName)))
}

func (suite *GlideTestSuite) TestCustomCommandGet_NullResponse() {
	client := suite.defaultClient()
	key := uuid.New().String()
	result, err := client.CustomCommand([]string{"GET", key})

	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), nil, result)
}

func (suite *GlideTestSuite) TestCustomCommandDel_LongResponse() {
	client := suite.defaultClient()
	key := uuid.New().String()
	suite.verifyOK(client.Set(key, "value"))
	result, err := client.CustomCommand([]string{"DEL", key})

	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), int64(1), result.(int64))
}

func (suite *GlideTestSuite) TestCustomCommandHExists_BoolResponse() {
	client := suite.defaultClient()
	fields := map[string]string{"field1": "value1"}
	key := uuid.New().String()

	res1, err := client.HSet(key, fields)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), int64(1), res1.Value())

	result, err := client.CustomCommand([]string{"HEXISTS", key, "field1"})

	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), true, result.(bool))
}

func (suite *GlideTestSuite) TestCustomCommandIncrByFloat_FloatResponse() {
	client := suite.defaultClient()
	key := uuid.New().String()

	result, err := client.CustomCommand([]string{"INCRBYFLOAT", key, fmt.Sprintf("%f", 0.1)})

	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), float64(0.1), result.(float64))
}

func (suite *GlideTestSuite) TestCustomCommandMGet_ArrayResponse() {
	clientName := "TEST_CLIENT_NAME"
	config := api.NewGlideClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithClientName(clientName)
	client := suite.client(config)

	key1 := uuid.New().String()
	key2 := uuid.New().String()
	key3 := uuid.New().String()
	oldValue := uuid.New().String()
	value := uuid.New().String()
	suite.verifyOK(client.Set(key1, oldValue))
	keyValueMap := map[string]string{
		key1: value,
		key2: value,
	}
	suite.verifyOK(client.MSet(keyValueMap))
	keys := []string{key1, key2, key3}
	values := []interface{}{value, value, nil}
	result, err := client.CustomCommand(append([]string{"MGET"}, keys...))

	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), values, result.([]interface{}))
}

func (suite *GlideTestSuite) TestCustomCommandConfigGet_MapResponse() {
	client := suite.defaultClient()

	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	configMap := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	result, err := client.ConfigSet(configMap)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "OK", result.Value())

	result2, err := client.CustomCommand([]string{"CONFIG", "GET", "timeout", "maxmemory"})
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), map[string]interface{}{"timeout": "1000", "maxmemory": "1073741824"}, result2)
}

func (suite *GlideTestSuite) TestCustomCommandConfigSMembers_SetResponse() {
	client := suite.defaultClient()
	key := uuid.NewString()
	members := []string{"member1", "member2", "member3"}

	res1, err := client.SAdd(key, members)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), int64(3), res1.Value())

	result2, err := client.CustomCommand([]string{"SMEMBERS", key})
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), map[string]struct{}{"member1": {}, "member2": {}, "member3": {}}, result2)
}

func (suite *GlideTestSuite) TestCustomCommand_invalidCommand() {
	client := suite.defaultClient()
	result, err := client.CustomCommand([]string{"pewpew"})

	assert.Nil(suite.T(), result)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.RequestError{}, err)
}

func (suite *GlideTestSuite) TestCustomCommand_invalidArgs() {
	client := suite.defaultClient()
	result, err := client.CustomCommand([]string{"ping", "pang", "pong"})

	assert.Nil(suite.T(), result)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.RequestError{}, err)
}

func (suite *GlideTestSuite) TestCustomCommand_closedClient() {
	client := suite.defaultClient()
	client.Close()

	result, err := client.CustomCommand([]string{"ping"})

	assert.Nil(suite.T(), result)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.ClosingError{}, err)
}

func (suite *GlideTestSuite) TestConfigSetAndGet_multipleArgs() {
	client := suite.defaultClient()

	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	configMap := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	key1 := api.CreateStringResult("timeout")
	value1 := api.CreateStringResult("1000")
	key2 := api.CreateStringResult("maxmemory")
	value2 := api.CreateStringResult("1073741824")
	resultConfigMap := map[api.Result[string]]api.Result[string]{key1: value1, key2: value2}
	result, err := client.ConfigSet(configMap)
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "OK", result.Value())

	result2, err := client.ConfigGet([]string{"timeout", "maxmemory"})
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), resultConfigMap, result2)
}

func (suite *GlideTestSuite) TestConfigSetAndGet_noArgs() {
	client := suite.defaultClient()

	configMap := map[string]string{}

	result, err := client.ConfigSet(configMap)
	assert.Equal(suite.T(), api.CreateNilStringResult(), result)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.RequestError{}, err)

	result2, err := client.ConfigGet([]string{})
	assert.Nil(suite.T(), result2)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.RequestError{}, err)
}

func (suite *GlideTestSuite) TestConfigSetAndGet_invalidArgs() {
	client := suite.defaultClient()

	configMap := map[string]string{"time": "1000"}

	result, err := client.ConfigSet(configMap)
	assert.Equal(suite.T(), api.CreateNilStringResult(), result)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &api.RequestError{}, err)

	result2, err := client.ConfigGet([]string{"time"})
	assert.Equal(suite.T(), map[api.Result[string]]api.Result[string]{}, result2)
	assert.Nil(suite.T(), err)
}

func (suite *GlideTestSuite) TestSortWithOptions_ExternalWeights() {
	client := suite.defaultClient()
	key := uuid.New().String()
	client.LPush(key, []string{"item1", "item2", "item3"})

	client.Set("weight_item1", "3")
	client.Set("weight_item2", "1")
	client.Set("weight_item3", "2")

	options := api.NewSortOptions().
		SetByPattern("weight_*").
		SetOrderBy(api.ASC).
		SetIsAlpha(false)

	sortResult, err := client.SortWithOptions(key, options)

	assert.Nil(suite.T(), err)
	resultList := []api.Result[string]{
		api.CreateStringResult("item2"),
		api.CreateStringResult("item3"),
		api.CreateStringResult("item1"),
	}

	assert.Equal(suite.T(), resultList, sortResult)
}

func (suite *GlideTestSuite) TestSortWithOptions_GetPatterns() {
	client := suite.defaultClient()
	key := uuid.New().String()
	client.LPush(key, []string{"item1", "item2", "item3"})

	client.Set("object_item1", "Object_1")
	client.Set("object_item2", "Object_2")
	client.Set("object_item3", "Object_3")

	options := api.NewSortOptions().
		SetByPattern("weight_*").
		SetOrderBy(api.ASC).
		SetIsAlpha(false).
		AddGetPattern("object_*")

	sortResult, err := client.SortWithOptions(key, options)

	assert.Nil(suite.T(), err)

	resultList := []api.Result[string]{
		api.CreateStringResult("Object_2"),
		api.CreateStringResult("Object_3"),
		api.CreateStringResult("Object_1"),
	}

	assert.Equal(suite.T(), resultList, sortResult)
}

func (suite *GlideTestSuite) TestSortWithOptions_SuccessfulSortByWeightAndGet() {
	client := suite.defaultClient()
	key := uuid.New().String()
	client.LPush(key, []string{"item1", "item2", "item3"})

	client.Set("weight_item1", "10")
	client.Set("weight_item2", "5")
	client.Set("weight_item3", "15")

	client.Set("object_item1", "Object 1")
	client.Set("object_item2", "Object 2")
	client.Set("object_item3", "Object 3")

	options := api.NewSortOptions().
		SetOrderBy(api.ASC).
		SetIsAlpha(false).
		SetByPattern("weight_*").
		AddGetPattern("object_*").
		AddGetPattern("#")

	sortResult, err := client.SortWithOptions(key, options)

	assert.Nil(suite.T(), err)

	resultList := []api.Result[string]{
		api.CreateStringResult("Object 2"),
		api.CreateStringResult("item2"),
		api.CreateStringResult("Object 1"),
		api.CreateStringResult("item1"),
		api.CreateStringResult("Object 3"),
		api.CreateStringResult("item3"),
	}

	assert.Equal(suite.T(), resultList, sortResult)

	objectItem2, err := client.Get("object_item2")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "Object 2", objectItem2.Value())

	objectItem1, err := client.Get("object_item1")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "Object 1", objectItem1.Value())

	objectItem3, err := client.Get("object_item3")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "Object 3", objectItem3.Value())

	assert.Equal(suite.T(), "item2", sortResult[1].Value())
	assert.Equal(suite.T(), "item1", sortResult[3].Value())
	assert.Equal(suite.T(), "item3", sortResult[5].Value())
}

func (suite *GlideTestSuite) TestSortStoreWithOptions_ByPattern() {
	client := suite.defaultClient()
	key := "{listKey}" + uuid.New().String()
	sortedKey := "{listKey}" + uuid.New().String()
	client.LPush(key, []string{"a", "b", "c", "d", "e"})
	client.Set("{listKey}weight_a", "5")
	client.Set("{listKey}weight_b", "2")
	client.Set("{listKey}weight_c", "3")
	client.Set("{listKey}weight_d", "1")
	client.Set("{listKey}weight_e", "4")

	options := api.NewSortOptions().SetByPattern("{listKey}weight_*")

	result, err := client.SortStoreWithOptions(key, sortedKey, options)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), result)
	assert.Equal(suite.T(), int64(5), result.Value())

	sortedValues, err := client.LRange(sortedKey, 0, -1)
	resultList := []api.Result[string]{
		api.CreateStringResult("d"),
		api.CreateStringResult("b"),
		api.CreateStringResult("c"),
		api.CreateStringResult("e"),
		api.CreateStringResult("a"),
	}
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), resultList, sortedValues)
}

func (suite *GlideTestSuite) TestSortReadOnlyWithOptions_ExternalWeights() {
	client := suite.defaultClient()
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	key := uuid.New().String()
	client.LPush(key, []string{"item1", "item2", "item3"})

	client.Set("weight_item1", "3")
	client.Set("weight_item2", "1")
	client.Set("weight_item3", "2")

	options := api.NewSortOptions().
		SetByPattern("weight_*").
		SetOrderBy(api.ASC).
		SetIsAlpha(false)

	sortResult, err := client.SortReadOnlyWithOptions(key, options)

	assert.Nil(suite.T(), err)
	resultList := []api.Result[string]{
		api.CreateStringResult("item2"),
		api.CreateStringResult("item3"),
		api.CreateStringResult("item1"),
	}
	assert.Equal(suite.T(), resultList, sortResult)
}

func (suite *GlideTestSuite) TestSortReadOnlyWithOptions_GetPatterns() {
	client := suite.defaultClient()
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	key := uuid.New().String()
	client.LPush(key, []string{"item1", "item2", "item3"})

	client.Set("object_item1", "Object_1")
	client.Set("object_item2", "Object_2")
	client.Set("object_item3", "Object_3")

	options := api.NewSortOptions().
		SetByPattern("weight_*").
		SetOrderBy(api.ASC).
		SetIsAlpha(false).
		AddGetPattern("object_*")

	sortResult, err := client.SortReadOnlyWithOptions(key, options)

	assert.Nil(suite.T(), err)

	resultList := []api.Result[string]{
		api.CreateStringResult("Object_2"),
		api.CreateStringResult("Object_3"),
		api.CreateStringResult("Object_1"),
	}

	assert.Equal(suite.T(), resultList, sortResult)
}

func (suite *GlideTestSuite) TestSortReadOnlyWithOptions_SuccessfulSortByWeightAndGet() {
	client := suite.defaultClient()
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	key := uuid.New().String()
	client.LPush(key, []string{"item1", "item2", "item3"})

	client.Set("weight_item1", "10")
	client.Set("weight_item2", "5")
	client.Set("weight_item3", "15")

	client.Set("object_item1", "Object 1")
	client.Set("object_item2", "Object 2")
	client.Set("object_item3", "Object 3")

	options := api.NewSortOptions().
		SetOrderBy(api.ASC).
		SetIsAlpha(false).
		SetByPattern("weight_*").
		AddGetPattern("object_*").
		AddGetPattern("#")

	sortResult, err := client.SortReadOnlyWithOptions(key, options)

	assert.Nil(suite.T(), err)

	resultList := []api.Result[string]{
		api.CreateStringResult("Object 2"),
		api.CreateStringResult("item2"),
		api.CreateStringResult("Object 1"),
		api.CreateStringResult("item1"),
		api.CreateStringResult("Object 3"),
		api.CreateStringResult("item3"),
	}

	assert.Equal(suite.T(), resultList, sortResult)

	objectItem2, err := client.Get("object_item2")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "Object 2", objectItem2.Value())

	objectItem1, err := client.Get("object_item1")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "Object 1", objectItem1.Value())

	objectItem3, err := client.Get("object_item3")
	assert.Nil(suite.T(), err)
	assert.Equal(suite.T(), "Object 3", objectItem3.Value())

	assert.Equal(suite.T(), "item2", sortResult[1].Value())
	assert.Equal(suite.T(), "item1", sortResult[3].Value())
	assert.Equal(suite.T(), "item3", sortResult[5].Value())
}
