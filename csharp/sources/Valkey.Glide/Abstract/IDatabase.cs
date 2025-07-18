// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

/// <summary>
/// Describes functionality that is common to both standalone and cluster servers.<br />
/// See also <see cref="GlideClient" /> and <see cref="GlideClusterClient" />.
/// </summary>
public interface IDatabase : IDatabaseAsync
{
    /// <inheritdoc cref="IDatabaseAsync.ExecuteAsync(string, object[])"/>
    ValkeyResult Execute(string command, params object[] args);

    /// <inheritdoc cref="IDatabaseAsync.ExecuteAsync(string, ICollection{object}, CommandFlags)"/>
    ValkeyResult Execute(string command, ICollection<object> args, CommandFlags flags = CommandFlags.None);
}
