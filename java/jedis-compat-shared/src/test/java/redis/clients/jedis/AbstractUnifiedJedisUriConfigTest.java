/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.net.URI;
import org.junit.jupiter.api.Test;

class AbstractUnifiedJedisUriConfigTest {

    @Test
    void buildConfigFromUriSplitsPasswordOnFirstColonOnly() throws Exception {
        JedisClientConfig config =
                invokeBuildConfigFromUri(URI.create("redis://user:pass:with:colons@localhost/2"));
        assertEquals("user", config.getUser());
        assertEquals("pass:with:colons", config.getPassword());
        assertEquals(2, config.getDatabase());
    }

    @Test
    void buildConfigFromUriDecodesPercentEncodedCredentials() throws Exception {
        JedisClientConfig config =
                invokeBuildConfigFromUri(URI.create("redis://user:pass%3Aword@localhost/0"));
        assertEquals("user", config.getUser());
        assertEquals("pass:word", config.getPassword());
    }

    @Test
    void buildConfigFromUriPasswordOnlyWithColon() throws Exception {
        JedisClientConfig config =
                invokeBuildConfigFromUri(URI.create("redis://:secret:colon@localhost/0"));
        assertEquals("secret:colon", config.getPassword());
    }

    private static JedisClientConfig invokeBuildConfigFromUri(URI uri) throws Exception {
        Method method =
                AbstractUnifiedJedis.class.getDeclaredMethod(
                        "buildConfigFromURI", URI.class, JedisClientConfig.class);
        method.setAccessible(true);
        return (JedisClientConfig) method.invoke(null, uri, null);
    }
}
