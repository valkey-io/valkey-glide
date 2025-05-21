package api

/*
#cgo LDFLAGS: -lglide_ffi
#include "../lib.h"
#include <stdlib.h>
*/
import "C"
import (
	"errors"
	"fmt"
	"sync"
	"unsafe"
)

// OpenTelemetryTracesConfig represents the configuration for exporting OpenTelemetry traces.
//
// - Endpoint: The endpoint to which trace data will be exported. Expected format:
//   - For gRPC: `grpc://host:port`
//   - For HTTP: `http://host:port` or `https://host:port`
//   - For file exporter: `file:///absolute/path/to/folder/file.json`
//   - SamplePercentage: The percentage of requests to sample and create a span for, used to measure command duration.
//     Must be between 0 and 100. If not specified, defaults to 1.
type OpenTelemetryTracesConfig struct {
	Endpoint         string
	SamplePercentage uint32
}

// OpenTelemetryMetricsConfig represents the configuration for exporting OpenTelemetry metrics.
//
// - Endpoint: The endpoint to which metrics data will be exported. Expected format:
//   - For gRPC: `grpc://host:port`
//   - For HTTP: `http://host:port` or `https://host:port`
//   - For file exporter: `file:///absolute/path/to/folder/file.json`
type OpenTelemetryMetricsConfig struct {
	Endpoint string
}

// OpenTelemetryConfig represents the full OpenTelemetry configuration for initialization.
//
//   - Traces: Optional configuration for exporting trace data. If nil, trace data will not be exported.
//   - Metrics: Optional configuration for exporting metrics data. If nil, metrics data will not be exported.
//   - FlushIntervalMs: Optional interval in milliseconds between consecutive exports of telemetry data.
//     Must be a positive integer. If not specified, defaults to 5000ms.
//
// At least one of Traces or Metrics must be provided.
type OpenTelemetryConfig struct {
	Traces          *OpenTelemetryTracesConfig
	Metrics         *OpenTelemetryMetricsConfig
	FlushIntervalMs int64
}

// OpenTelemetry is a singleton that manages OpenTelemetry initialization.
// It ensures that OpenTelemetry is only initialized once per process.
type OpenTelemetry struct{}

var (
	instance     *OpenTelemetry
	instanceOnce sync.Once
)

// validateConfig validates the OpenTelemetry configuration.
func validateConfig(cfg OpenTelemetryConfig) error {
	// At least one of traces or metrics must be provided
	if cfg.Traces == nil && cfg.Metrics == nil {
		return errors.New("at least one of traces or metrics must be provided for OpenTelemetry configuration")
	}

	// Validate traces config if provided
	if cfg.Traces != nil {
		if cfg.Traces.Endpoint == "" {
			return errors.New("traces endpoint must be provided")
		}
		if cfg.Traces.SamplePercentage > 100 {
			return errors.New("traces sample percentage must be between 0 and 100")
		}
	}

	// Validate metrics config if provided
	if cfg.Metrics != nil && cfg.Metrics.Endpoint == "" {
		return errors.New("metrics endpoint must be provided")
	}

	// Validate flush interval
	if cfg.FlushIntervalMs <= 0 {
		return fmt.Errorf("invalid input: flushIntervalMs must be a positive integer (got: %d)", cfg.FlushIntervalMs)
	}

	return nil
}

// Init initializes OpenTelemetry for the process. Call once before using the client.
//
// Example usage:
//
//	err := api.OpenTelemetry.Init(api.OpenTelemetryConfig{
//	    Traces: &api.OpenTelemetryTracesConfig{
//	        Endpoint: "http://localhost:4318/v1/traces",
//	        SamplePercentage: 10, // Optional, defaults to 1
//	    },
//	    Metrics: &api.OpenTelemetryMetricsConfig{
//	        Endpoint: "http://localhost:4318/v1/metrics",
//	    },
//	    FlushIntervalMs: 5000, // Optional, defaults to 5000
//	})
//	if err != nil { panic(err) }
//
// Note: OpenTelemetry can only be initialized once per process. Calling Init more than once will be ignored.
// If you need to change configuration, restart the process with new settings.
func (o *OpenTelemetry) Init(cfg OpenTelemetryConfig) error {
	var initErr error
	instanceOnce.Do(func() {
		// Validate configuration
		if err := validateConfig(cfg); err != nil {
			initErr = err
			return
		}

		// Convert Go config to C struct
		var cCfg C.struct_OpenTelemetryConfig

		// Set traces config
		if cfg.Traces != nil {
			cCfg.has_traces = C.bool(true)
			cCfg.traces.endpoint = C.CString(cfg.Traces.Endpoint)
			cCfg.traces.has_sample_percentage = C.bool(true)
			cCfg.traces.sample_percentage = C.uint(cfg.Traces.SamplePercentage)
		} else {
			cCfg.has_traces = C.bool(false)
		}

		// Set metrics config
		if cfg.Metrics != nil {
			cCfg.has_metrics = C.bool(true)
			cCfg.metrics.endpoint = C.CString(cfg.Metrics.Endpoint)
		} else {
			cCfg.has_metrics = C.bool(false)
		}

		// Set flush interval
		if cfg.FlushIntervalMs > 0 {
			cCfg.has_flush_interval_ms = C.bool(true)
			cCfg.flush_interval_ms = C.int64_t(cfg.FlushIntervalMs)
		} else {
			cCfg.has_flush_interval_ms = C.bool(false)
		}

		// Initialize OpenTelemetry
		outError := C.init_open_telemetry(cCfg)

		// Free the endpoint strings after they're no longer needed
		if cCfg.has_traces {
			C.free(unsafe.Pointer(cCfg.traces.endpoint))
		}
		if cCfg.has_metrics {
			C.free(unsafe.Pointer(cCfg.metrics.endpoint))
		}

		if outError != nil {
			errMsg := C.GoString(outError)
			C.free(unsafe.Pointer(outError))
			initErr = errors.New(errMsg)
			return
		}

		instance = o
	})

	return initErr
}

// GetInstance returns the singleton instance of OpenTelemetry.
func GetInstance() *OpenTelemetry {
	return instance
}
