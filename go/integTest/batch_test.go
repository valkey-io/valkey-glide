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

func CreateSetCommandsTests(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{set}-"
	atomicKey := prefix
	if !isAtomic {
		atomicKey = ""
	}

	key := atomicKey + "key-" + uuid.NewString()

	batch.SAdd(key, []string{"member1", "member2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "SAdd(key, [member1, member2])"})

	batch.SRem(key, []string{"member2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "SRem(key, [member2])"})

	batch.SMembers(key)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]struct{}{"member1": {}}, TestName: "SMembers(key)"},
	)

	batch.SCard(key)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "SCard(key)"})

	batch.SIsMember(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "SIsMember(key, member1)"})

	key2 := atomicKey + "key2-" + uuid.NewString()
	batch.SAdd(key2, []string{"member1", "member3"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "SAdd(key2, [member1, member3])"})
	batch.SDiff([]string{prefix + key, prefix + key2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]struct{}{}, TestName: "SDiff([prefix + key, prefix + key2])"},
	)

	batch.SAdd(prefix+key, []string{"member1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "SAdd(prefix + key, [member1])"})
	batch.SAdd(prefix+key2, []string{"member1", "member3"})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(2), TestName: "SAdd(prefix + key2, [member1, member3])"},
	)
	dest := prefix + "key3-" + uuid.NewString()
	batch.SDiffStore(dest, []string{prefix + key2, prefix + key})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(1), TestName: "SDiffStore(dest, [prefix + key2, prefix + key])"},
	)

	batch.SInter([]string{prefix + key, prefix + key2})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: map[string]struct{}{"member1": {}},
			TestName:         "SInter([prefix + key, prefix + key2])",
		},
	)

	batch.SInterStore(dest, []string{prefix + key, prefix + key2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(1), TestName: "SInterStore(dest, [prefix + key, prefix + key2])"},
	)

	batch.SInterCard([]string{prefix + key, prefix + key2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(1), TestName: "SInterCard([prefix + key, prefix + key2])"},
	)

	batch.SInterCardLimit([]string{prefix + key, prefix + key2}, 10)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(1), TestName: "SInterCardLimit([prefix + key, prefix + key2], 10)"},
	)

	batch.SRandMember(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "member1", TestName: "SRandMember(key)"})

	batch.SPop(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "member1", TestName: "SPop(key)"})

	batch.SAdd(key, []string{"member1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "SAdd(key, [member1])"})
	batch.SPopCount(key, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]struct{}{"member1": {}}, TestName: "SPopCount(key, 1)"},
	)

	batch.SAdd(key, []string{"member1"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "SAdd(key, [member1])"})
	batch.SMIsMember(key, []string{"member1", "nonexistent"})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{true, false}, TestName: "SMIsMember(key, [member1, nonexistent])"},
	)

	batch.SUnionStore(dest, []string{prefix + key, prefix + key2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(2), TestName: "SUnionStore(dest, [prefix + key, prefix + key2])"},
	)

	batch.SUnion([]string{prefix + key, prefix + key2})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: map[string]struct{}{"member1": {}, "member3": {}},
			TestName:         "SUnion([prefix + key, prefix + key2])",
		},
	)

	batch.SScan(key, "0")
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"0", []any{"member1"}}, TestName: "SScan(key, 0)"})

	scanOptions := options.NewBaseScanOptions().SetMatch("mem*")
	batch.SScanWithOptions(key, "0", *scanOptions)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"0", []any{"member1"}}, TestName: "SScanWithOptions(key, 0, options)"},
	)

	batch.SAdd(prefix+key2, []string{"newmember"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "SAdd(key2, [newmember])"})
	batch.SMove(prefix+key2, prefix+key, "newmember")
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: true, TestName: "SMove(prefix + key2, prefix + key, newmember)"},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Set commands"}
}

type BatchTestDataProvider func(*pipeline.ClusterBatch, bool) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
		CreateSetCommandsTests,
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
