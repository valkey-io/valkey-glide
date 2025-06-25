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
}
