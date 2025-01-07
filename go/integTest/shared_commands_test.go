// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"math"
	"reflect"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
	"github.com/valkey-io/valkey-glide/go/glide/api/options"
)

const (
	keyName      = "key"
	initialValue = "value"
	anotherValue = "value2"
)

func (suite *GlideTestSuite) TestSetAndGet_noOptions() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		suite.verifyOK(client.Set(keyName, initialValue))
		result, err := client.Get(keyName)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetAndGet_byteString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		invalidUTF8Value := "\xff\xfe\xfd"
		suite.verifyOK(client.Set(keyName, invalidUTF8Value))
		result, err := client.Get(keyName)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), invalidUTF8Value, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		suite.verifyOK(client.Set(keyName, initialValue))

		opts := api.NewSetOptionsBuilder().SetReturnOldValue(true)
		result, err := client.SetWithOptions(keyName, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_overwrite() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, initialValue))

		opts := api.NewSetOptionsBuilder().SetConditionalSet(api.OnlyIfExists)
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		opts := api.NewSetOptionsBuilder().SetConditionalSet(api.OnlyIfExists)
		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		opts := api.NewSetOptionsBuilder().SetConditionalSet(api.OnlyIfDoesNotExist)
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		opts := api.NewSetOptionsBuilder().SetConditionalSet(api.OnlyIfDoesNotExist)
		suite.verifyOK(client.Set(key, initialValue))

		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_KeepExistingExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		opts := api.NewSetOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.Milliseconds).SetCount(uint64(2000)))
		suite.verifyOK(client.SetWithOptions(key, initialValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		opts = api.NewSetOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.KeepExisting))
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Value())

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_UpdateExistingExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		opts := api.NewSetOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.Milliseconds).SetCount(uint64(100500)))
		suite.verifyOK(client.SetWithOptions(key, initialValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		opts = api.NewSetOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.Milliseconds).SetCount(uint64(2000)))
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Value())

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetEx_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, initialValue))

		result, err := client.GetEx(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		key = uuid.New().String()
		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetExWithOptions_PersistKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, initialValue))

		opts := api.NewGetExOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.Milliseconds).SetCount(uint64(2000)))
		result, err := client.GetExWithOptions(key, opts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		time.Sleep(1000 * time.Millisecond)

		opts = api.NewGetExOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.Persist))
		result, err = client.GetExWithOptions(key, opts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestGetExWithOptions_UpdateExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, initialValue))

		opts := api.NewGetExOptionsBuilder().SetExpiry(api.NewExpiryBuilder().SetType(api.Milliseconds).SetCount(uint64(2000)))
		result, err := client.GetExWithOptions(key, opts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		time.Sleep(2222 * time.Millisecond)

		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue_nonExistentKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		opts := api.NewSetOptionsBuilder().SetReturnOldValue(true)

		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestMSetAndMGet_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
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
		stringValue := api.CreateStringResult(value)
		nullResult := api.CreateNilStringResult()
		values := []api.Result[string]{stringValue, stringValue, nullResult}
		result, err := client.MGet(keys)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), values, result)
	})
}

func (suite *GlideTestSuite) TestMSetNXAndMGet_nonExistingKey_valuesSet() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()
		key3 := "{key}" + uuid.New().String()
		value := uuid.New().String()
		keyValueMap := map[string]string{
			key1: value,
			key2: value,
			key3: value,
		}
		res, err := client.MSetNX(keyValueMap)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), res.Value())
		keys := []string{key1, key2, key3}
		stringValue := api.CreateStringResult(value)
		values := []api.Result[string]{stringValue, stringValue, stringValue}
		result, err := client.MGet(keys)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), values, result)
	})
}

func (suite *GlideTestSuite) TestMSetNXAndMGet_existingKey_valuesNotUpdated() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()
		key3 := "{key}" + uuid.New().String()
		oldValue := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key1, oldValue))
		keyValueMap := map[string]string{
			key1: value,
			key2: value,
			key3: value,
		}
		res, err := client.MSetNX(keyValueMap)
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), res.Value())
		keys := []string{key1, key2, key3}
		oldResult := api.CreateStringResult(oldValue)
		nullResult := api.CreateNilStringResult()
		values := []api.Result[string]{oldResult, nullResult, nullResult}
		result, err := client.MGet(keys)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), values, result)
	})
}

func (suite *GlideTestSuite) TestIncrCommands_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, "10"))

		res1, err := client.Incr(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(11), res1.Value())

		res2, err := client.IncrBy(key, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(21), res2.Value())

		res3, err := client.IncrByFloat(key, float64(10.1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), float64(31.1), res3.Value())
	})
}

func (suite *GlideTestSuite) TestIncrCommands_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		res1, err := client.Incr(key1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res1.Value())

		key2 := uuid.New().String()
		res2, err := client.IncrBy(key2, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(10), res2.Value())

		key3 := uuid.New().String()
		res3, err := client.IncrByFloat(key3, float64(10.1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), float64(10.1), res3.Value())
	})
}

func (suite *GlideTestSuite) TestIncrCommands_TypeError() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, "stringValue"))

		res1, err := client.Incr(key)
		assert.Equal(suite.T(), int64(0), res1.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res2, err := client.IncrBy(key, 10)
		assert.Equal(suite.T(), int64(0), res2.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res3, err := client.IncrByFloat(key, float64(10.1))
		assert.Equal(suite.T(), float64(0), res3.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestDecrCommands_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, "10"))

		res1, err := client.Decr(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(9), res1.Value())

		res2, err := client.DecrBy(key, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), res2.Value())
	})
}

func (suite *GlideTestSuite) TestDecrCommands_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		res1, err := client.Decr(key1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), res1.Value())

		key2 := uuid.New().String()
		res2, err := client.DecrBy(key2, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-10), res2.Value())
	})
}

func (suite *GlideTestSuite) TestStrlen_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		res, err := client.Strlen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(len(value)), res.Value())
	})
}

func (suite *GlideTestSuite) TestStrlen_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		res, err := client.Strlen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
	})
}

func (suite *GlideTestSuite) TestSetRange_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		res, err := client.SetRange(key, 0, "Dummy string")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(12), res.Value())

		res, err = client.SetRange(key, 6, "values")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(12), res.Value())
		res1, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values", res1.Value())

		res, err = client.SetRange(key, 15, "test")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(19), res.Value())
		res1, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values\x00\x00\x00test", res1.Value())

		res, err = client.SetRange(key, math.MaxInt32, "test")
		assert.Equal(suite.T(), int64(0), res.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestSetRange_existingAndNonExistingKeys_binaryString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		nonUtf8String := "Dummy \xFF string"
		key := uuid.New().String()
		res, err := client.SetRange(key, 0, nonUtf8String)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(14), res.Value())

		res, err = client.SetRange(key, 6, "values ")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(14), res.Value())
		res1, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values g", res1.Value())

		res, err = client.SetRange(key, 15, "test")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(19), res.Value())
		res1, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values g\x00test", res1.Value())
	})
}

func (suite *GlideTestSuite) TestGetRange_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, "Dummy string"))

		res, err := client.GetRange(key, 0, 4)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy", res.Value())

		res, err = client.GetRange(key, -6, -1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "string", res.Value())

		res, err = client.GetRange(key, -1, -6)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Value())

		res, err = client.GetRange(key, 15, 16)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Value())

		nonExistingKey := uuid.New().String()
		res, err = client.GetRange(nonExistingKey, 0, 5)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Value())
	})
}

func (suite *GlideTestSuite) TestGetRange_binaryString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		nonUtf8String := "Dummy \xFF string"
		suite.verifyOK(client.Set(key, nonUtf8String))

		res, err := client.GetRange(key, 4, 6)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "y \xFF", res.Value())
	})
}

func (suite *GlideTestSuite) TestAppend_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value1 := uuid.New().String()
		value2 := uuid.New().String()

		res, err := client.Append(key, value1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(len(value1)), res.Value())
		res1, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value1, res1.Value())

		res, err = client.Append(key, value2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(len(value1)+len(value2)), res.Value())
		res1, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value1+value2, res1.Value())
	})
}

func (suite *GlideTestSuite) TestLCS_existingAndNonExistingKeys() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")

	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()

		res, err := client.LCS(key1, key2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Value())

		suite.verifyOK(client.Set(key1, "Dummy string"))
		suite.verifyOK(client.Set(key2, "Dummy value"))

		res, err = client.LCS(key1, key2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy ", res.Value())
	})
}

func (suite *GlideTestSuite) TestGetDel_ExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := "testValue"

		suite.verifyOK(client.Set(key, value))
		result, err := client.GetDel(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value, result.Value())

		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetDel_NonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()

		result, err := client.GetDel(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetDel_EmptyKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		result, err := client.GetDel("")

		assert.NotNil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
		assert.Equal(suite.T(), "key is required", err.Error())
	})
}

func (suite *GlideTestSuite) TestPing_NoArgument() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		result, err := client.Ping()
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "PONG", result)
	})
}

func (suite *GlideTestSuite) TestPing_WithArgument() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		// Passing "Hello" as the message
		result, err := client.PingWithMessage("Hello")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Hello", result)
	})
}

func (suite *GlideTestSuite) TestHSet_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.New().String()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())
	})
}

func (suite *GlideTestSuite) TestHSet_byteString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{
			string([]byte{0xFF, 0x00, 0xAA}):       string([]byte{0xDE, 0xAD, 0xBE, 0xEF}),
			string([]byte{0x01, 0x02, 0x03, 0xFE}): string([]byte{0xCA, 0xFE, 0xBA, 0xBE}),
		}
		key := string([]byte{0x01, 0x02, 0x03, 0xFE})

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HGetAll(key)
		key1 := api.CreateStringResult(string([]byte{0xFF, 0x00, 0xAA}))
		value1 := api.CreateStringResult(string([]byte{0xDE, 0xAD, 0xBE, 0xEF}))
		key2 := api.CreateStringResult(string([]byte{0x01, 0x02, 0x03, 0xFE}))
		value2 := api.CreateStringResult(string([]byte{0xCA, 0xFE, 0xBA, 0xBE}))
		fieldsResult := map[api.Result[string]]api.Result[string]{
			key1: value1,
			key2: value2,
		}
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), fieldsResult, res2)
	})
}

func (suite *GlideTestSuite) TestHSet_WithAddNewField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.New().String()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())

		fields["field3"] = "value3"
		res3, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res3.Value())
	})
}

func (suite *GlideTestSuite) TestHGet_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HGet(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "value1", res2.Value())
	})
}

func (suite *GlideTestSuite) TestHGet_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.HGet(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res1)
	})
}

func (suite *GlideTestSuite) TestHGet_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HGet(key, "foo")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res2)
	})
}

func (suite *GlideTestSuite) TestHGetAll_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		field1 := api.CreateStringResult("field1")
		value1 := api.CreateStringResult("value1")
		field2 := api.CreateStringResult("field2")
		value2 := api.CreateStringResult("value2")
		fieldsResult := map[api.Result[string]]api.Result[string]{field1: value1, field2: value2}
		res2, err := client.HGetAll(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), fieldsResult, res2)
	})
}

func (suite *GlideTestSuite) TestHGetAll_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HGetAll(key)
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHMGet() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HMGet(key, []string{"field1", "field2", "field3"})
		value1 := api.CreateStringResult("value1")
		value2 := api.CreateStringResult("value2")
		nullValue := api.CreateNilStringResult()
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{value1, value2, nullValue}, res2)
	})
}

func (suite *GlideTestSuite) TestHMGet_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HMGet(key, []string{"field1", "field2", "field3"})
		nullValue := api.CreateNilStringResult()
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{nullValue, nullValue, nullValue}, res)
	})
}

func (suite *GlideTestSuite) TestHMGet_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HMGet(key, []string{"field3", "field4"})
		nullValue := api.CreateNilStringResult()
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{nullValue, nullValue}, res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HSetNX(key, "field1", "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Value())
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.HSetNX(key, "field1", "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), true, res1.Value())

		res2, err := client.HGetAll(key)
		field1 := api.CreateStringResult("field1")
		value1 := api.CreateStringResult("value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), map[api.Result[string]]api.Result[string]{field1: value1}, res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HSetNX(key, "field1", "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Value())
	})
}

func (suite *GlideTestSuite) TestHDel() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HDel(key, []string{"field1", "field2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Value())

		res3, err := client.HGetAll(key)
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), res3)

		res4, err := client.HDel(key, []string{"field1", "field2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res4.Value())
	})
}

func (suite *GlideTestSuite) TestHDel_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		res, err := client.HDel(key, []string{"field1", "field2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
	})
}

func (suite *GlideTestSuite) TestHDel_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HDel(key, []string{"field3", "field4"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())
	})
}

func (suite *GlideTestSuite) TestHLen() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HLen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Value())
	})
}

func (suite *GlideTestSuite) TestHLen_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		res, err := client.HLen(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
	})
}

func (suite *GlideTestSuite) TestHVals_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HVals(key)
		value1 := api.CreateStringResult("value1")
		value2 := api.CreateStringResult("value2")
		assert.Nil(suite.T(), err)
		assert.Contains(suite.T(), res2, value1)
		assert.Contains(suite.T(), res2, value2)
	})
}

func (suite *GlideTestSuite) TestHVals_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HVals(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{}, res)
	})
}

func (suite *GlideTestSuite) TestHExists_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HExists(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), true, res2.Value())
	})
}

func (suite *GlideTestSuite) TestHExists_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HExists(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res.Value())
	})
}

func (suite *GlideTestSuite) TestHExists_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HExists(key, "field3")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Value())
	})
}

func (suite *GlideTestSuite) TestHKeys_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HKeys(key)
		field1 := api.CreateStringResult("field1")
		field2 := api.CreateStringResult("field2")
		assert.Nil(suite.T(), err)
		assert.Contains(suite.T(), res2, field1)
		assert.Contains(suite.T(), res2, field2)
	})
}

func (suite *GlideTestSuite) TestHKeys_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HKeys(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{}, res)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HStrLen(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(6), res2.Value())
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HStrLen(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.HStrLen(key, "field3")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())
	})
}

func (suite *GlideTestSuite) TestHIncrBy_WithExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		field := uuid.NewString()
		fieldValueMap := map[string]string{field: "10"}

		hsetResult, err := client.HSet(key, fieldValueMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), hsetResult.Value())

		hincrByResult, hincrByErr := client.HIncrBy(key, field, 1)
		assert.Nil(suite.T(), hincrByErr)
		assert.Equal(suite.T(), int64(11), hincrByResult.Value())
	})
}

func (suite *GlideTestSuite) TestHIncrBy_WithNonExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		field := uuid.NewString()
		field2 := uuid.NewString()
		fieldValueMap := map[string]string{field2: "1"}

		hsetResult, err := client.HSet(key, fieldValueMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), hsetResult.Value())

		hincrByResult, hincrByErr := client.HIncrBy(key, field, 2)
		assert.Nil(suite.T(), hincrByErr)
		assert.Equal(suite.T(), int64(2), hincrByResult.Value())
	})
}

func (suite *GlideTestSuite) TestHIncrByFloat_WithExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		field := uuid.NewString()
		fieldValueMap := map[string]string{field: "10"}

		hsetResult, err := client.HSet(key, fieldValueMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), hsetResult.Value())

		hincrByFloatResult, hincrByFloatErr := client.HIncrByFloat(key, field, 1.5)
		assert.Nil(suite.T(), hincrByFloatErr)
		assert.Equal(suite.T(), float64(11.5), hincrByFloatResult.Value())
	})
}

func (suite *GlideTestSuite) TestHIncrByFloat_WithNonExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		field := uuid.NewString()
		field2 := uuid.NewString()
		fieldValueMap := map[string]string{field2: "1"}

		hsetResult, err := client.HSet(key, fieldValueMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), hsetResult.Value())

		hincrByFloatResult, hincrByFloatErr := client.HIncrByFloat(key, field, 1.5)
		assert.Nil(suite.T(), hincrByFloatErr)
		assert.Equal(suite.T(), float64(1.5), hincrByFloatResult.Value())
	})
}

func (suite *GlideTestSuite) TestHScan() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		initialCursor := "0"
		defaultCount := 20

		// Setup test data
		numberMap := make(map[string]string)
		// This is an unusually large dataset because the server can ignore the COUNT option if the dataset is small enough
		// because it is more efficient to transfer its entire content at once.
		for i := 0; i < 50000; i++ {
			numberMap[strconv.Itoa(i)] = "num" + strconv.Itoa(i)
		}
		charMembers := []string{"a", "b", "c", "d", "e"}
		charMap := make(map[string]string)
		for i, val := range charMembers {
			charMap[val] = strconv.Itoa(i)
		}

		t := suite.T()

		// Check for empty set.
		resCursor, resCollection, err := client.HScan(key1, initialCursor)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor, resCursor.Value())
		assert.Empty(t, resCollection)

		// Negative cursor check.
		if suite.serverVersion >= "8.0.0" {
			_, _, err = client.HScan(key1, "-1")
			assert.NotEmpty(t, err)
		} else {
			resCursor, resCollection, _ = client.HScan(key1, "-1")
			assert.Equal(t, initialCursor, resCursor.Value())
			assert.Empty(t, resCollection)
		}

		// Result contains the whole set
		hsetResult, _ := client.HSet(key1, charMap)
		assert.Equal(t, int64(len(charMembers)), hsetResult.Value())

		resCursor, resCollection, _ = client.HScan(key1, initialCursor)
		assert.Equal(t, initialCursor, resCursor.Value())
		// Length includes the score which is twice the map size
		assert.Equal(t, len(charMap)*2, len(resCollection))

		resultKeys := make([]api.Result[string], 0)
		resultValues := make([]api.Result[string], 0)

		for i := 0; i < len(resCollection); i += 2 {
			resultKeys = append(resultKeys, resCollection[i])
			resultValues = append(resultValues, resCollection[i+1])
		}
		keysList, valuesList := convertMapKeysAndValuesToResultList(charMap)
		assert.True(t, isSubset(resultKeys, keysList) && isSubset(keysList, resultKeys))
		assert.True(t, isSubset(resultValues, valuesList) && isSubset(valuesList, resultValues))

		opts := options.NewHashScanOptionsBuilder().SetMatch("a")
		resCursor, resCollection, _ = client.HScanWithOptions(key1, initialCursor, opts)
		assert.Equal(t, initialCursor, resCursor.Value())
		assert.Equal(t, len(resCollection), 2)
		assert.Equal(t, resCollection[0].Value(), "a")
		assert.Equal(t, resCollection[1].Value(), "0")

		// Result contains a subset of the key
		combinedMap := make(map[string]string)
		for key, value := range numberMap {
			combinedMap[key] = value
		}
		for key, value := range charMap {
			combinedMap[key] = value
		}

		hsetResult, _ = client.HSet(key1, combinedMap)
		assert.Equal(t, int64(len(numberMap)), hsetResult.Value())
		resultCursor := "0"
		secondResultAllKeys := make([]api.Result[string], 0)
		secondResultAllValues := make([]api.Result[string], 0)
		isFirstLoop := true
		for {
			resCursor, resCollection, _ = client.HScan(key1, resultCursor)
			resultCursor = resCursor.Value()
			for i := 0; i < len(resCollection); i += 2 {
				secondResultAllKeys = append(secondResultAllKeys, resCollection[i])
				secondResultAllValues = append(secondResultAllValues, resCollection[i+1])
			}
			if isFirstLoop {
				assert.NotEqual(t, "0", resultCursor)
				isFirstLoop = false
			} else if resultCursor == "0" {
				break
			}

			// Scan with result cursor to get the next set of data.
			newResultCursor, secondResult, _ := client.HScan(key1, resultCursor)
			assert.NotEqual(t, resultCursor, newResultCursor)
			resultCursor = newResultCursor.Value()
			assert.False(t, reflect.DeepEqual(secondResult, resCollection))
			for i := 0; i < len(secondResult); i += 2 {
				secondResultAllKeys = append(secondResultAllKeys, secondResult[i])
				secondResultAllValues = append(secondResultAllValues, secondResult[i+1])
			}

			// 0 is returned for the cursor of the last iteration.
			if resultCursor == "0" {
				break
			}
		}
		numberKeysList, numberValuesList := convertMapKeysAndValuesToResultList(numberMap)
		assert.True(t, isSubset(numberKeysList, secondResultAllKeys))
		assert.True(t, isSubset(numberValuesList, secondResultAllValues))

		// Test match pattern
		opts = options.NewHashScanOptionsBuilder().SetMatch("*")
		resCursor, resCollection, _ = client.HScanWithOptions(key1, initialCursor, opts)
		resCursorInt, _ := strconv.Atoi(resCursor.Value())
		assert.True(t, resCursorInt >= 0)
		assert.True(t, int(len(resCollection)) >= defaultCount)

		// Test count
		opts = options.NewHashScanOptionsBuilder().SetCount(int64(20))
		resCursor, resCollection, _ = client.HScanWithOptions(key1, initialCursor, opts)
		resCursorInt, _ = strconv.Atoi(resCursor.Value())
		assert.True(t, resCursorInt >= 0)
		assert.True(t, len(resCollection) >= 20)

		// Test count with match returns a non-empty list
		opts = options.NewHashScanOptionsBuilder().SetMatch("1*").SetCount(int64(20))
		resCursor, resCollection, _ = client.HScanWithOptions(key1, initialCursor, opts)
		resCursorInt, _ = strconv.Atoi(resCursor.Value())
		assert.True(t, resCursorInt >= 0)
		assert.True(t, len(resCollection) >= 0)

		if suite.serverVersion >= "8.0.0" {
			opts = options.NewHashScanOptionsBuilder().SetNoValue(true)
			resCursor, resCollection, _ = client.HScanWithOptions(key1, initialCursor, opts)
			resCursorInt, _ = strconv.Atoi(resCursor.Value())
			assert.True(t, resCursorInt >= 0)

			// Check if all fields don't start with "num"
			containsElementsWithNumKeyword := false
			for i := 0; i < len(resCollection); i++ {
				if strings.Contains(resCollection[i].Value(), "num") {
					containsElementsWithNumKeyword = true
					break
				}
			}
			assert.False(t, containsElementsWithNumKeyword)
		}

		// Check if Non-hash key throws an error.
		setResult, _ := client.Set(key2, "test")
		assert.Equal(t, setResult.Value(), "OK")
		_, _, err = client.HScan(key2, initialCursor)
		assert.NotEmpty(t, err)

		// Check if Non-hash key throws an error when HSCAN called with options.
		opts = options.NewHashScanOptionsBuilder().SetMatch("test").SetCount(int64(1))
		_, _, err = client.HScanWithOptions(key2, initialCursor, opts)
		assert.NotEmpty(t, err)

		// Check if a negative cursor value throws an error.
		opts = options.NewHashScanOptionsBuilder().SetCount(int64(-1))
		_, _, err = client.HScanWithOptions(key1, initialCursor, opts)
		assert.NotEmpty(t, err)
	})
}

func (suite *GlideTestSuite) TestLPushLPop_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		res2, err := client.LPop(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "value1", res2.Value())

		resultList := []api.Result[string]{api.CreateStringResult("value2"), api.CreateStringResult("value3")}
		res3, err := client.LPopCount(key, 2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), resultList, res3)
	})
}

func (suite *GlideTestSuite) TestLPop_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.LPop(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res1)

		res2, err := client.LPopCount(key, 2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res2)
	})
}

func (suite *GlideTestSuite) TestLPushLPop_typeError() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		suite.verifyOK(client.Set(key, "value"))

		res1, err := client.LPush(key, []string{"value1"})
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res1)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res2, err := client.LPopCount(key, 2)
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res2)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLPos_withAndWithoutOptions() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		res1, err := client.RPush(key, []string{"a", "a", "b", "c", "a", "b"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(6), res1.Value())

		res2, err := client.LPos(key, "a")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())

		res3, err := client.LPosWithOptions(key, "b", api.NewLPosOptionsBuilder().SetRank(2))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res3.Value())

		// element doesn't exist
		res4, err := client.LPos(key, "e")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res4)

		// reverse traversal
		res5, err := client.LPosWithOptions(key, "b", api.NewLPosOptionsBuilder().SetRank(-2))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res5.Value())

		// unlimited comparisons
		res6, err := client.LPosWithOptions(
			key,
			"a",
			api.NewLPosOptionsBuilder().SetRank(1).SetMaxLen(0),
		)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res6.Value())

		// limited comparisons
		res7, err := client.LPosWithOptions(
			key,
			"c",
			api.NewLPosOptionsBuilder().SetRank(1).SetMaxLen(2),
		)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res7)

		// invalid rank value
		res8, err := client.LPosWithOptions(key, "a", api.NewLPosOptionsBuilder().SetRank(0))
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res8)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// invalid maxlen value
		res9, err := client.LPosWithOptions(key, "a", api.NewLPosOptionsBuilder().SetMaxLen(-1))
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res9)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// non-existent key
		res10, err := client.LPos("non_existent_key", "a")
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res10)
		assert.Nil(suite.T(), err)

		// wrong key data type
		keyString := uuid.NewString()
		suite.verifyOK(client.Set(keyString, "value"))
		res11, err := client.LPos(keyString, "a")
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res11)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLPosCount() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.RPush(key, []string{"a", "a", "b", "c", "a", "b"})
		assert.Equal(suite.T(), int64(6), res1.Value())
		assert.Nil(suite.T(), err)

		res2, err := client.LPosCount(key, "a", int64(2))
		assert.Equal(suite.T(), []api.Result[int64]{api.CreateInt64Result(0), api.CreateInt64Result(1)}, res2)
		assert.Nil(suite.T(), err)

		res3, err := client.LPosCount(key, "a", int64(0))
		assert.Equal(
			suite.T(),
			[]api.Result[int64]{api.CreateInt64Result(0), api.CreateInt64Result(1), api.CreateInt64Result(4)},
			res3,
		)
		assert.Nil(suite.T(), err)

		// invalid count value
		res4, err := client.LPosCount(key, "a", int64(-1))
		assert.Equal(suite.T(), ([]api.Result[int64])(nil), res4)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// non-existent key
		res5, err := client.LPosCount("non_existent_key", "a", int64(1))
		assert.Equal(suite.T(), []api.Result[int64]{}, res5)
		assert.Nil(suite.T(), err)

		// wrong key data type
		keyString := uuid.NewString()
		suite.verifyOK(client.Set(keyString, "value"))
		res6, err := client.LPosCount(keyString, "a", int64(1))
		assert.Equal(suite.T(), ([]api.Result[int64])(nil), res6)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLPosCount_withOptions() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.RPush(key, []string{"a", "a", "b", "c", "a", "b"})
		assert.Equal(suite.T(), int64(6), res1.Value())
		assert.Nil(suite.T(), err)

		res2, err := client.LPosCountWithOptions(key, "a", int64(0), api.NewLPosOptionsBuilder().SetRank(1))
		assert.Equal(
			suite.T(),
			[]api.Result[int64]{api.CreateInt64Result(0), api.CreateInt64Result(1), api.CreateInt64Result(4)},
			res2,
		)
		assert.Nil(suite.T(), err)

		res3, err := client.LPosCountWithOptions(key, "a", int64(0), api.NewLPosOptionsBuilder().SetRank(2))
		assert.Equal(suite.T(), []api.Result[int64]{api.CreateInt64Result(1), api.CreateInt64Result(4)}, res3)
		assert.Nil(suite.T(), err)

		// reverse traversal
		res4, err := client.LPosCountWithOptions(key, "a", int64(0), api.NewLPosOptionsBuilder().SetRank(-1))
		assert.Equal(
			suite.T(),
			[]api.Result[int64]{api.CreateInt64Result(4), api.CreateInt64Result(1), api.CreateInt64Result(0)},
			res4,
		)
		assert.Nil(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestRPush() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value1", "value2", "value3", "value4"}
		key := uuid.NewString()

		res1, err := client.RPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res2, err := client.RPush(key2, []string{"value1"})
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res2)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestSAdd() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2"}

		res, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res.Value())
		assert.False(suite.T(), res.IsNil())
	})
}

func (suite *GlideTestSuite) TestSAdd_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSRem() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SRem(key, []string{"member1", "member2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSRem_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SRem(key, []string{"member3", "member4"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSRem_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res2, err := client.SRem(key, []string{"member1", "member2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSRem_WithExistingKeyAndDifferentMembers() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SRem(key, []string{"member1", "member3", "member4"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSUnionStore() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		key4 := "{key}-4-" + uuid.NewString()
		stringKey := "{key}-5-" + uuid.NewString()
		nonExistingKey := "{key}-6-" + uuid.NewString()

		memberArray1 := []string{"a", "b", "c"}
		memberArray2 := []string{"c", "d", "e"}
		memberArray3 := []string{"e", "f", "g"}
		expected1 := map[api.Result[string]]struct{}{
			api.CreateStringResult("a"): {},
			api.CreateStringResult("b"): {},
			api.CreateStringResult("c"): {},
			api.CreateStringResult("d"): {},
			api.CreateStringResult("e"): {},
		}
		expected2 := map[api.Result[string]]struct{}{
			api.CreateStringResult("a"): {},
			api.CreateStringResult("b"): {},
			api.CreateStringResult("c"): {},
			api.CreateStringResult("d"): {},
			api.CreateStringResult("e"): {},
			api.CreateStringResult("f"): {},
			api.CreateStringResult("g"): {},
		}
		t := suite.T()

		res1, err := client.SAdd(key1, memberArray1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res1.Value())

		res2, err := client.SAdd(key2, memberArray2)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res2.Value())

		res3, err := client.SAdd(key3, memberArray3)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res3.Value())

		// store union in new key
		res4, err := client.SUnionStore(key4, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(5), res4.Value())

		res5, err := client.SMembers(key4)
		assert.NoError(t, err)
		assert.Len(t, res5, 5)
		assert.True(t, reflect.DeepEqual(res5, expected1))

		// overwrite existing set
		res6, err := client.SUnionStore(key1, []string{key4, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(5), res6.Value())

		res7, err := client.SMembers(key1)
		assert.NoError(t, err)
		assert.Len(t, res7, 5)
		assert.True(t, reflect.DeepEqual(res7, expected1))

		// overwrite one of the source keys
		res8, err := client.SUnionStore(key2, []string{key4, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(5), res8.Value())

		res9, err := client.SMembers(key2)
		assert.NoError(t, err)
		assert.Len(t, res9, 5)
		assert.True(t, reflect.DeepEqual(res9, expected1))

		// union with non-existing key
		res10, err := client.SUnionStore(key2, []string{nonExistingKey})
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res10.Value())

		// check that the key is now empty
		members1, err := client.SMembers(key2)
		assert.NoError(t, err)
		assert.Empty(t, members1)

		// invalid argument - key list must not be empty
		res11, err := client.SUnionStore(key4, []string{})
		assert.Equal(suite.T(), int64(0), res11.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// non-set key
		_, err = client.Set(stringKey, "value")
		assert.NoError(t, err)

		res12, err := client.SUnionStore(key4, []string{stringKey, key1})
		assert.Equal(suite.T(), int64(0), res12.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// overwrite destination when destination is not a set
		res13, err := client.SUnionStore(stringKey, []string{key1, key3})
		assert.NoError(t, err)
		assert.Equal(t, int64(7), res13.Value())

		// check that the key is now empty
		res14, err := client.SMembers(stringKey)
		assert.NoError(t, err)
		assert.Len(t, res14, 7)
		assert.True(t, reflect.DeepEqual(res14, expected2))
	})
}

func (suite *GlideTestSuite) TestSMembers() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SMembers(key)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), res2, 3)
	})
}

func (suite *GlideTestSuite) TestSMembers_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.SMembers(key)
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestSCard() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SCard(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSCard_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.SCard(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
		assert.False(suite.T(), res.IsNil())
	})
}

func (suite *GlideTestSuite) TestSIsMember() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())

		res2, err := client.SIsMember(key, "member2")
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSIsMember_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.SIsMember(key, "member2")
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), res.Value())
		assert.False(suite.T(), res.IsNil())
	})
}

func (suite *GlideTestSuite) TestSIsMember_WithNotExistingMember() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SIsMember(key, "nonExistingMember")
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), res2.Value())
		assert.False(suite.T(), res2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSDiff() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(key1, []string{"a", "b", "c", "d"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SAdd(key2, []string{"c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res2.Value())
		assert.False(suite.T(), res2.IsNil())

		result, err := client.SDiff([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), result, 2)
		assert.Contains(suite.T(), result, api.CreateStringResult("a"))
		assert.Contains(suite.T(), result, api.CreateStringResult("b"))
	})
}

func (suite *GlideTestSuite) TestSDiff_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		result, err := client.SDiff([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), result)
	})
}

func (suite *GlideTestSuite) TestSDiff_WithSingleKeyExist() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(key1, []string{"a", "b", "c"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SDiff([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), res2, 3)
		assert.Contains(suite.T(), res2, api.CreateStringResult("a"))
		assert.Contains(suite.T(), res2, api.CreateStringResult("b"))
		assert.Contains(suite.T(), res2, api.CreateStringResult("c"))
	})
}

func (suite *GlideTestSuite) TestSDiffStore() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()

		res1, err := client.SAdd(key1, []string{"a", "b", "c"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SAdd(key2, []string{"c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res2.Value())
		assert.False(suite.T(), res2.IsNil())

		res3, err := client.SDiffStore(key3, []string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res3.Value())
		assert.False(suite.T(), res3.IsNil())

		members, err := client.SMembers(key3)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), members, 2)
		assert.Contains(suite.T(), members, api.CreateStringResult("a"))
		assert.Contains(suite.T(), members, api.CreateStringResult("b"))
	})
}

func (suite *GlideTestSuite) TestSDiffStore_WithNotExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()

		res, err := client.SDiffStore(key3, []string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
		assert.False(suite.T(), res.IsNil())

		members, err := client.SMembers(key3)
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), members)
	})
}

func (suite *GlideTestSuite) TestSinter() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(key1, []string{"a", "b", "c", "d"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SAdd(key2, []string{"c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res2.Value())
		assert.False(suite.T(), res2.IsNil())

		members, err := client.SInter([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), members, 2)
		assert.Contains(suite.T(), members, api.CreateStringResult("c"))
		assert.Contains(suite.T(), members, api.CreateStringResult("d"))
	})
}

func (suite *GlideTestSuite) TestSinter_WithNotExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		members, err := client.SInter([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), members)
	})
}

func (suite *GlideTestSuite) TestSinterStore() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		stringKey := "{key}-4-" + uuid.NewString()
		nonExistingKey := "{key}-5-" + uuid.NewString()
		memberArray1 := []string{"a", "b", "c"}
		memberArray2 := []string{"c", "d", "e"}
		t := suite.T()

		res1, err := client.SAdd(key1, memberArray1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res1.Value())

		res2, err := client.SAdd(key2, memberArray2)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res2.Value())

		// store in new key
		res3, err := client.SInterStore(key3, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res3.Value())

		res4, err := client.SMembers(key3)
		assert.NoError(t, err)
		assert.Len(t, res4, 1)
		for key := range res4 {
			assert.Equal(t, key.Value(), "c")
		}

		// overwrite existing set, which is also a source set
		res5, err := client.SInterStore(key2, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res5.Value())

		res6, err := client.SMembers(key2)
		assert.NoError(t, err)
		assert.Len(t, res6, 1)
		for key := range res6 {
			assert.Equal(t, key.Value(), "c")
		}

		// source set is the same as the existing set
		res7, err := client.SInterStore(key1, []string{key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res7.Value())

		res8, err := client.SMembers(key2)
		assert.NoError(t, err)
		assert.Len(t, res8, 1)
		for key := range res8 {
			assert.Equal(t, key.Value(), "c")
		}

		// intersection with non-existing key
		res9, err := client.SInterStore(key1, []string{key2, nonExistingKey})
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res9.Value())

		// check that the key is now empty
		members1, err := client.SMembers(key1)
		assert.NoError(t, err)
		assert.Empty(t, members1)

		// invalid argument - key list must not be empty
		res10, err := client.SInterStore(key3, []string{})
		assert.Equal(suite.T(), int64(0), res10.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// non-set key
		_, err = client.Set(stringKey, "value")
		assert.NoError(t, err)

		res11, err := client.SInterStore(key3, []string{stringKey})
		assert.Equal(suite.T(), int64(0), res11.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// overwrite the non-set key
		res12, err := client.SInterStore(stringKey, []string{key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res12.Value())

		// check that the key is now empty
		res13, err := client.SMembers(stringKey)
		assert.NoError(t, err)
		assert.Len(t, res13, 1)
		for key := range res13 {
			assert.Equal(t, key.Value(), "c")
		}
	})
}

func (suite *GlideTestSuite) TestSInterCard() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")

	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(key1, []string{"one", "two", "three", "four"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		result1, err := client.SInterCard([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), result1.Value())
		assert.False(suite.T(), result1.IsNil())

		res2, err := client.SAdd(key2, []string{"two", "three", "four", "five"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())
		assert.False(suite.T(), res2.IsNil())

		result2, err := client.SInterCard([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), result2.Value())
		assert.False(suite.T(), result2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSInterCardLimit() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")

	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(key1, []string{"one", "two", "three", "four"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SAdd(key2, []string{"two", "three", "four", "five"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())
		assert.False(suite.T(), res2.IsNil())

		result1, err := client.SInterCardLimit([]string{key1, key2}, 2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result1.Value())
		assert.False(suite.T(), result1.IsNil())

		result2, err := client.SInterCardLimit([]string{key1, key2}, 4)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), result2.Value())
		assert.False(suite.T(), result2.IsNil())
	})
}

func (suite *GlideTestSuite) TestSRandMember() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.SAdd(key, []string{"one"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())
		assert.False(suite.T(), res.IsNil())

		member, err := client.SRandMember(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "one", member.Value())
		assert.False(suite.T(), member.IsNil())
	})
}

func (suite *GlideTestSuite) TestSPop() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		members := []string{"value1", "value2", "value3"}

		res, err := client.SAdd(key, members)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res.Value())
		assert.False(suite.T(), res.IsNil())

		popMember, err := client.SPop(key)
		assert.Nil(suite.T(), err)
		assert.Contains(suite.T(), members, popMember.Value())
		assert.False(suite.T(), popMember.IsNil())

		remainingMembers, err := client.SMembers(key)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), remainingMembers, 2)
		assert.NotContains(suite.T(), remainingMembers, popMember)
	})
}

func (suite *GlideTestSuite) TestSPop_LastMember() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.SAdd(key, []string{"lastValue"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		popMember, err := client.SPop(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "lastValue", popMember.Value())
		assert.False(suite.T(), popMember.IsNil())

		remainingMembers, err := client.SMembers(key)
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), remainingMembers)
	})
}

func (suite *GlideTestSuite) TestSMIsMember() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.NewString()
		stringKey := uuid.NewString()
		nonExistingKey := uuid.NewString()

		res1, err1 := client.SAdd(key1, []string{"one", "two"})
		assert.Nil(suite.T(), err1)
		assert.Equal(suite.T(), int64(2), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err2 := client.SMIsMember(key1, []string{"two", "three"})
		assert.Nil(suite.T(), err2)
		assert.Equal(
			suite.T(),
			[]api.Result[bool]{
				api.CreateBoolResult(true),
				api.CreateBoolResult(false),
			},
			res2)

		res3, err3 := client.SMIsMember(nonExistingKey, []string{"two"})
		assert.Nil(suite.T(), err3)
		assert.Equal(suite.T(), []api.Result[bool]{api.CreateBoolResult(false)}, res3)

		// invalid argument - member list must not be empty
		_, err4 := client.SMIsMember(key1, []string{})
		assert.NotNil(suite.T(), err4)
		assert.IsType(suite.T(), &api.RequestError{}, err4)

		// source key exists, but it is not a set
		setRes, setErr := client.Set(stringKey, "value")
		assert.Nil(suite.T(), setErr)
		assert.Equal(suite.T(), "OK", setRes.Value())
		_, err5 := client.SMIsMember(stringKey, []string{"two"})
		assert.NotNil(suite.T(), err5)
		assert.IsType(suite.T(), &api.RequestError{}, err5)
	})
}

func (suite *GlideTestSuite) TestSUnion() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		nonSetKey := uuid.NewString()
		memberList1 := []string{"a", "b", "c"}
		memberList2 := []string{"b", "c", "d", "e"}
		expected1 := map[api.Result[string]]struct{}{
			api.CreateStringResult("a"): {},
			api.CreateStringResult("b"): {},
			api.CreateStringResult("c"): {},
			api.CreateStringResult("d"): {},
			api.CreateStringResult("e"): {},
		}
		expected2 := map[api.Result[string]]struct{}{
			api.CreateStringResult("a"): {},
			api.CreateStringResult("b"): {},
			api.CreateStringResult("c"): {},
		}

		res1, err := client.SAdd(key1, memberList1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res1.Value())
		assert.False(suite.T(), res1.IsNil())

		res2, err := client.SAdd(key2, memberList2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())
		assert.False(suite.T(), res2.IsNil())

		res3, err := client.SUnion([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), reflect.DeepEqual(res3, expected1))

		res4, err := client.SUnion([]string{key3})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), map[api.Result[string]]struct{}{}, res4)

		res5, err := client.SUnion([]string{key1, key3})
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), reflect.DeepEqual(res5, expected2))

		// Exceptions with empty keys
		res6, err := client.SUnion([]string{})
		assert.Nil(suite.T(), res6)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// Exception with a non-set key
		suite.verifyOK(client.Set(nonSetKey, "value"))
		res7, err := client.SUnion([]string{nonSetKey, key1})
		assert.Nil(suite.T(), res7)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestSMove() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		stringKey := "{key}-4-" + uuid.NewString()
		nonExistingKey := "{key}-5-" + uuid.NewString()
		memberArray1 := []string{"1", "2", "3"}
		memberArray2 := []string{"2", "3"}
		t := suite.T()

		res1, err := client.SAdd(key1, memberArray1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res1.Value())

		res2, err := client.SAdd(key2, memberArray2)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res2.Value())

		// move an element
		res3, err := client.SMove(key1, key2, "1")
		assert.NoError(t, err)
		assert.True(t, res3.Value())

		res4, err := client.SMembers(key1)
		assert.NoError(t, err)
		expectedSet := map[api.Result[string]]struct{}{
			api.CreateStringResult("2"): {},
			api.CreateStringResult("3"): {},
		}
		assert.True(t, reflect.DeepEqual(expectedSet, res4))

		res5, err := client.SMembers(key2)
		assert.NoError(t, err)
		expectedSet = map[api.Result[string]]struct{}{
			api.CreateStringResult("1"): {},
			api.CreateStringResult("2"): {},
			api.CreateStringResult("3"): {},
		}
		assert.True(t, reflect.DeepEqual(expectedSet, res5))

		// moved element already exists in the destination set
		res6, err := client.SMove(key2, key1, "2")
		assert.NoError(t, err)
		assert.True(t, res6.Value())

		res7, err := client.SMembers(key1)
		assert.NoError(t, err)
		expectedSet = map[api.Result[string]]struct{}{
			api.CreateStringResult("2"): {},
			api.CreateStringResult("3"): {},
		}
		assert.True(t, reflect.DeepEqual(expectedSet, res7))

		res8, err := client.SMembers(key2)
		assert.NoError(t, err)
		expectedSet = map[api.Result[string]]struct{}{
			api.CreateStringResult("1"): {},
			api.CreateStringResult("3"): {},
		}
		assert.True(t, reflect.DeepEqual(expectedSet, res8))

		// attempt to move from a non-existing key
		res9, err := client.SMove(nonExistingKey, key1, "4")
		assert.NoError(t, err)
		assert.False(t, res9.Value())

		res10, err := client.SMembers(key1)
		assert.NoError(t, err)
		expectedSet = map[api.Result[string]]struct{}{
			api.CreateStringResult("2"): {},
			api.CreateStringResult("3"): {},
		}
		assert.True(t, reflect.DeepEqual(expectedSet, res10))

		// move to a new set
		res11, err := client.SMove(key1, key3, "2")
		assert.NoError(t, err)
		assert.True(t, res11.Value())

		res12, err := client.SMembers(key1)
		assert.NoError(t, err)
		assert.Len(t, res12, 1)
		assert.Contains(t, res12, api.CreateStringResult("3"))

		res13, err := client.SMembers(key3)
		assert.NoError(t, err)
		assert.Len(t, res13, 1)
		assert.Contains(t, res13, api.CreateStringResult("2"))

		// attempt to move a missing element
		res14, err := client.SMove(key1, key3, "42")
		assert.NoError(t, err)
		assert.False(t, res14.Value())

		res12, err = client.SMembers(key1)
		assert.NoError(t, err)
		assert.Len(t, res12, 1)
		assert.Contains(t, res12, api.CreateStringResult("3"))

		res13, err = client.SMembers(key3)
		assert.NoError(t, err)
		assert.Len(t, res13, 1)
		assert.Contains(t, res13, api.CreateStringResult("2"))

		// moving missing element to missing key
		res15, err := client.SMove(key1, nonExistingKey, "42")
		assert.NoError(t, err)
		assert.False(t, res15.Value())

		res12, err = client.SMembers(key1)
		assert.NoError(t, err)
		assert.Len(t, res12, 1)
		assert.Contains(t, res12, api.CreateStringResult("3"))

		// key exists but is not contain a set
		_, err = client.Set(stringKey, "value")
		assert.NoError(t, err)

		_, err = client.SMove(stringKey, key1, "_")
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestSScan() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		initialCursor := "0"
		defaultCount := 10
		// use large dataset to force an iterative cursor.
		numMembers := make([]string, 50000)
		numMembersResult := make([]api.Result[string], 50000)
		charMembers := []string{"a", "b", "c", "d", "e"}
		charMembersResult := []api.Result[string]{
			api.CreateStringResult("a"),
			api.CreateStringResult("b"),
			api.CreateStringResult("c"),
			api.CreateStringResult("d"),
			api.CreateStringResult("e"),
		}
		t := suite.T()

		// populate the dataset slice
		for i := 0; i < 50000; i++ {
			numMembers[i] = strconv.Itoa(i)
			numMembersResult[i] = api.CreateStringResult(strconv.Itoa(i))
		}

		// empty set
		resCursor, resCollection, err := client.SScan(key1, initialCursor)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor, resCursor.Value())
		assert.Empty(t, resCollection)

		// negative cursor
		if suite.serverVersion < "8.0.0" {
			resCursor, resCollection, err = client.SScan(key1, "-1")
			assert.NoError(t, err)
			assert.Equal(t, initialCursor, resCursor.Value())
			assert.Empty(t, resCollection)
		} else {
			_, _, err = client.SScan(key1, "-1")
			assert.NotNil(suite.T(), err)
			assert.IsType(suite.T(), &api.RequestError{}, err)
		}

		// result contains the whole set
		res, err := client.SAdd(key1, charMembers)
		assert.NoError(t, err)
		assert.Equal(t, int64(len(charMembers)), res.Value())
		resCursor, resCollection, err = client.SScan(key1, initialCursor)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor, resCursor.Value())
		assert.Equal(t, len(charMembers), len(resCollection))
		assert.True(t, isSubset(resCollection, charMembersResult))

		opts := options.NewBaseScanOptionsBuilder().SetMatch("a")
		resCursor, resCollection, err = client.SScanWithOptions(key1, initialCursor, opts)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor, resCursor.Value())
		assert.True(t, isSubset(resCollection, []api.Result[string]{api.CreateStringResult("a")}))

		// result contains a subset of the key
		res, err = client.SAdd(key1, numMembers)
		assert.NoError(t, err)
		assert.Equal(t, int64(50000), res.Value())
		resCursor, resCollection, err = client.SScan(key1, "0")
		assert.NoError(t, err)
		resultCollection := resCollection

		// 0 is returned for the cursor of the last iteration
		for resCursor.Value() != "0" {
			nextCursor, nextCol, err := client.SScan(key1, resCursor.Value())
			assert.NoError(t, err)
			assert.NotEqual(t, nextCursor, resCursor)
			assert.False(t, isSubset(resultCollection, nextCol))
			resultCollection = append(resultCollection, nextCol...)
			resCursor = nextCursor
		}
		assert.NotEmpty(t, resultCollection)
		assert.True(t, isSubset(numMembersResult, resultCollection))
		assert.True(t, isSubset(charMembersResult, resultCollection))

		// test match pattern
		opts = options.NewBaseScanOptionsBuilder().SetMatch("*")
		resCursor, resCollection, err = client.SScanWithOptions(key1, initialCursor, opts)
		assert.NoError(t, err)
		assert.NotEqual(t, initialCursor, resCursor.Value())
		assert.GreaterOrEqual(t, len(resCollection), defaultCount)

		// test count
		opts = options.NewBaseScanOptionsBuilder().SetCount(20)
		resCursor, resCollection, err = client.SScanWithOptions(key1, initialCursor, opts)
		assert.NoError(t, err)
		assert.NotEqual(t, initialCursor, resCursor.Value())
		assert.GreaterOrEqual(t, len(resCollection), 20)

		// test count with match, returns a non-empty array
		opts = options.NewBaseScanOptionsBuilder().SetMatch("1*").SetCount(20)
		resCursor, resCollection, err = client.SScanWithOptions(key1, initialCursor, opts)
		assert.NoError(t, err)
		assert.NotEqual(t, initialCursor, resCursor.Value())
		assert.GreaterOrEqual(t, len(resCollection), 0)

		// exceptions
		// non-set key
		_, err = client.Set(key2, "test")
		assert.NoError(t, err)

		_, _, err = client.SScan(key2, initialCursor)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLRange() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		resultList := []api.Result[string]{
			api.CreateStringResult("value1"),
			api.CreateStringResult("value2"),
			api.CreateStringResult("value3"),
			api.CreateStringResult("value4"),
		}
		res2, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), resultList, res2)

		res3, err := client.LRange("non_existing_key", int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{}, res3)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res4, err := client.LRange(key2, int64(0), int64(1))
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res4)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLIndex() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		res2, err := client.LIndex(key, int64(0))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "value1", res2.Value())
		assert.Equal(suite.T(), false, res2.IsNil())

		res3, err := client.LIndex(key, int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "value4", res3.Value())
		assert.Equal(suite.T(), false, res3.IsNil())

		res4, err := client.LIndex("non_existing_key", int64(0))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res4)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res5, err := client.LIndex(key2, int64(0))
		assert.Equal(suite.T(), api.CreateNilStringResult(), res5)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLTrim() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		suite.verifyOK(client.LTrim(key, int64(0), int64(1)))

		res2, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{api.CreateStringResult("value1"), api.CreateStringResult("value2")}, res2)

		suite.verifyOK(client.LTrim(key, int64(4), int64(2)))

		res3, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{}, res3)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res4, err := client.LIndex(key2, int64(0))
		assert.Equal(suite.T(), api.CreateNilStringResult(), res4)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLLen() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		res2, err := client.LLen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())
		assert.Equal(suite.T(), false, res2.IsNil())

		res3, err := client.LLen("non_existing_key")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res3.Value())
		assert.Equal(suite.T(), false, res3.IsNil())

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res4, err := client.LLen(key2)
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res4)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLRem() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value1", "value2", "value1", "value1", "value2"}
		key := uuid.NewString()

		res1, err := client.LPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res1.Value())

		res2, err := client.LRem(key, 2, "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Value())
		assert.Equal(suite.T(), false, res2.IsNil())
		res3, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("value2"),
				api.CreateStringResult("value2"),
				api.CreateStringResult("value1"),
			},
			res3,
		)

		res4, err := client.LRem(key, -1, "value2")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res4.Value())
		assert.Equal(suite.T(), false, res4.IsNil())
		res5, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{api.CreateStringResult("value2"), api.CreateStringResult("value1")}, res5)

		res6, err := client.LRem(key, 0, "value2")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res6.Value())
		assert.Equal(suite.T(), false, res6.IsNil())
		res7, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{api.CreateStringResult("value1")}, res7)

		res8, err := client.LRem("non_existing_key", 0, "value")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res8.Value())
		assert.Equal(suite.T(), false, res8.IsNil())
	})
}

func (suite *GlideTestSuite) TestRPopAndRPopCount() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value1", "value2", "value3", "value4"}
		key := uuid.NewString()

		res1, err := client.RPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		res2, err := client.RPop(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "value4", res2.Value())
		assert.Equal(suite.T(), false, res2.IsNil())

		res3, err := client.RPopCount(key, int64(2))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{api.CreateStringResult("value3"), api.CreateStringResult("value2")}, res3)

		res4, err := client.RPop("non_existing_key")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res4)

		res5, err := client.RPopCount("non_existing_key", int64(2))
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res5)
		assert.Nil(suite.T(), err)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res6, err := client.RPop(key2)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res6)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res7, err := client.RPopCount(key2, int64(2))
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res7)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLInsert() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		list := []string{"value1", "value2", "value3", "value4"}
		key := uuid.NewString()

		res1, err := client.RPush(key, list)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res1.Value())

		res2, err := client.LInsert(key, api.Before, "value2", "value1.5")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res2.Value())
		assert.Equal(suite.T(), false, res2.IsNil())

		res3, err := client.LInsert(key, api.After, "value3", "value3.5")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(6), res3.Value())
		assert.Equal(suite.T(), false, res3.IsNil())

		res4, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("value1"),
				api.CreateStringResult("value1.5"),
				api.CreateStringResult("value2"),
				api.CreateStringResult("value3"),
				api.CreateStringResult("value3.5"),
				api.CreateStringResult("value4"),
			},
			res4,
		)

		res5, err := client.LInsert("non_existing_key", api.Before, "pivot", "elem")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res5.Value())
		assert.Equal(suite.T(), false, res5.IsNil())

		res6, err := client.LInsert(key, api.Before, "value5", "value6")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), res6.Value())
		assert.Equal(suite.T(), false, res6.IsNil())

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(key2, "value"))

		res7, err := client.LInsert(key2, api.Before, "value5", "value6")
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res7)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestBLPop() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		listKey1 := "{listKey}-1-" + uuid.NewString()
		listKey2 := "{listKey}-2-" + uuid.NewString()

		res1, err := client.LPush(listKey1, []string{"value1", "value2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.BLPop([]string{listKey1, listKey2}, float64(0.5))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{api.CreateStringResult(listKey1), api.CreateStringResult("value2")}, res2)

		res3, err := client.BLPop([]string{listKey2}, float64(1.0))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res3)

		key := uuid.NewString()
		suite.verifyOK(client.Set(key, "value"))

		res4, err := client.BLPop([]string{key}, float64(1.0))
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res4)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestBRPop() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		listKey1 := "{listKey}-1-" + uuid.NewString()
		listKey2 := "{listKey}-2-" + uuid.NewString()

		res1, err := client.LPush(listKey1, []string{"value1", "value2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Value())

		res2, err := client.BRPop([]string{listKey1, listKey2}, float64(0.5))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{api.CreateStringResult(listKey1), api.CreateStringResult("value1")}, res2)

		res3, err := client.BRPop([]string{listKey2}, float64(1.0))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res3)

		key := uuid.NewString()
		suite.verifyOK(client.Set(key, "value"))

		res4, err := client.BRPop([]string{key}, float64(1.0))
		assert.Equal(suite.T(), ([]api.Result[string])(nil), res4)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestRPushX() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		key3 := uuid.NewString()

		res1, err := client.RPush(key1, []string{"value1"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res1.Value())

		res2, err := client.RPushX(key1, []string{"value2", "value3", "value4"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())

		res3, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("value1"),
				api.CreateStringResult("value2"),
				api.CreateStringResult("value3"),
				api.CreateStringResult("value4"),
			},
			res3,
		)

		res4, err := client.RPushX(key2, []string{"value1"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res4.Value())
		assert.Equal(suite.T(), false, res4.IsNil())

		res5, err := client.LRange(key2, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{}, res5)

		suite.verifyOK(client.Set(key3, "value"))

		res6, err := client.RPushX(key3, []string{"value1"})
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res6)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res7, err := client.RPushX(key2, []string{})
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res7)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLPushX() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		key3 := uuid.NewString()

		res1, err := client.LPush(key1, []string{"value1"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res1.Value())

		res2, err := client.LPushX(key1, []string{"value2", "value3", "value4"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())

		res3, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("value4"),
				api.CreateStringResult("value3"),
				api.CreateStringResult("value2"),
				api.CreateStringResult("value1"),
			},
			res3,
		)

		res4, err := client.LPushX(key2, []string{"value1"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res4.Value())
		assert.Equal(suite.T(), false, res4.IsNil())

		res5, err := client.LRange(key2, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.Result[string]{}, res5)

		suite.verifyOK(client.Set(key3, "value"))

		res6, err := client.LPushX(key3, []string{"value1"})
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res6)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res7, err := client.LPushX(key2, []string{})
		assert.Equal(suite.T(), api.CreateNilInt64Result(), res7)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLMPopAndLMPopCount() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		res1, err := client.LMPop([]string{key1}, api.Left)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res1)

		res2, err := client.LMPopCount([]string{key1}, api.Left, int64(1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res2)

		res3, err := client.LPush(key1, []string{"one", "two", "three", "four", "five"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res3.Value())
		res4, err := client.LPush(key2, []string{"one", "two", "three", "four", "five"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res4.Value())

		res5, err := client.LMPop([]string{key1}, api.Left)
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			map[api.Result[string]][]api.Result[string]{api.CreateStringResult(key1): {api.CreateStringResult("five")}},
			res5,
		)

		res6, err := client.LMPopCount([]string{key2, key1}, api.Right, int64(2))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			map[api.Result[string]][]api.Result[string]{
				api.CreateStringResult(key2): {api.CreateStringResult("one"), api.CreateStringResult("two")},
			},
			res6,
		)

		suite.verifyOK(client.Set(key3, "value"))

		res7, err := client.LMPop([]string{key3}, api.Left)
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res7)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res8, err := client.LMPop([]string{key3}, "Invalid")
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res8)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestBLMPopAndBLMPopCount() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		res1, err := client.BLMPop([]string{key1}, api.Left, float64(0.1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res1)

		res2, err := client.BLMPopCount([]string{key1}, api.Left, int64(1), float64(0.1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res2)

		res3, err := client.LPush(key1, []string{"one", "two", "three", "four", "five"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res3.Value())
		res4, err := client.LPush(key2, []string{"one", "two", "three", "four", "five"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), res4.Value())

		res5, err := client.BLMPop([]string{key1}, api.Left, float64(0.1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			map[api.Result[string]][]api.Result[string]{api.CreateStringResult(key1): {api.CreateStringResult("five")}},
			res5,
		)

		res6, err := client.BLMPopCount([]string{key2, key1}, api.Right, int64(2), float64(0.1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			map[api.Result[string]][]api.Result[string]{
				api.CreateStringResult(key2): {api.CreateStringResult("one"), api.CreateStringResult("two")},
			},
			res6,
		)

		suite.verifyOK(client.Set(key3, "value"))

		res7, err := client.BLMPop([]string{key3}, api.Left, float64(0.1))
		assert.Equal(suite.T(), (map[api.Result[string]][]api.Result[string])(nil), res7)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestLSet() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		nonExistentKey := uuid.NewString()

		res1, err := client.LSet(nonExistentKey, int64(0), "zero")
		assert.Equal(suite.T(), api.CreateNilStringResult(), res1)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res2, err := client.LPush(key, []string{"four", "three", "two", "one"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())

		res3, err := client.LSet(key, int64(10), "zero")
		assert.Equal(suite.T(), api.CreateNilStringResult(), res3)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res4, err := client.LSet(key, int64(0), "zero")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "OK", res4.Value())

		res5, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("zero"),
				api.CreateStringResult("two"),
				api.CreateStringResult("three"),
				api.CreateStringResult("four"),
			},
			res5,
		)

		res6, err := client.LSet(key, int64(-1), "zero")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "OK", res6.Value())

		res7, err := client.LRange(key, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("zero"),
				api.CreateStringResult("two"),
				api.CreateStringResult("three"),
				api.CreateStringResult("zero"),
			},
			res7,
		)
	})
}

func (suite *GlideTestSuite) TestLMove() {
	if suite.serverVersion < "6.2.0" {
		suite.T().Skip("This feature is added in version 6.2.0")
	}
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		nonExistentKey := "{key}-3" + uuid.NewString()
		nonListKey := "{key}-4" + uuid.NewString()

		res1, err := client.LMove(key1, key2, api.Left, api.Right)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res1)
		assert.Nil(suite.T(), err)

		res2, err := client.LPush(key1, []string{"four", "three", "two", "one"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())

		// only source exists, only source elements gets popped, creates a list at nonExistingKey
		res3, err := client.LMove(key1, nonExistentKey, api.Right, api.Left)
		assert.Equal(suite.T(), "four", res3.Value())
		assert.Nil(suite.T(), err)

		res4, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("one"),
				api.CreateStringResult("two"),
				api.CreateStringResult("three"),
			},
			res4,
		)

		// source and destination are the same, performing list rotation, "one" gets popped and added back
		res5, err := client.LMove(key1, key1, api.Left, api.Left)
		assert.Equal(suite.T(), "one", res5.Value())
		assert.Nil(suite.T(), err)

		res6, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("one"),
				api.CreateStringResult("two"),
				api.CreateStringResult("three"),
			},
			res6,
		)
		// normal use case, "three" gets popped and added to the left of destination
		res7, err := client.LPush(key2, []string{"six", "five", "four"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res7.Value())

		res8, err := client.LMove(key1, key2, api.Right, api.Left)
		assert.Equal(suite.T(), "three", res8.Value())
		assert.Nil(suite.T(), err)

		res9, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("one"),
				api.CreateStringResult("two"),
			},
			res9,
		)
		res10, err := client.LRange(key2, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("three"),
				api.CreateStringResult("four"),
				api.CreateStringResult("five"),
				api.CreateStringResult("six"),
			},
			res10,
		)

		// source exists but is not a list type key
		suite.verifyOK(client.Set(nonListKey, "value"))

		res11, err := client.LMove(nonListKey, key1, api.Left, api.Left)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res11)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// destination exists but is not a list type key
		suite.verifyOK(client.Set(nonListKey, "value"))

		res12, err := client.LMove(key1, nonListKey, api.Left, api.Left)
		assert.Equal(suite.T(), api.CreateNilStringResult(), res12)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestExists() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		// Test 1: Check if an existing key returns 1
		suite.verifyOK(client.Set(key, initialValue))
		result, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result.Value(), "The key should exist")

		// Test 2: Check if a non-existent key returns 0
		result, err = client.Exists([]string{"nonExistentKey"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), result.Value(), "The non-existent key should not exist")

		// Test 3: Multiple keys, some exist, some do not
		existingKey := uuid.New().String()
		testKey := uuid.New().String()
		suite.verifyOK(client.Set(existingKey, value))
		suite.verifyOK(client.Set(testKey, value))
		result, err = client.Exists([]string{testKey, existingKey, "anotherNonExistentKey"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result.Value(), "Two keys should exist")
	})
}

func (suite *GlideTestSuite) TestExpire() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		result, err := client.Expire(key, 1)
		assert.Nil(suite.T(), err, "Expected no error from Expire command")
		assert.True(suite.T(), result.Value(), "Expire command should return true when expiry is set")

		time.Sleep(1500 * time.Millisecond)

		resultGet, err := client.Get(key)
		assert.Nil(suite.T(), err, "Expected no error from Get command after expiry")
		assert.Equal(suite.T(), "", resultGet.Value(), "Key should be expired and return empty value")
	})
}

func (suite *GlideTestSuite) TestExpire_KeyDoesNotExist() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		// Trying to set an expiry on a non-existent key
		result, err := client.Expire(key, 1)
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), result.Value())
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		result, err := client.ExpireWithOptions(key, 2, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), result.Value())

		time.Sleep(2500 * time.Millisecond)

		resultGet, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", resultGet.Value())

		result, err = client.ExpireWithOptions(key, 1, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), result.Value())
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		resexp, err := client.ExpireWithOptions(key, 20, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resexp.Value())

		resultExpire, err := client.ExpireWithOptions(key, 1, api.HasExistingExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		time.Sleep(2 * time.Second)

		resultExpireTest, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)

		assert.Equal(suite.T(), int64(0), resultExpireTest.Value())
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		resultExpire, err := client.ExpireWithOptions(key, 2, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		resultExpire, err = client.ExpireWithOptions(key, 5, api.NewExpiryGreaterThanCurrent)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())
		time.Sleep(6 * time.Second)
		resultExpireTest, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExpireTest.Value())
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		resultExpire, err := client.ExpireWithOptions(key, 10, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		resultExpire, err = client.ExpireWithOptions(key, 5, api.NewExpiryLessThanCurrent)
		assert.Nil(suite.T(), err)

		assert.True(suite.T(), resultExpire.Value())

		resultExpire, err = client.ExpireWithOptions(key, 15, api.NewExpiryGreaterThanCurrent)
		assert.Nil(suite.T(), err)

		assert.True(suite.T(), resultExpire.Value())

		time.Sleep(16 * time.Second)
		resultExpireTest, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExpireTest.Value())
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		futureTimestamp := time.Now().Add(10 * time.Second).Unix()

		resultExpire, err := client.ExpireAtWithOptions(key, futureTimestamp, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())
		resultExpireAt, err := client.ExpireAt(key, futureTimestamp)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireAt.Value())
		resultExpireWithOptions, err := client.ExpireAtWithOptions(key, futureTimestamp+10, api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), resultExpireWithOptions.Value())
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		futureTimestamp := time.Now().Add(10 * time.Second).Unix()
		resultExpireAt, err := client.ExpireAt(key, futureTimestamp)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireAt.Value())

		resultExpireWithOptions, err := client.ExpireAtWithOptions(key, futureTimestamp+10, api.HasExistingExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		futureTimestamp := time.Now().Add(10 * time.Second).Unix()
		resultExpireAt, err := client.ExpireAt(key, futureTimestamp)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireAt.Value())

		newFutureTimestamp := time.Now().Add(20 * time.Second).Unix()
		resultExpireWithOptions, err := client.ExpireAtWithOptions(key, newFutureTimestamp, api.NewExpiryGreaterThanCurrent)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		futureTimestamp := time.Now().Add(10 * time.Second).Unix()
		resultExpireAt, err := client.ExpireAt(key, futureTimestamp)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireAt.Value())

		newFutureTimestamp := time.Now().Add(5 * time.Second).Unix()
		resultExpireWithOptions, err := client.ExpireAtWithOptions(key, newFutureTimestamp, api.NewExpiryLessThanCurrent)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(5 * time.Second)
		resultExpireAtTest, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)

		assert.Equal(suite.T(), int64(0), resultExpireAtTest.Value())
	})
}

func (suite *GlideTestSuite) TestPExpire() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		resultExpire, err := client.PExpire(key, 500)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		time.Sleep(600 * time.Millisecond)
		resultExpireCheck, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExpireCheck.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		initialExpire := 500
		resultExpire, err := client.PExpire(key, int64(initialExpire))
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		newExpire := 1000

		resultExpireWithOptions, err := client.PExpireWithOptions(key, int64(newExpire), api.HasExistingExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		newExpire := 500

		resultExpireWithOptions, err := client.PExpireWithOptions(key, int64(newExpire), api.HasNoExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(600 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		initialExpire := 500
		resultExpire, err := client.PExpire(key, int64(initialExpire))
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		newExpire := 1000

		resultExpireWithOptions, err := client.PExpireWithOptions(key, int64(newExpire), api.NewExpiryGreaterThanCurrent)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		initialExpire := 500
		resultExpire, err := client.PExpire(key, int64(initialExpire))
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		newExpire := 200

		resultExpireWithOptions, err := client.PExpireWithOptions(key, int64(newExpire), api.NewExpiryLessThanCurrent)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(600 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireAt() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		resultSet, err := client.Set(key, value)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultSet.Value() != "")

		expireAfterMilliseconds := time.Now().Unix() * 1000
		resultPExpireAt, err := client.PExpireAt(key, expireAfterMilliseconds)
		assert.Nil(suite.T(), err)

		assert.True(suite.T(), resultPExpireAt.Value())

		time.Sleep(6 * time.Second)

		resultpExists, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultpExists.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		timestamp := time.Now().Unix() * 1000
		result, err := client.PExpireAtWithOptions(key, timestamp, api.HasNoExpiry)

		assert.Nil(suite.T(), err)
		assert.True(suite.T(), result.Value())

		time.Sleep(2 * time.Second)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))
		initialExpire := 500
		resultExpire, err := client.PExpire(key, int64(initialExpire))
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())
		newExpire := time.Now().Unix()*1000 + 1000

		resultExpireWithOptions, err := client.PExpireAtWithOptions(key, newExpire, api.HasExistingExpiry)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		initialExpire := time.Now().UnixMilli() + 1000
		resultExpire, err := client.PExpireAt(key, initialExpire)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		newExpire := time.Now().UnixMilli() + 2000

		resultExpireWithOptions, err := client.PExpireAtWithOptions(key, newExpire, api.NewExpiryGreaterThanCurrent)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(2100 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		initialExpire := 1000
		resultExpire, err := client.PExpire(key, int64(initialExpire))
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpire.Value())

		newExpire := time.Now().Unix()*1000 + 500

		resultExpireWithOptions, err := client.PExpireAtWithOptions(key, newExpire, api.NewExpiryLessThanCurrent)
		assert.Nil(suite.T(), err)

		assert.True(suite.T(), resultExpireWithOptions.Value())

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultExist.Value())
	})
}

func (suite *GlideTestSuite) TestExpireTime() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		result, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value, result.Value())

		expireTime := time.Now().Unix() + 3
		resultExpAt, err := client.ExpireAt(key, expireTime)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpAt.Value())

		resexptime, err := client.ExpireTime(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), expireTime, resexptime.Value())

		time.Sleep(4 * time.Second)

		resultAfterExpiry, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", resultAfterExpiry.Value())
	})
}

func (suite *GlideTestSuite) TestExpireTime_KeyDoesNotExist() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()

		// Call ExpireTime on a key that doesn't exist
		expiryResult, err := client.ExpireTime(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), expiryResult.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireTime() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(key, value))

		result, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value, result.Value())

		pexpireTime := time.Now().UnixMilli() + 3000
		resultExpAt, err := client.PExpireAt(key, pexpireTime)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resultExpAt.Value())

		respexptime, err := client.PExpireTime(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), pexpireTime, respexptime.Value())

		time.Sleep(4 * time.Second)

		resultAfterExpiry, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", resultAfterExpiry.Value())
	})
}

func (suite *GlideTestSuite) Test_ZCard() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "{key}" + uuid.NewString()
		membersScores := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		t := suite.T()
		res1, err := client.ZAdd(key, membersScores)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res1.Value())

		res2, err := client.ZCard(key)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res2.Value())

		res3, err := client.ZRem(key, []string{"one"})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), res3.Value())

		res4, err := client.ZCard(key)
		assert.Nil(t, err)
		assert.Equal(t, int64(2), res4.Value())
	})
}

func (suite *GlideTestSuite) TestPExpireTime_KeyDoesNotExist() {
	suite.SkipIfServerVersionLowerThanBy("7.0.0")
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()

		// Call ExpireTime on a key that doesn't exist
		expiryResult, err := client.PExpireTime(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), expiryResult.Value())
	})
}

func (suite *GlideTestSuite) TestTTL_WithValidKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		resExpire, err := client.Expire(key, 1)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resExpire.Value())
		resTTL, err := client.TTL(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), resTTL.Value(), int64(1))
	})
}

func (suite *GlideTestSuite) TestTTL_WithExpiredKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		resExpire, err := client.Expire(key, 1)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resExpire.Value())

		time.Sleep(2 * time.Second)

		resTTL, err := client.TTL(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), resTTL.Value())
	})
}

func (suite *GlideTestSuite) TestPTTL_WithValidKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		resExpire, err := client.Expire(key, 1)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resExpire.Value())

		resPTTL, err := client.PTTL(key)
		assert.Nil(suite.T(), err)
		assert.Greater(suite.T(), resPTTL.Value(), int64(900))
	})
}

func (suite *GlideTestSuite) TestPTTL_WithExpiredKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		resExpire, err := client.Expire(key, 1)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), resExpire.Value())

		time.Sleep(2 * time.Second)

		resPTTL, err := client.PTTL(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), resPTTL.Value())
	})
}

func (suite *GlideTestSuite) TestPfAdd_SuccessfulAddition() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		res, err := client.PfAdd(key, []string{"a", "b", "c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())
	})
}

func (suite *GlideTestSuite) TestPfAdd_DuplicateElements() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()

		// case : Add elements and add same elements again
		res, err := client.PfAdd(key, []string{"a", "b", "c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())

		res2, err := client.PfAdd(key, []string{"a", "b", "c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Value())

		// case : (mixed elements) add new elements with 1 duplicate elements
		res1, err := client.PfAdd(key, []string{"f", "g", "h"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res1.Value())

		res2, err = client.PfAdd(key, []string{"i", "j", "g"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res2.Value())

		// case : add empty array(no elements to the HyperLogLog)
		res, err = client.PfAdd(key, []string{})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())
	})
}

func (suite *GlideTestSuite) TestPfCount_SingleKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		res, err := client.PfAdd(key, []string{"i", "j", "g"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())

		resCount, err := client.PfCount([]string{key})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), resCount.Value())
	})
}

func (suite *GlideTestSuite) TestPfCount_MultipleKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String() + "{group}"
		key2 := uuid.New().String() + "{group}"

		res, err := client.PfAdd(key1, []string{"a", "b", "c"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())

		res, err = client.PfAdd(key2, []string{"c", "d", "e"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())

		resCount, err := client.PfCount([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(5), resCount.Value())
	})
}

func (suite *GlideTestSuite) TestPfCount_NoExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String() + "{group}"
		key2 := uuid.New().String() + "{group}"

		resCount, err := client.PfCount([]string{key1, key2})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resCount.Value())
	})
}

func (suite *GlideTestSuite) TestBLMove() {
	if suite.serverVersion < "6.2.0" {
		suite.T().Skip("This feature is added in version 6.2.0")
	}
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		nonExistentKey := "{key}-3" + uuid.NewString()
		nonListKey := "{key}-4" + uuid.NewString()

		res1, err := client.BLMove(key1, key2, api.Left, api.Right, float64(0.1))
		assert.Equal(suite.T(), api.CreateNilStringResult(), res1)
		assert.Nil(suite.T(), err)

		res2, err := client.LPush(key1, []string{"four", "three", "two", "one"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(4), res2.Value())

		// only source exists, only source elements gets popped, creates a list at nonExistingKey
		res3, err := client.BLMove(key1, nonExistentKey, api.Right, api.Left, float64(0.1))
		assert.Equal(suite.T(), "four", res3.Value())
		assert.Nil(suite.T(), err)

		res4, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("one"),
				api.CreateStringResult("two"),
				api.CreateStringResult("three"),
			},
			res4,
		)

		// source and destination are the same, performing list rotation, "one" gets popped and added back
		res5, err := client.BLMove(key1, key1, api.Left, api.Left, float64(0.1))
		assert.Equal(suite.T(), "one", res5.Value())
		assert.Nil(suite.T(), err)

		res6, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("one"),
				api.CreateStringResult("two"),
				api.CreateStringResult("three"),
			},
			res6,
		)
		// normal use case, "three" gets popped and added to the left of destination
		res7, err := client.LPush(key2, []string{"six", "five", "four"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res7.Value())

		res8, err := client.BLMove(key1, key2, api.Right, api.Left, float64(0.1))
		assert.Equal(suite.T(), "three", res8.Value())
		assert.Nil(suite.T(), err)

		res9, err := client.LRange(key1, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("one"),
				api.CreateStringResult("two"),
			},
			res9,
		)
		res10, err := client.LRange(key2, int64(0), int64(-1))
		assert.Nil(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]api.Result[string]{
				api.CreateStringResult("three"),
				api.CreateStringResult("four"),
				api.CreateStringResult("five"),
				api.CreateStringResult("six"),
			},
			res10,
		)

		// source exists but is not a list type key
		suite.verifyOK(client.Set(nonListKey, "value"))

		res11, err := client.BLMove(nonListKey, key1, api.Left, api.Left, float64(0.1))
		assert.Equal(suite.T(), api.CreateNilStringResult(), res11)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// destination exists but is not a list type key
		suite.verifyOK(client.Set(nonListKey, "value"))

		res12, err := client.BLMove(key1, nonListKey, api.Left, api.Left, float64(0.1))
		assert.Equal(suite.T(), api.CreateNilStringResult(), res12)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestDel_MultipleKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "testKey1_" + uuid.New().String()
		key2 := "testKey2_" + uuid.New().String()
		key3 := "testKey3_" + uuid.New().String()

		suite.verifyOK(client.Set(key1, initialValue))
		suite.verifyOK(client.Set(key2, initialValue))
		suite.verifyOK(client.Set(key3, initialValue))

		deletedCount, err := client.Del([]string{key1, key2, key3})

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), deletedCount.Value())

		result1, err1 := client.Get(key1)
		result2, err2 := client.Get(key2)
		result3, err3 := client.Get(key3)

		assert.Nil(suite.T(), err1)
		assert.True(suite.T(), result1.IsNil())

		assert.Nil(suite.T(), err2)
		assert.True(suite.T(), result2.IsNil())

		assert.Nil(suite.T(), err3)
		assert.True(suite.T(), result3.IsNil())
	})
}

func (suite *GlideTestSuite) TestType() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		// Test 1: Check if the value is string
		keyName := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(keyName, initialValue))
		result, err := client.Type(keyName)
		assert.Nil(suite.T(), err)
		assert.IsType(suite.T(), result, api.CreateStringResult("string"), "Value is string")

		// Test 2: Check if the value is list
		key1 := "{keylist}-1" + uuid.NewString()
		resultLPush, err := client.LPush(key1, []string{"one", "two", "three"})
		assert.Equal(suite.T(), int64(3), resultLPush.Value())
		assert.Nil(suite.T(), err)
		resultType, err := client.Type(key1)
		assert.Nil(suite.T(), err)
		assert.IsType(suite.T(), resultType, api.CreateStringResult("list"), "Value is list")
	})
}

func (suite *GlideTestSuite) TestTouch() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		// Test 1: Check if an touch valid key
		keyName := "{keyName}" + uuid.NewString()
		keyName1 := "{keyName1}" + uuid.NewString()
		suite.verifyOK(client.Set(keyName, initialValue))
		suite.verifyOK(client.Set(keyName1, "anotherValue"))
		result, err := client.Touch([]string{keyName, keyName1})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result.Value(), "The touch should be 2")

		// Test 2: Check if an touch invalid key
		resultInvalidKey, err := client.Touch([]string{"invalidKey", "invalidKey1"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultInvalidKey.Value(), "The touch should be 0")
	})
}

func (suite *GlideTestSuite) TestUnlink() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		// Test 1: Check if an unlink valid key
		keyName := "{keyName}" + uuid.NewString()
		keyName1 := "{keyName1}" + uuid.NewString()
		suite.verifyOK(client.Set(keyName, initialValue))
		suite.verifyOK(client.Set(keyName1, "anotherValue"))
		resultValidKey, err := client.Unlink([]string{keyName, keyName1})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), resultValidKey.Value(), "The unlink should be 2")

		// Test 2: Check if an unlink for invalid key
		resultInvalidKey, err := client.Unlink([]string{"invalidKey2", "invalidKey3"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultInvalidKey.Value(), "The unlink should be 0")
	})
}

func (suite *GlideTestSuite) TestRename() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		// Test 1 Check if the command successfully renamed
		key := "{keyName}" + uuid.NewString()
		initialValueRename := "TestRename_RenameValue"
		newRenameKey := "{newkeyName}" + uuid.NewString()
		suite.verifyOK(client.Set(key, initialValueRename))
		client.Rename(key, newRenameKey)

		// Test 2 Check if the rename command return false if the key/newkey is invalid.
		key1 := "{keyName}" + uuid.NewString()
		res1, err := client.Rename(key1, "invalidKey")
		assert.Equal(suite.T(), "", res1.Value())
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestRenamenx() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		// Test 1 Check if the renamenx command return true if key was renamed to newKey
		key := "{keyName}" + uuid.NewString()
		key2 := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(key, initialValue))
		res1, err := client.Renamenx(key, key2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), true, res1.Value())

		// Test 2 Check if the renamenx command return false if newKey already exists.
		key3 := "{keyName}" + uuid.NewString()
		key4 := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(key3, initialValue))
		suite.verifyOK(client.Set(key4, initialValue))
		res2, err := client.Renamenx(key3, key4)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Value())
	})
}

func (suite *GlideTestSuite) TestXAdd() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		// stream does not exist
		res, err := client.XAdd(key, [][]string{{"field1", "value1"}, {"field1", "value2"}})
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), res.IsNil())
		// don't check the value, because it contains server's timestamp

		// adding data to existing stream
		res, err = client.XAdd(key, [][]string{{"field3", "value3"}})
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), res.IsNil())

		// incorrect input
		_, err = client.XAdd(key, [][]string{})
		assert.NotNil(suite.T(), err)
		_, err = client.XAdd(key, [][]string{{"1", "2", "3"}})
		assert.NotNil(suite.T(), err)

		// key is not a string
		key = uuid.NewString()
		client.Set(key, "abc")
		_, err = client.XAdd(key, [][]string{{"f", "v"}})
		assert.NotNil(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestXAddWithOptions() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		// stream does not exist
		res, err := client.XAddWithOptions(
			key,
			[][]string{{"field1", "value1"}},
			options.NewXAddOptions().SetDontMakeNewStream(),
		)
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), res.IsNil())

		// adding data to with given ID
		res, err = client.XAddWithOptions(key, [][]string{{"field1", "value1"}}, options.NewXAddOptions().SetId("0-1"))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "0-1", res.Value())

		client.XAdd(key, [][]string{{"field2", "value2"}})
		// TODO run XLen there
		// this will trim the first entry.
		res, err = client.XAddWithOptions(
			key,
			[][]string{{"field3", "value3"}},
			options.NewXAddOptions().SetTrimOptions(options.NewXTrimOptionsWithMaxLen(2).SetExactTrimming()),
		)
		assert.Nil(suite.T(), err)
		assert.False(suite.T(), res.IsNil())
		// TODO run XLen there
	})
}

func (suite *GlideTestSuite) TestZAddAndZAddIncr() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		key2 := uuid.New().String()
		key3 := uuid.New().String()
		key4 := uuid.New().String()
		membersScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		t := suite.T()

		res, err := client.ZAdd(key, membersScoreMap)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res.Value())

		resIncr, err := client.ZAddIncr(key, "one", float64(2))
		assert.Nil(t, err)
		assert.Equal(t, float64(3), resIncr.Value())

		// exceptions
		// non-sortedset key
		_, err = client.Set(key2, "test")
		assert.NoError(t, err)

		_, err = client.ZAdd(key2, membersScoreMap)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// wrong key type for zaddincr
		_, err = client.ZAddIncr(key2, "one", float64(2))
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		// with NX & XX
		onlyIfExistsOpts := options.NewZAddOptionsBuilder().SetConditionalChange(options.OnlyIfExists)
		onlyIfDoesNotExistOpts := options.NewZAddOptionsBuilder().SetConditionalChange(options.OnlyIfDoesNotExist)

		res, err = client.ZAddWithOptions(key3, membersScoreMap, onlyIfExistsOpts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())

		res, err = client.ZAddWithOptions(key3, membersScoreMap, onlyIfDoesNotExistOpts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res.Value())

		resIncr, err = client.ZAddIncrWithOptions(key3, "one", 5, onlyIfDoesNotExistOpts)
		assert.NotNil(suite.T(), err)
		assert.True(suite.T(), resIncr.IsNil())

		resIncr, err = client.ZAddIncrWithOptions(key3, "one", 5, onlyIfExistsOpts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), float64(6), resIncr.Value())

		// with GT or LT
		membersScoreMap2 := map[string]float64{
			"one":   -3.0,
			"two":   2.0,
			"three": 3.0,
		}

		res, err = client.ZAdd(key4, membersScoreMap2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res.Value())

		membersScoreMap2["one"] = 10.0

		gtOpts := options.NewZAddOptionsBuilder().SetUpdateOptions(options.ScoreGreaterThanCurrent)
		ltOpts := options.NewZAddOptionsBuilder().SetUpdateOptions(options.ScoreLessThanCurrent)
		gtOptsChanged, _ := options.NewZAddOptionsBuilder().SetUpdateOptions(options.ScoreGreaterThanCurrent).SetChanged(true)
		ltOptsChanged, _ := options.NewZAddOptionsBuilder().SetUpdateOptions(options.ScoreLessThanCurrent).SetChanged(true)

		res, err = client.ZAddWithOptions(key4, membersScoreMap2, gtOptsChanged)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())

		res, err = client.ZAddWithOptions(key4, membersScoreMap2, ltOptsChanged)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Value())

		resIncr, err = client.ZAddIncrWithOptions(key4, "one", -3, ltOpts)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), float64(7), resIncr.Value())

		resIncr, err = client.ZAddIncrWithOptions(key4, "one", -3, gtOpts)
		assert.NotNil(suite.T(), err)
		assert.True(suite.T(), resIncr.IsNil())
	})
}

func (suite *GlideTestSuite) TestZincrBy() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		key2 := uuid.New().String()

		// key does not exist
		res1, err := client.ZIncrBy(key1, 2.5, "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), 2.5, res1.Value())

		// key exists, but value doesn't
		res2, err := client.ZIncrBy(key1, -3.3, "value2")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), -3.3, res2.Value())

		// updating existing value in existing key
		res3, err := client.ZIncrBy(key1, 1.0, "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), 3.5, res3.Value())

		// Key exists, but it is not a sorted set
		res4, err := client.SAdd(key2, []string{"one", "two"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res4.Value())

		_, err = client.ZIncrBy(key2, 0.5, "_")
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestBZPopMin() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{zset}-1-" + uuid.NewString()
		key2 := "{zset}-2-" + uuid.NewString()
		key3 := "{zset}-2-" + uuid.NewString()

		// Add elements to key1
		zaddResult1, err := client.ZAdd(key1, map[string]float64{"a": 1.0, "b": 1.5})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), zaddResult1.Value())

		// Add elements to key2
		zaddResult2, err := client.ZAdd(key2, map[string]float64{"c": 2.0})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), zaddResult2.Value())

		// Pop minimum element from key1 and key2
		bzpopminResult1, err := client.BZPopMin([]string{key1, key2}, float64(.5))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.KeyWithMemberAndScore{Key: key1, Member: "a", Score: 1.0}, bzpopminResult1.Value())

		// Attempt to pop from non-existent key3
		bzpopminResult2, err := client.BZPopMin([]string{key3}, float64(1))
		assert.Nil(suite.T(), err)
		assert.True(suite.T(), bzpopminResult2.IsNil())

		// Pop minimum element from key2
		bzpopminResult3, err := client.BZPopMin([]string{key3, key2}, float64(.5))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.KeyWithMemberAndScore{Key: key2, Member: "c", Score: 2.0}, bzpopminResult3.Value())

		// Set key3 to a non-sorted set value
		setResult, err := client.Set(key3, "value")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "OK", setResult.Value())

		// Attempt to pop from key3 which is not a sorted set
		_, err = client.BZPopMin([]string{key3}, float64(.5))
		if assert.Error(suite.T(), err) {
			assert.IsType(suite.T(), &api.RequestError{}, err)
		}
	})
}

func (suite *GlideTestSuite) TestZPopMin() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		key2 := uuid.New().String()
		memberScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		res, err := client.ZAdd(key1, memberScoreMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res.Value())

		res2, err := client.ZPopMin(key1)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), res2, 1)
		assert.Equal(suite.T(), float64(1.0), res2[api.CreateStringResult("one")].Value())

		res3, err := client.ZPopMinWithCount(key1, 2)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), res3, 2)
		assert.Equal(suite.T(), float64(2.0), res3[api.CreateStringResult("two")].Value())
		assert.Equal(suite.T(), float64(3.0), res3[api.CreateStringResult("three")].Value())

		// non sorted set key
		_, err = client.Set(key2, "test")
		assert.Nil(suite.T(), err)

		_, err = client.ZPopMin(key2)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestZPopMax() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		key2 := uuid.New().String()
		memberScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		res, err := client.ZAdd(key1, memberScoreMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res.Value())

		res2, err := client.ZPopMax(key1)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), res2, 1)
		assert.Equal(suite.T(), float64(3.0), res2[api.CreateStringResult("three")].Value())

		res3, err := client.ZPopMaxWithCount(key1, 2)
		assert.Nil(suite.T(), err)
		assert.Len(suite.T(), res3, 2)
		assert.Equal(suite.T(), float64(2.0), res3[api.CreateStringResult("two")].Value())
		assert.Equal(suite.T(), float64(1.0), res3[api.CreateStringResult("one")].Value())

		// non sorted set key
		_, err = client.Set(key2, "test")
		assert.Nil(suite.T(), err)

		_, err = client.ZPopMax(key2)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestZRem() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		memberScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		res, err := client.ZAdd(key, memberScoreMap)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res.Value())

		// no members to remove
		_, err = client.ZRem(key, []string{})
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res, err = client.ZRem(key, []string{"one"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res.Value())

		// TODO: run ZCard there
		res, err = client.ZRem(key, []string{"one", "two", "three"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res.Value())

		// non sorted set key
		_, err = client.Set(key, "test")
		assert.Nil(suite.T(), err)

		_, err = client.ZRem(key, []string{"value"})
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestXClaim() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		client.XAdd(key, [][]string{{"field1", "value1"}})

		// TODO: finish test case using custom commands
	})
}
