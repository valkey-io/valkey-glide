/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.LInsertOptions.InsertPosition;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SortBaseOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldGet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldIncrby;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow.BitOverflowControl;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.Offset;
import glide.api.models.commands.bitmap.BitFieldOptions.OffsetMultiplier;
import glide.api.models.commands.bitmap.BitFieldOptions.SignedEncoding;
import glide.api.models.commands.bitmap.BitFieldOptions.UnsignedEncoding;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.scan.HScanOptionsBinary;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import redis.clients.jedis.args.BitCountOption;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.args.ExpiryOption;
import redis.clients.jedis.args.ListDirection;
import redis.clients.jedis.args.ListPosition;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.HGetExParams;
import redis.clients.jedis.params.HSetExParams;
import redis.clients.jedis.params.LPosParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.KeyValue;
import redis.clients.jedis.util.Pool;

/**
 * Jedis compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like API while
 * using Valkey GLIDE underneath for improved performance, reliability, and feature support.
 *
 * <p>This compatibility layer allows existing Jedis applications to migrate to Valkey GLIDE with
 * minimal code changes while benefiting from GLIDE's advanced features such as:
 *
 * <ul>
 *   <li>Improved connection management and pooling
 *   <li>Better error handling and retry mechanisms
 *   <li>Enhanced performance optimizations
 *   <li>Support for the latest Valkey features
 * </ul>
 *
 * <p>The class implements the same method signatures as the original Jedis client, ensuring drop-in
 * compatibility for most use cases. Some advanced features may require migration to native GLIDE
 * APIs for optimal performance.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try (Jedis jedis = new Jedis("localhost", 6379)) {
 *     jedis.set("key", "value");
 *     String value = jedis.get("key");
 * }
 * }</pre>
 *
 * @author Valkey GLIDE Project Contributors
 * @since 1.0.0
 * @see GlideClient
 * @see JedisClientConfig
 */
public final class Jedis implements Closeable {

    private static final Logger logger = Logger.getLogger(Jedis.class.getName());

    /** Character encoding used for string-to-byte conversions in Valkey operations. */
    private static final Charset VALKEY_CHARSET = StandardCharsets.UTF_8;

    /** Keyword used in hash field expiration commands to specify the number of fields. */
    private static final String FIELDS_KEYWORD = "FIELDS";

    private volatile GlideClient glideClient; // Changed from final to volatile for lazy init
    private final boolean isPooled;
    private volatile String resourceId; // Changed from final to volatile for lazy init
    private final JedisClientConfig config;
    private Pool<Jedis> dataSource; // Following original Jedis pattern
    private volatile boolean closed = false;
    private volatile boolean lazyInitialized = false; // New field to track initialization

    // Store connection parameters for lazy initialization (nullable for pooled connections)
    private final String host;
    private final int port;

    /** Create a new Jedis instance with default localhost:6379 connection. */
    public Jedis() {
        this("localhost", 6379);
    }

    /**
     * Create a new Jedis instance with specified host and port.
     *
     * @param host the Valkey server host
     * @param port the Valkey server port
     */
    public Jedis(String host, int port) {
        this(host, port, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new Jedis instance with specified host, port and SSL configuration.
     *
     * @param host the Valkey server host
     * @param port the Valkey server port
     * @param useSsl whether to use SSL/TLS
     */
    public Jedis(String host, int port, boolean useSsl) {
        this(host, port, DefaultJedisClientConfig.builder().ssl(useSsl).build());
    }

    /**
     * Create a new Jedis instance with full configuration.
     *
     * @param host the Valkey server host
     * @param port the Valkey server port
     * @param config the jedis client configuration
     */
    public Jedis(String host, int port, JedisClientConfig config) {
        this.host = host;
        this.port = port;
        this.isPooled = false;
        this.dataSource = null;
        this.config = config;

        // Defer GlideClient creation until first Valkey operation (lazy initialization)
        // Configuration validation happens during mapping when GlideClient is created
        this.glideClient = null;
        this.resourceId = null;
        this.lazyInitialized = false;
    }

    /**
     * Lazy initialization of GlideClient. This defers native library loading until actually needed
     * for Valkey operations. Solves DataGrip compatibility issues where JDBC driver loading fails due
     * to native library restrictions in IDE environments.
     */
    private synchronized void ensureInitialized() {
        if (lazyInitialized) {
            return;
        }

        // Skip initialization for pooled connections (already initialized)
        if (isPooled) {
            lazyInitialized = true;
            return;
        }
        int i = 0;
        try {
            // Map Jedis config to GLIDE config

            GlideClientConfiguration glideConfig =
                    ConfigurationMapper.mapToGlideConfig(host, port, config);
            i++;
            this.glideClient = GlideClient.createClient(glideConfig).get();
            i++;
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
            i++;
            this.lazyInitialized = true;
        } catch (ConfigurationMapper.JedisConfigurationException e) {
            // Enhanced error handling for configuration conversion issues
            throw new JedisConnectionException(
                    "Failed to convert Jedis configuration to GLIDE: "
                            + e.getMessage()
                            + ". Please check your SSL/TLS certificate configuration or consider using PEM format"
                            + " certificates.",
                    e);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE client", e);
        } catch (RuntimeException e) {
            // Handle native library loading issues (like in DataGrip)
            if (e.getMessage() != null && e.getMessage().contains("native")) {
                throw new JedisConnectionException(
                        "Native library loading failed - this may be due to environment restrictions (e.g.,"
                                + " DataGrip). "
                                + i
                                + "Error: "
                                + e.getMessage(),
                        e);
            }
            throw e;
        }
    }

    /**
     * Create a new Jedis instance with comprehensive SSL/TLS configuration. This constructor provides
     * full control over SSL settings including custom socket factories and hostname verification.
     *
     * @param host the Valkey server host (must not be null)
     * @param port the Valkey server port (must be positive)
     * @param ssl whether to use SSL/TLS encryption for the connection
     * @param sslSocketFactory custom SSL socket factory for advanced SSL configuration (can be null
     *     for default)
     * @param sslParameters SSL parameters for fine-tuning SSL behavior (can be null for default)
     * @param hostnameVerifier hostname verifier for SSL certificate validation (can be null for
     *     default)
     * @throws JedisException if the connection cannot be established
     */
    public Jedis(
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    /**
     * Create Jedis with timeout configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param timeout the timeout in milliseconds
     */
    public Jedis(String host, int port, int timeout) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    /**
     * Create a new Jedis instance with host and port from HostAndPort and configuration.
     *
     * @param hostAndPort the host and port
     * @param config the jedis client configuration
     */
    public Jedis(HostAndPort hostAndPort, JedisClientConfig config) {
        this(hostAndPort.getHost(), hostAndPort.getPort(), config);
    }

    /**
     * Internal constructor for pooled connections. This follows the original Jedis pattern where the
     * pool reference is set separately.
     *
     * @param glideClient the underlying GLIDE client
     * @param config the client configuration
     */
    protected Jedis(GlideClient glideClient, JedisClientConfig config) {
        this.host = null; // Not needed for pooled connections
        this.port = 0; // Not needed for pooled connections
        this.glideClient = glideClient;
        this.isPooled = true;
        this.dataSource = null; // Will be set by setDataSource()
        this.config = config;
        this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        this.lazyInitialized = true; // Already initialized for pooled connections
    }

    /**
     * Set the string value of a key. If the key already exists, its value will be overwritten. This
     * is the most basic Valkey SET operation.
     *
     * @param key the key to set (must not be null)
     * @param value the value to set (must not be null)
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public String set(String key, String value) {
        return executeCommandWithGlide("SET", () -> glideClient.set(key, value).get());
    }

    /**
     * Set the binary value of a key.
     *
     * @param key the key
     * @param value the value
     * @return "OK" if successful
     */
    public String set(final byte[] key, final byte[] value) {
        return executeCommandWithGlide(
                "SET", () -> glideClient.set(GlideString.of(key), GlideString.of(value)).get());
    }

    /**
     * Set the string value of a key.
     *
     * @param key the key
     * @param value the value
     * @param params set parameters
     * @return "OK" if successful, null if not set due to conditions
     */
    public String set(final String key, final String value, final SetParams params) {
        return executeCommandWithGlide(
                "SET",
                () -> {
                    SetOptions options = convertSetParamsToSetOptions(params);
                    return glideClient.set(key, value, options).get();
                });
    }

    /**
     * Set the string value of a key.
     *
     * @param key the key
     * @param value the value
     * @param params set parameters
     * @return "OK" if successful, null if not set due to conditions
     */
    public String set(final byte[] key, final byte[] value, final SetParams params) {
        return executeCommandWithGlide(
                "SET",
                () -> {
                    SetOptions options = convertSetParamsToSetOptions(params);
                    return glideClient.set(GlideString.of(key), GlideString.of(value), options).get();
                });
    }

    /** Convert Jedis BitCountOption to GLIDE BitmapIndexType. */
    private static BitmapIndexType convertBitCountOptionToBitmapIndexType(BitCountOption option) {
        switch (option) {
            case BYTE:
                return BitmapIndexType.BYTE;
            case BIT:
                return BitmapIndexType.BIT;
            default:
                throw new IllegalArgumentException("Unknown BitCountOption: " + option);
        }
    }

    /** Convert Jedis ExpiryOption to GLIDE ExpireOptions. */
    private static ExpireOptions convertExpiryOptionToExpireOptions(ExpiryOption expiryOption) {
        switch (expiryOption) {
            case NX:
                return ExpireOptions.HAS_NO_EXPIRY;
            case XX:
                return ExpireOptions.HAS_EXISTING_EXPIRY;
            case GT:
                return ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT;
            case LT:
                return ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT;
            default:
                throw new IllegalArgumentException("Unknown ExpiryOption: " + expiryOption);
        }
    }

    /** Convert Jedis SetParams to GLIDE SetOptions. */
    private static SetOptions convertSetParamsToSetOptions(SetParams params) {
        SetOptions.SetOptionsBuilder builder = SetOptions.builder();

        // Handle existence conditions
        if (params.getExistenceCondition() != null) {
            switch (params.getExistenceCondition()) {
                case NX:
                    builder.conditionalSet(SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST);
                    break;
                case XX:
                    builder.conditionalSet(SetOptions.ConditionalSet.ONLY_IF_EXISTS);
                    break;
            }
        }

        // Handle expiration
        if (params.getExpirationType() != null) {
            switch (params.getExpirationType()) {
                case EX:
                    builder.expiry(SetOptions.Expiry.Seconds(params.getExpirationValue()));
                    break;
                case PX:
                    builder.expiry(SetOptions.Expiry.Milliseconds(params.getExpirationValue()));
                    break;
                case EXAT:
                    builder.expiry(SetOptions.Expiry.UnixSeconds(params.getExpirationValue()));
                    break;
                case PXAT:
                    builder.expiry(SetOptions.Expiry.UnixMilliseconds(params.getExpirationValue()));
                    break;
                case KEEPTTL:
                    builder.expiry(SetOptions.Expiry.KeepExisting());
                    break;
            }
        }

        // Handle GET option
        if (params.isGet()) {
            builder.returnOldValue(true);
        }

        return builder.build();
    }

    /** Add SetParams options to String command arguments. */
    private static void addSetParamsToArgs(List<String> args, SetParams params) {
        // Handle existence conditions
        if (params.getExistenceCondition() != null) {
            switch (params.getExistenceCondition()) {
                case NX:
                    args.add("NX");
                    break;
                case XX:
                    args.add("XX");
                    break;
            }
        }

        // Handle expiration
        if (params.getExpirationType() != null) {
            switch (params.getExpirationType()) {
                case EX:
                    args.add("EX");
                    args.add(String.valueOf(params.getExpirationValue()));
                    break;
                case PX:
                    args.add("PX");
                    args.add(String.valueOf(params.getExpirationValue()));
                    break;
                case EXAT:
                    args.add("EXAT");
                    args.add(String.valueOf(params.getExpirationValue()));
                    break;
                case PXAT:
                    args.add("PXAT");
                    args.add(String.valueOf(params.getExpirationValue()));
                    break;
                case KEEPTTL:
                    args.add("KEEPTTL");
                    break;
            }
        }
    }

    /** Add SetParams options to GlideString command arguments. */
    private static void addSetParamsToGlideStringArgs(List<GlideString> args, SetParams params) {
        // Handle existence conditions
        if (params.getExistenceCondition() != null) {
            switch (params.getExistenceCondition()) {
                case NX:
                    args.add(GlideString.of("NX"));
                    break;
                case XX:
                    args.add(GlideString.of("XX"));
                    break;
            }
        }

        // Handle expiration
        if (params.getExpirationType() != null) {
            switch (params.getExpirationType()) {
                case EX:
                    args.add(GlideString.of("EX"));
                    args.add(GlideString.of(String.valueOf(params.getExpirationValue())));
                    break;
                case PX:
                    args.add(GlideString.of("PX"));
                    args.add(GlideString.of(String.valueOf(params.getExpirationValue())));
                    break;
                case EXAT:
                    args.add(GlideString.of("EXAT"));
                    args.add(GlideString.of(String.valueOf(params.getExpirationValue())));
                    break;
                case PXAT:
                    args.add(GlideString.of("PXAT"));
                    args.add(GlideString.of(String.valueOf(params.getExpirationValue())));
                    break;
                case KEEPTTL:
                    args.add(GlideString.of("KEEPTTL"));
                    break;
            }
        }
    }

    /**
     * Convert Jedis GetExParams to GLIDE GetExOptions. This helper method translates between the
     * Jedis parameter format and the GLIDE native options format for GETEX operations.
     *
     * <p>Supported conversions:
     *
     * <ul>
     *   <li>EX → GetExOptions.Seconds()
     *   <li>PX → GetExOptions.Milliseconds()
     *   <li>EXAT → GetExOptions.UnixSeconds()
     *   <li>PXAT → GetExOptions.UnixMilliseconds()
     *   <li>PERSIST → GetExOptions.Persist()
     * </ul>
     *
     * @param params the Jedis GetExParams to convert (must not be null and must have expiration type
     *     set)
     * @return the equivalent GLIDE GetExOptions
     * @throws IllegalArgumentException if params is invalid or no expiration type is specified
     */
    private static GetExOptions convertGetExParamsToGetExOptions(GetExParams params) {
        if (params.getExpirationType() != null) {
            switch (params.getExpirationType()) {
                case EX:
                    return GetExOptions.Seconds(params.getExpirationValue());
                case PX:
                    return GetExOptions.Milliseconds(params.getExpirationValue());
                case EXAT:
                    return GetExOptions.UnixSeconds(params.getExpirationValue());
                case PXAT:
                    return GetExOptions.UnixMilliseconds(params.getExpirationValue());
                case PERSIST:
                    return GetExOptions.Persist();
            }
        }

        // Default case - should not happen with proper GetExParams usage
        throw new IllegalArgumentException("Invalid GetExParams: no expiration type specified");
    }

    /**
     * Get the string value of a key. This is the most basic Valkey GET operation.
     *
     * @param key the key to retrieve the value from (must not be null)
     * @return the value stored at the key, or null if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public String get(final String key) {
        return executeCommandWithGlide("GET", () -> glideClient.get(key).get());
    }

    /**
     * Get the value of a key.
     *
     * @param key the key
     * @return the value of the key, or null if the key does not exist
     */
    public byte[] get(final byte[] key) {
        return executeCommandWithGlide(
                "GET",
                () -> {
                    GlideString result = glideClient.get(GlideString.of(key)).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Test if the server is alive and responding. This command is often used for health checks and
     * connection testing. The server will respond with "PONG" if it's functioning correctly.
     *
     * @return "PONG" if the server is responding
     * @throws JedisException if the operation fails or connection is lost
     * @since Valkey 1.0.0
     */
    public String ping() {
        return executeCommandWithGlide("PING", () -> glideClient.ping().get());
    }

    /**
     * Test if the server is alive and echo back a custom message. This variant of PING allows you to
     * send a custom message that will be echoed back by the server, useful for testing message
     * integrity and round-trip functionality.
     *
     * @param message the message to echo back (must not be null)
     * @return the echoed message exactly as sent
     * @throws JedisException if the operation fails or connection is lost
     * @since Valkey 2.8.0
     */
    public String ping(String message) {
        return executeCommandWithGlide("PING", () -> glideClient.ping(message).get());
    }

    /**
     * Test if the server is alive with a custom message.
     *
     * @param message the message to echo back
     * @return the echoed message
     */
    public byte[] ping(final byte[] message) {
        return executeCommandWithGlide(
                "PING",
                () -> {
                    GlideString result = glideClient.ping(GlideString.of(message)).get();
                    return result.getBytes();
                });
    }

    /**
     * Select a database.
     *
     * @param index the database index
     * @return "OK" if successful
     */
    public String select(int index) {
        checkNotClosed();
        if (config.getDatabase() != index) {
            logger.warning("Database selection may behave differently in GLIDE compatibility mode");
        }
        // TO DO: GLIDE handles database selection differently. This is a placeholder implementation
        // In case of Glide, the databaseId is set in  GlideClientConfiguration. Will need to re call
        // the constructor for this to work.

        return "OK";
    }

    /**
     * Authenticate with the server.
     *
     * @param password the password
     * @return "OK" if successful
     */
    public String auth(String password) {
        checkNotClosed();
        // TO DO: GLIDE handles auth differently. This is a placeholder for runtime authentication.
        // In case of Glide, the auth is set in  ServerCredentials.
        // Will need to call the constructor again for this to work.

        return "OK";
    }

    /**
     * Authenticate with username and password.
     *
     * @param user the username
     * @param password the password
     * @return "OK" if successful
     */
    public String auth(String user, String password) {
        checkNotClosed();
        // TO DO: GLIDE handles auth differently. This is a placeholder for runtime authentication.
        // In case of Glide, the auth is set in  ServerCredentials.
        // Will need to call the constructor again for this to work.
        return "OK";
    }

    /**
     * Check if the connection is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Connect to the Valkey server.
     *
     * <p><strong>Note:</strong> This method is provided for Jedis API compatibility only. In the
     * Valkey GLIDE compatibility layer, connections are established automatically during object
     * construction. This method performs no operation since the underlying GLIDE client is already
     * connected when the Jedis object is created successfully.
     *
     * <p>Unlike the original Jedis client which uses lazy connection initialization, this
     * compatibility layer uses eager connection establishment for better error handling and
     * simplified resource management.
     *
     * @throws JedisException if the connection is already closed
     * @see #isClosed()
     * @see #close()
     */
    public void connect() {
        checkNotClosed();
        this.ensureInitialized();
        // No implementation required - connection is established in constructor.
        // This method exists solely for Jedis API compatibility.
    }

    /**
     * Get the client configuration.
     *
     * @return the configuration
     */
    public JedisClientConfig getConfig() {
        return config;
    }

    /**
     * Set the data source (pool) for this Jedis instance. This follows the original Jedis pattern for
     * pool management.
     *
     * @param jedisPool the pool that manages this instance
     */
    protected void setDataSource(Pool<Jedis> jedisPool) {
        this.dataSource = jedisPool;
    }

    /**
     * Close the connection. If this is a pooled connection, return it to the pool. Otherwise, close
     * the underlying GLIDE client.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;

        // Only unregister if we were actually initialized
        if (resourceId != null) {
            ResourceLifecycleManager.getInstance().unregisterResource(resourceId);
        }

        // Follow original Jedis pattern for pool management
        if (dataSource != null) {
            Pool<Jedis> pool = this.dataSource;
            this.dataSource = null;
            // Note: Original Jedis checks isBroken() here, but we don't have that concept
            // so we always return to pool as a good resource
            pool.returnResource(this);
        } else if (glideClient != null) { // Only close if initialized
            try {
                glideClient.close();
            } catch (Exception e) {
                throw new JedisException("Failed to close GLIDE client", e);
            }
        }

        // Cleanup temporary certificate files created during configuration conversion
        try {
            ConfigurationMapper.cleanupTempFiles();
        } catch (Exception e) {
            // Log warning but don't fail the close operation
            System.err.println("Warning: Failed to cleanup temporary certificate files:");
            e.printStackTrace();
        }
    }

    /**
     * Get the underlying GLIDE client. This method is for internal use by the pool.
     *
     * @return the GLIDE client
     */
    protected GlideClient getGlideClient() {
        ensureInitialized(); // Lazy initialization
        return glideClient;
    }

    /**
     * Check if this connection is from a pool.
     *
     * @return true if pooled, false otherwise
     */
    protected boolean isPooled() {
        return isPooled;
    }

    /** Reset the closed state for pooled connections. */
    protected void resetForReuse() {
        if (isPooled) {
            closed = false;
        }
    }

    /** Check if the connection is not closed and throw exception if it is. */
    private void checkNotClosed() {
        if (closed) {
            throw new JedisException("Connection is closed");
        }
    }

    /**
     * Functional interface for operations that can throw InterruptedException and ExecutionException.
     */
    @FunctionalInterface
    private interface GlideOperation<T> {
        T execute() throws InterruptedException, ExecutionException;
    }

    /**
     * Helper method that encapsulates the common try/catch pattern with connection checks. This
     * method handles the standard flow: checkNotClosed() -> ensureInitialized() -> execute operation
     * -> handle exceptions.
     *
     * @param operationName the name of the operation for error messages
     * @param operation the lambda containing the GLIDE client operation
     * @param <T> the return type of the operation
     * @return the result of the operation
     * @throws JedisException if the operation fails or connection is closed
     */
    private <T> T executeCommandWithGlide(String operationName, GlideOperation<T> operation) {
        checkNotClosed();
        ensureInitialized();
        try {
            return operation.execute();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException(operationName + " operation failed", e);
        }
    }

    /**
     * Delete one or more keys.
     *
     * @param key the key to delete
     * @return the number of keys that were removed
     */
    public long del(String key) {
        return executeCommandWithGlide("DEL", () -> glideClient.del(new String[] {key}).get());
    }

    /**
     * Delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public long del(String... keys) {
        return executeCommandWithGlide("DEL", () -> glideClient.del(keys).get());
    }

    /**
     * Delete one or more keys.
     *
     * @param key the key to delete
     * @return the number of keys that were removed
     */
    public long del(final byte[] key) {
        return executeCommandWithGlide(
                "DEL", () -> glideClient.del(new GlideString[] {GlideString.of(key)}).get());
    }

    /**
     * Delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public long del(final byte[]... keys) {
        return executeCommandWithGlide(
                "DEL",
                () -> {
                    GlideString[] glideKeys = convertToGlideStringArray(keys);
                    return glideClient.del(glideKeys).get();
                });
    }

    /**
     * Find all keys matching the given pattern.
     *
     * @param pattern the pattern to match (e.g., "prefix:*")
     * @return a set of keys matching the pattern
     */
    public Set<String> keys(String pattern) {
        checkNotClosed();
        try {
            Object result = glideClient.customCommand(new String[] {"KEYS", pattern}).get();

            // Handle different possible return types
            if (result instanceof String[]) {
                return new HashSet<>(Arrays.asList((String[]) result));
            } else if (result instanceof Object[]) {
                // Convert Object[] to String[]
                Object[] objArray = (Object[]) result;
                Set<String> keySet = new HashSet<>();
                for (Object obj : objArray) {
                    if (obj != null) {
                        keySet.add(obj.toString());
                    }
                }
                return keySet;
            } else if (result == null) {
                return new HashSet<>();
            } else {
                // Fallback: try to convert to string and split if needed
                logger.warning("Unexpected KEYS result type: " + result.getClass().getName());
                return new HashSet<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("KEYS operation failed", e);
        }
    }

    /**
     * Find all keys matching the given pattern.
     *
     * @param pattern the pattern to match (e.g., "prefix:*")
     * @return a set of keys matching the pattern
     */
    public Set<byte[]> keys(final byte[] pattern) {
        checkNotClosed();
        try {
            Object result =
                    glideClient
                            .customCommand(new GlideString[] {GlideString.of("KEYS"), GlideString.of(pattern)})
                            .get();

            // Handle different possible return types
            if (result instanceof GlideString[]) {
                GlideString[] glideArray = (GlideString[]) result;
                Set<byte[]> keySet = new HashSet<>();
                for (GlideString gs : glideArray) {
                    if (gs != null) {
                        keySet.add(gs.getBytes());
                    }
                }
                return keySet;
            } else if (result instanceof Object[]) {
                // Convert Object[] to byte[][]
                Object[] objArray = (Object[]) result;
                Set<byte[]> keySet = new HashSet<>();
                for (Object obj : objArray) {
                    if (obj instanceof GlideString) {
                        keySet.add(((GlideString) obj).getBytes());
                    } else if (obj != null) {
                        keySet.add(obj.toString().getBytes(VALKEY_CHARSET));
                    }
                }
                return keySet;
            } else {
                return new HashSet<>();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("KEYS operation failed", e);
        }
    }

    // ===== STRING COMMANDS =====

    /**
     * Set multiple key-value pairs.
     *
     * @param keysvalues alternating keys and values
     * @return "OK"
     */
    public String mset(String... keysvalues) {
        return executeCommandWithGlide(
                "MSET",
                () -> {
                    if (keysvalues.length % 2 == 1) {
                        throw new IllegalArgumentException("keyvalues must be of even length");
                    }
                    Map<String, String> keyValueMap = new HashMap<>();
                    for (int i = 0; i < keysvalues.length; i += 2) {
                        if (i + 1 < keysvalues.length) {
                            keyValueMap.put(keysvalues[i], keysvalues[i + 1]);
                        }
                    }
                    return glideClient.mset(keyValueMap).get();
                });
    }

    /**
     * Set multiple key-value pairs.
     *
     * @param keyValueMap map of keys to values
     * @return "OK"
     */
    public String mset(Map<String, String> keyValueMap) {
        return executeCommandWithGlide("MSET", () -> glideClient.mset(keyValueMap).get());
    }

    /**
     * Set multiple key-value pairs.
     *
     * @param keysvalues alternating keys and values
     * @return "OK"
     */
    public String mset(final byte[]... keysvalues) {
        return executeCommandWithGlide(
                "MSET",
                () -> {
                    if (keysvalues.length % 2 == 1) {
                        throw new IllegalArgumentException("keyvalues must be of even length");
                    }
                    Map<GlideString, GlideString> keyValueMap = new HashMap<>();
                    for (int i = 0; i < keysvalues.length; i += 2) {
                        if (i + 1 < keysvalues.length) {
                            keyValueMap.put(GlideString.of(keysvalues[i]), GlideString.of(keysvalues[i + 1]));
                        }
                    }
                    return glideClient.msetBinary(keyValueMap).get();
                });
    }

    /**
     * Get multiple values.
     *
     * @param keys the keys to get
     * @return list of values corresponding to the keys
     */
    public List<String> mget(String... keys) {
        return executeCommandWithGlide(
                "MGET",
                () -> {
                    String[] result = glideClient.mget(keys).get();
                    return Arrays.asList(result);
                });
    }

    /**
     * Get multiple values.
     *
     * @param keys the keys to get
     * @return list of values corresponding to the keys
     */
    public List<byte[]> mget(final byte[]... keys) {
        return executeCommandWithGlide(
                "MGET",
                () -> {
                    GlideString[] glideKeys = convertToGlideStringArray(keys);
                    GlideString[] result = glideClient.mget(glideKeys).get();
                    List<byte[]> byteList = new ArrayList<>();
                    for (GlideString gs : result) {
                        byteList.add(gs != null ? gs.getBytes() : null);
                    }
                    return byteList;
                });
    }

    /**
     * Set key to value if key does not exist.
     *
     * @param key the key
     * @param value the value
     * @return 1 if the key was set, 0 if the key already exists
     */
    public long setnx(String key, String value) {
        return executeCommandWithGlide(
                "SETNX",
                () -> {
                    Object result = glideClient.customCommand(new String[] {"SETNX", key, value}).get();
                    if (result instanceof Long) {
                        return (Long) result;
                    } else if (result instanceof Boolean) {
                        return ((Boolean) result) ? 1L : 0L;
                    } else {
                        return Long.parseLong(result.toString());
                    }
                });
    }

    /**
     * Set key to value only if key does not exist.
     *
     * @param key the key
     * @param value the value
     * @return 1 if the key was set, 0 if the key already exists
     */
    public long setnx(final byte[] key, final byte[] value) {
        return executeCommandWithGlide(
                "SETNX",
                () -> {
                    Object result =
                            glideClient
                                    .customCommand(
                                            new GlideString[] {
                                                GlideString.of("SETNX"), GlideString.of(key), GlideString.of(value)
                                            })
                                    .get();
                    if (result instanceof Long) {
                        return (Long) result;
                    } else if (result instanceof Boolean) {
                        return ((Boolean) result) ? 1L : 0L;
                    } else {
                        return Long.parseLong(result.toString());
                    }
                });
    }

    /**
     * Set key to value with expiration in seconds.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @param value the value
     * @return "OK"
     */
    public String setex(String key, long seconds, String value) {
        return executeCommandWithGlide(
                "SETEX",
                () -> {
                    Object result =
                            glideClient
                                    .customCommand(new String[] {"SETEX", key, String.valueOf(seconds), value})
                                    .get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Set key to value with expiration in seconds.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @param value the value
     * @return "OK"
     */
    public String setex(final byte[] key, final long seconds, final byte[] value) {
        return executeCommandWithGlide(
                "SETEX",
                () -> {
                    Object result =
                            glideClient
                                    .customCommand(
                                            new GlideString[] {
                                                GlideString.of("SETEX"),
                                                GlideString.of(key),
                                                GlideString.of(String.valueOf(seconds)),
                                                GlideString.of(value)
                                            })
                                    .get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Set key to value with expiration in milliseconds.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @param value the value
     * @return "OK"
     */
    public String psetex(String key, long milliseconds, String value) {
        return executeCommandWithGlide(
                "PSETEX",
                () -> {
                    Object result =
                            glideClient
                                    .customCommand(new String[] {"PSETEX", key, String.valueOf(milliseconds), value})
                                    .get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Set key to value with expiration in milliseconds.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @param value the value
     * @return "OK"
     */
    public String psetex(final byte[] key, final long milliseconds, final byte[] value) {
        return executeCommandWithGlide(
                "PSETEX",
                () -> {
                    Object result =
                            glideClient
                                    .customCommand(
                                            new GlideString[] {
                                                GlideString.of("PSETEX"),
                                                GlideString.of(key),
                                                GlideString.of(String.valueOf(milliseconds)),
                                                GlideString.of(value)
                                            })
                                    .get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Get old value and set new value (deprecated, use setGet instead).
     *
     * @param key the key
     * @param value the new value
     * @return the old value, or null if key did not exist
     * @deprecated Use {@link #setGet(String, String)} instead
     */
    @Deprecated
    public String getSet(final String key, final String value) {
        return executeCommandWithGlide(
                "GETSET",
                () -> {
                    Object result = glideClient.customCommand(new String[] {"GETSET", key, value}).get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Set new value and return old value.
     *
     * @deprecated Use {@link #setGet(byte[], byte[])} instead.
     * @param key the key
     * @param value the new value
     * @return the old value, or null if key did not exist
     */
    @Deprecated
    public byte[] getSet(final byte[] key, final byte[] value) {
        return executeCommandWithGlide(
                "GETSET",
                () -> {
                    Object result =
                            glideClient
                                    .customCommand(
                                            new GlideString[] {
                                                GlideString.of("GETSET"), GlideString.of(key), GlideString.of(value)
                                            })
                                    .get();
                    return result != null ? result.toString().getBytes(VALKEY_CHARSET) : null;
                });
    }

    /**
     * Set the string value of a key and return its old value. This is an atomic operation that
     * combines SET and GET operations. If the key does not exist, it will be created with the new
     * value and null will be returned.
     *
     * @param key the key to set
     * @param value the new value to set
     * @return the old value stored at the key, or null if the key did not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     */
    public String setGet(String key, String value) {
        checkNotClosed();
        try {
            // Use modern SET command with GET option for consistency
            Object result = glideClient.customCommand(new String[] {"SET", key, value, "GET"}).get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETGET operation failed", e);
        }
    }

    /**
     * Set the binary value of a key and return its old value. This is an atomic operation that
     * combines SET and GET operations. If the key does not exist, it will be created with the new
     * value and null will be returned.
     *
     * @param key the key to set
     * @param value the new binary value to set
     * @return the old binary value stored at the key, or null if the key did not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     */
    public byte[] setGet(final byte[] key, final byte[] value) {
        checkNotClosed();
        try {
            // Use modern SET command with GET option for consistency
            Object result =
                    glideClient
                            .customCommand(
                                    new GlideString[] {
                                        GlideString.of("SET"),
                                        GlideString.of(key),
                                        GlideString.of(value),
                                        GlideString.of("GET")
                                    })
                            .get();
            return result != null ? result.toString().getBytes(VALKEY_CHARSET) : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETGET operation failed", e);
        }
    }

    /**
     * Get old value and set new value with additional parameters.
     *
     * @param key the key
     * @param value the new value
     * @param params additional SET parameters
     * @return the old value, or null if key did not exist
     */
    public String setGet(final String key, final String value, final SetParams params) {
        return executeCommandWithGlide(
                "SETGET",
                () -> { // Build SET command with correct parameter order: SET key value [params] GET
                    List<String> args = new ArrayList<>();
                    args.add("SET");
                    args.add(key);
                    args.add(value);
                    addSetParamsToArgs(args, params);
                    // Add GET option AFTER SetParams
                    args.add("GET");

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Get old value and set new value with additional parameters.
     *
     * @param key the key
     * @param value the new value
     * @param params additional SET parameters
     * @return the old value, or null if key did not exist
     */
    public byte[] setGet(final byte[] key, final byte[] value, final SetParams params) {
        return executeCommandWithGlide(
                "SETGET",
                () -> { // Build SET command with correct parameter order: SET key value [params] GET
                    List<GlideString> args = new ArrayList<>();
                    args.add(GlideString.of("SET"));
                    args.add(GlideString.of(key));
                    args.add(GlideString.of(value));
                    addSetParamsToGlideStringArgs(args, params);
                    // Add GET option AFTER SetParams
                    args.add(GlideString.of("GET"));

                    Object result = glideClient.customCommand(args.toArray(new GlideString[0])).get();
                    return result != null ? result.toString().getBytes(VALKEY_CHARSET) : null;
                });
    }

    /**
     * Get value and delete key.
     *
     * @param key the key
     * @return the value, or null if key did not exist
     */
    public String getDel(final String key) {
        return executeCommandWithGlide("GETDEL", () -> glideClient.getdel(key).get());
    }

    /**
     * Get value and delete key.
     *
     * @param key the key
     * @return the value, or null if key did not exist
     */
    public byte[] getDel(final byte[] key) {
        return executeCommandWithGlide(
                "GETDEL",
                () -> {
                    GlideString result = glideClient.getdel(GlideString.of(key)).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Get the value of a key and optionally set its expiration. This command is similar to GET but
     * allows setting expiration parameters atomically with the retrieval operation.
     *
     * <p>The expiration can be set using various time units and formats:
     *
     * <ul>
     *   <li>EX seconds - Set expiration in seconds
     *   <li>PX milliseconds - Set expiration in milliseconds
     *   <li>EXAT timestamp - Set expiration as Unix timestamp in seconds
     *   <li>PXAT timestamp - Set expiration as Unix timestamp in milliseconds
     *   <li>PERSIST - Remove existing expiration
     * </ul>
     *
     * @param key the key to retrieve the value from (must not be null)
     * @param params expiration parameters specifying how to set the key's expiration
     * @return the value of the key, or null if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     * @see GetExParams
     */
    public String getEx(final String key, final GetExParams params) {
        return executeCommandWithGlide(
                "GETEX",
                () -> {
                    GetExOptions getExOptions = convertGetExParamsToGetExOptions(params);
                    return glideClient.getex(key, getExOptions).get();
                });
    }

    /**
     * Get the binary value of a key and optionally set its expiration. This command is similar to GET
     * but allows setting expiration parameters atomically with the retrieval operation.
     *
     * <p>The expiration can be set using various time units and formats:
     *
     * <ul>
     *   <li>EX seconds - Set expiration in seconds
     *   <li>PX milliseconds - Set expiration in milliseconds
     *   <li>EXAT timestamp - Set expiration as Unix timestamp in seconds
     *   <li>PXAT timestamp - Set expiration as Unix timestamp in milliseconds
     *   <li>PERSIST - Remove existing expiration
     * </ul>
     *
     * @param key the key to retrieve the value from (must not be null)
     * @param params expiration parameters specifying how to set the key's expiration
     * @return the binary value of the key, or null if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     * @see GetExParams
     */
    public byte[] getEx(final byte[] key, final GetExParams params) {
        return executeCommandWithGlide(
                "GETEX",
                () -> {
                    GetExOptions getExOptions = convertGetExParamsToGetExOptions(params);
                    GlideString result = glideClient.getex(GlideString.of(key), getExOptions).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Append a value to the end of the string stored at the specified key. If the key does not exist,
     * it is created and set as an empty string before performing the append operation. This operation
     * is atomic and efficient for building strings incrementally.
     *
     * <p>This command is useful for:
     *
     * <ul>
     *   <li>Building log entries or messages incrementally
     *   <li>Concatenating strings without retrieving the current value
     *   <li>Implementing counters or accumulators in string format
     * </ul>
     *
     * @param key the key where the string is stored (must not be null)
     * @param value the value to append to the existing string (must not be null)
     * @return the length of the string after the append operation (includes both original and
     *     appended content)
     * @throws JedisException if the operation fails or if the key contains a non-string value
     * @since Valkey 2.0.0
     */
    public long append(final String key, final String value) {
        return executeCommandWithGlide("APPEND", () -> glideClient.append(key, value).get());
    }

    /**
     * Append a binary value to the end of the string stored at the specified key. If the key does not
     * exist, it is created and set as an empty string before performing the append operation. This
     * operation is atomic and efficient for building binary strings incrementally.
     *
     * <p>This command is useful for:
     *
     * <ul>
     *   <li>Building binary log entries or data incrementally
     *   <li>Concatenating binary data without retrieving the current value
     *   <li>Implementing binary accumulators or buffers
     * </ul>
     *
     * @param key the key where the binary string is stored (must not be null)
     * @param value the binary value to append to the existing string (must not be null)
     * @return the length of the string after the append operation (includes both original and
     *     appended content)
     * @throws JedisException if the operation fails or if the key contains a non-string value
     * @since Valkey 2.0.0
     */
    public long append(final byte[] key, final byte[] value) {
        return executeCommandWithGlide(
                "APPEND", () -> glideClient.append(GlideString.of(key), GlideString.of(value)).get());
    }

    /**
     * Get the length of the string value stored at key.
     *
     * @param key the key
     * @return the length of the string, or 0 if key does not exist
     */
    public long strlen(String key) {
        return executeCommandWithGlide("STRLEN", () -> glideClient.strlen(key).get());
    }

    /**
     * Get the length of the string value stored at key.
     *
     * @param key the key
     * @return the length of the string, or 0 if key does not exist
     */
    public long strlen(final byte[] key) {
        return executeCommandWithGlide("STRLEN", () -> glideClient.strlen(GlideString.of(key)).get());
    }

    /**
     * Increment the integer value of key by 1.
     *
     * @param key the key
     * @return the value after increment
     */
    public long incr(String key) {
        return executeCommandWithGlide("INCR", () -> glideClient.incr(key).get());
    }

    /**
     * Increment the integer value of key by 1.
     *
     * @param key the key
     * @return the value after increment
     */
    public long incr(final byte[] key) {
        return executeCommandWithGlide("INCR", () -> glideClient.incr(GlideString.of(key)).get());
    }

    /**
     * Increment the integer value of key by amount.
     *
     * @param key the key
     * @param increment the amount to increment by
     * @return the value after increment
     */
    public long incrBy(String key, long increment) {
        return executeCommandWithGlide("INCRBY", () -> glideClient.incrBy(key, increment).get());
    }

    /**
     * Increment the float value of key by amount.
     *
     * @param key the key
     * @param increment the amount to increment by
     * @return the value after increment
     */
    public double incrByFloat(String key, double increment) {
        return executeCommandWithGlide(
                "INCRBYFLOAT", () -> glideClient.incrByFloat(key, increment).get());
    }

    /**
     * Increment the integer value of a key by the given amount (alternative method name).
     *
     * @param key the key
     * @param increment the increment value
     * @return the value after increment
     */
    public long incrBy(final byte[] key, final long increment) {
        return executeCommandWithGlide(
                "INCRBY", () -> glideClient.incrBy(GlideString.of(key), increment).get());
    }

    /**
     * Increment the float value of a key by the given amount.
     *
     * @param key the key
     * @param increment the increment value
     * @return the value after increment
     */
    public double incrByFloat(final byte[] key, final double increment) {
        return executeCommandWithGlide(
                "INCRBYFLOAT", () -> glideClient.incrByFloat(GlideString.of(key), increment).get());
    }

    /**
     * Decrement the integer value of key by 1.
     *
     * @param key the key
     * @return the value after decrement
     */
    public long decr(String key) {
        return executeCommandWithGlide("DECR", () -> glideClient.decr(key).get());
    }

    /**
     * Decrement the integer value of key by 1.
     *
     * @param key the key
     * @return the value after decrement
     */
    public long decr(final byte[] key) {
        return executeCommandWithGlide("DECR", () -> glideClient.decr(GlideString.of(key)).get());
    }

    /**
     * Decrement the integer value of key by amount.
     *
     * @param key the key
     * @param decrement the amount to decrement by
     * @return the value after decrement
     */
    public long decrBy(String key, long decrement) {
        return executeCommandWithGlide("DECRBY", () -> glideClient.decrBy(key, decrement).get());
    }

    /**
     * Decrement the integer value of a key by the given amount (alternative method name).
     *
     * @param key the key
     * @param decrement the decrement value
     * @return the value after decrement
     */
    public long decrBy(final byte[] key, final long decrement) {
        return executeCommandWithGlide(
                "DECRBY", () -> glideClient.decrBy(GlideString.of(key), decrement).get());
    }

    // ===== KEY MANAGEMENT COMMANDS =====

    /**
     * Delete one or more keys (already implemented above, but adding alias for completeness).
     *
     * @param key the key to delete
     * @return the number of keys that were removed
     */
    // del method already exists above

    /**
     * Asynchronously delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public long unlink(String... keys) {
        return executeCommandWithGlide("UNLINK", () -> glideClient.unlink(keys).get());
    }

    /**
     * Asynchronously delete a key.
     *
     * @param key the key to delete
     * @return the number of keys that were removed
     */
    public long unlink(final byte[] key) {
        return executeCommandWithGlide(
                "UNLINK", () -> glideClient.unlink(new GlideString[] {GlideString.of(key)}).get());
    }

    /**
     * Asynchronously delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public long unlink(final byte[]... keys) {
        return executeCommandWithGlide(
                "UNLINK",
                () -> {
                    GlideString[] glideKeys = convertToGlideStringArray(keys);
                    return glideClient.unlink(glideKeys).get();
                });
    }

    /**
     * Check if one or more keys exist.
     *
     * @param keys the keys to check
     * @return the number of keys that exist
     */
    public long exists(String... keys) {
        return executeCommandWithGlide("EXISTS", () -> glideClient.exists(keys).get());
    }

    /**
     * Check if a key exists.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean exists(final byte[] key) {
        return executeCommandWithGlide(
                "EXISTS", () -> glideClient.exists(new GlideString[] {GlideString.of(key)}).get() > 0);
    }

    /**
     * Check if a key exists.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean exists(final String key) {
        return executeCommandWithGlide(
                "EXISTS", () -> glideClient.exists(new String[] {key}).get() > 0);
    }

    /**
     * Check if a key exists (boolean version for Jedis compatibility).
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean keyExists(final String key) {
        return executeCommandWithGlide(
                "EXISTS", () -> glideClient.exists(new String[] {key}).get() > 0);
    }

    /**
     * Check if a key exists (boolean version for Jedis compatibility).
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean keyExists(final byte[] key) {
        return executeCommandWithGlide(
                "EXISTS", () -> glideClient.exists(new GlideString[] {GlideString.of(key)}).get() > 0);
    }

    /**
     * Check if one or more keys exist.
     *
     * @param keys the keys to check
     * @return the number of keys that exist
     */
    public long exists(final byte[]... keys) {
        return executeCommandWithGlide(
                "EXISTS",
                () -> {
                    GlideString[] glideKeys = convertToGlideStringArray(keys);
                    return glideClient.exists(glideKeys).get();
                });
    }

    /**
     * Get the type of a key.
     *
     * @param key the key
     * @return the type of the key
     */
    public String type(String key) {
        return executeCommandWithGlide("TYPE", () -> glideClient.type(key).get());
    }

    /**
     * Get the type of a key.
     *
     * @param key the key
     * @return the type of the key
     */
    public String type(final byte[] key) {
        return executeCommandWithGlide("TYPE", () -> glideClient.type(GlideString.of(key)).get());
    }

    /**
     * Find all keys matching the given pattern (already implemented above).
     *
     * @param pattern the pattern to match
     * @return a set of keys matching the pattern
     */
    // keys method already exists above

    /**
     * Get a random key from the currently selected database. This command is useful for sampling keys
     * from the database. The key is selected uniformly at random.
     *
     * @return a random key from the database, or null if the database is empty
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public String randomKey() {
        return executeCommandWithGlide("RANDOMKEY", () -> glideClient.randomKey().get());
    }

    /**
     * Rename a key.
     *
     * @param oldkey the old key name
     * @param newkey the new key name
     * @return "OK"
     */
    public String rename(String oldkey, String newkey) {
        return executeCommandWithGlide("RENAME", () -> glideClient.rename(oldkey, newkey).get());
    }

    /**
     * Rename a key.
     *
     * @param oldkey the old key name
     * @param newkey the new key name
     * @return "OK"
     */
    public String rename(final byte[] oldkey, final byte[] newkey) {
        return executeCommandWithGlide(
                "RENAME", () -> glideClient.rename(GlideString.of(oldkey), GlideString.of(newkey)).get());
    }

    /**
     * Rename a key if the new key does not exist.
     *
     * @param oldkey the old key name
     * @param newkey the new key name
     * @return 1 if the key was renamed, 0 if the new key already exists
     */
    public long renamenx(String oldkey, String newkey) {
        return executeCommandWithGlide(
                "RENAMENX",
                () -> {
                    Boolean result = glideClient.renamenx(oldkey, newkey).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Rename a key if the new key does not exist.
     *
     * @param oldkey the old key name
     * @param newkey the new key name
     * @return 1 if the key was renamed, 0 if the new key already exists
     */
    public long renamenx(final byte[] oldkey, final byte[] newkey) {
        return executeCommandWithGlide(
                "RENAMENX",
                () -> {
                    Boolean result =
                            glideClient.renamenx(GlideString.of(oldkey), GlideString.of(newkey)).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in seconds.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long expire(String key, long seconds) {
        return executeCommandWithGlide(
                "EXPIRE",
                () -> {
                    Boolean result = glideClient.expire(key, seconds).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in seconds.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long expire(final byte[] key, final long seconds) {
        return executeCommandWithGlide(
                "EXPIRE",
                () -> {
                    Boolean result = glideClient.expire(GlideString.of(key), seconds).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in seconds with expiry option.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @param expiryOption the expiry option
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long expire(final byte[] key, final long seconds, final ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "EXPIRE",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result = glideClient.expire(GlideString.of(key), seconds, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in seconds with expiry option.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @param expiryOption the expiry option
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long expire(final String key, final long seconds, final ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "EXPIRE",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result = glideClient.expire(key, seconds, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set the expiration time of a key as a Unix timestamp (seconds since January 1, 1970). The key
     * will be automatically deleted when the specified time is reached.
     *
     * @param key the key to set expiration for (must not be null)
     * @param unixTime expiration timestamp in seconds since Unix epoch
     * @return 1 if the expiration was set successfully, 0 if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 1.2.0
     */
    public long expireAt(String key, long unixTime) {
        return executeCommandWithGlide(
                "EXPIREAT",
                () -> {
                    Boolean result = glideClient.expireAt(key, unixTime).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific timestamp.
     *
     * @param key the key
     * @param unixTime expiration timestamp in seconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long expireAt(final byte[] key, final long unixTime) {
        return executeCommandWithGlide(
                "EXPIREAT",
                () -> {
                    Boolean result = glideClient.expireAt(GlideString.of(key), unixTime).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific timestamp with expiry option.
     *
     * @param key the key
     * @param unixTime expiration timestamp in seconds
     * @param expiryOption expiry option (NX, XX, GT, LT)
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long expireAt(String key, long unixTime, ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "EXPIREAT",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result = glideClient.expireAt(key, unixTime, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific timestamp with expiry option.
     *
     * @param key the key
     * @param unixTime expiration timestamp in seconds
     * @param expiryOption expiry option (NX, XX, GT, LT)
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long expireAt(byte[] key, long unixTime, ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "EXPIREAT",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result = glideClient.expireAt(GlideString.of(key), unixTime, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in milliseconds.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long pexpire(String key, long milliseconds) {
        return executeCommandWithGlide(
                "PEXPIRE",
                () -> {
                    Boolean result = glideClient.pexpire(key, milliseconds).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in milliseconds.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long pexpire(final byte[] key, final long milliseconds) {
        return executeCommandWithGlide(
                "PEXPIRE",
                () -> {
                    Boolean result = glideClient.pexpire(GlideString.of(key), milliseconds).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in milliseconds with expiry option.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @param expiryOption the expiry option
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long pexpire(final byte[] key, final long milliseconds, final ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "PEXPIRE",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result =
                            glideClient.pexpire(GlideString.of(key), milliseconds, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time in milliseconds with expiry option.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @param expiryOption the expiry option
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long pexpire(final String key, final long milliseconds, final ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "PEXPIRE",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result = glideClient.pexpire(key, milliseconds, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific millisecond timestamp.
     *
     * @param key the key
     * @param millisecondsTimestamp expiration timestamp in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long pexpireAt(String key, long millisecondsTimestamp) {
        return executeCommandWithGlide(
                "PEXPIREAT",
                () -> {
                    Boolean result = glideClient.pexpireAt(key, millisecondsTimestamp).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific millisecond timestamp.
     *
     * @param key the key
     * @param millisecondsTimestamp expiration timestamp in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long pexpireAt(final byte[] key, final long millisecondsTimestamp) {
        return executeCommandWithGlide(
                "PEXPIREAT",
                () -> {
                    Boolean result = glideClient.pexpireAt(GlideString.of(key), millisecondsTimestamp).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific millisecond timestamp with expiry option.
     *
     * @param key the key
     * @param millisecondsTimestamp expiration timestamp in milliseconds
     * @param expiryOption expiry option (NX, XX, GT, LT)
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long pexpireAt(String key, long millisecondsTimestamp, ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "PEXPIREAT",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result = glideClient.pexpireAt(key, millisecondsTimestamp, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Set expiration time at a specific millisecond timestamp with expiry option.
     *
     * @param key the key
     * @param millisecondsTimestamp expiration timestamp in milliseconds
     * @param expiryOption expiry option (NX, XX, GT, LT)
     * @return 1 if expiration was set, 0 if key does not exist or condition not met
     */
    public long pexpireAt(byte[] key, long millisecondsTimestamp, ExpiryOption expiryOption) {
        return executeCommandWithGlide(
                "PEXPIREAT",
                () -> {
                    ExpireOptions glideOption = convertExpiryOptionToExpireOptions(expiryOption);
                    Boolean result =
                            glideClient.pexpireAt(GlideString.of(key), millisecondsTimestamp, glideOption).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Get the expiration timestamp of a key in seconds.
     *
     * @param key the key
     * @return expiration timestamp in seconds, or -1 if key has no expiration, -2 if key does not
     *     exist
     */
    public long expireTime(String key) {
        return executeCommandWithGlide("EXPIRETIME", () -> glideClient.expiretime(key).get());
    }

    /**
     * Get the expiration timestamp of a key in milliseconds.
     *
     * @param key the key
     * @return expiration timestamp in milliseconds, or -1 if key has no expiration, -2 if key does
     *     not exist
     */
    public long pexpireTime(String key) {
        return executeCommandWithGlide("PEXPIRETIME", () -> glideClient.pexpiretime(key).get());
    }

    /**
     * Set expiration time at a specific timestamp in milliseconds.
     *
     * @param key the key
     * @param millisecondsTimestamp expiration timestamp in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public long pexpireat(final byte[] key, final long millisecondsTimestamp) {
        return executeCommandWithGlide(
                "PEXPIREAT",
                () -> {
                    Boolean result = glideClient.pexpireAt(GlideString.of(key), millisecondsTimestamp).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Get the expiration timestamp of a key in seconds.
     *
     * @param key the key
     * @return expiration timestamp in seconds, or -1 if key has no expiration, -2 if key does not
     *     exist
     */
    public long expireTime(final byte[] key) {
        return executeCommandWithGlide(
                "EXPIRETIME", () -> glideClient.expiretime(GlideString.of(key)).get());
    }

    /**
     * Get the expiration timestamp of a key in milliseconds.
     *
     * @param key the key
     * @return expiration timestamp in milliseconds, or -1 if key has no expiration, -2 if key does
     *     not exist
     */
    public long pexpireTime(final byte[] key) {
        return executeCommandWithGlide(
                "PEXPIRETIME", () -> glideClient.pexpiretime(GlideString.of(key)).get());
    }

    /**
     * Get the time to live of a key in seconds.
     *
     * @param key the key
     * @return time to live in seconds, or -1 if key has no expiration, -2 if key does not exist
     */
    public long ttl(String key) {
        return executeCommandWithGlide("TTL", () -> glideClient.ttl(key).get());
    }

    /**
     * Get the time to live of a key in seconds.
     *
     * @param key the key
     * @return time to live in seconds, or -1 if key has no expiration, -2 if key does not exist
     */
    public long ttl(final byte[] key) {
        return executeCommandWithGlide("TTL", () -> glideClient.ttl(GlideString.of(key)).get());
    }

    /**
     * Get the time to live of a key in milliseconds.
     *
     * @param key the key
     * @return time to live in milliseconds, or -1 if key has no expiration, -2 if key does not exist
     */
    public long pttl(String key) {
        return executeCommandWithGlide("PTTL", () -> glideClient.pttl(key).get());
    }

    /**
     * Get the time to live of a key in milliseconds.
     *
     * @param key the key
     * @return time to live in milliseconds, or -1 if key has no expiration, -2 if key does not exist
     */
    public long pttl(final byte[] key) {
        return executeCommandWithGlide("PTTL", () -> glideClient.pttl(GlideString.of(key)).get());
    }

    /**
     * Remove the expiration from a key.
     *
     * @param key the key
     * @return 1 if expiration was removed, 0 if key does not exist or has no expiration
     */
    public long persist(String key) {
        return executeCommandWithGlide(
                "PERSIST",
                () -> {
                    Boolean result = glideClient.persist(key).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Remove the expiration from a key.
     *
     * @param key the key
     * @return 1 if expiration was removed, 0 if key does not exist or has no expiration
     */
    public long persist(final byte[] key) {
        return executeCommandWithGlide(
                "PERSIST",
                () -> {
                    Boolean result = glideClient.persist(GlideString.of(key)).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Sort the elements in a list, set, or sorted set.
     *
     * @param key the key
     * @return the sorted elements
     */
    public List<String> sort(String key) {
        return executeCommandWithGlide(
                "SORT",
                () -> {
                    String[] result = glideClient.sort(key).get();
                    return Arrays.asList(result);
                });
    }

    /**
     * Sort the elements in a list, set, or sorted set with options.
     *
     * @param key the key
     * @param sortingParameters sorting parameters (BY, LIMIT, GET, ASC/DESC, ALPHA)
     * @return the sorted elements
     */
    public List<String> sort(String key, String... sortingParameters) {
        checkNotClosed();
        try {
            if (sortingParameters.length == 0) {
                // Simple sort without options
                String[] result = glideClient.sort(key).get();
                return Arrays.asList(result);
            } else {
                // Parse Jedis-style parameters into SortOptions
                SortOptions sortOptions = parseSortParameters(sortingParameters);
                String[] result = glideClient.sort(key, sortOptions).get();
                return Arrays.asList(result);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT operation failed", e);
        }
    }

    /**
     * Parse Jedis-style sort parameters into GLIDE SortOptions.
     *
     * @param params the Jedis-style parameters (BY, LIMIT, GET, ASC/DESC, ALPHA)
     * @return SortOptions object
     */
    private static SortOptions parseSortParameters(String[] params) {
        SortOptions.SortOptionsBuilder builder = SortOptions.builder();

        for (int i = 0; i < params.length; i++) {
            String param = params[i].toUpperCase();

            switch (param) {
                case "BY":
                    if (i + 1 < params.length) {
                        builder.byPattern(params[++i]);
                    }
                    break;

                case "LIMIT":
                    if (i + 2 < params.length) {
                        try {
                            long offset = Long.parseLong(params[++i]);
                            long count = Long.parseLong(params[++i]);
                            builder.limit(new SortBaseOptions.Limit(offset, count));
                        } catch (NumberFormatException e) {
                            // Skip invalid limit parameters
                        }
                    }
                    break;

                case "GET":
                    if (i + 1 < params.length) {
                        builder.getPattern(params[++i]);
                    }
                    break;

                case "ASC":
                    builder.orderBy(SortBaseOptions.OrderBy.ASC);
                    break;

                case "DESC":
                    builder.orderBy(SortBaseOptions.OrderBy.DESC);
                    break;

                case "ALPHA":
                    builder.alpha();
                    break;

                default:
                    // Unknown parameter, skip it
                    break;
            }
        }

        return builder.build();
    }

    /**
     * Serialize a key's value.
     *
     * @param key the key
     * @return the serialized value, or null if key does not exist
     */
    public byte[] dump(String key) {
        return executeCommandWithGlide("DUMP", () -> glideClient.dump(GlideString.of(key)).get());
    }

    /**
     * Serialize a key's value.
     *
     * @param key the key
     * @return the serialized value, or null if key does not exist
     */
    public byte[] dump(final byte[] key) {
        return executeCommandWithGlide("DUMP", () -> glideClient.dump(GlideString.of(key)).get());
    }

    /**
     * Deserialize a value and store it at a key.
     *
     * @param key the key
     * @param ttl time to live in milliseconds (0 for no expiration)
     * @param serializedValue the serialized value
     * @return "OK"
     */
    public String restore(String key, long ttl, byte[] serializedValue) {
        return executeCommandWithGlide(
                "RESTORE", () -> glideClient.restore(GlideString.of(key), ttl, serializedValue).get());
    }

    /**
     * Move a key to another Valkey instance.
     *
     * @param host destination host
     * @param port destination port
     * @param key the key to migrate
     * @param destinationDb destination database
     * @param timeout timeout in milliseconds
     * @return "OK" or "NOKEY"
     */
    public String migrate(String host, int port, String key, int destinationDb, int timeout) {
        checkNotClosed();
        try {
            Object result =
                    glideClient
                            .customCommand(
                                    new String[] {
                                        "MIGRATE",
                                        host,
                                        String.valueOf(port),
                                        key,
                                        String.valueOf(destinationDb),
                                        String.valueOf(timeout)
                                    })
                            .get();
            return result != null ? result.toString() : "OK";
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MIGRATE operation failed", e);
        }
    }

    /**
     * Move a key to another database.
     *
     * @param key the key
     * @param dbIndex destination database index
     * @return 1 if key was moved, 0 if key does not exist or already exists in target database
     */
    public long move(String key, int dbIndex) {
        return executeCommandWithGlide(
                "MOVE",
                () -> {
                    Boolean result = glideClient.move(key, dbIndex).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Move a key to another database.
     *
     * @param key the key
     * @param dbIndex destination database index
     * @return 1 if key was moved, 0 if key does not exist or already exists in target database
     */
    public long move(final byte[] key, final int dbIndex) {
        return executeCommandWithGlide(
                "MOVE",
                () -> {
                    Boolean result = glideClient.move(GlideString.of(key), dbIndex).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Helper method to convert GLIDE scan result to ScanResult format.
     *
     * @param result the GLIDE scan result
     * @return ScanResult with cursor and keys
     */
    /** Helper method to convert Jedis LPosParams to GLIDE LPosOptions. */
    private static LPosOptions convertLPosParamsToLPosOptions(LPosParams params) {
        LPosOptions.LPosOptionsBuilder builder = LPosOptions.builder();
        if (params.getRank() != null) {
            builder.rank((long) params.getRank());
        }
        if (params.getMaxlen() != null) {
            builder.maxLength((long) params.getMaxlen());
        }
        return builder.build();
    }

    /** Helper method to convert Jedis ListDirection to GLIDE ListDirection. */
    private static glide.api.models.commands.ListDirection convertToGlideListDirection(
            ListDirection jedisDirection) {
        return jedisDirection == ListDirection.LEFT
                ? glide.api.models.commands.ListDirection.LEFT
                : glide.api.models.commands.ListDirection.RIGHT;
    }

    /** Helper method to convert String array to GlideString array. */
    private static GlideString[] convertToGlideStringArray(String[] strings) {
        GlideString[] glideStrings = new GlideString[strings.length];
        for (int i = 0; i < strings.length; i++) {
            glideStrings[i] = GlideString.of(strings[i]);
        }
        return glideStrings;
    }

    /** Helper method to convert byte array to GlideString array. */
    private static GlideString[] convertToGlideStringArray(byte[][] bytes) {
        GlideString[] glideStrings = new GlideString[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            glideStrings[i] = GlideString.of(bytes[i]);
        }
        return glideStrings;
    }

    private static ScanResult<String> convertToScanResult(Object[] result) {
        if (result != null && result.length >= 2) {
            String newCursor = result[0].toString();
            Object keysObj = result[1];

            if (keysObj instanceof Object[]) {
                Object[] keysArray = (Object[]) keysObj;
                List<String> keys = new ArrayList<>();
                for (Object key : keysArray) {
                    keys.add(key != null ? key.toString() : null);
                }
                return new ScanResult<>(newCursor, keys);
            }
        }
        return new ScanResult<>("0", Collections.emptyList());
    }

    /** Convert ScanParams to GLIDE ScanOptions. */
    private static ScanOptions convertScanParamsToScanOptions(ScanParams params) {
        ScanOptions.ScanOptionsBuilder builder = ScanOptions.builder();

        if (params.getMatchPattern() != null) {
            builder.matchPattern(params.getMatchPattern());
        }

        if (params.getCount() != null) {
            builder.count(params.getCount());
        }

        if (params.getType() != null) {
            // Convert string type to ObjectType enum
            try {
                ScanOptions.ObjectType objectType =
                        ScanOptions.ObjectType.valueOf(params.getType().toUpperCase());
                builder.type(objectType);
            } catch (IllegalArgumentException e) {
                // Ignore invalid type, let GLIDE handle it
            }
        }

        return builder.build();
    }

    /**
     * Iterate over keys in the database with scan parameters.
     *
     * @param cursor the cursor
     * @param params the scan parameters
     * @return scan result with new cursor and keys
     */
    public ScanResult<String> scan(final String cursor, final ScanParams params) {
        return executeCommandWithGlide(
                "SCAN",
                () -> {
                    ScanOptions options = convertScanParamsToScanOptions(params);
                    Object[] result = glideClient.scan(cursor, options).get();
                    return convertToScanResult(result);
                });
    }

    /**
     * Iterate over keys in the database with scan parameters.
     *
     * @param cursor the cursor
     * @param params the scan parameters
     * @return scan result with new cursor and keys
     */
    public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params) {
        checkNotClosed();
        try {
            ScanOptions options = convertScanParamsToScanOptions(params);
            Object[] result = glideClient.scan(new String(cursor, VALKEY_CHARSET), options).get();

            // Convert to binary ScanResult
            if (result != null && result.length >= 2) {
                String newCursor = result[0].toString();
                Object keysObj = result[1];

                if (keysObj instanceof Object[]) {
                    Object[] keysArray = (Object[]) keysObj;
                    List<byte[]> keys = new ArrayList<>();
                    for (Object key : keysArray) {
                        keys.add(key != null ? key.toString().getBytes(VALKEY_CHARSET) : null);
                    }
                    return new ScanResult<>(newCursor, keys);
                }
            }
            return new ScanResult<>("0", Collections.emptyList());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Iterate over keys in the database.
     *
     * @param cursor the cursor
     * @return scan result with new cursor and keys
     */
    public ScanResult<byte[]> scan(final byte[] cursor) {
        checkNotClosed();
        try {
            Object[] result = glideClient.scan(new String(cursor, VALKEY_CHARSET)).get();

            // Convert to binary ScanResult
            if (result != null && result.length >= 2) {
                String newCursor = result[0].toString();
                Object keysObj = result[1];

                if (keysObj instanceof Object[]) {
                    Object[] keysArray = (Object[]) keysObj;
                    List<byte[]> keys = new ArrayList<>();
                    for (Object key : keysArray) {
                        keys.add(key != null ? key.toString().getBytes(VALKEY_CHARSET) : null);
                    }
                    return new ScanResult<>(newCursor, keys);
                }
            }
            return new ScanResult<>("0", Collections.emptyList());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Iterate over keys in the database.
     *
     * @param cursor the cursor
     * @return scan result with new cursor and keys
     */
    public ScanResult<String> scan(final String cursor) {
        return executeCommandWithGlide(
                "SCAN",
                () -> {
                    Object[] result = glideClient.scan(cursor).get();
                    return convertToScanResult(result);
                });
    }

    /**
     * Iterate over keys in the database with scan parameters and type filter.
     *
     * @param cursor the cursor
     * @param params the scan parameters
     * @param type the type filter
     * @return scan result with new cursor and keys
     */
    public ScanResult<String> scan(final String cursor, final ScanParams params, final String type) {
        checkNotClosed();
        try {
            ScanOptions options = convertScanParamsToScanOptions(params);
            if (type != null) {
                // Override type from params with explicit type parameter
                try {
                    ScanOptions.ObjectType objectType = ScanOptions.ObjectType.valueOf(type.toUpperCase());
                    options =
                            ScanOptions.builder()
                                    .matchPattern(params.getMatchPattern())
                                    .count(params.getCount())
                                    .type(objectType)
                                    .build();
                } catch (IllegalArgumentException e) {
                    // Invalid type, use params as-is
                }
            }
            Object[] result = glideClient.scan(cursor, options).get();
            return convertToScanResult(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Iterate over keys in the database with scan parameters and type filter.
     *
     * @param cursor the cursor
     * @param params the scan parameters
     * @param type the type filter
     * @return scan result with new cursor and keys
     */
    public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params, final byte[] type) {
        checkNotClosed();
        try {
            ScanOptions options = convertScanParamsToScanOptions(params);
            if (type != null) {
                // Override type from params with explicit type parameter
                try {
                    String typeStr = new String(type, VALKEY_CHARSET);
                    ScanOptions.ObjectType objectType = ScanOptions.ObjectType.valueOf(typeStr.toUpperCase());
                    options =
                            ScanOptions.builder()
                                    .matchPattern(params.getMatchPattern())
                                    .count(params.getCount())
                                    .type(objectType)
                                    .build();
                } catch (IllegalArgumentException e) {
                    // Invalid type, use params as-is
                }
            }
            Object[] result = glideClient.scan(new String(cursor, VALKEY_CHARSET), options).get();

            // Convert to binary ScanResult
            if (result != null && result.length >= 2) {
                String newCursor = result[0].toString();
                Object keysObj = result[1];

                if (keysObj instanceof Object[]) {
                    Object[] keysArray = (Object[]) keysObj;
                    List<byte[]> keys = new ArrayList<>();
                    for (Object key : keysArray) {
                        keys.add(key != null ? key.toString().getBytes(VALKEY_CHARSET) : null);
                    }
                    return new ScanResult<>(newCursor, keys);
                }
            }
            return new ScanResult<>("0", Collections.emptyList());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Update the last access time of keys.
     *
     * @param keys the keys to touch
     * @return the number of keys that were touched
     */
    public long touch(String... keys) {
        return executeCommandWithGlide("TOUCH", () -> glideClient.touch(keys).get());
    }

    /**
     * Update the last access time of a key.
     *
     * @param key the key to touch
     * @return the number of keys that were touched
     */
    public long touch(final byte[] key) {
        return executeCommandWithGlide(
                "TOUCH", () -> glideClient.touch(new GlideString[] {GlideString.of(key)}).get());
    }

    /**
     * Update the last access time of keys.
     *
     * @param keys the keys to touch
     * @return the number of keys that were touched
     */
    public long touch(final byte[]... keys) {
        return executeCommandWithGlide(
                "TOUCH",
                () -> {
                    GlideString[] glideKeys = convertToGlideStringArray(keys);
                    return glideClient.touch(glideKeys).get();
                });
    }

    /**
     * Copy a key to another key.
     *
     * @param srcKey source key
     * @param dstKey destination key
     * @param replace whether to replace the destination key if it exists
     * @return true if key was copied, false if source key does not exist or destination exists and
     *     replace is false
     */
    public boolean copy(String srcKey, String dstKey, boolean replace) {
        return executeCommandWithGlide("COPY", () -> glideClient.copy(srcKey, dstKey, replace).get());
    }

    /**
     * Copy a key to another key.
     *
     * @param srcKey source key
     * @param dstKey destination key
     * @param replace whether to replace the destination key if it exists
     * @return true if key was copied, false if source key does not exist or destination exists and
     *     replace is false
     */
    public boolean copy(final byte[] srcKey, final byte[] dstKey, final boolean replace) {
        return executeCommandWithGlide(
                "COPY",
                () -> glideClient.copy(GlideString.of(srcKey), GlideString.of(dstKey), replace).get());
    }

    /**
     * Copy a key to another key in a different database.
     *
     * @param srcKey source key
     * @param dstKey destination key
     * @param db destination database index
     * @param replace whether to replace the destination key if it exists
     * @return true if key was copied, false if source key does not exist or destination exists and
     *     replace is false
     */
    public boolean copy(
            final String srcKey, final String dstKey, final int db, final boolean replace) {
        checkNotClosed();
        try {
            // Use customCommand since GLIDE doesn't support database parameter
            String[] args =
                    replace
                            ? new String[] {"COPY", srcKey, dstKey, "DB", String.valueOf(db), "REPLACE"}
                            : new String[] {"COPY", srcKey, dstKey, "DB", String.valueOf(db)};

            Object result = glideClient.customCommand(args).get();
            if (result instanceof Long) {
                return ((Long) result).equals(1L);
            } else if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return "1".equals(result.toString());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("COPY operation failed", e);
        }
    }

    /**
     * Copy a key to another key in a different database.
     *
     * @param srcKey source key
     * @param dstKey destination key
     * @param db destination database index
     * @param replace whether to replace the destination key if it exists
     * @return true if key was copied, false if source key does not exist or destination exists and
     *     replace is false
     */
    public boolean copy(
            final byte[] srcKey, final byte[] dstKey, final int db, final boolean replace) {
        checkNotClosed();
        try {
            // Use customCommand since GLIDE doesn't support database parameter
            GlideString[] args =
                    replace
                            ? new GlideString[] {
                                GlideString.of("COPY"),
                                GlideString.of(srcKey),
                                GlideString.of(dstKey),
                                GlideString.of("DB"),
                                GlideString.of(String.valueOf(db)),
                                GlideString.of("REPLACE")
                            }
                            : new GlideString[] {
                                GlideString.of("COPY"),
                                GlideString.of(srcKey),
                                GlideString.of(dstKey),
                                GlideString.of("DB"),
                                GlideString.of(String.valueOf(db))
                            };

            Object result = glideClient.customCommand(args).get();
            if (result instanceof Long) {
                return ((Long) result).equals(1L);
            } else if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return "1".equals(result.toString());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("COPY operation failed", e);
        }
    }

    // ===== BITMAP COMMANDS =====

    /**
     * Sets or clears the bit at offset in the string value stored at key.
     *
     * @param key the key
     * @param offset the bit offset
     * @param value the bit value (true for 1, false for 0)
     * @return the original bit value stored at offset
     */
    public boolean setbit(String key, long offset, boolean value) {
        return executeCommandWithGlide(
                "SETBIT",
                () -> {
                    Long result = glideClient.setbit(key, offset, value ? 1L : 0L).get();
                    return result.equals(1L);
                });
    }

    /**
     * Sets or clears the bit at offset in the string value stored at key.
     *
     * @param key the key
     * @param offset the bit offset
     * @param value the bit value (true for 1, false for 0)
     * @return the original bit value stored at offset
     */
    public boolean setbit(final byte[] key, final long offset, final boolean value) {
        return executeCommandWithGlide(
                "SETBIT",
                () -> {
                    Long result = glideClient.setbit(GlideString.of(key), offset, value ? 1L : 0L).get();
                    return result.equals(1L);
                });
    }

    /**
     * Returns the bit value at offset in the string value stored at key.
     *
     * @param key the key
     * @param offset the bit offset
     * @return the bit value stored at offset
     */
    public boolean getbit(final String key, final long offset) {
        return executeCommandWithGlide(
                "GETBIT",
                () -> {
                    Long result = glideClient.getbit(key, offset).get();
                    return result.equals(1L);
                });
    }

    /**
     * Returns the bit value at offset in the string value stored at key.
     *
     * @param key the key
     * @param offset the bit offset
     * @return the bit value stored at offset
     */
    public boolean getbit(final byte[] key, final long offset) {
        return executeCommandWithGlide(
                "GETBIT",
                () -> {
                    Long result = glideClient.getbit(GlideString.of(key), offset).get();
                    return result.equals(1L);
                });
    }

    /**
     * Count the number of set bits (population counting) in a string. This operation counts all bits
     * set to 1 in the entire string value stored at the specified key.
     *
     * @param key the key containing the string value to analyze
     * @return the number of bits set to 1 (0 if key doesn't exist)
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long bitcount(final String key) {
        return executeCommandWithGlide("BITCOUNT", () -> glideClient.bitcount(key).get());
    }

    /**
     * Count the number of set bits (population counting) in a string within a specified byte range.
     * The range is specified by start and end byte offsets (inclusive).
     *
     * @param key the key containing the string value to analyze
     * @param start the start offset (byte index, can be negative for end-relative indexing)
     * @param end the end offset (byte index, can be negative for end-relative indexing)
     * @return the number of bits set to 1 within the specified range
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long bitcount(final String key, final long start, final long end) {
        return executeCommandWithGlide("BITCOUNT", () -> glideClient.bitcount(key, start, end).get());
    }

    /**
     * Count the number of set bits in a string.
     *
     * @param key the key
     * @return the number of bits set to 1
     */
    public long bitcount(final byte[] key) {
        return executeCommandWithGlide(
                "BITCOUNT", () -> glideClient.bitcount(GlideString.of(key)).get());
    }

    /**
     * Count the number of set bits in a string at a range.
     *
     * @param key the key
     * @param start the start offset (byte index)
     * @param end the end offset (byte index)
     * @return the number of bits set to 1
     */
    public long bitcount(final byte[] key, final long start, final long end) {
        return executeCommandWithGlide(
                "BITCOUNT", () -> glideClient.bitcount(GlideString.of(key), start, end).get());
    }

    /**
     * Count the number of set bits in a string at a range with bit count option.
     *
     * @param key the key
     * @param start the start offset
     * @param end the end offset
     * @param option the bit count option (BYTE or BIT)
     * @return the number of bits set to 1
     */
    public long bitcount(
            final byte[] key, final long start, final long end, final BitCountOption option) {
        return executeCommandWithGlide(
                "BITCOUNT",
                () -> {
                    BitmapIndexType indexType = convertBitCountOptionToBitmapIndexType(option);
                    return glideClient.bitcount(GlideString.of(key), start, end, indexType).get();
                });
    }

    /**
     * Count the number of set bits in a string at a range with bit count option.
     *
     * @param key the key
     * @param start the start offset
     * @param end the end offset
     * @param option the bit count option (BYTE or BIT)
     * @return the number of bits set to 1
     */
    public long bitcount(
            final String key, final long start, final long end, final BitCountOption option) {
        return executeCommandWithGlide(
                "BITCOUNT",
                () -> {
                    BitmapIndexType indexType = convertBitCountOptionToBitmapIndexType(option);
                    return glideClient.bitcount(key, start, end, indexType).get();
                });
    }

    /**
     * Return the position of the first bit set to 1 or 0 in a string.
     *
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0)
     * @return the position of the first bit set to the specified value, or -1 if not found
     */
    public long bitpos(final String key, final boolean value) {
        return executeCommandWithGlide("BITPOS", () -> glideClient.bitpos(key, value ? 1L : 0L).get());
    }

    /**
     * Return the position of the first bit set to 1 or 0 in a string within a range.
     *
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0) /** Return the position of
     *     the first bit set to 1 or 0 in a string.
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0)
     * @return the position of the first bit set to the specified value, or -1 if not found
     */
    public long bitpos(final byte[] key, final boolean value) {
        return executeCommandWithGlide(
                "BITPOS", () -> glideClient.bitpos(GlideString.of(key), value ? 1L : 0L).get());
    }

    /**
     * /** Return the position of the first bit set to 1 or 0 in a string with parameters.
     *
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0)
     * @param params the bitpos parameters
     * @return the position of the first bit set to the specified value, or -1 if not found
     */
    public long bitpos(final String key, final boolean value, final BitPosParams params) {
        checkNotClosed();
        try {
            long bitValue = value ? 1L : 0L;

            if (params.getStart() != null && params.getEnd() != null) {
                if (params.getModifier() != null) {
                    BitmapIndexType indexType = convertBitCountOptionToBitmapIndexType(params.getModifier());
                    return glideClient
                            .bitpos(key, bitValue, params.getStart(), params.getEnd(), indexType)
                            .get();
                } else {
                    return glideClient.bitpos(key, bitValue, params.getStart(), params.getEnd()).get();
                }
            } else if (params.getStart() != null) {
                return glideClient.bitpos(key, bitValue, params.getStart()).get();
            } else {
                return glideClient.bitpos(key, bitValue).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITPOS operation failed", e);
        }
    }

    /**
     * Return the position of the first bit set to 1 or 0 in a string with parameters.
     *
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0)
     * @param params the bitpos parameters
     * @return the position of the first bit set to the specified value, or -1 if not found
     */
    public long bitpos(final byte[] key, final boolean value, final BitPosParams params) {
        checkNotClosed();
        try {
            long bitValue = value ? 1L : 0L;
            GlideString glideKey = GlideString.of(key);

            if (params.getStart() != null && params.getEnd() != null) {
                if (params.getModifier() != null) {
                    BitmapIndexType indexType = convertBitCountOptionToBitmapIndexType(params.getModifier());
                    return glideClient
                            .bitpos(glideKey, bitValue, params.getStart(), params.getEnd(), indexType)
                            .get();
                } else {
                    return glideClient.bitpos(glideKey, bitValue, params.getStart(), params.getEnd()).get();
                }
            } else if (params.getStart() != null) {
                return glideClient.bitpos(glideKey, bitValue, params.getStart()).get();
            } else {
                return glideClient.bitpos(glideKey, bitValue).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITPOS operation failed", e);
        }
    }

    /**
     * Perform bitwise operations between strings.
     *
     * @param op the bitwise operation (AND, OR, XOR, NOT)
     * @param destKey the destination key where the result will be stored
     * @param srcKeys the source keys for the bitwise operation
     * @return the size of the string stored in the destination key
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long bitop(final BitOP op, final String destKey, final String... srcKeys) {
        checkNotClosed();
        try {
            BitwiseOperation operation;
            switch (op) {
                case AND:
                    operation = BitwiseOperation.AND;
                    break;
                case OR:
                    operation = BitwiseOperation.OR;
                    break;
                case XOR:
                    operation = BitwiseOperation.XOR;
                    break;
                case NOT:
                    operation = BitwiseOperation.NOT;
                    break;
                case DIFF:
                case DIFF1:
                case ANDOR:
                case ONE:
                    // These operations are not supported by GLIDE's BitwiseOperation enum
                    throw new UnsupportedOperationException(
                            "BITOP operation " + op + " is not supported by GLIDE");
                default:
                    throw new IllegalArgumentException("Unsupported bitwise operation: " + op);
            }
            return glideClient.bitop(operation, destKey, srcKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITOP operation failed", e);
        }
    }

    /**
     * Perform bitwise operations between strings.
     *
     * @param op the bitwise operation (AND, OR, XOR, NOT)
     * @param destKey the destination key where the result will be stored
     * @param srcKeys the source keys for the bitwise operation
     * @return the size of the string stored in the destination key
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long bitop(final BitOP op, final byte[] destKey, final byte[]... srcKeys) {
        checkNotClosed();
        try {
            BitwiseOperation operation;
            switch (op) {
                case AND:
                    operation = BitwiseOperation.AND;
                    break;
                case OR:
                    operation = BitwiseOperation.OR;
                    break;
                case XOR:
                    operation = BitwiseOperation.XOR;
                    break;
                case NOT:
                    operation = BitwiseOperation.NOT;
                    break;
                case DIFF:
                case DIFF1:
                case ANDOR:
                case ONE:
                    // These operations are not supported by GLIDE's BitwiseOperation enum
                    throw new UnsupportedOperationException(
                            "BITOP operation " + op + " is not supported by GLIDE");
                default:
                    throw new IllegalArgumentException("Unsupported bitwise operation: " + op);
            }
            String[] stringSrcKeys = new String[srcKeys.length];
            for (int i = 0; i < srcKeys.length; i++) {
                stringSrcKeys[i] = new String(srcKeys[i], VALKEY_CHARSET);
            }
            return glideClient.bitop(operation, new String(destKey, VALKEY_CHARSET), stringSrcKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITOP operation failed", e);
        }
    }

    /**
     * Perform multiple bitfield operations on a string.
     *
     * @param key the key
     * @param arguments the bitfield arguments
     * @return list of results from the bitfield operations
     */
    public List<Long> bitfield(final String key, final String... arguments) {
        checkNotClosed();
        try {
            if (arguments.length == 0) {
                // Empty arguments return empty array
                return Arrays.asList();
            }

            // Parse Jedis-style arguments into GLIDE BitFieldSubCommands
            BitFieldSubCommands[] subCommands = parseBitFieldArguments(arguments);
            Long[] result = glideClient.bitfield(key, subCommands).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITFIELD operation failed", e);
        }
    }

    /**
     * Perform multiple bitfield operations on a string.
     *
     * @param key the key
     * @param arguments the bitfield arguments
     * @return list of results from the bitfield operations
     */
    public List<Long> bitfield(final byte[] key, final byte[]... arguments) {
        checkNotClosed();
        try {
            if (arguments.length == 0) {
                // Empty arguments return empty array
                return Arrays.asList();
            }

            // Convert byte[] arguments to String arguments
            String[] stringArguments = new String[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                stringArguments[i] = new String(arguments[i], VALKEY_CHARSET);
            }

            // Parse Jedis-style arguments into GLIDE BitFieldSubCommands
            BitFieldSubCommands[] subCommands = parseBitFieldArguments(stringArguments);
            Long[] result = glideClient.bitfield(GlideString.of(key), subCommands).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITFIELD operation failed", e);
        }
    }

    /**
     * Perform read-only bitfield operations on a string.
     *
     * @param key the key
     * @param arguments the bitfield arguments
     * @return list of results from the bitfield operations
     */
    public List<Long> bitfieldReadonly(final String key, final String... arguments) {
        checkNotClosed();
        try {
            if (arguments.length == 0) {
                // Empty arguments return empty array
                return Arrays.asList();
            }

            // Parse Jedis-style arguments into GLIDE BitFieldReadOnlySubCommands (only GET operations)
            BitFieldReadOnlySubCommands[] subCommands = parseBitFieldReadOnlyArguments(arguments);
            Long[] result = glideClient.bitfieldReadOnly(key, subCommands).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITFIELD_RO operation failed", e);
        }
    }

    /**
     * Perform read-only bitfield operations on a string.
     *
     * @param key the key
     * @param arguments the bitfield arguments
     * @return list of results from the bitfield operations
     */
    public List<Long> bitfieldReadonly(final byte[] key, final byte[]... arguments) {
        checkNotClosed();
        try {
            if (arguments.length == 0) {
                // Empty arguments return empty array
                return Arrays.asList();
            }

            // Convert byte[] arguments to String arguments
            String[] stringArguments = new String[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                stringArguments[i] = new String(arguments[i], VALKEY_CHARSET);
            }

            // Parse Jedis-style arguments into GLIDE BitFieldReadOnlySubCommands (only GET operations)
            BitFieldReadOnlySubCommands[] subCommands = parseBitFieldReadOnlyArguments(stringArguments);
            Long[] result = glideClient.bitfieldReadOnly(GlideString.of(key), subCommands).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITFIELD_RO operation failed", e);
        }
    }

    /**
     * Parse Jedis-style bitfield arguments into GLIDE BitFieldSubCommands.
     *
     * @param args the Jedis-style arguments (GET/SET/INCRBY/OVERFLOW followed by parameters)
     * @return array of BitFieldSubCommands
     */
    private static BitFieldSubCommands[] parseBitFieldArguments(String[] args) {
        List<BitFieldSubCommands> commands = new ArrayList<>();

        for (int i = 0; i < args.length; ) {
            String command = args[i].toUpperCase();

            switch (command) {
                case "GET":
                    if (i + 2 < args.length) {
                        commands.add(createBitFieldGet(args[i + 1], args[i + 2]));
                        i += 3;
                    } else {
                        throw new IllegalArgumentException(
                                "GET command requires encoding and offset parameters");
                    }
                    break;

                case "SET":
                    if (i + 3 < args.length) {
                        long value = Long.parseLong(args[i + 3]);
                        commands.add(createBitFieldSet(args[i + 1], args[i + 2], value));
                        i += 4;
                    } else {
                        throw new IllegalArgumentException(
                                "SET command requires encoding, offset, and value parameters");
                    }
                    break;

                case "INCRBY":
                    if (i + 3 < args.length) {
                        long increment = Long.parseLong(args[i + 3]);
                        commands.add(createBitFieldIncrby(args[i + 1], args[i + 2], increment));
                        i += 4;
                    } else {
                        throw new IllegalArgumentException(
                                "INCRBY command requires encoding, offset, and increment parameters");
                    }
                    break;

                case "OVERFLOW":
                    if (i + 1 < args.length) {
                        BitOverflowControl control = parseOverflowControl(args[i + 1]);
                        commands.add(new BitFieldOverflow(control));
                        i += 2;
                    } else {
                        throw new IllegalArgumentException("OVERFLOW command requires control parameter");
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unknown bitfield command: " + command);
            }
        }

        return commands.toArray(new BitFieldSubCommands[0]);
    }

    /**
     * Parse Jedis-style bitfield arguments into GLIDE BitFieldReadOnlySubCommands (only GET
     * operations).
     *
     * @param args the Jedis-style arguments (only GET operations allowed)
     * @return array of BitFieldReadOnlySubCommands
     */
    private static BitFieldReadOnlySubCommands[] parseBitFieldReadOnlyArguments(String[] args) {
        List<BitFieldReadOnlySubCommands> commands = new ArrayList<>();

        for (int i = 0; i < args.length; ) {
            String command = args[i].toUpperCase();

            if ("GET".equals(command)) {
                if (i + 2 < args.length) {
                    commands.add(createBitFieldGet(args[i + 1], args[i + 2]));
                    i += 3;
                } else {
                    throw new IllegalArgumentException("GET command requires encoding and offset parameters");
                }
            } else {
                throw new IllegalArgumentException(
                        "BITFIELD_RO only supports GET operations, found: " + command);
            }
        }

        return commands.toArray(new BitFieldReadOnlySubCommands[0]);
    }

    /**
     * Parse encoding string into BitEncoding object.
     *
     * @param encodingStr encoding string (e.g., "u4", "i8")
     * @return BitEncoding object (UnsignedEncoding or SignedEncoding)
     */
    private static Object parseEncoding(String encodingStr) {
        if (encodingStr.startsWith("u")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            return new UnsignedEncoding(bits);
        } else if (encodingStr.startsWith("i")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            return new SignedEncoding(bits);
        } else {
            throw new IllegalArgumentException(
                    "Invalid encoding format: " + encodingStr + ". Must start with 'u' or 'i'");
        }
    }

    /**
     * Parse offset string into BitOffset object.
     *
     * @param offsetStr offset string (e.g., "0", "#1")
     * @return BitOffset object (Offset or OffsetMultiplier)
     */
    private static Object parseOffset(String offsetStr) {
        if (offsetStr.startsWith("#")) {
            long offset = Long.parseLong(offsetStr.substring(1));
            return new OffsetMultiplier(offset);
        } else {
            long offset = Long.parseLong(offsetStr);
            return new Offset(offset);
        }
    }

    /** Create BitFieldGet with proper interface types. */
    private static BitFieldGet createBitFieldGet(String encodingStr, String offsetStr) {
        if (encodingStr.startsWith("u")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            UnsignedEncoding encoding = new UnsignedEncoding(bits);

            if (offsetStr.startsWith("#")) {
                long offset = Long.parseLong(offsetStr.substring(1));
                return new BitFieldGet(encoding, new OffsetMultiplier(offset));
            } else {
                long offset = Long.parseLong(offsetStr);
                return new BitFieldGet(encoding, new Offset(offset));
            }
        } else if (encodingStr.startsWith("i")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            SignedEncoding encoding = new SignedEncoding(bits);

            if (offsetStr.startsWith("#")) {
                long offset = Long.parseLong(offsetStr.substring(1));
                return new BitFieldGet(encoding, new OffsetMultiplier(offset));
            } else {
                long offset = Long.parseLong(offsetStr);
                return new BitFieldGet(encoding, new Offset(offset));
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid encoding format: " + encodingStr + ". Must start with 'u' or 'i'");
        }
    }

    /** Create BitFieldSet with proper interface types. */
    private static BitFieldSet createBitFieldSet(String encodingStr, String offsetStr, long value) {
        if (encodingStr.startsWith("u")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            UnsignedEncoding encoding = new UnsignedEncoding(bits);

            if (offsetStr.startsWith("#")) {
                long offset = Long.parseLong(offsetStr.substring(1));
                return new BitFieldSet(encoding, new OffsetMultiplier(offset), value);
            } else {
                long offset = Long.parseLong(offsetStr);
                return new BitFieldSet(encoding, new Offset(offset), value);
            }
        } else if (encodingStr.startsWith("i")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            SignedEncoding encoding = new SignedEncoding(bits);

            if (offsetStr.startsWith("#")) {
                long offset = Long.parseLong(offsetStr.substring(1));
                return new BitFieldSet(encoding, new OffsetMultiplier(offset), value);
            } else {
                long offset = Long.parseLong(offsetStr);
                return new BitFieldSet(encoding, new Offset(offset), value);
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid encoding format: " + encodingStr + ". Must start with 'u' or 'i'");
        }
    }

    /** Create BitFieldIncrby with proper interface types. */
    private static BitFieldIncrby createBitFieldIncrby(
            String encodingStr, String offsetStr, long increment) {
        if (encodingStr.startsWith("u")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            UnsignedEncoding encoding = new UnsignedEncoding(bits);

            if (offsetStr.startsWith("#")) {
                long offset = Long.parseLong(offsetStr.substring(1));
                return new BitFieldIncrby(encoding, new OffsetMultiplier(offset), increment);
            } else {
                long offset = Long.parseLong(offsetStr);
                return new BitFieldIncrby(encoding, new Offset(offset), increment);
            }
        } else if (encodingStr.startsWith("i")) {
            long bits = Long.parseLong(encodingStr.substring(1));
            SignedEncoding encoding = new SignedEncoding(bits);

            if (offsetStr.startsWith("#")) {
                long offset = Long.parseLong(offsetStr.substring(1));
                return new BitFieldIncrby(encoding, new OffsetMultiplier(offset), increment);
            } else {
                long offset = Long.parseLong(offsetStr);
                return new BitFieldIncrby(encoding, new Offset(offset), increment);
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid encoding format: " + encodingStr + ". Must start with 'u' or 'i'");
        }
    }

    /**
     * Parse overflow control string into BitOverflowControl enum.
     *
     * @param controlStr control string (e.g., "WRAP", "SAT", "FAIL")
     * @return BitOverflowControl enum
     */
    private static BitOverflowControl parseOverflowControl(String controlStr) {
        switch (controlStr.toUpperCase()) {
            case "WRAP":
                return BitOverflowControl.WRAP;
            case "SAT":
                return BitOverflowControl.SAT;
            case "FAIL":
                return BitOverflowControl.FAIL;
            default:
                throw new IllegalArgumentException(
                        "Invalid overflow control: " + controlStr + ". Must be WRAP, SAT, or FAIL");
        }
    }

    /** Helper method to concatenate string arrays. */
    private static String[] concatenateArrays(String[] array1, String[] array2) {
        String[] result = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    // ===== HYPERLOGLOG COMMANDS =====

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified key. Creates a new
     * structure if the key does not exist. HyperLogLog is a probabilistic data structure used for
     * estimating the cardinality of large datasets.
     *
     * @param key the key of the HyperLogLog data structure
     * @param elements the elements to add to the HyperLogLog (must not be null)
     * @return 1 if the HyperLogLog is newly created or modified, 0 otherwise
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfadd(String key, String... elements) {
        return executeCommandWithGlide(
                "PFADD",
                () -> {
                    Boolean result = glideClient.pfadd(key, elements).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified key. Creates a new
     * structure if the key does not exist. HyperLogLog is a probabilistic data structure used for
     * estimating the cardinality of large datasets.
     *
     * @param key the key of the HyperLogLog data structure
     * @param elements the elements to add to the HyperLogLog (must not be null)
     * @return 1 if the HyperLogLog is newly created or modified, 0 otherwise
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfadd(final byte[] key, final byte[]... elements) {
        return executeCommandWithGlide(
                "PFADD",
                () -> {
                    String[] stringElements = new String[elements.length];
                    for (int i = 0; i < elements.length; i++) {
                        stringElements[i] = new String(elements[i], VALKEY_CHARSET);
                    }
                    Boolean result = glideClient.pfadd(new String(key, VALKEY_CHARSET), stringElements).get();
                    return result ? 1L : 0L;
                });
    }

    /**
     * Estimates the cardinality of the data stored in a HyperLogLog structure for a single key. The
     * cardinality is the approximate number of unique elements that have been added to the set.
     *
     * @param key the key of the HyperLogLog data structure
     * @return the approximated cardinality of the HyperLogLog data structure (0 if key doesn't exist)
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfcount(String key) {
        return executeCommandWithGlide("PFCOUNT", () -> glideClient.pfcount(new String[] {key}).get());
    }

    /**
     * Estimates the cardinality of the data stored in multiple HyperLogLog structures by calculating
     * the combined cardinality of multiple keys. This operation is equivalent to performing a union
     * of the HyperLogLog structures and then counting the cardinality.
     *
     * @param keys the keys of the HyperLogLog data structures to be analyzed (must not be empty)
     * @return the approximated cardinality of the combined HyperLogLog data structures
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfcount(String... keys) {
        return executeCommandWithGlide("PFCOUNT", () -> glideClient.pfcount(keys).get());
    }

    /**
     * Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it
     * is treated as one of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.
     *
     * @param destKey the key of the destination HyperLogLog where the merged data sets will be stored
     * @param sourceKeys the keys of the HyperLogLog structures to be merged
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public String pfmerge(String destKey, String... sourceKeys) {
        return executeCommandWithGlide("PFMERGE", () -> glideClient.pfmerge(destKey, sourceKeys).get());
    }

    /**
     * Estimates the cardinality of the data stored in a HyperLogLog structure for a single key.
     *
     * @param key the key of the HyperLogLog data structure
     * @return the approximated cardinality of the HyperLogLog data structure
     */
    public long pfcount(final byte[] key) {
        return executeCommandWithGlide(
                "PFCOUNT", () -> glideClient.pfcount(new String[] {new String(key, VALKEY_CHARSET)}).get());
    }

    /**
     * Estimates the cardinality of the union of multiple HyperLogLog data structures.
     *
     * @param keys the keys of the HyperLogLog data structures
     * @return the approximated cardinality of the union of the HyperLogLog data structures
     */
    public long pfcount(final byte[]... keys) {
        checkNotClosed();
        try {
            String[] stringKeys = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                stringKeys[i] = new String(keys[i], VALKEY_CHARSET);
            }
            return glideClient.pfcount(stringKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFCOUNT operation failed", e);
        }
    }

    /**
     * Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it
     * is treated as one of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.
     *
     * @param destKey the key of the destination HyperLogLog where the merged data sets will be stored
     * @param sourceKeys the keys of the HyperLogLog structures to be merged
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public String pfmerge(final byte[] destKey, final byte[]... sourceKeys) {
        checkNotClosed();
        try {
            String[] stringSourceKeys = new String[sourceKeys.length];
            for (int i = 0; i < sourceKeys.length; i++) {
                stringSourceKeys[i] = new String(sourceKeys[i], VALKEY_CHARSET);
            }
            return glideClient.pfmerge(new String(destKey, VALKEY_CHARSET), stringSourceKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFMERGE operation failed", e);
        }
    }

    // ========== sendCommand Methods ==========

    /**
     * Sends a Valkey command using the GLIDE client with full compatibility to original Jedis.
     *
     * <p>This method provides complete compatibility with the original Jedis sendCommand
     * functionality by using GLIDE's customCommand directly.
     *
     * <p><b>Compatibility Note:</b> This method provides full compatibility with original Jedis
     * sendCommand behavior. All Valkey commands and their optional arguments are supported through
     * GLIDE's customCommand.
     *
     * @param cmd the Valkey command to execute
     * @param args the command arguments as byte arrays
     * @return the command response from GLIDE customCommand
     * @throws JedisException if the command fails or is not supported
     * @throws UnsupportedOperationException if the command is not supported in the compatibility
     *     layer
     */
    public Object sendCommand(ProtocolCommand cmd, byte[]... args) {
        checkNotClosed();
        // Check if it's a Protocol.Command (standard Valkey commands)
        if (cmd instanceof Protocol.Command) {
            Protocol.Command command = (Protocol.Command) cmd;
            try {
                return executeProtocolCommandWithByteArgs(command.name(), args);
            } catch (Exception e) {
                throw new JedisException("Command execution failed: " + command.name(), e);
            }
        }

        // Future expansion: Add support for other ProtocolCommand implementations
        throw new UnsupportedOperationException(
                "ProtocolCommand type "
                        + cmd.getClass().getSimpleName()
                        + " is not supported in GLIDE compatibility layer. "
                        + "Supported types: Protocol.Command. Use specific typed methods instead.");
    }

    /**
     * Sends a command to the Valkey server with string arguments.
     *
     * <p><b>Compatibility Note:</b> This method provides full compatibility with original Jedis
     * sendCommand functionality. All Valkey commands and their optional arguments are supported.
     *
     * @param cmd the Valkey command to execute
     * @param args the command arguments as strings
     * @return the command response from GLIDE customCommand
     * @throws JedisException if the command fails or is not supported
     * @throws UnsupportedOperationException if the command is not supported in the compatibility
     *     layer
     */
    public Object sendCommand(ProtocolCommand cmd, String... args) {
        checkNotClosed();
        // Check if it's a Protocol.Command (standard Valkey commands)
        if (cmd instanceof Protocol.Command) {
            Protocol.Command command = (Protocol.Command) cmd;
            try {
                return executeProtocolCommandWithStringArgs(command.name(), args);
            } catch (Exception e) {
                throw new JedisException("Command execution failed: " + command.name(), e);
            }
        }

        // Future expansion: Add support for other ProtocolCommand implementations
        throw new UnsupportedOperationException(
                "ProtocolCommand type "
                        + cmd.getClass().getSimpleName()
                        + " is not supported in GLIDE compatibility layer. "
                        + "Supported types: Protocol.Command. Use specific typed methods instead.");
    }

    /**
     * Sends a command to the Valkey server without arguments.
     *
     * <p><b>Compatibility Note:</b> This method provides full compatibility with original Jedis
     * sendCommand functionality. All Valkey commands are supported.
     *
     * @param cmd the Valkey command to execute
     * @return the command response from GLIDE customCommand
     * @throws JedisException if the command fails or is not supported
     * @throws UnsupportedOperationException if the command is not supported in the compatibility
     *     layer
     */
    public Object sendCommand(ProtocolCommand cmd) {
        return sendCommand(cmd, new byte[0][]);
    }

    /**
     * Executes a Valkey command using GLIDE's string customCommand for optimal performance. This
     * avoids unnecessary string→byte[]→GlideString conversions.
     *
     * @param commandName the Valkey command name
     * @param args the command arguments as strings
     * @return the command response from GLIDE customCommand
     * @throws Exception if the command execution fails
     */
    private Object executeProtocolCommandWithStringArgs(String commandName, String... args)
            throws Exception {
        // Convert command and args to String array for GLIDE's customCommand(String[])
        String[] stringArgs = new String[args.length + 1];
        stringArgs[0] = commandName;
        System.arraycopy(args, 0, stringArgs, 1, args.length);

        try {
            return glideClient.customCommand(stringArgs).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Command " + commandName + " execution failed", e);
        }
    }

    /**
     * Executes a Valkey command using GLIDE's binary customCommand for byte array arguments. This
     * preserves binary data integrity for commands that need exact byte representation.
     *
     * @param commandName the Valkey command name
     * @param args the command arguments as byte arrays
     * @return the command response from GLIDE customCommand
     * @throws Exception if the command execution fails
     */
    private Object executeProtocolCommandWithByteArgs(String commandName, byte[]... args)
            throws Exception {
        // Convert command and args to GlideString array for GLIDE's customCommand(GlideString[])
        GlideString[] glideArgs = new GlideString[args.length + 1];
        glideArgs[0] = GlideString.of(commandName);
        for (int i = 0; i < args.length; i++) {
            glideArgs[i + 1] = GlideString.of(args[i]);
        }

        try {
            return glideClient.customCommand(glideArgs).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Command " + commandName + " execution failed", e);
        }
    }

    // ===== HASH COMMANDS =====

    /**
     * Sets the specified field in the hash stored at key to value.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the value to set
     * @return 1 if field is a new field in the hash and value was set, 0 if field already exists in
     *     the hash and the value was updated
     */
    public long hset(String key, String field, String value) {
        return executeCommandWithGlide(
                "HSET",
                () -> {
                    Map<String, String> fieldValueMap = new HashMap<>();
                    fieldValueMap.put(field, value);
                    return glideClient.hset(key, fieldValueMap).get();
                });
    }

    /**
     * Sets the specified field in the hash stored at key to value (binary version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the value to set
     * @return 1 if field is a new field in the hash and value was set, 0 if field already exists in
     *     the hash and the value was updated
     */
    public long hset(final byte[] key, final byte[] field, final byte[] value) {
        return executeCommandWithGlide(
                "HSET",
                () -> {
                    Map<GlideString, GlideString> fieldValueMap = new HashMap<>();
                    fieldValueMap.put(GlideString.of(field), GlideString.of(value));
                    return glideClient.hset(GlideString.of(key), fieldValueMap).get();
                });
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at key.
     *
     * @param key the key of the hash
     * @param hash a map of field-value pairs to set in the hash
     * @return the number of fields that were added
     */
    public long hset(String key, Map<String, String> hash) {
        return executeCommandWithGlide("HSET", () -> glideClient.hset(key, hash).get());
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at key (binary
     * version).
     *
     * @param key the key of the hash
     * @param hash a map of field-value pairs to set in the hash
     * @return the number of fields that were added
     */
    public long hset(final byte[] key, final Map<byte[], byte[]> hash) {
        checkNotClosed();
        ensureInitialized();
        try {
            Map<GlideString, GlideString> glideHash = new HashMap<>();
            for (Map.Entry<byte[], byte[]> entry : hash.entrySet()) {
                glideHash.put(GlideString.of(entry.getKey()), GlideString.of(entry.getValue()));
            }
            return glideClient.hset(GlideString.of(key), glideHash).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSET operation failed", e);
        }
    }

    /**
     * Returns the value associated with field in the hash stored at key.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @return the value associated with field, or null when field is not present in the hash or key
     *     does not exist
     */
    public String hget(String key, String field) {
        return executeCommandWithGlide("HGET", () -> glideClient.hget(key, field).get());
    }

    /**
     * Returns the value associated with field in the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @return the value associated with field, or null when field is not present in the hash or key
     *     does not exist
     */
    public byte[] hget(final byte[] key, final byte[] field) {
        return executeCommandWithGlide(
                "HGET",
                () -> {
                    GlideString result = glideClient.hget(GlideString.of(key), GlideString.of(field)).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at key. This command
     * overwrites any specified fields already existing in the hash.
     *
     * @param key the key of the hash
     * @param hash a map of field-value pairs to set in the hash
     * @return "OK"
     */
    public String hmset(String key, Map<String, String> hash) {
        checkNotClosed();
        ensureInitialized();
        try {
            // Use customCommand to execute HMSET and get the actual server response
            List<String> args = new ArrayList<>();
            args.add("HMSET");
            args.add(key);
            for (Map.Entry<String, String> entry : hash.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HMSET operation failed", e);
        }
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at key (binary
     * version).
     *
     * @param key the key of the hash
     * @param hash a map of field-value pairs to set in the hash
     * @return "OK"
     */
    public String hmset(final byte[] key, final Map<byte[], byte[]> hash) {
        checkNotClosed();
        ensureInitialized();
        try {
            // Use customCommand to execute HMSET and get the actual server response
            List<String> args = new ArrayList<>();
            args.add("HMSET");
            args.add(new String(key));
            for (Map.Entry<byte[], byte[]> entry : hash.entrySet()) {
                args.add(new String(entry.getKey()));
                args.add(new String(entry.getValue()));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HMSET operation failed", e);
        }
    }

    /**
     * Returns the values associated with the specified fields in the hash stored at key.
     *
     * @param key the key of the hash
     * @param fields the fields in the hash
     * @return a list of values associated with the given fields, in the same order as they are
     *     requested
     */
    public List<String> hmget(String key, String... fields) {
        return executeCommandWithGlide(
                "HMGET",
                () -> {
                    String[] result = glideClient.hmget(key, fields).get();
                    return Arrays.asList(result);
                });
    }

    /**
     * Returns the values associated with the specified fields in the hash stored at key (binary
     * version).
     *
     * @param key the key of the hash
     * @param fields the fields in the hash
     * @return a list of values associated with the given fields, in the same order as they are
     *     requested
     */
    public List<byte[]> hmget(final byte[] key, final byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] glideFields = convertToGlideStringArray(fields);
            GlideString[] result = glideClient.hmget(GlideString.of(key), glideFields).get();
            List<byte[]> byteResult = new ArrayList<>();
            for (GlideString gs : result) {
                byteResult.add(gs != null ? gs.getBytes() : null);
            }
            return byteResult;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HMGET operation failed", e);
        }
    }

    /**
     * Returns all fields and values of the hash stored at key.
     *
     * @param key the key of the hash
     * @return a map of fields and their values stored in the hash, or an empty map when key does not
     *     exist
     */
    public Map<String, String> hgetAll(String key) {
        return executeCommandWithGlide("HGETALL", () -> glideClient.hgetall(key).get());
    }

    /**
     * Returns all fields and values of the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @return a map of fields and their values stored in the hash, or an empty map when key does not
     *     exist
     */
    public Map<byte[], byte[]> hgetAll(final byte[] key) {
        checkNotClosed();
        ensureInitialized();
        try {
            Map<GlideString, GlideString> result = glideClient.hgetall(GlideString.of(key)).get();
            Map<byte[], byte[]> byteResult = new HashMap<>();
            for (Map.Entry<GlideString, GlideString> entry : result.entrySet()) {
                byteResult.put(entry.getKey().getBytes(), entry.getValue().getBytes());
            }
            return byteResult;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HGETALL operation failed", e);
        }
    }

    /**
     * Removes the specified fields from the hash stored at key.
     *
     * @param key the key of the hash
     * @param fields the fields to remove from the hash
     * @return the number of fields that were removed from the hash, not including specified but non
     *     existing fields
     */
    public long hdel(String key, String... fields) {
        return executeCommandWithGlide("HDEL", () -> glideClient.hdel(key, fields).get());
    }

    /**
     * Removes the specified fields from the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @param fields the fields to remove from the hash
     * @return the number of fields that were removed from the hash, not including specified but non
     *     existing fields
     */
    public long hdel(final byte[] key, final byte[]... fields) {
        return executeCommandWithGlide(
                "HDEL",
                () -> {
                    GlideString[] glideFields = convertToGlideStringArray(fields);
                    return glideClient.hdel(GlideString.of(key), glideFields).get();
                });
    }

    /**
     * Returns if field is an existing field in the hash stored at key.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @return true if the hash contains field, false if the hash does not contain field, or key does
     *     not exist
     */
    public boolean hexists(String key, String field) {
        return executeCommandWithGlide("HEXISTS", () -> glideClient.hexists(key, field).get());
    }

    /**
     * Returns if field is an existing field in the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @return true if the hash contains field, false if the hash does not contain field, or key does
     *     not exist
     */
    public boolean hexists(final byte[] key, final byte[] field) {
        return executeCommandWithGlide(
                "HEXISTS", () -> glideClient.hexists(GlideString.of(key), GlideString.of(field)).get());
    }

    /**
     * Returns the number of fields contained in the hash stored at key.
     *
     * @param key the key of the hash
     * @return the number of fields in the hash, or 0 when key does not exist
     */
    public long hlen(String key) {
        return executeCommandWithGlide("HLEN", () -> glideClient.hlen(key).get());
    }

    /**
     * Returns the number of fields contained in the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @return the number of fields in the hash, or 0 when key does not exist
     */
    public long hlen(final byte[] key) {
        return executeCommandWithGlide("HLEN", () -> glideClient.hlen(GlideString.of(key)).get());
    }

    /**
     * Returns all field names in the hash stored at key.
     *
     * @param key the key of the hash
     * @return a set of field names in the hash, or an empty set when key does not exist
     */
    public Set<String> hkeys(String key) {
        return executeCommandWithGlide(
                "HKEYS",
                () -> {
                    String[] keys = glideClient.hkeys(key).get();
                    return new HashSet<>(Arrays.asList(keys));
                });
    }

    /**
     * Returns all field names in the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @return a set of field names in the hash, or an empty set when key does not exist
     */
    public Set<byte[]> hkeys(final byte[] key) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] keys = glideClient.hkeys(GlideString.of(key)).get();
            Set<byte[]> byteKeys = new HashSet<>();
            for (GlideString gs : keys) {
                byteKeys.add(gs.getBytes());
            }
            return byteKeys;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HKEYS operation failed", e);
        }
    }

    /**
     * Returns all values in the hash stored at key.
     *
     * @param key the key of the hash
     * @return a list of values in the hash, or an empty list when key does not exist
     */
    public List<String> hvals(String key) {
        return executeCommandWithGlide(
                "HVALS",
                () -> {
                    String[] values = glideClient.hvals(key).get();
                    return Arrays.asList(values);
                });
    }

    /**
     * Returns all values in the hash stored at key (binary version).
     *
     * @param key the key of the hash
     * @return a list of values in the hash, or an empty list when key does not exist
     */
    public List<byte[]> hvals(final byte[] key) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] values = glideClient.hvals(GlideString.of(key)).get();
            List<byte[]> byteValues = new ArrayList<>();
            for (GlideString gs : values) {
                byteValues.add(gs.getBytes());
            }
            return byteValues;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HVALS operation failed", e);
        }
    }

    /**
     * Increments the number stored at field in the hash stored at key by increment.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the increment value
     * @return the value at field after the increment operation
     */
    public long hincrBy(String key, String field, long value) {
        return executeCommandWithGlide("HINCRBY", () -> glideClient.hincrBy(key, field, value).get());
    }

    /**
     * Increments the number stored at field in the hash stored at key by increment (binary version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the increment value
     * @return the value at field after the increment operation
     */
    public long hincrBy(final byte[] key, final byte[] field, final long value) {
        return executeCommandWithGlide(
                "HINCRBY",
                () -> glideClient.hincrBy(GlideString.of(key), GlideString.of(field), value).get());
    }

    /**
     * Increment the specified field of a hash stored at key, and representing a floating point
     * number, by the specified increment.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the increment value
     * @return the value at field after the increment operation
     */
    public double hincrByFloat(String key, String field, double value) {
        return executeCommandWithGlide(
                "HINCRBYFLOAT", () -> glideClient.hincrByFloat(key, field, value).get());
    }

    /**
     * Increment the specified field of a hash stored at key, and representing a floating point
     * number, by the specified increment (binary version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the increment value
     * @return the value at field after the increment operation
     */
    public double hincrByFloat(final byte[] key, final byte[] field, final double value) {
        return executeCommandWithGlide(
                "HINCRBYFLOAT",
                () -> glideClient.hincrByFloat(GlideString.of(key), GlideString.of(field), value).get());
    }

    /**
     * Sets field in the hash stored at key to value, only if field does not yet exist.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the value to set
     * @return 1 if field is a new field in the hash and value was set, 0 if field already exists in
     *     the hash and no operation was performed
     */
    public long hsetnx(String key, String field, String value) {
        return executeCommandWithGlide(
                "HSETNX",
                () -> {
                    return glideClient.hsetnx(key, field, value).get() ? 1L : 0L;
                });
    }

    /**
     * Sets field in the hash stored at key to value, only if field does not yet exist (binary
     * version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @param value the value to set
     * @return 1 if field is a new field in the hash and value was set, 0 if field already exists in
     *     the hash and no operation was performed
     */
    public long hsetnx(final byte[] key, final byte[] field, final byte[] value) {
        return executeCommandWithGlide(
                "HSETNX",
                () -> {
                    return glideClient
                                    .hsetnx(GlideString.of(key), GlideString.of(field), GlideString.of(value))
                                    .get()
                            ? 1L
                            : 0L;
                });
    }

    /**
     * Returns the string length of the value associated with field in the hash stored at key.
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @return the string length of the value associated with field, or 0 when field is not present in
     *     the hash or key does not exist
     */
    public long hstrlen(String key, String field) {
        return executeCommandWithGlide("HSTRLEN", () -> glideClient.hstrlen(key, field).get());
    }

    /**
     * Returns the string length of the value associated with field in the hash stored at key (binary
     * version).
     *
     * @param key the key of the hash
     * @param field the field in the hash
     * @return the string length of the value associated with field, or 0 when field is not present in
     *     the hash or key does not exist
     */
    public long hstrlen(final byte[] key, final byte[] field) {
        return executeCommandWithGlide(
                "HSTRLEN", () -> glideClient.hstrlen(GlideString.of(key), GlideString.of(field)).get());
    }

    /**
     * Returns a random field from the hash value stored at key.
     *
     * @param key the key of the hash
     * @return a random field from the hash, or null when key does not exist
     */
    public String hrandfield(String key) {
        return executeCommandWithGlide("HRANDFIELD", () -> glideClient.hrandfield(key).get());
    }

    /**
     * Returns a random field from the hash value stored at key (binary version).
     *
     * @param key the key of the hash
     * @return a random field from the hash, or null when key does not exist
     */
    public byte[] hrandfield(final byte[] key) {
        return executeCommandWithGlide(
                "HRANDFIELD",
                () -> {
                    GlideString result = glideClient.hrandfield(GlideString.of(key)).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Returns an array of random fields from the hash value stored at key.
     *
     * @param key the key of the hash
     * @param count the number of fields to return
     * @return an array of random fields from the hash
     */
    public List<String> hrandfield(String key, long count) {
        return executeCommandWithGlide(
                "HRANDFIELD",
                () -> {
                    String[] fields = glideClient.hrandfieldWithCount(key, count).get();
                    return Arrays.asList(fields);
                });
    }

    /**
     * Returns an array of random fields from the hash value stored at key (binary version).
     *
     * @param key the key of the hash
     * @param count the number of fields to return
     * @return an array of random fields from the hash
     */
    public List<byte[]> hrandfield(final byte[] key, final long count) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] fields = glideClient.hrandfieldWithCount(GlideString.of(key), count).get();
            List<byte[]> byteFields = new ArrayList<>();
            for (GlideString gs : fields) {
                byteFields.add(gs.getBytes());
            }
            return byteFields;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HRANDFIELD operation failed", e);
        }
    }

    /**
     * Returns an array of random field-value pairs from the hash value stored at key.
     *
     * @param key the key of the hash
     * @param count the number of field-value pairs to return
     * @return a list of field-value pairs from the hash
     */
    public List<Map.Entry<String, String>> hrandfieldWithValues(String key, long count) {
        checkNotClosed();
        ensureInitialized();
        try {
            String[][] result = glideClient.hrandfieldWithCountWithValues(key, count).get();
            List<Map.Entry<String, String>> entries = new ArrayList<>();
            for (String[] pair : result) {
                if (pair.length == 2) {
                    entries.add(new AbstractMap.SimpleEntry<>(pair[0], pair[1]));
                }
            }
            return entries;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HRANDFIELD operation failed", e);
        }
    }

    /**
     * Returns an array of random field-value pairs from the hash value stored at key (binary
     * version).
     *
     * @param key the key of the hash
     * @param count the number of field-value pairs to return
     * @return a list of field-value pairs from the hash
     */
    public List<Map.Entry<byte[], byte[]>> hrandfieldWithValues(final byte[] key, final long count) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[][] result =
                    glideClient.hrandfieldWithCountWithValues(GlideString.of(key), count).get();
            List<Map.Entry<byte[], byte[]>> entries = new ArrayList<>();
            for (GlideString[] pair : result) {
                if (pair.length == 2) {
                    entries.add(new AbstractMap.SimpleEntry<>(pair[0].getBytes(), pair[1].getBytes()));
                }
            }
            return entries;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HRANDFIELD operation failed", e);
        }
    }

    /**
     * Sets the specified field in the hash stored at key to value with expiration and existence
     * conditions. Note: This command requires Valkey 7.9+ and may not be available in all Valkey
     * versions.
     *
     * @param key the key of the hash
     * @param params the expiration and existence parameters
     * @param field the field in the hash
     * @param value the value to set
     * @return 1 if field is a new field in the hash and value was set, 0 if field already exists in
     *     the hash and the value was updated
     */
    public long hsetex(String key, HSetExParams params, String field, String value) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HSETEX");
            args.add(key);

            // Add existence condition
            if (params.getExistenceCondition() != null) {
                args.add(params.getExistenceCondition().name());
            }

            // Add expiration parameters
            if (params.getExpirationType() != null) {
                args.add(params.getExpirationType().name());
                if (params.getExpirationValue() != null) {
                    args.add(params.getExpirationValue().toString());
                }
            }

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add("1"); // Single field

            args.add(field);
            args.add(value);

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSETEX operation failed", e);
        }
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at key with expiration
     * and existence conditions. Note: This command requires Valkey 7.9+ and may not be available in
     * all Valkey versions.
     *
     * @param key the key of the hash
     * @param params the expiration and existence parameters
     * @param hash a map of field-value pairs to set in the hash
     * @return the number of fields that were added
     */
    public long hsetex(String key, HSetExParams params, Map<String, String> hash) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HSETEX");
            args.add(key);

            // Add existence condition
            if (params.getExistenceCondition() != null) {
                args.add(params.getExistenceCondition().name());
            }

            // Add expiration parameters
            if (params.getExpirationType() != null) {
                args.add(params.getExpirationType().name());
                if (params.getExpirationValue() != null) {
                    args.add(params.getExpirationValue().toString());
                }
            }

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(hash.size()));

            // Add field-value pairs
            for (Map.Entry<String, String> entry : hash.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSETEX operation failed", e);
        }
    }

    /**
     * Retrieves the values associated with the specified fields in a hash stored at the given key and
     * optionally sets their expiration. Note: This command requires Valkey 7.9+ and may not be
     * available in all Valkey versions.
     *
     * @param key the key of the hash
     * @param params additional parameters for the HGETEX command
     * @param fields the fields whose values are to be retrieved
     * @return a list of the value associated with each field or nil if the field doesn't exist
     */
    public List<String> hgetex(String key, HGetExParams params, String... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HGETEX");
            args.add(key);

            // Add expiration parameters
            if (params.getExpirationType() != null) {
                args.add(params.getExpirationType().name());
                if (params.getExpirationValue() != null) {
                    args.add(params.getExpirationValue().toString());
                }
            }

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            // Add fields
            args.addAll(Arrays.asList(fields));

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            if (result instanceof Object[]) {
                Object[] resultArray = (Object[]) result;
                List<String> stringResult = new ArrayList<>();
                for (Object obj : resultArray) {
                    stringResult.add(obj != null ? obj.toString() : null);
                }
                return stringResult;
            }
            return Arrays.asList(result != null ? result.toString() : null);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HGETEX operation failed", e);
        }
    }

    /**
     * Retrieves the values associated with the specified fields in the hash stored at the given key
     * and then deletes those fields from the hash. Note: This command requires Valkey 7.9+ and may
     * not be available in all Valkey versions.
     *
     * @param key the key of the hash
     * @param fields the fields whose values are to be retrieved and then deleted
     * @return a list of values associated with the specified fields before they were deleted
     */
    public List<String> hgetdel(String key, String... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HGETDEL");
            args.add(key);
            args.addAll(Arrays.asList(fields));

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            if (result instanceof Object[]) {
                Object[] resultArray = (Object[]) result;
                List<String> stringResult = new ArrayList<>();
                for (Object obj : resultArray) {
                    stringResult.add(obj != null ? obj.toString() : null);
                }
                return stringResult;
            }
            return Arrays.asList(result != null ? result.toString() : null);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HGETDEL operation failed", e);
        }
    }

    /**
     * Sets the specified field in the hash stored at key to value with expiration and existence
     * conditions (binary version). Note: This command requires Valkey 7.9+ and may not be available
     * in all Valkey versions.
     *
     * @param key the key of the hash
     * @param params the expiration and existence parameters
     * @param field the field in the hash
     * @param value the value to set
     * @return 1 if field is a new field in the hash and value was set, 0 if field already exists in
     *     the hash and the value was updated
     */
    public long hsetex(byte[] key, HSetExParams params, byte[] field, byte[] value) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HSETEX");
            args.add(new String(key));

            // Add existence condition
            if (params.getExistenceCondition() != null) {
                args.add(params.getExistenceCondition().name());
            }

            // Add expiration parameters
            if (params.getExpirationType() != null) {
                args.add(params.getExpirationType().name());
                if (params.getExpirationValue() != null) {
                    args.add(params.getExpirationValue().toString());
                }
            }

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add("1"); // Single field

            args.add(new String(field));
            args.add(new String(value));

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSETEX operation failed", e);
        }
    }

    /**
     * Sets the specified fields to their respective values in the hash stored at key with expiration
     * and existence conditions (binary version). Note: This command requires Valkey 7.9+ and may not
     * be available in all Valkey versions.
     *
     * @param key the key of the hash
     * @param params the expiration and existence parameters
     * @param hash a map of field-value pairs to set in the hash
     * @return the number of fields that were added
     */
    public long hsetex(byte[] key, HSetExParams params, Map<byte[], byte[]> hash) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HSETEX");
            args.add(new String(key));

            // Add existence condition
            if (params.getExistenceCondition() != null) {
                args.add(params.getExistenceCondition().name());
            }

            // Add expiration parameters
            if (params.getExpirationType() != null) {
                args.add(params.getExpirationType().name());
                if (params.getExpirationValue() != null) {
                    args.add(params.getExpirationValue().toString());
                }
            }

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(hash.size()));

            // Add field-value pairs
            for (Map.Entry<byte[], byte[]> entry : hash.entrySet()) {
                args.add(new String(entry.getKey()));
                args.add(new String(entry.getValue()));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSETEX operation failed", e);
        }
    }

    /**
     * Retrieves the values associated with the specified fields in a hash stored at the given key and
     * optionally sets their expiration (binary version). Note: This command requires Valkey 7.9+ and
     * may not be available in all Valkey versions.
     *
     * @param key the key of the hash
     * @param params additional parameters for the HGETEX command
     * @param fields the fields whose values are to be retrieved
     * @return a list of the value associated with each field or nil if the field doesn't exist
     */
    public List<byte[]> hgetex(byte[] key, HGetExParams params, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HGETEX");
            args.add(new String(key));

            // Add expiration parameters
            if (params.getExpirationType() != null) {
                args.add(params.getExpirationType().name());
                if (params.getExpirationValue() != null) {
                    args.add(params.getExpirationValue().toString());
                }
            }

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            // Add fields
            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            if (result instanceof Object[]) {
                Object[] resultArray = (Object[]) result;
                List<byte[]> byteResult = new ArrayList<>();
                for (Object obj : resultArray) {
                    byteResult.add(obj != null ? obj.toString().getBytes() : null);
                }
                return byteResult;
            }
            return Arrays.asList(result != null ? result.toString().getBytes() : null);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HGETEX operation failed", e);
        }
    }

    /**
     * Retrieves the values associated with the specified fields in the hash stored at the given key
     * and then deletes those fields from the hash (binary version). Note: This command requires
     * Valkey 7.9+ and may not be available in all Valkey versions.
     *
     * @param key the key of the hash
     * @param fields the fields whose values are to be retrieved and then deleted
     * @return a list of values associated with the specified fields before they were deleted
     */
    public List<byte[]> hgetdel(byte[] key, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HGETDEL");
            args.add(new String(key));
            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            if (result instanceof Object[]) {
                Object[] resultArray = (Object[]) result;
                List<byte[]> byteResult = new ArrayList<>();
                for (Object obj : resultArray) {
                    byteResult.add(obj != null ? obj.toString().getBytes() : null);
                }
                return byteResult;
            }
            return Arrays.asList(result != null ? result.toString().getBytes() : null);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HGETDEL operation failed", e);
        }
    }

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @return scan result with the cursor and the fields
     */
    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
        return hscan(key, cursor, new ScanParams());
    }

    /**
     * Iterates fields of Hash types and their associated values (binary version).
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @return scan result with the cursor and the fields
     */
    public ScanResult<Map.Entry<byte[], byte[]>> hscan(final byte[] key, final byte[] cursor) {
        return hscan(key, cursor, new ScanParams());
    }

    /**
     * Iterates fields of Hash types and their associated values.
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @param params the scan parameters
     * @return scan result with the cursor and the fields
     */
    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        checkNotClosed();
        ensureInitialized();
        try {
            HScanOptions options = convertScanParamsToHScanOptions(params);
            Object[] result = glideClient.hscan(key, cursor, options).get();

            String nextCursor = (String) result[0];
            Object[] fieldsAndValues = (Object[]) result[1];

            List<Map.Entry<String, String>> entries = new ArrayList<>();
            for (int i = 0; i < fieldsAndValues.length; i += 2) {
                String field = (String) fieldsAndValues[i];
                String value = (String) fieldsAndValues[i + 1];
                entries.add(new AbstractMap.SimpleEntry<>(field, value));
            }

            return new ScanResult<>(nextCursor, entries);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSCAN operation failed", e);
        }
    }

    /**
     * Iterates fields of Hash types and their associated values (binary version).
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @param params the scan parameters
     * @return scan result with the cursor and the fields
     */
    public ScanResult<Map.Entry<byte[], byte[]>> hscan(
            final byte[] key, final byte[] cursor, final ScanParams params) {
        checkNotClosed();
        ensureInitialized();
        try {
            HScanOptionsBinary options = convertScanParamsToHScanOptionsBinary(params);
            Object[] result =
                    glideClient.hscan(GlideString.of(key), GlideString.of(cursor), options).get();

            String nextCursor = (String) result[0];
            Object[] fieldsAndValues = (Object[]) result[1];

            List<Map.Entry<byte[], byte[]>> entries = new ArrayList<>();
            for (int i = 0; i < fieldsAndValues.length; i += 2) {
                GlideString field = (GlideString) fieldsAndValues[i];
                GlideString value = (GlideString) fieldsAndValues[i + 1];
                entries.add(new AbstractMap.SimpleEntry<>(field.getBytes(), value.getBytes()));
            }

            return new ScanResult<>(nextCursor.getBytes(), entries);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSCAN operation failed", e);
        }
    }

    /**
     * Iterates fields of Hash types without their values.
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @return scan result with the cursor and the field names
     */
    public ScanResult<String> hscanNoValues(String key, String cursor) {
        return hscanNoValues(key, cursor, new ScanParams());
    }

    /**
     * Iterates fields of Hash types without their values (binary version).
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @return scan result with the cursor and the field names
     */
    public ScanResult<byte[]> hscanNoValues(final byte[] key, final byte[] cursor) {
        return hscanNoValues(key, cursor, new ScanParams());
    }

    /**
     * Iterates fields of Hash types without their values.
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @param params the scan parameters
     * @return scan result with the cursor and the field names
     */
    public ScanResult<String> hscanNoValues(String key, String cursor, ScanParams params) {
        checkNotClosed();
        ensureInitialized();
        try {
            HScanOptions options = convertScanParamsToHScanOptions(params);
            Object[] result = glideClient.hscan(key, cursor, options).get();

            String nextCursor = (String) result[0];
            Object[] fieldsAndValues = (Object[]) result[1];

            List<String> fields = new ArrayList<>();
            for (int i = 0; i < fieldsAndValues.length; i += 2) {
                fields.add((String) fieldsAndValues[i]);
            }

            return new ScanResult<>(nextCursor, fields);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSCAN operation failed", e);
        }
    }

    /**
     * Iterates fields of Hash types without their values (binary version).
     *
     * @param key the key of the hash
     * @param cursor the cursor
     * @param params the scan parameters
     * @return scan result with the cursor and the field names
     */
    public ScanResult<byte[]> hscanNoValues(
            final byte[] key, final byte[] cursor, final ScanParams params) {
        checkNotClosed();
        ensureInitialized();
        try {
            HScanOptionsBinary options = convertScanParamsToHScanOptionsBinary(params);
            Object[] result =
                    glideClient.hscan(GlideString.of(key), GlideString.of(cursor), options).get();

            String nextCursor = (String) result[0];
            Object[] fieldsAndValues = (Object[]) result[1];

            List<byte[]> fields = new ArrayList<>();
            for (int i = 0; i < fieldsAndValues.length; i += 2) {
                GlideString field = (GlideString) fieldsAndValues[i];
                fields.add(field.getBytes());
            }

            return new ScanResult<>(nextCursor.getBytes(), fields);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HSCAN operation failed", e);
        }
    }

    // Hash expiration commands (these may not be available in all Valkey versions)
    // For now, implementing them as unsupported operations

    /**
     * Set expiry for hash field using relative time to expire (seconds). Note: This command may not
     * be available in all Valkey versions.
     *
     * @param key hash
     * @param seconds time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpire(String key, long seconds, String... fields) {
        return executeCommandWithGlide(
                "HEXPIRE",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HEXPIRE");
                    args.add(key);
                    args.add(String.valueOf(seconds));

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using relative time to expire (seconds) with condition. Note: This
     * command may not be available in all Valkey versions.
     *
     * @param key hash
     * @param seconds time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpire(String key, long seconds, ExpiryOption condition, String... fields) {
        return executeCommandWithGlide(
                "HEXPIRE",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HEXPIRE");
                    args.add(key);
                    args.add(String.valueOf(seconds));
                    args.add(condition.name());

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using relative time to expire (milliseconds). Note: This command may
     * not be available in all Valkey versions.
     *
     * @param key hash
     * @param milliseconds time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpire(String key, long milliseconds, String... fields) {
        return executeCommandWithGlide(
                "HPEXPIRE",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPEXPIRE");
                    args.add(key);
                    args.add(String.valueOf(milliseconds));

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using relative time to expire (milliseconds) with condition. Note:
     * This command may not be available in all Valkey versions.
     *
     * @param key hash
     * @param milliseconds time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpire(
            String key, long milliseconds, ExpiryOption condition, String... fields) {
        return executeCommandWithGlide(
                "HPEXPIRE",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPEXPIRE");
                    args.add(key);
                    args.add(String.valueOf(milliseconds));
                    args.add(condition.name());

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (seconds). Note: This command may
     * not be available in all Valkey versions.
     *
     * @param key hash
     * @param unixTimeSeconds time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpireAt(String key, long unixTimeSeconds, String... fields) {
        return executeCommandWithGlide(
                "HEXPIREAT",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HEXPIREAT");
                    args.add(key);
                    args.add(String.valueOf(unixTimeSeconds));

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (seconds) with condition. Note: This
     * command may not be available in all Valkey versions.
     *
     * @param key hash
     * @param unixTimeSeconds time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpireAt(
            String key, long unixTimeSeconds, ExpiryOption condition, String... fields) {
        return executeCommandWithGlide(
                "HEXPIREAT",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HEXPIREAT");
                    args.add(key);
                    args.add(String.valueOf(unixTimeSeconds));
                    args.add(condition.name());

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (milliseconds). Note: This command
     * may not be available in all Valkey versions.
     *
     * @param key hash
     * @param unixTimeMillis time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpireAt(String key, long unixTimeMillis, String... fields) {
        return executeCommandWithGlide(
                "HPEXPIREAT",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPEXPIREAT");
                    args.add(key);
                    args.add(String.valueOf(unixTimeMillis));

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (milliseconds) with condition. Note:
     * This command may not be available in all Valkey versions.
     *
     * @param key hash
     * @param unixTimeMillis time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpireAt(
            String key, long unixTimeMillis, ExpiryOption condition, String... fields) {
        return executeCommandWithGlide(
                "HPEXPIREAT",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPEXPIREAT");
                    args.add(key);
                    args.add(String.valueOf(unixTimeMillis));
                    args.add(condition.name());

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Returns the expiration time of a hash field as a Unix timestamp, in seconds. Note: This command
     * may not be available in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get expiration time for
     * @return list of expiration times for each field
     */
    public List<Long> hexpireTime(String key, String... fields) {
        return executeCommandWithGlide(
                "HEXPIRETIME",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HEXPIRETIME");
                    args.add(key);

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Returns the expiration time of a hash field as a Unix timestamp, in milliseconds. Note: This
     * command may not be available in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get expiration time for
     * @return list of expiration times for each field
     */
    public List<Long> hpexpireTime(String key, String... fields) {
        return executeCommandWithGlide(
                "HPEXPIRETIME",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPEXPIRETIME");
                    args.add(key);

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Returns the TTL in seconds of a hash field. Note: This command may not be available in all
     * Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get TTL for
     * @return list of TTL values for each field
     */
    public List<Long> httl(String key, String... fields) {
        return executeCommandWithGlide(
                "HTTL",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HTTL");
                    args.add(key);

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Returns the TTL in milliseconds of a hash field. Note: This command may not be available in all
     * Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get TTL for
     * @return list of TTL values for each field
     */
    public List<Long> hpttl(String key, String... fields) {
        return executeCommandWithGlide(
                "HPTTL",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPTTL");
                    args.add(key);

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    /**
     * Removes the expiration time for each specified field. Note: This command may not be available
     * in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to remove expiration for
     * @return list of results for each field
     */
    public List<Long> hpersist(String key, String... fields) {
        return executeCommandWithGlide(
                "HPERSIST",
                () -> {
                    List<String> args = new ArrayList<>();
                    args.add("HPERSIST");
                    args.add(key);

                    // Add FIELDS keyword and numfields count
                    args.add(FIELDS_KEYWORD);
                    args.add(String.valueOf(fields.length));

                    args.addAll(Arrays.asList(fields));

                    Object result = glideClient.customCommand(args.toArray(new String[0])).get();
                    return convertToLongList(result);
                });
    }

    // Binary variants for hash expiration commands

    /**
     * Set expiry for hash field using relative time to expire (seconds) - binary version. Note: This
     * command requires Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param seconds time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpire(byte[] key, long seconds, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HEXPIRE");
            args.add(new String(key));
            args.add(String.valueOf(seconds));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HEXPIRE operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using relative time to expire (seconds) with condition - binary
     * version. Note: This command requires Valkey 7.4+ and may not be available in all Valkey
     * versions.
     *
     * @param key hash
     * @param seconds time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpire(byte[] key, long seconds, ExpiryOption condition, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HEXPIRE");
            args.add(new String(key));
            args.add(String.valueOf(seconds));
            args.add(condition.name());

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HEXPIRE operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using relative time to expire (milliseconds) - binary version. Note:
     * This command requires Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param milliseconds time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpire(byte[] key, long milliseconds, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPEXPIRE");
            args.add(new String(key));
            args.add(String.valueOf(milliseconds));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPEXPIRE operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using relative time to expire (milliseconds) with condition - binary
     * version. Note: This command requires Valkey 7.4+ and may not be available in all Valkey
     * versions.
     *
     * @param key hash
     * @param milliseconds time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpire(
            byte[] key, long milliseconds, ExpiryOption condition, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPEXPIRE");
            args.add(new String(key));
            args.add(String.valueOf(milliseconds));
            args.add(condition.name());

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPEXPIRE operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (seconds) - binary version. Note:
     * This command requires Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param unixTimeSeconds time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpireAt(byte[] key, long unixTimeSeconds, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HEXPIREAT");
            args.add(new String(key));
            args.add(String.valueOf(unixTimeSeconds));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HEXPIREAT operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (seconds) with condition - binary
     * version. Note: This command requires Valkey 7.4+ and may not be available in all Valkey
     * versions.
     *
     * @param key hash
     * @param unixTimeSeconds time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hexpireAt(
            byte[] key, long unixTimeSeconds, ExpiryOption condition, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HEXPIREAT");
            args.add(new String(key));
            args.add(String.valueOf(unixTimeSeconds));
            args.add(condition.name());

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HEXPIREAT operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (milliseconds) - binary version.
     * Note: This command requires Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param unixTimeMillis time to expire
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpireAt(byte[] key, long unixTimeMillis, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPEXPIREAT");
            args.add(new String(key));
            args.add(String.valueOf(unixTimeMillis));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPEXPIREAT operation failed", e);
        }
    }

    /**
     * Set expiry for hash field using an absolute Unix timestamp (milliseconds) with condition -
     * binary version. Note: This command requires Valkey 7.4+ and may not be available in all Valkey
     * versions.
     *
     * @param key hash
     * @param unixTimeMillis time to expire
     * @param condition expiry condition
     * @param fields the fields to set expiration for
     * @return list of results for each field
     */
    public List<Long> hpexpireAt(
            byte[] key, long unixTimeMillis, ExpiryOption condition, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPEXPIREAT");
            args.add(new String(key));
            args.add(String.valueOf(unixTimeMillis));
            args.add(condition.name());

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPEXPIREAT operation failed", e);
        }
    }

    /**
     * Returns the expiration time of a hash field as a Unix timestamp, in seconds - binary version.
     * Note: This command requires Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get expiration time for
     * @return list of expiration times for each field
     */
    public List<Long> hexpireTime(byte[] key, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HEXPIRETIME");
            args.add(new String(key));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HEXPIRETIME operation failed", e);
        }
    }

    /**
     * Returns the expiration time of a hash field as a Unix timestamp, in milliseconds - binary
     * version. Note: This command requires Valkey 7.4+ and may not be available in all Valkey
     * versions.
     *
     * @param key hash
     * @param fields the fields to get expiration time for
     * @return list of expiration times for each field
     */
    public List<Long> hpexpireTime(byte[] key, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPEXPIRETIME");
            args.add(new String(key));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPEXPIRETIME operation failed", e);
        }
    }

    /**
     * Returns the TTL in seconds of a hash field - binary version. Note: This command requires Valkey
     * 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get TTL for
     * @return list of TTL values for each field
     */
    public List<Long> httl(byte[] key, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HTTL");
            args.add(new String(key));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HTTL operation failed", e);
        }
    }

    /**
     * Returns the TTL in milliseconds of a hash field - binary version. Note: This command requires
     * Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to get TTL for
     * @return list of TTL values for each field
     */
    public List<Long> hpttl(byte[] key, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPTTL");
            args.add(new String(key));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPTTL operation failed", e);
        }
    }

    /**
     * Removes the expiration time for each specified field - binary version. Note: This command
     * requires Valkey 7.4+ and may not be available in all Valkey versions.
     *
     * @param key hash
     * @param fields the fields to remove expiration for
     * @return list of results for each field
     */
    public List<Long> hpersist(byte[] key, byte[]... fields) {
        checkNotClosed();
        ensureInitialized();
        try {
            List<String> args = new ArrayList<>();
            args.add("HPERSIST");
            args.add(new String(key));

            // Add FIELDS keyword and numfields count
            args.add(FIELDS_KEYWORD);
            args.add(String.valueOf(fields.length));

            for (byte[] field : fields) {
                args.add(new String(field));
            }

            Object result = glideClient.customCommand(args.toArray(new String[0])).get();
            return convertToLongList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("HPERSIST operation failed", e);
        }
    }

    /** Helper method to convert result to List<Long>. */
    private static List<Long> convertToLongList(Object result) {
        if (result instanceof Object[]) {
            Object[] resultArray = (Object[]) result;
            List<Long> longResult = new ArrayList<>();
            for (Object obj : resultArray) {
                if (obj instanceof Long) {
                    longResult.add((Long) obj);
                } else if (obj != null) {
                    longResult.add(Long.parseLong(obj.toString()));
                } else {
                    longResult.add(null);
                }
            }
            return longResult;
        } else if (result instanceof Long) {
            return Arrays.asList((Long) result);
        } else if (result != null) {
            return Arrays.asList(Long.parseLong(result.toString()));
        }
        return Arrays.asList((Long) null);
    }

    /** Helper method to convert Jedis ScanParams to GLIDE HScanOptions. */
    private static HScanOptions convertScanParamsToHScanOptions(ScanParams params) {
        HScanOptions.HScanOptionsBuilder builder = HScanOptions.builder();

        if (params.getMatchPattern() != null) {
            builder.matchPattern(params.getMatchPattern());
        }

        if (params.getCount() != null) {
            builder.count(params.getCount());
        }

        return builder.build();
    }

    /** Helper method to convert Jedis ScanParams to GLIDE HScanOptionsBinary. */
    private static HScanOptionsBinary convertScanParamsToHScanOptionsBinary(ScanParams params) {
        HScanOptionsBinary.HScanOptionsBinaryBuilder builder = HScanOptionsBinary.builder();

        if (params.getMatchPattern() != null) {
            builder.matchPattern(GlideString.of(params.getMatchPattern()));
        }

        if (params.getCount() != null) {
            builder.count(params.getCount());
        }

        return builder.build();
    }

    // ===== MISSING METHODS FOR Valkey JDBC DRIVER COMPATIBILITY =====

    /**
     * Constructor with Connection (compatibility stub). NOTE: Connection is not used in GLIDE
     * compatibility layer.
     */
    public Jedis(Connection connection) {
        // Extract host/port from connection for GLIDE client creation
        this(connection.getHost(), connection.getPort());
    }

    /**
     * Send a blocking command to Valkey server. Uses the same implementation as sendCommand since
     * GLIDE handles blocking internally.
     */
    public Object sendBlockingCommand(ProtocolCommand cmd, String... args) {
        return sendCommand(cmd, args);
    }

    /**
     * Send a blocking command to Valkey server with byte arrays. Uses the same implementation as
     * sendCommand since GLIDE handles blocking internally.
     */
    public Object sendBlockingCommand(ProtocolCommand cmd, byte[]... args) {
        return sendCommand(cmd, args);
    }

    /** Get the current database index. NOTE: GLIDE manages database selection internally. */
    public int getDB() {
        // TODO: Track database selection in compatibility layer
        return 0; // Default database for now
    }

    // ========== LIST COMMANDS ==========

    /**
     * Inserts all the specified values at the head of the list stored at key.
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long lpush(String key, String... strings) {
        return executeCommandWithGlide("LPUSH", () -> glideClient.lpush(key, strings).get());
    }

    /**
     * Inserts all the specified values at the head of the list stored at key (binary version).
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long lpush(final byte[] key, final byte[]... strings) {
        return executeCommandWithGlide(
                "LPUSH",
                () -> {
                    GlideString[] glideStrings = convertToGlideStringArray(strings);
                    return glideClient.lpush(GlideString.of(key), glideStrings).get();
                });
    }

    /**
     * Inserts all the specified values at the tail of the list stored at key.
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long rpush(String key, String... strings) {
        return executeCommandWithGlide("RPUSH", () -> glideClient.rpush(key, strings).get());
    }

    /**
     * Inserts all the specified values at the tail of the list stored at key (binary version).
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long rpush(final byte[] key, final byte[]... strings) {
        return executeCommandWithGlide(
                "RPUSH",
                () -> {
                    GlideString[] glideStrings = convertToGlideStringArray(strings);
                    return glideClient.rpush(GlideString.of(key), glideStrings).get();
                });
    }

    /**
     * Removes and returns the first element of the list stored at key.
     *
     * @param key the key of the list
     * @return the value of the first element, or null when key does not exist
     */
    public String lpop(String key) {
        return executeCommandWithGlide("LPOP", () -> glideClient.lpop(key).get());
    }

    /**
     * Removes and returns the first element of the list stored at key (binary version).
     *
     * @param key the key of the list
     * @return the value of the first element, or null when key does not exist
     */
    public byte[] lpop(final byte[] key) {
        return executeCommandWithGlide(
                "LPOP",
                () -> {
                    GlideString result = glideClient.lpop(GlideString.of(key)).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Removes and returns up to count elements from the head of the list stored at key.
     *
     * @param key the key of the list
     * @param count the number of elements to pop
     * @return list of popped elements, or empty list when key does not exist
     */
    public List<String> lpop(String key, int count) {
        return executeCommandWithGlide(
                "LPOP",
                () -> {
                    String[] result = glideClient.lpopCount(key, count).get();
                    return result != null ? Arrays.asList(result) : Collections.emptyList();
                });
    }

    /**
     * Removes and returns up to count elements from the head of the list stored at key (binary
     * version).
     *
     * @param key the key of the list
     * @param count the number of elements to pop
     * @return list of popped elements, or empty list when key does not exist
     */
    public List<byte[]> lpop(final byte[] key, int count) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] result = glideClient.lpopCount(GlideString.of(key), count).get();
            if (result == null) {
                return Collections.emptyList();
            }
            List<byte[]> byteResult = new ArrayList<>();
            for (GlideString gs : result) {
                byteResult.add(gs.getBytes());
            }
            return byteResult;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("LPOP operation failed", e);
        }
    }

    /**
     * Removes and returns the last element of the list stored at key.
     *
     * @param key the key of the list
     * @return the value of the last element, or null when key does not exist
     */
    public String rpop(String key) {
        return executeCommandWithGlide("RPOP", () -> glideClient.rpop(key).get());
    }

    /**
     * Removes and returns the last element of the list stored at key (binary version).
     *
     * @param key the key of the list
     * @return the value of the last element, or null when key does not exist
     */
    public byte[] rpop(final byte[] key) {
        return executeCommandWithGlide(
                "RPOP",
                () -> {
                    GlideString result = glideClient.rpop(GlideString.of(key)).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Removes and returns up to count elements from the tail of the list stored at key.
     *
     * @param key the key of the list
     * @param count the number of elements to pop
     * @return list of popped elements, or empty list when key does not exist
     */
    public List<String> rpop(String key, int count) {
        return executeCommandWithGlide(
                "RPOP",
                () -> {
                    String[] result = glideClient.rpopCount(key, count).get();
                    return result != null ? Arrays.asList(result) : Collections.emptyList();
                });
    }

    /**
     * Removes and returns up to count elements from the tail of the list stored at key (binary
     * version).
     *
     * @param key the key of the list
     * @param count the number of elements to pop
     * @return list of popped elements, or empty list when key does not exist
     */
    public List<byte[]> rpop(final byte[] key, int count) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] result = glideClient.rpopCount(GlideString.of(key), count).get();
            if (result == null) {
                return Collections.emptyList();
            }
            List<byte[]> byteResult = new ArrayList<>();
            for (GlideString gs : result) {
                byteResult.add(gs.getBytes());
            }
            return byteResult;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RPOP operation failed", e);
        }
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @param key the key of the list
     * @return the length of the list at key
     */
    public long llen(String key) {
        return executeCommandWithGlide("LLEN", () -> glideClient.llen(key).get());
    }

    /**
     * Returns the length of the list stored at key (binary version).
     *
     * @param key the key of the list
     * @return the length of the list at key
     */
    public long llen(final byte[] key) {
        return executeCommandWithGlide("LLEN", () -> glideClient.llen(GlideString.of(key)).get());
    }

    /**
     * Returns the specified elements of the list stored at key.
     *
     * @param key the key of the list
     * @param start the start index
     * @param stop the stop index
     * @return list of elements in the specified range
     */
    public List<String> lrange(String key, long start, long stop) {
        return executeCommandWithGlide(
                "LRANGE",
                () -> {
                    String[] result = glideClient.lrange(key, start, stop).get();
                    return result != null ? Arrays.asList(result) : Collections.emptyList();
                });
    }

    /**
     * Returns the specified elements of the list stored at key (binary version).
     *
     * @param key the key of the list
     * @param start the start index
     * @param stop the stop index
     * @return list of elements in the specified range
     */
    public List<byte[]> lrange(final byte[] key, long start, long stop) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] result = glideClient.lrange(GlideString.of(key), start, stop).get();
            if (result == null) {
                return Collections.emptyList();
            }
            List<byte[]> byteResult = new ArrayList<>();
            for (GlideString gs : result) {
                byteResult.add(gs.getBytes());
            }
            return byteResult;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("LRANGE operation failed", e);
        }
    }

    /**
     * Trim an existing list so that it will contain only the specified range of elements specified.
     *
     * @param key the key of the list
     * @param start the start index
     * @param stop the stop index
     * @return always "OK"
     */
    public String ltrim(String key, long start, long stop) {
        return executeCommandWithGlide("LTRIM", () -> glideClient.ltrim(key, start, stop).get());
    }

    /**
     * Trim an existing list so that it will contain only the specified range of elements specified
     * (binary version).
     *
     * @param key the key of the list
     * @param start the start index
     * @param stop the stop index
     * @return always "OK"
     */
    public String ltrim(final byte[] key, long start, long stop) {
        return executeCommandWithGlide(
                "LTRIM", () -> glideClient.ltrim(GlideString.of(key), start, stop).get());
    }

    /**
     * Returns the element at index in the list stored at key.
     *
     * @param key the key of the list
     * @param index the index of the element
     * @return the requested element, or null when index is out of range
     */
    public String lindex(String key, long index) {
        return executeCommandWithGlide("LINDEX", () -> glideClient.lindex(key, index).get());
    }

    /**
     * Returns the element at index in the list stored at key (binary version).
     *
     * @param key the key of the list
     * @param index the index of the element
     * @return the requested element, or null when index is out of range
     */
    public byte[] lindex(final byte[] key, long index) {
        return executeCommandWithGlide(
                "LINDEX",
                () -> {
                    GlideString result = glideClient.lindex(GlideString.of(key), index).get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Sets the list element at index to element.
     *
     * @param key the key of the list
     * @param index the index of the element to set
     * @param element the new element value
     * @return "OK" on success
     */
    public String lset(String key, long index, String element) {
        return executeCommandWithGlide("LSET", () -> glideClient.lset(key, index, element).get());
    }

    /**
     * Sets the list element at index to element (binary version).
     *
     * @param key the key of the list
     * @param index the index of the element to set
     * @param element the new element value
     * @return "OK" on success
     */
    public String lset(final byte[] key, long index, final byte[] element) {
        return executeCommandWithGlide(
                "LSET", () -> glideClient.lset(GlideString.of(key), index, GlideString.of(element)).get());
    }

    /**
     * Removes the first count occurrences of elements equal to element from the list stored at key.
     *
     * @param key the key of the list
     * @param count the number of elements to remove
     * @param element the element to remove
     * @return the number of removed elements
     */
    public long lrem(String key, long count, String element) {
        return executeCommandWithGlide("LREM", () -> glideClient.lrem(key, count, element).get());
    }

    /**
     * Removes the first count occurrences of elements equal to element from the list stored at key
     * (binary version).
     *
     * @param key the key of the list
     * @param count the number of elements to remove
     * @param element the element to remove
     * @return the number of removed elements
     */
    public long lrem(final byte[] key, long count, final byte[] element) {
        return executeCommandWithGlide(
                "LREM", () -> glideClient.lrem(GlideString.of(key), count, GlideString.of(element)).get());
    }

    /**
     * Inserts element in the list stored at key either before or after the reference value pivot.
     *
     * @param key the key of the list
     * @param where BEFORE or AFTER
     * @param pivot the reference value
     * @param element the element to insert
     * @return the length of the list after the insert operation, or -1 when the value pivot was not
     *     found
     */
    public long linsert(String key, ListPosition where, String pivot, String element) {
        return executeCommandWithGlide(
                "LINSERT",
                () -> {
                    InsertPosition position =
                            where == ListPosition.BEFORE ? InsertPosition.BEFORE : InsertPosition.AFTER;
                    return glideClient.linsert(key, position, pivot, element).get();
                });
    }

    /**
     * Inserts element in the list stored at key either before or after the reference value pivot
     * (binary version).
     *
     * @param key the key of the list
     * @param where BEFORE or AFTER
     * @param pivot the reference value
     * @param element the element to insert
     * @return the length of the list after the insert operation, or -1 when the value pivot was not
     *     found
     */
    public long linsert(
            final byte[] key, ListPosition where, final byte[] pivot, final byte[] element) {
        return executeCommandWithGlide(
                "LINSERT",
                () -> {
                    InsertPosition position =
                            where == ListPosition.BEFORE ? InsertPosition.BEFORE : InsertPosition.AFTER;
                    return glideClient
                            .linsert(
                                    GlideString.of(key), position, GlideString.of(pivot), GlideString.of(element))
                            .get();
                });
    }

    /**
     * Inserts specified values at the head of the list stored at key, only if key already exists and
     * holds a list.
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long lpushx(String key, String... strings) {
        return executeCommandWithGlide("LPUSHX", () -> glideClient.lpushx(key, strings).get());
    }

    /**
     * Inserts specified values at the head of the list stored at key, only if key already exists and
     * holds a list (binary version).
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long lpushx(final byte[] key, final byte[]... strings) {
        return executeCommandWithGlide(
                "LPUSHX",
                () -> {
                    GlideString[] glideStrings = convertToGlideStringArray(strings);
                    return glideClient.lpushx(GlideString.of(key), glideStrings).get();
                });
    }

    /**
     * Inserts specified values at the tail of the list stored at key, only if key already exists and
     * holds a list.
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long rpushx(String key, String... strings) {
        return executeCommandWithGlide("RPUSHX", () -> glideClient.rpushx(key, strings).get());
    }

    /**
     * Inserts specified values at the tail of the list stored at key, only if key already exists and
     * holds a list (binary version).
     *
     * @param key the key of the list
     * @param strings the values to insert
     * @return the length of the list after the push operation
     */
    public long rpushx(final byte[] key, final byte[]... strings) {
        return executeCommandWithGlide(
                "RPUSHX",
                () -> {
                    GlideString[] glideStrings = convertToGlideStringArray(strings);
                    return glideClient.rpushx(GlideString.of(key), glideStrings).get();
                });
    }

    /**
     * BLPOP is a blocking list pop primitive. It is the blocking version of LPOP.
     *
     * @param timeout the timeout in seconds
     * @param keys the keys to check
     * @return list containing the key and the popped element, or null when no element could be popped
     */
    public List<String> blpop(int timeout, String... keys) {
        return executeCommandWithGlide(
                "BLPOP",
                () -> {
                    String[] result = glideClient.blpop(keys, timeout).get();
                    return result != null ? Arrays.asList(result) : null;
                });
    }

    /**
     * BLPOP is a blocking list pop primitive. It is the blocking version of LPOP.
     *
     * @param timeout the timeout in seconds (double precision)
     * @param keys the keys to check
     * @return KeyValue containing the key and the popped element, or null when no element could be
     *     popped
     */
    public KeyValue<String, String> blpop(double timeout, String... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            String[] result = glideClient.blpop(keys, timeout).get();
            if (result != null && result.length >= 2) {
                return new KeyValue<>(result[0], result[1]);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLPOP operation failed", e);
        }
    }

    /**
     * BLPOP is a blocking list pop primitive. It is the blocking version of LPOP (binary version).
     *
     * @param timeout the timeout in seconds
     * @param keys the keys to check
     * @return list containing the key and the popped element, or null when no element could be popped
     */
    public List<byte[]> blpop(int timeout, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            GlideString[] result = glideClient.blpop(glideKeys, timeout).get();
            if (result != null) {
                List<byte[]> byteResult = new ArrayList<>();
                for (GlideString gs : result) {
                    byteResult.add(gs.getBytes());
                }
                return byteResult;
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLPOP operation failed", e);
        }
    }

    /**
     * BLPOP is a blocking list pop primitive. It is the blocking version of LPOP (binary version).
     *
     * @param timeout the timeout in seconds (double precision)
     * @param keys the keys to check
     * @return KeyValue containing the key and the popped element, or null when no element could be
     *     popped
     */
    public KeyValue<byte[], byte[]> blpop(double timeout, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            GlideString[] result = glideClient.blpop(glideKeys, timeout).get();
            if (result != null && result.length >= 2) {
                return new KeyValue<>(result[0].getBytes(), result[1].getBytes());
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLPOP operation failed", e);
        }
    }

    /**
     * BRPOP is a blocking list pop primitive. It is the blocking version of RPOP.
     *
     * @param timeout the timeout in seconds
     * @param keys the keys to check
     * @return list containing the key and the popped element, or null when no element could be popped
     */
    public List<String> brpop(int timeout, String... keys) {
        return executeCommandWithGlide(
                "BRPOP",
                () -> {
                    String[] result = glideClient.brpop(keys, timeout).get();
                    return result != null ? Arrays.asList(result) : null;
                });
    }

    /**
     * BRPOP is a blocking list pop primitive. It is the blocking version of RPOP.
     *
     * @param timeout the timeout in seconds (double precision)
     * @param keys the keys to check
     * @return KeyValue containing the key and the popped element, or null when no element could be
     *     popped
     */
    public KeyValue<String, String> brpop(double timeout, String... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            String[] result = glideClient.brpop(keys, timeout).get();
            if (result != null && result.length >= 2) {
                return new KeyValue<>(result[0], result[1]);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BRPOP operation failed", e);
        }
    }

    /**
     * BRPOP is a blocking list pop primitive. It is the blocking version of RPOP (binary version).
     *
     * @param timeout the timeout in seconds
     * @param keys the keys to check
     * @return list containing the key and the popped element, or null when no element could be popped
     */
    public List<byte[]> brpop(int timeout, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            GlideString[] result = glideClient.brpop(glideKeys, timeout).get();
            if (result != null) {
                List<byte[]> byteResult = new ArrayList<>();
                for (GlideString gs : result) {
                    byteResult.add(gs.getBytes());
                }
                return byteResult;
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BRPOP operation failed", e);
        }
    }

    /**
     * BRPOP is a blocking list pop primitive. It is the blocking version of RPOP (binary version).
     *
     * @param timeout the timeout in seconds (double precision)
     * @param keys the keys to check
     * @return KeyValue containing the key and the popped element, or null when no element could be
     *     popped
     */
    public KeyValue<byte[], byte[]> brpop(double timeout, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            GlideString[] result = glideClient.brpop(glideKeys, timeout).get();
            if (result != null && result.length >= 2) {
                return new KeyValue<>(result[0].getBytes(), result[1].getBytes());
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BRPOP operation failed", e);
        }
    }

    /**
     * BLPOP is a blocking list pop primitive for a single key.
     *
     * @param timeout the timeout in seconds
     * @param key the key to check
     * @return list containing the key and the popped element, or null when no element could be popped
     */
    public List<String> blpop(int timeout, String key) {
        return blpop(timeout, new String[] {key});
    }

    /**
     * BLPOP is a blocking list pop primitive for a single key.
     *
     * @param timeout the timeout in seconds (double precision)
     * @param key the key to check
     * @return KeyValue containing the key and the popped element, or null when no element could be
     *     popped
     */
    public KeyValue<String, String> blpop(double timeout, String key) {
        return blpop(timeout, new String[] {key});
    }

    /**
     * BRPOP is a blocking list pop primitive for a single key.
     *
     * @param timeout the timeout in seconds
     * @param key the key to check
     * @return list containing the key and the popped element, or null when no element could be popped
     */
    public List<String> brpop(int timeout, String key) {
        return brpop(timeout, new String[] {key});
    }

    /**
     * BRPOP is a blocking list pop primitive for a single key.
     *
     * @param timeout the timeout in seconds (double precision)
     * @param key the key to check
     * @return KeyValue containing the key and the popped element, or null when no element could be
     *     popped
     */
    public KeyValue<String, String> brpop(double timeout, String key) {
        return brpop(timeout, new String[] {key});
    }

    /**
     * Returns the index of the first matching element in the list stored at key.
     *
     * @param key the key of the list
     * @param element the element to search for
     * @return the index of the first matching element, or null if not found
     */
    public Long lpos(String key, String element) {
        return executeCommandWithGlide("LPOS", () -> glideClient.lpos(key, element).get());
    }

    /**
     * Returns the index of the first matching element in the list stored at key (binary version).
     *
     * @param key the key of the list
     * @param element the element to search for
     * @return the index of the first matching element, or null if not found
     */
    public Long lpos(final byte[] key, final byte[] element) {
        return executeCommandWithGlide(
                "LPOS", () -> glideClient.lpos(GlideString.of(key), GlideString.of(element)).get());
    }

    /**
     * Returns the index of matching elements in the list stored at key with additional options.
     *
     * @param key the key of the list
     * @param element the element to search for
     * @param params additional parameters for the search
     * @return the index of the matching element, or null if not found
     */
    public Long lpos(String key, String element, LPosParams params) {
        return executeCommandWithGlide(
                "LPOS",
                () -> {
                    LPosOptions options = convertLPosParamsToLPosOptions(params);
                    return glideClient.lpos(key, element, options).get();
                });
    }

    /**
     * Returns the index of matching elements in the list stored at key with additional options
     * (binary version).
     *
     * @param key the key of the list
     * @param element the element to search for
     * @param params additional parameters for the search
     * @return the index of the matching element, or null if not found
     */
    public Long lpos(final byte[] key, final byte[] element, LPosParams params) {
        return executeCommandWithGlide(
                "LPOS",
                () -> {
                    LPosOptions options = convertLPosParamsToLPosOptions(params);
                    return glideClient.lpos(GlideString.of(key), GlideString.of(element), options).get();
                });
    }

    /**
     * Returns the indices of matching elements in the list stored at key.
     *
     * @param key the key of the list
     * @param element the element to search for
     * @param params additional parameters for the search
     * @param count the maximum number of matches to return
     * @return list of indices of matching elements
     */
    public List<Long> lpos(String key, String element, LPosParams params, long count) {
        return executeCommandWithGlide(
                "LPOS",
                () -> {
                    LPosOptions options = convertLPosParamsToLPosOptions(params);
                    Long[] result = glideClient.lposCount(key, element, count, options).get();
                    return result != null ? Arrays.asList(result) : Collections.emptyList();
                });
    }

    /**
     * Returns the indices of matching elements in the list stored at key (binary version).
     *
     * @param key the key of the list
     * @param element the element to search for
     * @param params additional parameters for the search
     * @param count the maximum number of matches to return
     * @return list of indices of matching elements
     */
    public List<Long> lpos(final byte[] key, final byte[] element, LPosParams params, long count) {
        return executeCommandWithGlide(
                "LPOS",
                () -> {
                    LPosOptions options = convertLPosParamsToLPosOptions(params);
                    Long[] result =
                            glideClient
                                    .lposCount(GlideString.of(key), GlideString.of(element), count, options)
                                    .get();
                    return result != null ? Arrays.asList(result) : Collections.emptyList();
                });
    }

    /**
     * Atomically moves an element from one list to another.
     *
     * @param srcKey the source list key
     * @param dstKey the destination list key
     * @param from the direction to pop from the source list
     * @param to the direction to push to the destination list
     * @return the element being moved, or null when the source list is empty
     */
    public String lmove(String srcKey, String dstKey, ListDirection from, ListDirection to) {
        return executeCommandWithGlide(
                "LMOVE",
                () -> {
                    glide.api.models.commands.ListDirection glideFrom = convertToGlideListDirection(from);
                    glide.api.models.commands.ListDirection glideTo = convertToGlideListDirection(to);
                    return glideClient.lmove(srcKey, dstKey, glideFrom, glideTo).get();
                });
    }

    /**
     * Atomically moves an element from one list to another (binary version).
     *
     * @param srcKey the source list key
     * @param dstKey the destination list key
     * @param from the direction to pop from the source list
     * @param to the direction to push to the destination list
     * @return the element being moved, or null when the source list is empty
     */
    public byte[] lmove(byte[] srcKey, byte[] dstKey, ListDirection from, ListDirection to) {
        return executeCommandWithGlide(
                "LMOVE",
                () -> {
                    glide.api.models.commands.ListDirection glideFrom = convertToGlideListDirection(from);
                    glide.api.models.commands.ListDirection glideTo = convertToGlideListDirection(to);
                    GlideString result =
                            glideClient
                                    .lmove(GlideString.of(srcKey), GlideString.of(dstKey), glideFrom, glideTo)
                                    .get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Atomically moves an element from one list to another (binary version).
     *
     * @param srcKey the source list key
     * @param dstKey the destination list key
     * @param from the direction to pop from the source list
     * @param to the direction to push to the destination list
     * @param timeout the timeout in seconds
     * @return the element being moved, or null when timeout is reached
     */
    public String blmove(
            String srcKey, String dstKey, ListDirection from, ListDirection to, double timeout) {
        return executeCommandWithGlide(
                "BLMOVE",
                () -> {
                    glide.api.models.commands.ListDirection glideFrom = convertToGlideListDirection(from);
                    glide.api.models.commands.ListDirection glideTo = convertToGlideListDirection(to);
                    return glideClient.blmove(srcKey, dstKey, glideFrom, glideTo, timeout).get();
                });
    }

    /**
     * Blocking version of LMOVE. Atomically moves an element from one list to another (binary
     * version).
     *
     * @param srcKey the source list key
     * @param dstKey the destination list key
     * @param from the direction to pop from the source list
     * @param to the direction to push to the destination list
     * @param timeout the timeout in seconds
     * @return the element being moved, or null when timeout is reached
     */
    public byte[] blmove(
            byte[] srcKey, byte[] dstKey, ListDirection from, ListDirection to, double timeout) {
        return executeCommandWithGlide(
                "BLMOVE",
                () -> {
                    glide.api.models.commands.ListDirection glideFrom = convertToGlideListDirection(from);
                    glide.api.models.commands.ListDirection glideTo = convertToGlideListDirection(to);
                    GlideString result =
                            glideClient
                                    .blmove(
                                            GlideString.of(srcKey), GlideString.of(dstKey), glideFrom, glideTo, timeout)
                                    .get();
                    return result != null ? result.getBytes() : null;
                });
    }

    /**
     * Pops one or more elements from the first non-empty list key from the list of provided key
     * names.
     *
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when no element could
     *     be popped
     */
    public KeyValue<String, List<String>> lmpop(ListDirection direction, String... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            Map<String, String[]> result = glideClient.lmpop(keys, glideDirection).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<String, String[]> entry = result.entrySet().iterator().next();
                return new KeyValue<>(entry.getKey(), Arrays.asList(entry.getValue()));
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("LMPOP operation failed", e);
        }
    }

    /**
     * Pops one or more elements from the first non-empty list key from the list of provided key
     * names.
     *
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param count the maximum number of elements to pop
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when no element could
     *     be popped
     */
    public KeyValue<String, List<String>> lmpop(ListDirection direction, int count, String... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            Map<String, String[]> result = glideClient.lmpop(keys, glideDirection, count).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<String, String[]> entry = result.entrySet().iterator().next();
                return new KeyValue<>(entry.getKey(), Arrays.asList(entry.getValue()));
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("LMPOP operation failed", e);
        }
    }

    /**
     * Pops one or more elements from the first non-empty list key from the list of provided key names
     * (binary version).
     *
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when no element could
     *     be popped
     */
    public KeyValue<byte[], List<byte[]>> lmpop(ListDirection direction, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            Map<GlideString, GlideString[]> result = glideClient.lmpop(glideKeys, glideDirection).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<GlideString, GlideString[]> entry = result.entrySet().iterator().next();
                List<byte[]> values = new ArrayList<>();
                for (GlideString gs : entry.getValue()) {
                    values.add(gs.getBytes());
                }
                return new KeyValue<>(entry.getKey().getBytes(), values);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("LMPOP operation failed", e);
        }
    }

    /**
     * Pops one or more elements from the first non-empty list key from the list of provided key names
     * (binary version).
     *
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param count the maximum number of elements to pop
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when no element could
     *     be popped
     */
    public KeyValue<byte[], List<byte[]>> lmpop(ListDirection direction, int count, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            Map<GlideString, GlideString[]> result =
                    glideClient.lmpop(glideKeys, glideDirection, count).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<GlideString, GlideString[]> entry = result.entrySet().iterator().next();
                List<byte[]> values = new ArrayList<>();
                for (GlideString gs : entry.getValue()) {
                    values.add(gs.getBytes());
                }
                return new KeyValue<>(entry.getKey().getBytes(), values);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("LMPOP operation failed", e);
        }
    }

    /**
     * Blocking version of LMPOP. Pops one or more elements from the first non-empty list key.
     *
     * @param timeout the timeout in seconds
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when timeout is
     *     reached
     */
    public KeyValue<String, List<String>> blmpop(
            double timeout, ListDirection direction, String... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            Map<String, String[]> result = glideClient.blmpop(keys, glideDirection, timeout).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<String, String[]> entry = result.entrySet().iterator().next();
                return new KeyValue<>(entry.getKey(), Arrays.asList(entry.getValue()));
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLMPOP operation failed", e);
        }
    }

    /**
     * Blocking version of LMPOP. Pops one or more elements from the first non-empty list key.
     *
     * @param timeout the timeout in seconds
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param count the maximum number of elements to pop
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when timeout is
     *     reached
     */
    public KeyValue<String, List<String>> blmpop(
            double timeout, ListDirection direction, int count, String... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            Map<String, String[]> result = glideClient.blmpop(keys, glideDirection, count, timeout).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<String, String[]> entry = result.entrySet().iterator().next();
                return new KeyValue<>(entry.getKey(), Arrays.asList(entry.getValue()));
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLMPOP operation failed", e);
        }
    }

    /**
     * Blocking version of LMPOP. Pops one or more elements from the first non-empty list key (binary
     * version).
     *
     * @param timeout the timeout in seconds
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when timeout is
     *     reached
     */
    public KeyValue<byte[], List<byte[]>> blmpop(
            double timeout, ListDirection direction, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            Map<GlideString, GlideString[]> result =
                    glideClient.blmpop(glideKeys, glideDirection, timeout).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<GlideString, GlideString[]> entry = result.entrySet().iterator().next();
                List<byte[]> values = new ArrayList<>();
                for (GlideString gs : entry.getValue()) {
                    values.add(gs.getBytes());
                }
                return new KeyValue<>(entry.getKey().getBytes(), values);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLMPOP operation failed", e);
        }
    }

    /**
     * Blocking version of LMPOP. Pops one or more elements from the first non-empty list key (binary
     * version).
     *
     * @param timeout the timeout in seconds
     * @param direction the direction to pop from (LEFT or RIGHT)
     * @param count the maximum number of elements to pop
     * @param keys the keys to check
     * @return KeyValue containing the key and list of popped elements, or null when timeout is
     *     reached
     */
    public KeyValue<byte[], List<byte[]>> blmpop(
            double timeout, ListDirection direction, int count, byte[]... keys) {
        checkNotClosed();
        ensureInitialized();
        try {
            glide.api.models.commands.ListDirection glideDirection =
                    convertToGlideListDirection(direction);
            GlideString[] glideKeys = convertToGlideStringArray(keys);
            Map<GlideString, GlideString[]> result =
                    glideClient.blmpop(glideKeys, glideDirection, count, timeout).get();
            if (result != null && !result.isEmpty()) {
                Map.Entry<GlideString, GlideString[]> entry = result.entrySet().iterator().next();
                List<byte[]> values = new ArrayList<>();
                for (GlideString gs : entry.getValue()) {
                    values.add(gs.getBytes());
                }
                return new KeyValue<>(entry.getKey().getBytes(), values);
            }
            return null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BLMPOP operation failed", e);
        }
    }

    /**
     * Atomically returns and removes the last element of the list stored at source, and pushes the
     * element at the first element of the list stored at destination.
     *
     * @deprecated Use LMOVE instead
     * @param srckey the source key
     * @param dstkey the destination key
     * @return the element being popped and pushed
     */
    @Deprecated
    public String rpoplpush(String srckey, String dstkey) {
        return lmove(srckey, dstkey, ListDirection.RIGHT, ListDirection.LEFT);
    }

    /**
     * Atomically returns and removes the last element of the list stored at source, and pushes the
     * element at the first element of the list stored at destination (binary version).
     *
     * @deprecated Use LMOVE instead
     * @param srckey the source key
     * @param dstkey the destination key
     * @return the element being popped and pushed
     */
    @Deprecated
    public byte[] rpoplpush(final byte[] srckey, final byte[] dstkey) {
        return lmove(srckey, dstkey, ListDirection.RIGHT, ListDirection.LEFT);
    }

    /**
     * Blocking version of RPOPLPUSH.
     *
     * @deprecated Use BLMOVE instead
     * @param source the source key
     * @param destination the destination key
     * @param timeout the timeout in seconds
     * @return the element being popped and pushed, or null when timeout is reached
     */
    @Deprecated
    public String brpoplpush(String source, String destination, int timeout) {
        return blmove(source, destination, ListDirection.RIGHT, ListDirection.LEFT, timeout);
    }

    /**
     * Blocking version of RPOPLPUSH (binary version).
     *
     * @deprecated Use BLMOVE instead
     * @param source the source key
     * @param destination the destination key
     * @param timeout the timeout in seconds
     * @return the element being popped and pushed, or null when timeout is reached
     */
    @Deprecated
    public byte[] brpoplpush(final byte[] source, final byte[] destination, int timeout) {
        return blmove(source, destination, ListDirection.RIGHT, ListDirection.LEFT, timeout);
    }

    // Static initialization block for cleanup hooks
    static {
        // Add shutdown hook to cleanup temporary certificate files
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        ConfigurationMapper.cleanupTempFiles();
                                    } catch (Exception e) {
                                        // Ignore exceptions during shutdown
                                        System.err.println(
                                                "Warning: Failed to cleanup temporary certificate files during shutdown:");
                                        e.printStackTrace();
                                    }
                                },
                                "Jedis-Certificate-Cleanup"));
    }
}
