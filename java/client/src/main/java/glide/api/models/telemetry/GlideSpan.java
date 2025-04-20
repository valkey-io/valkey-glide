/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.telemetry;

import glide.ffi.resolvers.OpenTelemetryResolver;

/**
 * Represents an OpenTelemetry span for tracing operations in Valkey GLIDE.
 * 
 * <p>This class provides a wrapper around the native OpenTelemetry span implementation.
 * It manages the lifecycle of the span, ensuring proper creation and cleanup of resources.
 * 
 * <p>Spans are used to track the execution of operations and collect telemetry data.
 * Each span represents a single operation or unit of work.
 */
public class GlideSpan implements AutoCloseable {
    private final long spanPtr;
    private boolean closed = false;

    /**
     * Creates a new span with the specified name.
     *
     * @param name The name of the span, typically representing the operation being performed
     */
    public GlideSpan(String name) {
        this.spanPtr = OpenTelemetryResolver.createOtelSpan(name);
    }

    /**
     * Returns the native pointer to the span.
     * This is used internally by the GLIDE client to associate commands with spans.
     *
     * @return The native pointer to the span
     */
    public long getSpanPtr() {
        return spanPtr;
    }

    /**
     * Closes the span, releasing any resources associated with it.
     * This method is automatically called when using try-with-resources.
     */
    @Override
    public void close() {
        if (!closed) {
            OpenTelemetryResolver.dropOtelSpan(spanPtr);
            closed = true;
        }
    }

    /**
     * Ensures the span is properly closed when the object is garbage collected.
     * This is a safety mechanism, but spans should be explicitly closed using {@link #close()}
     * or try-with-resources for deterministic resource management.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                close();
            }
        } finally {
            super.finalize();
        }
    }
}
