package glide.telemetry;

import glide.ffi.resolvers.OpenTelemetryResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenTelemetry functionality in the Valkey GLIDE Java client.
 * These tests focus only on the native method bindings and do not require a Redis server.
 */
public class OpenTelemetryTest {

    /**
     * Test that spans are properly created and cleaned up.
     */
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

    /**
     * Test that spans are properly created and cleaned up when using null span name.
     */
//    @Test
//    void testSpanLifecycleWithNullName() {
//        // This should not throw an exception
//        long spanPtr = OpenTelemetryResolver.createOtelSpan(null);
//
//        // Verify span was created even with null name
//        assertNotEquals(0, spanPtr, "Span pointer should not be zero even with null name");
//
//        // Clean up span
//        OpenTelemetryResolver.dropOtelSpan(spanPtr);
//    }

    /**
     * Test that dropping a zero span pointer doesn't cause issues.
     */
    @Test
    void testDropZeroSpan() {
        // This should not throw an exception
        OpenTelemetryResolver.dropOtelSpan(0);
    }
}
