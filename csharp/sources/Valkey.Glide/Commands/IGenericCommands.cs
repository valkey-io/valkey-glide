// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Errors;
using static Valkey.Glide.Pipeline.Options;

namespace Valkey.Glide.Commands;

public interface IGenericCommands
{
    /// <summary>
    /// Executes a single command, without checking inputs. Every part of the command, including subcommands,
    /// should be added as a separate value in <paramref name="args" />.
    /// See the <see href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey GLIDE Wiki</see>.
    /// for details on the restrictions and limitations of the custom command API.
    /// <para />
    /// This function should only be used for single-response commands. Commands that don't return complete response and awaits
    /// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
    /// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
    /// </summary>
    /// <remarks>
    /// <example>
    /// <code>
    /// // Query all pub/sub clients
    /// object result = await client.CustomCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"]);
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="args">A list including the command name and arguments for the custom command.</param>
    /// <returns>The returning value depends on the executed command.</returns>
    Task<object?> CustomCommand(GlideString[] args);

    /// <summary>
    /// Executes a batch by processing the queued commands.
    /// <para />
    /// See the <see href="https://valkey.io/topics/transactions/">Valkey Transactions (Atomic Batches)</see>.<br />
    /// See the <see href="https://valkey.io/topics/pipelining/">Valkey Pipelines (Non-Atomic Batches)</see>.
    /// </summary>
    /// <remarks>
    /// <b>Behavior notes:</b><br />
    /// <b>Atomic Batches (Transactions):</b> If a transaction fails due to a <c>WATCH</c> command,
    /// <c>Exec</c> will return <see langword="null" />.
    /// <example>
    /// <code>
    /// // Example 1: Atomic Batch (Transaction)
    /// Batch batch = new Batch(true) // Atomic (Transaction)
    ///     .Set("key", "1")
    ///     .Incr("key")
    ///     .Get("key");
    ///
    /// var result = await client.Exec(batch, true);
    /// // Expected result: ["OK", 2, 2]
    /// </code>
    /// </example>
    /// <example>
    /// <code>
    /// // Example 2: Non-Atomic Batch (Pipeline)
    /// Batch batch = new Batch(false) // Non-Atomic (Pipeline)
    ///     .Set("key1", "value1")
    ///     .Set("key2", "value2")
    ///     .Get("key1")
    ///     .Get("key2");
    ///
    /// var result = await client.Exec(batch, true);
    /// // Expected result: ["OK", "OK", "value1", "value2"]
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="batch">A <see cref="Batch" /> object containing a list of commands to be executed.</param>
    /// <param name="raiseOnError">
    /// Determines how errors are handled within the batch response.
    /// <para />
    /// When set to <see langword="true" />, the first encountered error in the batch will be raised as an
    /// exception of type <see cref="RequestException" /> after all retries and reconnections have been
    /// executed.
    /// <para />
    /// When set to <see langword="false" />, errors will be included as part of the batch response, allowing
    /// the caller to process both successful and failed commands together. In this case, error details
    /// will be provided as instances of <see cref="RequestException" />.
    /// </param>
    /// <returns>An array of results, where each entry corresponds to a command’s execution result.</returns>
    Task<object?[]?> Exec(Batch batch, bool raiseOnError);

    /// <summary>
    /// Executes a batch by processing the queued commands.
    /// <para />
    /// <b>Routing Behavior:</b>
    /// <list type="bullet">
    ///   <item>
    ///     If a <see cref="Route" /> is specified in <see cref="ClusterBatchOptions" />, the entire batch is sent
    ///     to the specified node.
    ///   </item>
    ///   <item>
    ///     If no <see cref="Route" /> is specified:
    ///     <list type="bullet">
    ///       <item>
    ///         <b>Atomic batches (Transactions):</b> Routed to the slot owner of the
    ///         first key in the batch. If no key is found, the request is sent to a random node.
    ///       </item>
    ///       <item>
    ///         <b>Non-atomic batches (Pipelines):</b> Each command is routed to the node
    ///         owning the corresponding key's slot. If no key is present, routing follows the
    ///         command's request policy. Multi-node commands are automatically split and
    ///         dispatched to the appropriate nodes.
    ///       </item>
    ///     </list>
    ///   </item>
    /// </list>
    /// See the <see href="https://valkey.io/topics/transactions/">Valkey Transactions (Atomic Batches)</see>.<br />
    /// See the <see href="https://valkey.io/topics/pipelining/">Valkey Pipelines (Non-Atomic Batches)</see>.
    /// </summary>
    /// <remarks>
    /// <b>Behavior notes:</b><br />
    /// <b>Atomic Batches (Transactions):</b> If a transaction fails due to a <c>WATCH</c> command,
    /// <c>Exec</c> will return <see langword="null" />.
    /// <example>
    /// <code>
    /// // Example 1: Atomic Batch (Transaction) all keys must share the same hash slot
    /// BatchOptions options = new(
    ///     timeout: 1000, // Set a timeout of 1000 milliseconds
    ///     raiseOnError: false); // Do not raise an error on failure
    ///
    /// Batch batch = new Batch(true) // Atomic (Transaction)
    ///     .Set("key", "1")
    ///     .Incr("key")
    ///     .Get("key");
    ///
    /// var result = await client.Exec(batch, false, options);
    /// // Expected result: ["OK", 2, 2]
    /// </code>
    /// </example>
    /// <example>
    /// <code>
    /// // Example 2: Non-Atomic Batch (Pipeline)
    /// BatchOptions options = new(
    ///     timeout: 1000, // Set a timeout of 1000 milliseconds
    ///     raiseOnError: false); // Do not raise an error on failure
    ///
    /// Batch batch = new Batch(false) // Non-Atomic (Pipeline) keys may span different hash slots
    ///     .Set("key1", "value1")
    ///     .Set("key2", "value2")
    ///     .Get("key1")
    ///     .Get("key2");
    ///
    /// var result = await client.Exec(batch, false, options);
    /// // Expected result: ["OK", "OK", "value1", "value2"]
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="batch">A <see cref="Batch" /> object containing a list of commands to be executed.</param>
    /// <param name="raiseOnError">
    /// Determines how errors are handled within the batch response.
    /// <para />
    /// When set to <see langword="true" />, the first encountered error in the batch will be raised as an
    /// exception of type <see cref="RequestException" /> after all retries and reconnections have been
    /// executed.
    /// <para />
    /// When set to <see langword="false" />, errors will be included as part of the batch response, allowing
    /// the caller to process both successful and failed commands together. In this case, error details
    /// will be provided as instances of <see cref="RequestException" />.
    /// </param>
    /// <param name="options">A <see cref="BatchOptions" /> object containing execution options.</param>
    /// <returns>An array of results, where each entry corresponds to a command’s execution result.</returns>
    Task<object?[]?> Exec(Batch batch, bool raiseOnError, BatchOptions options);
}
