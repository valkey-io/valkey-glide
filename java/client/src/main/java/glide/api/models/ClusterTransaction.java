/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import lombok.AllArgsConstructor;

/**
 * Extends BaseTransaction class for cluster mode commands. Transactions allow the execution of a
 * group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client <code>exec</code>
 * command, in the order they were given. Each element in the array represents a command given to
 * the <code>Transaction</code>. The response for each command depends on the executed Redis
 * command. Specific response types are documented alongside each method.
 *
 * @example
 *     <pre>
 *  ClusterTransaction transaction = new ClusterTransaction();
 *    .set("key", "value");
 *    .get("key");
 *  ClusterValue[] result = client.exec(transaction, route).get();
 *  // result contains: OK and "value"
 *  </pre>
 */
@AllArgsConstructor
public class ClusterTransaction extends BaseTransaction<ClusterTransaction> {
    @Override
    protected ClusterTransaction getThis() {
        return this;
    }
}
