// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide.Commands;

/// <summary>
/// Supports commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
internal interface IStringBaseCommands
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
    /// Overwrites part of the string stored at <paramref name="key" />, starting at the specified <paramref name="offset" />, 
    /// for the entire length of <paramref name="value" />. If the <paramref name="offset" /> is larger than the current 
    /// length of the string at <paramref name="key" />, the string will be padded with zero-bytes to make <paramref name="offset" /> fit. 
    /// Creates the <paramref name="key" /> if it doesn't exist.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// long length = await client.SetRange("key", 6, "GLIDE");
    /// // length is 11 if key was created, or the length of the modified string
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="key">The key of the string to update.</param>
    /// <param name="offset">The position in the string where <paramref name="value" /> should be written.</param>
    /// <param name="value">The string written with <paramref name="offset" />.</param>
    /// <returns>The length of the string stored at <paramref name="key" /> after it was modified.</returns>
    /// <see href="https://valkey.io/commands/setrange/">valkey.io</see>
    Task<long> SetRange(GlideString key, int offset, GlideString value);
}
