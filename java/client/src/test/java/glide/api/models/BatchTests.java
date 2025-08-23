/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.Append;
import static command_request.CommandRequestOuterClass.RequestType.BLMPop;
import static command_request.CommandRequestOuterClass.RequestType.BLMove;
import static command_request.CommandRequestOuterClass.RequestType.BLPop;
import static command_request.CommandRequestOuterClass.RequestType.BRPop;
import static command_request.CommandRequestOuterClass.RequestType.BZMPop;
import static command_request.CommandRequestOuterClass.RequestType.BZPopMax;
import static command_request.CommandRequestOuterClass.RequestType.BZPopMin;
import static command_request.CommandRequestOuterClass.RequestType.BitCount;
import static command_request.CommandRequestOuterClass.RequestType.BitField;
import static command_request.CommandRequestOuterClass.RequestType.BitFieldReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.BitOp;
import static command_request.CommandRequestOuterClass.RequestType.BitPos;
import static command_request.CommandRequestOuterClass.RequestType.ClientGetName;
import static command_request.CommandRequestOuterClass.RequestType.ClientId;
import static command_request.CommandRequestOuterClass.RequestType.ConfigGet;
import static command_request.CommandRequestOuterClass.RequestType.ConfigResetStat;
import static command_request.CommandRequestOuterClass.RequestType.ConfigRewrite;
import static command_request.CommandRequestOuterClass.RequestType.ConfigSet;
import static command_request.CommandRequestOuterClass.RequestType.Copy;
import static command_request.CommandRequestOuterClass.RequestType.DBSize;
import static command_request.CommandRequestOuterClass.RequestType.Decr;
import static command_request.CommandRequestOuterClass.RequestType.DecrBy;
import static command_request.CommandRequestOuterClass.RequestType.Del;
import static command_request.CommandRequestOuterClass.RequestType.Dump;
import static command_request.CommandRequestOuterClass.RequestType.Echo;
import static command_request.CommandRequestOuterClass.RequestType.Exists;
import static command_request.CommandRequestOuterClass.RequestType.Expire;
import static command_request.CommandRequestOuterClass.RequestType.ExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.ExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.FCall;
import static command_request.CommandRequestOuterClass.RequestType.FCallReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.FlushAll;
import static command_request.CommandRequestOuterClass.RequestType.FlushDB;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDelete;
import static command_request.CommandRequestOuterClass.RequestType.FunctionDump;
import static command_request.CommandRequestOuterClass.RequestType.FunctionFlush;
import static command_request.CommandRequestOuterClass.RequestType.FunctionList;
import static command_request.CommandRequestOuterClass.RequestType.FunctionLoad;
import static command_request.CommandRequestOuterClass.RequestType.FunctionRestore;
import static command_request.CommandRequestOuterClass.RequestType.FunctionStats;
import static command_request.CommandRequestOuterClass.RequestType.GeoAdd;
import static command_request.CommandRequestOuterClass.RequestType.GeoDist;
import static command_request.CommandRequestOuterClass.RequestType.GeoHash;
import static command_request.CommandRequestOuterClass.RequestType.GeoPos;
import static command_request.CommandRequestOuterClass.RequestType.GeoSearch;
import static command_request.CommandRequestOuterClass.RequestType.GeoSearchStore;
import static command_request.CommandRequestOuterClass.RequestType.Get;
import static command_request.CommandRequestOuterClass.RequestType.GetBit;
import static command_request.CommandRequestOuterClass.RequestType.GetDel;
import static command_request.CommandRequestOuterClass.RequestType.GetEx;
import static command_request.CommandRequestOuterClass.RequestType.GetRange;
import static command_request.CommandRequestOuterClass.RequestType.HDel;
import static command_request.CommandRequestOuterClass.RequestType.HExists;
import static command_request.CommandRequestOuterClass.RequestType.HExpire;
import static command_request.CommandRequestOuterClass.RequestType.HExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.HExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.HGet;
import static command_request.CommandRequestOuterClass.RequestType.HGetAll;
import static command_request.CommandRequestOuterClass.RequestType.HGetEx;
import static command_request.CommandRequestOuterClass.RequestType.HIncrBy;
import static command_request.CommandRequestOuterClass.RequestType.HIncrByFloat;
import static command_request.CommandRequestOuterClass.RequestType.HKeys;
import static command_request.CommandRequestOuterClass.RequestType.HLen;
import static command_request.CommandRequestOuterClass.RequestType.HMGet;
import static command_request.CommandRequestOuterClass.RequestType.HPExpire;
import static command_request.CommandRequestOuterClass.RequestType.HPExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.HPExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.HPTtl;
import static command_request.CommandRequestOuterClass.RequestType.HPersist;
import static command_request.CommandRequestOuterClass.RequestType.HRandField;
import static command_request.CommandRequestOuterClass.RequestType.HScan;
import static command_request.CommandRequestOuterClass.RequestType.HSet;
import static command_request.CommandRequestOuterClass.RequestType.HSetEx;
import static command_request.CommandRequestOuterClass.RequestType.HSetNX;
import static command_request.CommandRequestOuterClass.RequestType.HStrlen;
import static command_request.CommandRequestOuterClass.RequestType.HTtl;
import static command_request.CommandRequestOuterClass.RequestType.HVals;
import static command_request.CommandRequestOuterClass.RequestType.Incr;
import static command_request.CommandRequestOuterClass.RequestType.IncrBy;
import static command_request.CommandRequestOuterClass.RequestType.IncrByFloat;
import static command_request.CommandRequestOuterClass.RequestType.Info;
import static command_request.CommandRequestOuterClass.RequestType.LCS;
import static command_request.CommandRequestOuterClass.RequestType.LIndex;
import static command_request.CommandRequestOuterClass.RequestType.LInsert;
import static command_request.CommandRequestOuterClass.RequestType.LLen;
import static command_request.CommandRequestOuterClass.RequestType.LMPop;
import static command_request.CommandRequestOuterClass.RequestType.LMove;
import static command_request.CommandRequestOuterClass.RequestType.LPop;
import static command_request.CommandRequestOuterClass.RequestType.LPos;
import static command_request.CommandRequestOuterClass.RequestType.LPush;
import static command_request.CommandRequestOuterClass.RequestType.LPushX;
import static command_request.CommandRequestOuterClass.RequestType.LRange;
import static command_request.CommandRequestOuterClass.RequestType.LRem;
import static command_request.CommandRequestOuterClass.RequestType.LSet;
import static command_request.CommandRequestOuterClass.RequestType.LTrim;
import static command_request.CommandRequestOuterClass.RequestType.LastSave;
import static command_request.CommandRequestOuterClass.RequestType.Lolwut;
import static command_request.CommandRequestOuterClass.RequestType.MGet;
import static command_request.CommandRequestOuterClass.RequestType.MSet;
import static command_request.CommandRequestOuterClass.RequestType.MSetNX;
import static command_request.CommandRequestOuterClass.RequestType.ObjectEncoding;
import static command_request.CommandRequestOuterClass.RequestType.ObjectFreq;
import static command_request.CommandRequestOuterClass.RequestType.ObjectIdleTime;
import static command_request.CommandRequestOuterClass.RequestType.ObjectRefCount;
import static command_request.CommandRequestOuterClass.RequestType.PExpire;
import static command_request.CommandRequestOuterClass.RequestType.PExpireAt;
import static command_request.CommandRequestOuterClass.RequestType.PExpireTime;
import static command_request.CommandRequestOuterClass.RequestType.PTTL;
import static command_request.CommandRequestOuterClass.RequestType.Persist;
import static command_request.CommandRequestOuterClass.RequestType.PfAdd;
import static command_request.CommandRequestOuterClass.RequestType.PfCount;
import static command_request.CommandRequestOuterClass.RequestType.PfMerge;
import static command_request.CommandRequestOuterClass.RequestType.Ping;
import static command_request.CommandRequestOuterClass.RequestType.PubSubChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubNumPat;
import static command_request.CommandRequestOuterClass.RequestType.PubSubNumSub;
import static command_request.CommandRequestOuterClass.RequestType.Publish;
import static command_request.CommandRequestOuterClass.RequestType.RPop;
import static command_request.CommandRequestOuterClass.RequestType.RPush;
import static command_request.CommandRequestOuterClass.RequestType.RPushX;
import static command_request.CommandRequestOuterClass.RequestType.RandomKey;
import static command_request.CommandRequestOuterClass.RequestType.Rename;
import static command_request.CommandRequestOuterClass.RequestType.RenameNX;
import static command_request.CommandRequestOuterClass.RequestType.Restore;
import static command_request.CommandRequestOuterClass.RequestType.SAdd;
import static command_request.CommandRequestOuterClass.RequestType.SCard;
import static command_request.CommandRequestOuterClass.RequestType.SDiff;
import static command_request.CommandRequestOuterClass.RequestType.SDiffStore;
import static command_request.CommandRequestOuterClass.RequestType.SInter;
import static command_request.CommandRequestOuterClass.RequestType.SInterCard;
import static command_request.CommandRequestOuterClass.RequestType.SInterStore;
import static command_request.CommandRequestOuterClass.RequestType.SIsMember;
import static command_request.CommandRequestOuterClass.RequestType.SMIsMember;
import static command_request.CommandRequestOuterClass.RequestType.SMembers;
import static command_request.CommandRequestOuterClass.RequestType.SMove;
import static command_request.CommandRequestOuterClass.RequestType.SPop;
import static command_request.CommandRequestOuterClass.RequestType.SRandMember;
import static command_request.CommandRequestOuterClass.RequestType.SRem;
import static command_request.CommandRequestOuterClass.RequestType.SScan;
import static command_request.CommandRequestOuterClass.RequestType.SUnion;
import static command_request.CommandRequestOuterClass.RequestType.SUnionStore;
import static command_request.CommandRequestOuterClass.RequestType.Set;
import static command_request.CommandRequestOuterClass.RequestType.SetBit;
import static command_request.CommandRequestOuterClass.RequestType.SetRange;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.Strlen;
import static command_request.CommandRequestOuterClass.RequestType.TTL;
import static command_request.CommandRequestOuterClass.RequestType.Time;
import static command_request.CommandRequestOuterClass.RequestType.Touch;
import static command_request.CommandRequestOuterClass.RequestType.Type;
import static command_request.CommandRequestOuterClass.RequestType.Unlink;
import static command_request.CommandRequestOuterClass.RequestType.Wait;
import static command_request.CommandRequestOuterClass.RequestType.XAck;
import static command_request.CommandRequestOuterClass.RequestType.XAdd;
import static command_request.CommandRequestOuterClass.RequestType.XAutoClaim;
import static command_request.CommandRequestOuterClass.RequestType.XClaim;
import static command_request.CommandRequestOuterClass.RequestType.XDel;
import static command_request.CommandRequestOuterClass.RequestType.XGroupCreate;
import static command_request.CommandRequestOuterClass.RequestType.XGroupCreateConsumer;
import static command_request.CommandRequestOuterClass.RequestType.XGroupDelConsumer;
import static command_request.CommandRequestOuterClass.RequestType.XGroupDestroy;
import static command_request.CommandRequestOuterClass.RequestType.XGroupSetId;
import static command_request.CommandRequestOuterClass.RequestType.XInfoConsumers;
import static command_request.CommandRequestOuterClass.RequestType.XInfoGroups;
import static command_request.CommandRequestOuterClass.RequestType.XInfoStream;
import static command_request.CommandRequestOuterClass.RequestType.XLen;
import static command_request.CommandRequestOuterClass.RequestType.XPending;
import static command_request.CommandRequestOuterClass.RequestType.XRange;
import static command_request.CommandRequestOuterClass.RequestType.XRead;
import static command_request.CommandRequestOuterClass.RequestType.XReadGroup;
import static command_request.CommandRequestOuterClass.RequestType.XRevRange;
import static command_request.CommandRequestOuterClass.RequestType.XTrim;
import static command_request.CommandRequestOuterClass.RequestType.ZAdd;
import static command_request.CommandRequestOuterClass.RequestType.ZCard;
import static command_request.CommandRequestOuterClass.RequestType.ZCount;
import static command_request.CommandRequestOuterClass.RequestType.ZDiff;
import static command_request.CommandRequestOuterClass.RequestType.ZDiffStore;
import static command_request.CommandRequestOuterClass.RequestType.ZIncrBy;
import static command_request.CommandRequestOuterClass.RequestType.ZInter;
import static command_request.CommandRequestOuterClass.RequestType.ZInterCard;
import static command_request.CommandRequestOuterClass.RequestType.ZInterStore;
import static command_request.CommandRequestOuterClass.RequestType.ZLexCount;
import static command_request.CommandRequestOuterClass.RequestType.ZMPop;
import static command_request.CommandRequestOuterClass.RequestType.ZMScore;
import static command_request.CommandRequestOuterClass.RequestType.ZPopMax;
import static command_request.CommandRequestOuterClass.RequestType.ZPopMin;
import static command_request.CommandRequestOuterClass.RequestType.ZRandMember;
import static command_request.CommandRequestOuterClass.RequestType.ZRange;
import static command_request.CommandRequestOuterClass.RequestType.ZRangeStore;
import static command_request.CommandRequestOuterClass.RequestType.ZRank;
import static command_request.CommandRequestOuterClass.RequestType.ZRem;
import static command_request.CommandRequestOuterClass.RequestType.ZRemRangeByLex;
import static command_request.CommandRequestOuterClass.RequestType.ZRemRangeByRank;
import static command_request.CommandRequestOuterClass.RequestType.ZRemRangeByScore;
import static command_request.CommandRequestOuterClass.RequestType.ZRevRank;
import static command_request.CommandRequestOuterClass.RequestType.ZScan;
import static command_request.CommandRequestOuterClass.RequestType.ZScore;
import static command_request.CommandRequestOuterClass.RequestType.ZUnion;
import static command_request.CommandRequestOuterClass.RequestType.ZUnionStore;
import static glide.api.commands.GenericBaseCommands.REPLACE_VALKEY_API;
import static glide.api.commands.HashBaseCommands.WITH_VALUES_VALKEY_API;
import static glide.api.commands.ServerManagementCommands.VERSION_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.COUNT_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.LIMIT_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_VALKEY_API;
import static glide.api.commands.StringBaseCommands.IDX_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.MINMATCHLEN_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.WITHMATCHLEN_COMMAND_STRING;
import static glide.api.models.commands.ExpireOptions.HAS_EXISTING_EXPIRY;
import static glide.api.models.commands.ExpireOptions.HAS_NO_EXPIRY;
import static glide.api.models.commands.ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.InfoOptions.Section.EVERYTHING;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;
import static glide.api.models.commands.RangeOptions.InfScoreBound.NEGATIVE_INFINITY;
import static glide.api.models.commands.RangeOptions.InfScoreBound.POSITIVE_INFINITY;
import static glide.api.models.commands.ScoreFilter.MAX;
import static glide.api.models.commands.ScoreFilter.MIN;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.WeightAggregateOptions.AGGREGATE_VALKEY_API;
import static glide.api.models.commands.WeightAggregateOptions.WEIGHTS_VALKEY_API;
import static glide.api.models.commands.ZAddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_VALKEY_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoAddOptions.CHANGED_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoSearchOrigin.FROMLONLAT_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoSearchOrigin.FROMMEMBER_VALKEY_API;
import static glide.api.models.commands.stream.StreamClaimOptions.FORCE_VALKEY_API;
import static glide.api.models.commands.stream.StreamClaimOptions.IDLE_VALKEY_API;
import static glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_VALKEY_API;
import static glide.api.models.commands.stream.StreamClaimOptions.RETRY_COUNT_VALKEY_API;
import static glide.api.models.commands.stream.StreamClaimOptions.TIME_VALKEY_API;
import static glide.api.models.commands.stream.StreamGroupOptions.ENTRIES_READ_VALKEY_API;
import static glide.api.models.commands.stream.StreamGroupOptions.MAKE_STREAM_VALKEY_API;
import static glide.api.models.commands.stream.StreamPendingOptions.IDLE_TIME_VALKEY_API;
import static glide.api.models.commands.stream.StreamRange.EXCLUSIVE_RANGE_VALKEY_API;
import static glide.api.models.commands.stream.StreamRange.MAXIMUM_RANGE_VALKEY_API;
import static glide.api.models.commands.stream.StreamRange.MINIMUM_RANGE_VALKEY_API;
import static glide.api.models.commands.stream.StreamRange.RANGE_COUNT_VALKEY_API;
import static glide.api.models.commands.stream.StreamReadGroupOptions.READ_GROUP_VALKEY_API;
import static glide.api.models.commands.stream.StreamReadGroupOptions.READ_NOACK_VALKEY_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_BLOCK_VALKEY_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_COUNT_VALKEY_API;
import static glide.api.models.commands.stream.StreamReadOptions.READ_STREAMS_VALKEY_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_EXACT_VALKEY_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_MINID_VALKEY_API;
import static glide.api.models.commands.stream.XInfoStreamOptions.COUNT;
import static glide.api.models.commands.stream.XInfoStreamOptions.FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import command_request.CommandRequestOuterClass.Command;
import command_request.CommandRequestOuterClass.Command.ArgsArray;
import command_request.CommandRequestOuterClass.RequestType;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.HashFieldExpirationOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.Limit;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.RestoreOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SortOrder;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.bitmap.BitFieldOptions;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldGet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSet;
import glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands;
import glide.api.models.commands.bitmap.BitFieldOptions.Offset;
import glide.api.models.commands.bitmap.BitFieldOptions.OffsetMultiplier;
import glide.api.models.commands.bitmap.BitFieldOptions.SignedEncoding;
import glide.api.models.commands.bitmap.BitFieldOptions.UnsignedEncoding;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeoSearchOptions;
import glide.api.models.commands.geospatial.GeoSearchOrigin;
import glide.api.models.commands.geospatial.GeoSearchResultOptions;
import glide.api.models.commands.geospatial.GeoSearchShape;
import glide.api.models.commands.geospatial.GeoSearchStoreOptions;
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.scan.SScanOptions;
import glide.api.models.commands.scan.ZScanOptions;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamClaimOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamRange.InfRangeBound;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
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

public class BatchTests {
    private static Stream<Arguments> getBatchBuilders() {
        return Stream.of(
                Arguments.of(new Batch(true)),
                Arguments.of(new Batch(false)),
                Arguments.of(new ClusterBatch(true)),
                Arguments.of(new ClusterBatch(false)));
    }

    @ParameterizedTest
    @MethodSource("getBatchBuilders")
    public void batch_builds_protobuf_request(BaseBatch<?> batch) {
        List<Pair<RequestType, ArgsArray>> results = new LinkedList<>();

        batch.get("key");
        results.add(Pair.of(Get, buildArgs("key")));

        batch.getex("key");
        results.add(Pair.of(GetEx, buildArgs("key")));

        batch.getex("key", GetExOptions.Seconds(10L));
        results.add(Pair.of(GetEx, buildArgs("key", "EX", "10")));

        batch.set("key", "value");
        results.add(Pair.of(Set, buildArgs("key", "value")));

        batch.set("key", "value", SetOptions.builder().returnOldValue(true).build());
        results.add(Pair.of(Set, buildArgs("key", "value", RETURN_OLD_VALUE)));

        batch.append("key", "value");
        results.add(Pair.of(Append, buildArgs("key", "value")));

        batch.del(new String[] {"key1", "key2"});
        results.add(Pair.of(Del, buildArgs("key1", "key2")));

        batch.echo("GLIDE");
        results.add(Pair.of(Echo, buildArgs("GLIDE")));

        batch.ping();
        results.add(Pair.of(Ping, buildArgs()));

        batch.ping("KING PONG");
        results.add(Pair.of(Ping, buildArgs("KING PONG")));

        batch.info();
        results.add(Pair.of(Info, buildArgs()));

        batch.info(new Section[] {EVERYTHING});
        results.add(Pair.of(Info, buildArgs(EVERYTHING.toString())));

        batch.mset(Map.of("key", "value"));
        results.add(Pair.of(MSet, buildArgs("key", "value")));

        batch.msetnx(Map.of("key", "value"));
        results.add(Pair.of(MSetNX, buildArgs("key", "value")));

        batch.mget(new String[] {"key"});
        results.add(Pair.of(MGet, buildArgs("key")));

        batch.incr("key");
        results.add(Pair.of(Incr, buildArgs("key")));

        batch.incrBy("key", 1);
        results.add(Pair.of(IncrBy, buildArgs("key", "1")));

        batch.incrByFloat("key", 2.5);
        results.add(Pair.of(IncrByFloat, buildArgs("key", "2.5")));

        batch.decr("key");
        results.add(Pair.of(Decr, buildArgs("key")));

        batch.decrBy("key", 2);
        results.add(Pair.of(DecrBy, buildArgs("key", "2")));

        batch.strlen("key");
        results.add(Pair.of(Strlen, buildArgs("key")));

        batch.setrange("key", 42, "str");
        results.add(Pair.of(SetRange, buildArgs("key", "42", "str")));

        batch.getrange("key", 42, 54);
        results.add(Pair.of(GetRange, buildArgs("key", "42", "54")));

        batch.hset("key", Map.of("field", "value"));
        results.add(Pair.of(HSet, buildArgs("key", "field", "value")));

        batch.hsetex(
                "key",
                Map.of("field", "value"),
                HashFieldExpirationOptions.builder()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(10L))
                        .build());
        results.add(Pair.of(HSetEx, buildArgs("key", "EX", "10", "FIELDS", "1", "field", "value")));

        Map<String, String> fieldValueMap = new LinkedHashMap<>();
        fieldValueMap.put("field1", "value1");
        fieldValueMap.put("field2", "value2");
        batch.hsetex(
                "key",
                fieldValueMap,
                HashFieldExpirationOptions.builder()
                        .fieldConditionalChange(
                                HashFieldExpirationOptions.FieldConditionalChange.ONLY_IF_ALL_EXIST)
                        .expiry(HashFieldExpirationOptions.ExpirySet.Milliseconds(5000L))
                        .build());
        results.add(
                Pair.of(
                        HSetEx,
                        buildArgs(
                                "key", "FXX", "PX", "5000", "FIELDS", "2", "field1", "value1", "field2",
                                "value2")));

        batch.hsetnx("key", "field", "value");
        results.add(Pair.of(HSetNX, buildArgs("key", "field", "value")));

        batch.hget("key", "field");
        results.add(Pair.of(HGet, buildArgs("key", "field")));

        batch.hdel("key", new String[] {"field"});
        results.add(Pair.of(HDel, buildArgs("key", "field")));

        batch.hlen("key");
        results.add(Pair.of(HLen, buildArgs("key")));

        batch.hvals("key");
        results.add(Pair.of(HVals, buildArgs("key")));

        batch.hmget("key", new String[] {"field"});
        results.add(Pair.of(HMGet, buildArgs("key", "field")));

        batch.hexists("key", "field");
        results.add(Pair.of(HExists, buildArgs("key", "field")));

        batch.hgetall("key");
        results.add(Pair.of(HGetAll, buildArgs("key")));

        batch.hincrBy("key", "field", 1);
        results.add(Pair.of(HIncrBy, buildArgs("key", "field", "1")));

        batch.hincrByFloat("key", "field", 1.5);
        results.add(Pair.of(HIncrByFloat, buildArgs("key", "field", "1.5")));

        batch.hkeys("key");
        results.add(Pair.of(HKeys, buildArgs("key")));

        batch.hstrlen("key", "field");
        results.add(Pair.of(HStrlen, buildArgs("key", "field")));

        batch.hrandfield("key").hrandfieldWithCount("key", 2).hrandfieldWithCountWithValues("key", 3);
        results.add(Pair.of(HRandField, buildArgs("key")));
        results.add(Pair.of(HRandField, buildArgs("key", "2")));
        results.add(Pair.of(HRandField, buildArgs("key", "3", WITH_VALUES_VALKEY_API)));

        // Hash field expiration commands
        HashFieldExpirationOptions expiryOptions =
                HashFieldExpirationOptions.builder()
                        .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                        .build();

        batch.hgetex("key", new String[] {"field1", "field2"}, expiryOptions);
        results.add(Pair.of(HGetEx, buildArgs("key", "EX", "60", "FIELDS", "2", "field1", "field2")));

        batch.hexpire(
                "key",
                60L,
                new String[] {"field1", "field2"},
                HashFieldExpirationOptions.builder().build());
        results.add(Pair.of(HExpire, buildArgs("key", "60", "FIELDS", "2", "field1", "field2")));

        batch.hpersist("key", new String[] {"field1", "field2"});
        results.add(Pair.of(HPersist, buildArgs("key", "FIELDS", "2", "field1", "field2")));

        batch.hpexpire(
                "key",
                60000L,
                new String[] {"field1", "field2"},
                HashFieldExpirationOptions.builder().build());
        results.add(Pair.of(HPExpire, buildArgs("key", "60000", "FIELDS", "2", "field1", "field2")));

        batch.hexpireat(
                "key",
                1234567890L,
                new String[] {"field1", "field2"},
                HashFieldExpirationOptions.builder().build());
        results.add(
                Pair.of(HExpireAt, buildArgs("key", "1234567890", "FIELDS", "2", "field1", "field2")));

        batch.hpexpireat(
                "key",
                1234567890000L,
                new String[] {"field1", "field2"},
                HashFieldExpirationOptions.builder().build());
        results.add(
                Pair.of(HPExpireAt, buildArgs("key", "1234567890000", "FIELDS", "2", "field1", "field2")));

        batch.httl("key", new String[] {"field1", "field2"});
        results.add(Pair.of(HTtl, buildArgs("key", "FIELDS", "2", "field1", "field2")));

        batch.hpttl("key", new String[] {"field1", "field2"});
        results.add(Pair.of(HPTtl, buildArgs("key", "FIELDS", "2", "field1", "field2")));

        batch.hexpiretime("key", new String[] {"field1", "field2"});
        results.add(Pair.of(HExpireTime, buildArgs("key", "FIELDS", "2", "field1", "field2")));

        batch.hpexpiretime("key", new String[] {"field1", "field2"});
        results.add(Pair.of(HPExpireTime, buildArgs("key", "FIELDS", "2", "field1", "field2")));

        batch.lpush("key", new String[] {"element1", "element2"});
        results.add(Pair.of(LPush, buildArgs("key", "element1", "element2")));

        batch.lpos("key", "element1");
        results.add(Pair.of(LPos, buildArgs("key", "element1")));

        batch.lpos("key", "element1", LPosOptions.builder().rank(1L).build());
        results.add(Pair.of(LPos, buildArgs("key", "element1", "RANK", "1")));

        batch.lposCount("key", "element1", 1L);
        results.add(Pair.of(LPos, buildArgs("key", "element1", "COUNT", "1")));

        batch.lposCount("key", "element1", 1L, LPosOptions.builder().rank(1L).build());
        results.add(Pair.of(LPos, buildArgs("key", "element1", "COUNT", "1", "RANK", "1")));

        batch.lpop("key");
        results.add(Pair.of(LPop, buildArgs("key")));

        batch.lpopCount("key", 2);
        results.add(Pair.of(LPop, buildArgs("key", "2")));

        batch.lrange("key", 1, 2);
        results.add(Pair.of(LRange, buildArgs("key", "1", "2")));

        batch.lindex("key", 1);
        results.add(Pair.of(LIndex, buildArgs("key", "1")));

        batch.ltrim("key", 1, 2);
        results.add(Pair.of(LTrim, buildArgs("key", "1", "2")));

        batch.llen("key");
        results.add(Pair.of(LLen, buildArgs("key")));

        batch.lrem("key", 1, "element");
        results.add(Pair.of(LRem, buildArgs("key", "1", "element")));

        batch.rpush("key", new String[] {"element"});
        results.add(Pair.of(RPush, buildArgs("key", "element")));

        batch.rpop("key");
        results.add(Pair.of(RPop, buildArgs("key")));

        batch.rpopCount("key", 2);
        results.add(Pair.of(RPop, buildArgs("key", "2")));

        batch.sadd("key", new String[] {"value"});
        results.add(Pair.of(SAdd, buildArgs("key", "value")));

        batch.sismember("key", "member");
        results.add(Pair.of(SIsMember, buildArgs("key", "member")));

        batch.srem("key", new String[] {"value"});
        results.add(Pair.of(SRem, buildArgs("key", "value")));

        batch.smembers("key");
        results.add(Pair.of(SMembers, buildArgs("key")));

        batch.scard("key");
        results.add(Pair.of(SCard, buildArgs("key")));

        batch.smove("key1", "key2", "elem");
        results.add(Pair.of(SMove, buildArgs("key1", "key2", "elem")));

        batch.sinter(new String[] {"key1", "key2"});
        results.add(Pair.of(SInter, buildArgs("key1", "key2")));

        batch.sinterstore("key", new String[] {"set1", "set2"});
        results.add(Pair.of(SInterStore, buildArgs("key", "set1", "set2")));

        batch.smismember("key", new String[] {"1", "2"});
        results.add(Pair.of(SMIsMember, buildArgs("key", "1", "2")));

        batch.sunionstore("key", new String[] {"set1", "set2"});
        results.add(Pair.of(SUnionStore, buildArgs("key", "set1", "set2")));

        batch.exists(new String[] {"key1", "key2"});
        results.add(Pair.of(Exists, buildArgs("key1", "key2")));

        batch.unlink(new String[] {"key1", "key2"});
        results.add(Pair.of(Unlink, buildArgs("key1", "key2")));

        batch.expire("key", 9L);
        results.add(Pair.of(Expire, buildArgs("key", "9")));
        batch.expireAt("key", 9999L, NEW_EXPIRY_LESS_THAN_CURRENT);
        results.add(Pair.of(ExpireAt, buildArgs("key", "9999", "LT")));

        batch.pexpire("key", 99999L);
        results.add(Pair.of(PExpire, buildArgs("key", "99999")));

        batch.pexpire("key", 999999L, HAS_EXISTING_EXPIRY);
        results.add(Pair.of(PExpire, buildArgs("key", "999999", "XX")));

        batch.pexpireAt("key", 9999999L);
        results.add(Pair.of(PExpireAt, buildArgs("key", "9999999")));

        batch.pexpireAt("key", 99999999L, HAS_NO_EXPIRY);
        results.add(Pair.of(PExpireAt, buildArgs("key", "99999999", "NX")));

        batch.getdel("key");
        results.add(Pair.of(GetDel, buildArgs("key")));

        batch.ttl("key");
        results.add(Pair.of(TTL, buildArgs("key")));

        batch.pttl("key");
        results.add(Pair.of(PTTL, buildArgs("key")));

        batch.expiretime("key");
        results.add(Pair.of(ExpireTime, buildArgs("key")));

        batch.pexpiretime("key");
        results.add(Pair.of(PExpireTime, buildArgs("key")));

        batch.clientId();
        results.add(Pair.of(ClientId, buildArgs()));

        batch.clientGetName();
        results.add(Pair.of(ClientGetName, buildArgs()));

        batch.configRewrite();
        results.add(Pair.of(ConfigRewrite, buildArgs()));

        batch.configResetStat();
        results.add(Pair.of(ConfigResetStat, buildArgs()));

        batch.configGet(new String[] {"maxmemory", "hash-max-listpack-entries"});
        results.add(Pair.of(ConfigGet, buildArgs("maxmemory", "hash-max-listpack-entries")));

        var configSetMap = new LinkedHashMap<String, String>();
        configSetMap.put("maxmemory", "100mb");
        configSetMap.put("save", "60");

        batch.configSet(configSetMap);
        results.add(Pair.of(ConfigSet, buildArgs("maxmemory", "100mb", "save", "60")));

        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("member1", 1.0);
        membersScores.put("member2", 2.0);
        batch.zadd(
                "key",
                membersScores,
                ZAddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build(),
                true);
        results.add(Pair.of(ZAdd, buildArgs("key", "LT", "CH", "1.0", "member1", "2.0", "member2")));

        batch.zaddIncr(
                "key",
                "member1",
                3.0,
                ZAddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build());
        results.add(Pair.of(ZAdd, buildArgs("key", "LT", "INCR", "3.0", "member1")));

        batch.zrem("key", new String[] {"member1", "member2"});
        results.add(Pair.of(ZRem, buildArgs("key", "member1", "member2")));

        batch.zcard("key");
        results.add(Pair.of(ZCard, buildArgs("key")));

        batch.zpopmin("key");
        results.add(Pair.of(ZPopMin, buildArgs("key")));

        batch.zpopmin("key", 2);
        results.add(Pair.of(ZPopMin, buildArgs("key", "2")));

        batch.bzpopmin(new String[] {"key1", "key2"}, .5);
        results.add(Pair.of(BZPopMin, buildArgs("key1", "key2", "0.5")));

        batch.zpopmax("key");
        results.add(Pair.of(ZPopMax, buildArgs("key")));

        batch.bzpopmax(new String[] {"key1", "key2"}, .5);
        results.add(Pair.of(BZPopMax, buildArgs("key1", "key2", "0.5")));

        batch.zpopmax("key", 2);
        results.add(Pair.of(ZPopMax, buildArgs("key", "2")));

        batch.zscore("key", "member");
        results.add(Pair.of(ZScore, buildArgs("key", "member")));

        batch.zrank("key", "member");
        results.add(Pair.of(ZRank, buildArgs("key", "member")));

        batch.zrankWithScore("key", "member");
        results.add(Pair.of(ZRank, buildArgs("key", "member", WITH_SCORE_VALKEY_API)));

        batch.zrevrank("key", "member");
        results.add(Pair.of(ZRevRank, buildArgs("key", "member")));

        batch.zrevrankWithScore("key", "member");
        results.add(Pair.of(ZRevRank, buildArgs("key", "member", WITH_SCORE_VALKEY_API)));

        batch.zmscore("key", new String[] {"member1", "member2"});
        results.add(Pair.of(ZMScore, buildArgs("key", "member1", "member2")));

        batch.zdiff(new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiff, buildArgs("2", "key1", "key2")));

        batch.zdiffWithScores(new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiff, buildArgs("2", "key1", "key2", WITH_SCORES_VALKEY_API)));

        batch.zdiffstore("destKey", new String[] {"key1", "key2"});
        results.add(Pair.of(ZDiffStore, buildArgs("destKey", "2", "key1", "key2")));

        batch.zmpop(new String[] {"key1", "key2"}, MAX).zmpop(new String[] {"key"}, MIN, 42);
        results.add(Pair.of(ZMPop, buildArgs("2", "key1", "key2", "MAX")));
        results.add(Pair.of(ZMPop, buildArgs("1", "key", "MIN", "COUNT", "42")));

        batch.bzmpop(new String[] {"key1", "key2"}, MAX, .1).bzmpop(new String[] {"key"}, MIN, .1, 42);
        results.add(Pair.of(BZMPop, buildArgs("0.1", "2", "key1", "key2", "MAX")));
        results.add(Pair.of(BZMPop, buildArgs("0.1", "1", "key", "MIN", "COUNT", "42")));

        batch.zcount("key", new ScoreBoundary(5, false), InfScoreBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZCount, buildArgs("key", "(5.0", "+inf")));

        batch.zremrangebyrank("key", 0, -1);
        results.add(Pair.of(ZRemRangeByRank, buildArgs("key", "0", "-1")));

        batch.zremrangebylex("key", new LexBoundary("a", false), InfLexBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZRemRangeByLex, buildArgs("key", "(a", "+")));

        batch.zremrangebyscore(
                "key", new ScoreBoundary(5, false), RangeOptions.InfScoreBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZRemRangeByScore, buildArgs("key", "(5.0", "+inf")));

        batch.zlexcount("key", new LexBoundary("c", false), InfLexBound.POSITIVE_INFINITY);
        results.add(Pair.of(ZLexCount, buildArgs("key", "(c", "+")));

        batch.zrangestore(
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

        batch.zrangestore("destination", "source", new RangeByIndex(2, 3));
        results.add(Pair.of(ZRangeStore, buildArgs("destination", "source", "2", "3")));

        batch.zinterstore("destination", new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInterStore, buildArgs("destination", "2", "key1", "key2")));

        batch.zunionstore("destination", new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnionStore, buildArgs("destination", "2", "key1", "key2")));

        batch.zunion(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnion, buildArgs("2", "key1", "key2")));

        batch.zunionWithScores(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZUnion, buildArgs("2", "key1", "key2", WITH_SCORES_VALKEY_API)));

        List<Pair<String, Double>> weightedKeys = new ArrayList<>();
        weightedKeys.add(Pair.of("key1", 10.0));
        weightedKeys.add(Pair.of("key2", 20.0));

        batch.zinterstore("destination", new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZInterStore,
                        buildArgs(
                                "destination",
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_VALKEY_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_VALKEY_API,
                                Aggregate.MAX.toString())));

        batch.zunionstore("destination", new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZUnionStore,
                        buildArgs(
                                "destination",
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_VALKEY_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_VALKEY_API,
                                Aggregate.MAX.toString())));

        batch.zunionWithScores(new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZUnion,
                        buildArgs(
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_VALKEY_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_VALKEY_API,
                                Aggregate.MAX.toString(),
                                WITH_SCORES_VALKEY_API)));
        batch.zintercard(new String[] {"key1", "key2"}, 5);
        results.add(Pair.of(ZInterCard, buildArgs("2", "key1", "key2", LIMIT_VALKEY_API, "5")));

        batch.zintercard(new String[] {"key1", "key2"});
        results.add(Pair.of(ZInterCard, buildArgs("2", "key1", "key2")));

        batch.zinter(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInter, buildArgs("2", "key1", "key2")));

        batch.zinterWithScores(new KeyArray(new String[] {"key1", "key2"}));
        results.add(Pair.of(ZInter, buildArgs("2", "key1", "key2", WITH_SCORES_VALKEY_API)));

        batch.zinterWithScores(new WeightedKeys(weightedKeys), Aggregate.MAX);
        results.add(
                Pair.of(
                        ZInter,
                        buildArgs(
                                "2",
                                "key1",
                                "key2",
                                WEIGHTS_VALKEY_API,
                                "10.0",
                                "20.0",
                                AGGREGATE_VALKEY_API,
                                Aggregate.MAX.toString(),
                                WITH_SCORES_VALKEY_API)));

        batch.xadd("key", Map.of("field1", "foo1"));
        results.add(Pair.of(XAdd, buildArgs("key", "*", "field1", "foo1")));

        batch.xadd("key", Map.of("field1", "foo1"), StreamAddOptions.builder().id("id").build());
        results.add(Pair.of(XAdd, buildArgs("key", "id", "field1", "foo1")));

        batch.xadd("key", new String[][] {new String[] {"field1", "foo1"}});
        results.add(Pair.of(XAdd, buildArgs("key", "*", "field1", "foo1")));

        batch.xadd(
                "key",
                new String[][] {new String[] {"field1", "foo1"}},
                StreamAddOptions.builder().id("id").build());
        results.add(Pair.of(XAdd, buildArgs("key", "id", "field1", "foo1")));

        batch.xtrim("key", new MinId(true, "id"));
        results.add(
                Pair.of(XTrim, buildArgs("key", TRIM_MINID_VALKEY_API, TRIM_EXACT_VALKEY_API, "id")));

        batch.xread(Map.of("key", "id"));
        results.add(Pair.of(XRead, buildArgs(READ_STREAMS_VALKEY_API, "key", "id")));

        batch.xread(Map.of("key", "id"), StreamReadOptions.builder().block(1L).count(2L).build());
        results.add(
                Pair.of(
                        XRead,
                        buildArgs(
                                READ_COUNT_VALKEY_API,
                                "2",
                                READ_BLOCK_VALKEY_API,
                                "1",
                                READ_STREAMS_VALKEY_API,
                                "key",
                                "id")));

        batch.xlen("key");
        results.add(Pair.of(XLen, buildArgs("key")));

        batch.xdel("key", new String[] {"12345-1", "98765-4"});
        results.add(Pair.of(XDel, buildArgs("key", "12345-1", "98765-4")));

        batch.xrange("key", InfRangeBound.MIN, InfRangeBound.MAX);
        results.add(
                Pair.of(XRange, buildArgs("key", MINIMUM_RANGE_VALKEY_API, MAXIMUM_RANGE_VALKEY_API)));

        batch.xrange("key", InfRangeBound.MIN, InfRangeBound.MAX, 99L);
        results.add(
                Pair.of(
                        XRange,
                        buildArgs(
                                "key",
                                MINIMUM_RANGE_VALKEY_API,
                                MAXIMUM_RANGE_VALKEY_API,
                                RANGE_COUNT_VALKEY_API,
                                "99")));

        batch.xrevrange("key", InfRangeBound.MAX, InfRangeBound.MIN);
        results.add(
                Pair.of(XRevRange, buildArgs("key", MAXIMUM_RANGE_VALKEY_API, MINIMUM_RANGE_VALKEY_API)));

        batch.xrevrange("key", InfRangeBound.MAX, InfRangeBound.MIN, 99L);
        results.add(
                Pair.of(
                        XRevRange,
                        buildArgs(
                                "key",
                                MAXIMUM_RANGE_VALKEY_API,
                                MINIMUM_RANGE_VALKEY_API,
                                RANGE_COUNT_VALKEY_API,
                                "99")));

        batch.xgroupCreate("key", "group", "id");
        results.add(Pair.of(XGroupCreate, buildArgs("key", "group", "id")));

        batch.xgroupCreate(
                "key", "group", "id", StreamGroupOptions.builder().makeStream().entriesRead(123L).build());
        results.add(
                Pair.of(
                        XGroupCreate,
                        buildArgs(
                                "key", "group", "id", MAKE_STREAM_VALKEY_API, ENTRIES_READ_VALKEY_API, "123")));

        batch.xgroupDestroy("key", "group");
        results.add(Pair.of(XGroupDestroy, buildArgs("key", "group")));

        batch.xgroupCreateConsumer("key", "group", "consumer");
        results.add(Pair.of(XGroupCreateConsumer, buildArgs("key", "group", "consumer")));

        batch.xgroupDelConsumer("key", "group", "consumer");
        results.add(Pair.of(XGroupDelConsumer, buildArgs("key", "group", "consumer")));

        batch.xreadgroup(Map.of("key", "id"), "group", "consumer");
        results.add(
                Pair.of(
                        XReadGroup,
                        buildArgs(
                                READ_GROUP_VALKEY_API, "group", "consumer", READ_STREAMS_VALKEY_API, "key", "id")));

        batch.xgroupSetId("key", "group", "id");
        results.add(Pair.of(XGroupSetId, buildArgs("key", "group", "id")));

        batch.xgroupSetId("key", "group", "id", 1);
        results.add(Pair.of(XGroupSetId, buildArgs("key", "group", "id", "ENTRIESREAD", "1")));

        batch.xreadgroup(
                Map.of("key", "id"),
                "group",
                "consumer",
                StreamReadGroupOptions.builder().block(1L).count(2L).noack().build());
        results.add(
                Pair.of(
                        XReadGroup,
                        buildArgs(
                                READ_GROUP_VALKEY_API,
                                "group",
                                "consumer",
                                READ_COUNT_VALKEY_API,
                                "2",
                                READ_BLOCK_VALKEY_API,
                                "1",
                                READ_NOACK_VALKEY_API,
                                READ_STREAMS_VALKEY_API,
                                "key",
                                "id")));

        batch.xack("key", "group", new String[] {"12345-1", "98765-4"});
        results.add(Pair.of(XAck, buildArgs("key", "group", "12345-1", "98765-4")));

        batch.xpending("key", "group");
        results.add(Pair.of(XPending, buildArgs("key", "group")));

        batch.xpending("key", "group", InfRangeBound.MAX, InfRangeBound.MIN, 99L);
        results.add(
                Pair.of(
                        XPending,
                        buildArgs("key", "group", MAXIMUM_RANGE_VALKEY_API, MINIMUM_RANGE_VALKEY_API, "99")));

        batch.xpending(
                "key",
                "group",
                StreamRange.IdBound.ofExclusive("11"),
                StreamRange.IdBound.ofExclusive("1234-0"),
                99L,
                StreamPendingOptions.builder().minIdleTime(5L).consumer("consumer").build());
        results.add(
                Pair.of(
                        XPending,
                        buildArgs(
                                "key",
                                "group",
                                IDLE_TIME_VALKEY_API,
                                "5",
                                EXCLUSIVE_RANGE_VALKEY_API + "11",
                                EXCLUSIVE_RANGE_VALKEY_API + "1234-0",
                                "99",
                                "consumer")));

        batch.xinfoStream("key").xinfoStreamFull("key").xinfoStreamFull("key", 42);
        results.add(Pair.of(XInfoStream, buildArgs("key")));
        results.add(Pair.of(XInfoStream, buildArgs("key", FULL)));
        results.add(Pair.of(XInfoStream, buildArgs("key", FULL, COUNT, "42")));

        batch.xclaim("key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"});
        results.add(Pair.of(XClaim, buildArgs("key", "group", "consumer", "99", "12345-1", "98765-4")));

        StreamClaimOptions claimOptions =
                StreamClaimOptions.builder().force().idle(11L).idleUnixTime(12L).retryCount(5L).build();
        batch.xclaim(
                "key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"}, claimOptions);
        results.add(
                Pair.of(
                        XClaim,
                        buildArgs(
                                "key",
                                "group",
                                "consumer",
                                "99",
                                "12345-1",
                                "98765-4",
                                IDLE_VALKEY_API,
                                "11",
                                TIME_VALKEY_API,
                                "12",
                                RETRY_COUNT_VALKEY_API,
                                "5",
                                FORCE_VALKEY_API)));

        batch.xclaimJustId("key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"});
        results.add(
                Pair.of(
                        XClaim,
                        buildArgs("key", "group", "consumer", "99", "12345-1", "98765-4", JUST_ID_VALKEY_API)));

        batch.xclaimJustId(
                "key", "group", "consumer", 99L, new String[] {"12345-1", "98765-4"}, claimOptions);
        results.add(
                Pair.of(
                        XClaim,
                        buildArgs(
                                "key",
                                "group",
                                "consumer",
                                "99",
                                "12345-1",
                                "98765-4",
                                IDLE_VALKEY_API,
                                "11",
                                TIME_VALKEY_API,
                                "12",
                                RETRY_COUNT_VALKEY_API,
                                "5",
                                FORCE_VALKEY_API,
                                JUST_ID_VALKEY_API)));

        batch.xinfoGroups("key");
        results.add(Pair.of(XInfoGroups, buildArgs("key")));

        batch.xinfoConsumers("key", "groupName");
        results.add(Pair.of(XInfoConsumers, buildArgs("key", "groupName")));

        batch.xautoclaim("key", "group", "consumer", 99L, "0-0");
        results.add(Pair.of(XAutoClaim, buildArgs("key", "group", "consumer", "99", "0-0")));

        batch.xautoclaim("key", "group", "consumer", 99L, "0-0", 1234L);
        results.add(
                Pair.of(XAutoClaim, buildArgs("key", "group", "consumer", "99", "0-0", "COUNT", "1234")));

        batch.xautoclaimJustId("key", "group", "consumer", 99L, "0-0");
        results.add(Pair.of(XAutoClaim, buildArgs("key", "group", "consumer", "99", "0-0", "JUSTID")));

        batch.xautoclaimJustId("key", "group", "consumer", 99L, "0-0", 1234L);
        results.add(
                Pair.of(
                        XAutoClaim,
                        buildArgs("key", "group", "consumer", "99", "0-0", "COUNT", "1234", "JUSTID")));

        batch.time();
        results.add(Pair.of(Time, buildArgs()));

        batch.lastsave();
        results.add(Pair.of(LastSave, buildArgs()));

        batch.flushall().flushall(ASYNC);
        results.add(Pair.of(FlushAll, buildArgs()));
        results.add(Pair.of(FlushAll, buildArgs(ASYNC.toString())));

        batch.flushdb().flushdb(ASYNC);
        results.add(Pair.of(FlushDB, buildArgs()));
        results.add(Pair.of(FlushDB, buildArgs(ASYNC.toString())));

        batch.lolwut().lolwut(5).lolwut(new int[] {1, 2}).lolwut(6, new int[] {42});
        results.add(Pair.of(Lolwut, buildArgs()));
        results.add(Pair.of(Lolwut, buildArgs(VERSION_VALKEY_API, "5")));
        results.add(Pair.of(Lolwut, buildArgs("1", "2")));
        results.add(Pair.of(Lolwut, buildArgs(VERSION_VALKEY_API, "6", "42")));

        batch.dbsize();
        results.add(Pair.of(DBSize, buildArgs()));

        batch.persist("key");
        results.add(Pair.of(Persist, buildArgs("key")));

        batch.zrandmember("key");
        results.add(Pair.of(ZRandMember, buildArgs("key")));

        batch.zrandmemberWithCount("key", 5);
        results.add(Pair.of(ZRandMember, buildArgs("key", "5")));

        batch.zrandmemberWithCountWithScores("key", 5);
        results.add(Pair.of(ZRandMember, buildArgs("key", "5", WITH_SCORES_VALKEY_API)));

        batch.zincrby("key", 3.14, "value");
        results.add(Pair.of(ZIncrBy, buildArgs("key", "3.14", "value")));

        batch.type("key");
        results.add(Pair.of(Type, buildArgs("key")));

        batch.randomKey();
        results.add(Pair.of(RandomKey, buildArgs()));

        batch.rename("key", "newKey");
        results.add(Pair.of(Rename, buildArgs("key", "newKey")));

        batch.renamenx("key", "newKey");
        results.add(Pair.of(RenameNX, buildArgs("key", "newKey")));

        batch.linsert("key", AFTER, "pivot", "elem");
        results.add(Pair.of(LInsert, buildArgs("key", "AFTER", "pivot", "elem")));

        batch.brpop(new String[] {"key1", "key2"}, 0.5);
        results.add(Pair.of(BRPop, buildArgs("key1", "key2", "0.5")));
        batch.blpop(new String[] {"key1", "key2"}, 0.5);
        results.add(Pair.of(BLPop, buildArgs("key1", "key2", "0.5")));

        batch.rpushx("key", new String[] {"element1", "element2"});
        results.add(Pair.of(RPushX, buildArgs("key", "element1", "element2")));

        batch.lpushx("key", new String[] {"element1", "element2"});
        results.add(Pair.of(LPushX, buildArgs("key", "element1", "element2")));

        batch.zrange(
                "key",
                new RangeByScore(NEGATIVE_INFINITY, new ScoreBoundary(3, false), new Limit(1, 2)),
                true);
        results.add(
                Pair.of(ZRange, buildArgs("key", "-inf", "(3.0", "BYSCORE", "REV", "LIMIT", "1", "2")));

        batch.zrangeWithScores(
                "key",
                new RangeByScore(new ScoreBoundary(5, true), POSITIVE_INFINITY, new Limit(1, 2)),
                false);
        results.add(
                Pair.of(
                        ZRange,
                        buildArgs("key", "5.0", "+inf", "BYSCORE", "LIMIT", "1", "2", WITH_SCORES_VALKEY_API)));

        batch.pfadd("hll", new String[] {"a", "b", "c"});
        results.add(Pair.of(PfAdd, buildArgs("hll", "a", "b", "c")));

        batch.pfcount(new String[] {"hll1", "hll2"});
        results.add(Pair.of(PfCount, buildArgs("hll1", "hll2")));
        batch.pfmerge("hll", new String[] {"hll1", "hll2"});
        results.add(Pair.of(PfMerge, buildArgs("hll", "hll1", "hll2")));

        batch.sdiff(new String[] {"key1", "key2"});
        results.add(Pair.of(SDiff, buildArgs("key1", "key2")));

        batch.sdiffstore("key1", new String[] {"key2", "key3"});
        results.add(Pair.of(SDiffStore, buildArgs("key1", "key2", "key3")));

        batch.objectEncoding("key");
        results.add(Pair.of(ObjectEncoding, buildArgs("key")));

        batch.objectFreq("key");
        results.add(Pair.of(ObjectFreq, buildArgs("key")));

        batch.objectIdletime("key");
        results.add(Pair.of(ObjectIdleTime, buildArgs("key")));

        batch.objectRefcount("key");
        results.add(Pair.of(ObjectRefCount, buildArgs("key")));

        batch.touch(new String[] {"key1", "key2"});
        results.add(Pair.of(Touch, buildArgs("key1", "key2")));

        batch.geoadd("key", Map.of("Place", new GeospatialData(10.0, 20.0)));
        results.add(Pair.of(GeoAdd, buildArgs("key", "10.0", "20.0", "Place")));

        batch.getbit("key", 1);
        results.add(Pair.of(GetBit, buildArgs("key", "1")));

        batch.geoadd(
                "key",
                Map.of("Place", new GeospatialData(10.0, 20.0)),
                new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true));
        results.add(
                Pair.of(
                        GeoAdd,
                        buildArgs(
                                "key",
                                ConditionalChange.ONLY_IF_EXISTS.getValkeyApi(),
                                CHANGED_VALKEY_API,
                                "10.0",
                                "20.0",
                                "Place")));
        batch.geopos("key", new String[] {"Place"});
        results.add(Pair.of(GeoPos, buildArgs("key", "Place")));

        batch.functionLoad("pewpew", false).functionLoad("ololo", true);
        results.add(Pair.of(FunctionLoad, buildArgs("pewpew")));
        results.add(Pair.of(FunctionLoad, buildArgs("REPLACE", "ololo")));

        batch.functionList(true).functionList("*", false);
        results.add(Pair.of(FunctionList, buildArgs(WITH_CODE_VALKEY_API)));
        results.add(Pair.of(FunctionList, buildArgs(LIBRARY_NAME_VALKEY_API, "*")));

        batch.functionDump();
        results.add(Pair.of(FunctionDump, buildArgs()));

        batch.functionRestore("TEST".getBytes());
        results.add(Pair.of(FunctionRestore, buildArgs("TEST")));

        batch.fcall("func", new String[] {"key1", "key2"}, new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCall, buildArgs("func", "2", "key1", "key2", "arg1", "arg2")));
        batch.fcall("func", new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCall, buildArgs("func", "0", "arg1", "arg2")));

        batch.fcallReadOnly("func", new String[] {"key1", "key2"}, new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCallReadOnly, buildArgs("func", "2", "key1", "key2", "arg1", "arg2")));
        batch.fcallReadOnly("func", new String[] {"arg1", "arg2"});
        results.add(Pair.of(FCallReadOnly, buildArgs("func", "0", "arg1", "arg2")));

        batch.functionStats();
        results.add(Pair.of(FunctionStats, buildArgs()));

        batch.geodist("key", "Place", "Place2");
        results.add(Pair.of(GeoDist, buildArgs("key", "Place", "Place2")));
        batch.geodist("key", "Place", "Place2", GeoUnit.KILOMETERS);
        results.add(Pair.of(GeoDist, buildArgs("key", "Place", "Place2", "km")));

        batch.geohash("key", new String[] {"Place"});
        results.add(Pair.of(GeoHash, buildArgs("key", "Place")));

        batch.bitcount("key");
        results.add(Pair.of(BitCount, buildArgs("key")));

        batch.bitcount("key", 1);
        results.add(Pair.of(BitCount, buildArgs("key", "1")));

        batch.bitcount("key", 1, 1);
        results.add(Pair.of(BitCount, buildArgs("key", "1", "1")));

        batch.bitcount("key", 1, 1, BitmapIndexType.BIT);
        results.add(Pair.of(BitCount, buildArgs("key", "1", "1", BitmapIndexType.BIT.toString())));

        batch.setbit("key", 8, 1);
        results.add(Pair.of(SetBit, buildArgs("key", "8", "1")));

        batch.bitpos("key", 1);
        results.add(Pair.of(BitPos, buildArgs("key", "1")));
        batch.bitpos("key", 0, 8);
        results.add(Pair.of(BitPos, buildArgs("key", "0", "8")));
        batch.bitpos("key", 1, 8, 10);
        results.add(Pair.of(BitPos, buildArgs("key", "1", "8", "10")));
        batch.bitpos("key", 1, 8, 10, BitmapIndexType.BIT);
        results.add(Pair.of(BitPos, buildArgs("key", "1", "8", "10", BitmapIndexType.BIT.toString())));

        batch.bitop(BitwiseOperation.AND, "destination", new String[] {"key"});
        results.add(Pair.of(BitOp, buildArgs(BitwiseOperation.AND.toString(), "destination", "key")));

        batch.lmpop(new String[] {"key"}, ListDirection.LEFT);
        results.add(Pair.of(LMPop, buildArgs("1", "key", "LEFT")));
        batch.lmpop(new String[] {"key"}, ListDirection.LEFT, 1L);
        results.add(Pair.of(LMPop, buildArgs("1", "key", "LEFT", "COUNT", "1")));

        batch.blmpop(new String[] {"key"}, ListDirection.LEFT, 0.1);
        results.add(Pair.of(BLMPop, buildArgs("0.1", "1", "key", "LEFT")));
        batch.blmpop(new String[] {"key"}, ListDirection.LEFT, 1L, 0.1);
        results.add(Pair.of(BLMPop, buildArgs("0.1", "1", "key", "LEFT", "COUNT", "1")));

        batch.lset("key", 0, "zero");
        results.add(Pair.of(LSet, buildArgs("key", "0", "zero")));

        batch.lmove("key1", "key2", ListDirection.LEFT, ListDirection.LEFT);
        results.add(Pair.of(LMove, buildArgs("key1", "key2", "LEFT", "LEFT")));

        batch.blmove("key1", "key2", ListDirection.LEFT, ListDirection.LEFT, 0.1);
        results.add(Pair.of(BLMove, buildArgs("key1", "key2", "LEFT", "LEFT", "0.1")));

        batch.srandmember("key");
        results.add(Pair.of(SRandMember, buildArgs("key")));

        batch.srandmember("key", 1);
        results.add(Pair.of(SRandMember, buildArgs("key", "1")));

        batch.spop("key");
        results.add(Pair.of(SPop, buildArgs("key")));

        batch.spopCount("key", 1);
        results.add(Pair.of(SPop, buildArgs("key", "1")));

        batch.bitfieldReadOnly(
                "key",
                new BitFieldReadOnlySubCommands[] {new BitFieldGet(new SignedEncoding(5), new Offset(3))});
        results.add(
                Pair.of(
                        BitFieldReadOnly,
                        buildArgs(
                                "key",
                                BitFieldOptions.GET_COMMAND_STRING,
                                BitFieldOptions.SIGNED_ENCODING_PREFIX.concat("5"),
                                "3")));
        batch.bitfield(
                "key",
                new BitFieldSubCommands[] {
                    new BitFieldSet(new UnsignedEncoding(10), new OffsetMultiplier(3), 4)
                });
        results.add(
                Pair.of(
                        BitField,
                        buildArgs(
                                "key",
                                BitFieldOptions.SET_COMMAND_STRING,
                                BitFieldOptions.UNSIGNED_ENCODING_PREFIX.concat("10"),
                                BitFieldOptions.OFFSET_MULTIPLIER_PREFIX.concat("3"),
                                "4")));

        batch.sintercard(new String[] {"key1", "key2"});
        results.add(Pair.of(SInterCard, buildArgs("2", "key1", "key2")));

        batch.sintercard(new String[] {"key1", "key2"}, 1);
        results.add(Pair.of(SInterCard, buildArgs("2", "key1", "key2", "LIMIT", "1")));

        batch.functionFlush().functionFlush(ASYNC);
        results.add(Pair.of(FunctionFlush, buildArgs()));
        results.add(Pair.of(FunctionFlush, buildArgs("ASYNC")));

        batch.functionDelete("LIB");
        results.add(Pair.of(FunctionDelete, buildArgs("LIB")));

        batch.copy("key1", "key2", true);
        results.add(Pair.of(Copy, buildArgs("key1", "key2", REPLACE_VALKEY_API)));

        batch.dump("key1");
        results.add(Pair.of(Dump, buildArgs("key1")));

        batch.restore("key2", 0, "TEST".getBytes());
        results.add(Pair.of(Restore, buildArgs("key2", "0", "TEST")));

        batch.restore(
                "key3", 0, "TEST".getBytes(), RestoreOptions.builder().replace().idletime(100L).build());
        results.add(Pair.of(Restore, buildArgs("key3", "0", "TEST", "REPLACE", "IDLETIME", "100")));

        batch.lcs("key1", "key2");
        results.add(Pair.of(LCS, buildArgs("key1", "key2")));

        batch.lcsLen("key1", "key2");
        results.add(Pair.of(LCS, buildArgs("key1", "key2", "LEN")));

        batch.publish("msg", "ch1");
        results.add(Pair.of(Publish, buildArgs("ch1", "msg")));

        batch.pubsubChannels();
        results.add(Pair.of(PubSubChannels, buildArgs()));

        batch.pubsubChannels("pattern");
        results.add(Pair.of(PubSubChannels, buildArgs("pattern")));

        batch.pubsubNumPat();
        results.add(Pair.of(PubSubNumPat, buildArgs()));

        batch.pubsubNumSub(new String[] {"ch1", "ch2"});
        results.add(Pair.of(PubSubNumSub, buildArgs("ch1", "ch2")));

        batch.lcsIdx("key1", "key2");
        results.add(Pair.of(LCS, buildArgs("key1", "key2", IDX_COMMAND_STRING)));

        batch.lcsIdx("key1", "key2", 10);
        results.add(
                Pair.of(
                        LCS, buildArgs("key1", "key2", IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, "10")));

        batch.lcsIdxWithMatchLen("key1", "key2");
        results.add(
                Pair.of(LCS, buildArgs("key1", "key2", IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING)));

        batch.lcsIdxWithMatchLen("key1", "key2", 10);
        results.add(
                Pair.of(
                        LCS,
                        buildArgs(
                                "key1",
                                "key2",
                                IDX_COMMAND_STRING,
                                MINMATCHLEN_COMMAND_STRING,
                                "10",
                                WITHMATCHLEN_COMMAND_STRING)));

        batch.sunion(new String[] {"key1", "key2"});
        results.add(Pair.of(SUnion, buildArgs("key1", "key2")));

        batch.sort("key1");
        results.add(Pair.of(Sort, buildArgs("key1")));
        batch.sortReadOnly("key1");
        results.add(Pair.of(SortReadOnly, buildArgs("key1")));
        batch.sortStore("key1", "key2");
        results.add(Pair.of(Sort, buildArgs("key1", STORE_COMMAND_STRING, "key2")));

        batch.geosearch(
                "key",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS));
        results.add(
                Pair.of(
                        GeoSearch, buildArgs("key", FROMMEMBER_VALKEY_API, "member", "BYRADIUS", "1.0", "km")));

        batch.geosearch(
                "key",
                new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                new GeoSearchShape(1, 1, GeoUnit.KILOMETERS),
                new GeoSearchResultOptions(SortOrder.ASC, 2));
        results.add(
                Pair.of(
                        GeoSearch,
                        buildArgs(
                                "key",
                                FROMLONLAT_VALKEY_API,
                                "1.0",
                                "1.0",
                                "BYBOX",
                                "1.0",
                                "1.0",
                                "km",
                                COUNT_VALKEY_API,
                                "2",
                                "ASC")));

        batch.geosearch(
                "key",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS),
                GeoSearchOptions.builder().withhash().withdist().withcoord().build(),
                new GeoSearchResultOptions(SortOrder.ASC, 1, true));
        results.add(
                Pair.of(
                        GeoSearch,
                        buildArgs(
                                "key",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km",
                                "WITHDIST",
                                "WITHCOORD",
                                "WITHHASH",
                                "COUNT",
                                "1",
                                "ANY",
                                "ASC")));

        batch.geosearch(
                "key",
                new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                new GeoSearchShape(1, 1, GeoUnit.KILOMETERS),
                GeoSearchOptions.builder().withhash().withdist().withcoord().build());
        results.add(
                Pair.of(
                        GeoSearch,
                        buildArgs(
                                "key",
                                FROMLONLAT_VALKEY_API,
                                "1.0",
                                "1.0",
                                "BYBOX",
                                "1.0",
                                "1.0",
                                "km",
                                "WITHDIST",
                                "WITHCOORD",
                                "WITHHASH")));

        batch.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS));
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km")));

        batch.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                new GeoSearchShape(1, 1, GeoUnit.KILOMETERS),
                new GeoSearchResultOptions(SortOrder.ASC, 2));
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMLONLAT_VALKEY_API,
                                "1.0",
                                "1.0",
                                "BYBOX",
                                "1.0",
                                "1.0",
                                "km",
                                COUNT_VALKEY_API,
                                "2",
                                "ASC")));

        batch.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS),
                GeoSearchStoreOptions.builder().storedist().build());
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km",
                                "STOREDIST")));

        batch.geosearchstore(
                "destination",
                "source",
                new GeoSearchOrigin.MemberOrigin("member"),
                new GeoSearchShape(1, GeoUnit.KILOMETERS),
                GeoSearchStoreOptions.builder().storedist().build(),
                new GeoSearchResultOptions(SortOrder.ASC, 1, true));
        results.add(
                Pair.of(
                        GeoSearchStore,
                        buildArgs(
                                "destination",
                                "source",
                                FROMMEMBER_VALKEY_API,
                                "member",
                                "BYRADIUS",
                                "1.0",
                                "km",
                                "STOREDIST",
                                "COUNT",
                                "1",
                                "ANY",
                                "ASC")));

        batch.sscan("key1", "0");
        results.add(Pair.of(SScan, buildArgs("key1", "0")));

        batch.sscan("key1", "0", SScanOptions.builder().matchPattern("*").count(10L).build());
        results.add(
                Pair.of(
                        SScan,
                        buildArgs(
                                "key1",
                                "0",
                                SScanOptions.MATCH_OPTION_STRING,
                                "*",
                                SScanOptions.COUNT_OPTION_STRING,
                                "10")));

        batch.zscan("key1", "0");
        results.add(Pair.of(ZScan, buildArgs("key1", "0")));

        batch.zscan("key1", "0", ZScanOptions.builder().matchPattern("*").count(10L).build());
        results.add(
                Pair.of(
                        ZScan,
                        buildArgs(
                                "key1",
                                "0",
                                ZScanOptions.MATCH_OPTION_STRING,
                                "*",
                                ZScanOptions.COUNT_OPTION_STRING,
                                "10")));

        batch.hscan("key1", "0");
        results.add(Pair.of(HScan, buildArgs("key1", "0")));

        batch.hscan("key1", "0", HScanOptions.builder().matchPattern("*").count(10L).build());
        results.add(
                Pair.of(
                        HScan,
                        buildArgs(
                                "key1",
                                "0",
                                HScanOptions.MATCH_OPTION_STRING,
                                "*",
                                HScanOptions.COUNT_OPTION_STRING,
                                "10")));

        batch.wait(1L, 1000L);
        results.add(Pair.of(Wait, buildArgs("1", "1000")));

        var protobufbatch = batch.getProtobufBatch().build();

        for (int idx = 0; idx < protobufbatch.getCommandsCount(); idx++) {
            Command protobuf = protobufbatch.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }

    static ArgsArray buildArgs(String... args) {
        var builder = ArgsArray.newBuilder();
        for (var arg : args) {
            builder.addArgs(ByteString.copyFromUtf8(arg));
        }
        return builder.build();
    }
}
