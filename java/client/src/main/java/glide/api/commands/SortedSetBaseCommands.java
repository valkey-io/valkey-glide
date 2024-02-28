/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.ZaddOptions;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sorted set Commands interface for both standalone and cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=sorted-set">Sorted set Commands</a>
 */
public interface SortedSetBaseCommands {
    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A mapping of members to their corresponding scores.
     * @param options The Zadd options.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     *     <br>
     * @example
     *     <pre>
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), ZaddOptions.builder().build(), false).get();
     * assert num == 2L //Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), ZaddOptions.builder().conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_EXISTS).build(), false).get();
     * assert num == 2L //Updates the scores of two existing members in the sorted set "existingSortedSet".
     * </pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZaddOptions options, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A mapping of members to their corresponding scores.
     * @param options The Zadd options.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), ZaddOptions.builder().build()).get();
     * assert num == 2L //Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), ZaddOptions.builder().conditionalChange(ZaddOptions.ConditionalChange.ONLY_IF_EXISTS).build()).get();
     * assert num == 2L //Updates the scores of two existing members in the sorted set "existingSortedSet".
     * </pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZaddOptions options);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A mapping of members to their corresponding scores.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set. <br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     *     <br>
     * @example
     *     <pre>
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), false).get();
     * assert num == 2L //Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     * </pre>
     */
    CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A mapping of members to their corresponding scores.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2)).get();
     * assert num == 2L //Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     * </pre>
     */
    CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @param options The Zadd options.
     * @return The score of the member.<br>
     *     If there was a conflict with the options, the operation aborts and <code>null</code> is
     *     returned.<br>
     * @example
     *     <pre>
     * Double num = client.zaddIncr("mySortedSet", member, 5.0, ZaddOptions.builder().build()).get();
     * assert num == 5.0
     * Double num = client.zaddIncr("existingSortedSet", member, 3.0, ZaddOptions.builder().updateOptions(ZaddOptions.UpdateOptions.SCORE_LESS_THAN_CURRENT).build()).get();
     * assert num == null
     * </pre>
     */
    CompletableFuture<Double> zaddIncr(
            String key, String member, double increment, ZaddOptions options);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was 0.0).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @return The score of the member.
     * @example
     *     <pre>
     * Double num = client.zaddIncr("mySortedSet", member, 5.0).get();
     * assert num == 5.0
     * </pre>
     */
    CompletableFuture<Double> zaddIncr(String key, String member, double increment);
}
