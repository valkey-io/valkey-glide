// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

// OpenTelemetryConfig provides configuration options for OpenTelemetry integration.
//
// This struct provides configuration options for OpenTelemetry integration, including:
//   - Traces collector endpoint: The endpoint to send trace data to
//   - Metrics collector endpoint: The endpoint to send metrics data to
//   - Flush interval: How often to flush data to the collectors (in milliseconds)
//
// The collector endpoints support multiple protocols:
//   - HTTP: Use "http://" prefix (e.g., "http://localhost:4318")
//   - HTTPS: Use "https://" prefix (e.g., "https://collector.example.com:4318")
//   - gRPC: Use "grpc://" prefix (e.g., "grpc://localhost:4317")
//   - File: Use "file://" prefix followed by the full path (e.g., "file:///path/to/")
//     to write the signals data to a file.
type OpenTelemetryConfig struct {
	// TracesCollectorEndPoint is the endpoint to send trace data to.
	TracesCollectorEndPoint string

	// MetricsCollectorEndPoint is the endpoint to send metrics data to.
	MetricsCollectorEndPoint string

	// FlushIntervalMs is how often to flush data to the collectors (in milliseconds).
	// If not specified, defaults to 5000ms (5 seconds).
	FlushIntervalMs int
}

// NewOpenTelemetryConfig creates a new OpenTelemetryConfig with the given parameters.
func NewOpenTelemetryConfig(tracesEndPoint, metricsEndPoint string, flushIntervalMs int) *OpenTelemetryConfig {
	return &OpenTelemetryConfig{
		TracesCollectorEndPoint:  tracesEndPoint,
		MetricsCollectorEndPoint: metricsEndPoint,
		FlushIntervalMs:          flushIntervalMs,
	}
}
