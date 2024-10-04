/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.IndexType;
import glide.api.models.commands.FT.FTCreateOptions.NumericField;
import glide.api.models.commands.FT.FTCreateOptions.TagField;
import glide.api.models.commands.FT.FTCreateOptions.TextField;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldHnsw;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.exceptions.RequestException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class VectorSearchTests {

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
        assertTrue(info.contains("# search_index_stats"));
    }

    @SneakyThrows
    @Test
    public void ft_create() {
        // create few simple indices
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "$.vec", "VEC", VectorFieldFlat.builder(DistanceMetric.L2, 6).build())
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.JSON)
                                        .prefixes(new String[] {"json:"})
                                        .build())
                        .get());

        // create an index with HNSW vector with additional parameters
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "doc_embedding",
                                            VectorFieldHnsw.builder(DistanceMetric.COSINE, 1536)
                                                    .numberOfEdges(40)
                                                    .vectorsExaminedOnConstruction(250)
                                                    .vectorsExaminedOnRuntime(40)
                                                    .build())
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {"docs:"})
                                        .build())
                        .get());

        // create an index with multiple fields
        assertEquals(
                OK,
                FT.create(
                                client,
                                UUID.randomUUID().toString(),
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("published_at", new NumericField()),
                                    new FieldInfo("category", new TagField())
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {"blog:post:"})
                                        .build())
                        .get());

        // create an index with multiple prefixes
        var index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("author_id", new TagField()),
                                    new FieldInfo("author_ids", new TagField()),
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("name", new TextField())
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {"author:details:", "book:details:"})
                                        .build())
                        .get());

        // create a duplicating index
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.create(
                                                client,
                                                index,
                                                new FieldInfo[] {
                                                    new FieldInfo("title", new TextField()),
                                                    new FieldInfo("name", new TextField())
                                                })
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("already exists"));

        // create an index without fields
        exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.create(client, UUID.randomUUID().toString(), new FieldInfo[0]).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("wrong number of arguments"));

        // duplicated field name
        exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                FT.create(
                                                client,
                                                UUID.randomUUID().toString(),
                                                new FieldInfo[] {
                                                    new FieldInfo("name", new TextField()),
                                                    new FieldInfo("name", new TextField())
                                                })
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @SneakyThrows
    @Test
    @SuppressWarnings("unchecked")
    public void ft_info() {
        var indices =
                client
                        .customCommand(new String[] {"FT._LIST"}, ALL_PRIMARIES)
                        .get()
                        .getMultiValue()
                        .values()
                        .stream()
                        .flatMap(s -> Arrays.stream((Object[]) s))
                        .collect(Collectors.toSet());

        // check that we can get a response for all existing indices (no crashes on value conversion or
        // so)
        for (var idx : indices) {
            FT.info(client, (String) idx).get();
        }

        var index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "$.vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.COSINE, 42).build()),
                                    new FieldInfo("name", new TextField()),
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.JSON)
                                        .prefixes(new String[] {"123"})
                                        .build())
                        .get());

        var response = FT.info(client, index).get();
        assertEquals(index, response.get("index_name"));
        assertEquals("JSON", response.get("key_type"));
        assertArrayEquals(new String[] {"123"}, (Object[]) response.get("key_prefixes"));
        var fields = (Object[]) response.get("fields");
        assertEquals(2, fields.length);
        var f1 = (Map<String, Object>) fields[1];
        assertEquals("$.vec", f1.get("identifier"));
        assertEquals("VECTOR", f1.get("type"));
        assertEquals("VEC", f1.get("field_name"));
        var f1params = (Map<String, Object>) f1.get("vector_params");
        assertEquals("COSINE", f1params.get("distance_metric"));
        assertEquals(42L, f1params.get("dimension"));

        assertEquals(
                Map.of("identifier", "$.name", "type", "TEXT", "field_name", "$.name", "option", ""),
                fields[0]);

        var exception = assertThrows(ExecutionException.class, () -> FT.info(client, index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }
}
