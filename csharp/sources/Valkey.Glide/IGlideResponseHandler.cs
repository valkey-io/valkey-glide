// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Commands.Abstraction;
using Valkey.Glide.InterOp;

namespace Valkey.Glide;

/// <summary>
/// Represents a handler for processing responses.
/// </summary>
/// <typeparam name="TResult">The type of the result returned by the response handler.</typeparam>
/// <seealso cref="IGlideCommand{TResult}"/>
/// <seealso cref="IGlideSerializer{TValue}"/>
public interface IGlideResponseHandler<TResult>
{
    /// <summary>
    /// Handles the specified client operation by processing a given value asynchronously.
    /// </summary>
    /// <param name="client">
    /// The client instance of type <see cref="IGlideClient"/> that is used for handling the operation.
    /// </param>
    /// <param name="value">
    /// The value of type Value that is being processed within the operation.
    /// </param>
    /// <param name="cancellationToken">
    /// A <see cref="CancellationToken"/> to observe while waiting for the operation to complete.
    /// </param>
    /// <returns>
    /// A <see cref="ValueTask{TResult}"/> promising to hold a <typeparamref name="TResult"/>
    /// that represents the result of the operation.
    /// </returns>
    ValueTask<TResult> HandleAsync(
        IGlideClient client,
        Value value,
        CancellationToken cancellationToken = default
    );
}
