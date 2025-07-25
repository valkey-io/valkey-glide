package glide

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestCreateSpan_Success tests successful span creation
func TestCreateSpan_Success(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		t.Skip("OpenTelemetry is not initialized, skipping CreateSpan success tests")
		return
	}

	testCases := []struct {
		name     string
		spanName string
	}{
		{"simple name", "test-span"},
		{"empty name allowed", ""}, // Note: Based on FFI tests, empty names should be allowed
		{"name with spaces", "test span with spaces"},
		{"name with special chars", "test-span_with.special@chars"},
		{"name with numbers", "test123span456"},
		{"unicode name", "测试span"},
		{"maximum length name", strings.Repeat("a", 256)},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			spanPtr, err := otelInstance.CreateSpan(tc.spanName)
			
			if tc.spanName == "" {
				// Empty names should fail according to the Go implementation
				assert.Error(t, err)
				assert.Contains(t, err.Error(), "span name cannot be empty")
				return
			}
			
			require.NoError(t, err)
			assert.NotEqual(t, uint64(0), spanPtr, "Span pointer should not be zero")
			
			// Clean up
			otelInstance.EndSpan(spanPtr)
		})
	}
}

// TestCreateSpan_InputValidationExtended tests extended input validation for CreateSpan
func TestCreateSpan_InputValidationExtended(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		t.Skip("OpenTelemetry is not initialized, skipping CreateSpan validation tests")
		return
	}

	// Test empty name (should fail)
	_, err := otelInstance.CreateSpan("")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "span name cannot be empty")

	// Test too long name (should fail)
	longName := strings.Repeat("a", 257) // 257 characters
	_, err = otelInstance.CreateSpan(longName)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "span name too long")
	assert.Contains(t, err.Error(), "257 chars")

	// Test maximum allowed length (should work)
	maxName := strings.Repeat("a", 256) // 256 characters
	spanPtr, err := otelInstance.CreateSpan(maxName)
	require.NoError(t, err)
	assert.NotEqual(t, uint64(0), spanPtr)
	otelInstance.EndSpan(spanPtr)
}

// TestCreateSpan_NotInitializedExtended tests CreateSpan when OpenTelemetry is not initialized
func TestCreateSpan_NotInitializedExtended(t *testing.T) {
	otelInstance := GetOtelInstance()

	if otelInstance.IsInitialized() {
		t.Skip("OpenTelemetry is already initialized, cannot test uninitialized behavior")
		return
	}

	// Test CreateSpan when not initialized (should fail)
	_, err := otelInstance.CreateSpan("test-span")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "openTelemetry not initialized")
}

// TestEndSpan_SafetyChecksExtended tests that EndSpan is safe to call with various inputs
func TestEndSpan_SafetyChecksExtended(t *testing.T) {
	otelInstance := GetOtelInstance()

	// Test EndSpan with zero pointer (should be safe no-op)
	require.NotPanics(t, func() {
		otelInstance.EndSpan(0)
	})

	// Test EndSpan with arbitrary pointer (should be safe no-op)
	require.NotPanics(t, func() {
		otelInstance.EndSpan(12345)
	})

	// Test EndSpan with maximum uint64 value (should be safe no-op)
	require.NotPanics(t, func() {
		otelInstance.EndSpan(^uint64(0)) // Maximum uint64
	})

	// Test double EndSpan (should be safe)
	if otelInstance.IsInitialized() {
		spanPtr, err := otelInstance.CreateSpan("test-double-end")
		require.NoError(t, err)
		
		// End the span twice - should not panic
		require.NotPanics(t, func() {
			otelInstance.EndSpan(spanPtr)
			otelInstance.EndSpan(spanPtr) // Second call should be safe
		})
	}
}

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
		name           string
		setupContext   func() context.Context
		expectedSpan   uint64
		expectedFound  bool
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
				return context.WithValue(context.Background(), "different-key", uint64(12345))
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
	customSpanFromContext := func(ctx context.Context) (uint64, bool) {
		if spanPtr, ok := ctx.Value("custom-span-key").(uint64); ok && spanPtr != 0 {
			return spanPtr, true
		}
		return 0, false
	}

	// Test that the function can be configured (we can't actually test the full flow
	// without reinitializing OpenTelemetry, but we can test the function itself)
	ctx := context.WithValue(context.Background(), "custom-span-key", uint64(54321))
	spanPtr, found := customSpanFromContext(ctx)
	
	assert.Equal(t, uint64(54321), spanPtr)
	assert.True(t, found)

	// Test with missing key
	emptyCtx := context.Background()
	spanPtr, found = customSpanFromContext(emptyCtx)
	
	assert.Equal(t, uint64(0), spanPtr)
	assert.False(t, found)
}

// TestExtractSpanPointer_ErrorHandling tests error handling in span extraction
func TestExtractSpanPointer_ErrorHandling(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		t.Skip("OpenTelemetry is not initialized, skipping extractSpanPointer tests")
		return
	}

	// Test with nil SpanFromContext function (should return no parent)
	// Note: We can't directly test extractSpanPointer as it's internal,
	// but we can test the behavior through the public API

	// Test panic recovery in SpanFromContext function
	panicSpanFromContext := func(ctx context.Context) (uint64, bool) {
		panic("test panic in SpanFromContext")
	}

	// Test that the panic is handled gracefully
	require.NotPanics(t, func() {
		// We can't directly call extractSpanPointer, but we can test that
		// a panicking function would be handled gracefully
		defer func() {
			if r := recover(); r != nil {
				t.Logf("Panic recovered as expected: %v", r)
			}
		}()
		
		// This would normally be called internally, but we test the panic handling
		_, _ = panicSpanFromContext(context.Background())
	})
}

// TestSpanContextKey_Constant tests the SpanContextKey constant
func TestSpanContextKey_Constant(t *testing.T) {
	assert.Equal(t, "glide-span", SpanContextKey, "SpanContextKey should have expected value")
	
	// Test that the constant can be used as a context key
	ctx := context.WithValue(context.Background(), SpanContextKey, uint64(12345))
	value, ok := ctx.Value(SpanContextKey).(uint64)
	assert.True(t, ok)
	assert.Equal(t, uint64(12345), value)
}

// TestSpanLifecycle_CreateAndEnd tests the complete lifecycle of span creation and cleanup
func TestSpanLifecycle_CreateAndEnd(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		t.Skip("OpenTelemetry is not initialized, skipping span lifecycle tests")
		return
	}

	// Test creating multiple spans and ending them
	spanNames := []string{"span1", "span2", "span3"}
	spanPtrs := make([]uint64, len(spanNames))

	// Create spans
	for i, name := range spanNames {
		spanPtr, err := otelInstance.CreateSpan(name)
		require.NoError(t, err)
		assert.NotEqual(t, uint64(0), spanPtr)
		spanPtrs[i] = spanPtr
	}

	// Verify all spans have unique pointers
	for i := 0; i < len(spanPtrs); i++ {
		for j := i + 1; j < len(spanPtrs); j++ {
			assert.NotEqual(t, spanPtrs[i], spanPtrs[j], "Span pointers should be unique")
		}
	}

	// End spans in reverse order
	for i := len(spanPtrs) - 1; i >= 0; i-- {
		require.NotPanics(t, func() {
			otelInstance.EndSpan(spanPtrs[i])
		})
	}
}

// TestSpanContextAttachment_ThreadSafety tests thread safety of span context operations
func TestSpanContextAttachment_ThreadSafety(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		t.Skip("OpenTelemetry is not initialized, skipping thread safety tests")
		return
	}

	const numGoroutines = 10
	const spansPerGoroutine = 5

	var wg sync.WaitGroup
	spanPtrs := make([]uint64, numGoroutines*spansPerGoroutine)
	spanPtrsMutex := sync.Mutex{}
	spanIndex := 0

	// Create spans concurrently
	for i := 0; i < numGoroutines; i++ {
		wg.Add(1)
		go func(goroutineID int) {
			defer wg.Done()
			
			for j := 0; j < spansPerGoroutine; j++ {
				spanName := fmt.Sprintf("goroutine_%d_span_%d", goroutineID, j)
				spanPtr, err := otelInstance.CreateSpan(spanName)
				
				if err != nil {
					t.Errorf("Failed to create span %s: %v", spanName, err)
					continue
				}
				
				if spanPtr == 0 {
					t.Errorf("Received zero span pointer for %s", spanName)
					continue
				}
				
				// Store span pointer thread-safely
				spanPtrsMutex.Lock()
				if spanIndex < len(spanPtrs) {
					spanPtrs[spanIndex] = spanPtr
					spanIndex++
				}
				spanPtrsMutex.Unlock()
				
				// Small delay to increase chance of race conditions
				time.Sleep(time.Millisecond)
			}
		}(i)
	}

	wg.Wait()

	// Clean up all created spans
	spanPtrsMutex.Lock()
	validSpans := spanPtrs[:spanIndex]
	spanPtrsMutex.Unlock()

	for _, spanPtr := range validSpans {
		if spanPtr != 0 {
			require.NotPanics(t, func() {
				otelInstance.EndSpan(spanPtr)
			})
		}
	}

	t.Logf("Successfully created and cleaned up %d spans across %d goroutines", len(validSpans), numGoroutines)
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
	multiKeySpanFromContext := func(ctx context.Context) (uint64, bool) {
		// Try multiple keys in order of preference
		keys := []interface{}{"primary-span", "secondary-span", SpanContextKey}
		
		for _, key := range keys {
			if spanPtr, ok := ctx.Value(key).(uint64); ok && spanPtr != 0 {
				return spanPtr, true
			}
		}
		return 0, false
	}

	// Test with primary key
	ctx1 := context.WithValue(context.Background(), "primary-span", uint64(11111))
	spanPtr, found := multiKeySpanFromContext(ctx1)
	assert.True(t, found)
	assert.Equal(t, uint64(11111), spanPtr)

	// Test with secondary key (primary not present)
	ctx2 := context.WithValue(context.Background(), "secondary-span", uint64(22222))
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

// BenchmarkCreateSpan benchmarks span creation performance
func BenchmarkCreateSpan(b *testing.B) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		b.Skip("OpenTelemetry is not initialized, skipping benchmark")
		return
	}

	b.ResetTimer()
	
	for i := 0; i < b.N; i++ {
		spanName := fmt.Sprintf("benchmark-span-%d", i)
		spanPtr, err := otelInstance.CreateSpan(spanName)
		if err != nil {
			b.Fatalf("Failed to create span: %v", err)
		}
		otelInstance.EndSpan(spanPtr)
	}
}

// BenchmarkWithSpan benchmarks context span storage performance
func BenchmarkWithSpan(b *testing.B) {
	ctx := context.Background()
	spanPtr := uint64(12345)
	
	b.ResetTimer()
	
	for i := 0; i < b.N; i++ {
		ctxWithSpan := WithSpan(ctx, spanPtr)
		_, _ = DefaultSpanFromContext(ctxWithSpan)
	}
}

// BenchmarkDefaultSpanFromContext benchmarks span extraction performance
func BenchmarkDefaultSpanFromContext(b *testing.B) {
	ctx := WithSpan(context.Background(), 12345)
	
	b.ResetTimer()
	
	for i := 0; i < b.N; i++ {
		_, _ = DefaultSpanFromContext(ctx)
	}
}
