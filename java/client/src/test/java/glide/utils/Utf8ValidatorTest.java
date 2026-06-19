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

    // =========================================================================
    // The following tests and helpers are ported from Google Guava's Utf8Test:
    // https://github.com/google/guava/blob/3e65944ec9207ca652128969fd1334e9920dde07/guava-tests/test/com/google/common/base/Utf8Test.java
    // =========================================================================

    // 128 - [chars 0x0000 to 0x007f]
    private static final long ONE_BYTE_ROUNDTRIPPABLE_CHARACTERS = 0x007f - 0x0000 + 1;
    // 128
    private static final long EXPECTED_ONE_BYTE_ROUNDTRIPPABLE_COUNT =
            ONE_BYTE_ROUNDTRIPPABLE_CHARACTERS;
    // 1920 [chars 0x0080 to 0x07FF]
    private static final long TWO_BYTE_ROUNDTRIPPABLE_CHARACTERS = 0x07FF - 0x0080 + 1;
    // 18,304
    private static final long EXPECTED_TWO_BYTE_ROUNDTRIPPABLE_COUNT =
            (long) Math.pow(EXPECTED_ONE_BYTE_ROUNDTRIPPABLE_COUNT, 2)
                    + TWO_BYTE_ROUNDTRIPPABLE_CHARACTERS;
    // 2048
    private static final long THREE_BYTE_SURROGATES = 2 * 1024;
    // 61,440 [chars 0x0800 to 0xFFFF, minus surrogates]
    private static final long THREE_BYTE_ROUNDTRIPPABLE_CHARACTERS =
            0xFFFF - 0x0800 + 1 - THREE_BYTE_SURROGATES;
    // 2,650,112
    private static final long EXPECTED_THREE_BYTE_ROUNDTRIPPABLE_COUNT =
            (long) Math.pow(EXPECTED_ONE_BYTE_ROUNDTRIPPABLE_COUNT, 3)
                    + 2 * TWO_BYTE_ROUNDTRIPPABLE_CHARACTERS * ONE_BYTE_ROUNDTRIPPABLE_CHARACTERS
                    + THREE_BYTE_ROUNDTRIPPABLE_CHARACTERS;

    @org.junit.jupiter.api.Test
    public void testIsWellFormed_1Byte() {
        testBytes(1, EXPECTED_ONE_BYTE_ROUNDTRIPPABLE_COUNT);
    }

    @org.junit.jupiter.api.Test
    public void testIsWellFormed_2Bytes() {
        testBytes(2, EXPECTED_TWO_BYTE_ROUNDTRIPPABLE_COUNT);
    }

    @org.junit.jupiter.api.Test
    public void testIsWellFormed_3Bytes() {
        testBytes(3, EXPECTED_THREE_BYTE_ROUNDTRIPPABLE_COUNT);
    }

    @org.junit.jupiter.api.Test
    public void testIsWellFormed_4BytesSamples() {
        // Valid 4 byte.
        assertWellFormed(0xF0, 0xA4, 0xAD, 0xA2);
        // Bad trailing bytes
        assertNotWellFormed(0xF0, 0xA4, 0xAD, 0x7F);
        assertNotWellFormed(0xF0, 0xA4, 0xAD, 0xC0);
        // Special cases for byte2
        assertNotWellFormed(0xF0, 0x8F, 0xAD, 0xA2);
        assertNotWellFormed(0xF4, 0x90, 0xAD, 0xA2);
    }

    @org.junit.jupiter.api.Test
    public void testSomeSequences() {
        // Empty
        assertWellFormed();
        // One-byte characters, including control characters
        assertWellFormed(0x00, 0x61, 0x62, 0x63, 0x7F); // "\u0000abc\u007f"
        // Two-byte characters
        assertWellFormed(0xC2, 0xA2, 0xC2, 0xA2); // "\u00a2\u00a2"
        // Three-byte characters
        assertWellFormed(0xc8, 0x8a, 0x63, 0xc8, 0x8a, 0x63); // "\u020ac\u020ac"
        // Four-byte characters
        // "\u024B62\u024B62"
        assertWellFormed(0xc9, 0x8b, 0x36, 0x32, 0xc9, 0x8b, 0x36, 0x32);
        // Mixed string
        // "a\u020ac\u00a2b\\u024B62u020acc\u00a2de\u024B62"
        assertWellFormed(
                0x61, 0xc8, 0x8a, 0x63, 0xc2, 0xa2, 0x62, 0x5c, 0x75, 0x30, 0x32, 0x34, 0x42, 0x36, 0x32,
                0x75, 0x30, 0x32, 0x30, 0x61, 0x63, 0x63, 0xc2, 0xa2, 0x64, 0x65, 0xc9, 0x8b, 0x36, 0x32);
        // Not a valid string
        assertNotWellFormed(-1, 0, -1, 0);
    }

    private static byte[] toByteArray(int... bytes) {
        byte[] realBytes = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            realBytes[i] = (byte) bytes[i];
        }
        return realBytes;
    }

    private static void assertWellFormed(int... bytes) {
        assertTrue(Utf8Validator.isWellFormed(toByteArray(bytes)));
    }

    private static void assertNotWellFormed(int... bytes) {
        assertFalse(Utf8Validator.isWellFormed(toByteArray(bytes)));
    }

    private static void testBytes(int numBytes, long expectedCount) {
        testBytes(numBytes, expectedCount, 0, -1);
    }

    private static void testBytes(int numBytes, long expectedCount, long start, long lim) {
        byte[] bytes = new byte[numBytes];
        if (lim == -1) {
            lim = 1L << (numBytes * 8);
        }
        long countRoundTripped = 0;
        for (long byteChar = start; byteChar < lim; byteChar++) {
            long tmpByteChar = byteChar;
            for (int i = 0; i < numBytes; i++) {
                bytes[bytes.length - i - 1] = (byte) tmpByteChar;
                tmpByteChar = tmpByteChar >> 8;
            }
            boolean isRoundTrippable = Utf8Validator.isWellFormed(bytes);
            assertEquals(isRoundTrippable, Utf8Validator.isWellFormed(bytes, 0, numBytes));
            String s = new String(bytes, StandardCharsets.UTF_8);
            byte[] bytesReencoded = s.getBytes(StandardCharsets.UTF_8);
            boolean bytesEqual = java.util.Arrays.equals(bytes, bytesReencoded);

            if (bytesEqual != isRoundTrippable) {
                org.junit.jupiter.api.Assertions.fail();
            }
            if (isRoundTrippable) {
                countRoundTripped++;
            }
        }
        assertEquals(expectedCount, countRoundTripped);
    }
}
