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
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.vss.FTCreateOptions;
import glide.api.models.commands.vss.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.vss.FTCreateOptions.FieldInfo;
import glide.api.models.commands.vss.FTCreateOptions.IndexType;
import glide.api.models.commands.vss.FTCreateOptions.NumericField;
import glide.api.models.commands.vss.FTCreateOptions.TagField;
import glide.api.models.commands.vss.FTCreateOptions.TextField;
import glide.api.models.commands.vss.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.vss.FTCreateOptions.VectorFieldHnsw;
import glide.api.models.commands.vss.FTSearchOptions;
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
        var client =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(5000).build())
                        .get();
        var info = client.info(new Section[] {Section.MODULES}, RANDOM).get().getSingleValue();
        assertTrue(info.contains("# search_index_stats"));
        client.close();
    }

    @SneakyThrows
    @Test
    public void ft_create() {
        // create few simple indices
        assertEquals(
                OK,
                client
                        .ftcreate(
                                UUID.randomUUID().toString(),
                                FTCreateOptions.empty(),
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());
        assertEquals(
                OK,
                client
                        .ftcreate(
                                UUID.randomUUID().toString(),
                                FTCreateOptions.builder()
                                        .indexType(IndexType.JSON)
                                        .prefixes(new String[] {"json:"})
                                        .build(),
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "$.vec", "VEC", VectorFieldFlat.builder(DistanceMetric.L2, 6).build())
                                })
                        .get());

        // create an index with NSFW vector with additional parameters
        assertEquals(
                OK,
                client
                        .ftcreate(
                                UUID.randomUUID().toString(),
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {"docs:"})
                                        .build(),
                                new FieldInfo[] {
                                    new FieldInfo(
                                            "doc_embedding",
                                            VectorFieldHnsw.builder(DistanceMetric.COSINE, 1536)
                                                    .numberOfEdges(40)
                                                    .vectorsExaminedOnConstruction(250)
                                                    .vectorsExaminedOnRuntime(40)
                                                    .build())
                                })
                        .get());

        // create an index with multiple fields
        assertEquals(
                OK,
                client
                        .ftcreate(
                                UUID.randomUUID().toString(),
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {"blog:post:"})
                                        .build(),
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("published_at", new NumericField()),
                                    new FieldInfo("category", new TagField())
                                })
                        .get());

        // create an index with multiple prefixes
        var name = UUID.randomUUID().toString();
        assertEquals(
                OK,
                client
                        .ftcreate(
                                name,
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {"author:details:", "book:details:"})
                                        .build(),
                                new FieldInfo[] {
                                    new FieldInfo("author_id", new TagField()),
                                    new FieldInfo("author_ids", new TagField()),
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("name", new TextField())
                                })
                        .get());

        // create a duplicating index
        var exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .ftcreate(
                                                name,
                                                FTCreateOptions.empty(),
                                                new FieldInfo[] {new FieldInfo("title", new TextField())})
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("already exists"));

        // create an index without fields
        exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                client
                                        .ftcreate(
                                                UUID.randomUUID().toString(), FTCreateOptions.empty(), new FieldInfo[0])
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("wrong number of arguments"));
    }

    @SneakyThrows
    @Test
    public void ft_search() {
        String index = UUID.randomUUID().toString();
        String prefix = "{" + UUID.randomUUID() + "}:";

        assertEquals(
                OK,
                client
                        .ftcreate(
                                index,
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {prefix})
                                        .build(),
                                new FieldInfo[] {
                                    new FieldInfo("vec", "VEC", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
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
                client
                        .ftsearch(
                                index,
                                "*=>[KNN 2 @VEC $query_vec]",
                                FTSearchOptions.builder()
                                        .params(
                                                Map.of(
                                                        "query_vec",
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
                        () ->
                                client
                                        .ftsearch(UUID.randomUUID().toString(), "*", FTSearchOptions.builder().build())
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index not found"));
    }

    @SneakyThrows
    @Test
    public void ft_drop() {
        var index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                client
                        .ftcreate(
                                index,
                                FTCreateOptions.empty(),
                                new FieldInfo[] {
                                    new FieldInfo("vec", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());

        var before =
                client
                        .customCommand(new String[] {"FT._LIST"}, ALL_PRIMARIES)
                        .get()
                        .getMultiValue()
                        .values()
                        .stream()
                        .flatMap(s -> Arrays.stream((Object[]) s))
                        .collect(Collectors.toSet());

        assertEquals(OK, client.ftdrop(index).get());

        var after =
                client
                        .customCommand(new String[] {"FT._LIST"}, ALL_PRIMARIES)
                        .get()
                        .getMultiValue()
                        .values()
                        .stream()
                        .flatMap(s -> Arrays.stream((Object[]) s))
                        .collect(Collectors.toSet());

        assertFalse(after.contains(index));
        after.add(index);
        assertEquals(after, before);

        var exception = assertThrows(ExecutionException.class, () -> client.ftdrop(index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Index does not exist"));
    }
}
