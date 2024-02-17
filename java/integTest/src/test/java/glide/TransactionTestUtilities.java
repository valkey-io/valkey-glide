/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import glide.api.models.BaseTransaction;
import glide.api.models.commands.SetOptions;
import java.util.Set;
import java.util.UUID;

public class TransactionTestUtilities {

    public static BaseTransaction<?> transactionTest(BaseTransaction<?> baseTransaction) {
        String key1 = "{key}" + UUID.randomUUID();
        String key2 = "{key}" + UUID.randomUUID();
        String key3 = "{key}" + UUID.randomUUID();

        baseTransaction.set(key1, "bar");
        baseTransaction.set(key2, "baz", SetOptions.builder().returnOldValue(true).build());
        baseTransaction.customCommand(new String[] {"MGET", key1, key2});

        baseTransaction.sadd(key3, new String[] {"baz", "foo"});
        baseTransaction.srem(key3, new String[] {"foo"});
        baseTransaction.scard(key3);
        baseTransaction.smembers(key3);

        return baseTransaction;
    }

    public static Object[] transactionTestResult() {
        return new Object[] {"OK", null, new String[] {"bar", "baz"}, 2L, 1L, 1L, Set.of("baz")};
    }
}
