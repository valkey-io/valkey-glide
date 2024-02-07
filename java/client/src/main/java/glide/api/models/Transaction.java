/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import lombok.AllArgsConstructor;

/**
 * Extends BaseTransaction class for Redis standalone commands. Transactions allow the execution of
 * a group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client <code>exec</code>
 * command, in the order they were given. Each element in the array represents a command given to
 * the <code>Transaction</code>. The response for each command depends on the executed Redis
 * command. Specific response types are documented alongside each method.
 *
 * @Examples:
 *     <pre>
 *  Transaction transaction = new Transaction()
 *    .transaction.set("key", "value");
 *    .transaction.get("key");
 *  Object[] result = client.exec(transaction).get();
 *  // result contains: OK and "value"
 *  </pre>
 */
@AllArgsConstructor
public class Transaction extends BaseTransaction<Transaction> {
    @Override
    protected Transaction getThis() {
        return this;
    }
}
