// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for batch operations.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=sorted-set#sorted-set">valkey.io</see>.
/// </summary>
public interface IBatchSortedSetCommands
{
// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands;

namespace Valkey.Glide.Pipeline;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for batch operations.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=sorted-set#sorted-set">valkey.io</see>.
/// </summary>
public interface IBatchSortedSetCommands
{
    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
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
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="when">Which condition to add the element under.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, member, score, When.NotExists);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, When when);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to add to the sorted set.</param>
    /// <param name="score">The score for the member.</param>
    /// <param name="when">Which condition to add the element under.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, member, score, SortedSetWhen.GreaterThan);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, ValkeyValue member, double score, SortedSetWhen when);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add to the sorted set.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, [new SortedSetEntry(member1, score1), new SortedSetEntry(member2, score2)]);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, SortedSetEntry[] values);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add to the sorted set.</param>
    /// <param name="when">Which condition to add the element under.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, values, When.NotExists);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, When when);

    /// <summary>
    /// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zadd"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="values">The entries (member-score pairs) to add to the sorted set.</param>
    /// <param name="when">Which condition to add the element under.</param>
    /// <returns>The batch instance for method chaining.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// batch.SortedSetAdd(key, values, SortedSetWhen.GreaterThan);
    /// </code>
    /// </example>
    /// </remarks>
    IBatch SortedSetAdd(ValkeyKey key, SortedSetEntry[] values, SortedSetWhen when);
}
}
