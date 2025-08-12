/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.concatenateArrays;
import static glide.TestUtilities.generateLuaLibCode;
import static glide.api.BaseClient.OK;
import static glide.api.models.commands.FlushMode.ASYNC;
import static glide.api.models.commands.FlushMode.SYNC;
import static glide.api.models.commands.LInsertOptions.InsertPosition.AFTER;
import static glide.api.models.commands.ScoreFilter.MAX;
import static glide.api.models.commands.ScoreFilter.MIN;

import glide.api.models.BaseBatch;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.HashFieldExpirationOptions;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.RangeOptions.InfLexBound;
import glide.api.models.commands.RangeOptions.InfScoreBound;
import glide.api.models.commands.RangeOptions.LexBoundary;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.ScoreBoundary;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.SortOrder;
import glide.api.models.commands.WeightAggregateOptions.Aggregate;
import glide.api.models.commands.WeightAggregateOptions.KeyArray;
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
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamRange.IdBound;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions.MinId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class BatchTestUtilities {

    private static final int KEY_SUFFIX_LENGTH = 6;
    private static final String value1 = "value1-" + generateRandomNumericSuffix();
    private static final String value2 = "value2-" + generateRandomNumericSuffix();
    private static final String value3 = "value3-" + generateRandomNumericSuffix();
    private static final String field1 = "field1-" + generateRandomNumericSuffix();
    private static final String field2 = "field2-" + generateRandomNumericSuffix();
    private static final String field3 = "field3-" + generateRandomNumericSuffix();

    /**
     * Generate a key with the same hash slot if keySlot is provided and isAtomic is true; otherwise,
     * generate a random key.
     *
     * @param keySlot optional hash tag to force the same slot; if null or empty, a random key is
     *     generated
     * @param isAtomic if true, keySlot is used to force the hash slot
     * @return the generated key
     */
    public static String generateKey(String keySlot, boolean isAtomic) {
        if (isAtomic && keySlot != null && !keySlot.isEmpty()) {
            // Use keySlot to force the same hash slot with a random suffix.
            return "{" + keySlot + "}-" + generateRandomNumericSuffix();
        }
        // Generate a random key
        return generateRandomNumericSuffix();
    }

    private static String generateRandomNumericSuffix() {
        final int lowerBound = (int) Math.pow(10, KEY_SUFFIX_LENGTH - 1);
        final int upperBound = (int) Math.pow(10, KEY_SUFFIX_LENGTH);
        final int intSuffix = ThreadLocalRandom.current().nextInt(lowerBound, upperBound);
        return String.valueOf(intSuffix);
    }

    /**
     * Generate a key with the same hash slot as the provided keySlot. If keySlot already contains a
     * hash tag (i.e. starts with "{" and contains "}"), only the tag between the braces is used.
     *
     * @param keySlot the key or tag to be used for the hash slot; if it contains a tag, the tag is
     *     extracted
     * @return the generated key with the same hash slot
     */
    public static String generateKeySameSlot(String keySlot) {
        String tag;
        if (keySlot.startsWith("{") && keySlot.indexOf("}") > 0) {
            // Extract the tag between the first '{' and '}'
            tag = keySlot.substring(1, keySlot.indexOf("}"));
        } else {
            tag = keySlot;
        }
        return "{" + tag + "}-" + generateRandomNumericSuffix();
    }

    @FunctionalInterface
    public interface BatchBuilder extends BiFunction<BaseBatch<?>, Boolean, Object[]> {}

    /** Generate test samples for parametrized tests. Could be routed to random node. */
    public static Stream<Arguments> getCommonBatchBuilders() {
        return Stream.of(
                Arguments.of("Generic Commands", (BatchBuilder) BatchTestUtilities::genericCommands),
                Arguments.of("String Commands", (BatchBuilder) BatchTestUtilities::stringCommands),
                Arguments.of("Hash Commands", (BatchBuilder) BatchTestUtilities::hashCommands),
                Arguments.of("List Commands", (BatchBuilder) BatchTestUtilities::listCommands),
                Arguments.of("Set Commands", (BatchBuilder) BatchTestUtilities::setCommands),
                Arguments.of("Sorted Set Commands", (BatchBuilder) BatchTestUtilities::sortedSetCommands),
                Arguments.of(
                        "HyperLogLog Commands", (BatchBuilder) BatchTestUtilities::hyperLogLogCommands),
                Arguments.of("Stream Commands", (BatchBuilder) BatchTestUtilities::streamCommands),
                Arguments.of(
                        "Connection Management Commands",
                        (BatchBuilder) BatchTestUtilities::connectionManagementCommands),
                Arguments.of("Geospatial Commands", (BatchBuilder) BatchTestUtilities::geospatialCommands),
                Arguments.of("Bitmap Commands", (BatchBuilder) BatchTestUtilities::bitmapCommands),
                Arguments.of("PubSub Commands", (BatchBuilder) BatchTestUtilities::pubsubCommands));
    }

    /** Generate test samples for parametrized tests. Could be routed to primary nodes only. */
    public static Stream<Arguments> getPrimaryNodeBatchBuilders() {
        return Stream.of(
                Arguments.of(
                        "Server Management Commands",
                        (BatchBuilder) BatchTestUtilities::serverManagementCommands),
                Arguments.of(
                        "Scripting and Function Commands",
                        (BatchBuilder) BatchTestUtilities::scriptingAndFunctionsCommands));
    }

    private static Object[] genericCommands(BaseBatch<?> batch, boolean isAtomic) {
        String genericKey1 = generateKey("GenericKey", isAtomic);
        String genericKey2 = generateKeySameSlot(genericKey1);
        String genericKey3 = generateKey("GenericKey", isAtomic);
        String genericKey4 = generateKeySameSlot(genericKey3);
        String[] ascendingList = new String[] {"1", "2", "3"};
        String[] descendingList = new String[] {"3", "2", "1"};

        batch
                .set(genericKey1, value1)
                .customCommand(new String[] {"MGET", genericKey1, genericKey2})
                .exists(new String[] {genericKey1})
                .persist(genericKey1)
                .type(genericKey1)
                .objectEncoding(genericKey1)
                .touch(new String[] {genericKey1})
                .set(genericKey2, value2)
                .rename(genericKey1, genericKey1)
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
                .ttl(genericKey2)
                .lpush(genericKey3, new String[] {"3", "1", "2"})
                .sort(genericKey3)
                .sortStore(genericKey3, genericKey4)
                .lrange(genericKey4, 0, -1);

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .set(genericKey1, value1)
                    .expire(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                    .expireAt(genericKey1, 500, ExpireOptions.HAS_EXISTING_EXPIRY)
                    .pexpire(genericKey1, 42, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
                    .pexpireAt(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                    .expiretime(genericKey1)
                    .pexpiretime(genericKey1)
                    .sortReadOnly(genericKey3);
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            batch
                    .set(genericKey3, "value")
                    .set(genericKey4, "value2")
                    .copy(genericKey3, genericKey4, false)
                    .copy(genericKey3, genericKey4, true);
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
                    OK, // rename(genericKey1, genericKey1)
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
                    3L, // lpush(genericKey3, new String[] {"3", "1", "2"})
                    ascendingList, // sort(genericKey3)
                    3L, // sortStore(genericKey3, genericKey4)
                    ascendingList, // lrange(genericKey4, 0, -1)
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // set(genericKey1, value1)
                                true, // expire(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                                true, // expireAt(genericKey1, 500, ExpireOptions.HAS_EXISTING_EXPIRY)
                                false, // pexpire(genericKey1, 42, ExpireOptions.NEW_EXPIRY_GREATER_THAN_CURRENT)
                                false, // pexpireAt(genericKey1, 42, ExpireOptions.HAS_NO_EXPIRY)
                                -2L, // expiretime(genericKey1)
                                -2L, // pexpiretime(genericKey1)
                                ascendingList, // sortReadOnly(genericKey3)
                            });
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // set(genericKey3, "value1")
                                OK, // set(genericKey4, "value2")
                                false, // copy(genericKey3, genericKey4, false)
                                true, // copy(genericKey3, genericKey4, true)
                            });
        }
        return expectedResults;
    }

    private static Object[] stringCommands(BaseBatch<?> batch, boolean isAtomic) {
        String stringKey1 = generateKey("StringKey", isAtomic);
        String stringKey2 = generateKey("StringKey", isAtomic);
        String stringKey3 = generateKey("StringKey", isAtomic);
        String stringKey4 = generateKey("StringKey", isAtomic);
        String stringKey5 = generateKeySameSlot(stringKey4);
        String stringKey6 = generateKey("StringKey", isAtomic);
        String stringKey7 = generateKeySameSlot(stringKey6);
        String stringKey8 = generateKeySameSlot(stringKey6);
        String stringKey9 = generateKey("StringKey", isAtomic);

        Map<String, Object> expectedLcsIdxObject =
                Map.of("matches", new Long[][][] {{{1L, 3L}, {0L, 2L}}}, "len", 3L);

        Map<String, Object> expectedLcsIdxWithMatchLenObject =
                Map.of(
                        "matches",
                        new Object[] {new Object[] {new Long[] {1L, 3L}, new Long[] {0L, 2L}, 3L}},
                        "len",
                        3L);

        batch
                .flushall()
                .set(stringKey1, value1)
                .randomKey()
                .get(stringKey1)
                .getdel(stringKey1)
                .set(stringKey2, value2, SetOptions.builder().returnOldValue(true).build())
                .strlen(stringKey2)
                .append(stringKey2, value2)
                .mset(Map.of(stringKey1, value2, stringKey2, value1))
                .mget(new String[] {stringKey1, stringKey2})
                .incr(stringKey3)
                .incrBy(stringKey3, 2)
                .decr(stringKey3)
                .decrBy(stringKey3, 2)
                .incrByFloat(stringKey3, 0.5)
                .setrange(stringKey3, 0, "GLIDE")
                .getrange(stringKey3, 0, 5)
                .msetnx(Map.of(stringKey4, "foo", stringKey5, "bar"))
                .mget(new String[] {stringKey4, stringKey5})
                .del(new String[] {stringKey5})
                .msetnx(Map.of(stringKey4, "foo", stringKey5, "bar"))
                .mget(new String[] {stringKey4, stringKey5});

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .set(stringKey6, "abcd")
                    .set(stringKey7, "bcde")
                    .set(stringKey8, "wxyz")
                    .lcs(stringKey6, stringKey7)
                    .lcs(stringKey6, stringKey8)
                    .lcsLen(stringKey6, stringKey7)
                    .lcsLen(stringKey6, stringKey8)
                    .lcsIdx(stringKey6, stringKey7)
                    .lcsIdx(stringKey6, stringKey7, 1)
                    .lcsIdxWithMatchLen(stringKey6, stringKey7)
                    .lcsIdxWithMatchLen(stringKey6, stringKey7, 1);
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            batch.set(stringKey9, value1).getex(stringKey9).getex(stringKey9, GetExOptions.Seconds(20L));
        }

        var expectedResults =
                new Object[] {
                    OK, // flushall()
                    OK, // set(stringKey1, value1)
                    stringKey1, // randomKey()
                    value1, // get(stringKey1)
                    value1, // getdel(stringKey1)
                    null, // set(stringKey2, value2, returnOldValue(true))
                    (long) value1.length(), // strlen(key2)
                    Long.valueOf(value2.length() * 2), // append(key2, value2)
                    OK, // mset(Map.of(stringKey1, value2, stringKey2, value1))
                    new String[] {value2, value1}, // mget(new String[] {stringKey1, stringKey2})
                    1L, // incr(stringKey3)
                    3L, // incrBy(stringKey3, 2)
                    2L, // decr(stringKey3)
                    0L, // decrBy(stringKey3, 2)
                    0.5, // incrByFloat(stringKey3, 0.5)
                    5L, // setrange(stringKey3, 0, "GLIDE")
                    "GLIDE", // getrange(stringKey3, 0, 5)
                    true, // msetnx(Map.of(stringKey4, "foo", stringKey5, "bar"))
                    new String[] {"foo", "bar"}, // mget({stringKey4, stringKey5})
                    1L, // del(stringKey5)
                    false, // msetnx(Map.of(stringKey4, "foo", stringKey5, "bar"))
                    new String[] {"foo", null}, // mget({stringKey4, stringKey5})
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // set(stringKey6, "abcd")
                                OK, // set(stringKey6, "bcde")
                                OK, // set(stringKey6, "wxyz")
                                "bcd", // lcs(stringKey6, stringKey7)
                                "", // lcs(stringKey6, stringKey8)
                                3L, // lcsLEN(stringKey6, stringKey7)
                                0L, // lcsLEN(stringKey6, stringKey8)
                                expectedLcsIdxObject, // lcsIdx(stringKey6, stringKey7)
                                expectedLcsIdxObject, // lcsIdx(stringKey6, stringKey7, minMatchLen(1L)
                                expectedLcsIdxWithMatchLenObject, // lcsIdxWithMatchLen(stringKey6, stringKey7)
                                expectedLcsIdxWithMatchLenObject, // lcsIdxWithMatchLen(key6, key7, minMatchLen(1L))
                            });
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // set(stringKey9, value1)
                                value1, // getex(stringKey1)
                                value1, // getex(stringKey1,GetExOptions.Seconds(20L))
                            });
        }

        return expectedResults;
    }

    private static Object[] hashCommands(BaseBatch<?> batch, boolean isAtomic) {
        String hashKey1 = generateKey("HashKey", isAtomic);

        // This extra key is for HScan testing. It is a key with only one field. HScan doesn't guarantee
        // a return order but this test compares arrays so order is significant.
        String hashKey2 = generateKey("HashKey", isAtomic);

        batch
                .hset(hashKey1, Map.of(field1, value1, field2, value2))
                .hget(hashKey1, field1)
                .hlen(hashKey1)
                .hexists(hashKey1, field2)
                .hsetnx(hashKey1, field1, value1)
                .hmget(hashKey1, new String[] {field1, "non_existing_field", field2})
                .hgetall(hashKey1)
                .hdel(hashKey1, new String[] {field1})
                .hvals(hashKey1)
                .hrandfield(hashKey1)
                .hrandfieldWithCount(hashKey1, 2)
                .hrandfieldWithCount(hashKey1, -2)
                .hrandfieldWithCountWithValues(hashKey1, 2)
                .hrandfieldWithCountWithValues(hashKey1, -2)
                .hincrBy(hashKey1, field3, 5)
                .hincrByFloat(hashKey1, field3, 5.5)
                .hkeys(hashKey1)
                .hstrlen(hashKey1, field2)
                .hset(hashKey2, Map.of(field1, value1))
                .hscan(hashKey2, "0")
                .hscan(hashKey2, "0", HScanOptions.builder().count(20L).build());

        // Hash field expiration commands (Valkey 9.0.0+)
        if (SERVER_VERSION.isGreaterThanOrEqualTo("9.0.0")) {
            String hashKey3 = generateKey("HashKey", isAtomic);
            HashFieldExpirationOptions expiryOptions =
                    HashFieldExpirationOptions.builder()
                            .expiry(HashFieldExpirationOptions.ExpirySet.Seconds(60L))
                            .build();

            batch
                    .hsetex(hashKey3, Map.of(field1, value1, field2, value2), expiryOptions)
                    .hgetex(hashKey3, new String[] {field1, field2}, expiryOptions)
                    .hexpire(
                            hashKey3,
                            60L,
                            new String[] {field1, field2},
                            HashFieldExpirationOptions.builder().build())
                    .hpersist(hashKey3, new String[] {field1, field2})
                    .hpexpire(
                            hashKey3,
                            60000L,
                            new String[] {field1, field2},
                            HashFieldExpirationOptions.builder().build())
                    .hexpireat(
                            hashKey3,
                            System.currentTimeMillis() / 1000 + 60,
                            new String[] {field1, field2},
                            HashFieldExpirationOptions.builder().build())
                    .hpexpireat(
                            hashKey3,
                            System.currentTimeMillis() + 60000,
                            new String[] {field1, field2},
                            HashFieldExpirationOptions.builder().build())
                    .httl(hashKey3, new String[] {field1, field2})
                    .hpttl(hashKey3, new String[] {field1, field2})
                    .hexpiretime(hashKey3, new String[] {field1, field2})
                    .hpexpiretime(hashKey3, new String[] {field1, field2});
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            batch
                    .hscan(hashKey2, "0", HScanOptions.builder().count(20L).noValues(false).build())
                    .hscan(hashKey2, "0", HScanOptions.builder().count(20L).noValues(true).build());
        }

        var result =
                new Object[] {
                    2L, // hset(hashKey1, Map.of(field1, value1, field2, value2))
                    value1, // hget(hashKey1, field1)
                    2L, // hlen(hashKey1)
                    true, // hexists(hashKey1, field2)
                    false, // hsetnx(hashKey1, field1, value1)
                    new String[] {value1, null, value2}, // hmget(hashKey1, new String[] {...})
                    Map.of(field1, value1, field2, value2), // hgetall(hashKey1)
                    1L, // hdel(hashKey1, new String[] {field1})
                    new String[] {value2}, // hvals(hashKey1)
                    field2, // hrandfield(hashKey1)
                    new String[] {field2}, // hrandfieldWithCount(hashKey1, 2)
                    new String[] {field2, field2}, // hrandfieldWithCount(hashKey1, -2)
                    new String[][] {{field2, value2}}, // hrandfieldWithCountWithValues(hashKey1, 2)
                    new String[][] {
                        {field2, value2}, {field2, value2}
                    }, // hrandfieldWithCountWithValues(hashKey1, -2)
                    5L, // hincrBy(hashKey1, field3, 5)
                    10.5, // hincrByFloat(hashKey1, field3, 5.5)
                    new String[] {field2, field3}, // hkeys(hashKey1)
                    (long) value2.length(), // hstrlen(hashKey1, field2)
                    1L, // hset(hashKey2, Map.of(field1, value1))
                    new Object[] {"0", new Object[] {field1, value1}}, // hscan(hashKey2, "0")
                    new Object[] {
                        "0", new Object[] {field1, value1}
                    }, // hscan(hashKey2, "0", HScanOptions.builder().count(20L).build());
                };

        // Add expected results for hash field expiration commands (Valkey 9.0.0+)
        if (SERVER_VERSION.isGreaterThanOrEqualTo("9.0.0")) {
            result =
                    concatenateArrays(
                            result,
                            new Object[] {
                                2L, // hsetex(hashKey3, Map.of(field1, value1, field2, value2), expiryOptions)
                                new String[] {
                                    value1, value2
                                }, // hgetex(hashKey3, new String[] {field1, field2}, expiryOptions)
                                new Long[] {
                                    1L, 1L
                                }, // hexpire(hashKey3, 60L, new String[] {field1, field2}, options)
                                new Long[] {1L, 1L}, // hpersist(hashKey3, new String[] {field1, field2})
                                new Long[] {
                                    1L, 1L
                                }, // hpexpire(hashKey3, 60000L, new String[] {field1, field2}, options)
                                new Long[] {
                                    1L, 1L
                                }, // hexpireat(hashKey3, timestamp, new String[] {field1, field2}, options)
                                new Long[] {
                                    1L, 1L
                                }, // hpexpireat(hashKey3, timestamp, new String[] {field1, field2}, options)
                                new Long[] {
                                    -1L, -1L
                                }, // httl(hashKey3, new String[] {field1, field2}) - persistent after hpersist
                                new Long[] {
                                    -1L, -1L
                                }, // hpttl(hashKey3, new String[] {field1, field2}) - persistent after hpersist
                                new Long[] {
                                    -1L, -1L
                                }, // hexpiretime(hashKey3, new String[] {field1, field2}) - persistent after
                                // hpersist
                                new Long[] {
                                    -1L, -1L
                                }, // hpexpiretime(hashKey3, new String[] {field1, field2}) - persistent after
                                // hpersist
                            });
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            result =
                    concatenateArrays(
                            result,
                            new Object[] {
                                new Object[] {"0", new Object[] {field1, value1}}, // hscan(hashKey2, "0",
                                // HScanOptions.builder().count(20L).noValues(false).build());
                                new Object[] {"0", new Object[] {field1}}, // hscan(hashKey2, "0",
                                // HScanOptions.builder().count(20L).noValues(true).build());
                            });
        }

        return result;
    }

    private static Object[] listCommands(BaseBatch<?> batch, boolean isAtomic) {
        String listKey1 = generateKey("ListKey", isAtomic);
        String listKey2 = generateKey("ListKey", isAtomic);
        String listKey3 = generateKey("ListKey", isAtomic);
        String listKey4 = generateKey("ListKey", isAtomic);
        String listKey5 = generateKey("ListKey", isAtomic);
        String listKey6 = generateKey("ListKey", isAtomic);
        String listKey7 = generateKeySameSlot(listKey6);

        batch
                .lpush(listKey1, new String[] {value1, value1, value2, value3, value3})
                .llen(listKey1)
                .lindex(listKey1, 0)
                .lrem(listKey1, 1, value1)
                .ltrim(listKey1, 1, -1)
                .lrange(listKey1, 0, -2)
                .lpop(listKey1)
                .lpopCount(listKey1, 2) // listKey1 is now empty
                .rpush(listKey1, new String[] {value1, value1, value2, value3, value3})
                .lpos(listKey1, value1)
                .lpos(listKey1, value1, LPosOptions.builder().rank(2L).build())
                .lposCount(listKey1, value1, 1L)
                .lposCount(listKey1, value1, 0L, LPosOptions.builder().rank(1L).build())
                .rpush(listKey2, new String[] {value1, value2, value2})
                .rpop(listKey2)
                .rpopCount(listKey2, 2)
                .rpushx(listKey3, new String[] {"_"})
                .lpushx(listKey3, new String[] {"_"})
                .lpush(listKey3, new String[] {value1, value2, value3})
                .linsert(listKey3, AFTER, value2, value2)
                .blpop(new String[] {listKey3}, 0.01)
                .brpop(new String[] {listKey3}, 0.01)
                .lpush(listKey5, new String[] {value2, value3})
                .lset(listKey5, 0, value1)
                .lrange(listKey5, 0, -1);

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .lpush(listKey4, new String[] {value1, value2, value3, value1, value2, value3})
                    .lmpop(new String[] {listKey4}, ListDirection.LEFT)
                    .lmpop(new String[] {listKey4}, ListDirection.LEFT, 2L)
                    .blmpop(new String[] {listKey4}, ListDirection.LEFT, 0.1)
                    .blmpop(new String[] {listKey4}, ListDirection.LEFT, 2L, 0.1);
        } // listKey4 is now empty

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            batch
                    .lpush(listKey6, new String[] {value3, value2, value1})
                    .lpush(listKey7, new String[] {value1, value2, value3})
                    .lmove(listKey7, listKey7, ListDirection.LEFT, ListDirection.LEFT)
                    .lmove(listKey6, listKey7, ListDirection.LEFT, ListDirection.RIGHT)
                    .lrange(listKey6, 0, -1)
                    .lrange(listKey7, 0, -1)
                    .blmove(listKey7, listKey7, ListDirection.LEFT, ListDirection.LEFT, 0.1)
                    .blmove(listKey7, listKey6, ListDirection.RIGHT, ListDirection.LEFT, 0.1)
                    .lrange(listKey6, 0, -1)
                    .lrange(listKey7, 0, -1);
        }

        var expectedResults =
                new Object[] {
                    5L, // lpush(listKey1, new String[] {value1, value1, value2, value3, value3})
                    5L, // llen(listKey1)
                    value3, // lindex(key5, 0)
                    1L, // lrem(listKey1, 1, value1)
                    OK, // ltrim(listKey1, 1, -1)
                    new String[] {value3, value2}, // lrange(listKey1, 0, -2)
                    value3, // lpop(listKey1)
                    new String[] {value2, value1}, // lpopCount(listKey1, 2)
                    5L, // lpush(listKey1, new String[] {value1, value1, value2, value3, value3})
                    0L, // lpos(listKey1, value1)
                    1L, // lpos(listKey1, value1, LPosOptions.builder().rank(2L).build())
                    new Long[] {0L}, // lposCount(listKey1, value1, 1L)
                    new Long[] {0L, 1L}, // lposCount(listKey1, value1, 0L, LPosOptions.rank(2L))
                    3L, // rpush(listKey2, new String[] {value1, value2, value2})
                    value2, // rpop(listKey2)
                    new String[] {value2, value1}, // rpopCount(listKey2, 2)
                    0L, // rpushx(listKey3, new String[] { "_" })
                    0L, // lpushx(listKey3, new String[] { "_" })
                    3L, // lpush(listKey3, new String[] { value1, value2, value3})
                    4L, // linsert(listKey3, AFTER, value2, value2)
                    new String[] {listKey3, value3}, // blpop(new String[] { listKey3 }, 0.01)
                    new String[] {listKey3, value1}, // brpop(new String[] { listKey3 }, 0.01)
                    2L, // lpush(listKey5, new String[] {value2, value3})
                    OK, // lset(listKey5, 0, value1)
                    new String[] {value1, value2}, // lrange(listKey5, 0, -1)
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                6L, // lpush(listKey4, {value1, value2, value3})
                                Map.of(listKey4, new String[] {value3}), // lmpop({listKey4}, LEFT)
                                Map.of(listKey4, new String[] {value2, value1}), // lmpop({listKey4}, LEFT, 1L)
                                Map.of(listKey4, new String[] {value3}), // blmpop({listKey4}, LEFT, 0.1)
                                Map.of(listKey4, new String[] {value2, value1}), // blmpop(listKey4}, LEFT, 1L, 0.1)
                            });
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                3L, // lpush(listKey6, {value3, value2, value1})
                                3L, // lpush(listKey7, {value1, value2, value3})
                                value3, // lmove(listKey7, listKey5, LEFT, LEFT)
                                value1, // lmove(listKey6, listKey5, RIGHT, LEFT)
                                new String[] {value2, value3}, // lrange(listKey6, 0, -1)
                                new String[] {value3, value2, value1, value1}, // lrange(listKey7, 0, -1);
                                value3, // blmove(listKey7, listKey7, LEFT, LEFT, 0.1)
                                value1, // blmove(listKey7, listKey6, RIGHT, LEFT, 0.1)
                                new String[] {value1, value2, value3}, // lrange(listKey6, 0, -1)
                                new String[] {value3, value2, value1}, // lrange(listKey7, 0, -1)
                            });
        }

        return expectedResults;
    }

    private static Object[] setCommands(BaseBatch<?> batch, boolean isAtomic) {
        String setKey1 = generateKey("setKey", isAtomic);
        String setKey2 = generateKeySameSlot(setKey1);
        String setKey3 = generateKeySameSlot(setKey1);
        String setKey4 = generateKey("setKey", isAtomic);
        String setKey5 = generateKey("setKey", isAtomic);
        String setKey6 = generateKeySameSlot(setKey5);

        batch
                .sadd(setKey1, new String[] {"baz", "foo"})
                .srem(setKey1, new String[] {"foo"})
                .sscan(setKey1, "0")
                .sscan(setKey1, "0", SScanOptions.builder().matchPattern("*").count(10L).build())
                .scard(setKey1)
                .sismember(setKey1, "baz")
                .smembers(setKey1)
                .smismember(setKey1, new String[] {"baz", "foo"})
                .sinter(new String[] {setKey1, setKey1})
                .sadd(setKey2, new String[] {"a", "b"})
                .sunion(new String[] {setKey2, setKey1})
                .sunionstore(setKey3, new String[] {setKey2, setKey1})
                .sdiffstore(setKey3, new String[] {setKey2, setKey1})
                .sinterstore(setKey3, new String[] {setKey2, setKey1})
                .sdiff(new String[] {setKey2, setKey3})
                .smove(setKey1, setKey2, "baz")
                .sadd(setKey4, new String[] {"foo"})
                .srandmember(setKey4)
                .srandmember(setKey4, 2)
                .srandmember(setKey4, -2)
                .spop(setKey4)
                .spopCount(setKey4, 3); // setKey4 is now empty

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .sadd(setKey5, new String[] {"one", "two", "three", "four"})
                    .sadd(setKey6, new String[] {"two", "three", "four", "five"})
                    .sintercard(new String[] {setKey5, setKey6})
                    .sintercard(new String[] {setKey5, setKey6}, 2);
        }

        var expectedResults =
                new Object[] {
                    2L, // sadd(setKey1, new String[] {"baz", "foo"});
                    1L, // srem(setKey1, new String[] {"foo"});
                    new Object[] {"0", new String[] {"baz"}}, // sscan(setKey1, "0")
                    new Object[] {"0", new String[] {"baz"}}, // sscan(key1, "0", match "*", count(10L))
                    1L, // scard(setKey1);
                    true, // sismember(setKey1, "baz")
                    Set.of("baz"), // smembers(setKey1);
                    new Boolean[] {true, false}, // smismembmer(setKey1, new String[] {"baz", "foo"})
                    Set.of("baz"), // sinter(new String[] { setKey1, setKey1 })
                    2L, // sadd(setKey2, new String[] { "a", "b" })
                    Set.of("a", "b", "baz"), // sunion(new String[] {setKey2, setKey1})
                    3L, // sunionstore(setKey3, new String[] { setKey2, setKey1 })
                    2L, // sdiffstore(setKey3, new String[] { setKey2, setKey1 })
                    0L, // sinterstore(setKey3, new String[] { setKey2, setKey1 })
                    Set.of("a", "b"), // sdiff(new String[] {setKey2, setKey3})
                    true, // smove(setKey1, setKey2, "baz")
                    1L, // sadd(setKey4, {"foo})
                    "foo", // srandmember(setKey4)
                    new String[] {"foo"}, // srandmember(setKey4, 2)
                    new String[] {"foo", "foo"}, // srandmember(setKey4, -2)};
                    "foo", // spop(setKey4)
                    Set.of(), // spopCount(setKey4, 3)
                };
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                4L, // sadd(setKey5, {"one", "two", "three", "four"})
                                4L, // sadd(setKey6, {"two", "three", "four", "five"})
                                3L, // sintercard({setKey5, setKey6})
                                2L, // sintercard({setKey5, setKey6}, 2)
                            });
        }

        return expectedResults;
    }

    private static Object[] sortedSetCommands(BaseBatch<?> batch, boolean isAtomic) {
        String zSetKey1 = generateKey("ZSetKey", isAtomic);
        String zSetKey2 = generateKey("ZSetKey", isAtomic);
        String zSetKey3 = generateKey("ZSetKey", isAtomic);
        String zSetKey4 = generateKeySameSlot(zSetKey3);
        String zSetKey5 = generateKey("ZSetKey", isAtomic);
        String zSetKey6 = generateKeySameSlot(zSetKey5);

        batch
                .zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0))
                .zrank(zSetKey1, "one")
                .zrevrank(zSetKey1, "one")
                .zaddIncr(zSetKey1, "one", 3)
                .zincrby(zSetKey1, -3., "one")
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
                // zSetKey1 is now empty
                .zremrangebyrank(zSetKey1, 5, 10)
                .zremrangebylex(zSetKey1, new LexBoundary("j"), InfLexBound.POSITIVE_INFINITY)
                .zremrangebyscore(zSetKey1, new ScoreBoundary(5), InfScoreBound.POSITIVE_INFINITY)
                .zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0))
                .bzpopmax(new String[] {zSetKey2}, .1)
                .zrandmember(zSetKey2)
                .zrandmemberWithCount(zSetKey2, 1)
                .zrandmemberWithCountWithScores(zSetKey2, 1)
                .zscan(zSetKey2, "0")
                .zscan(zSetKey2, "0", ZScanOptions.builder().count(20L).build());
        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            batch
                    .zscan(zSetKey2, 0, ZScanOptions.builder().count(20L).noScores(false).build())
                    .zscan(zSetKey2, 0, ZScanOptions.builder().count(20L).noScores(true).build());
        }

        batch.bzpopmin(new String[] {zSetKey2}, .1);
        // zSetKey2 is now empty

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            batch
                    .zadd(zSetKey5, Map.of("one", 1.0, "two", 2.0))
                    // zSetKey6 is empty
                    .zdiffstore(zSetKey6, new String[] {zSetKey6, zSetKey6})
                    .zdiff(new String[] {zSetKey5, zSetKey6})
                    .zdiffWithScores(new String[] {zSetKey5, zSetKey6})
                    .zunionstore(zSetKey5, new KeyArray(new String[] {zSetKey5, zSetKey6}))
                    .zunion(new KeyArray(new String[] {zSetKey5, zSetKey6}))
                    .zunionWithScores(new KeyArray(new String[] {zSetKey5, zSetKey6}))
                    .zunionWithScores(new KeyArray(new String[] {zSetKey5, zSetKey6}), Aggregate.MAX)
                    .zinterstore(zSetKey6, new KeyArray(new String[] {zSetKey5, zSetKey6}))
                    .zinter(new KeyArray(new String[] {zSetKey5, zSetKey6}))
                    .zinterWithScores(new KeyArray(new String[] {zSetKey5, zSetKey6}))
                    .zinterWithScores(new KeyArray(new String[] {zSetKey5, zSetKey6}), Aggregate.MAX);
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .zadd(zSetKey3, Map.of("a", 1., "b", 2., "c", 3., "d", 4., "e", 5., "f", 6., "g", 7.))
                    .zadd(zSetKey4, Map.of("a", 1., "b", 2., "c", 3., "d", 4.))
                    .zmpop(new String[] {zSetKey3}, MAX)
                    .zmpop(new String[] {zSetKey3}, MIN, 2)
                    .bzmpop(new String[] {zSetKey3}, MAX, .1)
                    .bzmpop(new String[] {zSetKey3}, MIN, .1, 2)
                    .zadd(zSetKey3, Map.of("a", 1., "b", 2., "c", 3., "d", 4., "e", 5., "f", 6., "g", 7.))
                    .zintercard(new String[] {zSetKey4, zSetKey3})
                    .zintercard(new String[] {zSetKey4, zSetKey3}, 2);
        }

        var expectedResults =
                new Object[] {
                    3L, // zadd(zSetKey1, Map.of("one", 1.0, "two", 2.0, "three", 3.0))
                    0L, // zrank(zSetKey1, "one")
                    2L, // zrevrank(zSetKey1, "one")
                    4.0, // zaddIncr(zSetKey1, "one", 3)
                    1., // zincrby(zSetKey1, -3.3, "one")
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
                    2L, // zadd(zSetKey2, Map.of("one", 1.0, "two", 2.0))
                    new Object[] {zSetKey2, "two", 2.0}, // bzpopmax(new String[] { zsetKey2 }, .1)
                    "one", // zrandmember(zSetKey2)
                    new String[] {"one"}, // .zrandmemberWithCount(zSetKey2, 1)
                    new Object[][] {{"one", 1.0}}, // .zrandmemberWithCountWithScores(zSetKey2, 1);
                    new Object[] {"0", new String[] {"one", "1"}}, // zscan(zSetKey2, 0)
                    new Object[] {
                        "0", new String[] {"one", "1"}
                    }, // zscan(zSetKey2, 0, ZScanOptions.builder().count(20L).build())
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                new Object[] {
                                    "0", new String[] {"one", "1"}
                                }, // zscan(zSetKey2, 0, ZScanOptions.builder().count(20L).noScores(false).build())
                                new Object[] {
                                    "0", new String[] {"one"}
                                }, // zscan(zSetKey2, 0, ZScanOptions.builder().count(20L).noScores(true).build())
                            });
        }

        expectedResults =
                concatenateArrays(
                        expectedResults,
                        new Object[] {
                            new Object[] {zSetKey2, "one", 1.0}, // bzpopmin(new String[] { zsetKey2 }, .1)
                        });
        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                2L, // zadd(zSetKey5, Map.of("one", 1.0, "two", 2.0))
                                0L, // zdiffstore(zSetKey6, new String[] {zSetKey6, zSetKey6})
                                new String[] {"one", "two"}, // zdiff(new String[] {zSetKey5, zSetKey6})
                                Map.of("one", 1.0, "two", 2.0), // zdiffWithScores({zSetKey5, zSetKey6})
                                2L, // zunionstore(zSetKey5, new KeyArray(new String[] {zSetKey5, zSetKey6}))
                                new String[] {"one", "two"}, // zunion(new KeyArray({zSetKey5, zSetKey6}))
                                Map.of("one", 1.0, "two", 2.0), // zunionWithScores({zSetKey5, zSetKey6})
                                Map.of("one", 1.0, "two", 2.0), // zunionWithScores({zSetKey5, zSetKey6}, MAX)
                                0L, // zinterstore(zSetKey6, new String[] {zSetKey5, zSetKey6})
                                new String[0], // zinter(new KeyArray({zSetKey5, zSetKey6}))
                                Map.of(), // zinterWithScores(new KeyArray({zSetKey5, zSetKey6}))
                                Map.of(), // zinterWithScores(new KeyArray({zSetKey5, zSetKey6}), Aggregate.MAX)
                            });
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                7L, // zadd(zSetKey3, "a", 1., "b", 2., "c", 3., "d", 4., "e", 5., "f", 6., "g", 7.)
                                4L, // zadd(zSetKey4, Map.of("a", 1., "b", 2., "c", 3., "d", 4.))
                                new Object[] {zSetKey3, Map.of("g", 7.)}, // zmpop(zSetKey3, MAX)
                                new Object[] {zSetKey3, Map.of("a", 1., "b", 2.)}, // zmpop(zSetKey3, MIN, 2)
                                new Object[] {zSetKey3, Map.of("f", 6.)}, // bzmpop(zSetKey3, MAX, .1)
                                new Object[] {zSetKey3, Map.of("c", 3., "d", 4.)}, // bzmpop(zSetKey3, MIN, .1, 2)
                                6L, // zadd(zSetKey3, "a", 1., "b", 2., "c", 3., "d", 4., "e", 5., "f", 6., "g", 7.)
                                4L, // zintercard(new String[] {zSetKey4, zSetKey3})
                                2L, // zintercard(new String[] {zSetKey4, zSetKey3}, 2)
                            });
        }

        return expectedResults;
    }

    private static Object[] serverManagementCommands(BaseBatch<?> batch, boolean isAtomic) {
        batch
                .configSet(Map.of("timeout", "1000"))
                .configGet(new String[] {"timeout"})
                .configResetStat()
                .lolwut(1)
                .flushall()
                .flushall(ASYNC)
                .flushdb()
                .flushdb(ASYNC)
                .dbsize();

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .configSet(Map.of("timeout", "2000", "rdb-save-incremental-fsync", "no"))
                    .configGet(new String[] {"timeout", "rdb-save-incremental-fsync"});
        }

        var expectedResults =
                new Object[] {
                    OK, // configSet(Map.of("timeout", "1000"))
                    Map.of("timeout", "1000"), // configGet(new String[] {"timeout"})
                    OK, // configResetStat()
                    new Object() {
                        @Override
                        public boolean equals(Object obj) {
                            if (obj instanceof String) {
                                String response = (String) obj;
                                return response.contains("ver") && response.contains(SERVER_VERSION.toString());
                            }
                            return false;
                        }

                        @Override
                        public String toString() {
                            return "LOLWUT version matcher for " + SERVER_VERSION;
                        }
                    }, // lolwut(1) - accepts both Redis and Valkey formats
                    OK, // flushall()
                    OK, // flushall(ASYNC)
                    OK, // flushdb()
                    OK, // flushdb(ASYNC)
                    0L, // dbsize()
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // configSet(Map.of("timeout", "2000", "rdb-save-incremental-fsync", "no"))
                                Map.of(
                                        "timeout",
                                        "2000",
                                        "rdb-save-incremental-fsync",
                                        "no"), // configGet(new String[] {"timeout", "rdb-save-incremental-fsync"})
                            });
        }

        return expectedResults;
    }

    private static Object[] connectionManagementCommands(BaseBatch<?> batch, boolean isAtomic) {
        batch.ping().ping(value1).echo(value2);
        // untested:
        // clientId
        // clientGetName

        return new Object[] {
            "PONG", // ping()
            value1, // ping(value1)
            value2, // echo(value2)
        };
    }

    private static Object[] hyperLogLogCommands(BaseBatch<?> batch, boolean isAtomic) {
        String hllKey1 = generateKey("HllKey", isAtomic);
        String hllKey2 = generateKeySameSlot(hllKey1);
        String hllKey3 = generateKeySameSlot(hllKey1);

        batch
                .pfadd(hllKey1, new String[] {"a", "b", "c"})
                .pfcount(new String[] {hllKey1, hllKey2})
                .pfmerge(hllKey3, new String[] {hllKey1, hllKey2})
                .pfcount(new String[] {hllKey3});

        return new Object[] {
            true, // pfadd(hllKey1, new String[] {"a", "b", "c"})
            3L, // pfcount(new String[] { hllKey1, hllKey2 })
            OK, // pfmerge(hllKey3, new String[] {hllKey1, hllKey2})
            3L, // pfcount(new String[] { hllKey3 })
        };
    }

    private static Object[] streamCommands(BaseBatch<?> batch, boolean isAtomic) {
        final String streamKey1 = generateKey("streamKey", isAtomic);
        final String streamKey2 = generateKey("streamKey", isAtomic);
        final String streamKey3 = generateKey("streamKey", isAtomic);
        final String streamKey4 = generateKey("streamKey", isAtomic);
        final String groupName1 = "{groupName}-1-" + generateRandomNumericSuffix();
        final String groupName2 = "{groupName}-2-" + generateRandomNumericSuffix();
        final String groupName3 = "{groupName}-2-" + generateRandomNumericSuffix();
        final String consumer1 = "{consumer}-1-" + generateRandomNumericSuffix();

        batch
                .xadd(streamKey1, Map.of("field1", "value1"), StreamAddOptions.builder().id("0-1").build())
                .xadd(streamKey1, Map.of("field2", "value2"), StreamAddOptions.builder().id("0-2").build())
                .xadd(streamKey1, Map.of("field3", "value3"), StreamAddOptions.builder().id("0-3").build())
                .xadd(
                        streamKey4,
                        new String[][] {{"field4", "value4"}, {"field4", "value5"}},
                        StreamAddOptions.builder().id("0-4").build())
                .xlen(streamKey1)
                .xread(Map.of(streamKey1, "0-2"))
                .xread(Map.of(streamKey1, "0-2"), StreamReadOptions.builder().count(1L).build())
                .xrange(streamKey1, IdBound.of("0-1"), IdBound.of("0-1"))
                .xrange(streamKey1, IdBound.of("0-1"), IdBound.of("0-1"), 1L)
                .xrevrange(streamKey1, IdBound.of("0-1"), IdBound.of("0-1"))
                .xrevrange(streamKey1, IdBound.of("0-1"), IdBound.of("0-1"), 1L)
                .xtrim(streamKey1, new MinId(true, "0-2"))
                .xgroupCreate(streamKey1, groupName1, "0-2")
                .xinfoConsumers(streamKey1, groupName1)
                .xgroupCreate(
                        streamKey1, groupName2, "0-0", StreamGroupOptions.builder().makeStream().build())
                .xgroupCreateConsumer(streamKey1, groupName1, consumer1)
                .xgroupSetId(streamKey1, groupName1, "0-2")
                .xreadgroup(Map.of(streamKey1, ">"), groupName1, consumer1)
                .xreadgroup(
                        Map.of(streamKey1, "0-3"),
                        groupName1,
                        consumer1,
                        StreamReadGroupOptions.builder().count(2L).build())
                .xclaim(streamKey1, groupName1, consumer1, 0L, new String[] {"0-1"})
                .xclaim(
                        streamKey1,
                        groupName1,
                        consumer1,
                        0L,
                        new String[] {"0-3"},
                        StreamClaimOptions.builder().force().build())
                .xclaimJustId(streamKey1, groupName1, consumer1, 0L, new String[] {"0-3"})
                .xclaimJustId(
                        streamKey1,
                        groupName1,
                        consumer1,
                        0L,
                        new String[] {"0-4"},
                        StreamClaimOptions.builder().force().build())
                .xpending(streamKey1, groupName1);

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            batch
                    .xautoclaim(streamKey1, groupName1, consumer1, 0L, "0-0")
                    .xautoclaimJustId(streamKey1, groupName1, consumer1, 0L, "0-0");
        }

        batch
                .xack(streamKey1, groupName1, new String[] {"0-3"})
                .xpending(
                        streamKey1,
                        groupName1,
                        StreamRange.InfRangeBound.MIN,
                        StreamRange.InfRangeBound.MAX,
                        1L)
                .xgroupDelConsumer(streamKey1, groupName1, consumer1)
                .xgroupDestroy(streamKey1, groupName1)
                .xgroupDestroy(streamKey1, groupName2)
                .xdel(streamKey1, new String[] {"0-3", "0-5"})
                .xadd(streamKey3, Map.of("f0", "v0"), StreamAddOptions.builder().id("1-0").build())
                .xgroupCreate(streamKey3, groupName3, "0")
                .xinfoGroups(streamKey1);

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .xadd(streamKey2, Map.of("f0", "v0"), StreamAddOptions.builder().id("1-0").build())
                    .xgroupCreate(streamKey2, groupName3, "0")
                    .xgroupSetId(streamKey2, groupName3, "1-0", 1);
        }

        var result =
                new Object[] {
                    "0-1", // xadd(streamKey1, Map.of("field1", "value1"), ... .id("0-1").build());
                    "0-2", // xadd(streamKey1, Map.of("field2", "value2"), ... .id("0-2").build());
                    "0-3", // xadd(streamKey1, Map.of("field3", "value3"), ... .id("0-3").build());
                    "0-4", // xadd(streamKey4, new String[][] {{"field4", "value4"}, {"field4", "value5"}}),
                    // ... .id("0-4").build());
                    3L, // xlen(streamKey1)
                    Map.of(
                            streamKey1,
                            Map.of("0-3", new String[][] {{"field3", "value3"}})), // xread(Map.of(key9, "0-2"));
                    Map.of(
                            streamKey1,
                            Map.of(
                                    "0-3",
                                    new String[][] {{"field3", "value3"}})), // xread(Map.of(key9, "0-2"), options);
                    Map.of("0-1", new String[][] {{"field1", "value1"}}), // .xrange(streamKey1, "0-1", "0-1")
                    Map.of(
                            "0-1",
                            new String[][] {{"field1", "value1"}}), // .xrange(streamKey1, "0-1", "0-1", 1l)
                    Map.of(
                            "0-1", new String[][] {{"field1", "value1"}}), // .xrevrange(streamKey1, "0-1", "0-1")
                    Map.of(
                            "0-1",
                            new String[][] {{"field1", "value1"}}), // .xrevrange(streamKey1, "0-1", "0-1", 1l)
                    1L, // xtrim(streamKey1, new MinId(true, "0-2"))
                    OK, // xgroupCreate(streamKey1, groupName1, "0-0")
                    new Map[] {}, // .xinfoConsumers(streamKey1, groupName1)
                    OK, // xgroupCreate(streamKey1, groupName1, "0-0", options)
                    true, // xgroupCreateConsumer(streamKey1, groupName1, consumer1)
                    OK, // xgroupSetId(streamKey1, groupName1, "0-2")
                    Map.of(
                            streamKey1,
                            Map.of(
                                    "0-3",
                                    new String[][] {
                                        {"field3", "value3"}
                                    })), // xreadgroup(Map.of(streamKey1, ">"), groupName1, consumer1);
                    Map.of(
                            streamKey1,
                            Map.of()), // xreadgroup(Map.of(streamKey1, ">"), groupName1, consumer1, options);
                    Map.of(), // xclaim(streamKey1, groupName1, consumer1, 0L, new String[] {"0-1"})
                    Map.of(
                            "0-3",
                            new String[][] {{"field3", "value3"}}), // xclaim(streamKey1, ..., {"0-3"}, options)
                    new String[] {"0-3"}, // xclaimJustId(streamKey1, ..., new String[] {"0-3"})
                    new String[0], // xclaimJustId(streamKey1, ..., new String[] {"0-4"}, options)
                    new Object[] {
                        1L, "0-3", "0-3", new Object[][] {{consumer1, "1"}} // xpending(streamKey1, groupName1)
                    }
                };
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            result =
                    concatenateArrays(
                            result,
                            new Object[] {
                                new Object[] {
                                    "0-0",
                                    Map.of("0-3", new String[][] {{"field3", "value3"}}),
                                    new Object[] {} // one more array is returned here for version >= 7.0.0
                                }, // xautoclaim(streamKey1, groupName1, consumer1, 0L, "0-0")
                                new Object[] {
                                    "0-0",
                                    new String[] {"0-3"},
                                    new Object[] {} // one more array is returned here for version >= 7.0.0
                                } // xautoclaimJustId(streamKey1, groupName1, consumer1, 0L, "0-0");
                            });
        } else if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            result =
                    concatenateArrays(
                            result,
                            new Object[] {
                                new Object[] {
                                    "0-0", Map.of("0-3", new String[][] {{"field3", "value3"}})
                                }, // xautoclaim(streamKey1, groupName1, consumer1, 0L, "0-0")
                                new Object[] {
                                    "0-0", new String[] {"0-3"}
                                } // xautoclaimJustId(streamKey1, groupName1, consumer1, 0L, "0-0");
                            });
        }
        result =
                concatenateArrays(
                        result,
                        new Object[] {
                            1L, // xack(streamKey1, groupName1, new String[] {"0-3"})
                            new Object[] {}, // xpending(streamKey1, groupName1, MIN, MAX, 1L)
                            0L, // xgroupDelConsumer(streamKey1, groupName1, consumer1)
                            true, // xgroupDestroy(streamKey1, groupName1)
                            true, // xgroupDestroy(streamKey1, groupName2)
                            1L, // .xdel(streamKey1, new String[] {"0-1", "0-5"})
                            "1-0", // xadd(streamKey3, Map.of("f0", "v0"), id("1-0"))
                            OK, // xgroupCreate(streamKey3, groupName3, "0")
                            new Map[] {} // xinfoGroups(streamKey3)
                        });

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            result =
                    concatenateArrays(
                            result,
                            new Object[] {
                                "1-0", // xadd(streamKey2, Map.of("f0", "v0"),
                                // StreamAddOptions.builder().id("1-0").build())
                                OK, // xgroupCreate(streamKey2, groupName3, "0")
                                OK, // xgroupSetId(streamKey2, groupName3, "1-0", "0");
                            });
        }

        return result;
    }

    private static Object[] geospatialCommands(BaseBatch<?> batch, boolean isAtomic) {
        final String geoKey1 = generateKey("geoKey", isAtomic);
        final String geoKey2 = generateKeySameSlot(geoKey1);

        batch
                .geoadd(
                        geoKey1,
                        Map.of(
                                "Palermo",
                                new GeospatialData(13.361389, 38.115556),
                                "Catania",
                                new GeospatialData(15.087269, 37.502669)))
                .geopos(geoKey1, new String[] {"Palermo", "Catania"})
                .geodist(geoKey1, "Palermo", "Catania")
                .geodist(geoKey1, "Palermo", "Catania", GeoUnit.KILOMETERS)
                .geohash(geoKey1, new String[] {"Palermo", "Catania", "NonExisting"});

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            batch
                    .geosearch(
                            geoKey1,
                            new GeoSearchOrigin.MemberOrigin("Palermo"),
                            new GeoSearchShape(200, GeoUnit.KILOMETERS),
                            new GeoSearchResultOptions(SortOrder.ASC))
                    .geosearch(
                            geoKey1,
                            new GeoSearchOrigin.CoordOrigin(new GeospatialData(15, 37)),
                            new GeoSearchShape(400, 400, GeoUnit.KILOMETERS))
                    .geosearch(
                            geoKey1,
                            new GeoSearchOrigin.MemberOrigin("Palermo"),
                            new GeoSearchShape(200, GeoUnit.KILOMETERS),
                            GeoSearchOptions.builder().withhash().withdist().withcoord().build(),
                            new GeoSearchResultOptions(SortOrder.ASC, 2))
                    .geosearch(
                            geoKey1,
                            new GeoSearchOrigin.CoordOrigin(new GeospatialData(15, 37)),
                            new GeoSearchShape(400, 400, GeoUnit.KILOMETERS),
                            GeoSearchOptions.builder().withhash().withdist().withcoord().build(),
                            new GeoSearchResultOptions(SortOrder.ASC, 2))
                    .geosearchstore(
                            geoKey2,
                            geoKey1,
                            new GeoSearchOrigin.MemberOrigin("Palermo"),
                            new GeoSearchShape(200, GeoUnit.KILOMETERS),
                            new GeoSearchResultOptions(SortOrder.ASC))
                    .geosearchstore(
                            geoKey2,
                            geoKey1,
                            new GeoSearchOrigin.CoordOrigin(new GeospatialData(15, 37)),
                            new GeoSearchShape(400, 400, GeoUnit.KILOMETERS),
                            GeoSearchStoreOptions.builder().storedist().build(),
                            new GeoSearchResultOptions(SortOrder.ASC, 2));
        }

        var expectedResults =
                new Object[] {
                    2L, // geoadd(geoKey1, Map.of("Palermo", ..., "Catania", ...))
                    new Double[][] {
                        {13.36138933897018433, 38.11555639549629859},
                        {15.08726745843887329, 37.50266842333162032},
                    }, // geopos(geoKey1, new String[]{"Palermo", "Catania"})
                    166274.1516, // geodist(geoKey1, "Palermo", "Catania")
                    166.2742, // geodist(geoKey1, "Palermo", "Catania", GeoUnit.KILOMETERS)
                    new String[] {
                        "sqc8b49rny0", "sqdtr74hyu0", null
                    } // geohash(geoKey1, new String[] {"Palermo", "Catania", "NonExisting"})
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                new String[] {
                                    "Palermo", "Catania"
                                }, // geosearch(geoKey1, "Palermo", byradius(200, km))
                                new String[] {
                                    "Palermo", "Catania"
                                }, // geosearch(geoKey1, (15, 37), bybox(400, 400, km))
                                new Object[] {
                                    new Object[] {
                                        "Palermo",
                                        new Object[] {
                                            0.0, 3479099956230698L, new Object[] {13.361389338970184, 38.1155563954963}
                                        }
                                    },
                                    new Object[] {
                                        "Catania",
                                        new Object[] {
                                            166.2742,
                                            3479447370796909L,
                                            new Object[] {15.087267458438873, 37.50266842333162}
                                        }
                                    }
                                }, // geosearch(geoKey1, "Palermo", BYRADIUS(200, km), ASC, COUNT 2)
                                new Object[] {
                                    new Object[] {
                                        "Catania",
                                        new Object[] {
                                            56.4413,
                                            3479447370796909L,
                                            new Object[] {15.087267458438873, 37.50266842333162}
                                        }
                                    },
                                    new Object[] {
                                        "Palermo",
                                        new Object[] {
                                            190.4424,
                                            3479099956230698L,
                                            new Object[] {13.361389338970184, 38.1155563954963}
                                        }
                                    },
                                }, // geosearch(geoKey1, (15,37), BYBOX(400,400,km), ASC, COUNT 2)
                                2L, // geosearchstore(geoKey2, geoKey1, (15, 37), (400, 400, km), ASC, 2)
                                2L, // geosearchstore(geoKey2, geoKey1, (15, 37), (400, 400, km), STOREDIST, ASC, 2)
                            });
        }

        return expectedResults;
    }

    private static Object[] scriptingAndFunctionsCommands(BaseBatch<?> batch, boolean isAtomic) {
        if (SERVER_VERSION.isLowerThan("7.0.0")) {
            return new Object[0];
        }

        final String libName = "mylib1T";
        final String funcName = "myfunc1T";

        // function $funcName returns first argument
        final String code = generateLuaLibCode(libName, Map.of(funcName, "return args[1]"), true);

        var expectedFuncData =
                new HashMap<String, Object>() {
                    {
                        put("name", funcName);
                        put("description", null);
                        put("flags", Set.of("no-writes"));
                    }
                };

        var expectedLibData =
                new Map[] {
                    Map.<String, Object>of(
                            "library_name",
                            libName,
                            "engine",
                            "LUA",
                            "functions",
                            new Object[] {expectedFuncData},
                            "library_code",
                            code)
                };

        var expectedFunctionStatsNonEmpty =
                new HashMap<String, Map<String, Object>>() {
                    {
                        put("running_script", null);
                        put("engines", Map.of("LUA", Map.of("libraries_count", 1L, "functions_count", 1L)));
                    }
                };
        var expectedFunctionStatsEmpty =
                new HashMap<String, Map<String, Object>>() {
                    {
                        put("running_script", null);
                        put("engines", Map.of("LUA", Map.of("libraries_count", 0L, "functions_count", 0L)));
                    }
                };

        batch
                .functionFlush(SYNC)
                .functionList(false)
                .functionLoad(code, false)
                .functionLoad(code, true)
                .functionStats()
                .fcall(funcName, new String[0], new String[] {"a", "b"})
                .fcall(funcName, new String[] {"a", "b"})
                .fcallReadOnly(funcName, new String[0], new String[] {"a", "b"})
                .fcallReadOnly(funcName, new String[] {"a", "b"})
                .functionList("otherLib", false)
                .functionList(libName, true)
                .functionDelete(libName)
                .functionList(true)
                .functionStats();

        return new Object[] {
            OK, // functionFlush(SYNC)
            new Map[0], // functionList(false)
            libName, // functionLoad(code, false)
            libName, // functionLoad(code, true)
            expectedFunctionStatsNonEmpty, // functionStats()
            "a", // fcall(funcName, new String[0], new String[]{"a", "b"})
            "a", // fcall(funcName, new String[] {"a", "b"})
            "a", // fcallReadOnly(funcName, new String[0], new String[]{"a", "b"})
            "a", // fcallReadOnly(funcName, new String[] {"a", "b"})
            new Map[0], // functionList("otherLib", false)
            expectedLibData, // functionList(libName, true)
            OK, // functionDelete(libName)
            new Map[0], // functionList(true)
            expectedFunctionStatsEmpty, // functionStats()
        };
    }

    private static Object[] bitmapCommands(BaseBatch<?> batch, boolean isAtomic) {
        String key1 = generateKey("bitmapKey", isAtomic);
        String key2 = generateKey("bitmapKey", isAtomic);
        String key3 = generateKeySameSlot(key1);
        String key4 = generateKeySameSlot(key1);
        BitFieldGet bitFieldGet = new BitFieldGet(new SignedEncoding(5), new Offset(3));
        BitFieldSet bitFieldSet = new BitFieldSet(new UnsignedEncoding(10), new OffsetMultiplier(3), 4);

        batch
                .set(key1, "foobar")
                .bitcount(key1)
                .bitcount(key1, 1, 1)
                .setbit(key2, 1, 1)
                .setbit(key2, 1, 0)
                .getbit(key1, 1)
                .bitpos(key1, 1)
                .bitpos(key1, 1, 3)
                .bitpos(key1, 1, 3, 5)
                .set(key3, "abcdef")
                .bitop(BitwiseOperation.AND, key4, new String[] {key1, key3})
                .get(key4)
                .bitfieldReadOnly(key1, new BitFieldReadOnlySubCommands[] {bitFieldGet})
                .bitfield(key1, new BitFieldSubCommands[] {bitFieldSet});

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            batch
                    .set(key3, "foobar")
                    .bitcount(key3, 5, 30, BitmapIndexType.BIT)
                    .bitpos(key3, 1, 44, 50, BitmapIndexType.BIT);
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            batch.set(key4, "foobar").bitcount(key4, 0);
        }

        var expectedResults =
                new Object[] {
                    OK, // set(key1, "foobar")
                    26L, // bitcount(key1)
                    6L, // bitcount(key1, 1, 1)
                    0L, // setbit(key2, 1, 1)
                    1L, // setbit(key2, 1, 0)
                    1L, // getbit(key1, 1)
                    1L, // bitpos(key, 1)
                    25L, // bitpos(key, 1, 3)
                    25L, // bitpos(key, 1, 3, 5)
                    OK, // set(key3, "abcdef")
                    6L, // bitop(BitwiseOperation.AND, key4, new String[] {key1, key3})
                    "`bc`ab", // get(key4)
                    new Long[] {6L}, // bitfieldReadOnly(key1, BitFieldReadOnlySubCommands)
                    new Long[] {609L}, // bitfield(key1, BitFieldSubCommands)
                };

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // set(key3, "foobar")
                                17L, // bitcount(key3, 5, 30, BitmapIndexType.BIT)
                                46L, // bitpos(key3, 1, 44, 50, BitmapIndexType.BIT)
                            });
        }

        if (SERVER_VERSION.isGreaterThanOrEqualTo("8.0.0")) {
            expectedResults =
                    concatenateArrays(
                            expectedResults,
                            new Object[] {
                                OK, // set(key4, "foobar")
                                26L, // bitcount(key4, 0)
                            });
        }
        return expectedResults;
    }

    private static Object[] pubsubCommands(BaseBatch<?> batch, boolean isAtomic) {
        batch.publish("message", "Tchannel");

        return new Object[] {
            0L, // publish("message", "Tchannel")
        };
    }
}
