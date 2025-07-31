// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "List Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands#list">valkey.io</see>.
/// </summary>
public interface IListCommands
{
    /// <summary>
    /// Removes and returns the first elements of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The value of the first element.<br/>
    /// If <paramref name="key" /> does not exist, <see cref="ValkeyValue.Null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.ListLeftPopAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> ListLeftPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns up to <paramref name="count" /> elements of the list
    /// stored at <paramref name="key" />, depending on the list's length.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="count">The count of the elements to pop from the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>An array of the popped elements will be returned depending on the list's length.<br/>
    /// If <paramref name="key" /> does not exist, <see langword="null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.ListLeftPopAsync(key, 2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]?> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts the specified value at the head of the list stored at key.
    /// If key does not exist, it is created as an empty list before performing the push operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="value">The value to add to the head of the list.</param>
    /// <param name="when">Not supported. LPUSHX has not been implemented yet.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLeftPushAsync(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the head of the list stored at <paramref name="key" />. Elements are inserted one
    /// after the other to the head of the list, from the leftmost element to the rightmost element. If key does not exist, it
    /// is created as an empty list before performing the push operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the head of the list stored at <paramref name="key" />.</param>
    /// <param name="when">Not supported. LPUSHX has not been implemented yet.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLeftPushAsync(key, values, When.Always);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the head of the list stored at <paramref name="key" />. Elements are inserted one
    /// after the other to the head of the list, from the leftmost element to the rightmost element. If key does not exist, it
    /// is created as an empty list before performing the push operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the head of the list stored at <paramref name="key" />.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLeftPushAsync(key, values, CommandFlags.None);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags);

    /// <summary>
    /// Inserts the specified value at the tail of the list stored at key.
    /// If key does not exist, it is created as an empty list before performing the push operation.
    /// </summary>
    /// <param name="key">The key of the list.</param>
    /// <param name="value">The value to add to the tail of the list.</param>
    /// <param name="when">Not supported. RPUSHX has not been implemented yet.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <seealso href="https://valkey.io/commands/rpush"/>
    /// </remarks>
    Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue value, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the tail of the list stored at key.
    /// elements are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
    /// If key does not exist, it is created as an empty list before performing the push operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the tail of the list stored at <paramref name="key" />.</param>
    /// <param name="when">Not supported. RPUSHX has not been implemented yet.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListRightPushAsync(key, values, When.Always);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the tail of the list stored at key.
    /// elements are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
    /// If key does not exist, it is created as an empty list before performing the push operation.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the tail of the list stored at <paramref name="key" />.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListRightPushAsync(key, values, CommandFlags.None);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListRightPushAsync(ValkeyKey key, ValkeyValue[] values, CommandFlags flags);

    /// <summary>
    /// Removes and returns the last elements of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The value of the last element.<br/>
    /// If <paramref name="key" /> does not exist, <see cref="ValkeyValue.Null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue result = await client.ListRightPopAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue> ListRightPopAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes and returns up to <paramref name="count" /> elements from the tail of the list 
    /// stored at <paramref name="key" />, depending on the list's length.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/rpop"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="count">The count of the elements to pop from the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>An array of the popped elements will be returned depending on the list's length.<br/>
    /// If <paramref name="key" /> does not exist, <see langword="null"/> will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.ListRightPopAsync(key, 2);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]?> ListRightPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the length of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/llen"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>
    /// The length of the list at <paramref name="key" />.
    /// If <paramref name="key" /> does not exist, it is interpreted as an empty list and 0 is returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLengthAsync(key);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLengthAsync(ValkeyKey key, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Removes the first <paramref name="count" /> occurrences of elements equal to <paramref name="value" /> from the list stored at <paramref name="key" />.
    /// The <paramref name="count" /> argument influences the operation in the following ways:
    /// <list type="bullet">
    ///     <item><paramref name="count" /> &gt; 0: Remove elements equal to <paramref name="value" /> moving from head to tail.</item>
    ///     <item><paramref name="count" /> &lt; 0: Remove elements equal to <paramref name="value" /> moving from tail to head.</item>
    ///     <item><paramref name="count" /> = 0: Remove all elements equal to <paramref name="value" />.</item>
    /// </list>
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lrem"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="value">The value to remove from the list.</param>
    /// <param name="count">The count of the occurrences of elements equal to value to remove.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>
    ///	The number of the removed elements.
    ///	If <paramref name="key" /> does not exist, 0 is returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListRemoveAsync(key, value, count);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListRemoveAsync(ValkeyKey key, ValkeyValue value, long count = 0, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Trims an existing list so that it will contain only the specified range of elements specified.
    /// The offsets <paramref name="start" /> and <paramref name="stop" />  are zero-based indexes, with 0 being the first element of the list, 1 being the next element
    /// and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being
    /// the last element of the list, -2 being the penultimate, and so on.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/ltrim"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="start">The start index of the list to trim to.</param>
    /// <param name="stop">The end index of the list to trim to.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>
    /// A task that represents the asynchronous operation.
    ///	If <paramref name="start" /> exceeds the end of the list, or if <paramref name="start" /> is greater than <paramref name="stop" /> , the list is emptied
    ///	and the key is removed.
    ///	If <paramref name="stop" />  exceeds the actual end of the list, it will be treated like the last element of the list.
    ///	If key does not exist, the command will return without any changes to the database.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// _ = await client.ListTrimAsync(key, start, stop);
    /// </code>
    /// </example>
    /// </remarks>
    Task ListTrimAsync(ValkeyKey key, long start, long stop, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Returns the specified elements of the list stored at <paramref name="key" />.
    /// The offsets <paramref name="start" /> and <paramref name="stop" /> are zero-based indexes, with 0 being the first element of the list (the head of the list), 1 being the next element and so on.
    /// These offsets can also be negative numbers indicating offsets starting at the end of the list, with -1 being the last element of the list, -2 being the penultimate, and so on.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lrange"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="start">The starting point of the range.</param>
    /// <param name="stop">The end of the range.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>
    ///	Array of <see cref="ValkeyValue"/>s in the specified range.
    ///	If <paramref name="start" /> exceeds the end of the list, or if <paramref name="start" /> is greater than <paramref name="stop" />, an empty array will be returned.
    ///	If <paramref name="stop" /> exceeds the actual end of the list, the range will stop at the actual end of the list.
    ///	If <paramref name="key" /> does not exist an empty array will be returned.
    /// </returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// ValkeyValue[] result = await client.ListRangeAsync(key, start, stop);
    /// </code>
    /// </example>
    /// </remarks>
    Task<ValkeyValue[]> ListRangeAsync(ValkeyKey key, long start = 0, long stop = -1, CommandFlags flags = CommandFlags.None);
}
