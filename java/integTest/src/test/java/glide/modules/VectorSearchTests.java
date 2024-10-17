/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.exceptions.RequestException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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
        var name = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                name,
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
                                                name,
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
    public void ft_search() {
        String prefix = "{" + UUID.randomUUID() + "}:";
        String index = prefix + "index";

        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build())
                        .get());

        assertEquals(
                1L,
                client
                        .hset(
                                gs(prefix + 0),
                                Map.of(
                                        gs("vec"),
                                        gs(
                                                new byte[] {
                                                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                                                    (byte) 0
                                                })))
                        .get());
        assertEquals(
                1L,
                client
                        .hset(
                                gs(prefix + 1),
                                Map.of(
                                        gs("vec"),
                                        gs(
                                                new byte[] {
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0,
                                                    (byte) 0x80,
                                                    (byte) 0xBF
                                                })))
                        .get());

        var ftsearch =
                FT.search(
                                client,
                                index,
                                "*=>[KNN 2 @VEC $query_vec]",
                                FTSearchOptions.builder()
                                        .params(
                                                Map.of(
                                                        gs("query_vec"),
                                                        gs(
                                                                new byte[] {
                                                                    (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
                                                                    (byte) 0, (byte) 0
                                                                })))
                                        .build())
                        .get();

        assertArrayEquals(
                new Object[] {
                    2L,
                    Map.of(
                            gs(prefix + 0),
                            Map.of(gs("__VEC_score"), gs("0"), gs("vec"), gs("\0\0\0\0\0\0\0\0")),
                            gs(prefix + 1),
                            Map.of(
                                    gs("__VEC_score"),
                                    gs("1"),
                                    gs("vec"),
                                    gs(
                                            new byte[] {
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0,
                                                (byte) 0x80,
                                                (byte) 0xBF
                                            })))
                },
                ftsearch);

        // TODO more tests with json index

        // querying non-existing index
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () -> FT.search(client, UUID.randomUUID().toString(), "*").get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @Test
    public void ft_drop() {
        var index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                FT.create(
                                client,
                                index,
                                new FieldInfo[] {
                                    new FieldInfo("vec", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());

        // TODO use FT.LIST with it is done
        var before =
                Set.of((Object[]) client.customCommand(new String[] {"FT._LIST"}).get().getSingleValue());

        assertEquals(OK, FT.dropindex(client, index).get());

        // TODO use FT.LIST with it is done
        var after =
                new HashSet<>(
                        Set.of(
                                (Object[]) client.customCommand(new String[] {"FT._LIST"}).get().getSingleValue()));

        assertFalse(after.contains(index));
        after.add(index);
        assertEquals(after, before);

        var exception = assertThrows(ExecutionException.class, () -> FT.dropindex(client, index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index does not exist"));
    }
}
