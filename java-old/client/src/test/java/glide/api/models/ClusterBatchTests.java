/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.PubSubShardChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardNumSub;
import static command_request.CommandRequestOuterClass.RequestType.SPublish;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static glide.api.models.BatchTests.buildArgs;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.OrderBy.ASC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import command_request.CommandRequestOuterClass.Command;
import command_request.CommandRequestOuterClass.Command.ArgsArray;
import command_request.CommandRequestOuterClass.RequestType;
import glide.api.models.commands.SortBaseOptions;
import glide.api.models.commands.SortOptions;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ClusterBatchTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void cluster_batch_builds_protobuf_request(boolean isAtomic) {
        ClusterBatch batch = new ClusterBatch(isAtomic);
        List<Pair<RequestType, ArgsArray>> results = new LinkedList<>();

        batch.publish("msg", "ch1", true);
        results.add(Pair.of(SPublish, buildArgs("ch1", "msg")));

        batch.pubsubShardChannels();
        results.add(Pair.of(PubSubShardChannels, buildArgs()));

        batch.pubsubShardChannels("test*");
        results.add(Pair.of(PubSubShardChannels, buildArgs("test*")));

        batch.pubsubShardNumSub(new String[] {"ch1", "ch2"});
        results.add(Pair.of(PubSubShardNumSub, buildArgs("ch1", "ch2")));

        batch.sortReadOnly(
                "key1",
                SortOptions.builder()
                        .orderBy(ASC)
                        .alpha()
                        .limit(new SortBaseOptions.Limit(0L, 1L))
                        .build());
        results.add(
                Pair.of(
                        SortReadOnly,
                        buildArgs(
                                "key1", LIMIT_COMMAND_STRING, "0", "1", ASC.toString(), ALPHA_COMMAND_STRING)));

        batch.sort(
                "key1",
                SortOptions.builder()
                        .orderBy(ASC)
                        .alpha()
                        .limit(new SortBaseOptions.Limit(0L, 1L))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1", LIMIT_COMMAND_STRING, "0", "1", ASC.toString(), ALPHA_COMMAND_STRING)));

        batch.sortStore(
                "key1",
                "key2",
                SortOptions.builder()
                        .orderBy(ASC)
                        .alpha()
                        .limit(new SortBaseOptions.Limit(0L, 1L))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                ASC.toString(),
                                ALPHA_COMMAND_STRING,
                                STORE_COMMAND_STRING,
                                "key2")));

        var protobufBatch = batch.getProtobufBatch().build();

        for (int idx = 0; idx < protobufBatch.getCommandsCount(); idx++) {
            Command protobuf = protobufBatch.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
