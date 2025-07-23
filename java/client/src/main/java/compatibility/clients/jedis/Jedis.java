/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
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
            Object result = glideClient.customCommand(new String[] {"SETEX", key, String.valueOf(seconds), value}).get();
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
            Object result = glideClient.customCommand(new String[] {"PSETEX", key, String.valueOf(milliseconds), value}).get();
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
            // Build command arguments
            String[] args = new String[options.length + 2];
            args[0] = "GETEX";
            args[1] = key;
            System.arraycopy(options, 0, args, 2, options.length);
            
            Object result = glideClient.customCommand(args).get();
            return result != null ? result.toString() : null;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETEX operation failed", e);
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
            Object result = glideClient.customCommand(new String[] {"RANDOMKEY"}).get();
            return result != null ? result.toString() : null;
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
     * @return expiration timestamp in seconds, or -1 if key has no expiration, -2 if key does not exist
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
     * @return expiration timestamp in milliseconds, or -1 if key has no expiration, -2 if key does not exist
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
            String[] args = new String[sortingParameters.length + 2];
            args[0] = "SORT";
            args[1] = key;
            System.arraycopy(sortingParameters, 0, args, 2, sortingParameters.length);
            
            Object result = glideClient.customCommand(args).get();
            if (result instanceof String[]) {
                return Arrays.asList((String[]) result);
            } else if (result instanceof Object[]) {
                Object[] objArray = (Object[]) result;
                String[] strArray = new String[objArray.length];
                for (int i = 0; i < objArray.length; i++) {
                    strArray[i] = objArray[i] != null ? objArray[i].toString() : null;
                }
                return Arrays.asList(strArray);
            } else {
                return Arrays.asList();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT operation failed", e);
        }
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
            Object result = glideClient.customCommand(new String[] {"DUMP", key}).get();
            if (result instanceof byte[]) {
                return (byte[]) result;
            } else if (result instanceof String) {
                return ((String) result).getBytes();
            } else {
                return null;
            }
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
            Object result = glideClient.customCommand(new String[] {"RESTORE", key, String.valueOf(ttl), new String(serializedValue)}).get();
            return result != null ? result.toString() : "OK";
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
            Object result = glideClient.customCommand(new String[] {
                "MIGRATE", host, String.valueOf(port), key, String.valueOf(destinationDb), String.valueOf(timeout)
            }).get();
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
            Object result = glideClient.customCommand(new String[] {"MOVE", key, String.valueOf(dbIndex)}).get();
            if (result instanceof Long) {
                return (Long) result;
            } else if (result instanceof Boolean) {
                return ((Boolean) result) ? 1L : 0L;
            } else {
                return Long.parseLong(result.toString());
            }
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
            Object result = glideClient.customCommand(new String[] {"SCAN", cursor}).get();
            if (result instanceof Object[]) {
                Object[] resultArray = (Object[]) result;
                if (resultArray.length >= 2) {
                    // First element is cursor, second is array of keys
                    String newCursor = resultArray[0].toString();
                    Object keysObj = resultArray[1];
                    
                    if (keysObj instanceof Object[]) {
                        Object[] keysArray = (Object[]) keysObj;
                        String[] keys = new String[keysArray.length];
                        for (int i = 0; i < keysArray.length; i++) {
                            keys[i] = keysArray[i] != null ? keysArray[i].toString() : null;
                        }
                        
                        // Return cursor + keys
                        String[] scanResult = new String[keys.length + 1];
                        scanResult[0] = newCursor;
                        System.arraycopy(keys, 0, scanResult, 1, keys.length);
                        return scanResult;
                    }
                }
            }
            return new String[] {"0"};
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
            Object result = glideClient.customCommand(new String[] {"SCAN", cursor, "MATCH", pattern}).get();
            if (result instanceof Object[]) {
                Object[] resultArray = (Object[]) result;
                if (resultArray.length >= 2) {
                    String newCursor = resultArray[0].toString();
                    Object keysObj = resultArray[1];
                    
                    if (keysObj instanceof Object[]) {
                        Object[] keysArray = (Object[]) keysObj;
                        String[] keys = new String[keysArray.length];
                        for (int i = 0; i < keysArray.length; i++) {
                            keys[i] = keysArray[i] != null ? keysArray[i].toString() : null;
                        }
                        
                        String[] scanResult = new String[keys.length + 1];
                        scanResult[0] = newCursor;
                        System.arraycopy(keys, 0, scanResult, 1, keys.length);
                        return scanResult;
                    }
                }
            }
            return new String[] {"0"};
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
     * @return 1 if key was copied, 0 if source key does not exist or destination exists and replace is false
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
}
