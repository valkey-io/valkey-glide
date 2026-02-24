// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"strings"

	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// This example demonstrates creating a standalone client with ZSTD compression enabled
// using default settings (level: default, min size: 64 bytes).
// It validates that compression actually reduces the data size using client statistics.
func ExampleClient_compressionZSTD() {
	compressionConfig := config.NewCompressionConfiguration()

	clientConfig := config.NewClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithCompressionConfiguration(compressionConfig)

	client, err := NewClient(clientConfig)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer client.Close()

	// Capture statistics before the SET to isolate this operation's metrics
	statsBefore := client.GetStatistics()
	originalBytesBefore := statsBefore["total_original_bytes"]
	compressedBytesBefore := statsBefore["total_bytes_compressed"]
	compressedCountBefore := statsBefore["total_values_compressed"]

	// Values >= 64 bytes will be automatically compressed before sending to the server
	value := strings.Repeat("hello world ", 100) // ~1200 bytes, will be compressed
	result, err := client.Set(context.Background(), "compressed_key", value)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(result)

	// Values are automatically decompressed on retrieval
	retrieved, err := client.Get(context.Background(), "compressed_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Data integrity check:", retrieved.Value() == value)

	// Validate compression using client statistics
	statsAfter := client.GetStatistics()
	originalBytes := statsAfter["total_original_bytes"] - originalBytesBefore
	compressedBytes := statsAfter["total_bytes_compressed"] - compressedBytesBefore
	compressedCount := statsAfter["total_values_compressed"] - compressedCountBefore

	fmt.Println("Value was compressed:", compressedCount > 0)
	fmt.Println("Compressed bytes < original bytes:", compressedBytes < originalBytes)

	// Output:
	// OK
	// Data integrity check: true
	// Value was compressed: true
	// Compressed bytes < original bytes: true
}

// This example demonstrates creating a standalone client with LZ4 compression.
// It validates that compression actually reduces the data size using client statistics.
func ExampleClient_compressionLZ4() {
	compressionConfig := config.NewCompressionConfiguration().
		WithBackend(config.LZ4)

	clientConfig := config.NewClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithCompressionConfiguration(compressionConfig)

	client, err := NewClient(clientConfig)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer client.Close()

	// Capture statistics before the SET
	statsBefore := client.GetStatistics()
	originalBytesBefore := statsBefore["total_original_bytes"]
	compressedBytesBefore := statsBefore["total_bytes_compressed"]
	compressedCountBefore := statsBefore["total_values_compressed"]

	value := strings.Repeat("data ", 200)
	result, err := client.Set(context.Background(), "lz4_key", value)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(result)

	// Validate compression using client statistics
	statsAfter := client.GetStatistics()
	originalBytes := statsAfter["total_original_bytes"] - originalBytesBefore
	compressedBytes := statsAfter["total_bytes_compressed"] - compressedBytesBefore
	compressedCount := statsAfter["total_values_compressed"] - compressedCountBefore

	fmt.Println("Value was compressed:", compressedCount > 0)
	fmt.Println("Compressed bytes < original bytes:", compressedBytes < originalBytes)

	// Output:
	// OK
	// Value was compressed: true
	// Compressed bytes < original bytes: true
}

// This example demonstrates creating a client with a custom compression level and min size.
// It validates that values below the minimum size threshold are not compressed,
// while larger values are compressed and the compressed size is smaller than the original.
func ExampleClient_compressionCustomLevel() {
	compressionConfig := config.NewCompressionConfiguration().
		WithBackend(config.ZSTD).
		WithCompressionLevel(10).
		WithMinCompressionSize(256)

	clientConfig := config.NewClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithCompressionConfiguration(compressionConfig)

	client, err := NewClient(clientConfig)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer client.Close()

	// Capture statistics before operations
	statsBefore := client.GetStatistics()
	compressedCountBefore := statsBefore["total_values_compressed"]
	skippedCountBefore := statsBefore["compression_skipped_count"]

	// Only values >= 256 bytes will be compressed — this short value should be skipped
	smallValue := "short"
	result, err := client.Set(context.Background(), "small_key", smallValue)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(result)

	statsAfterSmall := client.GetStatistics()
	fmt.Println("Small value compression skipped:",
		statsAfterSmall["compression_skipped_count"] > skippedCountBefore)
	fmt.Println("Small value was not compressed:",
		statsAfterSmall["total_values_compressed"] == compressedCountBefore)

	// Now send a large value that exceeds the 256-byte threshold
	originalBytesBefore := statsAfterSmall["total_original_bytes"]
	compressedBytesBefore := statsAfterSmall["total_bytes_compressed"]

	largeValue := strings.Repeat("compressible data pattern ", 50) // ~1300 bytes
	result, err = client.Set(context.Background(), "large_key", largeValue)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(result)

	statsAfterLarge := client.GetStatistics()
	originalBytes := statsAfterLarge["total_original_bytes"] - originalBytesBefore
	compressedBytes := statsAfterLarge["total_bytes_compressed"] - compressedBytesBefore

	fmt.Println("Large value was compressed:",
		statsAfterLarge["total_values_compressed"] > compressedCountBefore)
	fmt.Println("Compressed bytes < original bytes:", compressedBytes < originalBytes)

	// Output:
	// OK
	// Small value compression skipped: true
	// Small value was not compressed: true
	// OK
	// Large value was compressed: true
	// Compressed bytes < original bytes: true
}

// This example demonstrates creating a cluster client with compression enabled.
// It validates that compression actually reduces the data size using client statistics.
func ExampleClusterClient_compression() {
	compressionConfig := config.NewCompressionConfiguration()

	clientConfig := config.NewClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithCompressionConfiguration(compressionConfig)

	client, err := NewClusterClient(clientConfig)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer client.Close()

	// Capture statistics before the SET
	statsBefore := client.GetStatistics()
	originalBytesBefore := statsBefore["total_original_bytes"]
	compressedBytesBefore := statsBefore["total_bytes_compressed"]
	compressedCountBefore := statsBefore["total_values_compressed"]

	value := strings.Repeat("cluster data ", 100)
	result, err := client.Set(context.Background(), "cluster_compressed_key", value)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(result)

	// Validate compression using client statistics
	statsAfter := client.GetStatistics()
	originalBytes := statsAfter["total_original_bytes"] - originalBytesBefore
	compressedBytes := statsAfter["total_bytes_compressed"] - compressedBytesBefore
	compressedCount := statsAfter["total_values_compressed"] - compressedCountBefore

	fmt.Println("Value was compressed:", compressedCount > 0)
	fmt.Println("Compressed bytes < original bytes:", compressedBytes < originalBytes)

	// Output:
	// OK
	// Value was compressed: true
	// Compressed bytes < original bytes: true
}

// This example demonstrates reading compression statistics from the client
// and validating that compressed bytes are smaller than the original value.
func ExampleClient_compressionStatistics() {
	compressionConfig := config.NewCompressionConfiguration()

	clientConfig := config.NewClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithCompressionConfiguration(compressionConfig)

	client, err := NewClient(clientConfig)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer client.Close()

	// Capture statistics before the SET to isolate this operation's metrics
	statsBefore := client.GetStatistics()
	originalBytesBefore := statsBefore["total_original_bytes"]
	compressedBytesBefore := statsBefore["total_bytes_compressed"]

	// Perform a SET with a compressible value
	value := strings.Repeat("statistics test ", 100)
	_, err = client.Set(context.Background(), "stats_key", value)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Read compression statistics and validate compression occurred
	stats := client.GetStatistics()
	originalBytes := stats["total_original_bytes"] - originalBytesBefore
	compressedBytes := stats["total_bytes_compressed"] - compressedBytesBefore

	fmt.Println("total_values_compressed exists:", stats["total_values_compressed"] > 0)
	fmt.Println("Compressed bytes < original bytes:", compressedBytes < originalBytes)

	// Output:
	// total_values_compressed exists: true
	// Compressed bytes < original bytes: true
}
