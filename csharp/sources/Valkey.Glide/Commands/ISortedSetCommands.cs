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
    /// Adds members with their scores to the sorted set stored at key.
    /// If a member is already a part of the sorted set, its score is updated.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score of the member.</param>
    /// <param name="when">Indicates when this operation should be performed.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the member was added. <see langword="false"/> if the member already existed and the score was updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SortedSetAddAsync(key, "member1", 10.5);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Adds members with their scores to the sorted set stored at key.
    /// If a member is already a part of the sorted set, its score is updated.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">An array of <see cref="SortedSetEntry"/> objects representing the members and their scores to add.</param>
    /// <param name="when">Indicates when this operation should be performed.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements added to the sorted set, not including elements already existing for which the score was updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// var entries = new SortedSetEntry[] 
    /// {
    ///     new SortedSetEntry("member1", 10.5),
    ///     new SortedSetEntry("member2", 8.2)
    /// };
    /// long result = await client.SortedSetAddAsync(key, entries);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Increments the score of member in the sorted set stored at key by increment.
    /// If member does not exist in the sorted set, it is added with increment as its score (as if its previous score was 0.0).
    /// If key does not exist, a new sorted set with the specified member as its sole member is created.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to increment.</param>
    /// <param name="increment">The score to increment the member by.</param>
    /// <param name="when">Indicates when this operation should be performed.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The new score of the member, or <see langword="null"/> if the operation was aborted due to the specified conditions.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// double? result = await client.SortedSetIncrementAsync(key, "member1", 5.0);
    /// </code>
    /// </example>
    /// </remarks>
    Task<double?> SortedSetIncrementAsync(ValkeyKey key, ValkeyValue member, double increment, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None);
}
