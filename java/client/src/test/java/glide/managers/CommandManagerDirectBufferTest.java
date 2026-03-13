/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import glide.internal.GlideCoreClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CommandManagerDirectBufferTest {

    private CommandManager commandManager;

    @BeforeEach
    void setUp() {
        commandManager = new CommandManager(mock(GlideCoreClient.class));
    }

    @Test
    void deserializeByteBufferArray_handlesBooleanDoubleAndBigNumberMarkers() throws Exception {
        String bigNumberText = "1234567890123456789012345678901234567890";
        byte[] bigNumberBytes = bigNumberText.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer =
                ByteBuffer.allocate(1 + 4 + (1 + 1) + (1 + 8) + (1 + 4 + bigNumberBytes.length) + (1 + 4))
                        .order(ByteOrder.BIG_ENDIAN);

        buffer.put((byte) '*');
        buffer.putInt(4);

        buffer.put((byte) '?');
        buffer.put((byte) 1);

        buffer.put((byte) ',');
        buffer.putDouble(42.25d);

        buffer.put((byte) '(');
        buffer.putInt(bigNumberBytes.length);
        buffer.put(bigNumberBytes);

        buffer.put((byte) '$');
        buffer.putInt(-1);

        buffer.flip();

        Object[] decoded = deserializeByteBufferArray(buffer, false);

        assertEquals(4, decoded.length);
        assertEquals(Boolean.TRUE, decoded[0]);
        assertTrue(decoded[1] instanceof Double);
        assertEquals(42.25d, (Double) decoded[1]);
        assertEquals(new BigInteger(bigNumberText), decoded[2]);
        assertNull(decoded[3]);
    }

    // ==================== Array Bounds Checking Tests ====================

    @Test
    void deserializeByteBufferArray_rejectsNegativeCount() {
        ByteBuffer buffer = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '*');
        buffer.putInt(-1);
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferArray(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("negative array count"));
    }

    @Test
    void deserializeByteBufferArray_rejectsTooSmallBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '*');
        buffer.putShort((short) 0); // Only 2 bytes instead of 4
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferArray(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Buffer too small"));
    }

    @Test
    void deserializeByteBufferArray_rejectsBulkStringLengthExceedingBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '*');
        buffer.putInt(1); // 1 element
        buffer.put((byte) '$'); // bulk string
        buffer.putInt(1000); // claims 1000 bytes but buffer only has a few
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferArray(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("exceeds buffer remaining"));
    }

    @Test
    void deserializeByteBufferArray_rejectsNegativeBulkStringLength() {
        ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '*');
        buffer.putInt(1); // 1 element
        buffer.put((byte) '$'); // bulk string
        buffer.putInt(-5); // negative but not -1 (which means null)
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferArray(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Invalid negative bulk string length"));
    }

    @Test
    void deserializeByteBufferArray_rejectsBufferUnderflowOnTypeMarker() {
        ByteBuffer buffer = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '*');
        buffer.putInt(1); // claims 1 element but no data follows
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferArray(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("type marker"));
    }

    // ==================== Map Bounds Checking Tests ====================

    @Test
    void deserializeByteBufferMap_rejectsNegativeCount() {
        ByteBuffer buffer = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putInt(-1);
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferMap(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("negative map count"));
    }

    @Test
    void deserializeByteBufferMap_rejectsTooSmallBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putShort((short) 0); // Only 2 bytes instead of 4
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferMap(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Buffer too small"));
    }

    @Test
    void deserializeByteBufferMap_rejectsKeyLengthExceedingBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putInt(1); // 1 entry
        buffer.putInt(1000); // key length claims 1000 bytes
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferMap(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Key length"));
        assertTrue(ex.getCause().getMessage().contains("exceeds buffer remaining"));
    }

    @Test
    void deserializeByteBufferMap_rejectsNegativeKeyLength() {
        ByteBuffer buffer = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putInt(1); // 1 entry
        buffer.putInt(-5); // negative key length
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferMap(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Invalid negative key length"));
    }

    @Test
    void deserializeByteBufferMap_rejectsValueLengthExceedingBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putInt(1); // 1 entry
        buffer.putInt(3); // key length = 3
        buffer.put("key".getBytes(StandardCharsets.UTF_8));
        buffer.putInt(1000); // value length claims 1000 bytes
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferMap(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Value length"));
        assertTrue(ex.getCause().getMessage().contains("exceeds buffer remaining"));
    }

    @Test
    void deserializeByteBufferMap_rejectsNegativeValueLength() {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putInt(1); // 1 entry
        buffer.putInt(3); // key length = 3
        buffer.put("key".getBytes(StandardCharsets.UTF_8));
        buffer.putInt(-5); // negative value length
        buffer.flip();

        InvocationTargetException ex =
                assertThrows(
                        InvocationTargetException.class, () -> deserializeByteBufferMap(buffer, false));
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertTrue(ex.getCause().getMessage().contains("Invalid negative value length"));
    }

    @Test
    void deserializeByteBufferMap_validMapDeserializesCorrectly() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(50).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) '%');
        buffer.putInt(2); // 2 entries

        // Entry 1: "key1" -> "val1"
        buffer.putInt(4);
        buffer.put("key1".getBytes(StandardCharsets.UTF_8));
        buffer.putInt(4);
        buffer.put("val1".getBytes(StandardCharsets.UTF_8));

        // Entry 2: "key2" -> "val2"
        buffer.putInt(4);
        buffer.put("key2".getBytes(StandardCharsets.UTF_8));
        buffer.putInt(4);
        buffer.put("val2".getBytes(StandardCharsets.UTF_8));

        buffer.flip();

        LinkedHashMap<Object, Object> map = deserializeByteBufferMap(buffer, true);

        assertEquals(2, map.size());
        assertEquals("val1", map.get("key1"));
        assertEquals("val2", map.get("key2"));
    }

    // ==================== Helper Methods ====================

    private Object[] deserializeByteBufferArray(ByteBuffer buffer, boolean expectUtf8Response)
            throws Exception {
        Method method =
                CommandManager.class.getDeclaredMethod(
                        "deserializeByteBufferArray", ByteBuffer.class, boolean.class);
        method.setAccessible(true);
        return (Object[]) method.invoke(commandManager, buffer, expectUtf8Response);
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<Object, Object> deserializeByteBufferMap(
            ByteBuffer buffer, boolean expectUtf8Response) throws Exception {
        Method method =
                CommandManager.class.getDeclaredMethod(
                        "deserializeByteBufferMap", ByteBuffer.class, boolean.class);
        method.setAccessible(true);
        return (LinkedHashMap<Object, Object>)
                method.invoke(commandManager, buffer, expectUtf8Response);
    }
}
