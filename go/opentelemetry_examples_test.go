// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
)

// ExampleOpenTelemetry demonstrates creating parent spans and using them with Valkey commands
func ExampleOpenTelemetry() {
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

	// Create a parent span for the user operation
	spanPtr, err := GetOtelInstance().CreateSpan("user-operation")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	defer GetOtelInstance().EndSpan(spanPtr)

	// Get a Valkey client
	client := getExampleClient()

	// Add the span to context - this creates parent-child relationship
	ctx := WithSpan(context.Background(), spanPtr)

	// Execute a Valkey command - this will create a child span under "user-operation"
	result, err := client.Set(ctx, "example:key", "example_value")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)
}
