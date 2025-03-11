using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Exceptions;

namespace Valkey.Glide.Commands;

/// <summary>
/// Represents an exception that is thrown when a command execution fails within Glide operations.
/// </summary>
/// <remarks>
/// This exception serves as a base class for command-specific exceptions in the Glide framework.
/// It contains information about the associated <see cref="Value"/> that led to the failure and includes
/// a descriptive error message.
/// Derived classes can represent more specific failure scenarios for different command types.
/// </remarks>
/// <seealso cref="GlideException"/>
/// <seealso cref="Value"/>
public abstract class GlideCommandFailedException : GlideException
{
    /// <summary>
    /// The value returned by the operation
    /// </summary>
    public Value Value { get; }

    /// <summary>
    /// Represents an exception that is thrown when a Glide command execution fails.
    /// </summary>
    /// <remarks>
    /// This exception provides details about the specific <see cref="Value"/> that caused the failure
    /// and captures a meaningful error message for diagnostic purposes.
    /// </remarks>
    protected GlideCommandFailedException(Value value, string message, Exception? innerException = null) : base(message, innerException)
    {
        Value = value;
    }
}