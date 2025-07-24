// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

/// <summary>
/// Represents a group of operations that will be sent to the server as a single unit,
/// and processed on the server as a single unit. Transactions can also include constraints
/// (implemented via <c>WATCH</c>), but note that constraint checking involves will (very briefly)
/// block the connection, since the transaction cannot be correctly committed (<c>EXEC</c>),
/// aborted (<c>DISCARD</c>) or not applied in the first place (<c>UNWATCH</c>) until the responses from
/// the constraint checks have arrived.
/// See also <see cref="Pipeline.Batch" /> and  <see cref="Pipeline.ClusterBatch" />.
/// </summary>
/// <remarks>
/// <para>Note that on a cluster, it may be required that all keys involved in the transaction (including constraints) are in the same hash-slot.</para>
/// <para><seealso href="https://valkey.io/topics/transactions/"/></para>
/// </remarks>
public interface ITransaction : IBatch
{
    /// <summary>
    /// Adds a precondition for this transaction.
    /// </summary>
    /// <param name="condition">The condition to add to the transaction.</param>
    ConditionResult AddCondition(Condition condition);

    /// <summary>
    /// Execute the batch operation, sending all queued commands to the server.
    /// </summary>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>
    /// <see langword="true" /> if a transaction was applied or
    /// <see langword="false" /> if a transaction failed due to a <c>WATCH</c> command.
    /// </returns>
    bool Execute(CommandFlags flags = CommandFlags.None);

    /// <summary>
    /// Execute the batch operation, sending all queued commands to the server.
    /// </summary>
    /// <param name="flags">Command flags are not supported by GLIDE.</param>
    /// <returns>
    /// <see langword="true" /> if a transaction was applied or
    /// <see langword="false" /> if a transaction failed due to a <c>WATCH</c> command.
    /// </returns>
    Task<bool> ExecuteAsync(CommandFlags flags = CommandFlags.None);
}
