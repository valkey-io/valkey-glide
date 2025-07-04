// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
public interface IStringBaseCommands
{
    /// <summary>
    /// Sets the given <paramref name="key" /> with the given <paramref name="value" />.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string result = await client.Set(key, value);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> to store.</param>
    /// <param name="value">The value to store with the given <paramref name="key" />.</param>
    /// <returns>A simple <c>"OK"</c> response.</returns>
    Task<string> Set(GlideString key, GlideString value);

    /// <summary>
    /// Gets the value associated with the given <paramref name="key" />, or <see langword="null" /> if no such <paramref name="key" /> exists.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// string value = await client.Get(key);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> to retrieve from the database.</param>
    /// <returns>
    /// If <paramref name="key" /> exists, returns the value of <paramref name="key" /> as a <see langword="string" />.<br/>
    /// Otherwise, returns <see langword="null" />.
    /// </returns>
    Task<GlideString?> Get(GlideString key);

    /// <summary>
    /// Returns the length of the string value stored at <paramref name="key" />.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// await client.Set("key", "GLIDE");
    /// long len = await client.Strlen("key");
    /// Console.WriteLine(len); // Output: 5
    /// 
    /// long len2 = await client.Strlen("non_existing_key");
    /// Console.WriteLine(len2); // Output: 0
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The <paramref name="key" /> to check its length.</param>
    /// <returns>
    /// The length of the string value stored at <paramref name="key" />.<br/>
    /// If <paramref name="key" /> does not exist, it is treated as an empty string, and the command returns <c>0</c>.
    /// </returns>
    /// <seealso href="https://valkey.io/commands/strlen/">valkey.io</seealso>
    Task<long> Strlen(GlideString key);
}
