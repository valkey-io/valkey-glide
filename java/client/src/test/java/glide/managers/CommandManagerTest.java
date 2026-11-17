/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import command_request.CommandRequestOuterClass.RequestType;
import glide.api.OpenTelemetry;
import glide.api.OpenTelemetry.GlideSpanContext;
import glide.api.models.Batch;
import glide.api.models.ClusterBatch;
import glide.ffi.resolvers.OpenTelemetryResolver;
import glide.internal.GlideCoreClient;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

public class CommandManagerTest {
    private static final GlideSpanContext PARENT =
            new GlideSpanContext(
                    "0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", 1, "vendor=value");

    private static GlideCoreClient coreClient(AtomicLong spanPointer) {
        GlideCoreClient core = mock(GlideCoreClient.class);
        when(core.isConnected()).thenReturn(true);
        when(core.executeCommandAsync(
                        anyInt(), any(), anyBoolean(), anyInt(), any(), anyBoolean(), anyLong(), anyLong()))
                .thenAnswer(
                        invocation -> {
                            spanPointer.set(invocation.getArgument(7));
                            return CompletableFuture.completedFuture("OK");
                        });
        when(core.executeBatchAsync(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        anyBoolean(),
                        anyLong(),
                        anyLong()))
                .thenAnswer(
                        invocation -> {
                            spanPointer.set(invocation.getArgument(12));
                            return CompletableFuture.completedFuture("OK");
                        });
        return core;
    }

    private static void submit(CommandManager manager, String path) {
        switch (path) {
            case "normal":
                manager.submitNewCommand(RequestType.Get, new String[] {"key"}, response -> "OK").join();
                break;
            case "blocking":
                manager
                        .submitBlockingCommand(RequestType.BLPop, new String[] {"key", "1"}, response -> "OK")
                        .join();
                break;
            case "custom":
                manager.submitCustomCommand(new String[] {"GET", "key"}, response -> "OK").join();
                break;
            case "customBlocking":
                manager.submitCustomCommand(new String[] {"BLPOP", "key", "1"}, response -> "OK").join();
                break;
            case "batch":
                manager
                        .submitNewBatch(new Batch(false).ping(), false, Optional.empty(), response -> "OK")
                        .join();
                break;
            case "transaction":
                manager
                        .submitNewBatch(
                                new ClusterBatch(true).ping(), false, Optional.empty(), response -> "OK")
                        .join();
                break;
            default:
                throw new AssertionError(path);
        }
    }

    private static String spanName(String path) {
        switch (path) {
            case "normal":
                return "Get";
            case "blocking":
                return "BLPop";
            case "custom":
            case "customBlocking":
                return "CustomCommand";
            default:
                return "Batch";
        }
    }

    private static Stream<Arguments> parentContexts() {
        return Stream.of("normal", "blocking", "custom", "customBlocking", "batch", "transaction")
                .flatMap(
                        path ->
                                Stream.of(0, 1, 255)
                                        .flatMap(
                                                flags ->
                                                        Stream.of(null, "", "vendor=value,other=state")
                                                                .map(state -> Arguments.of(path, flags, state))));
    }

    @ParameterizedTest
    @MethodSource("parentContexts")
    void forwardsExactParentContext(String path, int flags, String state) {
        AtomicLong pointer = new AtomicLong();
        GlideCoreClient core = coreClient(pointer);
        GlideSpanContext parent =
                new GlideSpanContext(PARENT.getTraceId(), PARENT.getSpanId(), flags, state);
        try (MockedStatic<OpenTelemetry> telemetry = mockStatic(OpenTelemetry.class);
                MockedStatic<OpenTelemetryResolver> resolver = mockStatic(OpenTelemetryResolver.class)) {
            telemetry.when(OpenTelemetry::shouldSample).thenReturn(true);
            telemetry.when(OpenTelemetry::getParentSpanContext).thenReturn(parent);
            resolver
                    .when(
                            () ->
                                    OpenTelemetryResolver.createLeakedOtelSpanWithTraceContext(
                                            spanName(path), parent.getTraceId(), parent.getSpanId(), flags, state))
                    .thenReturn(123L);
            submit(new CommandManager(core), path);
            assertEquals(123L, pointer.get());
            resolver.verify(
                    () ->
                            OpenTelemetryResolver.createLeakedOtelSpanWithTraceContext(
                                    spanName(path), parent.getTraceId(), parent.getSpanId(), flags, state));
            resolver.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "blocking", "custom", "customBlocking", "batch", "transaction"})
    void sampledOperationsCaptureParentOnce(String path) {
        AtomicLong pointer = new AtomicLong();
        GlideCoreClient core = coreClient(pointer);
        try (MockedStatic<OpenTelemetry> telemetry = mockStatic(OpenTelemetry.class)) {
            AtomicInteger phase = new AtomicInteger();
            Thread submittingThread = Thread.currentThread();
            telemetry
                    .when(OpenTelemetry::shouldSample)
                    .thenAnswer(
                            invocation -> {
                                assertEquals(0, phase.getAndIncrement());
                                return true;
                            });
            telemetry
                    .when(OpenTelemetry::getParentSpanContext)
                    .thenAnswer(
                            invocation -> {
                                assertEquals(1, phase.getAndIncrement());
                                assertSame(submittingThread, Thread.currentThread());
                                return PARENT;
                            });
            submit(new CommandManager(core), path);
            assertEquals(2, phase.get());
            telemetry.verify(OpenTelemetry::getParentSpanContext, times(1));
            assertNotEquals(0, pointer.get());
            if (spanName(path).equals("Batch")) {
                verify(core)
                        .executeBatchAsync(
                                any(),
                                any(),
                                anyBoolean(),
                                anyBoolean(),
                                anyInt(),
                                anyBoolean(),
                                anyBoolean(),
                                anyBoolean(),
                                anyInt(),
                                any(),
                                anyBoolean(),
                                anyLong(),
                                eq(pointer.get()));
            } else {
                verify(core)
                        .executeCommandAsync(
                                anyInt(),
                                any(),
                                anyBoolean(),
                                anyInt(),
                                any(),
                                anyBoolean(),
                                anyLong(),
                                eq(pointer.get()));
            }
        } finally {
            // The mocked transport does not consume spans as the real JNI command path does.
            if (pointer.get() != 0) OpenTelemetryResolver.dropOtelSpan(pointer.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "blocking", "custom", "customBlocking", "batch", "transaction"})
    void unsampledOperationsDoNotCaptureContextOrAllocateSpans(String path) {
        AtomicLong pointer = new AtomicLong();
        GlideCoreClient core = coreClient(pointer);
        try (MockedStatic<OpenTelemetry> telemetry = mockStatic(OpenTelemetry.class)) {
            telemetry.when(OpenTelemetry::shouldSample).thenReturn(false);
            submit(new CommandManager(core), path);
            telemetry.verify(OpenTelemetry::getParentSpanContext, never());
            assertEquals(0, pointer.get());
        } finally {
            if (pointer.get() != 0) OpenTelemetryResolver.dropOtelSpan(pointer.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "blocking", "batch"})
    void missingParentUsesExistingStandaloneCreation(String path) {
        AtomicLong pointer = new AtomicLong();
        GlideCoreClient core = coreClient(pointer);
        try (MockedStatic<OpenTelemetry> telemetry = mockStatic(OpenTelemetry.class)) {
            telemetry.when(OpenTelemetry::shouldSample).thenReturn(true);
            submit(new CommandManager(core), path);
            assertNotEquals(0, pointer.get());
        } finally {
            if (pointer.get() != 0) OpenTelemetryResolver.dropOtelSpan(pointer.get());
        }
    }
}
