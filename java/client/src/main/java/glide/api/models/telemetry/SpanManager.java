/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.telemetry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages OpenTelemetry spans for Valkey GLIDE operations.
 * 
 * <p>This class provides centralized management of spans, including creation,
 * tracking, and cleanup of spans associated with commands and operations.
 */
public class SpanManager {
    private static final ConcurrentHashMap<Long, GlideSpan> activeSpans = new ConcurrentHashMap<>();
    private static final AtomicLong spanCounter = new AtomicLong(0);

    /**
     * Creates a new span for the specified command.
     *
     * @param commandName The name of the command or operation being performed
     * @return A unique identifier for the created span
     */
    public static long createSpan(String commandName) {
        long spanId = spanCounter.incrementAndGet();
        GlideSpan span = new GlideSpan(commandName);
        activeSpans.put(spanId, span);
        return span.getSpanPtr();
    }

    /**
     * Closes and removes a span by its identifier.
     *
     * @param spanId The identifier of the span to close
     */
    public static void closeSpan(long spanId) {
        GlideSpan span = activeSpans.remove(spanId);
        if (span != null) {
            span.close();
        }
    }

    /**
     * Gets the current count of active spans.
     * This method is primarily intended for testing and monitoring.
     *
     * @return The number of currently active spans
     */
    public static int getActiveSpanCount() {
        return activeSpans.size();
    }

    /**
     * Closes all active spans.
     * This method should be called during client shutdown to ensure proper cleanup.
     */
    public static void closeAllSpans() {
        activeSpans.values().forEach(GlideSpan::close);
        activeSpans.clear();
    }
}
