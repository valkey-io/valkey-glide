/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class VectorSearchTests {

    @Getter private static List<Arguments> clients;

    @BeforeAll
    @SneakyThrows
    public static void init() {
        var standaloneClient =
                GlideClient.createClient(commonClientConfig().requestTimeout(5000).build()).get();

        var clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(5000).build())
                        .get();

        clients = List.of(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        for (var client : clients) {
            ((BaseClient) client.get()[0]).close();
        }
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_create(BaseClient client) {
        // create few simple indices
        assertEquals(
                OK,
                client
                        .ftcreate(
                                UUID.randomUUID().toString(),
                                IndexType.HASH,
                                new String[0],
                                new FieldInfo[] {
                                    new FieldInfo("vec", "vec", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());
        assertEquals(
                OK,
                client
                        .ftcreate(
                                UUID.randomUUID().toString(),
                                IndexType.JSON,
                                new String[] {"json:"},
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
                                IndexType.HASH,
                                new String[] {"docs:"},
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
                                IndexType.HASH,
                                new String[] {"blog:post:"},
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
                                IndexType.HASH,
                                new String[] {"author:details:", "book:details:"},
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
                                                IndexType.HASH,
                                                new String[0],
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
                                                UUID.randomUUID().toString(),
                                                IndexType.HASH,
                                                new String[0],
                                                new FieldInfo[0])
                                        .get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("arguments are missing"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_search(BaseClient client) {
        String index = UUID.randomUUID().toString();
        String prefix = "{" + UUID.randomUUID() + "}:";

        assertEquals(
                OK,
                client
                        .ftcreate(
                                index,
                                IndexType.HASH,
                                new String[] {prefix},
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
        assertTrue(exception.getMessage().contains("no such index"));
    }

    @SneakyThrows
    @ParameterizedTest(autoCloseArguments = false)
    @MethodSource("getClients")
    public void ft_drop(BaseClient client) {
        var index = UUID.randomUUID().toString();
        assertEquals(
                OK,
                client
                        .ftcreate(
                                index,
                                IndexType.HASH,
                                new String[0],
                                new FieldInfo[] {
                                    new FieldInfo("vec", VectorFieldHnsw.builder(DistanceMetric.L2, 2).build())
                                })
                        .get());

        var before =
                client instanceof GlideClient
                        ? Set.of(
                                (String[]) ((GlideClient) client).customCommand(new String[] {"FT._LIST"}).get())
                        : ((GlideClusterClient) client)
                                        .customCommand(new String[] {"FT._LIST"}, ALL_PRIMARIES)
                                        .get()
                                        .getMultiValue()
                                        .values()
                                        .stream()
                                        .flatMap(s -> Arrays.stream((String[]) s))
                                        .collect(Collectors.toSet());

        assertEquals(OK, client.ftdrop(index).get());

        var after =
                client instanceof GlideClient
                        ? new HashSet<>(
                                Set.of(
                                        (String[])
                                                ((GlideClient) client).customCommand(new String[] {"FT._LIST"}).get()))
                        : ((GlideClusterClient) client)
                                        .customCommand(new String[] {"FT._LIST"}, ALL_PRIMARIES)
                                        .get()
                                        .getMultiValue()
                                        .values()
                                        .stream()
                                        .flatMap(s -> Arrays.stream((String[]) s))
                                        .collect(Collectors.toSet());

        assertFalse(after.contains(index));
        after.add(index);
        assertEquals(after, before);

        var exception = assertThrows(ExecutionException.class, () -> client.ftdrop(index).get());
        assertInstanceOf(RequestException.class, exception.getCause());
        assertTrue(exception.getMessage().contains("Unknown: Index name"));
    }
}
