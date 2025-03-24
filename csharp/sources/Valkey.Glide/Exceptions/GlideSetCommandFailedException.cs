using Valkey.Glide.InterOp;
using Valkey.Glide.InterOp.Exceptions;
using Valkey.Glide.Properties;

namespace Valkey.Glide.Exceptions;

/// <summary>
/// Represents an exception thrown when a "set" command execution fails in the context of Glide operations.
/// </summary>
/// <remarks>
/// This exception is a specific type of <see cref="GlideCommandFailedException"/> that is triggered when
/// the execution of a "set" command does not succeed.
/// It contains information about the associated <see cref="Value"/> that caused the failure and provides
/// a message describing the error.
/// </remarks>
/// <seealso cref="GlideKeyCommandFailedException"/>
/// <seealso cref="GlideCommandFailedException"/>
/// <seealso cref="GlideException"/>
/// <seealso cref="Value"/>
public sealed class GlideSetCommandFailedException : GlideKeyCommandFailedException
{

    internal GlideSetCommandFailedException(string key, Value value) : base(Language.Exceptions_GlideSetCommandFailedException, key, value)
    {
    }
}
