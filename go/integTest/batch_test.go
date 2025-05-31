// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

func (suite *GlideTestSuite) runBatchTest(test func(client interfaces.BaseClientCommands, isAtomic bool)) {
	for _, client := range suite.getDefaultClients() {
		for _, isAtomic := range []bool{true, false} {
			suite.T().Run(fmt.Sprintf("%T isAtomic = %v", client, isAtomic)[7:], func(t *testing.T) {
				test(client, isAtomic)
			})
		}
	}
}

func (suite *GlideTestSuite) TestBatchTimeout() {
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic).CustomCommand([]string{"DEBUG", "sleep", "0.5"})
			opts := pipeline.NewClusterBatchOptions().WithRoute(config.RandomRoute).WithTimeout(100)
			// Expect a timeout exception on short timeout
			_, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			assert.Error(suite.T(), err)
			assert.IsType(suite.T(), &errors.TimeoutError{}, err)
			// Retry with a longer timeout and expect [OK]
			opts.WithTimeout(1000)
			res, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), []any{"OK"}, res)
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).CustomCommand([]string{"DEBUG", "sleep", "0.5"})
			opts := pipeline.NewStandaloneBatchOptions().WithTimeout(100)
			// Expect a timeout exception on short timeout
			_, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			assert.Error(suite.T(), err)
			assert.IsType(suite.T(), &errors.TimeoutError{}, err)
			// Retry with a longer timeout and expect [OK]
			opts.WithTimeout(1000)
			res, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), []any{"OK"}, res)
		}
	})
}

func (suite *GlideTestSuite) TestBatchRaiseOnError() {
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		key1 := "{BatchRaiseOnError}" + uuid.NewString()
		key2 := "{BatchRaiseOnError}" + uuid.NewString()

		var res []any
		var err1 error
		var err2 error

		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic).
				Set(key1, "hello").
				CustomCommand([]string{"lpop", key1}).
				CustomCommand([]string{"del", key1}).
				CustomCommand([]string{"rename", key1, key2})

			_, err1 = c.Exec(context.Background(), *batch, true)
			res, err2 = c.Exec(context.Background(), *batch, false)

		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).
				Set(key1, "hello").
				CustomCommand([]string{"lpop", key1}).
				CustomCommand([]string{"del", key1}).
				CustomCommand([]string{"rename", key1, key2})

			_, err1 = c.Exec(context.Background(), *batch, true)
			res, err2 = c.Exec(context.Background(), *batch, false)
		}
		// First exception is raised, all data lost
		assert.Error(suite.T(), err1)
		assert.IsType(suite.T(), &errors.RequestError{}, err1)

		// Exceptions aren't raised, but stored in the result set
		assert.NoError(suite.T(), err2)
		assert.Len(suite.T(), res, 4)
		assert.Equal(suite.T(), "OK", res[0])
		assert.Equal(suite.T(), int64(1), res[2])
		assert.IsType(suite.T(), &errors.RequestError{}, res[1])
		assert.IsType(suite.T(), &errors.RequestError{}, res[3])
		assert.Contains(suite.T(), res[1].(*errors.RequestError).Error(), "wrong kind of value")
		assert.Contains(suite.T(), res[3].(*errors.RequestError).Error(), "no such key")
	})
}

func CreateStringTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{stringKey}-"
	if isAtomic {
		prefix = ""
	}

	key1 := prefix + "1-" + uuid.NewString()

	value1 := "value-1-" + uuid.NewString()

	batch.Set(key1, value1)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value1)"})
	batch.Get(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: value1, TestName: "Get(key1)"})

	return BatchTestData{CommandTestData: testData, TestName: "String commands"}
}

func CreateBitmapTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{bitmap}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}
	key := atomicPrefix + "key0" + uuid.New().String()
	bitopkey1 := atomicPrefix + "key1" + uuid.New().String()
	bitopkey2 := atomicPrefix + "key2" + uuid.New().String()
	bitfieldkey1 := atomicPrefix + "key3" + uuid.New().String()
	bitfieldkey2 := atomicPrefix + "key4" + uuid.New().String()
	destKey := prefix + "dest" + uuid.New().String()

	batch.SetBit(key, 7, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "SetBit(key, 7, 1)"})

	batch.GetBit(key, 7)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GetBit(key, 7)"})

	for i := int64(0); i < 6; i++ {
		batch.SetBit(key, i, 1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: int64(0), TestName: fmt.Sprintf("SetBit(key, %d, 1)", i)},
		)
	}

	batch.BitCount(key)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(7), TestName: "BitCount(key)"})

	batch.BitCountWithOptions(key, *options.NewBitCountOptions().SetStart(0).SetEnd(5))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(7), TestName: "BitCountWithOptions(key, 0, 5)"})

	batch.BitPos(key, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "BitPos(key, 1)"})

	batch.BitPosWithOptions(key, 1, *options.NewBitPosOptions().SetStart(5).SetEnd(6))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "BitPosWithOptions(key, 1, 5, 6)"})

	batch.BitPosWithOptions(key, 1, *options.NewBitPosOptions().SetStart(0).SetEnd(6))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "BitPosWithOptions(key, 1, 0, 6)"})

	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	batch.BitField(bitfieldkey1, commands)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{int64(0), int64(0), int64(1)}, TestName: "BitField(key, commands)"},
	)

	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	batch.BitField(bitfieldkey2, bfcommands)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{int64(0)}, TestName: "BitField(key, bfcommands)"})
	commands2 := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	batch.BitFieldRO(bitfieldkey2, commands2)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{int64(24)}, TestName: "BitFieldRO(key, commands2)"})

	bitopkey1 = prefix + bitopkey1
	bitopkey2 = prefix + bitopkey2
	batch.Set(bitopkey1, "foobar")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(bitopkey1, foobar)"})
	batch.Set(bitopkey2, "abcdef")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(bitopkey2, abcdef)"})

	batch.BitOp(options.AND, destKey, []string{bitopkey1, bitopkey2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(AND, destKey, bitopkey1, bitopkey2)"},
	)
	batch.BitOp(options.OR, destKey, []string{bitopkey1, bitopkey2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(OR, destKey, bitopkey1, bitopkey2)"},
	)
	batch.BitOp(options.XOR, destKey, []string{bitopkey1, bitopkey2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(XOR, destKey, bitopkey1, bitopkey2)"},
	)
	batch.BitOp(options.NOT, destKey, []string{bitopkey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(NOT, destKey, bitopkey1)"})

	return BatchTestData{CommandTestData: testData, TestName: "BitMap commands"}
}

func CreateGenericBaseTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{baseKey}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}

	slotHashedKey1 := prefix + "1-" + uuid.NewString()
	slotHashedKey2 := prefix + "2-" + uuid.NewString()
	singleNodeKey1 := atomicPrefix + "3-" + uuid.NewString()

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.Set(singleNodeKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(singleNodeKey1, value)"})
	batch.Get(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: "value", TestName: "Get(slotHashedKey1)"})

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del([slotHashedKey1])"})

	batch.Exists([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "Exists([slotHashedKey1])"})

	batch.Expire(slotHashedKey1, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "Expire(slotHashedKey1, 1)"})
	batch.Expire(singleNodeKey1, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Expire(singleNodeKey1, 1)"})

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.ExpireAt(slotHashedKey1, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "ExpireAt(slotHashedKey1, 0)"})

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.PExpire(slotHashedKey1, int64(5*1000))
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpire(slotHashedKey1, 5000)"})
	batch.PExpire(prefix+"nonExistentKey", int64(5*1000))
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "PExpire(badkey, 5000)"})

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.PExpireAt(slotHashedKey1, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpireAt(slotHashedKey1, 0)"})

	if serverVer >= "7.0.0" {
		batch.ExpireWithOptions(singleNodeKey1, 1, constants.HasExistingExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "ExpireWithOptions(singleNodeKey1, 1, HasExistingExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.ExpireAtWithOptions(slotHashedKey1, 0, constants.HasNoExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "ExpireAtWithOptions(slotHashedKey1, 0, HasNoExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.PExpireWithOptions(slotHashedKey1, int64(5*1000), constants.HasNoExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "PExpireWithOptions(slotHashedKey1, 5000, HasNoExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.PExpireAtWithOptions(slotHashedKey1, 0, constants.HasNoExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "PExpireAtWithOptions(slotHashedKey1, 0, HasNoExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.ExpireTime(slotHashedKey1)
		testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "ExpireTime(slotHashedKey1)"})

		batch.PExpireTime(slotHashedKey1)
		testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "PExpireTime(slotHashedKey1)"})
	}

	batch.TTL(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), CheckTypeOnly: true, TestName: "TTL(slotHashedKey1)"})

	batch.PTTL(slotHashedKey1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(-1), CheckTypeOnly: true, TestName: "PTTL(slotHashedKey1)"},
	)

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.Set(slotHashedKey2, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey2, value)"})
	batch.Unlink([]string{slotHashedKey1, slotHashedKey2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(2), TestName: "Unlink(slotHashedKey1, slotHashedKey2)"},
	)

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.Set(slotHashedKey2, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey2, value)"})
	batch.Touch([]string{slotHashedKey1, slotHashedKey2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Touch(slotHashedKey1, slotHashedKey2)"})

	batch.Set(slotHashedKey1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value1)"})
	batch.Type(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: "string", TestName: "Type(slotHashedKey1)"})

	batch.Rename(slotHashedKey1, slotHashedKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Rename(slotHashedKey1, slotHashedKey2)"})
	batch.Get(slotHashedKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: "value1", TestName: "Get(slotHashedKey2)"})

	batch.Set(slotHashedKey1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value1)"})
	batch.RenameNX(slotHashedKey1, slotHashedKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "RenameNX(slotHashedKey1, slotHashedKey2)"})

	batch.Set(slotHashedKey1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value1)"})
	batch.Expire(slotHashedKey1, 100)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Expire(slotHashedKey1, 100)"})
	batch.Persist(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Persist(slotHashedKey1)"})
	batch.TTL(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "TTL(slotHashedKey1)"})

	// TODO: TEST DUMP AND RESTORE

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.ObjectEncoding(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectEncoding(slotHashedKey1)"})

	batch.ObjectFreq(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectFreq(slotHashedKey1)"})

	batch.ObjectIdleTime(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectIdleTime(slotHashedKey1)"})

	batch.ObjectRefCount(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectRefCount(slotHashedKey1)"})

	batch.LPush(slotHashedKey1, []string{"3", "2", "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [3, 2, 1])"})
	batch.Sort(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"1", "2", "3"}, TestName: "Sort(slotHashedKey1)"})

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.LPush(slotHashedKey1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [c, b, a])"})
	batch.SortWithOptions(slotHashedKey1, *options.NewSortOptions().SetIsAlpha(true))
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"a", "b", "c"}, TestName: "SortWithOptions(slotHashedKey1, {Alpha: true})"},
	)

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.LPush(slotHashedKey1, []string{"3", "2", "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [3, 2, 1])"})
	sortDestKey := prefix + "sortDest-" + uuid.NewString()
	batch.SortStore(slotHashedKey1, sortDestKey)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(3), TestName: "SortStore(slotHashedKey1, sortDestKey)"},
	)

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.LPush(slotHashedKey1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [c, b, a])"})
	batch.SortStoreWithOptions(slotHashedKey1, sortDestKey, *options.NewSortOptions().SetIsAlpha(true))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: int64(3),
			TestName:         "SortStoreWithOptions(slotHashedKey1, sortDestKey, {Alpha: true})",
		},
	)

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.LPush(slotHashedKey1, []string{"3", "2", "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [3, 2, 1])"})

	if serverVer >= "7.0.0" {
		batch.SortReadOnly(slotHashedKey1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: []any{"1", "2", "3"}, TestName: "SortReadOnly(slotHashedKey1)"},
		)
	}

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.LPush(slotHashedKey1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [c, b, a])"})

	if serverVer >= "7.0.0" {
		batch.SortReadOnlyWithOptions(slotHashedKey1, *options.NewSortOptions().SetIsAlpha(true))
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []any{"a", "b", "c"},
				TestName:         "SortReadOnlyWithOptions(slotHashedKey1, {Alpha: true})",
			},
		)
	}

	batch.Wait(0, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), CheckTypeOnly: true, TestName: "Wait(0, 0)"})

	batch.Del([]string{slotHashedKey1, slotHashedKey2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Del(slotHashedKey1, slotHashedKey2)"})
	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.Copy(slotHashedKey1, slotHashedKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Copy(slotHashedKey1, slotHashedKey2)"})
	batch.Get(slotHashedKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: "value", TestName: "Get(slotHashedKey2) after Copy"})

	batch.Del([]string{slotHashedKey1, slotHashedKey2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Del(slotHashedKey1, slotHashedKey2)"})
	batch.Set(slotHashedKey1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value1)"})
	batch.Set(slotHashedKey2, "value2")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key2, value2)"})
	batch.CopyWithOptions(slotHashedKey1, slotHashedKey2, *options.NewCopyOptions().SetReplace())
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: true,
			TestName:         "CopyWithOptions(slotHashedKey1, slotHashedKey2, ReplaceDestination)",
		},
	)
	batch.Get(slotHashedKey2)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: "value1", TestName: "Get(slotHashedKey2) after CopyWithOptions"},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Generic Base commands"}
}

func CreateGenericClusterCommandTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{key}-"
	if !isAtomic {
		prefix = ""
	}
	key := prefix + "1-" + uuid.NewString()

	// Start clean
	batch.FlushAll()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "CustomCommand: FlushAll()"})

	batch.CustomCommand([]string{"SET", key, "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "CustomCommand: Set(key, 1)"})
	batch.Get(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "1", TestName: "Get(key1)"})

	batch.RandomKey()
	testData = append(testData, CommandTestData{ExpectedResponse: key, TestName: "RandomKey()"})

	// TODO: add Move in separate standalone batch tests

	return BatchTestData{CommandTestData: testData, TestName: "Generic commands"}
}

func CreateHashTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	// TODO: After adding and fixing converters, remove 'any' typing in ExpectedResponse
	testData := make([]CommandTestData, 0)
	prefix := "{HashKey}-"
	if !isAtomic {
		prefix = ""
	}
	key := prefix + "1-" + uuid.NewString()

	simpleMap := map[string]string{"k1": "value"}

	batch.HSet(key, simpleMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HSet(k1, simpleMap)"})
	batch.HGet(key, "k1")
	testData = append(testData, CommandTestData{ExpectedResponse: "value", TestName: "HGet(key, k1)"})

	batch.HGetAll(key)
	testData = append(testData, CommandTestData{ExpectedResponse: map[string]any{"k1": "value"}, TestName: "HGetAll(key)"})

	batch.HMGet(key, []string{"k1"})
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"value"}, TestName: "HMGet(k1)"})

	batch.HSetNX(key, "k1", "value2")
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "HSetNX(key, k1, value2)"})

	batch.HDel(key, []string{"k1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HDel(key, k1)"})

	batch.HSet(key, map[string]string{"field1": "value1", "field2": "value2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "HSet(key, multiple fields)"})
	batch.HLen(key)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "HLen(key)"})

	batch.HDel(key, []string{"field2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HDel(key, field2)"})
	batch.HVals(key)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"value1"}, TestName: "HVals(key)"})

	batch.HExists(key, "field1")
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "HExists(key, field1)"})
	batch.HExists(key, "nonexistent")
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "HExists(key, nonexistent)"})

	batch.HKeys(key)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"field1"}, TestName: "HKeys(key)"})

	batch.HStrLen(key, "field1")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "HStrLen(key, field1)"})

	batch.HSet(key, map[string]string{"counter": "10"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HSet(key, counter)"})
	batch.HIncrBy(key, "counter", 5)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(15), TestName: "HIncrBy(key, counter, 5)"})

	batch.HSet(key, map[string]string{"float_counter": "10.5"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HSet(key, float_counter)"})
	batch.HIncrByFloat(key, "float_counter", 1.5)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: float64(12), TestName: "HIncrByFloat(key, float_counter, 1.5)"},
	)

	batch.FlushAll()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FlushAll()"})
	batch.HScan(key, "0")
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"0", ([]any)(nil)}, TestName: "HScan(key, 0)"})

	batch.HSet(key, map[string]string{"counter": "10"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HSet(key, counter)"})
	batch.HRandField(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "counter", TestName: "HRandField(key)"})

	batch.HRandFieldWithCount(key, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"counter"}, TestName: "HRandFieldWithCount(key, 1)"})

	batch.HRandFieldWithCountWithValues(key, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{[]any{"counter", "10"}}, TestName: "HRandFieldWithCountWithValues(key, 1)"},
	)

	batch.HScanWithOptions(key, "0", *options.NewHashScanOptions().SetCount(1))
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"0", []any{"counter", "10"}}, TestName: "HScanWithOptions(key, 0, options)"},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Hash commands"}
}

func CreateHyperLogLogTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{hyperloglog}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}
	key1 := atomicPrefix + "key-1-" + uuid.NewString()
	key2 := atomicPrefix + "key-2-" + uuid.NewString()
	dest := atomicPrefix + "dest-" + uuid.NewString()

	batch.PfAdd(key1, []string{"val"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "PfAdd(key1, [val])"})

	batch.PfCount([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "PfCount([key1])"})

	batch.PfAdd(key1, []string{"val2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "PfAdd(key2, [val2])"})
	batch.PfMerge(prefix+dest, []string{prefix + key1, prefix + key2})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "PfMerge(dest, [key1 key2])"})

	return BatchTestData{CommandTestData: testData, TestName: "Hyperloglog commands"}
}

// ClusterBatch - The Batch object
// bool - isAtomic flag. True for transactions, false for pipeline
// string - The server version we are running on
type BatchTestDataProvider func(*pipeline.ClusterBatch, bool, string) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
		CreateBitmapTest,
		CreateGenericBaseTests,
		CreateGenericClusterCommandTests,
		CreateHashTest,
		CreateHyperLogLogTest,
	}
}

type CommandTestData struct {
	ExpectedResponse any
	CheckTypeOnly    bool
	TestName         string
}

type BatchTestData struct {
	CommandTestData []CommandTestData
	TestName        string
}

func (suite *GlideTestSuite) TestBatchCommandGroups() {
	for _, client := range suite.getDefaultClients() {
		clientType := fmt.Sprintf("%T", client)[7:]
		for _, isAtomic := range []bool{true, false} {
			for _, testProvider := range GetCommandGroupTestProviders() {
				batch := pipeline.NewClusterBatch(isAtomic)
				testData := testProvider(batch, isAtomic, suite.serverVersion)

				suite.T().Run(fmt.Sprintf("%s %s isAtomic = %v", testData.TestName, clientType, isAtomic), func(t *testing.T) {
					var res []any
					var err error
					switch c := client.(type) {
					case *glide.ClusterClient:
						res, err = c.Exec(context.Background(), *batch, true)
					case *glide.Client:
						// hacky hack ©
						standaloneBatch := pipeline.StandaloneBatch{BaseBatch: pipeline.BaseBatch[pipeline.StandaloneBatch]{Batch: batch.BaseBatch.Batch}}
						res, err = c.Exec(context.Background(), standaloneBatch, true)
					}
					assert.NoError(suite.T(), err)
					suite.verifyBatchTestResult(res, testData.CommandTestData)
				})
			}
		}
	}
}

func (suite *GlideTestSuite) verifyBatchTestResult(result []any, testData []CommandTestData) {
	assert.Equal(suite.T(), len(testData), len(result))
	for i := range result {
		if testData[i].CheckTypeOnly {
			assert.IsType(suite.T(), testData[i].ExpectedResponse, result[i], testData[i].TestName)
		} else {
			assert.Equal(suite.T(), testData[i].ExpectedResponse, result[i], testData[i].TestName)
		}
	}
}
