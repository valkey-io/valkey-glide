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

func (suite *GlideTestSuite) TestBatchGeoSpatial() {
	suite.runBatchTest(func(client interfaces.BaseClientCommands, isAtomic bool) {
		testData := make([]CommandTestData, 0)
		prefix := "{GeoKey}-"
		atomicPrefix := prefix
		if !isAtomic {
			atomicPrefix = ""
		}
		key := atomicPrefix + "1-" + uuid.NewString()
		destKey := atomicPrefix + "2-" + uuid.NewString()
		membersToGeospatialData := map[string]options.GeospatialData{
			"Palermo": {Longitude: 13.361389, Latitude: 38.115556},
			"Catania": {Longitude: 15.087269, Latitude: 37.502669},
		}
		membersToGeospatialData2 := map[string]options.GeospatialData{
			"Messina": {Longitude: 15.556349, Latitude: 38.194136},
		}

		switch c := client.(type) {
		case *glide.ClusterClient:
			batch := pipeline.NewClusterBatch(isAtomic)

			batch.GeoAdd(key, membersToGeospatialData)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "GeoAdd(key, membersToGeospatialData)"})

			geoAddOptions := options.GeoAddOptions{}
			geoAddOptions.SetConditionalChange(constants.OnlyIfDoesNotExist)
			batch.GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)"})

			batch.GeoHash(key, []string{"Palermo", "Catania", "NonExistingCity"})
			testData = append(testData, CommandTestData{
				ExpectedResponse: []any{"sqc8b49rny0", "sqdtr74hyu0", nil},
				TestName:         "GeoHash(key, [Palermo, Catania, NonExistingCity])",
			})

			batch.GeoPos(key, []string{"Palermo", "NonExistingCity"})
			// We can't directly assert on the float values due to precision differences
			testData = append(testData, CommandTestData{TestName: "GeoPos(key, [Palermo, NonExistingCity])"})

			batch.GeoDist(key, "Palermo", "Catania")
			testData = append(testData, CommandTestData{TestName: "GeoDist(key, Palermo, Catania)"})

			batch.GeoDistWithUnit(key, "Palermo", "Catania", constants.GeoUnitKilometers)
			testData = append(testData, CommandTestData{TestName: "GeoDistWithUnit(key, Palermo, Catania, Kilometers)"})

			searchFrom := &options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15.0, Latitude: 37.0},
			}
			searchByShape := options.NewCircleSearchShape(200, constants.GeoUnitKilometers)
			batch.GeoSearch(key, searchFrom, *searchByShape)
			// Can't assert on exact order as it may vary
			testData = append(testData, CommandTestData{TestName: "GeoSearch(key, searchFrom, searchByShape)"})

			resultOptions := options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.ASC)
			batch.GeoSearchWithResultOptions(key, searchFrom, *searchByShape, *resultOptions)
			testData = append(testData, CommandTestData{
				ExpectedResponse: []any{"Catania"},
				TestName:         "GeoSearchWithResultOptions(key, searchFrom, searchByShape, resultOptions)",
			})

			infoOptions := options.NewGeoSearchInfoOptions().SetWithDist(true)
			batch.GeoSearchWithInfoOptions(key, searchFrom, *searchByShape, *infoOptions)
			testData = append(testData, CommandTestData{TestName: "GeoSearchWithInfoOptions(key, searchFrom, searchByShape, infoOptions)"})

			batch.GeoSearchWithFullOptions(key, searchFrom, *searchByShape, *resultOptions, *infoOptions)
			testData = append(testData, CommandTestData{TestName: "GeoSearchWithFullOptions(key, searchFrom, searchByShape, resultOptions, infoOptions)"})

			batch.Copy(key, prefix+key)
			testData = append(testData, CommandTestData{ExpectedResponse: true, TestName: "Copy(key, prefix+key)"})
			fmt.Println(prefix + destKey)
			fmt.Println(prefix + key)
			key = prefix + key
			destKey = prefix + destKey
			batch.GeoSearchStore(destKey, key, searchFrom, *searchByShape)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "GeoSearchStore(prefix+destKey, prefix+key, searchFrom, searchByShape)"})

			// batch.GeoSearchStoreWithResultOptions(prefix+destKey, prefix+key, searchFrom, *searchByShape, *resultOptions)
			// testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GeoSearchStoreWithResultOptions(prefix+destKey, prefix+key, searchFrom, searchByShape, resultOptions)"})

			// storeInfoOptions := options.NewGeoSearchStoreInfoOptions().SetStoreDist(true)
			// batch.GeoSearchStoreWithInfoOptions(prefix+destKey, prefix+key, searchFrom, *searchByShape, *storeInfoOptions)
			// testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "GeoSearchStoreWithInfoOptions(prefix+destKey, prefix+key, searchFrom, searchByShape, storeInfoOptions)"})

			// batch.GeoSearchStoreWithFullOptions(prefix+destKey, prefix+key, searchFrom, *searchByShape, *resultOptions, *storeInfoOptions)
			// testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GeoSearchStoreWithFullOptions(prefix+destKey, prefix+key, searchFrom, searchByShape, resultOptions, storeInfoOptions)"})

			res, err := c.Exec(context.Background(), *batch, true)
			assert.NoError(suite.T(), err)

			// Verify results that don't depend on floating point precision
			for i, td := range testData {
				if td.ExpectedResponse != nil {
					assert.Equal(suite.T(), td.ExpectedResponse, res[i], td.TestName)
				}
			}

			// // Verify GeoPos results
			// geoPos := res[3].([]any)
			// assert.Len(suite.T(), geoPos, 2)
			// assert.NotNil(suite.T(), geoPos[0])
			// assert.Nil(suite.T(), geoPos[1])

			// // Verify distance results (approximately)
			// geoDist := res[4].(float64)
			// assert.InDelta(suite.T(), 166274.15, geoDist, 1.0)

			// geoDistKm := res[5].(float64)
			// assert.InDelta(suite.T(), 166.27, geoDistKm, 0.1)

			// // Verify search results
			// geoSearch := res[6]
			// assert.Len(suite.T(), geoSearch, 3)
			// assert.Contains(suite.T(), geoSearch, "Palermo")
			// assert.Contains(suite.T(), geoSearch, "Catania")
			// assert.Contains(suite.T(), geoSearch, "Messina")

			// // Verify search with info results
			// geoSearchInfo := res[8].([]any)
			// assert.Len(suite.T(), geoSearchInfo, 3)

			// // Verify full search results
			// geoSearchFull := res[9].([]any)
			// assert.Len(suite.T(), geoSearchFull, 1)
		case *glide.Client:
			batch := pipeline.NewStandaloneBatch(isAtomic)

			batch.GeoAdd(key, membersToGeospatialData)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(2), TestName: "GeoAdd(key, membersToGeospatialData)"})

			geoAddOptions := options.GeoAddOptions{}
			geoAddOptions.SetConditionalChange(constants.OnlyIfDoesNotExist)
			batch.GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GeoAddWithOptions(key, membersToGeospatialData2, geoAddOptions)"})

			batch.GeoHash(key, []string{"Palermo", "Catania", "NonExistingCity"})
			testData = append(testData, CommandTestData{
				ExpectedResponse: []any{"sqc8b49rny0", "sqdtr74hyu0", nil},
				TestName:         "GeoHash(key, [Palermo, Catania, NonExistingCity])",
			})

			batch.GeoPos(key, []string{"Palermo", "NonExistingCity"})
			// We can't directly assert on the float values due to precision differences
			testData = append(testData, CommandTestData{TestName: "GeoPos(key, [Palermo, NonExistingCity])"})

			batch.GeoDist(key, "Palermo", "Catania")
			testData = append(testData, CommandTestData{TestName: "GeoDist(key, Palermo, Catania)"})

			batch.GeoDistWithUnit(key, "Palermo", "Catania", constants.GeoUnitKilometers)
			testData = append(testData, CommandTestData{TestName: "GeoDistWithUnit(key, Palermo, Catania, Kilometers)"})

			searchFrom := &options.GeoCoordOrigin{
				GeospatialData: options.GeospatialData{Longitude: 15.0, Latitude: 37.0},
			}
			searchByShape := options.NewCircleSearchShape(200, constants.GeoUnitKilometers)
			batch.GeoSearch(key, searchFrom, *searchByShape)
			// Can't assert on exact order as it may vary
			testData = append(testData, CommandTestData{TestName: "GeoSearch(key, searchFrom, searchByShape)"})

			resultOptions := options.NewGeoSearchResultOptions().SetCount(1).SetSortOrder(options.ASC)
			batch.GeoSearchWithResultOptions(key, searchFrom, *searchByShape, *resultOptions)
			testData = append(testData, CommandTestData{
				ExpectedResponse: []any{"Catania"},
				TestName:         "GeoSearchWithResultOptions(key, searchFrom, searchByShape, resultOptions)",
			})

			infoOptions := options.NewGeoSearchInfoOptions().SetWithDist(true)
			batch.GeoSearchWithInfoOptions(key, searchFrom, *searchByShape, *infoOptions)
			testData = append(testData, CommandTestData{TestName: "GeoSearchWithInfoOptions(key, searchFrom, searchByShape, infoOptions)"})

			batch.GeoSearchWithFullOptions(key, searchFrom, *searchByShape, *resultOptions, *infoOptions)
			testData = append(testData, CommandTestData{TestName: "GeoSearchWithFullOptions(key, searchFrom, searchByShape, resultOptions, infoOptions)"})

			batch.GeoSearchStore(destKey, key, searchFrom, *searchByShape)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "GeoSearchStore(destKey, key, searchFrom, searchByShape)"})

			batch.GeoSearchStoreWithResultOptions(destKey+"1", key, searchFrom, *searchByShape, *resultOptions)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GeoSearchStoreWithResultOptions(destKey+1, key, searchFrom, searchByShape, resultOptions)"})

			storeInfoOptions := options.NewGeoSearchStoreInfoOptions().SetStoreDist(true)
			batch.GeoSearchStoreWithInfoOptions(destKey+"2", key, searchFrom, *searchByShape, *storeInfoOptions)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(3), TestName: "GeoSearchStoreWithInfoOptions(destKey+2, key, searchFrom, searchByShape, storeInfoOptions)"})

			batch.GeoSearchStoreWithFullOptions(destKey+"3", key, searchFrom, *searchByShape, *resultOptions, *storeInfoOptions)
			testData = append(testData, CommandTestData{ExpectedResponse: int64(1), TestName: "GeoSearchStoreWithFullOptions(destKey+3, key, searchFrom, searchByShape, resultOptions, storeInfoOptions)"})

			res, err := c.Exec(context.Background(), *batch, true)
			assert.NoError(suite.T(), err)

			// Verify results that don't depend on floating point precision
			for i, td := range testData {
				if td.ExpectedResponse != nil {
					assert.Equal(suite.T(), td.ExpectedResponse, res[i], td.TestName)
				}
			}

			// Verify GeoPos results
			geoPos := res[3].([]any)
			assert.Len(suite.T(), geoPos, 2)
			assert.NotNil(suite.T(), geoPos[0])
			assert.Nil(suite.T(), geoPos[1])

			// Verify distance results (approximately)
			geoDist := res[4].(float64)
			assert.InDelta(suite.T(), 166274.15, geoDist, 1.0)

			geoDistKm := res[5].(float64)
			assert.InDelta(suite.T(), 166.27, geoDistKm, 0.1)

			// Verify search results
			geoSearch := res[6]
			assert.Len(suite.T(), geoSearch, 3)
			assert.Contains(suite.T(), geoSearch, "Palermo")
			assert.Contains(suite.T(), geoSearch, "Catania")
			assert.Contains(suite.T(), geoSearch, "Messina")

			// Verify search with info results
			geoSearchInfo := res[8].([]any)
			assert.Len(suite.T(), geoSearchInfo, 3)

			// Verify full search results
			geoSearchFull := res[9].([]any)
			assert.Len(suite.T(), geoSearchFull, 1)
		}
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
