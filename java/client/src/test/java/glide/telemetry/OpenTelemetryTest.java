/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.telemetry;

import static org.junit.jupiter.api.Assertions.*;

import glide.ffi.resolvers.OpenTelemetryResolver;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OpenTelemetry functionality in the Valkey GLIDE Java client. These tests focus
 * only on the native method bindings and do not require a Redis server.
 */
public class OpenTelemetryTest {

    /** Test that spans are properly created and cleaned up. */
    @Test
    void testSpanLifecycle() {
        // Create a span
        long spanPtr = OpenTelemetryResolver.createOtelSpan("test_span");

        // Verify span was created
        assertNotEquals(0, spanPtr, "Span pointer should not be zero");

        // Clean up span
        OpenTelemetryResolver.dropOtelSpan(spanPtr);

        // No assertion needed for cleanup - if it doesn't crash, it worked
    }

    /** Test that dropping a zero span pointer doesn't cause issues. */
    @Test
    void testDropZeroSpan() {
        // This should not throw an exception
        OpenTelemetryResolver.dropOtelSpan(0);
    }
}
