// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Glide.Commands;

public interface IGenericClusterCommands
{
    // TODO example with route
    /// <summary>
    /// Executes a single command, without checking inputs. Every part of the command, including subcommands,
    /// should be added as a separate value in <paramref name="args"/>.
    /// See the <see href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey GLIDE Wiki</see>.
    /// for details on the restrictions and limitations of the custom command API.<br />
    /// The command will be routed automatically based on the passed command's default request policy, unless <paramref name="route"/>
    /// is provided, in which case the client will route the command to the nodes defined by <paramref name="route"/>.
    /// <para />
    /// This function should only be used for single-response commands. Commands that don't return complete response and awaits
    /// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
    /// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
    /// <example>
    /// <code>
    /// // Query all pub/sub clients
    /// object result = await client.CustomCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"]);
    /// </code>
    /// </example>
    /// </summary>
    /// <param name="args">A list including the command name and arguments for the custom command.</param>
    /// <param name="route">Specifies the routing configuration for the command. The client will route the command to the nodes defined by <c>route</c>.</param>
    /// <returns>The returning value depends on the executed command.</returns>
    Task<object?> CustomCommand(GlideString[] args, Route? route = null);
}
