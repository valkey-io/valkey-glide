/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.models.BaseTransaction;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamTrimOptions.MinId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class TransactionTestUtilities {

    private static final String value1 = "value1-" + UUID.randomUUID();
    private static final String value2 = "value2-" + UUID.randomUUID();
    private static final String value3 = "value3-" + UUID.randomUUID();
    private static final String field1 = "field1-" + UUID.randomUUID();
    private static final String field2 = "field2-" + UUID.randomUUID();
    private static final String field3 = "field3-" + UUID.randomUUID();

    @FunctionalInterface
    public interface TransactionBuilder extends Function<BaseTransaction<?>, Object[]> {}

    /** Generate test samples for parametrized tests. Could be routed to random node. */
    public static Stream<Arguments> getCommonTransactionBuilders() {
        return Stream.of(
                Arguments.of(
                        "Generic Commands", (TransactionBuilder) TransactionTestUtilities::genericCommands),
                Arguments.of(
                        "String Commands", (TransactionBuilder) TransactionTestUtilities::stringCommands),
                Arguments.of("Hash Commands", (TransactionBuilder) TransactionTestUtilities::hashCommands),
                Arguments.of("List Commands", (TransactionBuilder) TransactionTestUtilities::listCommands),
                Arguments.of("Set Commands", (TransactionBuilder) TransactionTestUtilities::setCommands),
                Arguments.of(
                        "Sorted Set Commands",
                        (TransactionBuilder) TransactionTestUtilities::sortedSetCommands),
                Arguments.of(
                        "HyperLogLog Commands",
                        (TransactionBuilder) TransactionTestUtilities::hyperLogLogCommands),
                Arguments.of(
                        "Stream Commands", (TransactionBuilder) TransactionTestUtilities::streamCommands),
                Arguments.of(
                        "Connection Management Commands",
                        (TransactionBuilder) TransactionTestUtilities::connectionManagementCommands),
                Arguments.of(
                        "Geospatial Commands",
                        (TransactionBuilder) TransactionTestUtilities::geospatialCommands));
    }

    /** Generate test samples for parametrized tests. Could be routed to primary nodes only. */
    public static Stream<Arguments> getPrimaryNodeTransactionBuilders() {
        return Stream.of(
                Arguments.of(
                        "Server Management Commands",
                        (TransactionBuilder) TransactionTestUtilities::serverManagementCommands));
    }

    private static Object[] genericCommands(BaseTransaction<?> transaction) {
        String genericKey1 = "{GenericKey}-1-" + UUID.randomUUID();
        String genericKey2 = "{GenericKey}-2-" + UUID.randomUUID();

        transaction
                .set(genericKey1, value1)
                .customCommand(new String[] {"MGET", genericKey1, genericKey2})
                .exists(new String[] {genericKey1})
                .persist(genericKey1)
                .type(genericKey1)
                .objectEncoding(genericKey1)
                .touch(new String[] {genericKey1})
                .set(genericKey2, value2)
                .renamenx(genericKey1, genericKey2)
                .unlink(new String[] {genericKey2})
                .get(genericKey2)
                .del(new String[] {genericKey1})
                .get(genericKey1)
                .set(genericKey1, value1)
                .expire(genericKey1, 100500)
                .expireAt(genericKey1, 42) // expire (delete) key immediately
                .pexpire(genericKey1, 42)
                .pexpireAt(genericKey1, 42)
                .ttl(genericKey2);

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            transaction
                    .set(genericKey1, value1)
                    .expire(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                    .expireAt(genericKey1, 500, ExpireOptions.HAS_EXISTING_EXPIRY)
                    .pexpire(genericKey1, 42, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
                    .pexpireAt(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY);
        }

        var expectedResults =
                new Object[] {
                    OK, // set(genericKey1, value1)
                    new String[] {value1, null}, // customCommand("MGET", genericKey1, genericKey2)
                    1L, // exists(new String[] {genericKey1})
                    false, // persist(key1)
                    "string", // type(genericKey1)
                    "embstr", // objectEncoding(genericKey1)
                    1L, // touch(new String[] {genericKey1})
                    OK, // set(genericKey2, value2)
                    false, // renamenx(genericKey1, genericKey2)
                    1L, // unlink(new String[] {genericKey2})
                    null, // get(genericKey2)
                    1L, // del(new String[] {genericKey1})
                    null, // get(genericKey1)
                    OK, // set(genericKey1, value1)
                    true, // expire(genericKey1, 100500)
                    true, // expireAt(genericKey1, 42)
                    false, // pexpire(genericKey1, 42)
                    false, // pexpireAt(genericKey1, 42)
                    -2L, // ttl(genericKey2)
                };

        if (REDIS_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            return concatenateArrays(
                    expectedResults,
                    new Object[] {
                        OK, // set(genericKey1, value1)
                        true, // expire(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                        true, // expireAt(genericKey1, 500, ExpireOptions.HAS_EXISTING_EXPIRY)
                        false, // pexpire(genericKey1, 42, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
                        false, // pexpireAt(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                    });
        }
        return expectedResults;
    }

    private static Object[] stringCommands(BaseTransaction<?> transaction) {
        String stringKey1 = "{StringKey}-1-" + UUID.randomUUID();
        String stringKey2 = "{StringKey}-2-" + UUID.randomUUID();
        String stringKey3 = "{StringKey}-3-" + UUID.randomUUID();

        transaction
                .set(stringKey1, value1)
                .get(stringKey1)
                .set(stringKey2, value2, SetOptions.builder().returnOldValue(true).build())
                .strlen(stringKey2)
                .mset(Map.of(stringKey1, value2, stringKey2, value1))
                .mget(new String[] {stringKey1, stringKey2})
                .incr(stringKey3)
                .incrBy(stringKey3, 2)
                .decr(stringKey3)
                .decrBy(stringKey3, 2)
                .incrByFloat(stringKey3, 0.5)
                .setrange(stringKey3, 0, "GLIDE")
                .getrange(stringKey3, 0, 5);

        return new Object[] {
            OK, // set(stringKey1, value1)
            value1, // get(stringKey1)
            null, // set(stringKey2, value2, returnOldValue(true))
            (long) value1.length(), // strlen(key2)
            OK, // mset(Map.of(stringKey1, value2, stringKey2, value1))
            new String[] {value2, value1}, // mget(new String[] {stringKey1, stringKey2})
            1L, // incr(stringKey3)
            3L, // incrBy(stringKey3, 2)
            2L, // decr(stringKey3)
            0L, // decrBy(stringKey3, 2)
            0.5, // incrByFloat(stringKey3, 0.5)
            5L, // setrange(stringKey3, 0, "GLIDE")
            "GLIDE" // getrange(stringKey3, 0, 5)
        };
    }

    private static Object[] hashCommands(BaseTransaction<?> transaction) {
        String hashKey1 = "{HashKey}-1-" + UUID.randomUUID();

        transaction
                .hset(hashKey1, Map.of(field1, value1, field2, value2))
                .hget(hashKey1, field1)
                .hlen(hashKey1)
                .hexists(hashKey1, field2)
                .hsetnx(hashKey1, field1, value1)
                .hmget(hashKey1, new String[] {field1, "non_existing_field", field2})
                .hgetall(hashKey1)
                .hdel(hashKey1, new String[] {field1})
                .hvals(hashKey1)
                .hincrBy(hashKey1, field3, 5)
                .hincrByFloat(hashKey1, field3, 5.5)
                .hkeys(hashKey1);

        return new Object[] {
            2L, // hset(hashKey1, Map.of(field1, value1, field2, value2))
            value1, // hget(hashKey1, field1)
            2L, // hlen(hashKey1)
            true, // hexists(hashKey1, field2)
            false, // hsetnx(hashKey1, field1, value1)
            new String[] {value1, null, value2}, // hmget(hashKey1, new String[] {...})
            Map.of(field1, value1, field2, value2), // hgetall(hashKey1)
            1L, // hdel(hashKey1, new String[] {field1})
            new String[] {value2}, // hvals(hashKey1)
            5L, // hincrBy(hashKey1, field3, 5)
            10.5, // hincrByFloat(hashKey1, field3, 5.5)
            new String[] {field2, field3}, // hkeys(hashKey1)
        };
    }

    private static Object[] listCommands(BaseTransaction<?> transaction) {
        String listKey1 = "{ListKey}-1-" + UUID.randomUUID();
        String listKey2 = "{ListKey}-2-" + UUID.randomUUID();
        String listKey3 = "{ListKey}-3-" + UUID.randomUUID();

        transaction
                .lpush(listKey1, new String[] {value1, value1, value2, value3, value3})
                .llen(listKey1)
                .lindex(listKey1, 0)
                .lrem(listKey1, 1, value1)
                .ltrim(listKey1, 1, -1)
                .lrange(listKey1, 0, -2)
                .lpop(listKey1)
                .lpopCount(listKey1, 2)
                .rpush(listKey2, new String[] {value1, value2, value2})
                .rpop(listKey2)
                .rpopCount(listKey2, 2)
                .rpushx(listKey3, new String[] {"_"})
                .lpushx(listKey3, new String[] {"_"})
                .lpush(listKey3, new String[] {value1, value2, value3})
                .linsert(listKey3, AFTER, value2, value2)
                .blpop(new String[] {listKey3}, 0.01)
                .brpop(new String[] {listKey3}, 0.01);

        return new Object[] {
            5L, // lpush(listKey1, new String[] {value1, value1, value2, value3, value3})
            5L, // llen(listKey1)
            value3, // lindex(key5, 0)
            1L, // lrem(listKey1, 1, value1)
            OK, // ltrim(listKey1, 1, -1)
            new String[] {value3, value2}, // lrange(listKey1, 0, -2)
            value3, // lpop(listKey1)
            new String[] {value2, value1}, // lpopCount(listKey1, 2)
            3L, // rpush(listKey2, new String[] {value1, value2, value2})
            value2, // rpop(listKey2)
            new String[] {value2, value1}, // rpopCount(listKey2, 2)
            0L, // rpushx(listKey3, new String[] { "_" })
            0L, // lpushx(listKey3, new String[] { "_" })
            3L, // lpush(listKey3, new String[] { value1, value2, value3})
            4L, // linsert(listKey3, AFTER, value2, value2)
            new String[] {listKey3, value3}, // blpop(new String[] { listKey3 }, 0.01)
            new String[] {listKey3, value1}, // brpop(new String[] { listKey3 }, 0.01)
        };
    }

    private static Object[] setCommands(BaseTransaction<?> transaction) {
        String setKey1 = "{setKey}-1-" + UUID.randomUUID();
        String setKey2 = "{setKey}-2-" + UUID.randomUUID();
        String setKey3 = "{setKey}-3-" + UUID.randomUUID();

        transaction
                .sadd(setKey1, new String[] {"baz", "foo"})
                .srem(setKey1, new String[] {"foo"})
                .scard(setKey1)
                .sismember(setKey1, "baz")
                .smembers(setKey1)
                .smismember(setKey1, new String[] {"baz", "foo"})
                .sinter(new String[] {setKey1, setKey1})
                .sadd(setKey2, new String[] {"a", "b"})
                .sunionstore(setKey3, new String[] {setKey2, setKey1})
                .sdiffstore(setKey3, new String[] {setKey2, setKey1})
                .sinterstore(setKey3, new String[] {setKey2, setKey1})
                .sdiff(new String[] {setKey2, setKey3})
                .smove(setKey1, setKey2, "baz");

        return new Object[] {
            2L, // sadd(setKey1, new String[] {"baz", "foo"});
            1L, // srem(setKey1, new String[] {"foo"});
            1L, // scard(setKey1);
            true, // sismember(setKey1, "baz")
            Set.of("baz"), // smembers(setKey1);
            new Boolean[] {true, false}, // smismembmer(setKey1, new String[] {"baz", "foo"})
            Set.of("baz"), // sinter(new String[] { setKey1, setKey1 })
            2L, // sadd(setKey2, new String[] { "a", "b" })
            3L, // sunionstore(setKey3, new String[] { setKey2, setKey1 })
            2L, // sdiffstore(setKey3, new String[] { setKey2, setKey1 })
            0L, // sinterstore(setKey3, new String[] { setKey2, setKey1 })
            Set.of("a", "b"), // sdiff(new String[] {setKey2, setKey3})
            true, // smove(setKey1, setKey2, "baz")
        };
    }

    private static Object[] sortedSetCommands(BaseTransaction<?> transaction) {
        String zSetKey1 = "{ZSetKey}-1-" + UUID.randomUUID();
        String zSetKey2 = "{ZSetKey}-2-" + UUID.randomUUID();

        transaction
                .zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0))
                .zrank(zSetKey1, "one")
                .zrevrank(zSetKey1, "one")
                .zaddIncr(zSetKey1, "one", 3)
                .zrem(zSetKey1, new String[] {"one"})
                .zcard(zSetKey1)
                .zmscore(zSetKey1, new String[] {"two", "three"})
                .zrange(zSetKey1, new RangeByIndex(0, 1))
                .zrangeWithScores(zSetKey1, new RangeByIndex(0, 1))
                .zrangestore(zSetKey1, zSetKey1, new RangeByIndex(0, -1))
                .zscore(zSetKey1, "two")
                .zcount(zSetKey1, new ScoreBoundary(2, true), InfScoreBound.POSITIVE_INFINITY)
                .zlexcount(zSetKey1, new LexBoundary("a", true), InfLexBound.POSITIVE_INFINITY)
                .zpopmin(zSetKey1)
                .zpopmax(zSetKey1)
                .zremrangebyrank(zSetKey1, 5, 10)
                .zremrangebylex(zSetKey1, new LexBoundary("j"), InfLexBound.POSITIVE_INFINITY)
                .zremrangebyscore(zSetKey1, new ScoreBoundary(5), InfScoreBound.POSITIVE_INFINITY)
                .zdiffstore(zSetKey1, new String[] {zSetKey1, zSetKey1})
                .zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0))
                .zdiff(new String[] {zSetKey2, zSetKey1})
                .zdiffWithScores(new String[] {zSetKey2, zSetKey1})
                .zunionstore(zSetKey2, new KeyArray(new String[] {zSetKey2, zSetKey1}))
                .zunion(new KeyArray(new String[] {zSetKey2, zSetKey1}))
                .zunion(new KeyArray(new String[] {zSetKey2, zSetKey1}), Aggregate.MAX)
                .zunionWithScores(new KeyArray(new String[] {zSetKey2, zSetKey1}))
                .zunionWithScores(new KeyArray(new String[] {zSetKey2, zSetKey1}), Aggregate.MAX)
                .zinterstore(zSetKey1, new KeyArray(new String[] {zSetKey2, zSetKey1}))
                .bzpopmax(new String[] {zSetKey2}, .1)
                .zrandmember(zSetKey2)
                .zrandmemberWithCount(zSetKey2, 1)
                .zrandmemberWithCountWithScores(zSetKey2, 1)
                .bzpopmin(new String[] {zSetKey2}, .1);
        // zSetKey2 is now empty

        return new Object[] {
            3L, // zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0))
            0L, // zrank(zSetKey1, "one")
            2L, // zrevrank(zSetKey1, "one")
            4.0, // zaddIncr(zSetKey1, "one", 3)
            1L, // zrem(zSetKey1, new String[] {"one"})
            2L, // zcard(zSetKey1)
            new Double[] {2.0, 3.0}, // zmscore(zSetKey1, new String[] {"two", "three"})
            new String[] {"two", "three"}, // zrange(zSetKey1, new RangeByIndex(0, 1))
            Map.of("two", 2.0, "three", 3.0), // zrangeWithScores(zSetKey1, new RangeByIndex(0, 1))
            2L, // zrangestore(zSetKey1, zSetKey1, new RangeByIndex(0, -1))
            2.0, // zscore(zSetKey1, "two")
            2L, // zcount(zSetKey1, new ScoreBoundary(2, true), InfScoreBound.POSITIVE_INFINITY)
            2L, // zlexcount(zSetKey1, new LexBoundary("a", true), InfLexBound.POSITIVE_INFINITY)
            Map.of("two", 2.0), // zpopmin(zSetKey1)
            Map.of("three", 3.0), // zpopmax(zSetKey1)
            0L, // zremrangebyrank(zSetKey1, 5, 10)
            0L, // zremrangebylex(zSetKey1, new LexBoundary("j"), InfLexBound.POSITIVE_INFINITY)
            0L, // zremrangebyscore(zSetKey1, new ScoreBoundary(5), InfScoreBound.POSITIVE_INFINITY)
            0L, // zdiffstore(zSetKey1, new String[] {zSetKey1, zSetKey1})
            2L, // zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0))
            new String[] {"one", "two"}, // zdiff(new String[] {zSetKey2, zSetKey1})
            Map.of("one", 1.0, "two", 2.0), // zdiffWithScores(new String[] {zSetKey2, zSetKey1})
            2L, // zunionstore(zSetKey2, new KeyArray(new String[] {zSetKey2, zSetKey1}))
            new String[] {"one", "two"}, // zunion(new KeyArray({zSetKey2, zSetKey1}))
            new String[] {"one", "two"}, // zunion(new KeyArray({zSetKey2, zSetKey1}), Aggregate.MAX);
            Map.of("one", 1.0, "two", 2.0), // zunionWithScores(new KeyArray({zSetKey2, zSetKey1}));
            Map.of("one", 1.0, "two", 2.0), // zunionWithScores(new KeyArray({zSetKey2, zSetKey1}), MAX)
            0L, // zinterstore(zSetKey1, new String[] {zSetKey2, zSetKey1})
            new Object[] {zSetKey2, "two", 2.0}, // bzpopmax(new String[] { zsetKey2 }, .1)
            "one", // .zrandmember(zSetKey2)
            new String[] {"one"}, // .zrandmemberWithCount(zSetKey2, 1)
            new Object[][] {{"one", 1.0}}, // .zrandmemberWithCountWithScores(zSetKey2, 1);
            new Object[] {zSetKey2, "one", 1.0}, // bzpopmin(new String[] { zsetKey2 }, .1)
        };
    }

    private static Object[] serverManagementCommands(BaseTransaction<?> transaction) {
        transaction
                .configSet(Map.of("timeout", "1000"))
                .configGet(new String[] {"timeout"})
                .configResetStat()
                .lolwut(1)
                .flushall()
                .flushall(ASYNC);

        return new Object[] {
            OK, // configSet(Map.of("timeout", "1000"))
            Map.of("timeout", "1000"), // configGet(new String[] {"timeout"})
            OK, // configResetStat()
            "Redis ver. " + REDIS_VERSION + '\n', // lolwut(1)
            OK, // flushall()
            OK, // flushall(ASYNC)
        };
    }

    private static Object[] connectionManagementCommands(BaseTransaction<?> transaction) {
        transaction.ping().ping(value1).echo(value2);
        // untested:
        // clientId
        // clientGetName

        return new Object[] {
            "PONG", // ping()
            value1, // ping(value1)
            value2, // echo(value2)
        };
    }

    private static Object[] hyperLogLogCommands(BaseTransaction<?> transaction) {
        String hllKey1 = "{HllKey}-1-" + UUID.randomUUID();
        String hllKey2 = "{HllKey}-2-" + UUID.randomUUID();
        String hllKey3 = "{HllKey}-3-" + UUID.randomUUID();

        transaction
                .pfadd(hllKey1, new String[] {"a", "b", "c"})
                .pfcount(new String[] {hllKey1, hllKey2})
                .pfmerge(hllKey3, new String[] {hllKey1, hllKey2})
                .pfcount(new String[] {hllKey3});

        return new Object[] {
            1L, // pfadd(hllKey1, new String[] {"a", "b", "c"})
            3L, // pfcount(new String[] { hllKey1, hllKey2 })
            OK, // pfmerge(hllKey3, new String[] {hllKey1, hllKey2})
            3L, // pfcount(new String[] { hllKey3 })
        };
    }

    private static Object[] streamCommands(BaseTransaction<?> transaction) {
        final String streamKey1 = "{streamKey}-1-" + UUID.randomUUID();

        transaction
                .xadd(streamKey1, Map.of("field1", "value1"), StreamAddOptions.builder().id("0-1").build())
                .xadd(streamKey1, Map.of("field2", "value2"), StreamAddOptions.builder().id("0-2").build())
                .xadd(streamKey1, Map.of("field3", "value3"), StreamAddOptions.builder().id("0-3").build())
                .xtrim(streamKey1, new MinId(true, "0-2"));

        return new Object[] {
            "0-1", // xadd(streamKey1, Map.of("field1", "value1"), ... .id("0-1").build());
            "0-2", // xadd(streamKey1, Map.of("field2", "value2"), ... .id("0-2").build());
            "0-3", // xadd(streamKey1, Map.of("field3", "value3"), ... .id("0-3").build());
            1L, // xtrim(streamKey1, new MinId(true, "0-2"))
        };
    }

    private static Object[] geospatialCommands(BaseTransaction<?> transaction) {
        final String geoKey1 = "{geoKey}-1-" + UUID.randomUUID();

        transaction
                .geoadd(
                        geoKey1,
                        Map.of(
                                "Palermo",
                                new GeospatialData(13.361389, 38.115556),
                                "Catania",
                                new GeospatialData(15.087269, 37.502669)))
                .geopos(geoKey1, new String[] {"Palermo", "Catania"});

        return new Object[] {
            2L, // geoadd(geoKey1, Map.of("Palermo", ..., "Catania", ...))
            new Double[][] {
                {13.36138933897018433, 38.11555639549629859},
                {15.08726745843887329, 37.50266842333162032},
            }, // geopos(new String[]{"Palermo", "Catania"})
        };
    }
}
