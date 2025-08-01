﻿// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

/// <summary>
/// Represents a block of operations that will be sent to the server together.
/// This can be useful to reduce packet fragmentation on slow connections - it
/// can improve the time to get *all* the operations processed, with the trade-off
/// of a slower time to get the *first* operation processed; this is usually
/// a good thing. Unless this batch is a <b>transaction</b>, there is no guarantee
/// that these operations will be processed either contiguously or atomically by the server.<br />
/// See also <see cref="Pipeline.Batch" /> and  <see cref="Pipeline.ClusterBatch" />.
/// </summary>
public interface IBatch : IDatabaseAsync
{
    /// <summary>
    /// Execute the batch operation, sending all queued commands to the server.
    /// Note that this operation is neither synchronous nor truly asynchronous - it simply enqueues the buffered messages.
    /// To check on completion, you should check the individual responses.
    /// </summary>
    void Execute();
}
