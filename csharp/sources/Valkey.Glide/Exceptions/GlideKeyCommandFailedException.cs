// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Exceptions;

namespace Valkey.Glide.Exceptions;

/// <summary>
/// Represents an exception specifically related to a key-based error in the Glide client.
/// </summary>
/// <remarks>
/// The <see cref="GlideKeyException"/> serves as an abstraction for exceptions that occur
/// during operations involving keys within the Glide client. This <see langword="class"/> inherits from
/// the <see cref="GlideException"/> base <see langword="class"/> and provides additional information
/// about the <paramref name="key"/> that caused the error.
/// </remarks>
/// <param name="message">A detailed message explaining the error.</param>
/// <param name="key">The key involved in the exception.</param>
/// <seealso cref="GlideCommandFailedException"/>
/// <seealso cref="GlideException"/>
/// <seealso cref="Value"/>
public abstract class GlideKeyCommandFailedException(string message, string key, Value value) : GlideCommandFailedException(message, value)
{
    /// <summary>
    /// Gets the key associated with the exception.
    /// </summary>
    /// <remarks>
    /// This property holds the value of the key that caused the exception to occur.
    /// It provides additional context and information for understanding and diagnosing
    /// the error within Glide operations.
    /// </remarks>
    public string Key { get; } = key;
}
