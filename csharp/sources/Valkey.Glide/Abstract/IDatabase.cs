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

    /// <summary>
    /// Allows creation of a group of operations that will be sent to the server as a single unit,
    /// but which may or may not be processed on the server contiguously.
    /// </summary>
    /// <param name="asyncState">The async state is not supported by GLIDE.</param>
    /// <returns>The created batch.</returns>
    IBatch CreateBatch(object? asyncState = null);

    /// <summary>
    /// Allows creation of a group of operations that will be sent to the server as a single unit,
    /// and processed on the server as a single unit.
    /// </summary>
    /// <param name="asyncState">The async state is not supported by GLIDE.</param>
    /// <returns>The created transaction.</returns>
    ITransaction CreateTransaction(object? asyncState = null);
}
