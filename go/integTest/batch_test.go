// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"testing"

	"github.com/google/uuid"
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
			suite.Error(err)
			suite.IsType(&errors.TimeoutError{}, err)
			// Retry with a longer timeout and expect [OK]
			opts.WithTimeout(1000)
			res, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			suite.NoError(err)
			suite.Equal([]any{"OK"}, res)
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).CustomCommand([]string{"DEBUG", "sleep", "0.5"})
			opts := pipeline.NewStandaloneBatchOptions().WithTimeout(100)
			// Expect a timeout exception on short timeout
			_, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			suite.Error(err)
			suite.IsType(&errors.TimeoutError{}, err)
			// Retry with a longer timeout and expect [OK]
			opts.WithTimeout(1000)
			res, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			suite.NoError(err)
			suite.Equal([]any{"OK"}, res)
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
		suite.Error(err1)
		suite.IsType(&errors.RequestError{}, err1)

		// Exceptions aren't raised, but stored in the result set
		suite.NoError(err2)
		suite.Len(res, 4)
		suite.Equal("OK", res[0])
		suite.Equal(int64(1), res[2])
		suite.IsType(&errors.RequestError{}, res[1])
		suite.IsType(&errors.RequestError{}, res[3])
		suite.Contains(res[1].(*errors.RequestError).Error(), "wrong kind of value")
		suite.Contains(res[3].(*errors.RequestError).Error(), "no such key")
	})
}

func (suite *GlideTestSuite) TestWatch_and_Unwatch() {
	suite.runWithDefaultClients(func(client1 interfaces.BaseClientCommands) {
		key1 := "{prefix}" + uuid.NewString()
		key2 := "{prefix}" + uuid.NewString()
		value := uuid.NewString()
		ctx := context.Background()
		suite.verifyOK(client1.Set(ctx, key1, value))
		var client2 interfaces.BaseClientCommands
		var transactionResult []any
		var err error
		switch client1.(type) {
		case *glide.ClusterClient:
			client2 = suite.defaultClusterClient()
		case *glide.Client:
			client2 = suite.defaultClient()
		}

		// Transaction executes command successfully with a read command on the watch key before
		// transaction is executed.
		suite.verifyOK(client1.Watch(ctx, []string{key1}))
		res, err := client2.Get(ctx, key1)
		suite.NoError(err)
		suite.Equal(value, res.Value())

		switch client := client1.(type) {
		case *glide.ClusterClient:
			transaction := pipeline.NewClusterBatch(true).Get(key1).Set(key1, uuid.NewString()).Get(key2)
			transactionResult, err = client.Exec(ctx, *transaction, true)
		case *glide.Client:
			transaction := pipeline.NewStandaloneBatch(true).Get(key1).Set(key1, uuid.NewString()).Get(key2)
			transactionResult, err = client.Exec(ctx, *transaction, true)
		}
		suite.NoError(err)
		suite.Equal([]any{value, "OK", nil}, transactionResult)

		suite.verifyOK(client1.Unwatch(ctx))

		// Returns `nil` when a watched key is modified before it is executed in a transaction command.
		// Transaction commands are not performed.
		suite.verifyOK(client1.Watch(ctx, []string{key1}))
		suite.verifyOK(client2.Set(ctx, key1, uuid.NewString()))

		switch client := client1.(type) {
		case *glide.ClusterClient:
			transaction := pipeline.NewClusterBatch(true).Set(key1, uuid.NewString())
			transactionResult, err = client.Exec(ctx, *transaction, true)
		case *glide.Client:
			transaction := pipeline.NewStandaloneBatch(true).Set(key1, uuid.NewString())
			transactionResult, err = client.Exec(ctx, *transaction, true)
		}
		suite.NoError(err)
		suite.Nil(transactionResult)

		client2.Close()

		// WATCH errors if no keys are given
		_, err = client1.Watch(ctx, []string{})
		suite.IsType(&errors.RequestError{}, err)
	})
}

func (suite *GlideTestSuite) TestWatch_and_Unwatch_cross_slot() {
	client := suite.defaultClusterClient()
	ctx := context.Background()

	suite.verifyOK(client.Watch(ctx, []string{"abc", "klm", "xyz"}))
	suite.verifyOK(client.UnwatchWithOptions(ctx, options.RouteOption{Route: config.AllNodes}))
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
					suite.NoError(err)
					suite.verifyBatchTestResult(res, testData.CommandTestData)
				})
			}
		}
	}
}

func (suite *GlideTestSuite) verifyBatchTestResult(result []any, testData []CommandTestData) {
	suite.Equal(len(testData), len(result))
	for i := range result {
		suite.Equal(testData[i].ExpectedResponse, result[i], testData[i].TestName)
	}
}
