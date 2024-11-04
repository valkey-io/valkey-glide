// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"math"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
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
		key := "TestSetWithOptions_OnlyIfExists_overwrite"
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
		key := "TestSetWithOptions_OnlyIfExists_missingKey"
		opts := api.NewSetOptionsBuilder().SetConditionalSet(api.OnlyIfExists)
		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfDoesNotExist_missingKey"
		opts := api.NewSetOptionsBuilder().SetConditionalSet(api.OnlyIfDoesNotExist)
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfDoesNotExist_existingKey"
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
		key := "TestSetWithOptions_KeepExistingExpiry"
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
		key := "TestSetWithOptions_UpdateExistingExpiry"
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
		key := "TestGetEx_ExisitingKey"
		suite.verifyOK(client.Set(key, initialValue))

		result, err := client.GetEx(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Value())

		key = "TestGetEx_NonExisitingKey"
		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetExWithOptions_PersistKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestGetExWithOptions_PersistKey"
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
		key := "TestGetExWithOptions_UpdateExpiry"
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
		key := "TestSetWithOptions_ReturnOldValue_nonExistentKey"
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
