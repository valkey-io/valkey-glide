/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Set Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=set">Set Commands</a>
 */
public interface SetBaseCommands {
    /**
     * Adds specified members to the set stored at <code>key</code>. Specified members that are
     * already a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/sadd/">redis.io</a> for details.
     * @param key The <code>key</code> where members will be added to its set.
     * @param members A list of members to add to the set stored at <code>key</code>.
     * @return The number of members that were added to the set, excluding members already present.
     * @remarks If <code>key</code> does not exist, a new set is created before adding <code>members
     *     </code>.
     * @example
     *     <pre>{@code
     * Long result = client.sadd("my_set", new String[]{"member1", "member2"}).get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> sadd(String key, String[] members);

    /**
     * Removes specified members from the set stored at <code>key</code>. Specified members that are
     * not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/srem/">redis.io</a> for details.
     * @param key The <code>key</code> from which members will be removed.
     * @param members A list of members to remove from the set stored at <code>key</code>.
     * @return The number of members that were removed from the set, excluding non-existing members.
     * @remarks If <code>key</code> does not exist, it is treated as an empty set and this command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long result = client.srem("my_set", new String[]{"member1", "member2"}).get();
     * assert result == 2L;
     * }</pre>
     */
    CompletableFuture<Long> srem(String key, String[] members);

    /**
     * Retrieves all the members of the set value stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smembers/">redis.io</a> for details.
     * @param key The key from which to retrieve the set members.
     * @return A <code>Set</code> of all members of the set.
     * @remarks If <code>key</code> does not exist an empty set will be returned.
     * @example
     *     <pre>{@code
     * Set<String> result = client.smembers("my_set").get();
     * assert result.equals(Set.of("member1", "member2", "member3"));
     * }</pre>
     */
    CompletableFuture<Set<String>> smembers(String key);

    /**
     * Retrieves the set cardinality (number of elements) of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/scard/">redis.io</a> for details.
     * @param key The key from which to retrieve the number of set members.
     * @return The cardinality (number of elements) of the set, or 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Long result = client.scard("my_set").get();
     * assert result == 3L;
     * }</pre>
     */
    CompletableFuture<Long> scard(String key);

    /**
     * Checks whether each member is contained in the members of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/smismember/">redis.io</a> for details.
     * @param key The key of the set to check.
     * @param members A list of members to check for existence in the set.
     * @return An <code>array</code> of <code>Boolean</code> values, each indicating if the respective
     *     member exists in the set.
     * @example
     *     <pre>{@code
     * Boolean[] areMembers = client.smismembmer("my_set", new String[] { "a", "b", "c" }).get();
     * assert areMembers[0] && areMembers[1] && !areMembers[2]; // Only first two elements are present in "my_set"
     * }</pre>
     */
    CompletableFuture<Boolean[]> smismember(String key, String[] members);

    /**
     * Moves <code>member</code> from the set at <code>source</code> to the set at <code>destination
     * </code>, removing it from the source set. Creates a new destination set if needed. The
     * operation is atomic.
     *
     * @apiNote When in cluster mode, <code>source</code> and <code>destination</code> must map to the
     *     same <code>hash slot</code>.
     * @see <a href="https://redis.io/commands/smove/">redis.io</a> for details.
     * @param source The key of the set to remove the element from.
     * @param destination The key of the set to add the element to.
     * @param member The set element to move.
     * @return <code>true</code> on success, or <code>false</code> if the <code>source</code> set does
     *     not exist or the element is not a member of the source set.
     * @example
     *     <pre>{@code
     * Boolean moved = client.smove("set1", "set2", "element").get();
     * assert moved;
     * }</pre>
     */
    CompletableFuture<Boolean> smove(String source, String destination, String member);

    /**
     * Returns if <code>member</code> is a member of the set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/sismember/">redis.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check for existence in the set.
     * @return <code>true</code> if the member exists in the set, <code>false</code> otherwise. If
     *     <code>key</code> doesn't exist, it is treated as an <code>empty set</code> and the command
     *     returns <code>false</code>.
     * @example
     *     <pre>{@code
     * Boolean payload1 = client.sismember("mySet", "member1").get();
     * assert payload1; // Indicates that "member1" exists in the set "mySet".
     *
     * Boolean payload2 = client.sismember("mySet", "nonExistingMember").get();
     * assert !payload2; // Indicates that "nonExistingMember" does not exist in the set "mySet".
     * }</pre>
     */
    CompletableFuture<Boolean> sismember(String key, String member);

    /**
     * Computes the difference between the first set and all the successive sets in <code>keys</code>.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same <code>hash slot
     *     </code>.
     * @see <a href="https://redis.io/commands/sdiff/">redis.io</a> for details.
     * @param keys The keys of the sets to diff.
     * @return A <code>Set</code> of elements representing the difference between the sets.<br>
     *     If the a <code>key</code> does not exist, it is treated as an empty set.
     * @example
     *     <pre>{@code
     * Set<String> values = client.sdiff(new String[] {"set1", "set2"}).get();
     * assert values.contains("element"); // Indicates that "element" is present in "set1", but missing in "set2"
     * }</pre>
     */
    CompletableFuture<Set<String>> sdiff(String[] keys);

    /**
     * Stores the difference between the first set and all the successive sets in <code>keys</code>
     * into a new set at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same <code>hash slot</code>.
     * @see <a href="https://redis.io/commands/sdiffstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys of the sets to diff.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sdiffstore("mySet", new String[] { "set1", "set2" }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sdiffstore(String destination, String[] keys);

    /**
     * Gets the intersection of all the given sets.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same <code>hash slot
     *     </code>.
     * @see <a href="https://redis.io/commands/sinter/">redis.io</a> for details.
     * @param keys The keys of the sets.
     * @return A <code>Set</code> of members which are present in all given sets.<br>
     *     If one or more sets do not exist, an empty set will be returned.
     * @example
     *     <pre>{@code
     * Set<String> values = client.sinter(new String[] {"set1", "set2"}).get();
     * assert values.contains("element"); // Indicates that these sets have a common element
     *
     * Set<String> values = client.sinter(new String[] {"set1", "nonExistingSet"}).get();
     * assert values.size() == 0;
     * }</pre>
     */
    CompletableFuture<Set<String>> sinter(String[] keys);

    /**
     * Stores the members of the intersection of all given sets specified by <code>keys</code> into a
     * new set at <code>destination</code>.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same <code>hash slot
     *     </code>.
     * @see <a href="https://redis.io/commands/sinterstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sinterstore("mySet", new String[] { "set1", "set2" }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sinterstore(String destination, String[] keys);

    /**
     * Stores the members of the union of all given sets specified by <code>keys</code> into a new set
     * at <code>destination</code>.
     *
     * @apiNote When in cluster mode, <code>destination</code> and all <code>keys</code> must map to
     *     the same <code>hash slot</code>.
     * @see <a href="https://redis.io/commands/sunionstore/">redis.io</a> for details.
     * @param destination The key of the destination set.
     * @param keys The keys from which to retrieve the set members.
     * @return The number of elements in the resulting set.
     * @example
     *     <pre>{@code
     * Long length = client.sunionstore("mySet", new String[] { "set1", "set2" }).get();
     * assert length == 5L;
     * }</pre>
     */
    CompletableFuture<Long> sunionstore(String destination, String[] keys);
}
