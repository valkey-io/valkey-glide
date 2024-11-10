/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for standalone and cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsBaseCommands {
    /**
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction.
     *
     * @apiNote In cluster mode, if keys in <code>keys</code> map to different hash slots, the command
     *     will be split across these slots and executed separately for each. This means the command
     *     is atomic only at the slot level. If one or more slot-specific requests fail, the entire
     *     call will return the first encountered error, even though some requests may have succeeded
     *     while others did not. If this behavior impacts your application logic, consider splitting
     *     the request into sub-requests per slot to ensure atomicity.
     * @see <a href="https://valkey.io/commands/watch/">valkey.io</a> for details.
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
     * Marks the given keys to be watched for conditional execution of a transaction. Transactions
     * will only execute commands if the watched keys are not modified before execution of the
     * transaction.
     *
     * @apiNote In cluster mode, if keys in <code>keys</code> map to different hash slots, the command
     *     will be split across these slots and executed separately for each. This means the command
     *     is atomic only at the slot level. If one or more slot-specific requests fail, the entire
     *     call will return the first encountered error, even though some requests may have succeeded
     *     while others did not. If this behavior impacts your application logic, consider splitting
     *     the request into sub-requests per slot to ensure atomicity.
     * @see <a href="https://valkey.io/commands/watch/">valkey.io</a> for details.
     * @param keys The keys to watch.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * assert client.watch(new GlideString[] {gs("sampleKey")}).get().equals("OK");
     * transaction.set(gs("sampleKey"), gs("foobar"));
     * Object[] result = client.exec(transaction).get();
     * assert result != null; // Executes successfully and keys are unwatched.
     *
     * assert client.watch(new GlideString[] {gs("sampleKey")}).get().equals("OK");
     * transaction.set(gs("sampleKey"), gs("foobar"));
     * assert client.set(gs("sampleKey"), gs("hello world")).get().equals("OK");
     * Object[] result = client.exec(transaction).get();
     * assert result == null; // null is returned when the watched key is modified before transaction execution.
     * }</pre>
     */
    CompletableFuture<String> watch(GlideString[] keys);
}
