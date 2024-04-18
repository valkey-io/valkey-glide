/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.OK;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORES_REDIS_API;
import static glide.api.commands.SortedSetBaseCommands.WITH_SCORE_REDIS_API;
import static glide.api.models.commands.LInsertOptions.InsertPosition.BEFORE;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.StreamAddOptions.NO_MAKE_STREAM_REDIS_API;
import static glide.api.models.commands.StreamAddOptions.TRIM_EXACT_REDIS_API;
import static glide.api.models.commands.StreamAddOptions.TRIM_LIMIT_REDIS_API;
import static glide.api.models.commands.StreamAddOptions.TRIM_MAXLEN_REDIS_API;
import static glide.api.models.commands.StreamAddOptions.TRIM_MINID_REDIS_API;
import static glide.api.models.commands.StreamAddOptions.TRIM_NOT_EXACT_REDIS_API;
import static glide.utils.ArrayTransformUtils.concatenateArrays;
import static glide.utils.ArrayTransformUtils.convertMapToKeyValueStringArray;
import static glide.utils.ArrayTransformUtils.convertMapToValueKeyStringArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static redis_request.RedisRequestOuterClass.RequestType.Blpop;
import static redis_request.RedisRequestOuterClass.RequestType.Brpop;
import static redis_request.RedisRequestOuterClass.RequestType.ClientGetName;
import static redis_request.RedisRequestOuterClass.RequestType.ClientId;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigGet;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigResetStat;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigRewrite;
import static redis_request.RedisRequestOuterClass.RequestType.ConfigSet;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Echo;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.Expire;
import static redis_request.RedisRequestOuterClass.RequestType.ExpireAt;
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
import static redis_request.RedisRequestOuterClass.RequestType.Hvals;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.LInsert;
import static redis_request.RedisRequestOuterClass.RequestType.LLen;
import static redis_request.RedisRequestOuterClass.RequestType.LPop;
import static redis_request.RedisRequestOuterClass.RequestType.LPush;
import static redis_request.RedisRequestOuterClass.RequestType.LPushX;
import static redis_request.RedisRequestOuterClass.RequestType.LRange;
import static redis_request.RedisRequestOuterClass.RequestType.LRem;
import static redis_request.RedisRequestOuterClass.RequestType.LTrim;
import static redis_request.RedisRequestOuterClass.RequestType.Lindex;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
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
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SInterStore;
import static redis_request.RedisRequestOuterClass.RequestType.SIsMember;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.Save;
import static redis_request.RedisRequestOuterClass.RequestType.Select;
import static redis_request.RedisRequestOuterClass.RequestType.SetRange;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;
import static redis_request.RedisRequestOuterClass.RequestType.Strlen;
import static redis_request.RedisRequestOuterClass.RequestType.TTL;
import static redis_request.RedisRequestOuterClass.RequestType.Time;
import static redis_request.RedisRequestOuterClass.RequestType.Type;
import static redis_request.RedisRequestOuterClass.RequestType.Unlink;
import static redis_request.RedisRequestOuterClass.RequestType.XAdd;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiff;
import static redis_request.RedisRequestOuterClass.RequestType.ZDiffStore;
import static redis_request.RedisRequestOuterClass.RequestType.ZLexCount;
import static redis_request.RedisRequestOuterClass.RequestType.ZMScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMax;
import static redis_request.RedisRequestOuterClass.RequestType.ZPopMin;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByLex;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByRank;
import static redis_request.RedisRequestOuterClass.RequestType.ZRemRangeByScore;
import static redis_request.RedisRequestOuterClass.RequestType.ZScore;
import static redis_request.RedisRequestOuterClass.RequestType.Zadd;
import static redis_request.RedisRequestOuterClass.RequestType.Zcard;
import static redis_request.RedisRequestOuterClass.RequestType.Zcount;
import static redis_request.RedisRequestOuterClass.RequestType.Zrange;
import static redis_request.RedisRequestOuterClass.RequestType.Zrank;
import static redis_request.RedisRequestOuterClass.RequestType.Zrem;

import glide.api.models.Script;
import glide.api.models.Transaction;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.Expiry;
import glide.api.models.commands.StreamAddOptions;
import glide.api.models.commands.ZaddOptions;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

public class RedisClientTest {

    RedisClient service;

    ConnectionManager connectionManager;

    CommandManager commandManager;

    @BeforeEach
    public void setUp() {
        connectionManager = mock(ConnectionManager.class);
        commandManager = mock(CommandManager.class);
        service = new RedisClient(connectionManager, commandManager);
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
    public void exec() {
        // setup
        Object[] value = new Object[] {"PONG", "PONG"};
        Transaction transaction = new Transaction().ping().ping();

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(transaction), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object[]> response = service.exec(transaction);
        Object[] payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertArrayEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void echo_returns_success() {
        // setup
        String message = "GLIDE FOR REDIS";
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
    public void get_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(GetString), eq(new String[] {key}), any()))
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
    public void set_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(null);
        when(commandManager.<String>submitNewCommand(
                        eq(SetString), eq(new String[] {key, value}), any()))
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
        String[] arguments = new String[] {key, value, ONLY_IF_EXISTS.getRedisApi(), "KEEPTTL"};

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(null);
        when(commandManager.<String>submitNewCommand(eq(SetString), eq(arguments), any()))
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
                    key, value, ONLY_IF_DOES_NOT_EXIST.getRedisApi(), RETURN_OLD_VALUE, "EXAT", "60"
                };
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(SetString), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertNotNull(response);
        assertEquals(value, response.get());
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
                        eq(script), eq(List.of("key1", "key2")), eq(List.of("arg1", "arg2")), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.invokeScript(script, options);

        // verify
        assertEquals(testResponse, response);
        assertEquals(payload, response.get());
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
        String[] arguments =
                new String[] {InfoOptions.Section.ALL.toString(), InfoOptions.Section.DEFAULT.toString()};
        String testPayload = "Key: Value";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        InfoOptions options =
                InfoOptions.builder()
                        .section(InfoOptions.Section.ALL)
                        .section(InfoOptions.Section.DEFAULT)
                        .build();
        CompletableFuture<String> response = service.info(options);
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
        CompletableFuture<String> response = service.info(InfoOptions.builder().build());
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
    public void hget_success() {
        // setup
        String key = "testKey";
        String field = "field";
        String[] args = new String[] {key, field};
        String value = "value";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);
        when(commandManager.<String>submitNewCommand(eq(HashGet), eq(args), any()))
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
        when(commandManager.<Long>submitNewCommand(eq(HashSet), eq(args), any()))
                .thenReturn(testResponse);

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
    public void hdel_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {key, "testField1", "testField2"};
        Long value = 2L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(HashDel), eq(args), any()))
                .thenReturn(testResponse);

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
    public void hvals_success() {
        // setup
        String key = "testKey";
        String[] args = {key};
        String[] values = new String[] {"value1", "value2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(values);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Hvals), eq(args), any()))
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
    public void hmget_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {"testKey", "testField1", "testField2"};
        String[] value = {"testValue1", "testValue2"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(HashMGet), eq(args), any()))
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
    public void hexists_success() {
        // setup
        String key = "testKey";
        String field = "testField";
        String[] args = new String[] {key, field};
        Boolean value = true;

        CompletableFuture<Boolean> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Boolean>submitNewCommand(eq(HashExists), eq(args), any()))
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
        when(commandManager.<Map<String, String>>submitNewCommand(eq(HashGetAll), eq(args), any()))
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
                        eq(HashIncrBy), eq(new String[] {key, field, Long.toString(amount)}), any()))
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
                        eq(HashIncrByFloat), eq(new String[] {key, field, Double.toString(amount)}), any()))
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
    public void lindex_returns_success() {
        // setup
        String key = "testKey";
        long index = 2;
        String[] args = new String[] {key, Long.toString(index)};
        String value = "value";

        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Lindex), eq(args), any()))
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
        when(commandManager.<Long>submitNewCommand(eq(Zadd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response =
                service.zadd(key, membersScores, ZaddOptions.builder().build(), false);
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
        ZaddOptions options =
                ZaddOptions.builder()
                        .conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_EXISTS)
                        .updateOptions(ZaddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
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
        when(commandManager.<Long>submitNewCommand(eq(Zadd), eq(arguments), any()))
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
        ZaddOptions options =
                ZaddOptions.builder()
                        .conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_DOES_NOT_EXIST)
                        .updateOptions(ZaddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
                        .build();
        Map<String, Double> membersScores = new LinkedHashMap<>();
        membersScores.put("testMember1", 1.0);
        membersScores.put("testMember2", 2.0);

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
        when(commandManager.<Double>submitNewCommand(eq(Zadd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Double> response =
                service.zaddIncr(key, member, increment, ZaddOptions.builder().build());
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
        ZaddOptions options =
                ZaddOptions.builder()
                        .updateOptions(ZaddOptions.UpdateOptions.SCORE_GREATER_THAN_CURRENT)
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
        when(commandManager.<Double>submitNewCommand(eq(Zadd), eq(arguments), any()))
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
        when(commandManager.<Long>submitNewCommand(eq(Zrem), eq(arguments), any()))
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
        when(commandManager.<Long>submitNewCommand(eq(Zcard), eq(arguments), any()))
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
    public void zrange_by_index_returns_success() {
        // setup
        String key = "testKey";
        RangeByIndex rangeByIndex = new RangeByIndex(0, 1);
        String[] arguments = new String[] {key, rangeByIndex.getStart(), rangeByIndex.getEnd()};
        String[] value = new String[] {"one", "two"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Zrange), eq(arguments), any()))
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
        when(commandManager.<String[]>submitNewCommand(eq(Zrange), eq(arguments), any()))
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
        when(commandManager.<String[]>submitNewCommand(eq(Zrange), eq(arguments), any()))
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
    public void zrangeWithScores_by_index_returns_success() {
        // setup
        String key = "testKey";
        RangeByIndex rangeByIndex = new RangeByIndex(0, 4);
        String[] arguments =
                new String[] {key, rangeByIndex.getStart(), rangeByIndex.getEnd(), WITH_SCORES_REDIS_API};
        Map<String, Double> value = Map.of("one", 1.0, "two", 2.0, "three", 3.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(Zrange), eq(arguments), any()))
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
                    WITH_SCORES_REDIS_API
                };
        Map<String, Double> value = Map.of("two", 2.0, "three", 3.0);

        CompletableFuture<Map<String, Double>> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Map<String, Double>>submitNewCommand(eq(Zrange), eq(arguments), any()))
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
    public void zrank_returns_success() {
        // setup
        String key = "testKey";
        String member = "testMember";
        String[] arguments = new String[] {key, member};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Zrank), eq(arguments), any()))
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
        String[] arguments = new String[] {key, member, WITH_SCORE_REDIS_API};
        Object[] value = new Object[] {1, 6.0};

        CompletableFuture<Object[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Object[]>submitNewCommand(eq(Zrank), eq(arguments), any()))
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
    public void zdiffWithScores_returns_success() {
        // setup
        String key1 = "testKey1";
        String key2 = "testKey2";
        String[] arguments = new String[] {"2", key1, key2, WITH_SCORES_REDIS_API};
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
    public void zcount_returns_success() {
        // setup
        String key = "testKey";
        String[] arguments = new String[] {key, "-inf", "10.0"};
        Long value = 3L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(Zcount), eq(arguments), any()))
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
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, payload);
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
                StreamAddOptions.builder()
                        .id("id")
                        .makeStream(false)
                        .trim(new StreamAddOptions.MaxLen(true, 5L))
                        .build();

        String[] arguments =
                new String[] {
                    key,
                    NO_MAKE_STREAM_REDIS_API,
                    TRIM_MAXLEN_REDIS_API,
                    TRIM_EXACT_REDIS_API,
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

    private static List<Arguments> getStreamAddOptions() {
        return List.of(
                Arguments.of(
                        Pair.of(
                                // no TRIM option
                                StreamAddOptions.builder().id("id").makeStream(Boolean.FALSE).build(),
                                new String[] {"testKey", NO_MAKE_STREAM_REDIS_API, "id"}),
                        Pair.of(
                                // MAXLEN with LIMIT
                                StreamAddOptions.builder()
                                        .id("id")
                                        .makeStream(Boolean.TRUE)
                                        .trim(new StreamAddOptions.MaxLen(Boolean.TRUE, 5L, 10L))
                                        .build(),
                                new String[] {
                                    "testKey",
                                    TRIM_MAXLEN_REDIS_API,
                                    TRIM_EXACT_REDIS_API,
                                    Long.toString(5L),
                                    TRIM_LIMIT_REDIS_API,
                                    Long.toString(10L),
                                    "id"
                                }),
                        Pair.of(
                                // MAXLEN with non exact match
                                StreamAddOptions.builder()
                                        .makeStream(Boolean.FALSE)
                                        .trim(new StreamAddOptions.MaxLen(Boolean.FALSE, 2L))
                                        .build(),
                                new String[] {
                                    "testKey",
                                    NO_MAKE_STREAM_REDIS_API,
                                    TRIM_MAXLEN_REDIS_API,
                                    TRIM_NOT_EXACT_REDIS_API,
                                    Long.toString(2L),
                                    "*"
                                }),
                        Pair.of(
                                // MIN ID with LIMIT
                                StreamAddOptions.builder()
                                        .id("id")
                                        .makeStream(Boolean.TRUE)
                                        .trim(new StreamAddOptions.MinId(Boolean.TRUE, "testKey", 10L))
                                        .build(),
                                new String[] {
                                    "testKey",
                                    TRIM_MINID_REDIS_API,
                                    TRIM_EXACT_REDIS_API,
                                    Long.toString(5L),
                                    TRIM_LIMIT_REDIS_API,
                                    Long.toString(10L),
                                    "id"
                                }),
                        Pair.of(
                                // MIN ID with non exact match
                                StreamAddOptions.builder()
                                        .makeStream(Boolean.FALSE)
                                        .trim(new StreamAddOptions.MinId(Boolean.FALSE, "testKey"))
                                        .build(),
                                new String[] {
                                    "testKey",
                                    NO_MAKE_STREAM_REDIS_API,
                                    TRIM_MINID_REDIS_API,
                                    TRIM_NOT_EXACT_REDIS_API,
                                    Long.toString(5L),
                                    "*"
                                })));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getStreamAddOptions")
    public void xadd_with_options_returns_success(Pair<StreamAddOptions, String[]> optionAndArgs) {
        // setup
        String key = "testKey";
        Map<String, String> fieldValues = new LinkedHashMap<>();
        fieldValues.put("testField1", "testValue1");
        fieldValues.put("testField2", "testValue2");
        String[] arguments =
                ArrayUtils.addAll(optionAndArgs.getRight(), convertMapToKeyValueStringArray(fieldValues));

        String returnId = "testId";
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(returnId);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(XAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.xadd(key, fieldValues, optionAndArgs.getLeft());
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(returnId, payload);
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
    public void save_returns_success() {
        // setup
        String value = OK;
        CompletableFuture<String> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Save), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.save();

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
    public void blpop_returns_success() {
        // setup
        String key = "key";
        double timeout = 0.5;
        String[] arguments = new String[] {key, "0.5"};
        String[] value = new String[] {"key", "value"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Blpop), eq(arguments), any()))
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
    public void brpop_returns_success() {
        // setup
        String key = "key";
        double timeout = 0.5;
        String[] arguments = new String[] {key, "0.5"};
        String[] value = new String[] {"key", "value"};

        CompletableFuture<String[]> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<String[]>submitNewCommand(eq(Brpop), eq(arguments), any()))
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
    public void pfadd_returns_success() {
        // setup
        String key = "testKey";
        String[] elements = new String[] {"a", "b", "c"};
        String[] arguments = new String[] {key, "a", "b", "c"};
        Long value = 1L;

        CompletableFuture<Long> testResponse = new CompletableFuture<>();
        testResponse.complete(value);

        // match on protobuf request
        when(commandManager.<Long>submitNewCommand(eq(PfAdd), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Long> response = service.pfadd(key, elements);
        Long payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
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
}
