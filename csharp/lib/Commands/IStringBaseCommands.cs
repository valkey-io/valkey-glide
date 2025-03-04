// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Glide.Commands;

/// <summary>
/// Supports commands for the "String Commands" group for standalone and cluster clients.
/// <br/>
/// See more on <see href="https://valkey.io/commands/?group=string">valkey.io</see>.
/// </summary>
internal interface IStringBaseCommands
{
    /// <summary>
    /// Sets the given <paramref name="key"/> with the given <paramref name="value"/>.
    /// <example>
    /// <code>
    /// string result = await client.Set(key, value);
    /// </code>
    /// </example>
    /// </summary>
    /// <param name="key">The <paramref name="key"/> to store.</param>
    /// <param name="value">The value to store with the given <paramref name="key"/>.</param>
    /// <returns>A simple <c>"OK"</c> response.</returns>
    Task<string?> Set(string key, string value);

    /// <summary>
    /// Gets the value associated with the given <paramref name="key"/>, or <see langword="null"/> if no such <paramref name="key"/> exists.
    /// <example>
    /// <code>
    /// string value = await client.Get(key);
    /// </code>
    /// </example>
    /// </summary>
    /// <param name="key">The <paramref name="key"/> to retrieve from the database.</param>
    /// <returns>
    /// If <paramref name="key"/> exists, returns the value of <paramref name="key"/> as a <see langword="string"/>.<br/>
    /// Otherwise, returns <see langword="null"/>.
    /// </returns>
    Task<string?> Get(string key);
}
