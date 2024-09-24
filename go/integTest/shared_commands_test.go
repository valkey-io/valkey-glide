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
		assert.Equal(suite.T(), initialValue, result.Val)
	})
}

func (suite *GlideTestSuite) TestSetAndGet_byteString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		invalidUTF8Value := "\xff\xfe\xfd"
		suite.verifyOK(client.Set(keyName, invalidUTF8Value))
		result, err := client.Get(keyName)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), invalidUTF8Value, result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		suite.verifyOK(client.Set(keyName, initialValue))

		opts := &api.SetOptions{ReturnOldValue: true}
		result, err := client.SetWithOptions(keyName, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_overwrite() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfExists_overwrite"
		suite.verifyOK(client.Set(key, initialValue))

		opts := &api.SetOptions{ConditionalSet: api.OnlyIfExists}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfExists_missingKey"
		opts := &api.SetOptions{ConditionalSet: api.OnlyIfExists}
		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfDoesNotExist_missingKey"
		opts := &api.SetOptions{ConditionalSet: api.OnlyIfDoesNotExist}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfDoesNotExist_existingKey"
		opts := &api.SetOptions{ConditionalSet: api.OnlyIfDoesNotExist}
		suite.verifyOK(client.Set(key, initialValue))

		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_KeepExistingExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_KeepExistingExpiry"
		opts := &api.SetOptions{Expiry: &api.Expiry{Type: api.Milliseconds, Count: uint64(2000)}}
		suite.verifyOK(client.SetWithOptions(key, initialValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Val)

		opts = &api.SetOptions{Expiry: &api.Expiry{Type: api.KeepExisting}}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Val)

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_UpdateExistingExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_UpdateExistingExpiry"
		opts := &api.SetOptions{Expiry: &api.Expiry{Type: api.Milliseconds, Count: uint64(100500)}}
		suite.verifyOK(client.SetWithOptions(key, initialValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result.Val)

		opts = &api.SetOptions{Expiry: &api.Expiry{Type: api.Milliseconds, Count: uint64(2000)}}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result.Val)

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue_nonExistentKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_ReturnOldValue_nonExistentKey"
		opts := &api.SetOptions{ReturnOldValue: true}

		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
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
		stringValue := api.StringValue{Val: value, IsNil: false}
		nullStringValue := api.StringValue{Val: "", IsNil: true}
		values := []api.StringValue{stringValue, stringValue, nullStringValue}
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
		assert.True(suite.T(), res.Val)
		keys := []string{key1, key2, key3}
		stringValue := api.StringValue{Val: value, IsNil: false}
		values := []api.StringValue{stringValue, stringValue, stringValue}
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
		assert.False(suite.T(), res.Val)
		keys := []string{key1, key2, key3}
		oldStringValue := api.StringValue{Val: oldValue, IsNil: false}
		nullStringValue := api.StringValue{Val: "", IsNil: true}
		values := []api.StringValue{oldStringValue, nullStringValue, nullStringValue}
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
		assert.Equal(suite.T(), int64(11), res1.Val)

		res2, err := client.IncrBy(key, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(21), res2.Val)

		res3, err := client.IncrByFloat(key, float64(10.1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), float64(31.1), res3.Val)
	})
}

func (suite *GlideTestSuite) TestIncrCommands_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		res1, err := client.Incr(key1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res1.Val)

		key2 := uuid.New().String()
		res2, err := client.IncrBy(key2, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(10), res2.Val)

		key3 := uuid.New().String()
		res3, err := client.IncrByFloat(key3, float64(10.1))
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), float64(10.1), res3.Val)
	})
}

func (suite *GlideTestSuite) TestIncrCommands_TypeError() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, "stringValue"))

		res1, err := client.Incr(key)
		assert.Equal(suite.T(), int64(0), res1.Val)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res2, err := client.IncrBy(key, 10)
		assert.Equal(suite.T(), int64(0), res2.Val)
		assert.NotNil(suite.T(), err)
		assert.IsType(suite.T(), &api.RequestError{}, err)

		res3, err := client.IncrByFloat(key, float64(10.1))
		assert.Equal(suite.T(), float64(0), res3.Val)
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
		assert.Equal(suite.T(), int64(9), res1.Val)

		res2, err := client.DecrBy(key, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), res2.Val)
	})
}

func (suite *GlideTestSuite) TestDecrCommands_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := uuid.New().String()
		res1, err := client.Decr(key1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), res1.Val)

		key2 := uuid.New().String()
		res2, err := client.DecrBy(key2, 10)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(-10), res2.Val)
	})
}

func (suite *GlideTestSuite) TestStrlen_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(key, value))

		res, err := client.Strlen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(len(value)), res.Val)
	})
}

func (suite *GlideTestSuite) TestStrlen_nonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		res, err := client.Strlen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Val)
	})
}

func (suite *GlideTestSuite) TestSetRange_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		res, err := client.SetRange(key, 0, "Dummy string")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(12), res.Val)

		res, err = client.SetRange(key, 6, "values")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(12), res.Val)
		res1, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values", res1.Val)

		res, err = client.SetRange(key, 15, "test")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(19), res.Val)
		res1, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values\x00\x00\x00test", res1.Val)

		res, err = client.SetRange(key, math.MaxInt32, "test")
		assert.Equal(suite.T(), int64(0), res.Val)
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
		assert.Equal(suite.T(), int64(14), res.Val)

		res, err = client.SetRange(key, 6, "values ")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(14), res.Val)
		res1, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values g", res1.Val)

		res, err = client.SetRange(key, 15, "test")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(19), res.Val)
		res1, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy values g\x00test", res1.Val)
	})
}

func (suite *GlideTestSuite) TestGetRange_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(key, "Dummy string"))

		res, err := client.GetRange(key, 0, 4)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy", res.Val)

		res, err = client.GetRange(key, -6, -1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "string", res.Val)

		res, err = client.GetRange(key, -1, -6)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Val)

		res, err = client.GetRange(key, 15, 16)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Val)

		nonExistingKey := uuid.New().String()
		res, err = client.GetRange(nonExistingKey, 0, 5)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Val)
	})
}

func (suite *GlideTestSuite) TestGetRange_binaryString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		nonUtf8String := "Dummy \xFF string"
		suite.verifyOK(client.Set(key, nonUtf8String))

		res, err := client.GetRange(key, 4, 6)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "y \xFF", res.Val)
	})
}

func (suite *GlideTestSuite) TestAppend_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value1 := uuid.New().String()
		value2 := uuid.New().String()

		res, err := client.Append(key, value1)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(len(value1)), res.Val)
		res1, err := client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value1, res1.Val)

		res, err = client.Append(key, value2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(len(value1)+len(value2)), res.Val)
		res1, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value1+value2, res1.Val)
	})
}

func (suite *GlideTestSuite) TestLCS_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()

		res, err := client.LCS(key1, key2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", res.Val)

		suite.verifyOK(client.Set(key1, "Dummy string"))
		suite.verifyOK(client.Set(key2, "Dummy value"))

		res, err = client.LCS(key1, key2)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "Dummy ", res.Val)
	})
}

func (suite *GlideTestSuite) TestGetDel_ExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()
		value := "testValue"

		suite.verifyOK(client.Set(key, value))
		result, err := client.GetDel(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), value, result.Val)

		result, err = client.Get(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
	})
}

func (suite *GlideTestSuite) TestGetDel_NonExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.New().String()

		result, err := client.GetDel(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
	})
}

func (suite *GlideTestSuite) TestGetDel_EmptyKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		result, err := client.GetDel("")

		assert.NotNil(suite.T(), err)
		assert.Equal(suite.T(), "", result.Val)
		assert.Equal(suite.T(), "key is required", err.Error())
	})
}

func (suite *GlideTestSuite) TestHSet_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.New().String()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Val)
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
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HGetAll(key)
		key1 := api.StringValue{Val: string([]byte{0xFF, 0x00, 0xAA}), IsNil: false}
		value1 := api.StringValue{Val: string([]byte{0xDE, 0xAD, 0xBE, 0xEF}), IsNil: false}
		key2 := api.StringValue{Val: string([]byte{0x01, 0x02, 0x03, 0xFE}), IsNil: false}
		value2 := api.StringValue{Val: string([]byte{0xCA, 0xFE, 0xBA, 0xBE}), IsNil: false}
		fieldsResult := map[api.StringValue]api.StringValue{
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
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Val)

		fields["field3"] = "value3"
		res3, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res3.Val)
	})
}

func (suite *GlideTestSuite) TestHGet_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HGet(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "value1", res2.Val)
	})
}

func (suite *GlideTestSuite) TestHGet_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.HGet(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.StringValue{Val: "", IsNil: true}, res1)
	})
}

func (suite *GlideTestSuite) TestHGet_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HGet(key, "foo")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), api.StringValue{Val: "", IsNil: true}, res2)
	})
}

func (suite *GlideTestSuite) TestHGetAll_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		field1 := api.StringValue{Val: "field1", IsNil: false}
		value1 := api.StringValue{Val: "value1", IsNil: false}
		field2 := api.StringValue{Val: "field2", IsNil: false}
		value2 := api.StringValue{Val: "value2", IsNil: false}
		fieldsResult := map[api.StringValue]api.StringValue{field1: value1, field2: value2}
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
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HMGet(key, []string{"field1", "field2", "field3"})
		value1 := api.StringValue{Val: "value1", IsNil: false}
		value2 := api.StringValue{Val: "value2", IsNil: false}
		nullValue := api.StringValue{Val: "", IsNil: true}
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.StringValue{value1, value2, nullValue}, res2)
	})
}

func (suite *GlideTestSuite) TestHMGet_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HMGet(key, []string{"field1", "field2", "field3"})
		nullValue := api.StringValue{Val: "", IsNil: true}
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.StringValue{nullValue, nullValue, nullValue}, res)
	})
}

func (suite *GlideTestSuite) TestHMGet_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HMGet(key, []string{"field3", "field4"})
		nullValue := api.StringValue{Val: "", IsNil: true}
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), []api.StringValue{nullValue, nullValue}, res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HSetNX(key, "field1", "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Val)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res1, err := client.HSetNX(key, "field1", "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), true, res1.Val)

		res2, err := client.HGetAll(key)
		field1 := api.StringValue{Val: "field1", IsNil: false}
		value1 := api.StringValue{Val: "value1", IsNil: false}
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), map[api.StringValue]api.StringValue{field1: value1}, res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HSetNX(key, "field1", "value1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Val)
	})
}

func (suite *GlideTestSuite) TestHDel() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HDel(key, []string{"field1", "field2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Val)

		res3, err := client.HGetAll(key)
		assert.Nil(suite.T(), err)
		assert.Empty(suite.T(), res3)

		res4, err := client.HDel(key, []string{"field1", "field2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res4.Val)
	})
}

func (suite *GlideTestSuite) TestHDel_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		res, err := client.HDel(key, []string{"field1", "field2"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Val)
	})
}

func (suite *GlideTestSuite) TestHDel_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HDel(key, []string{"field3", "field4"})
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Val)
	})
}

func (suite *GlideTestSuite) TestHLen() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HLen(key)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res2.Val)
	})
}

func (suite *GlideTestSuite) TestHLen_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()
		res, err := client.HLen(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Val)
	})
}

func (suite *GlideTestSuite) TestHVals_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HVals(key)
		value1 := api.StringValue{Val: "value1", IsNil: false}
		value2 := api.StringValue{Val: "value2", IsNil: false}
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
		assert.Nil(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHExists_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HExists(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), true, res2.Val)
	})
}

func (suite *GlideTestSuite) TestHExists_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HExists(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res.Val)
	})
}

func (suite *GlideTestSuite) TestHExists_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HExists(key, "field3")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), false, res2.Val)
	})
}

func (suite *GlideTestSuite) TestHKeys_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HKeys(key)
		field1 := api.StringValue{Val: "field1", IsNil: false}
		field2 := api.StringValue{Val: "field2", IsNil: false}
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
		assert.Nil(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HStrLen(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(6), res2.Val)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := uuid.NewString()

		res, err := client.HStrLen(key, "field1")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res.Val)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithNotExistingField() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(key, fields)
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res1.Val)

		res2, err := client.HStrLen(key, "field3")
		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res2.Val)
	})
}
