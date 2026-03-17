/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import java.net.URI;
import java.time.Duration;

/**
 * Redis URI configuration for Lettuce compatibility layer. Represents connection parameters for
 * connecting to a Redis/Valkey server.
 */
public class RedisURI {

    private final String host;
    private final int port;
    private final boolean ssl;
    private final String username;
    private final String password;
    private final int database;
    private final Duration timeout;

    private RedisURI(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.ssl = builder.ssl;
        this.username = builder.username;
        this.password = builder.password;
        this.database = builder.database;
        this.timeout = builder.timeout;
    }

    /**
     * Create a RedisURI from a URI string.
     *
     * @param uri the URI string (e.g., "redis://localhost:6379")
     * @return a new RedisURI instance
     */
    public static RedisURI create(String uri) {
        try {
            URI parsedUri = URI.create(uri);
            Builder builder = builder();

            if (parsedUri.getHost() != null) {
                builder.withHost(parsedUri.getHost());
            }

            if (parsedUri.getPort() != -1) {
                builder.withPort(parsedUri.getPort());
            }

            String scheme = parsedUri.getScheme();
            if ("rediss".equals(scheme)) {
                builder.withSsl(true);
            }

            String userInfo = parsedUri.getUserInfo();
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                if (parts.length == 2) {
                    builder.withAuthentication(parts[0], parts[1]);
                } else if (parts.length == 1) {
                    builder.withPassword(parts[0]);
                }
            }

            String path = parsedUri.getPath();
            if (path != null && path.length() > 1) {
                try {
                    int db = Integer.parseInt(path.substring(1));
                    builder.withDatabase(db);
                } catch (NumberFormatException e) {
                    // Ignore invalid database number
                }
            }

            return builder.build();
        } catch (Exception e) {
            throw new RedisException("Failed to parse Redis URI: " + uri, e);
        }
    }

    /**
     * Create a new Builder instance.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSsl() {
        return ssl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /** Builder for creating RedisURI instances. */
    public static class Builder {

        private String host = "localhost";
        private int port = 6379;
        private boolean ssl = false;
        private String username;
        private String password;
        private int database = 0;
        private Duration timeout;

        /**
         * Create a builder initialized with host and port.
         *
         * @param host the Redis host
         * @param port the Redis port
         * @return a new Builder instance
         */
        public static Builder redis(String host, int port) {
            Builder builder = new Builder();
            builder.host = host;
            builder.port = port;
            return builder;
        }

        /**
         * Create a builder initialized with host (default port 6379).
         *
         * @param host the Redis host
         * @return a new Builder instance
         */
        public static Builder redis(String host) {
            return redis(host, 6379);
        }

        /**
         * Set the Redis host.
         *
         * @param host the host
         * @return this builder
         */
        public Builder withHost(String host) {
            this.host = host;
            return this;
        }

        /**
         * Set the Redis port.
         *
         * @param port the port
         * @return this builder
         */
        public Builder withPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * Enable or disable SSL/TLS.
         *
         * @param ssl true to enable SSL
         * @return this builder
         */
        public Builder withSsl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        /**
         * Set the password for authentication.
         *
         * @param password the password
         * @return this builder
         */
        public Builder withPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * Set the password for authentication (alias for withPassword).
         *
         * @param password the password
         * @return this builder
         */
        public Builder auth(String password) {
            return withPassword(password);
        }

        /**
         * Set username and password for authentication.
         *
         * @param username the username
         * @param password the password
         * @return this builder
         */
        public Builder withAuthentication(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /**
         * Set the database number.
         *
         * @param database the database number
         * @return this builder
         */
        public Builder withDatabase(int database) {
            this.database = database;
            return this;
        }

        /**
         * Set the timeout for operations.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Build the RedisURI instance.
         *
         * @return a new RedisURI
         */
        public RedisURI build() {
            return new RedisURI(this);
        }
    }
}
