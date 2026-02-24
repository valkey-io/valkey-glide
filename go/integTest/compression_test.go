// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/rand"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

// --- Data generation helpers ---

func generateCompressibleText(sizeBytes int) string {
	pattern := strings.Repeat("A", 10) + strings.Repeat("B", 10) + strings.Repeat("C", 10)
	repeats := (sizeBytes / len(pattern)) + 1
	return strings.Repeat(pattern, repeats)[:sizeBytes]
}

func generateJSONData(sizeBytes int) string {
	obj := map[string]interface{}{
		"id":          12345,
		"name":        "Test User",
		"email":       "test@example.com",
		"description": strings.Repeat("A", 100),
		"metadata":    map[string]string{"key": "value"},
		"tags":        []string{"tag1", "tag2", "tag3"},
	}
	jsonStr, _ := json.Marshal(obj)
	s := string(jsonStr)
	repeats := (sizeBytes / len(s)) + 1
	return strings.Repeat(s, repeats)[:sizeBytes]
}

func generateBase64Data(sizeBytes int) string {
	raw := make([]byte, sizeBytes/2)
	for i := range raw {
		raw[i] = byte(rand.Intn(256))
	}
	encoded := base64.StdEncoding.EncodeToString(raw)
	if len(encoded) > sizeBytes {
		return encoded[:sizeBytes]
	}
	return encoded
}

func randomString(n int) string {
	return uuid.New().String()[:n]
}

// --- Helper to create compression-enabled clients ---

func (suite *GlideTestSuite) compressionClient() *glide.Client {
	compressionConfig := config.NewCompressionConfiguration()
	clientConfig := suite.defaultClientConfig().
		WithCompressionConfiguration(compressionConfig)
	client, err := suite.client(clientConfig)
	assert.NoError(suite.T(), err)
	return client
}

func (suite *GlideTestSuite) compressionClusterClient() *glide.ClusterClient {
	compressionConfig := config.NewCompressionConfiguration()
	clientConfig := suite.defaultClusterClientConfig().
		WithCompressionConfiguration(compressionConfig)
	client, err := suite.clusterClient(clientConfig)
	assert.NoError(suite.T(), err)
	return client
}

func (suite *GlideTestSuite) compressionClientWithBackend(
	backend config.CompressionBackend,
) *glide.Client {
	compressionConfig := config.NewCompressionConfiguration().
		WithBackend(backend)
	clientConfig := suite.defaultClientConfig().
		WithCompressionConfiguration(compressionConfig)
	client, err := suite.client(clientConfig)
	assert.NoError(suite.T(), err)
	return client
}

func (suite *GlideTestSuite) compressionClientWithLevel(
	backend config.CompressionBackend,
	level int32,
) (*glide.Client, error) {
	compressionConfig := config.NewCompressionConfiguration().
		WithBackend(backend).
		WithCompressionLevel(level)
	clientConfig := suite.defaultClientConfig().
		WithCompressionConfiguration(compressionConfig)
	return glide.NewClient(clientConfig)
}

// ============================================================================
// Basic Compression Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionBasicSetGet() {
	client := suite.compressionClient()
	defer client.Close()

	dataSizes := []int{512, 1024, 10240, 102400}

	for _, size := range dataSizes {
		suite.T().Run(fmt.Sprintf("size_%d", size), func(t *testing.T) {
			key := fmt.Sprintf("test_compression_%d_%s", size, randomString(8))
			value := generateCompressibleText(size)

			// Get initial statistics
			initialStats := client.GetStatistics()
			initialCompressed := initialStats["total_values_compressed"]
			initialOriginalBytes := initialStats["total_original_bytes"]
			initialBytesCompressed := initialStats["total_bytes_compressed"]

			// Set value with compression
			result, err := client.Set(context.Background(), key, value)
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)

			// Get value and verify it matches
			retrieved, err := client.Get(context.Background(), key)
			assert.NoError(t, err)
			assert.Equal(t, value, retrieved.Value())

			// Verify compression was applied
			stats := client.GetStatistics()
			assert.Greater(t, stats["total_values_compressed"], initialCompressed,
				"Compression should be applied for %dB value", size)

			// Verify invariant: compressed bytes <= original bytes
			bytesAddedOriginal := stats["total_original_bytes"] - initialOriginalBytes
			bytesAddedCompressed := stats["total_bytes_compressed"] - initialBytesCompressed
			assert.LessOrEqual(t, bytesAddedCompressed, bytesAddedOriginal,
				"Compressed size should be <= original size")

			// Cleanup
			client.Del(context.Background(), []string{key})
		})
	}
}

func (suite *GlideTestSuite) TestCompressionMinSizeThreshold() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	// Get initial statistics
	initialStats := client.GetStatistics()
	initialSkipped := initialStats["compression_skipped_count"]
	initialCompressed := initialStats["total_values_compressed"]

	// Test values below threshold (should be skipped)
	for _, size := range []int{32, 48, 63} {
		key := fmt.Sprintf("below_threshold_%d_%s", size, randomString(8))
		value := generateCompressibleText(size)

		_, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)

		retrieved, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value, retrieved.Value())

		stats := client.GetStatistics()
		assert.Greater(t, stats["compression_skipped_count"], initialSkipped,
			"Size %d: Compression should be skipped below threshold", size)
		assert.Equal(t, initialCompressed, stats["total_values_compressed"],
			"Size %d: No values should be compressed below threshold", size)

		initialSkipped = stats["compression_skipped_count"]
		client.Del(context.Background(), []string{key})
	}

	// Test values at/above threshold (should be compressed)
	for _, size := range []int{64, 128, 256} {
		key := fmt.Sprintf("above_threshold_%d_%s", size, randomString(8))
		value := generateCompressibleText(size)

		_, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)

		retrieved, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value, retrieved.Value())

		stats := client.GetStatistics()
		assert.Greater(t, stats["total_values_compressed"], initialCompressed,
			"Size %d: Compression should be applied at/above threshold", size)

		initialCompressed = stats["total_values_compressed"]
		client.Del(context.Background(), []string{key})
	}
}

func (suite *GlideTestSuite) TestCompressionDisabledByDefault() {
	client := suite.defaultClient()
	defer client.Close()

	t := suite.T()

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]
	initialSkipped := initialStats["compression_skipped_count"]

	sizes := []int{64, 1024, 10240}
	for _, size := range sizes {
		key := fmt.Sprintf("no_compression_%d_%s", size, randomString(8))
		value := generateCompressibleText(size)

		result, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		retrieved, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value, retrieved.Value())

		stats := client.GetStatistics()
		assert.Equal(t, initialCompressed, stats["total_values_compressed"],
			"No compression should be applied when disabled. Size: %dB", size)
		assert.Equal(t, initialSkipped, stats["compression_skipped_count"],
			"Compression should not even be attempted when disabled. Size: %dB", size)

		client.Del(context.Background(), []string{key})
	}
}

// ============================================================================
// Data Type Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionDataTypes() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	type dataGenerator struct {
		name     string
		generate func(int) string
	}

	generators := []dataGenerator{
		{"compressible_text", generateCompressibleText},
		{"json", generateJSONData},
		{"base64", generateBase64Data},
	}

	for _, gen := range generators {
		for _, size := range []int{1024, 10240} {
			suite.T().Run(fmt.Sprintf("%s_%d", gen.name, size), func(_ *testing.T) {
				key := fmt.Sprintf("test_%s_%d_%s", gen.name, size, randomString(8))
				value := gen.generate(size)

				initialStats := client.GetStatistics()
				initialCompressed := initialStats["total_values_compressed"]

				result, err := client.Set(context.Background(), key, value)
				assert.NoError(t, err)
				assert.Equal(t, "OK", result)

				retrieved, err := client.Get(context.Background(), key)
				assert.NoError(t, err)
				assert.Equal(t, value, retrieved.Value())

				stats := client.GetStatistics()
				assert.Greater(t, stats["total_values_compressed"], initialCompressed,
					"Compression should be applied for %s %dB value", gen.name, size)

				client.Del(context.Background(), []string{key})
			})
		}
	}
}

// ============================================================================
// Backend Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionZSTDBackend() {
	client := suite.compressionClientWithBackend(config.ZSTD)
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("zstd_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	result, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionLZ4Backend() {
	client := suite.compressionClientWithBackend(config.LZ4)
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("lz4_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	result, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed)

	client.Del(context.Background(), []string{key})
}

// ============================================================================
// Compression Level Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionValidLevels() {
	t := suite.T()

	testCases := []struct {
		backend config.CompressionBackend
		level   int32
	}{
		{config.ZSTD, 1},
		{config.ZSTD, 3},
		{config.ZSTD, 10},
		{config.ZSTD, 22},
		{config.ZSTD, -5},
		{config.LZ4, 0},
		{config.LZ4, 1},
		{config.LZ4, 6},
		{config.LZ4, 12},
		{config.LZ4, -10},
		{config.LZ4, -128},
	}

	for _, tc := range testCases {
		backendName := "ZSTD"
		if tc.backend == config.LZ4 {
			backendName = "LZ4"
		}
		suite.T().Run(fmt.Sprintf("%s_level_%d", backendName, tc.level), func(_ *testing.T) {
			client, err := suite.compressionClientWithLevel(tc.backend, tc.level)
			if err != nil {
				t.Fatalf("Failed to create client with %s level %d: %v", backendName, tc.level, err)
			}
			defer client.Close()

			key := fmt.Sprintf("level_test_%s_%d_%s", backendName, tc.level, randomString(8))
			value := generateCompressibleText(1024)

			initialStats := client.GetStatistics()
			initialCompressed := initialStats["total_values_compressed"]

			result, err := client.Set(context.Background(), key, value)
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)

			retrieved, err := client.Get(context.Background(), key)
			assert.NoError(t, err)
			assert.Equal(t, value, retrieved.Value())

			stats := client.GetStatistics()
			assert.Greater(t, stats["total_values_compressed"], initialCompressed,
				"Compression should be applied for %s level %d", backendName, tc.level)

			client.Del(context.Background(), []string{key})
		})
	}
}

func (suite *GlideTestSuite) TestCompressionInvalidLevels() {
	t := suite.T()

	testCases := []struct {
		backend config.CompressionBackend
		level   int32
	}{
		{config.ZSTD, 23},
		{config.ZSTD, 100},
		{config.ZSTD, -200000},
		{config.LZ4, 13},
		{config.LZ4, 100},
		{config.LZ4, -129},
		{config.LZ4, -1000},
	}

	for _, tc := range testCases {
		backendName := "ZSTD"
		if tc.backend == config.LZ4 {
			backendName = "LZ4"
		}
		suite.T().Run(fmt.Sprintf("%s_invalid_level_%d", backendName, tc.level), func(_ *testing.T) {
			_, err := suite.compressionClientWithLevel(tc.backend, tc.level)
			assert.Error(t, err, "Creating client with %s level %d should fail", backendName, tc.level)

			errMsg := strings.ToLower(err.Error())
			assert.True(t, strings.Contains(errMsg, "compression") || strings.Contains(errMsg, "level"),
				"Error should mention compression level issue: %v", err)
		})
	}
}

// ============================================================================
// Edge Case Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionEmptyValues() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("empty_test_%s", randomString(8))

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]
	initialSkipped := initialStats["compression_skipped_count"]

	result, err := client.Set(context.Background(), key, "")
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, "", retrieved.Value())

	stats := client.GetStatistics()
	assert.Greater(t, stats["compression_skipped_count"], initialSkipped,
		"Empty value should be skipped")
	assert.Equal(t, initialCompressed, stats["total_values_compressed"],
		"Empty value should not be compressed")

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionVeryLargeValues() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("very_large_%s", randomString(8))
	size := 10 * 1024 * 1024 // 10MB
	value := generateCompressibleText(size)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]
	initialOriginalBytes := initialStats["total_original_bytes"]
	initialBytesCompressed := initialStats["total_bytes_compressed"]

	result, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed,
		"Compression should be applied for 10MB value")

	bytesAddedOriginal := stats["total_original_bytes"] - initialOriginalBytes
	bytesAddedCompressed := stats["total_bytes_compressed"] - initialBytesCompressed
	assert.LessOrEqual(t, bytesAddedCompressed, bytesAddedOriginal,
		"Large value: Compressed size should be <= original size")

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionBackendMismatch() {
	// Write with ZSTD, read with LZ4 - data should still be readable
	zstdClient := suite.compressionClientWithBackend(config.ZSTD)
	defer zstdClient.Close()

	lz4Client := suite.compressionClientWithBackend(config.LZ4)
	defer lz4Client.Close()

	t := suite.T()

	key := fmt.Sprintf("backend_mismatch_%s", randomString(8))
	value := generateCompressibleText(10240)

	result, err := zstdClient.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Read with LZ4 client - should still work (decompression is transparent)
	retrieved, err := lz4Client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	zstdClient.Del(context.Background(), []string{key})
}

// ============================================================================
// Compatibility Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionWithTTL() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("ttl_test_%s", randomString(8))
	value := generateCompressibleText(10240)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	result, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	ok, err := client.Expire(context.Background(), key, 10*time.Second)
	assert.NoError(t, err)
	assert.True(t, ok)

	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	ttl, err := client.TTL(context.Background(), key)
	assert.NoError(t, err)
	assert.Greater(t, ttl, int64(0))
	assert.LessOrEqual(t, ttl, int64(10))

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed,
		"Compression should be applied with TTL")

	client.Del(context.Background(), []string{key})
}

// ============================================================================
// Cluster Compression Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionClusterBasicSetGet() {
	client := suite.compressionClusterClient()
	defer client.Close()

	t := suite.T()

	for _, size := range []int{512, 1024, 10240} {
		key := fmt.Sprintf("cluster_compression_%d_%s", size, randomString(8))
		value := generateCompressibleText(size)

		initialStats := client.GetStatistics()
		initialCompressed := initialStats["total_values_compressed"]

		result, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		retrieved, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value, retrieved.Value())

		stats := client.GetStatistics()
		assert.Greater(t, stats["total_values_compressed"], initialCompressed,
			"Cluster: Compression should be applied for %dB value", size)

		client.Del(context.Background(), []string{key})
	}
}

func (suite *GlideTestSuite) TestCompressionClusterMultiSlot() {
	client := suite.compressionClusterClient()
	defer client.Close()

	t := suite.T()

	numKeys := 50
	keysAndValues := make(map[string]string, numKeys)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	for i := 0; i < numKeys; i++ {
		key := fmt.Sprintf("multislot_%d_%s", i, randomString(8))
		value := generateCompressibleText(5120)
		keysAndValues[key] = value

		result, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)
	}

	stats := client.GetStatistics()
	compressedCount := stats["total_values_compressed"] - initialCompressed
	assert.Equal(t, uint64(numKeys), compressedCount,
		"All %d values should be compressed across slots", numKeys)

	// Verify all values
	keys := make([]string, 0, numKeys)
	for key, expectedValue := range keysAndValues {
		retrieved, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, expectedValue, retrieved.Value())
		keys = append(keys, key)
	}

	client.Del(context.Background(), keys)
}

// ============================================================================
// Statistics Tests
// ============================================================================

// ============================================================================
// Batch Compression Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionBatchSetGet() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	numKeys := 100
	keyPrefix := fmt.Sprintf("batch_test_%s", randomString(8))

	// Get initial statistics
	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]
	initialOriginalBytes := initialStats["total_original_bytes"]
	initialBytesCompressed := initialStats["total_bytes_compressed"]

	// Create pipeline batch with SET commands
	batch := pipeline.NewStandaloneBatch(false)
	type kv struct {
		key   string
		value string
	}
	keysAndValues := make([]kv, 0, numKeys)

	for i := 0; i < numKeys; i++ {
		key := fmt.Sprintf("%s_%d", keyPrefix, i)
		size := 1024 + rand.Intn(9216) // 1KB to 10KB
		value := generateCompressibleText(size)
		keysAndValues = append(keysAndValues, kv{key, value})
		batch.Set(key, value)
	}

	// Execute batch
	results, err := client.Exec(context.Background(), *batch, true)
	assert.NoError(t, err)
	assert.NotNil(t, results)
	for i, r := range results {
		assert.Equal(t, "OK", r, "SET result %d should be OK", i)
	}

	// Verify compression was applied to all values
	stats := client.GetStatistics()
	compressedCount := stats["total_values_compressed"] - initialCompressed
	assert.Equal(t, uint64(numKeys), compressedCount,
		"All %d values should be compressed", numKeys)

	// Verify invariant: compressed bytes <= original bytes
	bytesAddedOriginal := stats["total_original_bytes"] - initialOriginalBytes
	bytesAddedCompressed := stats["total_bytes_compressed"] - initialBytesCompressed
	assert.LessOrEqual(t, bytesAddedCompressed, bytesAddedOriginal,
		"Batch: Compressed size should be <= original size")

	// Verify all values are retrievable and correct
	keys := make([]string, 0, numKeys)
	for _, entry := range keysAndValues {
		retrieved, err := client.Get(context.Background(), entry.key)
		assert.NoError(t, err)
		assert.Equal(t, entry.value, retrieved.Value())
		keys = append(keys, entry.key)
	}

	client.Del(context.Background(), keys)
}

func (suite *GlideTestSuite) TestCompressionBatchMixedSizes() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	keyPrefix := fmt.Sprintf("mixed_batch_%s", randomString(8))

	// Get initial statistics
	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]
	initialSkipped := initialStats["compression_skipped_count"]
	initialOriginalBytes := initialStats["total_original_bytes"]
	initialBytesCompressed := initialStats["total_bytes_compressed"]

	// Create batch with mixed sizes
	batch := pipeline.NewStandaloneBatch(false)
	type kv struct {
		key   string
		value string
	}
	keysAndValues := make([]kv, 0, 30)

	// 10 small values (below 64-byte threshold)
	for i := 0; i < 10; i++ {
		key := fmt.Sprintf("%s_small_%d", keyPrefix, i)
		value := generateCompressibleText(32)
		keysAndValues = append(keysAndValues, kv{key, value})
		batch.Set(key, value)
	}

	// 10 medium values (5KB)
	for i := 0; i < 10; i++ {
		key := fmt.Sprintf("%s_medium_%d", keyPrefix, i)
		value := generateCompressibleText(5120)
		keysAndValues = append(keysAndValues, kv{key, value})
		batch.Set(key, value)
	}

	// 10 large values (100KB)
	for i := 0; i < 10; i++ {
		key := fmt.Sprintf("%s_large_%d", keyPrefix, i)
		value := generateCompressibleText(102400)
		keysAndValues = append(keysAndValues, kv{key, value})
		batch.Set(key, value)
	}

	// Execute batch
	results, err := client.Exec(context.Background(), *batch, true)
	assert.NoError(t, err)
	assert.NotNil(t, results)
	for i, r := range results {
		assert.Equal(t, "OK", r, "SET result %d should be OK", i)
	}

	// Verify statistics: 10 small values skipped, 20 medium+large compressed
	stats := client.GetStatistics()
	skippedCount := stats["compression_skipped_count"] - initialSkipped
	compressedCount := stats["total_values_compressed"] - initialCompressed

	assert.Equal(t, uint64(10), skippedCount,
		"10 small values should be skipped")
	assert.Equal(t, uint64(20), compressedCount,
		"20 medium+large values should be compressed")

	// Verify invariant: compressed bytes <= original bytes
	bytesAddedOriginal := stats["total_original_bytes"] - initialOriginalBytes
	bytesAddedCompressed := stats["total_bytes_compressed"] - initialBytesCompressed
	assert.LessOrEqual(t, bytesAddedCompressed, bytesAddedOriginal,
		"Mixed batch: Compressed size should be <= original size")

	// Verify all values
	keys := make([]string, 0, 30)
	for _, entry := range keysAndValues {
		retrieved, err := client.Get(context.Background(), entry.key)
		assert.NoError(t, err)
		assert.Equal(t, entry.value, retrieved.Value())
		keys = append(keys, entry.key)
	}

	client.Del(context.Background(), keys)
}

func (suite *GlideTestSuite) TestCompressionBatchLargePayload() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	numKeys := 1000
	valueSize := 10240 // 10KB each, ~10MB total
	keyPrefix := fmt.Sprintf("large_batch_%s", randomString(8))

	// Get initial statistics
	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]
	initialOriginalBytes := initialStats["total_original_bytes"]
	initialBytesCompressed := initialStats["total_bytes_compressed"]

	// Create batch
	batch := pipeline.NewStandaloneBatch(false)
	value := generateCompressibleText(valueSize)
	keys := make([]string, 0, numKeys)

	for i := 0; i < numKeys; i++ {
		key := fmt.Sprintf("%s_%d", keyPrefix, i)
		keys = append(keys, key)
		batch.Set(key, value)
	}

	// Execute batch
	results, err := client.Exec(context.Background(), *batch, true)
	assert.NoError(t, err)
	assert.NotNil(t, results)
	assert.Len(t, results, numKeys)
	for i, r := range results {
		assert.Equal(t, "OK", r, "SET result %d should be OK", i)
	}

	// Verify compression was applied to all values
	stats := client.GetStatistics()
	compressedCount := stats["total_values_compressed"] - initialCompressed
	assert.Equal(t, uint64(numKeys), compressedCount,
		"All %d values should be compressed", numKeys)

	// Verify invariant: compressed bytes <= original bytes
	bytesAddedOriginal := stats["total_original_bytes"] - initialOriginalBytes
	bytesAddedCompressed := stats["total_bytes_compressed"] - initialBytesCompressed
	assert.LessOrEqual(t, bytesAddedCompressed, bytesAddedOriginal,
		"Large batch: Compressed size should be <= original size")

	// Verify a sample of values
	for i := 0; i < numKeys; i += 100 {
		retrieved, err := client.Get(context.Background(), keys[i])
		assert.NoError(t, err)
		assert.Equal(t, value, retrieved.Value())
	}

	client.Del(context.Background(), keys)
}

func (suite *GlideTestSuite) TestCompressionClusterBatchSetGet() {
	client := suite.compressionClusterClient()
	defer client.Close()

	t := suite.T()

	numKeys := 50
	keyPrefix := fmt.Sprintf("cluster_batch_%s", randomString(8))

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	batch := pipeline.NewClusterBatch(false)
	type kv struct {
		key   string
		value string
	}
	keysAndValues := make([]kv, 0, numKeys)

	for i := 0; i < numKeys; i++ {
		key := fmt.Sprintf("%s_%d", keyPrefix, i)
		size := 1024 + rand.Intn(9216)
		value := generateCompressibleText(size)
		keysAndValues = append(keysAndValues, kv{key, value})
		batch.Set(key, value)
	}

	results, err := client.Exec(context.Background(), *batch, true)
	assert.NoError(t, err)
	assert.NotNil(t, results)
	for i, r := range results {
		assert.Equal(t, "OK", r, "Cluster batch SET result %d should be OK", i)
	}

	stats := client.GetStatistics()
	compressedCount := stats["total_values_compressed"] - initialCompressed
	assert.Equal(t, uint64(numKeys), compressedCount,
		"Cluster: All %d values should be compressed in batch", numKeys)

	keys := make([]string, 0, numKeys)
	for _, entry := range keysAndValues {
		retrieved, err := client.Get(context.Background(), entry.key)
		assert.NoError(t, err)
		assert.Equal(t, entry.value, retrieved.Value())
		keys = append(keys, entry.key)
	}

	client.Del(context.Background(), keys)
}

// ============================================================================
// Statistics Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionStatistics() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	// Get initial statistics
	initialStats := client.GetStatistics()

	// Verify all compression-related keys exist
	compressionKeys := []string{
		"total_values_compressed",
		"total_values_decompressed",
		"total_original_bytes",
		"total_bytes_compressed",
		"total_bytes_decompressed",
		"compression_skipped_count",
	}

	for _, key := range compressionKeys {
		_, exists := initialStats[key]
		assert.True(t, exists, "Expected key %s to exist in statistics", key)
	}

	// Perform some operations and verify stats change
	key := fmt.Sprintf("stats_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	_, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)

	_, err = client.Get(context.Background(), key)
	assert.NoError(t, err)

	afterStats := client.GetStatistics()

	assert.Greater(t, afterStats["total_values_compressed"],
		initialStats["total_values_compressed"],
		"total_values_compressed should increase after SET")
	assert.Greater(t, afterStats["total_original_bytes"],
		initialStats["total_original_bytes"],
		"total_original_bytes should increase after SET")
	assert.Greater(t, afterStats["total_bytes_compressed"],
		initialStats["total_bytes_compressed"],
		"total_bytes_compressed should increase after SET")
	assert.Greater(t, afterStats["total_values_decompressed"],
		initialStats["total_values_decompressed"],
		"total_values_decompressed should increase after GET")
	assert.Greater(t, afterStats["total_bytes_decompressed"],
		initialStats["total_bytes_decompressed"],
		"total_bytes_decompressed should increase after GET")

	client.Del(context.Background(), []string{key})
}
