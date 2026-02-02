/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Jedis Pub/Sub commands (publish, pubsubChannels, pubsubNumPat, pubsubNumSub).
 *
 * <p>{@code publish()} delegates to the GLIDE Java client and returns {@code 0} (subscriber count
 * is not provided by the underlying API).
 */
public class JedisPubSubCommandsTest {

    @Test
    public void publishStringSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("publish", String.class, String.class);
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void publishBinarySignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("publish", byte[].class, byte[].class);
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void pubsubChannelsNoArgSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("pubsubChannels");
        assertEquals(Set.class, m.getReturnType());
    }

    @Test
    public void pubsubChannelsStringSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("pubsubChannels", String.class);
        assertEquals(Set.class, m.getReturnType());
    }

    @Test
    public void pubsubChannelsBinarySignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("pubsubChannels", byte[].class);
        assertEquals(Set.class, m.getReturnType());
    }

    @Test
    public void pubsubNumPatSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("pubsubNumPat");
        assertEquals(long.class, m.getReturnType());
    }

    @Test
    public void pubsubNumSubStringSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("pubsubNumSub", String[].class);
        assertEquals(Map.class, m.getReturnType());
    }

    @Test
    public void pubsubNumSubBinarySignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("pubsubNumSub", byte[][].class);
        assertEquals(Map.class, m.getReturnType());
    }

    @Test
    public void pubsubMethodsExist() throws NoSuchMethodException {
        Class<Jedis> c = Jedis.class;
        assertNotNull(c.getMethod("publish", String.class, String.class));
        assertNotNull(c.getMethod("publish", byte[].class, byte[].class));
        assertNotNull(c.getMethod("pubsubChannels"));
        assertNotNull(c.getMethod("pubsubChannels", String.class));
        assertNotNull(c.getMethod("pubsubChannels", byte[].class));
        assertNotNull(c.getMethod("pubsubNumPat"));
        assertNotNull(c.getMethod("pubsubNumSub", String[].class));
        assertNotNull(c.getMethod("pubsubNumSub", byte[][].class));
    }
}
