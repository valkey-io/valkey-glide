// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"strings"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Standalone Client Tests

func (suite *GlideTestSuite) TestMemoryDoctor_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryDoctor(context.Background())

	assert.NoError(t, err)
	assert.NotEmpty(t, result, "MemoryDoctor should return a non-empty report")
	assert.IsType(t, "", result, "MemoryDoctor should return a string")
	assert.True(t, len(result) > 10, "MemoryDoctor report should have substantial content")
}

func (suite *GlideTestSuite) TestMemoryDoctor_StandaloneContentValidation() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryDoctor(context.Background())

	assert.NoError(t, err)
	lowerResult := strings.ToLower(result)
	hasExpectedContent := strings.Contains(lowerResult, "sam") ||
		strings.Contains(lowerResult, "memory") ||
		strings.Contains(lowerResult, "peak")
	assert.True(t, hasExpectedContent, "MemoryDoctor report should contain relevant keywords")
}

func (suite *GlideTestSuite) TestMemoryMallocStats_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryMallocStats(context.Background())

	assert.NoError(t, err)
	assert.IsType(t, "", result, "MemoryMallocStats should return a string")
}

func (suite *GlideTestSuite) TestMemoryPurge_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryPurge(context.Background())

	assert.NoError(t, err)
	assert.Equal(t, "OK", result, "MemoryPurge should return OK")
}

func (suite *GlideTestSuite) TestMemoryPurge_StandaloneIdempotency() {
	client := suite.defaultClient()
	t := suite.T()

	result1, err1 := client.MemoryPurge(context.Background())
	result2, err2 := client.MemoryPurge(context.Background())
	result3, err3 := client.MemoryPurge(context.Background())

	assert.NoError(t, err1)
	assert.NoError(t, err2)
	assert.NoError(t, err3)
	assert.Equal(t, "OK", result1)
	assert.Equal(t, "OK", result2)
	assert.Equal(t, "OK", result3)
}

func (suite *GlideTestSuite) TestMemoryStats_Standalone() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryStats(context.Background())

	assert.NoError(t, err)
	assert.NotNil(t, result, "MemoryStats should return a non-nil map")
	assert.True(t, len(result) > 0, "MemoryStats should return a map with data")
}

func (suite *GlideTestSuite) TestMemoryStats_StandaloneExpectedKeys() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryStats(context.Background())

	assert.NoError(t, err)
	assert.NotNil(t, result)

	expectedKeys := []string{
		"peak.allocated",
		"total.allocated",
		"startup.allocated",
		"replication.backlog",
		"clients.slaves",
		"clients.normal",
		"aof.buffer",
	}

	foundKeys := 0
	for _, key := range expectedKeys {
		if _, exists := result[key]; exists {
			foundKeys++
		}
	}

	assert.True(t, foundKeys > 0, "MemoryStats should contain at least one expected key")
}

func (suite *GlideTestSuite) TestMemoryStats_StandaloneValueTypes() {
	client := suite.defaultClient()
	t := suite.T()

	result, err := client.MemoryStats(context.Background())

	assert.NoError(t, err)
	assert.NotNil(t, result)

	for key, value := range result {
		switch v := value.(type) {
		case int, int64, int32, int16, int8:
			// Valid integer types
		case string:
			// Some values might be strings
		case map[string]any:
			// Nested maps are valid
		default:
			t.Logf("Key %s has unexpected type: %T", key, v)
		}
	}
}

func (suite *GlideTestSuite) TestMemoryStats_StandaloneWithDataOperations() {
	client := suite.defaultClient()
	t := suite.T()

	statsBefore, err := client.MemoryStats(context.Background())
	assert.NoError(t, err)

	for i := 0; i < 100; i++ {
		key := fmt.Sprintf("memory_test_key_%d", i)
		value := strings.Repeat("x", 1000)
		_, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)
	}

	statsAfter, err := client.MemoryStats(context.Background())
	assert.NoError(t, err)

	assert.NotNil(t, statsBefore)
	assert.NotNil(t, statsAfter)

	totalBefore, ok1 := statsBefore["total.allocated"].(int64)
	totalAfter, ok2 := statsAfter["total.allocated"].(int64)
	assert.True(t, ok1, "total.allocated should be present in stats before")
	assert.True(t, ok2, "total.allocated should be present in stats after")
	assert.Greater(t, totalAfter, totalBefore, "Memory should increase after writing data")
}

// Cluster Client Tests

func (suite *GlideTestSuite) TestMemoryDoctor_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryDoctor(context.Background())

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue(), "Default routing should return multi-value")

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue, "Should have results from multiple nodes")

	for _, report := range multiValue {
		assert.NotEmpty(t, report)
	}
}

func (suite *GlideTestSuite) TestMemoryDoctorWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryDoctorWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsSingleValue(), "RandomRoute should return single value")
	assert.NotEmpty(t, result.SingleValue())
}

func (suite *GlideTestSuite) TestMemoryMallocStats_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryMallocStats(context.Background())

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue(), "Default routing should return multi-value")
}

func (suite *GlideTestSuite) TestMemoryMallocStatsWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryMallocStatsWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsSingleValue(), "RandomRoute should return single value")
}

func (suite *GlideTestSuite) TestMemoryPurge_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryPurge(context.Background())

	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestMemoryPurgeWithOptions_ClusterAllNodes() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.AllNodes}
	result, err := client.MemoryPurgeWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.Equal(t, "OK", result)
}

func (suite *GlideTestSuite) TestMemoryStats_Cluster() {
	client := suite.defaultClusterClient()
	t := suite.T()

	result, err := client.MemoryStats(context.Background())

	assert.NoError(t, err)
	assert.True(t, result.IsMultiValue(), "Default routing should return multi-value")

	multiValue := result.MultiValue()
	assert.NotEmpty(t, multiValue)

	for _, stats := range multiValue {
		assert.NotNil(t, stats)
		assert.True(t, len(stats) > 0)
	}
}

func (suite *GlideTestSuite) TestMemoryStatsWithOptions_ClusterSingleNode() {
	client := suite.defaultClusterClient()
	t := suite.T()

	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryStatsWithOptions(context.Background(), opts)

	assert.NoError(t, err)
	assert.True(t, result.IsSingleValue(), "RandomRoute should return single value")

	stats := result.SingleValue()
	assert.NotNil(t, stats)
	assert.True(t, len(stats) > 0)
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
	assert.IsType(t, "", result2)

	result3, err3 := client.MemoryPurge(context.Background())
	assert.NoError(t, err3)
	assert.Equal(t, "OK", result3)

	result4, err4 := client.MemoryStats(context.Background())
	assert.NoError(t, err4)
	assert.NotNil(t, result4)
	assert.True(t, len(result4) > 0)
}
