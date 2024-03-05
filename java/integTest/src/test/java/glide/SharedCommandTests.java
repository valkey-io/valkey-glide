/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.CLUSTER_PORTS;
import static glide.TestConfiguration.REDIS_VERSION;
import static glide.TestConfiguration.STANDALONE_PORTS;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.Expiry.Milliseconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.BaseClient;
import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.exceptions.RequestException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(10)
public class SharedCommandTests {

    private static RedisClient standaloneClient = null;
    private static RedisClusterClient clusterClient = null;

    @Getter private static List<Arguments> clients;

    private static final String KEY_NAME = "key";
    private static final String INITIAL_VALUE = "VALUE";
    private static final String ANOTHER_VALUE = "VALUE2";

    @BeforeAll
    @SneakyThrows
    public static void init() {
        standaloneClient =
                RedisClient.CreateClient(
                                RedisClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(STANDALONE_PORTS[0]).build())
                                        .build())
                        .get();

        clusterClient =
                RedisClusterClient.CreateClient(
                                RedisClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().port(CLUSTER_PORTS[0]).build())
                                        .requestTimeout(5000)
                                        .build())
                        .get();

        clients = List.of(Arguments.of(standaloneClient), Arguments.of(clusterClient));
    }

    @AfterAll
    @SneakyThrows
    public static void teardown() {
        standaloneClient.close();
        clusterClient.close();
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void unlink_multiple_keys(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String key3 = "{key}" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        String setResult = client.set(key1, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key2, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key3, value).get();
        assertEquals(OK, setResult);

        Long unlinkedKeysNum = client.unlink(new String[] {key1, key2, key3}).get();
        assertEquals(3L, unlinkedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void unlink_non_existent_key(BaseClient client) {
        Long unlinkedKeysNum = client.unlink(new String[] {UUID.randomUUID().toString()}).get();
        assertEquals(0L, unlinkedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_and_get_without_options(BaseClient client) {
        String ok = client.set(KEY_NAME, INITIAL_VALUE).get();
        assertEquals(OK, ok);

        String data = client.get(KEY_NAME).get();
        assertEquals(INITIAL_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void get_missing_value(BaseClient client) {
        String data = client.get("invalid").get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void del_multiple_keys(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String key3 = "{key}" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        String setResult = client.set(key1, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key2, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key3, value).get();
        assertEquals(OK, setResult);

        Long deletedKeysNum = client.del(new String[] {key1, key2, key3}).get();
        assertEquals(3L, deletedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void del_non_existent_key(BaseClient client) {
        Long deletedKeysNum = client.del(new String[] {UUID.randomUUID().toString()}).get();
        assertEquals(0L, deletedKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_overwrite_value_and_returnOldValue_returns_string(BaseClient client) {
        String ok = client.set(KEY_NAME, INITIAL_VALUE).get();
        assertEquals(OK, ok);

        SetOptions options = SetOptions.builder().returnOldValue(true).build();
        String data = client.set(KEY_NAME, ANOTHER_VALUE, options).get();
        assertEquals(INITIAL_VALUE, data);
    }

    @ParameterizedTest
    @MethodSource("getClients")
    public void set_requires_a_value(BaseClient client) {
        assertThrows(NullPointerException.class, () -> client.set("SET", null));
    }

    @ParameterizedTest
    @MethodSource("getClients")
    public void set_requires_a_key(BaseClient client) {
        assertThrows(NullPointerException.class, () -> client.set(null, INITIAL_VALUE));
    }

    @ParameterizedTest
    @MethodSource("getClients")
    public void get_requires_a_key(BaseClient client) {
        assertThrows(NullPointerException.class, () -> client.get(null));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_only_if_exists_overwrite(BaseClient client) {
        String key = "set_only_if_exists_overwrite";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build();
        client.set(key, INITIAL_VALUE).get();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_only_if_exists_missing_key(BaseClient client) {
        String key = "set_only_if_exists_missing_key";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_EXISTS).build();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_only_if_does_not_exists_missing_key(BaseClient client) {
        String key = "set_only_if_does_not_exists_missing_key";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_only_if_does_not_exists_existing_key(BaseClient client) {
        String key = "set_only_if_does_not_exists_existing_key";
        SetOptions options = SetOptions.builder().conditionalSet(ONLY_IF_DOES_NOT_EXIST).build();
        client.set(key, INITIAL_VALUE).get();
        client.set(key, ANOTHER_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(INITIAL_VALUE, data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_value_with_ttl_and_update_value_with_keeping_ttl(BaseClient client) {
        String key = "set_value_with_ttl_and_update_value_with_keeping_ttl";
        SetOptions options = SetOptions.builder().expiry(Milliseconds(2000L)).build();
        client.set(key, INITIAL_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(SetOptions.Expiry.KeepExisting()).build();
        client.set(key, ANOTHER_VALUE, options).get();
        data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than TTL

        data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_value_with_ttl_and_update_value_with_new_ttl(BaseClient client) {
        String key = "set_value_with_ttl_and_update_value_with_new_ttl";
        SetOptions options = SetOptions.builder().expiry(Milliseconds(100500L)).build();
        client.set(key, INITIAL_VALUE, options).get();
        String data = client.get(key).get();
        assertEquals(INITIAL_VALUE, data);

        options = SetOptions.builder().expiry(Milliseconds(2000L)).build();
        client.set(key, ANOTHER_VALUE, options).get();
        data = client.get(key).get();
        assertEquals(ANOTHER_VALUE, data);

        Thread.sleep(2222); // sleep a bit more than new TTL

        data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_expired_value(BaseClient client) {
        String key = "set_expired_value";
        SetOptions options =
                SetOptions.builder()
                        // expiration is in the past
                        .expiry(SetOptions.Expiry.UnixSeconds(100500L))
                        .build();
        client.set(key, INITIAL_VALUE, options).get();
        String data = client.get(key).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void set_missing_value_and_returnOldValue_is_null(BaseClient client) {
        String ok = client.set(KEY_NAME, INITIAL_VALUE).get();
        assertEquals(OK, ok);

        SetOptions options = SetOptions.builder().returnOldValue(true).build();
        String data = client.set(UUID.randomUUID().toString(), ANOTHER_VALUE, options).get();
        assertNull(data);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void mset_mget_existing_non_existing_key(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();
        String nonExisting = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> keyValueMap = Map.of(key1, value, key2, value, key3, value);

        assertEquals(OK, client.mset(keyValueMap).get());
        assertArrayEquals(
                new String[] {value, value, null, value},
                client.mget(new String[] {key1, key2, nonExisting, key3}).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void incr_commands_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "10").get());

        assertEquals(11, client.incr(key).get());
        assertEquals("11", client.get(key).get());

        assertEquals(15, client.incrBy(key, 4).get());
        assertEquals("15", client.get(key).get());

        assertEquals(20.5, client.incrByFloat(key, 5.5).get());
        assertEquals("20.5", client.get(key).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void incr_commands_non_existing_key(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        assertNull(client.get(key1).get());
        assertEquals(1, client.incr(key1).get());
        assertEquals("1", client.get(key1).get());

        assertNull(client.get(key2).get());
        assertEquals(3, client.incrBy(key2, 3).get());
        assertEquals("3", client.get(key2).get());

        assertNull(client.get(key3).get());
        assertEquals(0.5, client.incrByFloat(key3, 0.5).get());
        assertEquals("0.5", client.get(key3).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void test_incr_commands_type_error(BaseClient client) {
        String key1 = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key1, "foo").get());

        Exception incrException = assertThrows(ExecutionException.class, () -> client.incr(key1).get());
        assertTrue(incrException.getCause() instanceof RequestException);

        Exception incrByException =
                assertThrows(ExecutionException.class, () -> client.incrBy(key1, 3).get());
        assertTrue(incrByException.getCause() instanceof RequestException);

        Exception incrByFloatException =
                assertThrows(ExecutionException.class, () -> client.incrByFloat(key1, 3.5).get());
        assertTrue(incrByFloatException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void decr_and_decrBy_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "10").get());

        assertEquals(9, client.decr(key).get());
        assertEquals("9", client.get(key).get());

        assertEquals(5, client.decrBy(key, 4).get());
        assertEquals("5", client.get(key).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void decr_and_decrBy_non_existing_key(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        assertNull(client.get(key1).get());
        assertEquals(-1, client.decr(key1).get());
        assertEquals("-1", client.get(key1).get());

        assertNull(client.get(key2).get(10, SECONDS));
        assertEquals(-3, client.decrBy(key2, 3).get());
        assertEquals("-3", client.get(key2).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hset_hget_existing_fields_non_existing_fields(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertEquals(value, client.hget(key, field1).get());
        assertEquals(value, client.hget(key, field2).get());
        assertNull(client.hget(key, "non_existing_field").get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hdel_multiple_existing_fields_non_existing_field_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String field3 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value, field3, value);

        assertEquals(3, client.hset(key, fieldValueMap).get());
        assertEquals(2, client.hdel(key, new String[] {field1, field2}).get());
        assertEquals(0, client.hdel(key, new String[] {"non_existing_field"}).get());
        assertEquals(0, client.hdel("non_existing_key", new String[] {field3}).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hmget_multiple_existing_fields_non_existing_field_non_existing_key(
            BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertArrayEquals(
                new String[] {value, null, value},
                client.hmget(key, new String[] {field1, "non_existing_field", field2}).get());
        assertArrayEquals(
                new String[] {null, null},
                client.hmget("non_existing_key", new String[] {field1, field2}).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hexists_existing_field_non_existing_field_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, "value1", field2, "value1");

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertTrue(client.hexists(key, field1).get());
        assertFalse(client.hexists(key, "non_existing_field").get());
        assertFalse(client.hexists("non_existing_key", field2).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hgetall_multiple_existing_fields_existing_key_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field1 = UUID.randomUUID().toString();
        String field2 = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field1, value, field2, value);

        assertEquals(2, client.hset(key, fieldValueMap).get());
        assertEquals(fieldValueMap, client.hgetall(key).get());
        assertEquals(Map.of(), client.hgetall("non_existing_key").get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hincrBy_hincrByFloat_commands_existing_key_existing_field(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field, "10");

        assertEquals(1, client.hset(key, fieldValueMap).get());

        assertEquals(11, client.hincrBy(key, field, 1).get());
        assertEquals(15, client.hincrBy(key, field, 4).get());
        assertEquals(16.5, client.hincrByFloat(key, field, 1.5).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hincrBy_hincrByFloat_commands_non_existing_key_non_existing_field(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field, "10");

        assertEquals(1, client.hincrBy("non_existing_key_1", field, 1).get());
        assertEquals(1, client.hset(key1, fieldValueMap).get());
        assertEquals(2, client.hincrBy(key1, "non_existing_field_1", 2).get());

        assertEquals(0.5, client.hincrByFloat("non_existing_key_2", field, 0.5).get());
        assertEquals(1, client.hset(key2, fieldValueMap).get());
        assertEquals(-0.5, client.hincrByFloat(key1, "non_existing_field_2", -0.5).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void hincrBy_hincrByFloat_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String field = UUID.randomUUID().toString();
        Map<String, String> fieldValueMap = Map.of(field, "foo");

        assertEquals(1, client.hset(key, fieldValueMap).get());

        Exception hincrByException =
                assertThrows(ExecutionException.class, () -> client.hincrBy(key, field, 2).get());
        assertTrue(hincrByException.getCause() instanceof RequestException);

        Exception hincrByFloatException =
                assertThrows(ExecutionException.class, () -> client.hincrByFloat(key, field, 2.5).get());
        assertTrue(hincrByFloatException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void lpush_lpop_lrange_existing_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value4", "value3", "value2", "value1"};

        assertEquals(4, client.lpush(key, valueArray).get());
        assertEquals("value1", client.lpop(key).get());
        assertArrayEquals(new String[] {"value2", "value3", "value4"}, client.lrange(key, 0, -1).get());
        assertArrayEquals(new String[] {"value2", "value3"}, client.lpopCount(key, 2).get());
        assertArrayEquals(new String[] {}, client.lrange("non_existing_key", 0, -1).get());
        assertNull(client.lpop("non_existing_key").get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void lpush_lpop_lrange_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "foo").get());

        Exception lpushException =
                assertThrows(ExecutionException.class, () -> client.lpush(key, new String[] {"foo"}).get());
        assertTrue(lpushException.getCause() instanceof RequestException);

        Exception lpopException = assertThrows(ExecutionException.class, () -> client.lpop(key).get());
        assertTrue(lpopException.getCause() instanceof RequestException);

        Exception lpopCountException =
                assertThrows(ExecutionException.class, () -> client.lpopCount(key, 2).get());
        assertTrue(lpopCountException.getCause() instanceof RequestException);

        Exception lrangeException =
                assertThrows(ExecutionException.class, () -> client.lrange(key, 0, -1).get());
        assertTrue(lrangeException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void ltrim_existing_non_existing_key_and_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value4", "value3", "value2", "value1"};

        assertEquals(4, client.lpush(key, valueArray).get());
        assertEquals(OK, client.ltrim(key, 0, 1).get());
        assertArrayEquals(new String[] {"value1", "value2"}, client.lrange(key, 0, -1).get());

        // `start` is greater than `end` so the key will be removed.
        assertEquals(OK, client.ltrim(key, 4, 2).get());
        assertArrayEquals(new String[] {}, client.lrange(key, 0, -1).get());

        assertEquals(OK, client.set(key, "foo").get());

        Exception ltrimException =
                assertThrows(ExecutionException.class, () -> client.ltrim(key, 0, 1).get());
        assertTrue(ltrimException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void llen_existing_non_existing_key_and_type_error(BaseClient client) {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value4", "value3", "value2", "value1"};

        assertEquals(4, client.lpush(key1, valueArray).get());
        assertEquals(4, client.llen(key1).get());
        assertEquals(0, client.llen("non_existing_key").get());

        assertEquals(OK, client.set(key2, "foo").get());

        Exception lrangeException =
                assertThrows(ExecutionException.class, () -> client.llen(key2).get());
        assertTrue(lrangeException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void rpush_rpop_existing_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();
        String[] valueArray = new String[] {"value1", "value2", "value3", "value4"};

        assertEquals(4, client.rpush(key, valueArray).get());
        assertEquals("value4", client.rpop(key).get());

        assertArrayEquals(new String[] {"value3", "value2"}, client.rpopCount(key, 2).get());
        assertNull(client.rpop("non_existing_key").get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void rpush_rpop_type_error(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "foo").get());

        Exception rpushException =
                assertThrows(ExecutionException.class, () -> client.rpush(key, new String[] {"foo"}).get());
        assertTrue(rpushException.getCause() instanceof RequestException);

        Exception rpopException = assertThrows(ExecutionException.class, () -> client.rpop(key).get());
        assertTrue(rpopException.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void sadd_srem_scard_smembers_existing_set(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(
                4, client.sadd(key, new String[] {"member1", "member2", "member3", "member4"}).get());
        assertEquals(1, client.srem(key, new String[] {"member3", "nonExistingMember"}).get());

        Set<String> expectedMembers = Set.of("member1", "member2", "member4");
        assertEquals(expectedMembers, client.smembers(key).get());
        assertEquals(1, client.srem(key, new String[] {"member1"}).get());
        assertEquals(2, client.scard(key).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void srem_scard_smembers_non_existing_key(BaseClient client) {
        assertEquals(0, client.srem("nonExistingKey", new String[] {"member"}).get());
        assertEquals(0, client.scard("nonExistingKey").get());
        assertEquals(Set.of(), client.smembers("nonExistingKey").get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void sadd_srem_scard_smembers_key_with_non_set_value(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "foo").get());

        Exception e =
                assertThrows(ExecutionException.class, () -> client.sadd(key, new String[] {"baz"}).get());
        assertTrue(e.getCause() instanceof RequestException);

        e = assertThrows(ExecutionException.class, () -> client.srem(key, new String[] {"baz"}).get());
        assertTrue(e.getCause() instanceof RequestException);

        e = assertThrows(ExecutionException.class, () -> client.scard(key).get());
        assertTrue(e.getCause() instanceof RequestException);

        e = assertThrows(ExecutionException.class, () -> client.smembers(key).get());
        assertTrue(e.getCause() instanceof RequestException);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void exists_multiple_keys(BaseClient client) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();

        String setResult = client.set(key1, value).get();
        assertEquals(OK, setResult);
        setResult = client.set(key2, value).get();
        assertEquals(OK, setResult);

        Long existsKeysNum =
                client.exists(new String[] {key1, key2, key1, UUID.randomUUID().toString()}).get();
        assertEquals(3L, existsKeysNum);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void expire_pexpire_and_ttl_with_positive_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "expire_timeout").get());
        assertTrue(client.expire(key, 10L).get());
        assertTrue(client.ttl(key).get() <= 10L);

        // set command clears the timeout.
        assertEquals(OK, client.set(key, "pexpire_timeout").get());
        if (REDIS_VERSION.feature() < 7) {
            assertTrue(client.pexpire(key, 10000L).get());
        } else {
            assertTrue(client.pexpire(key, 10000L, ExpireOptions.HAS_NO_EXPIRY).get());
        }
        assertTrue(client.ttl(key).get() <= 10L);

        // TTL will be updated to the new value = 15
        if (REDIS_VERSION.feature() < 7) {
            assertTrue(client.expire(key, 15L).get());
        } else {
            assertTrue(client.expire(key, 15L, ExpireOptions.HAS_EXISTING_EXPIRY).get());
        }
        assertTrue(client.ttl(key).get() <= 15L);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void expireAt_pexpireAt_and_ttl_with_positive_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();
        assertEquals(OK, client.set(key, "expireAt_timeout").get());
        assertTrue(client.expireAt(key, Instant.now().getEpochSecond() + 10L).get());
        assertTrue(client.ttl(key).get() <= 10L);

        // extend TTL
        if (REDIS_VERSION.feature() < 7) {
            assertTrue(client.expireAt(key, Instant.now().getEpochSecond() + 50L).get());
        } else {
            assertTrue(
                    client
                            .expireAt(
                                    key,
                                    Instant.now().getEpochSecond() + 50L,
                                    ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
                            .get());
        }
        assertTrue(client.ttl(key).get() <= 50L);

        if (REDIS_VERSION.feature() < 7) {
            assertTrue(client.pexpireAt(key, Instant.now().toEpochMilli() + 50000L).get());
        } else {
            // set command clears the timeout.
            assertEquals(OK, client.set(key, "pexpireAt_timeout").get());
            assertFalse(
                    client
                            .pexpireAt(
                                    key, Instant.now().toEpochMilli() + 50000L, ExpireOptions.HAS_EXISTING_EXPIRY)
                            .get());
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void expire_pexpire_with_timestamp_in_the_past_or_negative_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "expire_with_past_timestamp").get());
        assertEquals(-1L, client.ttl(key).get());
        assertTrue(client.expire(key, -10L).get());
        assertEquals(-2L, client.ttl(key).get());

        assertEquals(OK, client.set(key, "pexpire_with_past_timestamp").get());
        assertTrue(client.pexpire(key, -10000L).get());
        assertEquals(-2L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void expireAt_pexpireAt_with_timestamp_in_the_past_or_negative_timeout(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertEquals(OK, client.set(key, "expireAt_with_past_timestamp").get());
        // set timeout in the past
        assertTrue(client.expireAt(key, Instant.now().getEpochSecond() - 50L).get());
        assertEquals(-2L, client.ttl(key).get());

        assertEquals(OK, client.set(key, "pexpireAt_with_past_timestamp").get());
        // set timeout in the past
        assertTrue(client.pexpireAt(key, Instant.now().toEpochMilli() - 50000L).get());
        assertEquals(-2L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void expire_pexpire_and_ttl_with_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertFalse(client.expire(key, 10L).get());
        assertFalse(client.pexpire(key, 10000L).get());

        assertEquals(-2L, client.ttl(key).get());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getClients")
    public void expireAt_pexpireAt_and_ttl_with_non_existing_key(BaseClient client) {
        String key = UUID.randomUUID().toString();

        assertFalse(client.expireAt(key, Instant.now().getEpochSecond() + 10L).get());
        assertFalse(client.pexpireAt(key, Instant.now().toEpochMilli() + 10000L).get());

        assertEquals(-2L, client.ttl(key).get());
    }
}
