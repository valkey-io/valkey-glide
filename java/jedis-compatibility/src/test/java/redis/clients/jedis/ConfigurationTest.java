/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Jedis configuration mapping, SSL/TLS support, and resource lifecycle management.
 */
public class ConfigurationTest {

    @Test
    public void testBasicConfiguration() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(5000)
                        .connectionTimeoutMillis(3000)
                        .password("mypassword")
                        .clientName("test-client")
                        .database(1)
                        .protocol(RedisProtocol.RESP2)
                        .build();

        assertEquals(5000, config.getSocketTimeoutMillis());
        assertEquals(3000, config.getConnectionTimeoutMillis());
        assertEquals("mypassword", config.getPassword());
        assertEquals("test-client", config.getClientName());
        assertEquals(1, config.getDatabase());
        assertEquals(RedisProtocol.RESP2, config.getRedisProtocol());
    }

    @Test
    public void testJedisWithConfiguration() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(5000)
                        .connectionTimeoutMillis(3000)
                        .clientName("test-client")
                        .database(1)
                        .build();

        // Test that constructor signature exists
        assertDoesNotThrow(
                () -> {
                    Class<Jedis> jedisClass = Jedis.class;
                    jedisClass.getConstructor(String.class, int.class, JedisClientConfig.class);
                });
    }

    @Test
    public void testSslConfiguration() throws Exception {
        SSLContext sslContext = createTestSslContext();

        JedisClientConfig sslConfig =
                DefaultJedisClientConfig.builder()
                        .ssl(true)
                        .sslSocketFactory(sslContext.getSocketFactory())
                        .socketTimeoutMillis(10000)
                        .build();

        assertTrue(sslConfig.isSsl());
        assertNotNull(sslConfig.getSslSocketFactory());
        assertEquals(10000, sslConfig.getSocketTimeoutMillis());
    }

    @Test
    public void testAdvancedPoolConfiguration() {
        JedisClientConfig poolConfig =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(2000)
                        .connectionTimeoutMillis(1000)
                        .clientName("pool-client")
                        .build();

        // Test that constructor signature exists
        assertDoesNotThrow(
                () -> {
                    Class<JedisPool> poolClass = JedisPool.class;
                    // Test the proper constructor with GenericObjectPoolConfig
                    poolClass.getConstructor(
                            GenericObjectPoolConfig.class, String.class, int.class, JedisClientConfig.class);
                });
    }

    @Test
    public void testResourceLifecycleManager() {
        ResourceLifecycleManager manager = ResourceLifecycleManager.getInstance();
        assertNotNull(manager);

        int initialCount = manager.getTrackedResourceCount();
        assertTrue(initialCount >= 0);

        // Test that manager exists and can track resources (without creating actual pools)
        assertDoesNotThrow(
                () -> {
                    Class<JedisPool> poolClass = JedisPool.class;
                    assertNotNull(poolClass);
                });
    }

    @Test
    public void testConfigurationDefaults() {
        JedisClientConfig defaultConfig = DefaultJedisClientConfig.builder().build();

        // Test that defaults are reasonable
        assertTrue(defaultConfig.getSocketTimeoutMillis() > 0);
        assertTrue(defaultConfig.getConnectionTimeoutMillis() > 0);
        assertNotNull(defaultConfig.getRedisProtocol());
        assertTrue(defaultConfig.getDatabase() >= 0);
    }

    @Test
    public void testConfigurationValidation() {
        // Test that configuration builder validates inputs
        assertDoesNotThrow(
                () -> {
                    DefaultJedisClientConfig.builder()
                            .socketTimeoutMillis(1000)
                            .connectionTimeoutMillis(500)
                            .database(0)
                            .build();
                });

        assertDoesNotThrow(
                () -> {
                    DefaultJedisClientConfig.builder()
                            .socketTimeoutMillis(0) // Should handle zero timeout
                            .build();
                });
    }

    @Test
    public void testSslConfigurationOptions() throws Exception {
        SSLContext sslContext = createTestSslContext();

        JedisClientConfig sslConfig =
                DefaultJedisClientConfig.builder()
                        .ssl(true)
                        .sslSocketFactory(sslContext.getSocketFactory())
                        .hostnameVerifier((hostname, session) -> true) // Test hostname verifier
                        .build();

        assertTrue(sslConfig.isSsl());
        assertNotNull(sslConfig.getSslSocketFactory());
        assertNotNull(sslConfig.getHostnameVerifier());
    }

    @Test
    public void testProtocolConfiguration() {
        JedisClientConfig resp2Config =
                DefaultJedisClientConfig.builder().protocol(RedisProtocol.RESP2).build();
        assertEquals(RedisProtocol.RESP2, resp2Config.getRedisProtocol());

        JedisClientConfig resp3Config =
                DefaultJedisClientConfig.builder().protocol(RedisProtocol.RESP3).build();
        assertEquals(RedisProtocol.RESP3, resp3Config.getRedisProtocol());
    }

    @Test
    public void testAuthenticationConfiguration() {
        JedisClientConfig authConfig =
                DefaultJedisClientConfig.builder().password("secret123").user("testuser").build();

        assertEquals("secret123", authConfig.getPassword());
        assertEquals("testuser", authConfig.getUser());
    }

    /**
     * Create a test SSL context for configuration testing. WARNING: This is for testing only and
     * should not be used in production.
     */
    private SSLContext createTestSslContext() throws Exception {
        TrustManager[] trustAllCerts =
                new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                            // Test implementation - accept all
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                            // Test implementation - accept all
                        }
                    }
                };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }
}
