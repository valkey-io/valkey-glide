/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.ServerManagementCommands.VERSION_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_REDIS_API;
import static glide.api.models.commands.ExpireOptions.HAS_EXISTING_EXPIRY;
import static glide.api.models.commands.ExpireOptions.HAS_NO_EXPIRY;
import static glide.api.models.commands.ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;
import static glide.api.models.commands.RangeOptions.InfScoreBound.NEGATIVE_INFINITY;
import static glide.api.models.commands.RangeOptions.InfScoreBound.POSITIVE_INFINITY;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.WeightAggregateOptions.AGGREGATE_REDIS_API;
import static glide.api.models.commands.WeightAggregateOptions.WEIGHTS_REDIS_API;
import static glide.api.models.commands.ZaddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT;
import static glide.api.models.commands.geospatial.GeoAddOptions.CHANGED_REDIS_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_EXACT_REDIS_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_MINID_REDIS_API;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis_request.RedisRequestOuterClass.RequestType.BZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.BZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.Blpop;
import static redis_request.RedisRequestOuterClass.RequestType.Brpop;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Echo;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.FlushAll;
import static redis_request.RedisRequestOuterClass.RequestType.GeoAdd;
import static redis_request.RedisRequestOuterClass.RequestType.GeoPos;
import static redis_request.RedisRequestOuterClass.RequestType.GetRange;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.HLen;
import static redis_request.RedisRequestOuterClass.RequestType.HSetNX;
import static redis_request.RedisRequestOuterClass.RequestType.HashDel;
import static redis_request.RedisRequestOuterClass.RequestType.HashExists;
import static redis_request.RedisRequestOuterClass.RequestType.HashGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HashMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashSet;
import static redis_request.RedisRequestOuterClass.RequestType.Hkeys;
import static redis_request.RedisRequestOuterClass.RequestType.Hvals;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LInsert;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LOLWUT;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LPushX;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.LastSave;
import static redis_request.RedisRequestOuterClass.RequestType.Lindex;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectEncoding;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectFreq;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectIdletime;
import static redis_request.RedisRequestOuterClass.RequestType.ObjectRefcount;
import static redis_request.RedisRequestOuterClass.RequestType.PExpire;
import static redis_request.RedisRequestOuterClass.RequestType.PExpireAt;
import static redis_request.RedisRequestOuterClass.RequestType.PTTL;
import static redis_request.RedisRequestOuterClass.RequestType.Persist;
import static redis_request.RedisRequestOuterClass.RequestType.PfAdd;
import static redis_request.RedisRequestOuterClass.RequestType.PfCount;
import static redis_request.RedisRequestOuterClass.RequestType.PfMerge;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.RPop;
import static redis_request.RedisRequestOuterClass.RequestType.RPush;
import static redis_request.RedisRequestOuterClass.RequestType.RPushX;
import static redis_request.RedisRequestOuterClass.RequestType.RenameNx;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SDiff;
import static redis_request.RedisRequestOuterClass.RequestType.SDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.SInter;
import static redis_request.RedisRequestOuterClass.RequestType.SInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.SIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SMove;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SUnionStore;
import static redis_request.RedisRequestOuterClass.RequestType.SetRange;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.Strlen;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.Touch;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.XAdd;
import static redis_request.RedisRequestOuterClass.RequestType.XTrim;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiff;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZLexCount;
import static redis_request.RedisRequestOuterClass.RequestType.ZMScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.ZRangeStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByLex;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZRevRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZUnion;
import static redis_request.RedisRequestOuterClass.RequestType.Zadd;
import static redis_request.RedisRequestOuterClass.RequestType.Zcard;
import static redis_request.RedisRequestOuterClass.RequestType.Zcount;
import static redis_request.RedisRequestOuterClass.RequestType.Zrange;
import static redis_request.RedisRequestOuterClass.RequestType.Zrank;
import static redis_request.RedisRequestOuterClass.RequestType.Zrem;

import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.Limit;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.ZaddOptions;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamTrimOptions.MinId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RequestType;

public class TransactionTests {
    private static Stream<Arguments> getTransactionBuilders() {
        return Stream.of(Arguments.of(new Transaction()), Arguments.of(new ClusterTransaction()));
    }

    @ParameterizedTest
    @MethodSource("getTransactionBuilders")
    public void transaction_builds_protobuf_request(BaseTransaction<?> transaction) {
        List<Pair<RequestType, ArgsArray>> results = new LinkedList<>();

        transaction.get("key");
        results.add(Pair.of(GetString, buildArgs("key")));

        transaction.set("key", "value");
        results.add(Pair.of(SetString, buildArgs("key", "value")));

        transaction.set("key", "value", SetOptions.builder().returnOldValue(true).build());
        results.add(Pair.of(SetString, buildArgs("key", "value", RETURN_OLD_VALUE)));

        transaction.del(new String[] {"key1", "key2"});
        results.add(Pair.of(Del, buildArgs("key1", "key2")));

        transaction.echo("GLIDE");
        results.add(Pair.of(Echo, buildArgs("GLIDE")));

        transaction.ping();
        results.add(Pair.of(Ping, buildArgs()));

        transaction.ping("KING PONG");
        results.add(Pair.of(Ping, buildArgs("KING PONG")));

        transaction.info();
        results.add(Pair.of(Info, buildArgs()));

        transaction.info(InfoOptions.builder().section(EVERYTHING).build());
        results.add(Pair.of(Info, buildArgs(EVERYTHING.toString())));

        transaction.mset(Map.of("key", "value"));
        results.add(Pair.of(MSet, buildArgs("key", "value")));

        transaction.mget(new String[] {"key"});
        results.add(Pair.of(MGet, buildArgs("key")));

        transaction.incr("key");
        results.add(Pair.of(Incr, buildArgs("key")));

        transaction.incrBy("key", 1);
        results.add(Pair.of(IncrBy, buildArgs("key", "1")));

        transaction.incrByFloat("key", 2.5);
        results.add(Pair.of(IncrByFloat, buildArgs("key", "2.5")));

        transaction.decr("key");
        results.add(Pair.of(Decr, buildArgs("key")));

        transaction.decrBy("key", 2);
        results.add(Pair.of(DecrBy, buildArgs("key", "2")));

        transaction.strlen("key");
        results.add(Pair.of(Strlen, buildArgs("key")));

        transaction.setrange("key", 42, "str");
        results.add(Pair.of(SetRange, buildArgs("key", "42", "str")));

        transaction.getrange("key", 42, 54);
        results.add(Pair.of(GetRange, buildArgs("key", "42", "54")));

        transaction.hset("key", Map.of("field", "value"));
        results.add(Pair.of(HashSet, buildArgs("key", "field", "value")));

        transaction.hsetnx("key", "field", "value");
        results.add(Pair.of(HSetNX, buildArgs("key", "field", "value")));

        transaction.hget("key", "field");
        results.add(Pair.of(HashGet, buildArgs("key", "field")));

        transaction.hdel("key", new String[] {"field"});
        results.add(Pair.of(HashDel, buildArgs("key", "field")));

        transaction.hlen("key");
        results.add(Pair.of(HLen, buildArgs("key")));

        transaction.hvals("key");
        results.add(Pair.of(Hvals, buildArgs("key")));

        transaction.hmget("key", new String[] {"field"});
        results.add(Pair.of(HashMGet, buildArgs("key", "field")));

        transaction.hexists("key", "field");
        results.add(Pair.of(HashExists, buildArgs("key", "field")));

        transaction.hgetall("key");
        results.add(Pair.of(HashGetAll, buildArgs("key")));

        transaction.hincrBy("key", "field", 1);
        results.add(Pair.of(HashIncrBy, buildArgs("key", "field", "1")));

        transaction.hincrByFloat("key", "field", 1.5);
        results.add(Pair.of(HashIncrByFloat, buildArgs("key", "field", "1.5")));

        transaction.hkeys("key");
        results.add(Pair.of(Hkeys, buildArgs("key")));

        transaction.lpush("key", new String[] {"element1", "element2"});
        results.add(Pair.of(LPush, buildArgs("key", "element1", "element2")));

        transaction.lpop("key");
        results.add(Pair.of(LPop, buildArgs("key")));

        transaction.lpopCount("key", 2);
        results.add(Pair.of(LPop, buildArgs("key", "2")));

        transaction.lrange("key", 1, 2);
        results.add(Pair.of(LRange, buildArgs("key", "1", "2")));

        transaction.lindex("key", 1);
        results.add(Pair.of(Lindex, ArgsArray.newBuilder().addArgs("key").addArgs("1").build()));

        transaction.ltrim("key", 1, 2);
        results.add(Pair.of(LTrim, buildArgs("key", "1", "2")));

        transaction.llen("key");
        results.add(Pair.of(LLen, buildArgs("key")));

        transaction.lrem("key", 1, "element");
        results.add(Pair.of(LRem, buildArgs("key", "1", "element")));

        transaction.rpush("key", new String[] {"element"});
        results.add(Pair.of(RPush, buildArgs("key", "element")));

        transaction.rpop("key");
        results.add(Pair.of(RPop, buildArgs("key")));

        transaction.rpopCount("key", 2);
        results.add(Pair.of(RPop, buildArgs("key", "2")));

        transaction.sadd("key", new String[] {"value"});
        results.add(Pair.of(SAdd, buildArgs("key", "value")));

        transaction.sismember("key", "member");
        results.add(
                Pair.of(SIsMember, ArgsArray.newBuilder().addArgs("key").addArgs("member").build()));

        transaction.srem("key", new String[] {"value"});
        results.add(Pair.of(SRem, buildArgs("key", "value")));

        transaction.smembers("key");
        results.add(Pair.of(SMembers, buildArgs("key")));

        transaction.scard("key");
        results.add(Pair.of(SCard, buildArgs("key")));

        transaction.smove("key1", "key2", "elem");
        results.add(Pair.of(SMove, buildArgs("key1", "key2", "elem")));

        transaction.sinter(new String[] {"key1", "key2"});
        results.add(Pair.of(SInter, buildArgs("key1", "key2")));

        transaction.sinterstore("key", new String[] {"set1", "set2"});
        results.add(Pair.of(SInterStore, buildArgs("key", "set1", "set2")));

        transaction.smismember("key", new String[] {"1", "2"});
        results.add(Pair.of(SMIsMember, buildArgs("key", "1", "2")));

        transaction.sunionstore("key", new String[] {"set1", "set2"});
        results.add(
                Pair.of(
                        SUnionStore,
                        ArgsArray.newBuilder().addArgs("key").addArgs("set1").addArgs("set2").build()));

        transaction.exists(new String[] {"key1", "key2"});
        results.add(Pair.of(Exists, buildArgs("key1", "key2")));

        transaction.unlink(new String[] {"key1", "key2"});
        results.add(Pair.of(Unlink, buildArgs("key1", "key2")));

        transaction.expire("key", 9L);
        results.add(Pair.of(Expire, buildArgs("key", "9")));
        transaction.expireAt("key", 9999L, NEW_EXPIRY_LESS_THAN_CURRENT);
        results.add(Pair.of(ExpireAt, buildArgs("key", "9999", "LT")));

        transaction.pexpire("key", 99999L);
        results.add(Pair.of(PExpire, buildArgs("key", "99999")));

        transaction.pexpire("key", 999999L, HAS_EXISTING_EXPIRY);
        results.add(Pair.of(PExpire, buildArgs("key", "999999", "XX")));

        transaction.pexpireAt("key", 9999999L);
        results.add(Pair.of(PExpireAt, buildArgs("key", "9999999")));

        transaction.pexpireAt("key", 99999999L, HAS_NO_EXPIRY);
        results.add(Pair.of(PExpireAt, buildArgs("key", "99999999", "NX")));

        transaction.ttl("key");
        results.add(Pair.of(TTL, buildArgs("key")));

        transaction.pttl("key");
        results.add(Pair.of(PTTL, buildArgs("key")));

        transaction.clientId();
        results.add(Pair.of(ClientId, buildArgs()));

        transaction.clientGetName();
        results.add(Pair.of(ClientGetName, buildArgs()));

        transaction.configRewrite();
        results.add(Pair.of(ConfigRewrite, buildArgs()));

        transaction.configResetStat();
        results.add(Pair.of(ConfigResetStat, buildArgs()));

        transaction.configGet(new String[] {"maxmemory", "hash-max-listpack-entries"});
        results.add(Pair.of(ConfigGet, buildArgs("maxmemory", "hash-max-listpack-entries")));

        var configSetMap = new LinkedHashMap<String, String>();
        configSetMap.put("maxmemory", "100mb");
        configSetMap.put("save", "60");

        transaction.configSet(configSetMap);
        results.add(Pair.of(ConfigSet, buildArgs("maxmemory", "100mb", "save", "60")));

        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("member1", 1.0);
        membersScores.put("member2", 2.0);
        transaction.zadd(
                "key",
                membersScores,
                ZaddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build(),
                true);
        results.add(Pair.of(Zadd, buildArgs("key", "LT", "CH", "1.0", "member1", "2.0", "member2")));

        transaction.zaddIncr(
                "key",
                "member1",
                3.0,
                ZaddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build());
        results.add(Pair.of(Zadd, buildArgs("key", "LT", "INCR", "3.0", "member1")));

        transaction.zrem("key", new String[] {"member1", "member2"});
        results.add(Pair.of(Zrem, buildArgs("key", "member1", "member2")));

        transaction.zcard("key");
        results.add(Pair.of(Zcard, buildArgs("key")));

        transaction.zpopmin("key");
        results.add(Pair.of(ZPopMin, buildArgs("key")));

        transaction.zpopmin("key", 2);
        results.add(Pair.of(ZPopMin, buildArgs("key", "2")));

        transaction.bzpopmin(new String[] {"key1", "key2"}, .5);
        results.add(Pair.of(BZPopMin, buildArgs("key1", "key2", "0.5")));

        transaction.zpopmax("key");
        results.add(Pair.of(ZPopMax, buildArgs("key")));

        transaction.bzpopmax(new String[] {"key1", "key2"}, .5);
        results.add(Pair.of(BZPopMax, buildArgs("key1", "key2", "0.5")));

        transaction.zpopmax("key", 2);
        results.add(Pair.of(ZPopMax, buildArgs("key", "2")));

        transaction.zscore("key", "member");
        results.add(Pair.of(ZScore, buildArgs("key", "member")));

        transaction.zrank("key", "member");
        results.add(Pair.of(Zrank, buildArgs("key", "member")));

        transaction.zrankWithScore("key", "member");
        results.add(Pair.of(Zrank, buildArgs("key", "member", WITH_SCORE_REDIS_API)));

        transaction.zrevrank("key", "member");
        results.add(Pair.of(ZRevRank, buildArgs("key", "member")));

        transaction.zrevrankWithScore("key", "member");
        results.add(Pair.of(ZRevRank, buildArgs("key", "member", WITH_SCORE_REDIS_API)));

        transaction.zmscore("key", new String[] {"member1", "member2"});
        results.add(Pair.of(ZMScore, buildArgs("key", "member1", "member2")));

        transaction.zdiff(new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiff, buildArgs("2", "key1", "key2")));

        transaction.zdiffWithScores(new String[] {"key1", "key2"});
        results.add(
                Pair.of(
                        ZDiff,
                        ArgsArray.newBuilder()
                                .addArgs("2")
                                .addArgs("key1")
                                .addArgs("key2")
                                .addArgs(WITH_SCORES_REDIS_API)
                                .build()));

        transaction.zdiffstore("destKey", new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiffStore, buildArgs("destKey", "2", "key1", "key2")));

        transaction.zcount("key", new ScoreBoundary(5, false), InfScoreBound.POSITIVE_INFINITY);
        results.add(Pair.of(Zcount, buildArgs("key", "(5.0", "+inf")));

        transaction.zremrangebyrank("key", 0, -1);
        results.add(Pair.of(ZRemRangeByRank, buildArgs("key", "0", "-1")));

        transaction.zremrangebylex("key", new LexBoundary("a", false), InfLexBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZRemRangeByLex, buildArgs("key", "(a", "+")));

        transaction.zremrangebyscore(
                "key", new ScoreBoundary(5, false), RangeOptions.InfScoreBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZRemRangeByScore, buildArgs("key", "(5.0", "+inf")));

        transaction.zlexcount("key", new LexBoundary("c", false), InfLexBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZLexCount, buildArgs("key", "(c", "+")));

        transaction.zrangestore(
                "destination",
                "source",
                new RangeByScore(
                        InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3, false), new Limit(1, 2)),
                true);
        results.add(
                Pair.of(
                        ZRangeStore,
                        buildArgs(
                                "destination", "source", "-inf", "(3.0", "BYSCORE", "REV", "LIMIT", "1", "2")));

        transaction.zrangestore("destination", "source", new RangeByIndex(2, 3));
        results.add(Pair.of(ZRangeStore, buildArgs("destination", "source", "2", "3")));

        transaction.zinterstore("destination", new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInterStore, buildArgs("destination", "2", "key1", "key2")));

        transaction.zunion(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnion, buildArgs("2", "key1", "key2")));

        transaction.zunionWithScores(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnion, buildArgs("2", "key1", "key2", WITH_SCORES_REDIS_API)));

        List<Pair<String, Double>> weightedKeys = new ArrayList<>();
        weightedKeys.add(Pair.of("key1", 10.0));
        weightedKeys.add(Pair.of("key2", 20.0));

        transaction.zinterstore("destination", new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZInterStore,
                        buildArgs(
                                "destination",
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString())));

        transaction.zunion(new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZUnion,
                        buildArgs(
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString())));

        transaction.zunionWithScores(new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZUnion,
                        buildArgs(
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_REDIS_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_REDIS_API,
                                Aggregate.MAX.toString(),
                                WITH_SCORES_REDIS_API)));

        transaction.xadd("key", Map.of("field1", "foo1"));
        results.add(Pair.of(XAdd, buildArgs("key", "*", "field1", "foo1")));

        transaction.xadd("key", Map.of("field1", "foo1"), StreamAddOptions.builder().id("id").build());
        results.add(Pair.of(XAdd, buildArgs("key", "id", "field1", "foo1")));

        transaction.xtrim("key", new MinId(true, "id"));
        results.add(Pair.of(XTrim, buildArgs("key", TRIM_MINID_REDIS_API, TRIM_EXACT_REDIS_API, "id")));

        transaction.time();
        results.add(Pair.of(Time, buildArgs()));

        transaction.lastsave();
        results.add(Pair.of(LastSave, buildArgs()));

        transaction.flushall().flushall(ASYNC);
        results.add(Pair.of(FlushAll, buildArgs()));
        results.add(Pair.of(FlushAll, buildArgs(ASYNC.toString())));

        transaction.lolwut().lolwut(5).lolwut(new int[] {1, 2}).lolwut(6, new int[] {42});
        results.add(Pair.of(LOLWUT, buildArgs()));
        results.add(Pair.of(LOLWUT, buildArgs(VERSION_REDIS_API, "5")));
        results.add(Pair.of(LOLWUT, buildArgs("1", "2")));
        results.add(Pair.of(LOLWUT, buildArgs(VERSION_REDIS_API, "6", "42")));

        transaction.persist("key");
        results.add(Pair.of(Persist, buildArgs("key")));

        transaction.type("key");
        results.add(Pair.of(Type, buildArgs("key")));

        transaction.renamenx("key", "newKey");
        results.add(Pair.of(RenameNx, buildArgs("key", "newKey")));

        transaction.linsert("key", AFTER, "pivot", "elem");
        results.add(Pair.of(LInsert, buildArgs("key", "AFTER", "pivot", "elem")));

        transaction.brpop(new String[] {"key1", "key2"}, 0.5);
        results.add(Pair.of(Brpop, buildArgs("key1", "key2", "0.5")));
        transaction.blpop(new String[] {"key1", "key2"}, 0.5);
        results.add(Pair.of(Blpop, buildArgs("key1", "key2", "0.5")));

        transaction.rpushx("key", new String[] {"element1", "element2"});
        results.add(Pair.of(RPushX, buildArgs("key", "element1", "element2")));

        transaction.lpushx("key", new String[] {"element1", "element2"});
        results.add(Pair.of(LPushX, buildArgs("key", "element1", "element2")));

        transaction.zrange(
                "key",
                new RangeByScore(NEGATIVE_INFINITY, new ScoreBoundary(3, false), new Limit(1, 2)),
                true);
        results.add(
                Pair.of(Zrange, buildArgs("key", "-inf", "(3.0", "BYSCORE", "REV", "LIMIT", "1", "2")));

        transaction.zrangeWithScores(
                "key",
                new RangeByScore(new ScoreBoundary(5, true), POSITIVE_INFINITY, new Limit(1, 2)),
                false);
        results.add(
                Pair.of(
                        Zrange,
                        buildArgs("key", "5.0", "+inf", "BYSCORE", "LIMIT", "1", "2", WITH_SCORES_REDIS_API)));

        transaction.pfadd("hll", new String[] {"a", "b", "c"});
        results.add(Pair.of(PfAdd, buildArgs("hll", "a", "b", "c")));

        transaction.pfcount(new String[] {"hll1", "hll2"});
        results.add(Pair.of(PfCount, ArgsArray.newBuilder().addArgs("hll1").addArgs("hll2").build()));
        transaction.pfmerge("hll", new String[] {"hll1", "hll2"});
        results.add(
                Pair.of(
                        PfMerge,
                        ArgsArray.newBuilder().addArgs("hll").addArgs("hll1").addArgs("hll2").build()));

        transaction.sdiff(new String[] {"key1", "key2"});
        results.add(Pair.of(SDiff, buildArgs("key1", "key2")));

        transaction.sdiffstore("key1", new String[] {"key2", "key3"});
        results.add(
                Pair.of(
                        SDiffStore,
                        ArgsArray.newBuilder().addArgs("key1").addArgs("key2").addArgs("key3").build()));

        transaction.objectEncoding("key");
        results.add(Pair.of(ObjectEncoding, buildArgs("key")));

        transaction.objectFreq("key");
        results.add(Pair.of(ObjectFreq, buildArgs("key")));

        transaction.objectIdletime("key");
        results.add(Pair.of(ObjectIdletime, buildArgs("key")));

        transaction.objectRefcount("key");
        results.add(Pair.of(ObjectRefcount, buildArgs("key")));

        transaction.touch(new String[] {"key1", "key2"});
        results.add(Pair.of(Touch, buildArgs("key1", "key2")));

        transaction.geoadd("key", Map.of("Place", new GeospatialData(10.0, 20.0)));
        results.add(Pair.of(GeoAdd, buildArgs("key", "10.0", "20.0", "Place")));

        transaction.geoadd(
                "key",
                Map.of("Place", new GeospatialData(10.0, 20.0)),
                new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true));
        results.add(
                Pair.of(
                        GeoAdd,
                        buildArgs(
                                "key",
                                ConditionalChange.ONLY_IF_EXISTS.getRedisApi(),
                                CHANGED_REDIS_API,
                                "10.0",
                                "20.0",
                                "Place")));
        transaction.geopos("key", new String[] {"Place"});
        results.add(Pair.of(GeoPos, buildArgs("key", "Place")));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }

    private ArgsArray buildArgs(String... args) {
        var builder = ArgsArray.newBuilder();
        for (var arg : args) {
            builder.addArgs(arg);
        }
        return builder.build();
    }
}
