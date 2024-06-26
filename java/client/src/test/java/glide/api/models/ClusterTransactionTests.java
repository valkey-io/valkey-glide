/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.models.TransactionTests.buildArgs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static redis_request.RedisRequestOuterClass.RequestType.SPublish;

import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RequestType;

public class ClusterTransactionTests {

    @Test
    public void cluster_transaction_builds_protobuf_request() {
        ClusterTransaction transaction = new ClusterTransaction();
        List<Pair<RequestType, ArgsArray>> results = new LinkedList<>();

        transaction.spublish("ch1", "msg");
        results.add(Pair.of(SPublish, buildArgs("ch1", "msg")));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
