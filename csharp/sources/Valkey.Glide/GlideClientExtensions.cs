// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Runtime.CompilerServices;
using Valkey.Glide.Commands.Abstraction;

namespace Valkey.Glide;

/// <summary>
/// Provides extension methods for interacting with instances of the <see cref="IGlideClient"/> interface.
/// </summary>
public static class GlideClientExtensions
{
    /// <summary>
    /// Executes the specified command asynchronously using the provided <see cref="IGlideClient"/> instance.
    /// </summary>
    /// <remarks>
    /// This is a utility method that effectively reverses the call order from <c>command.ExecuteAsync(client)</c>
    /// to <c>client.ExecuteAsync(command)</c>
    /// </remarks>
    /// <typeparam name="TCommand">The type of the command which implements the <see cref="IGlideCommand"/> interface.</typeparam>
    /// <param name="glideClient">The instance of <see cref="IGlideClient"/> to execute the command on.</param>
    /// <param name="command">The command to be executed.</param>
    /// <returns>A task representing the asynchronous operation, containing the result of the command execution as a <see cref="Valkey.Glide.InterOp.Value"/>.</returns>
    [MethodImpl(MethodImplOptions.AggressiveInlining | MethodImplOptions.AggressiveOptimization)]
    public static Task<TResult> ExecuteAsync<TResult>(
        this IGlideClient glideClient,
        IGlideCommand<TResult> command
    )
        => command.ExecuteAsync(glideClient);
}
