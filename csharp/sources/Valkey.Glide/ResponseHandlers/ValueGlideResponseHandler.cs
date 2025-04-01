// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp;

namespace Valkey.Glide.ResponseHandlers;

/// <summary>
/// Represents a handler that just returns the <see cref="Value"/> as-is.
/// </summary>
public struct ValueGlideResponseHandler : IGlideResponseHandler<Value>
{
    /// <inheritdoc/>
    public ValueTask<Value> HandleAsync(
        IGlideClient client,
        Value value,
        CancellationToken cancellationToken = default
    ) => ValueTask.FromResult(value);
}
