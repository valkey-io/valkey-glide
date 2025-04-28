/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.telemetry;

import glide.ffi.resolvers.OpenTelemetryResolver;

/** Manages OpenTelemetry spans for the Valkey GLIDE Java client. */
public class SpanManager {

    /**
     * Creates a new span for a command.
     *
     * @param commandName The name of the command
     * @return A pointer to the span
     */
    public static long createSpan(String commandName) {
        return OpenTelemetryResolver.createOtelSpan(commandName);
    }

    /**
     * Drops a span.
     *
     * @param spanPtr The pointer to the span
     */
    public static void dropSpan(long spanPtr) {
        if (spanPtr != 0) {
            OpenTelemetryResolver.dropOtelSpan(spanPtr);
        }
    }
}
