// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

func (suite *GlideTestSuite) runBatchTest(test func(client interfaces.BaseClientCommands, isAtomic bool)) {
	for _, client := range suite.getDefaultClients() {
		for _, isAtomic := range []bool{true, false} {
			suite.T().Run(makeFullTestName(client, "", isAtomic), func(t *testing.T) {
				test(client, isAtomic)
			})
		}
	}
}

// Note: test may cause others to fail, because they run in parallel and DEBUG command locks the server
func (suite *GlideTestSuite) TestBatchTimeout() {
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic).CustomCommand([]string{"DEBUG", "sleep", "0.5"})
			opts := pipeline.NewClusterBatchOptions().WithRoute(config.RandomRoute).WithTimeout(100 * time.Millisecond)
			// Expect a timeout error on short timeout
			_, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			suite.Error(err)
			suite.IsType(&glide.TimeoutError{}, err)

			time.Sleep(1 * time.Second)

			// Retry with a longer timeout and expect [OK]
			opts.WithTimeout(1 * time.Second)
			res, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			suite.NoError(err)
			suite.Equal([]any{"OK"}, res)
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).CustomCommand([]string{"DEBUG", "sleep", "0.5"})
			opts := pipeline.NewStandaloneBatchOptions().WithTimeout(100 * time.Millisecond)
			// Expect a timeout error on short timeout
			_, err := c.ExecWithOptions(context.Background(), *batch, true, *opts)
			suite.Error(err)
			suite.IsType(&glide.TimeoutError{}, err)

			time.Sleep(1 * time.Second)

			// Retry with a longer timeout and expect [OK]
			opts.WithTimeout(1 * time.Second)
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

		batch := pipeline.NewClusterBatch(isAtomic).
			Set(key1, "hello").
			LPop(key1).
			Del([]string{key1}).
			Rename(key1, key2)

		_, err1 := runBatchOnClient(client, batch, true, nil)
		res, err2 := runBatchOnClient(client, batch, false, nil)

		// First exception is raised, all data lost
		suite.Error(err1)

		// Errors aren't raised, but stored in the result set
		suite.NoError(err2)
		suite.Len(res, 4)
		suite.Equal("OK", res[0])
		suite.Equal(int64(1), res[2])
		suite.ErrorContains(glide.IsError(res[1]), "wrong kind of value")
		suite.ErrorContains(glide.IsError(res[3]), "no such key")
	})
}

func (suite *GlideTestSuite) TestBatchDumpRestore() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{prefix}" + uuid.NewString()

		// Dump
		transaction := pipeline.NewClusterBatch(true).
			Set(key, "someValue").
			Dump(key)

		res1, err1 := runBatchOnClient(client, transaction, true, nil)

		suite.verifyOK(res1[0].(string), err1)
		suite.NotNil(res1[1])

		// Restore
		transaction = pipeline.NewClusterBatch(true).
			Del([]string{key}).
			Restore(key, 0, res1[1].(string))

		res2, err2 := runBatchOnClient(client, transaction, true, nil)

		suite.NoError(err2)
		suite.Equal(int64(1), res2[0].(int64))
		suite.verifyOK(res2[1].(string), err2)
	})
}

func (suite *GlideTestSuite) TestBatchMove() {
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		key := "{prefix}-" + uuid.NewString()
		switch c := client.(type) {
		case *glide.ClusterClient:
			return // Move is not supported in cluster client
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic).
				Set(key, "val").
				Move(key, 2)

			res, err := c.Exec(context.Background(), *batch, true)
			suite.verifyOK(res[0].(string), err)
			suite.True(res[1].(bool))
		}
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

		transaction := pipeline.NewClusterBatch(true).Get(key1).Set(key1, uuid.NewString()).Get(key2)
		transactionResult, err := runBatchOnClient(client1, transaction, true, nil)

		suite.NoError(err)
		suite.Equal([]any{value, "OK", nil}, transactionResult)

		suite.verifyOK(client1.Unwatch(ctx))

		// Returns `nil` when a watched key is modified before it is executed in a transaction command.
		// Transaction commands are not performed.
		suite.verifyOK(client1.Watch(ctx, []string{key1}))
		suite.verifyOK(client2.Set(ctx, key1, uuid.NewString()))

		transaction = pipeline.NewClusterBatch(true).Set(key1, uuid.NewString())
		transactionResult, err = runBatchOnClient(client1, transaction, true, nil)

		suite.NoError(err)
		suite.Nil(transactionResult)

		client2.Close()

		// WATCH errors if no keys are given
		_, err = client1.Watch(ctx, []string{})
		suite.Error(err)
	})
}

func (suite *GlideTestSuite) TestWatch_and_Unwatch_cross_slot() {
	client := suite.defaultClusterClient()
	ctx := context.Background()

	suite.verifyOK(client.Watch(ctx, []string{"abc", "klm", "xyz"}))
	suite.verifyOK(client.UnwatchWithOptions(ctx, options.RouteOption{Route: config.AllNodes}))
}

func (suite *GlideTestSuite) TestBatchCommandArgsError() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key := "{prefix}" + uuid.NewString()

		opts := options.NewGetExOptions().
			SetExpiry(options.NewExpiryIn(10 * time.Second).SetType(constants.ExpiryType("pewpew")))
		transaction := pipeline.NewClusterBatch(true).
			Get(key).
			GetExWithOptions(key, *opts).
			Get(key).
			GetExWithOptions(key, *opts)

		res, err := runBatchOnClient(client, transaction, true, nil)

		suite.Nil(res)
		suite.ErrorContains(err, "error processing arguments for 2'th command ('GetExWithOptions')")
		suite.ErrorContains(err, "error processing arguments for 4'th command ('GetExWithOptions')")
	})
}

func (suite *GlideTestSuite) TestBatchConvertersHandleServerError() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		key1 := "{prefix}" + uuid.NewString()
		key2 := "{prefix}" + uuid.NewString()
		suite.verifyOK(client.Set(context.Background(), key1, uuid.NewString()))
		hset, err := client.HSet(context.Background(), key2, map[string]string{"a": "b"})
		suite.Equal(int64(1), hset)
		suite.NoError(err)

		// Run all commands on a wrong key type - so they all return an error each.
		// Commands' converters should return these errors intact.
		// skipping blocking commands
		transaction := pipeline.NewClusterBatch(true).
			GeoHash(key1, []string{"A"}).
			GeoPos(key1, []string{"A"}).
			GeoDist(key1, "a", "b").
			GeoDistWithUnit(key1, "a", "b", constants.GeoUnitFeet).
			GeoSearch(key1, &options.GeoMemberOrigin{Member: "a"}, *options.NewCircleSearchShape(2, constants.GeoUnitFeet)).
			GeoSearchWithInfoOptions(key1, &options.GeoMemberOrigin{Member: "a"}, *options.NewCircleSearchShape(2, constants.GeoUnitFeet), *options.NewGeoSearchInfoOptions().SetWithDist(true)).
			GeoSearchWithResultOptions(key1, &options.GeoMemberOrigin{Member: "a"}, *options.NewCircleSearchShape(2, constants.GeoUnitFeet), *options.NewGeoSearchResultOptions().SetCount(1)).
			GeoSearchWithFullOptions(key1, &options.GeoMemberOrigin{Member: "a"}, *options.NewCircleSearchShape(2, constants.GeoUnitFeet), *options.NewGeoSearchResultOptions().SetCount(1), *options.NewGeoSearchInfoOptions().SetWithDist(true)).
			HGet(key1, "f").
			HGetAll(key1).
			HMGet(key1, []string{"A"}).
			HVals(key1).
			HKeys(key1).
			HScan(key1, "0").
			HRandField(key1).
			HRandFieldWithCount(key1, 1).
			HRandFieldWithCountWithValues(key1, 1).
			HScanWithOptions(key1, "0", *options.NewHashScanOptions().SetCount(42)).
			LPop(key1).
			LPopCount(key1, 2).
			LPos(key1, "e").
			LPosWithOptions(key1, "e", *options.NewLPosOptions().SetMaxLen(42)).
			LPosCount(key1, "e", 42).
			LPosCountWithOptions(key1, "e", 42, *options.NewLPosOptions().SetMaxLen(42)).
			LRange(key1, 0, 1).
			LIndex(key1, 2).
			RPop(key1).
			RPopCount(key1, 2).
			LMove(key1, key2, constants.Left, constants.Left).
			SMembers(key1).
			SRandMember(key1).
			SRandMemberCount(key1, 2).
			SPop(key1).
			SMIsMember(key1, []string{"a"}).
			SScan(key1, "0").
			SScanWithOptions(key1, "0", *options.NewBaseScanOptions().SetMatch("abc")).
			ZAddIncr(key1, "a", 2).
			ZAddIncrWithOptions(key1, "a", 2, *options.NewZAddOptions().SetUpdateOptions(options.ScoreGreaterThanCurrent)).
			ZPopMin(key1).
			ZPopMinWithOptions(key1, *options.NewZPopOptions().SetCount(2)).
			ZPopMax(key1).
			ZPopMaxWithOptions(key1, *options.NewZPopOptions().SetCount(2)).
			ZRange(key1, options.NewRangeByIndexQuery(0, 2)).
			ZRangeWithScores(key1, options.NewRangeByIndexQuery(0, 2)).
			ZRank(key1, "d").
			ZRevRank(key1, "d").
			ZScore(key1, "d").
			ZScan(key1, "0").
			ZScanWithOptions(key1, "0", *options.NewZScanOptions().SetMatch("abc")).
			ZDiff([]string{key1, key2}).
			ZDiffWithScores([]string{key1, key2}).
			ZRandMember(key1).
			ZRandMemberWithCount(key1, 42).
			ZRandMemberWithCountWithScores(key1, 42).
			ZMScore(key1, []string{"a"}).
			ZInter(options.KeyArray{Keys: []string{key1, key2}}).
			ZInterWithScores(options.KeyArray{Keys: []string{key1, key2}}, *options.NewZInterOptions().SetAggregate(options.AggregateMax)).
			ZUnion(options.KeyArray{Keys: []string{key1, key2}}).
			ZUnionWithScores(options.KeyArray{Keys: []string{key1, key2}}, *options.NewZUnionOptions().SetAggregate(options.AggregateMax)).
			XAdd(key1, []models.FieldValue{{Field: "a", Value: "b"}}).
			XAddWithOptions(key1, []models.FieldValue{{Field: "a", Value: "b"}}, *options.NewXAddOptions().SetId("0-1")).
			XAutoClaim(key1, "g", "c", 2, "0-0").
			XAutoClaimWithOptions(key1, "g", "c", 2, "0-0", *options.NewXAutoClaimOptions().SetCount(2)).
			XAutoClaimJustId(key1, "g", "c", 2, "0-0").
			XAutoClaimJustIdWithOptions(key1, "g", "c", 2, "0-0", *options.NewXAutoClaimOptions().SetCount(2)).
			XReadGroup("g", "c", map[string]string{key1: "0-0"}).
			XReadGroupWithOptions("g", "c", map[string]string{key1: "0-0"}, *options.NewXReadGroupOptions().SetNoAck()).
			XRead(map[string]string{key1: "0-0"}).
			XReadWithOptions(map[string]string{key1: "0-0"}, *options.NewXReadOptions().SetCount(2)).
			XPending(key1, "g").
			XPendingWithOptions(key1, "g", *options.NewXPendingOptions("0-0", "2-2", 3)).
			XClaim(key1, "g", "c", 2, []string{"0-0"}).
			XClaimWithOptions(key1, "g", "c", 2, []string{"0-0"}, *options.NewXClaimOptions().SetForce()).
			XClaimJustId(key1, "g", "c", 2, []string{"0-0"}).
			XClaimJustIdWithOptions(key1, "g", "c", 2, []string{"0-0"}, *options.NewXClaimOptions().SetForce()).
			XInfoStream(key1).
			XInfoStreamFullWithOptions(key1, options.NewXInfoStreamOptions().SetCount(2)).
			XInfoConsumers(key1, "g").
			XInfoGroups(key1).
			XRange(key1, options.NewStreamBoundary("0-0", true), options.NewStreamBoundary("2-0", true)).
			XRangeWithOptions(key1, options.NewStreamBoundary("0-0", true), options.NewStreamBoundary("2-0", true), *options.NewXRangeOptions().SetCount(2)).
			XRevRange(key1, options.NewStreamBoundary("0-0", true), options.NewStreamBoundary("2-0", true)).
			XRevRangeWithOptions(key1, options.NewStreamBoundary("0-0", true), options.NewStreamBoundary("2-0", true), *options.NewXRangeOptions().SetCount(2))

		if suite.serverVersion >= "7.0.0" {
			transaction.
				ZMPop([]string{key1}, constants.MAX).
				ZMPopWithOptions([]string{key1}, constants.MAX, *options.NewZMPopOptions().SetCount(2)).
				LMPop([]string{key1}, constants.Left).
				LMPopCount([]string{key1}, constants.Left, 42)
		}
		if suite.serverVersion >= "7.2.0" {
			transaction.
				ZRankWithScore(key1, "d").
				ZRevRankWithScore(key1, "d")
		}

		res, err := runBatchOnClient(client, transaction, false, nil)
		suite.NoError(err)
		for i, resp := range res {
			suite.Equal("WRONGTYPE: Operation against a key holding the wrong kind of value", resp.(error).Error(), i)
		}

		if suite.serverVersion < "7.0.0" {
			return
		}
		// LCS has another error message
		transaction = pipeline.NewClusterBatch(true).
			LCSWithOptions(key1, key2, *options.NewLCSIdxOptions().SetIdx(true).SetMinMatchLen(2).SetWithMatchLen(true))
		res, err = runBatchOnClient(client, transaction, false, nil)
		suite.NoError(err)
		for i, resp := range res {
			suite.ErrorContains(glide.IsError(resp), "ResponseError", i)
		}
	})
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

		res, err := runBatchOnClient(client, batch, true, nil)
		suite.NoError(err)

		// Verify GeoPos results
		geoPos := res[2].([][]float64)
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
		geoSearchInfo := res[6]
		suite.Len(geoSearchInfo, 3)
		suite.IsType([]options.Location{}, geoSearchInfo)

		// Verify full search results
		geoSearchFull := res[7]
		suite.Len(geoSearchFull, 1)
		suite.IsType([]options.Location{}, geoSearchFull)
	})
}

func (suite *GlideTestSuite) TestBatchStandaloneAndClusterPubSub() {
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
			suite.Equal(int64(0), res[0], "Publish")
			if suite.serverVersion >= "7.0.0" {
				suite.Equal([]string{}, res[1], "PubSubShardChannels")
				suite.Equal(map[string]int64{}, res[3], "PubSubShardNumSub")
			} else {
				// In 6.2.0, errors are raised instead
				suite.Error(glide.IsError(res[1]), "PubSubShardChannels")
				suite.Error(glide.IsError(res[2]), "PubSubShardChannelsWithPattern")
				suite.Error(glide.IsError(res[3]), "PubSubShardNumSub")
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
		SetExpiry(options.NewExpiryIn(5 * time.Second))
	batch.GetExWithOptions(atomicKey1, *opts)
	testData = append(testData, CommandTestData{ExpectedResponse: value1, TestName: "GetExWithOptions(atomicKey1, opts)"})

	batch.MSet(map[string]string{multiKey1: "value2"})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "MSet(multiKey1, value2)"})

	batch.MGet([]string{multiKey1, multiKey2})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[string]{models.CreateStringResult("value2"), models.CreateNilStringResult()},
			TestName:         "MGet(key2, key3)",
		},
	)

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
				ExpectedResponse: models.LCSMatch{
					Len:         2,
					MatchString: models.DefaultStringResponse,
					Matches:     []models.LCSMatchedPosition{},
				},
				TestName: "LCSWithOptions(multiKey1, multiKey2, opts)",
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
		CommandTestData{
			ExpectedResponse: []models.Result[int64]{
				models.CreateInt64Result(0),
				models.CreateInt64Result(0),
				models.CreateInt64Result(1),
			},
			TestName: "BitField(key, commands)",
		},
	)

	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	batch.BitField(bitfieldkey2, bfcommands)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[int64]{models.CreateInt64Result(0)},
			TestName:         "BitField(bitfieldkey2, bfcommands)",
		},
	)
	commands2 := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	batch.BitFieldRO(bitfieldkey2, commands2)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[int64]{models.CreateInt64Result(24)},
			TestName:         "BitFieldRO(bitfieldkey2, commands2)",
		},
	)

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

	batch.Expire(slotHashedKey1, 1*time.Second)
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "Expire(slotHashedKey1, 1)"})
	batch.Expire(singleNodeKey1, 1*time.Second)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Expire(singleNodeKey1, 1)"})

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.ExpireAt(slotHashedKey1, time.Now())
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "ExpireAt(slotHashedKey1, 0)"})

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.PExpire(slotHashedKey1, 5000*time.Millisecond)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpire(slotHashedKey1, 5000)"})
	batch.PExpire(prefix+"nonExistentKey", 5000*time.Millisecond)
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "PExpire(badkey, 5000)"})

	batch.Set(slotHashedKey1, "value")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
	batch.PExpireAt(slotHashedKey1, time.Now())
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PExpireAt(slotHashedKey1, 0)"})

	if serverVer >= "7.0.0" {
		batch.ExpireWithOptions(singleNodeKey1, 1*time.Second, constants.HasExistingExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "ExpireWithOptions(singleNodeKey1, 1, HasExistingExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.ExpireAtWithOptions(slotHashedKey1, time.Now(), constants.HasNoExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "ExpireAtWithOptions(slotHashedKey1, 0, HasNoExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.PExpireWithOptions(slotHashedKey1, 5000*time.Millisecond, constants.HasNoExpiry)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: true, TestName: "PExpireWithOptions(slotHashedKey1, 5000, HasNoExpiry)"},
		)

		batch.Set(slotHashedKey1, "value")
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Set(slotHashedKey1, value)"})
		batch.PExpireAtWithOptions(slotHashedKey1, time.Now(), constants.HasNoExpiry)
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
	batch.Expire(slotHashedKey1, 100*time.Second)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Expire(slotHashedKey1, 100)"})
	batch.Persist(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Persist(slotHashedKey1)"})
	batch.TTL(slotHashedKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(-1), TestName: "TTL(slotHashedKey1)"})

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
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[string]{
				models.CreateStringResult("1"),
				models.CreateStringResult("2"),
				models.CreateStringResult("3"),
			},
			TestName: "Sort(slotHashedKey1)",
		},
	)

	batch.Del([]string{slotHashedKey1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "Del(slotHashedKey1)"})
	batch.LPush(slotHashedKey1, []string{"c", "b", "a"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LPush(slotHashedKey1, [c, b, a])"})
	batch.SortWithOptions(slotHashedKey1, *options.NewSortOptions().SetIsAlpha(true))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[string]{
				models.CreateStringResult("a"),
				models.CreateStringResult("b"),
				models.CreateStringResult("c"),
			},
			TestName: "SortWithOptions(slotHashedKey1, {Alpha: true})",
		},
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
			CommandTestData{
				ExpectedResponse: []models.Result[string]{
					models.CreateStringResult("1"),
					models.CreateStringResult("2"),
					models.CreateStringResult("3"),
				},
				TestName: "SortReadOnly(slotHashedKey1)",
			},
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
				ExpectedResponse: []models.Result[string]{
					models.CreateStringResult("a"),
					models.CreateStringResult("b"),
					models.CreateStringResult("c"),
				},
				TestName: "SortReadOnlyWithOptions(slotHashedKey1, {Alpha: true})",
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
		ExpectedResponse: []models.Result[string]{
			models.CreateStringResult("sqc8b49rny0"),
			models.CreateStringResult("sqdtr74hyu0"),
			models.CreateNilStringResult(),
		},
		TestName: "GeoHash(key, [Palermo, Catania, NonExistingCity])",
	})

	searchFrom := &options.GeoCoordOrigin{
		GeospatialData: options.GeospatialData{Longitude: 15.0, Latitude: 37.0},
	}
	searchByShape := options.NewCircleSearchShape(200, constants.GeoUnitKilometers)

	resultOptions := options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.ASC)
	batch.GeoSearchWithResultOptions(key, searchFrom, *searchByShape, *resultOptions)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []string{"Catania"},
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
	testData = append(testData, CommandTestData{ExpectedResponse: map[string]string{"k1": "value"}, TestName: "HGetAll(key)"})

	batch.HMGet(key, []string{"k1", "k2"})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[string]{models.CreateStringResult("value"), models.CreateNilStringResult()},
			TestName:         "HMGet(key, [k1, k2])",
		},
	)

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
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"value1"}, TestName: "HVals(key)"})

	batch.HExists(key, "field1")
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "HExists(key, field1)"})
	batch.HExists(key, "nonexistent")
	testData = append(testData, CommandTestData{ExpectedResponse: false, TestName: "HExists(key, nonexistent)"})

	batch.HKeys(key)
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"field1"}, TestName: "HKeys(key)"})

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
	batch.HScan(key, "0")
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: models.ScanResult{
				Cursor: models.NewCursorFromString("0"),
				Data:   []string{"field1", "value1", "counter", "15", "float_counter", "12"},
			},
			TestName: "HScan(key, 0)",
		},
	)
	if serverVer >= "8.0.0" {
		batch.HScanWithOptions(key, "0", *options.NewHashScanOptions().SetNoValues(true))
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: models.ScanResult{
					Cursor: models.NewCursorFromString("0"),
					Data:   []string{"field1", "counter", "float_counter"},
				},
				TestName: "HScanWithOptions(key, 0, options)",
			},
		)
	} else {
		batch.HScanWithOptions(key, "0", *options.NewHashScanOptions().SetCount(42))
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: models.ScanResult{
				Cursor: models.NewCursorFromString("0"),
				Data:   []string{"field1", "value1", "counter", "15", "float_counter", "12"},
			}},
		)
	}

	batch.FlushAll()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FlushAll()"})

	batch.HSet(key, map[string]string{"counter": "10"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "HSet(key, counter)"})
	batch.HRandField(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "counter", TestName: "HRandField(key)"})

	batch.HRandFieldWithCount(key, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"counter"}, TestName: "HRandFieldWithCount(key, 1)"},
	)

	batch.HRandFieldWithCountWithValues(key, 1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: [][]string{{"counter", "10"}}, TestName: "HRandFieldWithCountWithValues(key, 1)"},
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
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PfAdd(key1, [val])"})

	batch.PfCount([]string{key1})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "PfCount([key1])"})

	batch.PfAdd(key1, []string{"val2"})
	testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "PfAdd(key2, [val2])"})
	batch.PfMerge(prefix+dest, []string{prefix + key1, prefix + key2})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "PfMerge(dest, [key1 key2])"})

	return BatchTestData{CommandTestData: testData, TestName: "Hyperloglog commands"}
}

func CreateListCommandsTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
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
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"val1"}, TestName: "LPopCount(key, 1)"})

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
		CommandTestData{ExpectedResponse: []int64{1, 3}, TestName: "LPosCount(key, elem2, 2)"},
	)

	batch.LPosCountWithOptions(key, "elem2", 2, *options.NewLPosOptions().SetMaxLen(4))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []int64{1, 3},
			TestName:         "LPosCountWithOptions(key, elem2, 2, {MaxLen: 4})",
		},
	)

	batch.LRange(key, 0, 2)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"elem1", "elem2", "elem3"}, TestName: "LRange(key, 0, 2)"},
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
		CommandTestData{ExpectedResponse: []string{"two", "three"}, TestName: "LRange(trimKey, 0, -1) after trim"},
	)

	batch.LLen(key)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "LLen(key)"})

	batch.LRem(key, 1, "elem2")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "LRem(key, 1, elem2)"})
	batch.LRange(key, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"elem1", "elem3", "elem2"}, TestName: "LRange(key, 0, -1) after LRem"},
	)

	batch.RPop(key)
	testData = append(testData, CommandTestData{ExpectedResponse: "elem2", TestName: "RPop(key)"})

	batch.RPopCount(key, 2)
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"elem3", "elem1"}, TestName: "RPopCount(key, 2)"})

	batch.RPush(key, []string{"hello", "world"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPush(key, [hello, world])"})
	batch.LInsert(key, constants.Before, "world", "there")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "LInsert(key, Before, world, there)"})
	batch.LRange(key, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"hello", "there", "world"}, TestName: "LRange(key, 0, -1) after LInsert"},
	)

	batch.BLPop([]string{key}, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []string{key, "hello"}, TestName: "BLPop([key], 1)"})

	batch.BRPop([]string{key}, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: []string{key, "world"}, TestName: "BRPop([key], 1)"})

	rpushxKey := atomicPrefix + "rpushx-" + uuid.NewString()
	batch.RPush(rpushxKey, []string{"initial"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "RPush(rpushxKey, [initial])"})
	batch.RPushX(rpushxKey, []string{"added"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPushX(rpushxKey, [added])"})
	batch.LRange(rpushxKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"initial", "added"}, TestName: "LRange(rpushxKey, 0, -1) after RPushX"},
	)

	lpushxKey := atomicPrefix + "lpushx-" + uuid.NewString()
	batch.RPush(lpushxKey, []string{"initial"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "RPush(lpushxKey, [initial])"})
	batch.LPushX(lpushxKey, []string{"added"})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "LPushX(lpushxKey, [added])"})
	batch.LRange(lpushxKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"added", "initial"}, TestName: "LRange(lpushxKey, 0, -1) after LPushX"},
	)

	if serverVer >= "7.0.0" {
		batch.LMPop([]string{key}, constants.Left)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []models.KeyValues{{Key: key, Values: []string{"there"}}},
				TestName:         "LMPop([key], Left)",
			},
		)

		batch.RPush(key, []string{"hello"})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "RPush(key, [hello])"})
		batch.LMPopCount([]string{key}, constants.Left, 1)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []models.KeyValues{{Key: key, Values: []string{"hello"}}},
				TestName:         "LMPopCount([key], Left, 1)",
			},
		)

		batch.RPush(key, []string{"hello", "world"})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "RPush(key, [hello, world])"})
		batch.BLMPop([]string{key}, constants.Left, 1)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []models.KeyValues{{Key: key, Values: []string{"hello"}}},
				TestName:         "BLMPop([key], Left, 1)",
			},
		)

		batch.BLMPopCount([]string{key}, constants.Left, 1, 1)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: []models.KeyValues{{Key: key, Values: []string{"world"}}},
				TestName:         "BLMPopCount([key], Left, 1, 1)",
			},
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
		CommandTestData{ExpectedResponse: []string{"one", "changed", "three"}, TestName: "LRange(lsetKey, 0, -1) after LSet"},
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
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"first"}, TestName: "LRange(key, 0, -1) after LMove"},
	)
	batch.LRange(destKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []string{"second", "third", "fourth"},
			TestName:         "LRange(destKey, 0, -1) after LMove",
		},
	)

	batch.BLMove(key, destKey, constants.Right, constants.Left, 1)
	testData = append(testData, CommandTestData{ExpectedResponse: "first", TestName: "BLMove(key, destKey, Right, Left, 1)"})
	batch.LRange(key, 0, -1)
	testData = append(testData, CommandTestData{ExpectedResponse: []string{}, TestName: "LRange(key, 0, -1) after BLMove"})
	batch.LRange(destKey, 0, -1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []string{"first", "second", "third", "fourth"},
			TestName:         "LRange(destKey, 0, -1) after BLMove",
		},
	)

	return BatchTestData{CommandTestData: testData, TestName: "List commands"}
}

func CreatePubSubTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	// Just test that the execution works
	testData := make([]CommandTestData, 0)

	batch.PubSubChannels()
	testData = append(testData, CommandTestData{ExpectedResponse: []string{}, TestName: "PubSubChannels()"})

	batch.PubSubChannelsWithPattern("")
	testData = append(testData, CommandTestData{ExpectedResponse: []string{}, TestName: "PubSubChannelsWithPattern()"})

	batch.PubSubNumPat()
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "PubSubNumPat()"})

	batch.PubSubNumSub([]string{""})
	testData = append(testData, CommandTestData{ExpectedResponse: map[string]int64{"": 0}, TestName: "PubSubNumSub()"})

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
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"member1"}, TestName: "SRandMemberCount(key, 1)"})

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
		CommandTestData{ExpectedResponse: []bool{true, false}, TestName: "SMIsMember(key, [member1, nonexistent])"},
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
	testData = append(testData, CommandTestData{
		ExpectedResponse: models.ScanResult{
			Cursor: models.NewCursorFromString("0"),
			Data:   []string{"member1"},
		},
		TestName: "SScan(key, 0)",
	})

	scanOptions := options.NewBaseScanOptions().SetMatch("mem*")
	batch.SScanWithOptions(key, "0", *scanOptions)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: models.ScanResult{
			Cursor: models.NewCursorFromString("0"),
			Data:   []string{"member1"},
		}, TestName: "SScanWithOptions(key, 0, options)"},
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
		CommandTestData{ExpectedResponse: map[string]float64{"member1": 2.5}, TestName: "ZPopMin(key)"},
	)

	zPopOpts := options.NewZPopOptions().SetCount(2)
	batch.ZPopMinWithOptions(key, *zPopOpts)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: map[string]float64{"member2": 4.0, "member3": 4.0},
			TestName:         "ZPopMinWithOptions(key, opts)",
		},
	)

	batch.ZAdd(key, membersScoreMap)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZAdd(key, {member1:1.0, member2:2.0})"})
	batch.ZPopMax(key)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]float64{"member2": 2.0}, TestName: "ZPopMax(key)"},
	)

	zPopOpts.SetCount(1)
	batch.ZPopMaxWithOptions(key, *zPopOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]float64{"member1": 1.0}, TestName: "ZPopMaxWithOptions(key, opts)"},
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
		CommandTestData{
			ExpectedResponse: models.KeyWithMemberAndScore{Key: key, Member: "member1", Score: 1},
			TestName:         "BZPopMin([key])",
		},
	)

	if serverVer >= "7.0.0" {
		batch.ZAdd(key, map[string]float64{"member1": float64(1.0)})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member1:1.0})"})
		batch.BZMPop([]string{key}, constants.MIN, 1)
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: models.CreateKeyWithArrayOfMembersAndScoresResult(
					models.KeyWithArrayOfMembersAndScores{
						Key: key,
						MembersAndScores: []models.MemberAndScore{
							{Member: "member1", Score: 1.0},
						},
					},
				),
				TestName: "BZMPop(key, MIN, 1)",
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
				ExpectedResponse: models.CreateKeyWithArrayOfMembersAndScoresResult(
					models.KeyWithArrayOfMembersAndScores{
						Key: key,
						MembersAndScores: []models.MemberAndScore{
							{Member: "member1", Score: 1.0},
						},
					},
				),
				TestName: "BZMPopWithOptions(key, MIN, 1, opts",
			},
		)
	} else {
		batch.ZAdd(key, map[string]float64{"member2": float64(2.0)})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member2:2.0})"})
	}

	rangeQuery := options.NewRangeByIndexQuery(0, -1)
	batch.ZRange(key, rangeQuery)
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"member2"}, TestName: "ZRange(key, 0, -1)"})

	batch.BZPopMax([]string{key}, 1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: models.KeyWithMemberAndScore{Key: key, Member: "member2", Score: 2},
			TestName:         "BZPopMax(key, 1)",
		},
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
				ExpectedResponse: models.CreateKeyWithArrayOfMembersAndScoresResult(
					models.KeyWithArrayOfMembersAndScores{
						Key: key,
						MembersAndScores: []models.MemberAndScore{
							{Member: "member1", Score: 1.0},
						},
					},
				),
				TestName: "ZMPop([key], min)",
			},
		)

		batch.ZMPopWithOptions([]string{key}, constants.MIN, *options.NewZMPopOptions().SetCount(1))
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: models.CreateKeyWithArrayOfMembersAndScoresResult(
					models.KeyWithArrayOfMembersAndScores{
						Key: key,
						MembersAndScores: []models.MemberAndScore{
							{Member: "member2", Score: 2.0},
						},
					},
				),
				TestName: "ZMPopWithOptions([key], min, opts)",
			},
		)
	}

	batch.ZAdd(key, map[string]float64{"member1": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(key, {member1:1.0})"})
	batch.ZRangeWithScores(key, options.NewRangeByIndexQuery(0, -1))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.MemberAndScore{{Member: "member1", Score: 1.0}},
			TestName:         "ZRangeWithScores(key, 0, -1)",
		},
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
			CommandTestData{
				ExpectedResponse: models.RankAndScore{Rank: 0, Score: 1.0},
				TestName:         "ZRankWithScore(key, member1)",
			},
		)
	}

	batch.ZRevRank(key, "member1")
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "ZRevRank(key, member1)"})

	if serverVer >= "7.2.0" {
		batch.ZRevRankWithScore(key, "member1")
		testData = append(
			testData,
			CommandTestData{
				ExpectedResponse: models.RankAndScore{Rank: 0, Score: 1.0},
				TestName:         "ZRevRankWithScore(key, member1)",
			},
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
		CommandTestData{ExpectedResponse: models.ScanResult{
			Cursor: models.NewCursorFromString("0"),
			Data:   []string{"member1", "1"},
		}, TestName: "ZScan(key, 0)"},
	)

	zScanOpts := options.NewZScanOptions().SetCount(1)
	batch.ZScanWithOptions(key, "0", *zScanOpts)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: models.ScanResult{
			Cursor: models.NewCursorFromString("0"),
			Data:   []string{"member1", "1"},
		}, TestName: "ZScanWithOptions(key, 0, opts)"},
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
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"member1"}, TestName: "ZRandMemberWithCount(key, 1)"},
	)

	batch.ZRandMemberWithCountWithScores(key, 1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.MemberAndScore{{Member: "member1", Score: 1}},
			TestName:         "ZRandMemberWithCountWithScores(key, 1)",
		},
	)

	batch.ZMScore(key, []string{"member1", "memberN"})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.Result[float64]{models.CreateFloat64Result(1), models.CreateNilFloat64Result()},
			TestName:         "ZMScore(key, [member1, memberN])",
		},
	)

	batch.ZAdd(prefix+key3, map[string]float64{"a": 1.0, "b": 1.0, "c": 1.0, "d": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(4), TestName: "ZAdd(prefix+key3, members)"})
	batch.ZAdd(prefix+key, map[string]float64{"member1": 1.0})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "ZAdd(prefix+key, {member1:1.0})"})
	batch.ZDiff([]string{prefix + key, prefix + key3})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []string{"member1"}, TestName: "ZDiff([prefix+key, prefix+key3])"},
	)

	batch.ZDiffWithScores([]string{prefix + key, prefix + key3})
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.MemberAndScore{{Member: "member1", Score: 1.0}},
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
	testData = append(testData, CommandTestData{ExpectedResponse: []string{}, TestName: "ZInter(keys)"})

	batch.ZInterWithScores(
		options.KeyArray{
			Keys: []string{prefix + key, prefix + key3},
		},
		*options.NewZInterOptions().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []models.MemberAndScore{}, TestName: "ZInterWithScores(keys, opts)"},
	)

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
	testData = append(testData, CommandTestData{ExpectedResponse: []string{"member1", "b"}, TestName: "ZUnion(keys)"})

	batch.ZUnionWithScores(
		options.KeyArray{Keys: []string{prefix + key, key4}},
		*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
	)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: []models.MemberAndScore{{Member: "member1", Score: 1.0}, {Member: "b", Score: 2.0}},
			TestName:         "ZUnionWithScores(keys, opts)",
		},
	)

	batch.ZUnionStore(dest, options.KeyArray{Keys: []string{prefix + key, key4}})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "ZUnionStore(dest, keys)"})

	batch.ZUnionStoreWithOptions(
		dest,
		options.KeyArray{Keys: []string{prefix + key, key4}},
		*options.NewZUnionOptions().SetAggregate(options.AggregateSum),
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
	batch.XAddWithOptions(streamKey1, []models.FieldValue{{Field: "field1", Value: "value1"}}, *xaddOpts1)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-1", TestName: "XAdd(streamKey1, field1=value1, 0-1)"})

	xaddOpts2 := options.NewXAddOptions().SetId("0-2")
	batch.XAddWithOptions(streamKey1, []models.FieldValue{{Field: "field2", Value: "value2"}}, *xaddOpts2)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-2", TestName: "XAdd(streamKey1, field2=value2, 0-2)"})

	xaddOpts3 := options.NewXAddOptions().SetId("0-3")
	batch.XAddWithOptions(streamKey1, []models.FieldValue{{Field: "field3", Value: "value3"}}, *xaddOpts3)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-3", TestName: "XAdd(streamKey1, field3=value3, 0-3)"})

	xaddOpts4 := options.NewXAddOptions().SetId("0-4")
	batch.XAddWithOptions(
		streamKey4,
		[]models.FieldValue{{Field: "field4", Value: "value4"}, {Field: "field4", Value: "value5"}},
		*xaddOpts4,
	)
	testData = append(testData, CommandTestData{ExpectedResponse: "0-4", TestName: "XAdd(streamKey4, field4=value4,5, 0-4)"})

	// XLEN command
	batch.XLen(streamKey1)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "XLen(streamKey1)"})

	// XREAD commands with options
	xreadOpts := options.NewXReadOptions().SetCount(1)
	batch.XReadWithOptions(map[string]string{streamKey1: "0-2"}, *xreadOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]models.StreamResponse{
			streamKey1: {Entries: []models.StreamEntry{{
				ID: "0-3", Fields: []models.FieldValue{{Field: "field3", Value: "value3"}},
			}}},
		},
		TestName: "XRead(streamKey1, 0-2)",
	})

	// XRANGE commands with options
	xrangeOpts := options.NewXRangeOptions().SetCount(1)
	batch.XRangeWithOptions(streamKey1, "0-1", "0-1", *xrangeOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []models.StreamEntry{
			{ID: "0-1", Fields: []models.FieldValue{{Field: "field1", Value: "value1"}}},
		},
		TestName: "XRange(streamKey1, 0-1, 0-1)",
	})

	// XREVRANGE commands with options
	xrevrangeOpts := options.NewXRangeOptions().SetCount(1)
	batch.XRevRangeWithOptions(streamKey1, "0-1", "0-1", *xrevrangeOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []models.StreamEntry{
			{ID: "0-1", Fields: []models.FieldValue{{Field: "field1", Value: "value1"}}},
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
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: []models.XInfoConsumerInfo{}, TestName: "XInfoConsumers(streamKey1, groupName1)"},
	)

	// Create second group with makeStream option
	batch.XGroupCreateWithOptions(streamKey1, groupName2, "0-0", *xgroupCreateOpts)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey1, groupName2, 0-0)"})

	batch.XGroupCreateConsumer(streamKey1, groupName1, consumer1)
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: true, TestName: "XGroupCreateConsumer(streamKey1, groupName1, consumer1)"},
	)

	batch.XGroupSetId(streamKey1, groupName1, "0-2")
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "XGroupSetId(streamKey1, groupName1, 0-2)"})

	// XREADGROUP commands with options
	xreadgroupOpts := options.NewXReadGroupOptions().SetCount(2)
	batch.XReadGroupWithOptions(groupName1, consumer1, map[string]string{streamKey1: "0-3"}, *xreadgroupOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]models.StreamResponse{
			streamKey1: {Entries: []models.StreamEntry{}},
		},
		TestName: "XReadGroup(streamKey1, 0-3, groupName1, consumer1)",
	})

	// XCLAIM commands with options
	xclaimOpts := options.NewXClaimOptions().SetForce()
	batch.XClaimWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-1"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]models.XClaimResponse{},
		TestName:         "XClaim(streamKey1, groupName1, consumer1, 0-1)",
	})

	batch.XClaimWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-3"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: map[string]models.XClaimResponse{
			"0-3": {Fields: []models.FieldValue{{Field: "field3", Value: "value3"}}},
		},
		TestName: "XClaim(streamKey1, groupName1, consumer1, 0-3)",
	})

	// XCLAIMJUSTID commands with options
	batch.XClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-3"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []string{"0-3"},
		TestName:         "XClaimJustId(streamKey1, groupName1, consumer1, 0-3)",
	})

	batch.XClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, []string{"0-4"}, *xclaimOpts)
	testData = append(testData, CommandTestData{
		ExpectedResponse: []string{},
		TestName:         "XClaimJustId(streamKey1, groupName1, consumer1, 0-4)",
	})

	// XPENDING command
	batch.XPending(streamKey1, groupName1)
	testData = append(testData, CommandTestData{
		ExpectedResponse: models.XPendingSummary{
			NumOfMessages:    1,
			StartId:          models.CreateStringResult("0-3"),
			EndId:            models.CreateStringResult("0-3"),
			ConsumerMessages: []models.ConsumerPendingMessage{{ConsumerName: consumer1, MessageCount: 1}},
		},
		TestName: "XPending(streamKey1, groupName1)",
	})

	// XAUTOCLAIM commands
	if serverVer >= "6.2.0" {
		expectedXAutoClaimResponse := models.XAutoClaimResponse{
			NextEntry:      "0-0",
			ClaimedEntries: []models.StreamEntry{{ID: "0-3", Fields: []models.FieldValue{{Field: "field3", Value: "value3"}}}},
		}

		if serverVer >= "7.0.0" {
			expectedXAutoClaimResponse = models.XAutoClaimResponse{
				NextEntry: "0-0",
				ClaimedEntries: []models.StreamEntry{
					{ID: "0-3", Fields: []models.FieldValue{{Field: "field3", Value: "value3"}}},
				},
				DeletedMessages: []string{},
			}
		}

		expectedXAutoClaimJustIdResponse := models.XAutoClaimJustIdResponse{
			NextEntry:      "0-0",
			ClaimedEntries: []string{"0-3"},
		}
		if serverVer >= "7.0.0" {
			expectedXAutoClaimJustIdResponse = models.XAutoClaimJustIdResponse{
				NextEntry:       "0-0",
				ClaimedEntries:  []string{"0-3"},
				DeletedMessages: []string{},
			}
		}

		xautoclaimOpts := options.NewXAutoClaimOptions().SetCount(1)
		batch.XAutoClaimWithOptions(streamKey1, groupName1, consumer1, 0, "0-0", *xautoclaimOpts)
		testData = append(testData, CommandTestData{
			ExpectedResponse: expectedXAutoClaimResponse,
			TestName:         "XAutoClaim(streamKey1, groupName1, consumer1, 0-0)",
		})

		batch.XAutoClaimJustIdWithOptions(streamKey1, groupName1, consumer1, 0, "0-0", *xautoclaimOpts)
		testData = append(testData, CommandTestData{
			ExpectedResponse: expectedXAutoClaimJustIdResponse,
			TestName:         "XAutoClaimJustId(streamKey1, groupName1, consumer1, 0-0)",
		})

		// XACK command
		batch.XAck(streamKey1, groupName1, []string{"0-3"})
		testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "XAck(streamKey1, groupName1, 0-3)"})

		// XPENDING with range
		xpendingOpts := options.NewXPendingOptions("-", "+", 1)
		batch.XPendingWithOptions(streamKey1, groupName1, *xpendingOpts)
		testData = append(testData, CommandTestData{
			ExpectedResponse: []models.XPendingDetail{},
			TestName:         "XPendingWithOptions(streamKey1, groupName1, MIN, MAX, 1)",
		})

		// XGROUP DELCONSUMER command
		batch.XGroupDelConsumer(streamKey1, groupName1, consumer1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: int64(0), TestName: "XGroupDelConsumer(streamKey1, groupName1, consumer1)"},
		)

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
		batch.XAddWithOptions(streamKey3, []models.FieldValue{{Field: "f0", Value: "v0"}}, *xaddOpts5)
		testData = append(testData, CommandTestData{ExpectedResponse: "1-0", TestName: "XAdd(streamKey3, f0=v0, 1-0)"})

		batch.XGroupCreate(streamKey3, groupName3, "0")
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey3, groupName3, 0)"},
		)

		// XINFO GROUPS command
		batch.XInfoGroups(streamKey1)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: []models.XInfoGroupInfo{}, TestName: "XInfoGroups(streamKey1)"},
		)
	}

	// Add entry to streamKey2 and create group
	if serverVer >= "7.0.0" {
		xaddOpts6 := options.NewXAddOptions().SetId("1-0")
		batch.XAddWithOptions(streamKey2, []models.FieldValue{{Field: "f0", Value: "v0"}}, *xaddOpts6)
		testData = append(testData, CommandTestData{ExpectedResponse: "1-0", TestName: "XAdd(streamKey2, f0=v0, 1-0)"})

		batch.XGroupCreate(streamKey2, groupName3, "0")
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: "OK", TestName: "XGroupCreate(streamKey2, groupName3, 0)"},
		)

		xgroupSetIdOpts2 := options.NewXGroupSetIdOptionsOptions().SetEntriesRead(1)
		batch.XGroupSetIdWithOptions(streamKey2, groupName3, "1-0", *xgroupSetIdOpts2)
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: "OK", TestName: "XGroupSetId(streamKey2, groupName3, 1-0)"},
		)
	}

	batch.XInfoStream(streamKey1)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: models.XInfoStreamResponse{},
			CheckTypeOnly:    true,
			TestName:         "XInfoStream(streamKey1)",
		},
	)
	batch.XInfoStreamFullWithOptions(streamKey1, options.NewXInfoStreamOptions().SetCount(1))
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: models.XInfoStreamFullOptionsResponse{},
			CheckTypeOnly:    true,
			TestName:         "XInfoStreamFullWithOptions(streamKey1)",
		},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Stream commands"}
}

func CreateServerManagementTests(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)

	batch.ConfigSet(map[string]string{"timeout": "1000"})
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "ConfigSet(timeout: 1000)"})
	batch.ConfigGet([]string{"timeout"})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: map[string]string{"timeout": "1000"}, TestName: "ConfigGet(timeout)"},
	)
	batch.Info()
	testData = append(testData, CommandTestData{ExpectedResponse: "", CheckTypeOnly: true, TestName: "Info()"})
	batch.InfoWithOptions(options.InfoOptions{})
	testData = append(testData, CommandTestData{ExpectedResponse: "", CheckTypeOnly: true, TestName: "InfoWithOptions()"})
	batch.Time()
	testData = append(testData, CommandTestData{ExpectedResponse: []string{}, CheckTypeOnly: true, TestName: "Time()"})
	batch.FlushAll()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FlushAll()"})
	batch.FlushAllWithOptions(options.SYNC)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FlushAllWithOptions(SYNC)"})
	batch.FlushDB()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FlushDB()"})
	batch.FlushDBWithOptions(options.SYNC)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FlushDBWithOptions(SYNC)"})
	batch.DBSize()
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), TestName: "DBSize()"})
	batch.Lolwut()
	testData = append(testData, CommandTestData{ExpectedResponse: "", CheckTypeOnly: true, TestName: "Lolwut()"})
	batch.LolwutWithOptions(options.LolwutOptions{})
	testData = append(testData, CommandTestData{ExpectedResponse: "", CheckTypeOnly: true, TestName: "LolwutWithOptions()"})
	batch.LastSave()
	testData = append(testData, CommandTestData{ExpectedResponse: int64(0), CheckTypeOnly: true, TestName: "LastSave()"})
	batch.ConfigResetStat()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "ConfigResetStat()"})
	// ConfigRewrite skipped, because depends on config

	// SELECT command is only available in Valkey 9+ for cluster mode
	if serverVer >= "9.0.0" {
		batch.Select(1)
		testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "Select(1)"})
	}

	return BatchTestData{CommandTestData: testData, TestName: "Server Management commands"}
}

func CreateScriptTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)

	batch.ScriptExists([]string{"abc"})
	testData = append(testData, CommandTestData{ExpectedResponse: []bool{false}, TestName: "ScriptExists([abc])"})
	batch.ScriptFlush()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "ScriptFlush()"})
	batch.ScriptFlushWithMode(options.SYNC)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "ScriptFlushWithMode()"})
	if serverVer >= "8.0.0" {
		batch.ScriptShow("abc")
		testData = append(
			testData,
			CommandTestData{ExpectedResponse: errors.New(""), CheckTypeOnly: true, TestName: "ScriptShow()"},
		)
	}
	batch.ScriptKill()
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: errors.New(""), CheckTypeOnly: true, TestName: "ScriptKill()"},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Script commands"}
}

func CreateFunctionTest(batch *pipeline.ClusterBatch, isAtomic bool, serverVer string) BatchTestData {
	testData := make([]CommandTestData, 0)
	// adding a dummy command to avoid "empty pipeline" error on server < 7.0
	batch.Ping()
	testData = append(testData, CommandTestData{ExpectedResponse: "PONG", TestName: "Ping()"})
	if serverVer < "7.0.0" {
		return BatchTestData{CommandTestData: testData, TestName: "Function commands"}
	}

	libName := "mylib_" + strings.ReplaceAll(uuid.NewString(), "-", "_")
	funcName := "myfunc"
	libCode := "#!lua name=" + libName + "\nredis.register_function{ function_name = 'myfunc', callback = function() return 42 end, flags = { 'no-writes' } }"
	query := models.FunctionListQuery{
		LibraryName: libName,
		WithCode:    false,
	}

	batch.FunctionFlush()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FunctionFlush()"})
	batch.FunctionFlushSync()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FunctionFlushSync()"})
	batch.FunctionFlushAsync()
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FunctionFlushAsync()"})
	batch.FunctionLoad(libCode, false)
	testData = append(testData, CommandTestData{ExpectedResponse: libName, TestName: "FunctionLoad(libCode, false)"})
	batch.FCall(funcName)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(42), TestName: "FCall(funcName)"})
	batch.FCallReadOnly(funcName)
	testData = append(testData, CommandTestData{ExpectedResponse: int64(42), TestName: "FCallReadOnly(funcName)"})
	batch.FCallWithKeysAndArgs(funcName, []string{}, []string{})
	testData = append(testData, CommandTestData{ExpectedResponse: int64(42), TestName: "FCallWithKeysAndArgs(funcName)"})
	batch.FCallReadOnlyWithKeysAndArgs(funcName, []string{}, []string{})
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: int64(42), TestName: "FCallReadOnlyWithKeysAndArgs(funcName)"},
	)
	batch.FunctionStats()
	testData = append(testData, CommandTestData{ExpectedResponse: models.FunctionStatsResult{
		Engines: map[string]models.Engine{
			"LUA": {
				Language:      "LUA",
				FunctionCount: 1,
				LibraryCount:  1,
			},
		},
	}, TestName: "FunctionStats()"})
	batch.FunctionDelete(libName)
	testData = append(testData, CommandTestData{ExpectedResponse: "OK", TestName: "FunctionDelete(libName)"})
	batch.FunctionLoad(libCode, false)
	testData = append(testData, CommandTestData{ExpectedResponse: libName, TestName: "FunctionLoad(libCode, false)"})
	batch.FunctionList(query)
	testData = append(testData, CommandTestData{ExpectedResponse: []models.LibraryInfo{
		{
			Engine: "LUA",
			Functions: []models.FunctionInfo{
				{
					Flags: []string{"no-writes"},
					Name:  funcName,
				},
			},
			Name: libName,
		},
	}, TestName: "FunctionList(query)"})

	batch.FunctionKill()
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: errors.New(""), CheckTypeOnly: true, TestName: "FunctionKill()"},
	)
	batch.FunctionDump()
	testData = append(testData, CommandTestData{ExpectedResponse: "", CheckTypeOnly: true, TestName: "FunctionDump()"})
	batch.FunctionRestore("payload")
	testData = append(
		testData,
		CommandTestData{ExpectedResponse: errors.New(""), CheckTypeOnly: true, TestName: "FunctionRestore()"},
	)
	batch.FunctionRestoreWithPolicy("payload", constants.FlushPolicy)
	testData = append(
		testData,
		CommandTestData{
			ExpectedResponse: errors.New(""),
			CheckTypeOnly:    true,
			TestName:         "FunctionRestoreWithPolicy(constants.FlushPolicy)",
		},
	)

	return BatchTestData{CommandTestData: testData, TestName: "Function commands"}
}

// ClusterBatch - The Batch object
// bool - isAtomic flag. True for transactions, false for pipeline
// string - The server version we are running on
type BatchTestDataProvider func(*pipeline.ClusterBatch, bool, string) BatchTestData

func GetKeyCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateBitmapTest,
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

func GetKeyLessCommandGroupTestProviders() []BatchTestDataProvider {
	return []BatchTestDataProvider{
		CreateConnectionManagementTests,
		CreateServerManagementTests,
		CreateScriptTest,
		CreateFunctionTest,
	}
}

type CommandTestData struct {
	ExpectedResponse any
	CheckTypeOnly    bool // don't validate the commmand response, only the response type
	TestName         string
}

type BatchTestData struct {
	CommandTestData []CommandTestData
	TestName        string
}

func (suite *GlideTestSuite) TestBatchCommandGroups() {
	for _, client := range suite.getDefaultClients() {
		for _, isAtomic := range []bool{true, false} {
			for _, testProvider := range GetKeyCommandGroupTestProviders() {
				batch := pipeline.NewClusterBatch(isAtomic)
				testData := testProvider(batch, isAtomic, suite.serverVersion)

				suite.T().Run(makeFullTestName(client, testData.TestName, isAtomic), func(t *testing.T) {
					res, err := runBatchOnClient(client, batch, true, nil)
					suite.NoError(err, testData.TestName)
					suite.verifyBatchTestResult(res, testData.CommandTestData)
				})
			}
			for _, testProvider := range GetKeyLessCommandGroupTestProviders() {
				batch := pipeline.NewClusterBatch(isAtomic)
				testData := testProvider(batch, isAtomic, suite.serverVersion)

				suite.T().Run(makeFullTestName(client, testData.TestName, isAtomic), func(t *testing.T) {
					res, err := runBatchOnClient(client, batch, false, config.NewSlotIdRoute(config.SlotTypePrimary, 42))
					suite.NoError(err, testData.TestName)
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

func runBatchOnClient(
	client interfaces.BaseClientCommands,
	batch *pipeline.ClusterBatch,
	raiseOnError bool,
	route config.SingleNodeRoute,
) ([]any, error) {
	switch c := client.(type) {
	case *glide.ClusterClient:
		if route != nil {
			opts := pipeline.NewClusterBatchOptions().WithRoute(route)
			return c.ExecWithOptions(context.Background(), *batch, raiseOnError, *opts)
		}
		return c.Exec(context.Background(), *batch, raiseOnError)
	case *glide.Client:
		// hacky hack ©
		standaloneBatch := pipeline.StandaloneBatch{BaseBatch: pipeline.BaseBatch[pipeline.StandaloneBatch]{Batch: batch.BaseBatch.Batch}}
		return c.Exec(context.Background(), standaloneBatch, raiseOnError)
	}
	return nil, nil
}

func makeFullTestName(client interfaces.BaseClientCommands, testName string, isAtomic bool) string {
	fullTestName := fmt.Sprintf("%T", client)[7:]
	if testName != "" {
		fullTestName += "/" + testName
	}
	if isAtomic {
		fullTestName += "/transaction"
	} else {
		fullTestName += "/pipeline"
	}
	return fullTestName
}
