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

func CreateBitmapTest(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
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
	key1 := "{key}-1-" + uuid.NewString()
	key2 := "{key}-2-" + uuid.NewString()

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.Set(key2, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key2, value)"})
	batch.Get(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: "value", TestName: "Get(key1)"})

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del([key1])"})

	batch.Exists([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "Exists([key1])"})
	batch.Exists([]string{key1, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Exists([key1, key2])"})

	batch.Expire(key1, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "Expire(key1, 1)"})
	batch.Expire(key2, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Expire(key2, 1)"})

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.ExpireAt(key1, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "ExpireAt(key1, 0)"})

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.PExpire(key1, int64(5*1000))
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpire(key1, 5000)"})

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.PExpireAt(key1, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpireAt(key1, 0)"})

	if serverVer >= "7.0.0" {
		batch.ExpireWithOptions(key2, 1, constants.HasExistingExpiry)
		testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "ExpireWithOptions(key2, 1, HasExistingExpiry)"})

		batch.Set(key1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
		batch.ExpireAtWithOptions(key1, 0, constants.HasNoExpiry)
		testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "ExpireAtWithOptions(key1, 0, HasNoExpiry)"})

		batch.Set(key1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
		batch.PExpireWithOptions(key1, int64(5*1000), constants.HasNoExpiry)
		testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpireWithOptions(key1, 5000, HasNoExpiry)"})

		batch.Set(key1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
		batch.PExpireAtWithOptions(key1, 0, constants.HasNoExpiry)
		testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpireAtWithOptions(key1, 0, HasNoExpiry)"})
	}

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.ExpireTime(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "ExpireTime(key1)"})

	batch.PExpireTime(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "PExpireTime(key1)"})

	batch.TTL(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "TTL(key1)"})

	batch.PTTL(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "PTTL(key1)"})

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.Set(key2, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key2, value)"})
	batch.Unlink([]string{key1, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Unlink(key1, key2)"})

	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.Set(key2, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key2, value)"})
	batch.Touch([]string{key1, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Touch(key1, key2)"})

	batch.Set(key1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value1)"})
	batch.Type(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: "string", TestName: "Type(key1)"})

	batch.Rename(key1, key2)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Rename(key1, key2)"})
	batch.Get(key2)
	testData = append(testData, CommandTestData{ExpectedResponse: "value1", TestName: "Get(key1)"})

	batch.Set(key1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value1)"})
	batch.RenameNX(key1, key2)
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "RenameNX(key1, key2)"})

	batch.Set(key1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value1)"})
	batch.Expire(key1, 100)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Expire(key1, 100)"})
	batch.Persist(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Persist(key1)"})
	batch.TTL(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "TTL(key1)"})

	// TODO: TEST DUMP AND RESTORE

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(key1)"})
	batch.ObjectEncoding(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectEncoding(key1)"})

	batch.ObjectFreq(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectFreq(key1)"})

	batch.ObjectIdleTime(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectIdleTime(key1)"})

	batch.ObjectRefCount(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: nil, TestName: "ObjectRefCount(key1)"})

	batch.LPush(key1, []string{"3", "2", "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(key1, [3, 2, 1])"})
	batch.Sort(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"1", "2", "3"}, TestName: "Sort(key1)"})

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(key1)"})
	batch.LPush(key1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(key1, [c, b, a])"})
	batch.SortWithOptions(key1, *options.NewSortOptions().SetIsAlpha(true))
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"a", "b", "c"}, TestName: "SortWithOptions(key1, {Alpha: true})"})

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(key1)"})
	batch.LPush(key1, []string{"3", "2", "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(key1, [3, 2, 1])"})
	sortDestKey := "{key}-sortDest-" + key1
	batch.SortStore(key1, sortDestKey)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "SortStore(key1, sortDestKey)"})

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(key1)"})
	batch.LPush(key1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(key1, [c, b, a])"})
	sortDestKey2 := "{key}-sortDest-" + key2
	batch.SortStoreWithOptions(key1, sortDestKey2, *options.NewSortOptions().SetIsAlpha(true))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "SortStoreWithOptions(key1, sortDestKey2, {Alpha: true})"})

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(key1)"})
	batch.LPush(key1, []string{"3", "2", "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(key1, [3, 2, 1])"})
	batch.SortReadOnly(key1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"1", "2", "3"}, TestName: "SortReadOnly(key1)"})

	batch.Del([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(key1)"})
	batch.LPush(key1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(key1, [c, b, a])"})
	batch.SortReadOnlyWithOptions(key1, *options.NewSortOptions().SetIsAlpha(true))
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"a", "b", "c"}, TestName: "SortReadOnlyWithOptions(key1, {Alpha: true})"})

	batch.Wait(0, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), CheckType: true, TestName: "Wait(0, 0)"})

	batch.Del([]string{key1, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Del(key1, key2)"})
	batch.Set(key1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value)"})
	batch.Copy(key1, key2)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Copy(key1, key2)"})
	batch.Get(key2)
	testData = append(testData, CommandTestData{ExpectedResponse: "value", TestName: "Get(key2) after Copy"})

	batch.Del([]string{key1, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "Del(key1, key2)"})
	batch.Set(key1, "value1")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key1, value1)"})
	batch.Set(key2, "value2")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(key2, value2)"})
	batch.CopyWithOptions(key1, key2, *options.NewCopyOptions().SetReplace())
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "CopyWithOptions(key1, key2, ReplaceDestination)"})
	batch.Get(key2)
	testData = append(testData, CommandTestData{ExpectedResponse: "value1", TestName: "Get(key2) after CopyWithOptions"})

	return BatchTestData{CommandTestData: testData, TestName: "Generic Base commands"}
}

func CreateHyperLogLogTest(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
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

type BatchTestDataProvider func(*pipeline.ClusterBatch, bool, string) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
		CreateBitmapTest,
		CreateGenericBaseTests,
		CreateHyperLogLogTest,
	}
}

type CommandTestData struct {
	ExpectedResponse any
	CheckType        bool
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
		if testData[i].CheckType {
			assert.IsType(suite.T(), testData[i].ExpectedResponse, result[i], testData[i].TestName)
		} else {
			assert.Equal(suite.T(), testData[i].ExpectedResponse, result[i], testData[i].TestName)
		}
	}
}
