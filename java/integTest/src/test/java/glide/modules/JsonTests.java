/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.json.JsonGetOptions;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JsonTests {

    private static GlideClusterClient client;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        client =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(5000).build())
                        .get();
        client.flushall(FlushMode.SYNC, ALL_PRIMARIES).get();
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        client.close();
    }

    @Test
    @SneakyThrows
    public void check_module_loaded() {
        var info = client.info(new Section[] {Section.MODULES}, RANDOM).get().getSingleValue();
        assertTrue(info.contains("# json_core_metrics"));
    }

    @Test
    @SneakyThrows
    public void json_set_get() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": 1.0,\"b\": 2}";

        assertEquals(OK, Json.set(client, key, "$", jsonValue).get());

        String getResult = Json.get(client, key).get();

        assertEquals(JsonParser.parseString(jsonValue), JsonParser.parseString(getResult));

        String getResultWithMultiPaths = Json.get(client, key, new String[] {"$.a", "$.b"}).get();

        assertEquals(
                JsonParser.parseString("{\"$.a\":[1.0],\"$.b\":[2]}"),
                JsonParser.parseString(getResultWithMultiPaths));

        assertNull(Json.get(client, "non_existing_key").get());
        assertEquals("[]", Json.get(client, key, new String[] {"$.d"}).get());
    }

    @Test
    @SneakyThrows
    public void json_set_get_multiple_values() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": {\"c\": 1, \"d\": 4}, \"b\": {\"c\": 2}, \"c\": true}";

        assertEquals(OK, Json.set(client, gs(key), gs("$"), gs(jsonValue)).get());

        GlideString getResult = Json.get(client, gs(key), new GlideString[] {gs("$..c")}).get();

        assertEquals(
                JsonParser.parseString("[true, 1, 2]"), JsonParser.parseString(getResult.getString()));

        String getResultWithMultiPaths = Json.get(client, key, new String[] {"$..c", "$.c"}).get();

        assertEquals(
                JsonParser.parseString("{\"$..c\": [True, 1, 2], \"$.c\": [True]}"),
                JsonParser.parseString(getResultWithMultiPaths));

        assertEquals(OK, Json.set(client, key, "$..c", "\"new_value\"").get());
        String getResultAfterSetNewValue = Json.get(client, key, new String[] {"$..c"}).get();
        assertEquals(
                JsonParser.parseString("[\"new_value\", \"new_value\", \"new_value\"]"),
                JsonParser.parseString(getResultAfterSetNewValue));
    }

    @Test
    @SneakyThrows
    public void json_set_get_conditional_set() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": 1.0, \"b\": 2}";

        assertNull(Json.set(client, key, "$", jsonValue, ConditionalChange.ONLY_IF_EXISTS).get());
        assertEquals(
                OK, Json.set(client, key, "$", jsonValue, ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get());
        assertNull(Json.set(client, key, "$.a", "4.5", ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get());
        assertEquals("1.0", Json.get(client, key, new String[] {".a"}).get());
        assertEquals(OK, Json.set(client, key, "$.a", "4.5", ConditionalChange.ONLY_IF_EXISTS).get());
        assertEquals("4.5", Json.get(client, key, new String[] {".a"}).get());
    }

    @Test
    @SneakyThrows
    public void json_set_get_formatting() {
        String key = UUID.randomUUID().toString();

        assertEquals(
                OK,
                Json.set(client, key, "$", "{\"a\": 1.0, \"b\": 2, \"c\": {\"d\": 3, \"e\": 4}}").get());

        String expectedGetResult =
                "[\n"
                        + "  {\n"
                        + "    \"a\": 1.0,\n"
                        + "    \"b\": 2,\n"
                        + "    \"c\": {\n"
                        + "      \"d\": 3,\n"
                        + "      \"e\": 4\n"
                        + "    }\n"
                        + "  }\n"
                        + "]";
        String actualGetResult =
                Json.get(
                                client,
                                key,
                                new String[] {"$"},
                                JsonGetOptions.builder().indent("  ").newline("\n").space(" ").build())
                        .get();
        assertEquals(expectedGetResult, actualGetResult);

        String expectedGetResult2 =
                "[\n~{\n~~\"a\":*1.0,\n~~\"b\":*2,\n~~\"c\":*{\n~~~\"d\":*3,\n~~~\"e\":*4\n~~}\n~}\n]";
        String actualGetResult2 =
                Json.get(
                                client,
                                key,
                                new String[] {"$"},
                                JsonGetOptions.builder().indent("~").newline("\n").space("*").build())
                        .get();
        assertEquals(expectedGetResult2, actualGetResult2);
    }

    @Test
    @SneakyThrows
    public void arrappend() {
        String key = UUID.randomUUID().toString();
        String doc = "{\"a\": 1, \"b\": [\"one\", \"two\"]}";

        assertEquals(OK, Json.set(client, key, "$", doc).get());

        assertArrayEquals(
                new Object[] {3L},
                (Object[]) Json.arrappend(client, key, "$.b", new String[] {"\"three\""}).get());
        assertEquals(
                5L, Json.arrappend(client, key, ".b", new String[] {"\"four\"", "\"five\""}).get());

        String getResult = Json.get(client, key, new String[] {"$"}).get();
        String expectedGetResult =
                "[{\"a\": 1, \"b\": [\"one\", \"two\", \"three\", \"four\", \"five\"]}]";
        assertEquals(JsonParser.parseString(expectedGetResult), JsonParser.parseString(getResult));

        assertArrayEquals(
                new Object[] {null},
                (Object[]) Json.arrappend(client, key, "$.a", new String[] {"\"value\""}).get());

        // JSONPath, path doesn't exist
        assertArrayEquals(
                new Object[] {},
                (Object[])
                        Json.arrappend(client, gs(key), gs("$.c"), new GlideString[] {gs("\"value\"")}).get());

        // Legacy path, path doesn't exist
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () -> Json.arrappend(client, key, ".c", new String[] {"\"value\""}).get());

        // Legacy path, the JSON value at path is not a array
        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> Json.arrappend(client, key, ".a", new String[] {"\"value\""}).get());

        exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                Json.arrappend(client, "non_existing_key", "$.b", new String[] {"\"six\""}).get());

        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> Json.arrappend(client, "non_existing_key", ".b", new String[] {"\"six\""}).get());
    }

    @Test
    @SneakyThrows
    public void arrinsert() {
        String key = UUID.randomUUID().toString();

        String doc =
                "{"
                        + "\"a\": [],"
                        + "\"b\": { \"a\": [1, 2, 3, 4] },"
                        + "\"c\": { \"a\": \"not an array\" },"
                        + "\"d\": [{ \"a\": [\"x\", \"y\"] }, { \"a\": [[\"foo\"]] }],"
                        + "\"e\": [{ \"a\": 42 }, { \"a\": {} }],"
                        + "\"f\": { \"a\": [true, false, null] }"
                        + "}";
        assertEquals("OK", Json.set(client, key, "$", doc).get());

        String[] values =
                new String[] {
                    "\"string_value\"", "123", "{\"key\": \"value\"}", "true", "null", "[\"bar\"]"
                };
        var res = Json.arrinsert(client, key, "$..a", 0, values).get();

        doc = Json.get(client, key).get();
        var expected =
                "{"
                        + "    \"a\": [\"string_value\", 123, {\"key\": \"value\"}, true, null, [\"bar\"]],"
                        + "    \"b\": {"
                        + "        \"a\": ["
                        + "            \"string_value\","
                        + "            123,"
                        + "            {\"key\": \"value\"},"
                        + "            true,"
                        + "            null,"
                        + "            [\"bar\"],"
                        + "            1,"
                        + "            2,"
                        + "            3,"
                        + "            4"
                        + "        ]"
                        + "    },"
                        + "    \"c\": {\"a\": \"not an array\"},"
                        + "    \"d\": ["
                        + "        {"
                        + "            \"a\": ["
                        + "                \"string_value\","
                        + "                123,"
                        + "                {\"key\": \"value\"},"
                        + "                true,"
                        + "                null,"
                        + "                [\"bar\"],"
                        + "                \"x\","
                        + "                \"y\""
                        + "            ]"
                        + "        },"
                        + "        {"
                        + "            \"a\": ["
                        + "                \"string_value\","
                        + "                123,"
                        + "                {\"key\": \"value\"},"
                        + "                true,"
                        + "                null,"
                        + "                [\"bar\"],"
                        + "                [\"foo\"]"
                        + "            ]"
                        + "        }"
                        + "    ],"
                        + "    \"e\": [{\"a\": 42}, {\"a\": {}}],"
                        + "    \"f\": {"
                        + "        \"a\": ["
                        + "            \"string_value\","
                        + "            123,"
                        + "            {\"key\": \"value\"},"
                        + "            true,"
                        + "            null,"
                        + "            [\"bar\"],"
                        + "            true,"
                        + "            false,"
                        + "            null"
                        + "        ]"
                        + "    }"
                        + "}";

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(doc));
    }

    @Test
    @SneakyThrows
    public void arrlen() {
        String key = UUID.randomUUID().toString();

        String doc = "{\"a\": [1, 2, 3], \"b\": {\"a\": [1, 2], \"c\": {\"a\": 42}}}";
        assertEquals("OK", Json.set(client, key, "$", doc).get());

        var res = Json.arrlen(client, key, "$.a").get();
        assertArrayEquals(new Object[] {3L}, (Object[]) res);

        res = Json.arrlen(client, key, "$..a").get();
        assertArrayEquals(new Object[] {3L, 2L, null}, (Object[]) res);

        // Legacy path retrieves the first array match at ..a
        res = Json.arrlen(client, key, "..a").get();
        assertEquals(3L, res);

        doc = "[1, 2, true, null, \"tree\"]";
        assertEquals("OK", Json.set(client, key, "$", doc).get());

        // no path
        res = Json.arrlen(client, key).get();
        assertEquals(5L, res);
    }

    @Test
    @SneakyThrows
    public void clear() {
        String key = UUID.randomUUID().toString();
        String json =
                "{\"obj\": {\"a\":1, \"b\":2}, \"arr\":[1, 2, 3], \"str\": \"foo\", \"bool\": true,"
                        + " \"int\": 42, \"float\": 3.14, \"nullVal\": null}";

        assertEquals("OK", Json.set(client, key, "$", json).get());

        assertEquals(6L, Json.clear(client, key, "$.*").get());
        var doc = Json.get(client, key, new String[] {"$"}).get();
        assertEquals(
                "[{\"obj\":{},\"arr\":[],\"str\":\"\",\"bool\":false,\"int\":0,\"float\":0.0,\"nullVal\":null}]",
                doc);
        assertEquals(0L, Json.clear(client, gs(key), gs(".*")).get());

        assertEquals(1L, Json.clear(client, gs(key)).get());
        doc = Json.get(client, key, new String[] {"$"}).get();
        assertEquals("[{}]", doc);

        assertThrows(
                ExecutionException.class, () -> Json.clear(client, UUID.randomUUID().toString()).get());
    }

    @Test
    @SneakyThrows
    public void arrtrim() {
        String key = UUID.randomUUID().toString();

        String doc =
                "{\"a\": [0, 1, 2, 3, 4, 5, 6, 7, 8], \"b\": {\"a\": [0, 9, 10, 11, 12, 13], \"c\": {\"a\":"
                        + " 42}}}";
        assertEquals("OK", Json.set(client, key, "$", doc).get());

        // Basic trim
        var res = Json.arrtrim(client, key, "$..a", 1, 7).get();
        assertArrayEquals(new Object[] {7L, 5L, null}, (Object[]) res);

        String getResult = Json.get(client, key, new String[] {"$..a"}).get();
        String expectedGetResult = "[[1, 2, 3, 4, 5, 6, 7], [9, 10, 11, 12, 13], 42]";
        assertEquals(JsonParser.parseString(expectedGetResult), JsonParser.parseString(getResult));

        // Test end >= size (should be treated as size-1)
        res = Json.arrtrim(client, key, "$.a", 0, 10).get();
        assertArrayEquals(new Object[] {7L}, (Object[]) res);
        res = Json.arrtrim(client, key, ".a", 0, 10).get();
        assertEquals(7L, res);

        // Test negative start (should be treated as 0)
        res = Json.arrtrim(client, key, "$.a", -1, 5).get();
        assertArrayEquals(new Object[] {6L}, (Object[]) res);
        res = Json.arrtrim(client, key, ".a", -1, 5).get();
        assertEquals(6L, res);

        // Test start >= size (should empty the array)
        res = Json.arrtrim(client, key, "$.a", 7, 10).get();
        assertArrayEquals(new Object[] {0L}, (Object[]) res);

        assertEquals("OK", Json.set(client, key, ".a", "[\"a\", \"b\", \"c\"]").get());
        res = Json.arrtrim(client, key, ".a", 7, 10).get();
        assertEquals(0L, res);

        // Test start > end (should empty the array)
        res = Json.arrtrim(client, key, "$..a", 2, 1).get();
        assertArrayEquals(new Object[] {0L, 0L, null}, (Object[]) res);

        assertEquals("OK", Json.set(client, key, ".a", "[\"a\", \"b\", \"c\", \"d\"]").get());
        res = Json.arrtrim(client, key, "..a", 2, 1).get();
        assertEquals(0L, res);

        // Multiple path match
        assertEquals("OK", Json.set(client, key, "$", doc).get());
        res = Json.arrtrim(client, key, "..a", 1, 10).get();
        assertEquals(8L, res);

        getResult = Json.get(client, key, new String[] {"$..a"}).get();
        expectedGetResult = "[[1,2,3,4,5,6,7,8], [9,10,11,12,13], 42]";
        assertEquals(JsonParser.parseString(expectedGetResult), JsonParser.parseString(getResult));

        // Test with non-existing path
        var exception =
                assertThrows(
                        ExecutionException.class, () -> Json.arrtrim(client, key, ".non_existing", 0, 1).get());

        res = Json.arrtrim(client, key, "$.non_existing", 0, 1).get();
        assertArrayEquals(new Object[] {}, (Object[]) res);

        // Test with non-array path
        res = Json.arrtrim(client, key, "$", 0, 1).get();
        assertArrayEquals(new Object[] {null}, (Object[]) res);

        exception =
                assertThrows(ExecutionException.class, () -> Json.arrtrim(client, key, ".", 0, 1).get());

        // Test with non-existing key
        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> Json.arrtrim(client, "non_existing_key", "$", 0, 1).get());

        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> Json.arrtrim(client, "non_existing_key", ".", 0, 1).get());

        // Test with empty array
        assertEquals("OK", Json.set(client, key, "$.empty", "[]").get());
        res = Json.arrtrim(client, key, "$.empty", 0, 1).get();
        assertArrayEquals(new Object[] {0L}, (Object[]) res);
        res = Json.arrtrim(client, key, ".empty", 0, 1).get();
        assertEquals(0L, res);
    }

    @Test
    @SneakyThrows
    public void objlen() {
        String key = UUID.randomUUID().toString();

        String doc = "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}";
        assertEquals("OK", Json.set(client, key, "$", doc).get());

        var res = Json.objlen(client, key, "$..").get();
        assertArrayEquals(new Object[] {2L, 3L, 2L}, (Object[]) res);

        res = Json.objlen(client, gs(key), gs("..b")).get();
        assertEquals(3L, res);

        // without path
        res = Json.objlen(client, key).get();
        assertEquals(2L, res);
        res = Json.objlen(client, gs(key)).get();
        assertEquals(2L, res);
    }

    @Test
    @SneakyThrows
    public void json_del() {
        String key = UUID.randomUUID().toString();
        assertEquals(
                OK,
                Json.set(client, key, "$", "{\"a\": 1.0, \"b\": {\"a\": 1, \"b\": 2.5, \"c\": true}}")
                        .get());
        assertEquals(2L, Json.del(client, key, "$..a").get());
        assertEquals("[]", Json.get(client, key, new String[] {"$..a"}).get());
        String expectedGetResult = "{\"b\": {\"b\": 2.5, \"c\": true}}";
        String actualGetResult = Json.get(client, key).get();
        assertEquals(
                JsonParser.parseString(expectedGetResult), JsonParser.parseString(actualGetResult));

        assertEquals(1L, Json.del(client, gs(key), gs("$")).get());
        assertEquals(0L, Json.del(client, key).get());
        assertNull(Json.get(client, key, new String[] {"$"}).get());
    }

    @Test
    @SneakyThrows
    public void objkeys() {
        String key = UUID.randomUUID().toString();

        String doc = "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}";
        assertEquals("OK", Json.set(client, key, "$", doc).get());

        var res = Json.objkeys(client, key, "..").get();
        assertArrayEquals(new Object[] {"a", "b"}, res);

        res = Json.objkeys(client, gs(key), gs("$..b")).get();
        assertArrayEquals(new Object[][] {{gs("a"), gs("b"), gs("c")}, {}}, res);

        // without path
        res = Json.objkeys(client, key).get();
        assertArrayEquals(new Object[] {"a", "b"}, res);
        res = Json.objkeys(client, gs(key)).get();
        assertArrayEquals(new Object[] {gs("a"), gs("b")}, res);
    }

    @Test
    @SneakyThrows
    public void json_forget() {
        String key = UUID.randomUUID().toString();
        assertEquals(
                OK,
                Json.set(client, key, "$", "{\"a\": 1.0, \"b\": {\"a\": 1, \"b\": 2.5, \"c\": true}}")
                        .get());
        assertEquals(2L, Json.forget(client, key, "$..a").get());
        assertEquals("[]", Json.get(client, key, new String[] {"$..a"}).get());
        String expectedGetResult = "{\"b\": {\"b\": 2.5, \"c\": true}}";
        String actualGetResult = Json.get(client, key).get();
        assertEquals(
                JsonParser.parseString(expectedGetResult), JsonParser.parseString(actualGetResult));

        assertEquals(1L, Json.forget(client, gs(key), gs("$")).get());
        assertEquals(0L, Json.forget(client, key).get());
        assertNull(Json.get(client, key, new String[] {"$"}).get());
    }

    @Test
    @SneakyThrows
    public void toggle() {
        String key = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String doc = "{\"bool\": true, \"nested\": {\"bool\": false, \"nested\": {\"bool\": 10}}}";

        assertEquals("OK", Json.set(client, key, "$", doc).get());

        assertArrayEquals(
                new Object[] {false, true, null}, (Object[]) Json.toggle(client, key, "$..bool").get());

        assertEquals(true, Json.toggle(client, gs(key), gs("bool")).get());

        assertArrayEquals(new Object[] {}, (Object[]) Json.toggle(client, key, "$.non_existing").get());
        assertArrayEquals(new Object[] {null}, (Object[]) Json.toggle(client, key, "$.nested").get());

        // testing behaviour with default path
        assertEquals("OK", Json.set(client, key2, ".", "true").get());
        assertEquals(false, Json.toggle(client, key2).get());
        assertEquals(true, Json.toggle(client, gs(key2)).get());

        // expect request errors
        var exception =
                assertThrows(ExecutionException.class, () -> Json.toggle(client, key, "nested").get());
        exception =
                assertThrows(
                        ExecutionException.class, () -> Json.toggle(client, key, ".non_existing").get());
        exception =
                assertThrows(
                        ExecutionException.class, () -> Json.toggle(client, "non_existing_key", "$").get());
    }

    @Test
    @SneakyThrows
    public void strappend() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": \"foo\", \"nested\": {\"a\": \"hello\"}, \"nested2\": {\"a\": 31}}";
        assertEquals("OK", Json.set(client, key, "$", jsonValue).get());

        assertArrayEquals(new Object[] {6L, 8L, null}, (Object[]) Json.strappend(client, key, "'\"bar\"'", "$..a").get());
        assertEquals(9L, (Long) Json.strappend(client, key, "foo", "a").get());

        String jsonStr = Json.get(client, key, new String[] {"."}).get();
        assertEquals("{ \"a\": \"foobarfoo\", \"nested\": {\"a\": \"hellobar\"}, \"nested2\": {\"a\": 31}}", jsonStr);

        assertNull(Json.strappend(client, key, "bar", "$.nested").get());

        assertThrows(ExecutionException.class, () -> Json.strappend(client, key, "bar", ".nested").get());

        assertThrows(ExecutionException.class, () -> Json.strappend(client, key, "bar").get());

        assertArrayEquals(new Object[] {}, (Object[]) Json.strappend(client, key, "try", "$.non_existing_path").get());

        assertThrows(ExecutionException.class, () -> Json.strappend(client, key, "try", "non_existing_path").get());

        assertThrows(ExecutionException.class, () -> Json.strappend(client, "non_existing_key", "try").get());

        // Binary test
        // Binary with path
        assertEquals(12L, (Long) Json.strappend(client, gs(key), gs("foo"), gs("a")).get());
        jsonStr = Json.get(client, key, new String[] {"."}).get();
        assertEquals("{ \"a\": \"foobarfoofoo\", \"nested\": {\"a\": \"hellobar\"}, \"nested2\": {\"a\": 31}}", jsonStr);

        // Binary no path
        assertEquals("OK", Json.set(client, key, "$", "'\"hi\"'").get());
        assertEquals(5L, Json.strappend(client, gs(key), gs("foo")).get());
        jsonStr = Json.get(client, key, new String[] {"."}).get();
        assertEquals("\"hifoo\"", jsonStr);
    }

    @Test
    @SneakyThrows
    public void strlen() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": \"foo\", \"nested\": {\"a\": \"hello\"}, \"nested2\": {\"a\": 31}}";
        assertEquals("OK", Json.set(client, key, "$", jsonValue).get());

        assertArrayEquals(new Object[] {3L, 5L, null}, (Object[]) Json.strlen(client, key, "$..a").get());
        assertEquals(3L, (Long) Json.strlen(client, key, "a").get());

        assertNull(Json.strlen(client, key, "$.nested").get());

        assertThrows(ExecutionException.class, () -> Json.strlen(client, key, "nested").get());

        assertThrows(ExecutionException.class, () -> Json.strlen(client, key).get());

        assertArrayEquals(new Object[] {}, (Object[]) Json.strlen(client, key, "$.non_existing_path").get());
        assertThrows(ExecutionException.class, () -> Json.strlen(client, key, ".non_existing_path").get());

        assertNull(Json.strlen(client, "non_exiting_key", ".").get());
        assertNull(Json.strlen(client, "non_exiting_key", "$").get());

        // Binary test
        // Binary with path
        assertEquals(3L, (Long) Json.strlen(client, gs(key), gs("a")).get());

        // Binary no path
        assertEquals("OK", Json.set(client, key, "$", "'\"hi\"'").get());
        assertEquals(2L, Json.strlen(client, gs(key)).get());
    }

    @Test
    @SneakyThrows
    public void json_resp() {
        String key = UUID.randomUUID().toString();
        String jsonValue =
                "{\"obj\":{\"a\":1, \"b\":2}, \"arr\":[1,2,3], \"str\": \"foo\", \"bool\": true, \"int\":"
                        + " 42, \"float\": 3.14, \"nullVal\": null}";
        assertEquals(OK, Json.set(client, key, "$", jsonValue).get());

        Object actualResult1 = Json.resp(client, key, "$.*").get();
        Object[] expectedResult1 =
                new Object[] {
                    new Object[] {
                        "{",
                        new Object[] {"a", 1L},
                        new Object[] {"b", 2L} // leading "{" indicates JSON objects
                    },
                    new Object[] {"[", 1L, 2L, 3L}, // leading "[" indicates JSON arrays
                    "foo",
                    "true",
                    42L,
                    "3.14",
                    null
                };
        assertInstanceOf(Object[].class, actualResult1);
        assertArrayEquals(expectedResult1, (Object[]) actualResult1);

        // multiple path match, the first will be returned
        Object actualResult2 = Json.resp(client, key, "*").get();
        Object[] expectedResult2 = new Object[] {"{", new Object[] {"a", 1L}, new Object[] {"b", 2L}};
        assertInstanceOf(Object[].class, actualResult2);
        assertArrayEquals(expectedResult2, (Object[]) actualResult2);

        Object actualResult3 = Json.resp(client, key, "$").get();
        Object[] expectedResult3 =
                new Object[] {
                    new Object[] {
                        "{",
                        new Object[] {
                            "obj", new Object[] {"{", new Object[] {"a", 1L}, new Object[] {"b", 2L}}
                        },
                        new Object[] {"arr", new Object[] {"[", 1L, 2L, 3L}},
                        new Object[] {"str", "foo"},
                        new Object[] {"bool", "true"},
                        new Object[] {"int", 42L},
                        new Object[] {"float", "3.14"},
                        new Object[] {"nullVal", null}
                    }
                };
        assertInstanceOf(Object[].class, actualResult3);
        assertArrayEquals(expectedResult3, (Object[]) actualResult3);

        Object actualResult4 = Json.resp(client, key, ".").get();
        Object[] expectedResult4 =
                new Object[] {
                    "{",
                    new Object[] {"obj", new Object[] {"{", new Object[] {"a", 1L}, new Object[] {"b", 2L}}},
                    new Object[] {"arr", new Object[] {"[", 1L, 2L, 3L}},
                    new Object[] {"str", "foo"},
                    new Object[] {"bool", "true"},
                    new Object[] {"int", 42L},
                    new Object[] {"float", "3.14"},
                    new Object[] {"nullVal", null}
                };
        assertInstanceOf(Object[].class, actualResult4);
        assertArrayEquals(expectedResult4, (Object[]) actualResult4);
        // resp without path defaults to the same behavior of passing "." as path
        Object actualResult4WithoutPath = Json.resp(client, key).get();
        assertArrayEquals(expectedResult4, (Object[]) actualResult4WithoutPath);
        assertArrayEquals(expectedResult4, (Object[]) actualResult4WithoutPath);

        Object actualResult5 = Json.resp(client, gs(key), gs("$.str")).get();
        Object[] expectedResult5 = new Object[] {gs("foo")};
        assertInstanceOf(Object[].class, actualResult5);
        assertArrayEquals(expectedResult5, (Object[]) actualResult5);

        Object actualResult6 = Json.resp(client, key, ".str").get();
        String expectedResult6 = "foo";
        assertEquals(expectedResult6, actualResult6);

        assertArrayEquals(new Object[] {}, (Object[]) Json.resp(client, key, "$.nonexistent").get());

        assertThrows(ExecutionException.class, () -> Json.resp(client, key, "nonexistent").get());

        assertNull(Json.resp(client, "nonexistent_key", "$").get());
        assertNull(Json.resp(client, "nonexistent_key", ".").get());
        assertNull(Json.resp(client, "nonexistent_key").get());
    }
}
