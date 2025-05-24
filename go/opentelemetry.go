// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

/*
#cgo LDFLAGS: -lglide_ffi
#include "lib.h"
#include <stdlib.h>
*/
import "C"

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"unsafe"
)

// OpenTelemetryConfig represents the configuration for OpenTelemetry integration.
// It allows configuring how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
type OpenTelemetryConfig struct {
	// Traces configuration for exporting trace data. If nil, trace data will not be exported.
	Traces *OpenTelemetryTracesConfig
	// Metrics configuration for exporting metrics data. If nil, metrics data will not be exported.
	Metrics *OpenTelemetryMetricsConfig
	// FlushIntervalMs is the interval in milliseconds between consecutive exports of telemetry data.
	// If nil, a default value will be used.
	FlushIntervalMs *int64
}

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
	SamplePercentage int32
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

var (
	otelInstance    *OpenTelemetry
	otelConfig      *OpenTelemetryConfig
	otelInitialized bool = false
)

// OpenTelemetry provides functionality for OpenTelemetry integration.
// It can only be initialized once per process. Calling Init() more than once will be ignored.
// If you need to change configuration, restart the process with new settings.
type OpenTelemetry struct{}

// GetInstance returns the singleton OpenTelemetry instance.
func GetInstance() *OpenTelemetry {
	if otelInstance == nil {
		otelInstance = &OpenTelemetry{}
	}
	return otelInstance
}

// Init initializes the OpenTelemetry instance with the provided configuration.
// It can only be called once per process. Subsequent calls will be ignored.
func (o *OpenTelemetry) Init(openTelemetryConfig OpenTelemetryConfig) error {
	if otelInitialized {
		return nil // otel already initialized, ignore the new config
	}
	// At least one of traces or metrics must be provided
	if openTelemetryConfig.Traces == nil && openTelemetryConfig.Metrics == nil {
		return fmt.Errorf("at least one of traces or metrics must be provided for OpenTelemetry configuration")
	}

	// Convert Go config to C config
	cConfig := C.OpenTelemetryConfig{
		has_traces:            openTelemetryConfig.Traces != nil,
		has_metrics:           openTelemetryConfig.Metrics != nil,
		has_flush_interval_ms: openTelemetryConfig.FlushIntervalMs != nil,
		flush_interval_ms:     C.int64_t(0), // Default to 0, will be set if provided
	}

	if openTelemetryConfig.FlushIntervalMs != nil {
		cConfig.flush_interval_ms = C.int64_t(*openTelemetryConfig.FlushIntervalMs)
	}

	if openTelemetryConfig.Traces != nil {
		cConfig.traces.endpoint = C.CString(openTelemetryConfig.Traces.Endpoint)
		cConfig.traces.has_sample_percentage = true
		cConfig.traces.sample_percentage = C.uint32_t(openTelemetryConfig.Traces.SamplePercentage)
	}

	if openTelemetryConfig.Metrics != nil {
		cConfig.metrics.endpoint = C.CString(openTelemetryConfig.Metrics.Endpoint)
	}

	// Initialize OpenTelemetry
	errMsg := C.init_open_telemetry(cConfig)
	if errMsg != nil {
		err := fmt.Errorf("failed to initialize OpenTelemetry: %s", C.GoString(errMsg))
		C.free(unsafe.Pointer(errMsg))
		return err
	}
	otelConfig = &openTelemetryConfig
	otelInitialized = true
	return nil
}

// IsInitialized returns true if the OpenTelemetry instance is initialized, false otherwise.
func (o *OpenTelemetry) IsInitialized() bool {
	return otelInitialized
}

// GetSamplePercentage returns the sample percentage for traces only if OpenTelemetry is initialized
// and the traces config is set, otherwise returns 0.
func (o *OpenTelemetry) GetSamplePercentage() int32 {
	if !o.IsInitialized() || otelConfig == nil || otelConfig.Traces == nil {
		return 0
	}
	return otelConfig.Traces.SamplePercentage
}

// ShouldSample determines if the current request should be sampled for OpenTelemetry tracing.
// Uses the configured sample percentage to randomly decide whether to create a span for this request.
func (o *OpenTelemetry) ShouldSample() bool {
	percentage := o.GetSamplePercentage()
	currentRandom, err := rand.Int(rand.Reader, big.NewInt(100))
	if err != nil {
		return false
	}
	return o.IsInitialized() && percentage > 0 && float32(currentRandom.Int64()) < float32(percentage)
}

// SetSamplePercentage sets the percentage of requests to be sampled and traced.
// Must be a value between 0 and 100.
// This setting only affects traces, not metrics.
func (o *OpenTelemetry) SetSamplePercentage(percentage int32) error {
	if !o.IsInitialized() || otelConfig == nil || otelConfig.Traces == nil {
		return fmt.Errorf("OpenTelemetry config traces not initialized")
	}
	otelConfig.Traces.SamplePercentage = percentage
	return nil
}

// CreateSpan creates a new OpenTelemetry span with the given name and returns a pointer to the span.
func (o *OpenTelemetry) CreateSpan(requestType C.RequestType) uint64 {
	if !o.IsInitialized() {
		return 0
	}
	return uint64(C.create_otel_span(uint32(requestType)))
}

// DropSpan drops an OpenTelemetry span given its pointer.
func (o *OpenTelemetry) DropSpan(spanPtr uint64) {
	if spanPtr == 0 {
		return
	}
	C.drop_otel_span(C.uint64_t(spanPtr))
}
