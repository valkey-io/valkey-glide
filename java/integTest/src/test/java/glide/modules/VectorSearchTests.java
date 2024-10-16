/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static glide.TestUtilities.assertDeepEquals;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.commands.FT.FTAggregateOptions;
import glide.api.models.commands.FT.FTAggregateOptions.Apply;
import glide.api.models.commands.FT.FTAggregateOptions.GroupBy;
import glide.api.models.commands.FT.FTAggregateOptions.GroupBy.Reducer;
import glide.api.models.commands.FT.FTAggregateOptions.SortBy;
import glide.api.models.commands.FT.FTAggregateOptions.SortBy.SortOrder;
import glide.api.models.commands.FT.FTAggregateOptions.SortBy.SortProperty;
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

    /** Waiting interval to let server process the data before querying */
    private static final int DATA_PROCESSING_TIMEOUT = 1000; // ms

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

    @SneakyThrows
    @Test
    public void ft_aggregate() {
        var prefixBicycles = "{bicycles}:";
        var indexBicycles = prefixBicycles + UUID.randomUUID();
        var prefixMovies = "{movies}:";
        var indexMovies = prefixMovies + UUID.randomUUID();

        // FT.CREATE idx:bicycle ON JSON PREFIX 1 bicycle: SCHEMA $.model AS model TEXT $.description AS
        // description TEXT $.price AS price NUMERIC $.condition AS condition TAG SEPARATOR ,
        assertEquals(
                OK,
                FT.create(
                                client,
                                indexBicycles,
                                new FieldInfo[] {
                                    new FieldInfo("$.model", "model", new TextField()),
                                    new FieldInfo("$.description", "description", new TextField()),
                                    new FieldInfo("$.price", "price", new NumericField()),
                                    new FieldInfo("$.condition", "condition", new TagField(',')),
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.JSON)
                                        .prefixes(new String[] {prefixBicycles})
                                        .build())
                        .get());

        // TODO use JSON module API
        // TODO check JSON module loaded

        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 0,
                            ".",
                            "{\"brand\": \"Velorim\", \"model\": \"Jigger\", \"price\": 270, \"description\":"
                                    + " \"Small and powerful, the Jigger is the best ride for the smallest of tikes!"
                                    + " This is the tiniest kids\\u2019 pedal bike on the market available without a"
                                    + " coaster brake, the Jigger is the vehicle of choice for the rare tenacious"
                                    + " little rider raring to go.\", \"condition\": \"new\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 1,
                            ".",
                            "{\"brand\": \"Bicyk\", \"model\": \"Hillcraft\", \"price\": 1200, \"description\":"
                                    + " \"Kids want to ride with as little weight as possible. Especially on an"
                                    + " incline! They may be at the age when a 27.5\\\" wheel bike is just too clumsy"
                                    + " coming off a 24\\\" bike. The Hillcraft 26 is just the solution they need!\","
                                    + " \"condition\": \"used\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 2,
                            ".",
                            "{\"brand\": \"Nord\", \"model\": \"Chook air 5\", \"price\": 815, \"description\":"
                                    + " \"The Chook Air 5  gives kids aged six years and older a durable and"
                                    + " uberlight mountain bike for their first experience on tracks and easy"
                                    + " cruising through forests and fields. The lower  top tube makes it easy to"
                                    + " mount and dismount in any situation, giving your kids greater safety on the"
                                    + " trails.\", \"condition\": \"used\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 3,
                            ".",
                            "{\"brand\": \"Eva\", \"model\": \"Eva 291\", \"price\": 3400, \"description\": \"The"
                                    + " sister company to Nord, Eva launched in 2005 as the first and only"
                                    + " women-dedicated bicycle brand. Designed by women for women, allEva bikes are"
                                    + " optimized for the feminine physique using analytics from a body metrics"
                                    + " database. If you like 29ers, try the Eva 291. It\\u2019s a brand new bike for"
                                    + " 2022.. This full-suspension, cross-country ride has been designed for"
                                    + " velocity. The 291 has 100mm of front and rear travel, a superlight aluminum"
                                    + " frame and fast-rolling 29-inch wheels. Yippee!\", \"condition\": \"used\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 4,
                            ".",
                            "{\"brand\": \"Noka Bikes\", \"model\": \"Kahuna\", \"price\": 3200, \"description\":"
                                    + " \"Whether you want to try your hand at XC racing or are looking for a lively"
                                    + " trail bike that's just as inspiring on the climbs as it is over rougher"
                                    + " ground, the Wilder is one heck of a bike built specifically for short women."
                                    + " Both the frames and components have been tweaked to include a women\\u2019s"
                                    + " saddle, different bars and unique colourway.\", \"condition\": \"used\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 5,
                            ".",
                            "{\"brand\": \"Breakout\", \"model\": \"XBN 2.1 Alloy\", \"price\": 810,"
                                    + " \"description\": \"The XBN 2.1 Alloy is our entry-level road bike \\u2013 but"
                                    + " that\\u2019s not to say that it\\u2019s a basic machine. With an internal"
                                    + " weld aluminium frame, a full carbon fork, and the slick-shifting Claris gears"
                                    + " from Shimano\\u2019s, this is a bike which doesn\\u2019t break the bank and"
                                    + " delivers craved performance.\", \"condition\": \"new\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 6,
                            ".",
                            "{\"brand\": \"ScramBikes\", \"model\": \"WattBike\", \"price\": 2300,"
                                    + " \"description\": \"The WattBike is the best e-bike for people who still feel"
                                    + " young at heart. It has a Bafang 1000W mid-drive system and a 48V 17.5AH"
                                    + " Samsung Lithium-Ion battery, allowing you to ride for more than 60 miles on"
                                    + " one charge. It\\u2019s great for tackling hilly terrain or if you just fancy"
                                    + " a more leisurely ride. With three working modes, you can choose between"
                                    + " E-bike, assisted bicycle, and normal bike modes.\", \"condition\": \"new\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 7,
                            ".",
                            "{\"brand\": \"Peaknetic\", \"model\": \"Secto\", \"price\": 430, \"description\":"
                                    + " \"If you struggle with stiff fingers or a kinked neck or back after a few"
                                    + " minutes on the road, this lightweight, aluminum bike alleviates those issues"
                                    + " and allows you to enjoy the ride. From the ergonomic grips to the"
                                    + " lumbar-supporting seat position, the Roll Low-Entry offers incredible"
                                    + " comfort. The rear-inclined seat tube facilitates stability by allowing you to"
                                    + " put a foot on the ground to balance at a stop, and the low step-over frame"
                                    + " makes it accessible for all ability and mobility levels. The saddle is very"
                                    + " soft, with a wide back to support your hip joints and a cutout in the center"
                                    + " to redistribute that pressure. Rim brakes deliver satisfactory braking"
                                    + " control, and the wide tires provide a smooth, stable ride on paved roads and"
                                    + " gravel. Rack and fender mounts facilitate setting up the Roll Low-Entry as"
                                    + " your preferred commuter, and the BMX-like handlebar offers space for mounting"
                                    + " a flashlight, bell, or phone holder.\", \"condition\": \"new\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 8,
                            ".",
                            "{\"brand\": \"nHill\", \"model\": \"Summit\", \"price\": 1200, \"description\":"
                                    + " \"This budget mountain bike from nHill performs well both on bike paths and"
                                    + " on the trail. The fork with 100mm of travel absorbs rough terrain. Fat Kenda"
                                    + " Booster tires give you grip in corners and on wet trails. The Shimano Tourney"
                                    + " drivetrain offered enough gears for finding a comfortable pace to ride"
                                    + " uphill, and the Tektro hydraulic disc brakes break smoothly. Whether you want"
                                    + " an affordable bike that you can take to work, but also take trail in"
                                    + " mountains on the weekends or you\\u2019re just after a stable, comfortable"
                                    + " ride for the bike path, the Summit gives a good value for money.\","
                                    + " \"condition\": \"new\"}"
                        })
                .get();
        client
                .customCommand(
                        new String[] {
                            "JSON.SET",
                            prefixBicycles + 9,
                            ".",
                            "{\"model\": \"ThrillCycle\", \"brand\": \"BikeShind\", \"price\": 815,"
                                    + " \"description\": \"An artsy,  retro-inspired bicycle that\\u2019s as"
                                    + " functional as it is pretty: The ThrillCycle steel frame offers a smooth ride."
                                    + " A 9-speed drivetrain has enough gears for coasting in the city, but we"
                                    + " wouldn\\u2019t suggest taking it to the mountains. Fenders protect you from"
                                    + " mud, and a rear basket lets you transport groceries, flowers and books. The"
                                    + " ThrillCycle comes with a limited lifetime warranty, so this little guy will"
                                    + " last you long past graduation.\", \"condition\": \"refurbished\"}"
                        })
                .get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT); // let server digest the data and update index

        // FT.AGGREGATE idx:bicycle "*" LOAD 1 "__key" GROUPBY 1 "@condition" REDUCE COUNT 0 AS bicylces
        var aggreg =
                FT.aggregate(
                                client,
                                indexBicycles,
                                "*",
                                FTAggregateOptions.builder()
                                        .loadFields(new String[] {"__key"})
                                        .addExpression(
                                                new GroupBy(
                                                        new String[] {"@condition"},
                                                        new Reducer[] {new Reducer("COUNT", new String[0], "bicycles")}))
                                        .build())
                        .get();
        // elements (maps in array) could be reordered, comparing as sets
        assertDeepEquals(
                Set.of(
                        Map.of(gs("condition"), gs("new"), gs("bicycles"), 5.),
                        Map.of(gs("condition"), gs("used"), gs("bicycles"), 4.),
                        Map.of(gs("condition"), gs("refurbished"), gs("bicycles"), 1.)),
                Set.of(aggreg));

        // FT.CREATE idx:movie ON hash PREFIX 1 "movie:" SCHEMA title TEXT release_year NUMERIC rating
        // NUMERIC genre TAG votes NUMERIC
        assertEquals(
                OK,
                FT.create(
                                client,
                                indexMovies,
                                new FieldInfo[] {
                                    new FieldInfo("title", new TextField()),
                                    new FieldInfo("release_year", new NumericField()),
                                    new FieldInfo("rating", new NumericField()),
                                    new FieldInfo("genre", new TagField()),
                                    new FieldInfo("votes", new NumericField()),
                                },
                                FTCreateOptions.builder()
                                        .indexType(IndexType.HASH)
                                        .prefixes(new String[] {prefixMovies})
                                        .build())
                        .get());

        client
                .hset(
                        prefixMovies + 11002,
                        Map.of(
                                "title",
                                "Star Wars: Episode V - The Empire Strikes Back",
                                "plot",
                                "After the Rebels are brutally overpowered by the Empire on the ice planet Hoth,"
                                        + " Luke Skywalker begins Jedi training with Yoda, while his friends are"
                                        + " pursued by Darth Vader and a bounty hunter named Boba Fett all over the"
                                        + " galaxy.",
                                "release_year",
                                "1980",
                                "genre",
                                "Action",
                                "rating",
                                "8.7",
                                "votes",
                                "1127635",
                                "imdb_id",
                                "tt0080684"))
                .get();
        client
                .hset(
                        prefixMovies + 11003,
                        Map.of(
                                "title",
                                "The Godfather",
                                "plot",
                                "The aging patriarch of an organized crime dynasty transfers control of his"
                                        + " clandestine empire to his reluctant son.",
                                "release_year",
                                "1972",
                                "genre",
                                "Drama",
                                "rating",
                                "9.2",
                                "votes",
                                "1563839",
                                "imdb_id",
                                "tt0068646"))
                .get();
        client
                .hset(
                        prefixMovies + 11004,
                        Map.of(
                                "title",
                                "Heat",
                                "plot",
                                "A group of professional bank robbers start to feel the heat from police when they"
                                        + " unknowingly leave a clue at their latest heist.",
                                "release_year",
                                "1995",
                                "genre",
                                "Thriller",
                                "rating",
                                "8.2",
                                "votes",
                                "559490",
                                "imdb_id",
                                "tt0113277"))
                .get();
        client
                .hset(
                        prefixMovies + 11005,
                        Map.of(
                                "title",
                                "Star Wars: Episode VI - Return of the Jedi",
                                "genre",
                                "Action",
                                "votes",
                                "906260",
                                "rating",
                                "8.3",
                                "release_year",
                                "1983",
                                "plot",
                                "The Rebels dispatch to Endor to destroy the second Empire's Death Star.",
                                "ibmdb_id",
                                "tt0086190"))
                .get();
        Thread.sleep(DATA_PROCESSING_TIMEOUT); // let server digest the data and update index

        // FT.AGGREGATE idx:movie * LOAD * APPLY ceil(@rating) as r_rating GROUPBY 1 @genre REDUCE
        // COUNT 0 AS nb_of_movies REDUCE SUM 1 votes AS nb_of_votes REDUCE AVG 1 r_rating AS avg_rating
        // SORTBY 4 @avg_rating DESC @nb_of_votes DESC
        aggreg =
                FT.aggregate(
                                client,
                                indexMovies,
                                "*",
                                FTAggregateOptions.builder()
                                        .loadAll()
                                        .addExpression(new Apply("ceil(@rating)", "r_rating"))
                                        .addExpression(
                                                new GroupBy(
                                                        new String[] {"@genre"},
                                                        new Reducer[] {
                                                            new Reducer("COUNT", new String[0], "nb_of_movies"),
                                                            new Reducer("SUM", new String[] {"votes"}, "nb_of_votes"),
                                                            new Reducer("AVG", new String[] {"r_rating"}, "avg_rating")
                                                        }))
                                        .addExpression(
                                                new SortBy(
                                                        new SortProperty[] {
                                                            new SortProperty("@avg_rating", SortOrder.DESC),
                                                            new SortProperty("@nb_of_votes", SortOrder.DESC)
                                                        }))
                                        .build())
                        .get();
        // elements (maps in array) could be reordered, comparing as sets
        assertDeepEquals(
                Set.of(
                        Map.of(
                                gs("genre"),
                                gs("Drama"),
                                gs("nb_of_movies"),
                                1.,
                                gs("nb_of_votes"),
                                1563839.,
                                gs("avg_rating"),
                                10.),
                        Map.of(
                                gs("genre"),
                                gs("Action"),
                                gs("nb_of_movies"),
                                2.,
                                gs("nb_of_votes"),
                                2033895.,
                                gs("avg_rating"),
                                9.),
                        Map.of(
                                gs("genre"),
                                gs("Thriller"),
                                gs("nb_of_movies"),
                                1.,
                                gs("nb_of_votes"),
                                559490.,
                                gs("avg_rating"),
                                9.)),
                Set.of(aggreg));
    }
}
