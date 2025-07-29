// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.ComponentModel;

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "Sorted Set Commands" group for standalone and cluster clients.
/// <br />
/// See more on <see href="https://valkey.io/commands/#sorted-set">valkey.io</see>.
/// </summary>
public interface ISortedSetCommands
{
    /// <inheritdoc cref="SortedSetAddAsync(ValkeyKey, ValkeyValue, double, SortedSetWhen, CommandFlags)" />
    [Browsable(false), EditorBrowsable(EditorBrowsableState.Never)]
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, CommandFlags flags);

    /// <inheritdoc cref="SortedSetAddAsync(ValkeyKey, ValkeyValue, double, SortedSetWhen, CommandFlags)" />
    [Browsable(false), EditorBrowsable(EditorBrowsableState.Never)]
    Task<bool> SortedSetAddAsync(ValkeyKey key, ValkeyValue member, double score, When when, CommandFlags flags = CommandFlags.None);

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

    /// <inheritdoc cref="SortedSetAddAsync(ValkeyKey, SortedSetEntry[], SortedSetWhen, CommandFlags)" />
    [Browsable(false), EditorBrowsable(EditorBrowsableState.Never)]
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, CommandFlags flags);

    /// <inheritdoc cref="SortedSetAddAsync(ValkeyKey, SortedSetEntry[], SortedSetWhen, CommandFlags)" />
    [Browsable(false), EditorBrowsable(EditorBrowsableState.Never)]
    Task<long> SortedSetAddAsync(ValkeyKey key, SortedSetEntry[] values, When when, CommandFlags flags = CommandFlags.None);

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
    /// Removes the specified member from the sorted set stored at key.
    /// Non existing members are ignored.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrem"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="member">The member to remove from the sorted set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns><see langword="true"/> if the member was removed. <see langword="false"/> if the member was not a member of the sorted set.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// bool result = await client.SortedSetRemoveAsync(key, "member1");
    /// </code>
    /// </example>
    /// </remarks>
    Task<bool> SortedSetRemoveAsync(ValkeyKey key, ValkeyValue member, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes the specified members from the sorted set stored at key.
    /// Non existing members are ignored.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrem"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="members">An array of members to remove from the sorted set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of members that were removed from the sorted set, not including non existing members.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetRemoveAsync(key, ["member1", "member2"]);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetRemoveAsync(ValkeyKey key, ValkeyValue[] members, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the sorted set cardinality (number of elements) of the sorted set stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zcard"/>
    /// <seealso href="https://valkey.io/commands/zcount"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="min">The min score to filter by (defaults to negative infinity).</param>
    /// <param name="max">The max score to filter by (defaults to positive infinity).</param>
    /// <param name="exclude">Whether to exclude <paramref name="min"/> and <paramref name="max"/> from the range check (defaults to both inclusive).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The cardinality (number of elements) of the sorted set, or 0 if key does not exist.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// // Get total cardinality
    /// long totalCount = await client.SortedSetLengthAsync(key);
    /// 
    /// // Count elements in score range
    /// long rangeCount = await client.SortedSetLengthAsync(key, 1.0, 10.0);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetLengthAsync(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the cardinality (number of elements) of the sorted set stored at key.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zcard"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	The number of elements in the sorted set.
    ///	If key does not exist, it is treated as an empty sorted set, and this command returns 0.
    ///	If key holds a value that is not a sorted set, an error is returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetCardAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetCardAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the number of members in the sorted set stored at key with scores between min and max score.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zcount"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="min">The minimum score to count from (defaults to negative infinity).</param>
    /// <param name="max">The maximum score to count up to (defaults to positive infinity).</param>
    /// <param name="exclude">Whether to exclude min and max from the range check (defaults to both inclusive).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>The number of members in the specified score range.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.SortedSetCountAsync(key, 1.0, 10.0);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> SortedSetCountAsync(ValkeyKey key, double min = double.NegativeInfinity, double max = double.PositiveInfinity, Exclude exclude = Exclude.None, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified range of elements in the sorted set stored at key by their index (rank).
    /// By default the elements are considered to be ordered from the lowest to the highest score.
    /// Both start and stop are zero-based indexes, where 0 is the first element, 1 is the next element and so on.<br/>
    /// To get the elements with their scores, <see cref="SortedSetRangeByRankWithScoresAsync" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrange"/>
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="start">The start index to get.</param>
    /// <param name="stop">The stop index to get.</param>
    /// <param name="order">The order to sort by (defaults to ascending).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of elements within the specified range.
    ///	If key does not exist, it is treated as an empty sorted set, and the command returns an empty array.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SortedSetRangeByRankAsync(key, 0, 10);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SortedSetRangeByRankAsync(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified range of elements with their scores in the sorted set stored at key by their index (rank).
    /// By default the elements are considered to be ordered from the lowest to the highest score.
    /// Both start and stop are zero-based indexes, where 0 is the first element, 1 is the next element and so on.<br/>
    /// To get the elements without their scores, <see cref="SortedSetRangeByRankAsync" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrange"/>
    /// <seealso href="https://redis.io/commands/zrevrange"/>.
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="start">The start index to get.</param>
    /// <param name="stop">The stop index to get.</param>
    /// <param name="order">The order to sort by (defaults to ascending).</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of elements and their scores within the specified range.
    ///	If key does not exist, it is treated as an empty sorted set, and the command returns an empty array.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// SortedSetEntry[] result = await client.SortedSetRangeByRankWithScoresAsync(key, 0, 10);
    /// </code>
    /// </example>
    /// </remarks>
    Task<SortedSetEntry[]> SortedSetRangeByRankWithScoresAsync(ValkeyKey key, long start = 0, long stop = -1, Order order = Order.Ascending, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified range of elements in the sorted set stored at key by their score.
    /// By default the elements are considered to be ordered from the lowest to the highest score.
    /// Start and stop are used to specify the min and max range for score values.
    /// To get the elements with their scores, <see cref="SortedSetRangeByScoreWithScoresAsync" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrange"/>
    /// <seealso href="https://redis.io/commands/zrevrange"/>.
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="start">The minimum score to filter by.</param>
    /// <param name="stop">The maximum score to filter by.</param>
    /// <param name="exclude">Which of start and stop to exclude (defaults to both inclusive).</param>
    /// <param name="order">The order to sort by (defaults to ascending).</param>
    /// <param name="skip">How many items to skip.</param>
    /// <param name="take">How many items to take.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of elements within the specified range.
    ///	If key does not exist, it is treated as an empty sorted set, and the command returns an empty array.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SortedSetRangeByScoreAsync(key, 1.0, 10.0);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SortedSetRangeByScoreAsync(
        ValkeyKey key,
        double start = double.NegativeInfinity,
        double stop = double.PositiveInfinity,
        Exclude exclude = Exclude.None,
        Order order = Order.Ascending,
        long skip = 0,
        long take = -1,
        CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified range of elements in the sorted set stored at key with their scores by their score.
    /// By default the elements are considered to be ordered from the lowest to the highest score.
    /// Start and stop are used to specify the min and max range for score values 
    /// To get the elements without their scores, <see cref="SortedSetRangeByScoreAsync" />.
    /// </summary>.
    /// <seealso href="https://valkey.io/commands/zrange"/>
    /// <seealso href="https://redis.io/commands/zrevrange"/>.
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="start">The minimum score to filter by.</param>
    /// <param name="stop">The maximum score to filter by.</param>
    /// <param name="exclude">Which of start and stop to exclude (defaults to both inclusive).</param>
    /// <param name="order">The order to sort by (defaults to ascending).</param>
    /// <param name="skip">How many items to skip.</param>
    /// <param name="take">How many items to take.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of elements and their scores within the specified range.
    ///	If key does not exist, it is treated as an empty sorted set, and the command returns an empty array.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// SortedSetEntry[] result = await client.SortedSetRangeByScoreWithScoresAsync(key, 1.0, 10.0);
    /// </code>
    /// </example>
    /// </remarks>
    Task<SortedSetEntry[]> SortedSetRangeByScoreWithScoresAsync(
        ValkeyKey key,
        double start = double.NegativeInfinity,
        double stop = double.PositiveInfinity,
        Exclude exclude = Exclude.None,
        Order order = Order.Ascending,
        long skip = 0,
        long take = -1,
        CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified range of elements in the sorted set stored at key by their lexicographical order.
    /// This command returns all the elements in the sorted set at key with a value between min and max.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrange"/>
    /// <seealso href="https://redis.io/commands/zrevrange"/>.
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="min">The min value to filter by.</param>
    /// <param name="max">The max value to filter by.</param>
    /// <param name="exclude">Which of min and max to exclude.</param>
    /// <param name="skip">How many items to skip.</param>
    /// <param name="take">How many items to take.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of elements within the specified range.
    ///	If key does not exist, it is treated as an empty sorted set, and the command returns an empty array.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SortedSetRangeByValueAsync(key, "a", "z", Exclude.None, 0, -1);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SortedSetRangeByValueAsync(
        ValkeyKey key,
        ValkeyValue min,
        ValkeyValue max,
        Exclude exclude,
        long skip,
        long take = -1,
        CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified range of elements in the sorted set stored at key by their lexicographical order.
    /// This command returns all the elements in the sorted set at key with a value between min and max.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/zrange"/>
    /// <seealso href="https://redis.io/commands/zrevrange"/>.
    /// <param name="key">The key of the sorted set.</param>
    /// <param name="min">The min value to filter by.</param>
    /// <param name="max">The max value to filter by.</param>
    /// <param name="exclude">Which of min and max to exclude (defaults to both inclusive).</param>
    /// <param name="order">The order to sort by (defaults to ascending).</param>
    /// <param name="skip">How many items to skip.</param>
    /// <param name="take">How many items to take.</param>
    /// <param name="flags">The flags to use for this operation. Currently flags are ignored.</param>
    /// <returns>
    ///	An array of elements within the specified range.
    ///	If key does not exist, it is treated as an empty sorted set, and the command returns an empty array.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.SortedSetRangeByValueAsync(key, "a", "z", order: Order.Descending);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> SortedSetRangeByValueAsync(
        ValkeyKey key,
        ValkeyValue min = default,
        ValkeyValue max = default,
        Exclude exclude = Exclude.None,
        Order order = Order.Ascending,
        long skip = 0,
        long take = -1,
        CommandFlags flags = CommandFlags.None);
}
