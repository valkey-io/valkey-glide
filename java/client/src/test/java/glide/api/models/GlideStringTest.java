/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class GlideStringTest {

    @ParameterizedTest
    @MethodSource("validUtf8Provider")
    public void shouldReturnTrueWhenBytesAreValidUtf8(byte[] validUtf8Bytes) {
        // Given
        GlideString glideString = GlideString.of(validUtf8Bytes);

        // When
        boolean canConvert = glideString.canConvertToString();

        // Then
        assertTrue(canConvert);
    }

    @ParameterizedTest
    @MethodSource("invalidUtf8Provider")
    public void shouldReturnFalseWhenBytesContainInvalidUtf8(byte[] invalidUtf8Bytes) {
        // Given
        GlideString glideString = GlideString.of(invalidUtf8Bytes);

        // When
        boolean canConvert = glideString.canConvertToString();

        // Then
        assertFalse(canConvert);
    }

    @Test
    public void shouldProcessLargePayloadsEfficientlyForProfiling() {
        // Given
        int fiveMegabytes = 5 * 1024 * 1024;
        byte[] largePayload = new byte[fiveMegabytes];
        GlideString glideString = GlideString.of(largePayload);

        // When
        boolean canConvert = glideString.canConvertToString();

        // Then
        assertTrue(canConvert);
    }

    private static Stream<byte[]> validUtf8Provider() {
        return Stream.of(
                "Hello Valkey!".getBytes(StandardCharsets.UTF_8), // ASCII
                "ñ".getBytes(StandardCharsets.UTF_8), // 2-byte
                "안녕하세요".getBytes(StandardCharsets.UTF_8), // 3-byte (Korean)
                "😀".getBytes(StandardCharsets.UTF_8), // 4-byte (Emoji)
                new byte[0] // Empty
                );
    }

    private static Stream<byte[]> invalidUtf8Provider() {
        return Stream.of(
                new byte[] {(byte) 0xFF, (byte) 0xFE}, // Invalid bytes
                new byte[] {(byte) 0xC3, (byte) 0x28}, // Invalid continuation byte
                new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0x80}, // Overlong encoding
                new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80}, // Surrogate half
                new byte[] {'A', 'B', (byte) 0xC3} // Incomplete trailing sequence
                );
    }
}
