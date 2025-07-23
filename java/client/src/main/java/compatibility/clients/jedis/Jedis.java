/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.commands.GetExOptions;
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
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * Jedis compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like API while
 * using Valkey GLIDE underneath.
 */
public class Jedis implements Closeable {

    private static final Logger logger = Logger.getLogger(Jedis.class.getName());

    private final GlideClient glideClient;
    private final boolean isPooled;
    private final String resourceId;
    private final JedisClientConfig config;
    private JedisPool parentPool;
    private volatile boolean closed = false;

    /** Create a new Jedis instance with default localhost:6379 connection. */
    public Jedis() {
        this("localhost", 6379);
    }

    /**
     * Create a new Jedis instance with specified host and port.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     */
    public Jedis(String host, int port) {
        this(host, port, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new Jedis instance with specified host, port and SSL configuration.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param useSsl whether to use SSL/TLS
     */
    public Jedis(String host, int port, boolean useSsl) {
        this(host, port, DefaultJedisClientConfig.builder().ssl(useSsl).build());
    }

    /**
     * Create a new Jedis instance with full configuration.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param config the jedis client configuration
     */
    public Jedis(String host, int port, JedisClientConfig config) {
        this.isPooled = false;
        this.parentPool = null;
        this.config = config;

        // Validate configuration
        ConfigurationMapper.validateConfiguration(config);

        // Map Jedis config to GLIDE config
        GlideClientConfiguration glideConfig = ConfigurationMapper.mapToGlideConfig(host, port, config);

        try {
            this.glideClient = GlideClient.createClient(glideConfig).get();
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE client", e);
        }
    }

    /**
     * Create Jedis with SSL configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param ssl whether to use SSL
     * @param sslSocketFactory SSL socket factory
     * @param sslParameters SSL parameters
     * @param hostnameVerifier hostname verifier
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
     * Internal constructor for pooled connections.
     *
     * @param glideClient the underlying GLIDE client
     * @param pool the parent pool
     * @param config the client configuration
     */
    protected Jedis(GlideClient glideClient, JedisPool pool, JedisClientConfig config) {
        this.glideClient = glideClient;
        this.isPooled = true;
        this.parentPool = pool;
        this.config = config;
        this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
    }

    /**
     * Set the string value of a key.
     *
     * @param key the key
     * @param value the value
     * @return "OK" if successful
     */
    public String set(String key, String value) {
        checkNotClosed();
        try {
            return glideClient.set(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SET operation failed", e);
        }
    }

    /**
     * Get the value of a key.
     *
     * @param key the key
     * @return the value of the key, or null if the key does not exist
     */
    public String get(String key) {
        checkNotClosed();
        try {
            return glideClient.get(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GET operation failed", e);
        }
    }

    /**
     * Test if the server is alive.
     *
     * @return "PONG"
     */
    public String ping() {
        checkNotClosed();
        try {
            return glideClient.ping().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
    }

    /**
     * Test if the server is alive with a custom message.
     *
     * @param message the message to echo back
     * @return the echoed message
     */
    public String ping(String message) {
        checkNotClosed();
        try {
            return glideClient.ping(message).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
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
     * Get the client configuration.
     *
     * @return the configuration
     */
    public JedisClientConfig getConfig() {
        return config;
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
        ResourceLifecycleManager.getInstance().unregisterResource(resourceId);

        if (isPooled && parentPool != null) {
            parentPool.returnResource(this);
        } else {
            try {
                glideClient.close();
            } catch (Exception e) {
                throw new JedisException("Failed to close GLIDE client", e);
            }
        }
    }

    /**
     * Get the underlying GLIDE client. This method is for internal use by the pool.
     *
     * @return the GLIDE client
     */
    protected GlideClient getGlideClient() {
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
     * Delete one or more keys.
     *
     * @param key the key to delete
     * @return the number of keys that were removed
     */
    public Long del(String key) {
        checkNotClosed();
        try {
            return glideClient.del(new String[] {key}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /**
     * Delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public Long del(String... keys) {
        checkNotClosed();
        try {
            return glideClient.del(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
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

    // ===== STRING COMMANDS =====

    /**
     * Set multiple key-value pairs.
     *
     * @param keysvalues alternating keys and values
     * @return "OK"
     */
    public String mset(String... keysvalues) {
        checkNotClosed();
        try {
            Map<String, String> keyValueMap = new HashMap<>();
            for (int i = 0; i < keysvalues.length; i += 2) {
                if (i + 1 < keysvalues.length) {
                    keyValueMap.put(keysvalues[i], keysvalues[i + 1]);
                }
            }
            return glideClient.mset(keyValueMap).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MSET operation failed", e);
        }
    }

    /**
     * Set multiple key-value pairs.
     *
     * @param keyValueMap map of keys to values
     * @return "OK"
     */
    public String mset(Map<String, String> keyValueMap) {
        checkNotClosed();
        try {
            return glideClient.mset(keyValueMap).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MSET operation failed", e);
        }
    }

    /**
     * Get multiple values.
     *
     * @param keys the keys to get
     * @return list of values corresponding to the keys
     */
    public List<String> mget(String... keys) {
        checkNotClosed();
        try {
            String[] result = glideClient.mget(keys).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MGET operation failed", e);
        }
    }

    /**
     * Set key to value if key does not exist.
     *
     * @param key the key
     * @param value the value
     * @return 1 if the key was set, 0 if the key already exists
     */
    public Long setnx(String key, String value) {
        checkNotClosed();
        try {
            Object result = glideClient.customCommand(new String[] {"SETNX", key, value}).get();
            if (result instanceof Long) {
                return (Long) result;
            } else if (result instanceof Boolean) {
                return ((Boolean) result) ? 1L : 0L;
            } else {
                return Long.parseLong(result.toString());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETNX operation failed", e);
        }
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
        checkNotClosed();
        try {
            Object result =
                    glideClient
                            .customCommand(new String[] {"SETEX", key, String.valueOf(seconds), value})
                            .get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETEX operation failed", e);
        }
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
        checkNotClosed();
        try {
            Object result =
                    glideClient
                            .customCommand(new String[] {"PSETEX", key, String.valueOf(milliseconds), value})
                            .get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PSETEX operation failed", e);
        }
    }

    /**
     * Get old value and set new value (deprecated, use setget instead).
     *
     * @param key the key
     * @param value the new value
     * @return the old value, or null if key did not exist
     * @deprecated Use {@link #setget(String, String)} instead
     */
    @Deprecated
    public String getset(String key, String value) {
        return setget(key, value);
    }

    /**
     * Set new value and return old value.
     *
     * @param key the key
     * @param value the new value
     * @return the old value, or null if key did not exist
     */
    public String setget(String key, String value) {
        checkNotClosed();
        try {
            Object result = glideClient.customCommand(new String[] {"GETSET", key, value}).get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETGET operation failed", e);
        }
    }

    /**
     * Get value and delete key.
     *
     * @param key the key
     * @return the value, or null if key did not exist
     */
    public String getdel(String key) {
        checkNotClosed();
        try {
            return glideClient.getdel(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETDEL operation failed", e);
        }
    }

    /**
     * Get value with expiration options.
     *
     * @param key the key
     * @param options expiration options (EX, PX, EXAT, PXAT, PERSIST)
     * @return the value, or null if key does not exist
     */
    public String getex(String key, String... options) {
        checkNotClosed();
        try {
            if (options.length == 0) {
                // Simple GETEX without options
                return glideClient.getex(key).get();
            } else {
                // Parse options and use GLIDE native method with GetExOptions
                GetExOptions getExOptions = parseGetExOptions(options);
                return glideClient.getex(key, getExOptions).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETEX operation failed", e);
        }
    }

    /**
     * Parse Jedis-style GETEX options into GLIDE GetExOptions.
     *
     * @param options the string options array
     * @return parsed GetExOptions
     * @throws IllegalArgumentException if options are invalid
     */
    private GetExOptions parseGetExOptions(String[] options) {
        if (options.length == 1 && "PERSIST".equalsIgnoreCase(options[0])) {
            return GetExOptions.Persist();
        } else if (options.length == 2) {
            String command = options[0].toUpperCase();
            try {
                Long value = Long.parseLong(options[1]);

                switch (command) {
                    case "EX":
                        return GetExOptions.Seconds(value);
                    case "PX":
                        return GetExOptions.Milliseconds(value);
                    case "EXAT":
                        return GetExOptions.UnixSeconds(value);
                    case "PXAT":
                        return GetExOptions.UnixMilliseconds(value);
                    default:
                        throw new IllegalArgumentException("Unknown GETEX option: " + command);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid numeric value for GETEX option: " + options[1], e);
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid GETEX options. Expected: [EX|PX|EXAT|PXAT] <value> or PERSIST");
        }
    }

    /**
     * Append value to key.
     *
     * @param key the key
     * @param value the value to append
     * @return the length of the string after the append operation
     */
    public Long append(String key, String value) {
        checkNotClosed();
        try {
            return glideClient.append(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("APPEND operation failed", e);
        }
    }

    /**
     * Get the length of the string value stored at key.
     *
     * @param key the key
     * @return the length of the string, or 0 if key does not exist
     */
    public Long strlen(String key) {
        checkNotClosed();
        try {
            return glideClient.strlen(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("STRLEN operation failed", e);
        }
    }

    /**
     * Increment the integer value of key by 1.
     *
     * @param key the key
     * @return the value after increment
     */
    public Long incr(String key) {
        checkNotClosed();
        try {
            return glideClient.incr(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("INCR operation failed", e);
        }
    }

    /**
     * Increment the integer value of key by amount.
     *
     * @param key the key
     * @param increment the amount to increment by
     * @return the value after increment
     */
    public Long incrby(String key, long increment) {
        checkNotClosed();
        try {
            return glideClient.incrBy(key, increment).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("INCRBY operation failed", e);
        }
    }

    /**
     * Increment the float value of key by amount.
     *
     * @param key the key
     * @param increment the amount to increment by
     * @return the value after increment
     */
    public Double incrbyfloat(String key, double increment) {
        checkNotClosed();
        try {
            return glideClient.incrByFloat(key, increment).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("INCRBYFLOAT operation failed", e);
        }
    }

    /**
     * Decrement the integer value of key by 1.
     *
     * @param key the key
     * @return the value after decrement
     */
    public Long decr(String key) {
        checkNotClosed();
        try {
            return glideClient.decr(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DECR operation failed", e);
        }
    }

    /**
     * Decrement the integer value of key by amount.
     *
     * @param key the key
     * @param decrement the amount to decrement by
     * @return the value after decrement
     */
    public Long decrby(String key, long decrement) {
        checkNotClosed();
        try {
            return glideClient.decrBy(key, decrement).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DECRBY operation failed", e);
        }
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
    public Long unlink(String... keys) {
        checkNotClosed();
        try {
            return glideClient.unlink(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("UNLINK operation failed", e);
        }
    }

    /**
     * Check if one or more keys exist.
     *
     * @param keys the keys to check
     * @return the number of keys that exist
     */
    public Long exists(String... keys) {
        checkNotClosed();
        try {
            return glideClient.exists(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXISTS operation failed", e);
        }
    }

    /**
     * Get the type of a key.
     *
     * @param key the key
     * @return the type of the key
     */
    public String type(String key) {
        checkNotClosed();
        try {
            return glideClient.type(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TYPE operation failed", e);
        }
    }

    /**
     * Find all keys matching the given pattern (already implemented above).
     *
     * @param pattern the pattern to match
     * @return a set of keys matching the pattern
     */
    // keys method already exists above

    /**
     * Get a random key from the database.
     *
     * @return a random key, or null if the database is empty
     */
    public String randomkey() {
        checkNotClosed();
        try {
            return glideClient.randomKey().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RANDOMKEY operation failed", e);
        }
    }

    /**
     * Rename a key.
     *
     * @param oldkey the old key name
     * @param newkey the new key name
     * @return "OK"
     */
    public String rename(String oldkey, String newkey) {
        checkNotClosed();
        try {
            return glideClient.rename(oldkey, newkey).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RENAME operation failed", e);
        }
    }

    /**
     * Rename a key if the new key does not exist.
     *
     * @param oldkey the old key name
     * @param newkey the new key name
     * @return 1 if the key was renamed, 0 if the new key already exists
     */
    public Long renamenx(String oldkey, String newkey) {
        checkNotClosed();
        try {
            Boolean result = glideClient.renamenx(oldkey, newkey).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RENAMENX operation failed", e);
        }
    }

    /**
     * Set expiration time in seconds.
     *
     * @param key the key
     * @param seconds expiration time in seconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public Long expire(String key, long seconds) {
        checkNotClosed();
        try {
            Boolean result = glideClient.expire(key, seconds).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIRE operation failed", e);
        }
    }

    /**
     * Set expiration time at a specific timestamp.
     *
     * @param key the key
     * @param unixTime expiration timestamp in seconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public Long expireat(String key, long unixTime) {
        checkNotClosed();
        try {
            Boolean result = glideClient.expireAt(key, unixTime).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIREAT operation failed", e);
        }
    }

    /**
     * Set expiration time in milliseconds.
     *
     * @param key the key
     * @param milliseconds expiration time in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public Long pexpire(String key, long milliseconds) {
        checkNotClosed();
        try {
            Boolean result = glideClient.pexpire(key, milliseconds).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIRE operation failed", e);
        }
    }

    /**
     * Set expiration time at a specific millisecond timestamp.
     *
     * @param key the key
     * @param millisecondsTimestamp expiration timestamp in milliseconds
     * @return 1 if expiration was set, 0 if key does not exist
     */
    public Long pexpireat(String key, long millisecondsTimestamp) {
        checkNotClosed();
        try {
            Boolean result = glideClient.pexpireAt(key, millisecondsTimestamp).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIREAT operation failed", e);
        }
    }

    /**
     * Get the expiration timestamp of a key in seconds.
     *
     * @param key the key
     * @return expiration timestamp in seconds, or -1 if key has no expiration, -2 if key does not
     *     exist
     */
    public Long expiretime(String key) {
        checkNotClosed();
        try {
            return glideClient.expiretime(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIRETIME operation failed", e);
        }
    }

    /**
     * Get the expiration timestamp of a key in milliseconds.
     *
     * @param key the key
     * @return expiration timestamp in milliseconds, or -1 if key has no expiration, -2 if key does
     *     not exist
     */
    public Long pexpiretime(String key) {
        checkNotClosed();
        try {
            return glideClient.pexpiretime(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIRETIME operation failed", e);
        }
    }

    /**
     * Get the time to live of a key in seconds.
     *
     * @param key the key
     * @return time to live in seconds, or -1 if key has no expiration, -2 if key does not exist
     */
    public Long ttl(String key) {
        checkNotClosed();
        try {
            return glideClient.ttl(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TTL operation failed", e);
        }
    }

    /**
     * Get the time to live of a key in milliseconds.
     *
     * @param key the key
     * @return time to live in milliseconds, or -1 if key has no expiration, -2 if key does not exist
     */
    public Long pttl(String key) {
        checkNotClosed();
        try {
            return glideClient.pttl(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PTTL operation failed", e);
        }
    }

    /**
     * Remove the expiration from a key.
     *
     * @param key the key
     * @return 1 if expiration was removed, 0 if key does not exist or has no expiration
     */
    public Long persist(String key) {
        checkNotClosed();
        try {
            Boolean result = glideClient.persist(key).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PERSIST operation failed", e);
        }
    }

    /**
     * Sort the elements in a list, set, or sorted set.
     *
     * @param key the key
     * @return the sorted elements
     */
    public List<String> sort(String key) {
        checkNotClosed();
        try {
            String[] result = glideClient.sort(key).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT operation failed", e);
        }
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
    private SortOptions parseSortParameters(String[] params) {
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
        checkNotClosed();
        try {
            return glideClient.dump(GlideString.of(key)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DUMP operation failed", e);
        }
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
        checkNotClosed();
        try {
            return glideClient.restore(GlideString.of(key), ttl, serializedValue).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RESTORE operation failed", e);
        }
    }

    /**
     * Move a key to another Redis instance.
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
    public Long move(String key, int dbIndex) {
        checkNotClosed();
        try {
            Boolean result = glideClient.move(key, dbIndex).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MOVE operation failed", e);
        }
    }

    /**
     * Iterate over keys in the database.
     *
     * @param cursor the cursor
     * @return scan result with new cursor and keys
     */
    public String[] scan(String cursor) {
        checkNotClosed();
        try {
            Object[] result = glideClient.scan(cursor).get();
            return convertScanResult(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Iterate over keys in the database with pattern matching.
     *
     * @param cursor the cursor
     * @param pattern the pattern to match
     * @return scan result with new cursor and keys
     */
    public String[] scan(String cursor, String pattern) {
        checkNotClosed();
        try {
            ScanOptions options = ScanOptions.builder().matchPattern(pattern).build();
            Object[] result = glideClient.scan(cursor, options).get();
            return convertScanResult(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Helper method to convert GLIDE scan result to Jedis format. GLIDE returns Object[] with
     * [cursor, keys_array], Jedis expects String[] with [cursor, key1, key2, ...]
     *
     * @param result the GLIDE scan result
     * @return Jedis-formatted scan result
     */
    private String[] convertScanResult(Object[] result) {
        if (result != null && result.length >= 2) {
            // First element is cursor, second is array of keys
            String newCursor = result[0].toString();
            Object keysObj = result[1];

            if (keysObj instanceof Object[]) {
                Object[] keysArray = (Object[]) keysObj;
                String[] keys = new String[keysArray.length];
                for (int i = 0; i < keysArray.length; i++) {
                    keys[i] = keysArray[i] != null ? keysArray[i].toString() : null;
                }

                // Return cursor + keys in Jedis format
                String[] scanResult = new String[keys.length + 1];
                scanResult[0] = newCursor;
                System.arraycopy(keys, 0, scanResult, 1, keys.length);
                return scanResult;
            }
        }
        return new String[] {"0"};
    }

    /**
     * Update the last access time of keys.
     *
     * @param keys the keys to touch
     * @return the number of keys that were touched
     */
    public Long touch(String... keys) {
        checkNotClosed();
        try {
            return glideClient.touch(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TOUCH operation failed", e);
        }
    }

    /**
     * Copy a key to another key.
     *
     * @param srcKey source key
     * @param dstKey destination key
     * @return 1 if key was copied, 0 if source key does not exist
     */
    public Long copy(String srcKey, String dstKey) {
        checkNotClosed();
        try {
            Boolean result = glideClient.copy(srcKey, dstKey).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("COPY operation failed", e);
        }
    }

    /**
     * Copy a key to another key, optionally replacing the destination.
     *
     * @param srcKey source key
     * @param dstKey destination key
     * @param replace whether to replace the destination key if it exists
     * @return 1 if key was copied, 0 if source key does not exist or destination exists and replace
     *     is false
     */
    public Long copy(String srcKey, String dstKey, boolean replace) {
        checkNotClosed();
        try {
            Boolean result = glideClient.copy(srcKey, dstKey, replace).get();
            return result ? 1L : 0L;
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
        checkNotClosed();
        try {
            Long result = glideClient.setbit(key, offset, value ? 1L : 0L).get();
            return result == 1L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETBIT operation failed", e);
        }
    }

    /**
     * Returns the bit value at offset in the string value stored at key.
     *
     * @param key the key
     * @param offset the bit offset
     * @return the bit value stored at offset
     */
    public boolean getbit(String key, long offset) {
        checkNotClosed();
        try {
            Long result = glideClient.getbit(key, offset).get();
            return result == 1L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETBIT operation failed", e);
        }
    }

    /**
     * Count the number of set bits in a string.
     *
     * @param key the key
     * @return the number of bits set to 1
     */
    public long bitcount(String key) {
        checkNotClosed();
        try {
            return glideClient.bitcount(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITCOUNT operation failed", e);
        }
    }

    /**
     * Count the number of set bits in a string between start and end offsets.
     *
     * @param key the key
     * @param start the start offset (byte index)
     * @param end the end offset (byte index)
     * @return the number of bits set to 1
     */
    public long bitcount(String key, long start, long end) {
        checkNotClosed();
        try {
            return glideClient.bitcount(key, start, end).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITCOUNT operation failed", e);
        }
    }

    /**
     * Return the position of the first bit set to 1 or 0 in a string.
     *
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0)
     * @return the position of the first bit set to the specified value, or -1 if not found
     */
    public long bitpos(String key, boolean value) {
        checkNotClosed();
        try {
            return glideClient.bitpos(key, value ? 1L : 0L).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITPOS operation failed", e);
        }
    }

    /**
     * Return the position of the first bit set to 1 or 0 in a string within a range.
     *
     * @param key the key
     * @param value the bit value to search for (true for 1, false for 0)
     * @param start the start offset (byte index)
     * @param end the end offset (byte index)
     * @return the position of the first bit set to the specified value, or -1 if not found
     */
    public long bitpos(String key, boolean value, long start, long end) {
        checkNotClosed();
        try {
            return glideClient.bitpos(key, value ? 1L : 0L, start, end).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITPOS operation failed", e);
        }
    }

    /**
     * Perform bitwise operations between strings.
     *
     * @param op the bitwise operation (AND, OR, XOR, NOT)
     * @param destkey the destination key
     * @param srckeys the source keys
     * @return the size of the string stored in the destination key
     */
    public long bitop(BitOP op, String destkey, String... srckeys) {
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
                default:
                    throw new IllegalArgumentException("Unsupported bitwise operation: " + op);
            }
            return glideClient.bitop(operation, destkey, srckeys).get();
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
    public List<Long> bitfield(String key, String... arguments) {
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
     * Perform read-only bitfield operations on a string.
     *
     * @param key the key
     * @param arguments the bitfield arguments
     * @return list of results from the bitfield operations
     */
    public List<Long> bitfieldReadonly(String key, String... arguments) {
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
     * Parse Jedis-style bitfield arguments into GLIDE BitFieldSubCommands.
     *
     * @param args the Jedis-style arguments (GET/SET/INCRBY/OVERFLOW followed by parameters)
     * @return array of BitFieldSubCommands
     */
    private BitFieldSubCommands[] parseBitFieldArguments(String[] args) {
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
    private BitFieldReadOnlySubCommands[] parseBitFieldReadOnlyArguments(String[] args) {
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
    private Object parseEncoding(String encodingStr) {
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
    private Object parseOffset(String offsetStr) {
        if (offsetStr.startsWith("#")) {
            long offset = Long.parseLong(offsetStr.substring(1));
            return new OffsetMultiplier(offset);
        } else {
            long offset = Long.parseLong(offsetStr);
            return new Offset(offset);
        }
    }

    /** Create BitFieldGet with proper interface types. */
    private BitFieldGet createBitFieldGet(String encodingStr, String offsetStr) {
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
    private BitFieldSet createBitFieldSet(String encodingStr, String offsetStr, long value) {
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
    private BitFieldIncrby createBitFieldIncrby(
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
    private BitOverflowControl parseOverflowControl(String controlStr) {
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
    private String[] concatenateArrays(String[] array1, String[] array2) {
        String[] result = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    /** Enum for bitwise operations to match Jedis BitOP enum. */
    public enum BitOP {
        AND,
        OR,
        XOR,
        NOT
    }

    // ===== HYPERLOGLOG COMMANDS =====

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified key. Creates a new
     * structure if the key does not exist.
     *
     * @param key the key of the HyperLogLog data structure
     * @param elements the elements to add to the HyperLogLog
     * @return 1 if the HyperLogLog is newly created or modified, 0 otherwise
     */
    public long pfadd(String key, String... elements) {
        checkNotClosed();
        try {
            Boolean result = glideClient.pfadd(key, elements).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFADD operation failed", e);
        }
    }

    /**
     * Estimates the cardinality of the data stored in a HyperLogLog structure for a single key.
     *
     * @param key the key of the HyperLogLog data structure
     * @return the approximated cardinality of the HyperLogLog data structure
     */
    public long pfcount(String key) {
        checkNotClosed();
        try {
            return glideClient.pfcount(new String[] {key}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFCOUNT operation failed", e);
        }
    }

    /**
     * Estimates the cardinality of the data stored in multiple HyperLogLog structures by calculating
     * the combined cardinality of multiple keys.
     *
     * @param keys the keys of the HyperLogLog data structures to be analyzed
     * @return the approximated cardinality of the combined HyperLogLog data structures
     */
    public long pfcount(String... keys) {
        checkNotClosed();
        try {
            return glideClient.pfcount(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFCOUNT operation failed", e);
        }
    }

    /**
     * Merges multiple HyperLogLog values into a unique value. If the destination variable exists, it
     * is treated as one of the source HyperLogLog data sets, otherwise a new HyperLogLog is created.
     *
     * @param destkey the key of the destination HyperLogLog where the merged data sets will be stored
     * @param sourcekeys the keys of the HyperLogLog structures to be merged
     * @return "OK" if successful
     */
    public String pfmerge(String destkey, String... sourcekeys) {
        checkNotClosed();
        try {
            return glideClient.pfmerge(destkey, sourcekeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFMERGE operation failed", e);
        }
    }
}
