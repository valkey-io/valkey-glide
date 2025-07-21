// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "List Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/#list">valkey.io</see>.
/// </summary>
public interface IListBaseCommands
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
    Task<ValkeyValue[]> ListLeftPopAsync(ValkeyKey key, long count, CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Inserts all the specified values at the head of the list stored at <paramref name="key" />.
    /// </summary>
    /// <seealso href="https://valkey.io/commands/lpush"/>
    /// <param name="key">The key of the list.</param>
    /// <param name="values">The elements to insert at the head of the list stored at <paramref name="key" />.</param>
    /// <param name="when">When is not supported by GLIDE.</param>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>The length of the list after the push operation.</returns>
    /// <remarks>
    /// <example>
    /// <code>
    /// long result = await client.ListLeftPushAsync(key, values);
    /// </code>
    /// </example>
    /// </remarks>
    Task<long> ListLeftPushAsync(ValkeyKey key, ValkeyValue[] values, When when = When.Always, CommandFlags flags = CommandFlags.None);
}
