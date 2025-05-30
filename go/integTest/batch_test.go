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

func CreateSortedSetTests(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{zset}-"
	// if isAtomic {
	// 	prefix = ""
	// }

	key := prefix + "key-" + uuid.NewString()

	membersScoreMap := map[string]float64{"member1": 1.0, "member2": 2.0}
	batch.ZAdd(key, membersScoreMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"})

	zAddOpts, _ := options.NewZAddOptions().SetChanged(true)
	batch.ZAddWithOptions(key, map[string]float64{"member3": 3.0}, *zAddOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(1), TestName: "ZAddWithOptions(key, {member3:3.0}, opts)"},
	)

	batch.ZAddIncr(key, "member1", 1.5)
	testData = append(testData, CommandTestData{ExpectedResponse: float64(2.5), TestName: "ZAddIncr(key, member1, 1.5)"})

	zAddIncrOpts := options.NewZAddOptions()
	batch.ZAddIncrWithOptions(key, "member2", 2.0, *zAddIncrOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: float64(4.0), TestName: "ZAddIncrWithOptions(key, member2, 2.0, opts)"},
	)

	batch.ZIncrBy(key, 1.0, "member3")
	testData = append(testData, CommandTestData{ExpectedResponse: float64(4.0), TestName: "ZIncrBy(key, 1.0, member3)"})

	batch.ZPopMin(key)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": float64(2.5)}, TestName: "ZPopMin(key)"},
	)

	zPopOpts := options.NewZPopOptions().SetCount(2)
	batch.ZPopMinWithOptions(key, *zPopOpts)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: map[string]any{"member2": float64(4.0), "member3": float64(4.0)},
			TestName:         "ZPopMinWithOptions(key, opts)",
		},
	)

	batch.ZAdd(key, membersScoreMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"})
	batch.ZPopMax(key)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member2": float64(2.0)}, TestName: "ZPopMax(key)"},
	)

	zPopOpts.SetCount(1)
	batch.ZPopMaxWithOptions(key, *zPopOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": float64(1.0)}, TestName: "ZPopMaxWithOptions(key, opts)"},
	)

	batch.ZAdd(key, membersScoreMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"})
	batch.ZRem(key, []string{"member2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRem(key, [member2])"})

	batch.ZCard(key)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZCard(key)"})

	batch.BZPopMin([]string{key}, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{key, "member1", float64(1)}, TestName: "BZPopMin([key])"},
	)

	batch.ZAdd(key, map[string]float64{"member1": float64(1.0)})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member1:1.0})"})
	batch.BZMPop([]string{key}, constants.MIN, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{key, map[string]any{"member1": float64(1)}}, TestName: "BZMPop(key, MIN, 1)"},
	)

	batch.ZAdd(key, membersScoreMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"})
	batch.BZMPopWithOptions([]string{key}, constants.MIN, 1, *options.NewZMPopOptions().SetCount(1))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []any{key, map[string]any{"member1": float64(1)}},
			TestName:         "BZMPopWithOptions(key, MIN, 1, opts",
		},
	)

	rangeQuery := options.NewRangeByIndexQuery(0, -1)
	batch.ZRange(key, rangeQuery)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member2"}, TestName: "ZRange(key, 0, -1)"})

	batch.BZPopMax([]string{key}, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{key, "member2", float64(2.0)}, TestName: "BZPopMax(key, 1)"},
	)

	batch.ZAdd(key, membersScoreMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"})
	batch.ZMPop([]string{key}, constants.MIN)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{key, map[string]any{"member1": float64(1.0)}}, TestName: "ZMPop([key], min)"},
	)

	batch.ZMPopWithOptions([]string{key}, constants.MIN, *options.NewZMPopOptions().SetCount(1))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []any{key, map[string]any{"member2": float64(2.0)}},
			TestName:         "ZMPopWithOptions([key], min, opts)",
		},
	)

	batch.ZAdd(key, map[string]float64{"member1": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member1:1.0})"})
	batch.ZRangeWithScores(key, options.NewRangeByIndexQuery(0, -1))
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": 1.0}, TestName: "ZRangeWithScores(key, 0, -1)"},
	)

	dest := prefix + "dest-" + uuid.NewString()
	batch.ZRangeStore(dest, key, options.NewRangeByIndexQuery(0, -1))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRangeStore(dest, key, 0, -1)"})

	batch.ZRank(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZRank(key, member1)"})

	batch.ZRankWithScore(key, "member1")
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{int64(0), float64(1.0)}, TestName: "ZRankWithScore(key, member1)"},
	)

	batch.ZRevRank(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZRevRank(key, member1)"})

	batch.ZRevRankWithScore(key, "member1")
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{int64(0), float64(1.0)}, TestName: "ZRevRankWithScore(key, member2)"},
	)

	batch.ZScore(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: float64(1.0), TestName: "ZScore(key, member1)"})

	zCountRange := options.NewZCountRange(
		options.NewInclusiveScoreBoundary(0.0),
		options.NewInfiniteScoreBoundary(constants.PositiveInfinity),
	)
	batch.ZCount(key, *zCountRange)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZCount(key, 2.0, inf)"})

	batch.ZScan(key, "0")
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"0", []any{"member1", "1"}}, TestName: "ZScan(key, 0)"},
	)

	zScanOpts := options.NewZScanOptions().SetCount(1)
	batch.ZScanWithOptions(key, "0", *zScanOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"0", []any{"member1", "1"}}, TestName: "ZScanWithOptions(key, 0, opts)"},
	)

	key2 := prefix + "key2-" + uuid.NewString()
	batch.ZAdd(key2, map[string]float64{"member1": 1.0, "member2": 2.0, "member3": 3.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "ZAdd(key2, members)"})
	batch.ZRemRangeByRank(key2, 0, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRemRangeByRank(key2, 0, 0)"})

	scoreRange := options.NewRangeByScoreQuery(
		options.NewInclusiveScoreBoundary(3),
		options.NewInclusiveScoreBoundary(3),
	)
	batch.ZRemRangeByScore(key2, *scoreRange)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRemRangeByScore(key2, 2, 3)"})

	batch.ZAdd(key2, map[string]float64{"a": 1.0, "b": 1.0, "c": 1.0, "d": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "ZAdd(key2, members)"})
	lexRange := options.NewRangeByLexQuery(
		options.NewLexBoundary("a", true),
		options.NewLexBoundary("b", true),
	)
	batch.ZRemRangeByLex(key2, *lexRange)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZRemRangeByLex(key2, [a, [b)"})

	batch.ZRandMember(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "member1", TestName: "ZRandMember(key)"})

	batch.ZRandMemberWithCount(key, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member1"}, TestName: "ZRandMemberWithCount(key, 1)"})

	batch.ZRandMemberWithCountWithScores(key, 1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []any{[]any{"member1", float64(1.0)}},
			TestName:         "ZRandMemberWithCountWithScores(key, 1)",
		},
	)

	batch.ZMScore(key, []string{"member1"})
	testData = append(testData, CommandTestData{ExpectedResponse: []any{float64(1.0)}, TestName: "ZMScore(key, [member1])"})

	batch.ZDiff([]string{key, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member1"}, TestName: "ZDiff([key, key2])"})

	batch.ZDiffWithScores([]string{key, key2})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": float64(1.0)}, TestName: "ZDiffWithScores([key, key2])"},
	)

	batch.ZDiffStore(dest, []string{key, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZDiffStore(dest, [key, key2])"})

	batch.ZInter(options.KeyArray{
		Keys: []string{key, key2},
	})
	testData = append(testData, CommandTestData{ExpectedResponse: ([]any)(nil), TestName: "ZInter([key, key2])"})

	batch.ZInterWithScores(
		options.KeyArray{
			Keys: []string{key, key2},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	testData = append(testData, CommandTestData{ExpectedResponse: map[string]any{}, TestName: "ZInterWithScores(keys, opts)"})

	batch.ZInterStore(
		dest,
		options.KeyArray{
			Keys: []string{key, key2},
		},
	)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterStore(dest, keys)"})

	batch.ZInterStoreWithOptions(
		dest,
		options.KeyArray{
			Keys: []string{key, key2},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterStoreWithOptions(dest, keys, opts)"},
	)

	key3 := prefix + "key3-" + uuid.NewString()
	batch.ZAdd(key3, map[string]float64{"b": 2.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key3, members)"})
	batch.ZUnion(
		options.KeyArray{
			Keys: []string{key, key3},
		},
	)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member1", "b"}, TestName: "ZUnion([key, key2])"})

	batch.ZUnionWithScores(
		options.KeyArray{Keys: []string{key, key3}},
		*options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": 1.0, "b": 2.0}, TestName: "ZUnionWithScores(keys, opts)"},
	)

	batch.ZUnionStore(dest, options.KeyArray{Keys: []string{key, key3}})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZUnionStore(dest, keys)"})

	batch.ZUnionStoreWithOptions(
		dest,
		options.KeyArray{Keys: []string{key, key3}},
		*options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(2), TestName: "ZUnionStoreWithOptions(dest, keys, opts)"},
	)

	batch.ZInterCard([]string{key, key2})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterCard([key, key2])"})

	zInterCardOpts := options.NewZInterCardOptions().SetLimit(10)
	batch.ZInterCardWithOptions([]string{key, key2}, *zInterCardOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterCardWithOptions([key, key2], opts)"},
	)

	batch.ZLexCount(key2, *options.NewRangeByLexQuery(options.NewLexBoundary("a", true), options.NewLexBoundary("c", true)))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZLexCount(key2, [a, [c)"})

	return BatchTestData{CommandTestData: testData, TestName: "Sorted Set commands"}
}

type BatchTestDataProvider func(*pipeline.ClusterBatch, bool) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
		CreateSortedSetTests,
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
