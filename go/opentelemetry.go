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
	"strings"
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
//			SpanFromContext: func(ctx context.Context) uint64 {
//				// Extract span pointer from context for parent-child span relationships
//				if spanPtr, ok := ctx.Value(glide.SpanContextKey).(uint64); ok && spanPtr != 0 {
//					return spanPtr
//				}
//				return 0
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
	//
	// If this function is not provided or returns 0, Glide will create independent spans
	// as it currently does. If the function panics, Glide will gracefully fallback to creating
	// independent spans.
	//
	// Example implementation:
	//   SpanFromContext: func(ctx context.Context) uint64 {
	//       if spanPtr, ok := ctx.Value(glide.SpanContextKey).(uint64); ok && spanPtr != 0 {
	//           return spanPtr
	//       }
	//       return 0
	//   }
	//
	// Note: This function must be thread-safe as it may be called concurrently from multiple goroutines.
	SpanFromContext func(ctx context.Context) (spanPtr uint64)
}

// SpanContext represents a remote OpenTelemetry span context.
type SpanContext struct {
	TraceID    string
	SpanID     string
	TraceFlags byte
	TraceState string
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
	otelInstance         *OpenTelemetry
	otelConfig           *OpenTelemetryConfig
	otelInitialized      bool = false
	spanContextExtractor func(context.Context) (SpanContext, bool)
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

type spanParentSource int

const (
	spanParentNone spanParentSource = iota
	spanParentRemoteContext
	spanParentPointer
)

func (o *OpenTelemetry) selectSpanParent(ctx context.Context) (spanParentSource, SpanContext, uint64) {
	if spanContext, ok := o.extractRemoteSpanContext(ctx); ok {
		return spanParentRemoteContext, spanContext, 0
	}
	if parentSpanPtr := o.extractSpanPointer(ctx); parentSpanPtr != 0 {
		return spanParentPointer, SpanContext{}, parentSpanPtr
	}
	return spanParentNone, SpanContext{}, 0
}

func (o *OpenTelemetry) createSpanWithRemoteContext(requestType C.RequestType, spanContext SpanContext) uint64 {
	if !o.IsInitialized() {
		return 0
	}

	traceID, spanID, traceState, cleanup := spanContext.toCStrings()
	defer cleanup()

	return uint64(C.create_otel_span_with_trace_context(
		C.enum_RequestType(requestType),
		traceID,
		spanID,
		C.uint8_t(spanContext.TraceFlags),
		traceState,
	))
}

func (o *OpenTelemetry) createBatchSpanWithRemoteContext(spanContext SpanContext) uint64 {
	if !o.IsInitialized() {
		return 0
	}

	traceID, spanID, traceState, cleanup := spanContext.toCStrings()
	defer cleanup()

	return uint64(C.create_batch_otel_span_with_trace_context(
		traceID,
		spanID,
		C.uint8_t(spanContext.TraceFlags),
		traceState,
	))
}

func (spanContext SpanContext) toCStrings() (*C.char, *C.char, *C.char, func()) {
	traceID := C.CString(spanContext.TraceID)
	spanID := C.CString(spanContext.SpanID)

	var traceState *C.char
	if spanContext.TraceState != "" {
		traceState = C.CString(spanContext.TraceState)
	}

	cleanup := func() {
		C.free(unsafe.Pointer(traceID))
		C.free(unsafe.Pointer(spanID))
		if traceState != nil {
			C.free(unsafe.Pointer(traceState))
		}
	}

	return traceID, spanID, traceState, cleanup
}

func (o *OpenTelemetry) createCommandSpanForContext(ctx context.Context, requestType C.RequestType) uint64 {
	source, spanContext, parentSpanPtr := o.selectSpanParent(ctx)
	switch source {
	case spanParentRemoteContext:
		return o.createSpanWithRemoteContext(requestType, spanContext)
	case spanParentPointer:
		return o.createSpanWithParent(requestType, parentSpanPtr)
	default:
		return o.createSpan(requestType)
	}
}

func (o *OpenTelemetry) createBatchSpanForContext(ctx context.Context) uint64 {
	source, spanContext, parentSpanPtr := o.selectSpanParent(ctx)
	switch source {
	case spanParentRemoteContext:
		return o.createBatchSpanWithRemoteContext(spanContext)
	case spanParentPointer:
		return o.createBatchSpanWithParent(parentSpanPtr)
	default:
		return o.createBatchSpan()
	}
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
//   - spanPtr: The span pointer if found, 0 if no parent span is available
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
func DefaultSpanFromContext(ctx context.Context) uint64 {
	if ctx == nil {
		return 0
	}
	if spanPtr, ok := ctx.Value(SpanContextKey).(uint64); ok && spanPtr != 0 {
		return spanPtr
	}
	return 0
}

// SetSpanContextExtractor registers or clears the callback used to extract an external parent span context.
// The callback is invoked synchronously before each sampled GLIDE command or batch span.
// Pass nil to clear the extractor.
//
// Example with go.opentelemetry.io/otel:
//
//	glide.GetOtelInstance().SetSpanContextExtractor(func(ctx context.Context) (glide.SpanContext, bool) {
//		spanContext := trace.SpanContextFromContext(ctx)
//		if !spanContext.IsValid() {
//			return glide.SpanContext{}, false
//		}
//
//		return glide.SpanContext{
//			TraceID:    spanContext.TraceID().String(),
//			SpanID:     spanContext.SpanID().String(),
//			TraceFlags: byte(spanContext.TraceFlags()),
//			TraceState: spanContext.TraceState().String(),
//		}, true
//	})
func (o *OpenTelemetry) SetSpanContextExtractor(fn func(context.Context) (SpanContext, bool)) {
	configMutex.Lock()
	defer configMutex.Unlock()
	spanContextExtractor = fn
}

func (o *OpenTelemetry) extractRemoteSpanContext(ctx context.Context) (spanContext SpanContext, ok bool) {
	configMutex.RLock()
	extractor := spanContextExtractor
	configMutex.RUnlock()

	if extractor == nil {
		return SpanContext{}, false
	}

	defer func() {
		if r := recover(); r != nil {
			log.Printf("SpanContextExtractor function panicked: %v, falling back to existing span creation", r)
			spanContext = SpanContext{}
			ok = false
		}
	}()

	spanContext, ok = extractor(ctx)
	if !ok {
		return SpanContext{}, false
	}

	normalized, valid := normalizeSpanContext(spanContext)
	if !valid {
		return SpanContext{}, false
	}

	return normalized, true
}

func normalizeSpanContext(spanContext SpanContext) (SpanContext, bool) {
	traceID := strings.ToLower(spanContext.TraceID)
	spanID := strings.ToLower(spanContext.SpanID)

	if !isValidHexID(traceID, 32) || traceID == "00000000000000000000000000000000" {
		return SpanContext{}, false
	}
	if !isValidHexID(spanID, 16) || spanID == "0000000000000000" {
		return SpanContext{}, false
	}
	if spanContext.TraceState != "" && !isValidTraceState(spanContext.TraceState) {
		return SpanContext{}, false
	}

	spanContext.TraceID = traceID
	spanContext.SpanID = spanID
	return spanContext, true
}

func isValidHexID(value string, expectedLength int) bool {
	if len(value) != expectedLength {
		return false
	}
	for _, char := range value {
		if (char < '0' || char > '9') && (char < 'a' || char > 'f') {
			return false
		}
	}
	return true
}

const (
	maxTraceStateListMembers     = 32
	maxTraceStateSimpleKeyLength = 256
	maxTraceStateTenantIDLength  = 241
	maxTraceStateSystemIDLength  = 14
	maxTraceStateValueLength     = 256
)

// isValidTraceState validates remote tracestate before passing it through FFI so
// invalid remote contexts can still fall back to span-pointer parents.
func isValidTraceState(traceState string) bool {
	if traceState == "" {
		return true
	}

	listMembers := strings.Split(traceState, ",")
	if len(listMembers) > maxTraceStateListMembers {
		return false
	}

	keys := make(map[string]struct{}, len(listMembers))
	for _, listMember := range listMembers {
		listMember = strings.Trim(listMember, " \t")
		if listMember == "" {
			continue
		}

		key, value, ok := strings.Cut(listMember, "=")
		if !ok {
			return false
		}

		if !isValidTraceStateKey(key) || !isValidTraceStateValue(value) {
			return false
		}
		if _, exists := keys[key]; exists {
			return false
		}
		keys[key] = struct{}{}
	}
	return true
}

func isValidTraceStateKey(key string) bool {
	tenantID, systemID, hasTenantID := strings.Cut(key, "@")
	if hasTenantID {
		return isValidTraceStateTenantID(tenantID) && isValidTraceStateSystemID(systemID)
	}
	return isValidTraceStateSimpleKey(key)
}

func isValidTraceStateSimpleKey(key string) bool {
	return isValidTraceStateKeyPart(key, maxTraceStateSimpleKeyLength, false)
}

func isValidTraceStateTenantID(tenantID string) bool {
	return isValidTraceStateKeyPart(tenantID, maxTraceStateTenantIDLength, true)
}

func isValidTraceStateSystemID(systemID string) bool {
	return isValidTraceStateKeyPart(systemID, maxTraceStateSystemIDLength, false)
}

func isValidTraceStateKeyPart(part string, maxLength int, firstCanBeDigit bool) bool {
	if len(part) == 0 || len(part) > maxLength {
		return false
	}
	if !isTraceStateLowercaseAlpha(part[0]) && (!firstCanBeDigit || !isTraceStateDigit(part[0])) {
		return false
	}
	for i := 1; i < len(part); i++ {
		if !isTraceStateKeyChar(part[i]) {
			return false
		}
	}
	return true
}

func isTraceStateKeyChar(char byte) bool {
	return isTraceStateLowercaseAlpha(char) || isTraceStateDigit(char) || char == '_' || char == '-' || char == '*' ||
		char == '/'
}

func isTraceStateLowercaseAlpha(char byte) bool {
	return char >= 'a' && char <= 'z'
}

func isTraceStateDigit(char byte) bool {
	return char >= '0' && char <= '9'
}

func isValidTraceStateValue(value string) bool {
	if len(value) == 0 || len(value) > maxTraceStateValueLength {
		return false
	}

	for i := 0; i < len(value)-1; i++ {
		if !isValidTraceStateValueChar(value[i]) {
			return false
		}
	}
	return isValidTraceStateValueLast(value[len(value)-1])
}

func isValidTraceStateValueChar(char byte) bool {
	return char >= 0x20 && char <= 0x7e && char != ',' && char != '='
}

func isValidTraceStateValueLast(char byte) bool {
	return char >= 0x21 && char <= 0x7e && char != ',' && char != '='
}

// extractSpanPointer is an internal method that safely extracts parent span pointer from context
// using the configured SpanFromContext function. It includes error handling and fallback logic.
func (o *OpenTelemetry) extractSpanPointer(ctx context.Context) uint64 {
	// Thread-safe access to configuration
	configMutex.RLock()
	var spanFromContextFunc func(context.Context) uint64
	if otelConfig != nil {
		spanFromContextFunc = otelConfig.SpanFromContext
	}
	configMutex.RUnlock()

	// If no SpanFromContext function is configured, return no parent span
	if spanFromContextFunc == nil {
		return 0
	}

	// Call the user-provided function with panic recovery
	var spanPtr uint64
	func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("SpanFromContext function panicked: %v, falling back to independent span creation", r)
				spanPtr = 0
			}
		}()
		spanPtr = spanFromContextFunc(ctx)
	}()

	return spanPtr
}

// For runnable examples demonstrating OpenTelemetry usage, see the examples in opentelemetry_examples_test.go
