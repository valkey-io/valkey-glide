// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"reflect"
	"strings"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/errors"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/models"
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

func (suite *GlideTestSuite) TestBatchGeoSpatial() {
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		prefix := "{GeoKey}-"
		atomicPrefix := prefix
		if !isAtomic {
			atomicPrefix = ""
		}
		key := atomicPrefix + "1-" + uuid.NewString()
		membersToGeospatialData := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
		}
		membersToGeospatialData2 := map[string]options.GeospatialData{
			"Messina": {Longitude: 15.556349, Latitude: 38.194136},
		}

		var res []any
		var err error
		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic)

			key = prefix + key

			batch.GeoAdd(key, membersToGeospatialData)

			geoAddOptions := options.GeoAddOptions{}
			geoAddOptions.SetConditionalChange(constants.OnlyIfDoesNotExist)
			batch.GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)

			batch.GeoPos(key, []string{"Palermo", "NonExistingCity"})

			batch.GeoDist(key, "Palermo", "Catania")

			batch.GeoDistWithUnit(key, "Palermo", "Catania", constants.GeoUnitKilometers)

			searchFrom := &options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15.0, Latitude: 37.0},
			}
			searchByShape := options.NewCircleSearchShape(200, constants.GeoUnitKilometers)
			batch.GeoSearch(key, searchFrom, *searchByShape)

			infoOptions := options.NewGeoSearchInfoOptions().SetWithDist(true)
			batch.GeoSearchWithInfoOptions(key, searchFrom, *searchByShape, *infoOptions)

			resultOptions := options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.ASC)
			batch.GeoSearchWithFullOptions(key, searchFrom, *searchByShape, *resultOptions, *infoOptions)

			res, err = c.Exec(context.Background(), *batch, true)
			suite.NoError(err)
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic)

			batch.GeoAdd(key, membersToGeospatialData)

			geoAddOptions := options.GeoAddOptions{}
			geoAddOptions.SetConditionalChange(constants.OnlyIfDoesNotExist)
			batch.GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)

			batch.GeoPos(key, []string{"Palermo", "NonExistingCity"})

			batch.GeoDist(key, "Palermo", "Catania")

			batch.GeoDistWithUnit(key, "Palermo", "Catania", constants.GeoUnitKilometers)

			searchFrom := &options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15.0, Latitude: 37.0},
			}
			searchByShape := options.NewCircleSearchShape(200, constants.GeoUnitKilometers)
			batch.GeoSearch(key, searchFrom, *searchByShape)

			infoOptions := options.NewGeoSearchInfoOptions().SetWithDist(true)
			batch.GeoSearchWithInfoOptions(key, searchFrom, *searchByShape, *infoOptions)

			resultOptions := options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.ASC)
			batch.GeoSearchWithFullOptions(key, searchFrom, *searchByShape, *resultOptions, *infoOptions)

			res, err = c.Exec(context.Background(), *batch, true)
			suite.NoError(err)
		}

		// Verify GeoPos results
		geoPos := res[2].([]any)
		suite.Len(geoPos, 2)
		suite.NotNil(geoPos[0])
		suite.Nil(geoPos[1])

		// Verify distance results (approximately)
		geoDist := res[3].(float64)
		suite.InDelta(166274.15, geoDist, 1.0)

		geoDistKm := res[4].(float64)
		suite.InDelta(166.27, geoDistKm, 0.1)

		// Verify search results
		geoSearch := res[5]
		suite.Len(geoSearch, 3)
		suite.Contains(geoSearch, "Palermo")
		suite.Contains(geoSearch, "Catania")
		suite.Contains(geoSearch, "Messina")

		// Verify search with info results
		geoSearchInfo := res[6].([]any)
		suite.Len(geoSearchInfo, 3)

		// Verify full search results
		geoSearchFull := res[7].([]any)
		suite.Len(geoSearchFull, 1)
	})
}

func (suite *GlideTestSuite) TestBatchComplexFunctionCommands() {
	// TODO: Make tests that test the functionality. For now, we test that they can be sent and have responses received.
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		var res []any
		var err error
		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic).
				FunctionKill().
				FunctionDump().
				FunctionRestore("payload").
				FunctionRestoreWithPolicy("payload", constants.FlushPolicy)

			res, err = c.Exec(context.Background(), *batch, false)
			assert.NoError(suite.T(), err)
		case *glide.Client:
			// Just test that they run
			batch := pipeline.NewStandaloneBatch(isAtomic).
				FunctionKill().
				FunctionDump().
				FunctionRestore("payload").
				FunctionRestoreWithPolicy("payload", constants.FlushPolicy)

			if suite.serverVersion >= "7.0.0" {
				batch.FunctionKill()
			}

			res, err = c.Exec(context.Background(), *batch, false)
			assert.NoError(suite.T(), err)
		}
		assert.IsType(suite.T(), &errors.RequestError{}, res[0])
		assert.IsType(suite.T(), &errors.RequestError{}, res[1])
		assert.IsType(suite.T(), &errors.RequestError{}, res[2])
		assert.IsType(suite.T(), &errors.RequestError{}, res[3])
	})
}

func (suite *GlideTestSuite) TestBatchFunctionCommands() {
	suite.SkipIfServerVersionLowerThan("7.0.0", suite.T())

	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		libName := "mylib_" + strings.ReplaceAll(uuid.NewString(), "-", "_")
		funcName := "myfunc"
		libCode := `#!lua name=` + libName + `
redis.register_function{ function_name = 'myfunc', callback = function() return 42 end, flags = { 'no-writes' } }`
		query := models.FunctionListQuery{
			LibraryName: libName,
			WithCode:    false,
		}
		var res []any
		var err error
		switch c := client.(type) {
		case *glide.ClusterClient:
			opts := pipeline.NewClusterBatchOptions().WithRoute(config.NewSlotIdRoute(config.SlotTypePrimary, 42))
			batch := pipeline.NewClusterBatch(isAtomic).
				FunctionFlush().
				FunctionFlushSync().
				FunctionFlushAsync().
				FunctionLoad(libCode, false).
				FCall(funcName).
				FCallReadOnly(funcName).
				FCallWithKeysAndArgs(funcName, []string{}, []string{}).
				FCallReadOnlyWithKeysAndArgs(funcName, []string{}, []string{}).
				FunctionStats().
				FunctionDelete(libName).
				FunctionLoad(libCode, false).
				FunctionList(query)

			res, err = c.ExecWithOptions(context.Background(), *batch, false, *opts)
			assert.NoError(suite.T(), err)

		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).
				FunctionFlush().
				FunctionFlushSync().
				FunctionFlushAsync().
				FunctionLoad(libCode, false).
				FCall(funcName).
				FCallReadOnly(funcName).
				FCallWithKeysAndArgs(funcName, []string{}, []string{}).
				FCallReadOnlyWithKeysAndArgs(funcName, []string{}, []string{}).
				FunctionStats().
				FunctionDelete(libName).
				FunctionLoad(libCode, false).
				FunctionList(query)

			res, err = c.Exec(context.Background(), *batch, false)
			assert.NoError(suite.T(), err)
		}
		assert.Equal(suite.T(), "OK", res[0])
		assert.Equal(suite.T(), "OK", res[1])
		assert.Equal(suite.T(), "OK", res[2])
		assert.Equal(suite.T(), libName, res[3])
		assert.Equal(suite.T(), int64(42), res[4])
		assert.Equal(suite.T(), int64(42), res[5])
		assert.Equal(suite.T(), int64(42), res[6])
		assert.Equal(suite.T(), int64(42), res[7])
		assert.True(
			suite.T(),
			reflect.DeepEqual(
				map[string]any{
					"engines": map[string]any{
						"LUA": map[string]any{
							"functions_count": int64(1),
							"libraries_count": int64(1),
						},
					},
					"running_script": nil,
				},
				res[8],
			),
		)
		assert.Equal(suite.T(), "OK", res[9])
		assert.Equal(
			suite.T(),
			[]any{
				map[string]any{
					"engine": "LUA",
					"functions": []any{
						map[string]any{
							"description": nil,
							"flags": map[string]struct{}{
								"no-writes": {},
							},
							"name": funcName,
						},
					},
					"library_name": libName,
				},
			},
			res[11],
		)
	})
}

func (suite *GlideTestSuite) TestBatchStandaloneAndClusterPubSub() {
	// TODO: replace 'any' type after converters have been added

	// Just test that the execution works
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic).
				Publish("NonExistentChannel", "msg").
				PubSubShardChannels().
				PubSubShardChannelsWithPattern("").
				PubSubShardNumSub()

			res, err := c.Exec(context.Background(), *batch, false)
			suite.NoError(err)
			suite.Equal(int64(0), res[0])
			if suite.serverVersion >= "7.0.0" {
				suite.Equal(([]any)(nil), res[1])
				suite.Equal(([]any)(nil), res[2])
				suite.Equal(map[string]any{}, res[3])
			} else {
				// In 6.2.0, errors are raised instead
				suite.IsType(&errors.RequestError{}, res[1])
				suite.IsType(&errors.RequestError{}, res[2])
				suite.IsType(&errors.RequestError{}, res[3])
			}
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).
				Publish("NonExistentChannel", "msg")
			res, err := c.Exec(context.Background(), *batch, false)
			suite.NoError(err)
			suite.Equal(int64(0), res[0])
		}
	})
}

func CreateStringTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
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

	if serverVer >= "7.0.0" {
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
	}

	batch.GetDel(atomicKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: "7", TestName: "GetDel(atomicKey1)"})

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

func CreateConnectionManagementTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	connectionName := "test-connection-" + uuid.New().String()

	batch.Ping()
	testData = append(testData, CommandTestData{ExpectedResponse: "PONG", TestName: "Ping()"})

	pingOptions := options.PingOptions{
		Message: "hello",
	}
	batch.PingWithOptions(pingOptions)
	testData = append(testData, CommandTestData{ExpectedResponse: "hello", TestName: "PingWithOptions(pingOptions)"})

	batch.Echo("hello world")
	testData = append(testData, CommandTestData{ExpectedResponse: "hello world", TestName: "Echo(hello world)"})

	batch.ClientId()
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), CheckTypeOnly: true, TestName: "ClientId()"})

	batch.ClientSetName(connectionName)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "ClientSetName(connectionName)"})

	batch.ClientGetName()
	testData = append(testData, CommandTestData{ExpectedResponse: connectionName, TestName: "ClientGetName()"})

	return BatchTestData{CommandTestData: testData, TestName: "Connection Management commands"}
}

func CreateGenericCommandTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
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
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(-1), CheckTypeOnly: true, TestName: "TTL(slotHashedKey1)"},
	)

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

	// GenericClusterCommands
	batch.FlushAll()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "CustomCommand: FlushAll()"})

	batch.CustomCommand([]string{"SET", slotHashedKey1, "1"})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "CustomCommand: Set(key, 1)"})
	batch.Get(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: "1", TestName: "Get(key1)"})

	batch.RandomKey()
	testData = append(testData, CommandTestData{ExpectedResponse: slotHashedKey1, TestName: "RandomKey()"})

	// TODO: add Move in separate standalone batch tests

	return BatchTestData{CommandTestData: testData, TestName: "Generic commands"}
}

func CreateGeospatialTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{GeoKey}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}
	key := atomicPrefix + "1-" + uuid.NewString()
	destKey := prefix + "2-" + uuid.NewString()
	membersToGeospatialData := map[string]options.GeospatialData{
		"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
		"Catania": {Longitude: 15.087269, Latitude: 37.502669},
	}
	membersToGeospatialData2 := map[string]options.GeospatialData{
		"Messina": {Longitude: 15.556349, Latitude: 38.194136},
	}

	batch.GeoAdd(key, membersToGeospatialData)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "GeoAdd(key, membersToGeospatialData)"})

	geoAddOptions := options.GeoAddOptions{}
	geoAddOptions.SetConditionalChange(constants.OnlyIfDoesNotExist)
	batch.GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: int64(1),
			TestName:         "GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)",
		},
	)

	batch.GeoHash(key, []string{"Palermo", "Catania", "NonExistingCity"})
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any{"sqc8b49rny0", "sqdtr74hyu0", nil},
		TestName:         "GeoHash(key, [Palermo, Catania, NonExistingCity])",
	})

	searchFrom := &options.GeoCoordOrigin{
		GeospatialData: options.GeospatialData{Longitude: 15.0, Latitude: 37.0},
	}
	searchByShape := options.NewCircleSearchShape(200, constants.GeoUnitKilometers)

	resultOptions := options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.ASC)
	batch.GeoSearchWithResultOptions(key, searchFrom, *searchByShape, *resultOptions)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any{"Catania"},
		TestName:         "GeoSearchWithResultOptions(key, searchFrom, searchByShape, resultOptions)",
	})

	batch.GeoAdd(prefix+key, membersToGeospatialData)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "GeoAdd(key, membersToGeospatialData)"})

	batch.GeoAddWithOptions(prefix+key, membersToGeospatialData2, geoAddOptions)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: int64(1),
			TestName:         "GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)",
		},
	)

	batch.GeoSearchStore(destKey, prefix+key, searchFrom, *searchByShape)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(3), TestName: "GeoSearchStore(destKey, key, searchFrom, searchByShape)"},
	)

	batch.GeoSearchStoreWithResultOptions(destKey+"1", prefix+key, searchFrom, *searchByShape, *resultOptions)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: int64(1),
			TestName:         "GeoSearchStoreWithResultOptions(destKey+1, key, searchFrom, searchByShape, resultOptions)",
		},
	)

	storeInfoOptions := options.NewGeoSearchStoreInfoOptions().SetStoreDist(true)
	batch.GeoSearchStoreWithInfoOptions(destKey+"2", prefix+key, searchFrom, *searchByShape, *storeInfoOptions)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: int64(3),
			TestName:         "GeoSearchStoreWithInfoOptions(destKey+2, key, searchFrom, searchByShape, storeInfoOptions)",
		},
	)

	batch.GeoSearchStoreWithFullOptions(destKey+"3", prefix+key, searchFrom, *searchByShape, *resultOptions, *storeInfoOptions)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: int64(1),
			TestName:         "GeoSearchStoreWithFullOptions(destKey+3, key, searchFrom, searchByShape, resultOptions, storeInfoOptions)",
		},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Geospatial commands"}
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

func CreateListCommandsTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	// TODO: fix use more specific type than 'any' when converters are added
	testData := make([]CommandTestData, 0)
	prefix := "{listKey}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}

	key := atomicPrefix + uuid.NewString()

	batch.LPush(key, []string{"val1", "val2"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "LPush(key, [val1 val2])"})

	batch.LPop(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "val2", TestName: "LPop(key)"})

	batch.LPopCount(key, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"val1"}, TestName: "LPopCount(key, 1)"})

	batch.RPush(key, []string{"elem1", "elem2", "elem3", "elem2"})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(4), TestName: "RPush(key, [elem1, elem2, elem3, elem2])"},
	)

	batch.LPos(key, "elem2")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "LPos(key, elem2)"})

	batch.LPosWithOptions(key, "elem2", *options.NewLPosOptions().SetRank(2))
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(3), TestName: "LPosWithOptions(key, elem2, {Rank: 2})"},
	)

	batch.LPosCount(key, "elem2", 2)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{int64(1), int64(3)}, TestName: "LPosCount(key, elem2, 2)"},
	)

	batch.LPosCountWithOptions(key, "elem2", 2, *options.NewLPosOptions().SetMaxLen(4))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []any{int64(1), int64(3)},
			TestName:         "LPosCountWithOptions(key, elem2, 2, {MaxLen: 4})",
		},
	)

	batch.LRange(key, 0, 2)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"elem1", "elem2", "elem3"}, TestName: "LRange(key, 0, 2)"},
	)

	batch.LIndex(key, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: "elem2", TestName: "LIndex(key, 1)"})

	trimKey := atomicPrefix + "trim-" + uuid.NewString()
	batch.RPush(trimKey, []string{"one", "two", "three", "four"})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(4), TestName: "RPush(trimKey, [one, two, three, four])"},
	)
	batch.LTrim(trimKey, 1, 2)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "LTrim(trimKey, 1, 2)"})
	batch.LRange(trimKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"two", "three"}, TestName: "LRange(trimKey, 0, -1) after trim"},
	)

	batch.LLen(key)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "LLen(key)"})

	batch.LRem(key, 1, "elem2")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "LRem(key, 1, elem2)"})
	batch.LRange(key, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"elem1", "elem3", "elem2"}, TestName: "LRange(key, 0, -1) after LRem"},
	)

	batch.RPop(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "elem2", TestName: "RPop(key)"})

	batch.RPopCount(key, 2)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"elem3", "elem1"}, TestName: "RPopCount(key, 2)"})

	batch.RPush(key, []string{"hello", "world"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPush(key, [hello, world])"})
	batch.LInsert(key, constants.Before, "world", "there")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LInsert(key, Before, world, there)"})
	batch.LRange(key, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"hello", "there", "world"}, TestName: "LRange(key, 0, -1) after LInsert"},
	)

	batch.BLPop([]string{key}, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{key, "hello"}, TestName: "BLPop([key], 1)"})

	batch.BRPop([]string{key}, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{key, "world"}, TestName: "BRPop([key], 1)"})

	rpushxKey := atomicPrefix + "rpushx-" + uuid.NewString()
	batch.RPush(rpushxKey, []string{"initial"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "RPush(rpushxKey, [initial])"})
	batch.RPushX(rpushxKey, []string{"added"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPushX(rpushxKey, [added])"})
	batch.LRange(rpushxKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"initial", "added"}, TestName: "LRange(rpushxKey, 0, -1) after RPushX"},
	)

	lpushxKey := atomicPrefix + "lpushx-" + uuid.NewString()
	batch.RPush(lpushxKey, []string{"initial"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "RPush(lpushxKey, [initial])"})
	batch.LPushX(lpushxKey, []string{"added"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "LPushX(lpushxKey, [added])"})
	batch.LRange(lpushxKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"added", "initial"}, TestName: "LRange(lpushxKey, 0, -1) after LPushX"},
	)

	if serverVer >= "7.0.0" {
		batch.LMPop([]string{key}, constants.Left)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: map[string]any{key: []any{"there"}}, TestName: "LMPop([key], Left)"},
		)

		batch.RPush(key, []string{"hello"})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "RPush(key, [hello])"})
		batch.LMPopCount([]string{key}, constants.Left, 1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: map[string]any{key: []any{"hello"}}, TestName: "LMPopCount([key], Left, 1)"},
		)

		batch.RPush(key, []string{"hello", "world"})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPush(key, [hello, world])"})
		batch.BLMPop([]string{key}, constants.Left, 1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: map[string]any{key: []any{"hello"}}, TestName: "BLMPop([key], Left, 1)"},
		)

		batch.BLMPopCount([]string{key}, constants.Left, 1, 1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: map[string]any{key: []any{"world"}}, TestName: "BLMPopCount([key], Left, 1, 1)"},
		)
	}

	lsetKey := atomicPrefix + "lset-" + uuid.NewString()
	batch.RPush(lsetKey, []string{"one", "two", "three"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "RPush(lsetKey, [one, two, three])"})
	batch.LSet(lsetKey, 1, "changed")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "LSet(lsetKey, 1, changed)"})
	batch.LRange(lsetKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"one", "changed", "three"}, TestName: "LRange(lsetKey, 0, -1) after LSet"},
	)

	key = prefix + key
	destKey := prefix + "dest-" + uuid.NewString()
	batch.RPush(key, []string{"first", "second"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPush(key, [first, second])"})
	batch.RPush(destKey, []string{"third", "fourth"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPush(destKey, [third, fourth])"})
	batch.LMove(key, destKey, constants.Right, constants.Left)
	testData = append(testData, CommandTestData{ExpectedResponse: "second", TestName: "LMove(key, destKey, Right, Left)"})
	batch.LRange(key, 0, -1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"first"}, TestName: "LRange(key, 0, -1) after LMove"})
	batch.LRange(destKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"second", "third", "fourth"}, TestName: "LRange(destKey, 0, -1) after LMove"},
	)

	batch.BLMove(key, destKey, constants.Right, constants.Left, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: "first", TestName: "BLMove(key, destKey, Right, Left, 1)"})
	batch.LRange(key, 0, -1)
	testData = append(testData, CommandTestData{ExpectedResponse: ([]any)(nil), TestName: "LRange(key, 0, -1) after BLMove"})
	batch.LRange(destKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []any{"first", "second", "third", "fourth"},
			TestName:         "LRange(destKey, 0, -1) after BLMove",
		},
	)

	return BatchTestData{CommandTestData: testData, TestName: "List commands"}
}

func CreatePubSubTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	// TODO: replace 'any' type after converters have been added

	// Just test that the execution works
	testData := make([]CommandTestData, 0)

	batch.PubSubChannels()
	testData = append(testData, CommandTestData{ExpectedResponse: ([]any)(nil), TestName: "PubSubChannels()"})

	batch.PubSubChannelsWithPattern("")
	testData = append(testData, CommandTestData{ExpectedResponse: ([]any)(nil), TestName: "PubSubChannelsWithPattern()"})

	batch.PubSubNumPat()
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "PubSubNumPat()"})

	batch.PubSubNumSub([]string{""})
	testData = append(testData, CommandTestData{ExpectedResponse: map[string]any{"": int64(0)}, TestName: "PubSubNumSub()"})

	return BatchTestData{CommandTestData: testData, TestName: "PubSub commands"}
}

func CreateSetCommandsTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
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

	if serverVer >= "7.0.0" {
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
	}

	batch.SRandMember(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "member1", TestName: "SRandMember(key)"})

	batch.SRandMemberCount(key, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member1"}, TestName: "SRandMemberCount(key, 1)"})

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

func CreateSortedSetTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{zset}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}

	key := atomicPrefix + "key-" + uuid.NewString()

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

	if serverVer >= "7.0.0" {
		batch.ZAdd(key, map[string]float64{"member1": float64(1.0)})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member1:1.0})"})
		batch.BZMPop([]string{key}, constants.MIN, 1)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []any{key, map[string]any{"member1": float64(1)}},
				TestName:         "BZMPop(key, MIN, 1)",
			},
		)

		batch.ZAdd(key, membersScoreMap)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"},
		)
		batch.BZMPopWithOptions([]string{key}, constants.MIN, 1, *options.NewZMPopOptions().SetCount(1))
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []any{key, map[string]any{"member1": float64(1)}},
				TestName:         "BZMPopWithOptions(key, MIN, 1, opts",
			},
		)
	} else {
		batch.ZAdd(key, map[string]float64{"member2": float64(2.0)})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member2:2.0})"})
	}

	rangeQuery := options.NewRangeByIndexQuery(0, -1)
	batch.ZRange(key, rangeQuery)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member2"}, TestName: "ZRange(key, 0, -1)"})

	batch.BZPopMax([]string{key}, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{key, "member2", float64(2.0)}, TestName: "BZPopMax(key, 1)"},
	)

	if serverVer >= "7.0.0" {
		batch.ZAdd(key, membersScoreMap)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"},
		)
		batch.ZMPop([]string{key}, constants.MIN)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []any{key, map[string]any{"member1": float64(1.0)}},
				TestName:         "ZMPop([key], min)",
			},
		)

		batch.ZMPopWithOptions([]string{key}, constants.MIN, *options.NewZMPopOptions().SetCount(1))
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []any{key, map[string]any{"member2": float64(2.0)}},
				TestName:         "ZMPopWithOptions([key], min, opts)",
			},
		)
	}

	batch.ZAdd(key, map[string]float64{"member1": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member1:1.0})"})
	batch.ZRangeWithScores(key, options.NewRangeByIndexQuery(0, -1))
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": 1.0}, TestName: "ZRangeWithScores(key, 0, -1)"},
	)

	dest := prefix + "dest-" + uuid.NewString()
	prefixKey := prefix + "key2-" + uuid.NewString()
	batch.ZAdd(prefixKey, map[string]float64{"member1": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(prefixKey, {member1:1.0})"})
	batch.ZRangeStore(dest, prefixKey, options.NewRangeByIndexQuery(0, -1))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRangeStore(dest, prefixKey, 0, -1)"})

	batch.ZRank(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZRank(key, member1)"})

	if serverVer >= "7.2.0" {
		batch.ZRankWithScore(key, "member1")
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: []any{int64(0), float64(1.0)}, TestName: "ZRankWithScore(key, member1)"},
		)
	}

	batch.ZRevRank(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZRevRank(key, member1)"})

	if serverVer >= "7.2.0" {
		batch.ZRevRankWithScore(key, "member1")
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: []any{int64(0), float64(1.0)}, TestName: "ZRevRankWithScore(key, member2)"},
		)
	}

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

	key3 := atomicPrefix + "key3-" + uuid.NewString()
	batch.ZAdd(key3, map[string]float64{"member1": 1.0, "member2": 2.0, "member3": 3.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "ZAdd(key3, members)"})
	batch.ZRemRangeByRank(key3, 0, 0)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRemRangeByRank(key3, 0, 0)"})

	scoreRange := options.NewRangeByScoreQuery(
		options.NewInclusiveScoreBoundary(3),
		options.NewInclusiveScoreBoundary(3),
	)
	batch.ZRemRangeByScore(key3, *scoreRange)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZRemRangeByScore(key3, 2, 3)"})

	batch.ZAdd(key3, map[string]float64{"a": 1.0, "b": 1.0, "c": 1.0, "d": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "ZAdd(key3, members)"})
	lexRange := options.NewRangeByLexQuery(
		options.NewLexBoundary("a", true),
		options.NewLexBoundary("b", true),
	)
	batch.ZRemRangeByLex(key3, *lexRange)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZRemRangeByLex(key3, [a, [b)"})

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

	batch.ZAdd(prefix+key3, map[string]float64{"a": 1.0, "b": 1.0, "c": 1.0, "d": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "ZAdd(prefix+key3, members)"})
	batch.ZAdd(prefix+key, map[string]float64{"member1": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(prefix+key, {member1:1.0})"})
	batch.ZDiff([]string{prefix + key, prefix + key3})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []any{"member1"}, TestName: "ZDiff([prefix+key, prefix+key3])"},
	)

	batch.ZDiffWithScores([]string{prefix + key, prefix + key3})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: map[string]any{"member1": float64(1.0)},
			TestName:         "ZDiffWithScores([prefix+key, prefix+key3])",
		},
	)

	batch.ZDiffStore(dest, []string{prefix + key, prefix + key3})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(1), TestName: "ZDiffStore(dest, [prefix+key, prefix+key3])"},
	)

	batch.ZInter(options.KeyArray{
		Keys: []string{prefix + key, prefix + key3},
	})
	testData = append(testData, CommandTestData{ExpectedResponse: ([]any)(nil), TestName: "ZInter(keys)"})

	batch.ZInterWithScores(
		options.KeyArray{
			Keys: []string{prefix + key, prefix + key3},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	testData = append(testData, CommandTestData{ExpectedResponse: map[string]any{}, TestName: "ZInterWithScores(keys, opts)"})

	batch.ZInterStore(
		dest,
		options.KeyArray{
			Keys: []string{prefix + key, prefix + key3},
		},
	)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterStore(dest, keys)"})

	batch.ZInterStoreWithOptions(
		dest,
		options.KeyArray{
			Keys: []string{prefix + key, prefix + key3},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterStoreWithOptions(dest, keys, opts)"},
	)

	key4 := prefix + "key4-" + uuid.NewString()
	batch.ZAdd(key4, map[string]float64{"b": 2.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key4, members)"})
	batch.ZUnion(
		options.KeyArray{
			Keys: []string{prefix + key, key4},
		},
	)
	testData = append(testData, CommandTestData{ExpectedResponse: []any{"member1", "b"}, TestName: "ZUnion(keys)"})

	batch.ZUnionWithScores(
		options.KeyArray{Keys: []string{prefix + key, key4}},
		*options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]any{"member1": 1.0, "b": 2.0}, TestName: "ZUnionWithScores(keys, opts)"},
	)

	batch.ZUnionStore(dest, options.KeyArray{Keys: []string{prefix + key, key4}})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZUnionStore(dest, keys)"})

	batch.ZUnionStoreWithOptions(
		dest,
		options.KeyArray{Keys: []string{prefix + key, key4}},
		*options.NewZUnionOptionsBuilder().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(2), TestName: "ZUnionStoreWithOptions(dest, keys, opts)"},
	)

	if serverVer >= "7.0.0" {
		batch.ZInterCard([]string{prefix + key, prefix + key3})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterCard(keys)"})

		zInterCardOpts := options.NewZInterCardOptions().SetLimit(10)
		batch.ZInterCardWithOptions([]string{prefix + key, prefix + key3}, *zInterCardOpts)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: int64(0), TestName: "ZInterCardWithOptions(keys, opts)"},
		)
	}

	batch.ZLexCount(key3, *options.NewRangeByLexQuery(options.NewLexBoundary("a", true), options.NewLexBoundary("c", true)))
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZLexCount(key3, [a, [c)"})

	return BatchTestData{CommandTestData: testData, TestName: "Sorted Set commands"}
}

func CreateStreamTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	prefix := "{streamKey}-"
	atomicPrefix := prefix
	if !isAtomic {
		atomicPrefix = ""
	}

	streamKey1 := prefix + "1-" + uuid.NewString()
	streamKey2 := atomicPrefix + "2-" + uuid.NewString()
	streamKey3 := atomicPrefix + "3-" + uuid.NewString()
	streamKey4 := atomicPrefix + "4-" + uuid.NewString()
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
		ExpectedResponse: map[string]any{
			streamKey1: map[string]any{
				"0-3": []any{[]any{"field3", "value3"}},
			},
		},
		TestName: "XRead(streamKey1, 0-2)",
	})

	// XRANGE commands with options
	xrangeOpts := options.NewXRangeOptions().SetCount(1)
	batch.XRangeWithOptions(streamKey1, "0-1", "0-1", *xrangeOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]any{
			"0-1": []any{[]any{"field1", "value1"}},
		},
		TestName: "XRange(streamKey1, 0-1, 0-1)",
	})

	// XREVRANGE commands with options
	xrevrangeOpts := options.NewXRangeOptions().SetCount(1)
	batch.XRevRangeWithOptions(streamKey1, "0-1", "0-1", *xrevrangeOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]any{
			"0-1": []any{[]any{"field1", "value1"}},
		},
		TestName: "XRevRange(streamKey1, 0-1, 0-1)",
	})

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
	testData = append(testData, CommandTestData{ExpectedResponse: []any(nil), TestName: "XInfoConsumers(streamKey1, groupName1)"})

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
		ExpectedResponse: map[string]any{
			streamKey1: map[string]any{},
		},
		TestName: "XReadGroup(streamKey1, 0-3, groupName1, consumer1)",
	})

	// XCLAIM commands with options
	xclaimOpts := options.NewXClaimOptions().SetForce()
	batch.XClaimWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-1"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]any{},
		TestName:         "XClaim(streamKey1, groupName1, consumer1, 0-1)",
	})

	batch.XClaimWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-3"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]any{
			"0-3": []any{[]any{"field3", "value3"}},
		},
		TestName: "XClaim(streamKey1, groupName1, consumer1, 0-3)",
	})

	// XCLAIMJUSTID commands with options
	batch.XClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-3"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any{"0-3"},
		TestName:         "XClaimJustId(streamKey1, groupName1, consumer1, 0-3)",
	})

	batch.XClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-4"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any(nil),
		TestName:         "XClaimJustId(streamKey1, groupName1, consumer1, 0-4)",
	})

	// XPENDING command
	batch.XPending(streamKey1, groupName1)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any{int64(1), "0-3", "0-3", []any{[]any{consumer1, "1"}}},
		TestName:         "XPending(streamKey1, groupName1)",
	})

	// XAUTOCLAIM commands
	xautoclaimOpts := options.NewXAutoClaimOptions().SetCount(1)
	batch.XAutoClaimWithOptions(streamKey1, groupName1, consumer1, 0, "0-0", *xautoclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any{"0-0", map[string]any{"0-3": []any{[]any{"field3", "value3"}}}, []any(nil)},
		TestName:         "XAutoClaim(streamKey1, groupName1, consumer1, 0-0)",
	})

	batch.XAutoClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, "0-0", *xautoclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any{"0-0", []any{"0-3"}, []any(nil)},
		TestName:         "XAutoClaimJustId(streamKey1, groupName1, consumer1, 0-0)",
	})

	// XACK command
	batch.XAck(streamKey1, groupName1, []string{"0-3"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "XAck(streamKey1, groupName1, 0-3)"})

	// XPENDING with range
	xpendingOpts := options.NewXPendingOptions("-", "+", 1)
	batch.XPendingWithOptions(streamKey1, groupName1, *xpendingOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []any(nil),
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
	testData = append(testData, CommandTestData{ExpectedResponse: []any(nil), TestName: "XInfoGroups(streamKey1)"})

	// Add entry to streamKey2 and create group
	if serverVer >= "7.0.0" {
		xaddOpts6 := options.NewXAddOptions().SetId("1-0")
		batch.XAddWithOptions(streamKey2, [][]string{{"f0", "v0"}}, *xaddOpts6)
		testData = append(testData, CommandTestData{ExpectedResponse: "1-0", TestName: "XAdd(streamKey2, f0=v0, 1-0)"})

		batch.XGroupCreate(streamKey2, groupName3, "0")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey2, groupName3, 0)"})

		xgroupSetIdOpts2 := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
		batch.XGroupSetIdWithOptions(streamKey2, groupName3, "1-0", *xgroupSetIdOpts2)
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupSetId(streamKey2, groupName3, 1-0)"})
	}

	return BatchTestData{CommandTestData: testData, TestName: "Stream commands"}
}

// ClusterBatch - The Batch object
// bool - isAtomic flag. True for transactions, false for pipeline
// string - The server version we are running on
type BatchTestDataProvider func(*pipeline.ClusterBatch, bool, string) BatchTestData

func GetCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateBitmapTest,
		CreateConnectionManagementTests,
		CreateGenericCommandTests,
		CreateGeospatialTests,
		CreateHashTest,
		CreateHyperLogLogTest,
		CreateListCommandsTest,
		CreatePubSubTests,
		CreateSetCommandsTests,
		CreateSortedSetTests,
		CreateStreamTest,
		CreateStringTest,
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
		if testData[i].CheckTypeOnly {
			suite.IsType(testData[i].ExpectedResponse, result[i], testData[i].TestName)
			continue
		}
		suite.Equal(testData[i].ExpectedResponse, result[i], testData[i].TestName)
	}
}
