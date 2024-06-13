/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for standalone and cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsBaseCommands {
    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction.
     *
     * @apiNote When in cluster mode, the command may route to multiple nodes when <code>keys</code>
     *     map to different hash slots.
     * @see <a href="https://redis.io/docs/latest/commands/watch/">redis.io</a> for details.
     * @param keys The keys to watch.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * transaction.set("sampleKey", "foobar");
     * Object[] result = client.exec(transaction).get();
     * assert result != null; // Executes successfully and keys are unwatched.
     *
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * transaction.set("sampleKey", "foobar");
     * assert client.set("sampleKey", "hello world").get().equals("OK");
     * Object[] result = client.exec(transaction).get();
     * assert result == null; // null is returned when the watched key is modified before transaction execution.
     * }</pre>
     */
    CompletableFuture<String> watch(String[] keys);

    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see <a href="https://redis.io/docs/latest/commands/unwatch/">redis.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new String[] {"sampleKey"}).get().equals("OK");
     * assert client.unwatch().get().equals("OK"); // Flushes "sampleKey" from watched keys.
     * }</pre>
     */
    CompletableFuture<String> unwatch();
}
