package integTest

import (
	"encoding/json"
	"os"
	"testing"

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
	negativeFlushInterval := int64(-400)
	cfg = api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 1,
		},
		FlushIntervalMs: &negativeFlushInterval,
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

func (suite *OpenTelemetryIntegrationSuite) TestOpenTelemetry_SimpleOpenTelemetryInit() {
	// Initialize OpenTelemetry with file exporter
	flushIntervalMs := int64(otelSpanFlushIntervalMs)
	cfg := api.OpenTelemetryConfig{
		Traces: &api.OpenTelemetryTracesConfig{
			Endpoint:         "file://" + otelSpanFile,
			SamplePercentage: 100,
		},
		FlushIntervalMs: &flushIntervalMs,
	}
	err := api.GetInstance().Init(cfg)
	assert.NoError(suite.T(), err)
}

func TestOpenTelemetryIntegrationSuite(t *testing.T) {
	suite.Run(t, new(OpenTelemetryIntegrationSuite))
}
