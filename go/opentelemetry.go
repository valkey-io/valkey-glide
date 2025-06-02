// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
// Package glide provides functionality for OpenTelemetry integration.
// ⚠️ OpenTelemetry can only be initialized once per process. Calling Init() more than once will be ignored.
// If you need to change configuration, restart the process with new settings.

// File Exporter Details
// - For `file://` endpoints:
//   - The path must start with `file://` (e.g., `file:///tmp/otel` or `file:///tmp/otel/traces.json`).
//   - If the path is a directory or lacks a file extension, data is written to `signals.json` in that directory.
//   - If the path includes a filename with an extension, that file is used as-is.
//   - The parent directory must already exist; otherwise, initialization will fail with an InvalidInput error.
//   - If the target file exists, new data is appended (not overwritten).

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
	"log"
	"math/big"
	"sync"
	"unsafe"
)

// OpenTelemetryConfig represents the configuration for OpenTelemetry integration.
// It allows configuring how telemetry data (traces and metrics) is exported to an OpenTelemetry collector.
//
// Example usage:
//
//	config := glide.OpenTelemetryConfig{
//	    Traces: &glide.OpenTelemetryTracesConfig{
//	        Endpoint:         "http://localhost:4318/v1/traces",
//	        SamplePercentage: 10, // Sample 10% of commands
//	    },
//	    FlushIntervalMs: &interval, // Optional, defaults to 5000, e.g. interval := int64(1000)
//	}
//	err := glide.GetInstance().Init(config)
//	if err != nil {
//	    log.Fatalf("Failed to initialize OpenTelemetry: %v", err)
//	}
type OpenTelemetryConfig struct {
	// Traces configuration for exporting trace data. If nil, trace data will not be exported.
	Traces *OpenTelemetryTracesConfig
	// Metrics configuration for exporting metrics data. If nil, metrics data will not be exported.
	Metrics *OpenTelemetryMetricsConfig
	// (Optional)FlushIntervalMs is the interval in milliseconds between consecutive exports of telemetry data.
	// If not specified, defaults to 5000.
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
//
// Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more
// detailed telemetry data but will impact performance. It is recommended to keep this number low (1-5%) in production
// environments unless you have specific needs for higher sampling rates.
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
		traces:  nil,
		metrics: nil,
	}

	if openTelemetryConfig.FlushIntervalMs != nil {
		cConfig.has_flush_interval_ms = true
		cConfig.flush_interval_ms = C.int64_t(*openTelemetryConfig.FlushIntervalMs)
	}

	var tracesConfig *C.OpenTelemetryTracesConfig
	var metricsConfig *C.OpenTelemetryMetricsConfig
	var p pinner
	defer p.Unpin()

	if openTelemetryConfig.Traces != nil {
		endpoint := C.CString(openTelemetryConfig.Traces.Endpoint)
		tracesConfig = &C.OpenTelemetryTracesConfig{
			endpoint:              endpoint,
			has_sample_percentage: true,
			sample_percentage:     C.uint32_t(openTelemetryConfig.Traces.SamplePercentage),
		}
		p.Pin(unsafe.Pointer(tracesConfig))
		cConfig.traces = tracesConfig
	}

	if openTelemetryConfig.Metrics != nil {
		endpoint := C.CString(openTelemetryConfig.Metrics.Endpoint)
		metricsConfig = &C.OpenTelemetryMetricsConfig{
			endpoint: endpoint,
		}
		p.Pin(unsafe.Pointer(metricsConfig))
		cConfig.metrics = metricsConfig
	}

	// Initialize OpenTelemetry
	errMsg := C.init_open_telemetry(&cConfig)
	if errMsg != nil {
		err := fmt.Errorf("failed to initialize OpenTelemetry: %s", C.GoString(errMsg))
		C.free_c_string(errMsg)
		p.Unpin()
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

// ShouldSample determines if the current request should be sampled for OpenTelemetry tracing.
// Uses the configured sample percentage to randomly decide whether to create a span for this request.
func (o *OpenTelemetry) shouldSample() bool {
	percentage := o.GetSamplePercentage()
	if !o.IsInitialized() || percentage <= 0 {
		return false
	}

	currentRandom, err := rand.Int(rand.Reader, big.NewInt(100))
	if err != nil {
		log.Printf("Not sampling otel span due to failure to generate random number: %v", err)
		return false
	}
	return o.IsInitialized() && percentage > 0 && float32(currentRandom.Int64()) < float32(percentage)
}

var configMutex sync.RWMutex

// GetSamplePercentage returns the sample percentage for traces only if OpenTelemetry is initialized
// and the traces config is set, otherwise returns 0.
func (o *OpenTelemetry) GetSamplePercentage() int32 {
	configMutex.RLock()
	defer configMutex.RUnlock()
	if !o.IsInitialized() || otelConfig == nil || otelConfig.Traces == nil {
		return 0
	}
	return otelConfig.Traces.SamplePercentage
}

// SetSamplePercentage sets the percentage of requests to be sampled and traced.
// Must be a value between 0 and 100.
// This setting only affects traces, not metrics.
func (o *OpenTelemetry) SetSamplePercentage(percentage int32) error {
	configMutex.Lock()
	defer configMutex.Unlock()
	if !o.IsInitialized() || otelConfig == nil || otelConfig.Traces == nil {
		return fmt.Errorf("OpenTelemetry config traces not initialized")
	}
	if percentage < 0 || percentage > 100 {
		return fmt.Errorf("Otel sample percentage must be between 0 and 100")
	}
	otelConfig.Traces.SamplePercentage = percentage
	return nil
}

// CreateSpan creates a new OpenTelemetry span with the given name and returns a pointer to the span.
func (o *OpenTelemetry) createSpan(requestType C.RequestType) uint64 {
	if !o.IsInitialized() {
		return 0
	}
	return uint64(C.create_otel_span(uint32(requestType)))
}

// CreateBatchSpan creates a new OpenTelemetry span with the name "batch" and returns a pointer to the span.
func (o *OpenTelemetry) createBatchSpan() uint64 {
	if !o.IsInitialized() {
		return 0
	}
	return uint64(C.create_batch_otel_span())
}

// DropSpan drops an OpenTelemetry span given its pointer.
func (o *OpenTelemetry) dropSpan(spanPtr uint64) {
	if spanPtr == 0 {
		return
	}
	C.drop_otel_span(C.uint64_t(spanPtr))
}
