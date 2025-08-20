/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * Default implementation of JedisClientConfig with builder pattern. Provides sensible defaults and
 * allows for easy configuration.
 */
public class DefaultJedisClientConfig implements JedisClientConfig {

    public static final int DEFAULT_TIMEOUT_MILLIS = 2000;
    public static final int DEFAULT_DATABASE = 0;
    public static final RedisProtocol DEFAULT_PROTOCOL = RedisProtocol.RESP2;

    private final int connectionTimeoutMillis;
    private final int socketTimeoutMillis;
    private final int blockingSocketTimeoutMillis;
    private final String user;
    private final String password;
    private final int database;
    private final String clientName;
    private final boolean ssl;
    private final SSLSocketFactory sslSocketFactory;
    private final SSLParameters sslParameters;
    private final HostnameVerifier hostnameVerifier;
    private final SslOptions sslOptions;
    private final RedisProtocol redisProtocol;

    private DefaultJedisClientConfig(Builder builder) {
        this.connectionTimeoutMillis = builder.connectionTimeoutMillis;
        this.socketTimeoutMillis = builder.socketTimeoutMillis;
        this.blockingSocketTimeoutMillis = builder.blockingSocketTimeoutMillis;
        this.user = builder.user;
        this.password = builder.password;
        this.database = builder.database;
        this.clientName = builder.clientName;
        this.ssl = builder.ssl;
        this.sslSocketFactory = builder.sslSocketFactory;
        this.sslParameters = builder.sslParameters;
        this.hostnameVerifier = builder.hostnameVerifier;
        this.sslOptions = builder.sslOptions;
        this.redisProtocol = builder.redisProtocol;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    @Override
    public int getSocketTimeoutMillis() {
        return socketTimeoutMillis;
    }

    @Override
    public int getBlockingSocketTimeoutMillis() {
        return blockingSocketTimeoutMillis;
    }

    @Override
    public String getUser() {
        return user;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public int getDatabase() {
        return database;
    }

    @Override
    public String getClientName() {
        return clientName;
    }

    @Override
    public boolean isSsl() {
        return ssl;
    }

    @Override
    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    @Override
    public SSLParameters getSslParameters() {
        return SSLParametersUtils.copy(sslParameters);
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    @Override
    public SslOptions getSslOptions() {
        return sslOptions;
    }

    @Override
    public RedisProtocol getRedisProtocol() {
        return redisProtocol;
    }

    public static class Builder {
        private int connectionTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private int socketTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private int blockingSocketTimeoutMillis = 0; // 0 means no timeout
        private String user;
        private String password;
        private int database = DEFAULT_DATABASE;
        private String clientName;
        private boolean ssl = false;
        private SSLSocketFactory sslSocketFactory;
        private SSLParameters sslParameters;
        private HostnameVerifier hostnameVerifier;
        private SslOptions sslOptions;
        private RedisProtocol redisProtocol = DEFAULT_PROTOCOL;

        public Builder connectionTimeoutMillis(int connectionTimeoutMillis) {
            this.connectionTimeoutMillis = connectionTimeoutMillis;
            return this;
        }

        public Builder socketTimeoutMillis(int socketTimeoutMillis) {
            this.socketTimeoutMillis = socketTimeoutMillis;
            return this;
        }

        public Builder blockingSocketTimeoutMillis(int blockingSocketTimeoutMillis) {
            this.blockingSocketTimeoutMillis = blockingSocketTimeoutMillis;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(int database) {
            this.database = database;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        public Builder sslParameters(SSLParameters sslParameters) {
            this.sslParameters = SSLParametersUtils.copy(sslParameters);
            return this;
        }

        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        public Builder sslOptions(SslOptions sslOptions) {
            this.sslOptions = sslOptions;
            return this;
        }

        public Builder protocol(RedisProtocol redisProtocol) {
            this.redisProtocol = redisProtocol;
            return this;
        }

        public DefaultJedisClientConfig build() {
            return new DefaultJedisClientConfig(this);
        }
    }
}
