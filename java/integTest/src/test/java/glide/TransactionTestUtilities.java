/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.REDIS_VERSION;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;

import glide.api.models.BaseTransaction;
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

public class TransactionTestUtilities {
    private static final String key1 = "{key}" + UUID.randomUUID();
    private static final String key2 = "{key}" + UUID.randomUUID();
    private static final String key3 = "{key}" + UUID.randomUUID();
    private static final String key4 = "{key}" + UUID.randomUUID();
    private static final String key5 = "{key}" + UUID.randomUUID();
    private static final String key6 = "{key}" + UUID.randomUUID();
    private static final String listKey3 = "{key}:listKey3-" + UUID.randomUUID();
    private static final String key7 = "{key}" + UUID.randomUUID();
    private static final String setKey2 = "{key}" + UUID.randomUUID();
    private static final String setKey3 = "{key}" + UUID.randomUUID();
    private static final String key8 = "{key}" + UUID.randomUUID();
    private static final String zSetKey2 = "{key}:zsetKey2-" + UUID.randomUUID();
    private static final String key9 = "{key}" + UUID.randomUUID();
    private static final String hllKey1 = "{key}:hllKey1-" + UUID.randomUUID();
    private static final String hllKey2 = "{key}:hllKey2-" + UUID.randomUUID();
    private static final String hllKey3 = "{key}:hllKey3-" + UUID.randomUUID();
    private static final String geoKey1 = "{key}:geoKey1-" + UUID.randomUUID();
    private static final String value1 = UUID.randomUUID().toString();
    private static final String value2 = UUID.randomUUID().toString();
    private static final String value3 = UUID.randomUUID().toString();
    private static final String field1 = UUID.randomUUID().toString();
    private static final String field2 = UUID.randomUUID().toString();
    private static final String field3 = UUID.randomUUID().toString();

    public static BaseTransaction<?> transactionTest(BaseTransaction<?> baseTransaction) {

        baseTransaction.set(key1, value1);
        baseTransaction.get(key1);
        baseTransaction.type(key1);
        baseTransaction.objectEncoding(key1);

        baseTransaction.set(key2, value2, SetOptions.builder().returnOldValue(true).build());
        baseTransaction.strlen(key2);
        baseTransaction.customCommand(new String[] {"MGET", key1, key2});
        baseTransaction.renamenx(key1, key2);

        baseTransaction.exists(new String[] {key1});
        baseTransaction.persist(key1);

        baseTransaction.touch(new String[] {key1});

        baseTransaction.del(new String[] {key1});
        baseTransaction.get(key1);

        baseTransaction.unlink(new String[] {key2});
        baseTransaction.get(key2);

        baseTransaction.mset(Map.of(key1, value2, key2, value1));
        baseTransaction.mget(new String[] {key1, key2});

        baseTransaction.incr(key3);
        baseTransaction.incrBy(key3, 2);

        baseTransaction.decr(key3);
        baseTransaction.decrBy(key3, 2);

        baseTransaction.incrByFloat(key3, 0.5);

        baseTransaction.unlink(new String[] {key3});
        baseTransaction.setrange(key3, 0, "GLIDE");
        baseTransaction.getrange(key3, 0, 5);

        baseTransaction.hset(key4, Map.of(field1, value1, field2, value2));
        baseTransaction.hget(key4, field1);
        baseTransaction.hlen(key4);
        baseTransaction.hexists(key4, field2);
        baseTransaction.hsetnx(key4, field1, value1);
        baseTransaction.hmget(key4, new String[] {field1, "non_existing_field", field2});
        baseTransaction.hgetall(key4);
        baseTransaction.hdel(key4, new String[] {field1});
        baseTransaction.hvals(key4);

        baseTransaction.hincrBy(key4, field3, 5);
        baseTransaction.hincrByFloat(key4, field3, 5.5);
        baseTransaction.hkeys(key4);

        baseTransaction.lpush(key5, new String[] {value1, value1, value2, value3, value3});
        baseTransaction.llen(key5);
        baseTransaction.lindex(key5, 0);
        baseTransaction.lrem(key5, 1, value1);
        baseTransaction.ltrim(key5, 1, -1);
        baseTransaction.lrange(key5, 0, -2);
        baseTransaction.lpop(key5);
        baseTransaction.lpopCount(key5, 2);

        baseTransaction.rpush(key6, new String[] {value1, value2, value2});
        baseTransaction.rpop(key6);
        baseTransaction.rpopCount(key6, 2);

        baseTransaction.sadd(key7, new String[] {"baz", "foo"});
        baseTransaction.srem(key7, new String[] {"foo"});
        baseTransaction.scard(key7);
        baseTransaction.sismember(key7, "baz");
        baseTransaction.smembers(key7);
        baseTransaction.smismember(key7, new String[] {"baz", "foo"});
        baseTransaction.sinter(new String[] {key7, key7});

        baseTransaction.sadd(setKey2, new String[] {"a", "b"});
        baseTransaction.sunionstore(setKey3, new String[] {setKey2, key7});
        baseTransaction.sdiffstore(setKey3, new String[] {setKey2, key7});
        baseTransaction.sinterstore(setKey3, new String[] {setKey2, key7});
        baseTransaction.sdiff(new String[] {setKey2, setKey3});
        baseTransaction.smove(key7, setKey2, "baz");

        baseTransaction.zadd(key8, Map.of("one", 1.0, "two", 2.0, "three", 3.0));
        baseTransaction.zrank(key8, "one");
        baseTransaction.zrevrank(key8, "one");
        baseTransaction.zaddIncr(key8, "one", 3);
        baseTransaction.zrem(key8, new String[] {"one"});
        baseTransaction.zcard(key8);
        baseTransaction.zmscore(key8, new String[] {"two", "three"});
        baseTransaction.zrange(key8, new RangeByIndex(0, 1));
        baseTransaction.zrangeWithScores(key8, new RangeByIndex(0, 1));
        baseTransaction.zrangestore(key8, key8, new RangeByIndex(0, -1));
        baseTransaction.zscore(key8, "two");
        baseTransaction.zcount(key8, new ScoreBoundary(2, true), InfScoreBound.POSITIVE_INFINITY);
        baseTransaction.zlexcount(key8, new LexBoundary("a", true), InfLexBound.POSITIVE_INFINITY);
        baseTransaction.zpopmin(key8);
        baseTransaction.zpopmax(key8);
        baseTransaction.zremrangebyrank(key8, 5, 10);
        baseTransaction.zremrangebylex(key8, new LexBoundary("j"), InfLexBound.POSITIVE_INFINITY);
        baseTransaction.zremrangebyscore(key8, new ScoreBoundary(5), InfScoreBound.POSITIVE_INFINITY);
        baseTransaction.zdiffstore(key8, new String[] {key8, key8});

        baseTransaction.zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0));
        baseTransaction.zdiff(new String[] {zSetKey2, key8});
        baseTransaction.zdiffWithScores(new String[] {zSetKey2, key8});
        baseTransaction.zunion(new KeyArray(new String[] {zSetKey2, key8}));
        baseTransaction.zunion(new KeyArray(new String[] {zSetKey2, key8}), Aggregate.MAX);
        baseTransaction.zunionWithScores(new KeyArray(new String[] {zSetKey2, key8}));
        baseTransaction.zunionWithScores(new KeyArray(new String[] {zSetKey2, key8}), Aggregate.MAX);
        baseTransaction.zinterstore(key8, new KeyArray(new String[] {zSetKey2, key8}));
        baseTransaction.bzpopmax(new String[] {zSetKey2}, .1);
        baseTransaction.bzpopmin(new String[] {zSetKey2}, .1);
        // zSetKey2 is now empty

        baseTransaction.geoadd(
                geoKey1,
                Map.of(
                        "Palermo",
                        new GeospatialData(13.361389, 38.115556),
                        "Catania",
                        new GeospatialData(15.087269, 37.502669)));
        baseTransaction.geopos(geoKey1, new String[] {"Palermo", "Catania"});

        baseTransaction.xadd(
                key9, Map.of("field1", "value1"), StreamAddOptions.builder().id("0-1").build());
        baseTransaction.xadd(
                key9, Map.of("field2", "value2"), StreamAddOptions.builder().id("0-2").build());
        baseTransaction.xadd(
                key9, Map.of("field3", "value3"), StreamAddOptions.builder().id("0-3").build());
        baseTransaction.xtrim(key9, new MinId(true, "0-2"));

        baseTransaction.configSet(Map.of("timeout", "1000"));
        baseTransaction.configGet(new String[] {"timeout"});

        baseTransaction.configResetStat();

        baseTransaction.echo("GLIDE");

        baseTransaction.lolwut(1);

        baseTransaction.rpushx(listKey3, new String[] {"_"}).lpushx(listKey3, new String[] {"_"});
        baseTransaction
                .lpush(listKey3, new String[] {value1, value2, value3})
                .linsert(listKey3, AFTER, value2, value2);

        baseTransaction.blpop(new String[] {listKey3}, 0.01).brpop(new String[] {listKey3}, 0.01);

        baseTransaction.pfadd(hllKey1, new String[] {"a", "b", "c"});
        baseTransaction.pfcount(new String[] {hllKey1, hllKey2});
        baseTransaction
                .pfmerge(hllKey3, new String[] {hllKey1, hllKey2})
                .pfcount(new String[] {hllKey3});

        // keep it last - it deletes all the keys
        baseTransaction.flushall().flushall(ASYNC);

        return baseTransaction;
    }

    public static Object[] transactionTestResult() {
        return new Object[] {
            OK,
            value1,
            "string", // type(key1)
            "embstr", // objectEncoding(key1)
            null,
            (long) value1.length(), // strlen(key2)
            new String[] {value1, value2},
            false, // renamenx(key1, key2)
            1L,
            Boolean.FALSE, // persist(key1)
            1L, // touch(new String[] {key1})
            1L,
            null,
            1L,
            null,
            OK,
            new String[] {value2, value1},
            1L,
            3L,
            2L,
            0L,
            0.5,
            1L,
            5L, // setrange(key3, 0, "GLIDE")
            "GLIDE", // getrange(key3, 0, 5)
            2L,
            value1,
            2L, // hlen(key4)
            true,
            Boolean.FALSE, // hsetnx(key4, field1, value1)
            new String[] {value1, null, value2},
            Map.of(field1, value1, field2, value2),
            1L,
            new String[] {value2}, // hvals(key4)
            5L,
            10.5,
            new String[] {field2, field3}, // hkeys(key4)
            5L,
            5L,
            value3, // lindex(key5, 0)
            1L,
            OK,
            new String[] {value3, value2},
            value3,
            new String[] {value2, value1},
            3L,
            value2,
            new String[] {value2, value1},
            2L,
            1L,
            1L,
            true, // sismember(key7, "baz")
            Set.of("baz"), // smembers(key7)
            new Boolean[] {true, false}, // smismembmer(key7, new String[] {"baz", "foo"})
            Set.of("baz"), // sinter(new String[] { key7, key7 })
            2L, // sadd(setKey2, new String[] { "a", "b" })
            3L, // sunionstore(setKey3, new String[] { setKey2, key7 })
            2L, // sdiffstore(setKey3, new String[] { setKey2, key7 })
            0L, // sinterstore(setKey3, new String[] { setKey2, key7 })
            Set.of("a", "b"), // sdiff(new String[] {setKey2, setKey3})
            true, // smove(key7, setKey2, "baz")
            3L,
            0L, // zrank(key8, "one")
            2L, // zrevrank(key8, "one")
            4.0,
            1L,
            2L,
            new Double[] {2.0, 3.0}, // zmscore(key8, new String[] {"two", "three"})
            new String[] {"two", "three"}, // zrange
            Map.of("two", 2.0, "three", 3.0), // zrangeWithScores
            2L, // zrangestore(key8, key8, new RangeByIndex(0, -1))
            2.0, // zscore(key8, "two")
            2L, // zcount(key8, new ScoreBoundary(2, true), InfScoreBound.POSITIVE_INFINITY)
            2L, // zlexcount(key8, new LexBoundary("a", true), InfLexBound.POSITIVE_INFINITY)
            Map.of("two", 2.0), // zpopmin(key8)
            Map.of("three", 3.0), // zpopmax(key8)
            0L, // zremrangebyrank(key8, 5, 10)
            0L, // zremrangebylex(key8, new LexBoundary("j"), InfLexBound.POSITIVE_INFINITY)
            0L, // zremrangebyscore(key8, new ScoreBoundary(5), InfScoreBound.POSITIVE_INFINITY)
            0L, // zdiffstore(key8, new String[] {key8, key8})
            2L, // zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0))
            new String[] {"one", "two"}, // zdiff(new String[] {zSetKey2, key8})
            Map.of("one", 1.0, "two", 2.0), // zdiffWithScores(new String[] {zSetKey2, key8})
            new String[] {"one", "two"}, // zunion(new KeyArray({zSetKey2, key8}))
            new String[] {"one", "two"}, // zunion(new KeyArray({zSetKey2, key8}), Aggregate.MAX);
            Map.of("one", 1.0, "two", 2.0), // zunionWithScores(new KeyArray({zSetKey2, key8}));
            Map.of("one", 1.0, "two", 2.0), // zunionWithScores(new KeyArray({zSetKey2, key8}), MAX)
            0L, // zinterstore(key8, new String[] {zSetKey2, key8})
            new Object[] {zSetKey2, "two", 2.0}, // bzpopmax(new String[] { zsetKey2 }, .1)
            new Object[] {zSetKey2, "one", 1.0}, // bzpopmin(new String[] { zSetKey2 }, .1)
            2L, // geoadd(geoKey1, Map.of("Palermo", ..., "Catania", ...))
            new Double[][] {
                {13.36138933897018433, 38.11555639549629859},
                {15.08726745843887329, 37.50266842333162032},
            }, // geopos(new String[]{"Palermo", "Catania"})
            "0-1", // xadd(key9, Map.of("field1", "value1"), id("0-1"));
            "0-2", // xadd(key9, Map.of("field2", "value2"), id("0-2"));
            "0-3", // xadd(key9, Map.of("field3", "value3"), id("0-3"));
            1L, // xtrim(key9, new MinId(true, "0-2"));
            OK,
            Map.of("timeout", "1000"),
            OK,
            "GLIDE", // echo
            "Redis ver. " + REDIS_VERSION + '\n', // lolwut(1)
            0L, // rpushx(listKey3, new String[] { "_" })
            0L, // lpushx(listKey3, new String[] { "_" })
            3L, // lpush(listKey3, new String[] { value1, value2, value3})
            4L, // linsert(listKey3, AFTER, value2, value2)
            new String[] {listKey3, value3}, // blpop(new String[] { listKey3 }, 0.01)
            new String[] {listKey3, value1}, // brpop(new String[] { listKey3 }, 0.01);
            1L, // pfadd(hllKey1, new String[] {"a", "b", "c"})
            3L, // pfcount(new String[] { hllKey1, hllKey2 });;
            OK, // pfmerge(hllKey3, new String[] {hllKey1, hllKey2})
            3L, // pfcount(new String[] { hllKey3 })
            OK, // flushall()
            OK, // flushall(ASYNC)
        };
    }
}
