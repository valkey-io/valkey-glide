/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import glide.internal.GlideCoreClient;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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

    private Object[] deserializeByteBufferArray(ByteBuffer buffer, boolean expectUtf8Response)
            throws Exception {
        Method method =
                CommandManager.class.getDeclaredMethod(
                        "deserializeByteBufferArray", ByteBuffer.class, boolean.class);
        method.setAccessible(true);
        return (Object[]) method.invoke(commandManager, buffer, expectUtf8Response);
    }
}
