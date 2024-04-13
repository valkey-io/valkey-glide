/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.api.BaseClient.OK;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;

import glide.api.models.BaseTransaction;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.StreamAddOptions;
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

        baseTransaction.set(key2, value2, SetOptions.builder().returnOldValue(true).build());
        baseTransaction.strlen(key2);
        baseTransaction.customCommand(new String[] {"MGET", key1, key2});

        baseTransaction.exists(new String[] {key1});
        baseTransaction.persist(key1);

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
        // TODO update after #203 merge & rebase
        baseTransaction.sadd(setKey2, new String[] {"a", "b"});
        baseTransaction.sinterstore(setKey3, new String[] {setKey2, key7});

        baseTransaction.zadd(key8, Map.of("one", 1.0, "two", 2.0, "three", 3.0));
        baseTransaction.zrank(key8, "one");
        baseTransaction.zaddIncr(key8, "one", 3);
        baseTransaction.zrem(key8, new String[] {"one"});
        baseTransaction.zcard(key8);
        baseTransaction.zmscore(key8, new String[] {"two", "three"});
        baseTransaction.zrange(key8, new RangeByIndex(0, 1));
        baseTransaction.zrangeWithScores(key8, new RangeByIndex(0, 1));
        baseTransaction.zscore(key8, "two");
        baseTransaction.zcount(key8, new ScoreBoundary(2, true), InfScoreBound.POSITIVE_INFINITY);
        baseTransaction.zpopmin(key8);
        baseTransaction.zpopmax(key8);
        baseTransaction.zdiffstore(key8, new String[] {key8, key8});

        baseTransaction.zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0));
        baseTransaction.zdiff(new String[] {zSetKey2, key8});
        baseTransaction.zdiffWithScores(new String[] {zSetKey2, key8});

        baseTransaction.xadd(
                key9, Map.of("field1", "value1"), StreamAddOptions.builder().id("0-1").build());
        baseTransaction.xadd(
                key9, Map.of("field2", "value2"), StreamAddOptions.builder().id("0-2").build());
        baseTransaction.xadd(
                key9, Map.of("field3", "value3"), StreamAddOptions.builder().id("0-3").build());

        baseTransaction.configSet(Map.of("timeout", "1000"));
        baseTransaction.configGet(new String[] {"timeout"});

        baseTransaction.configResetStat();

        baseTransaction.echo("GLIDE");

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

        return baseTransaction;
    }

    public static Object[] transactionTestResult() {
        return new Object[] {
            OK,
            value1,
            "string", // type(key1)
            null,
            (long) value1.length(), // strlen(key2)
            new String[] {value1, value2},
            1L,
            Boolean.FALSE, // persist(key1)
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
            Set.of("baz"),
            // TODO update after #203 merge & rebase
            2L, // sadd(setKey2, new String[] { "a", "b" })
            0L, // sinterstore(setKey3, new String[] { setKey2, key7 })
            3L,
            0L, // zrank(key8, "one")
            4.0,
            1L,
            2L,
            new Double[] {2.0, 3.0}, // zmscore(key8, new String[] {"two", "three"})
            new String[] {"two", "three"}, // zrange
            Map.of("two", 2.0, "three", 3.0), // zrangeWithScores
            2.0, // zscore(key8, "two")
            2L, // zcount(key8, new ScoreBoundary(2, true), InfScoreBound.POSITIVE_INFINITY)
            Map.of("two", 2.0), // zpopmin(key8)
            Map.of("three", 3.0), // zpopmax(key8)
            0L, // zdiffstore(key8, new String[] {key8, key8})
            2L, // zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0))
            new String[] {"one", "two"}, // zdiff(new String[] {zSetKey2, key8})
            Map.of("one", 1.0, "two", 2.0), // zdiffWithScores(new String[] {zSetKey2, key8})
            "0-1", // xadd(key9, Map.of("field1", "value1"),
            // StreamAddOptions.builder().id("0-1").build());
            "0-2", // xadd(key9, Map.of("field2", "value2"),
            // StreamAddOptions.builder().id("0-2").build());
            "0-3", // xadd(key9, Map.of("field3", "value3"),
            // StreamAddOptions.builder().id("0-3").build());
            OK,
            Map.of("timeout", "1000"),
            OK,
            "GLIDE", // echo
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
        };
    }
}
