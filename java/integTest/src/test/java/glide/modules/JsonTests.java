/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.json.JsonGetOptions;
import java.util.UUID;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

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

        JSONAssert.assertEquals(jsonValue, getResult, JSONCompareMode.LENIENT);

        String getResultWithMultiPaths = Json.get(client, key, new String[] {"$.a", "$.b"}).get();

        JSONAssert.assertEquals(
                "{\"$.a\":[1.0],\"$.b\":[2]}", getResultWithMultiPaths, JSONCompareMode.LENIENT);

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

        JSONAssert.assertEquals("[true, 1, 2]", getResult.getString(), JSONCompareMode.LENIENT);

        String getResultWithMultiPaths = Json.get(client, key, new String[] {"$..c", "$.c"}).get();

        JSONAssert.assertEquals(
                "{\"$..c\": [True, 1, 2], \"$.c\": [True]}",
                getResultWithMultiPaths,
                JSONCompareMode.LENIENT);

        assertEquals(OK, Json.set(client, key, "$..c", "\"new_value\"").get());
        String getResultAfterSetNewValue = Json.get(client, key, new String[] {"$..c"}).get();
        JSONAssert.assertEquals(
                "[\"new_value\", \"new_value\", \"new_value\"]",
                getResultAfterSetNewValue,
                JSONCompareMode.LENIENT);
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
}
