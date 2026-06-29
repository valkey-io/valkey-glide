// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestWithSpan_ContextStorage tests the WithSpan helper function
func TestWithSpan_ContextStorage(t *testing.T) {
	// Test storing and retrieving span from context
	originalCtx := context.Background()
	spanPtr := uint64(12345)

	// Store span in context
	ctxWithSpan := WithSpan(originalCtx, spanPtr)

	// Verify span is stored correctly
	retrievedSpan, ok := ctxWithSpan.Value(SpanContextKey).(uint64)
	assert.True(t, ok, "Should be able to retrieve span from context")
	assert.Equal(t, spanPtr, retrievedSpan, "Retrieved span should match stored span")

	// Test with zero span pointer
	ctxWithZeroSpan := WithSpan(originalCtx, 0)
	retrievedZeroSpan, ok := ctxWithZeroSpan.Value(SpanContextKey).(uint64)
	assert.True(t, ok, "Should be able to retrieve zero span from context")
	assert.Equal(t, uint64(0), retrievedZeroSpan, "Retrieved zero span should be zero")
}

// TestDefaultSpanFromContext tests the DefaultSpanFromContext function
func TestDefaultSpanFromContext(t *testing.T) {
	testCases := []struct {
		name         string
		setupContext func() context.Context
		expectedSpan uint64
	}{
		{
			name: "context with valid span",
			setupContext: func() context.Context {
				return WithSpan(context.Background(), 12345)
			},
			expectedSpan: 12345,
		},
		{
			name: "context with zero span",
			setupContext: func() context.Context {
				return WithSpan(context.Background(), 0)
			},
			expectedSpan: 0, // Zero spans are treated as not found
		},
		{
			name: "context without span",
			setupContext: func() context.Context {
				return context.Background()
			},
			expectedSpan: 0,
		},
		{
			name: "context with wrong type",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, "not-a-uint64")
			},
			expectedSpan: 0,
		},
		{
			name: "context with different key",
			setupContext: func() context.Context {
				type differentKeyType struct{}
				return context.WithValue(context.Background(), differentKeyType{}, uint64(12345))
			},
			expectedSpan: 0,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			ctx := tc.setupContext()
			spanPtr := DefaultSpanFromContext(ctx)

			assert.Equal(t, tc.expectedSpan, spanPtr, "Span pointer should match expected")
		})
	}
}

// TestSpanFromContext_Configuration tests SpanFromContext configuration
func TestSpanFromContext_Configuration(t *testing.T) {
	// Test custom SpanFromContext function
	type customSpanKeyType struct{}
	customSpanFromContext := func(ctx context.Context) uint64 {
		if spanPtr, ok := ctx.Value(customSpanKeyType{}).(uint64); ok && spanPtr != 0 {
			return spanPtr
		}
		return 0
	}

	// Test that the function can be configured (we can't actually test the full flow
	// without reinitializing OpenTelemetry, but we can test the function itself)
	ctx := context.WithValue(context.Background(), customSpanKeyType{}, uint64(54321))
	spanPtr := customSpanFromContext(ctx)

	assert.Equal(t, uint64(54321), spanPtr)

	// Test with missing key
	emptyCtx := context.Background()
	spanPtr = customSpanFromContext(emptyCtx)

	assert.Equal(t, uint64(0), spanPtr)
}

// TestSpanContextKey_Constant tests the SpanContextKey variable
func TestSpanContextKey_Constant(t *testing.T) {
	// Test that the key can be used as a context key
	ctx := context.WithValue(context.Background(), SpanContextKey, uint64(12345))
	value, ok := ctx.Value(SpanContextKey).(uint64)
	assert.True(t, ok)
	assert.Equal(t, uint64(12345), value)
}

// TestContextHelperFunctions tests the context helper functions
func TestContextHelperFunctions(t *testing.T) {
	// Test WithSpan function
	originalCtx := context.Background()
	spanPtr := uint64(98765)

	ctxWithSpan := WithSpan(originalCtx, spanPtr)

	// Verify the span is stored correctly
	storedSpan := DefaultSpanFromContext(ctxWithSpan)
	assert.Equal(t, spanPtr, storedSpan)

	// Test context inheritance
	childCtx, cancel := context.WithTimeout(ctxWithSpan, time.Second)
	defer cancel()

	// Child context should inherit the span
	childSpan := DefaultSpanFromContext(childCtx)
	assert.Equal(t, spanPtr, childSpan)
}

// TestSpanFromContext_EdgeCases tests edge cases in span context extraction
func TestSpanFromContext_EdgeCases(t *testing.T) {
	testCases := []struct {
		name         string
		setupContext func() context.Context
		expectedSpan uint64
	}{
		{
			name: "nil context",
			setupContext: func() context.Context {
				return nil
			},
			expectedSpan: 0,
		},
		{
			name: "context with nil value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, nil)
			},
			expectedSpan: 0,
		},
		{
			name: "context with string value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, "12345")
			},
			expectedSpan: 0,
		},
		{
			name: "context with int value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, 12345)
			},
			expectedSpan: 0,
		},
		{
			name: "context with float value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, 12345.0)
			},
			expectedSpan: 0,
		},
		{
			name: "context with maximum uint64",
			setupContext: func() context.Context {
				return WithSpan(context.Background(), ^uint64(0))
			},
			expectedSpan: ^uint64(0),
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			var ctx context.Context
			if tc.setupContext != nil {
				ctx = tc.setupContext()
			}

			// Handle nil context case
			if ctx == nil {
				// DefaultSpanFromContext should handle nil context gracefully
				require.NotPanics(t, func() {
					spanPtr := DefaultSpanFromContext(ctx)
					assert.Equal(t, tc.expectedSpan, spanPtr)
				})
				return
			}

			spanPtr := DefaultSpanFromContext(ctx)
			assert.Equal(t, tc.expectedSpan, spanPtr)
		})
	}
}

// TestSpanFromContext_CustomImplementations tests custom SpanFromContext implementations
func TestSpanFromContext_CustomImplementations(t *testing.T) {
	// Test multiple key strategy
	type primarySpanKeyType struct{}
	type secondarySpanKeyType struct{}

	multiKeySpanFromContext := func(ctx context.Context) uint64 {
		// Try multiple keys in order of preference
		keys := []interface{}{primarySpanKeyType{}, secondarySpanKeyType{}, SpanContextKey}

		for _, key := range keys {
			if spanPtr, ok := ctx.Value(key).(uint64); ok && spanPtr != 0 {
				return spanPtr
			}
		}
		return 0
	}

	// Test with primary key
	ctx1 := context.WithValue(context.Background(), primarySpanKeyType{}, uint64(11111))
	spanPtr := multiKeySpanFromContext(ctx1)
	assert.Equal(t, uint64(11111), spanPtr)

	// Test with secondary key (primary not present)
	ctx2 := context.WithValue(context.Background(), secondarySpanKeyType{}, uint64(22222))
	spanPtr = multiKeySpanFromContext(ctx2)
	assert.Equal(t, uint64(22222), spanPtr)

	// Test with default key (others not present)
	ctx3 := WithSpan(context.Background(), 33333)
	spanPtr = multiKeySpanFromContext(ctx3)
	assert.Equal(t, uint64(33333), spanPtr)

	// Test with no keys present
	ctx4 := context.Background()
	spanPtr = multiKeySpanFromContext(ctx4)
	assert.Equal(t, uint64(0), spanPtr)
}

// TestSpanFromContext_ErrorRecovery tests error recovery in SpanFromContext functions
func TestSpanFromContext_ErrorRecovery(t *testing.T) {
	// Test function that panics
	panicSpanFromContext := func(ctx context.Context) uint64 {
		panic("intentional panic for testing")
	}

	// Test panic recovery
	require.NotPanics(t, func() {
		defer func() {
			if r := recover(); r != nil {
				// Panic should be recovered
				t.Logf("Panic recovered as expected: %v", r)
			}
		}()

		_ = panicSpanFromContext(context.Background())
	})
}

func TestSpanContextExtractor_SetReplaceClear(t *testing.T) {
	otel := GetOtelInstance()
	otel.SetSpanContextExtractor(nil)
	t.Cleanup(func() {
		otel.SetSpanContextExtractor(nil)
	})

	first := SpanContext{
		TraceID:    "0af7651916cd43dd8448eb211c80319c",
		SpanID:     "b7ad6b7169203331",
		TraceFlags: 1,
	}
	second := SpanContext{
		TraceID:    "4bf92f3577b34da6a3ce929d0e0e4736",
		SpanID:     "00f067aa0ba902b7",
		TraceFlags: 0,
	}

	otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
		return first, true
	})
	got, ok := otel.extractRemoteSpanContext(context.Background())
	require.True(t, ok)
	assert.Equal(t, first, got)

	otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
		return second, true
	})
	got, ok = otel.extractRemoteSpanContext(context.Background())
	require.True(t, ok)
	assert.Equal(t, second, got)

	otel.SetSpanContextExtractor(nil)
	_, ok = otel.extractRemoteSpanContext(context.Background())
	assert.False(t, ok)
}

func TestSpanContextExtractor_InvalidContextReturnsFalse(t *testing.T) {
	otel := GetOtelInstance()
	t.Cleanup(func() {
		otel.SetSpanContextExtractor(nil)
	})

	testCases := []struct {
		name string
		ctx  SpanContext
	}{
		{
			name: "invalid trace id length",
			ctx:  SpanContext{TraceID: "abc", SpanID: "b7ad6b7169203331", TraceFlags: 1},
		},
		{
			name: "zero trace id",
			ctx: SpanContext{
				TraceID:    "00000000000000000000000000000000",
				SpanID:     "b7ad6b7169203331",
				TraceFlags: 1,
			},
		},
		{
			name: "invalid span id length",
			ctx:  SpanContext{TraceID: "0af7651916cd43dd8448eb211c80319c", SpanID: "abc", TraceFlags: 1},
		},
		{
			name: "zero span id",
			ctx: SpanContext{
				TraceID:    "0af7651916cd43dd8448eb211c80319c",
				SpanID:     "0000000000000000",
				TraceFlags: 1,
			},
		},
		{
			name: "invalid hex",
			ctx: SpanContext{
				TraceID:    "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
				SpanID:     "b7ad6b7169203331",
				TraceFlags: 1,
			},
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
				return tc.ctx, true
			})

			_, ok := otel.extractRemoteSpanContext(context.Background())
			assert.False(t, ok)
		})
	}
}

func TestSpanContextExtractor_NormalizesUppercaseHex(t *testing.T) {
	otel := GetOtelInstance()
	t.Cleanup(func() {
		otel.SetSpanContextExtractor(nil)
	})

	otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
		return SpanContext{
			TraceID:    "0AF7651916CD43DD8448EB211C80319C",
			SpanID:     "B7AD6B7169203331",
			TraceFlags: 1,
		}, true
	})

	got, ok := otel.extractRemoteSpanContext(context.Background())
	require.True(t, ok)
	assert.Equal(t, "0af7651916cd43dd8448eb211c80319c", got.TraceID)
	assert.Equal(t, "b7ad6b7169203331", got.SpanID)
}

func TestTraceStateKeyValidation(t *testing.T) {
	testCases := []struct {
		name  string
		key   string
		valid bool
	}{
		{name: "simple key", key: "vendor", valid: true},
		{name: "empty key", key: "", valid: false},
		{name: "simple key starts with digit", key: "1vendor", valid: false},
		{name: "simple key with uppercase", key: "Vendor", valid: false},
		{name: "simple key with unsupported character", key: "vendor.name", valid: false},
		{name: "simple key with 256 characters", key: "a" + strings.Repeat("b", 255), valid: true},
		{name: "simple key with 257 characters", key: "a" + strings.Repeat("b", 256), valid: false},
		{name: "tenant id starts with digit", key: "1tenant@vendor", valid: true},
		{name: "tenant id with 241 characters", key: "a" + strings.Repeat("b", 240) + "@vendor", valid: true},
		{name: "tenant id with 242 characters", key: "a" + strings.Repeat("b", 241) + "@vendor", valid: false},
		{name: "system id starts with digit", key: "tenant@1vendor", valid: false},
		{name: "system id with fourteen characters", key: "tenant@abcdefghijklmn", valid: true},
		{name: "system id with fifteen characters", key: "tenant@abcdefghijklmno", valid: false},
		{name: "empty system id", key: "tenant@", valid: false},
		{name: "multiple tenant separators", key: "tenant@vendor@other", valid: false},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.valid, isValidTraceStateKey(tc.key))
		})
	}
}

func TestTraceStateValidation(t *testing.T) {
	testCases := []struct {
		name       string
		traceState string
		valid      bool
	}{
		{name: "thirty two list members", traceState: traceStateWithListMembers(32), valid: true},
		{name: "thirty three list members", traceState: traceStateWithListMembers(33), valid: false},
		{name: "spaces and tabs around list members", traceState: "vendor=value, \tother=opaque\t ,third=value", valid: true},
		{name: "empty list members", traceState: "vendor=value,, \t ,other=opaque", valid: true},
		{name: "duplicate keys", traceState: "vendor=value,other=opaque,vendor=new", valid: false},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.valid, isValidTraceState(tc.traceState))
		})
	}
}

func traceStateWithListMembers(count int) string {
	members := make([]string, count)
	for i := range members {
		members[i] = "v" + strconv.Itoa(i) + "=value"
	}
	return strings.Join(members, ",")
}

func TestTraceStateValueValidation(t *testing.T) {
	testCases := []struct {
		name  string
		value string
		valid bool
	}{
		{name: "opaque value", value: "opaqueValue1", valid: true},
		{name: "leading space with nonblank terminator", value: " opaque", valid: true},
		{name: "empty value", value: "", valid: false},
		{name: "space only value", value: " ", valid: false},
		{name: "trailing space", value: "opaque ", valid: false},
		{name: "tab character", value: "opaque\tvalue", valid: false},
		{name: "comma", value: "opaque,value", valid: false},
		{name: "equals", value: "opaque=value", valid: false},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			assert.Equal(t, tc.valid, isValidTraceStateValue(tc.value))
		})
	}
}

func TestSpanContextExtractor_PanicRecovery(t *testing.T) {
	otel := GetOtelInstance()
	t.Cleanup(func() {
		otel.SetSpanContextExtractor(nil)
	})

	otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
		panic("boom")
	})

	require.NotPanics(t, func() {
		_, ok := otel.extractRemoteSpanContext(context.Background())
		assert.False(t, ok)
	})
}

func TestSpanCreationSelection_Precedence(t *testing.T) {
	otel := GetOtelInstance()
	t.Cleanup(func() {
		otel.SetSpanContextExtractor(nil)
		otelConfig = nil
	})

	otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
		return SpanContext{
			TraceID:    "0af7651916cd43dd8448eb211c80319c",
			SpanID:     "b7ad6b7169203331",
			TraceFlags: 1,
		}, true
	})
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: func(context.Context) uint64 {
			return 12345
		},
	}

	source, spanContext, parentSpanPtr := otel.selectSpanParent(context.Background())
	assert.Equal(t, spanParentRemoteContext, source)
	assert.Equal(t, "0af7651916cd43dd8448eb211c80319c", spanContext.TraceID)
	assert.Equal(t, uint64(0), parentSpanPtr)

	otel.SetSpanContextExtractor(nil)
	source, _, parentSpanPtr = otel.selectSpanParent(context.Background())
	assert.Equal(t, spanParentPointer, source)
	assert.Equal(t, uint64(12345), parentSpanPtr)

	otelConfig = nil
	source, _, parentSpanPtr = otel.selectSpanParent(context.Background())
	assert.Equal(t, spanParentNone, source)
	assert.Equal(t, uint64(0), parentSpanPtr)
}

func TestSpanCreationSelection_InvalidTraceStateFallsBackToPointer(t *testing.T) {
	otel := GetOtelInstance()
	const fallbackSpanPtr uint64 = 12345
	t.Cleanup(func() {
		otel.SetSpanContextExtractor(nil)
		otelConfig = nil
	})

	otel.SetSpanContextExtractor(func(context.Context) (SpanContext, bool) {
		return SpanContext{
			TraceID:    "0af7651916cd43dd8448eb211c80319c",
			SpanID:     "b7ad6b7169203331",
			TraceFlags: 1,
			TraceState: "bad,tracestate,entry",
		}, true
	})
	otelConfig = &OpenTelemetryConfig{
		SpanFromContext: func(context.Context) uint64 {
			return fallbackSpanPtr
		},
	}

	source, _, parentSpanPtr := otel.selectSpanParent(context.Background())
	assert.Equal(t, spanParentPointer, source)
	assert.Equal(t, fallbackSpanPtr, parentSpanPtr)
}
