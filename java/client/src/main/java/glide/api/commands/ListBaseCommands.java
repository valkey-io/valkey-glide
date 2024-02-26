/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * List Commands interface for both standalone and cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=list">List Commands</a>
 */
public interface ListBaseCommands {
    /**
     * Inserts all the specified values at the head of the list stored at <code>key</code>. <code>
     * elements</code> are inserted one after the other to the head of the list, from the leftmost
     * element to the rightmost element. If <code>key</code> does not exist, it is created as an empty
     * list before performing the push operations.
     *
     * @see <a href="https://redis.io/commands/lpush/">redis.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to insert at the head of the list stored at <code>key</code>.
     * @return The length of the list after the push operations.
     * @example
     *     <pre>
     * Long pushCount1 = client.lpush("my_list", new String[] {"value1", "value2"}).get()
     * assert pushCount1 == 2L
     * Long pushCount2 = client.lpush("nonexistent_list", new String[] {"new_value"}).get()
     * assert pushCount2 == 1L
     * </pre>
     */
    CompletableFuture<Long> lpush(String key, String[] elements);

    /**
     * Removes and returns the first elements of the list stored at <code>key</code>. The command pops
     * a single element from the beginning of the list.
     *
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @return The value of the first element. <br>
     *     If <code>key</code> does not exist, null will be returned. <br>
     * @example
     *     <pre>
     * String value1 = client.lpop("my_list").get()
     * assert value1.equals("value1")
     * String value2 = client.lpop("non_exiting_key").get()
     * assert value2.equals(null)
     * </pre>
     */
    CompletableFuture<String> lpop(String key);

    /**
     * Removes and returns up to <code>count</code> elements of the list stored at <code>key</code>,
     * depending on the list's length.
     *
     * @see <a href="https://redis.io/commands/lpop/">redis.io</a> for details.
     * @param key The key of the list.
     * @param count The count of the elements to pop from the list.
     * @return An array of the popped elements will be returned depending on the list's length.<br>
     *     If <code>key</code> does not exist, null will be returned.<br>
     * @example
     *     <pre>
     * String[] values1 = client.lpopCount("my_list", 2).get()
     * assert values1.equals(new String[] {"value1", "value2"})
     * String[] values2 = client.lpopCount("non_exiting_key" , 7).get()
     * assert values2.equals(null)
     * </pre>
     */
    CompletableFuture<String[]> lpopCount(String key, long count);
}
