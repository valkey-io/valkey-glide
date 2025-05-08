// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Pipeline;

using static Valkey.Glide.Errors;
using static Valkey.Glide.Pipeline.Options;

namespace Valkey.Glide.Commands;

public interface IGenericClusterCommands
{
    /// <summary>
    /// Executes a single command, without checking inputs. Every part of the command, including subcommands,
    /// should be added as a separate value in <paramref name="args" />.
    /// See the <see href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey GLIDE Wiki</see>.
    /// for details on the restrictions and limitations of the custom command API.<br />
    /// The command will be routed automatically based on the passed command's default request policy.
    /// <para />
    /// This function should only be used for single-response commands. Commands that don't return complete response and awaits
    /// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
    /// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
    /// <example>
    /// <code>
    /// // Query all pub/sub clients
    /// ClusterValue&lt;object?&gt; result = await client.CustomCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"]);
    /// GlideString response = (result.SingleValue as GlideString)!;
    /// </code>
    /// </example>
    /// <example>
    /// <code>
    /// // Query all pub/sub clients on all nodes
    /// object result = await client.CustomCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"], Route.AllNodes);
    /// </code>
    /// </example>
    /// </summary>
    /// <remarks>
    /// This API returns all <see langword="string" /> data as <see cref="GlideString" />.
    /// </remarks>
    /// <param name="args">A list includes the command name and arguments for the custom command.</param>
    /// <returns>The returning value depends on the executed command.</returns>
    Task<ClusterValue<object?>> CustomCommand(GlideString[] args);

    /// <summary>
    /// Executes a single command, without checking inputs. Every part of the command, including subcommands,
    /// should be added as a separate value in <paramref name="args"/>.
    /// See the <see href="https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command">Valkey GLIDE Wiki</see>.
    /// for details on the restrictions and limitations of the custom command API.<br />
    /// The command will be routed to the nodes defined by <paramref name="route"/>.
    /// <para />
    /// This function should only be used for single-response commands. Commands that don't return complete response and awaits
    /// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
    /// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
    /// <example>
    /// <code>
    /// // Query all pub/sub clients
    /// Dictionary&lt;string, object?&gt; result = (await client.CustomCommand(["CLIENT", "LIST", "TYPE", "PUBSUB"], Route.AllNodes)).MultiValue;
    /// foreach (var pair in result)
    /// {
    ///     Console.WriteLine($"Response from {pair.Key}: {pair.Value});
    /// }
    /// </code>
    /// </example>
    /// </summary>
    /// <remarks>
    /// This API returns all <see langword="string" /> data as <see cref="GlideString" />.
    /// </remarks>
    /// <param name="args">A list including the command name and arguments for the custom command.</param>
    /// <param name="route">Specifies the routing configuration for the command. The client will route the command to the nodes defined by <c>route</c>.</param>
    /// <returns>The returning value depends on the executed command.</returns>
    Task<ClusterValue<object?>> CustomCommand(GlideString[] args, Route route);

    /// <summary>
    /// Executes a batch by processing the queued commands.
    /// <para />
    /// <b>Routing Behavior:</b>
    /// <list type="bullet">
    ///   <item>
    ///     <b>For atomic batches (Transactions):</b>
    ///     <list type="bullet">
    ///       <item>
    ///         The transaction will be routed to the slot owner of the first key found in the batch.
    ///       </item>
    ///       <item>
    ///         If no key is found, the request will be sent to a random node.
    ///       </item>
    ///     </list>
    ///   </item>
    ///   <item>
    ///     <b>For non-atomic batches (Pipelines):</b>
    ///     <list type="bullet">
    ///       <item>
    ///         Each command will be routed to the node that owns the corresponding key's slot. If
    ///         no key is present, the routing will follow the default policy for the command.
    ///       </item>
    ///       <item>
    ///         Multi-node commands will be automatically split and sent to the respective nodes.
    ///       </item>
    ///     </list>
    ///   </item>
    /// </list>
    /// See the <see href="https://valkey.io/topics/transactions/">Valkey Transactions (Atomic Batches)</see>.<br />
    /// See the <see href="https://valkey.io/topics/pipelining/">Valkey Pipelines (Non-Atomic Batches)</see>.
    /// </summary>
    /// <remarks>
    /// <b>Behavior notes:</b><br />
    /// <b>Atomic Batches (Transactions):</b> All key-based commands must map to the
    /// same hash slot. If keys span different slots, the transaction will fail.<br />
    /// If a transaction fails due to a <c>WATCH</c> command, <c>Exec</c> will return <see langword="null" />.
    /// <example>
    /// <code>
    /// // Example 1: Atomic Batch (Transaction)
    /// ClusterBatch batch = new ClusterBatch(true) // Atomic (Transaction)
    ///     .Set("key", "1")
    ///     .Incr("key")
    ///     .Get("key");
    ///
    /// var result = await clusterClient.Exec(batch, true);
    /// // Expected result: ["OK", 2, 2]
    /// </code>
    /// </example>
    /// <example>
    /// <code>
    /// // Example 2: Non-Atomic Batch (Pipeline)
    /// ClusterBatch batch = new ClusterBatch(false) // Non-Atomic (Pipeline)
    ///     .Set("key1", "value1")
    ///     .Set("key2", "value2")
    ///     .Get("key1")
    ///     .Get("key2");
    ///
    /// var result = await clusterClient.Exec(batch, true);
    /// // Expected result: ["OK", "OK", "value1", "value2"]
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="batch">A <see cref="ClusterBatch" /> object containing a list of commands to be executed.</param>
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
    Task<object?[]?> Exec(ClusterBatch batch, bool raiseOnError);

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
    /// <b>Atomic Batches (Transactions):</b> All key-based commands must map to the
    /// same hash slot. If keys span different slots, the transaction will fail.<br />
    /// If a transaction fails due to a <c>WATCH</c> command, <c>Exec</c> will return <see langword="null" />.
    /// <para />
    /// <b>Retry and Redirection:</b><br />
    /// <list type="bullet">
    ///   <item>
    ///     If a redirection error occurs:
    ///     <list type="bullet">
    ///       <item>
    ///         <b>Atomic batches (Transactions):</b> The entire transaction will be redirected.
    ///       </item>
    ///       <item>
    ///         <b>Non-atomic batches:</b> Only commands that encountered redirection errors will be redirected.
    ///       </item>
    ///     </list>
    ///   </item>
    ///   <item>
    ///     Retries for failures will be handled according to the configured <see cref="ClusterBatchRetryStrategy" />.
    ///   </item>
    /// </list>
    /// <example>
    /// <code>
    /// // Example 1: Atomic Batch (Transaction) all keys must share the same hash slot
    /// ClusterBatchOptions options = new(
    ///     timeout: 1000, // Set a timeout of 1000 milliseconds
    ///     raiseOnError: false); // Do not raise an error on failure
    ///
    /// ClusterBatch batch = new ClusterBatch(true) // Atomic (Transaction)
    ///     .Set("key", "1")
    ///     .Incr("key")
    ///     .Get("key");
    ///
    /// var result = await clusterClient.Exec(batch, false, options);
    /// // Expected result: ["OK", 2, 2]
    /// </code>
    /// </example>
    /// <example>
    /// <code>
    /// // Example 2: Non-Atomic Batch (Pipeline)
    /// ClusterBatchOptions options = new(retryStrategy: new(retryServerError: true, retryConnectionError: false));
    ///
    /// ClusterBatch batch = new ClusterBatch(false) // Non-Atomic (Pipeline) keys may span different hash slots
    ///     .Set("key1", "value1")
    ///     .Set("key2", "value2")
    ///     .Get("key1")
    ///     .Get("key2");
    ///
    /// var result = await clusterClient.Exec(batch, false, options);
    /// // Expected result: ["OK", "OK", "value1", "value2"]
    /// </code>
    /// </example>
    /// </remarks>
    /// <param name="batch">A <see cref="ClusterBatch" /> object containing a list of commands to be executed.</param>
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
    /// <param name="options">A <see cref="ClusterBatchOptions" /> object containing execution options.</param>
    /// <returns>An array of results, where each entry corresponds to a command’s execution result.</returns>
    Task<object?[]?> Exec(ClusterBatch batch, bool raiseOnError, ClusterBatchOptions options);
}
