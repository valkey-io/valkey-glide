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

func CreateStringTest(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{stringKey}-"
	atomicKeyPrefix := prefix
	if !isAtomic {
		atomicKeyPrefix = ""
	}

	atomicKey1 := atomicKeyPrefix + "1-" + uuid.NewString()
	multiKey1 := prefix + "2-" + uuid.NewString()
	multiKey2 := prefix + "3-" + uuid.NewString()

	value1 := "value-1-" + uuid.NewString()

	batch.Set(atomicKey1, value1)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(atomicKey1, value1)"})

	batch.SetWithOptions(atomicKey1, value1, *options.NewSetOptions().SetOnlyIfExists())
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: "OK", TestName: "SetWithOptions(atomicKey1, value1, OnlyIfExists)"},
	)

	batch.Get(atomicKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: value1, TestName: "Get(atomicKey1)"})

	batch.GetEx(atomicKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: value1, TestName: "GetEx(atomicKey1)"})

	batch.Set(atomicKey1, value1)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(atomicKey1, value1)"})
	opts := options.NewGetExOptions().
		SetExpiry(options.NewExpiry().
			SetType(constants.Seconds).
			SetCount(5))
	batch.GetExWithOptions(atomicKey1, *opts)
	testData = append(testData, CommandTestData{ExpectedResponse: value1, TestName: "GetExWithOptions(atomicKey1, opts)"})

	batch.MSet(map[string]string{multiKey1: "value2"})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "MSet(multiKey1, value2)"})

	batch.MGet([]string{multiKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"value2"}, TestName: "MGet(key2)"})

	batch.MSetNX(map[string]string{multiKey2: "3"})
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "MSetNX(key3, 3)"})

	batch.Incr(multiKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "Incr(key3)"})

	batch.IncrBy(multiKey2, 2)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "IncrBy(key3, 2)"})

	batch.Set(atomicKey1, "3.5")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(atomicKey1, 3.5)"})
	batch.IncrByFloat(atomicKey1, 3.5)
	testData = append(testData, CommandTestData{ExpectedResponse: float64(7.0), TestName: "IncrByFloat(atomicKey1, 3.5)"})

	batch.Decr(multiKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(5), TestName: "Decr(multiKey2)"})

	batch.DecrBy(multiKey2, 2)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "DecrBy(multiKey2, 2)"})

	batch.Strlen(multiKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "Strlen(multiKey1)"})

	batch.SetRange(multiKey1, 2, "b")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(6), TestName: "SetRange(multiKey1, 2, b)"})

	batch.GetRange(multiKey1, 0, 6)
	testData = append(testData, CommandTestData{ExpectedResponse: "vabue2", TestName: "GetRange(multiKey1, 0, 6)"})

	batch.Append(multiKey1, "3")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(7), TestName: "Append(multiKey1, 3)"})

	batch.Set(multiKey2, "val")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(multiKey2, val)"})
	batch.LCS(multiKey1, multiKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: "va", TestName: "LCS(multiKey1, multiKey2)"})

	batch.LCSLen(multiKey1, multiKey2)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "LCSLen(multiKey1, multiKey2)"})

	batch.LCSWithOptions(multiKey1, multiKey2, *options.NewLCSIdxOptions().SetMinMatchLen(3))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: map[string]any{"len": int64(2), "matches": ([]any)(nil)},
			TestName:         "LCSWithOptions(multiKey1, multiKey2, opts)",
		},
	)

	batch.GetDel(atomicKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: "7", TestName: "GetDel(atomicKey1)"})

	return BatchTestData{CommandTestData: testData, TestName: "String commands"}
}

type BatchTestDataProvider func(*pipeline.ClusterBatch, bool) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
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
