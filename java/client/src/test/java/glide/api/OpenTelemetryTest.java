/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import glide.api.OpenTelemetry.GlideSpanContext;
import glide.api.OpenTelemetry.OpenTelemetryConfig;
import glide.api.logging.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

public class OpenTelemetryTest {
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    @AfterEach
    void clearProvider() {
        OpenTelemetry.setParentSpanContextProvider(null);
    }

    @Test
    void providerCanBeReplacedAndCleared() {
        assertNull(OpenTelemetry.getParentSpanContext());
        GlideSpanContext first = new GlideSpanContext(TRACE_ID, SPAN_ID, 1, null);
        GlideSpanContext second = new GlideSpanContext(TRACE_ID, SPAN_ID, 0, "vendor=value");
        OpenTelemetry.setParentSpanContextProvider(() -> first);
        assertSame(first, OpenTelemetry.getParentSpanContext());
        OpenTelemetry.setParentSpanContextProvider(() -> second);
        assertSame(second, OpenTelemetry.getParentSpanContext());
        assertEquals("vendor=value", second.getTraceState());
        OpenTelemetry.setParentSpanContextProvider(null);
        assertNull(OpenTelemetry.getParentSpanContext());
    }

    @Test
    void absentAndThrowingProvidersFallBack() {
        OpenTelemetry.setParentSpanContextProvider(() -> null);
        assertNull(OpenTelemetry.getParentSpanContext());
        OpenTelemetry.setParentSpanContextProvider(
                () -> {
                    throw new IllegalStateException("test");
                });
        assertNull(OpenTelemetry.getParentSpanContext());
    }

    @Test
    void fatalProviderErrorsAreNotSwallowed() {
        OpenTelemetry.setParentSpanContextProvider(
                () -> {
                    throw new AssertionError("test");
                });
        assertThrows(AssertionError.class, OpenTelemetry::getParentSpanContext);
    }

    @ParameterizedTest
    @ValueSource(strings = {"throwing", "invalid"})
    void fallbackWarningsAreBoundedPerRegistration(String failure) {
        Supplier<GlideSpanContext> provider =
                () -> {
                    if (failure.equals("throwing")) throw new IllegalStateException("test");
                    return new GlideSpanContext("bad", SPAN_ID, 1, null);
                };
        try (MockedStatic<Logger> logger = mockStatic(Logger.class)) {
            OpenTelemetry.setParentSpanContextProvider(provider);
            for (int i = 0; i < 100; i++) assertNull(OpenTelemetry.getParentSpanContext());
            logger.verify(
                    () -> Logger.log(eq(Logger.Level.WARN), eq("GlideOpenTelemetry"), anyString()), times(1));
            OpenTelemetry.setParentSpanContextProvider(provider);
            assertNull(OpenTelemetry.getParentSpanContext());
            logger.verify(
                    () -> Logger.log(eq(Logger.Level.WARN), eq("GlideOpenTelemetry"), anyString()), times(2));
            logger.verifyNoMoreInteractions();
        }
    }

    @Test
    @Timeout(10)
    void concurrentFailuresLogOnlyOnce() throws Exception {
        OpenTelemetry.setParentSpanContextProvider(() -> new GlideSpanContext("bad", SPAN_ID, 1, null));
        AtomicInteger warnings = new AtomicInteger();
        CompletableFuture<?>[] tasks =
                IntStream.range(0, 8)
                        .mapToObj(
                                i ->
                                        CompletableFuture.runAsync(
                                                () -> {
                                                    // Mockito static mocks are thread-local, so intercept the logger in each
                                                    // worker.
                                                    try (MockedStatic<Logger> logger = mockStatic(Logger.class)) {
                                                        logger
                                                                .when(
                                                                        () ->
                                                                                Logger.log(
                                                                                        eq(Logger.Level.WARN),
                                                                                        eq("GlideOpenTelemetry"),
                                                                                        anyString()))
                                                                .thenAnswer(
                                                                        invocation -> {
                                                                            warnings.incrementAndGet();
                                                                            return null;
                                                                        });
                                                        for (int j = 0; j < 100; j++)
                                                            assertNull(OpenTelemetry.getParentSpanContext());
                                                    }
                                                }))
                        .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).get(5, TimeUnit.SECONDS);
        assertEquals(1, warnings.get());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "bad",
                "0af7651916cd43dd",
                "0af7651916cd43dd8448eb211c80319c0",
                "0AF7651916CD43DD8448EB211C80319C",
                "00000000000000000000000000000000",
                "zaf7651916cd43dd8448eb211c80319c"
            })
    void invalidTraceIdsFallBack(String traceId) {
        OpenTelemetry.setParentSpanContextProvider(
                () -> new GlideSpanContext(traceId, SPAN_ID, 1, null));
        assertNull(OpenTelemetry.getParentSpanContext());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "bad", "b7ad6b71692033310", "B7AD6B7169203331", "0000000000000000"})
    void invalidSpanIdsFallBack(String spanId) {
        OpenTelemetry.setParentSpanContextProvider(
                () -> new GlideSpanContext(TRACE_ID, spanId, 1, null));
        assertNull(OpenTelemetry.getParentSpanContext());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 256, Integer.MAX_VALUE})
    void invalidFlagsFallBack(int flags) {
        OpenTelemetry.setParentSpanContextProvider(
                () -> new GlideSpanContext(TRACE_ID, SPAN_ID, flags, null));
        assertNull(OpenTelemetry.getParentSpanContext());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 255})
    void unsignedFlagsAndOptionalTraceStateArePreserved(int flags) {
        GlideSpanContext context = new GlideSpanContext(TRACE_ID, SPAN_ID, flags, "");
        OpenTelemetry.setParentSpanContextProvider(() -> context);
        assertSame(context, OpenTelemetry.getParentSpanContext());
        assertEquals(TRACE_ID, context.getTraceId());
        assertEquals(SPAN_ID, context.getSpanId());
        assertEquals(flags, context.getTraceFlags());
        assertEquals("", context.getTraceState());
    }

    @Test
    void configStoresProviderWithoutInvokingIt() {
        @SuppressWarnings("unchecked")
        Supplier<GlideSpanContext> provider = mock(Supplier.class);
        OpenTelemetryConfig config =
                OpenTelemetryConfig.builder()
                        .traces(OpenTelemetry.TracesConfig.builder().endpoint("file:///unused.json").build())
                        .parentSpanContextProvider(provider)
                        .build();
        assertSame(provider, config.getParentSpanContextProvider());
        assertNotSame(config.getTraces(), config.getTraces());
        verifyNoInteractions(provider);
        assertNull(OpenTelemetryConfig.builder().build().getParentSpanContextProvider());
    }

    @Test
    void failedInitializationDoesNotReplaceProvider() {
        Integer samplePercentage = OpenTelemetry.getSamplePercentage();
        GlideSpanContext parent = new GlideSpanContext(TRACE_ID, SPAN_ID, 1, null);
        OpenTelemetry.setParentSpanContextProvider(() -> parent);
        OpenTelemetryConfig config =
                OpenTelemetryConfig.builder()
                        .traces(OpenTelemetry.TracesConfig.builder().endpoint("invalid-endpoint").build())
                        .parentSpanContextProvider(() -> null)
                        .build();
        assertThrows(Exception.class, () -> OpenTelemetry.init(config));
        assertSame(parent, OpenTelemetry.getParentSpanContext());
        assertEquals(samplePercentage, OpenTelemetry.getSamplePercentage());
    }

    @Test
    void providerCapturesEachSubmittingThreadsContext() {
        ThreadLocal<GlideSpanContext> active = new ThreadLocal<>();
        OpenTelemetry.setParentSpanContextProvider(active::get);
        GlideSpanContext parent = new GlideSpanContext(TRACE_ID, SPAN_ID, 1, null);
        active.set(parent);
        assertSame(parent, OpenTelemetry.getParentSpanContext());
        assertNull(CompletableFuture.supplyAsync(OpenTelemetry::getParentSpanContext).join());
        active.remove();
        assertNull(OpenTelemetry.getParentSpanContext());
    }
}
