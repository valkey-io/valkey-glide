// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

func (suite *GlideTestSuite) TestMemoryDoctor_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryDoctor(context.Background())

	assert.NoError(t, err)
	assert.NotEmpty(t, result)
}

func (suite *GlideTestSuite) TestMemoryMallocStats_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryMallocStats(context.Background())

	assert.NoError(t, err)
	assert.NotEmpty(t, result)
}

func (suite *GlideTestSuite) TestMemoryPurge_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryPurge(context.Background())

	assert.NoError(t, err)
	assert.Equal(t, "OK", result, "MemoryPurge should return OK")
}

func (suite *GlideTestSuite) TestMemoryStats_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	// Write a key to ensure db map has entries
	_, err := client.Set(context.Background(), "memory_stats_test_key", "test_value")
	assert.NoError(t, err)

	result, err := client.MemoryStats(context.Background())
	assert.NoError(t, err)

	suite.assertMemoryStatsFields(result, false)
}

func (suite *GlideTestSuite) TestMemoryDoctor_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryDoctor(context.Background())

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)

	for addr, report := range multiValue {
		assert.NotEmpty(t, addr)
		assert.NotEmpty(t, report)
	}
}

func (suite *GlideTestSuite) TestMemoryMallocStats_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryMallocStats(context.Background())

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)

	for addr, stats := range multiValue {
		assert.NotEmpty(t, addr)
		assert.NotEmpty(t, stats)
	}
}

func (suite *GlideTestSuite) TestMemoryPurge_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryPurge(context.Background())

	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestMemoryStats_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Write a key to ensure db map has entries on at least one node
	_, err := client.Set(context.Background(), "memory_stats_cluster_key", "test_value")
	assert.NoError(t, err)

	result, err := client.MemoryStats(context.Background())

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)

	for addr, stats := range multiValue {
		assert.NotEmpty(t, addr)
		suite.assertMemoryStatsFields(stats, true)
	}
}

func (suite *GlideTestSuite) TestMemoryDoctorWithOptions_ClusterAllNodes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.AllNodes}
	result, err := client.MemoryDoctorWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)

	for _, report := range multiValue {
		assert.NotEmpty(t, report)
	}
}

func (suite *GlideTestSuite) TestMemoryMallocStatsWithOptions_ClusterAllNodes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.AllNodes}
	result, err := client.MemoryMallocStatsWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)
}

func (suite *GlideTestSuite) TestMemoryPurgeWithOptions_ClusterAllNodes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.AllNodes}
	result, err := client.MemoryPurgeWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestMemoryStatsWithOptions_ClusterAllNodes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.AllNodes}
	result, err := client.MemoryStatsWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue())

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)

	for addr, stats := range multiValue {
		assert.NotEmpty(t, addr)
		suite.assertMemoryStatsFields(stats, true)
	}
}

func (suite *GlideTestSuite) TestMemoryDoctorWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryDoctorWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsSingleValue())
	assert.NotEmpty(t, result.SingleValue())
}

func (suite *GlideTestSuite) TestMemoryMallocStatsWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryMallocStatsWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsSingleValue())
	assert.NotEmpty(t, result.SingleValue())
}

func (suite *GlideTestSuite) TestMemoryPurgeWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryPurgeWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestMemoryStatsWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryStatsWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsSingleValue(), "RandomRoute should return single value")

	stats := result.SingleValue()
	suite.assertMemoryStatsFields(stats, true)
}

func (suite *GlideTestSuite) TestMemoryCommands_BatchTransaction_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	// Write a key so MEMORY STATS has db entries
	_, err := client.Set(context.Background(), "batch_memory_cluster_key", "value")
	assert.NoError(t, err)

	batch := pipeline.NewClusterBatch(true) // atomic (transaction)
	batch.MemoryDoctor()
	batch.MemoryMallocStats()
	batch.MemoryPurge()
	batch.MemoryStats()

	results, err := client.Exec(context.Background(), *batch, true)
	assert.NoError(t, err)
	assert.Len(t, results, 4)

	// Result 0: MemoryDoctor — string
	doctor, ok := results[0].(string)
	assert.True(t, ok)
	assert.NotEmpty(t, doctor)

	// Result 1: MemoryMallocStats — string
	mallocStats, ok := results[1].(string)
	assert.True(t, ok)
	assert.NotEmpty(t, mallocStats)

	// Result 2: MemoryPurge — "OK"
	purge, ok := results[2].(string)
	assert.True(t, ok)
	assert.Equal(t, "OK", purge)

	// Result 3: MemoryStats — models.MemoryStats
	stats, ok := results[3].(models.MemoryStats)
	assert.True(t, ok)
	suite.assertMemoryStatsFields(stats, true)
}

func (suite *GlideTestSuite) TestMemoryCommands_BatchPipeline_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	batch := pipeline.NewStandaloneBatch(false) // non-atomic (pipeline)
	batch.MemoryDoctor()
	batch.MemoryMallocStats()
	batch.MemoryPurge()
	batch.MemoryStats()

	results, err := client.Exec(context.Background(), *batch, true)
	assert.NoError(t, err)
	assert.Len(t, results, 4)

	// Verify order and types
	_, ok := results[0].(string)
	assert.True(t, ok)

	_, ok = results[1].(string)
	assert.True(t, ok)

	purge, ok := results[2].(string)
	assert.True(t, ok)
	assert.Equal(t, "OK", purge)

	_, ok = results[3].(models.MemoryStats)
	assert.True(t, ok)
}

// Context Cancellation Tests

func (suite *GlideTestSuite) TestMemoryCommands_StandaloneContextCancellation() {
	client := suite.defaultClient()
	t := suite.T()

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := client.MemoryDoctor(ctx)
	assert.Error(t, err)

	_, err = client.MemoryMallocStats(ctx)
	assert.Error(t, err)

	_, err = client.MemoryPurge(ctx)
	assert.Error(t, err)

	_, err = client.MemoryStats(ctx)
	assert.Error(t, err)
}

func (suite *GlideTestSuite) TestMemoryCommands_StandaloneSequentialExecution() {
	client := suite.defaultClient()
	t := suite.T()

	result1, err1 := client.MemoryDoctor(context.Background())
	assert.NoError(t, err1)
	assert.NotEmpty(t, result1)

	result2, err2 := client.MemoryMallocStats(context.Background())
	assert.NoError(t, err2)
	assert.NotEmpty(t, result2)

	result3, err3 := client.MemoryPurge(context.Background())
	assert.NoError(t, err3)
	assert.Equal(t, "OK", result3)

	result4, err4 := client.MemoryStats(context.Background())
	assert.NoError(t, err4)
	suite.assertMemoryStatsFields(result4, false)
}

// assertMemoryStatsFields validates all expected fields in a MemoryStats result.
func (suite *GlideTestSuite) assertMemoryStatsFields(result models.MemoryStats, isCluster bool) {
	t := suite.T()

	assert.Greater(t, result.PeakAllocated, int64(0))
	assert.Greater(t, result.TotalAllocated, int64(0))
	assert.Greater(t, result.StartupAllocated, int64(0))
	assert.GreaterOrEqual(t, result.ReplicationBacklog, int64(0))
	assert.GreaterOrEqual(t, result.ClientsSlaves, int64(0))
	assert.GreaterOrEqual(t, result.ClientsNormal, int64(0))
	assert.GreaterOrEqual(t, result.AofBuffer, int64(0))
	assert.GreaterOrEqual(t, result.LuaCaches, int64(0))
	assert.Greater(t, result.OverheadTotal, int64(0))
	assert.GreaterOrEqual(t, result.KeysCount, int64(0))
	assert.GreaterOrEqual(t, result.KeysBytesPerKey, int64(0))
	assert.GreaterOrEqual(t, result.DatasetBytes, int64(0))
	assert.GreaterOrEqual(t, result.DatasetPercentage, float64(0))
	assert.GreaterOrEqual(t, result.PeakPercentage, float64(0))
	assert.Greater(t, result.AllocatorAllocated, int64(0))
	assert.Greater(t, result.AllocatorActive, int64(0))
	assert.Greater(t, result.AllocatorResident, int64(0))
	assert.GreaterOrEqual(t, result.AllocatorFragmentationRatio, float64(0))
	assert.GreaterOrEqual(t, result.AllocatorFragmentationBytes, int64(0))
	assert.GreaterOrEqual(t, result.AllocatorRssRatio, float64(0))
	assert.GreaterOrEqual(t, result.AllocatorRssBytes, int64(0))
	assert.GreaterOrEqual(t, result.RssOverheadRatio, float64(0))
	assert.GreaterOrEqual(t, result.RssOverheadBytes, int64(0))
	assert.GreaterOrEqual(t, result.Fragmentation, float64(0))
	assert.GreaterOrEqual(t, result.FragmentationBytes, int64(0))
	assert.GreaterOrEqual(t, result.ClusterLinks, int64(0))
	assert.GreaterOrEqual(t, result.FunctionsCaches, int64(0))
	assert.GreaterOrEqual(t, result.AllocatorMuzzy, int64(0))

	if suite.serverVersion >= "8.0.0" {
		assert.False(t, result.OverheadDbHashtableLut.IsNil())
		assert.False(t, result.OverheadDbHashtableRehashing.IsNil())
		assert.False(t, result.DbDictRehashingCount.IsNil())
	} else {
		assert.True(t, result.OverheadDbHashtableLut.IsNil())
		assert.True(t, result.OverheadDbHashtableRehashing.IsNil())
		assert.True(t, result.DbDictRehashingCount.IsNil())
	}

	assert.NotEmpty(t, result.Db)
	for _, dbStats := range result.Db {
		assert.GreaterOrEqual(t, dbStats.OverheadHashtableMain, int64(0))
		assert.GreaterOrEqual(t, dbStats.OverheadHashtableExpires, int64(0))
	}
}
