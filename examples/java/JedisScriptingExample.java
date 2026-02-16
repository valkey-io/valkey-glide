/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.FlushMode;

/**
 * Example demonstrating Jedis compatibility layer scripting features. Shows usage of EVAL, EVALSHA,
 * SCRIPT management, FCALL, and FUNCTION commands.
 */
public class JedisScriptingExample {

    public static void main(String[] args) {
        // Connect to Valkey server
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            System.out.println("Connected to Valkey server");

            // Lua scripting examples
            demonstrateLuaScripting(jedis);

            // Function examples (requires Valkey 7.0+)
            try {
                demonstrateFunctions(jedis);
            } catch (Exception e) {
                System.out.println(
                        "Skipping function examples - requires Valkey 7.0+: " + e.getMessage());
            }
        }
    }

    /** Demonstrates Lua scripting with EVAL, EVALSHA, and SCRIPT commands. */
    private static void demonstrateLuaScripting(Jedis jedis) {
        System.out.println("\n=== Lua Scripting Examples ===");

        // 1. Simple EVAL
        System.out.println("\n1. Simple EVAL:");
        Object result = jedis.eval("return 'Hello from Lua!'");
        System.out.println("Result: " + result);

        // 2. EVAL with keys and arguments
        System.out.println("\n2. EVAL with keys and arguments:");
        jedis.set("mykey", "myvalue");
        Object value = jedis.eval("return redis.call('GET', KEYS[1])", 1, "mykey");
        System.out.println("Value from script: " + value);

        // 3. EVAL with multiple keys and arguments
        System.out.println("\n3. EVAL with multiple keys and arguments:");
        String script = "return {KEYS[1], KEYS[2], ARGV[1], ARGV[2]}";
        List<String> keys = Arrays.asList("key1", "key2");
        List<String> args = Arrays.asList("arg1", "arg2");
        Object multiResult = jedis.eval(script, keys, args);
        if (multiResult instanceof Object[]) {
            System.out.println("Results: " + Arrays.toString((Object[]) multiResult));
        }

        // 4. SCRIPT LOAD and EVALSHA
        System.out.println("\n4. SCRIPT LOAD and EVALSHA:");
        String luaScript = "return ARGV[1] .. ' World!'";
        String sha1 = jedis.scriptLoad(luaScript);
        System.out.println("Script SHA1: " + sha1);

        // Check if script exists
        List<Boolean> exists = jedis.scriptExists(sha1);
        System.out.println("Script exists: " + exists.get(0));

        // Execute by SHA1
        Object shaResult =
                jedis.evalsha(sha1, Collections.emptyList(), Collections.singletonList("Hello"));
        System.out.println("EVALSHA result: " + shaResult);

        // 5. Complex script with Redis operations
        System.out.println("\n5. Complex script with Redis operations:");
        jedis.set("counter", "0");
        String counterScript =
                "redis.call('INCR', KEYS[1])\n"
                        + "redis.call('INCR', KEYS[1])\n"
                        + "return redis.call('GET', KEYS[1])";
        Object counterResult = jedis.eval(counterScript, 1, "counter");
        System.out.println("Counter after two increments: " + counterResult);

        // 6. SCRIPT management
        System.out.println("\n6. SCRIPT management:");
        String anotherScript = "return 'test'";
        String sha2 = jedis.scriptLoad(anotherScript);

        // Check multiple scripts
        List<Boolean> multiExists = jedis.scriptExists(sha1, sha2);
        System.out.println("First script exists: " + multiExists.get(0));
        System.out.println("Second script exists: " + multiExists.get(1));

        // Flush scripts
        System.out.println("Flushing script cache...");
        String flushResult = jedis.scriptFlush(FlushMode.SYNC);
        System.out.println("Flush result: " + flushResult);

        // Verify scripts are flushed
        List<Boolean> afterFlush = jedis.scriptExists(sha1);
        System.out.println("Script exists after flush: " + afterFlush.get(0));
    }

    /**
     * Demonstrates Valkey Functions with FCALL, FUNCTION LOAD, and FUNCTION management commands.
     * Requires Valkey 7.0 or higher.
     */
    private static void demonstrateFunctions(Jedis jedis) {
        System.out.println("\n=== Valkey Functions Examples ===");

        // 1. Load a simple function
        System.out.println("\n1. Loading a simple function:");
        String simpleLib =
                "#!lua name=simplelib\n"
                        + "redis.register_function('greet', function(keys, args)\n"
                        + "  return 'Hello, ' .. args[1]\n"
                        + "end)";
        String libName = jedis.functionLoad(simpleLib);
        System.out.println("Loaded library: " + libName);

        // Call the function
        Object greeting =
                jedis.fcall("greet", Collections.emptyList(), Collections.singletonList("World"));
        System.out.println("Function result: " + greeting);

        // 2. Load a function that uses keys and arguments
        System.out.println("\n2. Function with keys and arguments:");
        String dataLib =
                "#!lua name=datalib\n"
                        + "redis.register_function('setget', function(keys, args)\n"
                        + "  redis.call('SET', keys[1], args[1])\n"
                        + "  return redis.call('GET', keys[1])\n"
                        + "end)";
        jedis.functionLoad(dataLib);

        Object setGetResult =
                jedis.fcall(
                        "setget",
                        Collections.singletonList("mykey"),
                        Collections.singletonList("myvalue"));
        System.out.println("Set and get result: " + setGetResult);

        // 3. List functions
        System.out.println("\n3. Listing functions:");
        List<Object> functions = jedis.functionList();
        System.out.println("Number of libraries loaded: " + functions.size());

        // List functions with code
        List<Object> functionsWithCode = jedis.functionListWithCode();
        System.out.println("Functions with code: " + functionsWithCode.size() + " libraries");

        // 4. Function DUMP and RESTORE
        System.out.println("\n4. Function DUMP and RESTORE:");
        byte[] dump = jedis.functionDump();
        System.out.println("Dumped " + dump.length + " bytes");

        // Flush functions
        jedis.functionFlush();
        System.out.println("Functions flushed");

        // Restore from dump
        String restoreResult = jedis.functionRestore(dump);
        System.out.println("Restore result: " + restoreResult);

        // Verify restoration
        Object verifyResult =
                jedis.fcall("greet", Collections.emptyList(), Collections.singletonList("Again"));
        System.out.println("Function after restore: " + verifyResult);

        // 5. Function stats
        System.out.println("\n5. Function statistics:");
        Object stats = jedis.functionStats();
        System.out.println("Function stats retrieved: " + (stats != null));

        // 6. Replace a function
        System.out.println("\n6. Replacing a function:");
        String replacedLib =
                "#!lua name=simplelib\n"
                        + "redis.register_function('greet', function(keys, args)\n"
                        + "  return 'Hi, ' .. args[1] .. '!'\n"
                        + "end)";
        String replacedName = jedis.functionLoadReplace(replacedLib);
        System.out.println("Replaced library: " + replacedName);

        Object newGreeting =
                jedis.fcall(
                        "greet", Collections.emptyList(), Collections.singletonList("Replacement"));
        System.out.println("New function result: " + newGreeting);

        // Clean up
        System.out.println("\n7. Cleaning up:");
        jedis.functionDelete("simplelib");
        jedis.functionDelete("datalib");
        System.out.println("Libraries deleted");
    }
}
