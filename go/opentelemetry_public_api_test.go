package glide

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestCreateSpan_InputValidation tests input validation for the CreateSpan method
func TestCreateSpan_InputValidation(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		// When not initialized, all CreateSpan calls should return initialization error
		_, err := otelInstance.CreateSpan("")
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "openTelemetry not initialized")

		longName := strings.Repeat("a", 257)
		_, err = otelInstance.CreateSpan(longName)
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "openTelemetry not initialized")

		t.Skip("OpenTelemetry is not initialized, skipping detailed input validation tests")
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

	// Test maximum allowed length (should work)
	maxName := strings.Repeat("a", 256) // 256 characters
	spanPtr, err := otelInstance.CreateSpan(maxName)
	if assert.NoError(t, err) {
		assert.NotEqual(t, uint64(0), spanPtr)
		otelInstance.EndSpan(spanPtr)
	}
}

// TestEndSpan_SafetyChecks tests that EndSpan is safe to call with various inputs
func TestEndSpan_SafetyChecks(t *testing.T) {
	otelInstance := GetOtelInstance()

	// Test EndSpan with zero pointer (should be safe no-op)
	require.NotPanics(t, func() {
		otelInstance.EndSpan(0)
	})

	// Test EndSpan with arbitrary pointer (should be safe no-op)
	require.NotPanics(t, func() {
		otelInstance.EndSpan(12345)
	})
}

// TestCreateSpan_NotInitialized tests behavior when OpenTelemetry is not initialized
func TestCreateSpan_NotInitialized(t *testing.T) {
	otelInstance := GetOtelInstance()

	if !otelInstance.IsInitialized() {
		// Test CreateSpan when not initialized (should fail)
		_, err := otelInstance.CreateSpan("test-span")
		assert.Error(t, err)
		assert.Contains(t, err.Error(), "openTelemetry not initialized")
	} else {
		t.Skip("OpenTelemetry is already initialized, skipping uninitialized test")
	}
}
