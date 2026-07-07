/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.AZ_CLUSTER_HOSTS;
import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static glide.TestConfiguration.TLS;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static glide.utils.Java8Utils.createMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import glide.api.models.configuration.ServiceType;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.cluster.ValkeyCluster;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestUtilities {
    /** Key names for versions returned in info command. */
    private static final String VALKEY_VERSION_KEY = "valkey_version";

    /** Expected server responses for BGSAVE and BGSAVE SCHEDULE. */
    public static final Set<String> BGSAVE_RESPONSES =
            new java.util.HashSet<>(
                    Arrays.asList("Background saving started", "Background saving scheduled"));

    /** Expected server error response for BGSAVE CANCEL when no save is in progress. */
    public static final String BGSAVE_NOT_CANCELLED_RESPONSE =
            "Background saving is currently not in progress or scheduled";

    /** Expected server responses for BGREWRITEAOF. */
    public static final Set<String> BGREWRITEAOF_RESPONSES =
            new java.util.HashSet<>(
                    Arrays.asList(
                            "Background append only file rewriting started",
                            "Background append only file rewriting scheduled"));

    /** Route for routing to a single primary node by slot key. */
    public static final SingleNodeRoute PRIMARY_SLOT_ROUTE = new SlotKeyRoute("1", SlotType.PRIMARY);

    private static final String REDIS_VERSION_KEY = "redis_version";

    /** IAM authentication test constants */
    public static final String IAM_USERNAME = "default";

    public static final String IAM_TEST_CLUSTER_NAME = "test-cluster";

    public static final String IAM_TEST_REGION_US_EAST_1 = "us-east-1";

    /**
     * Checks if the current operating system is Windows.
     *
     * @return true if running on Windows, false otherwise
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * Creates a Glide client for testing purposes
     *
     * @param addresses Optional list of node addresses
     * @param valkeyCluster Optional ValkeyCluster instance
     * @param lazyConnect Whether to connect lazily
     * @return A BaseClient that resolves to either a GlideClient or GlideClusterClient
     */
    @SneakyThrows
    public static BaseClient createDedicatedClient(
            boolean clusterMode,
            List<NodeAddress> addresses,
            ValkeyCluster valkeyCluster,
            Boolean lazyConnect) {

        if (valkeyCluster == null) {
            throw new IllegalArgumentException(
                    "ValkeyCluster instance is required for create dedicated client");
        }

        // For cluster mode, select k random seed nodes (k = min(3, total nodes))
        if (clusterMode) {
            List<NodeAddress> seedNodes = addresses;
            if (seedNodes == null) {
                List<NodeAddress> allNodes = valkeyCluster.getNodesAddr();
                int k = Math.min(3, allNodes.size());
                seedNodes =
                        new Random()
                                .ints(0, allNodes.size())
                                .distinct()
                                .limit(k)
                                .mapToObj(allNodes::get)
                                .collect(Collectors.toList());
            }

            return GlideClusterClient.createClient(
                            GlideClusterClientConfiguration.builder()
                                    .addresses(seedNodes)
                                    .requestTimeout(2000)
                                    .lazyConnect(lazyConnect)
                                    // Explicitly set no credentials for dedicated clusters to avoid
                                    // authentication issues from environment or global state
                                    .credentials(null)
                                    .advancedConfiguration(
                                            AdvancedGlideClusterClientConfiguration.builder()
                                                    .connectionTimeout(10000)
                                                    .build())
                                    .build())
                    .get();
        } else {
            List<NodeAddress> nodeAddresses =
                    addresses != null ? addresses : valkeyCluster.getNodesAddr();

            return GlideClient.createClient(
                            GlideClientConfiguration.builder()
                                    .addresses(nodeAddresses)
                                    .requestTimeout(2000)
                                    .lazyConnect(lazyConnect)
                                    // Explicitly set no credentials for dedicated clusters to avoid
                                    // authentication issues from environment or global state
                                    .credentials(null)
                                    .advancedConfiguration(
                                            AdvancedGlideClientConfiguration.builder().connectionTimeout(10000).build())
                                    .build())
                    .get();
        }
    }

    /** Extract integer parameter value from INFO command output */
    public static long getValueFromInfo(String data, String value) {
        for (String line : data.split("\r\n")) {
            if (line.contains(value)) {
                return Long.parseLong(line.split(":")[1]);
            }
        }
        fail("Key '" + value + "' not found in INFO output");
        return 0;
    }

    /** Extract first key from {@link ClusterValue} assuming it contains a multi-value. */
    public static <T> String getFirstKeyFromMultiValue(ClusterValue<T> data) {
        return data.getMultiValue().keySet().toArray(new String[0])[0];
    }

    /** Extract first value from {@link ClusterValue} assuming it contains a multi-value. */
    public static <T> T getFirstEntryFromMultiValue(ClusterValue<T> data) {
        return data.getMultiValue().get(getFirstKeyFromMultiValue(data));
    }

    /** Generates a random string of a specified length using ASCII letters. */
    public static String getRandomString(int length) {
        String asciiLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(asciiLetters.length());
            char randomChar = asciiLetters.charAt(index);
            sb.append(randomChar);
        }

        return sb.toString();
    }

    /**
     * Transforms server info string into a Map, using lines with ":" to create key-value pairs,
     * replacing duplicates with the last encountered value.
     */
    public static Map<String, String> parseInfoResponseToMap(String serverInfo) {
        return Arrays.stream(serverInfo.split("\n"))
                .filter(line -> line.contains(":"))
                .map(line -> line.split(":", 2))
                .collect(
                        Collectors.toMap(
                                parts -> parts[0].trim(),
                                parts -> parts[1].trim(),
                                (existingValue, newValue) -> newValue,
                                LinkedHashMap::new));
    }

    // copied from glide.utils.ArrayTransformUtils.concatenateArrays, because it is not exported
    /**
     * Concatenates multiple arrays of type T and returns a single concatenated array.
     *
     * @param arrays Varargs parameter for arrays to be concatenated.
     * @param <T> The type of the elements in the arrays.
     * @return A concatenated array of type T.
     */
    @SafeVarargs
    public static <T> T[] concatenateArrays(T[]... arrays) {
        return Stream.of(arrays).flatMap(Stream::of).toArray(size -> Arrays.copyOf(arrays[0], size));
    }

    public static GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?>
            commonClientConfig() {
        GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> builder =
                GlideClientConfiguration.builder();
        for (String host : STANDALONE_HOSTS) {
            String[] parts = host.split(":");
            builder.address(
                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
        }
        return builder.useTLS(TLS);
    }

    /**
     * Reads the CA certificate from the cluster_manager's TLS certificates directory.
     *
     * @return The CA certificate bytes in PEM format
     * @throws Exception if the certificate file cannot be read
     */
    @SneakyThrows
    public static byte[] getCaCertificate() {
        String glideHome =
                System.getenv().getOrDefault("GLIDE_HOME_DIR", System.getProperty("user.dir") + "/../..");
        Path caCertPath = Paths.get(glideHome, "utils/tls_crts/ca.crt");
        return Files.readAllBytes(caCertPath);
    }

    public static GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?>
            commonClusterClientConfig() {
        GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?> builder =
                GlideClusterClientConfiguration.builder();
        for (String host : CLUSTER_HOSTS) {
            String[] parts = host.split(":");
            builder.address(
                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
        }
        return builder.useTLS(TLS);
    }

    public static GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?>
            azClusterClientConfig() {
        GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?> builder =
                GlideClusterClientConfiguration.builder();
        for (String host : AZ_CLUSTER_HOSTS) {
            if (host.isEmpty()) {
                continue;
            }
            String[] parts = host.split(":");
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                        "Invalid host format: " + host + ". Expected format: host:port");
            }
            builder.address(
                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
        }
        return builder.useTLS(TLS);
    }

    /** Number of times {@link #createClientWithRetry} attempts the initial connect. */
    public static final int MAX_CONNECT_ATTEMPTS = 3;

    /** Backoff between {@link #createClientWithRetry} attempts, in milliseconds. */
    public static final long CONNECT_RETRY_BACKOFF_MILLIS = 1000;

    /**
     * Creates a client by issuing fresh {@code createClient} attempts, retrying a bounded number of
     * times when the initial connect fails transiently.
     *
     * <p>Under heavy CI load the server may not be accepting connections yet, so the first {@code
     * createClient} can fail with e.g. "Connection refused" or "Request timed out" and sink an entire
     * {@code @BeforeAll} setup before a single command runs. This helper retries the whole {@code
     * createClient} call ({@value #MAX_CONNECT_ATTEMPTS} attempts, {@value
     * #CONNECT_RETRY_BACKOFF_MILLIS}ms backoff) to survive that window. See issue #5343.
     *
     * <p>Glide's native reconnect strategy ({@code reconnectStrategy} / core {@code
     * connection_retry_strategy}) is intentionally not used for this: it does retry the initial
     * connect, but the whole retry loop is bounded by {@code connectionTimeout} (default ~2000ms, see
     * {@code reconnecting_connection.rs} which wraps the loop in {@code timeout(connection_timeout,
     * ...)}). Once that elapses {@code createClient} rejects, and the "retry forever" background
     * reconnect only runs on a connection the awaited future has already abandoned. A server that is
     * slow to accept for several seconds therefore needs fresh {@code createClient} attempts, each
     * with a new connection-timeout budget, which is exactly what this helper provides. Matching that
     * with native config would require globally raising {@code connectionTimeout}, slowing every
     * other path that uses {@link #commonClientConfig()} / {@link #commonClusterClientConfig()}.
     *
     * <p>Only {@link ExecutionException} (a failed connect surfaced from {@code
     * CompletableFuture.get()}) is retried; an {@link InterruptedException} is intentionally not
     * retried and propagates.
     *
     * @param clientFactory supplies a fresh {@code createClient} future on each attempt
     * @param <T> the client type produced (e.g. {@code GlideClient} or {@code GlideClusterClient})
     * @return the connected client
     */
    @SneakyThrows
    public static <T> T createClientWithRetry(Supplier<CompletableFuture<T>> clientFactory) {
        ExecutionException lastException = null;
        for (int attempt = 1; attempt <= MAX_CONNECT_ATTEMPTS; attempt++) {
            try {
                return clientFactory.get().get();
            } catch (ExecutionException e) {
                lastException = e;
                if (attempt < MAX_CONNECT_ATTEMPTS) {
                    Thread.sleep(CONNECT_RETRY_BACKOFF_MILLIS);
                }
            }
        }
        throw lastException;
    }

    /**
     * Deep traverse and compare two objects, including comparing content of all nested collections
     * recursively. Floating point numbers comparison performed with <code>1e-6</code> delta.
     *
     * @apiNote <code>Map</code> and <code>Set</code> comparison ignores element order.<br>
     *     <code>List</code> and <code>Array</code> comparison is order-sensitive.
     */
    public static void assertDeepEquals(Object expected, Object actual) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual);
        } else if (expected.getClass().isArray()) {
            Object[] expectedArray = (Object[]) expected;
            Object[] actualArray = (Object[]) actual;
            assertEquals(expectedArray.length, actualArray.length);
            for (int i = 0; i < expectedArray.length; i++) {
                assertDeepEquals(expectedArray[i], actualArray[i]);
            }
        } else if (expected instanceof List) {
            List<?> expectedList = (List<?>) expected;
            List<?> actualList = (List<?>) actual;
            assertEquals(expectedList.size(), actualList.size());
            for (int i = 0; i < expectedList.size(); i++) {
                assertDeepEquals(expectedList.get(i), actualList.get(i));
            }
        } else if (expected instanceof Set) {
            Set<?> expectedSet = (Set<?>) expected;
            Set<?> actualSet = (Set<?>) actual;
            assertEquals(expectedSet.size(), actualSet.size());
            assertTrue(expectedSet.containsAll(actualSet) && actualSet.containsAll(expectedSet));
        } else if (expected instanceof Map) {
            Map<?, ?> expectedMap = (Map<?, ?>) expected;
            Map<?, ?> actualMap = (Map<?, ?>) actual;
            assertEquals(expectedMap.size(), actualMap.size());
            for (Object key : expectedMap.keySet()) {
                assertDeepEquals(expectedMap.get(key), actualMap.get(key));
            }
        } else if (expected instanceof Double || actual instanceof Double) {
            assertEquals((Double) expected, (Double) actual, 1e-6);
        } else {
            assertEquals(expected, actual);
        }
    }

    /**
     * Validate whether `FUNCTION LIST` response contains required info.
     *
     * @param response The response from valkey.
     * @param libName Expected library name.
     * @param functionDescriptions Expected function descriptions. Key - function name, value -
     *     description.
     * @param functionFlags Expected function flags. Key - function name, value - flags set.
     * @param libCode Expected library to check if given.
     */
    @SuppressWarnings("unchecked")
    public static void checkFunctionListResponse(
            Map<String, Object>[] response,
            String libName,
            Map<String, String> functionDescriptions,
            Map<String, Set<String>> functionFlags,
            Optional<String> libCode) {
        assertTrue(response.length > 0);
        boolean hasLib = false;
        for (Map<String, Object> lib : response) {
            hasLib = lib.containsValue(libName);
            if (hasLib) {
                Object[] functions = (Object[]) lib.get("functions");
                assertEquals(functionDescriptions.size(), functions.length);
                for (Object functionInfo : functions) {
                    Map<String, Object> function = (Map<String, Object>) functionInfo;
                    String functionName = (String) function.get("name");
                    assertEquals(functionDescriptions.get(functionName), function.get("description"));
                    assertEquals(functionFlags.get(functionName), function.get("flags"));
                }
                if (libCode.isPresent()) {
                    assertEquals(libCode.get(), lib.get("library_code"));
                }
                break;
            }
        }
        assertTrue(hasLib);
    }

    private static <T> void assertSetsEqual(Set<T> expected, Set<T> actual) {
        // Convert both sets to lists. It is needed due to issue that rust return the flags as string
        List<GlideString> expectedList =
                expected.stream().sorted().map(GlideString::of).collect(Collectors.toList());
        List<GlideString> actualList =
                actual.stream().sorted().map(GlideString::of).collect(Collectors.toList());

        assertEquals(expectedList, actualList);
    }

    /**
     * Validate whether `FUNCTION LIST` response contains required info.
     *
     * @param response The response from valkey.
     * @param libName Expected library name.
     * @param functionDescriptions Expected function descriptions. Key - function name, value -
     *     description.
     * @param functionFlags Expected function flags. Key - function name, value - flags set.
     * @param libCode Expected library to check if given.
     */
    @SuppressWarnings("unchecked")
    public static void checkFunctionListResponseBinary(
            Map<GlideString, Object>[] response,
            GlideString libName,
            Map<GlideString, GlideString> functionDescriptions,
            Map<GlideString, Set<GlideString>> functionFlags,
            Optional<GlideString> libCode) {
        assertTrue(response.length > 0);
        boolean hasLib = false;
        for (Map<GlideString, Object> lib : response) {
            hasLib = lib.containsValue(libName);
            if (hasLib) {
                Object[] functions = (Object[]) lib.get(gs("functions"));
                assertEquals(functionDescriptions.size(), functions.length);
                for (Object functionInfo : functions) {
                    Map<GlideString, Object> function = (Map<GlideString, Object>) functionInfo;
                    GlideString functionName = (GlideString) function.get(gs("name"));
                    assertEquals(functionDescriptions.get(functionName), function.get(gs("description")));
                    assertSetsEqual(
                            functionFlags.get(functionName), (Set<GlideString>) function.get(gs("flags")));
                }
                if (libCode.isPresent()) {
                    assertEquals(libCode.get(), lib.get(gs("library_code")));
                }
                break;
            }
        }
        assertTrue(hasLib);
    }

    /**
     * Validate whether `FUNCTION STATS` response contains required info.
     *
     * @param response The response from server.
     * @param runningFunction Command line of running function expected. Empty, if nothing expected.
     * @param libCount Expected libraries count.
     * @param functionCount Expected functions count.
     */
    public static void checkFunctionStatsResponse(
            Map<String, Map<String, Object>> response,
            String[] runningFunction,
            long libCount,
            long functionCount) {
        Map<String, Object> runningScriptInfo = response.get("running_script");
        if (runningScriptInfo == null && runningFunction.length != 0) {
            fail("No running function info");
        }
        if (runningScriptInfo != null && runningFunction.length == 0) {
            String[] command = (String[]) runningScriptInfo.get("command");
            fail("Unexpected running function info: " + String.join(" ", command));
        }

        if (runningScriptInfo != null) {
            String[] command = (String[]) runningScriptInfo.get("command");
            assertArrayEquals(runningFunction, command);
            // command line format is:
            // fcall|fcall_ro <function name> <num keys> <key>* <arg>*
            assertEquals(runningFunction[1], runningScriptInfo.get("name"));
        }
        Map<String, Object> expected =
                createMap("LUA", createMap("libraries_count", libCount, "functions_count", functionCount));
        assertEquals(expected, response.get("engines"));
    }

    /**
     * Validate whether `FUNCTION STATS` response contains required info.
     *
     * @param response The response from server.
     * @param runningFunction Command line of running function expected. Empty, if nothing expected.
     * @param libCount Expected libraries count.
     * @param functionCount Expected functions count.
     */
    public static void checkFunctionStatsBinaryResponse(
            Map<GlideString, Map<GlideString, Object>> response,
            GlideString[] runningFunction,
            long libCount,
            long functionCount) {
        Map<GlideString, Object> runningScriptInfo = response.get(gs("running_script"));
        if (runningScriptInfo == null && runningFunction.length != 0) {
            fail("No running function info");
        }
        if (runningScriptInfo != null && runningFunction.length == 0) {
            GlideString[] command = (GlideString[]) runningScriptInfo.get(gs("command"));
            fail("Unexpected running function info: " + String.join(" ", Arrays.toString(command)));
        }

        if (runningScriptInfo != null) {
            GlideString[] command = (GlideString[]) runningScriptInfo.get(gs("command"));
            assertArrayEquals(runningFunction, command);
            // command line format is:
            // fcall|fcall_ro <function name> <num keys> <key>* <arg>*
            assertEquals(runningFunction[1], runningScriptInfo.get(gs("name")));
        }
        Map<GlideString, Object> expected =
                createMap(
                        gs("LUA"),
                        createMap(gs("libraries_count"), libCount, gs("functions_count"), functionCount));
        assertEquals(expected, response.get(gs("engines")));
    }

    /** Generate a String of LUA library code. */
    public static String generateLuaLibCode(
            String libName, Map<String, String> functions, boolean readonly) {
        StringBuilder code = new StringBuilder("#!lua name=" + libName + "\n");
        for (Map.Entry<String, String> function : functions.entrySet()) {
            code.append("redis.register_function{ function_name = '")
                    .append(function.getKey())
                    .append("', callback = function(keys, args) ")
                    .append(function.getValue())
                    .append(" end");
            if (readonly) {
                code.append(", flags = { 'no-writes' }");
            }
            code.append(" }\n");
        }
        return code.toString();
    }

    /** Generate a Glidestring of LUA library code. */
    public static GlideString generateLuaLibCodeBinary(
            GlideString libName, Map<GlideString, GlideString> functions, boolean readonly) {

        Map<String, String> transformedMap =
                functions.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        entry -> entry.getKey().toString(), entry -> entry.getValue().toString()));

        return gs(generateLuaLibCode(libName.toString(), transformedMap, readonly));
    }

    /**
     * Create a lua lib with a function which runs an endless loop up to timeout sec.<br>
     * Execution takes at least 5 sec regardless of the timeout configured.
     */
    public static String createLuaLibWithLongRunningFunction(
            String libName, String funcName, int timeout, boolean readOnly) {
        String code =
                "#!lua name=$libName\n"
                        + "local function $libName_$funcName(keys, args)\n"
                        + "  local started = tonumber(redis.pcall('time')[1])\n"
                        // fun fact - redis does no writes if 'no-writes' flag is set
                        + "  redis.pcall('set', keys[1], 42)\n"
                        + "  while (true) do\n"
                        + "    local now = tonumber(redis.pcall('time')[1])\n"
                        + "    if now > started + $timeout then\n"
                        + "      return 'Timed out $timeout sec'\n"
                        + "    end\n"
                        + "  end\n"
                        + "  return 'OK'\n"
                        + "end\n"
                        + "redis.register_function{\n"
                        + "function_name='$funcName',\n"
                        + "callback=$libName_$funcName,\n"
                        + (readOnly ? "flags={ 'no-writes' }\n" : "")
                        + "}";
        return code.replace("$timeout", Integer.toString(timeout))
                .replace("$funcName", funcName)
                .replace("$libName", libName);
    }

    /**
     * Create a lua script which runs an endless loop up to timeout sec.<br>
     * Execution takes at least 5 sec regardless of the timeout configured.
     */
    public static String createLongRunningLuaScript(int timeout, boolean readOnly) {
        String script =
                readOnly
                        ? "  local started = tonumber(redis.pcall('time')[1])\n"
                                + "  while (true) do\n"
                                + "    local now = tonumber(redis.pcall('time')[1])\n"
                                + "    if now > started + $timeout then\n"
                                + "      return 'Timed out $timeout sec'\n"
                                + "    end\n"
                                + "  end\n"
                        : "redis.call('SET', KEYS[1], 'value')\n"
                                + "  local start = redis.call('time')[1]\n"
                                + "  while redis.call('time')[1] - start < $timeout do\n"
                                + "      redis.call('SET', KEYS[1], 'value')\n"
                                + "   end\n";
        return script.replace("$timeout", Integer.toString(timeout));
    }

    /**
     * Lock test until server completes a script/function execution.
     *
     * @param lambda Client api reference to use for checking the server.
     */
    public static void waitForNotBusy(Supplier<CompletableFuture<?>> lambda) {
        // If function wasn't killed, and it didn't time out - it blocks the server and cause rest
        // test to fail.
        boolean isBusy = true;
        do {
            try {
                lambda.get().get();
            } catch (Exception busy) {
                // should throw `notbusy` error, because the function should be killed before
                if (busy.getMessage().toLowerCase().contains("notbusy")) {
                    isBusy = false;
                }
            }
        } while (isBusy);
    }

    /**
     * This method returns the server version using a glide client.
     *
     * @param client Glide client to be used for running the info command.
     * @return String The server version number.
     */
    @SneakyThrows
    public static String getServerVersion(@NonNull final BaseClient client) {
        String infoResponse =
                client instanceof GlideClient
                        ? ((GlideClient) client).info(new Section[] {Section.SERVER}).get()
                        : ((GlideClusterClient) client)
                                .info(new Section[] {Section.SERVER}, RANDOM)
                                .get()
                                .getSingleValue();
        Map<String, String> infoResponseMap = parseInfoResponseToMap(infoResponse);
        if (infoResponseMap.containsKey(VALKEY_VERSION_KEY)) {
            return infoResponseMap.get(VALKEY_VERSION_KEY);
        } else if (infoResponseMap.containsKey(REDIS_VERSION_KEY)) {
            return infoResponseMap.get(REDIS_VERSION_KEY);
        }
        return null;
    }

    /**
     * Delete an ACL user and assert it was deleted.
     *
     * @param client Glide client to be used for running the ACL DELUSER command.
     * @param username The username of the ACL user to be deleted.
     */
    @SneakyThrows
    public static void deleteAclUser(GlideClient client, String username) {
        try {
            assertEquals(1L, client.customCommand(new String[] {"ACL", "DELUSER", username}).get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Set an ACL user and a password for it.
     *
     * @param client Glide client to be used for running the ACL SETUSER command.
     * @param username The username of the ACL user to be registered.
     * @param password The password of the ACL user to be registered.
     */
    @SneakyThrows
    public static void setNewAclUserPassword(GlideClient client, String username, String password) {
        try {
            assertEquals(
                    OK,
                    client
                            .customCommand(
                                    new String[] {
                                        "ACL", "SETUSER", username, "on", ">" + password, "~*", "&*", "+@all",
                                    })
                            .get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Delete an ACL user and assert it was deleted.
     *
     * @param client Glide client to be used for running the ACL DELUSER command.
     * @param username The username of the ACL user to be deleted.
     */
    @SneakyThrows
    public static void deleteAclUser(GlideClusterClient client, String username) {
        try {
            assertEquals(
                    1L,
                    client.customCommand(new String[] {"ACL", "DELUSER", username}).get().getSingleValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Set an ACL user and a password for it.
     *
     * @param client Glide client to be used for running the ACL SETUSER command.
     * @param username The username of the ACL user to be registered.
     * @param password The password of the ACL user to be registered.
     */
    @SneakyThrows
    public static void setNewAclUserPassword(
            GlideClusterClient client, String username, String password) {
        try {
            assertEquals(
                    OK,
                    client
                            .customCommand(
                                    new String[] {
                                        "ACL", "SETUSER", username, "on", ">" + password, "~*", "&*", "+@all",
                                    })
                            .get()
                            .getSingleValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static GlideClientConfiguration createStandaloneConfigWithRootCert(
            byte[] caCert, NodeAddress nodeAddr) {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();
        AdvancedGlideClientConfiguration advancedConfig =
                AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build();
        GlideClientConfiguration config =
                GlideClientConfiguration.builder()
                        .address(nodeAddr)
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();
        return config;
    }

    public static GlideClusterClientConfiguration createClusterConfigWithRootCert(
            byte[] caCert, List<NodeAddress> clusterNodes) {
        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();
        AdvancedGlideClusterClientConfiguration advancedConfig =
                AdvancedGlideClusterClientConfiguration.builder()
                        .tlsAdvancedConfiguration(tlsConfig)
                        .build();
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(clusterNodes)
                        .useTLS(true)
                        .advancedConfiguration(advancedConfig)
                        .build();
        return config;
    }

    /** Assert that the given client is connected. */
    @SneakyThrows
    public static void assertConnected(BaseClient client) {
        final String expected = "PONG";
        if (client instanceof GlideClusterClient) {
            assertEquals(expected, ((GlideClusterClient) client).ping().get());
        } else {
            assertEquals(expected, ((GlideClient) client).ping().get());
        }
    }

    /**
     * Gets the number of connected replicas for the primary node targeted by the given route.
     *
     * <p>Sends {@code INFO REPLICATION} to the primary identified by {@code primaryRoute} and parses
     * the {@code connected_slaves} field from the response.
     *
     * @param client the cluster client to query
     * @param primaryRoute a route targeting the desired primary node
     * @return the number of connected replicas
     */
    @SneakyThrows
    public static long getReplicaCount(GlideClusterClient client, Route primaryRoute) {
        ClusterValue<Object> replicationInfo =
                client.customCommand(new String[] {"INFO", "REPLICATION"}, primaryRoute).get();
        return Long.parseLong(
                Stream.of(((String) replicationInfo.getSingleValue()).split("\\R"))
                        .map(line -> line.split(":", 2))
                        .filter(parts -> parts.length == 2 && parts[0].trim().equals("connected_slaves"))
                        .map(parts -> parts[1].trim())
                        .findFirst()
                        .orElseThrow(
                                () -> new RuntimeException("connected_slaves not found in INFO REPLICATION")));
    }

    /**
     * Creates a test IAM authentication configuration.
     *
     * @param refreshIntervalSeconds The refresh interval in seconds for IAM token refresh
     * @return IamAuthConfig configured for testing
     */
    public static IamAuthConfig createTestIamConfig(int refreshIntervalSeconds) {
        return IamAuthConfig.builder()
                .clusterName(IAM_TEST_CLUSTER_NAME)
                .service(ServiceType.ELASTICACHE)
                .region(IAM_TEST_REGION_US_EAST_1)
                .refreshIntervalSeconds(refreshIntervalSeconds)
                .build();
    }

    /**
     * Waits until no save (RDB save or AOF rewrite) is in progress.
     *
     * @param client The client to query.
     */
    public static void waitForSaveNotInProgress(@NonNull final BaseClient client) {
        waitFor(() -> !isSaveInProgress(client), "Timed out waiting for save to complete");
    }

    /**
     * Waits until a condition is met.
     *
     * @param condition A callable that returns {@code true} when the desired state is reached.
     * @param failure Message to include in the assertion if the timeout is exceeded.
     */
    @SneakyThrows
    public static void waitFor(Callable<Boolean> condition, String failure) {
        long sleep = 100;
        long timeout = 10000;

        while (timeout > 0) {
            if (condition.call()) {
                return;
            }

            Thread.sleep(sleep);
            timeout -= sleep;
        }

        fail(failure);
    }

    /**
     * Returns {@code true} if a save (RDB save or AOF rewrite) is in progress on any node.
     *
     * @param client The client to query.
     */
    @SneakyThrows
    private static boolean isSaveInProgress(@NonNull final BaseClient client) {
        List<String> infos;
        if (client instanceof GlideClient) {
            infos = Collections.singletonList(((GlideClient) client).info().get());
        } else {
            ClusterValue<String> clusterInfo = ((GlideClusterClient) client).info(ALL_PRIMARIES).get();
            infos = new ArrayList<>(clusterInfo.getMultiValue().values());
        }

        return infos.stream()
                .anyMatch(
                        info ->
                                info.contains("rdb_bgsave_in_progress:1")
                                        || info.contains("aof_rewrite_in_progress:1"));
    }

    /** Returns the current server time as a Unix timestamp in seconds. */
    @SneakyThrows
    public static long getUnixSeconds(BaseClient client) {

        // TODO #6166: Use a base client method to call time() directly.
        if (client instanceof GlideClusterClient) {
            return Long.parseLong(((GlideClusterClient) client).time().get()[0]);
        }

        return Long.parseLong(((GlideClient) client).time().get()[0]);
    }

    /** Asserts that a CLIENT TRACKINGINFO response matches expected tracking state. */
    @SuppressWarnings("unchecked")
    public static void assertClientTrackingInfo(Map<String, Object> info, boolean on) {
        assertNotNull(info);
        assertEquals(3, info.size());

        Set<String> flags = (Set<String>) info.get("flags");
        Long redirect = (Long) info.get("redirect");
        Object[] prefixes = (Object[]) info.get("prefixes");

        if (on) {
            assertTrue(flags.contains("on"));
            assertTrue(flags.contains("bcast"));
            assertEquals(0L, redirect);
            assertEquals(1, prefixes.length);
            assertEquals("", prefixes[0].toString());
        } else {
            assertTrue(flags.contains("off"));
            assertEquals(-1L, redirect);
            assertEquals(0, prefixes.length);
        }
    }

    /**
     * Validates that a MEMORY STATS response map contains expected fields with correct types.
     *
     * @param stats The memory stats map to validate.
     */
    @SuppressWarnings("unchecked")
    public static void assertMemoryStatsFields(Map<String, Object> stats) {
        assertNotNull(stats);
        assertFalse(stats.isEmpty());

        // db.0 is only populated if the node has at least one key. In cluster mode, it will only
        // be present if that key is stored on that node. Standalone and single-node cluster tests
        // validate db.0 directly via assertMemoryStatsDbEntry.
        if (stats.containsKey("db.0")) {
            assertMemoryStatsDbEntry((Map<String, Object>) stats.get("db.0"));
        }

        assertTrue((Long) stats.get("allocator.active") > 0);
        assertTrue((Long) stats.get("allocator.allocated") > 0);
        assertTrue((Long) stats.get("allocator-fragmentation.bytes") >= 0);
        assertTrue((Long) stats.get("allocator.resident") > 0);
        assertInstanceOf(Long.class, stats.get("allocator-rss.bytes"));
        assertTrue((Long) stats.get("aof.buffer") >= 0);
        assertTrue((Long) stats.get("clients.normal") >= 0);
        assertTrue((Long) stats.get("clients.slaves") >= 0);
        // dataset.bytes (net data memory after subtracting overhead) can be negative depending on
        // engine memory accounting, so only assert type/presence rather than a non-negative value.
        assertInstanceOf(Long.class, stats.get("dataset.bytes"));
        assertInstanceOf(Long.class, stats.get("fragmentation.bytes"));
        assertTrue((Long) stats.get("keys.bytes-per-key") >= 0);
        assertTrue((Long) stats.get("keys.count") >= 0);
        assertTrue((Long) stats.get("lua.caches") >= 0);
        assertTrue((Long) stats.get("overhead.total") > 0);
        assertTrue((Long) stats.get("peak.allocated") > 0);
        assertTrue((Long) stats.get("replication.backlog") >= 0);
        assertInstanceOf(Long.class, stats.get("rss-overhead.bytes"));
        assertTrue((Long) stats.get("startup.allocated") > 0);
        assertTrue((Long) stats.get("total.allocated") > 0);

        assertTrue((Double) stats.get("allocator-fragmentation.ratio") >= 0);
        assertTrue((Double) stats.get("allocator-rss.ratio") >= 0);
        assertTrue((Double) stats.get("dataset.percentage") >= 0);
        assertTrue((Double) stats.get("fragmentation") >= 0);
        assertTrue((Double) stats.get("peak.percentage") >= 0);
        assertTrue((Double) stats.get("rss-overhead.ratio") >= 0);

        // Optional Redis 7.0+ fields
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            assertTrue((Long) stats.get("cluster.links") >= 0);
            assertTrue((Long) stats.get("functions.caches") >= 0);
        } else {
            assertFalse(stats.containsKey("cluster.links"));
            assertFalse(stats.containsKey("functions.caches"));
        }

        // Optional Valkey 8.0+ fields
        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            assertTrue((Long) stats.get("allocator.muzzy") >= 0);
            assertTrue((Long) stats.get("db.dict.rehashing.count") >= 0);
            assertTrue((Long) stats.get("overhead.db.hashtable.lut") >= 0);
            assertTrue((Long) stats.get("overhead.db.hashtable.rehashing") >= 0);
        } else {
            assertFalse(stats.containsKey("allocator.muzzy"));
            assertFalse(stats.containsKey("db.dict.rehashing.count"));
            assertFalse(stats.containsKey("overhead.db.hashtable.lut"));
            assertFalse(stats.containsKey("overhead.db.hashtable.rehashing"));
        }
    }

    /**
     * Validates that a MEMORY STATS db entry map has expected fields with correct types and values.
     *
     * @param dbMap The db entry map (e.g. from stats.get("db.0")).
     */
    public static void assertMemoryStatsDbEntry(Map<String, Object> dbMap) {
        assertNotNull(dbMap);
        assertInstanceOf(Map.class, dbMap);
        assertTrue((Long) dbMap.get("overhead.hashtable.expires") >= 0);
        assertTrue((Long) dbMap.get("overhead.hashtable.main") >= 0);
    }
}
