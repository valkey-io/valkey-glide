/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import glide.api.models.BaseTransaction;
import glide.api.models.commands.SetOptions;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TransactionTestUtilities {
    private static final String key1 = "{key}" + UUID.randomUUID();
    private static final String key2 = "{key}" + UUID.randomUUID();
    private static final String key3 = "{key}" + UUID.randomUUID();
    private static final String key4 = "{key}" + UUID.randomUUID();
    private static final String value1 = "{value}" + UUID.randomUUID();
    private static final String value2 = "{value}" + UUID.randomUUID();

    public static BaseTransaction<?> transactionTest(BaseTransaction<?> baseTransaction) {

        baseTransaction.set(key1, value1);
        baseTransaction.get(key1);

        baseTransaction.set(key2, value2, SetOptions.builder().returnOldValue(true).build());
        baseTransaction.customCommand(new String[] {"MGET", key1, key2});

        baseTransaction.mset(Map.of(key1, value2, key2, value1));
        baseTransaction.mget(new String[] {key1, key2});

        baseTransaction.incr(key3);
        baseTransaction.incrBy(key3, 2);

        baseTransaction.decr(key3);
        baseTransaction.decrBy(key3, 2);

        baseTransaction.incrByFloat(key3, 0.5);

        baseTransaction.sadd(key4, new String[] {"baz", "foo"});
        baseTransaction.srem(key4, new String[] {"foo"});
        baseTransaction.scard(key4);
        baseTransaction.smembers(key4);

        return baseTransaction;
    }

    public static Object[] transactionTestResult() {
        return new Object[] {
            "OK",
            value1,
            null,
            new String[] {value1, value2},
            "OK",
            new String[] {value2, value1},
            1L,
            3L,
            2L,
            0L,
            0.5,
            2L,
            1L,
            1L,
            Set.of("baz"),
        };
    }
}
