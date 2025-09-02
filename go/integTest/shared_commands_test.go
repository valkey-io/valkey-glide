// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"math"
	"reflect"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

const (
	keyName      = "key"
	initialValue = "value"
	anotherValue = "value2"
)

func (suite *GlideTestSuite) TestSetAndGet_noOptions() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		suite.verifyOK(client.Set(context.Background(), keyName, initialValue))
		result, err := client.Get(context.Background(), keyName)

		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetAndGet_byteString() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		invalidUTF8Value := "\xff\xfe\xfd"
		suite.verifyOK(client.Set(context.Background(), keyName, invalidUTF8Value))
		result, err := client.Get(context.Background(), keyName)

		suite.NoError(err)
		assert.Equal(suite.T(), invalidUTF8Value, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		suite.verifyOK(client.Set(context.Background(), keyName, initialValue))

		opts := options.NewSetOptions().SetReturnOldValue(true)
		result, err := client.SetWithOptions(context.Background(), keyName, anotherValue, *opts)

		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_overwrite() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))

		opts := options.NewSetOptions().SetOnlyIfExists()
		result, err := client.SetWithOptions(context.Background(), key, anotherValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), anotherValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_missingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		opts := options.NewSetOptions().SetOnlyIfExists()
		result, err := client.SetWithOptions(context.Background(), key, anotherValue, *opts)

		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_missingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		opts := options.NewSetOptions().SetOnlyIfDoesNotExist()
		result, err := client.SetWithOptions(context.Background(), key, anotherValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), anotherValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_existingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		opts := options.NewSetOptions().SetOnlyIfDoesNotExist()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))

		result, err := client.SetWithOptions(context.Background(), key, anotherValue, *opts)

		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())

		result, err = client.Get(context.Background(), key)

		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_KeepExistingExpiry() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		opts := options.NewSetOptions().
			SetExpiry(options.NewExpiryIn(2000 * time.Millisecond))
		result, err := client.SetWithOptions(context.Background(), key, initialValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		opts = options.NewSetOptions().SetExpiry(options.NewExpiryKeepExisting())
		result, err = client.SetWithOptions(context.Background(), key, anotherValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)

		suite.NoError(err)
		assert.Equal(suite.T(), anotherValue, result.Value())

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(context.Background(), key)

		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_UpdateExistingExpiry() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		opts := options.NewSetOptions().
			SetExpiry(options.NewExpiryIn(100500 * time.Millisecond))
		result, err := client.SetWithOptions(context.Background(), key, initialValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		opts = options.NewSetOptions().
			SetExpiry(options.NewExpiryIn(2000 * time.Millisecond))
		result, err = client.SetWithOptions(context.Background(), key, anotherValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), anotherValue, result.Value())

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(context.Background(), key)

		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfEquals() {
	suite.SkipIfServerVersionLowerThan("8.1.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))

		// successful set
		opts := options.NewSetOptions().SetOnlyIfEquals(initialValue)
		result, err := client.SetWithOptions(context.Background(), key, anotherValue, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), anotherValue, result.Value())

		// unsuccessful set
		opts = options.NewSetOptions().SetOnlyIfEquals(initialValue)
		result, err = client.SetWithOptions(context.Background(), key, initialValue, *opts)
		suite.NoError(err)
		assert.True(suite.T(), result.IsNil())
	})
}

func (suite *GlideTestSuite) TestGetEx_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))

		result, err := client.GetEx(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		key = uuid.New().String()
		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetExWithOptions_PersistKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))

		opts := options.NewGetExOptions().
			SetExpiry(options.NewExpiryIn(2000 * time.Millisecond))
		result, err := client.GetExWithOptions(context.Background(), key, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		time.Sleep(1000 * time.Millisecond)

		opts = options.NewGetExOptions().SetExpiry(options.NewExpiryPersist())
		result, err = client.GetExWithOptions(context.Background(), key, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())
	})
}

func (suite *GlideTestSuite) TestGetExWithOptions_UpdateExpiry() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))

		opts := options.NewGetExOptions().
			SetExpiry(options.NewExpiryIn(2000 * time.Millisecond))
		result, err := client.GetExWithOptions(context.Background(), key, *opts)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), initialValue, result.Value())

		time.Sleep(2222 * time.Millisecond)

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue_nonExistentKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		opts := options.NewSetOptions().SetReturnOldValue(true)

		result, err := client.SetWithOptions(context.Background(), key, anotherValue, *opts)

		suite.NoError(err)
		assert.Equal(suite.T(), "", result.Value())
	})
}

func (suite *GlideTestSuite) TestMSetAndMGet_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
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
		stringValue := models.CreateStringResult(value)
		nullResult := models.CreateNilStringResult()
		values := []models.Result[string]{stringValue, stringValue, nullResult}
		result, err := client.MGet(context.Background(), keys)

		suite.NoError(err)
		assert.Equal(suite.T(), values, result)
	})
}

func (suite *GlideTestSuite) TestMSetNXAndMGet_nonExistingKey_valuesSet() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()
		key3 := "{key}" + uuid.New().String()
		value := uuid.New().String()
		keyValueMap := map[string]string{
			key1: value,
			key2: value,
			key3: value,
		}
		res, err := client.MSetNX(context.Background(), keyValueMap)
		suite.NoError(err)
		assert.True(suite.T(), res)
		keys := []string{key1, key2, key3}
		stringValue := models.CreateStringResult(value)
		values := []models.Result[string]{stringValue, stringValue, stringValue}
		result, err := client.MGet(context.Background(), keys)

		suite.NoError(err)
		assert.Equal(suite.T(), values, result)
	})
}

func (suite *GlideTestSuite) TestMSetNXAndMGet_existingKey_valuesNotUpdated() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()
		key3 := "{key}" + uuid.New().String()
		oldValue := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key1, oldValue))
		keyValueMap := map[string]string{
			key1: value,
			key2: value,
			key3: value,
		}
		res, err := client.MSetNX(context.Background(), keyValueMap)
		suite.NoError(err)
		assert.False(suite.T(), res)
		keys := []string{key1, key2, key3}
		oldResult := models.CreateStringResult(oldValue)
		nullResult := models.CreateNilStringResult()
		values := []models.Result[string]{oldResult, nullResult, nullResult}
		result, err := client.MGet(context.Background(), keys)

		suite.NoError(err)
		assert.Equal(suite.T(), values, result)
	})
}

func (suite *GlideTestSuite) TestIncrCommands_existingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, "10"))

		res1, err := client.Incr(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(11), res1)

		res2, err := client.IncrBy(context.Background(), key, 10)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(21), res2)

		res3, err := client.IncrByFloat(context.Background(), key, float64(10.1))
		suite.NoError(err)
		assert.Equal(suite.T(), float64(31.1), res3)
	})
}

func (suite *GlideTestSuite) TestIncrCommands_nonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		res1, err := client.Incr(context.Background(), key1)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res1)

		key2 := uuid.New().String()
		res2, err := client.IncrBy(context.Background(), key2, 10)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(10), res2)

		key3 := uuid.New().String()
		res3, err := client.IncrByFloat(context.Background(), key3, float64(10.1))
		suite.NoError(err)
		assert.Equal(suite.T(), float64(10.1), res3)
	})
}

func (suite *GlideTestSuite) TestIncrCommands_TypeError() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, "stringValue"))

		res1, err := client.Incr(context.Background(), key)
		assert.Equal(suite.T(), int64(0), res1)
		suite.Error(err)

		res2, err := client.IncrBy(context.Background(), key, 10)
		assert.Equal(suite.T(), int64(0), res2)
		suite.Error(err)

		res3, err := client.IncrByFloat(context.Background(), key, float64(10.1))
		assert.Equal(suite.T(), float64(0), res3)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestDecrCommands_existingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, "10"))

		res1, err := client.Decr(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(9), res1)

		res2, err := client.DecrBy(context.Background(), key, 10)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-1), res2)
	})
}

func (suite *GlideTestSuite) TestDecrCommands_nonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		res1, err := client.Decr(context.Background(), key1)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-1), res1)

		key2 := uuid.New().String()
		res2, err := client.DecrBy(context.Background(), key2, 10)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-10), res2)
	})
}

func (suite *GlideTestSuite) TestStrlen_existingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		res, err := client.Strlen(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(len(value)), res)
	})
}

func (suite *GlideTestSuite) TestStrlen_nonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		res, err := client.Strlen(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)
	})
}

func (suite *GlideTestSuite) TestSetRange_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		res, err := client.SetRange(context.Background(), key, 0, "Dummy string")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(12), res)

		res, err = client.SetRange(context.Background(), key, 6, "values")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(12), res)
		res1, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "Dummy values", res1.Value())

		res, err = client.SetRange(context.Background(), key, 15, "test")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(19), res)
		res1, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "Dummy values\x00\x00\x00test", res1.Value())

		res, err = client.SetRange(context.Background(), key, math.MaxInt32, "test")
		assert.Equal(suite.T(), int64(0), res)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestSetRange_existingAndNonExistingKeys_binaryString() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		nonUtf8String := "Dummy \xFF string"
		key := uuid.New().String()
		res, err := client.SetRange(context.Background(), key, 0, nonUtf8String)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(14), res)

		res, err = client.SetRange(context.Background(), key, 6, "values ")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(14), res)
		res1, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "Dummy values g", res1.Value())

		res, err = client.SetRange(context.Background(), key, 15, "test")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(19), res)
		res1, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "Dummy values g\x00test", res1.Value())
	})
}

func (suite *GlideTestSuite) TestGetRange_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, "Dummy string"))

		res, err := client.GetRange(context.Background(), key, 0, 4)
		suite.NoError(err)
		assert.Equal(suite.T(), "Dummy", res)

		res, err = client.GetRange(context.Background(), key, -6, -1)
		suite.NoError(err)
		assert.Equal(suite.T(), "string", res)

		res, err = client.GetRange(context.Background(), key, -1, -6)
		suite.NoError(err)
		assert.Equal(suite.T(), "", res)

		res, err = client.GetRange(context.Background(), key, 15, 16)
		suite.NoError(err)
		assert.Equal(suite.T(), "", res)

		nonExistingKey := uuid.New().String()
		res, err = client.GetRange(context.Background(), nonExistingKey, 0, 5)
		suite.NoError(err)
		assert.Equal(suite.T(), "", res)
	})
}

func (suite *GlideTestSuite) TestGetRange_binaryString() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		nonUtf8String := "Dummy \xFF string"
		suite.verifyOK(client.Set(context.Background(), key, nonUtf8String))

		res, err := client.GetRange(context.Background(), key, 4, 6)
		suite.NoError(err)
		assert.Equal(suite.T(), "y \xFF", res)
	})
}

func (suite *GlideTestSuite) TestAppend_existingAndNonExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value1 := uuid.New().String()
		value2 := uuid.New().String()

		res, err := client.Append(context.Background(), key, value1)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(len(value1)), res)
		res1, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), value1, res1.Value())

		res, err = client.Append(context.Background(), key, value2)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(len(value1)+len(value2)), res)
		res1, err = client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), value1+value2, res1.Value())
	})
}

func (suite *GlideTestSuite) TestLCS_existingAndNonExistingKeys() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()

		res, err := client.LCS(context.Background(), key1, key2)
		suite.NoError(err)
		assert.Equal(suite.T(), "", res.MatchString)

		suite.verifyOK(client.Set(context.Background(), key1, "Dummy string"))
		suite.verifyOK(client.Set(context.Background(), key2, "Dummy value"))

		res, err = client.LCS(context.Background(), key1, key2)
		suite.NoError(err)
		assert.Equal(suite.T(), "Dummy ", res.MatchString)
	})
}

func (suite *GlideTestSuite) TestLCS_len_existingAndNonExistingKeys() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()

		res, err := client.LCSLen(context.Background(), key1, key2)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res.Len)

		suite.verifyOK(client.Set(context.Background(), key1, "ohmytext"))
		suite.verifyOK(client.Set(context.Background(), key2, "mynewtext"))

		res, err = client.LCSLen(context.Background(), key1, key2)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(6), res.Len)
	})
}

func (suite *GlideTestSuite) TestLCS_BasicIDXOption() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		_, err := client.Set(context.Background(), "{lcs}key1", "ohmytext")
		suite.NoError(err)

		_, err = client.Set(context.Background(), "{lcs}key2", "mynewtext")
		suite.NoError(err)

		opts := options.NewLCSIdxOptions()
		lcsIdxResult, err := client.LCSWithOptions(context.Background(), "{lcs}key1", "{lcs}key2", *opts)

		suite.NoError(err)
		assert.NotNil(suite.T(), lcsIdxResult)

		assert.Equal(suite.T(), int64(6), lcsIdxResult.Len)

		matches := lcsIdxResult.Matches
		assert.Len(suite.T(), matches, 2)

		expectedMatches := []models.LCSMatchedPosition{
			{
				Key1:     models.LCSPosition{Start: 4, End: 7},
				Key2:     models.LCSPosition{Start: 5, End: 8},
				MatchLen: 0,
			},
			{
				Key1:     models.LCSPosition{Start: 2, End: 3},
				Key2:     models.LCSPosition{Start: 0, End: 1},
				MatchLen: 0,
			},
		}
		assert.Equal(suite.T(), expectedMatches, matches)
	})
}

func (suite *GlideTestSuite) TestLCS_MinMatchLengthOption() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		_, err := client.Set(context.Background(), "{lcs}key1", "ohmytext")
		suite.NoError(err)

		_, err = client.Set(context.Background(), "{lcs}key2", "mynewtext")
		suite.NoError(err)

		opts := options.NewLCSIdxOptions()
		minMatchLen := int64(4)
		opts.SetMinMatchLen(minMatchLen)

		lcsIdxMinMatchResult, err := client.LCSWithOptions(context.Background(), "{lcs}key1", "{lcs}key2", *opts)

		suite.NoError(err)
		assert.NotNil(suite.T(), lcsIdxMinMatchResult)

		assert.Equal(suite.T(), int64(6), lcsIdxMinMatchResult.Len)

		matches := lcsIdxMinMatchResult.Matches
		assert.Len(suite.T(), matches, 1)

		expectedMatch := models.LCSMatchedPosition{
			Key1:     models.LCSPosition{Start: 4, End: 7},
			Key2:     models.LCSPosition{Start: 5, End: 8},
			MatchLen: 0,
		}
		assert.Equal(suite.T(), expectedMatch, matches[0])
	})
}

func (suite *GlideTestSuite) TestLCS_WithMatchLengthOption() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		_, err := client.Set(context.Background(), "{lcs}key1", "ohmytext")
		suite.NoError(err)

		_, err = client.Set(context.Background(), "{lcs}key2", "mynewtext")
		suite.NoError(err)

		opts := options.NewLCSIdxOptions()
		minMatchLen := int64(4)
		opts.SetMinMatchLen(minMatchLen)
		opts.SetWithMatchLen(true)

		lcsIdxFullOptionsResult, err := client.LCSWithOptions(context.Background(), "{lcs}key1", "{lcs}key2", *opts)

		suite.NoError(err)
		require.NotNil(suite.T(), lcsIdxFullOptionsResult)

		assert.Equal(suite.T(), int64(6), lcsIdxFullOptionsResult.Len)

		matches := lcsIdxFullOptionsResult.Matches
		assert.Len(suite.T(), matches, 1)

		expectedMatch := models.LCSMatchedPosition{
			Key1:     models.LCSPosition{Start: 4, End: 7},
			Key2:     models.LCSPosition{Start: 5, End: 8},
			MatchLen: 4,
		}
		assert.Equal(suite.T(), expectedMatch, matches[0])
	})
}

func (suite *GlideTestSuite) TestGetDel_ExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := "testValue"

		suite.verifyOK(client.Set(context.Background(), key, value))
		result, err := client.GetDel(context.Background(), key)
		suite.NoError(err)
		suite.Equal(value, result.Value())

		result, err = client.Get(context.Background(), key)
		suite.NoError(err)
		suite.Equal("", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetDel_NonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		result, err := client.GetDel(context.Background(), key)

		suite.NoError(err)
		suite.Equal("", result.Value())
	})
}

func (suite *GlideTestSuite) TestGetDel_EmptyKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		result, err := client.GetDel(context.Background(), "")

		suite.NoError(err)
		suite.Equal("", result.Value())
	})
}

func (suite *GlideTestSuite) TestHSet_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.New().String()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)
	})
}

func (suite *GlideTestSuite) TestHSet_byteString() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		field1 := string([]byte{0xFF, 0x00, 0xAA})
		value1 := string([]byte{0xDE, 0xAD, 0xBE, 0xEF})
		field2 := string([]byte{0x01, 0x02, 0x03, 0xFE})
		value2 := string([]byte{0xCA, 0xFE, 0xBA, 0xBE})

		fields := map[string]string{
			field1: value1,
			field2: value2,
		}
		key := string([]byte{0x01, 0x02, 0x03, 0xFE})

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HGetAll(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), fields, res2)
	})
}

func (suite *GlideTestSuite) TestHSet_WithAddNewField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.New().String()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)

		fields["field3"] = "value3"
		res3, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res3)
	})
}

func (suite *GlideTestSuite) TestHGet_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HGet(context.Background(), key, "field1")
		suite.NoError(err)
		assert.Equal(suite.T(), "value1", res2.Value())
	})
}

func (suite *GlideTestSuite) TestHGet_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res1, err := client.HGet(context.Background(), key, "field1")
		suite.NoError(err)
		assert.Equal(suite.T(), models.CreateNilStringResult(), res1)
	})
}

func (suite *GlideTestSuite) TestHGet_WithNotExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HGet(context.Background(), key, "foo")
		suite.NoError(err)
		assert.Equal(suite.T(), models.CreateNilStringResult(), res2)
	})
}

func (suite *GlideTestSuite) TestHGetAll_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HGetAll(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), fields, res2)
	})
}

func (suite *GlideTestSuite) TestHGetAll_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.HGetAll(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHMGet() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HMGet(context.Background(), key, []string{"field1", "field2", "field3"})
		value1 := models.CreateStringResult("value1")
		value2 := models.CreateStringResult("value2")
		nullValue := models.CreateNilStringResult()
		suite.NoError(err)
		assert.Equal(suite.T(), []models.Result[string]{value1, value2, nullValue}, res2)
	})
}

func (suite *GlideTestSuite) TestHMGet_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.HMGet(context.Background(), key, []string{"field1", "field2", "field3"})
		nullValue := models.CreateNilStringResult()
		suite.NoError(err)
		assert.Equal(suite.T(), []models.Result[string]{nullValue, nullValue, nullValue}, res)
	})
}

func (suite *GlideTestSuite) TestHMGet_WithNotExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HMGet(context.Background(), key, []string{"field3", "field4"})
		nullValue := models.CreateNilStringResult()
		suite.NoError(err)
		assert.Equal(suite.T(), []models.Result[string]{nullValue, nullValue}, res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HSetNX(context.Background(), key, "field1", "value1")
		suite.NoError(err)
		assert.False(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res1, err := client.HSetNX(context.Background(), key, "field1", "value1")
		suite.NoError(err)
		assert.True(suite.T(), res1)

		res2, err := client.HGetAll(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]string{"field1": "value1"}, res2)
	})
}

func (suite *GlideTestSuite) TestHSetNX_WithExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HSetNX(context.Background(), key, "field1", "value1")
		suite.NoError(err)
		assert.False(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestHDel() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HDel(context.Background(), key, []string{"field1", "field2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res2)

		res3, err := client.HGetAll(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), res3)

		res4, err := client.HDel(context.Background(), key, []string{"field1", "field2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res4)
	})
}

func (suite *GlideTestSuite) TestHDel_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		res, err := client.HDel(context.Background(), key, []string{"field1", "field2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)
	})
}

func (suite *GlideTestSuite) TestHDel_WithNotExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HDel(context.Background(), key, []string{"field3", "field4"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)
	})
}

func (suite *GlideTestSuite) TestHLen() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HLen(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res2)
	})
}

func (suite *GlideTestSuite) TestHLen_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		res, err := client.HLen(context.Background(), key)

		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)
	})
}

func (suite *GlideTestSuite) TestHVals_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HVals(context.Background(), key)
		suite.NoError(err)
		assert.ElementsMatch(suite.T(), []string{"value1", "value2"}, res2)
	})
}

func (suite *GlideTestSuite) TestHVals_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.HVals(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHExists_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HExists(context.Background(), key, "field1")
		suite.NoError(err)
		assert.True(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestHExists_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.HExists(context.Background(), key, "field1")
		suite.NoError(err)
		assert.False(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHExists_WithNotExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HExists(context.Background(), key, "field3")
		suite.NoError(err)
		assert.False(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestHKeys_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HKeys(context.Background(), key)
		suite.NoError(err)
		assert.ElementsMatch(suite.T(), []string{"field1", "field2"}, res2)
	})
}

func (suite *GlideTestSuite) TestHKeys_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.HKeys(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HStrLen(context.Background(), key, "field1")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(6), res2)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.HStrLen(context.Background(), key, "field1")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)
	})
}

func (suite *GlideTestSuite) TestHStrLen_WithNotExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		fields := map[string]string{"field1": "value1", "field2": "value2"}
		key := uuid.NewString()

		res1, err := client.HSet(context.Background(), key, fields)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.HStrLen(context.Background(), key, "field3")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)
	})
}

func (suite *GlideTestSuite) TestHIncrBy_WithExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		field := uuid.NewString()
		fieldValueMap := map[string]string{field: "10"}

		hsetResult, err := client.HSet(context.Background(), key, fieldValueMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), hsetResult)

		hincrByResult, hincrByErr := client.HIncrBy(context.Background(), key, field, 1)
		assert.Nil(suite.T(), hincrByErr)
		assert.Equal(suite.T(), int64(11), hincrByResult)
	})
}

func (suite *GlideTestSuite) TestHIncrBy_WithNonExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		field := uuid.NewString()
		field2 := uuid.NewString()
		fieldValueMap := map[string]string{field2: "1"}

		hsetResult, err := client.HSet(context.Background(), key, fieldValueMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), hsetResult)

		hincrByResult, hincrByErr := client.HIncrBy(context.Background(), key, field, 2)
		assert.Nil(suite.T(), hincrByErr)
		assert.Equal(suite.T(), int64(2), hincrByResult)
	})
}

func (suite *GlideTestSuite) TestHIncrByFloat_WithExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		field := uuid.NewString()
		fieldValueMap := map[string]string{field: "10"}

		hsetResult, err := client.HSet(context.Background(), key, fieldValueMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), hsetResult)

		hincrByFloatResult, hincrByFloatErr := client.HIncrByFloat(context.Background(), key, field, 1.5)
		assert.Nil(suite.T(), hincrByFloatErr)
		assert.Equal(suite.T(), float64(11.5), hincrByFloatResult)
	})
}

func (suite *GlideTestSuite) TestHIncrByFloat_WithNonExistingField() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		field := uuid.NewString()
		field2 := uuid.NewString()
		fieldValueMap := map[string]string{field2: "1"}

		hsetResult, err := client.HSet(context.Background(), key, fieldValueMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), hsetResult)

		hincrByFloatResult, hincrByFloatErr := client.HIncrByFloat(context.Background(), key, field, 1.5)
		suite.NoError(hincrByFloatErr)
		assert.Equal(suite.T(), float64(1.5), hincrByFloatResult)
	})
}

func (suite *GlideTestSuite) TestHScan() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		initialCursor := models.NewCursor()
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
		result, err := client.HScan(context.Background(), key1, initialCursor)
		suite.NoError(err)
		assert.Equal(t, initialCursor.String(), result.Cursor.String())
		assert.Empty(t, result.Data)

		// Negative cursor check.
		if suite.serverVersion >= "8.0.0" {
			_, err = client.HScan(context.Background(), key1, models.NewCursorFromString("-1"))
			assert.NotEmpty(t, err)
		} else {
			result, _ = client.HScan(context.Background(), key1, models.NewCursorFromString("-1"))
			assert.Equal(t, initialCursor.String(), result.Cursor.String())
			assert.Empty(t, result.Data)
		}

		// Result contains the whole set
		hsetResult, _ := client.HSet(context.Background(), key1, charMap)
		assert.Equal(t, int64(len(charMembers)), hsetResult)

		result, _ = client.HScan(context.Background(), key1, initialCursor)
		assert.Equal(t, initialCursor.String(), result.Cursor.String())
		// Length includes the score which is twice the map size
		assert.Equal(t, len(charMap)*2, len(result.Data))

		resultKeys := make([]string, 0)
		resultValues := make([]string, 0)

		for i := 0; i < len(result.Data); i += 2 {
			resultKeys = append(resultKeys, result.Data[i])
			resultValues = append(resultValues, result.Data[i+1])
		}
		keysList, valuesList := convertMapKeysAndValuesToLists(charMap)
		assert.True(t, isSubset(resultKeys, keysList) && isSubset(keysList, resultKeys))
		assert.True(t, isSubset(resultValues, valuesList) && isSubset(valuesList, resultValues))

		opts := options.NewHashScanOptions().SetMatch("a")
		result, _ = client.HScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.Equal(t, initialCursor.String(), result.Cursor.String())
		assert.Equal(t, len(result.Data), 2)
		assert.Equal(t, result.Data[0], "a")
		assert.Equal(t, result.Data[1], "0")

		// Result contains a subset of the key
		combinedMap := make(map[string]string)
		for key, value := range numberMap {
			combinedMap[key] = value
		}
		for key, value := range charMap {
			combinedMap[key] = value
		}

		hsetResult, _ = client.HSet(context.Background(), key1, combinedMap)
		assert.Equal(t, int64(len(numberMap)), hsetResult)
		secondResultAllKeys := make([]string, 0)
		secondResultAllValues := make([]string, 0)
		isFirstLoop := true
		cursor := models.NewCursor()
		for {
			result, _ = client.HScan(context.Background(), key1, cursor)
			cursor = result.Cursor
			for i := 0; i < len(result.Data); i += 2 {
				secondResultAllKeys = append(secondResultAllKeys, result.Data[i])
				secondResultAllValues = append(secondResultAllValues, result.Data[i+1])
			}
			if isFirstLoop {
				assert.NotEqual(t, models.NewCursor(), cursor)
				isFirstLoop = false
			} else if cursor.IsFinished() {
				break
			}

			// Scan with result cursor to get the next set of data.
			newResult, err := client.HScan(context.Background(), key1, cursor)
			assert.NoError(t, err)
			assert.NotEqual(t, cursor, newResult.Cursor)
			cursor = newResult.Cursor
			assert.NotEqual(t, newResult.Data, result.Data)
			for i := 0; i < len(newResult.Data); i += 2 {
				secondResultAllKeys = append(secondResultAllKeys, newResult.Data[i])
				secondResultAllValues = append(secondResultAllValues, newResult.Data[i+1])
			}

			if cursor.IsFinished() {
				break
			}
		}
		numberKeysList, numberValuesList := convertMapKeysAndValuesToLists(numberMap)
		assert.True(t, isSubset(numberKeysList, secondResultAllKeys))
		assert.True(t, isSubset(numberValuesList, secondResultAllValues))

		// Test match pattern
		opts = options.NewHashScanOptions().SetMatch("*")
		result, _ = client.HScanWithOptions(context.Background(), key1, initialCursor, *opts)
		resCursorInt, _ := strconv.Atoi(result.Cursor.String())
		assert.True(t, resCursorInt >= 0)
		assert.True(t, int(len(result.Data)) >= defaultCount)

		// Test count
		opts = options.NewHashScanOptions().SetCount(int64(20))
		result, _ = client.HScanWithOptions(context.Background(), key1, initialCursor, *opts)
		resCursorInt, _ = strconv.Atoi(result.Cursor.String())
		assert.True(t, resCursorInt >= 0)
		assert.True(t, len(result.Data) >= 20)

		// Test count with match returns a non-empty list
		opts = options.NewHashScanOptions().SetMatch("1*").SetCount(int64(20))
		result, _ = client.HScanWithOptions(context.Background(), key1, initialCursor, *opts)
		resCursorInt, _ = strconv.Atoi(result.Cursor.String())
		assert.True(t, resCursorInt >= 0)
		assert.True(t, len(result.Data) >= 0)

		if suite.serverVersion >= "8.0.0" {
			opts = options.NewHashScanOptions().SetNoValues(true)
			result, _ = client.HScanWithOptions(context.Background(), key1, initialCursor, *opts)
			resCursorInt, _ = strconv.Atoi(result.Cursor.String())
			assert.True(t, resCursorInt >= 0)

			// Check if all fields don't start with "num"
			containsElementsWithNumKeyword := false
			for i := 0; i < len(result.Data); i++ {
				if strings.Contains(result.Data[i], "num") {
					containsElementsWithNumKeyword = true
					break
				}
			}
			assert.False(t, containsElementsWithNumKeyword)
		}

		// Check if Non-hash key throws an error.
		suite.verifyOK(client.Set(context.Background(), key2, "test"))
		_, err = client.HScan(context.Background(), key2, initialCursor)
		assert.NotEmpty(t, err)

		// Check if Non-hash key throws an error when HSCAN called with options.
		opts = options.NewHashScanOptions().SetMatch("test").SetCount(int64(1))
		_, err = client.HScanWithOptions(context.Background(), key2, initialCursor, *opts)
		assert.NotEmpty(t, err)

		// Check if a negative cursor value throws an error.
		opts = options.NewHashScanOptions().SetCount(int64(-1))
		_, err = client.HScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NotEmpty(t, err)
	})
}

func (suite *GlideTestSuite) TestHRandField() {
	suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		// key does not exist
		res, err := client.HRandField(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), res.IsNil())
		resc, err := client.HRandFieldWithCount(context.Background(), key, 5)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), resc)
		rescv, err := client.HRandFieldWithCountWithValues(context.Background(), key, 5)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), rescv)

		data := map[string]string{"f1": "v1", "f2": "v2", "f3": "v3"}
		hset, err := client.HSet(context.Background(), key, data)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), hset)

		fields := make([]string, 0, len(data))
		for k := range data {
			fields = append(fields, k)
		}
		res, err = client.HRandField(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), fields, res.Value())

		// With Count - positive count
		resc, err = client.HRandFieldWithCount(context.Background(), key, 5)
		assert.NoError(suite.T(), err)
		assert.ElementsMatch(suite.T(), fields, resc)

		// With Count - negative count
		resc, err = client.HRandFieldWithCount(context.Background(), key, -5)
		assert.NoError(suite.T(), err)
		assert.Len(suite.T(), resc, 5)
		for _, field := range resc {
			assert.Contains(suite.T(), fields, field)
		}

		// With values - positive count
		rescv, err = client.HRandFieldWithCountWithValues(context.Background(), key, 5)
		assert.NoError(suite.T(), err)
		resvMap := make(map[string]string)
		for _, pair := range rescv {
			resvMap[pair[0]] = pair[1]
		}
		assert.Equal(suite.T(), data, resvMap)

		// With values - negative count
		rescv, err = client.HRandFieldWithCountWithValues(context.Background(), key, -5)
		assert.NoError(suite.T(), err)
		assert.Len(suite.T(), resc, 5)
		for _, pair := range rescv {
			assert.Contains(suite.T(), fields, pair[0])
		}

		// key exists but holds non hash type value
		key = uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, "HRandField"))
		_, err = client.HRandField(context.Background(), key)
		suite.Error(err)
		_, err = client.HRandFieldWithCount(context.Background(), key, 42)
		suite.Error(err)
		_, err = client.HRandFieldWithCountWithValues(context.Background(), key, 42)
		suite.Error(err)
	})
}

// Hash field expiration tests (Valkey 9.0+)

func (suite *GlideTestSuite) TestHSetEx_WithExpiration() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// Set fields with 10 second expiration
		hsetOptions := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(10 * time.Second))
		result, err := client.HSetEx(context.Background(), key, fields, hsetOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result)

		// Verify fields were set
		values, err := client.HMGet(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value1", values[0].Value())
		assert.Equal(suite.T(), "value2", values[1].Value())

		// Verify TTL was set
		ttls, err := client.HTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 0 && ttls[0] <= 10)
		assert.True(suite.T(), ttls[1] > 0 && ttls[1] <= 10)

		// Test with non-empty options (ExpiryAt)
		key2 := uuid.NewString()
		futureTime := time.Now().Add(30 * time.Second)
		hsetOptionsAt := options.NewHSetExOptions().SetExpiry(options.NewExpiryAt(futureTime))
		result2, err := client.HSetEx(context.Background(), key2, fields, hsetOptionsAt)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result2)

		// Verify expiration was set with ExpiryAt
		ttls2, err := client.HTtl(context.Background(), key2, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls2[0] > 0 && ttls2[0] <= 30)
		assert.True(suite.T(), ttls2[1] > 0 && ttls2[1] <= 30)
	})
}

func (suite *GlideTestSuite) TestHGetEx_WithExpiration() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Get fields and set 5 second expiration
		hgetOptions := options.NewHGetExOptions().SetExpiry(options.NewExpiryIn(5 * time.Second))
		values, err := client.HGetEx(context.Background(), key, []string{"field1", "field2"}, hgetOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value1", values[0].Value())
		assert.Equal(suite.T(), "value2", values[1].Value())

		// Verify TTL was set
		ttls, err := client.HTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 0 && ttls[0] <= 5)
		assert.True(suite.T(), ttls[1] > 0 && ttls[1] <= 5)

		// Test with non-empty options (ExpiryAt)
		key2 := uuid.NewString()
		result2, err := client.HSet(context.Background(), key2, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result2)

		futureTime := time.Now().Add(25 * time.Second)
		hgetOptionsAt := options.NewHGetExOptions().SetExpiry(options.NewExpiryAt(futureTime))
		values2, err := client.HGetEx(context.Background(), key2, []string{"field1", "field2"}, hgetOptionsAt)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value1", values2[0].Value())
		assert.Equal(suite.T(), "value2", values2[1].Value())

		// Verify expiration was set with ExpiryAt
		ttls2, err := client.HTtl(context.Background(), key2, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls2[0] > 0 && ttls2[0] <= 25)
		assert.True(suite.T(), ttls2[1] > 0 && ttls2[1] <= 25)
	})
}

func (suite *GlideTestSuite) TestHExpire_WithFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Set 30 second expiration on fields
		expireResult, err := client.HExpire(
			context.Background(),
			key,
			30*time.Second,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult[1]) // 1 means expiration was set

		// Test with NX condition (only set if field has no expiry) - should return 0 since fields already have expiry
		nxOptions := options.NewHExpireOptions().SetExpireCondition(constants.HasNoExpiry)
		expireResultNX, err := client.HExpire(context.Background(), key, 60*time.Second, []string{"field1"}, nxOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), expireResultNX[0]) // 0 means expiration was not set (field already has expiry)

		// Test with XX condition (only set if field has existing expiry) - should return 1 since field has expiry
		xxOptions := options.NewHExpireOptions().SetExpireCondition(constants.HasExistingExpiry)
		expireResultXX, err := client.HExpire(context.Background(), key, 45*time.Second, []string{"field2"}, xxOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResultXX[0]) // 1 means expiration was set (field had existing expiry)

		// Verify TTL was set
		ttls, err := client.HTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 0 && ttls[0] <= 30)
		assert.True(suite.T(), ttls[1] > 0 && ttls[1] <= 45)
	})
}

func (suite *GlideTestSuite) TestHExpireAt_WithFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Set expiration using Unix timestamp (60 seconds from now)
		futureTime := time.Now().Add(60 * time.Second)
		expireResult, err := client.HExpireAt(
			context.Background(),
			key,
			futureTime,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult[1]) // 1 means expiration was set

		// Test with GT condition (only set if new expiry is greater than current)
		// Try to set a shorter expiry (30 seconds) - should return 0 since it's less than current
		gtOptions := options.NewHExpireOptions().SetExpireCondition(constants.NewExpiryGreaterThanCurrent)
		shorterTime := time.Now().Add(30 * time.Second)
		expireResultGT, err := client.HExpireAt(context.Background(), key, shorterTime, []string{"field1"}, gtOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), expireResultGT[0]) // 0 means expiration was not set (new expiry not greater)

		// Test with LT condition (only set if new expiry is less than current)
		// Try to set a shorter expiry (30 seconds) - should return 1 since it's less than current
		ltOptions := options.NewHExpireOptions().SetExpireCondition(constants.NewExpiryLessThanCurrent)
		expireResultLT, err := client.HExpireAt(context.Background(), key, shorterTime, []string{"field2"}, ltOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResultLT[0]) // 1 means expiration was set (new expiry is less)

		ttls, err := client.HTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 50 && ttls[0] <= 60)
		assert.True(suite.T(), ttls[1] > 20 && ttls[1] <= 30)
	})
}

func (suite *GlideTestSuite) TestHPExpire_WithFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Set 30 second expiration on fields
		expireResult, err := client.HPExpire(
			context.Background(),
			key,
			30*time.Second,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult[1]) // 1 means expiration was set

		// Test with XX condition (only set if field has existing expiry) - should return 1 since field has expiry
		xxOptions := options.NewHExpireOptions().SetExpireCondition(constants.HasExistingExpiry)
		expireResultXX, err := client.HPExpire(context.Background(), key, 45*time.Second, []string{"field1"}, xxOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResultXX[0]) // 1 means expiration was set (field had existing expiry)

		// Test with GT condition (only set if new expiry is greater than current)
		gtOptions := options.NewHExpireOptions().SetExpireCondition(constants.NewExpiryGreaterThanCurrent)
		expireResultGT, err := client.HPExpire(context.Background(), key, 60*time.Second, []string{"field2"}, gtOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResultGT[0]) // 1 means expiration was set (new expiry is greater)

		// Verify TTL was set (in milliseconds)
		ttls, err := client.HPTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 35000 && ttls[0] <= 45000)
		assert.True(suite.T(), ttls[1] > 50000 && ttls[1] <= 60000)
	})
}

func (suite *GlideTestSuite) TestHPExpireAt_WithFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Set expiration using Unix timestamp (60 seconds from now)
		futureTime := time.Now().Add(60 * time.Second)
		expireResult, err := client.HPExpireAt(
			context.Background(),
			key,
			futureTime,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult[1]) // 1 means expiration was set

		// Test with NX condition (only set if field has no expiry) - should return 0 since fields already have expiry
		nxOptions := options.NewHExpireOptions().SetExpireCondition(constants.HasNoExpiry)
		longerTime := time.Now().Add(120 * time.Second)
		expireResultNX, err := client.HPExpireAt(context.Background(), key, longerTime, []string{"field1"}, nxOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), expireResultNX[0]) // 0 means expiration was not set (field already has expiry)

		// Verify TTL was set (should be around 60000 milliseconds)
		ttls, err := client.HPTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 50000 && ttls[0] <= 60000)
		assert.True(suite.T(), ttls[1] > 50000 && ttls[1] <= 60000)
	})
}

func (suite *GlideTestSuite) TestHPersist_RemoveExpiration() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// Set fields with expiration
		hsetOptions := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(60 * time.Second))
		result, err := client.HSetEx(context.Background(), key, fields, hsetOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result)

		// Verify expiration is set
		ttls, err := client.HTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 0)
		assert.True(suite.T(), ttls[1] > 0)

		// Remove expiration from fields
		persistResult, err := client.HPersist(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), persistResult[0]) // 1 means expiration was removed
		assert.Equal(suite.T(), int64(1), persistResult[1]) // 1 means expiration was removed

		// Verify expiration is removed
		ttls, err = client.HTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), ttls[0]) // -1 means field exists but has no expiration
		assert.Equal(suite.T(), int64(-1), ttls[1]) // -1 means field exists but has no expiration
	})
}

func (suite *GlideTestSuite) TestHTtl_WithExpiringFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2", "field3": "value3"}

		// Set fields with expiration
		hsetOptions := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(60 * time.Second))
		result, err := client.HSetEx(context.Background(), key, fields, hsetOptions)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result)

		// Get TTL for fields
		ttls, err := client.HTtl(context.Background(), key, []string{"field1", "field2", "field3"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 0 && ttls[0] <= 60)
		assert.True(suite.T(), ttls[1] > 0 && ttls[1] <= 60)
		assert.True(suite.T(), ttls[2] > 0 && ttls[2] <= 60)

		// Test non-existent field
		ttls, err = client.HTtl(context.Background(), key, []string{"nonexistent"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), ttls[0]) // -2 means field doesn't exist

		// Test field without expiration
		result2, err := client.HSet(context.Background(), key, map[string]string{"field4": "value4"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result2)
		ttls, err = client.HTtl(context.Background(), key, []string{"field4"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), ttls[0]) // -1 means field exists but has no expiration
	})
}

func (suite *GlideTestSuite) TestHPTtl_WithExpiringFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// Set fields with expiration in milliseconds
		expireResult, err := client.HPExpire(
			context.Background(),
			key,
			60*time.Second,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), expireResult[0]) // -2 means field doesn't exist, so set it first
		assert.Equal(suite.T(), int64(-2), expireResult[1])

		// First set the fields
		result2, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result2)

		// Now set expiration
		expireResult2, err := client.HPExpire(
			context.Background(),
			key,
			60*time.Second,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult2[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult2[1])

		// Get TTL for fields in milliseconds
		ttls, err := client.HPTtl(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), ttls[0] > 50000 && ttls[0] <= 60000)
		assert.True(suite.T(), ttls[1] > 50000 && ttls[1] <= 60000)

		// Test non-existent field
		ttls, err = client.HPTtl(context.Background(), key, []string{"nonexistent"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), ttls[0]) // -2 means field doesn't exist
	})
}

func (suite *GlideTestSuite) TestHExpireTime_WithExpiringFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Set expiration using Unix timestamp (60 seconds from now)
		futureTime := time.Now().Add(60 * time.Second)
		expireResult, err := client.HExpireAt(
			context.Background(),
			key,
			futureTime,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult[1])

		// Get expiration time for fields
		expireTimes, err := client.HExpireTime(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		futureTimestamp := futureTime.Unix()
		assert.True(suite.T(), expireTimes[0] >= futureTimestamp-10 && expireTimes[0] <= futureTimestamp+10)
		assert.True(suite.T(), expireTimes[1] >= futureTimestamp-10 && expireTimes[1] <= futureTimestamp+10)

		// Test non-existent field
		expireTimes, err = client.HExpireTime(context.Background(), key, []string{"nonexistent"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), expireTimes[0]) // -2 means field doesn't exist

		// Test field without expiration
		result3, err := client.HSet(context.Background(), key, map[string]string{"field3": "value3"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result3)
		expireTimes, err = client.HExpireTime(context.Background(), key, []string{"field3"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), expireTimes[0]) // -1 means field exists but has no expiration
	})
}

func (suite *GlideTestSuite) TestHPExpireTime_WithExpiringFields() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		fields := map[string]string{"field1": "value1", "field2": "value2"}

		// First set some fields
		result, err := client.HSet(context.Background(), key, fields)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), result)

		// Set expiration using Unix timestamp (60 seconds from now)
		futureTime := time.Now().Add(60 * time.Second)
		expireResult, err := client.HPExpireAt(
			context.Background(),
			key,
			futureTime,
			[]string{"field1", "field2"},
			options.HExpireOptions{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expireResult[0]) // 1 means expiration was set
		assert.Equal(suite.T(), int64(1), expireResult[1])

		// Get expiration time for fields in milliseconds
		expireTimes, err := client.HPExpireTime(context.Background(), key, []string{"field1", "field2"})
		assert.NoError(suite.T(), err)
		futureTimestampMs := futureTime.UnixMilli()
		assert.True(suite.T(), expireTimes[0] >= futureTimestampMs-10000 && expireTimes[0] <= futureTimestampMs+10000)
		assert.True(suite.T(), expireTimes[1] >= futureTimestampMs-10000 && expireTimes[1] <= futureTimestampMs+10000)

		// Test non-existent field
		expireTimes, err = client.HPExpireTime(context.Background(), key, []string{"nonexistent"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-2), expireTimes[0]) // -2 means field doesn't exist

		// Test field without expiration
		result2, err := client.HSet(context.Background(), key, map[string]string{"field3": "value3"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), result2)
		expireTimes, err = client.HPExpireTime(context.Background(), key, []string{"field3"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(-1), expireTimes[0]) // -1 means field exists but has no expiration
	})
}

func (suite *GlideTestSuite) TestLPushLPop_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.LPop(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "value1", res2.Value())

		res3, err := client.LPopCount(context.Background(), key, 2)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value2", "value3"}, res3)
	})
}

func (suite *GlideTestSuite) TestLPop_nonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res1, err := client.LPop(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), models.CreateNilStringResult(), res1)

		res2, err := client.LPopCount(context.Background(), key, 2)
		suite.NoError(err)
		assert.Nil(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestLPushLPop_typeError() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, "value"))

		res1, err := client.LPush(context.Background(), key, []string{"value1"})
		suite.Equal(int64(0), res1)
		suite.Error(err)

		res2, err := client.LPopCount(context.Background(), key, 2)
		suite.Error(err)
		suite.Nil(res2)
	})
}

func (suite *GlideTestSuite) TestLPos_withAndWithoutOptions() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		res1, err := client.RPush(context.Background(), key, []string{"a", "a", "b", "c", "a", "b"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(6), res1)

		res2, err := client.LPos(context.Background(), key, "a")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2.Value())

		res3, err := client.LPosWithOptions(context.Background(), key, "b", *options.NewLPosOptions().SetRank(2))
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res3.Value())

		// element doesn't exist
		res4, err := client.LPos(context.Background(), key, "e")
		suite.NoError(err)
		assert.Equal(suite.T(), models.CreateNilInt64Result(), res4)

		// reverse traversal
		res5, err := client.LPosWithOptions(context.Background(), key, "b", *options.NewLPosOptions().SetRank(-2))
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res5.Value())

		// unlimited comparisons
		res6, err := client.LPosWithOptions(context.Background(),
			key,
			"a",
			*options.NewLPosOptions().SetRank(1).SetMaxLen(0),
		)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res6.Value())

		// limited comparisons
		res7, err := client.LPosWithOptions(context.Background(),
			key,
			"c",
			*options.NewLPosOptions().SetRank(1).SetMaxLen(2),
		)
		suite.NoError(err)
		assert.Equal(suite.T(), models.CreateNilInt64Result(), res7)

		// invalid rank value
		res8, err := client.LPosWithOptions(context.Background(), key, "a", *options.NewLPosOptions().SetRank(0))
		assert.Equal(suite.T(), models.CreateNilInt64Result(), res8)
		suite.Error(err)

		// invalid maxlen value
		res9, err := client.LPosWithOptions(context.Background(), key, "a", *options.NewLPosOptions().SetMaxLen(-1))
		assert.Equal(suite.T(), models.CreateNilInt64Result(), res9)
		suite.Error(err)

		// non-existent key
		res10, err := client.LPos(context.Background(), "non_existent_key", "a")
		assert.Equal(suite.T(), models.CreateNilInt64Result(), res10)
		suite.NoError(err)

		// wrong key data type
		keyString := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), keyString, "value"))
		res11, err := client.LPos(context.Background(), keyString, "a")
		assert.Equal(suite.T(), models.CreateNilInt64Result(), res11)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestLPosCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res1, err := client.RPush(context.Background(), key, []string{"a", "a", "b", "c", "a", "b"})
		assert.Equal(suite.T(), int64(6), res1)
		suite.NoError(err)

		res2, err := client.LPosCount(context.Background(), key, "a", int64(2))
		assert.Equal(suite.T(), []int64{0, 1}, res2)
		suite.NoError(err)

		res3, err := client.LPosCount(context.Background(), key, "a", int64(0))
		assert.Equal(suite.T(), []int64{0, 1, 4}, res3)
		suite.NoError(err)

		// invalid count value
		res4, err := client.LPosCount(context.Background(), key, "a", int64(-1))
		suite.Nil(res4)
		suite.Error(err)

		// non-existent key
		res5, err := client.LPosCount(context.Background(), "non_existent_key", "a", int64(1))
		suite.Empty(res5)
		suite.NoError(err)

		// wrong key data type
		keyString := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), keyString, "value"))
		res6, err := client.LPosCount(context.Background(), keyString, "a", int64(1))
		assert.Nil(suite.T(), res6)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestLPosCount_withOptions() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res1, err := client.RPush(context.Background(), key, []string{"a", "a", "b", "c", "a", "b"})
		assert.Equal(suite.T(), int64(6), res1)
		suite.NoError(err)

		res2, err := client.LPosCountWithOptions(
			context.Background(),
			key,
			"a",
			int64(0),
			*options.NewLPosOptions().SetRank(1),
		)
		assert.Equal(suite.T(), []int64{0, 1, 4}, res2)
		suite.NoError(err)

		res3, err := client.LPosCountWithOptions(
			context.Background(),
			key,
			"a",
			int64(0),
			*options.NewLPosOptions().SetRank(2),
		)
		assert.Equal(suite.T(), []int64{1, 4}, res3)
		suite.NoError(err)

		// reverse traversal
		res4, err := client.LPosCountWithOptions(
			context.Background(),
			key,
			"a",
			int64(0),
			*options.NewLPosOptions().SetRank(-1),
		)
		assert.Equal(suite.T(), []int64{4, 1, 0}, res4)
		suite.NoError(err)
	})
}

func (suite *GlideTestSuite) TestRPush() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value1", "value2", "value3", "value4"}
		key := uuid.NewString()

		res1, err := client.RPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res2, err := client.RPush(context.Background(), key2, []string{"value1"})
		suite.Equal(int64(0), res2)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestSAdd() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2"}

		res, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res)
	})
}

func (suite *GlideTestSuite) TestSAdd_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)
	})
}

func (suite *GlideTestSuite) TestSRem() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SRem(context.Background(), key, []string{"member1", "member2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res2)
	})
}

func (suite *GlideTestSuite) TestSRem_WithExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.SRem(context.Background(), key, []string{"member3", "member4"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)
	})
}

func (suite *GlideTestSuite) TestSRem_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res2, err := client.SRem(context.Background(), key, []string{"member1", "member2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res2)
	})
}

func (suite *GlideTestSuite) TestSRem_WithExistingKeyAndDifferentMembers() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SRem(context.Background(), key, []string{"member1", "member3", "member4"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res2)
	})
}

func (suite *GlideTestSuite) TestSUnionStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		key4 := "{key}-4-" + uuid.NewString()
		stringKey := "{key}-5-" + uuid.NewString()
		nonExistingKey := "{key}-6-" + uuid.NewString()

		memberArray1 := []string{"a", "b", "c"}
		memberArray2 := []string{"c", "d", "e"}
		memberArray3 := []string{"e", "f", "g"}
		expected1 := map[string]struct{}{
			"a": {},
			"b": {},
			"c": {},
			"d": {},
			"e": {},
		}
		expected2 := map[string]struct{}{
			"a": {},
			"b": {},
			"c": {},
			"d": {},
			"e": {},
			"f": {},
			"g": {},
		}
		t := suite.T()

		res1, err := client.SAdd(context.Background(), key1, memberArray1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res1)

		res2, err := client.SAdd(context.Background(), key2, memberArray2)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res2)

		res3, err := client.SAdd(context.Background(), key3, memberArray3)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res3)

		// store union in new key
		res4, err := client.SUnionStore(context.Background(), key4, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(5), res4)

		res5, err := client.SMembers(context.Background(), key4)
		assert.NoError(t, err)
		assert.Len(t, res5, 5)
		assert.True(t, reflect.DeepEqual(res5, expected1))

		// overwrite existing set
		res6, err := client.SUnionStore(context.Background(), key1, []string{key4, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(5), res6)

		res7, err := client.SMembers(context.Background(), key1)
		assert.NoError(t, err)
		assert.Len(t, res7, 5)
		assert.True(t, reflect.DeepEqual(res7, expected1))

		// overwrite one of the source keys
		res8, err := client.SUnionStore(context.Background(), key2, []string{key4, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(5), res8)

		res9, err := client.SMembers(context.Background(), key2)
		assert.NoError(t, err)
		assert.Len(t, res9, 5)
		assert.True(t, reflect.DeepEqual(res9, expected1))

		// union with non-existing key
		res10, err := client.SUnionStore(context.Background(), key2, []string{nonExistingKey})
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res10)

		// check that the key is now empty
		members1, err := client.SMembers(context.Background(), key2)
		assert.NoError(t, err)
		assert.Empty(t, members1)

		// invalid argument - key list must not be empty
		res11, err := client.SUnionStore(context.Background(), key4, []string{})
		assert.Equal(suite.T(), int64(0), res11)
		suite.Error(err)

		// non-set key
		_, err = client.Set(context.Background(), stringKey, "value")
		suite.NoError(err)

		res12, err := client.SUnionStore(context.Background(), key4, []string{stringKey, key1})
		suite.Equal(int64(0), res12)
		suite.Error(err)

		// overwrite destination when destination is not a set
		res13, err := client.SUnionStore(context.Background(), stringKey, []string{key1, key3})
		suite.NoError(err)
		suite.Equal(int64(7), res13)

		// check that the key is now empty
		res14, err := client.SMembers(context.Background(), stringKey)
		suite.NoError(err)
		suite.Len(res14, 7)
		suite.True(reflect.DeepEqual(res14, expected2))
	})
}

func (suite *GlideTestSuite) TestSMembers() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		suite.Equal(int64(3), res1)

		res2, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		suite.Len(res2, 3)
	})
}

func (suite *GlideTestSuite) TestSMembers_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestSCard() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SCard(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res2)
	})
}

func (suite *GlideTestSuite) TestSCard_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.SCard(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)
	})
}

func (suite *GlideTestSuite) TestSIsMember() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SIsMember(context.Background(), key, "member2")
		suite.NoError(err)
		assert.True(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestSIsMember_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.SIsMember(context.Background(), key, "member2")
		suite.NoError(err)
		assert.False(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestSIsMember_WithNotExistingMember() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"member1", "member2", "member3"}

		res1, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SIsMember(context.Background(), key, "nonExistingMember")
		suite.NoError(err)
		assert.False(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestSDiff() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(context.Background(), key1, []string{"a", "b", "c", "d"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.SAdd(context.Background(), key2, []string{"c", "d", "e"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res2)

		result, err := client.SDiff(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]struct{}{"a": {}, "b": {}}, result)
	})
}

func (suite *GlideTestSuite) TestSDiff_WithNotExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		result, err := client.SDiff(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Empty(suite.T(), result)
	})
}

func (suite *GlideTestSuite) TestSDiff_WithSingleKeyExist() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(context.Background(), key1, []string{"a", "b", "c"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SDiff(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]struct{}{"a": {}, "b": {}, "c": {}}, res2)
	})
}

func (suite *GlideTestSuite) TestSDiffStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()

		res1, err := client.SAdd(context.Background(), key1, []string{"a", "b", "c"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res1)

		res2, err := client.SAdd(context.Background(), key2, []string{"c", "d", "e"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res2)

		res3, err := client.SDiffStore(context.Background(), key3, []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res3)

		members, err := client.SMembers(context.Background(), key3)
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]struct{}{"a": {}, "b": {}}, members)
	})
}

func (suite *GlideTestSuite) TestSDiffStore_WithNotExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()

		res, err := client.SDiffStore(context.Background(), key3, []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)

		members, err := client.SMembers(context.Background(), key3)
		suite.NoError(err)
		assert.Empty(suite.T(), members)
	})
}

func (suite *GlideTestSuite) TestSinter() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(context.Background(), key1, []string{"a", "b", "c", "d"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.SAdd(context.Background(), key2, []string{"c", "d", "e"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res2)

		members, err := client.SInter(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]struct{}{"c": {}, "d": {}}, members)
	})
}

func (suite *GlideTestSuite) TestSinter_WithNotExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		members, err := client.SInter(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Empty(suite.T(), members)
	})
}

func (suite *GlideTestSuite) TestSinterStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		stringKey := "{key}-4-" + uuid.NewString()
		nonExistingKey := "{key}-5-" + uuid.NewString()
		memberArray1 := []string{"a", "b", "c"}
		memberArray2 := []string{"c", "d", "e"}
		t := suite.T()

		res1, err := client.SAdd(context.Background(), key1, memberArray1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res1)

		res2, err := client.SAdd(context.Background(), key2, memberArray2)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res2)

		// store in new key
		res3, err := client.SInterStore(context.Background(), key3, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res3)

		res4, err := client.SMembers(context.Background(), key3)
		assert.NoError(t, err)
		assert.Equal(t, map[string]struct{}{"c": {}}, res4)

		// overwrite existing set, which is also a source set
		res5, err := client.SInterStore(context.Background(), key2, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res5)

		res6, err := client.SMembers(context.Background(), key2)
		assert.NoError(t, err)
		assert.Equal(t, map[string]struct{}{"c": {}}, res6)

		// source set is the same as the existing set
		res7, err := client.SInterStore(context.Background(), key1, []string{key2})
		assert.NoError(t, err)
		assert.Equal(t, int64(1), res7)

		res8, err := client.SMembers(context.Background(), key2)
		assert.NoError(t, err)
		assert.Equal(t, map[string]struct{}{"c": {}}, res8)

		// intersection with non-existing key
		res9, err := client.SInterStore(context.Background(), key1, []string{key2, nonExistingKey})
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res9)

		// check that the key is now empty
		members1, err := client.SMembers(context.Background(), key1)
		assert.NoError(t, err)
		assert.Empty(t, members1)

		// invalid argument - key list must not be empty
		res10, err := client.SInterStore(context.Background(), key3, []string{})
		suite.Equal(int64(0), res10)
		suite.Error(err)

		// non-set key
		_, err = client.Set(context.Background(), stringKey, "value")
		suite.NoError(err)

		res11, err := client.SInterStore(context.Background(), key3, []string{stringKey})
		suite.Equal(int64(0), res11)
		suite.Error(err)

		// overwrite the non-set key
		res12, err := client.SInterStore(context.Background(), stringKey, []string{key2})
		suite.NoError(err)
		suite.Equal(int64(1), res12)

		// check that the key is now empty
		res13, err := client.SMembers(context.Background(), stringKey)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"c": {}}, res13)
	})
}

func (suite *GlideTestSuite) TestSInterCard() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(context.Background(), key1, []string{"one", "two", "three", "four"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		result1, err := client.SInterCard(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), result1)

		res2, err := client.SAdd(context.Background(), key2, []string{"two", "three", "four", "five"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		result2, err := client.SInterCard(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), result2)
	})
}

func (suite *GlideTestSuite) TestSInterCardLimit() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()

		res1, err := client.SAdd(context.Background(), key1, []string{"one", "two", "three", "four"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.SAdd(context.Background(), key2, []string{"two", "three", "four", "five"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		result1, err := client.SInterCardLimit(context.Background(), []string{key1, key2}, 2)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), result1)

		result2, err := client.SInterCardLimit(context.Background(), []string{key1, key2}, 4)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), result2)
	})
}

func (suite *GlideTestSuite) TestSRandMember() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res, err := client.SAdd(context.Background(), key, []string{"one"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res)

		member, err := client.SRandMember(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "one", member.Value())
		assert.False(suite.T(), member.IsNil())
	})
}

func (suite *GlideTestSuite) TestSRandMemberCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		nonExistingKey := uuid.NewString()
		stringKey := uuid.NewString()
		members := []string{"one", "two", "three", "four", "five"}

		// Test with empty set
		emptyResult, err := client.SRandMemberCount(context.Background(), nonExistingKey, 2)
		suite.NoError(err)
		assert.Empty(suite.T(), emptyResult)

		// Add members to the set
		res, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res)

		// Test with positive count (unique elements)
		positiveResult, err := client.SRandMemberCount(context.Background(), key, 3)
		suite.NoError(err)
		assert.Equal(suite.T(), 3, len(positiveResult))
		// Verify all returned elements are unique and from the original set
		uniqueElements := make(map[string]struct{})
		for _, element := range positiveResult {
			uniqueElements[element] = struct{}{}
			assert.Contains(suite.T(), members, element)
		}
		assert.Equal(suite.T(), len(positiveResult), len(uniqueElements), "Elements should be unique")

		// Test with count larger than set size (should return all elements)
		largeCountResult, err := client.SRandMemberCount(context.Background(), key, 10)
		suite.NoError(err)
		assert.Equal(suite.T(), 5, len(largeCountResult))
		// Verify all elements are returned
		allElements := make(map[string]struct{})
		for _, element := range largeCountResult {
			allElements[element] = struct{}{}
		}
		assert.Equal(suite.T(), 5, len(allElements))

		// Test with negative count (allows duplicates)
		negativeResult, err := client.SRandMemberCount(context.Background(), key, -7)
		suite.NoError(err)
		assert.Equal(suite.T(), 7, len(negativeResult))
		// Verify all elements are from the original set (may contain duplicates)
		for _, element := range negativeResult {
			assert.Contains(suite.T(), members, element)
		}

		// Test with zero count (should return empty array)
		zeroResult, err := client.SRandMemberCount(context.Background(), key, 0)
		suite.NoError(err)
		assert.Empty(suite.T(), zeroResult)

		// Test with non-set key
		suite.verifyOK(client.Set(context.Background(), stringKey, "value"))
		_, err = client.SRandMemberCount(context.Background(), stringKey, 1)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestSPop() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"value1", "value2", "value3"}

		res, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		popMember, err := client.SPop(context.Background(), key)
		suite.NoError(err)
		assert.Contains(suite.T(), members, popMember.Value())
		assert.False(suite.T(), popMember.IsNil())

		remainingMembers, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		assert.Len(suite.T(), remainingMembers, 2)
		assert.NotContains(suite.T(), remainingMembers, popMember)
	})
}

func (suite *GlideTestSuite) TestSPop_LastMember() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		res1, err := client.SAdd(context.Background(), key, []string{"lastValue"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res1)

		popMember, err := client.SPop(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "lastValue", popMember.Value())
		assert.False(suite.T(), popMember.IsNil())

		remainingMembers, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), remainingMembers)
	})
}

func (suite *GlideTestSuite) TestSPopCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"value1", "value2", "value3", "value4", "value5"}

		res, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res)

		// Pop multiple members at once
		popMembers, err := client.SPopCount(context.Background(), key, 3)
		suite.NoError(err)
		assert.Len(suite.T(), popMembers, 3)

		// Verify all popped members were in the original set
		for member := range popMembers {
			assert.Contains(suite.T(), members, member)
		}

		// Verify remaining members count
		remainingMembers, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		assert.Len(suite.T(), remainingMembers, 2)

		// Verify no duplicates between popped and remaining
		for member := range popMembers {
			assert.NotContains(suite.T(), remainingMembers, member)
		}
	})
}

func (suite *GlideTestSuite) TestSPopCount_AllMembers() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"value1", "value2", "value3"}

		res, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		// Pop all members
		popMembers, err := client.SPopCount(context.Background(), key, 3)
		suite.NoError(err)
		assert.Len(suite.T(), popMembers, 3)

		popMembersArray := []string{}
		for popMember := range popMembers {
			popMembersArray = append(popMembersArray, popMember)
		}

		// Verify all original members were popped
		for _, member := range members {
			assert.Contains(suite.T(), popMembersArray, member)
		}

		// Verify set is now empty
		remainingMembers, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), remainingMembers)
	})
}

func (suite *GlideTestSuite) TestSPopCount_MoreThanExist() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		members := []string{"value1", "value2"}

		res, err := client.SAdd(context.Background(), key, members)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res)

		// Try to pop more members than exist
		popMembers, err := client.SPopCount(context.Background(), key, 5)
		suite.NoError(err)
		assert.Len(suite.T(), popMembers, 2) // Should only return existing members

		popMembersArray := []string{}
		for popMember := range popMembers {
			popMembersArray = append(popMembersArray, popMember)
		}

		// Verify all original members were popped
		for _, member := range members {
			assert.Contains(suite.T(), popMembersArray, member)
		}

		// Verify set is now empty
		remainingMembers, err := client.SMembers(context.Background(), key)
		suite.NoError(err)
		assert.Empty(suite.T(), remainingMembers)
	})
}

func (suite *GlideTestSuite) TestSPopCount_NonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		// Try to pop from non-existing key
		popMembers, err := client.SPopCount(context.Background(), key, 3)
		suite.NoError(err)
		assert.Empty(suite.T(), popMembers)
	})
}

func (suite *GlideTestSuite) TestSPopCount_WrongType() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		// Set key to a string value
		suite.verifyOK(client.Set(context.Background(), key, "string-value"))

		// Try to pop from a key that's not a set
		_, err := client.SPopCount(context.Background(), key, 3)
		suite.ErrorContains(err, "WRONGTYPE")
	})
}

func (suite *GlideTestSuite) TestSMIsMember() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		stringKey := uuid.NewString()
		nonExistingKey := uuid.NewString()

		res1, err1 := client.SAdd(context.Background(), key1, []string{"one", "two"})
		assert.Nil(suite.T(), err1)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err2 := client.SMIsMember(context.Background(), key1, []string{"two", "three"})
		assert.Nil(suite.T(), err2)
		assert.Equal(suite.T(), []bool{true, false}, res2)

		res3, err3 := client.SMIsMember(context.Background(), nonExistingKey, []string{"two"})
		assert.Nil(suite.T(), err3)
		assert.Equal(suite.T(), []bool{false}, res3)

		// invalid argument - member list must not be empty
		_, err4 := client.SMIsMember(context.Background(), key1, []string{})
		suite.Error(err4)

		// source key exists, but it is not a set
		suite.verifyOK(client.Set(context.Background(), stringKey, "value"))
		_, err5 := client.SMIsMember(context.Background(), stringKey, []string{"two"})
		suite.Error(err5)
	})
}

func (suite *GlideTestSuite) TestSUnion() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		nonSetKey := uuid.NewString()
		memberList1 := []string{"a", "b", "c"}
		memberList2 := []string{"b", "c", "d", "e"}
		expected1 := map[string]struct{}{
			"a": {},
			"b": {},
			"c": {},
			"d": {},
			"e": {},
		}
		expected2 := map[string]struct{}{
			"a": {},
			"b": {},
			"c": {},
		}

		res1, err := client.SAdd(context.Background(), key1, memberList1)
		suite.NoError(err)
		suite.Equal(int64(3), res1)

		res2, err := client.SAdd(context.Background(), key2, memberList2)
		suite.NoError(err)
		suite.Equal(int64(4), res2)

		res3, err := client.SUnion(context.Background(), []string{key1, key2})
		suite.NoError(err)
		suite.True(reflect.DeepEqual(res3, expected1))

		res4, err := client.SUnion(context.Background(), []string{key3})
		suite.NoError(err)
		suite.Empty(res4)

		res5, err := client.SUnion(context.Background(), []string{key1, key3})
		suite.NoError(err)
		suite.True(reflect.DeepEqual(res5, expected2))

		// Errors with empty keys
		res6, err := client.SUnion(context.Background(), []string{})
		suite.Nil(res6)
		suite.Error(err)

		// Error with a non-set key
		suite.verifyOK(client.Set(context.Background(), nonSetKey, "value"))
		res7, err := client.SUnion(context.Background(), []string{nonSetKey, key1})
		suite.Nil(res7)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestSMove() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		key3 := "{key}-3-" + uuid.NewString()
		stringKey := "{key}-4-" + uuid.NewString()
		nonExistingKey := "{key}-5-" + uuid.NewString()
		memberArray1 := []string{"1", "2", "3"}
		memberArray2 := []string{"2", "3"}

		res1, err := client.SAdd(context.Background(), key1, memberArray1)
		suite.NoError(err)
		suite.Equal(int64(3), res1)

		res2, err := client.SAdd(context.Background(), key2, memberArray2)
		suite.NoError(err)
		suite.Equal(int64(2), res2)

		// move an element
		res3, err := client.SMove(context.Background(), key1, key2, "1")
		suite.NoError(err)
		suite.True(res3)

		res4, err := client.SMembers(context.Background(), key1)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"2": {}, "3": {}}, res4)

		res5, err := client.SMembers(context.Background(), key2)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"1": {}, "2": {}, "3": {}}, res5)

		// moved element already exists in the destination set
		res6, err := client.SMove(context.Background(), key2, key1, "2")
		suite.NoError(err)
		suite.True(res6)

		res7, err := client.SMembers(context.Background(), key1)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"2": {}, "3": {}}, res7)

		res8, err := client.SMembers(context.Background(), key2)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"1": {}, "3": {}}, res8)

		// attempt to move from a non-existing key
		res9, err := client.SMove(context.Background(), nonExistingKey, key1, "4")
		suite.NoError(err)
		suite.False(res9)

		res10, err := client.SMembers(context.Background(), key1)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"2": {}, "3": {}}, res10)

		// move to a new set
		res11, err := client.SMove(context.Background(), key1, key3, "2")
		suite.NoError(err)
		suite.True(res11)

		res12, err := client.SMembers(context.Background(), key1)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"3": {}}, res12)

		res13, err := client.SMembers(context.Background(), key3)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"2": {}}, res13)

		// attempt to move a missing element
		res14, err := client.SMove(context.Background(), key1, key3, "42")
		suite.NoError(err)
		suite.False(res14)

		res12, err = client.SMembers(context.Background(), key1)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"3": {}}, res12)

		res13, err = client.SMembers(context.Background(), key3)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"2": {}}, res13)

		// moving missing element to missing key
		res15, err := client.SMove(context.Background(), key1, nonExistingKey, "42")
		suite.NoError(err)
		suite.False(res15)

		res12, err = client.SMembers(context.Background(), key1)
		suite.NoError(err)
		suite.Equal(map[string]struct{}{"3": {}}, res12)

		// key exists but is not contain a set
		_, err = client.Set(context.Background(), stringKey, "value")
		suite.NoError(err)

		_, err = client.SMove(context.Background(), stringKey, key1, "_")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestSScan() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.NewString()
		key2 := "{key}-2-" + uuid.NewString()
		initialCursor := models.NewCursor()
		defaultCount := 10
		// use large dataset to force an iterative cursor.
		numMembers := make([]string, 50000)
		numMembersResult := make([]string, 50000)
		charMembers := []string{"a", "b", "c", "d", "e"}
		t := suite.T()

		// populate the dataset slice
		for i := 0; i < 50000; i++ {
			numMembers[i] = strconv.Itoa(i)
			numMembersResult[i] = strconv.Itoa(i)
		}

		// empty set
		result, err := client.SScan(context.Background(), key1, initialCursor)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor.String(), result.Cursor.String())
		assert.Empty(t, result.Data)

		// negative cursor
		if suite.serverVersion < "8.0.0" {
			result, err = client.SScan(context.Background(), key1, models.NewCursorFromString("-1"))
			assert.NoError(t, err)
			assert.Equal(t, initialCursor.String(), result.Cursor.String())
			assert.Empty(t, result.Data)
		} else {
			_, err = client.SScan(context.Background(), key1, models.NewCursorFromString("-1"))
			suite.Error(err)
		}

		// result contains the whole set
		res, err := client.SAdd(context.Background(), key1, charMembers)
		assert.NoError(t, err)
		assert.Equal(t, int64(len(charMembers)), res)
		result, err = client.SScan(context.Background(), key1, initialCursor)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor.String(), result.Cursor.String())
		assert.Equal(t, len(charMembers), len(result.Data))
		suite.Subset(result.Data, charMembers)

		opts := options.NewBaseScanOptions().SetMatch("a")
		result, err = client.SScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(t, err)
		assert.Equal(t, initialCursor.String(), result.Cursor.String())
		suite.Subset(result.Data, []string{"a"})

		// result contains a subset of the key
		res, err = client.SAdd(context.Background(), key1, numMembers)
		assert.NoError(t, err)
		assert.Equal(t, int64(50000), res)
		resultCollection := []string{}

		cursor := models.NewCursor()
		// 0 is returned for the cursor of the last iteration
		for !cursor.IsFinished() {
			result, err := client.SScan(context.Background(), key1, cursor)
			assert.NoError(t, err)
			assert.NotEqual(t, cursor, result.Cursor)
			if len(result.Data) > 0 {
				assert.False(t, isSubset(result.Data, resultCollection))
			}
			resultCollection = append(resultCollection, result.Data...)
			cursor = result.Cursor
		}
		assert.NotEmpty(t, resultCollection)
		assert.True(t, isSubset(numMembersResult, resultCollection))
		assert.True(t, isSubset(charMembers, resultCollection))

		// test match pattern
		opts = options.NewBaseScanOptions().SetMatch("*")
		result, err = client.SScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(t, err)
		assert.NotEqual(t, initialCursor, result.Cursor)
		assert.GreaterOrEqual(t, len(result.Data), defaultCount)

		// test count
		opts = options.NewBaseScanOptions().SetCount(20)
		result, err = client.SScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(t, err)
		assert.NotEqual(t, initialCursor, result.Cursor)
		assert.GreaterOrEqual(t, len(result.Data), 20)

		// test count with match, returns a non-empty array
		opts = options.NewBaseScanOptions().SetMatch("1*").SetCount(20)
		result, err = client.SScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(t, err)
		assert.NotEqual(t, initialCursor, result.Cursor)
		assert.GreaterOrEqual(t, len(result.Data), 0)

		// error cases
		// non-set key
		_, err = client.Set(context.Background(), key2, "test")
		suite.NoError(err)

		_, err = client.SScan(context.Background(), key2, initialCursor)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestLRange() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value1", "value2", "value3", "value4"}, res2)

		res3, err := client.LRange(context.Background(), "non_existing_key", int64(0), int64(-1))
		suite.NoError(err)
		assert.Empty(suite.T(), res3)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res4, err := client.LRange(context.Background(), key2, int64(0), int64(1))
		suite.Error(err)
		suite.Empty(res4)
	})
}

func (suite *GlideTestSuite) TestLIndex() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(context.Background(), key, list)
		suite.NoError(err)
		suite.Equal(int64(4), res1)

		res2, err := client.LIndex(context.Background(), key, int64(0))
		suite.NoError(err)
		suite.Equal("value1", res2.Value())
		suite.False(res2.IsNil())

		res3, err := client.LIndex(context.Background(), key, int64(-1))
		suite.NoError(err)
		suite.Equal("value4", res3.Value())
		suite.False(res3.IsNil())

		res4, err := client.LIndex(context.Background(), "non_existing_key", int64(0))
		suite.NoError(err)
		suite.Equal(models.CreateNilStringResult(), res4)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res5, err := client.LIndex(context.Background(), key2, int64(0))
		suite.Error(err)
		suite.Equal(models.CreateNilStringResult(), res5)
	})
}

func (suite *GlideTestSuite) TestLTrim() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		suite.verifyOK(client.LTrim(context.Background(), key, int64(0), int64(1)))

		res2, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value1", "value2"}, res2)

		suite.verifyOK(client.LTrim(context.Background(), key, int64(4), int64(2)))

		res3, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Empty(suite.T(), res3)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res4, err := client.LIndex(context.Background(), key2, int64(0))
		suite.Error(err)
		suite.Equal(models.CreateNilStringResult(), res4)
	})
}

func (suite *GlideTestSuite) TestLLen() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value4", "value3", "value2", "value1"}
		key := uuid.NewString()

		res1, err := client.LPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.LLen(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		res3, err := client.LLen(context.Background(), "non_existing_key")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res3)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res4, err := client.LLen(context.Background(), key2)
		suite.Error(err)
		suite.Equal(int64(0), res4)
	})
}

func (suite *GlideTestSuite) TestLRem() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value1", "value2", "value1", "value1", "value2"}
		key := uuid.NewString()

		res1, err := client.LPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res1)

		res2, err := client.LRem(context.Background(), key, 2, "value1")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res2)
		res3, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value2", "value2", "value1"}, res3)

		res4, err := client.LRem(context.Background(), key, -1, "value2")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res4)
		res5, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value2", "value1"}, res5)

		res6, err := client.LRem(context.Background(), key, 0, "value2")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res6)
		res7, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value1"}, res7)

		res8, err := client.LRem(context.Background(), "non_existing_key", 0, "value")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res8)
	})
}

func (suite *GlideTestSuite) TestRPopAndRPopCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value1", "value2", "value3", "value4"}
		key := uuid.NewString()

		res1, err := client.RPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.RPop(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "value4", res2.Value())
		assert.False(suite.T(), res2.IsNil())

		res3, err := client.RPopCount(context.Background(), key, int64(2))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value3", "value2"}, res3)

		res4, err := client.RPop(context.Background(), "non_existing_key")
		suite.NoError(err)
		assert.Equal(suite.T(), models.CreateNilStringResult(), res4)

		res5, err := client.RPopCount(context.Background(), "non_existing_key", int64(2))
		assert.Nil(suite.T(), res5)
		suite.NoError(err)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res6, err := client.RPop(context.Background(), key2)
		suite.Equal(models.CreateNilStringResult(), res6)
		suite.Error(err)

		res7, err := client.RPopCount(context.Background(), key2, int64(2))
		suite.Nil(res7)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestLInsert() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		list := []string{"value1", "value2", "value3", "value4"}
		key := uuid.NewString()

		res1, err := client.RPush(context.Background(), key, list)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res1)

		res2, err := client.LInsert(context.Background(), key, constants.Before, "value2", "value1.5")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res2)

		res3, err := client.LInsert(context.Background(), key, constants.After, "value3", "value3.5")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(6), res3)

		res4, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value1", "value1.5", "value2", "value3", "value3.5", "value4"}, res4)

		res5, err := client.LInsert(context.Background(), "non_existing_key", constants.Before, "pivot", "elem")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res5)

		res6, err := client.LInsert(context.Background(), key, constants.Before, "value5", "value6")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-1), res6)

		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "value"))

		res7, err := client.LInsert(context.Background(), key2, constants.Before, "value5", "value6")
		suite.Error(err)
		suite.Equal(int64(0), res7)
	})
}

func (suite *GlideTestSuite) TestBLPop() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		listKey1 := "{listKey}-1-" + uuid.NewString()
		listKey2 := "{listKey}-2-" + uuid.NewString()

		res1, err := client.LPush(context.Background(), listKey1, []string{"value1", "value2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.BLPop(context.Background(), []string{listKey1, listKey2}, 500*time.Millisecond)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{listKey1, "value2"}, res2)

		res3, err := client.BLPop(context.Background(), []string{listKey2}, 1*time.Second)
		suite.NoError(err)
		assert.Nil(suite.T(), res3)

		key := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, "value"))

		res4, err := client.BLPop(context.Background(), []string{key}, 1*time.Second)
		suite.Error(err)
		suite.Nil(res4)
	})
}

func (suite *GlideTestSuite) TestBRPop() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		listKey1 := "{listKey}-1-" + uuid.NewString()
		listKey2 := "{listKey}-2-" + uuid.NewString()

		res1, err := client.LPush(context.Background(), listKey1, []string{"value1", "value2"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res1)

		res2, err := client.BRPop(context.Background(), []string{listKey1, listKey2}, 500*time.Millisecond)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{listKey1, "value1"}, res2)

		res3, err := client.BRPop(context.Background(), []string{listKey2}, 1*time.Second)
		suite.NoError(err)
		assert.Nil(suite.T(), res3)

		key := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, "value"))

		res4, err := client.BRPop(context.Background(), []string{key}, 1*time.Second)
		suite.Error(err)
		suite.Nil(res4)
	})
}

func (suite *GlideTestSuite) TestRPushX() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		key3 := uuid.NewString()

		res1, err := client.RPush(context.Background(), key1, []string{"value1"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res1)

		res2, err := client.RPushX(context.Background(), key1, []string{"value2", "value3", "value4"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		res3, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value1", "value2", "value3", "value4"}, res3)

		res4, err := client.RPushX(context.Background(), key2, []string{"value1"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res4)

		res5, err := client.LRange(context.Background(), key2, int64(0), int64(-1))
		suite.NoError(err)
		assert.Empty(suite.T(), res5)

		suite.verifyOK(client.Set(context.Background(), key3, "value"))

		res6, err := client.RPushX(context.Background(), key3, []string{"value1"})
		suite.Error(err)
		suite.Equal(int64(0), res6)

		res7, err := client.RPushX(context.Background(), key2, []string{})
		suite.Error(err)
		suite.Equal(int64(0), res7)
	})
}

func (suite *GlideTestSuite) TestLPushX() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		key3 := uuid.NewString()

		res1, err := client.LPush(context.Background(), key1, []string{"value1"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res1)

		res2, err := client.LPushX(context.Background(), key1, []string{"value2", "value3", "value4"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		res3, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"value4", "value3", "value2", "value1"}, res3)

		res4, err := client.LPushX(context.Background(), key2, []string{"value1"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res4)

		res5, err := client.LRange(context.Background(), key2, int64(0), int64(-1))
		suite.NoError(err)
		assert.Empty(suite.T(), res5)

		suite.verifyOK(client.Set(context.Background(), key3, "value"))

		res6, err := client.LPushX(context.Background(), key3, []string{"value1"})
		suite.Error(err)
		suite.Equal(int64(0), res6)

		res7, err := client.LPushX(context.Background(), key2, []string{})
		suite.Error(err)
		suite.Equal(int64(0), res7)
	})
}

func (suite *GlideTestSuite) TestLMPopAndLMPopCount() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		res1, err := client.LMPop(context.Background(), []string{key1}, constants.Left)
		suite.NoError(err)
		assert.Nil(suite.T(), res1)

		res2, err := client.LMPopCount(context.Background(), []string{key1}, constants.Left, int64(1))
		suite.NoError(err)
		assert.Nil(suite.T(), res2)

		res3, err := client.LPush(context.Background(), key1, []string{"one", "two", "three", "four", "five"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res3)
		res4, err := client.LPush(context.Background(), key2, []string{"one", "two", "three", "four", "five"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res4)

		res5, err := client.LMPop(context.Background(), []string{key1}, constants.Left)
		suite.NoError(err)
		assert.Equal(
			suite.T(),
			[]models.KeyValues{{Key: key1, Values: []string{"five"}}},
			res5,
		)

		res6, err := client.LMPopCount(context.Background(), []string{key2, key1}, constants.Right, int64(2))
		suite.NoError(err)
		assert.Equal(
			suite.T(),
			[]models.KeyValues{{Key: key2, Values: []string{"one", "two"}}},
			res6,
		)

		suite.verifyOK(client.Set(context.Background(), key3, "value"))

		res7, err := client.LMPop(context.Background(), []string{key3}, constants.Left)
		suite.Error(err)
		suite.Nil(res7)

		res8, err := client.LMPop(context.Background(), []string{key3}, "Invalid")
		suite.Error(err)
		suite.Nil(res8)
	})
}

func (suite *GlideTestSuite) TestBLMPopAndBLMPopCount() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		res1, err := client.BLMPop(context.Background(), []string{key1}, constants.Left, 100*time.Millisecond)
		suite.NoError(err)
		assert.Nil(suite.T(), res1)

		res2, err := client.BLMPopCount(context.Background(), []string{key1}, constants.Left, int64(1), 100*time.Millisecond)
		suite.NoError(err)
		assert.Nil(suite.T(), res2)

		res3, err := client.LPush(context.Background(), key1, []string{"one", "two", "three", "four", "five"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res3)
		res4, err := client.LPush(context.Background(), key2, []string{"one", "two", "three", "four", "five"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), res4)

		res5, err := client.BLMPop(context.Background(), []string{key1}, constants.Left, 100*time.Millisecond)
		suite.NoError(err)
		assert.Equal(
			suite.T(),
			[]models.KeyValues{{Key: key1, Values: []string{"five"}}},
			res5,
		)

		res6, err := client.BLMPopCount(
			context.Background(),
			[]string{key2, key1},
			constants.Right,
			int64(2),
			100*time.Millisecond,
		)
		suite.NoError(err)
		assert.Equal(
			suite.T(),
			[]models.KeyValues{{Key: key2, Values: []string{"one", "two"}}},
			res6,
		)

		suite.verifyOK(client.Set(context.Background(), key3, "value"))

		res7, err := client.BLMPop(context.Background(), []string{key3}, constants.Left, 100*time.Millisecond)
		suite.Error(err)
		suite.Nil(res7)
	})
}

func (suite *GlideTestSuite) TestBZMPopAndBZMPopWithOptions() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		res1, err := client.BZMPop(context.Background(), []string{key1}, constants.MIN, 100*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), res1.IsNil())

		membersScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		res3, err := client.ZAdd(context.Background(), key1, membersScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res3)
		res4, err := client.ZAdd(context.Background(), key2, membersScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res4)

		// Try to pop the top 2 elements from key1
		res5, err := client.BZMPopWithOptions(context.Background(),
			[]string{key1},
			constants.MAX,
			100*time.Millisecond,
			*options.NewZMPopOptions().SetCount(2),
		)
		suite.NoError(err)
		assert.Equal(suite.T(), key1, res5.Value().Key)
		assert.ElementsMatch(
			suite.T(),
			[]models.MemberAndScore{
				{Member: "three", Score: 3.0},
				{Member: "two", Score: 2.0},
			},
			res5.Value().MembersAndScores,
		)

		// Try to pop the minimum value from key2
		res6, err := client.BZMPop(context.Background(), []string{key2}, constants.MIN, 100*time.Millisecond)
		suite.NoError(err)
		assert.Equal(
			suite.T(),
			models.CreateKeyWithArrayOfMembersAndScoresResult(
				models.KeyWithArrayOfMembersAndScores{
					Key: key2,
					MembersAndScores: []models.MemberAndScore{
						{Member: "one", Score: 1.0},
					},
				},
			),
			res6,
		)

		// Pop the minimum value from multiple keys
		res7, err := client.BZMPop(context.Background(), []string{key1, key2}, constants.MIN, 100*time.Millisecond)
		suite.NoError(err)
		assert.Equal(
			suite.T(),
			models.CreateKeyWithArrayOfMembersAndScoresResult(
				models.KeyWithArrayOfMembersAndScores{
					Key: key1,
					MembersAndScores: []models.MemberAndScore{
						{Member: "one", Score: 1.0},
					},
				},
			),
			res7,
		)

		suite.verifyOK(client.Set(context.Background(), key3, "value"))

		// Popping a non-existent value in key3
		res8, err := client.BZMPop(context.Background(), []string{key3}, constants.MIN, 100*time.Millisecond)
		suite.Error(err)
		suite.True(res8.IsNil())
	})
}

func (suite *GlideTestSuite) TestLSet() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		nonExistentKey := uuid.NewString()

		_, err := client.LSet(context.Background(), nonExistentKey, int64(0), "zero")
		suite.Error(err)

		res2, err := client.LPush(context.Background(), key, []string{"four", "three", "two", "one"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		_, err = client.LSet(context.Background(), key, int64(10), "zero")
		suite.Error(err)

		suite.verifyOK(client.LSet(context.Background(), key, int64(0), "zero"))

		res5, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"zero", "two", "three", "four"}, res5)

		suite.verifyOK(client.LSet(context.Background(), key, int64(-1), "zero"))

		res7, err := client.LRange(context.Background(), key, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"zero", "two", "three", "zero"}, res7)
	})
}

func (suite *GlideTestSuite) TestLMove() {
	if suite.serverVersion < "6.2.0" {
		suite.T().Skip("This feature is added in version 6.2.0")
	}
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		nonExistentKey := "{key}-3" + uuid.NewString()
		nonListKey := "{key}-4" + uuid.NewString()

		res1, err := client.LMove(context.Background(), key1, key2, constants.Left, constants.Right)
		assert.Equal(suite.T(), models.CreateNilStringResult(), res1)
		suite.NoError(err)

		res2, err := client.LPush(context.Background(), key1, []string{"four", "three", "two", "one"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		// only source exists, only source elements gets popped, creates a list at nonExistingKey
		res3, err := client.LMove(context.Background(), key1, nonExistentKey, constants.Right, constants.Left)
		assert.Equal(suite.T(), "four", res3.Value())
		suite.NoError(err)

		res4, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"one", "two", "three"}, res4)

		// source and destination are the same, performing list rotation, "one" gets popped and added back
		res5, err := client.LMove(context.Background(), key1, key1, constants.Left, constants.Left)
		assert.Equal(suite.T(), "one", res5.Value())
		suite.NoError(err)

		res6, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"one", "two", "three"}, res6)
		// normal use case, "three" gets popped and added to the left of destination
		res7, err := client.LPush(context.Background(), key2, []string{"six", "five", "four"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res7)

		res8, err := client.LMove(context.Background(), key1, key2, constants.Right, constants.Left)
		assert.Equal(suite.T(), "three", res8.Value())
		suite.NoError(err)

		res9, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"one", "two"}, res9)
		res10, err := client.LRange(context.Background(), key2, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"three", "four", "five", "six"}, res10)

		// source exists but is not a list type key
		suite.verifyOK(client.Set(context.Background(), nonListKey, "value"))

		res11, err := client.LMove(context.Background(), nonListKey, key1, constants.Left, constants.Left)
		suite.Error(err)
		suite.Equal(models.CreateNilStringResult(), res11)

		// destination exists but is not a list type key
		suite.verifyOK(client.Set(context.Background(), nonListKey, "value"))

		res12, err := client.LMove(context.Background(), key1, nonListKey, constants.Left, constants.Left)
		suite.Error(err)
		suite.Equal(models.CreateNilStringResult(), res12)
	})
}

func (suite *GlideTestSuite) TestExists() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		// Test 1: Check if an existing key returns 1
		suite.verifyOK(client.Set(context.Background(), key, initialValue))
		result, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), result, "The key should exist")

		// Test 2: Check if a non-existent key returns 0
		result, err = client.Exists(context.Background(), []string{"nonExistentKey"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), result, "The non-existent key should not exist")

		// Test 3: Multiple keys, some exist, some do not
		existingKey := uuid.New().String()
		testKey := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), existingKey, value))
		suite.verifyOK(client.Set(context.Background(), testKey, value))
		result, err = client.Exists(context.Background(), []string{testKey, existingKey, "anotherNonExistentKey"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), result, "Two keys should exist")
	})
}

func (suite *GlideTestSuite) TestExpire() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		result, err := client.Expire(context.Background(), key, 1*time.Second)
		assert.Nil(suite.T(), err, "Expected no error from Expire command")
		assert.True(suite.T(), result, "Expire command should return true when expiry is set")

		time.Sleep(1500 * time.Millisecond)

		resultGet, err := client.Get(context.Background(), key)
		assert.Nil(suite.T(), err, "Expected no error from Get command after expiry")
		assert.Equal(suite.T(), "", resultGet.Value(), "Key should be expired and return empty value")
	})
}

func (suite *GlideTestSuite) TestExpire_KeyDoesNotExist() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		// Trying to set an expiry on a non-existent key
		result, err := client.Expire(context.Background(), key, 1*time.Second)
		suite.NoError(err)
		assert.False(suite.T(), result)
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		result, err := client.ExpireWithOptions(context.Background(), key, 2*time.Second, constants.HasNoExpiry)
		suite.NoError(err)
		assert.True(suite.T(), result)

		time.Sleep(2500 * time.Millisecond)

		resultGet, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "", resultGet.Value())

		result, err = client.ExpireWithOptions(context.Background(), key, 1*time.Second, constants.HasNoExpiry)
		suite.NoError(err)
		assert.False(suite.T(), result)
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resexp, err := client.ExpireWithOptions(context.Background(), key, 20*time.Second, constants.HasNoExpiry)
		suite.NoError(err)
		assert.True(suite.T(), resexp)

		resultExpire, err := client.ExpireWithOptions(context.Background(), key, 1*time.Second, constants.HasExistingExpiry)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		time.Sleep(2 * time.Second)

		resultExpireTest, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)

		assert.Equal(suite.T(), int64(0), resultExpireTest)
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.ExpireWithOptions(context.Background(), key, 2*time.Second, constants.HasNoExpiry)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		resultExpire, err = client.ExpireWithOptions(
			context.Background(),
			key,
			5*time.Second,
			constants.NewExpiryGreaterThanCurrent,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)
		time.Sleep(6 * time.Second)
		resultExpireTest, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExpireTest)
	})
}

func (suite *GlideTestSuite) TestExpireWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.ExpireWithOptions(context.Background(), key, 10*time.Second, constants.HasNoExpiry)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		resultExpire, err = client.ExpireWithOptions(
			context.Background(),
			key,
			5*time.Second,
			constants.NewExpiryLessThanCurrent,
		)
		suite.NoError(err)

		assert.True(suite.T(), resultExpire)

		resultExpire, err = client.ExpireWithOptions(
			context.Background(),
			key,
			15*time.Second,
			constants.NewExpiryGreaterThanCurrent,
		)
		suite.NoError(err)

		assert.True(suite.T(), resultExpire)

		time.Sleep(16 * time.Second)
		resultExpireTest, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExpireTest)
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		futureTimestamp := time.Now().Add(10 * time.Second)

		resultExpire, err := client.ExpireAtWithOptions(context.Background(), key, futureTimestamp, constants.HasNoExpiry)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)
		resultExpireAt, err := client.ExpireAt(context.Background(), key, futureTimestamp)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireAt)
		resultExpireWithOptions, err := client.ExpireAtWithOptions(
			context.Background(),
			key,
			futureTimestamp.Add(10),
			constants.HasNoExpiry,
		)
		suite.NoError(err)
		assert.False(suite.T(), resultExpireWithOptions)
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		futureTimestamp := time.Now().Add(10 * time.Second)
		resultExpireAt, err := client.ExpireAt(context.Background(), key, futureTimestamp)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireAt)

		resultExpireWithOptions, err := client.ExpireAtWithOptions(
			context.Background(),
			key,
			futureTimestamp.Add(10),
			constants.HasExistingExpiry,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		futureTimestamp := time.Now().Add(10 * time.Second)
		resultExpireAt, err := client.ExpireAt(context.Background(), key, futureTimestamp)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireAt)

		newFutureTimestamp := time.Now().Add(20 * time.Second)
		resultExpireWithOptions, err := client.ExpireAtWithOptions(context.Background(),
			key,
			newFutureTimestamp,
			constants.NewExpiryGreaterThanCurrent,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)
	})
}

func (suite *GlideTestSuite) TestExpireAtWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		futureTimestamp := time.Now().Add(10 * time.Second)
		resultExpireAt, err := client.ExpireAt(context.Background(), key, futureTimestamp)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireAt)

		newFutureTimestamp := time.Now().Add(5 * time.Second)
		resultExpireWithOptions, err := client.ExpireAtWithOptions(
			context.Background(),
			key,
			newFutureTimestamp,
			constants.NewExpiryLessThanCurrent,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(5 * time.Second)
		resultExpireAtTest, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)

		assert.Equal(suite.T(), int64(0), resultExpireAtTest)
	})
}

func (suite *GlideTestSuite) TestPExpire() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.PExpire(context.Background(), key, 500)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		time.Sleep(600 * time.Millisecond)
		resultExpireCheck, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExpireCheck)
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.PExpire(context.Background(), key, 500*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		newExpire := 1000 * time.Millisecond

		resultExpireWithOptions, err := client.PExpireWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.HasExistingExpiry,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		newExpire := 500 * time.Millisecond

		resultExpireWithOptions, err := client.PExpireWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.HasNoExpiry,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(600 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.PExpire(context.Background(), key, 500*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		newExpire := 1000 * time.Millisecond

		resultExpireWithOptions, err := client.PExpireWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.NewExpiryGreaterThanCurrent,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.PExpire(context.Background(), key, 500*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		newExpire := 200 * time.Millisecond

		resultExpireWithOptions, err := client.PExpireWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.NewExpiryLessThanCurrent,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(600 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireAt() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		expireAfterMilliseconds := time.Now().Add(1000 * time.Millisecond)
		resultPExpireAt, err := client.PExpireAt(context.Background(), key, expireAfterMilliseconds)
		suite.NoError(err)

		assert.True(suite.T(), resultPExpireAt)

		time.Sleep(6 * time.Second)

		resultpExists, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultpExists)
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_HasNoExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		timestamp := time.Now().Add(1000 * time.Millisecond)
		result, err := client.PExpireAtWithOptions(context.Background(), key, timestamp, constants.HasNoExpiry)

		suite.NoError(err)
		assert.True(suite.T(), result)

		time.Sleep(2 * time.Second)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_HasExistingExpiry() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))
		resultExpire, err := client.PExpire(context.Background(), key, 500*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)
		newExpire := time.Now().Add(1000 * time.Millisecond)

		resultExpireWithOptions, err := client.PExpireAtWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.HasExistingExpiry,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_NewExpiryGreaterThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		initialExpire := time.Now().Add(1000 * time.Millisecond)
		resultExpire, err := client.PExpireAt(context.Background(), key, initialExpire)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		newExpire := time.Now().Add(2000 * time.Millisecond)

		resultExpireWithOptions, err := client.PExpireAtWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.NewExpiryGreaterThanCurrent,
		)
		suite.NoError(err)
		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(2100 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestPExpireAtWithOptions_NewExpiryLessThanCurrent() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		resultExpire, err := client.PExpire(context.Background(), key, 1000*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), resultExpire)

		newExpire := time.Now().Add(500 * time.Millisecond)

		resultExpireWithOptions, err := client.PExpireAtWithOptions(
			context.Background(),
			key,
			newExpire,
			constants.NewExpiryLessThanCurrent,
		)
		suite.NoError(err)

		assert.True(suite.T(), resultExpireWithOptions)

		time.Sleep(1100 * time.Millisecond)
		resultExist, err := client.Exists(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultExist)
	})
}

func (suite *GlideTestSuite) TestExpireTime() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		result, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), value, result.Value())

		expireTime := time.Now().Add(3 * time.Second)
		resultExpAt, err := client.ExpireAt(context.Background(), key, expireTime)
		suite.NoError(err)
		assert.True(suite.T(), resultExpAt)

		resexptime, err := client.ExpireTime(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), expireTime.Unix(), resexptime)

		time.Sleep(4 * time.Second)

		resultAfterExpiry, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "", resultAfterExpiry.Value())
	})
}

func (suite *GlideTestSuite) TestExpireTime_KeyDoesNotExist() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		// Call ExpireTime on a key that doesn't exist
		expiryResult, err := client.ExpireTime(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-2), expiryResult)
	})
}

func (suite *GlideTestSuite) TestPExpireTime() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key, value))

		result, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), value, result.Value())

		pexpireTime := time.Now().Add(3000 * time.Millisecond)
		resultExpAt, err := client.PExpireAt(context.Background(), key, pexpireTime)
		suite.NoError(err)
		assert.True(suite.T(), resultExpAt)

		respexptime, err := client.PExpireTime(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), pexpireTime.UnixMilli(), respexptime)

		time.Sleep(4 * time.Second)

		resultAfterExpiry, err := client.Get(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), "", resultAfterExpiry.Value())
	})
}

func (suite *GlideTestSuite) Test_ZCard() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}" + uuid.NewString()
		membersScores := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		t := suite.T()
		res1, err := client.ZAdd(context.Background(), key, membersScores)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res1)

		res2, err := client.ZCard(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res2)

		res3, err := client.ZRem(context.Background(), key, []string{"one"})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), res3)

		res4, err := client.ZCard(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, int64(2), res4)
	})
}

func (suite *GlideTestSuite) TestPExpireTime_KeyDoesNotExist() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		// Call ExpireTime on a key that doesn't exist
		expiryResult, err := client.PExpireTime(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-2), expiryResult)
	})
}

func (suite *GlideTestSuite) TestTTL_WithValidKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		resExpire, err := client.Expire(context.Background(), key, 1*time.Second)
		suite.NoError(err)
		assert.True(suite.T(), resExpire)
		resTTL, err := client.TTL(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), resTTL)
	})
}

func (suite *GlideTestSuite) TestTTL_WithExpiredKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		resExpire, err := client.Expire(context.Background(), key, 1*time.Second)
		suite.NoError(err)
		assert.True(suite.T(), resExpire)

		time.Sleep(2 * time.Second)

		resTTL, err := client.TTL(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(-2), resTTL)
	})
}

func (suite *GlideTestSuite) TestPTTL_WithValidKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		resExpire, err := client.Expire(context.Background(), key, 1*time.Second)
		suite.NoError(err)
		assert.True(suite.T(), resExpire)

		resPTTL, err := client.PTTL(context.Background(), key)
		suite.NoError(err)
		assert.Greater(suite.T(), resPTTL, int64(900))
	})
}

func (suite *GlideTestSuite) TestPTTL_WithExpiredKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key, value))

		resExpire, err := client.Expire(context.Background(), key, 1*time.Second)
		suite.NoError(err)
		assert.True(suite.T(), resExpire)

		time.Sleep(2 * time.Second)

		resPTTL, err := client.PTTL(context.Background(), key)
		suite.NoError(err)
		assert.Equal(suite.T(), resPTTL, int64(-2))
	})
}

func (suite *GlideTestSuite) TestPfAdd_SuccessfulAddition() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		res, err := client.PfAdd(context.Background(), key, []string{"a", "b", "c", "d", "e"})
		suite.NoError(err)
		assert.True(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestPfAdd_DuplicateElements() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		// case : Add elements and add same elements again
		res, err := client.PfAdd(context.Background(), key, []string{"a", "b", "c", "d", "e"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		res2, err := client.PfAdd(context.Background(), key, []string{"a", "b", "c", "d", "e"})
		suite.NoError(err)
		assert.False(suite.T(), res2)

		// case : (mixed elements) add new elements with 1 duplicate elements
		res1, err := client.PfAdd(context.Background(), key, []string{"f", "g", "h"})
		suite.NoError(err)
		assert.True(suite.T(), res1)

		res2, err = client.PfAdd(context.Background(), key, []string{"i", "j", "g"})
		suite.NoError(err)
		assert.True(suite.T(), res2)

		// case : add empty array(no elements to the HyperLogLog)
		res, err = client.PfAdd(context.Background(), key, []string{})
		suite.NoError(err)
		assert.False(suite.T(), res)
	})
}

func (suite *GlideTestSuite) TestPfCount_SingleKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		res, err := client.PfAdd(context.Background(), key, []string{"i", "j", "g"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		resCount, err := client.PfCount(context.Background(), []string{key})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), resCount)
	})
}

func (suite *GlideTestSuite) TestPfCount_MultipleKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String() + "{group}"
		key2 := uuid.New().String() + "{group}"

		res, err := client.PfAdd(context.Background(), key1, []string{"a", "b", "c"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		res, err = client.PfAdd(context.Background(), key2, []string{"c", "d", "e"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		resCount, err := client.PfCount(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), resCount)
	})
}

func (suite *GlideTestSuite) TestPfCount_NoExistingKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String() + "{group}"
		key2 := uuid.New().String() + "{group}"

		resCount, err := client.PfCount(context.Background(), []string{key1, key2})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resCount)
	})
}

func (suite *GlideTestSuite) TestPfMerge() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		source1 := uuid.New().String() + "{group}"
		source2 := uuid.New().String() + "{group}"
		destination := uuid.New().String() + "{group}"

		res, err := client.PfAdd(context.Background(), source1, []string{"a", "b", "c"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		res, err = client.PfAdd(context.Background(), source2, []string{"c", "d", "e"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		result, err := client.PfMerge(context.Background(), destination, []string{source1, source2})
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result)

		count, err := client.PfCount(context.Background(), []string{destination})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(5), count)
	})
}

func (suite *GlideTestSuite) TestPfMerge_SingleSource() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		source := uuid.New().String() + "{group}"
		destination := uuid.New().String() + "{group}"

		res, err := client.PfAdd(context.Background(), source, []string{"a", "b", "c"})
		suite.NoError(err)
		assert.True(suite.T(), res)

		result, err := client.PfMerge(context.Background(), destination, []string{source})
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result)

		count, err := client.PfCount(context.Background(), []string{destination})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), count)
	})
}

func (suite *GlideTestSuite) TestPfMerge_NonExistentSource() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		nonExistentKey := uuid.New().String() + "{group}"
		destination := uuid.New().String() + "{group}"

		result, err := client.PfMerge(context.Background(), destination, []string{nonExistentKey})
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result)

		count, err := client.PfCount(context.Background(), []string{destination})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), count)
	})
}

func (suite *GlideTestSuite) TestSortWithOptions_AscendingOrder() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.LPush(context.Background(), key, []string{"b", "a", "c"})

		options := options.NewSortOptions().
			SetOrderBy(options.ASC).
			SetIsAlpha(true)

		sortResult, err := client.SortWithOptions(context.Background(), key, *options)

		suite.NoError(err)

		resultList := []models.Result[string]{
			models.CreateStringResult("a"),
			models.CreateStringResult("b"),
			models.CreateStringResult("c"),
		}
		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestSortWithOptions_DescendingOrder() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.LPush(context.Background(), key, []string{"b", "a", "c"})

		options := options.NewSortOptions().
			SetOrderBy(options.DESC).
			SetIsAlpha(true).
			SetLimit(options.Limit{Offset: 0, Count: 3})

		sortResult, err := client.SortWithOptions(context.Background(), key, *options)

		suite.NoError(err)

		resultList := []models.Result[string]{
			models.CreateStringResult("c"),
			models.CreateStringResult("b"),
			models.CreateStringResult("a"),
		}

		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestSort_SuccessfulSort() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.LPush(context.Background(), key, []string{"3", "1", "2"})

		sortResult, err := client.Sort(context.Background(), key)

		suite.NoError(err)

		resultList := []models.Result[string]{
			models.CreateStringResult("1"),
			models.CreateStringResult("2"),
			models.CreateStringResult("3"),
		}

		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestSortStore_BasicSorting() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{listKey}" + uuid.New().String()
		sortedKey := "{listKey}" + uuid.New().String()
		client.LPush(context.Background(), key, []string{"10", "2", "5", "1", "4"})

		result, err := client.SortStore(context.Background(), key, sortedKey)

		suite.NoError(err)
		assert.NotNil(suite.T(), result)
		assert.Equal(suite.T(), int64(5), result)

		sortedValues, err := client.LRange(context.Background(), sortedKey, 0, -1)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"1", "2", "4", "5", "10"}, sortedValues)
	})
}

func (suite *GlideTestSuite) TestSortStore_ErrorHandling() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		result, err := client.SortStore(context.Background(), "{listKey}nonExistingKey", "{listKey}mydestinationKey")

		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), result)
	})
}

func (suite *GlideTestSuite) TestSortStoreWithOptions_DescendingOrder() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}" + uuid.New().String()
		sortedKey := "{key}" + uuid.New().String()
		client.LPush(context.Background(), key, []string{"30", "20", "10", "40", "50"})

		options := options.NewSortOptions().SetOrderBy(options.DESC).SetIsAlpha(false)
		result, err := client.SortStoreWithOptions(context.Background(), key, sortedKey, *options)

		suite.NoError(err)
		assert.NotNil(suite.T(), result)
		assert.Equal(suite.T(), int64(5), result)

		sortedValues, err := client.LRange(context.Background(), sortedKey, 0, -1)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"50", "40", "30", "20", "10"}, sortedValues)
	})
}

func (suite *GlideTestSuite) TestSortStoreWithOptions_AlphaSorting() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{listKey}" + uuid.New().String()
		sortedKey := "{listKey}" + uuid.New().String()
		client.LPush(context.Background(), key, []string{"apple", "banana", "cherry", "date", "elderberry"})

		options := options.NewSortOptions().SetIsAlpha(true)
		result, err := client.SortStoreWithOptions(context.Background(), key, sortedKey, *options)

		suite.NoError(err)
		assert.NotNil(suite.T(), result)
		assert.Equal(suite.T(), int64(5), result)

		sortedValues, err := client.LRange(context.Background(), sortedKey, 0, -1)
		resultList := []string{"apple", "banana", "cherry", "date", "elderberry"}
		suite.NoError(err)
		assert.Equal(suite.T(), resultList, sortedValues)
	})
}

func (suite *GlideTestSuite) TestSortStoreWithOptions_Limit() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{listKey}" + uuid.New().String()
		sortedKey := "{listKey}" + uuid.New().String()
		client.LPush(context.Background(), key, []string{"10", "20", "30", "40", "50"})

		options := options.NewSortOptions().SetLimit(options.Limit{Offset: 1, Count: 3})
		result, err := client.SortStoreWithOptions(context.Background(), key, sortedKey, *options)

		suite.NoError(err)
		assert.NotNil(suite.T(), result)
		assert.Equal(suite.T(), int64(3), result)

		sortedValues, err := client.LRange(context.Background(), sortedKey, 0, -1)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"20", "30", "40"}, sortedValues)
	})
}

func (suite *GlideTestSuite) TestSortReadOnly_SuccessfulSort() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.LPush(context.Background(), key, []string{"3", "1", "2"})

		sortResult, err := client.SortReadOnly(context.Background(), key)

		suite.NoError(err)

		resultList := []models.Result[string]{
			models.CreateStringResult("1"),
			models.CreateStringResult("2"),
			models.CreateStringResult("3"),
		}

		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestSortReadyOnlyWithOptions_DescendingOrder() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.LPush(context.Background(), key, []string{"b", "a", "c"})

		options := options.NewSortOptions().
			SetOrderBy(options.DESC).
			SetIsAlpha(true).
			SetLimit(options.Limit{Offset: 0, Count: 3})

		sortResult, err := client.SortReadOnlyWithOptions(context.Background(), key, *options)

		suite.NoError(err)

		resultList := []models.Result[string]{
			models.CreateStringResult("c"),
			models.CreateStringResult("b"),
			models.CreateStringResult("a"),
		}
		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestBLMove() {
	if suite.serverVersion < "6.2.0" {
		suite.T().Skip("This feature is added in version 6.2.0")
	}
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		nonExistentKey := "{key}-3" + uuid.NewString()
		nonListKey := "{key}-4" + uuid.NewString()

		res1, err := client.BLMove(context.Background(), key1, key2, constants.Left, constants.Right, 100*time.Millisecond)
		assert.Equal(suite.T(), models.CreateNilStringResult(), res1)
		suite.NoError(err)

		res2, err := client.LPush(context.Background(), key1, []string{"four", "three", "two", "one"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		// only source exists, only source elements gets popped, creates a list at nonExistingKey
		res3, err := client.BLMove(
			context.Background(),
			key1,
			nonExistentKey,
			constants.Right,
			constants.Left,
			100*time.Millisecond,
		)
		assert.Equal(suite.T(), "four", res3.Value())
		suite.NoError(err)

		res4, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"one", "two", "three"}, res4)

		// source and destination are the same, performing list rotation, "one" gets popped and added back
		res5, err := client.BLMove(context.Background(), key1, key1, constants.Left, constants.Left, 100*time.Millisecond)
		assert.Equal(suite.T(), "one", res5.Value())
		suite.NoError(err)

		res6, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"one", "two", "three"}, res6)
		// normal use case, "three" gets popped and added to the left of destination
		res7, err := client.LPush(context.Background(), key2, []string{"six", "five", "four"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res7)

		res8, err := client.BLMove(context.Background(), key1, key2, constants.Right, constants.Left, 100*time.Millisecond)
		assert.Equal(suite.T(), "three", res8.Value())
		suite.NoError(err)

		res9, err := client.LRange(context.Background(), key1, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"one", "two"}, res9)

		res10, err := client.LRange(context.Background(), key2, int64(0), int64(-1))
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"three", "four", "five", "six"}, res10)

		// source exists but is not a list type key
		suite.verifyOK(client.Set(context.Background(), nonListKey, "value"))

		res11, _ := client.BLMove(
			context.Background(),
			nonListKey,
			key1,
			constants.Left,
			constants.Left,
			100*time.Millisecond,
		)
		suite.Equal(models.CreateNilStringResult(), res11)

		// destination exists but is not a list type key
		suite.verifyOK(client.Set(context.Background(), nonListKey, "value"))

		res12, _ := client.BLMove(
			context.Background(),
			key1,
			nonListKey,
			constants.Left,
			constants.Left,
			100*time.Millisecond,
		)
		suite.Equal(models.CreateNilStringResult(), res12)
	})
}

func (suite *GlideTestSuite) TestDel_MultipleKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "testKey1_" + uuid.New().String()
		key2 := "testKey2_" + uuid.New().String()
		key3 := "testKey3_" + uuid.New().String()

		suite.verifyOK(client.Set(context.Background(), key1, initialValue))
		suite.verifyOK(client.Set(context.Background(), key2, initialValue))
		suite.verifyOK(client.Set(context.Background(), key3, initialValue))

		deletedCount, err := client.Del(context.Background(), []string{key1, key2, key3})

		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), deletedCount)

		result1, err1 := client.Get(context.Background(), key1)
		result2, err2 := client.Get(context.Background(), key2)
		result3, err3 := client.Get(context.Background(), key3)

		assert.Nil(suite.T(), err1)
		assert.True(suite.T(), result1.IsNil())

		assert.Nil(suite.T(), err2)
		assert.True(suite.T(), result2.IsNil())

		assert.Nil(suite.T(), err3)
		assert.True(suite.T(), result3.IsNil())
	})
}

func (suite *GlideTestSuite) TestType() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1: Check if the value is string
		keyName := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), keyName, initialValue))
		result, err := client.Type(context.Background(), keyName)
		suite.NoError(err)
		assert.IsType(suite.T(), result, "string", "Value is string")

		// Test 2: Check if the value is list
		key1 := "{keylist}-1" + uuid.NewString()
		resultLPush, err := client.LPush(context.Background(), key1, []string{"one", "two", "three"})
		assert.Equal(suite.T(), int64(3), resultLPush)
		suite.NoError(err)
		resultType, err := client.Type(context.Background(), key1)
		suite.NoError(err)
		assert.IsType(suite.T(), resultType, "list", "Value is list")
	})
}

func (suite *GlideTestSuite) TestTouch() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1: Check if an touch valid key
		keyName := "{keyName}" + uuid.NewString()
		keyName1 := "{keyName1}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), keyName, initialValue))
		suite.verifyOK(client.Set(context.Background(), keyName1, "anotherValue"))
		result, err := client.Touch(context.Background(), []string{keyName, keyName1})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), result, "The touch should be 2")

		// Test 2: Check if an touch invalid key
		resultInvalidKey, err := client.Touch(context.Background(), []string{"invalidKey", "invalidKey1"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultInvalidKey, "The touch should be 0")
	})
}

func (suite *GlideTestSuite) TestUnlink() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1: Check if an unlink valid key
		keyName := "{keyName}" + uuid.NewString()
		keyName1 := "{keyName1}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), keyName, initialValue))
		suite.verifyOK(client.Set(context.Background(), keyName1, "anotherValue"))
		resultValidKey, err := client.Unlink(context.Background(), []string{keyName, keyName1})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), resultValidKey, "The unlink should be 2")

		// Test 2: Check if an unlink for invalid key
		resultInvalidKey, err := client.Unlink(context.Background(), []string{"invalidKey2", "invalidKey3"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), resultInvalidKey, "The unlink should be 0")
	})
}

func (suite *GlideTestSuite) TestRename() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1 Check if the command successfully renamed
		key := "{keyName}" + uuid.NewString()
		initialValueRename := "TestRename_RenameValue"
		newRenameKey := "{newkeyName}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, initialValueRename))
		client.Rename(context.Background(), key, newRenameKey)

		// Test 2 Check if the rename command return false if the key/newkey is invalid.
		key1 := "{keyName}" + uuid.NewString()
		res1, err := client.Rename(context.Background(), key1, "invalidKey")
		suite.Error(err)
		suite.Equal("", res1)
	})
}

func (suite *GlideTestSuite) TestRenameNX() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1 Check if the RenameNX command return true if key was renamed to newKey
		key := "{keyName}" + uuid.NewString()
		key2 := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, initialValue))
		res1, err := client.RenameNX(context.Background(), key, key2)
		suite.NoError(err)
		assert.True(suite.T(), res1)

		// Test 2 Check if the RenameNX command return false if newKey already exists.
		key3 := "{keyName}" + uuid.NewString()
		key4 := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key3, initialValue))
		suite.verifyOK(client.Set(context.Background(), key4, initialValue))
		res2, err := client.RenameNX(context.Background(), key3, key4)
		suite.NoError(err)
		assert.False(suite.T(), res2)
	})
}

func (suite *GlideTestSuite) TestXAdd() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		// stream does not exist
		_, err := client.XAdd(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "field1", Value: "value1"}, {Field: "field1", Value: "value2"}},
		)
		suite.NoError(err)
		// don't check the value, because it contains server's timestamp

		// adding data to existing stream
		_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field3", Value: "value3"}})
		suite.NoError(err)

		// incorrect input
		_, err = client.XAdd(context.Background(), key, []models.FieldValue{})
		suite.Error(err)

		// key is not a string
		key = uuid.NewString()
		client.Set(context.Background(), key, "abc")
		_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "f", Value: "v"}})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestXAddWithOptions() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		// stream does not exist
		res, err := client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "field1", Value: "value1"}},
			*options.NewXAddOptions().SetDontMakeNewStream(),
		)
		suite.NoError(err)
		assert.True(suite.T(), res.IsNil())

		// adding data to with given ID
		res, err = client.XAddWithOptions(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "field1", Value: "value1"}},
			*options.NewXAddOptions().SetId("0-1"),
		)
		suite.NoError(err)
		assert.Equal(suite.T(), "0-1", res.Value())

		client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
		// TODO run XLen there
		// this will trim the first entry.
		res, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "field3", Value: "value3"}},
			*options.NewXAddOptions().SetTrimOptions(options.NewXTrimOptionsWithMaxLen(2).SetExactTrimming()),
		)
		suite.NoError(err)
		assert.False(suite.T(), res.IsNil())
		// TODO run XLen there
	})
}

// submit args with custom command API, check that no error returned.
// returns a response or raises `errMsg` if failed to submit the command.
func sendWithCustomCommand(suite *GlideTestSuite, client interfaces.BaseClientCommands, args []string, errMsg string) any {
	var res any
	var err error
	switch c := client.(type) {
	case interfaces.GlideClientCommands:
		res, err = c.CustomCommand(context.Background(), args)
	case interfaces.GlideClusterClientCommands:
		res, err = c.CustomCommand(context.Background(), args)
	default:
		suite.FailNow(errMsg)
	}
	assert.NoError(suite.T(), err)
	return res
}

func (suite *GlideTestSuite) TestXAutoClaim() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		group := uuid.NewString()
		consumer := uuid.NewString()

		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "create", key, group, "0", "MKSTREAM"},
			"Can't send XGROUP CREATE as a custom command",
		)
		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "createconsumer", key, group, consumer},
			"Can't send XGROUP CREATECONSUMER as a custom command",
		)

		xadd, err := client.XAddWithOptions(
			context.Background(),
			key,
			[]models.FieldValue{
				{Field: "entry1_field1", Value: "entry1_value1"},
				{Field: "entry1_field2", Value: "entry1_value2"},
			},
			*options.NewXAddOptions().SetId("0-1"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-1", xadd.Value())
		xadd, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "entry2_field1", Value: "entry2_value1"}},
			*options.NewXAddOptions().SetId("0-2"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-2", xadd.Value())

		xreadgroup, err := client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xreadgroup))
		streamResponse, exists := xreadgroup[key]
		assert.True(suite.T(), exists)

		// Check that we have two entries with the correct IDs and fields
		assert.Equal(suite.T(), 2, len(streamResponse.Entries))
		sort.Slice(streamResponse.Entries, func(i, j int) bool {
			return streamResponse.Entries[i].ID < streamResponse.Entries[j].ID
		})
		assert.Equal(suite.T(), streamResponse, models.StreamResponse{
			Entries: []models.StreamEntry{{
				ID: "0-1",
				Fields: []models.FieldValue{{
					Field: "entry1_field1", Value: "entry1_value1",
				}, {
					Field: "entry1_field2", Value: "entry1_value2",
				}},
			}, {
				ID: "0-2",
				Fields: []models.FieldValue{{
					Field: "entry2_field1", Value: "entry2_value1",
				}},
			}},
		})

		opts := options.NewXAutoClaimOptions().SetCount(1)
		xautoclaim, err := client.XAutoClaimWithOptions(context.Background(), key, group, consumer, 0, "0-0", *opts)
		assert.NoError(suite.T(), err)
		var deletedEntries []string
		if suite.serverVersion >= "7.0.0" {
			deletedEntries = []string{}
		}
		assert.Equal(
			suite.T(),
			models.XAutoClaimResponse{
				NextEntry: "0-2",
				ClaimedEntries: []models.StreamEntry{
					{
						ID: "0-1",
						Fields: []models.FieldValue{
							{Field: "entry1_field1", Value: "entry1_value1"},
							{Field: "entry1_field2", Value: "entry1_value2"},
						},
					},
				},
				DeletedMessages: deletedEntries,
			},
			xautoclaim,
		)

		justId, err := client.XAutoClaimJustId(context.Background(), key, group, consumer, 0, "0-0")
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			models.XAutoClaimJustIdResponse{
				NextEntry:       "0-0",
				ClaimedEntries:  []string{"0-1", "0-2"},
				DeletedMessages: deletedEntries,
			},
			justId,
		)

		// add one more entry
		xadd, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "entry3_field1", Value: "entry3_value1"}},
			*options.NewXAddOptions().SetId("0-3"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-3", xadd.Value())

		// incorrect IDs - response is empty
		xautoclaim, err = client.XAutoClaim(context.Background(), key, group, consumer, 0, "5-0")
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			models.XAutoClaimResponse{
				NextEntry:       "0-0",
				ClaimedEntries:  []models.StreamEntry{},
				DeletedMessages: deletedEntries,
			},
			xautoclaim,
		)

		justId, err = client.XAutoClaimJustId(context.Background(), key, group, consumer, 0, "5-0")
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			models.XAutoClaimJustIdResponse{
				NextEntry:       "0-0",
				ClaimedEntries:  []string{},
				DeletedMessages: deletedEntries,
			},
			justId,
		)

		// key exists, but it is not a stream
		key2 := uuid.New().String()
		suite.verifyOK(client.Set(context.Background(), key2, key2))
		_, err = client.XAutoClaim(context.Background(), key2, "_", "_", 0, "_")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestXReadGroup() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{xreadgroup}-1-" + uuid.NewString()
		key2 := "{xreadgroup}-2-" + uuid.NewString()
		key3 := "{xreadgroup}-3-" + uuid.NewString()
		group := uuid.NewString()
		consumer := uuid.NewString()

		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "create", key1, group, "0", "MKSTREAM"},
			"Can't send XGROUP CREATE as a custom command",
		)
		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "createconsumer", key1, group, consumer},
			"Can't send XGROUP CREATECONSUMER as a custom command",
		)

		entry1, err := client.XAdd(context.Background(), key1, []models.FieldValue{{Field: "a", Value: "b"}})
		assert.NoError(suite.T(), err)
		entry2, err := client.XAdd(context.Background(), key1, []models.FieldValue{{Field: "c", Value: "d"}})
		assert.NoError(suite.T(), err)

		// read the entire stream for the consumer and mark messages as pending
		res, err := client.XReadGroup(context.Background(), group, consumer, map[string]string{key1: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(res))
		streamResponse, exists := res[key1]
		assert.True(suite.T(), exists)

		// Check that we have two entries with the correct IDs and fields
		assert.Equal(suite.T(), 2, len(streamResponse.Entries))
		sort.Slice(streamResponse.Entries, func(i, j int) bool {
			return streamResponse.Entries[i].ID < streamResponse.Entries[j].ID
		})
		assert.Equal(suite.T(), streamResponse, models.StreamResponse{
			Entries: []models.StreamEntry{{
				ID: entry1,
				Fields: []models.FieldValue{{
					Field: "a", Value: "b",
				}},
			}, {
				ID: entry2,
				Fields: []models.FieldValue{{
					Field: "c", Value: "d",
				}},
			}},
		})

		// delete one of the entries
		sendWithCustomCommand(suite, client, []string{"xdel", key1, entry1}, "Can't send XDEL as a custom command")

		// now xreadgroup returns one empty entry and one non-empty entry
		res, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key1: "0"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(res))
		streamResponse, exists = res[key1]
		assert.True(suite.T(), exists)
		// Check entries
		entryMap := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// Verify entry1 exists but has no fields (was deleted)
		assert.Contains(suite.T(), entryMap, entry1)
		assert.Empty(suite.T(), entryMap[entry1])

		// Verify entry2 has the correct fields
		assert.Contains(suite.T(), entryMap, entry2)
		assert.Equal(suite.T(), entryMap[entry2], []models.FieldValue{{Field: "c", Value: "d"}})

		// try to read new messages only
		res, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key1: ">"})
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), res)

		// add a message and read it with ">"
		entry3, err := client.XAdd(context.Background(), key1, []models.FieldValue{{Field: "e", Value: "f"}})
		assert.NoError(suite.T(), err)
		res, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key1: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(res))
		streamResponse, exists = res[key1]
		assert.True(suite.T(), exists)

		// Check that we have one entry with the correct ID and fields
		assert.Equal(suite.T(), 1, len(streamResponse.Entries))
		assert.Equal(suite.T(), entry3, streamResponse.Entries[0].ID)
		assert.Equal(suite.T(), streamResponse, models.StreamResponse{
			Entries: []models.StreamEntry{{
				ID: entry3,
				Fields: []models.FieldValue{{
					Field: "e", Value: "f",
				}},
			}},
		})

		// add second key with a group and a consumer, but no messages
		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "create", key2, group, "0", "MKSTREAM"},
			"Can't send XGROUP CREATE as a custom command",
		)
		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "createconsumer", key2, group, consumer},
			"Can't send XGROUP CREATECONSUMER as a custom command",
		)

		// read both keys
		res, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key1: "0", key2: "0"})
		assert.NoError(suite.T(), err)

		// Check that we have two streams
		assert.Equal(suite.T(), 2, len(res))

		// Check key1 stream
		streamResponse1, exists := res[key1]
		assert.True(suite.T(), exists)
		assert.Equal(suite.T(), 3, len(streamResponse1.Entries))

		// Create a map of entry IDs to their fields for key1
		entryMap1 := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse1.Entries {
			entryMap1[entry.ID] = entry.Fields
		}

		// Verify entries in key1
		assert.Contains(suite.T(), entryMap1, entry1)
		assert.Empty(suite.T(), entryMap1[entry1])

		// Check for field "c" with value "d" in entry2
		assert.Contains(suite.T(), entryMap1, entry2)
		assert.Equal(suite.T(), entryMap1[entry2], []models.FieldValue{{Field: "c", Value: "d"}})

		assert.Contains(suite.T(), entryMap1, entry3)
		assert.NotContains(suite.T(), entryMap1[entry3], []models.FieldValue{{Field: "e", Value: "f"}})

		// Check key2 stream (should be empty)
		streamResponse2, exists := res[key2]
		assert.True(suite.T(), exists)
		assert.Empty(suite.T(), streamResponse2.Entries)

		// error cases:
		// key does not exist
		_, err = client.XReadGroup(context.Background(), "_", "_", map[string]string{key3: "0"})
		suite.Error(err)
		// key is not a stream
		suite.verifyOK(client.Set(context.Background(), key3, uuid.New().String()))
		_, err = client.XReadGroup(context.Background(), "_", "_", map[string]string{key3: "0"})
		suite.Error(err)
		del, err := client.Del(context.Background(), []string{key3})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), del)
		// group and consumer don't exist
		xadd, err := client.XAdd(context.Background(), key3, []models.FieldValue{{Field: "a", Value: "b"}})
		assert.NoError(suite.T(), err)
		assert.NotNil(suite.T(), xadd)
		_, err = client.XReadGroup(context.Background(), "_", "_", map[string]string{key3: "0"})
		suite.Error(err)
		// consumer don't exist
		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "create", key3, group, "0-0"},
			"Can't send XGROUP CREATE as a custom command",
		)
		res, err = client.XReadGroup(context.Background(), group, "_", map[string]string{key3: "0"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(res))
		streamResponse3, exists := res[key3]
		assert.True(suite.T(), exists)

		// Check that the stream is empty
		assert.Empty(suite.T(), streamResponse3.Entries)
	})
}

func (suite *GlideTestSuite) TestXRead() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{xread}" + uuid.NewString()
		key2 := "{xread}" + uuid.NewString()
		key3 := "{xread}" + uuid.NewString()

		// key does not exist
		read, err := client.XRead(context.Background(), map[string]string{key1: "0-0"})
		suite.NoError(err)
		assert.Empty(suite.T(), read)

		res, err := client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: "k1_field1", Value: "k1_value1"}, {Field: "k1_field1", Value: "k1_value2"}},
			*options.NewXAddOptions().SetId("0-1"),
		)
		suite.NoError(err)
		assert.False(suite.T(), res.IsNil())

		res, err = client.XAddWithOptions(context.Background(),
			key2,
			[]models.FieldValue{{Field: "k2_field1", Value: "k2_value1"}},
			*options.NewXAddOptions().SetId("2-0"),
		)
		suite.NoError(err)
		assert.False(suite.T(), res.IsNil())

		// reading ID which does not exist yet
		read, err = client.XRead(context.Background(), map[string]string{key1: "100-500"})
		suite.NoError(err)
		assert.Empty(suite.T(), read)

		read, err = client.XRead(context.Background(), map[string]string{key1: "0-0", key2: "0-0"})
		suite.NoError(err)

		// Check that we have two streams
		assert.Equal(suite.T(), 2, len(read))

		// Check key1 stream
		streamResponse1, exists := read[key1]
		assert.True(suite.T(), exists)
		assert.Equal(suite.T(), 1, len(streamResponse1.Entries))
		assert.Equal(suite.T(), "0-1", streamResponse1.Entries[0].ID)
		assert.Equal(suite.T(), streamResponse1.Entries[0].Fields,
			[]models.FieldValue{{Field: "k1_field1", Value: "k1_value1"}, {Field: "k1_field1", Value: "k1_value2"}})

		// Check key2 stream
		streamResponse2, exists := read[key2]
		assert.True(suite.T(), exists)
		assert.Equal(suite.T(), 1, len(streamResponse2.Entries))
		assert.Equal(suite.T(), "2-0", streamResponse2.Entries[0].ID)

		assert.Equal(suite.T(), streamResponse2.Entries[0].Fields,
			[]models.FieldValue{{Field: "k2_field1", Value: "k2_value1"}})

		// Key exists, but it is not a stream
		client.Set(context.Background(), key3, "xread")
		_, err = client.XRead(context.Background(), map[string]string{key1: "0-0", key3: "0-0"})
		suite.Error(err)

		// ensure that commands doesn't time out even if timeout > request timeout
		var testClient interfaces.BaseClientCommands
		if _, ok := client.(interfaces.GlideClientCommands); ok {
			testClient, err = suite.client(config.NewClientConfiguration().
				WithAddress(&suite.standaloneHosts[0]).
				WithUseTLS(suite.tls))
			require.NoError(suite.T(), err)
		} else {
			testClient, err = suite.clusterClient(config.NewClusterClientConfiguration().
				WithAddress(&suite.clusterHosts[0]).
				WithUseTLS(suite.tls))
			require.NoError(suite.T(), err)
		}
		read, err = testClient.XReadWithOptions(context.Background(),
			map[string]string{key1: "0-1"},
			*options.NewXReadOptions().SetBlock(1000 * time.Millisecond),
		)
		suite.NoError(err)
		assert.Empty(suite.T(), read)

		// with 0 timeout (no timeout) should never time out,
		// but we wrap the test with timeout to avoid test failing or stuck forever
		finished := make(chan bool)
		go func() {
			_, err := testClient.XReadWithOptions(
				context.Background(),
				map[string]string{key1: "0-1"},
				*options.NewXReadOptions().SetBlock(0 * time.Millisecond),
			)
			suite.Error(err)
			finished <- true
		}()
		select {
		case <-finished:
			suite.Fail("Infinite block finished")
		case <-time.After(3 * time.Second):
		}
		testClient.Close()
		<-finished
	})
}

func (suite *GlideTestSuite) TestXGroupSetId() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		group := uuid.NewString()
		consumer := uuid.NewString()

		// Setup: Create stream with 3 entries, create consumer group, read entries to add them to the Pending Entries List
		xadd, err := client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "f0", Value: "v0"}},
			*options.NewXAddOptions().SetId("1-0"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "1-0", xadd.Value())
		xadd, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "f1", Value: "v1"}},
			*options.NewXAddOptions().SetId("1-1"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "1-1", xadd.Value())
		xadd, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "f2", Value: "v2"}},
			*options.NewXAddOptions().SetId("1-2"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "1-2", xadd.Value())

		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "create", key, group, "0"},
			"Can't send XGROUP CREATE as a custom command",
		)

		xreadgroup, err := client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xreadgroup))
		streamResponse, exists := xreadgroup[key]
		assert.True(suite.T(), exists)

		// Check entries
		entryMap := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// // Verify entries
		assert.Contains(suite.T(), entryMap, "1-0")
		assert.Equal(suite.T(), entryMap["1-0"],
			[]models.FieldValue{{Field: "f0", Value: "v0"}},
		)

		assert.Contains(suite.T(), entryMap, "1-1")
		// Check for field "f1" with value "v1" in entry 1-1
		assert.Equal(suite.T(), entryMap["1-1"],
			[]models.FieldValue{{Field: "f1", Value: "v1"}},
		)

		assert.Contains(suite.T(), entryMap, "1-2")
		// Check for field "f2" with value "v2" in entry 1-2
		assert.Equal(suite.T(), entryMap["1-2"],
			[]models.FieldValue{{Field: "f2", Value: "v2"}},
		)
		// Sanity check: xreadgroup should not return more entries since they're all already in the
		// Pending Entries List.
		xreadgroup, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)
		assert.Nil(suite.T(), xreadgroup)

		// Reset the last delivered ID for the consumer group to "1-1"
		if suite.serverVersion < "7.0.0" {
			suite.verifyOK(client.XGroupSetId(context.Background(), key, group, "1-1"))
		} else {
			opts := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(42)
			suite.verifyOK(client.XGroupSetIdWithOptions(context.Background(), key, group, "1-1", *opts))
		}

		// xreadgroup should only return entry 1-2 since we reset the last delivered ID to 1-1
		xreadgroup, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xreadgroup))
		streamResponse, exists = xreadgroup[key]
		assert.True(suite.T(), exists)

		// Check entries
		assert.Equal(suite.T(), 1, len(streamResponse.Entries))
		assert.Equal(suite.T(), "1-2", streamResponse.Entries[0].ID)
		assert.Equal(suite.T(), []models.FieldValue{{Field: "f2", Value: "v2"}}, streamResponse.Entries[0].Fields)

		// An error is raised if XGROUP SETID is called with a non-existing key
		_, err = client.XGroupSetId(context.Background(), uuid.NewString(), group, "1-1")
		suite.Error(err)

		// An error is raised if XGROUP SETID is called with a non-existing group
		_, err = client.XGroupSetId(context.Background(), key, uuid.NewString(), "1-1")
		suite.Error(err)

		// Setting the ID to a non-existing ID is allowed
		suite.verifyOK(client.XGroupSetId(context.Background(), key, group, "99-99"))

		// key exists, but is not a stream
		key = uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, "xgroup setid"))
		_, err = client.XGroupSetId(context.Background(), key, group, "1-1")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZAddAndZAddIncr() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
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

		res, err := client.ZAdd(context.Background(), key, membersScoreMap)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res)

		resIncr, err := client.ZAddIncr(context.Background(), key, "one", float64(2))
		assert.Nil(t, err)
		assert.Equal(t, float64(3), resIncr)

		// error cases
		// non-sortedset key
		_, err = client.Set(context.Background(), key2, "test")
		assert.NoError(t, err)

		_, err = client.ZAdd(context.Background(), key2, membersScoreMap)
		suite.Error(err)

		// wrong key type for zaddincr
		_, err = client.ZAddIncr(context.Background(), key2, "one", float64(2))
		suite.Error(err)

		// with NX & XX
		onlyIfExistsOpts := options.NewZAddOptions().SetConditionalChange(constants.OnlyIfExists)
		onlyIfDoesNotExistOpts := options.NewZAddOptions().SetConditionalChange(constants.OnlyIfDoesNotExist)

		res, err = client.ZAddWithOptions(context.Background(), key3, membersScoreMap, *onlyIfExistsOpts)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)

		res, err = client.ZAddWithOptions(context.Background(), key3, membersScoreMap, *onlyIfDoesNotExistOpts)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		resIncr2, err := client.ZAddIncrWithOptions(context.Background(), key3, "one", 5, *onlyIfDoesNotExistOpts)
		suite.NoError(err)
		assert.True(suite.T(), resIncr2.IsNil())

		resIncr2, err = client.ZAddIncrWithOptions(context.Background(), key3, "one", 5, *onlyIfExistsOpts)
		suite.NoError(err)
		assert.Equal(suite.T(), float64(6), resIncr2.Value())

		// with GT or LT
		membersScoreMap2 := map[string]float64{
			"one":   -3.0,
			"two":   2.0,
			"three": 3.0,
		}

		res, err = client.ZAdd(context.Background(), key4, membersScoreMap2)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		membersScoreMap2["one"] = 10.0

		gtOpts := options.NewZAddOptions().SetUpdateOptions(options.ScoreGreaterThanCurrent)
		ltOpts := options.NewZAddOptions().SetUpdateOptions(options.ScoreLessThanCurrent)
		gtOptsChanged, _ := options.NewZAddOptions().SetUpdateOptions(options.ScoreGreaterThanCurrent).SetChanged(true)
		ltOptsChanged, _ := options.NewZAddOptions().SetUpdateOptions(options.ScoreLessThanCurrent).SetChanged(true)

		res, err = client.ZAddWithOptions(context.Background(), key4, membersScoreMap2, *gtOptsChanged)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res)

		res, err = client.ZAddWithOptions(context.Background(), key4, membersScoreMap2, *ltOptsChanged)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(0), res)

		resIncr2, err = client.ZAddIncrWithOptions(context.Background(), key4, "one", -3, *ltOpts)
		suite.NoError(err)
		assert.Equal(suite.T(), float64(7), resIncr2.Value())

		resIncr2, err = client.ZAddIncrWithOptions(context.Background(), key4, "one", -3, *gtOpts)
		suite.NoError(err)
		assert.True(suite.T(), resIncr2.IsNil())
	})
}

func (suite *GlideTestSuite) TestZincrBy() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		key2 := uuid.New().String()

		// key does not exist
		res1, err := client.ZIncrBy(context.Background(), key1, 2.5, "value1")
		suite.NoError(err)
		assert.Equal(suite.T(), 2.5, res1)

		// key exists, but value doesn't
		res2, err := client.ZIncrBy(context.Background(), key1, -3.3, "value2")
		suite.NoError(err)
		assert.Equal(suite.T(), -3.3, res2)

		// updating existing value in existing key
		res3, err := client.ZIncrBy(context.Background(), key1, 1.0, "value1")
		suite.NoError(err)
		assert.Equal(suite.T(), 3.5, res3)

		// Key exists, but it is not a sorted set
		res4, err := client.SAdd(context.Background(), key2, []string{"one", "two"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res4)

		_, err = client.ZIncrBy(context.Background(), key2, 0.5, "_")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestBZPopMin() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{zset}-1-" + uuid.NewString()
		key2 := "{zset}-2-" + uuid.NewString()
		key3 := "{zset}-2-" + uuid.NewString()

		// Add elements to key1
		zaddResult1, err := client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 1.5})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), zaddResult1)

		// Add elements to key2
		zaddResult2, err := client.ZAdd(context.Background(), key2, map[string]float64{"c": 2.0})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), zaddResult2)

		// Pop minimum element from key1 and key2
		bzpopminResult1, err := client.BZPopMin(context.Background(), []string{key1, key2}, 500*time.Millisecond)
		suite.NoError(err)
		assert.Equal(suite.T(), models.KeyWithMemberAndScore{Key: key1, Member: "a", Score: 1.0}, bzpopminResult1.Value())

		// Attempt to pop from non-existent key3
		bzpopminResult2, err := client.BZPopMin(context.Background(), []string{key3}, 1*time.Second)
		suite.NoError(err)
		assert.True(suite.T(), bzpopminResult2.IsNil())

		// Pop minimum element from key2
		bzpopminResult3, err := client.BZPopMin(context.Background(), []string{key3, key2}, 500*time.Millisecond)
		suite.NoError(err)
		assert.Equal(suite.T(), models.KeyWithMemberAndScore{Key: key2, Member: "c", Score: 2.0}, bzpopminResult3.Value())

		// Set key3 to a non-sorted set value
		suite.verifyOK(client.Set(context.Background(), key3, "value"))

		// Attempt to pop from key3 which is not a sorted set
		_, err = client.BZPopMin(context.Background(), []string{key3}, 500*time.Millisecond)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZPopMin() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		key2 := uuid.New().String()
		memberScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		res, err := client.ZAdd(context.Background(), key1, memberScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		res2, err := client.ZPopMin(context.Background(), key1)
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]float64{"one": float64(1)}, res2)

		res3, err := client.ZPopMinWithOptions(context.Background(), key1, *options.NewZPopOptions().SetCount(2))
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]float64{"two": float64(2), "three": float64(3)}, res3)

		// non sorted set key
		_, err = client.Set(context.Background(), key2, "test")
		suite.NoError(err)

		_, err = client.ZPopMin(context.Background(), key2)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZPopMax() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		key2 := uuid.New().String()
		memberScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		res, err := client.ZAdd(context.Background(), key1, memberScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		res2, err := client.ZPopMax(context.Background(), key1)
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]float64{"three": float64(3)}, res2)

		res3, err := client.ZPopMaxWithOptions(context.Background(), key1, *options.NewZPopOptions().SetCount(2))
		suite.NoError(err)
		assert.Equal(suite.T(), map[string]float64{"two": float64(2), "one": float64(1)}, res3)

		// non sorted set key
		_, err = client.Set(context.Background(), key2, "test")
		suite.NoError(err)

		_, err = client.ZPopMax(context.Background(), key2)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZRem() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		memberScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		res, err := client.ZAdd(context.Background(), key, memberScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res)

		// no members to remove
		_, err = client.ZRem(context.Background(), key, []string{})
		suite.Error(err)

		res, err = client.ZRem(context.Background(), key, []string{"one"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res)

		// TODO: run ZCard there
		res, err = client.ZRem(context.Background(), key, []string{"one", "two", "three"})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res)

		// non sorted set key
		_, err = client.Set(context.Background(), key, "test")
		suite.NoError(err)

		_, err = client.ZRem(context.Background(), key, []string{"value"})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZRange() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		t := suite.T()
		key := uuid.New().String()
		memberScoreMap := map[string]float64{
			"a": 1.0,
			"b": 2.0,
			"c": 3.0,
		}
		_, err := client.ZAdd(context.Background(), key, memberScoreMap)
		assert.NoError(t, err)
		// index [0:1]
		res, err := client.ZRange(context.Background(), key, options.NewRangeByIndexQuery(0, 1))
		assert.NoError(t, err)
		assert.Equal(t, []string{"a", "b"}, res)
		// index [0:-1] (all)
		res, err = client.ZRange(context.Background(), key, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, []string{"a", "b", "c"}, res)
		// index [3:1] (none)
		res, err = client.ZRange(context.Background(), key, options.NewRangeByIndexQuery(3, 1))
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
		// score [-inf:3]
		var query options.ZRangeQuery
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true))
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"a", "b", "c"}, res)
		// score [-inf:3)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, false))
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"a", "b"}, res)
		// score (3:-inf] reverse
		query = options.NewRangeByScoreQuery(
			options.NewScoreBoundary(3, false),
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).
			SetReverse()
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"b", "a"}, res)
		// score [-inf:+inf] limit 1 2
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity)).
			SetLimit(1, 2)
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"b", "c"}, res)
		// score [-inf:3) reverse (none)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true)).
			SetReverse()
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
		// score [+inf:3) (none)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
			options.NewScoreBoundary(3, false))
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
		// lex [-:c)
		query = options.NewRangeByLexQuery(
			options.NewInfiniteLexBoundary(constants.NegativeInfinity),
			options.NewLexBoundary("c", false))
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"a", "b"}, res)
		// lex [+:-] reverse limit 1 2
		query = options.NewRangeByLexQuery(
			options.NewInfiniteLexBoundary(constants.PositiveInfinity),
			options.NewInfiniteLexBoundary(constants.NegativeInfinity)).
			SetReverse().SetLimit(1, 2)
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"b", "a"}, res)
		// lex (c:-] reverse
		query = options.NewRangeByLexQuery(
			options.NewLexBoundary("c", false),
			options.NewInfiniteLexBoundary(constants.NegativeInfinity)).
			SetReverse()
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, []string{"b", "a"}, res)
		// lex [+:c] (none)
		query = options.NewRangeByLexQuery(
			options.NewInfiniteLexBoundary(constants.PositiveInfinity),
			options.NewLexBoundary("c", true))
		res, err = client.ZRange(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
	})
}

func (suite *GlideTestSuite) TestZRangeWithScores() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		t := suite.T()
		key := uuid.New().String()
		memberScoreMap := map[string]float64{
			"a":  2.0,
			"ab": 2.0,
			"b":  4.0,
			"c":  3.0,
			"d":  8.0,
			"e":  5.0,
			"f":  1.0,
			"ac": 2.0,
			"g":  2.0,
		}
		_, err := client.ZAdd(context.Background(), key, memberScoreMap)
		assert.NoError(t, err)
		// index [0:1]
		res, err := client.ZRangeWithScores(context.Background(), key, options.NewRangeByIndexQuery(0, 1))
		expected := []models.MemberAndScore{
			{Member: "f", Score: float64(1.0)},
			{Member: "a", Score: float64(2.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// index [0:-1] (all)
		res, err = client.ZRangeWithScores(context.Background(), key, options.NewRangeByIndexQuery(0, -1))
		expected = []models.MemberAndScore{
			{Member: "f", Score: float64(1.0)},
			{Member: "a", Score: float64(2.0)},
			{Member: "ab", Score: float64(2.0)},
			{Member: "ac", Score: float64(2.0)},
			{Member: "g", Score: float64(2.0)},
			{Member: "c", Score: float64(3.0)},
			{Member: "b", Score: float64(4.0)},
			{Member: "e", Score: float64(5.0)},
			{Member: "d", Score: float64(8.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// index [3:1] (none)
		res, err = client.ZRangeWithScores(context.Background(), key, options.NewRangeByIndexQuery(3, 1))
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
		// score [-inf:3]
		query := options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true))
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		expected = []models.MemberAndScore{
			{Member: "f", Score: float64(1.0)},
			{Member: "a", Score: float64(2.0)},
			{Member: "ab", Score: float64(2.0)},
			{Member: "ac", Score: float64(2.0)},
			{Member: "g", Score: float64(2.0)},
			{Member: "c", Score: float64(3.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// score [-inf:3)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, false))
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		expected = []models.MemberAndScore{
			{Member: "f", Score: float64(1.0)},
			{Member: "a", Score: float64(2.0)},
			{Member: "ab", Score: float64(2.0)},
			{Member: "ac", Score: float64(2.0)},
			{Member: "g", Score: float64(2.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// score (3:-inf] reverse
		query = options.NewRangeByScoreQuery(
			options.NewScoreBoundary(3, false),
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).
			SetReverse()
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		expected = []models.MemberAndScore{
			{Member: "g", Score: float64(2.0)},
			{Member: "ac", Score: float64(2.0)},
			{Member: "ab", Score: float64(2.0)},
			{Member: "a", Score: float64(2.0)},
			{Member: "f", Score: float64(1.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// score [inf:-inf] reverse
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).
			SetReverse()
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		expected = []models.MemberAndScore{
			{Member: "d", Score: float64(8.0)},
			{Member: "e", Score: float64(5.0)},
			{Member: "b", Score: float64(4.0)},
			{Member: "c", Score: float64(3.0)},
			{Member: "g", Score: float64(2.0)},
			{Member: "ac", Score: float64(2.0)},
			{Member: "ab", Score: float64(2.0)},
			{Member: "a", Score: float64(2.0)},
			{Member: "f", Score: float64(1.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// score [-inf:+inf] limit 4 2
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity)).
			SetLimit(4, 2)
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		expected = []models.MemberAndScore{
			{Member: "g", Score: float64(2.0)},
			{Member: "c", Score: float64(3.0)},
		}
		assert.NoError(t, err)
		assert.Equal(t, expected, res)
		// score [-inf:3) reverse (none)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true)).
			SetReverse()
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
		// score [+inf:3) (none)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
			options.NewScoreBoundary(3, false))
		res, err = client.ZRangeWithScores(context.Background(), key, query)
		assert.NoError(t, err)
		assert.Equal(t, 0, len(res))
	})
}

func (suite *GlideTestSuite) TestZRangeStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		t := suite.T()
		key := "{key}" + uuid.New().String()
		dest := "{key}" + uuid.New().String()
		memberScoreMap := map[string]float64{
			"a": 1.0,
			"b": 2.0,
			"c": 3.0,
		}
		_, err := client.ZAdd(context.Background(), key, memberScoreMap)
		assert.NoError(t, err)
		// index [0:1]
		res, err := client.ZRangeStore(context.Background(), dest, key, options.NewRangeByIndexQuery(0, 1))
		assert.NoError(t, err)
		res1, err := client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, 1))
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"a", "b"}, res1)
		// index [0:-1] (all)
		res, err = client.ZRangeStore(context.Background(), dest, key, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res)
		assert.Equal(t, []string{"a", "b", "c"}, res1)
		// index [3:1] (none)
		res, err = client.ZRangeStore(context.Background(), dest, key, options.NewRangeByIndexQuery(3, 1))
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(3, 1))
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res)
		assert.Equal(t, 0, len(res1))
		// score [-inf:3]
		var query options.ZRangeQuery
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true))
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, query)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), res)
		assert.Equal(t, []string{"a", "b", "c"}, res1)
		// score [-inf:3)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, false))
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, query)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"a", "b"}, res1)
		// score (3:-inf] reverse
		query = options.NewRangeByScoreQuery(
			options.NewScoreBoundary(3, false),
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity)).
			SetReverse()
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, query)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"b", "a"}, res1)
		// score [-inf:+inf] limit 1 2
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity)).
			SetLimit(1, 2)
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"b", "c"}, res1)
		// score [-inf:3) reverse (none)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true)).
			SetReverse()
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res)
		assert.Equal(t, 0, len(res1))
		// score [+inf:3) (none)
		query = options.NewRangeByScoreQuery(
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
			options.NewScoreBoundary(3, false))
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res)
		assert.Equal(t, 0, len(res1))
		// lex [-:c)
		query = options.NewRangeByLexQuery(
			options.NewInfiniteLexBoundary(constants.NegativeInfinity),
			options.NewLexBoundary("c", false))
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"a", "b"}, res1)
		// lex [+:-] reverse limit 1 2
		query = options.NewRangeByLexQuery(
			options.NewInfiniteLexBoundary(constants.PositiveInfinity),
			options.NewInfiniteLexBoundary(constants.NegativeInfinity)).
			SetReverse().SetLimit(1, 2)
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"a", "b"}, res1)
		// lex (c:-] reverse
		query = options.NewRangeByLexQuery(
			options.NewLexBoundary("c", false),
			options.NewInfiniteLexBoundary(constants.NegativeInfinity)).
			SetReverse()
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(2), res)
		assert.Equal(t, []string{"a", "b"}, res1)
		// lex [+:c] (none)
		query = options.NewRangeByLexQuery(
			options.NewInfiniteLexBoundary(constants.PositiveInfinity),
			options.NewLexBoundary("c", true))
		res, err = client.ZRangeStore(context.Background(), dest, key, query)
		assert.NoError(t, err)
		res1, err = client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res)
		assert.Equal(t, 0, len(res1))
		// Pull from non-existent source
		res, err = client.ZRangeStore(context.Background(), dest, "{key}nonExistent", query)
		assert.NoError(t, err)
		assert.Equal(t, int64(0), res)
	})
}

func (suite *GlideTestSuite) TestPersist() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1: Check if persist command removes the expiration time of a key.
		keyName := "{keyName}" + uuid.NewString()
		t := suite.T()
		suite.verifyOK(client.Set(context.Background(), keyName, initialValue))
		resultExpire, err := client.Expire(context.Background(), keyName, 300*time.Second)
		assert.Nil(t, err)
		assert.True(t, resultExpire)
		resultPersist, err := client.Persist(context.Background(), keyName)
		assert.Nil(t, err)
		assert.True(t, resultPersist)

		// Test 2: Check if persist command return false if key that doesnt have associated timeout.
		keyNoExp := "{keyName}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), keyNoExp, initialValue))
		resultPersistNoExp, err := client.Persist(context.Background(), keyNoExp)
		assert.Nil(t, err)
		assert.False(t, resultPersistNoExp)

		// Test 3: Check if persist command return false if key not exist.
		keyInvalid := "{invalidkey_forPersistTest}" + uuid.NewString()
		resultInvalidKey, err := client.Persist(context.Background(), keyInvalid)
		assert.Nil(t, err)
		assert.False(t, resultInvalidKey)
	})
}

func (suite *GlideTestSuite) TestZRank() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		stringKey := uuid.New().String()
		client.ZAdd(context.Background(), key, map[string]float64{"one": 1.5, "two": 2.0, "three": 3.0})
		res, err := client.ZRank(context.Background(), key, "two")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res.Value())

		if suite.serverVersion >= "7.2.0" {
			res2, err := client.ZRankWithScore(context.Background(), key, "one")
			suite.NoError(err)
			assert.Equal(suite.T(), int64(0), res2.Value().Rank)
			assert.Equal(suite.T(), float64(1.5), res2.Value().Score)
			res4, err := client.ZRankWithScore(context.Background(), key, "non-existing-member")
			suite.NoError(err)
			assert.True(suite.T(), res4.IsNil())
			assert.True(suite.T(), res4.IsNil())
		}

		res3, err := client.ZRank(context.Background(), key, "non-existing-member")
		suite.NoError(err)
		assert.True(suite.T(), res3.IsNil())

		// key exists, but it is not a set
		suite.verifyOK(client.Set(context.Background(), stringKey, "value"))

		_, err = client.ZRank(context.Background(), stringKey, "value")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZRevRank() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		stringKey := uuid.New().String()
		client.ZAdd(context.Background(), key, map[string]float64{"one": 1.5, "two": 2.0, "three": 3.0})
		res, err := client.ZRevRank(context.Background(), key, "two")
		suite.NoError(err)
		assert.Equal(suite.T(), int64(1), res.Value())

		if suite.serverVersion >= "7.2.0" {
			res2, err := client.ZRevRankWithScore(context.Background(), key, "one")
			suite.NoError(err)
			assert.Equal(suite.T(), int64(2), res2.Value().Rank)
			assert.Equal(suite.T(), float64(1.5), res2.Value().Score)
			res4, err := client.ZRevRankWithScore(context.Background(), key, "non-existing-member")
			suite.NoError(err)
			assert.True(suite.T(), res4.IsNil())
			assert.True(suite.T(), res4.IsNil())
		}

		res3, err := client.ZRevRank(context.Background(), key, "non-existing-member")
		suite.NoError(err)
		assert.True(suite.T(), res3.IsNil())

		// key exists, but it is not a set
		suite.verifyOK(client.Set(context.Background(), stringKey, "value"))

		_, err = client.ZRevRank(context.Background(), stringKey, "value")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) Test_XAdd_XLen_XTrim() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		field1 := uuid.NewString()
		field2 := uuid.NewString()
		t := suite.T()
		xAddResult, err := client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: field1, Value: "foo"}, {Field: field2, Value: "bar"}},
			*options.NewXAddOptions().SetDontMakeNewStream(),
		)
		assert.NoError(t, err)
		assert.True(t, xAddResult.IsNil())

		xAddResult, err = client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: field1, Value: "foo1"}, {Field: field2, Value: "bar1"}},
			*options.NewXAddOptions().SetId("0-1"),
		)
		assert.NoError(t, err)
		assert.Equal(t, xAddResult.Value(), "0-1")

		_, err = client.XAdd(context.Background(),
			key1,
			[]models.FieldValue{{Field: field1, Value: "foo2"}, {Field: field2, Value: "bar2"}},
		)
		assert.NoError(t, err)

		xLenResult, err := client.XLen(context.Background(), key1)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), xLenResult)

		// Trim the first entry.
		xAddResult, err = client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: field1, Value: "foo3"}, {Field: field2, Value: "bar2"}},
			*options.NewXAddOptions().SetTrimOptions(
				options.NewXTrimOptionsWithMaxLen(2).SetExactTrimming(),
			),
		)
		assert.NotNil(t, xAddResult.Value())
		assert.NoError(t, err)
		id := xAddResult.Value()
		xLenResult, err = client.XLen(context.Background(), key1)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), xLenResult)

		// Trim the second entry.
		xAddResult, err = client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: field1, Value: "foo4"}, {Field: field2, Value: "bar4"}},
			*options.NewXAddOptions().SetTrimOptions(
				options.NewXTrimOptionsWithMinId(id).SetExactTrimming(),
			),
		)
		assert.NoError(t, err)
		assert.NotNil(t, xAddResult.Value())
		xLenResult, err = client.XLen(context.Background(), key1)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), xLenResult)

		// Test xtrim to remove 1 element
		xTrimResult, err := client.XTrim(context.Background(),
			key1,
			*options.NewXTrimOptionsWithMaxLen(1).SetExactTrimming(),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(1), xTrimResult)
		xLenResult, err = client.XLen(context.Background(), key1)
		assert.NoError(t, err)
		assert.Equal(t, int64(1), xLenResult)

		// Key does not exist - returns 0
		xTrimResult, err = client.XTrim(context.Background(),
			key2,
			*options.NewXTrimOptionsWithMaxLen(1).SetExactTrimming(),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(0), xTrimResult)
		xLenResult, err = client.XLen(context.Background(), key2)
		assert.NoError(t, err)
		assert.Equal(t, int64(0), xLenResult)

		// Throw error: Key exists - but it is not a stream
		suite.verifyOK(client.Set(context.Background(), key2, "xtrimtest"))
		_, err = client.XTrim(context.Background(), key2, *options.NewXTrimOptionsWithMinId("0-1"))
		suite.Error(err)
		_, err = client.XLen(context.Background(), key2)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) Test_ZScore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		t := suite.T()

		membersScores := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		zAddResult, err := client.ZAdd(context.Background(), key1, membersScores)
		assert.NoError(t, err)
		assert.Equal(t, zAddResult, int64(3))

		zScoreResult, err := client.ZScore(context.Background(), key1, "one")
		assert.NoError(t, err)
		assert.Equal(t, zScoreResult.Value(), float64(1.0))

		zScoreResult, err = client.ZScore(context.Background(), key1, "non_existing_member")
		assert.NoError(t, err)
		assert.True(t, zScoreResult.IsNil())

		zScoreResult, err = client.ZScore(context.Background(), "non_existing_key", "non_existing_member")
		assert.NoError(t, err)
		assert.True(t, zScoreResult.IsNil())

		// Key exists, but it is not a set
		setResult, err := client.Set(context.Background(), key2, "bar")
		assert.NoError(t, err)
		assert.Equal(t, setResult, "OK")

		_, err = client.ZScore(context.Background(), key2, "one")
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		membersScores := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		t := suite.T()
		res1, err := client.ZAdd(context.Background(), key1, membersScores)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), res1)

		// In range negative to positive infinity.
		zCountRange := options.NewZCountRange(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
		)
		zCountResult, err := client.ZCount(context.Background(), key1, *zCountRange)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), zCountResult)
		zCountRange = options.NewZCountRange(
			options.NewInclusiveScoreBoundary(math.Inf(-1)),
			options.NewInclusiveScoreBoundary(math.Inf(+1)),
		)
		zCountResult, err = client.ZCount(context.Background(), key1, *zCountRange)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), zCountResult)

		// In range 1 (exclusive) to 3 (inclusive)
		zCountRange = options.NewZCountRange(
			options.NewScoreBoundary(1, false),
			options.NewScoreBoundary(3, true),
		)
		zCountResult, err = client.ZCount(context.Background(), key1, *zCountRange)
		assert.Nil(t, err)
		assert.Equal(t, int64(2), zCountResult)

		// In range negative infinity to 3 (inclusive)
		zCountRange = options.NewZCountRange(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewScoreBoundary(3, true),
		)
		zCountResult, err = client.ZCount(context.Background(), key1, *zCountRange)
		assert.Nil(t, err)
		assert.Equal(t, int64(3), zCountResult)

		// Incorrect range start > end
		zCountRange = options.NewZCountRange(
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
			options.NewInclusiveScoreBoundary(3),
		)
		zCountResult, err = client.ZCount(context.Background(), key1, *zCountRange)
		assert.Nil(t, err)
		assert.Equal(t, int64(0), zCountResult)

		// Non-existing key
		zCountRange = options.NewZCountRange(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
		)
		zCountResult, err = client.ZCount(context.Background(), "non_existing_key", *zCountRange)
		assert.Nil(t, err)
		assert.Equal(t, int64(0), zCountResult)

		// Key exists, but it is not a set
		setResult, _ := client.Set(context.Background(), key2, "value")
		assert.Equal(t, setResult, "OK")
		zCountRange = options.NewZCountRange(
			options.NewInfiniteScoreBoundary(constants.NegativeInfinity),
			options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
		)
		_, err = client.ZCount(context.Background(), key2, *zCountRange)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) Test_XDel() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		streamId1 := "0-1"
		streamId2 := "0-2"
		streamId3 := "0-3"
		t := suite.T()

		xAddResult, err := client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: "f1", Value: "foo1"}, {Field: "f2", Value: "bar2"}},
			*options.NewXAddOptions().SetId(streamId1),
		)
		assert.NoError(t, err)
		assert.Equal(t, xAddResult.Value(), streamId1)

		xAddResult, err = client.XAddWithOptions(context.Background(),
			key1,
			[]models.FieldValue{{Field: "f1", Value: "foo1"}, {Field: "f2", Value: "bar2"}},
			*options.NewXAddOptions().SetId(streamId2),
		)
		assert.NoError(t, err)
		assert.Equal(t, xAddResult.Value(), streamId2)

		xLenResult, err := client.XLen(context.Background(), key1)
		assert.NoError(t, err)
		assert.Equal(t, xLenResult, int64(2))

		// Deletes one stream id, and ignores anything invalid:
		xDelResult, err := client.XDel(context.Background(), key1, []string{streamId1, streamId3})
		assert.NoError(t, err)
		assert.Equal(t, xDelResult, int64(1))

		xDelResult, err = client.XDel(context.Background(), key2, []string{streamId3})
		assert.NoError(t, err)
		assert.Equal(t, xDelResult, int64(0))

		// Throws error: Key exists - but it is not a stream
		setResult, err := client.Set(context.Background(), key2, "xdeltest")
		assert.NoError(t, err)
		assert.Equal(t, "OK", setResult)

		_, err = client.XDel(context.Background(), key2, []string{streamId3})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZScan() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		initialCursor := models.NewCursor()
		defaultCount := 20

		// Set up test data - use a large number of entries to force an iterative cursor
		numberMap := make(map[string]float64)
		numMembers := make([]string, 50000)
		charMembers := []string{"a", "b", "c", "d", "e"}
		for i := 0; i < 50000; i++ {
			numberMap["member"+strconv.Itoa(i)] = float64(i)
			numMembers[i] = "member" + strconv.Itoa(i)
		}
		charMap := make(map[string]float64)
		charMapValues := []string{}
		for i, val := range charMembers {
			charMap[val] = float64(i)
			charMapValues = append(charMapValues, strconv.Itoa(i))
		}

		// Empty set
		result, err := client.ZScan(context.Background(), key1, initialCursor)
		assert.NoError(suite.T(), err)
		suite.Equal(initialCursor.String(), result.Cursor.String())
		assert.Empty(suite.T(), result.Data)

		// Negative cursor
		if suite.serverVersion >= "8.0.0" {
			_, err = client.ZScan(context.Background(), key1, models.NewCursorFromString("-1"))
			suite.Error(err)
		} else {
			result, err = client.ZScan(context.Background(), key1, models.NewCursorFromString("-1"))
			suite.NoError(err)
			suite.Equal(initialCursor.String(), result.Cursor.String())
			suite.Empty(result.Data)
		}

		// Result contains the whole set
		res, err := client.ZAdd(context.Background(), key1, charMap)
		suite.NoError(err)
		suite.Equal(int64(5), res)

		result, err = client.ZScan(context.Background(), key1, initialCursor)
		suite.NoError(err)
		suite.Equal(initialCursor.String(), result.Cursor.String())
		suite.Equal(len(charMap)*2, len(result.Data))

		resultKeySet := make([]string, 0, len(charMap))
		resultValueSet := make([]string, 0, len(charMap))

		// Iterate through array taking pairs of items
		for i := 0; i < len(result.Data); i += 2 {
			resultKeySet = append(resultKeySet, result.Data[i])
			resultValueSet = append(resultValueSet, result.Data[i+1])
		}

		// Verify all expected keys exist in result
		assert.True(suite.T(), isSubset(charMembers, resultKeySet))

		// Scores come back as integers converted to a string when the fraction is zero.
		assert.True(suite.T(), isSubset(charMapValues, resultValueSet))

		opts := options.NewZScanOptions().SetMatch("a")
		result, err = client.ZScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(suite.T(), err)
		suite.Equal(initialCursor.String(), result.Cursor.String())
		assert.Equal(suite.T(), result.Data, []string{"a", "0"})

		// Result contains a subset of the key
		res, err = client.ZAdd(context.Background(), key1, numberMap)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(50000), res)

		resultCollection := []string{}
		resKeys := []string{}

		cursor := models.NewCursor()
		// 0 is returned for the cursor of the last iteration
		for !cursor.IsFinished() {
			result, err := client.ZScan(context.Background(), key1, cursor)
			assert.NoError(suite.T(), err)
			assert.NotEqual(suite.T(), cursor, result.Cursor)
			if len(result.Data) > 0 {
				assert.False(suite.T(), isSubset(result.Data, resultCollection))
			}
			resultCollection = append(resultCollection, result.Data...)
			cursor = result.Cursor
		}

		for i := 0; i < len(resultCollection); i += 2 {
			resKeys = append(resKeys, resultCollection[i])
		}

		assert.NotEmpty(suite.T(), resultCollection)
		// Verify we got all keys and values
		assert.True(suite.T(), isSubset(numMembers, resKeys))

		// Test match pattern
		opts = options.NewZScanOptions().SetMatch("*")
		result, err = client.ZScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), initialCursor, result.Cursor)
		assert.GreaterOrEqual(suite.T(), len(result.Data), defaultCount)

		// test count
		opts = options.NewZScanOptions().SetCount(20)
		result, err = client.ZScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), initialCursor, result.Cursor)
		assert.GreaterOrEqual(suite.T(), len(result.Data), 20)

		// test count with match, returns a non-empty array
		opts = options.NewZScanOptions().SetMatch("1*").SetCount(20)
		result, err = client.ZScanWithOptions(context.Background(), key1, initialCursor, *opts)
		assert.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), initialCursor, result.Cursor)
		assert.GreaterOrEqual(suite.T(), len(result.Data), 0)

		// Test NoScores option for Redis 8.0.0+
		if suite.serverVersion >= "8.0.0" {
			// Use a fresh key for NoScores test to avoid interference from previous entries
			noScoresKey := uuid.New().String()
			// Create a smaller fresh map for NoScores test - we don't need 50K entries just to test the NoScores option
			freshNumberMap := make(map[string]float64)
			for i := 0; i < 100; i++ {
				freshNumberMap["member"+strconv.Itoa(i)] = float64(i)
			}
			res, err := client.ZAdd(context.Background(), noScoresKey, freshNumberMap)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), int64(100), res)

			opts = options.NewZScanOptions().SetNoScores(true)
			result, err = client.ZScanWithOptions(context.Background(), noScoresKey, initialCursor, *opts)
			assert.NoError(suite.T(), err)
			cursor, err := strconv.ParseInt(result.Cursor.String(), 10, 64)
			assert.NoError(suite.T(), err)
			assert.GreaterOrEqual(suite.T(), cursor, int64(0))

			// Verify all fields start with "member"
			for _, field := range result.Data {
				assert.True(suite.T(), strings.HasPrefix(field, "member"))
			}
		}

		// Test errors
		// Non-set key
		stringKey := uuid.New().String()
		setRes, err := client.Set(context.Background(), stringKey, "test")
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", setRes)

		_, err = client.ZScan(context.Background(), stringKey, initialCursor)
		suite.Error(err)

		opts = options.NewZScanOptions().SetMatch("test").SetCount(1)
		_, err = client.ZScanWithOptions(context.Background(), stringKey, initialCursor, *opts)
		suite.Error(err)

		// Negative count
		opts = options.NewZScanOptions().SetCount(-1)
		_, err = client.ZScanWithOptions(context.Background(), key1, models.NewCursor(), *opts)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestXPending() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// TODO: Update tests when XGroupCreate, XGroupCreateConsumer, XReadGroup, XClaim, XClaimJustId and XAck are added to
		// the Go client.
		//
		// This test splits out the cluster and standalone tests into their own functions because we are forced to use
		// CustomCommands for many stream commands which are not included in the preview Go client. Using a type switch for
		// each use of CustomCommand would make the tests difficult to read and maintain. These tests can be
		// collapsed once the native commands are added in a subsequent release.

		execStandalone := func(client interfaces.GlideClientCommands) {
			// 1. Arrange the data
			key := uuid.New().String()
			groupName := "group" + uuid.New().String()
			zeroStreamId := "0"
			consumer1 := "consumer-1-" + uuid.New().String()
			consumer2 := "consumer-2-" + uuid.New().String()

			command := []string{"XGroup", "Create", key, groupName, zeroStreamId, "MKSTREAM"}

			resp, err := client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), "OK", resp.(string))

			command = []string{"XGroup", "CreateConsumer", key, groupName, consumer1}
			resp, err = client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), resp.(bool))

			command = []string{"XGroup", "CreateConsumer", key, groupName, consumer2}
			resp, err = client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), resp.(bool))

			streamid_1, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
			assert.NoError(suite.T(), err)
			streamid_2, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
			assert.NoError(suite.T(), err)

			_, err = client.XReadGroup(context.Background(), groupName, consumer1, map[string]string{key: ">"})
			assert.NoError(suite.T(), err)

			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field3", Value: "value3"}})
			assert.NoError(suite.T(), err)
			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field4", Value: "value4"}})
			assert.NoError(suite.T(), err)
			streamid_5, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field5", Value: "value5"}})
			assert.NoError(suite.T(), err)

			_, err = client.XReadGroup(context.Background(), groupName, consumer2, map[string]string{key: ">"})
			assert.NoError(suite.T(), err)

			expectedSummary := models.XPendingSummary{
				NumOfMessages: 5,
				StartId:       models.CreateStringResult(streamid_1),
				EndId:         models.CreateStringResult(streamid_5),
				ConsumerMessages: []models.ConsumerPendingMessage{
					{ConsumerName: consumer1, MessageCount: 2},
					{ConsumerName: consumer2, MessageCount: 3},
				},
			}

			// 2. Act
			summaryResult, err := client.XPending(context.Background(), key, groupName)

			// 3a. Assert that we get 5 messages in total, 2 for consumer1 and 3 for consumer2
			assert.NoError(suite.T(), err)
			assert.True(
				suite.T(),
				reflect.DeepEqual(expectedSummary, summaryResult),
				"Expected and actual results do not match",
			)

			// 3b. Assert that we get 2 details for consumer1 that includes
			detailResult, _ := client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
			)
			assert.Equal(suite.T(), len(detailResult), 2)
			assert.Equal(suite.T(), streamid_1, detailResult[0].Id)
			assert.Equal(suite.T(), streamid_2, detailResult[1].Id)
		}

		execCluster := func(client interfaces.GlideClusterClientCommands) {
			// 1. Arrange the data
			key := uuid.New().String()
			groupName := "group" + uuid.New().String()
			zeroStreamId := "0"
			consumer1 := "consumer-1-" + uuid.New().String()
			consumer2 := "consumer-2-" + uuid.New().String()

			command := []string{"XGroup", "Create", key, groupName, zeroStreamId, "MKSTREAM"}

			resp, err := client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), "OK", resp.SingleValue().(string))

			command = []string{"XGroup", "CreateConsumer", key, groupName, consumer1}
			resp, err = client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), resp.SingleValue().(bool))

			command = []string{"XGroup", "CreateConsumer", key, groupName, consumer2}
			resp, err = client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), resp.SingleValue().(bool))

			streamid_1, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
			assert.NoError(suite.T(), err)
			streamid_2, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
			assert.NoError(suite.T(), err)

			_, err = client.XReadGroup(context.Background(), groupName, consumer1, map[string]string{key: ">"})
			assert.NoError(suite.T(), err)

			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field3", Value: "value3"}})
			assert.NoError(suite.T(), err)
			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field4", Value: "value4"}})
			assert.NoError(suite.T(), err)
			streamid_5, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field5", Value: "value5"}})
			assert.NoError(suite.T(), err)

			_, err = client.XReadGroup(context.Background(), groupName, consumer2, map[string]string{key: ">"})
			assert.NoError(suite.T(), err)

			expectedSummary := models.XPendingSummary{
				NumOfMessages: 5,
				StartId:       models.CreateStringResult(streamid_1),
				EndId:         models.CreateStringResult(streamid_5),
				ConsumerMessages: []models.ConsumerPendingMessage{
					{ConsumerName: consumer1, MessageCount: 2},
					{ConsumerName: consumer2, MessageCount: 3},
				},
			}

			// 2. Act
			summaryResult, err := client.XPending(context.Background(), key, groupName)

			// 3a. Assert that we get 5 messages in total, 2 for consumer1 and 3 for consumer2
			assert.NoError(suite.T(), err)
			assert.True(
				suite.T(),
				reflect.DeepEqual(expectedSummary, summaryResult),
				"Expected and actual results do not match",
			)

			// 3b. Assert that we get 2 details for consumer1 that includes
			detailResult, _ := client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10).SetConsumer(consumer1),
			)
			assert.Equal(suite.T(), len(detailResult), 2)
			assert.Equal(suite.T(), streamid_1, detailResult[0].Id)
			assert.Equal(suite.T(), streamid_2, detailResult[1].Id)

			//
		}

		assert.Equal(suite.T(), "OK", "OK")

		// create group and consumer for the group
		// this is only needed in order to be able to use custom commands.
		// Once the native commands are added, this logic will be refactored.
		switch c := client.(type) {
		case interfaces.GlideClientCommands:
			execStandalone(c)
		case interfaces.GlideClusterClientCommands:
			execCluster(c)
		}
	})
}

func (suite *GlideTestSuite) TestXPendingFailures() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// TODO: Update tests when XGroupCreate, XGroupCreateConsumer, XReadGroup, XClaim, XClaimJustId and XAck are added to
		// the Go client.
		//
		// This test splits out the cluster and standalone tests into their own functions because we are forced to use
		// CustomCommands for many stream commands which are not included in the preview Go client. Using a type switch for
		// each use of CustomCommand would make the tests difficult to read and maintain. These tests can be
		// collapsed once the native commands are added in a subsequent release.

		execStandalone := func(client interfaces.GlideClientCommands) {
			// 1. Arrange the data
			key := uuid.New().String()
			missingKey := uuid.New().String()
			nonStreamKey := uuid.New().String()
			groupName := "group" + uuid.New().String()
			zeroStreamId := "0"
			consumer1 := "consumer-1-" + uuid.New().String()
			invalidConsumer := "invalid-consumer-" + uuid.New().String()

			suite.verifyOK(
				client.XGroupCreateWithOptions(context.Background(),
					key,
					groupName,
					zeroStreamId,
					*options.NewXGroupCreateOptions().SetMakeStream(),
				),
			)

			command := []string{"XGroup", "CreateConsumer", key, groupName, consumer1}
			resp, err := client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), resp.(bool))

			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
			assert.NoError(suite.T(), err)
			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
			assert.NoError(suite.T(), err)

			// no pending messages yet...
			summaryResult, err := client.XPending(context.Background(), key, groupName)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), int64(0), summaryResult.NumOfMessages)

			detailResult, err := client.XPendingWithOptions(
				context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// read the entire stream for the consumer and mark messages as pending
			_, err = client.XReadGroup(context.Background(), groupName, consumer1, map[string]string{key: ">"})
			assert.NoError(suite.T(), err)

			// sanity check - expect some results:
			summaryResult, err = client.XPending(context.Background(), key, groupName)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), summaryResult.NumOfMessages > 0)

			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 1).SetConsumer(consumer1),
			)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), len(detailResult) > 0)

			// returns empty if + before -
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("+", "-", 10).SetConsumer(consumer1),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// min idletime of 100 seconds shouldn't produce any results
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10).SetMinIdleTime(100000),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// invalid consumer - no results
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10).SetConsumer(invalidConsumer),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// Return an error when range bound is not a valid ID
			_, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("invalid-id", "+", 10),
			)
			suite.Error(err)

			_, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "invalid-id", 10),
			)
			suite.Error(err)

			// invalid count should return no results
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", -1),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// Return an error when an invalid group is provided
			_, err = client.XPending(context.Background(),
				key,
				"invalid-group",
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "NOGROUP"))

			// non-existent key throws a RequestError (NOGROUP)
			_, err = client.XPending(context.Background(),
				missingKey,
				groupName,
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "NOGROUP"))

			_, err = client.XPendingWithOptions(context.Background(),
				missingKey,
				groupName,
				*options.NewXPendingOptions("-", "+", 10),
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "NOGROUP"))

			// Key exists, but it is not a stream
			_, _ = client.Set(context.Background(), nonStreamKey, "bar")
			_, err = client.XPending(context.Background(),
				nonStreamKey,
				groupName,
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "WRONGTYPE"))

			_, err = client.XPendingWithOptions(context.Background(),
				nonStreamKey,
				groupName,
				*options.NewXPendingOptions("-", "+", 10),
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "WRONGTYPE"))
		}

		execCluster := func(client interfaces.GlideClusterClientCommands) {
			// 1. Arrange the data
			key := uuid.New().String()
			missingKey := uuid.New().String()
			nonStreamKey := uuid.New().String()
			groupName := "group" + uuid.New().String()
			zeroStreamId := "0"
			consumer1 := "consumer-1-" + uuid.New().String()
			invalidConsumer := "invalid-consumer-" + uuid.New().String()

			suite.verifyOK(
				client.XGroupCreateWithOptions(context.Background(),
					key,
					groupName,
					zeroStreamId,
					*options.NewXGroupCreateOptions().SetMakeStream(),
				),
			)

			command := []string{"XGroup", "CreateConsumer", key, groupName, consumer1}
			resp, err := client.CustomCommand(context.Background(), command)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), resp.SingleValue().(bool))

			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
			assert.NoError(suite.T(), err)
			_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
			assert.NoError(suite.T(), err)

			// no pending messages yet...
			summaryResult, err := client.XPending(context.Background(), key, groupName)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), int64(0), summaryResult.NumOfMessages)

			detailResult, err := client.XPendingWithOptions(
				context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// read the entire stream for the consumer and mark messages as pending
			_, err = client.XReadGroup(context.Background(), groupName, consumer1, map[string]string{key: ">"})
			assert.NoError(suite.T(), err)

			// sanity check - expect some results:
			summaryResult, err = client.XPending(context.Background(), key, groupName)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), summaryResult.NumOfMessages > 0)

			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 1).SetConsumer(consumer1),
			)
			assert.NoError(suite.T(), err)
			assert.True(suite.T(), len(detailResult) > 0)

			// returns empty if + before -
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("+", "-", 10).SetConsumer(consumer1),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// min idletime of 100 seconds shouldn't produce any results
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10).SetMinIdleTime(100000),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// invalid consumer - no results
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", 10).SetConsumer(invalidConsumer),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// Return an error when range bound is not a valid ID
			_, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("invalid-id", "+", 10),
			)
			suite.Error(err)

			_, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "invalid-id", 10),
			)
			suite.Error(err)

			// invalid count should return no results
			detailResult, err = client.XPendingWithOptions(context.Background(),
				key,
				groupName,
				*options.NewXPendingOptions("-", "+", -1),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), 0, len(detailResult))

			// Return an error when an invalid group is provided
			_, err = client.XPending(context.Background(),
				key,
				"invalid-group",
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "NOGROUP"))

			// non-existent key throws a RequestError (NOGROUP)
			_, err = client.XPending(context.Background(),
				missingKey,
				groupName,
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "NOGROUP"))

			_, err = client.XPendingWithOptions(context.Background(),
				missingKey,
				groupName,
				*options.NewXPendingOptions("-", "+", 10),
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "NOGROUP"))

			// Key exists, but it is not a stream
			_, _ = client.Set(context.Background(), nonStreamKey, "bar")
			_, err = client.XPending(context.Background(),
				nonStreamKey,
				groupName,
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "WRONGTYPE"))

			_, err = client.XPendingWithOptions(context.Background(),
				nonStreamKey,
				groupName,
				*options.NewXPendingOptions("-", "+", 10),
			)
			suite.Error(err)
			suite.True(strings.Contains(err.Error(), "WRONGTYPE"))
		}

		assert.Equal(suite.T(), "OK", "OK")

		// create group and consumer for the group
		// this is only needed in order to be able to use custom commands.
		// Once the native commands are added, this logic will be refactored.
		switch c := client.(type) {
		case interfaces.GlideClientCommands:
			execStandalone(c)
		case interfaces.GlideClusterClientCommands:
			execCluster(c)
		}
	})
}

func (suite *GlideTestSuite) TestXGroupCreate_XGroupDestroy() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		group := uuid.NewString()
		id := "0-1"

		// Stream not created results in error
		_, err := client.XGroupCreate(context.Background(), key, group, id)
		suite.Error(err)

		// Stream with option to create creates stream & Group
		opts := options.NewXGroupCreateOptions().SetMakeStream()
		suite.verifyOK(client.XGroupCreateWithOptions(context.Background(), key, group, id, *opts))

		// ...and again results in BUSYGROUP error, because group names must be unique
		_, err = client.XGroupCreate(context.Background(), key, group, id)
		suite.Error(err)
		suite.True(strings.Contains(err.Error(), "BUSYGROUP"))

		// Stream Group can be destroyed returns: true
		destroyed, err := client.XGroupDestroy(context.Background(), key, group)
		suite.NoError(err)
		suite.True(destroyed)

		// ...and again results in: false
		destroyed, err = client.XGroupDestroy(context.Background(), key, group)
		suite.NoError(err)
		suite.False(destroyed)

		// ENTRIESREAD option was added in valkey 7.0.0
		opts = options.NewXGroupCreateOptions().SetEntriesRead(100)
		if suite.serverVersion >= "7.0.0" {
			suite.verifyOK(client.XGroupCreateWithOptions(context.Background(), key, group, id, *opts))
		} else {
			_, err = client.XGroupCreateWithOptions(context.Background(), key, group, id, *opts)
			suite.Error(err)
		}

		// key is not a stream
		key = uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key, id))
		_, err = client.XGroupCreate(context.Background(), key, group, id)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestObjectEncoding() {
	suite.T().Skip("Skip until test is fixed")

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1: Check object encoding for embstr
		// We can't use UUID for a key here because of a behavior change with long keys in Valkey 8.1
		// see https://github.com/valkey-io/valkey/issues/2026
		key := "testKey"
		value1 := "Hello"
		t := suite.T()
		suite.verifyOK(client.Set(context.Background(), key, value1))
		resultObjectEncoding, err := client.ObjectEncoding(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, "embstr", resultObjectEncoding.Value(), "The result should be embstr")

		// Test 2: Check object encoding command for non existing key
		key2 := "{keyName}" + uuid.NewString()
		resultDumpNull, err := client.ObjectEncoding(context.Background(), key2)
		assert.Nil(t, err)
		assert.Equal(t, "", resultDumpNull.Value())
	})
}

func (suite *GlideTestSuite) TestDumpRestore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Test 1: Check restore command for deleted key and check value
		key := "testKey1_" + uuid.New().String()
		value := "hello"
		t := suite.T()
		suite.verifyOK(client.Set(context.Background(), key, value))
		resultDump, err := client.Dump(context.Background(), key)
		assert.Nil(t, err)
		assert.NotNil(t, resultDump)
		deletedCount, err := client.Del(context.Background(), []string{key})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), deletedCount)
		result_test1, err := client.Restore(context.Background(), key, 0, resultDump.Value())
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result_test1)
		resultGetRestoreKey, err := client.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGetRestoreKey.Value())

		// Test 2: Check dump command for non existing key
		key1 := "{keyName}" + uuid.NewString()
		resultDumpNull, err := client.Dump(context.Background(), key1)
		assert.Nil(t, err)
		assert.Equal(t, "", resultDumpNull.Value())
	})
}

func (suite *GlideTestSuite) TestRestoreWithOptions() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "testKey1_" + uuid.New().String()
		value := "hello"
		t := suite.T()
		suite.verifyOK(client.Set(context.Background(), key, value))

		resultDump, err := client.Dump(context.Background(), key)
		assert.Nil(t, err)
		assert.NotNil(t, resultDump)

		// Test 1: Check restore command with restoreOptions REPLACE modifier
		deletedCount, err := client.Del(context.Background(), []string{key})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), deletedCount)
		optsReplace := options.NewRestoreOptions().SetReplace()
		result_test1, err := client.RestoreWithOptions(context.Background(), key, 0, resultDump.Value(), *optsReplace)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result_test1)
		resultGetRestoreKey, err := client.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGetRestoreKey.Value())

		// Test 2: Check restore command with restoreOptions ABSTTL modifier
		delete_test2, err := client.Del(context.Background(), []string{key})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), delete_test2)
		opts_test2 := options.NewRestoreOptions().SetABSTTL()
		result_test2, err := client.RestoreWithOptions(context.Background(), key, 0, resultDump.Value(), *opts_test2)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result_test2)
		resultGet_test2, err := client.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet_test2.Value())

		// Test 3: Check restore command with restoreOptions FREQ modifier
		delete_test3, err := client.Del(context.Background(), []string{key})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), delete_test3)
		opts_test3 := options.NewRestoreOptions().SetEviction(constants.FREQ, 10)
		result_test3, err := client.RestoreWithOptions(context.Background(), key, 0, resultDump.Value(), *opts_test3)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result_test3)
		resultGet_test3, err := client.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet_test3.Value())

		// Test 4: Check restore command with restoreOptions IDLETIME modifier
		delete_test4, err := client.Del(context.Background(), []string{key})
		assert.Nil(t, err)
		assert.Equal(t, int64(1), delete_test4)
		opts_test4 := options.NewRestoreOptions().SetEviction(constants.IDLETIME, 10)
		result_test4, err := client.RestoreWithOptions(context.Background(), key, 0, resultDump.Value(), *opts_test4)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", result_test4)
		resultGet_test4, err := client.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet_test4.Value())
	})
}

func (suite *GlideTestSuite) TestZRemRangeByRank() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		stringKey := uuid.New().String()
		membersScores := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		zAddResult, err := client.ZAdd(context.Background(), key1, membersScores)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zAddResult)

		// Incorrect range start > stop
		zRemRangeByRankResult, err := client.ZRemRangeByRank(context.Background(), key1, 2, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zRemRangeByRankResult)

		// Remove first two members
		zRemRangeByRankResult, err = client.ZRemRangeByRank(context.Background(), key1, 0, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), zRemRangeByRankResult)

		// Remove all members
		zRemRangeByRankResult, err = client.ZRemRangeByRank(context.Background(), key1, 0, 10)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), zRemRangeByRankResult)

		zRangeWithScoresResult, err := client.ZRangeWithScores(context.Background(), key1, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 0, len(zRangeWithScoresResult))

		// Non-existing key
		zRemRangeByRankResult, err = client.ZRemRangeByRank(context.Background(), "non_existing_key", 0, 10)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zRemRangeByRankResult)

		// Key exists, but it is not a set
		setResult, err := client.Set(context.Background(), stringKey, "test")
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", setResult)

		_, err = client.ZRemRangeByRank(context.Background(), stringKey, 0, 10)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZRemRangeByLex() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		stringKey := uuid.New().String()

		// Add members to the set
		zAddResult, err := client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0, "d": 4.0})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(4), zAddResult)

		// min > max
		zRemRangeByLexResult, err := client.ZRemRangeByLex(context.Background(),
			key1,
			*options.NewRangeByLexQuery(options.NewLexBoundary("d", false), options.NewLexBoundary("a", false)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zRemRangeByLexResult)

		// Remove members with lexicographical range
		zRemRangeByLexResult, err = client.ZRemRangeByLex(context.Background(),
			key1,
			*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", true)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), zRemRangeByLexResult)

		zRemRangeByLexResult, err = client.ZRemRangeByLex(
			context.Background(),
			key1,
			*options.NewRangeByLexQuery(options.NewLexBoundary("d", true), options.NewInfiniteLexBoundary(constants.PositiveInfinity)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), zRemRangeByLexResult)

		// Non-existing key
		zRemRangeByLexResult, err = client.ZRemRangeByLex(context.Background(),
			"non_existing_key",
			*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", false)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zRemRangeByLexResult)

		// Key exists, but it is not a set
		setResult, err := client.Set(context.Background(), stringKey, "test")
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", setResult)

		_, err = client.ZRemRangeByLex(context.Background(),
			stringKey,
			*options.NewRangeByLexQuery(options.NewLexBoundary("a", false), options.NewLexBoundary("c", false)),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZRemRangeByScore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()
		stringKey := uuid.New().String()

		// Add members to the set
		zAddResult, err := client.ZAdd(
			context.Background(),
			key1,
			map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0, "four": 4.0},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(4), zAddResult)

		// min > max
		zRemRangeByScoreResult, err := client.ZRemRangeByScore(context.Background(),
			key1,
			*options.NewRangeByScoreQuery(options.NewScoreBoundary(2.0, false), options.NewScoreBoundary(1.0, false)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zRemRangeByScoreResult)

		// Remove members with score range
		zRemRangeByScoreResult, err = client.ZRemRangeByScore(context.Background(),
			key1,
			*options.NewRangeByScoreQuery(options.NewScoreBoundary(1.0, false), options.NewScoreBoundary(3.0, true)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), zRemRangeByScoreResult)

		// Remove all members
		zRemRangeByScoreResult, err = client.ZRemRangeByScore(context.Background(),
			key1,
			*options.NewRangeByScoreQuery(options.NewScoreBoundary(1.0, false), options.NewScoreBoundary(10.0, true)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), zRemRangeByScoreResult)

		// Non-existing key
		zRemRangeByScoreResult, err = client.ZRemRangeByScore(context.Background(),
			"non_existing_key",
			*options.NewRangeByScoreQuery(options.NewScoreBoundary(1.0, false), options.NewScoreBoundary(10.0, true)),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zRemRangeByScoreResult)

		// Key exists, but it is not a set
		setResult, err := client.Set(context.Background(), stringKey, "test")
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", setResult)

		_, err = client.ZRemRangeByScore(context.Background(),
			stringKey,
			*options.NewRangeByScoreQuery(options.NewScoreBoundary(1.0, false), options.NewScoreBoundary(10.0, true)),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZMScore() {
	suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()

		zAddResult, err := client.ZAdd(context.Background(), key, map[string]float64{"one": 1.0, "two": 2.0, "three": 3.0})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zAddResult)

		res, err := client.ZMScore(context.Background(), key, []string{"one", "three", "two"})
		expected := []models.Result[float64]{
			models.CreateFloat64Result(1),
			models.CreateFloat64Result(3),
			models.CreateFloat64Result(2),
		}
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), expected, res)

		// not existing members
		res, err = client.ZMScore(context.Background(), key, []string{"nonExistingMember", "two", "nonExistingMember"})
		expected = []models.Result[float64]{
			models.CreateNilFloat64Result(),
			models.CreateFloat64Result(2),
			models.CreateNilFloat64Result(),
		}
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), expected, res)

		// not existing key
		res, err = client.ZMScore(context.Background(), uuid.NewString(), []string{"one", "three", "two"})
		expected = []models.Result[float64]{
			models.CreateNilFloat64Result(),
			models.CreateNilFloat64Result(),
			models.CreateNilFloat64Result(),
		}
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), expected, res)

		// invalid arg - member list must not be empty
		_, err = client.ZMScore(context.Background(), key, []string{})
		suite.Error(err)

		// key exists, but it is not a sorted set
		key2 := uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key2, "ZMScore"))
		_, err = client.ZMScore(context.Background(), key2, []string{"one"})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZRandMember() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		t := suite.T()
		key1 := uuid.NewString()
		key2 := uuid.NewString()
		members := []string{"one", "two"}

		zadd, err := client.ZAdd(context.Background(), key1, map[string]float64{"one": 1.0, "two": 2.0})
		assert.NoError(t, err)
		assert.Equal(t, int64(2), zadd)

		randomMember, err := client.ZRandMember(context.Background(), key1)
		assert.NoError(t, err)
		assert.Contains(t, members, randomMember.Value())

		// unique values are expected as count is positive
		randomMembers, err := client.ZRandMemberWithCount(context.Background(), key1, 4)
		assert.NoError(t, err)
		assert.ElementsMatch(t, members, randomMembers)

		membersAndScores, err := client.ZRandMemberWithCountWithScores(context.Background(), key1, 4)
		expectedMembersAndScores := []models.MemberAndScore{{Member: "one", Score: 1}, {Member: "two", Score: 2}}
		assert.NoError(t, err)
		assert.ElementsMatch(t, expectedMembersAndScores, membersAndScores)

		// Duplicate values are expected as count is negative
		randomMembers, err = client.ZRandMemberWithCount(context.Background(), key1, -4)
		assert.NoError(t, err)
		assert.Len(t, randomMembers, 4)
		for _, member := range randomMembers {
			assert.Contains(t, members, member)
		}

		membersAndScores, err = client.ZRandMemberWithCountWithScores(context.Background(), key1, -4)
		assert.NoError(t, err)
		assert.Len(t, membersAndScores, 4)
		for _, MemberAndScore := range membersAndScores {
			assert.Contains(t, expectedMembersAndScores, MemberAndScore)
		}

		// non existing key should return null or empty array
		randomMember, err = client.ZRandMember(context.Background(), key2)
		assert.NoError(t, err)
		assert.True(t, randomMember.IsNil())
		randomMembers, err = client.ZRandMemberWithCount(context.Background(), key2, -4)
		assert.NoError(t, err)
		assert.Len(t, randomMembers, 0)
		membersAndScores, err = client.ZRandMemberWithCountWithScores(context.Background(), key2, -4)
		assert.NoError(t, err)
		assert.Len(t, membersAndScores, 0)

		// Key exists, but is not a set
		suite.verifyOK(client.Set(context.Background(), key2, "ZRandMember"))
		_, err = client.ZRandMember(context.Background(), key2)
		suite.Error(err)
		_, err = client.ZRandMemberWithCount(context.Background(), key2, 2)
		suite.Error(err)
		_, err = client.ZRandMemberWithCountWithScores(context.Background(), key2, 2)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestObjectIdleTime() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		defaultClient := suite.defaultClient()
		key := "testKey1_" + uuid.New().String()
		value := "hello"
		sleepSec := int64(5)
		t := suite.T()
		suite.verifyOK(defaultClient.Set(context.Background(), key, value))
		keyValueMap := map[string]string{
			"maxmemory-policy": "noeviction",
		}
		suite.verifyOK(defaultClient.ConfigSet(context.Background(), keyValueMap))
		resultConfig, err := defaultClient.ConfigGet(context.Background(), []string{"maxmemory-policy"})
		assert.Nil(t, err, "Failed to get configuration")
		assert.Equal(t, keyValueMap, resultConfig, "Configuration mismatch for maxmemory-policy")
		resultGet, err := defaultClient.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet.Value())
		time.Sleep(time.Duration(sleepSec) * time.Second)
		resultIdleTime, err := defaultClient.ObjectIdleTime(context.Background(), key)
		assert.Nil(t, err)
		assert.GreaterOrEqual(t, resultIdleTime.Value(), sleepSec-1)
	})
}

func (suite *GlideTestSuite) TestObjectRefCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "testKey1_" + uuid.New().String()
		value := "hello"
		t := suite.T()
		suite.verifyOK(client.Set(context.Background(), key, value))
		resultGetRestoreKey, err := client.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGetRestoreKey.Value())
		resultObjectRefCount, err := client.ObjectRefCount(context.Background(), key)
		assert.Nil(t, err)
		assert.GreaterOrEqual(t, resultObjectRefCount.Value(), int64(1))
	})
}

func (suite *GlideTestSuite) TestObjectFreq() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		defaultClient := suite.defaultClient()
		key := "testKey1_" + uuid.New().String()
		value := "hello"
		t := suite.T()
		suite.verifyOK(defaultClient.Set(context.Background(), key, value))
		keyValueMap := map[string]string{
			"maxmemory-policy": "volatile-lfu",
		}
		suite.verifyOK(defaultClient.ConfigSet(context.Background(), keyValueMap))
		resultConfig, err := defaultClient.ConfigGet(context.Background(), []string{"maxmemory-policy"})
		assert.Nil(t, err, "Failed to get configuration")
		assert.Equal(t, keyValueMap, resultConfig, "Configuration mismatch for maxmemory-policy")
		sleepSec := int64(5)
		time.Sleep(time.Duration(sleepSec) * time.Second)
		resultGet, err := defaultClient.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet.Value())
		resultGet2, err := defaultClient.Get(context.Background(), key)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet2.Value())
		resultObjFreq, err := defaultClient.ObjectFreq(context.Background(), key)
		assert.Nil(t, err)
		assert.GreaterOrEqual(t, resultObjFreq.Value(), int64(2))
	})
}

func (suite *GlideTestSuite) TestSortWithOptions_ExternalWeights() {
	suite.SkipIfServerVersionLowerThan("8.1.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}-1"
		client.LPush(context.Background(), key, []string{"item1", "item2", "item3"})

		client.Set(context.Background(), "{key}weight_item1", "3")
		client.Set(context.Background(), "{key}weight_item2", "1")
		client.Set(context.Background(), "{key}weight_item3", "2")

		options := options.NewSortOptions().
			SetByPattern("{key}weight_*").
			SetOrderBy(options.ASC).
			SetIsAlpha(false)

		sortResult, err := client.SortWithOptions(context.Background(), key, *options)

		suite.NoError(err)
		resultList := []models.Result[string]{
			models.CreateStringResult("item2"),
			models.CreateStringResult("item3"),
			models.CreateStringResult("item1"),
		}

		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestSortWithOptions_GetPatterns() {
	suite.SkipIfServerVersionLowerThan("8.1.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}-1"
		client.LPush(context.Background(), key, []string{"item1", "item2", "item3"})

		client.Set(context.Background(), "{key}object_item1", "Object_1")
		client.Set(context.Background(), "{key}object_item2", "Object_2")
		client.Set(context.Background(), "{key}object_item3", "Object_3")

		options := options.NewSortOptions().
			SetByPattern("{key}weight_*").
			SetOrderBy(options.ASC).
			SetIsAlpha(false).
			AddGetPattern("{key}object_*")

		sortResult, err := client.SortWithOptions(context.Background(), key, *options)

		suite.NoError(err)

		resultList := []models.Result[string]{
			models.CreateStringResult("Object_1"),
			models.CreateStringResult("Object_2"),
			models.CreateStringResult("Object_3"),
		}

		assert.Equal(suite.T(), resultList, sortResult)
	})
}

func (suite *GlideTestSuite) TestSortWithOptions_SuccessfulSortByWeightAndGet() {
	suite.SkipIfServerVersionLowerThan("8.1.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}-1"
		client.LPush(context.Background(), key, []string{"item1", "item2", "item3"})

		client.Set(context.Background(), "{key}weight_item1", "10")
		client.Set(context.Background(), "{key}weight_item2", "5")
		client.Set(context.Background(), "{key}weight_item3", "15")

		client.Set(context.Background(), "{key}object_item1", "Object 1")
		client.Set(context.Background(), "{key}object_item2", "Object 2")
		client.Set(context.Background(), "{key}object_item3", "Object 3")

		options := options.NewSortOptions().
			SetOrderBy(options.ASC).
			SetIsAlpha(false).
			SetByPattern("{key}weight_*").
			AddGetPattern("{key}object_*").
			AddGetPattern("#")

		sortResult, err := client.SortWithOptions(context.Background(), key, *options)

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

		objectItem2, err := client.Get(context.Background(), "{key}object_item2")
		suite.NoError(err)
		assert.Equal(suite.T(), "Object 2", objectItem2.Value())

		objectItem1, err := client.Get(context.Background(), "{key}object_item1")
		suite.NoError(err)
		assert.Equal(suite.T(), "Object 1", objectItem1.Value())

		objectItem3, err := client.Get(context.Background(), "{key}object_item3")
		suite.NoError(err)
		assert.Equal(suite.T(), "Object 3", objectItem3.Value())

		assert.Equal(suite.T(), "item2", sortResult[1].Value())
		assert.Equal(suite.T(), "item1", sortResult[3].Value())
		assert.Equal(suite.T(), "item3", sortResult[5].Value())
	})
}

func (suite *GlideTestSuite) TestSortStoreWithOptions_ByPattern() {
	suite.SkipIfServerVersionLowerThan("8.1.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{listKey}" + uuid.New().String()
		sortedKey := "{listKey}" + uuid.New().String()
		client.LPush(context.Background(), key, []string{"a", "b", "c", "d", "e"})
		client.Set(context.Background(), "{listKey}weight_a", "5")
		client.Set(context.Background(), "{listKey}weight_b", "2")
		client.Set(context.Background(), "{listKey}weight_c", "3")
		client.Set(context.Background(), "{listKey}weight_d", "1")
		client.Set(context.Background(), "{listKey}weight_e", "4")

		options := options.NewSortOptions().SetByPattern("{listKey}weight_*")

		result, err := client.SortStoreWithOptions(context.Background(), key, sortedKey, *options)

		suite.NoError(err)
		assert.NotNil(suite.T(), result)
		assert.Equal(suite.T(), int64(5), result)

		sortedValues, err := client.LRange(context.Background(), sortedKey, 0, -1)
		suite.NoError(err)
		assert.Equal(suite.T(), []string{"d", "b", "c", "e", "a"}, sortedValues)
	})
}

func (suite *GlideTestSuite) TestXGroupStreamCommands() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		stringKey := uuid.New().String()
		groupName := "group" + uuid.New().String()
		zeroStreamId := "0"
		consumerName := "consumer-" + uuid.New().String()

		sendWithCustomCommand(
			suite,
			client,
			[]string{"xgroup", "create", key, groupName, zeroStreamId, "MKSTREAM"},
			"Can't send XGROUP CREATE as a custom command",
		)
		respBool, err := client.XGroupCreateConsumer(context.Background(), key, groupName, consumerName)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), respBool)

		// create a consumer for a group that doesn't exist should result in a NOGROUP error
		_, err = client.XGroupCreateConsumer(context.Background(), key, "non-existent-group", consumerName)
		suite.Error(err)
		suite.True(strings.Contains(err.Error(), "NOGROUP"))

		// create consumer that already exists should return false
		respBool, err = client.XGroupCreateConsumer(context.Background(), key, groupName, consumerName)
		suite.NoError(err)
		suite.False(respBool)

		// Delete a consumer that hasn't been created should return 0
		respInt64, err := client.XGroupDelConsumer(context.Background(), key, groupName, "non-existent-consumer")
		suite.NoError(err)
		suite.Equal(int64(0), respInt64)

		// Add two stream entries
		streamId1, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
		suite.NoError(err)
		streamId2, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
		suite.NoError(err)

		// read the stream for the consumer and mark messages as pending
		actualGroup, err := client.XReadGroup(context.Background(), groupName, consumerName, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(actualGroup))
		streamResponse, exists := actualGroup[key]
		assert.True(suite.T(), exists)

		// Check that we have two entries with the correct IDs and fields
		assert.Equal(suite.T(), 2, len(streamResponse.Entries))

		// Create a map of entry IDs to their fields for easier comparison
		entryMap := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// Verify entries
		assert.Contains(suite.T(), entryMap, streamId1)
		// Check for field "field1" with value "value1" in entry
		assert.Equal(suite.T(), entryMap[streamId1], []models.FieldValue{{Field: "field1", Value: "value1"}})

		assert.Contains(suite.T(), entryMap, streamId2)
		// Check for field "field2" with value "value2" in entry
		assert.Equal(suite.T(), entryMap[streamId2], []models.FieldValue{{Field: "field2", Value: "value2"}})

		// delete one of the streams using XDel
		respInt64, err = client.XDel(context.Background(), key, []string{streamId1})
		suite.NoError(err)
		suite.Equal(int64(1), respInt64)

		// xreadgroup should return one empty stream and one non-empty stream
		resp, err := client.XReadGroup(context.Background(), groupName, consumerName, map[string]string{key: zeroStreamId})
		suite.NoError(err)

		// Check that we have one stream
		assert.Equal(suite.T(), 1, len(resp))
		streamResponse, exists = resp[key]
		assert.True(suite.T(), exists)

		// Check entries
		entryMap = make(map[string][]models.FieldValue)
		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// Verify entries
		assert.Contains(suite.T(), entryMap, streamId1)
		assert.Empty(suite.T(), entryMap[streamId1])

		assert.Contains(suite.T(), entryMap, streamId2)

		// Check for field "field2" with value "value2" in entry
		assert.Equal(suite.T(), entryMap[streamId2], []models.FieldValue{{Field: "field2", Value: "value2"}})

		// add a new stream entry
		streamId3, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field3", Value: "value3"}})
		suite.NoError(err)
		suite.NotNil(streamId3)

		// xack that streamid1 and streamid2 have been processed
		xackResult, err := client.XAck(context.Background(), key, groupName, []string{streamId1, streamId2})
		suite.NoError(err)
		suite.Equal(int64(2), xackResult)

		// Delete the consumer group and expect 0 pending messages
		respInt64, err = client.XGroupDelConsumer(context.Background(), key, groupName, consumerName)
		suite.NoError(err)
		suite.Equal(int64(0), respInt64)

		// xack streamid_1, and streamid_2 already received returns 0L
		xackResult, err = client.XAck(context.Background(), key, groupName, []string{streamId1})
		suite.NoError(err)
		suite.Equal(int64(0), xackResult)

		// Consume the last message with the previously deleted consumer (creates the consumer anew)
		resp, err = client.XReadGroup(context.Background(), groupName, consumerName, map[string]string{key: ">"})
		suite.NoError(err)

		// Check that we have one stream with entries
		assert.Equal(suite.T(), 1, len(resp))
		streamResponse, exists = resp[key]
		assert.True(suite.T(), exists)
		assert.Equal(suite.T(), 1, len(streamResponse.Entries))
		assert.Equal(suite.T(), streamId3, streamResponse.Entries[0].ID)

		// Check for field "field3" with value "value3" in entry
		assert.Equal(suite.T(), []models.FieldValue{{Field: "field3", Value: "value3"}}, streamResponse.Entries[0].Fields)

		// Use non existent group, so xack streamid_3 returns 0
		xackResult, err = client.XAck(context.Background(), key, "non-existent-group", []string{streamId3})
		suite.NoError(err)
		suite.Equal(int64(0), xackResult)

		// Delete the consumer group and expect 1 pending message
		respInt64, err = client.XGroupDelConsumer(context.Background(), key, groupName, consumerName)
		suite.NoError(err)
		suite.Equal(int64(1), respInt64)

		// Set a string key, and expect an error when you try to create or delete a consumer group
		_, err = client.Set(context.Background(), stringKey, "test")
		suite.NoError(err)
		_, err = client.XGroupCreateConsumer(context.Background(), stringKey, groupName, consumerName)
		suite.Error(err)

		_, err = client.XGroupDelConsumer(context.Background(), stringKey, groupName, consumerName)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestXInfoStream() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		group := uuid.NewString()
		consumer := uuid.NewString()

		xadd, err := client.XAddWithOptions(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "a", Value: "b"}, {Field: "c", Value: "d"}},
			*options.NewXAddOptions().SetId("1-0"),
		)
		suite.NoError(err)
		assert.Equal(suite.T(), "1-0", xadd.Value())

		suite.verifyOK(client.XGroupCreate(context.Background(), key, group, "0-0"))

		_, err = client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		infoSmall, err := client.XInfoStream(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), infoSmall.Length)
		assert.Equal(suite.T(), int64(1), infoSmall.Groups)

		// Check the first entry
		assert.Equal(suite.T(), "1-0", infoSmall.FirstEntry.ID)

		// Check fields in the first entry
		assert.Equal(
			suite.T(),
			infoSmall.FirstEntry.Fields,
			[]models.FieldValue{{Field: "a", Value: "b"}, {Field: "c", Value: "d"}},
		)

		// Check the last entry
		assert.Equal(suite.T(), "1-0", infoSmall.LastEntry.ID)

		// Check fields in the last entry
		assert.Equal(
			suite.T(),
			infoSmall.LastEntry.Fields,
			[]models.FieldValue{{Field: "a", Value: "b"}, {Field: "c", Value: "d"}},
		)

		xadd, err = client.XAddWithOptions(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "e", Value: "f"}},
			*options.NewXAddOptions().SetId("1-1"),
		)
		suite.NoError(err)
		assert.Equal(suite.T(), "1-1", xadd.Value())

		infoFull, _ := client.XInfoStreamFullWithOptions(
			context.Background(),
			key,
			*options.NewXInfoStreamOptions().SetCount(1),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), infoFull.Length)
		if suite.serverVersion >= "7.0.0" {
			assert.Equal(suite.T(), "1-0", infoFull.RecordedFirstEntryId.Value())
		} else {
			assert.True(suite.T(), infoFull.RecordedFirstEntryId.IsNil())
			assert.True(suite.T(), infoFull.MaxDeletedEntryID.IsNil())
			assert.True(suite.T(), infoFull.EntriesAdded.IsNil())
			assert.True(suite.T(), infoFull.Groups[0].EntriesRead.IsNil())
			assert.True(suite.T(), infoFull.Groups[0].Lag.IsNil())
		}
		// first group
		assert.Equal(suite.T(), len(infoFull.Groups), 1)
		groupItem := infoFull.Groups[0]
		if suite.serverVersion >= "7.0.0" {
			assert.Equal(suite.T(), groupItem.EntriesRead.Value(), int64(1))
			assert.Equal(suite.T(), groupItem.Lag.Value(), int64(1))
		}
		assert.Equal(suite.T(), groupItem.LastDeliveredId, "1-0")
		assert.Equal(suite.T(), groupItem.Name, group)
		assert.Equal(suite.T(), len(groupItem.Pending), int(1))

		// first consumer of first group
		cns := infoFull.Groups[0].Consumers[0]
		assert.NotNil(suite.T(), cns.SeenTime)
		assert.Equal(suite.T(), cns.Name, consumer)
		assert.Equal(suite.T(), cns.PelCount, int64(1))
		assert.Equal(suite.T(), len(cns.Pending), int(1))
		assert.Equal(suite.T(), cns.Pending[0].Id, "1-0")
		assert.Equal(suite.T(), cns.Pending[0].DeliveredCount, int64(1))
		if suite.serverVersion >= "7.2.0" {
			assert.False(suite.T(), cns.ActiveTime.IsNil())
		} else {
			assert.True(suite.T(), cns.ActiveTime.IsNil())
		}
	})
}

func (suite *GlideTestSuite) TestXInfoConsumers() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		group := uuid.NewString()
		consumer1 := uuid.NewString()
		consumer2 := uuid.NewString()

		xadd, err := client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "e1_f1", Value: "e1_v1"}, {Field: "e1_f2", Value: "e1_v2"}},
			*options.NewXAddOptions().SetId("0-1"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-1", xadd.Value())
		xadd, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "e2_f1", Value: "e2_v1"}, {Field: "e2_f2", Value: "e2_v2"}},
			*options.NewXAddOptions().SetId("0-2"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-2", xadd.Value())
		xadd, err = client.XAddWithOptions(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "e3_f1", Value: "e3_v1"}},
			*options.NewXAddOptions().SetId("0-3"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-3", xadd.Value())

		suite.verifyOK(client.XGroupCreate(context.Background(), key, group, "0-0"))

		xReadGroup, err := client.XReadGroupWithOptions(context.Background(),
			group,
			consumer1,
			map[string]string{key: ">"},
			*options.NewXReadGroupOptions().SetCount(1),
		)
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xReadGroup))
		streamResponse, exists := xReadGroup[key]
		assert.True(suite.T(), exists)

		// Check that we have one entry with the correct ID and fields
		assert.Equal(suite.T(), 1, len(streamResponse.Entries))
		assert.Equal(suite.T(), "0-1", streamResponse.Entries[0].ID)

		// Check for fields in the entry
		assert.Equal(
			suite.T(),
			streamResponse.Entries[0].Fields,
			[]models.FieldValue{{Field: "e1_f1", Value: "e1_v1"}, {Field: "e1_f2", Value: "e1_v2"}},
		)

		// Sleep to ensure the idle time value and inactive time value returned by xinfo_consumers is > 0
		time.Sleep(2000 * time.Millisecond)
		info, err := client.XInfoConsumers(context.Background(), key, group)
		assert.NoError(suite.T(), err)
		assert.Len(suite.T(), info, 1)
		assert.Equal(suite.T(), consumer1, info[0].Name)
		assert.Equal(suite.T(), int64(1), info[0].Pending)
		assert.Greater(suite.T(), info[0].Idle, int64(0))
		if suite.serverVersion > "7.2.0" {
			assert.False(suite.T(), info[0].Inactive.IsNil())
			assert.Greater(suite.T(), info[0].Inactive.Value(), int64(0))
		} else {
			assert.True(suite.T(), info[0].Inactive.IsNil())
		}

		respBool, err := client.XGroupCreateConsumer(context.Background(), key, group, consumer2)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), respBool)

		xReadGroup, err = client.XReadGroup(context.Background(), group, consumer2, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xReadGroup))
		streamResponse, exists = xReadGroup[key]
		assert.True(suite.T(), exists)

		// Check that we have two entries with the correct IDs and fields
		assert.Equal(suite.T(), 2, len(streamResponse.Entries))

		// Create a map of entry IDs to their fields for easier comparison
		entryMap := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// Verify entries
		assert.Contains(suite.T(), entryMap, "0-2")
		assert.Contains(suite.T(), entryMap, "0-3")
		sort.Slice(streamResponse.Entries, func(i, j int) bool {
			return streamResponse.Entries[i].ID < streamResponse.Entries[j].ID
		})
		// Check for fields
		assert.Equal(
			suite.T(),
			streamResponse,
			models.StreamResponse{
				Entries: []models.StreamEntry{
					{
						ID: "0-2",
						Fields: []models.FieldValue{
							{Field: "e2_f1", Value: "e2_v1"},
							{Field: "e2_f2", Value: "e2_v2"},
						},
					},
					{
						ID: "0-3",
						Fields: []models.FieldValue{
							{Field: "e3_f1", Value: "e3_v1"},
						},
					},
				},
			},
		)

		// Verify that xinfo_consumers contains info for 2 consumers now
		info, err = client.XInfoConsumers(context.Background(), key, group)
		assert.NoError(suite.T(), err)
		assert.Len(suite.T(), info, 2)

		// Passing a non-existing key raises an error
		key = uuid.NewString()
		_, err = client.XInfoConsumers(context.Background(), key, "_")
		suite.Error(err)

		// key exists, but it is not a stream
		suite.verifyOK(client.Set(context.Background(), key, key))
		_, err = client.XInfoConsumers(context.Background(), key, "_")
		suite.Error(err)

		// Passing a non-existing group raises an error
		key = uuid.NewString()
		_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "a", Value: "b"}})
		suite.NoError(err)
		_, err = client.XInfoConsumers(context.Background(), key, "_")
		suite.Error(err)

		// no consumers yet
		suite.verifyOK(client.XGroupCreate(context.Background(), key, group, "0-0"))
		info, err = client.XInfoConsumers(context.Background(), key, group)
		suite.NoError(err)
		suite.Empty(info)
	})
}

func (suite *GlideTestSuite) TestXInfoGroups() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.NewString()
		group := uuid.NewString()
		consumer := uuid.NewString()

		suite.verifyOK(
			client.XGroupCreateWithOptions(
				context.Background(),
				key,
				group,
				"0-0",
				*options.NewXGroupCreateOptions().SetMakeStream(),
			),
		)

		// one empty group exists
		xinfo, err := client.XInfoGroups(context.Background(), key)
		suite.NoError(err)
		if suite.serverVersion < "7.0.0" {
			suite.Equal([]models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       0,
					Pending:         0,
					LastDeliveredId: "0-0",
					EntriesRead:     models.CreateNilInt64Result(),
					Lag:             models.CreateNilInt64Result(),
				},
			}, xinfo)
		} else {
			assert.Equal(suite.T(), []models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       0,
					Pending:         0,
					LastDeliveredId: "0-0",
					EntriesRead:     models.CreateNilInt64Result(),
					Lag:             models.CreateInt64Result(0),
				},
			}, xinfo)
		}

		xadd, err := client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "e1_f1", Value: "e1_v1"}, {Field: "e1_f2", Value: "e1_v2"}},
			*options.NewXAddOptions().SetId("0-1"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-1", xadd.Value())
		xadd, err = client.XAddWithOptions(context.Background(),
			key,
			[]models.FieldValue{{Field: "e2_f1", Value: "e2_v1"}, {Field: "e2_f2", Value: "e2_v2"}},
			*options.NewXAddOptions().SetId("0-2"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-2", xadd.Value())
		xadd, err = client.XAddWithOptions(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "e3_f1", Value: "e3_v1"}},
			*options.NewXAddOptions().SetId("0-3"),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "0-3", xadd.Value())

		// same as previous check, bug lag = 3, there are 3 messages unread
		xinfo, err = client.XInfoGroups(context.Background(), key)
		suite.NoError(err)
		if suite.serverVersion < "7.0.0" {
			suite.Equal([]models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       0,
					Pending:         0,
					LastDeliveredId: "0-0",
					EntriesRead:     models.CreateNilInt64Result(),
					Lag:             models.CreateNilInt64Result(),
				},
			}, xinfo)
		} else {
			assert.Equal(suite.T(), []models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       0,
					Pending:         0,
					LastDeliveredId: "0-0",
					EntriesRead:     models.CreateNilInt64Result(),
					Lag:             models.CreateInt64Result(3),
				},
			}, xinfo)
		}

		xReadGroup, err := client.XReadGroup(context.Background(), group, consumer, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xReadGroup))
		streamResponse, exists := xReadGroup[key]
		assert.True(suite.T(), exists)

		// Check that we have three entries with the correct IDs and fields
		assert.Equal(suite.T(), 3, len(streamResponse.Entries))

		// Create a map of entry IDs to their fields for easier comparison
		entryMap := make(map[string][]models.FieldValue)

		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// Verify entries
		assert.Contains(suite.T(), entryMap, "0-1")
		assert.Contains(suite.T(), entryMap, "0-2")
		assert.Contains(suite.T(), entryMap, "0-3")
		sort.Slice(streamResponse.Entries, func(i, j int) bool {
			return streamResponse.Entries[i].ID < streamResponse.Entries[j].ID
		})
		assert.Equal(
			suite.T(),
			streamResponse,
			models.StreamResponse{
				Entries: []models.StreamEntry{
					{
						ID: "0-1",
						Fields: []models.FieldValue{
							{Field: "e1_f1", Value: "e1_v1"},
							{Field: "e1_f2", Value: "e1_v2"},
						},
					},
					{
						ID: "0-2",
						Fields: []models.FieldValue{
							{Field: "e2_f1", Value: "e2_v1"},
							{Field: "e2_f2", Value: "e2_v2"},
						},
					},
					{
						ID: "0-3",
						Fields: []models.FieldValue{
							{Field: "e3_f1", Value: "e3_v1"},
						},
					},
				},
			},
		)

		// after reading, `lag` is reset, and `pending`, consumer count and last ID are set
		xinfo, err = client.XInfoGroups(context.Background(), key)
		assert.NoError(suite.T(), err)
		if suite.serverVersion < "7.0.0" {
			assert.Equal(suite.T(), []models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       1,
					Pending:         3,
					LastDeliveredId: "0-3",
					EntriesRead:     models.CreateNilInt64Result(),
					Lag:             models.CreateNilInt64Result(),
				},
			}, xinfo)
		} else {
			assert.Equal(suite.T(), []models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       1,
					Pending:         3,
					LastDeliveredId: "0-3",
					EntriesRead:     models.CreateInt64Result(3),
					Lag:             models.CreateInt64Result(0),
				},
			}, xinfo)
		}

		xack, err := client.XAck(context.Background(), key, group, []string{"0-1"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), xack)

		// once message ack'ed, pending counter decreased
		xinfo, err = client.XInfoGroups(context.Background(), key)
		assert.NoError(suite.T(), err)
		if suite.serverVersion < "7.0.0" {
			assert.Equal(suite.T(), []models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       1,
					Pending:         2,
					LastDeliveredId: "0-3",
					EntriesRead:     models.CreateNilInt64Result(),
					Lag:             models.CreateNilInt64Result(),
				},
			}, xinfo)
		} else {
			assert.Equal(suite.T(), []models.XInfoGroupInfo{
				{
					Name:            group,
					Consumers:       1,
					Pending:         2,
					LastDeliveredId: "0-3",
					EntriesRead:     models.CreateInt64Result(3),
					Lag:             models.CreateInt64Result(0),
				},
			}, xinfo)
		}

		// Passing a non-existing key raises an error
		key = uuid.NewString()
		_, err = client.XInfoGroups(context.Background(), key)
		suite.Error(err)

		// key exists, but it is not a stream
		suite.verifyOK(client.Set(context.Background(), key, key))
		_, err = client.XInfoGroups(context.Background(), key)
		suite.Error(err)

		// create a second stream
		key = uuid.NewString()
		_, err = client.XAdd(context.Background(), key, []models.FieldValue{{Field: "1", Value: "2"}})
		assert.NoError(suite.T(), err)
		// no group yet exists
		xinfo, err = client.XInfoGroups(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xinfo)
	})
}

func (suite *GlideTestSuite) TestSetBit_SetSingleBit() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		var resultInt64 int64
		resultInt64, err := client.SetBit(context.Background(), key, 7, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultInt64)

		result, err := client.Get(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Contains(suite.T(), result.Value(), "\x01")
	})
}

func (suite *GlideTestSuite) TestSetBit_SetAndCheckPreviousBit() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		var resultInt64 int64
		resultInt64, err := client.SetBit(context.Background(), key, 7, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultInt64)

		resultInt64, err = client.SetBit(context.Background(), key, 7, 0)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), resultInt64)
	})
}

func (suite *GlideTestSuite) TestSetBit_SetMultipleBits() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		var resultInt64 int64

		resultInt64, err := client.SetBit(context.Background(), key, 3, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultInt64)

		resultInt64, err = client.SetBit(context.Background(), key, 5, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), resultInt64)

		result, err := client.Get(context.Background(), key)
		assert.NoError(suite.T(), err)
		value := result.Value()

		binaryString := fmt.Sprintf("%08b", value[0])

		assert.Equal(suite.T(), "00010100", binaryString)
	})
}

func (suite *GlideTestSuite) TestWait() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "test")
		// Test 1:  numberOfReplicas (2)
		resultInt64, err := client.Wait(context.Background(), 2, 2000*time.Millisecond)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), resultInt64 >= 2)

		// Test 2: Invalid timeout (negative)
		_, err = client.Wait(context.Background(), 2, -1*time.Millisecond)

		// Assert error and message for invalid timeout
		assert.NotNil(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestGetBit_ExistingKey_ValidOffset() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		offset := int64(7)
		value := int64(1)

		client.SetBit(context.Background(), key, offset, value)

		result, err := client.GetBit(context.Background(), key, offset)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, result)
	})
}

func (suite *GlideTestSuite) TestGetBit_NonExistentKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		offset := int64(10)

		result, err := client.GetBit(context.Background(), key, offset)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), result)
	})
}

func (suite *GlideTestSuite) TestGetBit_InvalidOffset() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		invalidOffset := int64(-1)

		_, err := client.GetBit(context.Background(), key, invalidOffset)
		assert.NotNil(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestBitCount_ExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		for i := int64(0); i < 8; i++ {
			client.SetBit(context.Background(), key, i, 1)
		}

		result, err := client.BitCount(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(8), result)
	})
}

func (suite *GlideTestSuite) TestBitCount_ZeroBits() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		result, err := client.BitCount(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), result)
	})
}

func (suite *GlideTestSuite) TestBitCountWithOptions_StartEnd() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := "TestBitCountWithOptions_StartEnd"

		client.Set(context.Background(), key, value)

		opts := options.NewBitCountOptions().
			SetStart(1).
			SetEnd(5)

		result, err := client.BitCountWithOptions(context.Background(), key, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(19), result)
	})
}

func (suite *GlideTestSuite) TestBitCountWithOptions_StartEndByte() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := "TestBitCountWithOptions_StartEnd"

		client.Set(context.Background(), key, value)

		opts := options.NewBitCountOptions().
			SetStart(1).
			SetEnd(5).
			SetBitmapIndexType(options.BYTE)

		result, err := client.BitCountWithOptions(context.Background(), key, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(19), result)
	})
}

func (suite *GlideTestSuite) TestBitCountWithOptions_StartEndBit() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := "TestBitCountWithOptions_StartEnd"

		client.Set(context.Background(), key, value)

		opts := options.NewBitCountOptions().
			SetStart(1).
			SetEnd(5).
			SetBitmapIndexType(options.BIT)

		result, err := client.BitCountWithOptions(context.Background(), key, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), result)
	})
}

func (suite *GlideTestSuite) TestBitOp_AND() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		bitopkey1 := "{bitop_test}" + uuid.New().String()
		bitopkey2 := "{bitop_test}" + uuid.New().String()
		destKey := "{bitop_test}" + uuid.New().String()

		_, err := client.Set(context.Background(), bitopkey1, "foobar")
		assert.NoError(suite.T(), err)

		_, err = client.Set(context.Background(), bitopkey2, "abcdef")
		assert.NoError(suite.T(), err)

		result, err := client.BitOp(context.Background(), options.AND, destKey, []string{bitopkey1, bitopkey2})
		assert.NoError(suite.T(), err)
		assert.GreaterOrEqual(suite.T(), result, int64(0))

		bitResult, err := client.Get(context.Background(), destKey)
		assert.NoError(suite.T(), err)
		assert.NotEmpty(suite.T(), bitResult.Value())
	})
}

func (suite *GlideTestSuite) TestBitOp_OR() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{bitop_test}" + uuid.New().String()
		key2 := "{bitop_test}" + uuid.New().String()
		destKey := "{bitop_test}" + uuid.New().String()

		_, err := client.Set(context.Background(), key1, "foo")
		assert.NoError(suite.T(), err)

		_, err = client.Set(context.Background(), key2, "bar")
		assert.NoError(suite.T(), err)

		result, err := client.BitOp(context.Background(), options.OR, destKey, []string{key1, key2})
		assert.NoError(suite.T(), err)
		assert.GreaterOrEqual(suite.T(), result, int64(0))

		bitResult, err := client.Get(context.Background(), destKey)
		assert.NoError(suite.T(), err)
		assert.NotEmpty(suite.T(), bitResult.Value())
	})
}

func (suite *GlideTestSuite) TestBitOp_XOR() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{bitop_test}" + uuid.New().String()
		key2 := "{bitop_test}" + uuid.New().String()
		destKey := "{bitop_test}" + uuid.New().String()

		_, err := client.Set(context.Background(), key1, "foo")
		assert.NoError(suite.T(), err)

		_, err = client.Set(context.Background(), key2, "bar")
		assert.NoError(suite.T(), err)

		result, err := client.BitOp(context.Background(), options.XOR, destKey, []string{key1, key2})
		assert.NoError(suite.T(), err)
		assert.GreaterOrEqual(suite.T(), result, int64(0))

		bitResult, err := client.Get(context.Background(), destKey)
		assert.NoError(suite.T(), err)
		assert.NotEmpty(suite.T(), bitResult.Value())
	})
}

func (suite *GlideTestSuite) TestBitOp_NOT() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		srcKey := "{bitop_test}" + uuid.New().String()
		destKey := "{bitop_test}" + uuid.New().String()

		_, err := client.Set(context.Background(), srcKey, "foobar")
		assert.NoError(suite.T(), err)

		result, err := client.BitOp(context.Background(), options.NOT, destKey, []string{srcKey})
		assert.NoError(suite.T(), err)
		assert.GreaterOrEqual(suite.T(), result, int64(0))

		bitResult, err := client.Get(context.Background(), destKey)
		assert.NoError(suite.T(), err)
		assert.NotEmpty(suite.T(), bitResult.Value())
	})
}

func (suite *GlideTestSuite) TestBitOp_InvalidArguments() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		destKey := "{bitop_test}" + uuid.New().String()
		key1 := "{bitop_test}" + uuid.New().String()
		key2 := "{bitop_test}" + uuid.New().String()

		_, err := client.Set(context.Background(), key1, "foo")
		assert.NoError(suite.T(), err)

		_, err = client.Set(context.Background(), key2, "bar")
		assert.NoError(suite.T(), err)

		_, err = client.BitOp(context.Background(), options.AND, destKey, []string{key1})
		assert.NotNil(suite.T(), err)

		_, err = client.BitOp(context.Background(), options.OR, destKey, []string{key1})
		assert.NotNil(suite.T(), err)

		_, err = client.BitOp(context.Background(), options.XOR, destKey, []string{key1})
		assert.NotNil(suite.T(), err)

		_, err = client.BitOp(context.Background(), options.NOT, destKey, []string{key1, key2})
		assert.NotNil(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestXPendingAndXClaim() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// 1. Arrange the data
		key := uuid.New().String()
		groupName := "group" + uuid.New().String()
		zeroStreamId := "0"
		consumer1 := "consumer-1-" + uuid.New().String()
		consumer2 := "consumer-2-" + uuid.New().String()

		resp, err := client.XGroupCreateWithOptions(context.Background(),
			key,
			groupName,
			zeroStreamId,
			*options.NewXGroupCreateOptions().SetMakeStream(),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", resp)

		respBool, err := client.XGroupCreateConsumer(context.Background(), key, groupName, consumer1)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), respBool)

		respBool, err = client.XGroupCreateConsumer(context.Background(), key, groupName, consumer2)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), respBool)

		// Add two stream entries for consumer 1
		streamid_1, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
		assert.NoError(suite.T(), err)
		streamid_2, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field2", Value: "value2"}})
		assert.NoError(suite.T(), err)

		// Read the stream entries for consumer 1 and mark messages as pending
		xReadGroupResult1, err := client.XReadGroup(context.Background(), groupName, consumer1, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xReadGroupResult1))
		streamResponse, exists := xReadGroupResult1[key]
		assert.True(suite.T(), exists)

		// Check that we have two entries with the correct IDs and fields
		assert.Equal(suite.T(), 2, len(streamResponse.Entries))

		// Create a map of entry IDs to their fields for easier comparison
		entryMap := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse.Entries {
			entryMap[entry.ID] = entry.Fields
		}

		// Verify entries
		assert.Contains(suite.T(), entryMap, streamid_1)

		// Check for field "field1" with value "value1" in entry
		assert.Equal(
			suite.T(),
			entryMap[streamid_1],
			[]models.FieldValue{{Field: "field1", Value: "value1"}})

		assert.Contains(suite.T(), entryMap, streamid_2)
		// Check for field "field2" with value "value2" in entry
		assert.Equal(
			suite.T(),
			entryMap[streamid_2],
			[]models.FieldValue{{Field: "field2", Value: "value2"}})

		// Add 3 more stream entries for consumer 2
		streamid_3, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field3", Value: "value3"}})
		assert.NoError(suite.T(), err)
		streamid_4, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field4", Value: "value4"}})
		assert.NoError(suite.T(), err)
		streamid_5, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field5", Value: "value5"}})
		assert.NoError(suite.T(), err)

		// read the entire stream for consumer 2 and mark messages as pending
		xReadGroupResult2, err := client.XReadGroup(context.Background(), groupName, consumer2, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)

		// Check that we have the stream with the correct name in the map
		assert.Equal(suite.T(), 1, len(xReadGroupResult2))
		streamResponse2, exists := xReadGroupResult2[key]
		assert.True(suite.T(), exists)

		// Check that we have three entries with the correct IDs and fields
		assert.Equal(suite.T(), 3, len(streamResponse2.Entries))

		// Create a map of entry IDs to their fields for easier comparison
		entryMap2 := make(map[string][]models.FieldValue)
		for _, entry := range streamResponse2.Entries {
			entryMap2[entry.ID] = entry.Fields
		}

		// Verify entries
		assert.Contains(suite.T(), entryMap2, streamid_3)
		assert.Contains(suite.T(), entryMap2, streamid_4)
		assert.Contains(suite.T(), entryMap2, streamid_5)
		sort.Slice(streamResponse2.Entries, func(i, j int) bool {
			return streamResponse2.Entries[i].ID < streamResponse2.Entries[j].ID
		})
		entries := []models.StreamEntry{
			{ID: streamid_3, Fields: []models.FieldValue{{Field: "field3", Value: "value3"}}},
			{ID: streamid_4, Fields: []models.FieldValue{{Field: "field4", Value: "value4"}}},
			{ID: streamid_5, Fields: []models.FieldValue{{Field: "field5", Value: "value5"}}},
		}
		sort.Slice(entries, func(i, j int) bool {
			return entries[i].ID < entries[j].ID
		})
		assert.Equal(
			suite.T(),
			streamResponse2,
			models.StreamResponse{
				Entries: entries,
			},
		)

		expectedSummary := models.XPendingSummary{
			NumOfMessages: 5,
			StartId:       models.CreateStringResult(streamid_1),
			EndId:         models.CreateStringResult(streamid_5),
			ConsumerMessages: []models.ConsumerPendingMessage{
				{ConsumerName: consumer1, MessageCount: 2},
				{ConsumerName: consumer2, MessageCount: 3},
			},
		}
		summaryResult, err := client.XPending(context.Background(), key, groupName)
		assert.NoError(suite.T(), err)
		assert.True(
			suite.T(),
			reflect.DeepEqual(expectedSummary, summaryResult),
			"Expected and actual results do not match",
		)

		// ensure idle time > 0
		time.Sleep(2000 * time.Millisecond)
		pendingResultExtended, err := client.XPendingWithOptions(context.Background(),
			key,
			groupName,
			*options.NewXPendingOptions("-", "+", 10),
		)
		assert.NoError(suite.T(), err)

		assert.Greater(suite.T(), len(pendingResultExtended), 2)
		// because of the idle time return, we have to exclude it from the expected result
		// and check separately
		assert.Equal(suite.T(), pendingResultExtended[0].Id, streamid_1)
		assert.Equal(suite.T(), pendingResultExtended[0].ConsumerName, consumer1)
		assert.GreaterOrEqual(suite.T(), pendingResultExtended[0].DeliveryCount, int64(0))

		assert.Equal(suite.T(), pendingResultExtended[1].Id, streamid_2)
		assert.Equal(suite.T(), pendingResultExtended[1].ConsumerName, consumer1)
		assert.GreaterOrEqual(suite.T(), pendingResultExtended[1].DeliveryCount, int64(0))

		assert.Equal(suite.T(), pendingResultExtended[2].Id, streamid_3)
		assert.Equal(suite.T(), pendingResultExtended[2].ConsumerName, consumer2)
		assert.GreaterOrEqual(suite.T(), pendingResultExtended[2].DeliveryCount, int64(0))

		assert.Equal(suite.T(), pendingResultExtended[3].Id, streamid_4)
		assert.Equal(suite.T(), pendingResultExtended[3].ConsumerName, consumer2)
		assert.GreaterOrEqual(suite.T(), pendingResultExtended[3].DeliveryCount, int64(0))

		assert.Equal(suite.T(), pendingResultExtended[4].Id, streamid_5)
		assert.Equal(suite.T(), pendingResultExtended[4].ConsumerName, consumer2)
		assert.GreaterOrEqual(suite.T(), pendingResultExtended[4].DeliveryCount, int64(0))

		// use claim to claim stream 3 and 5 for consumer 1
		claimResult, err := client.XClaim(context.Background(),
			key,
			groupName,
			consumer1,
			0,
			[]string{streamid_3, streamid_5},
		)
		assert.NoError(suite.T(), err)
		expectedClaimResult := map[string]models.XClaimResponse{
			streamid_3: {
				Fields: []models.FieldValue{
					{Field: "field3", Value: "value3"},
				},
			},
			streamid_5: {
				Fields: []models.FieldValue{
					{Field: "field5", Value: "value5"},
				},
			},
		}
		assert.Equal(suite.T(), expectedClaimResult, claimResult)

		claimResultJustId, err := client.XClaimJustId(context.Background(),
			key,
			groupName,
			consumer1,
			0,
			[]string{streamid_3, streamid_5},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{streamid_3, streamid_5}, claimResultJustId)

		// add one more stream
		streamid_6, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field6", Value: "value6"}})
		assert.NoError(suite.T(), err)

		// using force, we can xclaim the message without reading it
		claimResult, err = client.XClaimWithOptions(context.Background(),
			key,
			groupName,
			consumer1,
			0,
			[]string{streamid_6},
			*options.NewXClaimOptions().SetForce().SetRetryCount(99),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			map[string]models.XClaimResponse{
				streamid_6: {
					Fields: []models.FieldValue{
						{Field: "field6", Value: "value6"},
					},
				},
			},
			claimResult,
		)

		forcePendingResult, err := client.XPendingWithOptions(context.Background(),
			key,
			groupName,
			*options.NewXPendingOptions(streamid_6, streamid_6, 1),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 1, len(forcePendingResult))
		assert.Equal(suite.T(), streamid_6, forcePendingResult[0].Id)
		assert.Equal(suite.T(), consumer1, forcePendingResult[0].ConsumerName)
		assert.Equal(suite.T(), int64(99), forcePendingResult[0].DeliveryCount)

		// acknowledge streams 2, 3, 4 and 6 and remove them from xpending results
		xackResult, err := client.XAck(context.Background(),
			key, groupName,
			[]string{streamid_2, streamid_3, streamid_4, streamid_6})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(4), xackResult)

		pendingResultExtended, err = client.XPendingWithOptions(context.Background(),
			key,
			groupName,
			*options.NewXPendingOptions(streamid_3, "+", 10),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 1, len(pendingResultExtended))
		assert.Equal(suite.T(), streamid_5, pendingResultExtended[0].Id)
		assert.Equal(suite.T(), consumer1, pendingResultExtended[0].ConsumerName)

		pendingResultExtended, err = client.XPendingWithOptions(context.Background(),
			key,
			groupName,
			*options.NewXPendingOptions("-", "("+streamid_5, 10),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 1, len(pendingResultExtended))
		assert.Equal(suite.T(), streamid_1, pendingResultExtended[0].Id)
		assert.Equal(suite.T(), consumer1, pendingResultExtended[0].ConsumerName)

		pendingResultExtended, err = client.XPendingWithOptions(context.Background(),
			key,
			groupName,
			*options.NewXPendingOptions("-", "+", 10).SetMinIdleTime(1).SetConsumer(consumer1),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 2, len(pendingResultExtended))
	})
}

func (suite *GlideTestSuite) TestXClaimFailure() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		stringKey := "string-key-" + uuid.New().String()
		groupName := "group" + uuid.New().String()
		zeroStreamId := "0"
		consumer1 := "consumer-1-" + uuid.New().String()

		// create group and consumer for the group
		groupCreateResult, err := client.XGroupCreateWithOptions(context.Background(),
			key,
			groupName,
			zeroStreamId,
			*options.NewXGroupCreateOptions().SetMakeStream(),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", groupCreateResult)

		consumerCreateResult, err := client.XGroupCreateConsumer(context.Background(), key, groupName, consumer1)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), consumerCreateResult)

		// Add stream entry and mark as pending
		streamid_1, err := client.XAdd(context.Background(), key, []models.FieldValue{{Field: "field1", Value: "value1"}})
		assert.NoError(suite.T(), err)
		assert.NotNil(suite.T(), streamid_1)

		readGroupResult, err := client.XReadGroup(context.Background(), groupName, consumer1, map[string]string{key: ">"})
		assert.NoError(suite.T(), err)
		assert.NotNil(suite.T(), readGroupResult)

		// claim with invalid stream entry IDs
		_, err = client.XClaimJustId(
			context.Background(),
			key,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{"invalid-stream-id"},
		)
		assert.Error(suite.T(), err)

		// claim with empty stream entry IDs returns empty map
		claimResult, err := client.XClaimJustId(
			context.Background(),
			key,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{}, claimResult)

		// non existent key causes a RequestError
		claimOptions := options.NewXClaimOptions().SetIdleTime(1)
		_, err = client.XClaim(
			context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
		)
		suite.ErrorContains(err, "NOGROUP")

		_, err = client.XClaimWithOptions(context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
			*claimOptions,
		)
		suite.ErrorContains(err, "NOGROUP")

		_, err = client.XClaimJustId(
			context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
		)
		suite.ErrorContains(err, "NOGROUP")

		_, err = client.XClaimJustIdWithOptions(context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
			*claimOptions,
		)
		suite.ErrorContains(err, "NOGROUP")

		// key exists, but is not a stream
		_, err = client.Set(context.Background(), stringKey, "test")
		assert.NoError(suite.T(), err)
		_, err = client.XClaim(
			context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
		)
		assert.Error(suite.T(), err)

		_, err = client.XClaimWithOptions(context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
			*claimOptions,
		)
		suite.Error(err)

		_, err = client.XClaimJustId(
			context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
		)
		suite.Error(err)

		_, err = client.XClaimJustIdWithOptions(context.Background(),
			stringKey,
			groupName,
			consumer1,
			1*time.Millisecond,
			[]string{streamid_1},
			*claimOptions,
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestCopy() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()
		value := "hello"
		suite.verifyOK(client.Set(context.Background(), key, value))

		// Test 1: Check the copy command
		resultCopy, err := client.Copy(context.Background(), key, key2)
		suite.NoError(err)
		suite.True(resultCopy)

		// Test 2: Check if the value stored at the source is same with destination key.
		resultGet, err := client.Get(context.Background(), key2)
		suite.NoError(err)
		suite.Equal(value, resultGet.Value())
	})
}

func (suite *GlideTestSuite) TestCopyWithOptions() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{key}" + uuid.New().String()
		key2 := "{key}" + uuid.New().String()
		value := "hello"
		t := suite.T()
		suite.verifyOK(client.Set(context.Background(), key, value))
		suite.verifyOK(client.Set(context.Background(), key2, "World"))

		// Test 1: Check the copy command with options
		optsCopy := options.NewCopyOptions().SetReplace()
		resultCopy, err := client.CopyWithOptions(context.Background(), key, key2, *optsCopy)
		assert.Nil(t, err)
		assert.True(t, resultCopy)

		// Test 2: Check if the value stored at the source is same with destination key.
		resultGet, err := client.Get(context.Background(), key2)
		assert.Nil(t, err)
		assert.Equal(t, value, resultGet.Value())
	})
}

func (suite *GlideTestSuite) TestXRangeAndXRevRange() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		key2 := uuid.New().String()
		stringKey := uuid.New().String()
		positiveInfinity := options.NewInfiniteStreamBoundary(constants.PositiveInfinity)
		negativeInfinity := options.NewInfiniteStreamBoundary(constants.NegativeInfinity)

		// add stream entries
		streamId1, err := client.XAdd(context.Background(),
			key,
			[]models.FieldValue{{Field: "field1", Value: "value1"}},
		)
		assert.NoError(suite.T(), err)
		assert.NotNil(suite.T(), streamId1)

		streamId2, err := client.XAdd(context.Background(),
			key,
			[]models.FieldValue{{Field: "field2", Value: "value2"}},
		)
		assert.NoError(suite.T(), err)
		assert.NotNil(suite.T(), streamId2)

		xlenResult, err := client.XLen(context.Background(), key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), xlenResult)

		// get everything from the stream
		xrangeResult, err := client.XRange(
			context.Background(),
			key,
			negativeInfinity,
			positiveInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.StreamEntry{
				{ID: streamId1, Fields: []models.FieldValue{{Field: "field1", Value: "value1"}}},
				{ID: streamId2, Fields: []models.FieldValue{{Field: "field2", Value: "value2"}}},
			},
			xrangeResult,
		)

		// get everything from the stream in reverse
		xrevrangeResult, err := client.XRevRange(
			context.Background(),
			key,
			positiveInfinity,
			negativeInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.StreamEntry{
				{ID: streamId2, Fields: []models.FieldValue{{Field: "field2", Value: "value2"}}},
				{ID: streamId1, Fields: []models.FieldValue{{Field: "field1", Value: "value1"}}},
			},
			xrevrangeResult,
		)

		// returns empty map if + before -
		xrangeResult, err = client.XRange(
			context.Background(),
			key,
			positiveInfinity,
			negativeInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xrangeResult)

		// rev search returns empty if - before +
		xrevrangeResult, err = client.XRevRange(
			context.Background(),
			key,
			negativeInfinity,
			positiveInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xrevrangeResult)

		streamId3, err := client.XAdd(
			context.Background(),
			key,
			[]models.FieldValue{{Field: "field3", Value: "value3"}},
		)
		assert.NoError(suite.T(), err)
		assert.NotNil(suite.T(), streamId3)

		// Exclusive ranges are added in 6.2.0
		if suite.serverVersion >= "6.2.0" {
			// get the newest stream entry
			xrangeResult, err = client.XRangeWithOptions(
				context.Background(),
				key,
				options.NewStreamBoundary(streamId2, false),
				positiveInfinity,
				*options.NewXRangeOptions().SetCount(1),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(
				suite.T(),
				[]models.StreamEntry{
					{ID: streamId3, Fields: []models.FieldValue{{Field: "field3", Value: "value3"}}},
				},
				xrangeResult,
			)

			// doing the same with rev search
			xrevrangeResult, err = client.XRevRangeWithOptions(
				context.Background(),
				key,
				positiveInfinity,
				options.NewStreamBoundary(streamId2, false),
				*options.NewXRangeOptions().SetCount(1),
			)
			assert.NoError(suite.T(), err)
			assert.Equal(
				suite.T(),
				[]models.StreamEntry{
					{ID: streamId3, Fields: []models.FieldValue{{Field: "field3", Value: "value3"}}},
				},
				xrevrangeResult,
			)
		}

		// both xrange and xrevrange return nil with a zero/negative count
		xrangeResult, err = client.XRangeWithOptions(
			context.Background(),
			key,
			negativeInfinity,
			positiveInfinity,
			*options.NewXRangeOptions().SetCount(0),
		)
		assert.NoError(suite.T(), err)
		assert.Nil(suite.T(), xrangeResult)

		xrevrangeResult, err = client.XRevRangeWithOptions(
			context.Background(),
			key,
			positiveInfinity,
			negativeInfinity,
			*options.NewXRangeOptions().SetCount(0),
		)
		assert.NoError(suite.T(), err)
		assert.Nil(suite.T(), xrevrangeResult)

		// xrange and xrevrange against an empty stream
		xdelResult, err := client.XDel(
			context.Background(),
			key,
			[]string{streamId1, streamId2, streamId3},
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), xdelResult)

		xrangeResult, err = client.XRange(
			context.Background(),
			key,
			negativeInfinity,
			positiveInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xrangeResult)

		xrevrangeResult, err = client.XRevRange(
			context.Background(),
			key,
			positiveInfinity,
			negativeInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xrevrangeResult)

		// xrange and xrevrange against a non-existent stream
		xrangeResult, err = client.XRange(
			context.Background(),
			key2,
			negativeInfinity,
			positiveInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xrangeResult)

		xrevrangeResult, err = client.XRevRange(
			context.Background(),
			key2,
			positiveInfinity,
			negativeInfinity,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), xrevrangeResult)

		// xrange and xrevrange against a non-stream key
		_, err = client.Set(context.Background(), stringKey, "test")
		assert.NoError(suite.T(), err)
		_, err = client.XRange(context.Background(),
			stringKey,
			negativeInfinity,
			positiveInfinity,
		)
		suite.Error(err)

		_, err = client.XRevRange(context.Background(),
			stringKey,
			positiveInfinity,
			negativeInfinity,
		)
		suite.Error(err)

		// xrange and xrevrange when range bound is not a valid id
		_, err = client.XRange(context.Background(),
			key,
			options.NewStreamBoundary("invalid-id", true),
			positiveInfinity,
		)
		suite.Error(err)

		_, err = client.XRevRange(context.Background(),
			key,
			options.NewStreamBoundary("invalid-id", true),
			negativeInfinity,
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestBitField_GetAndIncrBy() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		commands := []options.BitFieldSubCommands{
			options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
		}

		result1, err := client.BitField(context.Background(), key, commands)
		suite.NoError(err)
		assert.Len(suite.T(), result1, 1)
		firstValue := result1[0].Value()

		result2, err := client.BitField(context.Background(), key, commands)
		suite.NoError(err)
		assert.Len(suite.T(), result2, 1)
		assert.Equal(suite.T(), firstValue+1, result2[0].Value())

		getCommands := []options.BitFieldSubCommands{
			options.NewBitFieldGet(options.SignedInt, 5, 100),
		}

		getResult, err := client.BitField(context.Background(), key, getCommands)
		suite.NoError(err)
		assert.Len(suite.T(), getResult, 1)
		assert.Equal(suite.T(), result2[0].Value(), getResult[0].Value())
	})
}

func (suite *GlideTestSuite) TestBitField_Overflow() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// SAT (Saturate) Overflow Test
		key1 := uuid.New().String()
		satCommands := []options.BitFieldSubCommands{
			options.NewBitFieldOverflow(options.SAT),
			options.NewBitFieldIncrBy(options.UnsignedInt, 2, 0, 2),
			options.NewBitFieldIncrBy(options.UnsignedInt, 2, 0, 2),
		}

		satResult, err := client.BitField(context.Background(), key1, satCommands)
		suite.NoError(err)
		assert.Len(suite.T(), satResult, 2)

		assert.Equal(suite.T(), int64(2), satResult[0].Value())
		assert.LessOrEqual(suite.T(), satResult[1].Value(), int64(3))

		// WRAP Overflow Test
		key2 := uuid.New().String()
		wrapCommands := []options.BitFieldSubCommands{
			options.NewBitFieldOverflow(options.WRAP),
			options.NewBitFieldIncrBy(options.UnsignedInt, 2, 0, 3),
			options.NewBitFieldIncrBy(options.UnsignedInt, 2, 0, 1),
		}

		wrapResult, err := client.BitField(context.Background(), key2, wrapCommands)
		suite.NoError(err)
		assert.Len(suite.T(), wrapResult, 2)

		assert.Equal(suite.T(), int64(3), wrapResult[0].Value())
		assert.Equal(suite.T(), int64(0), wrapResult[1].Value())

		// FAIL Overflow Test
		key3 := uuid.New().String()
		failCommands := []options.BitFieldSubCommands{
			options.NewBitFieldOverflow(options.FAIL),
			options.NewBitFieldIncrBy(options.UnsignedInt, 2, 0, 3),
			options.NewBitFieldIncrBy(options.UnsignedInt, 2, 0, 1),
		}

		failResult, err := client.BitField(context.Background(), key3, failCommands)
		suite.NoError(err)
		assert.Len(suite.T(), failResult, 2)

		assert.Equal(suite.T(), int64(3), failResult[0].Value())
		assert.True(suite.T(), failResult[1].IsNil())
	})
}

func (suite *GlideTestSuite) TestBitField_MultipleOperations() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		commands := []options.BitFieldSubCommands{
			options.NewBitFieldSet(options.UnsignedInt, 8, 0, 10),
			options.NewBitFieldGet(options.UnsignedInt, 8, 0),
			options.NewBitFieldIncrBy(options.UnsignedInt, 8, 0, 5),
		}

		result, err := client.BitField(context.Background(), key, commands)

		suite.NoError(err)
		assert.Len(suite.T(), result, 3)

		assert.LessOrEqual(suite.T(), result[0].Value(), int64(10))
		assert.Equal(suite.T(), int64(10), result[1].Value())
		assert.Equal(suite.T(), int64(15), result[2].Value())
	})
}

func (suite *GlideTestSuite) TestBitPos_ExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "\x10")
		result, err := client.BitPos(context.Background(), key, 1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), result)
	})
}

func (suite *GlideTestSuite) TestBitPos_NonExistingKey() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		result, err := client.BitPos(context.Background(), key, 0)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), result)
	})
}

func (suite *GlideTestSuite) TestBitPosWithOptions_StartEnd() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "\x00\x01\x80")

		opts := options.NewBitPosOptions().
			SetStart(0).
			SetEnd(1)

		result, err := client.BitPosWithOptions(context.Background(), key, 1, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(15), result)
	})
}

func (suite *GlideTestSuite) TestBitPosWithOptions_BitmapIndexType() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "\x00\x02\x00")

		opts := options.NewBitPosOptions().
			SetStart(1).
			SetEnd(2).
			SetBitmapIndexType(options.BYTE)

		result, err := client.BitPosWithOptions(context.Background(), key, 1, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(14), result)
	})
}

func (suite *GlideTestSuite) TestBitPosWithOptions_BitIndexType() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "\x00\x10\x00")

		opts := options.NewBitPosOptions().
			SetStart(10).
			SetEnd(14).
			SetBitmapIndexType(options.BIT)

		result, err := client.BitPosWithOptions(context.Background(), key, 1, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(11), result)
	})
}

func (suite *GlideTestSuite) TestBitPos_FindBitZero() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "\xFF\xF7")

		result, err := client.BitPos(context.Background(), key, 0)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(12), result)
	})
}

func (suite *GlideTestSuite) TestBitPosWithOptions_NegativeEnd() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		client.Set(context.Background(), key, "\x00\x01\x80")

		opts := options.NewBitPosOptions().
			SetStart(0).
			SetEnd(-2)

		result, err := client.BitPosWithOptions(context.Background(), key, 1, *opts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(15), result)
	})
}

func (suite *GlideTestSuite) TestBitField_Failures() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()

		// Test invalid bit size for unsigned
		invalidUnsignedCommands := []options.BitFieldSubCommands{
			options.NewBitFieldGet(options.UnsignedInt, 64, 0),
		}

		_, err := client.BitField(context.Background(), key, invalidUnsignedCommands)
		assert.NotNil(suite.T(), err)

		// Test invalid bit size for signed
		invalidSignedCommands := []options.BitFieldSubCommands{
			options.NewBitFieldGet(options.SignedInt, 65, 0),
		}

		_, err = client.BitField(context.Background(), key, invalidSignedCommands)
		assert.NotNil(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestBitFieldRO_BasicOperation() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value := int64(42)

		setCommands := []options.BitFieldSubCommands{
			options.NewBitFieldSet(options.SignedInt, 8, 16, value),
		}
		_, err := client.BitField(context.Background(), key, setCommands)
		suite.NoError(err)

		getNormalCommands := []options.BitFieldSubCommands{
			options.NewBitFieldGet(options.SignedInt, 8, 16),
		}
		getNormal, err := client.BitField(context.Background(), key, getNormalCommands)
		suite.NoError(err)

		getROCommands := []options.BitFieldROCommands{
			options.NewBitFieldGet(options.SignedInt, 8, 16),
		}
		getRO, err := client.BitFieldRO(context.Background(), key, getROCommands)
		suite.NoError(err)

		assert.Equal(suite.T(), getNormal[0].Value(), getRO[0].Value())
		assert.Equal(suite.T(), value, getRO[0].Value())
	})
}

func (suite *GlideTestSuite) TestBitFieldRO_MultipleGets() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := uuid.New().String()
		value1 := int64(42)
		value2 := int64(43)

		setCommands := []options.BitFieldSubCommands{
			options.NewBitFieldSet(options.SignedInt, 8, 0, value1),
			options.NewBitFieldSet(options.SignedInt, 8, 8, value2),
		}

		_, err := client.BitField(context.Background(), key, setCommands)
		suite.NoError(err)

		getNormalCommands := []options.BitFieldSubCommands{
			options.NewBitFieldGet(options.SignedInt, 8, 0),
			options.NewBitFieldGet(options.SignedInt, 8, 8),
		}

		getNormal, err := client.BitField(context.Background(), key, getNormalCommands)
		suite.NoError(err)

		getROCommands := []options.BitFieldROCommands{
			options.NewBitFieldGet(options.SignedInt, 8, 0),
			options.NewBitFieldGet(options.SignedInt, 8, 8),
		}

		getRO, err := client.BitFieldRO(context.Background(), key, getROCommands)
		suite.NoError(err)

		assert.Equal(suite.T(),
			[]int64{getNormal[0].Value(), getNormal[1].Value()},
			[]int64{getRO[0].Value(), getRO[1].Value()},
		)
		assert.Equal(suite.T(), []int64{value1, value2}, []int64{getRO[0].Value(), getRO[1].Value()})
	})
}

func (suite *GlideTestSuite) TestZInter() {
	suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-" + uuid.New().String()
		key2 := "{key}-" + uuid.New().String()
		key3 := "{key}-" + uuid.New().String()
		memberScoreMap1 := map[string]float64{
			"one": 1.0,
			"two": 2.0,
		}
		memberScoreMap2 := map[string]float64{
			"two":   3.5,
			"three": 3.0,
		}

		// Add members to sorted sets
		res, err := client.ZAdd(context.Background(), key1, memberScoreMap1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		res, err = client.ZAdd(context.Background(), key2, memberScoreMap2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// intersection results are aggregated by the max score of elements
		zinterResult, err := client.ZInter(context.Background(), options.KeyArray{Keys: []string{key1, key2}})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{"two"}, zinterResult)

		// intersection with scores
		zinterWithScoresResult, err := client.ZInterWithScores(
			context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []models.MemberAndScore{{Member: "two", Score: 5.5}}, zinterWithScoresResult)

		// intersect results with max aggregate
		zinterWithMaxAggregateResult, err := client.ZInterWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateMax),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []models.MemberAndScore{{Member: "two", Score: 3.5}}, zinterWithMaxAggregateResult)

		// intersect results with min aggregate
		zinterWithMinAggregateResult, err := client.ZInterWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateMin),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []models.MemberAndScore{{Member: "two", Score: 2.0}}, zinterWithMinAggregateResult)

		// intersect results with sum aggregate
		zinterWithSumAggregateResult, err := client.ZInterWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []models.MemberAndScore{{Member: "two", Score: 5.5}}, zinterWithSumAggregateResult)

		// Scores are multiplied by a 2.0 weight for key1 and key2 during aggregation
		zinterWithWeightedKeysResult, err := client.ZInterWithScores(context.Background(),
			options.WeightedKeys{
				KeyWeightPairs: []options.KeyWeightPair{
					{Key: key1, Weight: 2.0},
					{Key: key2, Weight: 2.0},
				},
			},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []models.MemberAndScore{{Member: "two", Score: 11.0}}, zinterWithWeightedKeysResult)

		// non-existent key - empty intersection
		zinterWithNonExistentKeyResult, err := client.ZInterWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key3}},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), zinterWithNonExistentKeyResult)

		// empty key list - request error
		_, err = client.ZInterWithScores(context.Background(), options.KeyArray{Keys: []string{}},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		suite.Error(err)

		// key exists but not a set
		_, err = client.Set(context.Background(), key3, "value")
		suite.NoError(err)

		_, err = client.ZInter(context.Background(), options.KeyArray{Keys: []string{key1, key3}})
		suite.Error(err)

		_, err = client.ZInterWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key3}},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZInterStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-" + uuid.New().String()
		key2 := "{key}-" + uuid.New().String()
		key3 := "{key}-" + uuid.New().String()
		key4 := "{key}-" + uuid.New().String()
		query := options.NewRangeByIndexQuery(0, -1)
		memberScoreMap1 := map[string]float64{
			"one": 1.0,
			"two": 2.0,
		}
		memberScoreMap2 := map[string]float64{
			"one":   1.5,
			"two":   2.5,
			"three": 3.5,
		}

		// Add members to sorted sets
		res, err := client.ZAdd(context.Background(), key1, memberScoreMap1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		res, err = client.ZAdd(context.Background(), key2, memberScoreMap2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), res)

		// Store the intersection of key1 and key2 in key3
		res, err = client.ZInterStore(context.Background(), key3, options.KeyArray{Keys: []string{key1, key2}})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// checking stored intersection result
		zrangeResult, err := client.ZRangeWithScores(context.Background(), key3, query)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 2.5}, {Member: "two", Score: 4.5}},
			zrangeResult,
		)

		// Store the intersection of key1 and key2 in key4 with max aggregate
		res, err = client.ZInterStoreWithOptions(context.Background(), key3, options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateMax),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// checking stored intersection result with max aggregate
		zrangeResult, err = client.ZRangeWithScores(context.Background(), key3, query)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.5}, {Member: "two", Score: 2.5}},
			zrangeResult,
		)

		// Store the intersection of key1 and key2 in key5 with min aggregate
		res, err = client.ZInterStoreWithOptions(context.Background(), key3, options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateMin),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// checking stored intersection result with min aggregate
		zrangeResult, err = client.ZRangeWithScores(context.Background(), key3, query)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "two", Score: 2.0}},
			zrangeResult,
		)

		// Store the intersection of key1 and key2 in key6 with sum aggregate
		res, err = client.ZInterStoreWithOptions(context.Background(), key3, options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZInterOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// checking stored intersection result with sum aggregate (same as default aggregate)
		zrangeResult, err = client.ZRangeWithScores(context.Background(), key3, query)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 2.5}, {Member: "two", Score: 4.5}},
			zrangeResult,
		)

		// Store the intersection of key1 and key2 in key3 with 2.0 weights
		res, err = client.ZInterStore(context.Background(), key3, options.WeightedKeys{
			KeyWeightPairs: []options.KeyWeightPair{
				{Key: key1, Weight: 2.0},
				{Key: key2, Weight: 2.0},
			},
		})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// checking stored intersection result with weighted keys
		zrangeResult, err = client.ZRangeWithScores(context.Background(), key3, query)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 5.0}, {Member: "two", Score: 9.0}},
			zrangeResult,
		)

		// Store the intersection of key1 with 1.0 weight and key2 with -2.0 weight in key3 with 2.0 weights
		// and min aggregate
		res, err = client.ZInterStoreWithOptions(context.Background(), key3, options.WeightedKeys{
			KeyWeightPairs: []options.KeyWeightPair{
				{Key: key1, Weight: 1.0},
				{Key: key2, Weight: -2.0},
			},
		},
			*options.NewZInterOptions().SetAggregate(options.AggregateMin),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// checking stored intersection result with weighted keys
		zrangeResult, err = client.ZRangeWithScores(context.Background(), key3, query)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "two", Score: -5.0}, {Member: "one", Score: -3.0}},
			zrangeResult,
		)

		// key exists but not a set
		_, err = client.Set(context.Background(), key4, "value")
		suite.NoError(err)

		_, err = client.ZInterStore(context.Background(), key3, options.KeyArray{Keys: []string{key1, key4}})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZDiff() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
		t := suite.T()
		key1 := "{testKey}:1-" + uuid.NewString()
		key2 := "{testKey}:2-" + uuid.NewString()
		key3 := "{testKey}:3-" + uuid.NewString()
		nonExistentKey := "{testKey}:4-" + uuid.NewString()

		membersScores1 := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		membersScores2 := map[string]float64{
			"two": 2.0,
		}

		membersScores3 := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
			"four":  4.0,
		}

		zAddResult1, err := client.ZAdd(context.Background(), key1, membersScores1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), zAddResult1)
		zAddResult2, err := client.ZAdd(context.Background(), key2, membersScores2)
		assert.NoError(t, err)
		assert.Equal(t, int64(1), zAddResult2)
		zAddResult3, err := client.ZAdd(context.Background(), key3, membersScores3)
		assert.NoError(t, err)
		assert.Equal(t, int64(4), zAddResult3)

		zDiffResult, err := client.ZDiff(context.Background(), []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, []string{"one", "three"}, zDiffResult)
		zDiffResult, err = client.ZDiff(context.Background(), []string{key1, key3})
		assert.NoError(t, err)
		assert.Equal(t, []string{}, zDiffResult)
		zDiffResult, err = client.ZDiff(context.Background(), []string{nonExistentKey, key3})
		assert.NoError(t, err)
		assert.Equal(t, []string{}, zDiffResult)

		zDiffResultWithScores, err := client.ZDiffWithScores(context.Background(), []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(
			t,
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}},
			zDiffResultWithScores,
		)
		zDiffResultWithScores, err = client.ZDiffWithScores(context.Background(), []string{key1, key3})
		assert.NoError(t, err)
		assert.Equal(t, []models.MemberAndScore{}, zDiffResultWithScores)
		zDiffResultWithScores, err = client.ZDiffWithScores(context.Background(), []string{nonExistentKey, key3})
		assert.NoError(t, err)
		assert.Equal(t, []models.MemberAndScore{}, zDiffResultWithScores)

		// Key exists, but it is not a set
		setResult, _ := client.Set(context.Background(), nonExistentKey, "bar")
		suite.Equal(setResult, "OK")

		_, err = client.ZDiff(context.Background(), []string{nonExistentKey, key2})
		suite.Error(err)

		_, err = client.ZDiffWithScores(context.Background(), []string{nonExistentKey, key2})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZDiffStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
		t := suite.T()
		key1 := "{testKey}:1-" + uuid.NewString()
		key2 := "{testKey}:2-" + uuid.NewString()
		key3 := "{testKey}:3-" + uuid.NewString()
		key4 := "{testKey}:4-" + uuid.NewString()
		key5 := "{testKey}:5-" + uuid.NewString()

		membersScores1 := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		membersScores2 := map[string]float64{
			"two": 2.0,
		}

		membersScores3 := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
			"four":  4.0,
		}

		zAddResult1, err := client.ZAdd(context.Background(), key1, membersScores1)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), zAddResult1)
		zAddResult2, err := client.ZAdd(context.Background(), key2, membersScores2)
		assert.NoError(t, err)
		assert.Equal(t, int64(1), zAddResult2)
		zAddResult3, err := client.ZAdd(context.Background(), key3, membersScores3)
		assert.NoError(t, err)
		assert.Equal(t, int64(4), zAddResult3)

		zDiffStoreResult, err := client.ZDiffStore(context.Background(), key4, []string{key1, key2})
		assert.NoError(t, err)
		assert.Equal(t, zDiffStoreResult, int64(2))
		zRangeWithScoreResult, err := client.ZRangeWithScores(context.Background(), key4, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(
			t,
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}},
			zRangeWithScoreResult,
		)

		zDiffStoreResult, err = client.ZDiffStore(context.Background(), key4, []string{key3, key2, key1})
		assert.NoError(t, err)
		assert.Equal(t, zDiffStoreResult, int64(1))
		zRangeWithScoreResult, err = client.ZRangeWithScores(context.Background(), key4, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, []models.MemberAndScore{{Member: "four", Score: 4.0}}, zRangeWithScoreResult)

		zDiffStoreResult, err = client.ZDiffStore(context.Background(), key4, []string{key1, key3})
		assert.NoError(t, err)
		assert.Equal(t, zDiffStoreResult, int64(0))
		zRangeWithScoreResult, err = client.ZRangeWithScores(context.Background(), key4, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, []models.MemberAndScore{}, zRangeWithScoreResult)

		// Non-Existing key
		zDiffStoreResult, err = client.ZDiffStore(context.Background(), key4, []string{key5, key1})
		assert.NoError(t, err)
		assert.Equal(t, zDiffStoreResult, int64(0))
		zRangeWithScoreResult, err = client.ZRangeWithScores(context.Background(), key4, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(t, err)
		assert.Equal(t, []models.MemberAndScore{}, zRangeWithScoreResult)

		// Key exists, but it is not a set
		setResult, err := client.Set(context.Background(), key5, "bar")
		suite.NoError(err)
		suite.Equal(setResult, "OK")
		_, err = client.ZDiffStore(context.Background(), key4, []string{key5, key1})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZUnionAndZUnionWithScores() {
	suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-" + uuid.New().String()
		key2 := "{key}-" + uuid.New().String()
		key3 := "{key}-" + uuid.New().String()
		memberScoreMap1 := map[string]float64{
			"one": 1.0,
			"two": 2.0,
		}
		memberScoreMap2 := map[string]float64{
			"two":   3.5,
			"three": 3.0,
		}

		// Add members to sorted sets
		res, err := client.ZAdd(context.Background(), key1, memberScoreMap1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		res, err = client.ZAdd(context.Background(), key2, memberScoreMap2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		zUnionResult, err := client.ZUnion(context.Background(), options.KeyArray{Keys: []string{key1, key2}})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{"one", "three", "two"}, zUnionResult)

		// Union with scores
		zUnionWithScoresResult, err := client.ZUnionWithScores(
			context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}, {Member: "two", Score: 5.5}},
			zUnionWithScoresResult,
		)

		// Union results with max aggregate
		zUnionWithMaxAggregateResult, err := client.ZUnionWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateMax),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}, {Member: "two", Score: 3.5}},
			zUnionWithMaxAggregateResult,
		)

		// Union results with min aggregate
		zUnionWithMinAggregateResult, err := client.ZUnionWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateMin),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "two", Score: 2.0}, {Member: "three", Score: 3.0}},
			zUnionWithMinAggregateResult,
		)

		// Union results with sum aggregate
		zUnionWithSumAggregateResult, err := client.ZUnionWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}, {Member: "two", Score: 5.5}},
			zUnionWithSumAggregateResult,
		)

		// Scores are multiplied by a 2.0 weight for key1 and key2 during aggregation
		zUnionWithWeightedKeysResult, err := client.ZUnionWithScores(context.Background(),
			options.WeightedKeys{
				KeyWeightPairs: []options.KeyWeightPair{
					{Key: key1, Weight: 3.0},
					{Key: key2, Weight: 2.0},
				},
			},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 3.0}, {Member: "three", Score: 6.0}, {Member: "two", Score: 13.0}},
			zUnionWithWeightedKeysResult,
		)

		// non-existent key - empty union
		zUnionWithNonExistentKeyResult, err := client.ZUnionWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key3}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "two", Score: 2.0}},
			zUnionWithNonExistentKeyResult,
		)

		// empty key list - empty union
		zUnionWithEmptyKeyArray, err := client.ZUnionWithScores(context.Background(), options.KeyArray{Keys: []string{}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NotNil(suite.T(), err)
		assert.Empty(suite.T(), zUnionWithEmptyKeyArray)

		// key exists but not a set
		_, err = client.Set(context.Background(), key3, "value")
		suite.NoError(err)

		_, err = client.ZUnion(context.Background(), options.KeyArray{Keys: []string{key1, key3}})
		suite.Error(err)

		_, err = client.ZUnionWithScores(context.Background(),
			options.KeyArray{Keys: []string{key1, key3}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZUnionStoreAndZUnionStoreWithOptions() {
	suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-" + uuid.New().String()
		key2 := "{key}-" + uuid.New().String()
		key3 := "{key}-" + uuid.New().String()
		dest := "{key}-" + uuid.New().String()
		memberScoreMap1 := map[string]float64{
			"one": 1.0,
			"two": 2.0,
		}
		memberScoreMap2 := map[string]float64{
			"two":   3.5,
			"three": 3.0,
		}

		// Add members to sorted sets
		res, err := client.ZAdd(context.Background(), key1, memberScoreMap1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		res, err = client.ZAdd(context.Background(), key2, memberScoreMap2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		zUnionStoreResult, err := client.ZUnionStore(context.Background(), dest, options.KeyArray{Keys: []string{key1, key2}})
		assert.NoError(suite.T(), err)
		zRangeZUnionDest, err := client.ZRange(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zUnionStoreResult)
		assert.Equal(suite.T(), []string{"one", "three", "two"}, zRangeZUnionDest)

		// Union with scores
		zUnionStoreWithScoresResult, err := client.ZUnionStoreWithOptions(
			context.Background(),
			dest,
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		zRangeDest, err := client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zUnionStoreWithScoresResult)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}, {Member: "two", Score: 5.5}},
			zRangeDest,
		)

		// Union results with max aggregate
		zUnionStoreWithMaxAggregateResult, err := client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateMax),
		)
		assert.NoError(suite.T(), err)
		zRangeDest, err = client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zUnionStoreWithMaxAggregateResult)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}, {Member: "two", Score: 3.5}},
			zRangeDest,
		)

		// Union results with min aggregate
		zUnionStoreWithMinAggregateResult, err := client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateMin),
		)
		assert.NoError(suite.T(), err)
		zRangeDest, err = client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zUnionStoreWithMinAggregateResult)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "two", Score: 2.0}, {Member: "three", Score: 3.0}},
			zRangeDest,
		)

		// Union results with sum aggregate
		zUnionStoreWithSumAggregateResult, err := client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.KeyArray{Keys: []string{key1, key2}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		zRangeDest, err = client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zUnionStoreWithSumAggregateResult)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "three", Score: 3.0}, {Member: "two", Score: 5.5}},
			zRangeDest,
		)

		// Scores are multiplied by a 2.0 weight for key1 and key2 during aggregation
		zUnionStoreWithWeightedKeysResult, err := client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.WeightedKeys{
				KeyWeightPairs: []options.KeyWeightPair{
					{Key: key1, Weight: 3.0},
					{Key: key2, Weight: 2.0},
				},
			},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		zRangeDest, err = client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zUnionStoreWithWeightedKeysResult)
		assert.Equal(
			suite.T(),
			[]models.MemberAndScore{{Member: "one", Score: 3.0}, {Member: "three", Score: 6.0}, {Member: "two", Score: 13.0}},
			zRangeDest,
		)

		// non-existent key - empty union
		zUnionStoreWithNonExistentKeyResult, err := client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.KeyArray{Keys: []string{key1, key3}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NoError(suite.T(), err)
		zRangeDest, err = client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), zUnionStoreWithNonExistentKeyResult)
		assert.Equal(suite.T(), []models.MemberAndScore{{Member: "one", Score: 1.0}, {Member: "two", Score: 2.0}}, zRangeDest)

		// empty key list - empty union
		_, err = client.ZRem(context.Background(), dest, []string{"one", "two"}) // Flush previous results
		assert.NoError(suite.T(), err)
		zUnionStoreWithEmptyKeyArray, err := client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.KeyArray{Keys: []string{}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		assert.NotNil(suite.T(), err)
		zRangeDest, err = client.ZRangeWithScores(context.Background(), dest, options.NewRangeByIndexQuery(0, -1))
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), zUnionStoreWithEmptyKeyArray)
		assert.Empty(suite.T(), zRangeDest)

		// key exists but not a set
		_, err = client.Set(context.Background(), key3, "value")
		suite.NoError(err)

		_, err = client.ZUnionStore(context.Background(), dest, options.KeyArray{Keys: []string{key1, key3}})
		suite.Error(err)

		_, err = client.ZUnionStoreWithOptions(context.Background(),
			dest,
			options.KeyArray{Keys: []string{key1, key3}},
			*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZInterCard() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}:1-" + uuid.NewString()
		key2 := "{key}:2-" + uuid.NewString()
		key3 := "{key}:3-" + uuid.NewString()

		membersScores1 := map[string]float64{
			"a": 1.0,
			"b": 2.0,
			"c": 3.0,
		}
		membersScores2 := map[string]float64{
			"b": 1.0,
			"c": 2.0,
			"d": 3.0,
		}

		zAddResult1, err := client.ZAdd(context.Background(), key1, membersScores1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zAddResult1)

		zAddResult2, err := client.ZAdd(context.Background(), key2, membersScores2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), zAddResult2)

		res, err := client.ZInterCard(context.Background(), []string{key1, key2})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		res, err = client.ZInterCard(context.Background(), []string{key1, key3})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), res)

		res, err = client.ZInterCardWithOptions(
			context.Background(),
			[]string{key1, key2},
			*options.NewZInterCardOptions().SetLimit(0),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		res, err = client.ZInterCardWithOptions(
			context.Background(),
			[]string{key1, key2},
			*options.NewZInterCardOptions().SetLimit(1),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), res)

		res, err = client.ZInterCardWithOptions(
			context.Background(),
			[]string{key1, key2},
			*options.NewZInterCardOptions().SetLimit(3),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), res)

		// key exists but not a set
		_, err = client.Set(context.Background(), key3, "bar")
		assert.NoError(suite.T(), err)

		_, err = client.ZInterCardWithOptions(
			context.Background(),
			[]string{key1, key3},
			*options.NewZInterCardOptions().SetLimit(3),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestZLexCount() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		suite.SkipIfServerVersionLowerThan("6.2.0", suite.T())
		t := suite.T()
		key1 := "{testKey}:1-" + uuid.New().String()
		key2 := "{testKey}:3-" + uuid.New().String()

		// add members to sorted sets
		client.ZAdd(context.Background(), key1, map[string]float64{"a": 1.0, "b": 2.0, "c": 3.0})

		// count members in range a exclusive to c inclusive
		result, err := client.ZLexCount(context.Background(),
			key1,
			*options.NewRangeByLexQuery(
				options.NewLexBoundary("a", false),
				options.NewLexBoundary("c", true),
			),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), result)

		// count members in range negative to positive infinity
		result, err = client.ZLexCount(context.Background(),
			key1,
			*options.NewRangeByLexQuery(
				options.NewInfiniteLexBoundary("-"),
				options.NewInfiniteLexBoundary("+"),
			),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), result)

		// count members in range negative infinity to c inclusive
		result, err = client.ZLexCount(context.Background(),
			key1,
			*options.NewRangeByLexQuery(
				options.NewInfiniteLexBoundary("-"),
				options.NewLexBoundary("c", true),
			),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(3), result)

		// non-existent key
		result, err = client.ZLexCount(context.Background(),
			key2,
			*options.NewRangeByLexQuery(
				options.NewLexBoundary("a", false),
				options.NewLexBoundary("c", true),
			),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(0), result)

		// key exists but not a set
		_, err = client.Set(context.Background(), key2, "value")
		assert.NoError(t, err)

		_, err = client.ZLexCount(context.Background(),
			key2,
			*options.NewRangeByLexQuery(
				options.NewLexBoundary("a", false),
				options.NewLexBoundary("c", true),
			),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGeoAdd() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		t := suite.T()
		key1 := "{testKey}:1-" + uuid.New().String()
		key2 := "{testKey}:2-" + uuid.New().String()

		// Test basic GEOADD
		membersToCoordinates := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
		}

		result, err := client.GeoAdd(context.Background(), key1, membersToCoordinates)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), result)

		// Test with NX option (only if not exists)
		membersToCoordinates = map[string]options.GeospatialData{
			"Catania": {Longitude: 15.087269, Latitude: 39},
		}
		result, err = client.GeoAddWithOptions(context.Background(),
			key1,
			membersToCoordinates,
			*options.NewGeoAddOptions().SetConditionalChange(constants.OnlyIfDoesNotExist),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(0), result)

		// Test with XX option (only if exists)
		result, err = client.GeoAddWithOptions(context.Background(),
			key1,
			membersToCoordinates,
			*options.NewGeoAddOptions().SetConditionalChange(constants.OnlyIfExists),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(0), result)

		// Test with CH option (change coordinates)
		membersToCoordinates = map[string]options.GeospatialData{
			"Catania":  {Longitude: 15.087269, Latitude: 40},
			"Tel-Aviv": {Longitude: 32.0853, Latitude: 34.7818},
		}
		result, err = client.GeoAddWithOptions(context.Background(),
			key1,
			membersToCoordinates,
			*options.NewGeoAddOptions().SetChanged(true),
		)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), result)

		// Test error case with wrong key type
		_, err = client.Set(context.Background(), key2, "bar")
		assert.NoError(t, err)

		_, err = client.GeoAddWithOptions(context.Background(),
			key2,
			membersToCoordinates,
			*options.NewGeoAddOptions().SetChanged(true),
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGeoDist() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		t := suite.T()
		key1 := uuid.New().String()
		key2 := uuid.New().String()
		member1 := "Palermo"
		member2 := "Catania"
		member3 := "NonExisting"
		expected := 166274.1516
		expectedKM := 166.2742
		delta := 1e-9

		// adding locations
		membersToCoordinates := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
		}
		result, err := client.GeoAdd(context.Background(), key1, membersToCoordinates)
		assert.NoError(t, err)
		assert.Equal(t, int64(2), result)

		// assert correct result with default metric
		actual, err := client.GeoDist(context.Background(), key1, member1, member2)
		assert.NoError(t, err)
		assert.LessOrEqual(t, float64(math.Abs(actual.Value()-expected)), float64(delta))

		// assert correct result with manual metric specification kilometers
		actualKM, err := client.GeoDistWithUnit(context.Background(), key1, member1, member2, constants.GeoUnitKilometers)
		assert.NoError(t, err)
		assert.LessOrEqual(t, math.Abs(actualKM.Value()-expectedKM), delta)

		// assert null result when member index is missing
		actual, _ = client.GeoDist(context.Background(), key1, member1, member3)
		assert.True(t, actual.IsNil())

		// key exists but holds a non-ZSET value
		_, err = client.Set(context.Background(), key2, "bar")
		suite.NoError(err)
		_, err = client.GeoDist(context.Background(), key2, member1, member2)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGeoAdd_InvalidArgs() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{testKey}:3-" + uuid.New().String()

		// Test empty members
		_, err := client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{})
		suite.Error(err)

		// Test invalid longitude (-181)
		_, err = client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{
			"Place": {Longitude: -181, Latitude: 0},
		})
		suite.Error(err)

		// Test invalid longitude (181)
		_, err = client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{
			"Place": {Longitude: 181, Latitude: 0},
		})
		suite.Error(err)

		// Test invalid latitude (86)
		_, err = client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{
			"Place": {Longitude: 0, Latitude: 86},
		})
		suite.Error(err)

		// Test invalid latitude (-86)
		_, err = client.GeoAdd(context.Background(), key, map[string]options.GeospatialData{
			"Place": {Longitude: 0, Latitude: -86},
		})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGeoHash() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.New().String()

		// Add some locations to the geo index
		membersToCoordinates := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
		}

		// Add the coordinates
		result, err := client.GeoAdd(context.Background(), key1, membersToCoordinates)
		suite.NoError(err)
		suite.Equal(int64(2), result)

		// Test getting geohash for multiple members
		geoHashResults, err := client.GeoHash(context.Background(), key1, []string{"Palermo", "Catania"})
		suite.NoError(err)
		suite.Equal(2, len(geoHashResults))
		suite.Equal(geoHashResults[0].Value(), "sqc8b49rny0")
		suite.Equal(geoHashResults[1].Value(), "sqdtr74hyu0")

		// Test getting geohash for non-existent members
		geoHashResults, err = client.GeoHash(context.Background(), key1, []string{"Gotham City"})
		suite.NoError(err)
		suite.Equal(1, len(geoHashResults))
		suite.True(geoHashResults[0].IsNil())

		// Test getting geohash for empty members
		geoHashResults, err = client.GeoHash(context.Background(), key1, []string{})
		suite.NoError(err)
		suite.Equal(0, len(geoHashResults))

		// Test with wrong key type
		wrongKey := "{testKey}:3-" + uuid.New().String()
		_, err = client.Set(context.Background(), wrongKey, "value")
		suite.NoError(err)
		_, err = client.GeoHash(context.Background(), wrongKey, []string{"Palermo"})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGetSet_SendLargeValues() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Run with a 5 second timeout
		RunWithTimeout(suite.T(), 5*time.Second, func(ctx context.Context) {
			key := suite.GenerateLargeUuid()
			value := suite.GenerateLargeUuid()
			suite.verifyOK(client.Set(ctx, key, value))
			result, err := client.Get(ctx, key)
			suite.NoError(err)
			assert.Equal(suite.T(), value, result.Value())
		})
	})
}

func (suite *GlideTestSuite) TestGeoPos() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{testKey}:1-" + uuid.New().String()
		key2 := "{testKey}:2-" + uuid.New().String()

		members := []string{"Palermo", "Catania"}
		expected := [][]float64{
			{13.36138933897018433, 38.11555639549629859},
			{15.08726745843887329, 37.50266842333162032},
		}

		// Add locations
		membersCoordinates := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
		}

		result, err := client.GeoAdd(context.Background(), key1, membersCoordinates)
		suite.NoError(err)
		suite.Equal(int64(2), result)

		// Get positions and verify
		actual, err := client.GeoPos(context.Background(), key1, members)
		suite.NoError(err)

		// Verify each coordinate with high precision
		for i, coords := range actual {
			suite.NotNil(coords)
			suite.Equal(2, len(coords))

			suite.InDeltaSlice(expected[i], coords, 1e-6)
		}

		// Test error case with wrong key type
		_, err = client.Set(context.Background(), key2, "geopos")
		suite.NoError(err)

		_, err = client.GeoPos(context.Background(), key2, members)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGeoSearch() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1-" + uuid.New().String()
		key2 := "{key}-2-" + uuid.New().String()

		// Setup test data
		members := []string{"Catania", "Palermo", "edge2", "edge1"}
		membersToCoordinates := map[string]options.GeospatialData{
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"edge2":   {Longitude: 17.241510, Latitude: 38.788135},
			"edge1":   {Longitude: 12.758489, Latitude: 38.788135},
		}

		expectedResults := []options.Location{
			{
				Name: "Catania",
				Dist: 56.4413,
				Hash: int64(3479447370796909),
				Coord: options.GeospatialData{
					Longitude: 15.087267458438873,
					Latitude:  37.50266842333162,
				},
			},
			{
				Name: "Palermo",
				Dist: 190.4424,
				Hash: int64(3479099956230698),
				Coord: options.GeospatialData{
					Longitude: 13.361389338970184,
					Latitude:  38.1155563954963,
				},
			},
			{
				Name: "edge2",
				Dist: 279.7403,
				Hash: int64(3481342659049484),
				Coord: options.GeospatialData{
					Longitude: 17.241510450839996,
					Latitude:  38.78813451624225,
				},
			},
			{
				Name: "edge1",
				Dist: 279.7405,
				Hash: int64(3479273021651468),
				Coord: options.GeospatialData{
					Longitude: 12.75848776102066,
					Latitude:  38.78813451624225,
				},
			},
		}

		// Add geospatial data
		result, err := client.GeoAdd(context.Background(), key1, membersToCoordinates)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(4), result)

		// Test search by box, unit: km, from a geospatial data point
		searchOrigin := options.GeoCoordOrigin{
			GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
		}
		searchShape := options.NewBoxSearchShape(400, 400, constants.GeoUnitKilometers)
		resultOpts := options.NewGeoSearchResultOptions().SetSortOrder(options.ASC)

		results, err := client.GeoSearchWithResultOptions(context.Background(), key1, &searchOrigin, *searchShape, *resultOpts)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), members, results)

		// Search with all options (WITHDIST, WITHHASH, WITHCOORD)
		searchOpts := options.NewGeoSearchInfoOptions().
			SetWithDist(true).
			SetWithHash(true).
			SetWithCoord(true)

		fullResults, err := client.GeoSearchWithFullOptions(
			context.Background(),
			key1,
			&searchOrigin,
			*searchShape,
			*resultOpts,
			*searchOpts,
		)
		assert.NoError(suite.T(), err)
		// Verify structure of results - exact values may vary slightly due to floating-point precision
		assert.Equal(suite.T(), len(expectedResults), len(fullResults))
		for i := range expectedResults {
			assert.Equal(suite.T(), expectedResults[i].Name, fullResults[i].Name)
			assert.Equal(suite.T(), expectedResults[i].Dist, fullResults[i].Dist)
			assert.Equal(suite.T(), expectedResults[i].Hash, fullResults[i].Hash)
			assert.InDelta(suite.T(), expectedResults[i].Coord.Latitude, fullResults[i].Coord.Latitude, 1e-6)
			assert.InDelta(suite.T(), expectedResults[i].Coord.Longitude, fullResults[i].Coord.Longitude, 1e-6)
		}

		// Test with count limiting result to 1
		resultOptsWithCount := options.NewGeoSearchResultOptions().
			SetSortOrder(options.ASC).
			SetCount(1)

		countResults, err := client.GeoSearchWithResultOptions(
			context.Background(),
			key1,
			&searchOrigin,
			*searchShape,
			*resultOptsWithCount,
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 1, len(countResults))
		assert.Equal(suite.T(), "Catania", countResults[0])

		// Test search by box from member, with distance included
		meters := float64(400 * 1000)
		expectedResults2 := []options.Location{
			{
				Name: "edge2",
				Dist: 236529.1799,
			},
			{
				Name: "Palermo",
				Dist: 166274.1516,
			},
			{
				Name: "Catania",
				Dist: 0.0,
			},
		}
		memberResults, err := client.GeoSearchWithFullOptions(context.Background(),
			key1,
			&options.GeoMemberOrigin{Member: "Catania"},
			*options.NewBoxSearchShape(meters, meters, constants.GeoUnitMeters),
			*options.NewGeoSearchResultOptions().SetSortOrder(options.DESC),
			*options.NewGeoSearchInfoOptions().SetWithDist(true),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), expectedResults2, memberResults)

		// Test search by box, unit: feet, from a member, with limited ANY count to 2, with hash
		feetValue := 400 * 3280.8399
		feetShape := options.NewBoxSearchShape(feetValue, feetValue, constants.GeoUnitFeet)
		feetResult, err := client.GeoSearchWithFullOptions(context.Background(),
			key1,
			&options.GeoMemberOrigin{Member: "Palermo"},
			*feetShape,
			*options.NewGeoSearchResultOptions().SetSortOrder(options.ASC).SetCount(2),
			*options.NewGeoSearchInfoOptions().SetWithHash(true),
		)
		expectedResults3 := []options.Location{
			{Name: "Palermo", Hash: int64(3479099956230698)},
			{Name: "edge1", Hash: int64(3479273021651468)},
		}
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 2, len(feetResult))
		assert.Equal(suite.T(), expectedResults3, feetResult)

		// Test search by radius with feet units from member
		feetRadius := 200 * 3280.8399

		feetResults, err := client.GeoSearchWithResultOptions(context.Background(),
			key1,
			&options.GeoMemberOrigin{Member: "Catania"},
			*options.NewCircleSearchShape(feetRadius, constants.GeoUnitFeet),
			*options.NewGeoSearchResultOptions().SetSortOrder(options.ASC),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{"Catania", "Palermo"}, feetResults)

		// Test search by radius with meters units from member
		metersRadius := 200 * 1000
		metersResults, err := client.GeoSearchWithResultOptions(context.Background(),
			key1,
			&options.GeoMemberOrigin{Member: "Catania"},
			*options.NewCircleSearchShape(float64(metersRadius), constants.GeoUnitMeters),
			*options.NewGeoSearchResultOptions().SetSortOrder(options.DESC),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{"Palermo", "Catania"}, metersResults)

		// Test search by radius with miles units from geospatial data
		milesResults, err := client.GeoSearchWithResultOptions(context.Background(),
			key1,
			&options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
			},
			*options.NewCircleSearchShape(175, constants.GeoUnitMiles),
			*options.NewGeoSearchResultOptions().SetSortOrder(options.DESC),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{"edge1", "edge2", "Palermo", "Catania"}, milesResults)

		// Test search by radius with kilometers units, with limited count and all options
		kmResults, err := client.GeoSearchWithFullOptions(context.Background(),
			key1,
			&options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
			},
			*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
			*options.NewGeoSearchResultOptions().SetSortOrder(options.ASC).SetCount(2),
			*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithHash(true).SetWithCoord(true),
		)
		assert.NoError(suite.T(), err)
		expectedKmResults := []options.Location{
			{
				Name: "Catania",
				Dist: 56.4413,
				Hash: int64(3479447370796909),
				Coord: options.GeospatialData{
					Longitude: 15.087267458438873,
					Latitude:  37.50266842333162,
				},
			},
			{
				Name: "Palermo",
				Dist: 190.4424,
				Hash: int64(3479099956230698),
				Coord: options.GeospatialData{
					Longitude: 13.361389338970184,
					Latitude:  38.1155563954963,
				},
			},
		}
		for i := range expectedKmResults {
			assert.Equal(suite.T(), expectedKmResults[i].Name, kmResults[i].Name)
			assert.Equal(suite.T(), expectedKmResults[i].Dist, kmResults[i].Dist)
			assert.Equal(suite.T(), expectedKmResults[i].Hash, kmResults[i].Hash)
			assert.InDelta(suite.T(), expectedKmResults[i].Coord.Latitude, kmResults[i].Coord.Latitude, 1e-6)
			assert.InDelta(suite.T(), expectedKmResults[i].Coord.Longitude, kmResults[i].Coord.Longitude, 1e-6)
		}

		// Test search with ANY option
		expectedAnyResults := []options.Location{
			{
				Name: "Palermo",
				Dist: 190.4424,
				Hash: int64(3479099956230698),
				Coord: options.GeospatialData{
					Longitude: 13.361389338970184,
					Latitude:  38.1155563954963,
				},
			},
		}
		anyResult, err := client.GeoSearchWithFullOptions(context.Background(),
			key1,
			&options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
			},
			*options.NewCircleSearchShape(200, constants.GeoUnitKilometers),
			*options.NewGeoSearchResultOptions().SetSortOrder(options.ASC).SetCount(1).SetIsAny(true),
			*options.NewGeoSearchInfoOptions().SetWithDist(true).SetWithHash(true).SetWithCoord(true),
		)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), expectedAnyResults, anyResult)

		// Test empty results - small area
		smallShape := options.NewBoxSearchShape(50, 50, constants.GeoUnitMeters)
		emptyResults1, err := client.GeoSearchWithResultOptions(context.Background(),
			key1,
			&options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
			},
			*smallShape,
			*options.NewGeoSearchResultOptions().SetSortOrder(options.ASC).SetCount(1),
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), emptyResults1)

		// Test empty results - very small radius
		tinyShape := options.NewCircleSearchShape(5, constants.GeoUnitMeters)
		emptyResults2, err := client.GeoSearchWithResultOptions(context.Background(),
			key1,
			&options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
			},
			*tinyShape,
			*resultOpts,
		)
		assert.NoError(suite.T(), err)
		assert.Empty(suite.T(), emptyResults2)

		// Test non-existing member error
		nonExistingMemberOrigin := &options.GeoMemberOrigin{Member: "non-existing-member"}
		_, err = client.GeoSearchWithResultOptions(context.Background(),
			key1,
			nonExistingMemberOrigin,
			*options.NewCircleSearchShape(100, constants.GeoUnitMeters),
			*resultOpts,
		)
		suite.Error(err)

		// Test wrong key type error
		_, err = client.Set(context.Background(), key2, "nonZSETvalue")
		suite.NoError(err)
		_, err = client.GeoSearchWithResultOptions(context.Background(),
			key2,
			&options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
			},
			*options.NewCircleSearchShape(100, constants.GeoUnitMeters),
			*resultOpts,
		)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestGeoSearchStore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		sourceKey := "{key}-1-" + uuid.New().String()
		destinationKey := "{key}-2-" + uuid.New().String()
		key3 := "{key}-3-" + uuid.New().String()

		membersToCoordinates := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
			"edge2":   {Longitude: 17.241510, Latitude: 38.788135},
			"edge1":   {Longitude: 12.758489, Latitude: 38.788135},
		}
		// Expected results arrays
		expectedArray := []models.MemberAndScore{
			{Member: "Palermo", Score: 3479099956230698.0},
			{Member: "edge1", Score: 3479273021651468.0},
			{Member: "Catania", Score: 3479447370796909.0},
			{Member: "edge2", Score: 3481342659049484.0},
		}
		expectedArray2 := []models.MemberAndScore{
			{Member: "Catania", Score: 56.4412578701582},
			{Member: "Palermo", Score: 190.44242984775784},
			{Member: "edge2", Score: 279.7403417843143},
			{Member: "edge1", Score: 279.7404521356343},
		}
		expectedArray3 := []models.MemberAndScore{
			{Member: "Palermo", Score: 3479099956230698.0},
			{Member: "Catania", Score: 3479447370796909.0},
		}
		// Add geospatial data
		result, err := client.GeoAdd(context.Background(), sourceKey, membersToCoordinates)
		suite.NoError(err)
		suite.Equal(int64(4), result)

		// Test storing results of a box search, from a geospatial data point
		searchOrigin := &options.GeoCoordOrigin{
			GeospatialData: options.GeospatialData{Longitude: 15, Latitude: 37},
		}
		boxShape := options.NewBoxSearchShape(400, 400, constants.GeoUnitKilometers)

		count, err := client.GeoSearchStore(context.Background(), destinationKey, sourceKey, searchOrigin, *boxShape)
		suite.NoError(err)
		suite.Equal(int64(4), count)

		// Verify stored results
		zRangeResult, err := client.ZRangeWithScores(context.Background(), destinationKey, options.NewRangeByIndexQuery(0, -1))
		suite.NoError(err)
		suite.Equal(expectedArray, zRangeResult)

		// Test storing results of a box search, unit: kilometers, from a geospatial data point, with distance
		count, err = client.GeoSearchStoreWithInfoOptions(context.Background(),
			destinationKey,
			sourceKey,
			searchOrigin,
			*boxShape,
			*options.NewGeoSearchStoreInfoOptions().SetStoreDist(true),
		)
		suite.NoError(err)
		suite.Equal(int64(4), count)

		// Verify stored results with distance
		zRangeResultWithDist, err := client.ZRangeWithScores(
			context.Background(),
			destinationKey,
			options.NewRangeByIndexQuery(0, -1),
		)
		suite.NoError(err)
		for i := range expectedArray2 {
			suite.InDelta(expectedArray2[i].Score, zRangeResultWithDist[i].Score, 1e-6)
		}

		// Test storing results of a box search, unit: kilometers, from a geospatial data point, with count
		count, err = client.GeoSearchStoreWithResultOptions(context.Background(),
			destinationKey,
			sourceKey,
			searchOrigin,
			*boxShape,
			*options.NewGeoSearchResultOptions().SetCount(2),
		)
		suite.NoError(err)
		suite.Equal(int64(2), count)

		// Verify stored results with count
		zRangeResultWithCount, err := client.ZRangeWithScores(
			context.Background(),
			destinationKey,
			options.NewRangeByIndexQuery(0, -1),
		)
		suite.NoError(err)
		suite.Equal(
			[]models.MemberAndScore{
				{Member: "Palermo", Score: 3479099956230698},
				{Member: "Catania", Score: 3479447370796909},
			},
			zRangeResultWithCount,
		)

		// Test storing results of a radius search, unit: feet, from a member
		feetValue := 200 * 3280.8399
		count, err = client.GeoSearchStoreWithResultOptions(context.Background(),
			destinationKey,
			sourceKey,
			&options.GeoMemberOrigin{Member: "Catania"},
			*options.NewCircleSearchShape(feetValue, constants.GeoUnitFeet),
			*options.NewGeoSearchResultOptions().SetCount(2),
		)
		suite.NoError(err)
		suite.Equal(int64(2), count)

		// Verify stored results with count
		zRangeResultWithCount, err = client.ZRangeWithScores(
			context.Background(),
			destinationKey,
			options.NewRangeByIndexQuery(0, -1),
		)
		suite.NoError(err)
		suite.Equal(expectedArray3, zRangeResultWithCount)

		// Test storing results of a search that returns 0 results
		count, err = client.GeoSearchStore(context.Background(),
			destinationKey,
			sourceKey,
			searchOrigin,
			*options.NewCircleSearchShape(1, constants.GeoUnitMeters),
		)
		suite.NoError(err)
		suite.Equal(int64(0), count)
		zRangeResultZero, err := client.ZRangeWithScores(
			context.Background(),
			destinationKey,
			options.NewRangeByIndexQuery(0, -1),
		)
		suite.NoError(err)
		suite.Equal([]models.MemberAndScore{}, zRangeResultZero)

		// Test storing results of a search with ANY option
		count, err = client.GeoSearchStoreWithResultOptions(context.Background(),
			destinationKey,
			sourceKey,
			searchOrigin,
			*boxShape,
			*options.NewGeoSearchResultOptions().SetIsAny(true),
		)
		suite.NoError(err)
		suite.Equal(int64(4), count)
		zRangeResultANY, err := client.ZRangeWithScores(
			context.Background(),
			destinationKey,
			options.NewRangeByIndexQuery(0, -1),
		)
		suite.NoError(err)
		expectedANYResults := []models.MemberAndScore{
			{Member: "Palermo", Score: 3479099956230698.0},
			{Member: "edge1", Score: 3479273021651468.0},
			{Member: "Catania", Score: 3479447370796909.0},
			{Member: "edge2", Score: 3481342659049484.0},
		}
		suite.Equal(expectedANYResults, zRangeResultANY)

		// member does not exist
		nonExistingMemberOrigin := &options.GeoMemberOrigin{Member: "non-existing-member"}
		_, err = client.GeoSearchStore(context.Background(), destinationKey, sourceKey, nonExistingMemberOrigin, *boxShape)
		suite.Error(err)

		// key exists but holds a non-ZSET value
		_, err = client.Set(context.Background(), key3, "nonZSETvalue")
		suite.NoError(err)
		_, err = client.GeoSearchStore(context.Background(), destinationKey, key3, searchOrigin, *boxShape)
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestBZPopMax() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()

		res1, err := client.BZPopMax(context.Background(), []string{key1}, 100*time.Millisecond)
		suite.NoError(err)
		assert.True(suite.T(), res1.IsNil())

		membersScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}

		res2, err := client.ZAdd(context.Background(), key1, membersScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res2)

		res3, err := client.BZPopMax(context.Background(), []string{key1}, 100*time.Millisecond)
		suite.NoError(err)
		assert.Equal(suite.T(), models.KeyWithMemberAndScore{Key: key1, Member: "three", Score: 3.0}, res3.Value())
	})
}

func (suite *GlideTestSuite) TestZMPop() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		res1, err := client.ZMPop(context.Background(), []string{key1}, constants.MIN)
		suite.NoError(err)
		assert.True(suite.T(), res1.IsNil())

		membersScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
		}
		res2, err := client.ZAdd(context.Background(), key1, membersScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(3), res2)

		res3, err := client.ZAdd(context.Background(), key2, map[string]float64{
			"four": 4.0,
			"five": 5.0,
		})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res3)

		// Pop minimum value from key1
		res4, err := client.ZMPop(context.Background(), []string{key1}, constants.MIN)
		suite.NoError(err)
		assert.Equal(suite.T(), key1, res4.Value().Key)
		assert.ElementsMatch(
			suite.T(),
			[]models.MemberAndScore{
				{Member: "one", Score: 1.0},
			},
			res4.Value().MembersAndScores,
		)

		// Pop maximum value from key2
		res5, err := client.ZMPop(context.Background(), []string{key2}, constants.MAX)
		suite.NoError(err)
		assert.Equal(suite.T(), key2, res5.Value().Key)
		assert.ElementsMatch(
			suite.T(),
			[]models.MemberAndScore{
				{Member: "five", Score: 5.0},
			},
			res5.Value().MembersAndScores,
		)

		// pop from an empty key3
		res6, err := client.ZMPop(context.Background(), []string{key3}, constants.MIN)
		suite.NoError(err)
		assert.True(suite.T(), res6.IsNil())
	})
}

func (suite *GlideTestSuite) TestZMPopWithOptions() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{key}-1" + uuid.NewString()
		key2 := "{key}-2" + uuid.NewString()
		key3 := "{key}-3" + uuid.NewString()

		opts := *options.NewZMPopOptions().SetCount(2)

		res1, err := client.ZMPopWithOptions(context.Background(), []string{key1}, constants.MIN, opts)
		suite.NoError(err)
		assert.True(suite.T(), res1.IsNil())

		membersScoreMap := map[string]float64{
			"one":   1.0,
			"two":   2.0,
			"three": 3.0,
			"four":  4.0,
		}
		res2, err := client.ZAdd(context.Background(), key1, membersScoreMap)
		suite.NoError(err)
		assert.Equal(suite.T(), int64(4), res2)

		res3, err := client.ZAdd(context.Background(), key2, map[string]float64{
			"a": 10.0,
			"b": 20.0,
		})
		suite.NoError(err)
		assert.Equal(suite.T(), int64(2), res3)

		res4, err := client.ZMPopWithOptions(context.Background(), []string{key1}, constants.MIN, opts)
		suite.NoError(err)
		assert.Equal(suite.T(), key1, res4.Value().Key)
		assert.ElementsMatch(
			suite.T(),
			[]models.MemberAndScore{
				{Member: "one", Score: 1.0},
				{Member: "two", Score: 2.0},
			},
			res4.Value().MembersAndScores,
		)

		opts10 := *options.NewZMPopOptions().SetCount(10)
		res5, err := client.ZMPopWithOptions(context.Background(), []string{key1}, constants.MIN, opts10)
		suite.NoError(err)
		assert.Equal(suite.T(), key1, res5.Value().Key)
		assert.ElementsMatch(
			suite.T(),
			[]models.MemberAndScore{
				{Member: "three", Score: 3.0},
				{Member: "four", Score: 4.0},
			},
			res5.Value().MembersAndScores,
		)

		opts1 := *options.NewZMPopOptions().SetCount(1)
		res6, err := client.ZMPopWithOptions(context.Background(), []string{key2}, constants.MAX, opts1)
		suite.NoError(err)
		assert.Equal(suite.T(), key2, res6.Value().Key)
		assert.ElementsMatch(
			suite.T(),
			[]models.MemberAndScore{
				{Member: "b", Score: 20.0},
			},
			res6.Value().MembersAndScores,
		)

		res7, err := client.ZMPopWithOptions(context.Background(), []string{key3}, constants.MIN, opts1)
		suite.NoError(err)
		assert.True(suite.T(), res7.IsNil())
	})
}

func (suite *GlideTestSuite) TestInvokeScriptWithoutRoute() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := uuid.NewString()
		key2 := uuid.NewString()

		// Test a script that returns a string without keys and args.
		script1 := options.NewScript("return 'Hello'")
		response1, err := client.InvokeScript(context.Background(), *script1)
		suite.NoError(err)
		assert.Equal(suite.T(), "Hello", response1)

		// Test script that sets a key with value.
		script2 := options.NewScript("return redis.call('SET', KEYS[1], ARGV[1])")

		// Create Script options for setting key1
		scriptOptions := options.NewScriptOptions()
		scriptOptions.WithKeys([]string{key1}).WithArgs([]string{"value1"})
		setResponse, err := client.InvokeScriptWithOptions(context.Background(), *script2, *scriptOptions)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", setResponse)

		// Set another key, key2 with the same script
		scriptOptions2 := options.NewScriptOptions()
		scriptOptions2.WithKeys([]string{key2}).WithArgs([]string{"value2"})
		setResponse2, err := client.InvokeScriptWithOptions(context.Background(), *script2, *scriptOptions2)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", setResponse2)
		script2.Close()

		// Test script that gets a key's value
		script3 := options.NewScript("return redis.call('GET', KEYS[1])")

		// Create ClusterScriptOptions for getting key1
		scriptOptions3 := options.NewScriptOptions()
		scriptOptions3.WithKeys([]string{key1})
		getResponse1, err := client.InvokeScriptWithOptions(context.Background(), *script3, *scriptOptions3)
		suite.NoError(err)
		assert.Equal(suite.T(), "value1", getResponse1)

		// Get another key's value
		scriptOptions4 := options.NewScriptOptions()
		scriptOptions4.WithKeys([]string{key2})
		getResponse2, err := client.InvokeScriptWithOptions(context.Background(), *script3, *scriptOptions4)
		assert.Equal(suite.T(), "value2", getResponse2)
		suite.NoError(err)
		script3.Close()
	})
}

func (suite *GlideTestSuite) TestScriptFlush() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
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

		// Check that the script no longer exists
		result, err = client.ScriptExists(context.Background(), []string{scriptHash})
		suite.NoError(err)
		assert.Equal(suite.T(), []bool{false}, result)

		// Test with ASYNC mode
		_, err = client.InvokeScript(context.Background(), *script)
		suite.NoError(err)

		asyncMode := options.FlushMode(options.ASYNC)
		flushResult, err = client.ScriptFlushWithMode(context.Background(), asyncMode)
		suite.NoError(err)
		assert.Equal(suite.T(), "OK", flushResult)

		result, err = client.ScriptExists(context.Background(), []string{scriptHash})
		suite.NoError(err)
		assert.Equal(suite.T(), []bool{false}, result)

		script.Close()
	})
}

func (suite *GlideTestSuite) TestScriptShow() {
	suite.SkipIfServerVersionLowerThan("8.0.0", suite.T())

	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create a unique script code
		uuid1 := uuid.NewString()
		code := fmt.Sprintf("return '%s'", uuid1[:5])
		script := options.NewScript(code)

		// Load the script
		_, err := client.InvokeScript(context.Background(), *script)
		suite.NoError(err)

		// Get the SHA1 digest of the script
		sha1 := script.GetHash()

		// Add a small delay to allow cluster cache synchronization
		time.Sleep(100 * time.Millisecond)

		// Test with String
		scriptSource, err := client.ScriptShow(context.Background(), sha1)
		suite.NoError(err)
		assert.Equal(suite.T(), code, scriptSource)

		// Test with non-existing SHA1
		nonExistingSha1 := uuid.NewString()
		_, err = client.ScriptShow(context.Background(), nonExistingSha1)
		assert.NotNil(suite.T(), err)

		// Clean up
		script.Close()
	})
}

func (suite *GlideTestSuite) TestRegisterClientNameAndVersion() {
	suite.SkipIfServerVersionLowerThan("7.2.0", suite.T())
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		result := sendWithCustomCommand(
			suite,
			client,
			[]string{"CLIENT", "INFO"},
			"Can't send CLIENT INFO as a custom command",
		)

		var infoStr string
		switch v := result.(type) {
		case string:
			infoStr = v
		case models.ClusterValue[any]:
			infoStr = v.SingleValue().(string)
		}
		assert.Contains(suite.T(), infoStr, "lib-name=GlideGo", "lib-name not found or incorrect")
		assert.Regexp(suite.T(), "lib-ver=unknown|lib-ver=v", infoStr, "lib-ver not found or incorrect")
	})
}
