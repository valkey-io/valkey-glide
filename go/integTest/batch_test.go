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

func CreateStreamTest(batch *pipeline.ClusterBatch, isAtomic bool) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{streamKey}-"

	streamKey1 := prefix + "1-" + uuid.NewString()
	streamKey2 := prefix + "2-" + uuid.NewString()
	streamKey3 := prefix + "3-" + uuid.NewString()
	streamKey4 := prefix + "4-" + uuid.NewString()
	groupName1 := "{groupName}-1-" + uuid.NewString()
	groupName2 := "{groupName}-2-" + uuid.NewString()
	groupName3 := "{groupName}-3-" + uuid.NewString()
	consumer1 := "{consumer}-1-" + uuid.NewString()

	// XADD commands with options
	xaddOpts1 := options.NewXAddOptions().SetId("0-1")
	batch.XAddWithOptions(streamKey1, [][]string{{"field1", "value1"}}, *xaddOpts1)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-1", TestName: "XAdd(streamKey1, field1=value1, 0-1)"})

	xaddOpts2 := options.NewXAddOptions().SetId("0-2")
	batch.XAddWithOptions(streamKey1, [][]string{{"field2", "value2"}}, *xaddOpts2)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-2", TestName: "XAdd(streamKey1, field2=value2, 0-2)"})

	xaddOpts3 := options.NewXAddOptions().SetId("0-3")
	batch.XAddWithOptions(streamKey1, [][]string{{"field3", "value3"}}, *xaddOpts3)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-3", TestName: "XAdd(streamKey1, field3=value3, 0-3)"})

	xaddOpts4 := options.NewXAddOptions().SetId("0-4")
	batch.XAddWithOptions(streamKey4, [][]string{{"field4", "value4"}, {"field4", "value5"}}, *xaddOpts4)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-4", TestName: "XAdd(streamKey4, field4=value4,5, 0-4)"})

	// XLEN command
	batch.XLen(streamKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "XLen(streamKey1)"})

	// XREAD commands with options
	xreadOpts := options.NewXReadOptions().SetCount(1)
	batch.XReadWithOptions(map[string]string{streamKey1: "0-2"}, *xreadOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]interface{}{
			streamKey1: map[string]interface{}{
				"0-3": []interface{}{[]interface{}{"field3", "value3"}},
			},
		},
		TestName: "XRead(streamKey1, 0-2)",
	})

	// // XRANGE commands with options
	// xrangeOpts := options.NewXRangeOptions().SetCount(1)
	// batch.XRangeWithOptions(streamKey1, "0-1", "0-1", *xrangeOpts)
	// testData = append(testData, CommandTestData{
	// 	ExpectedResponse: []models.XRangeResponse{
	// 		{StreamId: "0-1", Entries: [][]string{{"field1", "value1"}}},
	// 	},
	// 	TestName: "XRange(streamKey1, 0-1, 0-1)", // TODO
	// })

	// // XREVRANGE commands with options
	// xrevrangeOpts := options.NewXRangeOptions().SetCount(1)
	// batch.XRevRangeWithOptions(streamKey1, "0-1", "0-1", *xrevrangeOpts)
	// testData = append(testData, CommandTestData{
	// 	ExpectedResponse: []models.XRangeResponse{
	// 		{StreamId: "0-1", Entries: [][]string{{"field1", "value1"}}},
	// 	},
	// 	TestName: "XRevRange(streamKey1, 0-1, 0-1)", // TODO
	// })

	// XTRIM command with options
	xtrimOpts := options.NewXTrimOptionsWithMinId("0-2").SetExactTrimming()
	batch.XTrim(streamKey1, *xtrimOpts)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "XTrim(streamKey1, 0-2)"})

	// XGROUP commands with options
	xgroupCreateOpts := options.NewXGroupCreateOptions().SetMakeStream()
	batch.XGroupCreateWithOptions(streamKey1, groupName1, "0-2", *xgroupCreateOpts)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey1, groupName1, 0-2)"})

	// XINFO CONSUMERS command
	batch.XInfoConsumers(streamKey1, groupName1)
	testData = append(testData, CommandTestData{ExpectedResponse: []interface{}(nil), TestName: "XInfoConsumers(streamKey1, groupName1)"})

	// Create second group with makeStream option
	batch.XGroupCreateWithOptions(streamKey1, groupName2, "0-0", *xgroupCreateOpts)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey1, groupName2, 0-0)"})

	batch.XGroupCreateConsumer(streamKey1, groupName1, consumer1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "XGroupCreateConsumer(streamKey1, groupName1, consumer1)"})

	xgroupSetIdOpts := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	batch.XGroupSetIdWithOptions(streamKey1, groupName1, "0-2", *xgroupSetIdOpts)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupSetId(streamKey1, groupName1, 0-2)"})

	// XREADGROUP commands with options
	xreadgroupOpts := options.NewXReadGroupOptions().SetCount(2)
	batch.XReadGroupWithOptions(groupName1, consumer1, map[string]string{streamKey1: "0-3"}, *xreadgroupOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]interface{}{
			streamKey1: map[string]interface{}{},
		},
		TestName: "XReadGroup(streamKey1, 0-3, groupName1, consumer1)",
	})

	// XCLAIM commands with options
	xclaimOpts := options.NewXClaimOptions().SetForce()
	batch.XClaimWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-1"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]interface{}{},
		TestName:         "XClaim(streamKey1, groupName1, consumer1, 0-1)",
	})

	batch.XClaimWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-3"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]interface{}{
			"0-3": []interface{}{[]interface{}{"field3", "value3"}},
		},
		TestName: "XClaim(streamKey1, groupName1, consumer1, 0-3)",
	})

	// XCLAIMJUSTID commands with options
	batch.XClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-3"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []interface{}{"0-3"},
		TestName:         "XClaimJustId(streamKey1, groupName1, consumer1, 0-3)",
	})

	batch.XClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-4"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []interface{}(nil),
		TestName:         "XClaimJustId(streamKey1, groupName1, consumer1, 0-4)",
	})

	// XPENDING command
	batch.XPending(streamKey1, groupName1)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []interface{}{int64(1), "0-3", "0-3", []interface{}{[]interface{}{consumer1, "1"}}},
		TestName:         "XPending(streamKey1, groupName1)",
	})

	// XAUTOCLAIM commands
	xautoclaimOpts := options.NewXAutoClaimOptions().SetCount(1)
	batch.XAutoClaimWithOptions(streamKey1, groupName1, consumer1, 0, "0-0", *xautoclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []interface{}{"0-0", map[string]interface{}{"0-3": []interface{}{[]interface{}{"field3", "value3"}}}, []interface{}(nil)},
		TestName:         "XAutoClaim(streamKey1, groupName1, consumer1, 0-0)",
	})

	batch.XAutoClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, "0-0", *xautoclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []interface{}{"0-0", []interface{}{"0-3"}, []interface{}(nil)},
		TestName:         "XAutoClaimJustId(streamKey1, groupName1, consumer1, 0-0)",
	})

	// XACK command
	batch.XAck(streamKey1, groupName1, []string{"0-3"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "XAck(streamKey1, groupName1, 0-3)"})

	// XPENDING with range
	xpendingOpts := options.NewXPendingOptions("-", "+", 1)
	batch.XPendingWithOptions(streamKey1, groupName1, *xpendingOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []interface{}(nil),
		TestName:         "XPending(streamKey1, groupName1, MIN, MAX, 1)",
	})

	// XGROUP DELCONSUMER command
	batch.XGroupDelConsumer(streamKey1, groupName1, consumer1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "XGroupDelConsumer(streamKey1, groupName1, consumer1)"})

	// XGROUP DESTROY commands
	batch.XGroupDestroy(streamKey1, groupName1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "XGroupDestroy(streamKey1, groupName1)"})

	batch.XGroupDestroy(streamKey1, groupName2)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "XGroupDestroy(streamKey1, groupName2)"})

	// XDEL command
	batch.XDel(streamKey1, []string{"0-3", "0-5"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "XDel(streamKey1, 0-3,0-5)"})

	// Add entry to streamKey3 and create group
	xaddOpts5 := options.NewXAddOptions().SetId("1-0")
	batch.XAddWithOptions(streamKey3, [][]string{{"f0", "v0"}}, *xaddOpts5)
	testData = append(testData, CommandTestData{ExpectedResponse: "1-0", TestName: "XAdd(streamKey3, f0=v0, 1-0)"})

	batch.XGroupCreate(streamKey3, groupName3, "0")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey3, groupName3, 0)"})

	// XINFO GROUPS command
	batch.XInfoGroups(streamKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: []interface{}(nil), TestName: "XInfoGroups(streamKey1)"})

	// Add entry to streamKey2 and create group (for Redis >= 7.0.0)
	xaddOpts6 := options.NewXAddOptions().SetId("1-0")
	batch.XAddWithOptions(streamKey2, [][]string{{"f0", "v0"}}, *xaddOpts6)
	testData = append(testData, CommandTestData{ExpectedResponse: "1-0", TestName: "XAdd(streamKey2, f0=v0, 1-0)"})

	batch.XGroupCreate(streamKey2, groupName3, "0")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey2, groupName3, 0)"})

	xgroupSetIdOpts2 := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
	batch.XGroupSetIdWithOptions(streamKey2, groupName3, "1-0", *xgroupSetIdOpts2)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupSetId(streamKey2, groupName3, 1-0)"})

	return BatchTestData{CommandTestData: testData, TestName: "Stream commands"}
}

type BatchTestDataProvider func(*pipeline.ClusterBatch, bool) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateStringTest,
		// more command groups here
		CreateStreamTest,
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
