/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.GlideString;
import glide.utils.BufferUtils;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;

/**
 * Decodes the zero-copy {@link ByteBuffer} the native layer hands back for responses above the 16 KB
 * threshold. Every response path shares this one decoder so no two of them can drift apart.
 */
public final class DirectBufferResolver {

    private DirectBufferResolver() {}

    /**
     * Decode a direct buffer into the same object a smaller response would have produced: a {@link
     * String} or {@link GlideString} for a bulk value, an {@code Object[]} for an array, or a {@link
     * LinkedHashMap} for a map.
     */
    public static Object normalizeDirectBuffer(ByteBuffer buffer, boolean expectUtf8Response) {
        ByteBuffer dup = buffer.duplicate();
        dup.order(ByteOrder.BIG_ENDIAN);
        dup.rewind();
        if (dup.remaining() == 0) {
            return expectUtf8Response ? "" : GlideString.gs(new byte[0]);
        }
        byte marker = dup.get();
        dup.rewind();
        if (marker == '*') {
            // Serialized array/map (custom wire format)
            return deserializeByteBufferArray(dup, expectUtf8Response);
        } else if (marker == '%') {
            return deserializeByteBufferMap(dup, expectUtf8Response);
        }
        // Bulk string bytes
        if (expectUtf8Response) {
            // Decode UTF-8 directly from buffer
            return BufferUtils.decodeUtf8(dup);
        } else {
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            return GlideString.gs(bytes);
        }
    }

    /**
     * Validate that the buffer has at least the required number of bytes remaining.
     *
     * @param buffer the buffer to check
     * @param required the minimum number of bytes required
     * @param context description of what is being read (for error message)
     * @throws IllegalArgumentException if buffer has insufficient bytes
     */
    private static void requireBufferBytes(ByteBuffer buffer, int required, String context) {
        if (buffer.remaining() < required) {
            throw new IllegalArgumentException(
                    "Buffer too small for " + context + ": " + buffer.remaining() + " bytes");
        }
    }

    /**
     * Validate a length field read from the buffer.
     *
     * @param length the length value to validate
     * @param buffer the buffer to check remaining bytes against
     * @param typeName description of the data type (for error message), capitalized (e.g., "Key",
     *     "Value")
     * @param index the element/entry index (for error message)
     * @throws IllegalArgumentException if length is negative or exceeds buffer remaining
     */
    private static void validateLength(int length, ByteBuffer buffer, String typeName, int index) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "Invalid negative "
                            + typeName.toLowerCase()
                            + " length at element "
                            + index
                            + ": "
                            + length);
        }
        if (length > buffer.remaining()) {
            throw new IllegalArgumentException(
                    typeName
                            + " length "
                            + length
                            + " exceeds buffer remaining "
                            + buffer.remaining()
                            + " at element "
                            + index);
        }
    }

    /**
     * Deserialize a ByteBuffer containing a serialized map back to Map<?,?>. Format: '%' + count(u32
     * BE) + repeated [keyLen(u32) + keyBytes + valLen(u32) + valBytes]
     *
     * <p>This method includes defense-in-depth validation to protect against malformed buffers from
     * the native layer (due to bugs or memory corruption).
     *
     * @throws IllegalArgumentException if the buffer format is invalid or contains out-of-bounds
     *     values
     */
    public static LinkedHashMap<Object, Object> deserializeByteBufferMap(
            ByteBuffer buffer, boolean expectUtf8) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.rewind();

        // Validate minimum buffer size for marker + count
        requireBufferBytes(buffer, 5, "map header");

        byte marker = buffer.get();
        if (marker != '%') {
            throw new IllegalArgumentException("Expected map marker '%', got: " + (char) marker);
        }

        int count = buffer.getInt();

        // Validate count is non-negative (primary protection is per-element bounds checking)
        if (count < 0) {
            throw new IllegalArgumentException("Invalid negative map count: " + count);
        }

        // Use reasonable initial capacity to avoid huge upfront allocation
        // The actual elements will be validated one-by-one against buffer bounds
        LinkedHashMap<Object, Object> map = new LinkedHashMap<>(Math.min(count, 1024));

        for (int i = 0; i < count; i++) {
            requireBufferBytes(buffer, 4, "key length at entry " + i);
            int klen = buffer.getInt();
            validateLength(klen, buffer, "Key", i);

            Object key;
            if (expectUtf8) {
                key = BufferUtils.decodeUtf8(buffer, klen);
            } else {
                byte[] kbytes = new byte[klen];
                buffer.get(kbytes);
                key = GlideString.gs(kbytes);
            }

            requireBufferBytes(buffer, 4, "value length at entry " + i);
            int vlen = buffer.getInt();
            validateLength(vlen, buffer, "Value", i);

            Object val;
            if (expectUtf8) {
                val = BufferUtils.decodeUtf8(buffer, vlen);
            } else {
                byte[] vbytes = new byte[vlen];
                buffer.get(vbytes);
                val = GlideString.gs(vbytes);
            }
            map.put(key, val);
        }
        return map;
    }

    /**
     * Deserialize a ByteBuffer containing a serialized array back to Object[]. This handles
     * DirectByteBuffer responses for large data (>16KB). Format uses Redis-like protocol: '*' +
     * array_len(4 bytes BE) + elements Each element: type_marker + data
     *
     * <p>This method includes defense-in-depth validation to protect against malformed buffers from
     * the native layer (due to bugs or memory corruption).
     *
     * @throws IllegalArgumentException if the buffer format is invalid or contains out-of-bounds
     *     values
     */
    public static Object[] deserializeByteBufferArray(ByteBuffer buffer, boolean expectUtf8Response) {
        buffer.order(ByteOrder.BIG_ENDIAN); // Rust uses big-endian
        buffer.rewind();

        // Validate minimum buffer size for marker + count
        requireBufferBytes(buffer, 5, "array header");

        // Read array marker ('*')
        byte marker = buffer.get();
        if (marker != '*') {
            throw new IllegalArgumentException("Expected array marker '*', got: " + (char) marker);
        }

        // Read array element count (4 bytes, big-endian)
        int count = buffer.getInt();

        // Validate count is non-negative (primary protection is per-element bounds checking)
        if (count < 0) {
            throw new IllegalArgumentException("Invalid negative array count: " + count);
        }

        Object[] result = new Object[count];

        for (int i = 0; i < count; i++) {
            requireBufferBytes(buffer, 1, "type marker at element " + i);

            // Read element type marker
            byte typeMarker = buffer.get();

            switch (typeMarker) {
                case '$': // Bulk string
                    requireBufferBytes(buffer, 4, "bulk string length at element " + i);
                    int bulkLen = buffer.getInt();
                    if (bulkLen == -1) {
                        result[i] = null;
                    } else {
                        validateLength(bulkLen, buffer, "bulk string", i);
                        if (expectUtf8Response) {
                            result[i] = BufferUtils.decodeUtf8(buffer, bulkLen);
                        } else {
                            byte[] data = new byte[bulkLen];
                            buffer.get(data);
                            result[i] = GlideString.gs(data);
                        }
                    }
                    break;

                case '+': // Simple string (includes "OK")
                    requireBufferBytes(buffer, 4, "simple string length at element " + i);
                    int simpleLen = buffer.getInt();
                    validateLength(simpleLen, buffer, "simple string", i);
                    String simpleString = BufferUtils.decodeUtf8(buffer, simpleLen);
                    result[i] = simpleString.equalsIgnoreCase("ok") ? "OK" : simpleString;
                    break;

                case ':': // Integer
                    requireBufferBytes(buffer, 8, "integer at element " + i);
                    result[i] = buffer.getLong();
                    break;

                case ',': // Double
                    requireBufferBytes(buffer, 8, "double at element " + i);
                    result[i] = buffer.getDouble();
                    break;

                case '?': // Boolean
                    requireBufferBytes(buffer, 1, "boolean at element " + i);
                    result[i] = buffer.get() != 0;
                    break;

                case '(': // BigNumber
                    requireBufferBytes(buffer, 4, "big number length at element " + i);
                    int bigNumberLen = buffer.getInt();
                    validateLength(bigNumberLen, buffer, "big number", i);
                    String bigNumberStr = BufferUtils.decodeUtf8(buffer, bigNumberLen);
                    result[i] = new BigInteger(bigNumberStr);
                    break;

                case '#': // Complex type (serialized as string)
                    requireBufferBytes(buffer, 4, "complex type length at element " + i);
                    int complexLen = buffer.getInt();
                    validateLength(complexLen, buffer, "complex type", i);
                    if (expectUtf8Response) {
                        result[i] = BufferUtils.decodeUtf8(buffer, complexLen);
                    } else {
                        byte[] complexData = new byte[complexLen];
                        buffer.get(complexData);
                        result[i] = GlideString.gs(complexData);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unknown type marker: " + (char) typeMarker);
            }
        }

        return result;
    }
}
