// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for batch operations.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=sorted-set#sorted-set">valkey.io</see>.
/// </summary>
public interface IBatchSortedSetCommands
{
    /// <summary>
    /// ZAdd adds specified members with their scores to the sorted set stored at key.
    /// Specified members that are already a member of this sorted set get their score updated.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key where members will be added to its sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, member, score);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, ValkeyValue member, double score);

    /// <summary>
    /// ZAdd adds specified members with their scores to the sorted set stored at key.
    /// Specified members that are already a member of this sorted set get their score updated.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key where members will be added to its sorted set.</param>
    /// <param name="entries">The entries (member-score pairs) to add to the sorted set.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, [new SortedSetEntry(member1, score1), new SortedSetEntry(member2, score2)]);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, SortedSetEntry[] entries);
}
