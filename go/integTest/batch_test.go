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

func CreateStringTest(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
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
	key := prefix + "key0" + uuid.New().String()
	bitopkey1 := prefix + "key1" + uuid.New().String()
	bitopkey2 := prefix + "key2" + uuid.New().String()
	bitfieldkey1 := prefix + "key3" + uuid.New().String()
	bitfieldkey2 := prefix + "key4" + uuid.New().String()
	destKey := prefix + "dest" + uuid.New().String()

	batch.SetBit(key, 7, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "SetBit(key, 7, 1)"})

	batch.GetBit(key, 7)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GetBit(key, 7)"})

	for i := int64(0); i < 6; i++ {
		batch.SetBit(key, i, 1)
		testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: fmt.Sprintf("SetBit(key, %d, 1)", i)})
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

	// TODO: FIND A BETTER WAY TO HANDLE BITFIELD RESULTS
	var expectedBfInterfaces []interface{}
	expectedBfInterfaces = append(expectedBfInterfaces, interface{}(int64(0)))
	expectedBfInterfaces = append(expectedBfInterfaces, interface{}(int64(0)))
	expectedBfInterfaces = append(expectedBfInterfaces, interface{}(int64(1)))
	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	batch.BitField(bitfieldkey1, commands)
	testData = append(testData, CommandTestData{ExpectedResponse: expectedBfInterfaces, TestName: "BitField(key, commands)"})

	var expectedBfInterfaces2 []interface{}
	var expectedBfInterfaces3 []interface{}
	expectedBfInterfaces2 = append(expectedBfInterfaces2, interface{}(int64(0)))
	expectedBfInterfaces3 = append(expectedBfInterfaces3, interface{}(int64(24)))
	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	batch.BitField(bitfieldkey2, bfcommands)
	testData = append(testData, CommandTestData{ExpectedResponse: expectedBfInterfaces2, TestName: "BitField(key, bfcommands)"})
	commands2 := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	batch.BitFieldRO(bitfieldkey2, commands2)
	testData = append(testData, CommandTestData{ExpectedResponse: expectedBfInterfaces3, TestName: "BitFieldRO(key, commands2)"})

	batch.Set(bitopkey1, "foobar")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(bitopkey1, foobar)"})
	batch.Set(bitopkey2, "abcdef")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(bitopkey2, abcdef)"})

	batch.BitOp(options.AND, destKey, []string{bitopkey1, bitopkey2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(AND, destKey, bitopkey1, bitopkey2)"})
	batch.BitOp(options.OR, destKey, []string{bitopkey1, bitopkey2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(OR, destKey, bitopkey1, bitopkey2)"})
	batch.BitOp(options.XOR, destKey, []string{bitopkey1, bitopkey2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(XOR, destKey, bitopkey1, bitopkey2)"})
	batch.BitOp(options.NOT, destKey, []string{bitopkey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "BitOp(NOT, destKey, bitopkey1)"})

	return BatchTestData{CommandTestData: testData, TestName: "BitMap commands"}
}

type BatchTestDataProvider func(*pipeline.ClusterBatch, bool) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
		CreateBitmapTest,
	}
}

type CommandTestData struct {
	ExpectedResponse any
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
				testData := testProvider(batch, isAtomic)

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
		assert.Equal(suite.T(), testData[i].ExpectedResponse, result[i], testData[i].TestName)
	}
}
