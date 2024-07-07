/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.Copy;
import static command_request.CommandRequestOuterClass.RequestType.Move;
import static command_request.CommandRequestOuterClass.RequestType.Scan;
import static command_request.CommandRequestOuterClass.RequestType.Select;
import static command_request.CommandRequestOuterClass.RequestType.Sort;
import static command_request.CommandRequestOuterClass.RequestType.SortReadOnly;
import static glide.api.commands.GenericBaseCommands.REPLACE_VALKEY_API;
import static glide.api.commands.GenericCommands.DB_VALKEY_API;
import static glide.api.models.TransactionTests.buildArgs;
import static glide.api.models.commands.SortBaseOptions.ALPHA_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.LIMIT_COMMAND_STRING;
import static glide.api.models.commands.SortBaseOptions.Limit;
import static glide.api.models.commands.SortBaseOptions.OrderBy.DESC;
import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.BY_COMMAND_STRING;
import static glide.api.models.commands.SortOptions.GET_COMMAND_STRING;
import static glide.api.models.commands.scan.BaseScanOptions.COUNT_OPTION_STRING;
import static glide.api.models.commands.scan.BaseScanOptions.MATCH_OPTION_STRING;
import static glide.api.models.commands.scan.ScanOptions.ObjectType.ZSET;
import static glide.api.models.commands.scan.ScanOptions.TYPE_OPTION_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import command_request.CommandRequestOuterClass;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.scan.ScanOptions;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class StandaloneTransactionTests {
    @Test
    public void standalone_transaction_commands() {
        List<Pair<CommandRequestOuterClass.RequestType, CommandRequestOuterClass.Command.ArgsArray>>
                results = new LinkedList<>();
        Transaction transaction = new Transaction();

        transaction.select(5L);
        results.add(Pair.of(Select, buildArgs("5")));
        transaction.move("testKey", 2L);
        results.add(Pair.of(Move, buildArgs("testKey", "2")));
        transaction.copy("key1", "key2", 1, true);
        results.add(Pair.of(Copy, buildArgs("key1", "key2", DB_VALKEY_API, "1", REPLACE_VALKEY_API)));

        transaction.sort(
                "key1",
                SortOptions.builder()
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sort(
                "key1",
                SortOptions.builder()
                        .orderBy(DESC)
                        .alpha()
                        .limit(new Limit(0L, 1L))
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                DESC.toString(),
                                ALPHA_COMMAND_STRING,
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sortReadOnly(
                "key1",
                SortOptions.builder()
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        SortReadOnly,
                        buildArgs(
                                "key1",
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sortReadOnly(
                "key1",
                SortOptions.builder()
                        .orderBy(DESC)
                        .alpha()
                        .limit(new Limit(0L, 1L))
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        SortReadOnly,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                DESC.toString(),
                                ALPHA_COMMAND_STRING,
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2")));
        transaction.sortStore(
                "key1",
                "key2",
                SortOptions.builder()
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2",
                                STORE_COMMAND_STRING,
                                "key2")));
        transaction.sortStore(
                "key1",
                "key2",
                SortOptions.builder()
                        .orderBy(DESC)
                        .alpha()
                        .limit(new Limit(0L, 1L))
                        .byPattern("byPattern")
                        .getPatterns(List.of("getPattern1", "getPattern2"))
                        .build());
        results.add(
                Pair.of(
                        Sort,
                        buildArgs(
                                "key1",
                                LIMIT_COMMAND_STRING,
                                "0",
                                "1",
                                DESC.toString(),
                                ALPHA_COMMAND_STRING,
                                BY_COMMAND_STRING,
                                "byPattern",
                                GET_COMMAND_STRING,
                                "getPattern1",
                                GET_COMMAND_STRING,
                                "getPattern2",
                                STORE_COMMAND_STRING,
                                "key2")));

        transaction.scan("cursor");
        results.add(Pair.of(Scan, buildArgs("cursor")));

        transaction.scan(
                "cursor", ScanOptions.builder().matchPattern("pattern").count(99L).type(ZSET).build());
        results.add(
                Pair.of(
                        Scan,
                        buildArgs(
                                "cursor",
                                MATCH_OPTION_STRING,
                                "pattern",
                                COUNT_OPTION_STRING,
                                "99",
                                TYPE_OPTION_STRING,
                                ZSET.toString())));

        var protobufTransaction = transaction.getProtobufTransaction().build();

        for (int idx = 0; idx < protobufTransaction.getCommandsCount(); idx++) {
            CommandRequestOuterClass.Command protobuf = protobufTransaction.getCommands(idx);

            assertEquals(results.get(idx).getLeft(), protobuf.getRequestType());
            assertEquals(
                    results.get(idx).getRight().getArgsCount(), protobuf.getArgsArray().getArgsCount());
            assertEquals(results.get(idx).getRight(), protobuf.getArgsArray());
        }
    }
}
