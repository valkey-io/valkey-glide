/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.BaseClient.OK;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Decr;
import static redis_request.RedisRequestOuterClass.RequestType.DecrBy;
import static redis_request.RedisRequestOuterClass.RequestType.Del;
import static redis_request.RedisRequestOuterClass.RequestType.Exists;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.HashDel;
import static redis_request.RedisRequestOuterClass.RequestType.HashExists;
import static redis_request.RedisRequestOuterClass.RequestType.HashGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashGetAll;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.HashIncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.HashMGet;
import static redis_request.RedisRequestOuterClass.RequestType.HashSet;
import static redis_request.RedisRequestOuterClass.RequestType.Incr;
import static redis_request.RedisRequestOuterClass.RequestType.IncrBy;
import static redis_request.RedisRequestOuterClass.RequestType.IncrByFloat;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.MGet;
import static redis_request.RedisRequestOuterClass.RequestType.MSet;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.SAdd;
import static redis_request.RedisRequestOuterClass.RequestType.SCard;
import static redis_request.RedisRequestOuterClass.RequestType.SMembers;
import static redis_request.RedisRequestOuterClass.RequestType.SRem;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SetOptions.Expiry;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        CompletableFuture<Object> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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
    public void ping_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn("PONG");

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
        CompletableFuture<String> testResponse = new CompletableFuture();
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
    public void del_returns_long_success() {
        // setup
        String[] keys = new String[] {"testKey1", "testKey2"};
        Long numberDeleted = 1L;
        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(numberDeleted);
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
    public void get_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
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
        CompletableFuture<Void> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(null);
        when(commandManager.<Void>submitNewCommand(eq(SetString), eq(new String[] {key, value}), any()))
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

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(null);
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
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
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
        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(numberExisting);
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
    public void info_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
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
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
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
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(values);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(MGet), eq(keys), any()))
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

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(OK);

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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Incr), eq(new String[] {key}), any()))
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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Decr), eq(new String[] {key}), any()))
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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(
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
    public void hget_success() {
        // setup
        String key = "testKey";
        String field = "field";
        String[] args = new String[] {key, field};
        String value = "value";

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
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

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
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
    public void hdel_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {key, "testField1", "testField2"};
        Long value = 2L;

        CompletableFuture testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
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
    public void hmget_success() {
        // setup
        String key = "testKey";
        String[] fields = {"testField1", "testField2"};
        String[] args = {"testKey", "testField1", "testField2"};
        String[] value = {"testValue1", "testValue2"};

        CompletableFuture<String[]> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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

        CompletableFuture<Boolean> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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

        CompletableFuture<Map<String, String>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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

        CompletableFuture<Double> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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
    public void sadd_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"testMember1", "testMember2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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
    public void srem_returns_success() {
        // setup
        String key = "testKey";
        String[] members = new String[] {"testMember1", "testMember2"};
        String[] arguments = ArrayUtils.addFirst(members, key);
        Long value = 2L;

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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

        CompletableFuture<Set<String>> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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

        CompletableFuture<Long> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

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
}
