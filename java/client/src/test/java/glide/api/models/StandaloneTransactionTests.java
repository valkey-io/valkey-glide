/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis_request.RedisRequestOuterClass.RequestType.Select;

import com.google.protobuf.ByteString;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;

public class StandaloneTransactionTests {
    @Test
    public void standalone_transaction_commands() {
        List<Pair<RedisRequestOuterClass.RequestType, RedisRequestOuterClass.Command.ArgsArray>>
                results = new LinkedList<>();
        Transaction transaction = new Transaction();

        transaction.select(5L);
        results.add(
                Pair.of(Select, ArgsArray.newBuilder().addArgs(ByteString.copyFromUtf8("5")).build()));

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
