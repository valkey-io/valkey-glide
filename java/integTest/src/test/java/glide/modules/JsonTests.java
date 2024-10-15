/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.GlideJson;
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

        assertEquals(OK, GlideJson.set(client, key, "$", jsonValue).get());

        Object getResult = GlideJson.get(client, key, ".").get();

        assertInstanceOf(String.class, getResult);
        JSONAssert.assertEquals(jsonValue, (String) getResult, JSONCompareMode.LENIENT);

        Object getResultWithMultiPaths = GlideJson.get(client, key, new String[] {"$.a", "$.b"}).get();

        assertInstanceOf(String.class, getResultWithMultiPaths);
        JSONAssert.assertEquals(
                "{\"$.a\":[1.0],\"$.b\":[2]}", (String) getResultWithMultiPaths, JSONCompareMode.LENIENT);

        assertNull(GlideJson.get(client, "non_existing_key", "$").get());
        assertEquals("[]", GlideJson.get(client, key, "$.d").get());
    }

    @Test
    @SneakyThrows
    public void json_set_get_multiple_values() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": {\"c\": 1, \"d\": 4}, \"b\": {\"c\": 2}, \"c\": true}";

        assertEquals(OK, GlideJson.set(client, gs(key), gs("$"), gs(jsonValue)).get());

        Object getResult = GlideJson.get(client, gs(key), gs("$..c")).get();

        assertInstanceOf(GlideString.class, getResult);
        JSONAssert.assertEquals("[true, 1, 2]", ((GlideString)getResult).getString(), JSONCompareMode.LENIENT);

        Object getResultWithMultiPaths = GlideJson.get(client, key, new String[] {"$..c", "$.c"}).get();

        assertInstanceOf(String.class, getResultWithMultiPaths);
        JSONAssert.assertEquals(
                "{\"$..c\": [True, 1, 2], \"$.c\": [True]}",
                (String) getResultWithMultiPaths,
                JSONCompareMode.LENIENT);

        assertEquals(OK, GlideJson.set(client, key, "$..c", "\"new_value\"").get());
        Object getResultAfterSetNewValue = GlideJson.get(client, key, "$..c").get();
        assertInstanceOf(String.class, getResultAfterSetNewValue);
        JSONAssert.assertEquals(
                "[\"new_value\", \"new_value\", \"new_value\"]",
                (String) getResultAfterSetNewValue,
                JSONCompareMode.LENIENT);
    }

    @Test
    @SneakyThrows
    public void json_set_get_conditional_set() {
        String key = UUID.randomUUID().toString();
        String jsonValue = "{\"a\": 1.0, \"b\": 2}";

        assertNull(GlideJson.set(client, key, "$", jsonValue, ConditionalChange.ONLY_IF_EXISTS).get());
        assertEquals(
                OK,
                GlideJson.set(client, key, "$", jsonValue, ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get());
        assertNull(
                GlideJson.set(client, key, "$.a", "4.5", ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get());
        assertEquals("1.0", (String) GlideJson.get(client, key, ".a").get());
        assertEquals(
                OK, GlideJson.set(client, key, "$.a", "4.5", ConditionalChange.ONLY_IF_EXISTS).get());
        assertEquals("4.5", GlideJson.get(client, key, ".a").get());
    }

    @Test
    @SneakyThrows
    public void json_set_get_formatting() {
        String key = UUID.randomUUID().toString();

        assertEquals(
                OK,
                GlideJson.set(client, key, "$", "{\"a\": 1.0, \"b\": 2, \"c\": {\"d\": 3, \"e\": 4}}")
                        .get());

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
        Object actualGetResult =
                GlideJson.get(
                                client,
                                key,
                                "$",
                                JsonGetOptions.builder().indent("  ").newline("\n").space(" ").build())
                        .get();
        assertEquals(expectedGetResult, actualGetResult);

        String expectedGetResult2 =
                "[\n~{\n~~\"a\":*1.0,\n~~\"b\":*2,\n~~\"c\":*{\n~~~\"d\":*3,\n~~~\"e\":*4\n~~}\n~}\n]";
        Object actualGetResult2 =
                GlideJson.get(
                                client,
                                key,
                                "$",
                                JsonGetOptions.builder().indent("~").newline("\n").space("*").build())
                        .get();
        assertEquals(expectedGetResult2, actualGetResult2);
    }
}
