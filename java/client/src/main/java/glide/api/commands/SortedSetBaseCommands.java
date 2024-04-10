/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.RangeOptions.RangeByLex;
import glide.api.models.commands.RangeOptions.RangeByScore;
import glide.api.models.commands.RangeOptions.RangeQuery;
import glide.api.models.commands.RangeOptions.ScoredRangeQuery;
import glide.api.models.commands.ZaddOptions;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=sorted-set">Sorted Set Commands</a>
 */
public interface SortedSetBaseCommands {
    public static final String WITH_SCORES_REDIS_API = "WITHSCORES";
    public static final String WITH_SCORE_REDIS_API = "WITHSCORE";

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The Zadd options.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set.<br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     * @example
     *     <pre>{@code
     * ZaddOptions options = ZaddOptions.builder().conditionalChange(ONLY_IF_DOES_NOT_EXIST).build();
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), options, false).get();
     * assert num == 2L; // Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     *
     * options = ZaddOptions.builder().conditionalChange(ONLY_IF_EXISTS).build();
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), options, false).get();
     * assert num == 2L; // Updates the scores of two existing members in the sorted set "existingSortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZaddOptions options, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param options The Zadd options.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * ZaddOptions options = ZaddOptions.builder().conditionalChange(ONLY_IF_DOES_NOT_EXIST).build();
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), options).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     *
     * options = ZaddOptions.builder().conditionalChange(ONLY_IF_EXISTS).build();
     * Long num = client.zadd("existingSortedSet", Map.of("member1", 15.0, "member2", 5.5), options).get();
     * assert num == 0L; // No new members were added to the sorted set "existingSortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(
            String key, Map<String, Double> membersScoresMap, ZaddOptions options);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @param changed Modify the return value from the number of new elements added, to the total
     *     number of elements changed.
     * @return The number of elements added to the sorted set.<br>
     *     If <code>changed</code> is set, returns the number of elements updated in the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2), true).get();
     * assert num == 2L; // Indicates that two elements have been added or updated in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap, boolean changed);

    /**
     * Adds members with their scores to the sorted set stored at <code>key</code>.<br>
     * If a member is already a part of the sorted set, its score is updated.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A <code>Map</code> of members to their corresponding scores.
     * @return The number of elements added to the sorted set.
     * @example
     *     <pre>{@code
     * Long num = client.zadd("mySortedSet", Map.of("member1", 10.5, "member2", 8.2)).get();
     * assert num == 2L; // Indicates that two elements have been added to the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zadd(String key, Map<String, Double> membersScoresMap);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was <code>0.0</code>).<br>
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
     *     returned.
     * @example
     *     <pre>{@code
     * ZaddOptions options = ZaddOptions.builder().conditionalChange(ONLY_IF_DOES_NOT_EXIST).build();
     * Double num = client.zaddIncr("mySortedSet", member, 5.0, options).get();
     * assert num == 5.0;
     *
     * options = ZaddOptions.builder().updateOptions(SCORE_LESS_THAN_CURRENT).build();
     * Double num = client.zaddIncr("existingSortedSet", member, 3.0, options).get();
     * assert num == null;
     * }</pre>
     */
    CompletableFuture<Double> zaddIncr(
            String key, String member, double increment, ZaddOptions options);

    /**
     * Increments the score of member in the sorted set stored at <code>key</code> by <code>increment
     * </code>.<br>
     * If <code>member</code> does not exist in the sorted set, it is added with <code>
     * increment</code> as its score (as if its previous score was <code>0.0</code>).<br>
     * If <code>key</code> does not exist, a new sorted set with the specified member as its sole
     * member is created.
     *
     * @see <a href="https://redis.io/commands/zadd/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member A member in the sorted set to increment.
     * @param increment The score to increment the member.
     * @return The score of the member.
     * @example
     *     <pre>{@code
     * Double num = client.zaddIncr("mySortedSet", member, 5.0).get();
     * assert num == 5.0;
     * }</pre>
     */
    CompletableFuture<Double> zaddIncr(String key, String member, double increment);

    /**
     * Removes the specified members from the sorted set stored at <code>key</code>.<br>
     * Specified members that are not a member of this set are ignored.
     *
     * @see <a href="https://redis.io/commands/zrem/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members to remove from the sorted set.
     * @return The number of members that were removed from the sorted set, not including non-existing
     *     members.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long num1 = client.zrem("mySortedSet", new String[] {"member1", "member2"}).get();
     * assert num1 == 2L; // Indicates that two members have been removed from the sorted set "mySortedSet".
     *
     * Long num2 = client.zrem("nonExistingSortedSet", new String[] {"member1", "member2"}).get();
     * assert num2 == 0L; // Indicates that no members were removed as the sorted set "nonExistingSortedSet" does not exist.
     * }</pre>
     */
    CompletableFuture<Long> zrem(String key, String[] members);

    /**
     * Returns the cardinality (number of elements) of the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zcard/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return The number of elements in the sorted set.<br>
     *     If <code>key</code> does not exist, it is treated as an empty sorted set, and this command
     *     return <code>0</code>.
     * @example
     *     <pre>{@code
     * Long num1 = client.zcard("mySortedSet").get();
     * assert num1 == 3L; // Indicates that there are 3 elements in the sorted set "mySortedSet".
     *
     * Long num2 = client.zcard("nonExistingSortedSet").get();
     * assert num2 == 0L;
     * }</pre>
     */
    CompletableFuture<Long> zcard(String key);

    /**
     * Removes and returns up to <code>count</code> members with the lowest scores from the sorted set
     * stored at the specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from lowest to highest.
     * @return A map of the removed members and their scores, ordered from the one with the lowest
     *     score to the one with the highest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmax("mySortedSet", 2).get();
     * assert payload.equals(Map.of('member3', 7.5 , 'member2', 8.0)); // Indicates that 'member3' with a score of 7.5 and 'member2' with a score of 8.0 have been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmin(String key, long count);

    /**
     * Removes and returns the member with the lowest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmin/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmin("mySortedSet").get();
     * assert payload.equals(Map.of('member1', 5.0)); // Indicates that 'member1' with a score of 5.0 has been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmin(String key);

    /**
     * Removes and returns up to <code>count</code> members with the highest scores from the sorted
     * set stored at the specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param count Specifies the quantity of members to pop.<br>
     *     If <code>count</code> is higher than the sorted set's cardinality, returns all members and
     *     their scores, ordered from highest to lowest.
     * @return A map of the removed members and their scores, ordered from the one with the highest
     *     score to the one with the lowest.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmax("mySortedSet", 2).get();
     * assert payload.equals(Map.of('member2', 8.0, 'member3', 7.5)); // Indicates that 'member2' with a score of 8.0 and 'member3' with a score of 7.5 have been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmax(String key, long count);

    /**
     * Removes and returns the member with the highest score from the sorted set stored at the
     * specified <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zpopmax/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @return A map containing the removed member and its corresponding score.<br>
     *     If <code>key</code> doesn't exist, it will be treated as an empty sorted set and the
     *     command returns an empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * Map<String, Double> payload = client.zpopmax("mySortedSet").get();
     * assert payload.equals(Map.of('member1', 10.0)); // Indicates that 'member1' with a score of 10.0 has been removed from the sorted set.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zpopmax(String key);

    /**
     * Returns the score of <code>member</code> in the sorted set stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose score is to be retrieved.
     * @return The score of the member.<br>
     *     If <code>member</code> does not exist in the sorted set, <code>null</code> is returned.<br>
     *     If <code>key</code> does not exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Double num1 = client.zscore("mySortedSet", "member").get();
     * assert num1 == 10.5; // Indicates that the score of "member" in the sorted set "mySortedSet" is 10.5.
     *
     * Double num2 = client.zscore("mySortedSet", "nonExistingMember").get();
     * assert num2 == null;
     * }</pre>
     */
    CompletableFuture<Double> zscore(String key, String member);

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return An array of elements within the specified range. If <code>key</code> does not exist, it
     *     is treated as an empty sorted set, and the command returns an empty array.
     * @example
     *     <pre>{@code
     * RangeByScore query1 = new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20));
     * String[] payload1 = client.zrange("mySortedSet", query1, true).get(); // Returns members with scores between 10 and 20.
     * assert payload1.equals(new String[] {'member3', 'member2', 'member1'}); // Returns all members in descending order.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * String[] payload2 = client.zrange("mySortedSet", query2, false).get();
     * assert payload2.equals(new String[] {'member2', 'member3'}); // Returns members with scores within the range of negative infinity to 3, in ascending order.
     * }</pre>
     */
    CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery, boolean reverse);

    /**
     * Returns the specified range of elements in the sorted set stored at <code>key</code>.<br>
     * <code>ZRANGE</code> can perform different types of range queries: by index (rank), by the
     * score, or by lexicographical order.<br>
     * To get the elements with their scores, see {@link #zrangeWithScores}.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by lexicographical order, use {@link RangeByLex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return An of array elements within the specified range. If <code>key</code> does not exist, it
     *     is treated as an empty sorted set, and the command returns an empty array.
     * @example
     *     <pre>{@code
     * RangeByIndex query1 = new RangeByIndex(0, -1);
     * String[] payload1 = client.zrange("mySortedSet",query1).get();
     * assert payload1.equals(new String[] {'member1', 'member2', 'member3'}); // Returns all members in ascending order.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * String[] payload2 = client.zrange("mySortedSet", query2).get();
     * assert payload2.equals(new String[] {'member2', 'member3'}); // Returns members with scores within the range of negative infinity to 3, in ascending order.
     * }</pre>
     */
    CompletableFuture<String[]> zrange(String key, RangeQuery rangeQuery);

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @param reverse If true, reverses the sorted set, with index 0 as the element with the highest
     *     score.
     * @return A <code>Map</code> of elements and their scores within the specified range. If <code>
     *     key</code> does not exist, it is treated as an empty sorted set, and the command returns an
     *     empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * RangeByScore query1 = new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20));
     * Map<String, Double> payload1 = client.zrangeWithScores("mySortedSet", query1, true).get();
     * assert payload1.equals(Map.of('member2', 15.2, 'member1', 10.5)); // Returns members with scores between 10 and 20 (inclusive) with their scores.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * Map<String, Double> payload2 = client.zrangeWithScores("mySortedSet", query2, false).get();
     * assert payload2.equals(Map.of('member4', -2.0, 'member7', 1.5)); // Returns members with with scores within the range of negative infinity to 3, with their scores.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zrangeWithScores(
            String key, ScoredRangeQuery rangeQuery, boolean reverse);

    /**
     * Returns the specified range of elements with their scores in the sorted set stored at <code>key
     * </code>. Similar to {@link #zrange} but with a <code>WITHSCORE</code> flag.
     *
     * @see <a href="https://redis.io/commands/zrange/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param rangeQuery The range query object representing the type of range query to perform.<br>
     *     <ul>
     *       <li>For range queries by index (rank), use {@link RangeByIndex}.
     *       <li>For range queries by score, use {@link RangeByScore}.
     *     </ul>
     *
     * @return A <code>Map</code> of elements and their scores within the specified range. If <code>
     *     key</code> does not exist, it is treated as an empty sorted set, and the command returns an
     *     empty <code>Map</code>.
     * @example
     *     <pre>{@code
     * RangeByScore query1 = new RangeByScore(new ScoreBoundary(10), new ScoreBoundary(20));
     * Map<String, Double> payload1 = client.zrangeWithScores("mySortedSet", query1).get();
     * assert payload1.equals(Map.of('member1', 10.5, 'member2', 15.2)); // Returns members with scores between 10 and 20 (inclusive) with their scores.
     *
     * RangeByScore query2 = new RangeByScore(InfScoreBound.NEGATIVE_INFINITY, new ScoreBoundary(3));
     * Map<String, Double> payload2 = client.zrangeWithScores("mySortedSet", query2).get();
     * assert payload2.equals(Map.of('member4', -2.0, 'member7', 1.5)); // Returns members with with scores within the range of negative infinity to 3, with their scores.
     * }</pre>
     */
    CompletableFuture<Map<String, Double>> zrangeWithScores(String key, ScoredRangeQuery rangeQuery);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code>, with
     * scores ordered from low to high.<br>
     * To get the rank of <code>member</code> with its score, see {@link #zrankWithScore}.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return The rank of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Long num1 = client.zrank("mySortedSet", "member2").get();
     * assert num1 == 3L; // Indicates that "member2" has the second-lowest score in the sorted set "mySortedSet".
     *
     * Long num2 = client.zcard("mySortedSet", "nonExistingMember").get();
     * assert num2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Long> zrank(String key, String member);

    /**
     * Returns the rank of <code>member</code> in the sorted set stored at <code>key</code> with its
     * score, where scores are ordered from the lowest to highest.
     *
     * @see <a href="https://redis.io/commands/zrank/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param member The member whose rank is to be retrieved.
     * @return An array containing the rank (as <code>Long</code>) and score (as <code>Double</code>)
     *     of <code>member</code> in the sorted set.<br>
     *     If <code>key</code> doesn't exist, or if <code>member</code> is not present in the set,
     *     <code>null</code> will be returned.
     * @example
     *     <pre>{@code
     * Object[] result1 = client.zrankWithScore("mySortedSet", "member2").get();
     * assert ((Long)result1[0]) == 1L && ((Double)result1[1]) == 6.0; // Indicates that "member2" with score 6.0 has the second-lowest score in the sorted set "mySortedSet".
     *
     * Object[] result2 = client.zrankWithScore("mySortedSet", "nonExistingMember").get();
     * assert num2 == null; // Indicates that "nonExistingMember" is not present in the sorted set "mySortedSet".
     * }</pre>
     */
    CompletableFuture<Object[]> zrankWithScore(String key, String member);

    /**
     * Returns the scores associated with the specified <code>members</code> in the sorted set stored
     * at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/zmscore/">redis.io</a> for more details.
     * @param key The key of the sorted set.
     * @param members An array of members in the sorted set.
     * @return An <code>Array</code> of scores of the <code>members</code>.<br>
     *     If a <code>member</code> does not exist, the corresponding value in the <code>Array</code>
     *     will be <code>null</code>.
     * @example
     *     <pre>{@code
     * Double[] payload = client.zmscore(key1, new String[] {"one", "nonExistentMember", "three"}).get();
     * assert payload.equals(new Double[] {1.0, null, 3.0});
     * }</pre>
     */
    CompletableFuture<Double[]> zmscore(String key, String[] members);
}
