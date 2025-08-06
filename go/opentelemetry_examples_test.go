// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
)

// ExampleWithSpan demonstrates storing and retrieving span pointers from context
func ExampleWithSpan() {
	// Create a mock span pointer
	spanPtr := uint64(12345)

	// Store span in context
	ctx := WithSpan(context.Background(), spanPtr)

	// Retrieve span from context
	retrievedSpan := DefaultSpanFromContext(ctx)

	fmt.Println(retrievedSpan)

	// Output: 12345
}

// ExampleDefaultSpanFromContext demonstrates the default span extraction function
func ExampleDefaultSpanFromContext() {
	// Test with context containing a span
	spanPtr := uint64(54321)
	ctx := WithSpan(context.Background(), spanPtr)

	// Extract span using default function
	extractedSpan := DefaultSpanFromContext(ctx)
	fmt.Println(extractedSpan)

	// Test with context without span
	emptyCtx := context.Background()
	noSpan := DefaultSpanFromContext(emptyCtx)
	fmt.Println(noSpan)

	// Output: 54321
	// 0
}

// ExampleOpenTelemetry_CreateSpan demonstrates creating and ending spans
func ExampleOpenTelemetry_CreateSpan() {
	// Initialize OpenTelemetry with file exporter for demonstration
	config := OpenTelemetryConfig{
		Traces: &OpenTelemetryTracesConfig{
			Endpoint:         "file:///tmp/glide_traces.json",
			SamplePercentage: 100, // Sample all requests for demo
		},
		SpanFromContext: DefaultSpanFromContext,
	}

	err := GetOtelInstance().Init(config)
	if err != nil {
		// OpenTelemetry may already be initialized from previous examples
		// This is expected behavior in the test environment
	}

	// Create a span
	spanPtr, err := GetOtelInstance().CreateSpan("example-operation")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer GetOtelInstance().EndSpan(spanPtr)

	// Use the span with a client operation
	client := getExampleClient()
	ctx := WithSpan(context.Background(), spanPtr)

	result, err := client.Set(ctx, "example:key", "example_value")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)

	// Output: OK
}
