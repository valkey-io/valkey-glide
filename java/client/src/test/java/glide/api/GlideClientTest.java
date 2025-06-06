/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

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
import static command_request.CommandRequestOuterClass.RequestType.CustomCommand;
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
import static command_request.CommandRequestOuterClass.RequestType.FunctionKill;
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
import static command_request.CommandRequestOuterClass.RequestType.HGet;
import static command_request.CommandRequestOuterClass.RequestType.HGetAll;
import static command_request.CommandRequestOuterClass.RequestType.HIncrBy;
import static command_request.CommandRequestOuterClass.RequestType.HIncrByFloat;
import static command_request.CommandRequestOuterClass.RequestType.HKeys;
import static command_request.CommandRequestOuterClass.RequestType.HLen;
import static command_request.CommandRequestOuterClass.RequestType.HMGet;
import static command_request.CommandRequestOuterClass.RequestType.HRandField;
import static command_request.CommandRequestOuterClass.RequestType.HScan;
import static command_request.CommandRequestOuterClass.RequestType.HSet;
import static command_request.CommandRequestOuterClass.RequestType.HSetNX;
import static command_request.CommandRequestOuterClass.RequestType.HStrlen;
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
import static command_request.CommandRequestOuterClass.RequestType.Move;
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
import static command_request.CommandRequestOuterClass.RequestType.Scan;
import static command_request.CommandRequestOuterClass.RequestType.ScriptExists;
import static command_request.CommandRequestOuterClass.RequestType.ScriptFlush;
import static command_request.CommandRequestOuterClass.RequestType.ScriptKill;
import static command_request.CommandRequestOuterClass.RequestType.ScriptShow;
import static command_request.CommandRequestOuterClass.RequestType.Select;
import static command_request.CommandRequestOuterClass.RequestType.SetBit;
import static command_request.CommandRequestOuterClass.RequestType.SetRange;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.Strlen;
import static command_request.CommandRequestOuterClass.RequestType.TTL;
import static command_request.CommandRequestOuterClass.RequestType.Time;
import static command_request.CommandRequestOuterClass.RequestType.Touch;
import static command_request.CommandRequestOuterClass.RequestType.Type;
import static command_request.CommandRequestOuterClass.RequestType.UnWatch;
import static command_request.CommandRequestOuterClass.RequestType.Unlink;
import static command_request.CommandRequestOuterClass.RequestType.Wait;
import static command_request.CommandRequestOuterClass.RequestType.Watch;
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
import static glide.api.BaseClient.OK;
import static glide.api.commands.GenericBaseCommands.REPLACE_VALKEY_API;
import static glide.api.commands.GenericCommands.DB_VALKEY_API;
import static glide.api.commands.HashBaseCommands.WITH_VALUES_VALKEY_API;
import static glide.api.commands.ListBaseCommands.COUNT_FOR_LIST_VALKEY_API;
import static glide.api.commands.ServerManagementCommands.VERSION_VALKEY_API;
import static glide.api.commands.SetBaseCommands.SET_LIMIT_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.COUNT_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.LIMIT_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_VALKEY_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_VALKEY_API;
import static glide.api.commands.StringBaseCommands.IDX_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.LCS_MATCHES_RESULT_KEY;
import static glide.api.commands.StringBaseCommands.LEN_VALKEY_API;
import static glide.api.commands.StringBaseCommands.MINMATCHLEN_COMMAND_STRING;
import static glide.api.commands.StringBaseCommands.WITHMATCHLEN_COMMAND_STRING;
import static glide.api.models.GlideString.gs;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.FlushMode.SYNC;
import static glide.api.models.commands.LInsertOptions.InsertPosition.BEFORE;
import static glide.api.models.commands.ScoreFilter.MAX;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EQUAL;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.BY_COMMAND_STRING;
import static glide.api.models.commands.SortOptionsBinary.BY_COMMAND_GLIDE_STRING;
import static glide.api.models.commands.SortOptionsBinary.GET_COMMAND_GLIDE_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.BitFieldOverflow.BitOverflowControl.SAT;
import static glide.api.models.commands.bitmap.BitFieldOptions.GET_COMMAND_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.INCRBY_COMMAND_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.OVERFLOW_COMMAND_STRING;
import static glide.api.models.commands.bitmap.BitFieldOptions.SET_COMMAND_STRING;
import static glide.api.models.commands.function.FunctionListOptions.LIBRARY_NAME_VALKEY_API;
import static glide.api.models.commands.function.FunctionListOptions.WITH_CODE_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoAddOptions.CHANGED_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoSearchOrigin.FROMLONLAT_VALKEY_API;
import static glide.api.models.commands.geospatial.GeoSearchOrigin.FROMMEMBER_VALKEY_API;
import static glide.api.models.commands.scan.BaseScanOptions.COUNT_OPTION_STRING;
import static glide.api.models.commands.scan.BaseScanOptions.MATCH_OPTION_STRING;
import static glide.api.models.commands.scan.BaseScanOptionsBinary.COUNT_OPTION_GLIDE_STRING;
import static glide.api.models.commands.scan.BaseScanOptionsBinary.MATCH_OPTION_GLIDE_STRING;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.STRING;
import static glide.api.models.commands.scan.ScanOptions.TYPE_OPTION_STRING;
import static glide.api.models.commands.stream.StreamAddOptions.NO_MAKE_STREAM_VALKEY_API;
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
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_LIMIT_VALKEY_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_MAXLEN_VALKEY_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_MINID_VALKEY_API;
import static glide.api.models.commands.stream.StreamTrimOptions.TRIM_NOT_EXACT_VALKEY_API;
import static glide.api.models.commands.stream.XInfoStreamOptions.COUNT;
import static glide.api.models.commands.stream.XInfoStreamOptions.FULL;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueGlideStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArrayBinary;
import static glide.utils.ArrayTransformUtils.convertNestedArrayToKeyValueGlideStringArray;
import static glide.utils.ArrayTransformUtils.convertNestedArrayToKeyValueStringArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import command_request.CommandRequestOuterClass.RequestType;
import glide.api.models.Batch;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.RestoreOptions;
import glide.api.models.commands.ScoreFilter;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.ScriptOptionsGlideString;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.Expiry;
import glide.api.models.commands.SortBaseOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.SortOptionsBinary;
import glide.api.models.commands.SortOrder;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
import glide.api.models.commands.WeightAggregateOptions.KeyArrayBinary;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeys;
import glide.api.models.commands.WeightAggregateOptions.WeightedKeysBinary;
import glide.api.models.commands.ZAddOptions;
import glide.api.models.commands.batch.BatchOptions;
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
import glide.api.models.commands.function.FunctionLoadOptions;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.commands.geospatial.GeoAddOptions;
import glide.api.models.commands.geospatial.GeoSearchOptions;
import glide.api.models.commands.geospatial.GeoSearchOrigin;
import glide.api.models.commands.geospatial.GeoSearchResultOptions;
import glide.api.models.commands.geospatial.GeoSearchShape;
import glide.api.models.commands.geospatial.GeoSearchStoreOptions;
import glide.api.models.commands.geospatial.GeoUnit;
import glide.api.models.commands.geospatial.GeospatialData;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.scan.HScanOptionsBinary;
import glide.api.models.commands.scan.SScanOptions;
import glide.api.models.commands.scan.SScanOptionsBinary;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.commands.scan.ZScanOptions;
import glide.api.models.commands.scan.ZScanOptionsBinary;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamAddOptionsBinary;
import glide.api.models.commands.stream.StreamClaimOptions;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamPendingOptionsBinary;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamRange.IdBound;
import glide.api.models.commands.stream.StreamRange.InfRangeBound;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
import glide.api.models.commands.stream.StreamTrimOptions.MaxLen;
import glide.api.models.commands.stream.StreamTrimOptions.MinId;
import glide.managers.CommandManager;
import glide.utils.ArgsBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class GlideClientTest {

    // bypass import conflict between Set (collection) and Set (enum variant)
    private static final RequestType pSet = RequestType.Set;

    GlideClient service;

    CommandManager commandManager;

    @BeforeEach
    public void setUp() {
        commandManager = mock(CommandManager.class);
        service = new GlideClient(new BaseClient.ClientBuilder(null, commandManager, null, null));
    }

    @SneakyThrows
    @Test
    public void customCommand_returns_success() {
        // setup
        String key = "testKey";
        Object value = "testValue";
        String cmd = "GETSTRING";
        String[] arguments = new String[] {cmd, key};
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(CustomCommand), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.customCommand(arguments);
        String payload = (String) response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void customCommand_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Object value = "testValue";
        GlideString cmd = gs("GETSTRING");
        GlideString[] arguments = new GlideString[] {cmd, key};
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(CustomCommand), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.customCommand(arguments);
        String payload = (String) response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void exec(boolean isAtomic) {
        // setup
        Object[] value = new Object[] {"PONG", "PONG"};
        Batch batch = new Batch(isAtomic).ping().ping();

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewBatch(eq(batch), eq(false), eq(Optional.empty()), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.exec(batch, false);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void exec_with_options(boolean isAtomic) {
        // setup
        Object[] value = new Object[] {"PONG", "PONG"};
        Batch batch = new Batch(isAtomic).ping().ping();
        BatchOptions options = BatchOptions.builder().timeout(1000).build();

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewBatch(
                        eq(batch), eq(false), eq(Optional.of(options)), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.exec(batch, false, options);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void echo_returns_success() {
        // setup
        String message = "Valkey GLIDE";
        String[] arguments = new String[] {message};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Echo), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.echo(message);
        String echo = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, echo);
    }

    @SneakyThrows
    @Test
    public void echo_binary_returns_success() {
        // setup
        GlideString message = gs("Valkey GLIDE");
        GlideString[] arguments = new GlideString[] {message};
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(Echo), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.echo(message);
        GlideString echo = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, echo);
    }

    @SneakyThrows
    @Test
    public void ping_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete("PONG");

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals("PONG", payload);
    }

    @SneakyThrows
    @Test
    public void ping_with_message_returns_success() {
        // setup
        String message = "RETURN OF THE PONG";
        String[] arguments = new String[] {message};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping(message);
        String pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void ping_binary_with_message_returns_success() {
        // setup
        GlideString message = gs("RETURN OF THE PONG");
        GlideString[] arguments = new GlideString[] {message};
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(Ping), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.ping(message);
        GlideString pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void select_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        long index = 5L;
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(Select), eq(new String[] {Long.toString(index)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.select(index);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void del_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberDeleted = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(numberDeleted);
        when(commandManager.<Long>submitNewCommand(eq(Del), eq(keys), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.del(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberDeleted, result);
    }

    @SneakyThrows
    @Test
    public void del_returns_long_success_binary() {
        // setup
        GlideString[] keys = new GlideString[] {gs("testKey1"), gs("testKey2")};
        Long numberDeleted = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(numberDeleted);
        when(commandManager.<Long>submitNewCommand(eq(Del), eq(keys), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.del(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberDeleted, result);
    }

    @SneakyThrows
    @Test
    public void unlink_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberUnlinked = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(numberUnlinked);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Unlink), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.unlink(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberUnlinked, result);
    }

    @SneakyThrows
    @Test
    public void unlink_binary_returns_long_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("testKey1"), gs("testKey2")};
        Long numberUnlinked = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(numberUnlinked);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Unlink), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.unlink(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberUnlinked, result);
    }

    @SneakyThrows
    @Test
    public void get_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(Get), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.get(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getdel() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(GetDel), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.getdel(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getex() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(GetEx), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.getex(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getex_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString value = gs("testValue");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<GlideString>submitNewCommand(
                        eq(GetEx), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.getex(key);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getex_with_options() {
        // setup
        String key = "testKey";
        String value = "testValue";
        GetExOptions options = GetExOptions.Seconds(10L);
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(
                        eq(GetEx), eq(new String[] {key, "EX", "10"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.getex(key, options);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getex_with_options_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString value = gs("testValue");
        GetExOptions options = GetExOptions.Seconds(10L);
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<GlideString>submitNewCommand(
                        eq(GetEx), eq(new GlideString[] {key, gs("EX"), gs("10")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.getex(key, options);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    private static List<Arguments> getGetExOptions() {
        return List.of(
                Arguments.of(
                        // seconds
                        "test_with_seconds", GetExOptions.Seconds(10L), new String[] {"EX", "10"}),
                Arguments.of(
                        // milliseconds
                        "test_with_milliseconds",
                        GetExOptions.Milliseconds(1000L),
                        new String[] {"PX", "1000"}),
                Arguments.of(
                        // unix seconds
                        "test_with_unix_seconds", GetExOptions.UnixSeconds(10L), new String[] {"EXAT", "10"}),
                Arguments.of(
                        // unix milliseconds
                        "test_with_unix_milliseconds",
                        GetExOptions.UnixMilliseconds(1000L),
                        new String[] {"PXAT", "1000"}),
                Arguments.of(
                        // persist
                        "test_with_persist", GetExOptions.Persist(), new String[] {"PERSIST"}));
    }

    private static List<Arguments> getGetExOptionsBinary() {
        return List.of(
                Arguments.of(
                        // seconds
                        "test_with_seconds", GetExOptions.Seconds(10L), new GlideString[] {gs("EX"), gs("10")}),
                Arguments.of(
                        // milliseconds
                        "test_with_milliseconds",
                        GetExOptions.Milliseconds(1000L),
                        new GlideString[] {gs("PX"), gs("1000")}),
                Arguments.of(
                        // unix seconds
                        "test_with_unix_seconds",
                        GetExOptions.UnixSeconds(10L),
                        new GlideString[] {gs("EXAT"), gs("10")}),
                Arguments.of(
                        // unix milliseconds
                        "test_with_unix_milliseconds",
                        GetExOptions.UnixMilliseconds(1000L),
                        new GlideString[] {gs("PXAT"), gs("1000")}),
                Arguments.of(
                        // persist
                        "test_with_persist", GetExOptions.Persist(), new GlideString[] {gs("PERSIST")}));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("getGetExOptions")
    public void getex_options(String testName, GetExOptions options, String[] expectedArgs) {
        assertArrayEquals(
                expectedArgs, options.toArgs(), "Expected " + testName + " toArgs() to pass.");
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("getGetExOptionsBinary")
    public void getex_options_binary(
            String testName, GetExOptions options, GlideString[] expectedGlideStringArgs) {
        assertArrayEquals(
                expectedGlideStringArgs,
                options.toGlideStringArgs(),
                "Expected " + testName + " toArgs() to pass.");
    }

    @SneakyThrows
    @Test
    public void set_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(null);
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(new String[] {key, value}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value);
        Object okResponse = response.get();

        // verify
        assertEquals(testResponse, response);
        assertNull(okResponse);
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfExists_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSet(ONLY_IF_EXISTS)
                        .returnOldValue(false)
                        .expiry(Expiry.KeepExisting())
                        .build();
        String[] arguments = new String[] {key, value, ONLY_IF_EXISTS.getValkeyApi(), "KEEPTTL"};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(null);
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertEquals(testResponse, response);
        assertNull(response.get());
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfDoesNotExist_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSet(ONLY_IF_DOES_NOT_EXIST)
                        .returnOldValue(true)
                        .expiry(Expiry.UnixSeconds(60L))
                        .build();
        String[] arguments =
                new String[] {
                    key, value, ONLY_IF_DOES_NOT_EXIST.getValkeyApi(), RETURN_OLD_VALUE, "EXAT", "60"
                };
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertNotNull(response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfEqual_success() {
        // setup
        String key = "key";
        String value = "value";
        String newValue = "newValue";

        // Set `key` to `value` initially
        CompletableFuture<String> initialSetResponse = new CompletableFuture<>();
        initialSetResponse.complete("OK");
        String[] initialArguments = new String[] {key, value};
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(initialArguments), any()))
                .thenReturn(initialSetResponse);

        CompletableFuture<String> initialResponse = service.set(key, value);
        assertNotNull(initialResponse);
        assertEquals("OK", initialResponse.get());

        // Set `key` to `newValue` with the correct condition
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSetOnlyIfEqualTo(value) // Key must currently have `value`
                        .expiry(Expiry.UnixSeconds(60L))
                        .build();
        String[] correctConditionArguments =
                new String[] {key, newValue, ONLY_IF_EQUAL.getValkeyApi(), value, "EXAT", "60"};
        CompletableFuture<String> correctSetResponse = new CompletableFuture<>();
        correctSetResponse.complete("OK");
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(correctConditionArguments), any()))
                .thenReturn(correctSetResponse);

        CompletableFuture<String> correctResponse = service.set(key, newValue, setOptions);
        assertNotNull(correctResponse);
        assertEquals("OK", correctResponse.get());

        // Verify that the key is now set to `newValue`
        CompletableFuture<String> fetchValueResponse = new CompletableFuture<>();
        fetchValueResponse.complete(newValue);
        when(commandManager.<String>submitNewCommand(eq(Get), eq(new String[] {key}), any()))
                .thenReturn(fetchValueResponse);

        CompletableFuture<String> finalValue = service.get(key);
        assertEquals(newValue, finalValue.get());
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfEqual_fails() {
        // Key-Value setup
        String key = "key";
        String value = "value";
        String newValue = "newValue";

        // Set `key` to `value` initially
        CompletableFuture<String> initialSetResponse = new CompletableFuture<>();
        initialSetResponse.complete("OK");
        String[] initialArguments = new String[] {key, value};
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(initialArguments), any()))
                .thenReturn(initialSetResponse);

        CompletableFuture<String> initialResponse = service.set(key, value);
        assertNotNull(initialResponse);
        assertEquals("OK", initialResponse.get());

        // Attempt to set `key` to `newValue` with the wrong condition
        SetOptions wrongConditionOptions =
                SetOptions.builder()
                        .conditionalSetOnlyIfEqualTo(newValue) // Incorrect: current value of key is `value`
                        .expiry(Expiry.UnixSeconds(60L))
                        .build();

        String[] wrongConditionArguments =
                new String[] {key, newValue, ONLY_IF_EQUAL.getValkeyApi(), newValue, "EXAT", "60"};

        CompletableFuture<String> failedSetResponse = new CompletableFuture<>();
        failedSetResponse.complete(null);
        when(commandManager.<String>submitNewCommand(eq(pSet), eq(wrongConditionArguments), any()))
                .thenReturn(failedSetResponse);

        CompletableFuture<String> failedResponse = service.set(key, newValue, wrongConditionOptions);
        assertNotNull(failedResponse);
        assertNull(failedResponse.get()); // Ensure the set operation failed

        // Verify that the key remains set to `value`
        CompletableFuture<String> fetchValueResponse = new CompletableFuture<>();
        fetchValueResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(Get), eq(new String[] {key}), any()))
                .thenReturn(fetchValueResponse);

        CompletableFuture<String> finalValue = service.get(key);
        assertEquals(value, finalValue.get());
    }

    @SneakyThrows
    @Test
    public void exists_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberExisting = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(numberExisting);
        when(commandManager.<Long>submitNewCommand(eq(Exists), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.exists(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberExisting, result);
    }

    @SneakyThrows
    @Test
    public void exists_binary_returns_long_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("testKey1"), gs("testKey2")};
        Long numberExisting = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(numberExisting);
        when(commandManager.<Long>submitNewCommand(eq(Exists), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.exists(keys);
        Long result = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(numberExisting, result);
    }

    @SneakyThrows
    @Test
    public void expire_returns_success() {
        // setup
        String key = "testKey";
        long seconds = 10L;
        String[] arguments = new String[] {key, Long.toString(seconds)};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Expire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expire(key, seconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void expire_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long seconds = 10L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(seconds))};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Expire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expire(key, seconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void expire_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long seconds = 10L;
        String[] arguments = new String[] {key, Long.toString(seconds), "NX"};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Expire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expire(key, seconds, ExpireOptions.HAS_NO_EXPIRY);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void expire_with_expireOptions_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long seconds = 10L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(seconds)), gs("NX")};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Expire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expire(key, seconds, ExpireOptions.HAS_NO_EXPIRY);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void expireAt_returns_success() {
        // setup
        String key = "testKey";
        long unixSeconds = 100000L;
        String[] arguments = new String[] {key, Long.toString(unixSeconds)};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(ExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expireAt(key, unixSeconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void expireAt_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long unixSeconds = 100000L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(unixSeconds))};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(ExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.expireAt(key, unixSeconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void expireAt_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long unixSeconds = 100000L;
        String[] arguments = new String[] {key, Long.toString(unixSeconds), "XX"};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(ExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.expireAt(key, unixSeconds, ExpireOptions.HAS_EXISTING_EXPIRY);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void expireAt_with_expireOptions_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long unixSeconds = 100000L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(unixSeconds)), gs("XX")};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(ExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.expireAt(key, unixSeconds, ExpireOptions.HAS_EXISTING_EXPIRY);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpire_returns_success() {
        // setup
        String key = "testKey";
        long milliseconds = 50000L;
        String[] arguments = new String[] {key, Long.toString(milliseconds)};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pexpire(key, milliseconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpire_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long milliseconds = 50000L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(milliseconds))};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pexpire(key, milliseconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpire_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long milliseconds = 50000L;
        String[] arguments = new String[] {key, Long.toString(milliseconds), "LT"};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.pexpire(key, milliseconds, ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpire_with_expireOptions_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long milliseconds = 50000L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(milliseconds)), gs("LT")};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpire), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.pexpire(key, milliseconds, ExpireOptions.NEW_EXPIRY_LESS_THAN_CURRENT);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpireAt_returns_success() {
        // setup
        String key = "testKey";
        long unixMilliseconds = 999999L;
        String[] arguments = new String[] {key, Long.toString(unixMilliseconds)};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pexpireAt(key, unixMilliseconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpireAt_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long unixMilliseconds = 999999L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(unixMilliseconds))};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pexpireAt(key, unixMilliseconds);

        // verify
        assertEquals(testResponse, response);
        assertEquals(true, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpireAt_with_expireOptions_returns_success() {
        // setup
        String key = "testKey";
        long unixMilliseconds = 999999L;
        String[] arguments = new String[] {key, Long.toString(unixMilliseconds), "GT"};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.pexpireAt(key, unixMilliseconds, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpireAt_with_expireOptions_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long unixMilliseconds = 999999L;
        GlideString[] arguments =
                new GlideString[] {key, gs(Long.toString(unixMilliseconds)), gs("GT")};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.FALSE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PExpireAt), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.pexpireAt(key, unixMilliseconds, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT);

        // verify
        assertEquals(testResponse, response);
        assertEquals(false, response.get());
    }

    @SneakyThrows
    @Test
    public void ttl_returns_success() {
        // setup
        String key = "testKey";
        long ttl = 999L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(ttl);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(TTL), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.ttl(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(ttl, response.get());
    }

    @SneakyThrows
    @Test
    public void ttl_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long ttl = 999L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(ttl);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(TTL), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.ttl(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(ttl, response.get());
    }

    @SneakyThrows
    @Test
    public void expiretime_returns_success() {
        // setup
        String key = "testKey";
        long value = 999L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ExpireTime), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.expiretime(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void expiretime_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long value = 999L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ExpireTime), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.expiretime(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpiretime_returns_success() {
        // setup
        String key = "testKey";
        long value = 999L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PExpireTime), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pexpiretime(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void pexpiretime_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long value = 999L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PExpireTime), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pexpiretime(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void invokeScript_returns_success() {
        // setup
        Script script = mock(Script.class);
        String hash = UUID.randomUUID().toString();
        when(script.getHash()).thenReturn(hash);
        String payload = "hello";

        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(payload);

        // match on protobuf request
        when(commandManager.submitScript(eq(script), eq(List.of()), eq(List.of()), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.invokeScript(script);

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void invokeScript_with_ScriptOptions_returns_success() {
        // setup
        Script script = mock(Script.class);
        String hash = UUID.randomUUID().toString();
        when(script.getHash()).thenReturn(hash);
        String payload = "hello";

        ScriptOptions options =
                ScriptOptions.builder().key("key1").key("key2").arg("arg1").arg("arg2").build();

        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(payload);

        // match on protobuf request
        when(commandManager.submitScript(
                        eq(script),
                        eq(List.of(gs("key1"), gs("key2"))),
                        eq(List.of(gs("arg1"), gs("arg2"))),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.invokeScript(script, options);

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void invokeScript_with_ScriptOptionsGlideString_returns_success() {
        // setup
        Script script = mock(Script.class);
        String hash = UUID.randomUUID().toString();
        when(script.getHash()).thenReturn(hash);
        GlideString payload = gs("hello");

        ScriptOptionsGlideString options =
                ScriptOptionsGlideString.builder()
                        .key(gs("key1"))
                        .key(gs("key2"))
                        .arg(gs("arg1"))
                        .arg(gs("arg2"))
                        .build();

        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(payload);

        // match on protobuf request
        when(commandManager.submitScript(
                        eq(script),
                        eq(List.of(gs("key1"), gs("key2"))),
                        eq(List.of(gs("arg1"), gs("arg2"))),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.invokeScript(script, options);

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void scriptShow_returns_script_source() {
        // setup
        String scriptSource = "return { KEYS[1], ARGV[1] }";
        String hash = UUID.randomUUID().toString();

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(scriptSource);

        when(commandManager.<String>submitNewCommand(eq(ScriptShow), eq(new String[] {hash}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.scriptShow(hash);

        // verify
        assertEquals(testResponse, response);
        assertEquals(scriptSource, response.get());
    }

    @SneakyThrows
    @Test
    public void scriptShow_returns_script_source_glidestring() {
        // setup
        GlideString scriptSource = gs("return { KEYS[1], ARGV[1] }");
        GlideString hash = gs(UUID.randomUUID().toString());

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(scriptSource);

        when(commandManager.<GlideString>submitNewCommand(
                        eq(ScriptShow), eq(new GlideString[] {hash}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.scriptShow(hash);

        // verify
        assertEquals(testResponse, response);
        assertEquals(scriptSource, response.get());
    }

    @SneakyThrows
    @Test
    public void pttl_returns_success() {
        // setup
        String key = "testKey";
        long pttl = 999000L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(pttl);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PTTL), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pttl(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(pttl, response.get());
    }

    @SneakyThrows
    @Test
    public void pttl_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long pttl = 999000L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(pttl);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PTTL), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pttl(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(pttl, response.get());
    }

    @SneakyThrows
    @Test
    public void persist_returns_success() {
        // setup
        String key = "testKey";
        Boolean isTimeoutRemoved = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(isTimeoutRemoved);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Persist), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.persist(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(isTimeoutRemoved, response.get());
    }

    @SneakyThrows
    @Test
    public void persist_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Boolean isTimeoutRemoved = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(isTimeoutRemoved);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Persist), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.persist(key);

        // verify
        assertEquals(testResponse, response);
        assertEquals(isTimeoutRemoved, response.get());
    }

    @SneakyThrows
    @Test
    public void info_returns_success() {
        // setup
        String testPayload = "Key: Value";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.info();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_multiple_InfoOptions_returns_success() {
        // setup
        String[] arguments = new String[] {Section.ALL.toString(), Section.DEFAULT.toString()};
        String testPayload = "Key: Value";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        Section[] sections = {Section.ALL, Section.DEFAULT};
        CompletableFuture<String> response = service.info(sections);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_empty_InfoOptions_returns_success() {
        // setup
        String testPayload = "Key: Value";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.info(new Section[0]);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void mget_returns_success() {
        // setup
        String[] keys = {"key1", null, "key2"};
        String[] values = {"value1", null, "value2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(values);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(MGet), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.mget(keys);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(values, payload);
    }

    @SneakyThrows
    @Test
    public void mset_returns_success() {
        // setup
        Map<String, String> keyValueMap = new LinkedHashMap<>();
        keyValueMap.put("key1", "value1");
        keyValueMap.put("key2", "value2");
        String[] args = {"key1", "value1", "key2", "value2"};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(MSet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.mset(keyValueMap);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void mset_binary_returns_success() {
        // setup
        Map<GlideString, GlideString> keyValueMap = new LinkedHashMap<>();
        keyValueMap.put(gs("key1"), gs("value1"));
        keyValueMap.put(gs("key2"), gs("value2"));
        GlideString[] args = {gs("key1"), gs("value1"), gs("key2"), gs("value2")};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(MSet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.msetBinary(keyValueMap);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void msetnx_returns_success() {
        // setup
        Map<String, String> keyValueMap = new LinkedHashMap<>();
        keyValueMap.put("key1", "value1");
        keyValueMap.put("key2", "value2");
        String[] args = {"key1", "value1", "key2", "value2"};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(MSetNX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.msetnx(keyValueMap);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void msetnx_binary_returns_success() {
        // setup
        Map<GlideString, GlideString> keyValueMap = new LinkedHashMap<>();
        keyValueMap.put(gs("key1"), gs("value1"));
        keyValueMap.put(gs("key2"), gs("value2"));
        GlideString[] args = {gs("key1"), gs("value1"), gs("key2"), gs("value2")};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(MSetNX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.msetnxBinary(keyValueMap);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incr_returns_success() {
        // setup
        String key = "testKey";
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Incr), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.incr(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incr_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Incr), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.incr(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incrBy_returns_success() {
        // setup
        String key = "testKey";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(IncrBy), eq(new String[] {key, Long.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.incrBy(key, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incrBy_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(IncrBy), eq(new GlideString[] {key, gs(Long.toString(amount).getBytes())}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.incrBy(key, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incrByFloat_returns_success() {
        // setup
        String key = "testKey";
        double amount = 1.1;
        Double value = 10.1;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(
                        eq(IncrByFloat), eq(new String[] {key, Double.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.incrByFloat(key, amount);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void incrByFloat_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        double amount = 1.1;
        Double value = 10.1;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(
                        eq(IncrByFloat),
                        eq(new GlideString[] {key, gs(Double.toString(amount).getBytes())}),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.incrByFloat(key, amount);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void decr_returns_success() {
        // setup
        String key = "testKey";
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Decr), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.decr(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void decr_returns_success_binary() {
        // setup
        String key = "testKey";
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Decr), eq(new GlideString[] {gs(key)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.decr(gs(key));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void decrBy_returns_success() {
        // setup
        String key = "testKey";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(DecrBy), eq(new String[] {key, Long.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.decrBy(key, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void decrBy_returns_success_binary() {
        // setup
        String key = "testKey";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(DecrBy), eq(new GlideString[] {gs(key), gs(Long.toString(amount))}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.decrBy(gs(key), amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void strlen_returns_success() {
        // setup
        String key = "testKey";
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Strlen), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.strlen(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void setrange_returns_success() {
        // setup
        String key = "testKey";
        int offset = 42;
        String str = "pewpew";
        String[] arguments = new String[] {key, Integer.toString(offset), str};
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SetRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.setrange(key, offset, str);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void setrange_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        int offset = 42;
        GlideString str = gs("pewpew");
        GlideString[] arguments = new GlideString[] {key, gs(Integer.toString(offset)), str};
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SetRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.setrange(key, offset, str);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getrange_returns_success() {
        // setup
        String key = "testKey";
        int start = 42;
        int end = 54;
        String[] arguments = new String[] {key, Integer.toString(start), Integer.toString(end)};
        String value = "pewpew";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(GetRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.getrange(key, start, end);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getrange_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        int start = 42;
        int end = 54;
        GlideString[] arguments =
                new GlideString[] {key, gs(Integer.toString(start)), gs(Integer.toString(end))};
        GlideString value = gs("pewpew");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(GetRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.getrange(key, start, end);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hget_success() {
        // setup
        String key = "testKey";
        String field = "field";
        String[] args = new String[] {key, field};
        String value = "value";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(HGet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.hget(key, field);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hget_binary_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString field = gs("field");
        GlideString[] args = new GlideString[] {key, field};
        GlideString value = gs("value");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<GlideString>submitNewCommand(eq(HGet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.hget(key, field);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hset_success() {
        // setup
        String key = "testKey";
        Map<String, String> fieldValueMap = new LinkedHashMap<>();
        fieldValueMap.put("field1", "value1");
        fieldValueMap.put("field2", "value2");
        String[] args = new String[] {key, "field1", "value1", "field2", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<Long>submitNewCommand(eq(HSet), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hset(key, fieldValueMap);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hset_binary_success() {
        // setup
        GlideString key = gs("testKey");
        Map<GlideString, GlideString> fieldValueMap = new LinkedHashMap<>();
        fieldValueMap.put(gs("field1"), gs("value1"));
        fieldValueMap.put(gs("field2"), gs("value2"));
        GlideString[] args =
                new GlideString[] {key, gs("field1"), gs("value1"), gs("field2"), gs("value2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<Long>submitNewCommand(eq(HSet), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hset(key, fieldValueMap);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hsetnx_success() {
        // setup
        String key = "testKey";
        String field = "testField";
        String value = "testValue";
        String[] args = new String[] {key, field, value};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(HSetNX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.hsetnx(key, field, value);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertTrue(payload);
    }

    @SneakyThrows
    @Test
    public void hsetnx_binary_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString field = gs("testField");
        GlideString value = gs("testValue");
        GlideString[] args = new GlideString[] {key, field, value};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(HSetNX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.hsetnx(key, field, value);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertTrue(payload);
    }

    @SneakyThrows
    @Test
    public void hdel_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {key, "testField1", "testField2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(HDel), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hdel(key, fields);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hdel_success_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] fields = {gs("testField1"), gs("testField2")};
        GlideString[] args = {key, gs("testField1"), gs("testField2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(HDel), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hdel(key, fields);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hlen_success() {
        // setup
        String key = "testKey";
        String[] args = {key};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(HLen), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hlen(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hlen_binary_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] args = {key};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(HLen), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hlen(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hvals_success() {
        // setup
        String key = "testKey";
        String[] args = {key};
        String[] values = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(values);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(HVals), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.hvals(key);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(values, payload);
    }

    @SneakyThrows
    @Test
    public void hvals_binary_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] args = {key};
        GlideString[] values = new GlideString[] {gs("value1"), gs("value2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(values);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(HVals), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.hvals(key);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(values, payload);
    }

    @SneakyThrows
    @Test
    public void hmget_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {"testKey", "testField1", "testField2"};
        String[] value = {"testValue1", "testValue2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(HMGet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.hmget(key, fields);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hmget_binary_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] fields = {gs("testField1"), gs("testField2")};
        GlideString[] args = {gs("testKey"), gs("testField1"), gs("testField2")};
        GlideString[] value = {gs("testValue1"), gs("testValue2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(HMGet), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.hmget(key, fields);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hexists_success() {
        // setup
        String key = "testKey";
        String field = "testField";
        String[] args = new String[] {key, field};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(HExists), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.hexists(key, field);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hexists_binary_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString field = gs("testField");
        GlideString[] args = new GlideString[] {key, field};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(HExists), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.hexists(key, field);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hgetall_success() {
        // setup
        String key = "testKey";
        String[] args = new String[] {key};
        Map<String, String> value = new LinkedHashMap<>();
        value.put("key1", "field1");
        value.put("key2", "field2");

        CompletableFuture<Map<String, String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, String>>submitNewCommand(eq(HGetAll), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String>> response = service.hgetall(key);
        Map<String, String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hincrBy_returns_success() {
        // setup
        String key = "testKey";
        String field = "field";
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(HIncrBy), eq(new String[] {key, field, Long.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hincrBy(key, field, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hincrBy_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString field = gs("field");
        long amount = 1L;
        Long value = 10L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(HIncrBy),
                        eq(new GlideString[] {key, field, gs(Long.toString(amount).getBytes())}),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hincrBy(key, field, amount);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hincrByFloat_returns_success() {
        // setup
        String key = "testKey";
        String field = "field";
        double amount = 1.0;
        Double value = 10.0;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(
                        eq(HIncrByFloat), eq(new String[] {key, field, Double.toString(amount)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.hincrByFloat(key, field, amount);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hincrByFloat_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString field = gs("field");
        double amount = 1.0;
        Double value = 10.0;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(
                        eq(HIncrByFloat),
                        eq(new GlideString[] {key, field, gs(Double.toString(amount).getBytes())}),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.hincrByFloat(key, field, amount);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hkeys_returns_success() {
        // setup
        String key = "testKey";
        String[] args = {key};
        String[] values = new String[] {"field_1", "field_2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(values);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(HKeys), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.hkeys(key);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(values, payload);
    }

    @SneakyThrows
    @Test
    public void hkeys_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] args = {key};
        GlideString[] values = new GlideString[] {gs("field_1"), gs("field_2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(values);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(HKeys), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.hkeys(key);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(values, payload);
    }

    @SneakyThrows
    @Test
    public void hstrlen_returns_success() {
        // setup
        String key = "testKey";
        String field = "field";
        String[] args = {key, field};
        Long value = 42L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(HStrlen), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.hstrlen(key, field);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hrandfield_returns_success() {
        // setup
        String key = "testKey";
        String[] args = {key};
        String field = "field";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(field);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(HRandField), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.hrandfield(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(field, payload);
    }

    @SneakyThrows
    @Test
    public void hrandfieldWithCount_returns_success() {
        // setup
        String key = "testKey";
        String[] args = {key, "2"};
        String[] fields = new String[] {"field_1", "field_2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(fields);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(HRandField), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.hrandfieldWithCount(key, 2);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(fields, payload);
    }

    @SneakyThrows
    @Test
    public void hrandfieldWithCountWithValues_returns_success() {
        // setup
        String key = "testKey";
        String[] args = {key, "2", WITH_VALUES_VALKEY_API};
        String[][] fields = new String[][] {{"field_1", "value_1"}, {"field_2", "value_2"}};

        CompletableFuture<String[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(fields);

        // match on protobuf request
        when(commandManager.<String[][]>submitNewCommand(eq(HRandField), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[][]> response = service.hrandfieldWithCountWithValues(key, 2);
        String[][] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(fields, payload);
    }

    @SneakyThrows
    @Test
    public void lpush_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"value1", "value2"};
        String[] args = new String[] {key, "value1", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpush(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpush_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] elements = new GlideString[] {gs("value1"), gs("value2")};
        GlideString[] args = new GlideString[] {key, gs("value1"), gs("value2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpush(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpop_returns_success() {
        // setup
        String key = "testKey";
        String[] args = new String[] {key};
        String value = "value";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lpop(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpop_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] args = new GlideString[] {key};
        GlideString value = gs("value");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(LPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.lpop(key);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpopCount_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] args = new String[] {key, Long.toString(count)};
        String[] value = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(LPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.lpopCount(key, count);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpopCount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString[] args = new GlideString[] {key, gs(Long.toString(count))};
        GlideString[] value = new GlideString[] {gs("value1"), gs("value2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(LPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.lpopCount(key, count);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpos() {
        // setup
        String[] args = new String[] {"list", "element"};
        long index = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPos), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpos("list", "element");
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lpos_binary() {
        // setup
        GlideString[] args = new GlideString[] {gs("list"), gs("element")};
        long index = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPos), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpos(gs("list"), gs("element"));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lpos_withOptions() {
        // setup
        LPosOptions options = LPosOptions.builder().rank(1L).maxLength(1000L).build();
        String[] args = new String[] {"list", "element", "RANK", "1", "MAXLEN", "1000"};
        long index = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPos), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpos("list", "element", options);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lpos_withOptions_binary() {
        // setup
        LPosOptions options = LPosOptions.builder().rank(1L).maxLength(1000L).build();
        GlideString[] args =
                new GlideString[] {
                    gs("list"), gs("element"), gs("RANK"), gs("1"), gs("MAXLEN"), gs("1000")
                };
        long index = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPos), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpos(gs("list"), gs("element"), options);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lposCount() {
        // setup
        String[] args = new String[] {"list", "element", "COUNT", "1"};
        Long[] index = new Long[] {1L};

        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(LPos), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response = service.lposCount("list", "element", 1L);
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lposCount_binary() {
        // setup
        GlideString[] args = new GlideString[] {gs("list"), gs("element"), gs("COUNT"), gs("1")};
        Long[] index = new Long[] {1L};

        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(LPos), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response = service.lposCount(gs("list"), gs("element"), 1L);
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lposCount_withOptions() {
        // setup
        LPosOptions options = LPosOptions.builder().rank(1L).maxLength(1000L).build();
        String[] args = new String[] {"list", "element", "COUNT", "0", "RANK", "1", "MAXLEN", "1000"};
        Long[] index = new Long[] {0L};

        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(LPos), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response = service.lposCount("list", "element", 0L, options);
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lposCount_withOptions_binary() {
        // setup
        LPosOptions options = LPosOptions.builder().rank(1L).maxLength(1000L).build();
        GlideString[] args =
                new GlideString[] {
                    gs("list"),
                    gs("element"),
                    gs("COUNT"),
                    gs("0"),
                    gs("RANK"),
                    gs("1"),
                    gs("MAXLEN"),
                    gs("1000")
                };
        Long[] index = new Long[] {0L};

        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(index);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(LPos), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response = service.lposCount(gs("list"), gs("element"), 0L, options);
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(index, payload);
    }

    @SneakyThrows
    @Test
    public void lrange_returns_success() {
        // setup
        String key = "testKey";
        long start = 2L;
        long end = 4L;
        String[] args = new String[] {key, Long.toString(start), Long.toString(end)};
        String[] value = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(LRange), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.lrange(key, start, end);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lrange_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long start = 2L;
        long end = 4L;
        GlideString[] args = new GlideString[] {key, gs(Long.toString(start)), gs(Long.toString(end))};
        GlideString[] value = new GlideString[] {gs("value1"), gs("value2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(LRange), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.lrange(key, start, end);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lindex_returns_success() {
        // setup
        String key = "testKey";
        long index = 2;
        String[] args = new String[] {key, Long.toString(index)};
        String value = "value";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LIndex), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lindex(key, index);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lindex_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long index = 2;
        GlideString[] args = new GlideString[] {key, gs(Long.toString(index))};
        GlideString value = gs("value");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(LIndex), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.lindex(key, index);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void ltrim_returns_success() {
        // setup
        String key = "testKey";
        long start = 2L;
        long end = 2L;
        String[] args = new String[] {key, Long.toString(end), Long.toString(start)};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LTrim), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ltrim(key, start, end);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void ltrim_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long start = 2L;
        long end = 2L;
        GlideString[] args = new GlideString[] {key, gs(Long.toString(end)), gs(Long.toString(start))};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LTrim), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ltrim(key, start, end);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void llen_returns_success() {
        // setup
        String key = "testKey";
        String[] args = new String[] {key};
        long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LLen), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.llen(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lrem_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String element = "value";
        String[] args = new String[] {key, Long.toString(count), element};
        long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LRem), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lrem(key, count, element);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lrem_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString element = gs("value");
        GlideString[] args = new GlideString[] {key, gs(Long.toString(count).getBytes()), element};
        long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LRem), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lrem(key, count, element);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpush_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"value1", "value2"};
        String[] args = new String[] {key, "value1", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(RPush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.rpush(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpush_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] elements = new GlideString[] {gs("value1"), gs("value2")};
        GlideString[] args = new GlideString[] {key, gs("value1"), gs("value2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(RPush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.rpush(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpop_returns_success() {
        // setup
        String key = "testKey";
        String value = "value";
        String[] args = new String[] {key};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(RPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.rpop(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpop_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString value = gs("value");
        GlideString[] args = new GlideString[] {key};

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(RPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.rpop(key);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpopCount_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] args = new String[] {key, Long.toString(count)};
        String[] value = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(RPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.rpopCount(key, count);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpopCount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString[] args = new GlideString[] {key, gs(Long.toString(count))};
        GlideString[] value = new GlideString[] {gs("value1"), gs("value2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(RPop), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.rpopCount(key, count);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sadd_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"testMember1", "testMember2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sadd(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sadd_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = new GlideString[] {gs("testMember1"), gs("testMember2")};
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sadd(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sismember_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(SIsMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.sismember(key, member);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertTrue(payload);
    }

    @SneakyThrows
    @Test
    public void sismember_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member = gs("testMember");
        GlideString[] arguments = new GlideString[] {key, member};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(SIsMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.sismember(key, member);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertTrue(payload);
    }

    @SneakyThrows
    @Test
    public void srem_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"testMember1", "testMember2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SRem), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.srem(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void srem_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = new GlideString[] {gs("testMember1"), gs("testMember2")};
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SRem), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.srem(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void smembers_returns_success() {
        // setup
        String key = "testKey";
        Set<String> value = Set.of("testMember");

        CompletableFuture<Set<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<String>>submitNewCommand(eq(SMembers), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<String>> response = service.smembers(key);
        Set<String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scard_returns_success() {
        // setup
        String key = "testKey";
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SCard), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.scard(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scard_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SCard), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.scard(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sdiff_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        Set<String> value = Set.of("1", "2");

        CompletableFuture<Set<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<String>>submitNewCommand(eq(SDiff), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<String>> response = service.sdiff(keys);
        Set<String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sdiff_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        Set<GlideString> value = Set.of(gs("1"), gs("2"));

        CompletableFuture<Set<GlideString>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<GlideString>>submitNewCommand(eq(SDiff), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<GlideString>> response = service.sdiff(keys);
        Set<GlideString> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void smismember_returns_success() {
        // setup
        String key = "testKey";
        String[] members = {"1", "2"};
        String[] arguments = {"testKey", "1", "2"};
        Boolean[] value = {true, false};

        CompletableFuture<Boolean[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean[]>submitNewCommand(eq(SMIsMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean[]> response = service.smismember(key, members);
        Boolean[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void smismember_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = {gs("1"), gs("2")};
        GlideString[] arguments = {gs("testKey"), gs("1"), gs("2")};
        Boolean[] value = {true, false};

        CompletableFuture<Boolean[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean[]>submitNewCommand(eq(SMIsMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean[]> response = service.smismember(key, members);
        Boolean[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sdiffstore_returns_success() {
        // setup
        String destination = "dest";
        String[] keys = new String[] {"set1", "set2"};
        String[] arguments = {"dest", "set1", "set2"};

        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SDiffStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sdiffstore(destination, keys);

        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sdiffstore_binary_returns_success() {
        // setup
        GlideString destination = gs("dest");
        GlideString[] keys = new GlideString[] {gs("set1"), gs("set2")};
        GlideString[] arguments = {gs("dest"), gs("set1"), gs("set2")};

        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SDiffStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sdiffstore(destination, keys);

        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void smove_returns_success() {
        // setup
        String source = "src";
        String destination = "dst";
        String member = "elem";
        String[] arguments = {source, destination, member};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(SMove), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.smove(source, destination, member);

        // verify
        assertEquals(testResponse, response);
        assertTrue(response.get());
    }

    @SneakyThrows
    @Test
    public void smove_binary_returns_success() {
        // setup
        GlideString source = gs("src");
        GlideString destination = gs("dst");
        GlideString member = gs("elem");
        GlideString[] arguments = {source, destination, member};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(SMove), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.smove(source, destination, member);

        // verify
        assertEquals(testResponse, response);
        assertTrue(response.get());
    }

    @SneakyThrows
    @Test
    public void sinter_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        Set<String> value = Set.of("1", "2");

        CompletableFuture<Set<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<String>>submitNewCommand(eq(SInter), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<String>> response = service.sinter(keys);
        Set<String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sinter_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        Set<GlideString> value = Set.of(gs("1"), gs("2"));

        CompletableFuture<Set<GlideString>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<GlideString>>submitNewCommand(eq(SInter), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<GlideString>> response = service.sinter(keys);
        Set<GlideString> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sinterstore_returns_success() {
        // setup
        String destination = "key";
        String[] keys = new String[] {"set1", "set2"};
        String[] args = new String[] {"key", "set1", "set2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SInterStore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sinterstore(destination, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sinterstore_binary_returns_success() {
        // setup
        GlideString destination = gs("key");
        GlideString[] keys = new GlideString[] {gs("set1"), gs("set2")};
        GlideString[] args = new GlideString[] {gs("key"), gs("set1"), gs("set2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SInterStore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sinterstore(destination, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sunionstore_returns_success() {
        // setup
        String destination = "key";
        String[] keys = new String[] {"set1", "set2"};
        String[] args = new String[] {"key", "set1", "set2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SUnionStore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sunionstore(destination, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sunionstore_binary_returns_success() {
        // setup
        GlideString destination = gs("key");
        GlideString[] keys = new GlideString[] {gs("set1"), gs("set2")};
        GlideString[] args = new GlideString[] {gs("key"), gs("set1"), gs("set2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SUnionStore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sunionstore(destination, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_noOptions_returns_success() {
        // setup
        String key = "testKey";
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);
        String[] membersScoresArgs = convertMapToValueKeyStringArray(membersScores);
        String[] arguments = ArrayUtils.addFirst(membersScoresArgs, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zadd(key, membersScores, ZAddOptions.builder().build(), false);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_binary_noOptions_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Map<GlideString, Double> membersScores = new LinkedHashMap<>();
        membersScores.put(gs("testMember1"), 1.0);
        membersScores.put(gs("testMember2"), 2.0);
        GlideString[] membersScoresArgs = convertMapToValueKeyStringArrayBinary(membersScores);
        GlideString[] arguments = ArrayUtils.addFirst(membersScoresArgs, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zadd(key, membersScores, ZAddOptions.builder().build(), false);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_withOptions_returns_success() {
        // setup
        String key = "testKey";
        ZAddOptions options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);
        String[] membersScoresArgs = convertMapToValueKeyStringArray(membersScores);
        String[] arguments = ArrayUtils.addAll(new String[] {key}, options.toArgs());
        arguments = ArrayUtils.addAll(arguments, membersScoresArgs);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zadd(key, membersScores, options, false);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_binary_withOptions_returns_success() {
        // setup
        GlideString key = gs("testKey");
        ZAddOptions options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<GlideString, Double> membersScores = new LinkedHashMap<>();
        membersScores.put(gs("testMember1"), 1.0);
        membersScores.put(gs("testMember2"), 2.0);
        GlideString[] membersScoresArgs = convertMapToValueKeyStringArrayBinary(membersScores);
        GlideString[] arguments = ArrayUtils.addAll(new GlideString[] {key}, options.toArgsBinary());
        arguments = ArrayUtils.addAll(arguments, membersScoresArgs);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zadd(key, membersScores, options, false);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zadd_withIllegalArgument_throws_exception() {
        // setup
        String key = "testKey";
        ZAddOptions options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);

        assertThrows(
                IllegalArgumentException.class, () -> service.zadd(key, membersScores, options, false));
    }

    @SneakyThrows
    @Test
    public void zadd_binary_withIllegalArgument_throws_exception() {
        // setup
        GlideString key = gs("testKey");
        ZAddOptions options =
                ZAddOptions.builder()
                        .conditionalChange(ZAddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<GlideString, Double> membersScores = new LinkedHashMap<>();
        membersScores.put(gs("testMember1"), 1.0);
        membersScores.put(gs("testMember2"), 2.0);

        assertThrows(
                IllegalArgumentException.class, () -> service.zadd(key, membersScores, options, false));
    }

    @SneakyThrows
    @Test
    public void zaddIncr_noOptions_returns_success() {
        // setup
        String key = "testKey";
        String member = "member";
        double increment = 3.0;
        String[] arguments = new String[] {key, "INCR", Double.toString(increment), member};
        Double value = 3.0;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response =
                service.zaddIncr(key, member, increment, ZAddOptions.builder().build());
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zaddIncr_binary_noOptions_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member = gs("member");
        double increment = 3.0;
        GlideString[] arguments =
                new GlideString[] {key, gs("INCR"), gs(Double.toString(increment)), member};
        Double value = 3.0;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response =
                service.zaddIncr(key, member, increment, ZAddOptions.builder().build());
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zaddIncr_withOptions_returns_success() {
        // setup
        String key = "testKey";
        ZAddOptions options =
                ZAddOptions.builder()
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        String member = "member";
        double increment = 3.0;
        String[] arguments =
                concatenateArrays(
                        new String[] {key},
                        options.toArgs(),
                        new String[] {"INCR", Double.toString(increment), member});
        Double value = 3.0;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zaddIncr(key, member, increment, options);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zaddIncr_binary_withOptions_returns_success() {
        // setup
        GlideString key = gs("testKey");
        ZAddOptions options =
                ZAddOptions.builder()
                        .updateOptions(ZAddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        GlideString member = gs("member");
        double increment = 3.0;
        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {key},
                        options.toArgsBinary(),
                        new GlideString[] {gs("INCR"), gs(Double.toString(increment)), member});
        Double value = 3.0;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zaddIncr(key, member, increment, options);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zmpop_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        ScoreFilter modifier = MAX;
        String[] arguments = {"2", "key1", "key2", "MAX"};
        Map<String, Object> value = Map.of("key1", "elem");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(ZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.zmpop(keys, modifier);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zmpop_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        ScoreFilter modifier = MAX;
        GlideString[] arguments = {gs("2"), gs("key1"), gs("key2"), gs("MAX")};
        Map<GlideString, Object> value = Map.of(gs("key1"), "elem");

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(eq(ZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response = service.zmpop(keys, modifier);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zmpop_with_count_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        ScoreFilter modifier = MAX;
        long count = 42;
        String[] arguments = {"2", "key1", "key2", "MAX", "COUNT", "42"};
        Map<String, Object> value = Map.of("key1", "elem");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(ZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.zmpop(keys, modifier, count);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zmpop_binary_with_count_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        ScoreFilter modifier = MAX;
        long count = 42;
        GlideString[] arguments = {gs("2"), gs("key1"), gs("key2"), gs("MAX"), gs("COUNT"), gs("42")};
        Map<GlideString, Object> value = Map.of(gs("key1"), "elem");

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(eq(ZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response = service.zmpop(keys, modifier, count);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzmpop_returns_success() {
        // setup
        double timeout = .5;
        String[] keys = new String[] {"key1", "key2"};
        ScoreFilter modifier = MAX;
        String[] arguments = {"0.5", "2", "key1", "key2", "MAX"};
        Map<String, Object> value = Map.of("key1", "elem");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(BZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.bzmpop(keys, modifier, timeout);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzmpop_binary_returns_success() {
        // setup
        double timeout = .5;
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        ScoreFilter modifier = MAX;
        GlideString[] arguments = {gs("0.5"), gs("2"), gs("key1"), gs("key2"), gs("MAX")};
        Map<GlideString, Object> value = Map.of(gs("key1"), "elem");

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(
                        eq(BZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response = service.bzmpop(keys, modifier, timeout);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzmpop_with_count_returns_success() {
        // setup
        double timeout = .5;
        String[] keys = new String[] {"key1", "key2"};
        ScoreFilter modifier = MAX;
        long count = 42;
        String[] arguments = {"0.5", "2", "key1", "key2", "MAX", "COUNT", "42"};
        Map<String, Object> value = Map.of("key1", "elem");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(BZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response =
                service.bzmpop(keys, modifier, timeout, count);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzmpop_binary_with_count_returns_success() {
        // setup
        double timeout = .5;
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        ScoreFilter modifier = MAX;
        long count = 42;
        GlideString[] arguments = {
            gs("0.5"), gs("2"), gs("key1"), gs("key2"), gs("MAX"), gs("COUNT"), gs("42")
        };
        Map<GlideString, Object> value = Map.of(gs("key1"), "elem");

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(
                        eq(BZMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response =
                service.bzmpop(keys, modifier, timeout, count);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void clientId_returns_success() {
        // setup
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(42L);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ClientId), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.clientId();

        // verify
        assertEquals(testResponse, response);
        assertEquals(42L, response.get());
    }

    @SneakyThrows
    @Test
    public void clientGetName_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete("TEST");

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ClientGetName), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.clientGetName();

        // verify
        assertEquals(testResponse, response);
        assertEquals("TEST", response.get());
    }

    @SneakyThrows
    @Test
    public void configRewrite_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ConfigRewrite), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configRewrite();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void configResetStat_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ConfigResetStat), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configResetStat();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void configGet_returns_success() {
        // setup
        Map<String, String> testPayload = Map.of("timeout", "1000");
        CompletableFuture<Map<String, String>> testResponse = new CompletableFuture<>();
        testResponse.complete(testPayload);

        // match on protobuf request
        when(commandManager.<Map<String, String>>submitNewCommand(
                        eq(ConfigGet), eq(new String[] {"timeout"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String>> response = service.configGet(new String[] {"timeout"});
        Map<String, String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void configSet_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(ConfigSet), eq(new String[] {"timeout", "1000"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.configSet(Map.of("timeout", "1000"));

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void zrem_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"member1", "member2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRem), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrem(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrem_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = new GlideString[] {gs("member1"), gs("member2")};
        GlideString[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRem), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrem(key, members);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zcard_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zcard(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zcard_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zcard(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmin_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        Map<String, Double> value = Map.of("member1", 2.5);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZPopMin), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zpopmin(key);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmin_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        Map<GlideString, Double> value = Map.of(gs("member1"), 2.5);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZPopMin), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response = service.zpopmin(key);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmin_with_count_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] arguments = new String[] {key, Long.toString(count)};
        Map<String, Double> value = Map.of("member1", 2.0, "member2", 3.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZPopMin), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zpopmin(key, count);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmin_with_count_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        Map<GlideString, Double> value = Map.of(gs("member1"), 2.0, gs("member2"), 3.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZPopMin), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response = service.zpopmin(key, count);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzpopmin_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        double timeout = .5;
        String[] arguments = new String[] {"key1", "key2", "0.5"};
        Object[] value = new Object[] {"key1", "elem", 42.};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(BZPopMin), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.bzpopmin(keys, timeout);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzpopmin_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        double timeout = .5;
        GlideString[] arguments = new GlideString[] {gs("key1"), gs("key2"), gs("0.5")};
        Object[] value = new Object[] {"key1", "elem", 42.};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(BZPopMin), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.bzpopmin(keys, timeout);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmax_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        Map<String, Double> value = Map.of("member1", 2.5);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zpopmax(key);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmax_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        Map<GlideString, Double> value = Map.of(gs("member1"), 2.5);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response = service.zpopmax(key);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzpopmax_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        double timeout = .5;
        String[] arguments = new String[] {"key1", "key2", "0.5"};
        Object[] value = new Object[] {"key1", "elem", 42.};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(BZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.bzpopmax(keys, timeout);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bzpopmax_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        double timeout = .5;
        GlideString[] arguments = new GlideString[] {gs("key1"), gs("key2"), gs("0.5")};
        Object[] value = new Object[] {"key1", "elem", 42.};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(BZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.bzpopmax(keys, timeout);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmax_with_count_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] arguments = new String[] {key, Long.toString(count)};
        Map<String, Double> value = Map.of("member1", 3.0, "member2", 1.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zpopmax(key, count);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zpopmax_with_count_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        Map<GlideString, Double> value = Map.of(gs("member1"), 3.0, gs("member2"), 1.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZPopMax), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response = service.zpopmax(key, count);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zscore_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member};
        Double value = 3.5;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZScore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zscore(key, member);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zscore_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member = gs("testMember");
        GlideString[] arguments = new GlideString[] {key, member};
        Double value = 3.5;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZScore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zscore(key, member);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrange_by_index_returns_success() {
        // setup
        String key = "testKey";
        RangeByIndex rangeByIndex = new RangeByIndex(0, 1);
        String[] arguments = new String[] {key, rangeByIndex.getStart(), rangeByIndex.getEnd()};
        String[] value = new String[] {"one", "two"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zrange(key, rangeByIndex);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrange_binary_by_index_returns_success() {
        // setup
        GlideString key = gs("testKey");
        RangeByIndex rangeByIndex = new RangeByIndex(0, 1);
        GlideString[] arguments =
                new GlideString[] {key, gs(rangeByIndex.getStart()), gs(rangeByIndex.getEnd())};
        GlideString[] value = new GlideString[] {gs("one"), gs("two")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zrange(key, rangeByIndex);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrange_by_score_with_reverse_returns_success() {
        // setup
        String key = "testKey";
        RangeByScore rangeByScore =
                new RangeByScore(new ScoreBoundary(3, false), InfScoreBound.NEGATIVE_INFINITY);
        String[] arguments =
                new String[] {key, rangeByScore.getStart(), rangeByScore.getEnd(), "BYSCORE", "REV"};
        String[] value = new String[] {"two", "one"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zrange(key, rangeByScore, true);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrange_binary_by_score_with_reverse_returns_success() {
        // setup
        GlideString key = gs("testKey");
        RangeByScore rangeByScore =
                new RangeByScore(new ScoreBoundary(3, false), InfScoreBound.NEGATIVE_INFINITY);
        GlideString[] arguments =
                new GlideString[] {
                    key, gs(rangeByScore.getStart()), gs(rangeByScore.getEnd()), gs("BYSCORE"), gs("REV")
                };
        GlideString[] value = new GlideString[] {gs("two"), gs("one")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zrange(key, rangeByScore, true);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrange_by_lex_returns_success() {
        // setup
        String key = "testKey";
        RangeByLex rangeByLex =
                new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        String[] arguments = new String[] {key, rangeByLex.getStart(), rangeByLex.getEnd(), "BYLEX"};
        String[] value = new String[] {"a", "b"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zrange(key, rangeByLex);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrange_binary_by_lex_returns_success() {
        // setup
        GlideString key = gs("testKey");
        RangeByLex rangeByLex =
                new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        GlideString[] arguments =
                new GlideString[] {key, gs(rangeByLex.getStart()), gs(rangeByLex.getEnd()), gs("BYLEX")};
        GlideString[] value = new GlideString[] {gs("a"), gs("b")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zrange(key, rangeByLex);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangeWithScores_by_index_returns_success() {
        // setup
        String key = "testKey";
        RangeByIndex rangeByIndex = new RangeByIndex(0, 4);
        String[] arguments =
                new String[] {key, rangeByIndex.getStart(), rangeByIndex.getEnd(), WITH_SCORES_VALKEY_API};
        Map<String, Double> value = Map.of("one", 1.0, "two", 2.0, "three", 3.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zrangeWithScores(key, rangeByIndex);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangeWithScores_binary_by_index_returns_success() {
        // setup
        GlideString key = gs("testKey");
        RangeByIndex rangeByIndex = new RangeByIndex(0, 4);
        GlideString[] arguments =
                new GlideString[] {
                    key, gs(rangeByIndex.getStart()), gs(rangeByIndex.getEnd()), gs(WITH_SCORES_VALKEY_API)
                };
        Map<GlideString, Double> value = Map.of(gs("one"), 1.0, gs("two"), 2.0, gs("three"), 3.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response =
                service.zrangeWithScores(key, rangeByIndex);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangeWithScores_by_score_returns_success() {
        // setup
        String key = "testKey";
        RangeByScore rangeByScore =
                new RangeByScore(
                        InfScoreBound.NEGATIVE_INFINITY,
                        InfScoreBound.POSITIVE_INFINITY,
                        new RangeOptions.Limit(1, 2));
        String[] arguments =
                new String[] {
                    key,
                    rangeByScore.getStart(),
                    rangeByScore.getEnd(),
                    "BYSCORE",
                    "LIMIT",
                    "1",
                    "2",
                    WITH_SCORES_VALKEY_API
                };
        Map<String, Double> value = Map.of("two", 2.0, "three", 3.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response =
                service.zrangeWithScores(key, rangeByScore, false);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangeWithScores_binary_by_score_returns_success() {
        // setup
        GlideString key = gs("testKey");
        RangeByScore rangeByScore =
                new RangeByScore(
                        InfScoreBound.NEGATIVE_INFINITY,
                        InfScoreBound.POSITIVE_INFINITY,
                        new RangeOptions.Limit(1, 2));
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    gs(rangeByScore.getStart()),
                    gs(rangeByScore.getEnd()),
                    gs("BYSCORE"),
                    gs("LIMIT"),
                    gs("1"),
                    gs("2"),
                    gs(WITH_SCORES_VALKEY_API)
                };
        Map<GlideString, Double> value = Map.of(gs("two"), 2.0, gs("three"), 3.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZRange), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response =
                service.zrangeWithScores(key, rangeByScore, false);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrank_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrank(key, member);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrank_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member = gs("testMember");
        GlideString[] arguments = new GlideString[] {key, member};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrank(key, member);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrankWithScore_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member, WITH_SCORE_VALKEY_API};
        Object[] value = new Object[] {1, 6.0};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.zrankWithScore(key, member);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrankWithScore_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member = gs("testMember");
        GlideString[] arguments = new GlideString[] {key, member, gs(WITH_SCORE_VALKEY_API)};
        Object[] value = new Object[] {1, 6.0};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.zrankWithScore(key, member);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrevrank_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRevRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrevrank(key, member);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrevrankWithScore_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member, WITH_SCORE_VALKEY_API};
        Object[] value = new Object[] {1, 6.0};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZRevRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.zrevrankWithScore(key, member);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zmscore_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"member1", "member2"};
        String[] arguments = new String[] {key, "member1", "member2"};
        Double[] value = new Double[] {2.5, 8.2};

        CompletableFuture<Double[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double[]>submitNewCommand(eq(ZMScore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double[]> response = service.zmscore(key, members);
        Double[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zmscore_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = new GlideString[] {gs("member1"), gs("member2")};
        GlideString[] arguments = new GlideString[] {key, gs("member1"), gs("member2")};
        Double[] value = new Double[] {2.5, 8.2};

        CompletableFuture<Double[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double[]>submitNewCommand(eq(ZMScore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double[]> response = service.zmscore(key, members);
        Double[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zdiffstore_returns_success() {
        // setup
        String destKey = "testDestKey";
        String[] keys = new String[] {"testKey1", "testKey2"};
        String[] arguments = new String[] {destKey, Long.toString(keys.length), "testKey1", "testKey2"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZDiffStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zdiffstore(destKey, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zdiffstore_binary_returns_success() {
        // setup
        GlideString destKey = gs("testDestKey");
        GlideString[] keys = new GlideString[] {gs("testKey1"), gs("testKey2")};
        GlideString[] arguments =
                new GlideString[] {
                    destKey, gs(Long.toString(keys.length).getBytes()), gs("testKey1"), gs("testKey2")
                };
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZDiffStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zdiffstore(destKey, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zdiff_returns_success() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {"2", key1, key2};
        String[] value = new String[] {"element1"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZDiff), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zdiff(new String[] {key1, key2});
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zdiff_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {gs("2"), key1, key2};
        GlideString[] value = new GlideString[] {gs("element1")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZDiff), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zdiff(new GlideString[] {key1, key2});
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zdiffWithScores_returns_success() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {"2", key1, key2, WITH_SCORES_VALKEY_API};
        Map<String, Double> value = Map.of("element1", 2.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZDiff), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response =
                service.zdiffWithScores(new String[] {key1, key2});
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zdiffWithScores_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {gs("2"), key1, key2, gs(WITH_SCORES_VALKEY_API)};
        Map<GlideString, Double> value = Map.of(gs("element1"), 2.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(eq(ZDiff), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response =
                service.zdiffWithScores(new GlideString[] {key1, key2});
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zcount_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key, "-inf", "10.0"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZCount), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zcount(key, InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(10, true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zcount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key, gs("-inf"), gs("10.0")};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZCount), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zcount(key, InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(10, true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zremrangebyrank_returns_success() {
        // setup
        String key = "testKey";
        long start = 0;
        long end = -1;
        String[] arguments = new String[] {key, Long.toString(start), Long.toString(end)};
        Long value = 5L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRemRangeByRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zremrangebyrank(key, start, end);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zremrangebyrank_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long start = 0;
        long end = -1;
        GlideString[] arguments =
                new GlideString[] {
                    key, gs(Long.toString(start).getBytes()), gs(Long.toString(end).getBytes())
                };
        Long value = 5L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRemRangeByRank), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zremrangebyrank(key, start, end);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zremrangebylex_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key, "-", "[z"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRemRangeByLex), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zremrangebylex(key, InfLexBound.NEGATIVE_INFINITY, new LexBoundary("z", true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zremrangebylex_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key, gs("-"), gs("[z")};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRemRangeByLex), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zremrangebylex(key, InfLexBound.NEGATIVE_INFINITY, new LexBoundary("z", true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zremrangebyscore_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key, "-inf", "10.0"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRemRangeByScore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zremrangebyscore(key, InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(10, true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zremrangebyscore_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key, gs("-inf"), gs("10.0")};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRemRangeByScore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zremrangebyscore(key, InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(10, true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zlexcount_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key, "-", "[c"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZLexCount), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zlexcount(key, InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zlexcount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key, gs("-"), gs("[c")};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZLexCount), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zlexcount(key, InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", true));
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangestore_by_lex_returns_success() {
        // setup
        String source = "testSourceKey";
        String destination = "testDestinationKey";
        RangeByLex rangeByLex =
                new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        String[] arguments =
                new String[] {source, destination, rangeByLex.getStart(), rangeByLex.getEnd(), "BYLEX"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRangeStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrangestore(source, destination, rangeByLex);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangestore_binary_by_lex_returns_success() {
        // setup
        GlideString source = gs("testSourceKey");
        GlideString destination = gs("testDestinationKey");
        RangeByLex rangeByLex =
                new RangeByLex(InfLexBound.NEGATIVE_INFINITY, new LexBoundary("c", false));
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(rangeByLex.getStart()), gs(rangeByLex.getEnd()), gs("BYLEX")
                };
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRangeStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrangestore(source, destination, rangeByLex);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangestore_by_index_returns_success() {
        // setup
        String source = "testSourceKey";
        String destination = "testDestinationKey";
        RangeByIndex rangeByIndex = new RangeByIndex(0, 1);
        String[] arguments =
                new String[] {source, destination, rangeByIndex.getStart(), rangeByIndex.getEnd()};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRangeStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrangestore(source, destination, rangeByIndex);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangestore_binary_by_index_returns_success() {
        // setup
        GlideString source = gs("testSourceKey");
        GlideString destination = gs("testDestinationKey");
        RangeByIndex rangeByIndex = new RangeByIndex(0, 1);
        GlideString[] arguments =
                new GlideString[] {
                    source, destination, gs(rangeByIndex.getStart()), gs(rangeByIndex.getEnd())
                };
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRangeStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zrangestore(source, destination, rangeByIndex);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangestore_by_score_with_reverse_returns_success() {
        // setup
        String source = "testSourceKey";
        String destination = "testDestinationKey";
        RangeByScore rangeByScore =
                new RangeByScore(new ScoreBoundary(3, false), InfScoreBound.NEGATIVE_INFINITY);
        boolean reversed = true;
        String[] arguments =
                new String[] {
                    source, destination, rangeByScore.getStart(), rangeByScore.getEnd(), "BYSCORE", "REV"
                };
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRangeStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zrangestore(source, destination, rangeByScore, reversed);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrangestore_binary_by_score_with_reverse_returns_success() {
        // setup
        GlideString source = gs("testSourceKey");
        GlideString destination = gs("testDestinationKey");
        RangeByScore rangeByScore =
                new RangeByScore(new ScoreBoundary(3, false), InfScoreBound.NEGATIVE_INFINITY);
        boolean reversed = true;
        GlideString[] arguments =
                new GlideString[] {
                    source,
                    destination,
                    gs(rangeByScore.getStart()),
                    gs(rangeByScore.getEnd()),
                    gs("BYSCORE"),
                    gs("REV")
                };
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZRangeStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zrangestore(source, destination, rangeByScore, reversed);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunion_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        KeyArray keyArray = new KeyArray(keys);
        String[] arguments = keyArray.toArgs();
        String[] value = new String[] {"elem1", "elem2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZUnion), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zunion(keyArray);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunion_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        KeyArrayBinary keyArray = new KeyArrayBinary(keys);
        GlideString[] arguments = keyArray.toArgs();
        GlideString[] value = new GlideString[] {gs("elem1"), gs("elem2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZUnion), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zunion(keyArray);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionstore_returns_success() {
        // setup
        String destination = "destinationKey";
        String[] keys = new String[] {"key1", "key2"};
        KeyArray keyArray = new KeyArray(keys);
        String[] arguments = concatenateArrays(new String[] {destination}, keyArray.toArgs());
        Long value = 5L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZUnionStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zunionstore(destination, keyArray);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionstore_binary_returns_success() {
        // setup
        GlideString destination = gs("destinationKey");
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        KeyArrayBinary keyArray = new KeyArrayBinary(keys);
        GlideString[] arguments = concatenateArrays(new GlideString[] {destination}, keyArray.toArgs());
        Long value = 5L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZUnionStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zunionstore(destination, keyArray);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionstore_with_options_returns_success() {
        // setup
        String destination = "destinationKey";
        String[] keys = new String[] {"key1", "key2"};
        List<Pair<String, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of("key1", 10.0));
        keysWeights.add(Pair.of("key2", 20.0));
        WeightedKeys weightedKeys = new WeightedKeys(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        String[] arguments =
                concatenateArrays(new String[] {destination}, weightedKeys.toArgs(), aggregate.toArgs());
        Long value = 5L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZUnionStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zunionstore(destination, weightedKeys, aggregate);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionstore_with_options_binary_returns_success() {
        // setup
        GlideString destination = gs("destinationKey");
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        List<Pair<GlideString, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of(gs("key1"), 10.0));
        keysWeights.add(Pair.of(gs("key2"), 20.0));
        WeightedKeysBinary weightedKeys = new WeightedKeysBinary(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(weightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .toArray();
        Long value = 5L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZUnionStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zunionstore(destination, weightedKeys, aggregate);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionWithScores_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        KeyArray keyArray = new KeyArray(keys);
        String[] arguments =
                concatenateArrays(keyArray.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        Map<String, Double> value = Map.of("elem1", 1.0, "elem2", 2.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZUnion), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zunionWithScores(keyArray);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionWithScores_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        KeyArrayBinary keyArray = new KeyArrayBinary(keys);
        GlideString[] arguments =
                concatenateArrays(keyArray.toArgs(), new GlideString[] {gs(WITH_SCORES_VALKEY_API)});
        Map<GlideString, Double> value = Map.of(gs("elem1"), 1.0, gs("elem2"), 2.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZUnion), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response = service.zunionWithScores(keyArray);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionWithScores_with_options_returns_success() {
        // setup
        List<Pair<String, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of("key1", 10.0));
        keysWeights.add(Pair.of("key2", 20.0));
        WeightedKeys weightedKeys = new WeightedKeys(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        String[] arguments =
                concatenateArrays(
                        weightedKeys.toArgs(), aggregate.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        Map<String, Double> value = Map.of("elem1", 1.0, "elem2", 2.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZUnion), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response =
                service.zunionWithScores(weightedKeys, aggregate);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zunionWithScores_with_options_binary_returns_success() {
        // setup
        List<Pair<GlideString, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of(gs("key1"), 10.0));
        keysWeights.add(Pair.of(gs("key2"), 20.0));
        WeightedKeysBinary weightedKeys = new WeightedKeysBinary(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(weightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .add(WITH_SCORES_VALKEY_API)
                        .toArray();
        Map<GlideString, Double> value = Map.of(gs("elem1"), 1.0, gs("elem2"), 2.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZUnion), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response =
                service.zunionWithScores(weightedKeys, aggregate);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinter_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        KeyArray keyArray = new KeyArray(keys);
        String[] arguments = keyArray.toArgs();
        String[] value = new String[] {"elem1", "elem2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZInter), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zinter(keyArray);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinter_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        KeyArrayBinary keyArray = new KeyArrayBinary(keys);
        GlideString[] arguments = keyArray.toArgs();
        GlideString[] value = new GlideString[] {gs("elem1"), gs("elem2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZInter), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zinter(keyArray);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterWithScores_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        KeyArray keyArray = new KeyArray(keys);
        String[] arguments =
                concatenateArrays(keyArray.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        Map<String, Double> value = Map.of("elem1", 1.0, "elem2", 2.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZInter), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response = service.zinterWithScores(keyArray);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterWithScores_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        KeyArrayBinary keyArray = new KeyArrayBinary(keys);
        GlideString[] arguments =
                concatenateArrays(keyArray.toArgs(), new GlideString[] {gs(WITH_SCORES_VALKEY_API)});
        Map<GlideString, Double> value = Map.of(gs("elem1"), 1.0, gs("elem2"), 2.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZInter), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response = service.zinterWithScores(keyArray);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterWithScores_with_aggregation_returns_success() {
        // setup
        List<Pair<String, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of("key1", 10.0));
        keysWeights.add(Pair.of("key2", 20.0));
        WeightedKeys weightedKeys = new WeightedKeys(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        String[] arguments =
                concatenateArrays(
                        weightedKeys.toArgs(), aggregate.toArgs(), new String[] {WITH_SCORES_VALKEY_API});
        Map<String, Double> value = Map.of("elem1", 1.0, "elem2", 2.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(ZInter), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Double>> response =
                service.zinterWithScores(weightedKeys, aggregate);
        Map<String, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterWithScores_binary_with_aggregation_returns_success() {
        // setup
        List<Pair<GlideString, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of(gs("key1"), 10.0));
        keysWeights.add(Pair.of(gs("key2"), 20.0));
        WeightedKeysBinary weightedKeys = new WeightedKeysBinary(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(weightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .add(WITH_SCORES_VALKEY_API)
                        .toArray();
        Map<GlideString, Double> value = Map.of(gs("elem1"), 1.0, gs("elem2"), 2.0);

        CompletableFuture<Map<GlideString, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Double>>submitNewCommand(
                        eq(ZInter), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Double>> response =
                service.zinterWithScores(weightedKeys, aggregate);
        Map<GlideString, Double> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterstore_returns_success() {
        // setup
        String destination = "destinationKey";
        String[] keys = new String[] {"key1", "key2"};
        KeyArray keyArray = new KeyArray(keys);
        String[] arguments = concatenateArrays(new String[] {destination}, keyArray.toArgs());
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zinterstore(destination, keyArray);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterstore_binary_returns_success() {
        // setup
        GlideString destination = gs("destinationKey");
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        KeyArrayBinary keyArray = new KeyArrayBinary(keys);
        GlideString[] arguments = concatenateArrays(new GlideString[] {destination}, keyArray.toArgs());
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zinterstore(destination, keyArray);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterstore_with_options_returns_success() {
        // setup
        String destination = "destinationKey";
        List<Pair<String, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of("key1", 10.0));
        keysWeights.add(Pair.of("key2", 20.0));
        WeightedKeys weightedKeys = new WeightedKeys(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        String[] arguments =
                concatenateArrays(new String[] {destination}, weightedKeys.toArgs(), aggregate.toArgs());
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zinterstore(destination, weightedKeys, aggregate);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zinterstore_binary_with_options_returns_success() {
        // setup
        GlideString destination = gs("destinationKey");
        List<Pair<GlideString, Double>> keysWeights = new ArrayList<>();
        keysWeights.add(Pair.of(gs("key1"), 10.0));
        keysWeights.add(Pair.of(gs("key2"), 20.0));
        WeightedKeysBinary weightedKeys = new WeightedKeysBinary(keysWeights);
        Aggregate aggregate = Aggregate.MIN;
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(destination)
                        .add(weightedKeys.toArgs())
                        .add(aggregate.toArgs())
                        .toArray();
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterStore), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zinterstore(destination, weightedKeys, aggregate);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zintercard_with_limit_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        long limit = 3L;
        String[] arguments = new String[] {"2", "key1", "key2", LIMIT_VALKEY_API, "3"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zintercard(keys, limit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zintercard_with_limit_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        long limit = 3L;
        GlideString[] arguments =
                new GlideString[] {gs("2"), gs("key1"), gs("key2"), gs(LIMIT_VALKEY_API), gs("3")};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zintercard(keys, limit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zintercard_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        String[] arguments = new String[] {"2", "key1", "key2"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zintercard(keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zintercard_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        GlideString[] arguments = new GlideString[] {gs("2"), gs("key1"), gs("key2")};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ZInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.zintercard(keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrandmember_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        String value = "testValue";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ZRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.zrandmember(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrandmember_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        GlideString value = gs("testValue");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(ZRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.zrandmember(key);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrandmemberWithCount_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] arguments = new String[] {key, Long.toString(count)};
        String[] value = new String[] {"member1", "member2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(ZRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.zrandmemberWithCount(key, count);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrandmemberWithCount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        GlideString[] value = new GlideString[] {gs("member1"), gs("member2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(ZRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.zrandmemberWithCount(key, count);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrandmemberWithCountWithScores_returns_success() {
        // setup
        String key = "testKey";
        long count = 2L;
        String[] arguments = new String[] {key, Long.toString(count), WITH_SCORES_VALKEY_API};
        Object[][] value = new Object[][] {{"member1", 2.0}, {"member2", 3.0}};

        CompletableFuture<Object[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[][]>submitNewCommand(eq(ZRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[][]> response = service.zrandmemberWithCountWithScores(key, count);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zrandmemberWithCountWithScores_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2L;
        GlideString[] arguments =
                new GlideString[] {key, gs(Long.toString(count)), gs(WITH_SCORES_VALKEY_API)};
        Object[][] value = new Object[][] {{gs("member1"), 2.0}, {gs("member2"), 3.0}};

        CompletableFuture<Object[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[][]>submitNewCommand(eq(ZRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[][]> response = service.zrandmemberWithCountWithScores(key, count);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zincrby_returns_success() {
        // setup
        String key = "testKey";
        double increment = 4.2;
        String member = "member";
        String[] arguments = new String[] {key, "4.2", member};
        Double value = 3.14;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZIncrBy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zincrby(key, increment, member);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zincrby_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        double increment = 4.2;
        GlideString member = gs("member");
        GlideString[] arguments = new GlideString[] {key, gs("4.2"), member};
        Double value = 3.14;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(ZIncrBy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.zincrby(key, increment, member);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void xadd_returns_success() {
        // setup
        String key = "testKey";
        Map<String, String> fieldValues = new LinkedHashMap<>();
        fieldValues.put("testField1", "testValue1");
        fieldValues.put("testField2", "testValue2");
        String[] fieldValuesArgs = convertMapToKeyValueStringArray(fieldValues);
        String[] arguments = new String[] {key, "*"};
        arguments = ArrayUtils.addAll(arguments, fieldValuesArgs);
        String returnId = "testId";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xadd(key, fieldValues);

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, response.get());
    }

    @SneakyThrows
    @Test
    public void xadd_nested_array_returns_success() {
        // setup
        String key = "testKey";
        String[][] fieldValues = {{"testField1", "testValue1"}, {"testField2", "testValue2"}};
        String[] fieldValuesArgs = convertNestedArrayToKeyValueStringArray(fieldValues);
        String[] arguments = new String[] {key, "*"};
        arguments = ArrayUtils.addAll(arguments, fieldValuesArgs);
        String returnId = "testId";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xadd(key, fieldValues);

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, response.get());
    }

    @SneakyThrows
    @Test
    public void xadd_nested_array_with_options_returns_success() {
        // setup
        String key = "testKey";
        String[][] fieldValues = {{"testField1", "testValue1"}, {"testField2", "testValue2"}};
        String[] fieldValuesArgs = convertNestedArrayToKeyValueStringArray(fieldValues);
        String[] arguments = new String[] {key, "*"};
        arguments = ArrayUtils.addAll(arguments, fieldValuesArgs);
        String returnId = "testId";
        StreamAddOptions options = StreamAddOptions.builder().build();

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xadd(key, fieldValues, options);

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, response.get());
    }

    @SneakyThrows
    @Test
    public void xadd_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Map<GlideString, GlideString> fieldValues = new LinkedHashMap<>();
        fieldValues.put(gs("testField1"), gs("testValue1"));
        fieldValues.put(gs("testField2"), gs("testValue2"));
        GlideString[] fieldValuesArgs = convertMapToKeyValueGlideStringArray(fieldValues);
        GlideString[] arguments = new GlideString[] {key, gs("*")};
        arguments = ArrayUtils.addAll(arguments, fieldValuesArgs);
        GlideString returnId = gs("testId");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.xadd(key, fieldValues);

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, response.get());
    }

    @SneakyThrows
    @Test
    public void xadd_nested_array_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[][] fieldValues = {
            {gs("testField1"), gs("testValue1")}, {gs("testField2"), gs("testValue2")}
        };
        GlideString[] fieldValuesArgs = convertNestedArrayToKeyValueGlideStringArray(fieldValues);
        GlideString[] arguments = new GlideString[] {key, gs("*")};
        arguments = ArrayUtils.addAll(arguments, fieldValuesArgs);
        GlideString returnId = gs("testId");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.xadd(key, fieldValues);

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, response.get());
    }

    @SneakyThrows
    @Test
    public void xadd_nested_array_with_options_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[][] fieldValues = {
            {gs("testField1"), gs("testValue1")}, {gs("testField2"), gs("testValue2")}
        };
        GlideString[] fieldValuesArgs = convertNestedArrayToKeyValueGlideStringArray(fieldValues);
        GlideString[] arguments = new GlideString[] {key, gs("*")};
        arguments = ArrayUtils.addAll(arguments, fieldValuesArgs);
        GlideString returnId = gs("testId");
        StreamAddOptionsBinary options = StreamAddOptionsBinary.builder().build();

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.xadd(key, fieldValues, options);

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, response.get());
    }

    private static List<Arguments> getStreamAddOptions() {
        return List.of(
                Arguments.of(
                        // no TRIM option
                        "test_xadd_no_trim",
                        StreamAddOptions.builder().id("id").makeStream(Boolean.FALSE).build(),
                        new String[] {NO_MAKE_STREAM_VALKEY_API, "id"}),
                Arguments.of(
                        // MAXLEN with LIMIT
                        "test_xadd_maxlen_with_limit",
                        StreamAddOptions.builder()
                                .id("id")
                                .makeStream(Boolean.TRUE)
                                .trim(new MaxLen(5L, 10L))
                                .build(),
                        new String[] {
                            TRIM_MAXLEN_VALKEY_API,
                            TRIM_NOT_EXACT_VALKEY_API,
                            Long.toString(5L),
                            TRIM_LIMIT_VALKEY_API,
                            Long.toString(10L),
                            "id"
                        }),
                Arguments.of(
                        // MAXLEN with non exact match
                        "test_xadd_maxlen_with_non_exact_match",
                        StreamAddOptions.builder()
                                .makeStream(Boolean.FALSE)
                                .trim(new MaxLen(false, 2L))
                                .build(),
                        new String[] {
                            NO_MAKE_STREAM_VALKEY_API,
                            TRIM_MAXLEN_VALKEY_API,
                            TRIM_NOT_EXACT_VALKEY_API,
                            Long.toString(2L),
                            "*"
                        }),
                Arguments.of(
                        // MIN ID with LIMIT
                        "test_xadd_minid_with_limit",
                        StreamAddOptions.builder()
                                .id("id")
                                .makeStream(Boolean.TRUE)
                                .trim(new MinId("testKey", 10L))
                                .build(),
                        new String[] {
                            TRIM_MINID_VALKEY_API,
                            TRIM_NOT_EXACT_VALKEY_API,
                            "testKey",
                            TRIM_LIMIT_VALKEY_API,
                            Long.toString(10L),
                            "id"
                        }),
                Arguments.of(
                        // MIN ID with non-exact match
                        "test_xadd_minid_with_non_exact_match",
                        StreamAddOptions.builder()
                                .makeStream(Boolean.FALSE)
                                .trim(new MinId(false, "testKey"))
                                .build(),
                        new String[] {
                            NO_MAKE_STREAM_VALKEY_API,
                            TRIM_MINID_VALKEY_API,
                            TRIM_NOT_EXACT_VALKEY_API,
                            "testKey",
                            "*"
                        }));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("getStreamAddOptions")
    public void xadd_with_options_to_arguments(
            String testName, StreamAddOptions options, String[] expectedArgs) {
        assertArrayEquals(
                expectedArgs, options.toArgs(), "Expected " + testName + " toArgs() to pass.");
    }

    @SneakyThrows
    @Test
    public void xadd_with_nomakestream_maxlen_options_returns_success() {
        // setup
        String key = "testKey";
        Map<String, String> fieldValues = new LinkedHashMap<>();
        fieldValues.put("testField1", "testValue1");
        fieldValues.put("testField2", "testValue2");
        StreamAddOptions options =
                StreamAddOptions.builder().id("id").makeStream(false).trim(new MaxLen(true, 5L)).build();

        String[] arguments =
                new String[] {
                    key,
                    NO_MAKE_STREAM_VALKEY_API,
                    TRIM_MAXLEN_VALKEY_API,
                    TRIM_EXACT_VALKEY_API,
                    Long.toString(5L),
                    "id"
                };
        arguments = ArrayUtils.addAll(arguments, convertMapToKeyValueStringArray(fieldValues));

        String returnId = "testId";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xadd(key, fieldValues, options);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, payload);
    }

    @SneakyThrows
    @Test
    public void xadd_binary_with_nomakestream_maxlen_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Map<GlideString, GlideString> fieldValues = new LinkedHashMap<>();
        fieldValues.put(gs("testField1"), gs("testValue1"));
        fieldValues.put(gs("testField2"), gs("testValue2"));
        StreamAddOptionsBinary options =
                StreamAddOptionsBinary.builder()
                        .id(gs("id"))
                        .makeStream(false)
                        .trim(new MaxLen(true, 5L))
                        .build();

        GlideString[] arguments =
                new GlideString[] {
                    key,
                    gs(NO_MAKE_STREAM_VALKEY_API),
                    gs(TRIM_MAXLEN_VALKEY_API),
                    gs(TRIM_EXACT_VALKEY_API),
                    gs(Long.toString(5L)),
                    gs("id")
                };
        arguments = ArrayUtils.addAll(arguments, convertMapToKeyValueGlideStringArray(fieldValues));

        GlideString returnId = gs("testId");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.xadd(key, fieldValues, options);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, payload);
    }

    @Test
    @SneakyThrows
    public void xtrim_with_exact_MinId() {
        // setup
        String key = "testKey";
        StreamTrimOptions limit = new MinId(true, "id");
        String[] arguments = new String[] {key, TRIM_MINID_VALKEY_API, TRIM_EXACT_VALKEY_API, "id"};
        Long completedResult = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XTrim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xtrim(key, limit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xtrim_binary_with_exact_MinId() {
        // setup
        GlideString key = gs("testKey");
        StreamTrimOptions limit = new MinId(true, "id");
        GlideString[] arguments =
                new GlideString[] {key, gs(TRIM_MINID_VALKEY_API), gs(TRIM_EXACT_VALKEY_API), gs("id")};
        Long completedResult = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XTrim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xtrim(key, limit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    private static List<Arguments> getStreamTrimOptions() {
        return List.of(
                Arguments.of(
                        // MAXLEN just THRESHOLD
                        "test_xtrim_maxlen", new MaxLen(5L), new String[] {TRIM_MAXLEN_VALKEY_API, "5"}),
                Arguments.of(
                        // MAXLEN with LIMIT
                        "test_xtrim_maxlen_with_limit",
                        new MaxLen(5L, 10L),
                        new String[] {
                            TRIM_MAXLEN_VALKEY_API, TRIM_NOT_EXACT_VALKEY_API, "5", TRIM_LIMIT_VALKEY_API, "10"
                        }),
                Arguments.of(
                        // MAXLEN with exact
                        "test_xtrim_exact_maxlen",
                        new MaxLen(true, 10L),
                        new String[] {TRIM_MAXLEN_VALKEY_API, TRIM_EXACT_VALKEY_API, "10"}),
                Arguments.of(
                        // MINID just THRESHOLD
                        "test_xtrim_minid", new MinId("0-1"), new String[] {TRIM_MINID_VALKEY_API, "0-1"}),
                Arguments.of(
                        // MINID with exact
                        "test_xtrim_exact_minid",
                        new MinId(true, "0-2"),
                        new String[] {TRIM_MINID_VALKEY_API, TRIM_EXACT_VALKEY_API, "0-2"}),
                Arguments.of(
                        // MINID with LIMIT
                        "test_xtrim_minid_with_limit",
                        new MinId("0-3", 10L),
                        new String[] {
                            TRIM_MINID_VALKEY_API, TRIM_NOT_EXACT_VALKEY_API, "0-3", TRIM_LIMIT_VALKEY_API, "10"
                        }));
    }

    private static List<Arguments> getStreamTrimOptionsBinary() {
        return List.of(
                Arguments.of(
                        // MAXLEN just THRESHOLD
                        "test_xtrim_maxlen",
                        new MaxLen(5L),
                        new GlideString[] {gs(TRIM_MAXLEN_VALKEY_API), gs("5")}),
                Arguments.of(
                        // MAXLEN with LIMIT
                        "test_xtrim_maxlen_with_limit",
                        new MaxLen(5L, 10L),
                        new GlideString[] {
                            gs(TRIM_MAXLEN_VALKEY_API),
                            gs(TRIM_NOT_EXACT_VALKEY_API),
                            gs("5"),
                            gs(TRIM_LIMIT_VALKEY_API),
                            gs("10")
                        }),
                Arguments.of(
                        // MAXLEN with exact
                        "test_xtrim_exact_maxlen",
                        new MaxLen(true, 10L),
                        new GlideString[] {gs(TRIM_MAXLEN_VALKEY_API), gs(TRIM_EXACT_VALKEY_API), gs("10")}),
                Arguments.of(
                        // MINID just THRESHOLD
                        "test_xtrim_minid",
                        new MinId("0-1"),
                        new GlideString[] {gs(TRIM_MINID_VALKEY_API), gs("0-1")}),
                Arguments.of(
                        // MINID with exact
                        "test_xtrim_exact_minid",
                        new MinId(true, "0-2"),
                        new GlideString[] {gs(TRIM_MINID_VALKEY_API), gs(TRIM_EXACT_VALKEY_API), gs("0-2")}),
                Arguments.of(
                        // MINID with LIMIT
                        "test_xtrim_minid_with_limit",
                        new MinId("0-3", 10L),
                        new GlideString[] {
                            gs(TRIM_MINID_VALKEY_API),
                            gs(TRIM_NOT_EXACT_VALKEY_API),
                            gs("0-3"),
                            gs(TRIM_LIMIT_VALKEY_API),
                            gs("10")
                        }));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("getStreamTrimOptions")
    public void xtrim_with_options_to_arguments(
            String testName, StreamTrimOptions options, String[] expectedArgs) {
        assertArrayEquals(expectedArgs, options.toArgs());
    }

    @SneakyThrows
    @ParameterizedTest(name = "{0}")
    @MethodSource("getStreamTrimOptionsBinary")
    public void xtrim_with_options_to_arguments_binary(
            String testName, StreamTrimOptions options, GlideString[] expectedGlideStringArgs) {
        assertArrayEquals(expectedGlideStringArgs, options.toGlideStringArgs());
    }

    @Test
    @SneakyThrows
    public void xlen_returns_success() {
        // setup
        String key = "testKey";
        Long completedResult = 99L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XLen), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xlen(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xread_multiple_keys() {
        // setup
        String keyOne = "one";
        String streamIdOne = "id-one";
        String keyTwo = "two";
        String streamIdTwo = "id-two";
        String[][] fieldValues = {{"field", "value"}};
        Map<String, Map<String, String[][]>> completedResult = new LinkedHashMap<>();
        completedResult.put(keyOne, Map.of(streamIdOne, fieldValues));
        completedResult.put(keyTwo, Map.of(streamIdTwo, fieldValues));
        String[] arguments = {READ_STREAMS_VALKEY_API, keyOne, keyTwo, streamIdOne, streamIdTwo};

        CompletableFuture<Map<String, Map<String, String[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, Map<String, String[][]>>>submitNewCommand(
                        eq(XRead), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        Map<String, String> keysAndIds = new LinkedHashMap<>();
        keysAndIds.put(keyOne, streamIdOne);
        keysAndIds.put(keyTwo, streamIdTwo);
        CompletableFuture<Map<String, Map<String, String[][]>>> response = service.xread(keysAndIds);
        Map<String, Map<String, String[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xread_binary_multiple_keys() {
        // setup
        GlideString keyOne = gs("one");
        GlideString streamIdOne = gs("id-one");
        GlideString keyTwo = gs("two");
        GlideString streamIdTwo = gs("id-two");
        GlideString[][] fieldValues = {{gs("field"), gs("value")}};
        Map<GlideString, Map<GlideString, GlideString[][]>> completedResult = new LinkedHashMap<>();
        completedResult.put(keyOne, Map.of(streamIdOne, fieldValues));
        completedResult.put(keyTwo, Map.of(streamIdTwo, fieldValues));
        GlideString[] arguments = {
            gs(READ_STREAMS_VALKEY_API), keyOne, keyTwo, streamIdOne, streamIdTwo
        };

        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Map<GlideString, GlideString[][]>>>submitNewCommand(
                        eq(XRead), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        Map<GlideString, GlideString> keysAndIds = new LinkedHashMap<>();
        keysAndIds.put(keyOne, streamIdOne);
        keysAndIds.put(keyTwo, streamIdTwo);
        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> response =
                service.xreadBinary(keysAndIds);
        Map<GlideString, Map<GlideString, GlideString[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xread_with_options() {
        // setup
        String keyOne = "one";
        String streamIdOne = "id-one";
        Long block = 2L;
        Long count = 10L;
        String[][] fieldValues = {{"field", "value"}};
        Map<String, Map<String, String[][]>> completedResult =
                Map.of(keyOne, Map.of(streamIdOne, fieldValues));
        String[] arguments = {
            READ_COUNT_VALKEY_API,
            count.toString(),
            READ_BLOCK_VALKEY_API,
            block.toString(),
            READ_STREAMS_VALKEY_API,
            keyOne,
            streamIdOne
        };

        CompletableFuture<Map<String, Map<String, String[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, Map<String, String[][]>>>submitNewCommand(
                        eq(XRead), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Map<String, String[][]>>> response =
                service.xread(
                        Map.of(keyOne, streamIdOne),
                        StreamReadOptions.builder().block(block).count(count).build());
        Map<String, Map<String, String[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xread_with_options_binary() {
        // setup
        GlideString keyOne = gs("one");
        GlideString streamIdOne = gs("id-one");
        Long block = 2L;
        Long count = 10L;
        GlideString[][] fieldValues = {{gs("field"), gs("value")}};
        Map<GlideString, Map<GlideString, GlideString[][]>> completedResult =
                Map.of(keyOne, Map.of(streamIdOne, fieldValues));
        GlideString[] arguments = {
            gs(READ_COUNT_VALKEY_API),
            gs(count.toString()),
            gs(READ_BLOCK_VALKEY_API),
            gs(block.toString()),
            gs(READ_STREAMS_VALKEY_API),
            keyOne,
            streamIdOne
        };

        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Map<GlideString, GlideString[][]>>>submitNewCommand(
                        eq(XRead), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> response =
                service.xreadBinary(
                        Map.of(keyOne, streamIdOne),
                        StreamReadOptions.builder().block(block).count(count).build());
        Map<GlideString, Map<GlideString, GlideString[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xdel_returns_success() {
        // setup
        String key = "testKey";
        String[] ids = {"one-1", "two-2", "three-3"};
        Long completedResult = 69L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(XDel), eq(new String[] {key, "one-1", "two-2", "three-3"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xdel(key, ids);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xdel_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] ids = {gs("one-1"), gs("two-2"), gs("three-3")};
        Long completedResult = 69L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(XDel), eq(new GlideString[] {key, gs("one-1"), gs("two-2"), gs("three-3")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xdel(key, ids);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrange_returns_success() {
        // setup
        String key = "testKey";
        StreamRange start = IdBound.of(9999L);
        StreamRange end = IdBound.ofExclusive("696969-10");
        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<String, String[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, String[][]>>submitNewCommand(
                        eq(XRange), eq(new String[] {key, "9999", "(696969-10"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[][]>> response = service.xrange(key, start, end);
        Map<String, String[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrange_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        StreamRange start = IdBound.of(9999L);
        StreamRange end = IdBound.ofExclusive("696969-10");
        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<GlideString, GlideString[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[][]>>submitNewCommand(
                        eq(XRange), eq(new GlideString[] {key, gs("9999"), gs("(696969-10")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[][]>> response = service.xrange(key, start, end);
        Map<GlideString, GlideString[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrange_withcount_returns_success() {
        // setup
        String key = "testKey";
        StreamRange start = InfRangeBound.MIN;
        StreamRange end = InfRangeBound.MAX;
        long count = 99L;
        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<String, String[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, String[][]>>submitNewCommand(
                        eq(XRange),
                        eq(
                                new String[] {
                                    key,
                                    MINIMUM_RANGE_VALKEY_API,
                                    MAXIMUM_RANGE_VALKEY_API,
                                    RANGE_COUNT_VALKEY_API,
                                    Long.toString(count)
                                }),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[][]>> response = service.xrange(key, start, end, count);
        Map<String, String[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrange_binary_withcount_returns_success() {
        // setup
        GlideString key = gs("testKey");
        StreamRange start = InfRangeBound.MIN;
        StreamRange end = InfRangeBound.MAX;
        long count = 99L;
        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<GlideString, GlideString[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[][]>>submitNewCommand(
                        eq(XRange),
                        eq(
                                new GlideString[] {
                                    key,
                                    gs(MINIMUM_RANGE_VALKEY_API),
                                    gs(MAXIMUM_RANGE_VALKEY_API),
                                    gs(RANGE_COUNT_VALKEY_API),
                                    gs(Long.toString(count))
                                }),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[][]>> response =
                service.xrange(key, start, end, count);
        Map<GlideString, GlideString[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrevrange_returns_success() {
        // setup
        String key = "testKey";
        StreamRange end = IdBound.of(9999L);
        StreamRange start = IdBound.ofExclusive("696969-10");
        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<String, String[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, String[][]>>submitNewCommand(
                        eq(XRevRange), eq(new String[] {key, "9999", "(696969-10"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[][]>> response = service.xrevrange(key, end, start);
        Map<String, String[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrevrange_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        StreamRange end = IdBound.of(9999L);
        StreamRange start = IdBound.ofExclusive("696969-10");
        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<GlideString, GlideString[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[][]>>submitNewCommand(
                        eq(XRevRange), eq(new GlideString[] {key, gs("9999"), gs("(696969-10")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[][]>> response =
                service.xrevrange(key, end, start);
        Map<GlideString, GlideString[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrevrange_withcount_returns_success() {
        // setup
        String key = "testKey";
        StreamRange end = InfRangeBound.MAX;
        StreamRange start = InfRangeBound.MIN;
        long count = 99L;
        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<String, String[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, String[][]>>submitNewCommand(
                        eq(XRevRange),
                        eq(
                                new String[] {
                                    key,
                                    MAXIMUM_RANGE_VALKEY_API,
                                    MINIMUM_RANGE_VALKEY_API,
                                    RANGE_COUNT_VALKEY_API,
                                    Long.toString(count)
                                }),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[][]>> response = service.xrevrange(key, end, start, count);
        Map<String, String[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @Test
    @SneakyThrows
    public void xrevrange_binary_withcount_returns_success() {
        // setup
        GlideString key = gs("testKey");
        StreamRange end = InfRangeBound.MAX;
        StreamRange start = InfRangeBound.MIN;
        long count = 99L;
        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        CompletableFuture<Map<GlideString, GlideString[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[][]>>submitNewCommand(
                        eq(XRevRange),
                        eq(
                                new GlideString[] {
                                    key,
                                    gs(MAXIMUM_RANGE_VALKEY_API),
                                    gs(MINIMUM_RANGE_VALKEY_API),
                                    gs(RANGE_COUNT_VALKEY_API),
                                    gs(Long.toString(count))
                                }),
                        any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[][]>> response =
                service.xrevrange(key, end, start, count);
        Map<GlideString, GlideString[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupCreate() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String id = "testId";
        String[] arguments = new String[] {key, groupName, id};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupCreate), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupCreate(key, groupName, id);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupCreate_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString id = gs("testId");
        GlideString[] arguments = new GlideString[] {key, groupName, id};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupCreate), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupCreate(key, groupName, id);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupCreate_withOptions() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String id = "testId";
        Long testEntry = 123L;
        StreamGroupOptions options =
                StreamGroupOptions.builder().makeStream().entriesRead(testEntry).build();
        String[] arguments =
                new String[] {
                    key,
                    groupName,
                    id,
                    MAKE_STREAM_VALKEY_API,
                    ENTRIES_READ_VALKEY_API,
                    Long.toString(testEntry)
                };

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupCreate), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupCreate(key, groupName, id, options);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupCreate_withOptions_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString id = gs("testId");
        Long testEntry = 123L;
        StreamGroupOptions options =
                StreamGroupOptions.builder().makeStream().entriesRead(testEntry).build();
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(groupName)
                        .add(id)
                        .add(MAKE_STREAM_VALKEY_API)
                        .add(ENTRIES_READ_VALKEY_API)
                        .add(testEntry)
                        .toArray();

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupCreate), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupCreate(key, groupName, id, options);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupDestroy() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String[] arguments = new String[] {key, groupName};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(XGroupDestroy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.xgroupDestroy(key, groupName);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(Boolean.TRUE, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupDestroy_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString[] arguments = new GlideString[] {key, groupName};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(XGroupDestroy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.xgroupDestroy(key, groupName);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(Boolean.TRUE, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupCreateConsumer() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumerName = "testConsumerName";
        String[] arguments = new String[] {key, groupName, consumerName};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(XGroupCreateConsumer), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.xgroupCreateConsumer(key, groupName, consumerName);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(Boolean.TRUE, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupCreateConsumer_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumerName = gs("testConsumerName");
        GlideString[] arguments = new GlideString[] {key, groupName, consumerName};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(Boolean.TRUE);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(XGroupCreateConsumer), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response =
                service.xgroupCreateConsumer(key, groupName, consumerName);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(Boolean.TRUE, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupDelConsumer() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumerName = "testConsumerName";
        String[] arguments = new String[] {key, groupName, consumerName};
        Long result = 28L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XGroupDelConsumer), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xgroupDelConsumer(key, groupName, consumerName);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupDelConsumer_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumerName = gs("testConsumerName");
        GlideString[] arguments = new GlideString[] {key, groupName, consumerName};
        Long result = 28L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XGroupDelConsumer), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xgroupDelConsumer(key, groupName, consumerName);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupSetid() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String id = "testId";
        String[] arguments = new String[] {key, groupName, id};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupSetId), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupSetId(key, groupName, id);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupSetid_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString id = gs("testId");
        GlideString[] arguments = new GlideString[] {key, groupName, id};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupSetId), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupSetId(key, groupName, id);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupSetidWithEntriesRead() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String id = "testId";
        Long entriesRead = 1L;
        String[] arguments =
                new String[] {key, groupName, id, ENTRIES_READ_VALKEY_API, Long.toString(entriesRead)};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupSetId), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupSetId(key, groupName, id, entriesRead);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xgroupSetidWithEntriesRead_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString id = gs("testId");
        Long entriesRead = 1L;
        GlideString[] arguments =
                new ArgsBuilder()
                        .add(key)
                        .add(groupName)
                        .add(id)
                        .add(ENTRIES_READ_VALKEY_API)
                        .add(entriesRead)
                        .toArray();

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XGroupSetId), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xgroupSetId(key, groupName, id, entriesRead);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void xreadgroup_multiple_keys() {
        // setup
        String keyOne = "one";
        String streamIdOne = "id-one";
        String keyTwo = "two";
        String streamIdTwo = "id-two";
        String groupName = "testGroup";
        String consumerName = "consumerGroup";
        String[][] fieldValues = {{"field", "value"}};
        Map<String, Map<String, String[][]>> completedResult = new LinkedHashMap<>();
        completedResult.put(keyOne, Map.of(streamIdOne, fieldValues));
        completedResult.put(keyTwo, Map.of(streamIdTwo, fieldValues));
        String[] arguments = {
            READ_GROUP_VALKEY_API,
            groupName,
            consumerName,
            READ_STREAMS_VALKEY_API,
            keyOne,
            keyTwo,
            streamIdOne,
            streamIdTwo
        };

        CompletableFuture<Map<String, Map<String, String[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, Map<String, String[][]>>>submitNewCommand(
                        eq(XReadGroup), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        Map<String, String> keysAndIds = new LinkedHashMap<>();
        keysAndIds.put(keyOne, streamIdOne);
        keysAndIds.put(keyTwo, streamIdTwo);
        CompletableFuture<Map<String, Map<String, String[][]>>> response =
                service.xreadgroup(keysAndIds, groupName, consumerName);
        Map<String, Map<String, String[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xreadgroup_binary_multiple_keys() {
        // setup
        GlideString keyOne = gs("one");
        GlideString streamIdOne = gs("id-one");
        GlideString keyTwo = gs("two");
        GlideString streamIdTwo = gs("id-two");
        GlideString groupName = gs("testGroup");
        GlideString consumerName = gs("consumerGroup");
        GlideString[][] fieldValues = {{gs("field"), gs("value")}};
        Map<GlideString, Map<GlideString, GlideString[][]>> completedResult = new LinkedHashMap<>();
        completedResult.put(keyOne, Map.of(streamIdOne, fieldValues));
        completedResult.put(keyTwo, Map.of(streamIdTwo, fieldValues));
        GlideString[] arguments = {
            gs(READ_GROUP_VALKEY_API),
            groupName,
            consumerName,
            gs(READ_STREAMS_VALKEY_API),
            keyOne,
            keyTwo,
            streamIdOne,
            streamIdTwo
        };

        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Map<GlideString, GlideString[][]>>>submitNewCommand(
                        eq(XReadGroup), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        Map<GlideString, GlideString> keysAndIds = new LinkedHashMap<>();
        keysAndIds.put(keyOne, streamIdOne);
        keysAndIds.put(keyTwo, streamIdTwo);
        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> response =
                service.xreadgroup(keysAndIds, groupName, consumerName);
        Map<GlideString, Map<GlideString, GlideString[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xreadgroup_with_options() {
        // setup
        String keyOne = "one";
        String streamIdOne = "id-one";
        Long block = 2L;
        Long count = 10L;
        String groupName = "testGroup";
        String consumerName = "consumerGroup";
        String[][] fieldValues = {{"field", "value"}};
        Map<String, Map<String, String[][]>> completedResult =
                Map.of(keyOne, Map.of(streamIdOne, fieldValues));
        String[] arguments = {
            READ_GROUP_VALKEY_API,
            groupName,
            consumerName,
            READ_COUNT_VALKEY_API,
            count.toString(),
            READ_BLOCK_VALKEY_API,
            block.toString(),
            READ_NOACK_VALKEY_API,
            READ_STREAMS_VALKEY_API,
            keyOne,
            streamIdOne
        };

        CompletableFuture<Map<String, Map<String, String[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<String, Map<String, String[][]>>>submitNewCommand(
                        eq(XReadGroup), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Map<String, String[][]>>> response =
                service.xreadgroup(
                        Map.of(keyOne, streamIdOne),
                        groupName,
                        consumerName,
                        StreamReadGroupOptions.builder().block(block).count(count).noack().build());
        Map<String, Map<String, String[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xreadgroup_with_options_binary() {
        // setup
        GlideString keyOne = gs("one");
        GlideString streamIdOne = gs("id-one");
        Long block = 2L;
        Long count = 10L;
        GlideString groupName = gs("testGroup");
        GlideString consumerName = gs("consumerGroup");
        GlideString[][] fieldValues = {{gs("field"), gs("value")}};
        Map<GlideString, Map<GlideString, GlideString[][]>> completedResult =
                Map.of(keyOne, Map.of(streamIdOne, fieldValues));
        GlideString[] arguments = {
            gs(READ_GROUP_VALKEY_API),
            groupName,
            consumerName,
            gs(READ_COUNT_VALKEY_API),
            gs(count.toString()),
            gs(READ_BLOCK_VALKEY_API),
            gs(block.toString()),
            gs(READ_NOACK_VALKEY_API),
            gs(READ_STREAMS_VALKEY_API),
            keyOne,
            streamIdOne
        };

        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(completedResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Map<GlideString, GlideString[][]>>>submitNewCommand(
                        eq(XReadGroup), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Map<GlideString, GlideString[][]>>> response =
                service.xreadgroup(
                        Map.of(keyOne, streamIdOne),
                        groupName,
                        consumerName,
                        StreamReadGroupOptions.builder().block(block).count(count).noack().build());
        Map<GlideString, Map<GlideString, GlideString[][]>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(completedResult, payload);
    }

    @SneakyThrows
    @Test
    public void xack_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String[] ids = new String[] {"testId"};
        String[] arguments = concatenateArrays(new String[] {key, groupName}, ids);
        Long mockResult = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XAck), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xack(key, groupName, ids);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaim_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String[] ids = new String[] {"testId"};
        String[] arguments = concatenateArrays(new String[] {key, groupName, consumer, "18"}, ids);
        Map<String, String[][]> mockResult = Map.of("1234-0", new String[][] {{"message", "log"}});

        CompletableFuture<Map<String, String[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<String, String[][]>>submitNewCommand(eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[][]>> response =
                service.xclaim(key, groupName, consumer, minIdleTime, ids);
        Map<String, String[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaim_biary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString[] ids = new GlideString[] {gs("testId")};
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {key, groupName, consumer, gs("18")}, ids);
        Map<GlideString, GlideString[][]> mockResult =
                Map.of(gs("1234-0"), new GlideString[][] {{gs("message"), gs("log")}});

        CompletableFuture<Map<GlideString, GlideString[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[][]>>submitNewCommand(
                        eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[][]>> response =
                service.xclaim(key, groupName, consumer, minIdleTime, ids);
        Map<GlideString, GlideString[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaim_with_options_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String[] ids = new String[] {"testId"};
        StreamClaimOptions options =
                StreamClaimOptions.builder().force().idle(11L).idleUnixTime(12L).retryCount(5L).build();
        String[] arguments =
                new String[] {
                    key,
                    groupName,
                    consumer,
                    "18",
                    "testId",
                    IDLE_VALKEY_API,
                    "11",
                    TIME_VALKEY_API,
                    "12",
                    RETRY_COUNT_VALKEY_API,
                    "5",
                    FORCE_VALKEY_API
                };
        Map<String, String[][]> mockResult = Map.of("1234-0", new String[][] {{"message", "log"}});

        CompletableFuture<Map<String, String[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<String, String[][]>>submitNewCommand(eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[][]>> response =
                service.xclaim(key, groupName, consumer, minIdleTime, ids, options);
        Map<String, String[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaim_binary_with_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString[] ids = new GlideString[] {gs("testId")};
        StreamClaimOptions options =
                StreamClaimOptions.builder().force().idle(11L).idleUnixTime(12L).retryCount(5L).build();
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    groupName,
                    consumer,
                    gs("18"),
                    gs("testId"),
                    gs(IDLE_VALKEY_API),
                    gs("11"),
                    gs(TIME_VALKEY_API),
                    gs("12"),
                    gs(RETRY_COUNT_VALKEY_API),
                    gs("5"),
                    gs(FORCE_VALKEY_API)
                };
        Map<GlideString, GlideString[][]> mockResult =
                Map.of(gs("1234-0"), new GlideString[][] {{gs("message"), gs("log")}});

        CompletableFuture<Map<GlideString, GlideString[][]>> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[][]>>submitNewCommand(
                        eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[][]>> response =
                service.xclaim(key, groupName, consumer, minIdleTime, ids, options);
        Map<GlideString, GlideString[][]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaimJustId_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String[] ids = new String[] {"testId"};
        String[] arguments =
                new String[] {key, groupName, consumer, "18", "testId", JUST_ID_VALKEY_API};
        String[] mockResult = {"message", "log"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response =
                service.xclaimJustId(key, groupName, consumer, minIdleTime, ids);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaimJustId_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString[] ids = new GlideString[] {gs("testId")};
        GlideString[] arguments =
                new GlideString[] {
                    key, groupName, consumer, gs("18"), gs("testId"), gs(JUST_ID_VALKEY_API)
                };
        GlideString[] mockResult = {gs("message"), gs("log")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response =
                service.xclaimJustId(key, groupName, consumer, minIdleTime, ids);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaimJustId_with_options_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String[] ids = new String[] {"testId"};
        StreamClaimOptions options =
                StreamClaimOptions.builder().force().idle(11L).idleUnixTime(12L).retryCount(5L).build();
        String[] arguments =
                new String[] {
                    key,
                    groupName,
                    consumer,
                    "18",
                    "testId",
                    IDLE_VALKEY_API,
                    "11",
                    TIME_VALKEY_API,
                    "12",
                    RETRY_COUNT_VALKEY_API,
                    "5",
                    FORCE_VALKEY_API,
                    JUST_ID_VALKEY_API
                };
        String[] mockResult = {"message", "log"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response =
                service.xclaimJustId(key, groupName, consumer, minIdleTime, ids, options);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xclaimJustId_binary_with_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString[] ids = new GlideString[] {gs("testId")};
        StreamClaimOptions options =
                StreamClaimOptions.builder().force().idle(11L).idleUnixTime(12L).retryCount(5L).build();
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    groupName,
                    consumer,
                    gs("18"),
                    gs("testId"),
                    gs(IDLE_VALKEY_API),
                    gs("11"),
                    gs(TIME_VALKEY_API),
                    gs("12"),
                    gs(RETRY_COUNT_VALKEY_API),
                    gs("5"),
                    gs(FORCE_VALKEY_API),
                    gs(JUST_ID_VALKEY_API)
                };
        GlideString[] mockResult = {gs("message"), gs("log")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(XClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response =
                service.xclaimJustId(key, groupName, consumer, minIdleTime, ids, options);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaim_return_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String start = "0-0";

        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        String[] deletedMessageIds = new String[] {"13-1", "46-2", "89-3"};

        String[] arguments = concatenateArrays(new String[] {key, groupName, consumer, "18", start});
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaim(key, groupName, consumer, minIdleTime, start);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaim_binary_return_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString start = gs("0-0");

        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        GlideString[] deletedMessageIds = new GlideString[] {gs("13-1"), gs("46-2"), gs("89-3")};

        GlideString[] arguments =
                concatenateArrays(new GlideString[] {key, groupName, consumer, gs("18"), start});
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaim(key, groupName, consumer, minIdleTime, start);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaim_with_count_return_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String start = "0-0";
        long count = 1234;

        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        String[] deletedMessageIds = new String[] {"13-1", "46-2", "89-3"};

        String[] arguments =
                concatenateArrays(
                        new String[] {key, groupName, consumer, "18", start, "COUNT", Long.toString(count)});
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaim(key, groupName, consumer, minIdleTime, start, count);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaim_binary_with_count_return_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString start = gs("0-0");
        long count = 1234;

        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        GlideString[] deletedMessageIds = new GlideString[] {gs("13-1"), gs("46-2"), gs("89-3")};

        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {
                            key, groupName, consumer, gs("18"), start, gs("COUNT"), gs(Long.toString(count))
                        });
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaim(key, groupName, consumer, minIdleTime, start, count);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaimJustId_return_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String start = "0-0";

        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        String[] deletedMessageIds = new String[] {"13-1", "46-2", "89-3"};

        String[] arguments =
                concatenateArrays(new String[] {key, groupName, consumer, "18", start, "JUSTID"});
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaimJustId(key, groupName, consumer, minIdleTime, start);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaimJustId_binary_return_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString start = gs("0-0");

        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        GlideString[] deletedMessageIds = new GlideString[] {gs("13-1"), gs("46-2"), gs("89-3")};

        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {key, groupName, consumer, gs("18"), start, gs("JUSTID")});
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaimJustId(key, groupName, consumer, minIdleTime, start);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaimJustId_with_count_return_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        Long minIdleTime = 18L;
        String start = "0-0";
        long count = 1234;

        String[][] fieldValuesResult = {{"duration", "12345"}, {"event-id", "2"}, {"user-id", "42"}};
        Map<String, String[][]> completedResult = Map.of(key, fieldValuesResult);

        String[] deletedMessageIds = new String[] {"13-1", "46-2", "89-3"};

        String[] arguments =
                concatenateArrays(
                        new String[] {
                            key,
                            groupName,
                            consumer,
                            Long.toString(minIdleTime),
                            start,
                            "COUNT",
                            Long.toString(count),
                            "JUSTID"
                        });
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaimJustId(key, groupName, consumer, minIdleTime, start, count);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xautoclaimJustId_binary_with_count_return_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        Long minIdleTime = 18L;
        GlideString start = gs("0-0");
        long count = 1234;

        GlideString[][] fieldValuesResult = {
            {gs("duration"), gs("12345")}, {gs("event-id"), gs("2")}, {gs("user-id"), gs("42")}
        };
        Map<GlideString, GlideString[][]> completedResult = Map.of(key, fieldValuesResult);

        GlideString[] deletedMessageIds = new GlideString[] {gs("13-1"), gs("46-2"), gs("89-3")};

        GlideString[] arguments =
                concatenateArrays(
                        new GlideString[] {
                            key,
                            groupName,
                            consumer,
                            gs(Long.toString(minIdleTime)),
                            start,
                            gs("COUNT"),
                            gs(Long.toString(count)),
                            gs("JUSTID")
                        });
        Object[] mockResult = new Object[] {start, completedResult, deletedMessageIds};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XAutoClaim), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.xautoclaimJustId(key, groupName, consumer, minIdleTime, start, count);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xack_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString[] ids = new GlideString[] {gs("testId")};
        GlideString[] arguments = concatenateArrays(new GlideString[] {key, groupName}, ids);
        Long mockResult = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(XAck), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.xack(key, groupName, ids);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xpending_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String[] arguments = {key, groupName};
        Object[] summary = new Object[] {1L, "1234-0", "2345-4", new Object[][] {{"consumer", "4"}}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XPending), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.xpending(key, groupName);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xpending_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString[] arguments = {key, groupName};
        Object[] summary = new Object[] {1L, "1234-0", "2345-4", new Object[][] {{"consumer", "4"}}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(XPending), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.xpending(key, groupName);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xpending_with_start_end_count_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String[] arguments = {key, groupName, EXCLUSIVE_RANGE_VALKEY_API + "1234-0", "2345-5", "4"};
        StreamRange start = IdBound.ofExclusive("1234-0");
        StreamRange end = IdBound.of("2345-5");
        Long count = 4L;
        Object[][] extendedForm = new Object[][] {{"1234-0", "consumer", 4L, 1L}};

        CompletableFuture<Object[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(extendedForm);

        // match on protobuf request
        when(commandManager.<Object[][]>submitNewCommand(eq(XPending), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[][]> response = service.xpending(key, groupName, start, end, count);
        Object[][] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(extendedForm, payload);
    }

    @SneakyThrows
    @Test
    public void xpending_binary_with_start_end_count_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString[] arguments = {
            key, groupName, gs(EXCLUSIVE_RANGE_VALKEY_API + "1234-0"), gs("2345-5"), gs("4")
        };
        StreamRange start = IdBound.ofExclusive("1234-0");
        StreamRange end = IdBound.of("2345-5");
        Long count = 4L;
        Object[][] extendedForm = new Object[][] {{"1234-0", "consumer", 4L, 1L}};

        CompletableFuture<Object[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(extendedForm);

        // match on protobuf request
        when(commandManager.<Object[][]>submitNewCommand(eq(XPending), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[][]> response = service.xpending(key, groupName, start, end, count);
        Object[][] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(extendedForm, payload);
    }

    @SneakyThrows
    @Test
    public void xpending_with_start_end_count_options_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "testGroupName";
        String consumer = "testConsumer";
        String[] arguments = {
            key,
            groupName,
            IDLE_TIME_VALKEY_API,
            "100",
            MINIMUM_RANGE_VALKEY_API,
            MAXIMUM_RANGE_VALKEY_API,
            "4",
            consumer
        };
        StreamRange start = InfRangeBound.MIN;
        StreamRange end = InfRangeBound.MAX;
        Long count = 4L;
        Object[][] extendedForm = new Object[][] {{"1234-0", consumer, 4L, 1L}};

        CompletableFuture<Object[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(extendedForm);

        // match on protobuf request
        when(commandManager.<Object[][]>submitNewCommand(eq(XPending), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[][]> response =
                service.xpending(
                        key,
                        groupName,
                        start,
                        end,
                        count,
                        StreamPendingOptions.builder().minIdleTime(100L).consumer(consumer).build());
        Object[][] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(extendedForm, payload);
    }

    @SneakyThrows
    @Test
    public void xpending_binary_with_start_end_count_options_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("testGroupName");
        GlideString consumer = gs("testConsumer");
        GlideString[] arguments = {
            key,
            groupName,
            gs(IDLE_TIME_VALKEY_API),
            gs("100"),
            gs(MINIMUM_RANGE_VALKEY_API),
            gs(MAXIMUM_RANGE_VALKEY_API),
            gs("4"),
            consumer
        };
        StreamRange start = InfRangeBound.MIN;
        StreamRange end = InfRangeBound.MAX;
        Long count = 4L;
        Object[][] extendedForm = new Object[][] {{"1234-0", consumer, 4L, 1L}};

        CompletableFuture<Object[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(extendedForm);

        // match on protobuf request
        when(commandManager.<Object[][]>submitNewCommand(eq(XPending), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[][]> response =
                service.xpending(
                        key,
                        groupName,
                        start,
                        end,
                        count,
                        StreamPendingOptionsBinary.builder().minIdleTime(100L).consumer(consumer).build());
        Object[][] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(extendedForm, payload);
    }

    @SneakyThrows
    @Test
    public void type_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        String value = "none";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Type), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.type(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void type_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        String value = "none";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Type), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.type(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void randomKey() {
        // setup
        String key1 = "key1";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(key1);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(RandomKey), eq(new String[0]), any()))
                .thenReturn(testResponse);
        CompletableFuture<String> response = service.randomKey();

        // verify
        assertEquals(testResponse, response);
    }

    @SneakyThrows
    @Test
    public void randomKeyBinary() {
        // setup
        GlideString key1 = gs("key1");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(key1);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(RandomKey), eq(new GlideString[0]), any()))
                .thenReturn(testResponse);
        CompletableFuture<GlideString> response = service.randomKeyBinary();

        // verify
        assertEquals(testResponse, response);
    }

    @SneakyThrows
    @Test
    public void rename() {
        // setup
        String key = "key1";
        String newKey = "key2";
        String[] arguments = new String[] {key, newKey};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Rename), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.rename(key, newKey);

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void rename_binary() {
        // setup
        GlideString key = gs("key1");
        GlideString newKey = gs("key2");
        GlideString[] arguments = new GlideString[] {key, newKey};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Rename), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.rename(key, newKey);

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void renamenx_returns_success() {
        // setup
        String key = "key1";
        String newKey = "key2";
        String[] arguments = new String[] {key, newKey};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(RenameNX), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.renamenx(key, newKey);

        // verify
        assertEquals(testResponse, response);
        assertTrue(response.get());
    }

    @SneakyThrows
    @Test
    public void renamenx_binary_returns_success() {
        // setup
        GlideString key = gs("key1");
        GlideString newKey = gs("key2");
        GlideString[] arguments = new GlideString[] {key, newKey};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(RenameNX), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.renamenx(key, newKey);

        // verify
        assertEquals(testResponse, response);
        assertTrue(response.get());
    }

    @SneakyThrows
    @Test
    public void time_returns_success() {
        // setup
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        String[] payload = new String[] {"UnixTime", "ms"};
        testResponse.complete(payload);
        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Time), eq(new String[0]), any()))
                .thenReturn(testResponse);
        // exercise
        CompletableFuture<String[]> response = service.time();

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void lastsave_returns_success() {
        // setup
        Long value = 42L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LastSave), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lastsave();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void flushall_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FlushAll), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushall();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushall_with_mode_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushAll), eq(new String[] {SYNC.toString()}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushall(SYNC);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushdb_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FlushDB), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushdb();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void flushdb_with_mode_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FlushDB), eq(new String[] {SYNC.toString()}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.flushdb(SYNC);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void lolwut_returns_success() {
        // setup
        String value = "pewpew";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lolwut), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_params_returns_success() {
        // setup
        String value = "pewpew";
        String[] arguments = new String[] {"1", "2"};
        int[] params = new int[] {1, 2};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lolwut), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut(params);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_version_returns_success() {
        // setup
        String value = "pewpew";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(Lolwut), eq(new String[] {VERSION_VALKEY_API, "42"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut(42);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void lolwut_with_version_and_params_returns_success() {
        // setup
        String value = "pewpew";
        String[] arguments = new String[] {VERSION_VALKEY_API, "42", "1", "2"};
        int[] params = new int[] {1, 2};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lolwut), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lolwut(42, params);

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void dbsize_returns_success() {
        // setup
        Long value = 10L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(DBSize), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.dbsize();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, response.get());
    }

    @SneakyThrows
    @Test
    public void linsert_returns_success() {
        // setup
        String key = "testKey";
        var position = BEFORE;
        String pivot = "pivot";
        String elem = "elem";
        String[] arguments = new String[] {key, position.toString(), pivot, elem};
        long value = 42;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LInsert), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.linsert(key, position, pivot, elem);
        long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void linsert_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        var position = BEFORE;
        GlideString pivot = gs("pivot");
        GlideString elem = gs("elem");
        GlideString[] arguments = new GlideString[] {key, gs(position.toString()), pivot, elem};
        long value = 42;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LInsert), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.linsert(key, position, pivot, elem);
        long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blpop_returns_success() {
        // setup
        String key = "key";
        double timeout = 0.5;
        String[] arguments = new String[] {key, "0.5"};
        String[] value = new String[] {"key", "value"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(BLPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.blpop(new String[] {key}, timeout);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blpop_binary_returns_success() {
        // setup
        GlideString key = gs("key");
        double timeout = 0.5;
        GlideString[] arguments = new GlideString[] {key, gs("0.5")};
        GlideString[] value = new GlideString[] {gs("key"), gs("value")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(BLPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.blpop(new GlideString[] {key}, timeout);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpushx_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"value1", "value2"};
        String[] args = new String[] {key, "value1", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(RPushX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.rpushx(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void rpushx_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] elements = new GlideString[] {gs("value1"), gs("value2")};
        GlideString[] args = new GlideString[] {key, gs("value1"), gs("value2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(RPushX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.rpushx(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpushx_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"value1", "value2"};
        String[] args = new String[] {key, "value1", "value2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPushX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpushx(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lpushx_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] elements = new GlideString[] {gs("value1"), gs("value2")};
        GlideString[] args = new GlideString[] {key, gs("value1"), gs("value2")};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LPushX), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lpushx(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void brpop_returns_success() {
        // setup
        String key = "key";
        double timeout = 0.5;
        String[] arguments = new String[] {key, "0.5"};
        String[] value = new String[] {"key", "value"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(BRPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.brpop(new String[] {key}, timeout);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void brpop_binary_returns_success() {
        // setup
        GlideString key = gs("key");
        double timeout = 0.5;
        GlideString[] arguments = new GlideString[] {key, gs("0.5")};
        GlideString[] value = new GlideString[] {gs("key"), gs("value")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(BRPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.brpop(new GlideString[] {key}, timeout);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pfadd_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"a", "b", "c"};
        String[] arguments = new String[] {key, "a", "b", "c"};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PfAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pfadd(key, elements);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertTrue(payload);
    }

    @SneakyThrows
    @Test
    public void pfadd_returns_success_binary() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] elements = new GlideString[] {gs("a"), gs("b"), gs("c")};
        GlideString[] arguments = new GlideString[] {key, gs("a"), gs("b"), gs("c")};

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(true);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(PfAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.pfadd(key, elements);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertTrue(payload);
    }

    @SneakyThrows
    @Test
    public void pfcount_returns_success() {
        // setup
        String[] keys = new String[] {"a", "b", "c"};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PfCount), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pfcount(keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void pfcount_returns_success_binary() {
        // setup
        GlideString[] keys = new GlideString[] {gs("a"), gs("b"), gs("c")};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PfCount), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pfcount(keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
        assertEquals(payload, response.get());
    }

    @SneakyThrows
    @Test
    public void pfmerge_returns_success() {
        // setup
        String destKey = "testKey";
        String[] sourceKeys = new String[] {"a", "b", "c"};
        String[] arguments = new String[] {destKey, "a", "b", "c"};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(PfMerge), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.pfmerge(destKey, sourceKeys);

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void pfmerge_returns_success_binary() {
        // setup
        GlideString destKey = gs("testKey");
        GlideString[] sourceKeys = new GlideString[] {gs("a"), gs("b"), gs("c")};
        GlideString[] arguments = new GlideString[] {destKey, gs("a"), gs("b"), gs("c")};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(PfMerge), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.pfmerge(destKey, sourceKeys);

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void objectEncoding_returns_success() {
        // setup
        String key = "testKey";
        String encoding = "testEncoding";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(encoding);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ObjectEncoding), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.objectEncoding(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(encoding, payload);
    }

    @SneakyThrows
    @Test
    public void objectEncoding_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        String encoding = "testEncoding";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(encoding);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(ObjectEncoding), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.objectEncoding(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(encoding, payload);
    }

    @SneakyThrows
    @Test
    public void objectFreq_returns_success() {
        // setup
        String key = "testKey";
        Long frequency = 0L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(frequency);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ObjectFreq), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.objectFreq(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(frequency, payload);
    }

    @SneakyThrows
    @Test
    public void objectFreq_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long frequency = 0L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(frequency);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ObjectFreq), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.objectFreq(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(frequency, payload);
    }

    @SneakyThrows
    @Test
    public void objectIdletime_returns_success() {
        // setup
        String key = "testKey";
        Long idletime = 0L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(idletime);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ObjectIdleTime), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.objectIdletime(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(idletime, payload);
    }

    @SneakyThrows
    @Test
    public void objectIdletime_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long idletime = 0L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(idletime);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(ObjectIdleTime), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.objectIdletime(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(idletime, payload);
    }

    @SneakyThrows
    @Test
    public void objectRefcount_returns_success() {
        // setup
        String key = "testKey";
        Long refcount = 0L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(refcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(ObjectRefCount), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.objectRefcount(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(refcount, payload);
    }

    @SneakyThrows
    @Test
    public void objectRefcount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long refcount = 0L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(refcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(ObjectRefCount), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.objectRefcount(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(refcount, payload);
    }

    @SneakyThrows
    @Test
    public void touch_returns_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long value = 2L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Touch), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.touch(keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void touch_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("testKey1"), gs("testKey2")};
        Long value = 2L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Touch), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.touch(keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geoadd_returns_success() {
        // setup
        String key = "testKey";
        Map<String, GeospatialData> membersToGeoSpatialData = new LinkedHashMap<>();
        membersToGeoSpatialData.put("Catania", new GeospatialData(15.087269, 40));
        membersToGeoSpatialData.put("Palermo", new GeospatialData(13.361389, 38.115556));
        String[] arguments =
                new String[] {key, "15.087269", "40.0", "Catania", "13.361389", "38.115556", "Palermo"};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.geoadd(key, membersToGeoSpatialData);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geoadd_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Map<GlideString, GeospatialData> membersToGeoSpatialData = new LinkedHashMap<>();
        membersToGeoSpatialData.put(gs("Catania"), new GeospatialData(15.087269, 40));
        membersToGeoSpatialData.put(gs("Palermo"), new GeospatialData(13.361389, 38.115556));
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    gs("15.087269"),
                    gs("40.0"),
                    gs("Catania"),
                    gs("13.361389"),
                    gs("38.115556"),
                    gs("Palermo")
                };
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.geoadd(key, membersToGeoSpatialData);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geoadd_with_options_returns_success() {
        // setup
        String key = "testKey";
        Map<String, GeospatialData> membersToGeoSpatialData = new LinkedHashMap<>();
        membersToGeoSpatialData.put("Catania", new GeospatialData(15.087269, 40));
        membersToGeoSpatialData.put("Palermo", new GeospatialData(13.361389, 38.115556));
        GeoAddOptions options = new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true);
        String[] arguments =
                new String[] {
                    key,
                    ConditionalChange.ONLY_IF_EXISTS.getValkeyApi(),
                    CHANGED_VALKEY_API,
                    "15.087269",
                    "40.0",
                    "Catania",
                    "13.361389",
                    "38.115556",
                    "Palermo"
                };
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.geoadd(key, membersToGeoSpatialData, options);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geoadd_with_options_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Map<GlideString, GeospatialData> membersToGeoSpatialData = new LinkedHashMap<>();
        membersToGeoSpatialData.put(gs("Catania"), new GeospatialData(15.087269, 40));
        membersToGeoSpatialData.put(gs("Palermo"), new GeospatialData(13.361389, 38.115556));
        GeoAddOptions options = new GeoAddOptions(ConditionalChange.ONLY_IF_EXISTS, true);
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    gs(ConditionalChange.ONLY_IF_EXISTS.getValkeyApi()),
                    gs(CHANGED_VALKEY_API),
                    gs("15.087269"),
                    gs("40.0"),
                    gs("Catania"),
                    gs("13.361389"),
                    gs("38.115556"),
                    gs("Palermo")
                };
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.geoadd(key, membersToGeoSpatialData, options);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geopos_returns_success() {
        // setup
        String key = "testKey";
        String[] members = {"Catania", "Palermo"};
        String[] arguments = new String[] {key, "Catania", "Palermo"};
        Double[][] value = {{15.087269, 40.0}, {13.361389, 38.115556}};

        CompletableFuture<Double[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double[][]>submitNewCommand(eq(GeoPos), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double[][]> response = service.geopos(key, members);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geopos_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = {gs("Catania"), gs("Palermo")};
        GlideString[] arguments = new GlideString[] {key, gs("Catania"), gs("Palermo")};
        Double[][] value = {{15.087269, 40.0}, {13.361389, 38.115556}};

        CompletableFuture<Double[][]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double[][]>submitNewCommand(eq(GeoPos), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double[][]> response = service.geopos(key, members);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void append() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(1L);
        when(commandManager.<Long>submitNewCommand(eq(Append), eq(new String[] {key, value}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.append(key, value);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(1L, payload);
    }

    @SneakyThrows
    @Test
    public void geohash_returns_success() {
        // setup
        String key = "testKey";
        String[] members = {"Catania", "Palermo", "NonExisting"};
        String[] arguments = new String[] {key, "Catania", "Palermo", "NonExisting"};
        String[] value = {"sqc8b49rny0", "sqdtr74hyu0", null};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(GeoHash), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.geohash(key, members);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geohash_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] members = {gs("Catania"), gs("Palermo"), gs("NonExisting")};
        GlideString[] arguments =
                new GlideString[] {key, gs("Catania"), gs("Palermo"), gs("NonExisting")};
        GlideString[] value = {gs("sqc8b49rny0"), gs("sqdtr74hyu0"), null};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(GeoHash), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.geohash(key, members);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geodist_returns_success() {
        // setup
        String key = "testKey";
        String member1 = "Catania";
        String member2 = "Palermo";
        String[] arguments = new String[] {key, member1, member2};
        Double value = 166274.1516;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(GeoDist), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.geodist(key, member1, member2);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geodist_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member1 = gs("Catania");
        GlideString member2 = gs("Palermo");
        GlideString[] arguments = new GlideString[] {key, member1, member2};
        Double value = 166274.1516;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(GeoDist), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.geodist(key, member1, member2);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geodist_with_metrics_returns_success() {
        // setup
        String key = "testKey";
        String member1 = "Catania";
        String member2 = "Palermo";
        GeoUnit geoUnit = GeoUnit.KILOMETERS;
        String[] arguments = new String[] {key, member1, member2, GeoUnit.KILOMETERS.getValkeyAPI()};
        Double value = 166.2742;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(GeoDist), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.geodist(key, member1, member2, geoUnit);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void geodist_with_metrics_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString member1 = gs("Catania");
        GlideString member2 = gs("Palermo");
        GeoUnit geoUnit = GeoUnit.KILOMETERS;
        GlideString[] arguments =
                new GlideString[] {key, member1, member2, gs(GeoUnit.KILOMETERS.getValkeyAPI())};
        Double value = 166.2742;

        CompletableFuture<Double> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Double>submitNewCommand(eq(GeoDist), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response = service.geodist(key, member1, member2, geoUnit);
        Double payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_returns_success() {
        // setup
        String code = "The best code ever";
        String[] args = new String[] {code};
        String value = "42";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionLoad(code, false);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_binary_returns_success() {
        // setup
        GlideString code = gs("The best code ever");
        GlideString[] args = new GlideString[] {code};
        GlideString value = gs("42");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.functionLoad(code, false);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_replace_returns_success() {
        // setup
        String code = "The best code ever";
        String[] args = new String[] {FunctionLoadOptions.REPLACE.toString(), code};
        String value = "42";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionLoad(code, true);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionLoad_with_replace_binary_returns_success() {
        // setup
        GlideString code = gs("The best code ever");
        GlideString[] args = new GlideString[] {gs(FunctionLoadOptions.REPLACE.toString()), code};
        GlideString value = gs("42");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(FunctionLoad), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.functionLoad(code, true);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_returns_success() {
        // setup
        String[] args = new String[0];
        @SuppressWarnings("unchecked")
        Map<String, Object>[] value = new Map[0];
        CompletableFuture<Map<String, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>[]>submitNewCommand(eq(FunctionList), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>[]> response = service.functionList(false);
        Map<String, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_binary_returns_success() {
        // setup
        GlideString[] args = new GlideString[0];
        @SuppressWarnings("unchecked")
        Map<GlideString, Object>[] value = new Map[0];
        CompletableFuture<Map<GlideString, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>[]>submitNewCommand(
                        eq(FunctionList), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>[]> response = service.functionListBinary(false);
        Map<GlideString, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_with_pattern_returns_success() {
        // setup
        String pattern = "*";
        String[] args = new String[] {LIBRARY_NAME_VALKEY_API, pattern, WITH_CODE_VALKEY_API};
        @SuppressWarnings("unchecked")
        Map<String, Object>[] value = new Map[0];
        CompletableFuture<Map<String, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>[]>submitNewCommand(eq(FunctionList), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>[]> response = service.functionList(pattern, true);
        Map<String, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionList_binary_with_pattern_returns_success() {
        // setup
        GlideString pattern = gs("*");
        GlideString[] args =
                new GlideString[] {gs(LIBRARY_NAME_VALKEY_API), pattern, gs(WITH_CODE_VALKEY_API)};
        @SuppressWarnings("unchecked")
        Map<GlideString, Object>[] value = new Map[0];
        CompletableFuture<Map<GlideString, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>[]>submitNewCommand(
                        eq(FunctionList), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>[]> response =
                service.functionListBinary(pattern, true);
        Map<GlideString, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionFlush_returns_success() {
        // setup
        String[] args = new String[0];
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionFlush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionFlush();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionFlush_with_mode_returns_success() {
        // setup
        FlushMode mode = ASYNC;
        String[] args = new String[] {mode.toString()};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionFlush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionFlush(mode);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionDelete_returns_success() {
        // setup
        String libName = "GLIDE";
        String[] args = new String[] {libName};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionDelete), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionDelete(libName);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionDelete_binary_returns_success() {
        // setup
        GlideString libName = gs("GLIDE");
        GlideString[] args = new GlideString[] {libName};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionDelete), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionDelete(libName);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_with_keys_and_args_returns_success() {
        // setup
        String function = "func";
        String[] keys = new String[] {"key1", "key2"};
        String[] arguments = new String[] {"1", "2"};
        String[] args = new String[] {function, "2", "key1", "key2", "1", "2"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function, keys, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_with_keys_and_args_binary_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        GlideString[] arguments = new GlideString[] {gs("1"), gs("2")};
        GlideString[] args =
                new GlideString[] {function, gs("2"), gs("key1"), gs("key2"), gs("1"), gs("2")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function, keys, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_returns_success() {
        // setup
        String function = "func";
        String[] args = new String[] {function, "0"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcall_binary_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] args = new GlideString[] {function, gs("0")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCall), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcall(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_with_keys_and_args_returns_success() {
        // setup
        String function = "func";
        String[] keys = new String[] {"key1", "key2"};
        String[] arguments = new String[] {"1", "2"};
        String[] args = new String[] {function, "2", "key1", "key2", "1", "2"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function, keys, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_with_keys_and_args_binary_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        GlideString[] arguments = new GlideString[] {gs("1"), gs("2")};
        GlideString[] args =
                new GlideString[] {function, gs("2"), gs("key1"), gs("key2"), gs("1"), gs("2")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function, keys, arguments);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_returns_success() {
        // setup
        String function = "func";
        String[] args = new String[] {function, "0"};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void fcallReadOnly_binary_returns_success() {
        // setup
        GlideString function = gs("func");
        GlideString[] args = new GlideString[] {function, gs("0")};
        Object value = "42";
        CompletableFuture<Object> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(FCallReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.fcallReadOnly(function);
        Object payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionKill_returns_success() {
        // setup
        String[] args = new String[0];
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionKill), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionKill();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionStats_returns_success() {
        // setup
        String[] args = new String[0];
        Map<String, Map<String, Map<String, Object>>> value =
                Map.of("::1", Map.of("1", Map.of("2", 2)));
        CompletableFuture<Map<String, Map<String, Map<String, Object>>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Map<String, Map<String, Object>>>>submitNewCommand(
                        eq(FunctionStats), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Map<String, Map<String, Object>>>> response =
                service.functionStats();
        Map<String, Map<String, Map<String, Object>>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionStatsBinary_returns_success() {
        // setup
        GlideString[] args = new GlideString[0];
        Map<String, Map<GlideString, Map<GlideString, Object>>> value =
                Map.of("::1", Map.of(gs("1"), Map.of(gs("2"), 2)));
        CompletableFuture<Map<String, Map<GlideString, Map<GlideString, Object>>>> testResponse =
                new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Map<GlideString, Map<GlideString, Object>>>>submitNewCommand(
                        eq(FunctionStats), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Map<GlideString, Map<GlideString, Object>>>> response =
                service.functionStatsBinary();
        Map<String, Map<GlideString, Map<GlideString, Object>>> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionDump_returns_success() {
        // setup
        byte[] value = new byte[] {42};
        CompletableFuture<byte[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<byte[]>submitNewCommand(eq(FunctionDump), eq(new GlideString[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<byte[]> response = service.functionDump();
        byte[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void functionRestore_returns_success() {
        // setup
        byte[] data = new byte[] {42};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
                        eq(FunctionRestore), eq(new GlideString[] {gs(data)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionRestore(data);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void functionRestore_with_policy_returns_success() {
        // setup
        byte[] data = new byte[] {42};
        GlideString[] args = {gs(data), gs(FunctionRestorePolicy.FLUSH.toString())};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(FunctionRestore), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.functionRestore(data, FunctionRestorePolicy.FLUSH);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_returns_success() {
        // setup
        String key = "testKey";
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitCount), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(1L, payload);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitCount), eq(new GlideString[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(1L, payload);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_start_returns_success() {
        // setup
        String key = "testKey";
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitCount), eq(new String[] {key, "1"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key, 1);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_start_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(BitCount), eq(new GlideString[] {key, gs("1")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key, 1);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_indices_returns_success() {
        // setup
        String key = "testKey";
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(BitCount), eq(new String[] {key, "1", "2"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key, 1, 2);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_indices_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(BitCount), eq(new GlideString[] {key, gs("1"), gs("2")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key, 1, 2);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_indices_with_option_returns_success() {
        // setup
        String key = "testKey";
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(BitCount), eq(new String[] {key, "1", "2", "BIT"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key, 1, 2, BitmapIndexType.BIT);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void bitcount_indices_with_option_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long bitcount = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitcount);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(BitCount), eq(new GlideString[] {key, gs("1"), gs("2"), gs("BIT")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitcount(key, 1, 2, BitmapIndexType.BIT);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitcount, payload);
    }

    @SneakyThrows
    @Test
    public void setbit_returns_success() {
        // setup
        String key = "testKey";
        Long value = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SetBit), eq(new String[] {key, "8", "1"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.setbit(key, 8, 1);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void setbit_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long value = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(SetBit), eq(new GlideString[] {key, gs("8"), gs("1")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.setbit(key, 8, 1);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blmpop_returns_success() {
        // setup
        String key = "testKey";
        String key2 = "testKey2";
        String[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        double timeout = 0.1;
        String[] arguments =
                new String[] {Double.toString(timeout), "2", key, key2, listDirection.toString()};
        Map<String, String[]> value = Map.of(key, new String[] {"five"});

        CompletableFuture<Map<String, String[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, String[]>>submitNewCommand(eq(BLMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[]>> response =
                service.blmpop(keys, listDirection, timeout);
        Map<String, String[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blmpop_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString key2 = gs("testKey2");
        GlideString[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        double timeout = 0.1;
        GlideString[] arguments =
                new GlideString[] {
                    gs(Double.toString(timeout)), gs("2"), key, key2, gs(listDirection.toString())
                };
        Map<GlideString, GlideString[]> value = Map.of(key, new GlideString[] {gs("five")});

        CompletableFuture<Map<GlideString, GlideString[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[]>>submitNewCommand(
                        eq(BLMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[]>> response =
                service.blmpop(keys, listDirection, timeout);
        Map<GlideString, GlideString[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blmpop_with_count_returns_success() {
        // setup
        String key = "testKey";
        String key2 = "testKey2";
        String[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        long count = 1L;
        double timeout = 0.1;
        String[] arguments =
                new String[] {
                    Double.toString(timeout),
                    "2",
                    key,
                    key2,
                    listDirection.toString(),
                    COUNT_FOR_LIST_VALKEY_API,
                    Long.toString(count)
                };
        Map<String, String[]> value = Map.of(key, new String[] {"five"});

        CompletableFuture<Map<String, String[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, String[]>>submitNewCommand(eq(BLMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[]>> response =
                service.blmpop(keys, listDirection, count, timeout);
        Map<String, String[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blmpop_with_count_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString key2 = gs("testKey2");
        GlideString[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        long count = 1L;
        double timeout = 0.1;
        GlideString[] arguments =
                new GlideString[] {
                    gs(Double.toString(timeout)),
                    gs("2"),
                    key,
                    key2,
                    gs(listDirection.toString()),
                    gs(COUNT_FOR_LIST_VALKEY_API),
                    gs(Long.toString(count))
                };
        Map<GlideString, GlideString[]> value = Map.of(key, new GlideString[] {gs("five")});

        CompletableFuture<Map<GlideString, GlideString[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[]>>submitNewCommand(
                        eq(BLMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[]>> response =
                service.blmpop(keys, listDirection, count, timeout);
        Map<GlideString, GlideString[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void getbit_returns_success() {
        // setup
        String key = "testKey";
        Long bit = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bit);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GetBit), eq(new String[] {key, "8"}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.getbit(key, 8);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bit, payload);
    }

    @SneakyThrows
    @Test
    public void getbit_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long bit = 1L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bit);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(GetBit), eq(new GlideString[] {key, gs("8")}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.getbit(key, 8);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bit, payload);
    }

    @SneakyThrows
    @Test
    public void bitpos_returns_success() {
        // setup
        String key = "testKey";
        Long bit = 0L;
        Long bitPosition = 10L;
        String[] arguments = new String[] {key, Long.toString(bit)};
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitPosition);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitPos), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitpos(key, bit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitPosition, payload);
    }

    @SneakyThrows
    @Test
    public void bitpos_with_start_returns_success() {
        // setup
        String key = "testKey";
        Long bit = 0L;
        Long start = 5L;
        Long bitPosition = 10L;
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitPosition);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(
                        eq(BitPos), eq(new String[] {key, Long.toString(bit), Long.toString(start)}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitpos(key, bit, start);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitPosition, payload);
    }

    @SneakyThrows
    @Test
    public void bitpos_with_start_and_end_returns_success() {
        // setup
        String key = "testKey";
        Long bit = 0L;
        Long start = 5L;
        Long end = 10L;
        Long bitPosition = 10L;
        String[] arguments =
                new String[] {key, Long.toString(bit), Long.toString(start), Long.toString(end)};
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitPosition);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitPos), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitpos(key, bit, start, end);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitPosition, payload);
    }

    @SneakyThrows
    @Test
    public void bitpos_with_start_and_end_and_type_returns_success() {
        // setup
        String key = "testKey";
        Long bit = 0L;
        Long start = 5L;
        Long end = 10L;
        Long bitPosition = 10L;
        String[] arguments =
                new String[] {
                    key,
                    Long.toString(bit),
                    Long.toString(start),
                    Long.toString(end),
                    BitmapIndexType.BIT.toString()
                };
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitPosition);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitPos), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitpos(key, bit, start, end, BitmapIndexType.BIT);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitPosition, payload);
    }

    @SneakyThrows
    @Test
    public void bitpos_with_start_and_end_and_type_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long bit = 0L;
        Long start = 5L;
        Long end = 10L;
        Long bitPosition = 10L;
        GlideString[] arguments =
                new GlideString[] {
                    key,
                    gs(Long.toString(bit)),
                    gs(Long.toString(start)),
                    gs(Long.toString(end)),
                    gs(BitmapIndexType.BIT.toString())
                };
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(bitPosition);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitPos), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitpos(key, bit, start, end, BitmapIndexType.BIT);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(bitPosition, payload);
    }

    @SneakyThrows
    @Test
    public void bitop_returns_success() {
        // setup
        String destination = "destination";
        String[] keys = new String[] {"key1", "key2"};
        Long result = 6L;
        BitwiseOperation bitwiseAnd = BitwiseOperation.AND;
        String[] arguments = concatenateArrays(new String[] {bitwiseAnd.toString(), destination}, keys);
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitOp), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitop(bitwiseAnd, destination, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void bitop_bianry_returns_success() {
        // setup
        GlideString destination = gs("destination");
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        Long result = 6L;
        BitwiseOperation bitwiseAnd = BitwiseOperation.AND;
        GlideString[] arguments =
                concatenateArrays(new GlideString[] {gs(bitwiseAnd.toString()), destination}, keys);
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(BitOp), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.bitop(bitwiseAnd, destination, keys);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void lmpop_returns_success() {
        // setup
        String key = "testKey";
        String key2 = "testKey2";
        String[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        String[] arguments = new String[] {"2", key, key2, listDirection.toString()};
        Map<String, String[]> value = Map.of(key, new String[] {"five"});

        CompletableFuture<Map<String, String[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, String[]>>submitNewCommand(eq(LMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[]>> response = service.lmpop(keys, listDirection);
        Map<String, String[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lmpop_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString key2 = gs("testKey2");
        GlideString[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        GlideString[] arguments = new GlideString[] {gs("2"), key, key2, gs(listDirection.toString())};
        Map<GlideString, GlideString[]> value = Map.of(key, new GlideString[] {gs("five")});

        CompletableFuture<Map<GlideString, GlideString[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[]>>submitNewCommand(
                        eq(LMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[]>> response =
                service.lmpop(keys, listDirection);
        Map<GlideString, GlideString[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lmpop_with_count_returns_success() {
        // setup
        String key = "testKey";
        String key2 = "testKey2";
        String[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        long count = 1L;
        String[] arguments =
                new String[] {
                    "2", key, key2, listDirection.toString(), COUNT_FOR_LIST_VALKEY_API, Long.toString(count)
                };
        Map<String, String[]> value = Map.of(key, new String[] {"five"});

        CompletableFuture<Map<String, String[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, String[]>>submitNewCommand(eq(LMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, String[]>> response = service.lmpop(keys, listDirection, count);
        Map<String, String[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lmpop_with_count_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString key2 = gs("testKey2");
        GlideString[] keys = {key, key2};
        ListDirection listDirection = ListDirection.LEFT;
        long count = 1L;
        GlideString[] arguments =
                new GlideString[] {
                    gs("2"),
                    key,
                    key2,
                    gs(listDirection.toString()),
                    gs(COUNT_FOR_LIST_VALKEY_API),
                    gs(Long.toString(count))
                };
        Map<GlideString, GlideString[]> value = Map.of(key, new GlideString[] {gs("five")});

        CompletableFuture<Map<GlideString, GlideString[]>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, GlideString[]>>submitNewCommand(
                        eq(LMPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, GlideString[]>> response =
                service.lmpop(keys, listDirection, count);
        Map<GlideString, GlideString[]> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lmove_returns_success() {
        // setup
        String key1 = "testKey";
        String key2 = "testKey2";
        ListDirection wherefrom = ListDirection.LEFT;
        ListDirection whereto = ListDirection.RIGHT;
        String[] arguments = new String[] {key1, key2, wherefrom.toString(), whereto.toString()};
        String value = "one";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LMove), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lmove(key1, key2, wherefrom, whereto);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lmove_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey");
        GlideString key2 = gs("testKey2");
        ListDirection wherefrom = ListDirection.LEFT;
        ListDirection whereto = ListDirection.RIGHT;
        GlideString[] arguments =
                new GlideString[] {key1, key2, gs(wherefrom.toString()), gs(whereto.toString())};
        GlideString value = gs("one");
        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(LMove), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.lmove(key1, key2, wherefrom, whereto);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lset_returns_success() {
        // setup
        String key = "testKey";
        long index = 0;
        String element = "two";
        String[] arguments = new String[] {key, "0", element};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LSet), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lset(key, index, element);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void lset_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long index = 0;
        GlideString element = gs("two");
        GlideString[] arguments = new GlideString[] {key, gs("0"), element};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LSet), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lset(key, index, element);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void blmove_returns_success() {
        // setup
        String key1 = "testKey";
        String key2 = "testKey2";
        ListDirection wherefrom = ListDirection.LEFT;
        ListDirection whereto = ListDirection.RIGHT;
        String[] arguments = new String[] {key1, key2, wherefrom.toString(), whereto.toString(), "0.1"};
        String value = "one";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(BLMove), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.blmove(key1, key2, wherefrom, whereto, 0.1);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void blmove_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey");
        GlideString key2 = gs("testKey2");
        ListDirection wherefrom = ListDirection.LEFT;
        ListDirection whereto = ListDirection.RIGHT;
        GlideString[] arguments =
                new GlideString[] {key1, key2, gs(wherefrom.toString()), gs(whereto.toString()), gs("0.1")};
        GlideString value = gs("one");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(BLMove), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.blmove(key1, key2, wherefrom, whereto, 0.1);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sintercard_returns_success() {
        // setup
        String key1 = "testKey";
        String key2 = "testKey2";
        String[] arguments = new String[] {"2", key1, key2};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sintercard(new String[] {key1, key2});
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sintercard_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {gs("2"), key1, key2};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sintercard(new GlideString[] {key1, key2});
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sintercard_with_limit_returns_success() {
        // setup
        String key1 = "testKey";
        String key2 = "testKey2";
        long limit = 1L;
        String[] arguments = new String[] {"2", key1, key2, SET_LIMIT_VALKEY_API, "1"};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sintercard(new String[] {key1, key2}, limit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sintercard_with_limit_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey");
        GlideString key2 = gs("testKey2");
        long limit = 1L;
        GlideString[] arguments =
                new GlideString[] {gs("2"), key1, key2, gs(SET_LIMIT_VALKEY_API), gs("1")};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(SInterCard), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.sintercard(new GlideString[] {key1, key2}, limit);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void srandmember_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        String value = "one";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(SRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.srandmember(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void srandmember_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        GlideString value = gs("one");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(SRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.srandmember(key);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void srandmember_with_count_returns_success() {
        // setup
        String key = "testKey";
        long count = 2;
        String[] arguments = new String[] {key, Long.toString(count)};
        String[] value = {"one", "two"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(SRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.srandmember(key, count);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void srandmember_with_count_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        GlideString[] value = {gs("one"), gs("two")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(SRandMember), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.srandmember(key, count);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void spop_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key};
        String value = "value";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(SPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.spop(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void spop_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = new GlideString[] {key};
        GlideString value = gs("value");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(SPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.spop(key);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void spopCount_returns_success() {
        // setup
        String key = "testKey";
        long count = 2;
        String[] arguments = new String[] {key, Long.toString(count)};
        Set<String> value = Set.of("one", "two");

        CompletableFuture<Set<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<String>>submitNewCommand(eq(SPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<String>> response = service.spopCount(key, count);
        Set<String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void spopCount_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long count = 2;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(count))};
        Set<GlideString> value = Set.of(gs("one"), gs("two"));

        CompletableFuture<Set<GlideString>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<GlideString>>submitNewCommand(eq(SPop), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<GlideString>> response = service.spopCount(key, count);
        Set<GlideString> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void bitfieldReadOnly_returns_success() {
        // setup
        String key = "testKey";
        Long[] result = new Long[] {7L, 8L};
        Offset offset = new Offset(1);
        OffsetMultiplier offsetMultiplier = new OffsetMultiplier(8);
        BitFieldGet subcommand1 = new BitFieldGet(new UnsignedEncoding(4), offset);
        BitFieldGet subcommand2 = new BitFieldGet(new SignedEncoding(5), offsetMultiplier);
        String[] args = {
            key,
            BitFieldOptions.GET_COMMAND_STRING,
            BitFieldOptions.UNSIGNED_ENCODING_PREFIX.concat("4"),
            offset.getOffset(),
            BitFieldOptions.GET_COMMAND_STRING,
            BitFieldOptions.SIGNED_ENCODING_PREFIX.concat("5"),
            offsetMultiplier.getOffset()
        };
        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(BitFieldReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response =
                service.bitfieldReadOnly(key, new BitFieldReadOnlySubCommands[] {subcommand1, subcommand2});
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void bitfieldReadOnly_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long[] result = new Long[] {7L, 8L};
        Offset offset = new Offset(1);
        OffsetMultiplier offsetMultiplier = new OffsetMultiplier(8);
        BitFieldGet subcommand1 = new BitFieldGet(new UnsignedEncoding(4), offset);
        BitFieldGet subcommand2 = new BitFieldGet(new SignedEncoding(5), offsetMultiplier);
        GlideString[] args = {
            key,
            gs(BitFieldOptions.GET_COMMAND_STRING),
            gs(BitFieldOptions.UNSIGNED_ENCODING_PREFIX.concat("4")),
            gs(offset.getOffset()),
            gs(BitFieldOptions.GET_COMMAND_STRING),
            gs(BitFieldOptions.SIGNED_ENCODING_PREFIX.concat("5")),
            gs(offsetMultiplier.getOffset())
        };
        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(BitFieldReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response =
                service.bitfieldReadOnly(key, new BitFieldReadOnlySubCommands[] {subcommand1, subcommand2});
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void bitfield_returns_success() {
        // setup
        String key = "testKey";
        Long[] result = new Long[] {7L, 8L, 9L};
        UnsignedEncoding u2 = new UnsignedEncoding(2);
        SignedEncoding i8 = new SignedEncoding(8);
        Offset offset = new Offset(1);
        OffsetMultiplier offsetMultiplier = new OffsetMultiplier(8);
        long setValue = 2;
        long incrbyValue = 5;
        String[] args =
                new String[] {
                    key,
                    SET_COMMAND_STRING,
                    u2.getEncoding(),
                    offset.getOffset(),
                    Long.toString(setValue),
                    GET_COMMAND_STRING,
                    i8.getEncoding(),
                    offsetMultiplier.getOffset(),
                    OVERFLOW_COMMAND_STRING,
                    SAT.toString(),
                    INCRBY_COMMAND_STRING,
                    u2.getEncoding(),
                    offset.getOffset(),
                    Long.toString(incrbyValue)
                };
        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(BitField), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response =
                service.bitfield(
                        key,
                        new BitFieldSubCommands[] {
                            new BitFieldSet(u2, offset, setValue),
                            new BitFieldGet(i8, offsetMultiplier),
                            new BitFieldOptions.BitFieldOverflow(SAT),
                            new BitFieldOptions.BitFieldIncrby(u2, offset, incrbyValue),
                        });
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void bitfield_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        Long[] result = new Long[] {7L, 8L, 9L};
        UnsignedEncoding u2 = new UnsignedEncoding(2);
        SignedEncoding i8 = new SignedEncoding(8);
        Offset offset = new Offset(1);
        OffsetMultiplier offsetMultiplier = new OffsetMultiplier(8);
        long setValue = 2;
        long incrbyValue = 5;
        GlideString[] args =
                new GlideString[] {
                    key,
                    gs(SET_COMMAND_STRING),
                    gs(u2.getEncoding()),
                    gs(offset.getOffset()),
                    gs(Long.toString(setValue)),
                    gs(GET_COMMAND_STRING),
                    gs(i8.getEncoding()),
                    gs(offsetMultiplier.getOffset()),
                    gs(OVERFLOW_COMMAND_STRING),
                    gs(SAT.toString()),
                    gs(INCRBY_COMMAND_STRING),
                    gs(u2.getEncoding()),
                    gs(offset.getOffset()),
                    gs(Long.toString(incrbyValue))
                };
        CompletableFuture<Long[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long[]>submitNewCommand(eq(BitField), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long[]> response =
                service.bitfield(
                        key,
                        new BitFieldSubCommands[] {
                            new BitFieldSet(u2, offset, setValue),
                            new BitFieldGet(i8, offsetMultiplier),
                            new BitFieldOptions.BitFieldOverflow(SAT),
                            new BitFieldOptions.BitFieldIncrby(u2, offset, incrbyValue),
                        });
        Long[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void move_returns_success() {
        // setup
        String key = "testKey";
        long dbIndex = 2L;
        String[] arguments = new String[] {key, Long.toString(dbIndex)};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Move), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.move(key, dbIndex);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void move_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long dbIndex = 2L;
        GlideString[] arguments = new GlideString[] {key, gs(Long.toString(dbIndex))};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Move), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.move(key, dbIndex);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void copy_returns_success() {
        // setup
        String source = "testKey1";
        String destination = "testKey2";
        String[] arguments = new String[] {source, destination};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Copy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.copy(source, destination);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void copy_binary_returns_success() {
        // setup
        GlideString source = gs("testKey1");
        GlideString destination = gs("testKey2");
        GlideString[] arguments = new GlideString[] {source, destination};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Copy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.copy(source, destination);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void copy_with_replace_returns_success() {
        // setup
        String source = "testKey1";
        String destination = "testKey2";
        String[] arguments = new String[] {source, destination, REPLACE_VALKEY_API};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Copy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.copy(source, destination, true);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void copy_with_destinationDB_returns_success() {
        // setup
        String source = "testKey1";
        String destination = "testKey2";
        long destinationDB = 1;
        String[] arguments = new String[] {source, destination, DB_VALKEY_API, "1", REPLACE_VALKEY_API};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(Copy), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean> response = service.copy(source, destination, destinationDB, true);
        Boolean payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcs() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {key1, key2};
        String value = "foo";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.lcs(key1, key2);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcs_binary() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {key1, key2};
        GlideString value = gs("foo");

        CompletableFuture<GlideString> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString> response = service.lcs(key1, key2);
        GlideString payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcs_with_len_option() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {key1, key2, LEN_VALKEY_API};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lcsLen(key1, key2);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcs_with_len_option_binary() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {key1, key2, gs(LEN_VALKEY_API)};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.lcsLen(key1, key2);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdx() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {key1, key2, IDX_COMMAND_STRING};
        Map<String, Object> value = Map.of("matches", new Long[][][] {{{1L, 3L}, {0L, 2L}}}, "len", 3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdx(key1, key2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdx_binary() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {key1, key2, gs(IDX_COMMAND_STRING)};
        Map<String, Object> value = Map.of("matches", new Long[][][] {{{1L, 3L}, {0L, 2L}}}, "len", 3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdx(key1, key2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdx_throws_NullPointerException() {
        // setup
        Map<String, Object> value = Map.of("missing", new Long[][][] {{{1L, 3L}, {0L, 2L}}}, "len", 3L);

        // exception
        RuntimeException runtimeException =
                assertThrows(RuntimeException.class, () -> service.handleLcsIdxResponse(value));
        assertInstanceOf(NullPointerException.class, runtimeException);
        assertEquals(
                "LCS result does not contain the key \"" + LCS_MATCHES_RESULT_KEY + "\"",
                runtimeException.getMessage());
    }

    @SneakyThrows
    @Test
    public void lcsIdx_with_options() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments =
                new String[] {key1, key2, IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, "2"};
        Map<String, Object> value =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdx(key1, key2, 2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdx_with_options_binary() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments =
                new GlideString[] {
                    key1, key2, gs(IDX_COMMAND_STRING), gs(MINMATCHLEN_COMMAND_STRING), gs("2")
                };
        Map<String, Object> value =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdx(key1, key2, 2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdxWithMatchLen() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {key1, key2, IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING};
        Map<String, Object> value =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdxWithMatchLen(key1, key2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdxWithMatchLen_binary() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments =
                new GlideString[] {key1, key2, gs(IDX_COMMAND_STRING), gs(WITHMATCHLEN_COMMAND_STRING)};
        Map<String, Object> value =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdxWithMatchLen(key1, key2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdxWithMatchLen_with_options() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments =
                new String[] {
                    key1,
                    key2,
                    IDX_COMMAND_STRING,
                    MINMATCHLEN_COMMAND_STRING,
                    "2",
                    WITHMATCHLEN_COMMAND_STRING
                };
        Map<String, Object> value =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdxWithMatchLen(key1, key2, 2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void lcsIdxWithMatchLen_with_options_binary() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments =
                new GlideString[] {
                    key1,
                    key2,
                    gs(IDX_COMMAND_STRING),
                    gs(MINMATCHLEN_COMMAND_STRING),
                    gs("2"),
                    gs(WITHMATCHLEN_COMMAND_STRING)
                };
        Map<String, Object> value =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(eq(LCS), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.lcsIdxWithMatchLen(key1, key2, 2);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void watch_returns_success() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {key1, key2};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Watch), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.watch(arguments);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void watch_binary_returns_success() {
        // setup
        GlideString key1 = gs("testKey1");
        GlideString key2 = gs("testKey2");
        GlideString[] arguments = new GlideString[] {key1, key2};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Watch), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.watch(arguments);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void unwatch_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(UnWatch), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.unwatch();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void publish_returns_success() {
        // setup
        String channel = "channel";
        String message = "message";
        String[] arguments = new String[] {channel, message};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Publish), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.publish(message, channel);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubChannels_returns_success() {
        // setup
        String[] arguments = new String[0];
        String[] value = new String[] {"ch1", "ch2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(PubSubChannels), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.pubsubChannels();
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubChannelsBinary_returns_success() {
        // setup
        GlideString[] arguments = new GlideString[0];
        GlideString[] value = new GlideString[] {gs("ch1"), gs("ch2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(PubSubChannels), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.pubsubChannelsBinary();
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubChannels_with_pattern_returns_success() {
        // setup
        String pattern = "ch*";
        String[] arguments = new String[] {pattern};
        String[] value = new String[] {"ch1", "ch2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(PubSubChannels), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response = service.pubsubChannels(pattern);
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubChannelsBinary_with_pattern_returns_success() {
        // setup
        GlideString pattern = gs("ch*");
        GlideString[] arguments = new GlideString[] {pattern};
        GlideString[] value = new GlideString[] {gs("ch1"), gs("ch2")};

        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(PubSubChannels), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response = service.pubsubChannels(pattern);
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubNumPat_returns_success() {
        // setup
        String[] arguments = new String[0];
        Long value = 42L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PubSubNumPat), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pubsubNumPat();
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubNumSub_returns_success() {
        // setup
        String[] arguments = new String[] {"ch1", "ch2"};
        Map<String, Long> value = Map.of();

        CompletableFuture<Map<String, Long>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Long>>submitNewCommand(eq(PubSubNumSub), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Long>> response = service.pubsubNumSub(arguments);
        Map<String, Long> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void pubsubNumSubBinary_returns_success() {
        // setup
        GlideString[] arguments = new GlideString[] {gs("ch1"), gs("ch2")};
        Map<GlideString, Long> value = Map.of();

        CompletableFuture<Map<GlideString, Long>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Long>>submitNewCommand(
                        eq(PubSubNumSub), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Long>> response = service.pubsubNumSub(arguments);
        Map<GlideString, Long> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sunion_returns_success() {
        // setup
        String[] keys = new String[] {"key1", "key2"};
        Set<String> value = Set.of("1", "2");
        CompletableFuture<Set<String>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<String>>submitNewCommand(eq(SUnion), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<String>> response = service.sunion(keys);
        Set<String> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sunion_binary_returns_success() {
        // setup
        GlideString[] keys = new GlideString[] {gs("key1"), gs("key2")};
        Set<GlideString> value = Set.of(gs("1"), gs("2"));
        CompletableFuture<Set<GlideString>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Set<GlideString>>submitNewCommand(eq(SUnion), eq(keys), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Set<GlideString>> response = service.sunion(keys);
        Set<GlideString> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void dump_returns_success() {
        // setup
        GlideString key = gs("testKey");
        byte[] value = "value".getBytes();
        GlideString[] arguments = new GlideString[] {key};

        CompletableFuture<byte[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<byte[]>submitNewCommand(eq(Dump), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<byte[]> response = service.dump(key);
        byte[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void restore_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long ttl = 0L;
        byte[] value = "value".getBytes();

        GlideString[] arg = new GlideString[] {key, gs(Long.toString(ttl).getBytes()), gs(value)};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Restore), eq(arg), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.restore(key, ttl, value);

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void restore_with_restoreOptions_returns_success() {
        // setup
        GlideString key = gs("testKey");
        long ttl = 0L;
        byte[] value = "value".getBytes();
        Long idletime = 10L;
        Long frequency = 5L;

        GlideString[] arg =
                new GlideString[] {
                    key, gs(Long.toString(ttl)), gs(value), gs("REPLACE"), gs("ABSTTL"), gs("FREQ"), gs("5")
                };

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Restore), eq(arg), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response =
                service.restore(
                        key,
                        ttl,
                        value,
                        RestoreOptions.builder().replace().absttl().frequency(frequency).build());

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, response.get());
    }

    @SneakyThrows
    @Test
    public void restore_with_restoreOptions_throwsError() {
        // setup
        GlideString key = gs("testKey");
        long ttl = 0L;
        byte[] value = "value".getBytes();
        Long idletime = 10L;
        Long frequency = 5L;

        GlideString[] arg =
                new GlideString[] {
                    key,
                    gs(Long.toString(ttl)),
                    gs(value),
                    gs("REPLACE"),
                    gs("ABSTTL"),
                    gs("IDLETIME"),
                    gs("10"),
                    gs("FREQ"),
                    gs("5")
                };

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Restore), eq(arg), any()))
                .thenReturn(testResponse);

        IllegalArgumentException illegalArgumentException =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.restore(
                                        key,
                                        ttl,
                                        value,
                                        RestoreOptions.builder()
                                                .replace()
                                                .absttl()
                                                .idletime(10L)
                                                .frequency(5L)
                                                .build()));
        assertEquals(
                "IDLETIME and FREQ cannot be set at the same time.", illegalArgumentException.getMessage());
    }

    @SneakyThrows
    @Test
    public void sort_with_options_returns_success() {
        // setup
        String[] result = new String[] {"1", "2", "3"};
        String key = "key";
        Long limitOffset = 0L;
        Long limitCount = 2L;
        String byPattern = "byPattern";
        String getPattern = "getPattern";
        String[] args =
                new String[] {
                    key,
                    LIMIT_COMMAND_STRING,
                    limitOffset.toString(),
                    limitCount.toString(),
                    DESC.toString(),
                    ALPHA_COMMAND_STRING,
                    BY_COMMAND_STRING,
                    byPattern,
                    GET_COMMAND_STRING,
                    getPattern
                };
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Sort), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response =
                service.sort(
                        key,
                        SortOptions.builder()
                                .alpha()
                                .limit(new SortBaseOptions.Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .getPattern(getPattern)
                                .byPattern(byPattern)
                                .build());
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sort_with_options_binary_returns_success() {
        // setup
        GlideString[] result = new GlideString[] {gs("1"), gs("2"), gs("3")};
        GlideString key = gs("key");
        Long limitOffset = 0L;
        Long limitCount = 2L;
        GlideString byPattern = gs("byPattern");
        GlideString getPattern = gs("getPattern");
        GlideString[] args =
                new GlideString[] {
                    key,
                    gs(LIMIT_COMMAND_STRING),
                    gs(limitOffset.toString()),
                    gs(limitCount.toString()),
                    gs(DESC.toString()),
                    gs(ALPHA_COMMAND_STRING),
                    BY_COMMAND_GLIDE_STRING,
                    byPattern,
                    GET_COMMAND_GLIDE_STRING,
                    getPattern
                };
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(Sort), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response =
                service.sort(
                        key,
                        SortOptionsBinary.builder()
                                .alpha()
                                .limit(new SortBaseOptions.Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .getPattern(getPattern)
                                .byPattern(byPattern)
                                .build());
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sscan_returns_success() {
        // setup
        String key = "testKey";
        String cursor = "0";
        String[] arguments = new String[] {key, cursor};
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(SScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.sscan(key, cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scan_returns_success() {
        // setup
        String cursor = "0";
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(Scan), eq(new String[] {cursor}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.scan(cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scan_binary_returns_success() {
        // setup
        GlideString cursor = gs("0");
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(Scan), eq(new GlideString[] {cursor}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.scan(cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scan_with_options_returns_success() {
        // setup
        String cursor = "0";
        ScanOptions options =
                ScanOptions.builder().matchPattern("match").count(10L).type(STRING).build();
        String[] args =
                new String[] {
                    cursor,
                    MATCH_OPTION_STRING,
                    "match",
                    COUNT_OPTION_STRING,
                    "10",
                    TYPE_OPTION_STRING,
                    STRING.toString()
                };
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(Scan), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.scan(cursor, options);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scan_binary_with_options_returns_success() {
        // setup
        GlideString cursor = gs("0");
        ScanOptions options =
                ScanOptions.builder().matchPattern("match").count(10L).type(STRING).build();
        GlideString[] args =
                new GlideString[] {
                    cursor,
                    gs(MATCH_OPTION_STRING),
                    gs("match"),
                    gs(COUNT_OPTION_STRING),
                    gs("10"),
                    gs(TYPE_OPTION_STRING),
                    gs(STRING.toString())
                };
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(Scan), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.scan(cursor, options);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sscan_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString cursor = gs("0");
        GlideString[] arguments = new GlideString[] {key, cursor};
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(SScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.sscan(key, cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sortReadOnly_with_options_returns_success() {
        // setup
        String[] result = new String[] {"1", "2", "3"};
        String key = "key";
        String byPattern = "byPattern";
        String getPattern = "getPattern";
        String[] args =
                new String[] {key, BY_COMMAND_STRING, byPattern, GET_COMMAND_STRING, getPattern};
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(SortReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String[]> response =
                service.sortReadOnly(
                        key, SortOptions.builder().getPattern(getPattern).byPattern(byPattern).build());
        String[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortReadOnly_with_options_binary_returns_success() {
        // setup
        GlideString[] result = new GlideString[] {gs("1"), gs("2"), gs("3")};
        GlideString key = gs("key");
        GlideString byPattern = gs("byPattern");
        GlideString getPattern = gs("getPattern");
        GlideString[] args =
                new GlideString[] {
                    key, BY_COMMAND_GLIDE_STRING, byPattern, GET_COMMAND_GLIDE_STRING, getPattern
                };
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(SortReadOnly), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<GlideString[]> response =
                service.sortReadOnly(
                        key, SortOptionsBinary.builder().getPattern(getPattern).byPattern(byPattern).build());
        GlideString[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortStore_with_options_returns_success() {
        // setup
        Long result = 5L;
        String key = "key";
        String destKey = "destKey";
        Long limitOffset = 0L;
        Long limitCount = 2L;
        String byPattern = "byPattern";
        String getPattern = "getPattern";
        String[] args =
                new String[] {
                    key,
                    LIMIT_COMMAND_STRING,
                    limitOffset.toString(),
                    limitCount.toString(),
                    DESC.toString(),
                    ALPHA_COMMAND_STRING,
                    BY_COMMAND_STRING,
                    byPattern,
                    GET_COMMAND_STRING,
                    getPattern,
                    STORE_COMMAND_STRING,
                    destKey
                };
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Sort), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.sortStore(
                        key,
                        destKey,
                        SortOptions.builder()
                                .alpha()
                                .limit(new SortBaseOptions.Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .getPattern(getPattern)
                                .byPattern(byPattern)
                                .build());
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sortStore_with_options_binary_returns_success() {
        // setup
        Long result = 5L;
        GlideString key = gs("key");
        GlideString destKey = gs("destKey");
        Long limitOffset = 0L;
        Long limitCount = 2L;
        GlideString byPattern = gs("byPattern");
        GlideString getPattern = gs("getPattern");
        GlideString[] args =
                new GlideString[] {
                    key,
                    gs(LIMIT_COMMAND_STRING),
                    gs(limitOffset.toString()),
                    gs(limitCount.toString()),
                    gs(DESC.toString()),
                    gs(ALPHA_COMMAND_STRING),
                    gs(BY_COMMAND_STRING),
                    byPattern,
                    GET_COMMAND_GLIDE_STRING,
                    getPattern,
                    gs(STORE_COMMAND_STRING),
                    destKey
                };
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Sort), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.sortStore(
                        key,
                        destKey,
                        SortOptionsBinary.builder()
                                .alpha()
                                .limit(new SortBaseOptions.Limit(limitOffset, limitCount))
                                .orderBy(DESC)
                                .getPattern(getPattern)
                                .byPattern(byPattern)
                                .build());
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void wait_returns_success() {
        // setup
        long numreplicas = 1L;
        long timeout = 1000L;
        Long result = 5L;
        String[] args = new String[] {"1", "1000"};

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(result);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Wait), eq(args), any())).thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.wait(numreplicas, timeout);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(result, payload);
    }

    @SneakyThrows
    @Test
    public void sscan_with_options_returns_success() {
        // setup
        String key = "testKey";
        String cursor = "0";
        String[] arguments =
                new String[] {key, cursor, MATCH_OPTION_STRING, "*", COUNT_OPTION_STRING, "1"};
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(SScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.sscan(key, cursor, SScanOptions.builder().matchPattern("*").count(1L).build());
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void sscan_with_options_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString cursor = gs("0");
        GlideString[] arguments =
                new GlideString[] {
                    key, cursor, MATCH_OPTION_GLIDE_STRING, gs("*"), COUNT_OPTION_GLIDE_STRING, gs("1")
                };
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(SScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.sscan(
                        key, cursor, SScanOptionsBinary.builder().matchPattern(gs("*")).count(1L).build());
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zscan_returns_success() {
        // setup
        String key = "testKey";
        String cursor = "0";
        String[] arguments = new String[] {key, cursor};
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.zscan(key, cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zscan_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString cursor = gs("0");
        GlideString[] arguments = new GlideString[] {key, cursor};
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.zscan(key, cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zscan_with_options_returns_success() {
        // setup
        String key = "testKey";
        String cursor = "0";
        String[] arguments =
                new String[] {key, cursor, MATCH_OPTION_STRING, "*", COUNT_OPTION_STRING, "1"};
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.zscan(key, cursor, ZScanOptions.builder().matchPattern("*").count(1L).build());
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void zscan_with_options_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString cursor = gs("0");
        GlideString[] arguments =
                new GlideString[] {
                    key, cursor, MATCH_OPTION_GLIDE_STRING, gs("*"), COUNT_OPTION_GLIDE_STRING, gs("1")
                };
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(ZScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.zscan(
                        key, cursor, ZScanOptionsBinary.builder().matchPattern(gs("*")).count(1L).build());
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hscan_returns_success() {
        // setup
        String key = "testKey";
        String cursor = "0";
        String[] arguments = new String[] {key, cursor};
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(HScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.hscan(key, cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hscan_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString cursor = gs("0");
        GlideString[] arguments = new GlideString[] {key, cursor};
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(HScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.hscan(key, cursor);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hscan_with_options_returns_success() {
        // setup
        String key = "testKey";
        String cursor = "0";
        String[] arguments =
                new String[] {key, cursor, MATCH_OPTION_STRING, "*", COUNT_OPTION_STRING, "1"};
        Object[] value = new Object[] {0L, new String[] {"hello", "world"}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(HScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.hscan(key, cursor, HScanOptions.builder().matchPattern("*").count(1L).build());
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void hscan_with_options_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString cursor = gs("0");
        GlideString[] arguments =
                new GlideString[] {
                    key, cursor, MATCH_OPTION_GLIDE_STRING, gs("*"), COUNT_OPTION_GLIDE_STRING, gs("1")
                };
        Object[] value = new Object[] {0L, new GlideString[] {gs("hello"), gs("world")}};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(HScan), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response =
                service.hscan(
                        key, cursor, HScanOptionsBinary.builder().matchPattern(gs("*")).count(1L).build());
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    private static List<Arguments> getGeoSearchArguments() {
        return List.of(
                Arguments.of(
                        "geosearch_from_member_no_options",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        null,
                        new String[] {
                            "GeoSearchTestKey", FROMMEMBER_VALKEY_API, "member", "BYRADIUS", "1.0", "km"
                        },
                        new String[] {"place1", "place2"}),
                Arguments.of(
                        "geosearch_from_member_ASC",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.ASC),
                        new String[] {
                            "GeoSearchTestKey",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYBOX",
                            "1.0",
                            "1.0",
                            "km",
                            "ASC"
                        },
                        new String[] {"place2", "place1"}),
                Arguments.of(
                        "geosearch_from_lonlat_with_count",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(2),
                        new String[] {
                            "GeoSearchTestKey",
                            FROMLONLAT_VALKEY_API,
                            "1.0",
                            "1.0",
                            "BYRADIUS",
                            "1.0",
                            "km",
                            COUNT_VALKEY_API,
                            "2"
                        },
                        new String[] {"place3", "place4"}),
                Arguments.of(
                        "geosearch_from_lonlat_with_count_any_DESC",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.DESC, 2, true),
                        new String[] {
                            "GeoSearchTestKey",
                            FROMLONLAT_VALKEY_API,
                            "1.0",
                            "1.0",
                            "BYBOX",
                            "1.0",
                            "1.0",
                            "km",
                            COUNT_VALKEY_API,
                            "2",
                            "ANY",
                            "DESC"
                        },
                        new String[] {"place4", "place3"}));
    }

    private static List<Arguments> getGeoSearchArgumentsBinary() {
        return List.of(
                Arguments.of(
                        "geosearch_from_member_no_options",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        null,
                        new GlideString[] {
                            gs("GeoSearchTestKey"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYRADIUS"),
                            gs("1.0"),
                            gs("km")
                        },
                        new GlideString[] {gs("place1"), gs("place2")}),
                Arguments.of(
                        "geosearch_from_member_ASC",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.ASC),
                        new GlideString[] {
                            gs("GeoSearchTestKey"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYBOX"),
                            gs("1.0"),
                            gs("1.0"),
                            gs("km"),
                            gs("ASC")
                        },
                        new GlideString[] {gs("place2"), gs("place1")}),
                Arguments.of(
                        "geosearch_from_lonlat_with_count",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(2),
                        new GlideString[] {
                            gs("GeoSearchTestKey"),
                            gs(FROMLONLAT_VALKEY_API),
                            gs("1.0"),
                            gs("1.0"),
                            gs("BYRADIUS"),
                            gs("1.0"),
                            gs("km"),
                            gs(COUNT_VALKEY_API),
                            gs("2")
                        },
                        new GlideString[] {gs("place3"), gs("place4")}),
                Arguments.of(
                        "geosearch_from_lonlat_with_count_any_DESC",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.DESC, 2, true),
                        new GlideString[] {
                            gs("GeoSearchTestKey"),
                            gs(FROMLONLAT_VALKEY_API),
                            gs("1.0"),
                            gs("1.0"),
                            gs("BYBOX"),
                            gs("1.0"),
                            gs("1.0"),
                            gs("km"),
                            gs(COUNT_VALKEY_API),
                            gs("2"),
                            gs("ANY"),
                            gs("DESC")
                        },
                        new GlideString[] {gs("place4"), gs("place3")}));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{1}")
    @MethodSource("getGeoSearchArguments")
    public void geosearch_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchResultOptions resultOptions,
            String[] args,
            String[] expected) {
        // setup
        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(GeoSearch), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<String[]> response =
                resultOptions == null
                        ? service.geosearch("GeoSearchTestKey", origin, shape)
                        : service.geosearch("GeoSearchTestKey", origin, shape, resultOptions);
        String[] payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertArrayEquals(expected, payload);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{1}")
    @MethodSource("getGeoSearchArgumentsBinary")
    public void geosearch_binary_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchResultOptions resultOptions,
            GlideString[] args,
            GlideString[] expected) {
        // setup
        CompletableFuture<GlideString[]> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<GlideString[]>submitNewCommand(eq(GeoSearch), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<GlideString[]> response =
                resultOptions == null
                        ? service.geosearch(gs("GeoSearchTestKey"), origin, shape)
                        : service.geosearch(gs("GeoSearchTestKey"), origin, shape, resultOptions);
        GlideString[] payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertArrayEquals(expected, payload);
    }

    private static List<Arguments> getGeoSearchWithOptionsArguments() {
        return List.of(
                Arguments.of(
                        "geosearch_from_member_with_options",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        GeoSearchOptions.builder().withcoord().withdist().withhash().build(),
                        null,
                        new String[] {
                            "GeoSearchTestKey",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYRADIUS",
                            "1.0",
                            "km",
                            "WITHDIST",
                            "WITHCOORD",
                            "WITHHASH"
                        },
                        new Object[] {
                            new Object[] {
                                "Catania",
                                new Object[] {
                                    56.4413, 3479447370796909L, new Object[] {15.087267458438873, 37.50266842333162}
                                }
                            },
                            new Object[] {
                                "Palermo",
                                new Object[] {
                                    190.4424, 3479099956230698L, new Object[] {13.361389338970184, 38.1155563954963}
                                }
                            }
                        }),
                Arguments.of(
                        "geosearch_from_member_with_options_and_SORT_and_COUNT_ANY",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        GeoSearchOptions.builder().withcoord().withdist().withhash().build(),
                        new GeoSearchResultOptions(SortOrder.ASC, 2, true),
                        new String[] {
                            "GeoSearchTestKey",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYBOX",
                            "1.0",
                            "1.0",
                            "km",
                            "WITHDIST",
                            "WITHCOORD",
                            "WITHHASH",
                            "COUNT",
                            "2",
                            "ANY",
                            "ASC"
                        },
                        new Object[] {
                            new Object[] {
                                "Catania",
                                new Object[] {
                                    56.4413, 3479447370796909L, new Object[] {15.087267458438873, 37.50266842333162}
                                }
                            }
                        }));
    }

    private static List<Arguments> getGeoSearchWithOptionsArgumentsBinary() {
        return List.of(
                Arguments.of(
                        "geosearch_from_member_with_options",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        GeoSearchOptions.builder().withcoord().withdist().withhash().build(),
                        null,
                        new GlideString[] {
                            gs("GeoSearchTestKey"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYRADIUS"),
                            gs("1.0"),
                            gs("km"),
                            gs("WITHDIST"),
                            gs("WITHCOORD"),
                            gs("WITHHASH")
                        },
                        new Object[] {
                            new Object[] {
                                gs("Catania"),
                                new Object[] {
                                    56.4413, 3479447370796909L, new Object[] {15.087267458438873, 37.50266842333162}
                                }
                            },
                            new Object[] {
                                gs("Palermo"),
                                new Object[] {
                                    190.4424, 3479099956230698L, new Object[] {13.361389338970184, 38.1155563954963}
                                }
                            }
                        }),
                Arguments.of(
                        "geosearch_from_member_with_options_and_SORT_and_COUNT_ANY",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        GeoSearchOptions.builder().withcoord().withdist().withhash().build(),
                        new GeoSearchResultOptions(SortOrder.ASC, 2, true),
                        new GlideString[] {
                            gs("GeoSearchTestKey"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYBOX"),
                            gs("1.0"),
                            gs("1.0"),
                            gs("km"),
                            gs("WITHDIST"),
                            gs("WITHCOORD"),
                            gs("WITHHASH"),
                            gs("COUNT"),
                            gs("2"),
                            gs("ANY"),
                            gs("ASC")
                        },
                        new Object[] {
                            new Object[] {
                                gs("Catania"),
                                new Object[] {
                                    56.4413, 3479447370796909L, new Object[] {15.087267458438873, 37.50266842333162}
                                }
                            }
                        }));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{2}")
    @MethodSource("getGeoSearchWithOptionsArguments")
    public void geosearch_with_options_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchOptions options,
            GeoSearchResultOptions resultOptions,
            String[] args,
            Object[] expected) {
        // setup
        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(GeoSearch), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<Object[]> response =
                resultOptions == null
                        ? service.geosearch("GeoSearchTestKey", origin, shape, options)
                        : service.geosearch("GeoSearchTestKey", origin, shape, options, resultOptions);
        Object[] payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertArrayEquals(expected, payload);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{2}")
    @MethodSource("getGeoSearchWithOptionsArgumentsBinary")
    public void geosearch_with_options_binary_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchOptions options,
            GeoSearchResultOptions resultOptions,
            GlideString[] args,
            Object[] expected) {
        // setup
        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(GeoSearch), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<Object[]> response =
                resultOptions == null
                        ? service.geosearch(gs("GeoSearchTestKey"), origin, shape, options)
                        : service.geosearch(gs("GeoSearchTestKey"), origin, shape, options, resultOptions);
        Object[] payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertArrayEquals(expected, payload);
    }

    private static List<Arguments> getGeoSearchStoreArguments() {
        return List.of(
                Arguments.of(
                        "geosearchstore_from_member_no_options",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        null,
                        new String[] {
                            "testDestination",
                            "testSource",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYRADIUS",
                            "1.0",
                            "km"
                        },
                        1L),
                Arguments.of(
                        "geosearchstore_from_member_ASC",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.ASC),
                        new String[] {
                            "testDestination",
                            "testSource",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYBOX",
                            "1.0",
                            "1.0",
                            "km",
                            "ASC"
                        },
                        2L),
                Arguments.of(
                        "geosearchstore_from_lonlat_with_count",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(2),
                        new String[] {
                            "testDestination",
                            "testSource",
                            FROMLONLAT_VALKEY_API,
                            "1.0",
                            "1.0",
                            "BYRADIUS",
                            "1.0",
                            "km",
                            COUNT_VALKEY_API,
                            "2"
                        },
                        3L),
                Arguments.of(
                        "geosearchstore_from_lonlat_with_count_any_DESC",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.DESC, 2, true),
                        new String[] {
                            "testDestination",
                            "testSource",
                            FROMLONLAT_VALKEY_API,
                            "1.0",
                            "1.0",
                            "BYBOX",
                            "1.0",
                            "1.0",
                            "km",
                            COUNT_VALKEY_API,
                            "2",
                            "ANY",
                            "DESC"
                        },
                        4L));
    }

    private static List<Arguments> getGeoSearchStoreArgumentsBinary() {
        return List.of(
                Arguments.of(
                        "geosearchstore_from_member_no_options",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        null,
                        new GlideString[] {
                            gs("testDestination"),
                            gs("testSource"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYRADIUS"),
                            gs("1.0"),
                            gs("km")
                        },
                        1L),
                Arguments.of(
                        "geosearchstore_from_member_ASC",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.ASC),
                        new GlideString[] {
                            gs("testDestination"),
                            gs("testSource"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYBOX"),
                            gs("1.0"),
                            gs("1.0"),
                            gs("km"),
                            gs("ASC")
                        },
                        2L),
                Arguments.of(
                        "geosearchstore_from_lonlat_with_count",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(2),
                        new GlideString[] {
                            gs("testDestination"),
                            gs("testSource"),
                            gs(FROMLONLAT_VALKEY_API),
                            gs("1.0"),
                            gs("1.0"),
                            gs("BYRADIUS"),
                            gs("1.0"),
                            gs("km"),
                            gs(COUNT_VALKEY_API),
                            gs("2")
                        },
                        3L),
                Arguments.of(
                        "geosearchstore_from_lonlat_with_count_any_DESC",
                        new GeoSearchOrigin.CoordOrigin(new GeospatialData(1.0, 1.0)),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        new GeoSearchResultOptions(SortOrder.DESC, 2, true),
                        new GlideString[] {
                            gs("testDestination"),
                            gs("testSource"),
                            gs(FROMLONLAT_VALKEY_API),
                            gs("1.0"),
                            gs("1.0"),
                            gs("BYBOX"),
                            gs("1.0"),
                            gs("1.0"),
                            gs("km"),
                            gs(COUNT_VALKEY_API),
                            gs("2"),
                            gs("ANY"),
                            gs("DESC")
                        },
                        4L));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{3}")
    @MethodSource("getGeoSearchStoreArguments")
    public void geosearchstore_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchResultOptions resultOptions,
            String[] args,
            Long expected) {
        // setup
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoSearchStore), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<Long> response =
                resultOptions == null
                        ? service.geosearchstore("testDestination", "testSource", origin, shape)
                        : service.geosearchstore("testDestination", "testSource", origin, shape, resultOptions);
        Long payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertEquals(expected, payload);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{3}")
    @MethodSource("getGeoSearchStoreArgumentsBinary")
    public void geosearchstore_binary_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchResultOptions resultOptions,
            GlideString[] args,
            Long expected) {
        // setup
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoSearchStore), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<Long> response =
                resultOptions == null
                        ? service.geosearchstore(gs("testDestination"), gs("testSource"), origin, shape)
                        : service.geosearchstore(
                                gs("testDestination"), gs("testSource"), origin, shape, resultOptions);
        Long payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertEquals(expected, payload);
    }

    private static List<Arguments> getGeoSearchStoreWithOptionsArguments() {
        return List.of(
                Arguments.of(
                        "geosearchstore_from_member_with_options",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        GeoSearchStoreOptions.builder().build(),
                        null,
                        new String[] {
                            "testDestination",
                            "testSource",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYRADIUS",
                            "1.0",
                            "km"
                        },
                        1L),
                Arguments.of(
                        "geosearchstore_from_member_with_options_and_SORT_and_COUNT_ANY",
                        new GeoSearchOrigin.MemberOrigin("member"),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        GeoSearchStoreOptions.builder().storedist().build(),
                        new GeoSearchResultOptions(SortOrder.ASC, 2, true),
                        new String[] {
                            "testDestination",
                            "testSource",
                            FROMMEMBER_VALKEY_API,
                            "member",
                            "BYBOX",
                            "1.0",
                            "1.0",
                            "km",
                            "STOREDIST",
                            "COUNT",
                            "2",
                            "ANY",
                            "ASC"
                        },
                        2L));
    }

    private static List<Arguments> getGeoSearchStoreWithOptionsArgumentsBinary() {
        return List.of(
                Arguments.of(
                        "geosearchstore_from_member_with_options",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, GeoUnit.KILOMETERS),
                        GeoSearchStoreOptions.builder().build(),
                        null,
                        new GlideString[] {
                            gs("testDestination"),
                            gs("testSource"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYRADIUS"),
                            gs("1.0"),
                            gs("km")
                        },
                        1L),
                Arguments.of(
                        "geosearchstore_from_member_with_options_and_SORT_and_COUNT_ANY",
                        new GeoSearchOrigin.MemberOriginBinary(gs("member")),
                        new GeoSearchShape(1L, 1L, GeoUnit.KILOMETERS),
                        GeoSearchStoreOptions.builder().storedist().build(),
                        new GeoSearchResultOptions(SortOrder.ASC, 2, true),
                        new GlideString[] {
                            gs("testDestination"),
                            gs("testSource"),
                            gs(FROMMEMBER_VALKEY_API),
                            gs("member"),
                            gs("BYBOX"),
                            gs("1.0"),
                            gs("1.0"),
                            gs("km"),
                            gs("STOREDIST"),
                            gs("COUNT"),
                            gs("2"),
                            gs("ANY"),
                            gs("ASC")
                        },
                        2L));
    }

    @SneakyThrows
    @ParameterizedTest(name = "{4}")
    @MethodSource("getGeoSearchStoreWithOptionsArguments")
    public void geosearchstore_with_options_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchStoreOptions options,
            GeoSearchResultOptions resultOptions,
            String[] args,
            Long expected) {
        // setup
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoSearchStore), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<Long> response =
                resultOptions == null
                        ? service.geosearchstore("testDestination", "testSource", origin, shape, options)
                        : service.geosearchstore(
                                "testDestination", "testSource", origin, shape, options, resultOptions);
        Long payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertEquals(expected, payload);
    }

    @SneakyThrows
    @ParameterizedTest(name = "{4}")
    @MethodSource("getGeoSearchStoreWithOptionsArgumentsBinary")
    public void geosearchstore_with_options_binary_returns_success(
            String testName,
            GeoSearchOrigin.SearchOrigin origin,
            GeoSearchShape shape,
            GeoSearchStoreOptions options,
            GeoSearchResultOptions resultOptions,
            GlideString[] args,
            Long expected) {
        // setup
        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(expected);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(GeoSearchStore), eq(args), any()))
                .thenReturn(testResponse);

        // Exercise
        CompletableFuture<Long> response =
                resultOptions == null
                        ? service.geosearchstore(
                                gs("testDestination"), gs("testSource"), origin, shape, options)
                        : service.geosearchstore(
                                gs("testDestination"), gs("testSource"), origin, shape, options, resultOptions);
        Long payload = response.get();

        // Verify
        assertEquals(testResponse, response);
        assertEquals(expected, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoGroups_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = {key};
        Map<String, Object>[] mockResult =
                new Map[] {
                    Map.of(
                            "name",
                            "groupName",
                            "consumers",
                            2,
                            "pending",
                            2,
                            "last-delivered-id",
                            "1638126030001-0",
                            "entries-read",
                            2,
                            "lag",
                            2)
                };

        CompletableFuture<Map<String, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<String, Object>[]>submitNewCommand(
                        eq(XInfoGroups), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>[]> response = service.xinfoGroups(key);
        Map<String, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoGroups_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = {key};
        Map<GlideString, Object>[] mockResult =
                new Map[] {
                    Map.of(
                            gs("name"),
                            gs("groupName"),
                            gs("consumers"),
                            2,
                            gs("pending"),
                            2,
                            gs("last-delivered-id"),
                            gs("1638126030001-0"),
                            gs("entries-read"),
                            2,
                            gs("lag"),
                            2)
                };

        CompletableFuture<Map<GlideString, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>[]>submitNewCommand(
                        eq(XInfoGroups), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>[]> response = service.xinfoGroups(key);
        Map<GlideString, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoConsumers_returns_success() {
        // setup
        String key = "testKey";
        String groupName = "groupName";
        String[] arguments = {key, groupName};
        Map<String, Object>[] mockResult =
                new Map[] {
                    Map.of("name", "groupName", "pending", 2, "idle", 9104628, "inactive", 18104698)
                };

        CompletableFuture<Map<String, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<String, Object>[]>submitNewCommand(
                        eq(XInfoConsumers), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>[]> response = service.xinfoConsumers(key, groupName);
        Map<String, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoConsumers_binary_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString groupName = gs("groupName");
        GlideString[] arguments = {key, groupName};
        Map<GlideString, Object>[] mockResult =
                new Map[] {
                    Map.of(
                            gs("name"),
                            gs("groupName"),
                            gs("pending"),
                            2,
                            gs("idle"),
                            9104628,
                            gs("inactive"),
                            18104698)
                };

        CompletableFuture<Map<GlideString, Object>[]> testResponse = new CompletableFuture<>();
        testResponse.complete(mockResult);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>[]>submitNewCommand(
                        eq(XInfoConsumers), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>[]> response = service.xinfoConsumers(key, groupName);
        Map<GlideString, Object>[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(mockResult, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoStream_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = {key};
        Map<String, Object> summary = Map.of("some", "data");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(
                        eq(XInfoStream), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.xinfoStream(key);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoStreamFull_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = {key, FULL};
        Map<String, Object> summary = Map.of("some", "data");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(
                        eq(XInfoStream), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.xinfoStreamFull(key);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoStreamFull_with_count_returns_success() {
        // setup
        String key = "testKey";
        int count = 42;
        String[] arguments = {key, FULL, COUNT, "42"};
        Map<String, Object> summary = Map.of("some", "data");

        CompletableFuture<Map<String, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Map<String, Object>>submitNewCommand(
                        eq(XInfoStream), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<String, Object>> response = service.xinfoStreamFull(key, count);
        Map<String, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoStream_glidestring_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = {key};
        Map<GlideString, Object> summary = Map.of(gs("some"), gs("data"));

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(
                        eq(XInfoStream), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response = service.xinfoStream(key);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoStreamFull_glidestring_returns_success() {
        // setup
        GlideString key = gs("testKey");
        GlideString[] arguments = {key, gs(FULL)};
        Map<GlideString, Object> summary = Map.of(gs("some"), gs("data"));

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(
                        eq(XInfoStream), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response = service.xinfoStreamFull(key);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void xinfoStreamFull_glidestring_with_count_returns_success() {
        // setup
        GlideString key = gs("testKey");
        int count = 42;
        GlideString[] arguments = {key, gs(FULL), gs(COUNT), gs("42")};
        Map<GlideString, Object> summary = Map.of(gs("some"), gs("data"));

        CompletableFuture<Map<GlideString, Object>> testResponse = new CompletableFuture<>();
        testResponse.complete(summary);

        // match on protobuf request
        when(commandManager.<Map<GlideString, Object>>submitNewCommand(
                        eq(XInfoStream), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Map<GlideString, Object>> response = service.xinfoStreamFull(key, count);
        Map<GlideString, Object> payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(summary, payload);
    }

    @SneakyThrows
    @Test
    public void scriptExists_returns_success() {
        // setup
        String hash = UUID.randomUUID().toString();
        String[] sha1s = {hash};
        Boolean[] value = {true};

        CompletableFuture<Boolean[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean[]>submitNewCommand(eq(ScriptExists), eq(sha1s), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean[]> response = service.scriptExists(sha1s);
        Boolean[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scriptExists_binary_returns_success() {
        // setup
        GlideString hash = gs(UUID.randomUUID().toString());
        GlideString[] sha1s = {hash};
        Boolean[] value = {true};

        CompletableFuture<Boolean[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean[]>submitNewCommand(eq(ScriptExists), eq(sha1s), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Boolean[]> response = service.scriptExists(sha1s);
        Boolean[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void scriptFlush_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ScriptFlush), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.scriptFlush();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void scriptFlush_with_mode_returns_success() {
        // setup
        String[] args = new String[] {ASYNC.toString()};
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ScriptFlush), eq(args), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.scriptFlush(ASYNC);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }

    @SneakyThrows
    @Test
    public void scriptKill_returns_success() {
        // setup
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(OK);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(ScriptKill), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.scriptKill();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(OK, payload);
    }
}
