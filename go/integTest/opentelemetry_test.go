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

// getSpanField tries to get a field value from a span using multiple possible field names
func getSpanField(span map[string]interface{}, fieldNames []string) string {
	for _, fieldName := range fieldNames {
		if value, ok := span[fieldName].(string); ok {
			return value
		}
	}
	return ""
}

// verifySpanHierarchy strictly checks that child spans have the expected parent span in their trace
func (suite *GlideTestSuite) verifySpanHierarchy(spans []string, expectedParentName string, expectedChildNames []string) {
	var parentSpanID string
	var parentTraceID string

	// First, find the parent span and extract its span ID and trace ID
	for _, spanLine := range spans {
		var span map[string]interface{}
		if err := json.Unmarshal([]byte(spanLine), &span); err != nil {
			continue
		}

		if name, ok := span["name"].(string); ok && name == expectedParentName {
			parentSpanID = getSpanField(span, []string{"spanId", "span_id", "id"})
			parentTraceID = getSpanField(span, []string{"traceId", "trace_id"})
			if parentSpanID != "" && parentTraceID != "" {
				break
			}
		}
	}

	// STRICT: Parent span MUST be found
	require.NotEmpty(suite.T(), parentSpanID, "Parent span '%s' MUST be found in exported spans", expectedParentName)
	require.NotEmpty(suite.T(), parentTraceID, "Parent span '%s' MUST have a valid trace ID", expectedParentName)

	suite.T().Logf("Parent span '%s' found with ID %s, trace ID %s", expectedParentName, parentSpanID, parentTraceID)

	// STRICT: Verify ALL expected child spans are found and properly linked
	childSpansFound := make(map[string]bool)

	for _, spanLine := range spans {
		var span map[string]interface{}
		if err := json.Unmarshal([]byte(spanLine), &span); err != nil {
			continue
		}

		if name, ok := span["name"].(string); ok {
			// Check if this is one of our expected child spans
			for _, expectedChild := range expectedChildNames {
				if name == expectedChild {
					// First check if this span belongs to the same trace as our parent
					traceID := getSpanField(span, []string{"traceId", "trace_id"})
					if traceID != parentTraceID {
						// This span belongs to a different trace, skip it
						continue
					}

					// STRICT: Child span MUST have the same trace ID as parent
					require.NotEmpty(suite.T(), traceID, "Child span %s MUST have a trace ID", name)
					assert.Equal(suite.T(), parentTraceID, traceID,
						"Child span %s MUST have same trace ID as parent %s", name, expectedParentName)

					// STRICT: Child span MUST have the parent span ID as its parent
					parentSpanIDField := getSpanField(
						span,
						[]string{"parentSpanId", "parent_span_id", "parentId", "parent_id"},
					)
					require.NotEmpty(suite.T(), parentSpanIDField, "Child span %s MUST have a parent span ID", name)
					assert.Equal(suite.T(), parentSpanID, parentSpanIDField,
						"Child span %s MUST have parent span ID %s", name, parentSpanID)

					childSpansFound[name] = true
					suite.T().Logf("Child span '%s' correctly linked to parent", name)
					break
				}
			}
		}
	}

	// STRICT: ALL expected child spans MUST be found
	for _, expectedChild := range expectedChildNames {
		assert.True(suite.T(), childSpansFound[expectedChild],
			"Expected child span '%s' MUST be found and linked to parent '%s'", expectedChild, expectedParentName)
	}
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

func (suite *GlideTestSuite) TestOpenTelemetry_ClusterClientSendCommandSpan() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}
	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
		// Force garbage collection
		runtime.GC()

		// Get initial memory stats
		var startMem runtime.MemStats
		runtime.ReadMemStats(&startMem)

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Execute commands with 0% sampling
		for i := 0; i < 10; i++ {
			_, err := client.Set(context.Background(), "GlideClusterClient_test_send_command_span", "value")
			require.NoError(suite.T(), err)
		}

		// Wait for spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Count send_command spans
		sendCommandSpanCount := 0
		for _, name := range spans.SpanNames {
			if name == "send_command" {
				sendCommandSpanCount++
			}
		}

		// Verify we have exactly 10 send_command spans
		assert.Equal(suite.T(), 10, sendCommandSpanCount, "Should have exactly 10 send_command spans")

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

// TestOpenTelemetry_PublicSpanManagementAPIs tests the new public CreateSpan and EndSpan APIs
func (suite *GlideTestSuite) TestOpenTelemetry_PublicSpanManagementAPIs() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	otelInstance := glide.GetOtelInstance()

	// Test CreateSpan with valid input
	spanPtr, err := otelInstance.CreateSpan("test-user-operation")
	require.NoError(suite.T(), err)
	assert.NotEqual(suite.T(), uint64(0), spanPtr, "CreateSpan should return non-zero span pointer")

	// Test EndSpan with valid span pointer
	otelInstance.EndSpan(spanPtr)

	// Test CreateSpan with empty name (should fail)
	_, err = otelInstance.CreateSpan("")
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "span name cannot be empty")

	// Test CreateSpan with too long name (should fail)
	longName := strings.Repeat("a", 257) // 257 characters
	_, err = otelInstance.CreateSpan(longName)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "span name too long")

	// Test EndSpan with zero pointer (should be safe no-op)
	otelInstance.EndSpan(0) // Should not panic or error

	// Test multiple spans can be created and ended
	span1, err := otelInstance.CreateSpan("operation-1")
	require.NoError(suite.T(), err)
	span2, err := otelInstance.CreateSpan("operation-2")
	require.NoError(suite.T(), err)

	assert.NotEqual(suite.T(), span1, span2, "Different spans should have different pointers")

	otelInstance.EndSpan(span1)
	otelInstance.EndSpan(span2)
}

// TestOpenTelemetry_PublicSpanManagementAPIs_NotInitialized tests behavior when OpenTelemetry is not initialized
func (suite *GlideTestSuite) TestOpenTelemetry_PublicSpanManagementAPIs_NotInitialized() {
	// Note: Since OpenTelemetry is designed to be initialized once per process,
	// and other tests may have already initialized it, we test the behavior
	// by checking if the instance reports as initialized or not.

	otelInstance := glide.GetOtelInstance()

	if !otelInstance.IsInitialized() {
		// Test CreateSpan when not initialized (should fail)
		_, err := otelInstance.CreateSpan("test-span")
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), err.Error(), "openTelemetry not initialized")
	} else {
		// If already initialized, test that CreateSpan works
		spanPtr, err := otelInstance.CreateSpan("test-span")
		assert.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), spanPtr)
		otelInstance.EndSpan(spanPtr)
	}

	// Test EndSpan with arbitrary pointer (should be safe no-op regardless of initialization)
	otelInstance.EndSpan(123) // Should not panic
}

// TestOpenTelemetry_SpanContextAttachment tests comprehensive span context attachment functionality
func (suite *GlideTestSuite) TestOpenTelemetry_SpanContextAttachment() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		otelInstance := glide.GetOtelInstance()

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file to start fresh
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Test 1: Command execution without parent span context (current behavior)
		ctx := context.Background()
		result, err := client.Set(ctx, "test-key-no-parent", "test-value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// Test 2: Command execution with parent span context
		// Create a parent span
		parentSpanPtr, err := otelInstance.CreateSpan("user-operation")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), parentSpanPtr)

		// Store parent span in context using the helper function
		ctxWithSpan := glide.WithSpan(context.Background(), parentSpanPtr)

		// Execute command with parent context - this should create a child span
		result, err = client.Set(ctxWithSpan, "test-key-with-parent", "test-value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// Test 3: Batch operations with parent span context
		// Create a batch operation with parent context
		batch := pipeline.NewStandaloneBatch(false) // non-atomic pipeline
		batch.Set("batch-key-1", "value1")
		batch.Set("batch-key-2", "value2")
		batch.Get("batch-key-1")

		// Execute batch with parent context - this should create a child batch span
		batchResults, err := client.(*glide.Client).Exec(ctxWithSpan, *batch, false)
		require.NoError(suite.T(), err)
		assert.Len(suite.T(), batchResults, 3)

		// Test 4: Multiple nested operations with the same parent
		getResult, err := client.Get(ctxWithSpan, "test-key-with-parent")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "test-value", getResult.Value())

		// Test 5: Mixed operations (some with context, some without)
		result, err = client.Set(context.Background(), "test-key-no-parent-2", "test-value-2")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		getResult, err = client.Get(ctxWithSpan, "test-key-no-parent-2")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "test-value-2", getResult.Value())

		// End parent span BEFORE verification to ensure proper hierarchy capture
		otelInstance.EndSpan(parentSpanPtr)

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans with hierarchy validation
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify we have the expected span names
		expectedSpans := []string{"SET", "GET", "send_batch"}
		for _, expectedSpan := range expectedSpans {
			assert.Contains(suite.T(), spans.SpanNames, expectedSpan,
				"Should find %s span in exported spans", expectedSpan)
		}

		// STRICT: Verify span hierarchy - commands executed with parent context should be children
		suite.verifySpanHierarchy(spans.Spans, "user-operation", []string{"SET", "GET", "Batch"})

		suite.T().Logf("Successfully verified comprehensive span context attachment with %d total spans", len(spans.SpanNames))
	})
}

// TestOpenTelemetry_SpanContextAttachment_BatchOperations tests batch operations with parent span context
func (suite *GlideTestSuite) TestOpenTelemetry_SpanContextAttachment_BatchOperations() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		otelInstance := glide.GetOtelInstance()

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file to start fresh
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Test 1: Create parent span for batch operations
		parentSpanPtr, err := otelInstance.CreateSpan("batch-user-operations")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), parentSpanPtr)

		// Store parent span in context
		ctxWithParent := glide.WithSpan(context.Background(), parentSpanPtr)

		// Test 2: Execute batch operations with parent context
		batch := pipeline.NewStandaloneBatch(false) // non-atomic pipeline
		batch.Set("user:456:profile", "jane_doe")
		batch.Set("user:456:email", "jane@example.com")
		batch.Incr("user:456:login_count")
		batch.SAdd("active_users", []string{"456"})

		// Execute batch with parent context - should create child batch span
		batchResults, err := client.(*glide.Client).Exec(ctxWithParent, *batch, false)
		require.NoError(suite.T(), err)
		assert.Len(suite.T(), batchResults, 4)

		// Verify the batch results
		assert.Equal(suite.T(), "OK", batchResults[0])     // SET user:456:profile
		assert.Equal(suite.T(), "OK", batchResults[1])     // SET user:456:email
		assert.Equal(suite.T(), int64(1), batchResults[2]) // INCR user:456:login_count
		assert.Equal(suite.T(), int64(1), batchResults[3]) // SADD active_users

		// Test 3: Execute individual command with parent context (for comparison)
		getResult, err := client.Get(ctxWithParent, "user:456:profile")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "jane_doe", getResult.Value())

		// Test 4: Execute another batch operation with parent context
		batch2 := pipeline.NewStandaloneBatch(false)
		batch2.Set("batch:operation:2", "value")
		batch2.Get("batch:operation:2")
		batch2.Del([]string{"batch:operation:2"})

		batchResults2, err := client.(*glide.Client).Exec(ctxWithParent, *batch2, false)
		require.NoError(suite.T(), err)
		assert.Len(suite.T(), batchResults2, 3)

		// Test 5: Execute batch operation WITHOUT parent context (for comparison)
		batchIndependent := pipeline.NewStandaloneBatch(false)
		batchIndependent.Set("independent:batch:key", "value")
		batchIndependent.Get("independent:batch:key")

		batchResultsIndependent, err := client.(*glide.Client).Exec(context.Background(), *batchIndependent, false)
		require.NoError(suite.T(), err)
		assert.Len(suite.T(), batchResultsIndependent, 2)

		// End parent span BEFORE verification to ensure proper hierarchy capture
		otelInstance.EndSpan(parentSpanPtr)

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify we have the expected span names
		expectedSpans := []string{"Batch", "GET", "send_batch", "send_command"}
		for _, expectedSpan := range expectedSpans {
			assert.Contains(suite.T(), spans.SpanNames, expectedSpan,
				"Should find %s span in exported spans", expectedSpan)
		}

		// Count batch spans - should have at least 3 (2 with parent + 1 independent)
		batchSpanCount := 0
		for _, name := range spans.SpanNames {
			if name == "Batch" {
				batchSpanCount++
			}
		}
		assert.GreaterOrEqual(suite.T(), batchSpanCount, 3,
			"Should have at least 3 batch spans (2 with parent context + 1 independent)")

		// Verify span hierarchy - batch spans with parent context should be children
		// Expected hierarchy:
		// batch-user-operations (parent)
		// ├── Batch (child - first batch operation)
		// ├── GET (child - individual command)
		// └── Batch (child - second batch operation)
		// Batch (independent - third batch operation, no parent)

		expectedChildSpans := []string{"Batch", "GET"}
		suite.verifySpanHierarchy(spans.Spans, "batch-user-operations", expectedChildSpans)

		suite.T().
			Logf("Successfully verified batch operations with parent span context - %d total spans", len(spans.SpanNames))
	})
}

// TestOpenTelemetry_SpanContextAttachment_MixedScenarios tests mixed scenarios with and without parent context
func (suite *GlideTestSuite) TestOpenTelemetry_SpanContextAttachment_MixedScenarios() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		otelInstance := glide.GetOtelInstance()

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file to start fresh
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Create first parent span for operation-1
		parentSpan1, err := otelInstance.CreateSpan("operation-1")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), parentSpan1)
		ctx1 := glide.WithSpan(context.Background(), parentSpan1)

		// Create second parent span for operation-2
		parentSpan2, err := otelInstance.CreateSpan("operation-2")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), parentSpan2)
		ctx2 := glide.WithSpan(context.Background(), parentSpan2)

		// Execute commands with different parent contexts - these should create child spans
		_, err = client.Set(ctx1, "op1:key1", "value1")
		require.NoError(suite.T(), err)

		_, err = client.Set(ctx2, "op2:key1", "value1")
		require.NoError(suite.T(), err)

		// Execute command without parent context - should be independent
		_, err = client.Set(context.Background(), "independent:key", "value")
		require.NoError(suite.T(), err)

		_, err = client.Get(ctx1, "op1:key1")
		require.NoError(suite.T(), err)

		_, err = client.Get(ctx2, "op2:key1")
		require.NoError(suite.T(), err)

		_, err = client.Get(context.Background(), "independent:key")
		require.NoError(suite.T(), err)

		// Test batch operations with different parent contexts
		batch1 := pipeline.NewStandaloneBatch(false)
		batch1.Set("op1:batch:key", "value")
		batch1.Get("op1:batch:key")

		_, err = client.(*glide.Client).Exec(ctx1, *batch1, false)
		require.NoError(suite.T(), err)

		batch2 := pipeline.NewStandaloneBatch(false)
		batch2.Set("op2:batch:key", "value")
		batch2.Get("op2:batch:key")

		_, err = client.(*glide.Client).Exec(ctx2, *batch2, false)
		require.NoError(suite.T(), err)

		// Independent batch without parent context
		batchIndependent := pipeline.NewStandaloneBatch(false)
		batchIndependent.Set("independent:batch:key", "value")

		_, err = client.(*glide.Client).Exec(context.Background(), *batchIndependent, false)
		require.NoError(suite.T(), err)

		// End parent spans BEFORE verification to ensure proper hierarchy capture
		otelInstance.EndSpan(parentSpan1)
		otelInstance.EndSpan(parentSpan2)

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify we have the expected command spans (child spans created under parents)
		expectedSpans := []string{"SET", "GET", "Batch", "send_command", "send_batch"}
		for _, expectedSpan := range expectedSpans {
			assert.Contains(suite.T(), spans.SpanNames, expectedSpan,
				"Should find %s span in exported spans", expectedSpan)
		}

		// Count SET and GET spans - should have multiple of each (some with parents, some independent)
		setSpanCount := 0
		getSpanCount := 0
		batchSpanCount := 0
		for _, name := range spans.SpanNames {
			switch name {
			case "SET":
				setSpanCount++
			case "GET":
				getSpanCount++
			case "Batch":
				batchSpanCount++
			}
		}

		assert.GreaterOrEqual(suite.T(), setSpanCount, 3, "Should have at least 3 SET spans")
		assert.GreaterOrEqual(suite.T(), getSpanCount, 3, "Should have at least 3 GET spans")
		assert.GreaterOrEqual(suite.T(), batchSpanCount, 1, "Should have at least 1 batch span")

		// Verify span hierarchy for both parent operations
		// Note: Only verify direct children, not grandchildren like send_batch
		suite.verifySpanHierarchy(spans.Spans, "operation-1", []string{"SET", "GET"})
		suite.verifySpanHierarchy(spans.Spans, "operation-2", []string{"SET", "GET"})

		suite.T().Logf("Successfully tested mixed scenarios with %d total spans", len(spans.SpanNames))
	})
}

// TestOpenTelemetry_SpanContextAttachment_SpanHierarchy tests span hierarchy in exported traces
func (suite *GlideTestSuite) TestOpenTelemetry_SpanContextAttachment_SpanHierarchy() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		otelInstance := glide.GetOtelInstance()

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file to start fresh
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Create a parent span for the user request
		parentSpanPtr, err := otelInstance.CreateSpan("user-request")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), parentSpanPtr)

		// Store parent span in context
		ctx := glide.WithSpan(context.Background(), parentSpanPtr)

		// Create a nested operation span
		nestedSpanPtr, err := otelInstance.CreateSpan("nested-operation")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), nestedSpanPtr)

		// Store nested span in context (this becomes the new parent for commands)
		nestedCtx := glide.WithSpan(ctx, nestedSpanPtr)

		// Execute commands with nested context - these should create child spans
		_, err = client.Set(nestedCtx, "nested:key1", "value1")
		require.NoError(suite.T(), err)

		_, err = client.Get(nestedCtx, "nested:key1")
		require.NoError(suite.T(), err)

		// Execute batch operations with nested context
		batch := pipeline.NewStandaloneBatch(false)
		batch.Set("nested:batch:key", "value")
		batch.Get("nested:batch:key")

		_, err = client.(*glide.Client).Exec(nestedCtx, *batch, false)
		require.NoError(suite.T(), err)

		// End spans in reverse order BEFORE verification to ensure proper hierarchy capture
		otelInstance.EndSpan(nestedSpanPtr)
		otelInstance.EndSpan(parentSpanPtr)

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify we have the expected command span names (child spans created under parents)
		expectedSpans := []string{"SET", "GET", "send_command", "send_batch"}
		for _, expectedSpan := range expectedSpans {
			assert.Contains(suite.T(), spans.SpanNames, expectedSpan,
				"Should find %s span in exported spans", expectedSpan)
		}

		// Verify we have multiple spans showing the hierarchy
		assert.GreaterOrEqual(suite.T(), len(spans.Spans), 4,
			"Should have at least 4 spans in the hierarchy")

		// Note: User-created spans like "nested-operation" are independent spans, not children of other user spans
		// The CreateSpan API doesn't support parent-child relationships between user spans
		// We can only verify that Glide operations (SET, GET) are children of the nested-operation span
		suite.verifySpanHierarchy(spans.Spans, "nested-operation", []string{"SET", "GET"})

		suite.T().Logf("Successfully tested nested span hierarchy with %d total spans", len(spans.SpanNames))
	})
}

// TestOpenTelemetry_SpanContextAttachment_ClusterClient tests span context attachment with cluster client
func (suite *GlideTestSuite) TestOpenTelemetry_SpanContextAttachment_ClusterClient() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	suite.runWithSpecificClients(ClientTypeFlag(ClusterFlag), func(client interfaces.BaseClientCommands) {
		otelInstance := glide.GetOtelInstance()

		// Wait for any existing spans to be flushed
		time.Sleep(500 * time.Millisecond)

		// Remove any existing span file to start fresh
		if _, err := os.Stat(validEndpointTraces); err == nil {
			err = os.Remove(validEndpointTraces)
			require.NoError(suite.T(), err)
		}

		// Create a parent span for cluster operations
		parentSpanPtr, err := otelInstance.CreateSpan("cluster-user-operation")
		require.NoError(suite.T(), err)
		assert.NotEqual(suite.T(), uint64(0), parentSpanPtr)

		// Store parent span in context
		ctx := glide.WithSpan(context.Background(), parentSpanPtr)

		// Execute commands with parent context on cluster client
		result, err := client.Set(ctx, "cluster:user:123:profile", "john_doe")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		getResult, err := client.Get(ctx, "cluster:user:123:profile")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "john_doe", getResult.Value())

		// Test batch operations with cluster client - use same key to avoid CrossSlot error
		batch := pipeline.NewClusterBatch(true)
		batch.Set("cluster:batch:key", "value1")
		batch.Get("cluster:batch:key")

		batchResults, err := client.(*glide.ClusterClient).Exec(ctx, *batch, true)
		require.NoError(suite.T(), err)
		assert.Len(suite.T(), batchResults, 2)

		// Execute commands without parent context (should be independent)
		result, err = client.Set(context.Background(), "cluster:independent:key", "independent_value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// End parent span BEFORE verification to ensure proper hierarchy capture
		otelInstance.EndSpan(parentSpanPtr)

		// Wait for spans to be flushed
		time.Sleep(5 * time.Second)

		// Read and verify spans
		spans, err := readAndParseSpanFile(validEndpointTraces)
		require.NoError(suite.T(), err)

		// Verify we have the expected span names for cluster operations
		expectedSpans := []string{"SET", "GET"}
		for _, expectedSpan := range expectedSpans {
			assert.Contains(suite.T(), spans.SpanNames, expectedSpan,
				"Should find %s span in exported spans", expectedSpan)
		}

		// Verify we have multiple spans (parent + children + independent)
		assert.GreaterOrEqual(suite.T(), len(spans.SpanNames), 4,
			"Should have at least 4 spans (commands + batch)")

		// Verify span hierarchy for cluster operations
		suite.verifySpanHierarchy(spans.Spans, "cluster-user-operation", []string{"SET", "GET"})

		suite.T().Logf("Successfully tested cluster client span context attachment with %d total spans", len(spans.SpanNames))
	})
}

// TestOpenTelemetry_SpanContextAttachment_ErrorHandlingAdvanced tests advanced error handling scenarios
func (suite *GlideTestSuite) TestOpenTelemetry_SpanContextAttachment_ErrorHandlingAdvanced() {
	if !*otelTest {
		suite.T().Skip("OpenTelemetry tests are disabled")
	}

	suite.runWithSpecificClients(ClientTypeFlag(StandaloneFlag), func(client interfaces.BaseClientCommands) {
		// Test 1: Context with invalid span pointer (should fallback to independent spans)
		ctxWithInvalidSpan := context.WithValue(context.Background(), glide.SpanContextKey, uint64(999999))

		result, err := client.Set(ctxWithInvalidSpan, "test-key-invalid-span", "test-value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// Test 2: Context with zero span pointer (should be treated as no parent)
		ctxWithZeroSpan := context.WithValue(context.Background(), glide.SpanContextKey, uint64(0))

		result, err = client.Set(ctxWithZeroSpan, "test-key-zero-span", "test-value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// Test 3: Context with wrong type for span pointer (should fallback to independent spans)
		ctxWithWrongType := context.WithValue(context.Background(), glide.SpanContextKey, "not-a-uint64")

		result, err = client.Set(ctxWithWrongType, "test-key-wrong-type", "test-value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// Test 4: Context with nil value (should be treated as no parent)
		ctxWithNil := context.WithValue(context.Background(), glide.SpanContextKey, nil)

		result, err = client.Set(ctxWithNil, "test-key-nil", "test-value")
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "OK", result)

		// Test 5: Batch operations with invalid parent context (should fallback gracefully)
		batch := pipeline.NewStandaloneBatch(false)
		batch.Set("batch-key-invalid", "value")
		batch.Get("batch-key-invalid")

		batchResults, err := client.(*glide.Client).Exec(ctxWithInvalidSpan, *batch, false)
		require.NoError(suite.T(), err)
		assert.Len(suite.T(), batchResults, 2)
		assert.Equal(suite.T(), "OK", batchResults[0])

		// Test 6: Rapid context switching (stress test for context extraction)
		otelInstance := glide.GetOtelInstance()
		parentSpan, err := otelInstance.CreateSpan("rapid-context-test")
		require.NoError(suite.T(), err)

		validCtx := glide.WithSpan(context.Background(), parentSpan)

		for i := 0; i < 10; i++ {
			// Alternate between valid and invalid contexts
			var ctx context.Context
			if i%2 == 0 {
				ctx = validCtx
			} else {
				ctx = ctxWithInvalidSpan
			}

			key := fmt.Sprintf("rapid-test-key-%d", i)
			result, err := client.Set(ctx, key, "value")
			require.NoError(suite.T(), err)
			assert.Equal(suite.T(), "OK", result)
		}

		// End parent span after operations complete
		otelInstance.EndSpan(parentSpan)
	})
}
