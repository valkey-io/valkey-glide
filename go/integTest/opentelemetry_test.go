// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

const (
	otelSpanFlushIntervalMs = 100
	otelSpanTimeoutMs       = 50000
	validEndpointTraces     = "/tmp/spans.json"
	validFileEndpointTraces = "file://" + validEndpointTraces
	validEndpointMetrics    = "https://valid-endpoint/v1/metrics"
)

func WrongOpenTelemetryConfig(suite *GlideTestSuite) {
	// Test wrong traces endpoint
	cfg := glide.OpenTelemetryConfig{
		Traces: &glide.OpenTelemetryTracesConfig{
			Endpoint: "wrong.endpoint",
		},
	}
	err := glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Parse error")

	// Test wrong metrics endpoint
	cfg = glide.OpenTelemetryConfig{
		Metrics: &glide.OpenTelemetryMetricsConfig{
			Endpoint: "wrong.endpoint",
		},
	}
	err = glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Parse error")

	// Test negative flush interval
	negativeFlushInterval := int64(-400)
	cfg = glide.OpenTelemetryConfig{
		Traces: &glide.OpenTelemetryTracesConfig{
			Endpoint:         validFileEndpointTraces,
			SamplePercentage: 1,
		},
		FlushIntervalMs: &negativeFlushInterval,
	}
	err = glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "flushIntervalMs must be a positive integer")

	// Test out of range sample percentage
	cfg = glide.OpenTelemetryConfig{
		Traces: &glide.OpenTelemetryTracesConfig{
			Endpoint:         validFileEndpointTraces,
			SamplePercentage: 400,
		},
	}
	err = glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "sample percentage must be between 0 and 100")
	// Test wrong file path format
	cfg = glide.OpenTelemetryConfig{
		Traces: &glide.OpenTelemetryTracesConfig{
			Endpoint: "file:invalid-path/v1/traces.json",
		},
	}
	err = glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "File path must start with 'file://'")
	// Test non-existent directory
	cfg = glide.OpenTelemetryConfig{
		Traces: &glide.OpenTelemetryTracesConfig{
			Endpoint: "file:///no-exists-path/v1/traces.json",
		},
	}
	err = glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "The directory does not exist")
	// Test no traces or metrics provided
	cfg = glide.OpenTelemetryConfig{}
	err = glide.GetOtelInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "at least one of traces or metrics must be provided")
}

type SpanFileData struct {
	SpanData  string
	Spans     []string
	SpanNames []string
}

func readAndParseSpanFile(path string) (SpanFileData, error) {
	// Read file content
	spanData, err := os.ReadFile(path)
	if err != nil {
		return SpanFileData{}, fmt.Errorf("failed to read or validate file with the error: %w", err)
	}

	// Split into lines and filter empty lines
	lines := strings.Split(string(spanData), "\n")
	var spans []string
	for _, line := range lines {
		if strings.TrimSpace(line) != "" {
			spans = append(spans, line)
		}
	}

	// Check that we have spans
	if len(spans) == 0 {
		return SpanFileData{}, fmt.Errorf("no spans found in the span file")
	}

	// Parse and extract span names
	var spanNames []string
	for _, line := range spans {
		var span map[string]interface{}
		if err := json.Unmarshal([]byte(line), &span); err != nil {
			continue // Skip invalid JSON lines
		}
		if name, ok := span["name"].(string); ok {
			spanNames = append(spanNames, name)
		}
	}

	return SpanFileData{
		SpanData:  string(spanData),
		Spans:     spans,
		SpanNames: spanNames,
	}, nil
}

func (suite *GlideTestSuite) TestOpenTelemetry_AutomaticSpanLifecycle() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		// Force garbage collection
		runtime.GC()
		// Get initial memory stats
		var startMem runtime.MemStats
		runtime.ReadMemStats(&startMem)
		// Execute multiple commands - each should automatically create and clean up its span
		_, err := client.Set(context.Background(), "test_key1", "value1")
		require.NoError(suite.T(), err)
		_, err = client.Get(context.Background(), "test_key1")
		require.NoError(suite.T(), err)
		_, err = client.Set(context.Background(), "test_key2", "value2")
		require.NoError(suite.T(), err)
		_, err = client.Get(context.Background(), "test_key2")
		require.NoError(suite.T(), err)
		// Force garbage collection again
		runtime.GC()
		// Get final memory stats
		var endMem runtime.MemStats
		runtime.ReadMemStats(&endMem)
		// Allow small fluctuations (10% increase)
		maxAllowedMemory := float64(startMem.HeapAlloc) * 1.1
		assert.Less(suite.T(), float64(endMem.HeapAlloc), maxAllowedMemory,
			"Memory usage should not increase significantly")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_GlobalConfigNotReinitialize() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		// Try to initialize OpenTelemetry with wrong endpoint
		wrongConfig := glide.OpenTelemetryConfig{
			Traces: &glide.OpenTelemetryTracesConfig{
				Endpoint:         "wrong.endpoint",
				SamplePercentage: 1,
			},
		}

		// The init should not throw error because it can only be initialized once per process
		err := glide.GetOtelInstance().Init(wrongConfig)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), err.Error(), "openTelemetry already initialized, ignoring new config")

		// Verify that the original configuration is still in effect
		// by checking if spans are still being exported to the correct endpoint
		_, err = client.Set(context.Background(), "test_key", "test_value")
		require.NoError(suite.T(), err)

		time.Sleep(500 * time.Millisecond)

		// Read spans to verify they're still being exported to the correct endpoint
		// Use the actual file path, not the URL
		spans, _ := readAndParseSpanFile(validEndpointTraces)
		assert.NotNil(suite.T(), spans.Spans, "Spans should still be exported to the original endpoint")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_ConcurrentCommandsSpanLifecycle() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		// Force garbage collection
		runtime.GC()

		// Get initial memory stats
		var startMem runtime.MemStats
		runtime.ReadMemStats(&startMem)

		// Create a WaitGroup to wait for all commands to complete
		var wg sync.WaitGroup
		errChan := make(chan error, 6) // Buffer for all potential errors

		// Define commands to execute concurrently
		commands := []struct {
			cmd func() error
		}{
			{cmd: func() error { _, err := client.Set(context.Background(), "test_key1", "value1"); return err }},
			{cmd: func() error { _, err := client.Get(context.Background(), "test_key1"); return err }},
			{cmd: func() error { _, err := client.Set(context.Background(), "test_key2", "value2"); return err }},
			{cmd: func() error { _, err := client.Get(context.Background(), "test_key2"); return err }},
			{cmd: func() error { _, err := client.Set(context.Background(), "test_key3", "value3"); return err }},
			{cmd: func() error { _, err := client.Get(context.Background(), "test_key3"); return err }},
		}

		// Execute commands concurrently
		for _, cmd := range commands {
			wg.Add(1)
			go func(cmd func() error) {
				defer wg.Done()
				if err := cmd(); err != nil {
					errChan <- err
				}
			}(cmd.cmd)
		}

		// Wait for all commands to complete
		wg.Wait()
		close(errChan)

		// Check for any errors
		for err := range errChan {
			require.NoError(suite.T(), err, "Command execution failed")
		}

		// Force garbage collection again
		runtime.GC()

		// Get final memory stats
		var endMem runtime.MemStats
		runtime.ReadMemStats(&endMem)

		// Allow small fluctuations (10% increase)
		maxAllowedMemory := float64(startMem.HeapAlloc) * 1.1
		assert.Less(suite.T(), float64(endMem.HeapAlloc), maxAllowedMemory,
			"Memory usage should not increase significantly")
	})
}

// cluster tests
func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientMemoryLeak() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
		// Force garbage collection
		runtime.GC()

		// Get initial memory stats
		var startMem runtime.MemStats
		runtime.ReadMemStats(&startMem)

		// Execute multiple commands sequentially
		for i := 0; i < 100; i++ {
			key := fmt.Sprintf("test_key_%d", i)
			_, err := client.Set(context.Background(), key, fmt.Sprintf("value_%d", i))
			require.NoError(suite.T(), err)
			_, err = client.Get(context.Background(), key)
			require.NoError(suite.T(), err)
		}

		// Force garbage collection again
		runtime.GC()

		// Get final memory stats
		var endMem runtime.MemStats
		runtime.ReadMemStats(&endMem)

		// Allow small fluctuations (10% increase)
		maxAllowedMemory := float64(startMem.HeapAlloc) * 1.1
		assert.Less(suite.T(), float64(endMem.HeapAlloc), maxAllowedMemory,
			"Memory usage should not increase significantly")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientSamplingPercentage() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
		// Set sampling percentage to 0
		err := glide.GetOtelInstance().SetSamplePercentage(0)
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), int32(0), glide.GetOtelInstance().GetSamplePercentage())

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Execute commands with 0% sampling
		for i := 0; i < 100; i++ {
			_, err := client.Set(context.Background(), "GlideClusterClient_test_percentage_requests_config", "value")
			require.NoError(suite.T(), err)
		}

		// Wait for spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Verify no spans were exported
		_, err = os.Stat(validEndpointTraces)
		assert.True(suite.T(), os.IsNotExist(err), "Span file should not exist with 0% sampling")

		// Set sampling percentage to 100
		err = glide.GetOtelInstance().SetSamplePercentage(100)
		require.NoError(suite.T(), err)

		// Execute commands with 100% sampling
		for i := 0; i < 10; i++ {
			key := fmt.Sprintf("GlideClusterClient_test_percentage_requests_config_%d", i)
			_, err := client.Get(context.Background(), key)
			require.NoError(suite.T(), err)
		}

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Count Get spans
		getSpanCount := 0
		for _, name := range spans.SpanNames {
			if name == "GET" {
				getSpanCount++
			}
		}

		// Verify we have exactly 10 Get spans
		assert.Equal(suite.T(), 10, getSpanCount, "Should have exactly 10 Get spans")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientGlobalConfigNotReinitialize() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
		// Try to initialize OpenTelemetry with wrong endpoint
		wrongConfig := glide.OpenTelemetryConfig{
			Traces: &glide.OpenTelemetryTracesConfig{
				Endpoint:         "wrong.endpoint",
				SamplePercentage: 1,
			},
		}

		// The init should not throw error because it can only be initialized once per process
		err := glide.GetOtelInstance().Init(wrongConfig)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), err.Error(), "openTelemetry already initialized, ignoring new config")

		// Execute a command to verify spans are still being exported
		_, err = client.Set(context.Background(), "GlideClusterClient_test_otel_global_config", "value")
		require.NoError(suite.T(), err)

		// Wait for spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Read spans to verify they're still being exported to the correct endpoint
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)
		assert.Contains(suite.T(), spans.SpanNames, "SET", "Should find SET span in exported spans")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientMultipleClients() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client1 interfaces.BaseClientCommands) {
		// Create a second client with the same configuration
		client2, err := suite.clusterClient(suite.defaultClusterClientConfig())
		require.NoError(suite.T(), err)
		defer client2.Close()

		// Execute commands with both clients
		_, err = client1.Set(context.Background(), "test_key", "value")
		require.NoError(suite.T(), err)
		_, err = client2.Get(context.Background(), "test_key")
		require.NoError(suite.T(), err)

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify both SET and GET spans exist
		assert.Contains(suite.T(), spans.SpanNames, "SET", "Should find SET span in exported spans")
		assert.Contains(suite.T(), spans.SpanNames, "GET", "Should find GET span in exported spans")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientBatchSpan() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
		// Force garbage collection
		runtime.GC()

		// Get initial memory stats
		var startMem runtime.MemStats
		runtime.ReadMemStats(&startMem)

		// Create a batch
		batch := pipeline.NewClusterBatch(true)
		batch.Set("test_key", "foo")
		batch.CustomCommand([]string{"object", "refcount", "test_key"})

		// Execute the batch
		response, err := client.(*glide.ClusterClient).Exec(context.Background(), *batch, true)
		require.NoError(suite.T(), err)
		require.NotNil(suite.T(), response)
		require.Len(suite.T(), response, 2)
		assert.Equal(suite.T(), "OK", response[0])              // Set command response
		assert.GreaterOrEqual(suite.T(), response[1], int64(1)) // ObjectRefCount response

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify batch span names
		assert.Contains(suite.T(), spans.SpanNames, "Batch", "Should find Batch span in exported spans")
		assert.Contains(suite.T(), spans.SpanNames, "send_batch", "Should find send_batch span in exported spans")

		// Force garbage collection again
		runtime.GC()

		// Get final memory stats
		var endMem runtime.MemStats
		runtime.ReadMemStats(&endMem)

		// Allow small fluctuations (10% increase)
		maxAllowedMemory := float64(startMem.HeapAlloc) * 1.1
		assert.Less(suite.T(), float64(endMem.HeapAlloc), maxAllowedMemory,
			"Memory usage should not increase significantly")
	})
}

func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientSpanTransactionMemoryLeak() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	protocols := []string{"RESP2", "RESP3"}
	for _, protocol := range protocols {
		suite.T().Run(fmt.Sprintf("Protocol_%s", protocol), func(t *testing.T) {
			suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
				// Force garbage collection
				runtime.GC()

				// Get initial memory stats
				var startMem runtime.MemStats
				runtime.ReadMemStats(&startMem)

				// Create a batch
				batch := pipeline.NewClusterBatch(true)
				batch.Set("test_key", "foo")
				batch.CustomCommand([]string{"object", "refcount", "test_key"})

				// Execute the batch
				response, err := client.(*glide.ClusterClient).Exec(context.Background(), *batch, true)
				require.NoError(t, err)
				require.NotNil(t, response)
				require.Len(t, response, 2)
				assert.Equal(t, "OK", response[0])              // Set command response
				assert.GreaterOrEqual(t, response[1], int64(1)) // ObjectRefCount response

				// Force garbage collection again
				runtime.GC()

				// Get final memory stats
				var endMem runtime.MemStats
				runtime.ReadMemStats(&endMem)

				// Allow small fluctuations (10% increase)
				maxAllowedMemory := float64(startMem.HeapAlloc) * 1.1
				assert.Less(t, float64(endMem.HeapAlloc), maxAllowedMemory,
					"Memory usage should not increase significantly")
			})
		})
	}
}
