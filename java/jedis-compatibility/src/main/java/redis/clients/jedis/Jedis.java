/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GeoSearchParam;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.HGetExParams;
import redis.clients.jedis.params.HSetExParams;
import redis.clients.jedis.params.LCSParams;
import redis.clients.jedis.params.LPosParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XTrimParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.params.ZIncrByParams;
import redis.clients.jedis.params.ZParams;
import redis.clients.jedis.params.ZRangeParams;
import redis.clients.jedis.resps.GeoRadiusResponse;
import redis.clients.jedis.resps.LCSMatchResult;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

/**
 * Jedis 5.x-shaped compatibility facade; behavior is implemented in {@link AbstractGlideJedis}.
 *
 * <p>Shares the {@link JedisCommon} umbrella type with {@link UnifiedJedis}.
 */
public class Jedis extends BaseJedis {

    private static final Logger logger = Logger.getLogger(Jedis.class.getName());

    /** Character encoding used for string-to-byte conversions in Valkey operations. */
    private static final Charset VALKEY_CHARSET = StandardCharsets.UTF_8;

    /** Keyword used in hash field expiration commands to specify the number of fields. */
    private static final String FIELDS_KEYWORD = "FIELDS";

    private volatile GlideClient glideClient; // Changed from final to volatile for lazy init
    private volatile boolean broken;
    private final boolean isPooled;
    private volatile String resourceId; // Changed from final to volatile for lazy init
    private final JedisClientConfig config;
    private Pool<Jedis> dataSource; // Following original Jedis pattern
    private volatile boolean closed = false;
    private volatile boolean lazyInitialized = false; // New field to track initialization

    // Transaction support (Transaction owns its Batch; we only track multi() state)
    private volatile boolean inTransaction = false;

    // Store connection parameters for lazy initialization (nullable for pooled connections)
    private final String host;
    private final int port;

    /** Create a new Jedis instance with default localhost:6379 connection. */
    public Jedis() {
        super(true);
    }

    public Jedis(String host, int port) {
        super(true, host, port);
    }

    public Jedis(String host, int port, boolean useSsl) {
        super(true, host, port, useSsl);
    }

    public Jedis(String host, int port, JedisClientConfig config) {
        super(true, host, port, config);
    }

    public Jedis(
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        super(true, host, port, ssl, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public Jedis(String host, int port, int timeout) {
        super(true, host, port, timeout);
    }

    public Jedis(HostAndPort hostAndPort, JedisClientConfig config) {
        super(true, hostAndPort, config);
    }

    public Jedis(GlideClient glideClient, JedisClientConfig config) {
        super(true, glideClient, config);
    }

    public Jedis(Connection connection) {
        super(true, connection);
    }

    // Stock Jedis parameter types for reflection / linkage; delegate to Abstract* implementations.
    public String set(final String key, final String value, final SetParams params) {
        return set(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String set(final byte[] key, final byte[] value, final SetParams params) {
        return set(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
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
     * Echoes the provided message back.
     *
     * @param message the message to echo
     * @return the echoed message
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a>
     * @since Valkey 1.0.0
     */
    public String echo(String message) {
        return executeCommandWithGlide("ECHO", () -> glideClient.echo(message).get());
    }

    /**
     * Echoes the provided message back (binary version).
     *
     * @param message the message to echo
     * @return the echoed message
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a>
     * @since Valkey 1.0.0
     */
    public byte[] echo(final byte[] message) {
        return executeCommandWithGlide(
                "ECHO",
                () -> {
                    GlideString result = glideClient.echo(GlideString.of(message)).get();
                    return result.getBytes();
                });
    }

    /**
     * Returns the current connection ID.
     *
     * @return the connection ID
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/client-id/">valkey.io</a>
     * @since Valkey 5.0.0
     */
    public long clientId() {
        return executeCommandWithGlide("CLIENT ID", () -> glideClient.clientId().get());
    }

    /**
     * Gets the name of the current connection.
     *
     * @return the connection name, or null if no name is set
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/client-getname/">valkey.io</a>
     * @since Valkey 2.6.9
     */
    public String clientGetName() {
        return executeCommandWithGlide("CLIENT GETNAME", () -> glideClient.clientGetName().get());
    }

    /**
     * Get the name of the current connection. This is an alias for {@link #clientGetName()}.
     *
     * <p>This method name matches Jedis 5.1.5 naming convention (lowercase 'name').
     *
     * @return the connection name, or null if no name is set
     * @see #clientGetName()
     */
    public String clientGetname() {
        return clientGetName();
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
        // TODO (#5457): GLIDE handles database selection differently. This is a placeholder
        // implementation
        // In case of Glide, the databaseId is set in GlideClientConfiguration. Will need to re call
        // the constructor for this to work.

        return "OK";
    }

    /**
     * Executes a custom command without checking inputs. Every part of the command, including the
     * command name and subcommands, should be added as a separate value in the args array.
     *
     * @param args the command and its arguments
     * @return the result of the command execution
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/">valkey.io</a>
     * @since Valkey 1.0.0
     */
    public Object customCommand(String... args) {
        return executeCommandWithGlide("CUSTOM", () -> glideClient.customCommand(args).get());
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
     * Return the list of ACL rules in ACL configuration file format.
     *
     * @return list of user rule definitions
     */
    public List<String> aclList() {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    String[] result = glideClient.aclList().get();
                    return result == null ? Collections.emptyList() : Arrays.asList(result);
                });
    }

    /**
     * Return the ACL rules defined for the given user.
     *
     * @param name the username
     * @return the user's ACL rules, or null if the user does not exist
     */
    public AccessControlUser aclGetUser(String name) {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    Object result = glideClient.aclGetUser(name).get();
                    if (result == null) {
                        return null;
                    }
                    return parseAclGetUserResponse(result);
                });
    }

    /**
     * Create or modify an ACL user with default (no) rules.
     *
     * @param name the username
     * @return "OK" if successful
     */
    public String aclSetUser(String name) {
        return executeCommandWithGlide("ACL", () -> glideClient.aclSetUser(name, new String[0]).get());
    }

    /**
     * Create or modify an ACL user with the given rules.
     *
     * @param name the username
     * @param rules the ACL rule strings (e.g. "on", "+@all", "~*")
     * @return "OK" if successful
     */
    public String aclSetUser(String name, String... rules) {
        return executeCommandWithGlide("ACL", () -> glideClient.aclSetUser(name, rules).get());
    }

    /**
     * Delete the specified ACL users and terminate their connections.
     *
     * @param usernames the usernames to delete
     * @return the number of users deleted
     */
    public long aclDelUser(String... usernames) {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    Long result = glideClient.aclDelUser(usernames).get();
                    return result != null ? result : 0L;
                });
    }

    /**
     * Return the list of ACL categories.
     *
     * @return list of category names
     */
    public List<String> aclCat() {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    String[] result = glideClient.aclCat().get();
                    return result == null ? Collections.emptyList() : Arrays.asList(result);
                });
    }

    /**
     * Return the list of commands in the given ACL category.
     *
     * @param category the category name (e.g. "string", "list")
     * @return list of command names in the category
     */
    public List<String> aclCat(String category) {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    String[] result = glideClient.aclCat(category).get();
                    return result == null ? Collections.emptyList() : Arrays.asList(result);
                });
    }

    /**
     * Generate a random password for ACL users (default bit length).
     *
     * @return the generated password string
     */
    public String aclGenPass() {
        return executeCommandWithGlide("ACL", () -> glideClient.aclGenPass().get());
    }

    /**
     * Generate a random password with the specified number of bits for ACL users.
     *
     * @param bits the number of bits (e.g. 256 for default)
     * @return the generated password string
     */
    public String aclGenPass(int bits) {
        return executeCommandWithGlide("ACL", () -> glideClient.aclGenPass(bits).get());
    }

    /**
     * Return recent ACL security events (failed auth, violated rules).
     *
     * @return list of ACL log entries
     */
    public List<AccessControlLogEntry> aclLog() {
        return executeCommandWithGlide("ACL", () -> parseAclLogResponse(glideClient.aclLog().get()));
    }

    /**
     * Return the specified number of recent ACL security events.
     *
     * @param count the maximum number of entries to return
     * @return list of ACL log entries
     */
    public List<AccessControlLogEntry> aclLog(int count) {
        return executeCommandWithGlide(
                "ACL", () -> parseAclLogResponse(glideClient.aclLog(count).get()));
    }

    /**
     * Clear the ACL security events log.
     *
     * @return "OK" if successful
     */
    public String aclLogReset() {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    Object result = glideClient.customCommand(new String[] {"ACL", "LOG", "RESET"}).get();
                    return result != null ? result.toString() : null;
                });
    }

    /**
     * Return the username the current connection is authenticated as.
     *
     * @return the username (e.g. "default")
     */
    public String aclWhoAmI() {
        return executeCommandWithGlide("ACL", () -> glideClient.aclWhoami().get());
    }

    /**
     * Return the list of ACL usernames.
     *
     * @return list of usernames
     */
    public List<String> aclUsers() {
        return executeCommandWithGlide(
                "ACL",
                () -> {
                    String[] result = glideClient.aclUsers().get();
                    return result == null ? Collections.emptyList() : Arrays.asList(result);
                });
    }

    /**
     * Save the current ACL rules to the configured ACL file.
     *
     * @return "OK" if successful
     */
    public String aclSave() {
        return executeCommandWithGlide("ACL", () -> glideClient.aclSave().get());
    }

    /**
     * Reload ACL rules from the configured ACL file.
     *
     * @return "OK" if successful
     */
    public String aclLoad() {
        return executeCommandWithGlide("ACL", () -> glideClient.aclLoad().get());
    }

    /**
     * Simulate execution of a command by a user without executing it.
     *
     * @param username the username to simulate
     * @param command the command name
     * @param args the command arguments
     * @return "OK" if the user could execute the command, otherwise an error string
     */
    public String aclDryRun(String username, String command, String... args) {
        return executeCommandWithGlide(
                "ACL", () -> glideClient.aclDryRun(username, command, args).get());
    }

    private static AccessControlUser parseAclGetUserResponse(Object result) {
        Object[] arr = (Object[]) result;
        AccessControlUser user = new AccessControlUser();
        for (int i = 0; i + 1 < arr.length; i += 2) {
            String field = arr[i] != null ? arr[i].toString() : null;
            Object value = arr[i + 1];
            if (field == null) {
                continue;
            }
            switch (field) {
                case "flags":
                    if (value instanceof Object[]) {
                        for (Object f : (Object[]) value) {
                            if (f != null) user.addFlag(f.toString());
                        }
                    } else if (value != null) {
                        user.addFlag(value.toString());
                    }
                    break;
                case "passwords":
                    if (value instanceof Object[]) {
                        for (Object p : (Object[]) value) {
                            if (p != null) user.addPassword(p.toString());
                        }
                    } else if (value != null) {
                        user.addPassword(value.toString());
                    }
                    break;
                case "commands":
                    if (value != null) user.setCommands(value.toString());
                    break;
                case "keys":
                    if (value instanceof Object[]) {
                        for (Object k : (Object[]) value) {
                            if (k != null) user.addKey(k.toString());
                        }
                    } else if (value != null) {
                        user.addKey(value.toString());
                    }
                    break;
                case "channels":
                    if (value instanceof Object[]) {
                        for (Object c : (Object[]) value) {
                            if (c != null) user.addChannel(c.toString());
                        }
                    } else if (value != null) {
                        user.addChannel(value.toString());
                    }
                    break;
                default:
                    break;
            }
        }
        return user;
    }

    private static List<AccessControlLogEntry> parseAclLogResponse(Object result) {
        if (result == null) {
            return Collections.emptyList();
        }
        Object[] entries = (Object[]) result;
        List<AccessControlLogEntry> list = new ArrayList<>(entries.length);
        for (Object entryObj : entries) {
            Map<String, Object> map = flatArrayToMap(entryObj);
            map.putIfAbsent(AccessControlLogEntry.ENTRY_ID, 0L);
            map.putIfAbsent(AccessControlLogEntry.TIMESTAMP_CREATED, 0L);
            map.putIfAbsent(AccessControlLogEntry.TIMESTAMP_LAST_UPDATED, 0L);
            Object clientInfo = map.get(AccessControlLogEntry.CLIENT_INFO);
            if (clientInfo == null) {
                map.put(AccessControlLogEntry.CLIENT_INFO, "");
            }
            list.add(new AccessControlLogEntry(map));
        }
        return list;
    }

    private static Map<String, Object> flatArrayToMap(Object entryObj) {
        Map<String, Object> map = new HashMap<>();
        if (!(entryObj instanceof Object[])) {
            return map;
        }
        Object[] pairs = (Object[]) entryObj;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = pairs[i] != null ? pairs[i].toString() : null;
            Object value = pairs[i + 1];
            if (key == null) {
                continue;
            }
            if (value instanceof Number) {
                map.put(key, ((Number) value).longValue());
            } else {
                map.put(key, value != null ? value.toString() : null);
            }
        }
        return map;
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
            if (broken) {
                pool.returnBrokenResource(this);
            } else {
                pool.returnResource(this);
            }
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

    /**
     * Whether this connection is considered broken (must not be returned to the pool as healthy).
     * Matches core Jedis {@code Jedis#isBroken()} semantics for pooled usage.
     */
    public boolean isBroken() {
        return broken;
    }

    /**
     * Marks this connection as broken so the next {@link #close()} returns it to the pool via {@link
     * Pool#returnBrokenResource} instead of {@link Pool#returnResource}.
     */
    public void setBroken(boolean broken) {
        this.broken = broken;
    }

    /**
     * Clears pool association before {@link GlideJedisFactory} destroys the object, so {@link
     * #close()} does not attempt to return this instance to the pool again.
     */
    void detachFromPoolForDestroy() {
        this.dataSource = null;
    }

    /**
     * If this Jedis is pooled and the failure indicates the underlying client is unusable, mark
     * {@link #isBroken()} for correct pool invalidation on {@link #close()}.
     */
    void markBrokenIfPooledConnectionFailure(Throwable throwable) {
        if (!isPooled || throwable == null) {
            return;
        }
        Throwable t = throwable;
        if (t instanceof ExecutionException && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof ConnectionException || t instanceof ClosingException) {
            broken = true;
        }
    }

    /** Reset the closed state for pooled connections. */
    protected void resetForReuse() {
        if (isPooled) {
            broken = false;
            closed = false;
            resetState();
        }
    }

    /**
     * Reset the transaction state. Called by Transaction after exec() or discard(). This is
     * package-private for use by Transaction class.
     */
    void resetState() {
        inTransaction = false;
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JedisException(operationName + " operation interrupted", e);
        } catch (ExecutionException e) {
            markBrokenIfPooledConnectionFailure(e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new JedisException(operationName + " operation failed", cause);
        }
    }

    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction.
     *
     * @param keys the keys to watch
     * @return "OK" if successful
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/watch/">valkey.io</a>
     * @since Valkey 2.2.0
     */
    public String watch(String... keys) {
        return executeCommandWithGlide("WATCH", () -> glideClient.watch(keys).get());
    }

    /**
     * Marks the given keys to be watched for conditional execution of a transaction (binary version).
     *
     * @param keys the keys to watch
     * @return "OK" if successful
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/watch/">valkey.io</a>
     * @since Valkey 2.2.0
     */
    public String watch(byte[]... keys) {
        return executeCommandWithGlide(
                "WATCH",
                () -> {
                    GlideString[] glideKeys = new GlideString[keys.length];
                    for (int i = 0; i < keys.length; i++) {
                        glideKeys[i] = GlideString.of(keys[i]);
                    }
                    return glideClient.watch(glideKeys).get();
                });
    }

    /**
     * Flushes all the previously watched keys for a transaction.
     *
     * @return "OK" if successful
     * @throws JedisException if the operation fails or connection is lost
     * @see <a href="https://valkey.io/commands/unwatch/">valkey.io</a>
     * @since Valkey 2.2.0
     */
    public String unwatch() {
        return executeCommandWithGlide("UNWATCH", () -> glideClient.unwatch().get());
    }

    /**
     * Marks the start of a transaction block. Subsequent commands will be queued for atomic execution
     * using {@link Transaction#exec()}.
     *
     * <p>Commands are queued in a transaction and executed atomically when {@link Transaction#exec()}
     * is called. Use {@link Response#get()} to retrieve command results after execution.
     *
     * <p>Example usage:
     *
     * <pre>{@code
     * Transaction t = jedis.multi();
     * Response<String> r1 = t.set("key", "value");
     * Response<String> r2 = t.get("key");
     * t.exec();
     * String value = r2.get(); // Retrieve the actual value after exec()
     * }</pre>
     *
     * @return a Transaction object for queuing commands
     * @throws JedisException if already in a transaction or operation fails
     * @see <a href="https://valkey.io/commands/multi/">valkey.io</a>
     * @since Valkey 1.2.0
     */
    public synchronized Transaction multi() {
        checkNotClosed();
        ensureInitialized();

        if (inTransaction) {
            throw new JedisException("Already in transaction mode");
        }

        // Transaction owns its own Batch; we only track inTransaction to prevent nested multi()
        inTransaction = true;

        return new Transaction(this);
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
                Set<GlideString> glideSet = new HashSet<>();
                for (GlideString gs : glideArray) {
                    if (gs != null) {
                        glideSet.add(gs);
                    }
                }
                return new GlideStringSetWrapper(glideSet);
            } else if (result instanceof Object[]) {
                // Convert Object[] to Set<GlideString>
                Object[] objArray = (Object[]) result;
                Set<GlideString> glideSet = new HashSet<>();
                for (Object obj : objArray) {
                    if (obj instanceof GlideString) {
                        glideSet.add((GlideString) obj);
                    } else if (obj != null) {
                        glideSet.add(GlideString.of(obj.toString().getBytes(VALKEY_CHARSET)));
                    }
                }
                return new GlideStringSetWrapper(glideSet);
            } else {
                return new GlideStringSetWrapper(new HashSet<>());
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
     * Set multiple key-value pairs, only if none of the keys exist.
     *
     * @param keysvalues alternating keys and values
     * @return true if all keys were set, false if no key was set (at least one key already existed)
     * @see <a href="https://valkey.io/commands/msetnx/">valkey.io</a> for details.
     * @since Valkey 1.0.1
     */
    public long msetnx(String... keysvalues) {
        return executeCommandWithGlide(
                "MSETNX",
                () -> {
                    if (keysvalues.length % 2 == 1) {
                        throw new IllegalArgumentException("keysvalues must be of even length");
                    }
                    Map<String, String> keyValueMap = new HashMap<>();
                    for (int i = 0; i < keysvalues.length; i += 2) {
                        if (i + 1 < keysvalues.length) {
                            keyValueMap.put(keysvalues[i], keysvalues[i + 1]);
                        }
                    }
                    return glideClient.msetnx(keyValueMap).get() ? 1L : 0L;
                });
    }

    /**
     * Set multiple key-value pairs, only if none of the keys exist (binary version).
     *
     * @param keysvalues alternating keys and values
     * @return true if all keys were set, false if no key was set (at least one key already existed)
     * @see <a href="https://valkey.io/commands/msetnx/">valkey.io</a> for details.
     * @since Valkey 1.0.1
     */
    public long msetnx(final byte[]... keysvalues) {
        return executeCommandWithGlide(
                "MSETNX",
                () -> {
                    if (keysvalues.length % 2 == 1) {
                        throw new IllegalArgumentException("keysvalues must be of even length");
                    }
                    Map<GlideString, GlideString> keyValueMap = new HashMap<>();
                    for (int i = 0; i < keysvalues.length; i += 2) {
                        if (i + 1 < keysvalues.length) {
                            keyValueMap.put(GlideString.of(keysvalues[i]), GlideString.of(keysvalues[i + 1]));
                        }
                    }
                    return glideClient.msetnxBinary(keyValueMap).get() ? 1L : 0L;
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
        return setGet(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public byte[] setGet(final byte[] key, final byte[] value, final SetParams params) {
        return setGet(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String getEx(final String key, final GetExParams params) {
        return getEx(key, (redis.clients.jedis.params.AbstractGetExParams<?>) params);
    }

    public byte[] getEx(final byte[] key, final GetExParams params) {
        return getEx(key, (redis.clients.jedis.params.AbstractGetExParams<?>) params);
    }

    public LCSMatchResult lcs(String keyA, String keyB, LCSParams params) {
        return lcs(keyA, keyB, (redis.clients.jedis.params.AbstractLCSParams<?>) params);
    }

    public LCSMatchResult lcs(byte[] keyA, byte[] keyB, LCSParams params) {
        return lcs(keyA, keyB, (redis.clients.jedis.params.AbstractLCSParams<?>) params);
    }

    public ScanResult<String> scan(final String cursor, final ScanParams params) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<String> scan(final String cursor, final ScanParams params, final String type) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params, type);
    }

    public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params, final byte[] type) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params, type);
    }

    public long bitpos(final String key, final boolean value, final BitPosParams params) {
        return bitpos(key, value, (redis.clients.jedis.params.AbstractBitPosParams<?>) params);
    }

    public long bitpos(final byte[] key, final boolean value, final BitPosParams params) {
        return bitpos(key, value, (redis.clients.jedis.params.AbstractBitPosParams<?>) params);
    }

    public long hsetex(String key, HSetExParams params, String field, String value) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, field, value);
    }

    public long hsetex(String key, HSetExParams params, Map<String, String> hash) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, hash);
    }

    public List<String> hgetex(String key, HGetExParams params, String... fields) {
        return hgetex(key, (redis.clients.jedis.params.AbstractHGetExParams<?>) params, fields);
    }

    public long hsetex(byte[] key, HSetExParams params, byte[] field, byte[] value) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, field, value);
    }

    public long hsetex(byte[] key, HSetExParams params, Map<byte[], byte[]> hash) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, hash);
    }

    public List<byte[]> hgetex(byte[] key, HGetExParams params, byte[]... fields) {
        return hgetex(key, (redis.clients.jedis.params.AbstractHGetExParams<?>) params, fields);
    }

    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        return hscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<Map.Entry<byte[], byte[]>> hscan(
            final byte[] key, final byte[] cursor, final ScanParams params) {
        return hscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<String> hscanNoValues(String key, String cursor, ScanParams params) {
        return hscanNoValues(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> hscanNoValues(
            final byte[] key, final byte[] cursor, final ScanParams params) {
        return hscanNoValues(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public StreamEntryID xadd(String key, XAddParams params, Map<String, String> hash) {
        return xadd(key, (redis.clients.jedis.params.AbstractXAddParams<?>) params, hash);
    }

    public byte[] xadd(byte[] key, XAddParams params, Map<byte[], byte[]> hash) {
        return xadd(key, (redis.clients.jedis.params.AbstractXAddParams<?>) params, hash);
    }

    public long xtrim(String key, XTrimParams params) {
        return xtrim(key, (redis.clients.jedis.params.AbstractXTrimParams<?>) params);
    }

    public long xtrim(byte[] key, XTrimParams params) {
        return xtrim(key, (redis.clients.jedis.params.AbstractXTrimParams<?>) params);
    }

    public Long lpos(String key, String element, LPosParams params) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params);
    }

    public Long lpos(final byte[] key, final byte[] element, LPosParams params) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params);
    }

    public List<Long> lpos(String key, String element, LPosParams params, long count) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params, count);
    }

    public List<Long> lpos(final byte[] key, final byte[] element, LPosParams params, long count) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params, count);
    }

    public long zadd(String key, double score, String member, ZAddParams params) {
        return zadd(key, score, member, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public long zadd(byte[] key, double score, byte[] member, ZAddParams params) {
        return zadd(key, score, member, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
        return zadd(key, scoreMembers, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public long zadd(final byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
        return zadd(key, scoreMembers, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public List<String> zrange(String key, ZRangeParams zRangeParams) {
        return zrange(key, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public List<byte[]> zrange(byte[] key, ZRangeParams zRangeParams) {
        return zrange(key, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public Double zincrby(String key, double increment, String member, ZIncrByParams params) {
        return zincrby(
                key, increment, member, (redis.clients.jedis.params.AbstractZIncrByParams<?>) params);
    }

    public Double zincrby(
            final byte[] key, double increment, final byte[] member, ZIncrByParams params) {
        return zincrby(
                key, increment, member, (redis.clients.jedis.params.AbstractZIncrByParams<?>) params);
    }

    public long zunionstore(String dstkey, ZParams params, String... sets) {
        return zunionstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public long zunionstore(final byte[] dstkey, ZParams params, final byte[]... sets) {
        return zunionstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public long zinterstore(String dstkey, ZParams params, String... sets) {
        return zinterstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public long zinterstore(final byte[] dstkey, ZParams params, final byte[]... sets) {
        return zinterstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
        return zscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<Tuple> zscan(byte[] key, byte[] cursor, ScanParams params) {
        return zscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public long zrangestore(String dest, String src, ZRangeParams zRangeParams) {
        return zrangestore(
                dest, src, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public long zrangestore(byte[] dest, byte[] src, ZRangeParams zRangeParams) {
        return zrangestore(
                dest, src, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        return sscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> sscan(final byte[] key, final byte[] cursor, final ScanParams params) {
        return sscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public List<String> sortReadonly(String key, SortingParams sortingParams) {
        return sortReadonly(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public List<byte[]> sortReadonly(final byte[] key, SortingParams sortingParams) {
        return sortReadonly(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public long sort(String key, SortingParams sortingParameters, String dstkey) {
        return sort(
                key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParameters, dstkey);
    }

    public long sort(final byte[] key, SortingParams sortingParameters, final byte[] dstkey) {
        return sort(
                key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParameters, dstkey);
    }

    public List<GeoRadiusResponse> geosearch(String key, GeoSearchParam params) {
        return geosearch(key, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public List<GeoRadiusResponse> geosearch(final byte[] key, GeoSearchParam params) {
        return geosearch(key, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStore(String dest, String src, GeoSearchParam params) {
        return geosearchStore(dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStore(final byte[] dest, final byte[] src, GeoSearchParam params) {
        return geosearchStore(dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStoreStoreDist(String dest, String src, GeoSearchParam params) {
        return geosearchStoreStoreDist(
                dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStoreStoreDist(final byte[] dest, final byte[] src, GeoSearchParam params) {
        return geosearchStoreStoreDist(
                dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }
}
