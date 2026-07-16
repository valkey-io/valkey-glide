/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Base UnifiedJedis compatibility facade shared between Jedis 4.x and 5.x compatibility layers. All
 * behavior is implemented in {@link AbstractUnifiedJedis}.
 *
 * <p>This abstract class contains all common constructors. Parameter wrapper methods that accept
 * concrete param types (SetParams, ScanParams, etc.) remain in version-specific subclasses since
 * those param classes only exist in the version-specific modules.
 */
public abstract class BaseUnifiedJedis extends AbstractUnifiedJedis {

    // Standalone constructors
    protected BaseUnifiedJedis(boolean jedis5) {
        super(jedis5, new HostAndPort("localhost", 6379), DefaultJedisClientConfig.builder().build());
    }

    protected BaseUnifiedJedis(boolean jedis5, String host, int port) {
        super(jedis5, new HostAndPort(host, port), DefaultJedisClientConfig.builder().build());
    }

    protected BaseUnifiedJedis(boolean jedis5, HostAndPort hostAndPort) {
        super(jedis5, hostAndPort, DefaultJedisClientConfig.builder().build());
    }

    protected BaseUnifiedJedis(boolean jedis5, String url) {
        this(jedis5, URI.create(url));
    }

    protected BaseUnifiedJedis(boolean jedis5, URI uri) {
        super(
                jedis5,
                extractHostAndPort(uri),
                buildConfigFromURI(uri, DefaultJedisClientConfig.builder().build()));
    }

    protected BaseUnifiedJedis(boolean jedis5, URI uri, JedisClientConfig config) {
        super(jedis5, extractHostAndPort(uri), buildConfigFromURI(uri, config));
    }

    protected BaseUnifiedJedis(
            boolean jedis5, HostAndPort hostAndPort, JedisClientConfig clientConfig) {
        super(jedis5, hostAndPort, clientConfig);
    }

    protected BaseUnifiedJedis(
            boolean jedis5, String host, int port, JedisClientConfig clientConfig) {
        super(jedis5, new HostAndPort(host, port), clientConfig);
    }

    protected BaseUnifiedJedis(boolean jedis5, String host, int port, int timeout, String password) {
        this(
                jedis5,
                host,
                port,
                DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).password(password).build());
    }

    protected BaseUnifiedJedis(boolean jedis5, String host, int port, int timeout) {
        this(
                jedis5,
                host,
                port,
                DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build());
    }

    protected BaseUnifiedJedis(
            boolean jedis5, String host, int port, int timeout, String password, int database) {
        this(
                jedis5,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .build());
    }

    protected BaseUnifiedJedis(
            boolean jedis5,
            String host,
            int port,
            int timeout,
            String password,
            int database,
            String clientName) {
        this(
                jedis5,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
    }

    // Cluster constructors
    protected BaseUnifiedJedis(boolean jedis5, Set<HostAndPort> jedisClusterNodes) {
        super(jedis5, jedisClusterNodes, DefaultJedisClientConfig.builder().build());
    }

    protected BaseUnifiedJedis(
            boolean jedis5, Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig) {
        super(jedis5, jedisClusterNodes, clientConfig);
    }

    protected BaseUnifiedJedis(
            boolean jedis5,
            Set<HostAndPort> jedisClusterNodes,
            JedisClientConfig clientConfig,
            int maxAttempts) {
        super(jedis5, jedisClusterNodes, clientConfig);
    }

    protected BaseUnifiedJedis(
            boolean jedis5,
            Set<HostAndPort> jedisClusterNodes,
            JedisClientConfig clientConfig,
            int maxAttempts,
            Duration maxTotalRetriesDuration) {
        super(jedis5, jedisClusterNodes, clientConfig);
    }

    // Provider constructors
    protected BaseUnifiedJedis(boolean jedis5, ConnectionProvider provider) {
        super(jedis5, provider);
    }

    protected BaseUnifiedJedis(
            boolean jedis5,
            ConnectionProvider provider,
            int maxAttempts,
            Duration maxTotalRetriesDuration) {
        super(jedis5, provider);
    }

    // Protected constructors for internal use
    protected BaseUnifiedJedis(
            boolean jedis5, GlideClient glideClient, JedisClientConfig jedisConfig) {
        super(jedis5, glideClient, jedisConfig);
    }

    protected BaseUnifiedJedis(
            boolean jedis5, GlideClusterClient glideClusterClient, JedisClientConfig jedisConfig) {
        super(jedis5, glideClusterClient, jedisConfig);
    }

    // Utility methods (copied from AbstractUnifiedJedis since they're private there)
    private static HostAndPort extractHostAndPort(URI uri) {
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        int port = uri.getPort() != -1 ? uri.getPort() : 6379;
        return new HostAndPort(host, port);
    }

    private static JedisClientConfig buildConfigFromURI(URI uri, JedisClientConfig baseConfig) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();

        // Copy settings from base config if provided
        if (baseConfig != null) {
            builder.socketTimeoutMillis(baseConfig.getSocketTimeoutMillis());
            builder.connectionTimeoutMillis(baseConfig.getConnectionTimeoutMillis());
            builder.blockingSocketTimeoutMillis(baseConfig.getBlockingSocketTimeoutMillis());
            builder.user(baseConfig.getUser());
            builder.password(baseConfig.getPassword());
            builder.database(baseConfig.getDatabase());
            builder.clientName(baseConfig.getClientName());
            builder.ssl(baseConfig.isSsl());
            builder.sslSocketFactory(baseConfig.getSslSocketFactory());
            builder.sslParameters(baseConfig.getSslParameters());
            builder.hostnameVerifier(baseConfig.getHostnameVerifier());
        }

        // Override with URI settings
        if ("rediss".equals(uri.getScheme())) {
            builder.ssl(true);
        }

        if (uri.getUserInfo() != null) {
            String decodedUserInfo = JedisURIHelper.decodeUserInfo(uri.getUserInfo());
            String[] userInfo = decodedUserInfo.split(":", 2);
            if (userInfo.length == 2) {
                builder.user(userInfo[0]);
                builder.password(userInfo[1]);
            } else {
                builder.password(userInfo[0]);
            }
        }

        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            try {
                builder.database(Integer.parseInt(path.substring(1)));
            } catch (NumberFormatException e) {
                // Ignore invalid database number
            }
        }

        return builder.build();
    }
}
