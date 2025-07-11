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
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the specified member was not already present in the sorted set, else <see langword="false"/>.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SortedSetAddAsync(key, member, score, CommandFlags.None);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags flags);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="when">Which condition to add the element under (defaults to always).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the specified member was not already present in the sorted set, else <see langword="false"/>.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SortedSetAddAsync(key, member, score, When.NotExists);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="when">Which condition to add the element under (defaults to always).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the specified member was not already present in the sorted set, else <see langword="false"/>.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SortedSetAddAsync(key, member, score, SortedSetWhen.GreaterThan);
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add to the sorted set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements that were added to the sorted set, not including all the elements already present for which the score was updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetAddAsync(key, values, CommandFlags.None);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, CommandFlags flags);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add to the sorted set.</param>
    /// <param name="when">Which condition to add the element under (defaults to always).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements that were added to the sorted set, not including all the elements already present for which the score was updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetAddAsync(key, values, When.NotExists);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add to the sorted set.</param>
    /// <param name="when">Which condition to add the element under (defaults to always).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of elements that were added to the sorted set, not including all the elements already present for which the score was updated.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetAddAsync(key, [new SortedSetEntry(member1, score1), new SortedSetEntry(member2, score2)], SortedSetWhen.GreaterThan);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when = SortedSetWhen.Always, CommandFlags flags = CommandFlags.None);
}
