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
	otelSpanFile         = "/tmp/spans.json"
	otelSpanFileEndpoint = "file:///tmp/spans.json"
)

type OpenTelemetryIntegrationSuite struct {
	GlideTestSuite
}

type otelSpan struct {
	Name string `json:"name"`
}

func (suite *OpenTelemetryIntegrationSuite) SetupSuite() {
	suite.GlideTestSuite.SetupSuite()

	// Remove any existing span file before starting
	_ = os.Remove(otelSpanFile)

	cfg := api.OpenTelemetryConfig{
		Traces: api.OtelExporterConfig{
			Endpoint:         otelSpanFileEndpoint,
			SamplePercentage: 100,
		},
		FlushIntervalMs: 100,
	}
	err := api.InitOpenTelemetry(cfg)
	assert.Nil(suite.T(), err, "OpenTelemetry initialization should succeed")
}

func (suite *OpenTelemetryIntegrationSuite) TearDownTest() {
	_ = os.Remove(otelSpanFile)
}

func (suite *OpenTelemetryIntegrationSuite) TestClusterClientSpans() {
	client, err := api.NewGlideClusterClient(suite.defaultClusterClientConfig())
	assert.Nil(suite.T(), err)
	defer client.Close()

	key := "otel_test_key"
	value := "otel_test_value"

	_, err = client.Set(key, value)
	assert.Nil(suite.T(), err)

	_, err = client.Get(key)
	assert.Nil(suite.T(), err)

	// Wait for spans to be flushed
	time.Sleep(2 * time.Second)

	spanData, err := os.ReadFile(otelSpanFile)
	assert.Nil(suite.T(), err, "Should be able to read span file")

	var spanNames []string
	for _, line := range splitLines(string(spanData)) {
		if line == "" {
			continue
		}
		var span otelSpan
		if err := json.Unmarshal([]byte(line), &span); err == nil {
			spanNames = append(spanNames, span.Name)
		}
	}

	assert.Contains(suite.T(), spanNames, "Set", "Should contain Set span")
	assert.Contains(suite.T(), spanNames, "Get", "Should contain Get span")
}

func splitLines(s string) []string {
	var lines []string
	start := 0
	for i := range s {
		if s[i] == '\n' {
			lines = append(lines, s[start:i])
			start = i + 1
		}
	}
	if start < len(s) {
		lines = append(lines, s[start:])
	}
	return lines
}

func TestOpenTelemetryIntegrationSuite(t *testing.T) {
	suite.Run(t, new(OpenTelemetryIntegrationSuite))
}
