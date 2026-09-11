/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.exceptions.ClosingException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class GlideNativeBridgeTest {

    @Test
    void genericCommandEntryPointCannotRequestImmediateMgetBufferRelease() {
        assertDoesNotThrow(
                () ->
                        GlideNativeBridge.class.getDeclaredMethod(
                                "executeCommandAsync",
                                long.class,
                                long.class,
                                int.class,
                                byte[][].class,
                                boolean.class,
                                int.class,
                                String.class,
                                boolean.class,
                                long.class));
        assertEquals(1, countMethodsNamed(GlideNativeBridge.class, "executeCommandAsync"));
        assertThrows(
                NoSuchMethodException.class,
                () ->
                        GlideNativeBridge.class.getDeclaredMethod(
                                "executeCommandAsyncPacked",
                                long.class,
                                long.class,
                                int.class,
                                byte[].class,
                                boolean.class,
                                int.class,
                                String.class,
                                boolean.class,
                                long.class));
    }

    @Test
    void unusedGenericPackedMgetWrappersAreAbsent() {
        assertThrows(
                NoSuchMethodException.class,
                () ->
                        GlideCoreClient.class.getDeclaredMethod(
                                "executeCommandAsyncPacked",
                                int.class,
                                byte[].class,
                                boolean.class,
                                int.class,
                                String.class,
                                boolean.class,
                                long.class,
                                long.class));
        assertThrows(
                NoSuchMethodException.class,
                () ->
                        GlideCoreClient.class.getDeclaredMethod(
                                "executeMgetCommandAsync",
                                byte[][].class,
                                byte[].class,
                                boolean.class,
                                long.class,
                                long.class,
                                java.util.function.BiFunction.class));
    }

    @Test
    void typedMgetDisconnectPropagatesWithoutClosingTheClient() {
        ClosingException error = new ClosingException("Will attempt to reconnect");

        ClosingException thrown =
                assertThrows(ClosingException.class, () -> GlideCoreClient.throwMgetError(error));

        assertSame(error, thrown);
    }

    @Test
    void immediateMgetConversionIsOnlyReachableThroughPrivateFixedEntryPoint() {
        Method method =
                assertDoesNotThrow(
                        () ->
                                GlideCoreClient.class.getDeclaredMethod(
                                        "executeMgetCommandAsyncNative",
                                        long.class,
                                        long.class,
                                        byte[][].class,
                                        byte[].class,
                                        boolean.class,
                                        long.class));

        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(Modifier.isNative(method.getModifiers()));
    }

    private static int countMethodsNamed(Class<?> owner, String name) {
        int count = 0;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                count++;
            }
        }
        return count;
    }
}
