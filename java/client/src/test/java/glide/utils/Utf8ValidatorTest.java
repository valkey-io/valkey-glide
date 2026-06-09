/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class Utf8ValidatorTest {

    @ParameterizedTest(name = "Valid UTF-8: {0}")
    @MethodSource("provideValidUtf8Sequences")
    public void identifiesProperlyEncodedStringsAsValid(String description, byte[] validBytes) {
        // When
        boolean isValid = Utf8Validator.isWellFormed(validBytes);

        // Then
        assertTrue(isValid);
    }

    @ParameterizedTest(name = "Invalid UTF-8: {0}")
    @MethodSource("provideInvalidUtf8Sequences")
    public void identifiesMalformedOrIllegalSequencesAsInvalid(
            String description, byte[] invalidBytes) {
        // When
        boolean isValid = Utf8Validator.isWellFormed(invalidBytes);

        // Then
        assertFalse(isValid);
    }

    @ParameterizedTest(name = "Out of bounds: {0}")
    @MethodSource("provideOutOfBoundsParameters")
    public void throwsExceptionWhenBufferBoundariesAreViolated(
            String description, int offset, int length) {
        // Given
        byte[] buffer = new byte[10];

        // When & Then
        assertThrows(
                IndexOutOfBoundsException.class, () -> Utf8Validator.isWellFormed(buffer, offset, length));
    }

    @ParameterizedTest(name = "Slice: {0}")
    @MethodSource("provideBufferSlices")
    public void correctlyValidatesSpecificSlicesOfABuffer(
            String description, int offset, int length, boolean expectedValid) {
        // Given
        byte[] buffer = new byte[10];
        // Fill buffer with valid ASCII except the ends
        buffer[0] = (byte) 0xFF; // Invalid
        buffer[1] = 'A';
        buffer[2] = 'B';
        buffer[3] = 'C';
        buffer[4] = (byte) 0xFF; // Invalid

        // When
        boolean isValidSlice = Utf8Validator.isWellFormed(buffer, offset, length);

        // Then
        assertEquals(expectedValid, isValidSlice);
    }

    private static Stream<Arguments> provideBufferSlices() {
        return Stream.of(
                Arguments.of("Valid slice skipping invalid ends", 1, 3, true),
                Arguments.of("Invalid slice starting at invalid byte", 0, 3, false),
                Arguments.of("Invalid slice ending at invalid byte", 1, 4, false),
                Arguments.of("Empty slice (valid by definition)", 1, 0, true));
    }

    private static Stream<Arguments> provideValidUtf8Sequences() {
        return Stream.of(
                Arguments.of("Empty array", new byte[0]),
                Arguments.of("Standard ASCII", "Hello Valkey!".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("Two-byte characters", "ñ".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("Three-byte characters (Korean)", "안녕하세요".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("Four-byte characters (Emoji)", "😀".getBytes(StandardCharsets.UTF_8)));
    }

    private static Stream<Arguments> provideInvalidUtf8Sequences() {
        return Stream.of(
                Arguments.of("Invalid start byte", new byte[] {(byte) 0xFF, (byte) 0xFE}),
                Arguments.of("Invalid continuation byte", new byte[] {(byte) 0xC3, (byte) 0x28}),
                Arguments.of(
                        "Overlong 3-byte encoding", new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0x80}),
                Arguments.of("Surrogate half", new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80}),
                Arguments.of("Incomplete trailing sequence", new byte[] {'A', 'B', (byte) 0xC3}));
    }

    private static Stream<Arguments> provideOutOfBoundsParameters() {
        return Stream.of(
                Arguments.of("Negative offset", -1, 5),
                Arguments.of("Negative length", 0, -1),
                Arguments.of("Length exceeds array", 0, 11),
                Arguments.of("Offset + Length exceeds array", 5, 6));
    }
}
