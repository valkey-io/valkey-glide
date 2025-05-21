package integTest

import (
	"encoding/json"
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/valkey-io/valkey-glide/go/api"
)

const (
	otelSpanFile            = "/tmp/spans.json"
	otelSpanFlushIntervalMs = 100
)

type OpenTelemetryIntegrationSuite struct {
	GlideTestSuite
}

func (suite *OpenTelemetryIntegrationSuite) SetupTest() {
	// Create an empty file for spans
	file, err := os.Create(otelSpanFile)
	if err != nil {
		suite.T().Fatalf("Failed to create span file: %v", err)
	}
	file.Close()
}

func (suite *OpenTelemetryIntegrationSuite) TearDownTest() {
	// Clean up the span file
	os.Remove(otelSpanFile)
}

func (suite *OpenTelemetryIntegrationSuite) readSpans() []map[string]interface{} {
	data, err := os.ReadFile(otelSpanFile)
	if err != nil {
		return nil
	}

	var spans []map[string]interface{}
	err = json.Unmarshal(data, &spans)
	if err != nil {
		return nil
	}
	return spans
}

func (suite *OpenTelemetryIntegrationSuite) getSpanNames() []string {
	spans := suite.readSpans()
	if spans == nil {
		return nil
	}

	var names []string
	for _, span := range spans {
		if name, ok := span["name"].(string); ok {
			names = append(names, name)
		}
	}
	return names
}

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_WrongOpenTelemetryConfig() {
	// Test wrong traces endpoint
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint: "wrong.endpoint",
		},
	}
	err := api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Parse error")

	// Test wrong metrics endpoint
	cfg = api.OpenTelemetryConfig{
		Metrics: &api.OpenTelemetryMetricsConfig{
			Endpoint: "wrong.endpoint",
		},
	}
	err = api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Parse error")

	// Test negative flush interval
	cfg = api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 1,
		},
		FlushIntervalMs: -400,
	}
	err = api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "flushIntervalMs must be a positive integer")

	// Test negative sample percentage
	cfg = api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 400,
		},
	}
	err = api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "sample percentage must be between 0 and 100")

	// Test wrong file path format
	cfg = api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint: "file:invalid-path/v1/traces.json",
		},
	}
	err = api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "File path must start with 'file://'")

	// Test non-existent directory
	cfg = api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint: "file:///no-exists-path/v1/traces.json",
		},
	}
	err = api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "The directory does not exist")

	// Test no traces or metrics provided
	cfg = api.OpenTelemetryConfig{}
	err = api.GetInstance().Init(cfg)
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "at least one of traces or metrics must be provided")
}

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_AutomaticSpanLifecycle() {
	// Initialize OpenTelemetry with file exporter
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 100,
		},
		FlushIntervalMs: otelSpanFlushIntervalMs,
	}
	err := api.GetInstance().Init(cfg)
	assert.NoError(suite.T(), err)

	// Create client and execute commands
	client := suite.defaultClient()

	// Execute multiple commands - each should automatically create and clean up its span
	_, err = client.Set("test_key1", "value1")
	assert.NoError(suite.T(), err)
	_, err = client.Get("test_key1")
	assert.NoError(suite.T(), err)
	_, err = client.Set("test_key2", "value2")
	assert.NoError(suite.T(), err)
	_, err = client.Get("test_key2")
	assert.NoError(suite.T(), err)

	// Wait for spans to be flushed
	time.Sleep(time.Duration(otelSpanFlushIntervalMs*2) * time.Millisecond)

	// Verify spans were created
	spanNames := suite.getSpanNames()
	assert.NotNil(suite.T(), spanNames)
	assert.Contains(suite.T(), spanNames, "Set")
	assert.Contains(suite.T(), spanNames, "Get")
}

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_ConcurrentCommandsSpanLifecycle() {
	// Initialize OpenTelemetry with file exporter
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 100,
		},
		FlushIntervalMs: otelSpanFlushIntervalMs,
	}
	err := api.GetInstance().Init(cfg)
	assert.NoError(suite.T(), err)

	// Create client
	client := suite.defaultClient()

	// Execute multiple concurrent commands
	done := make(chan bool)
	go func() {
		_, err := client.Set("test_key1", "value1")
		assert.NoError(suite.T(), err)
		done <- true
	}()
	go func() {
		_, err := client.Get("test_key1")
		assert.NoError(suite.T(), err)
		done <- true
	}()
	go func() {
		_, err := client.Set("test_key2", "value2")
		assert.NoError(suite.T(), err)
		done <- true
	}()
	go func() {
		_, err := client.Get("test_key2")
		assert.NoError(suite.T(), err)
		done <- true
	}()

	// Wait for all commands to complete
	for i := 0; i < 4; i++ {
		<-done
	}

	// Wait for spans to be flushed
	time.Sleep(time.Duration(otelSpanFlushIntervalMs*2) * time.Millisecond)

	// Verify spans were created
	spanNames := suite.getSpanNames()
	assert.NotNil(suite.T(), spanNames)
	assert.Contains(suite.T(), spanNames, "Set")
	assert.Contains(suite.T(), spanNames, "Get")
}

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_GlobalConfigNotReinitialize() {
	// Initialize OpenTelemetry with file exporter
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 100,
		},
		FlushIntervalMs: otelSpanFlushIntervalMs,
	}
	err := api.GetInstance().Init(cfg)
	assert.NoError(suite.T(), err)

	// Try to initialize again with different config
	cfg2 := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile + "2",
			SamplePercentage: 50,
		},
		FlushIntervalMs: otelSpanFlushIntervalMs * 2,
	}
	err = api.GetInstance().Init(cfg2)
	assert.NoError(suite.T(), err) // Should not error, but should not change config

	// Verify original config is still in effect
	client := suite.defaultClient()
	_, err = client.Set("test_key", "value")
	assert.NoError(suite.T(), err)

	// Wait for spans to be flushed
	time.Sleep(time.Duration(otelSpanFlushIntervalMs*2) * time.Millisecond)

	// Verify spans were created with original config
	spanNames := suite.getSpanNames()
	assert.NotNil(suite.T(), spanNames)
	assert.Contains(suite.T(), spanNames, "Set")
}

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_MultipleClientsSameConfig() {
	// Initialize OpenTelemetry with file exporter
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 100,
		},
		FlushIntervalMs: otelSpanFlushIntervalMs,
	}
	err := api.GetInstance().Init(cfg)
	assert.NoError(suite.T(), err)

	// Create multiple clients
	client1 := suite.defaultClient()
	client2 := suite.defaultClient()

	// Execute commands on different clients
	_, err = client1.Set("test_key1", "value1")
	assert.NoError(suite.T(), err)
	_, err = client2.Set("test_key2", "value2")
	assert.NoError(suite.T(), err)

	// Wait for spans to be flushed
	time.Sleep(time.Duration(otelSpanFlushIntervalMs*2) * time.Millisecond)

	// Verify spans were created
	spanNames := suite.getSpanNames()
	assert.NotNil(suite.T(), spanNames)
	assert.Contains(suite.T(), spanNames, "Set")
}

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_SimpleOpenTelemetryInit() {
	// Initialize OpenTelemetry with file exporter
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 100,
		},
		FlushIntervalMs: otelSpanFlushIntervalMs,
	}
	err := api.GetInstance().Init(cfg)
	assert.NoError(suite.T(), err)
}

func TestOpenTelemetryIntegrationSuite(t *testing.T) {
	suite.Run(t, new(OpenTelemetryIntegrationSuite))
}
