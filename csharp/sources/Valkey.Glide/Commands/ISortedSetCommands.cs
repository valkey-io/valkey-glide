// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=sorted-set#sorted-set">valkey.io</see>.
/// </summary>
internal interface ISortedSetCommands
{
    /// <summary>
    /// ZAdd adds specified members with their scores to the sorted set stored at key.
    /// Specified members that are already a member of this sorted set get their score updated.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key where members will be added to its sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the specified member was not already present in the sorted set, else <see langword="false"/>.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SortedSetAddAsync(key, member, score);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags ignored = CommandFlags.None);

    /// <summary>
    /// ZAdd adds specified members with their scores to the sorted set stored at key.
    /// Specified members that are already a member of this sorted set get their score updated.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key where members will be added to its sorted set.</param>
    /// <param name="entries">The entries (member-score pairs) to add to the sorted set.</param>
    /// <param name="ignored">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements that were added to the sorted set, not including all the elements already present for which the score was updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetAddAsync(key, [new SortedSetEntry(member1, score1), new SortedSetEntry(member2, score2)]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] entries, CommandFlags ignored = CommandFlags.None);
}
