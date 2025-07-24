// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"testing"
)

// TestSpanFromContextConfiguration tests the SpanFromContext configuration functionality
func TestSpanFromContextConfiguration(t *testing.T) {
	// Test 1: DefaultSpanFromContext with valid span
	ctx := WithSpan(context.Background(), 12345)
	spanPtr, found := DefaultSpanFromContext(ctx)
	if !found || spanPtr != 12345 {
		t.Errorf("DefaultSpanFromContext failed: expected (12345, true), got (%d, %t)", spanPtr, found)
	}

	// Test 2: DefaultSpanFromContext with no span
	emptyCtx := context.Background()
	spanPtr, found = DefaultSpanFromContext(emptyCtx)
	if found || spanPtr != 0 {
		t.Errorf("DefaultSpanFromContext with empty context failed: expected (0, false), got (%d, %t)", spanPtr, found)
	}

	// Test 3: DefaultSpanFromContext with zero span (should be treated as not found)
	zeroCtx := WithSpan(context.Background(), 0)
	spanPtr, found = DefaultSpanFromContext(zeroCtx)
	if found || spanPtr != 0 {
		t.Errorf("DefaultSpanFromContext with zero span failed: expected (0, false), got (%d, %t)", spanPtr, found)
	}
}

// TestWithSpanHelper tests the WithSpan helper function
func TestWithSpanHelper(t *testing.T) {
	originalCtx := context.Background()
	spanPtr := uint64(54321)
	
	// Store span in context
	newCtx := WithSpan(originalCtx, spanPtr)
	
	// Retrieve span from context
	retrievedSpan, found := DefaultSpanFromContext(newCtx)
	if !found || retrievedSpan != spanPtr {
		t.Errorf("WithSpan helper failed: expected (%d, true), got (%d, %t)", spanPtr, retrievedSpan, found)
	}
	
	// Original context should not have the span
	_, found = DefaultSpanFromContext(originalCtx)
	if found {
		t.Error("Original context should not have span after WithSpan")
	}
}

// TestExtractSpanPointerWithNilConfig tests extractSpanPointer when no SpanFromContext is configured
func TestExtractSpanPointerWithNilConfig(t *testing.T) {
	// Save original config
	originalConfig := otelConfig
	defer func() { otelConfig = originalConfig }()
	
	// Set config without SpanFromContext
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: nil,
	}
	
	otelInstance := GetOtelInstance()
	ctx := WithSpan(context.Background(), 12345)
	
	spanPtr, found := otelInstance.extractSpanPointer(ctx)
	if found || spanPtr != 0 {
		t.Errorf("extractSpanPointer with nil config should return (0, false), got (%d, %t)", spanPtr, found)
	}
}

// TestExtractSpanPointerWithValidConfig tests extractSpanPointer with a configured SpanFromContext function
func TestExtractSpanPointerWithValidConfig(t *testing.T) {
	// Save original config
	originalConfig := otelConfig
	defer func() { otelConfig = originalConfig }()
	
	// Set config with SpanFromContext
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: DefaultSpanFromContext,
	}
	
	otelInstance := GetOtelInstance()
	
	// Test with valid span
	ctx := WithSpan(context.Background(), 98765)
	spanPtr, found := otelInstance.extractSpanPointer(ctx)
	if !found || spanPtr != 98765 {
		t.Errorf("extractSpanPointer with valid span should return (98765, true), got (%d, %t)", spanPtr, found)
	}
	
	// Test with no span
	emptyCtx := context.Background()
	spanPtr, found = otelInstance.extractSpanPointer(emptyCtx)
	if found || spanPtr != 0 {
		t.Errorf("extractSpanPointer with empty context should return (0, false), got (%d, %t)", spanPtr, found)
	}
}

// TestExtractSpanPointerPanicRecovery tests that extractSpanPointer handles panics gracefully
func TestExtractSpanPointerPanicRecovery(t *testing.T) {
	// Save original config
	originalConfig := otelConfig
	defer func() { otelConfig = originalConfig }()
	
	// Set config with panicking SpanFromContext function
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: func(ctx context.Context) (uint64, bool) {
			panic("test panic")
		},
	}
	
	otelInstance := GetOtelInstance()
	ctx := context.Background()
	
	// This should not panic and should return (0, false)
	spanPtr, found := otelInstance.extractSpanPointer(ctx)
	if found || spanPtr != 0 {
		t.Errorf("extractSpanPointer with panicking function should return (0, false), got (%d, %t)", spanPtr, found)
	}
}

// TestExtractSpanPointerInvalidSpanValidation tests validation of invalid span pointers
func TestExtractSpanPointerInvalidSpanValidation(t *testing.T) {
	// Save original config
	originalConfig := otelConfig
	defer func() { otelConfig = originalConfig }()
	
	// Set config with function that returns found=true but spanPtr=0
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: func(ctx context.Context) (uint64, bool) {
			return 0, true // Invalid: found=true but spanPtr=0
		},
	}
	
	otelInstance := GetOtelInstance()
	ctx := context.Background()
	
	// This should be treated as not found
	spanPtr, found := otelInstance.extractSpanPointer(ctx)
	if found || spanPtr != 0 {
		t.Errorf("extractSpanPointer with invalid span (0, true) should return (0, false), got (%d, %t)", spanPtr, found)
	}
}

// TestCustomSpanFromContextImplementation tests a custom SpanFromContext implementation
func TestCustomSpanFromContextImplementation(t *testing.T) {
	// Custom context key
	type customSpanKey struct{}
	
	// Custom SpanFromContext function
	customSpanFromContext := func(ctx context.Context) (uint64, bool) {
		if spanPtr, ok := ctx.Value(customSpanKey{}).(uint64); ok && spanPtr != 0 {
			return spanPtr, true
		}
		return 0, false
	}
	
	// Save original config
	originalConfig := otelConfig
	defer func() { otelConfig = originalConfig }()
	
	// Set config with custom function
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: customSpanFromContext,
	}
	
	otelInstance := GetOtelInstance()
	
	// Test with custom context
	ctx := context.WithValue(context.Background(), customSpanKey{}, uint64(11111))
	spanPtr, found := otelInstance.extractSpanPointer(ctx)
	if !found || spanPtr != 11111 {
		t.Errorf("extractSpanPointer with custom implementation should return (11111, true), got (%d, %t)", spanPtr, found)
	}
	
	// Test that default context key doesn't work with custom function
	defaultCtx := WithSpan(context.Background(), 22222)
	spanPtr, found = otelInstance.extractSpanPointer(defaultCtx)
	if found || spanPtr != 0 {
		t.Errorf("extractSpanPointer with custom implementation should not find default span, got (%d, %t)", spanPtr, found)
	}
}
