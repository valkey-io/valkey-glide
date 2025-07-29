// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package glide provides functionality for OpenTelemetry integration.
// ⚠️ OpenTelemetry can only be initialized once per process. Calling Init() more than once will be ignored.
// If you need to change configuration, restart the process with new settings.

// OpenTelemetry Configuration:
//   - traces: (optional) Configure trace exporting with endpoint and sample percentage
//   - metrics: (optional) Configure metrics exporting with endpoint
//   - flush_interval_ms: (optional) Interval in milliseconds for flushing data to the collector (defaults to 5000ms)
//
// File Exporter Details:
// - For `file://` endpoints:
//   - The path must start with `file://` (e.g., `file:///tmp/otel` or `file:///tmp/otel/traces.json`).
//   - If the path is a directory or lacks a file extension, data is written to `signals.json` in that directory.
//   - If the path includes a filename with an extension, that file is used as-is.
//   - The parent directory must already exist; otherwise, initialization will fail with an InvalidInput error.
//   - If the target file exists, new data is appended (not overwritten).
//
// Validation Rules:
//   - flush_interval_ms must be a positive integer
//   - sample_percentage must be between 0 and 100
//   - File exporter paths must start with file:// and have an existing parent directory
//   - Invalid configuration will return an error when calling Init()

package glide

/*
#include "lib.h"
*/
import "C"

import (
	"context"
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
//		import "github.com/valkey-io/valkey-glide/go/v2"
//
//		config := glide.OpenTelemetryConfig{
//			Traces: &glide.OpenTelemetryTracesConfig{
//				Endpoint:         "http://localhost:4318/v1/traces",
//				SamplePercentage: 10, // Optional, defaults to 1. Can also be changed at runtime via `SetSamplePercentage()`
//	        },
//			Metrics: &glide.OpenTelemetryMetricsConfig{
//				Endpoint: "http://localhost:4318/v1/metrics",
//			},
//			FlushIntervalMs: &interval, // Optional, defaults to 5000, e.g. interval := int64(1000)
//			SpanFromContext: func(ctx context.Context) (uint64, bool) {
//				// Extract span pointer from context for parent-child span relationships
//				if spanPtr, ok := ctx.Value(glide.SpanContextKey).(uint64); ok && spanPtr != 0 {
//					return spanPtr, true
//				}
//				return 0, false
//			},
//		}
//		err := glide.GetOtelInstance().Init(config)
//		if err != nil {
//			log.Fatalf("Failed to initialize OpenTelemetry: %v", err)
//		}
type OpenTelemetryConfig struct {
	// Traces configuration for exporting trace data. If nil, trace data will not be exported.
	Traces *OpenTelemetryTracesConfig
	// Metrics configuration for exporting metrics data. If nil, metrics data will not be exported.
	Metrics *OpenTelemetryMetricsConfig
	// (Optional)FlushIntervalMs is the interval in milliseconds between consecutive exports of telemetry data.
	// If not specified, defaults to 5000.
	FlushIntervalMs *int64
	// (Optional) SpanFromContext is a function that extracts parent span information from a context.Context.
	// When provided, Glide will use this function to create child spans under existing parent spans,
	// enabling end-to-end tracing across your application and database operations.
	//
	// The function should return:
	//   - spanPtr: A span pointer (uint64) obtained from CreateSpan() or 0 if no parent span is found
	//   - found: true if a valid parent span was found in the context, false otherwise
	//
	// If this function is not provided or returns found=false, Glide will create independent spans
	// as it currently does. If the function panics or returns invalid data, Glide will gracefully
	// fallback to creating independent spans.
	//
	// Example implementation:
	//   SpanFromContext: func(ctx context.Context) (uint64, bool) {
	//       if spanPtr, ok := ctx.Value(glide.SpanContextKey).(uint64); ok && spanPtr != 0 {
	//           return spanPtr, true
	//       }
	//       return 0, false
	//   }
	//
	// Note: This function must be thread-safe as it may be called concurrently from multiple goroutines.
	SpanFromContext func(ctx context.Context) (spanPtr uint64, found bool)
}

// OpenTelemetryTracesConfig represents the configuration for exporting OpenTelemetry traces.
//
// Endpoint: The endpoint to which trace data will be exported. Expected format:
//   - For gRPC: `grpc://host:port`
//   - For HTTP: `http://host:port` or `https://host:port`
//   - For file exporter: `file:///absolute/path/to/folder/file.json`
//
// SamplePercentage: The percentage of requests to sample and create a span for, used to measure command duration.
//   - Must be between 0 and 100. If not specified, defaults to 1.
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

// Context key type for consistent span storage
type spanContextKeyType struct{}

// SpanContextKey is the default context key used to store Glide span pointers in context.Context.
// This key is used by WithSpan() and DefaultSpanFromContext() functions.
var SpanContextKey = spanContextKeyType{}

var (
	otelInstance    *OpenTelemetry
	otelConfig      *OpenTelemetryConfig
	otelInitialized bool = false
)

// OpenTelemetry provides functionality for OpenTelemetry integration.
//
// This struct provides a centralized way to initialize OpenTelemetry and control
// sampling behavior at runtime.
//
// Example usage:
//
//		import "github.com/valkey-io/valkey-glide/go/v2"
//
//		config := glide.OpenTelemetryConfig{
//			Traces: &glide.OpenTelemetryTracesConfig{
//				Endpoint:         "http://localhost:4318/v1/traces",
//				SamplePercentage: 10, // Optional, defaults to 1. Can also be changed at runtime via `SetSamplePercentage()`
//	        },
//			Metrics: &glide.OpenTelemetryMetricsConfig{
//				Endpoint: "http://localhost:4318/v1/metrics",
//			},
//			FlushIntervalMs: &interval, // Optional, defaults to 5000, e.g. interval := int64(1000)
//		}
//		err := glide.GetOtelInstance().Init(config)
//		if err != nil {
//			log.Fatalf("Failed to initialize OpenTelemetry: %v", err)
//		}
//
// Note:
// OpenTelemetry can only be initialized once per process. Subsequent calls to
// Init() will be ignored. This is by design, as OpenTelemetry is a global
// resource that should be configured once at application startup.
type OpenTelemetry struct{}

// GetOtelInstance returns the singleton OpenTelemetry instance.
func GetOtelInstance() *OpenTelemetry {
	if otelInstance == nil {
		otelInstance = &OpenTelemetry{}
	}
	return otelInstance
}

// Init initializes the OpenTelemetry instance with the provided configuration.
// It can only be called once per process. Subsequent calls will be ignored.
func (o *OpenTelemetry) Init(openTelemetryConfig OpenTelemetryConfig) error {
	if otelInitialized {
		return fmt.Errorf("openTelemetry already initialized, ignoring new config")
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
		tracesEndpoint := C.CString(openTelemetryConfig.Traces.Endpoint)
		defer C.free(unsafe.Pointer(tracesEndpoint))
		tracesConfig = &C.OpenTelemetryTracesConfig{
			endpoint:              tracesEndpoint,
			has_sample_percentage: true,
			sample_percentage:     C.uint32_t(openTelemetryConfig.Traces.SamplePercentage),
		}
		p.Pin(unsafe.Pointer(tracesConfig))
		cConfig.traces = tracesConfig
	}

	if openTelemetryConfig.Metrics != nil {
		metricsEndpoint := C.CString(openTelemetryConfig.Metrics.Endpoint)
		defer C.free(unsafe.Pointer(metricsEndpoint))
		metricsConfig = &C.OpenTelemetryMetricsConfig{
			endpoint: metricsEndpoint,
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
		return fmt.Errorf("openTelemetry config traces not initialized")
	}
	if percentage < 0 || percentage > 100 {
		return fmt.Errorf("telemetry sample percentage must be between 0 and 100")
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

// createSpanWithParent creates a new OpenTelemetry span with the given request type and parent span pointer.
// This is an internal method used by command execution to create child spans.
func (o *OpenTelemetry) createSpanWithParent(requestType C.RequestType, parentSpanPtr uint64) uint64 {
	if !o.IsInitialized() {
		return 0
	}
	return uint64(C.create_otel_span_with_parent(C.enum_RequestType(requestType), C.uint64_t(parentSpanPtr)))
}

// createBatchSpanWithParent creates a new OpenTelemetry batch span with the given parent span pointer.
// This is an internal method used by batch execution to create child batch spans.
// Uses the dedicated create_batch_otel_span_with_parent FFI function for proper batch span creation.
func (o *OpenTelemetry) createBatchSpanWithParent(parentSpanPtr uint64) uint64 {
	if !o.IsInitialized() {
		return 0
	}

	// Use the dedicated FFI function for creating batch spans with parent context
	return uint64(C.create_batch_otel_span_with_parent(C.uint64_t(parentSpanPtr)))
}

// DropSpan drops an OpenTelemetry span given its pointer.
func (o *OpenTelemetry) dropSpan(spanPtr uint64) {
	if spanPtr == 0 {
		return
	}
	C.drop_otel_span(C.uint64_t(spanPtr))
}

// CreateSpan creates a new OpenTelemetry span with the given name and returns a pointer to the span.
// This is a PUBLIC API for users to create parent spans that can be used with command execution.
//
// Parameters:
//   - name: The name of the span to create
//
// Returns:
//   - uint64: A pointer to the created span (0 on failure)
//   - error: An error if the span creation fails or OpenTelemetry is not initialized
//
// Example usage:
//
//	spanPtr, err := glide.GetOtelInstance().CreateSpan("user-operation")
//	if err != nil {
//		log.Printf("Failed to create span: %v", err)
//		return
//	}
//	defer glide.GetOtelInstance().EndSpan(spanPtr)
//
// Note: The caller is responsible for calling EndSpan() to properly clean up the span.
func (o *OpenTelemetry) CreateSpan(name string) (uint64, error) {
	// Thread-safe check for initialization
	if !o.IsInitialized() {
		return 0, fmt.Errorf("openTelemetry not initialized")
	}

	// Validate input parameters
	if name == "" {
		return 0, fmt.Errorf("span name cannot be empty")
	}

	// Validate name length (reasonable limit to prevent abuse)
	if len(name) > 256 {
		return 0, fmt.Errorf("span name too long (%d chars), maximum 256 characters allowed", len(name))
	}

	// Convert Go string to C string
	cName := C.CString(name)
	defer C.free(unsafe.Pointer(cName))

	// Call FFI function to create named span
	spanPtr := uint64(C.create_named_otel_span(cName))
	if spanPtr == 0 {
		return 0, fmt.Errorf("failed to create span '%s'", name)
	}

	return spanPtr, nil
}

// EndSpan ends and drops an OpenTelemetry span given its pointer.
// This is a PUBLIC API for users to properly clean up spans created with CreateSpan().
//
// Parameters:
//   - spanPtr: A pointer to the span to end (obtained from CreateSpan)
//
// Note: This method is safe to call with a zero pointer (no-op).
// It is the caller's responsibility to ensure the span pointer is valid.
//
// Example usage:
//
//	spanPtr, err := glide.GetOtelInstance().CreateSpan("user-operation")
//	if err != nil {
//		log.Printf("Failed to create span: %v", err)
//		return
//	}
//	defer glide.GetOtelInstance().EndSpan(spanPtr)
func (o *OpenTelemetry) EndSpan(spanPtr uint64) {
	// Safe to call with zero pointer - dropSpan handles this case
	o.dropSpan(spanPtr)
}

// Context Integration Helper Functions

// WithSpan stores a Glide span pointer in a context.Context for later retrieval.
// This is a helper function that users can use to attach span pointers to contexts
// for parent-child span relationships.
//
// Parameters:
//   - ctx: The parent context
//   - spanPtr: A span pointer obtained from CreateSpan()
//
// Returns:
//   - context.Context: A new context containing the span pointer
//
// Example usage:
//
//	spanPtr, err := glide.GetOtelInstance().CreateSpan("user-operation")
//	if err != nil {
//		return err
//	}
//	defer glide.GetOtelInstance().EndSpan(spanPtr)
//
//	// Store span in context
//	ctx = glide.WithSpan(ctx, spanPtr)
//
//	// Now all Glide operations with this context will be child spans
//	result, err := client.Get(ctx, "key")
func WithSpan(ctx context.Context, spanPtr uint64) context.Context {
	return context.WithValue(ctx, SpanContextKey, spanPtr)
}

// DefaultSpanFromContext is a default implementation of the SpanFromContext function
// that extracts span pointers stored using WithSpan().
//
// This function can be used directly in OpenTelemetryConfig or as a reference
// for implementing custom span extraction logic.
//
// Parameters:
//   - ctx: The context to extract the span pointer from
//
// Returns:
//   - spanPtr: The span pointer if found, 0 otherwise
//   - found: true if a valid span pointer was found, false otherwise
//
// Example usage:
//
//	config := glide.OpenTelemetryConfig{
//		Traces: &glide.OpenTelemetryTracesConfig{
//			Endpoint:         "http://localhost:4318/v1/traces",
//			SamplePercentage: 10,
//		},
//		SpanFromContext: glide.DefaultSpanFromContext,
//	}
func DefaultSpanFromContext(ctx context.Context) (uint64, bool) {
	if ctx == nil {
		return 0, false
	}
	if spanPtr, ok := ctx.Value(SpanContextKey).(uint64); ok && spanPtr != 0 {
		return spanPtr, true
	}
	return 0, false
}

// extractSpanPointer is an internal method that safely extracts parent span pointer from context
// using the configured SpanFromContext function. It includes error handling and fallback logic.
func (o *OpenTelemetry) extractSpanPointer(ctx context.Context) (uint64, bool) {
	// Thread-safe access to configuration
	configMutex.RLock()
	spanFromContextFunc := otelConfig.SpanFromContext
	configMutex.RUnlock()

	// If no SpanFromContext function is configured, return no parent span
	if spanFromContextFunc == nil {
		return 0, false
	}

	// Call the user-provided function with panic recovery
	defer func() {
		if r := recover(); r != nil {
			log.Printf("SpanFromContext function panicked: %v, falling back to independent span creation", r)
		}
	}()

	spanPtr, found := spanFromContextFunc(ctx)

	// Validate the returned span pointer
	if found && spanPtr == 0 {
		log.Printf("SpanFromContext returned found=true but spanPtr=0, treating as not found")
		return 0, false
	}

	return spanPtr, found
}

// Span Context Attachment Usage Examples
//
// The following examples demonstrate how to use the SpanFromContext functionality
// to create parent-child span relationships for end-to-end tracing.

/*
Example 1: Basic Parent-Child Span Relationship

	package main

	import (
		"context"
		"log"

		"github.com/valkey-io/valkey-glide/go/v2"
		"github.com/valkey-io/valkey-glide/go/v2/config"
	)

	func main() {
		// Initialize Glide OpenTelemetry with SpanFromContext
		otelConfig := glide.OpenTelemetryConfig{
			Traces: &glide.OpenTelemetryTracesConfig{
				Endpoint:         "http://localhost:4318/v1/traces",
				SamplePercentage: 100, // Sample all requests for demo
			},
			// Use the default span extraction function
			SpanFromContext: glide.DefaultSpanFromContext,
		}

		err := glide.GetOtelInstance().Init(otelConfig)
		if err != nil {
			log.Fatalf("Failed to initialize OpenTelemetry: %v", err)
		}

		// Create Glide client
		clientConfig := &config.GlideClientConfiguration{
			Addresses: []config.NodeAddress{{Host: "localhost", Port: 6379}},
		}

		client, err := glide.NewGlideClient(context.Background(), clientConfig)
		if err != nil {
			log.Fatalf("Failed to create client: %v", err)
		}
		defer client.Close()

		// Create a parent span for the entire operation
		parentSpanPtr, err := glide.GetOtelInstance().CreateSpan("user-operation")
		if err != nil {
			log.Printf("Failed to create span: %v", err)
			return
		}
		defer glide.GetOtelInstance().EndSpan(parentSpanPtr)

		// Store the parent span in context
		ctx := glide.WithSpan(context.Background(), parentSpanPtr)

		// All Glide operations will now be children of the parent span
		result, err := client.Get(ctx, "user:123:profile")
		if err != nil {
			log.Printf("Error: %v", err)
		} else {
			log.Printf("Profile: %v", result.Value())
		}

		// Another child operation
		err = client.Set(ctx, "user:123:last_seen", "2024-01-15T10:30:00Z")
		if err != nil {
			log.Printf("Error: %v", err)
		}
	}

Example 2: Custom SpanFromContext Implementation

	// Custom context key type for type safety
	type spanContextKey struct{}

	// Custom span extraction function
	func customSpanFromContext(ctx context.Context) (uint64, bool) {
		if spanPtr, ok := ctx.Value(spanContextKey{}).(uint64); ok && spanPtr != 0 {
			return spanPtr, true
		}
		return 0, false
	}

	// Custom helper function to store spans
	func withCustomSpan(ctx context.Context, spanPtr uint64) context.Context {
		return context.WithValue(ctx, spanContextKey{}, spanPtr)
	}

	func main() {
		// Initialize with custom SpanFromContext function
		otelConfig := glide.OpenTelemetryConfig{
			Traces: &glide.OpenTelemetryTracesConfig{
				Endpoint:         "http://localhost:4318/v1/traces",
				SamplePercentage: 10,
			},
			SpanFromContext: customSpanFromContext,
		}

		// ... rest of initialization ...

		// Use custom helper function
		ctx := withCustomSpan(context.Background(), parentSpanPtr)

		// Operations will use the custom context extraction
		result, err := client.Get(ctx, "key")
	}

Example 3: Batch Operations with Parent Context

	func processBatchOperations(ctx context.Context, client *glide.GlideClient, userID string) error {
		// Create a parent span for the batch operation
		batchSpanPtr, err := glide.GetOtelInstance().CreateSpan("batch-user-operations")
		if err != nil {
			return err
		}
		defer glide.GetOtelInstance().EndSpan(batchSpanPtr)

		// Store the batch span in context
		batchCtx := glide.WithSpan(ctx, batchSpanPtr)

		// Create a batch of operations
		batch := glide.NewBatch()
		batch.Incr("user:" + userID + ":login_count")
		batch.SAdd("active_users", userID)
		batch.Expire("user:" + userID + ":session", 3600)

		// Execute batch - all operations will be children of "batch-user-operations"
		results, err := client.Exec(batchCtx, batch, false)
		if err != nil {
			return err
		}

		log.Printf("Batch results: %v", results)
		return nil
	}

Example 4: Error Handling and Fallback Behavior

	// SpanFromContext function with error handling
	func robustSpanFromContext(ctx context.Context) (uint64, bool) {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Panic in SpanFromContext: %v", r)
			}
		}()

		// Multiple extraction strategies
		if spanPtr, ok := ctx.Value(glide.SpanContextKey).(uint64); ok && spanPtr != 0 {
			return spanPtr, true
		}

		// Define the same key type for extraction
		type parentSpanKey struct{}
		if spanPtr, ok := ctx.Value(parentSpanKey{}).(uint64); ok && spanPtr != 0 {
			return spanPtr, true
		}

		return 0, false
	}

	func main() {
		otelConfig := glide.OpenTelemetryConfig{
			Traces: &glide.OpenTelemetryTracesConfig{
				Endpoint:         "http://localhost:4318/v1/traces",
				SamplePercentage: 5,
			},
			SpanFromContext: robustSpanFromContext,
		}

		// ... initialization ...

		// Even if SpanFromContext fails, operations will continue with independent spans
		result, err := client.Get(context.Background(), "key")
	}

Expected Trace Hierarchy:

With proper span context attachment, your exported traces will show hierarchical relationships:

	user-operation (parent span)
	├── GET user:123:profile (child span)
	└── SET user:123:last_seen (child span)
	batch-user-operations (parent span)
	└── Batch (child span)
	    ├── INCR user:123:login_count (child span)
	    ├── SADD active_users (child span)
	    └── EXPIRE user:123:session (child span)

This provides complete end-to-end visibility of how database operations fit within
larger business operation contexts.

Configuration Validation:

The SpanFromContext function is optional and backward compatible:
- If not provided, Glide creates independent spans (current behavior)
- If provided but returns found=false, Glide creates independent spans
- If the function panics, Glide logs the error and creates independent spans
- Invalid span pointers (0 when found=true) are treated as not found

Thread Safety:

The SpanFromContext function must be thread-safe as it may be called concurrently
from multiple goroutines. The function should not modify shared state without
proper synchronization.
*/
