/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.GenericCommands.DB_REDIS_API;
import static glide.api.models.TransactionTests.buildArgs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.Move;
import static redis_request.RedisRequestOuterClass.RequestType.Select;

import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass;

public class StandaloneTransactionTests {
    @Test
    public void standalone_transaction_commands() {
        List<Pair<RedisRequestOuterClass.RequestType, RedisRequestOuterClass.Command.ArgsArray>>
                results = new LinkedList<>();
        Transaction transaction = new Transaction();

        transaction.select(5L);
        results.add(Pair.of(Select, buildArgs("5")));
        transaction.move("testKey", 2L);
        results.add(Pair.of(Move, buildArgs("testKey", "2")));
        transaction.copy("key1", "key2", 1, true);
        results.add(Pair.of(Copy, buildArgs("key1", "key2", DB_REDIS_API, "1", REPLACE_REDIS_API)));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            RedisRequestOuterClass.Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
