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
	"github.com/valkey-io/valkey-glide/go/v2/options"
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

// ============================================================================
// Supported Commands Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionMSetMGet() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key1 := fmt.Sprintf("{mset_test}_1_%s", randomString(8))
	key2 := fmt.Sprintf("{mset_test}_2_%s", randomString(8))
	key3 := fmt.Sprintf("{mset_test}_3_%s", randomString(8))
	value := generateCompressibleText(1024)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	// MSET should compress values
	keyValueMap := map[string]string{
		key1: value,
		key2: value,
		key3: value,
	}
	result, err := client.MSet(context.Background(), keyValueMap)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	stats := client.GetStatistics()
	assert.GreaterOrEqual(t, stats["total_values_compressed"], initialCompressed+3,
		"MSET should compress all values")

	// MGET should decompress values
	retrieved, err := client.MGet(context.Background(), []string{key1, key2, key3})
	assert.NoError(t, err)
	assert.Equal(t, 3, len(retrieved))
	assert.Equal(t, value, retrieved[0].Value())
	assert.Equal(t, value, retrieved[1].Value())
	assert.Equal(t, value, retrieved[2].Value())

	client.Del(context.Background(), []string{key1, key2, key3})
}

func (suite *GlideTestSuite) TestCompressionMSetNX() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key1 := fmt.Sprintf("{msetnx_test}_1_%s", randomString(8))
	key2 := fmt.Sprintf("{msetnx_test}_2_%s", randomString(8))
	value := generateCompressibleText(1024)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	// MSETNX should compress values
	keyValueMap := map[string]string{
		key1: value,
		key2: value,
	}
	result, err := client.MSetNX(context.Background(), keyValueMap)
	assert.NoError(t, err)
	assert.True(t, result, "MSETNX should succeed for new keys")

	stats := client.GetStatistics()
	assert.GreaterOrEqual(t, stats["total_values_compressed"], initialCompressed+2,
		"MSETNX should compress all values")

	// Verify values can be retrieved and decompressed
	retrieved1, err := client.Get(context.Background(), key1)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved1.Value())

	retrieved2, err := client.Get(context.Background(), key2)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved2.Value())

	client.Del(context.Background(), []string{key1, key2})
}

func (suite *GlideTestSuite) TestCompressionGetEx() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("getex_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	// Set value (should be compressed)
	compressBefore := client.GetStatistics()["total_values_compressed"]
	_, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Greater(t, client.GetStatistics()["total_values_compressed"], compressBefore,
		"SET should compress value")

	// GETEX should decompress value
	decompressBefore := client.GetStatistics()["total_values_decompressed"]
	opts := options.NewGetExOptions().SetExpiry(options.NewExpiryIn(10 * time.Second))
	retrieved, err := client.GetExWithOptions(context.Background(), key, *opts)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())
	assert.Greater(t, client.GetStatistics()["total_values_decompressed"], decompressBefore,
		"GETEX should decompress value")

	// Verify TTL was set
	ttl, err := client.TTL(context.Background(), key)
	assert.NoError(t, err)
	assert.Greater(t, ttl, int64(0))
	assert.LessOrEqual(t, ttl, int64(10))

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionGetDel() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("getdel_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	// Set value (should be compressed)
	compressBefore := client.GetStatistics()["total_values_compressed"]
	_, err := client.Set(context.Background(), key, value)
	assert.NoError(t, err)
	assert.Greater(t, client.GetStatistics()["total_values_compressed"], compressBefore,
		"SET should compress value")

	// GETDEL should decompress value and delete key
	decompressBefore := client.GetStatistics()["total_values_decompressed"]
	retrieved, err := client.GetDel(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())
	assert.Greater(t, client.GetStatistics()["total_values_decompressed"], decompressBefore,
		"GETDEL should decompress value")

	// Verify key was deleted
	getResult, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.True(t, getResult.IsNil())
}

func (suite *GlideTestSuite) TestCompressionSetExViaCustomCommand() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("setex_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	// SETEX via custom command should compress value
	result, err := client.CustomCommand(context.Background(), []string{"SETEX", key, "10", value})
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed,
		"SETEX should compress value")

	// Verify value can be retrieved and decompressed
	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	// Verify TTL was set
	ttl, err := client.TTL(context.Background(), key)
	assert.NoError(t, err)
	assert.Greater(t, ttl, int64(0))
	assert.LessOrEqual(t, ttl, int64(10))

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionPSetExViaCustomCommand() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("psetex_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	// PSETEX via custom command should compress value
	result, err := client.CustomCommand(context.Background(), []string{"PSETEX", key, "10000", value})
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed,
		"PSETEX should compress value")

	// Verify value can be retrieved and decompressed
	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionSetNXViaCustomCommand() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("setnx_test_%s", randomString(8))
	value := generateCompressibleText(1024)

	// Ensure key doesn't exist
	client.Del(context.Background(), []string{key})

	initialStats := client.GetStatistics()
	initialCompressed := initialStats["total_values_compressed"]

	// SETNX via custom command should compress value
	result, err := client.CustomCommand(context.Background(), []string{"SETNX", key, value})
	assert.NoError(t, err)
	assert.Equal(t, int64(1), result)

	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], initialCompressed,
		"SETNX should compress value")

	// Verify value can be retrieved and decompressed
	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, value, retrieved.Value())

	client.Del(context.Background(), []string{key})
}

// ============================================================================
// Incompatible Commands Tests
// ============================================================================

func (suite *GlideTestSuite) TestCompressionAppendIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("append_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "initial_value")
	assert.NoError(t, err)

	// APPEND should fail with compression enabled
	_, err = client.Append(context.Background(), key, "_appended")
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionGetRangeIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("getrange_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, generateCompressibleText(1024))
	assert.NoError(t, err)

	// GETRANGE should fail with compression enabled
	_, err = client.GetRange(context.Background(), key, 0, 10)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionSetRangeIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("setrange_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, generateCompressibleText(1024))
	assert.NoError(t, err)

	// SETRANGE should fail with compression enabled
	_, err = client.SetRange(context.Background(), key, 5, "replacement")
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionStrlenIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("strlen_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, generateCompressibleText(1024))
	assert.NoError(t, err)

	// STRLEN should fail with compression enabled
	_, err = client.Strlen(context.Background(), key)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionIncrIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("incr_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "100")
	assert.NoError(t, err)

	// INCR should fail with compression enabled
	_, err = client.Incr(context.Background(), key)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionIncrByIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("incrby_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "100")
	assert.NoError(t, err)

	// INCRBY should fail with compression enabled
	_, err = client.IncrBy(context.Background(), key, 10)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionIncrByFloatIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("incrbyfloat_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "100.5")
	assert.NoError(t, err)

	// INCRBYFLOAT should fail with compression enabled
	_, err = client.IncrByFloat(context.Background(), key, 0.5)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionDecrIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("decr_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "100")
	assert.NoError(t, err)

	// DECR should fail with compression enabled
	_, err = client.Decr(context.Background(), key)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionDecrByIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("decrby_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "100")
	assert.NoError(t, err)

	// DECRBY should fail with compression enabled
	_, err = client.DecrBy(context.Background(), key, 10)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionGetBitIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("getbit_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "test_value")
	assert.NoError(t, err)

	// GETBIT should fail with compression enabled
	_, err = client.GetBit(context.Background(), key, 0)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionSetBitIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("setbit_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "test_value")
	assert.NoError(t, err)

	// SETBIT should fail with compression enabled
	_, err = client.SetBit(context.Background(), key, 0, 1)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionBitCountIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("bitcount_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "test_value")
	assert.NoError(t, err)

	// BITCOUNT should fail with compression enabled
	_, err = client.BitCount(context.Background(), key)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionBitPosIncompatible() {
	client := suite.compressionClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("bitpos_test_%s", randomString(8))
	_, err := client.Set(context.Background(), key, "test_value")
	assert.NoError(t, err)

	// BITPOS should fail with compression enabled
	_, err = client.BitPos(context.Background(), key, 1)
	assert.Error(t, err)
	errMsg := strings.ToLower(err.Error())
	assert.True(t, strings.Contains(errMsg, "incompatible") || strings.Contains(errMsg, "compression"),
		"Error should mention incompatibility: %v", err)

	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionIncompatibleCommandsWorkWithoutCompression() {
	client := suite.defaultClient()
	defer client.Close()

	t := suite.T()

	key := fmt.Sprintf("no_compression_test_%s", randomString(8))

	// Set initial value
	_, err := client.Set(context.Background(), key, "100")
	assert.NoError(t, err)

	// All these commands should work without compression
	// INCR
	incrResult, err := client.Incr(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, int64(101), incrResult)

	// INCRBY
	incrByResult, err := client.IncrBy(context.Background(), key, 10)
	assert.NoError(t, err)
	assert.Equal(t, int64(111), incrByResult)

	// DECR
	decrResult, err := client.Decr(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, int64(110), decrResult)

	// DECRBY
	decrByResult, err := client.DecrBy(context.Background(), key, 10)
	assert.NoError(t, err)
	assert.Equal(t, int64(100), decrByResult)

	// STRLEN
	_, err = client.Set(context.Background(), key, "hello")
	assert.NoError(t, err)
	strlenResult, err := client.Strlen(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, int64(5), strlenResult)

	// APPEND
	appendResult, err := client.Append(context.Background(), key, " world")
	assert.NoError(t, err)
	assert.Equal(t, int64(11), appendResult)

	// GETRANGE
	getrangeResult, err := client.GetRange(context.Background(), key, 0, 4)
	assert.NoError(t, err)
	assert.Equal(t, "hello", getrangeResult)

	// SETRANGE
	setrangeResult, err := client.SetRange(context.Background(), key, 6, "WORLD")
	assert.NoError(t, err)
	assert.Equal(t, int64(11), setrangeResult)

	// GETBIT
	_, err = client.Set(context.Background(), key, "\x00")
	assert.NoError(t, err)
	getbitResult, err := client.GetBit(context.Background(), key, 0)
	assert.NoError(t, err)
	assert.Equal(t, int64(0), getbitResult)

	// SETBIT
	setbitResult, err := client.SetBit(context.Background(), key, 0, 1)
	assert.NoError(t, err)
	assert.Equal(t, int64(0), setbitResult)

	// BITCOUNT
	bitcountResult, err := client.BitCount(context.Background(), key)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, bitcountResult, int64(0))

	client.Del(context.Background(), []string{key})
}

// ============================================================================
// MaxDecompressedSize Enforcement Tests
// ============================================================================

// Helper to create a compression client with custom max decompressed size
func (suite *GlideTestSuite) compressionClientWithMaxSize(maxSize uint64) (*glide.Client, error) {
	compressionConfig := config.NewCompressionConfiguration().
		WithMaxDecompressedSize(&maxSize)
	clientConfig := suite.defaultClientConfig().
		WithCompressionConfiguration(compressionConfig)
	return glide.NewClient(clientConfig)
}

// Helper to create a compression cluster client with custom max decompressed size
func (suite *GlideTestSuite) compressionClusterClientWithMaxSize(maxSize uint64) (*glide.ClusterClient, error) {
	compressionConfig := config.NewCompressionConfiguration().
		WithMaxDecompressedSize(&maxSize)
	clientConfig := suite.defaultClusterClientConfig().
		WithCompressionConfiguration(compressionConfig)
	return glide.NewClusterClient(clientConfig)
}

func (suite *GlideTestSuite) TestCompressionMaxDecompressedSizeEnforced() {
	t := suite.T()

	// Step 1: Create a client with compression enabled (default max size - 512MB)
	unlimitedClient := suite.compressionClient()
	defer unlimitedClient.Close()

	// Step 2: Set a large compressible value (10KB)
	key := fmt.Sprintf("max_decomp_test_%s", randomString(8))
	largeValue := generateCompressibleText(10000) // 10KB of compressible data

	result, err := unlimitedClient.Set(context.Background(), key, largeValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Verify the value was compressed
	stats := unlimitedClient.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], uint64(0),
		"Value should have been compressed")

	// Step 3: Create a client with a small maxDecompressedSize limit (100 bytes)
	var maxSize uint64 = 100 // Only allow 100 bytes decompressed
	limitedClient, err := suite.compressionClientWithMaxSize(maxSize)
	assert.NoError(t, err)
	defer limitedClient.Close()

	// Step 4: Try to GET the value with the limited client
	// This SHOULD fail because the decompressed size (10KB) exceeds the limit (100 bytes)
	_, err = limitedClient.Get(context.Background(), key)

	// Verify the error is returned
	assert.Error(t, err, "GET should fail when decompressed size exceeds maxDecompressedSize")
	errMsg := strings.ToLower(err.Error())
	assert.True(
		t,
		strings.Contains(errMsg, "decompressed") || strings.Contains(errMsg, "exceeds") || strings.Contains(errMsg, "size"),
		"Error should mention decompression size limit: %v",
		err,
	)

	// Cleanup
	unlimitedClient.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionMaxDecompressedSizeEnforcedCluster() {
	t := suite.T()

	// Step 1: Create a cluster client with compression enabled (default max size)
	unlimitedClient := suite.compressionClusterClient()
	defer unlimitedClient.Close()

	// Step 2: Set a large compressible value (10KB)
	key := fmt.Sprintf("max_decomp_cluster_test_%s", randomString(8))
	largeValue := generateCompressibleText(10000)

	result, err := unlimitedClient.Set(context.Background(), key, largeValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Step 3: Create a cluster client with a small maxDecompressedSize limit
	var maxSize uint64 = 100
	limitedClient, err := suite.compressionClusterClientWithMaxSize(maxSize)
	assert.NoError(t, err)
	defer limitedClient.Close()

	// Step 4: Try to GET the value - should fail
	_, err = limitedClient.Get(context.Background(), key)

	// Verify the error is returned
	assert.Error(t, err, "GET should fail when decompressed size exceeds maxDecompressedSize in cluster mode")
	errMsg := strings.ToLower(err.Error())
	assert.True(
		t,
		strings.Contains(errMsg, "decompressed") || strings.Contains(errMsg, "exceeds") || strings.Contains(errMsg, "size"),
		"Error should mention decompression size limit: %v",
		err,
	)

	// Cleanup
	unlimitedClient.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionMaxDecompressedSizeWithMGet() {
	t := suite.T()

	// Create unlimited client
	unlimitedClient := suite.compressionClient()
	defer unlimitedClient.Close()

	// Set multiple large values
	keys := make([]string, 3)
	largeValue := generateCompressibleText(5000) // 5KB each
	for i := 0; i < 3; i++ {
		keys[i] = fmt.Sprintf("{mget_max_test}_%d_%s", i, randomString(8))
		_, err := unlimitedClient.Set(context.Background(), keys[i], largeValue)
		assert.NoError(t, err)
	}

	// Create limited client
	var maxSize uint64 = 100
	limitedClient, err := suite.compressionClientWithMaxSize(maxSize)
	assert.NoError(t, err)
	defer limitedClient.Close()

	// Try MGET - should fail
	_, err = limitedClient.MGet(context.Background(), keys)

	// Verify the error is returned
	assert.Error(t, err, "MGET should fail when decompressed size exceeds maxDecompressedSize")
	errMsg := strings.ToLower(err.Error())
	assert.True(
		t,
		strings.Contains(errMsg, "decompressed") || strings.Contains(errMsg, "exceeds") || strings.Contains(errMsg, "size"),
		"Error should mention decompression size limit: %v",
		err,
	)

	// Cleanup
	unlimitedClient.Del(context.Background(), keys)
}

func (suite *GlideTestSuite) TestCompressionMaxDecompressedSizeAllowsWithinLimit() {
	t := suite.T()

	// Create client with 1KB limit
	var maxSize uint64 = 1024 // 1KB limit
	client, err := suite.compressionClientWithMaxSize(maxSize)
	assert.NoError(t, err)
	defer client.Close()

	key := fmt.Sprintf("within_limit_test_%s", randomString(8))
	smallValue := generateCompressibleText(500) // 500 bytes, within limit

	// Set and get should work
	result, err := client.Set(context.Background(), key, smallValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, smallValue, retrieved.Value())

	// Cleanup
	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionSetWithGetReturnsDecompressedValue() {
	t := suite.T()

	client := suite.compressionClient()
	defer client.Close()

	key := fmt.Sprintf("set_with_get_test_%s", randomString(8))
	originalValue := generateCompressibleText(1024) // 1KB
	newValue := generateCompressibleText(2048)      // 2KB

	// First, set the original value
	result, err := client.Set(context.Background(), key, originalValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Verify compression was applied
	stats := client.GetStatistics()
	assert.Greater(t, stats["total_values_compressed"], uint64(0),
		"Value should have been compressed")

	// Now use SET with returnOldValue option to get the old value
	setOpts := options.NewSetOptions().SetReturnOldValue(true)
	oldValue, err := client.SetWithOptions(context.Background(), key, newValue, *setOpts)
	assert.NoError(t, err)

	// The old value should be the decompressed original value, not compressed bytes
	assert.Equal(t, originalValue, oldValue.Value(),
		"SET with GET should return decompressed old value, not compressed bytes")

	// Verify the new value was set correctly
	retrieved, err := client.Get(context.Background(), key)
	assert.NoError(t, err)
	assert.Equal(t, newValue, retrieved.Value())

	// Cleanup
	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionSetWithGetReturnsNullForNonexistentKey() {
	t := suite.T()

	client := suite.compressionClient()
	defer client.Close()

	key := fmt.Sprintf("set_with_get_nonexistent_%s", randomString(8))
	value := generateCompressibleText(1024)

	// SET with returnOldValue on non-existent key should return nil
	setOpts := options.NewSetOptions().SetReturnOldValue(true)
	oldValue, err := client.SetWithOptions(context.Background(), key, value, *setOpts)
	assert.NoError(t, err)
	assert.True(t, oldValue.IsNil(), "SET with GET on non-existent key should return nil")

	// Cleanup
	client.Del(context.Background(), []string{key})
}

func (suite *GlideTestSuite) TestCompressionSetWithGetCluster() {
	t := suite.T()

	client := suite.compressionClusterClient()
	defer client.Close()

	key := fmt.Sprintf("set_with_get_cluster_%s", randomString(8))
	originalValue := generateCompressibleText(1024)
	newValue := generateCompressibleText(2048)

	// First, set the original value
	result, err := client.Set(context.Background(), key, originalValue)
	assert.NoError(t, err)
	assert.Equal(t, "OK", result)

	// Now use SET with returnOldValue option to get the old value
	setOpts := options.NewSetOptions().SetReturnOldValue(true)
	oldValue, err := client.SetWithOptions(context.Background(), key, newValue, *setOpts)
	assert.NoError(t, err)

	// The old value should be the decompressed original value
	assert.Equal(t, originalValue, oldValue.Value(),
		"SET with GET in cluster mode should return decompressed old value")

	// Cleanup
	client.Del(context.Background(), []string{key})
}
