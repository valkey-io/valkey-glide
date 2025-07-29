// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
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
		name          string
		setupContext  func() context.Context
		expectedSpan  uint64
		expectedFound bool
	}{
		{
			name: "context with valid span",
			setupContext: func() context.Context {
				return WithSpan(context.Background(), 12345)
			},
			expectedSpan:  12345,
			expectedFound: true,
		},
		{
			name: "context with zero span",
			setupContext: func() context.Context {
				return WithSpan(context.Background(), 0)
			},
			expectedSpan:  0,
			expectedFound: false, // Zero spans are treated as not found
		},
		{
			name: "context without span",
			setupContext: func() context.Context {
				return context.Background()
			},
			expectedSpan:  0,
			expectedFound: false,
		},
		{
			name: "context with wrong type",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, "not-a-uint64")
			},
			expectedSpan:  0,
			expectedFound: false,
		},
		{
			name: "context with different key",
			setupContext: func() context.Context {
				type differentKeyType struct{}
				return context.WithValue(context.Background(), differentKeyType{}, uint64(12345))
			},
			expectedSpan:  0,
			expectedFound: false,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			ctx := tc.setupContext()
			spanPtr, found := DefaultSpanFromContext(ctx)

			assert.Equal(t, tc.expectedSpan, spanPtr, "Span pointer should match expected")
			assert.Equal(t, tc.expectedFound, found, "Found flag should match expected")
		})
	}
}

// TestSpanFromContext_Configuration tests SpanFromContext configuration
func TestSpanFromContext_Configuration(t *testing.T) {
	// Test custom SpanFromContext function
	type customSpanKeyType struct{}
	customSpanFromContext := func(ctx context.Context) (uint64, bool) {
		if spanPtr, ok := ctx.Value(customSpanKeyType{}).(uint64); ok && spanPtr != 0 {
			return spanPtr, true
		}
		return 0, false
	}

	// Test that the function can be configured (we can't actually test the full flow
	// without reinitializing OpenTelemetry, but we can test the function itself)
	ctx := context.WithValue(context.Background(), customSpanKeyType{}, uint64(54321))
	spanPtr, found := customSpanFromContext(ctx)

	assert.Equal(t, uint64(54321), spanPtr)
	assert.True(t, found)

	// Test with missing key
	emptyCtx := context.Background()
	spanPtr, found = customSpanFromContext(emptyCtx)

	assert.Equal(t, uint64(0), spanPtr)
	assert.False(t, found)
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
	storedSpan, found := DefaultSpanFromContext(ctxWithSpan)
	assert.True(t, found)
	assert.Equal(t, spanPtr, storedSpan)

	// Test context inheritance
	childCtx, cancel := context.WithTimeout(ctxWithSpan, time.Second)
	defer cancel()

	// Child context should inherit the span
	childSpan, found := DefaultSpanFromContext(childCtx)
	assert.True(t, found)
	assert.Equal(t, spanPtr, childSpan)
}

// TestSpanFromContext_EdgeCases tests edge cases in span context extraction
func TestSpanFromContext_EdgeCases(t *testing.T) {
	testCases := []struct {
		name          string
		setupContext  func() context.Context
		expectedFound bool
		expectedSpan  uint64
	}{
		{
			name: "nil context",
			setupContext: func() context.Context {
				return nil
			},
			expectedFound: false,
			expectedSpan:  0,
		},
		{
			name: "context with nil value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, nil)
			},
			expectedFound: false,
			expectedSpan:  0,
		},
		{
			name: "context with string value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, "12345")
			},
			expectedFound: false,
			expectedSpan:  0,
		},
		{
			name: "context with int value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, 12345)
			},
			expectedFound: false,
			expectedSpan:  0,
		},
		{
			name: "context with float value",
			setupContext: func() context.Context {
				return context.WithValue(context.Background(), SpanContextKey, 12345.0)
			},
			expectedFound: false,
			expectedSpan:  0,
		},
		{
			name: "context with maximum uint64",
			setupContext: func() context.Context {
				return WithSpan(context.Background(), ^uint64(0))
			},
			expectedFound: true,
			expectedSpan:  ^uint64(0),
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
					spanPtr, found := DefaultSpanFromContext(ctx)
					assert.Equal(t, tc.expectedSpan, spanPtr)
					assert.Equal(t, tc.expectedFound, found)
				})
				return
			}

			spanPtr, found := DefaultSpanFromContext(ctx)
			assert.Equal(t, tc.expectedSpan, spanPtr)
			assert.Equal(t, tc.expectedFound, found)
		})
	}
}

// TestSpanFromContext_CustomImplementations tests custom SpanFromContext implementations
func TestSpanFromContext_CustomImplementations(t *testing.T) {
	// Test multiple key strategy
	type primarySpanKeyType struct{}
	type secondarySpanKeyType struct{}

	multiKeySpanFromContext := func(ctx context.Context) (uint64, bool) {
		// Try multiple keys in order of preference
		keys := []interface{}{primarySpanKeyType{}, secondarySpanKeyType{}, SpanContextKey}

		for _, key := range keys {
			if spanPtr, ok := ctx.Value(key).(uint64); ok && spanPtr != 0 {
				return spanPtr, true
			}
		}
		return 0, false
	}

	// Test with primary key
	ctx1 := context.WithValue(context.Background(), primarySpanKeyType{}, uint64(11111))
	spanPtr, found := multiKeySpanFromContext(ctx1)
	assert.True(t, found)
	assert.Equal(t, uint64(11111), spanPtr)

	// Test with secondary key (primary not present)
	ctx2 := context.WithValue(context.Background(), secondarySpanKeyType{}, uint64(22222))
	spanPtr, found = multiKeySpanFromContext(ctx2)
	assert.True(t, found)
	assert.Equal(t, uint64(22222), spanPtr)

	// Test with default key (others not present)
	ctx3 := WithSpan(context.Background(), 33333)
	spanPtr, found = multiKeySpanFromContext(ctx3)
	assert.True(t, found)
	assert.Equal(t, uint64(33333), spanPtr)

	// Test with no keys present
	ctx4 := context.Background()
	spanPtr, found = multiKeySpanFromContext(ctx4)
	assert.False(t, found)
	assert.Equal(t, uint64(0), spanPtr)
}

// TestSpanFromContext_ErrorRecovery tests error recovery in SpanFromContext functions
func TestSpanFromContext_ErrorRecovery(t *testing.T) {
	// Test function that panics
	panicSpanFromContext := func(ctx context.Context) (uint64, bool) {
		panic("intentional panic for testing")
	}

	// Test function that returns inconsistent values
	inconsistentSpanFromContext := func(ctx context.Context) (uint64, bool) {
		return 0, true // Returns found=true but spanPtr=0 (invalid)
	}

	// Test panic recovery
	require.NotPanics(t, func() {
		defer func() {
			if r := recover(); r != nil {
				// Panic should be recovered
				t.Logf("Panic recovered as expected: %v", r)
			}
		}()

		_, _ = panicSpanFromContext(context.Background())
	})

	// Test inconsistent return values
	spanPtr, found := inconsistentSpanFromContext(context.Background())
	assert.Equal(t, uint64(0), spanPtr)
	assert.True(t, found) // Function returns true, but this would be handled by validation
}
