/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.FlushMode;
import redis.clients.jedis.args.FunctionRestorePolicy;

/**
 * Integration tests for Jedis scripting and function commands. Tests EVAL, EVALSHA, SCRIPT
 * management, FCALL, and FUNCTION management commands.
 */
public class JedisScriptingIntegTest {

    // Server configuration - dynamically resolved from CI environment
    private static final String valkeyHost;
    private static final int valkeyPort;

    private Jedis jedis;

    static {
        String[] standaloneHosts = STANDALONE_HOSTS;

        if (standaloneHosts.length == 0 || standaloneHosts[0].trim().isEmpty()) {
            throw new IllegalStateException(
                    "Standalone server configuration not found in system properties. "
                            + "Please set 'test.server.standalone' system property with server address "
                            + "(e.g., -Dtest.server.standalone=localhost:6379)");
        }

        String firstHost = standaloneHosts[0].trim();
        String[] hostPort = firstHost.split(":");

        if (hostPort.length == 2) {
            try {
                valkeyHost = hostPort[0];
                valkeyPort = Integer.parseInt(hostPort[1]);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Invalid port number in standalone server configuration: "
                                + firstHost
                                + ". "
                                + "Expected format: host:port (e.g., localhost:6379)",
                        e);
            }
        } else {
            throw new IllegalStateException(
                    "Invalid standalone server format: "
                            + firstHost
                            + ". "
                            + "Expected format: host:port (e.g., localhost:6379)");
        }
    }

    @BeforeEach
    void setup() {
        jedis = new Jedis(valkeyHost, valkeyPort);
        jedis.connect();
        assertNotNull(jedis, "Jedis instance should be created successfully");
    }

    @AfterEach
    void teardown() {
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testEvalBasic() {
        Object result = jedis.eval("return 'hello'");
        assertEquals("hello", result);
    }

    @Test
    void testEvalWithScript() {
        Object result = jedis.eval("return 42");
        assertEquals(42L, result);
    }

    @Test
    void testEvalWithKeys() {
        jedis.set("key1", "value1");
        Object result = jedis.eval("return redis.call('GET', KEYS[1])", 1, "key1");
        assertEquals("value1", result);
    }

    @Test
    void testEvalWithKeysAndArgs() {
        Object result =
                jedis.eval(
                        "return {KEYS[1], ARGV[1]}",
                        Collections.singletonList("mykey"),
                        Collections.singletonList("myarg"));
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(2, arr.length);
        assertEquals("mykey", arr[0]);
        assertEquals("myarg", arr[1]);
    }

    @Test
    void testEvalWithMultipleKeysAndArgs() {
        List<String> keys = Arrays.asList("key1", "key2");
        List<String> args = Arrays.asList("arg1", "arg2", "arg3");
        Object result = jedis.eval("return {KEYS[1], KEYS[2], ARGV[1], ARGV[2], ARGV[3]}", keys, args);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(5, arr.length);
    }

    @Test
    void testScriptLoadAndEvalsha() {
        String script = "return ARGV[1]";
        String sha1 = jedis.scriptLoad(script);
        assertNotNull(sha1);
        assertEquals(40, sha1.length()); // SHA1 is 40 characters

        List<Boolean> exists = jedis.scriptExists(sha1);
        assertNotNull(exists);
        assertEquals(1, exists.size());
        assertTrue(exists.get(0));

        Object result = jedis.evalsha(sha1, Collections.emptyList(), Collections.singletonList("test"));
        assertEquals("test", result);
    }

    @Test
    void testScriptExists() {
        String script1 = "return 1";
        String script2 = "return 2";

        String sha1 = jedis.scriptLoad(script1);
        String sha2 = jedis.scriptLoad(script2);

        List<Boolean> exists = jedis.scriptExists(sha1, sha2);
        assertEquals(2, exists.size());
        assertTrue(exists.get(0));
        assertTrue(exists.get(1));

        // Check non-existent script
        String fakeSha = "0000000000000000000000000000000000000000";
        List<Boolean> exists2 = jedis.scriptExists(fakeSha);
        assertEquals(1, exists2.size());
        assertFalse(exists2.get(0));
    }

    @Test
    void testScriptFlush() {
        String script = "return 'test'";
        String sha1 = jedis.scriptLoad(script);

        List<Boolean> existsBefore = jedis.scriptExists(sha1);
        assertTrue(existsBefore.get(0));

        String result = jedis.scriptFlush();
        assertEquals("OK", result);

        List<Boolean> existsAfter = jedis.scriptExists(sha1);
        assertFalse(existsAfter.get(0));
    }

    @Test
    void testScriptFlushWithMode() {
        String script = "return 'test'";
        jedis.scriptLoad(script);

        String result = jedis.scriptFlush(FlushMode.SYNC);
        assertEquals("OK", result);
    }

    @Test
    void testEvalReadonly() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for EVAL_RO");

        jedis.set("key1", "value1");
        Object result =
                jedis.evalReadonly(
                        "return redis.call('GET', KEYS[1])",
                        Collections.singletonList("key1"),
                        Collections.emptyList());
        assertEquals("value1", result);
    }

    @Test
    void testEvalshaReadonly() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for EVALSHA_RO");

        jedis.set("key1", "value1");
        String script = "return redis.call('GET', KEYS[1])";
        String sha1 = jedis.scriptLoad(script);

        Object result =
                jedis.evalshaReadonly(sha1, Collections.singletonList("key1"), Collections.emptyList());
        assertEquals("value1", result);
    }

    @Test
    void testFunctionLoadAndCall() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=mylib\n"
                        + "redis.register_function('myfunc', function(keys, args) return args[1] end)";
        String libName = jedis.functionLoad(lib);
        assertEquals("mylib", libName);

        Object result = jedis.fcall("myfunc", Collections.emptyList(), Collections.singletonList("42"));
        assertEquals("42", result);

        // Clean up
        jedis.functionDelete("mylib");
    }

    @Test
    void testFunctionLoadReplace() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib1 =
                "#!lua name=replacelib\n"
                        + "redis.register_function('func1', function(keys, args) return 1 end)";
        jedis.functionLoad(lib1);

        String lib2 =
                "#!lua name=replacelib\n"
                        + "redis.register_function('func2', function(keys, args) return 2 end)";
        String libName = jedis.functionLoadReplace(lib2);
        assertEquals("replacelib", libName);

        Object result = jedis.fcall("func2", Collections.emptyList(), Collections.emptyList());
        assertEquals(2L, result);

        // Clean up
        jedis.functionDelete("replacelib");
    }

    @Test
    void testFunctionList() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=listlib\n"
                        + "redis.register_function('listfunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        List<Object> functions = jedis.functionList();
        assertNotNull(functions);
        assertTrue(functions.size() > 0);

        // Clean up
        jedis.functionDelete("listlib");
    }

    @Test
    void testFunctionListWithPattern() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=patternlib\n"
                        + "redis.register_function('patternfunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        List<Object> functions = jedis.functionList("pattern*");
        assertNotNull(functions);

        // Clean up
        jedis.functionDelete("patternlib");
    }

    @Test
    void testFunctionListWithCode() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=codelib\n"
                        + "redis.register_function('codefunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        List<Object> functions = jedis.functionListWithCode();
        assertNotNull(functions);
        assertTrue(functions.size() > 0);

        // Clean up
        jedis.functionDelete("codelib");
    }

    @Test
    void testFunctionDumpAndRestore() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=dumplib\n"
                        + "redis.register_function('dumpfunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        byte[] dump = jedis.functionDump();
        assertNotNull(dump);
        assertTrue(dump.length > 0);

        // Flush and restore
        jedis.functionFlush();
        String result = jedis.functionRestore(dump);
        assertEquals("OK", result);

        // Verify function is restored
        Object callResult = jedis.fcall("dumpfunc", Collections.emptyList(), Collections.emptyList());
        assertEquals(1L, callResult);

        // Clean up
        jedis.functionDelete("dumplib");
    }

    @Test
    void testFunctionRestoreWithPolicy() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=policylib\n"
                        + "redis.register_function('policyfunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        byte[] dump = jedis.functionDump();

        // Restore with FLUSH policy
        String result = jedis.functionRestore(dump, FunctionRestorePolicy.FLUSH);
        assertEquals("OK", result);

        // Clean up
        jedis.functionDelete("policylib");
    }

    @Test
    void testFunctionFlush() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=flushlib\n"
                        + "redis.register_function('flushfunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        String result = jedis.functionFlush();
        assertEquals("OK", result);

        List<Object> functions = jedis.functionList();
        assertEquals(0, functions.size());
    }

    @Test
    void testFunctionFlushWithMode() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=flushmodelib\n"
                        + "redis.register_function('flushmodefunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        String result = jedis.functionFlush(FlushMode.SYNC);
        assertEquals("OK", result);
    }

    @Test
    void testFunctionDelete() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=deletelib\n"
                        + "redis.register_function('deletefunc', function(keys, args) return 1 end)";
        jedis.functionLoad(lib);

        String result = jedis.functionDelete("deletelib");
        assertEquals("OK", result);
    }

    @Test
    void testFunctionStats() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        Object stats = jedis.functionStats();
        assertNotNull(stats);
    }

    @Test
    void testFcallReadonly() {
        // Requires Valkey 7.0+
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "Valkey version 7.0 or higher is required for functions");

        String lib =
                "#!lua name=rolib\n"
                        + "redis.register_function{function_name='rofunc', callback=function(keys, args) return"
                        + " args[1] end, flags={'no-writes'}}";
        jedis.functionLoad(lib);

        Object result =
                jedis.fcallReadonly(
                        "rofunc", Collections.emptyList(), Collections.singletonList("readonly"));
        assertEquals("readonly", result);

        // Clean up
        jedis.functionDelete("rolib");
    }
}
