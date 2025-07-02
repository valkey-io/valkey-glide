// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Options;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=sorted-set">valkey.io</see>.
/// </summary>
internal interface ISortedSetBaseCommands
{
    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ZAdd("my_sorted_set", new Dictionary&lt;string, double&gt; { { "member1", 1.0 }, { "member2", 2.0 } });
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="membersScoreMap">A dictionary of members to their scores.</param>
    /// <returns>The number of members added to the sorted set.</returns>
    Task<long> ZAdd(GlideString key, Dictionary<GlideString, double> membersScoreMap);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// var options = new ZAddOptions().SetConditionalChange(ConditionalSet.OnlyIfExists);
    /// long result = await client.ZAdd("my_sorted_set", new Dictionary&lt;string, double&gt; { { "member1", 1.0 } }, options);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="membersScoreMap">A dictionary of members to their scores.</param>
    /// <param name="options">The options for the ZAdd command.</param>
    /// <returns>The number of members added to the sorted set. If <c>Changed</c> is set, the number of members that were updated.</returns>
    Task<long> ZAdd(GlideString key, Dictionary<GlideString, double> membersScoreMap, ZAddOptions options);

    /// <summary>
    /// Removes one or more members from a sorted set.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ZRem("my_sorted_set", new GlideString[] { "member1", "member2" });
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="members">An array of members to remove from the sorted set.</param>
    /// <returns>The number of members that were removed from the sorted set, not including non-existing members.
    /// If <paramref name="key"/> does not exist, it is treated as an empty sorted set, and this command returns <c>0</c>.</returns>
    /// <see href="https://valkey.io/commands/zrem/">valkey.io</see>
    Task<long> ZRem(GlideString key, GlideString[] members);

    /// <summary>
    /// Returns the specified range of elements in the sorted set stored at <paramref name="key"/>.
    /// ZRANGE can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// // Get elements by index range
    /// GlideString[] result1 = await client.ZRange("my_sorted_set", new RangeByIndex(0, 2));
    /// 
    /// // Get elements by score range
    /// GlideString[] result2 = await client.ZRange("my_sorted_set", new RangeByScore(ScoreBoundary.Inclusive(1.0), ScoreBoundary.Inclusive(3.0)));
    /// 
    /// // Get elements by lexicographical range
    /// GlideString[] result3 = await client.ZRange("my_sorted_set", new RangeByLex(LexBoundary.Inclusive("a"), LexBoundary.Inclusive("c")));
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="rangeQuery">The range query object representing the type of range query to perform.</param>
    /// <returns>An array of elements within the specified range.
    /// If <paramref name="key"/> does not exist, it is treated as an empty sorted set, and the command returns an empty array.</returns>
    /// <see href="https://valkey.io/commands/zrange/">valkey.io</see>
    Task<GlideString[]> ZRange(GlideString key, IZRangeQuery rangeQuery);

    /// <summary>
    /// Returns the specified range of elements with their scores in the sorted set stored at <paramref name="key"/>.
    /// ZRANGE can perform different types of range queries: by index (rank) or by the score.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// // Get elements with scores by index range
    /// MemberAndScore[] result1 = await client.ZRangeWithScores("my_sorted_set", new RangeByIndex(0, 2));
    /// 
    /// // Get elements with scores by score range
    /// MemberAndScore[] result2 = await client.ZRangeWithScores("my_sorted_set", new RangeByScore(ScoreBoundary.Inclusive(1.0), ScoreBoundary.Inclusive(3.0)));
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="rangeQuery">The range query object representing the type of range query to perform.</param>
    /// <returns>An array of elements and their scores within the specified range.
    /// If <paramref name="key"/> does not exist, it is treated as an empty sorted set, and the command returns an empty array.</returns>
    /// <see href="https://valkey.io/commands/zrange/">valkey.io</see>
    Task<MemberAndScore[]> ZRangeWithScores(GlideString key, IZRangeWithScoresQuery rangeQuery);
}
