using Valkey.Glide.InterOp;

namespace Valkey.Glide.Exceptions;

/// <summary>
/// Represents an exception that is thrown when a Glide "get" command fails to retrieve a value for the specified key.
/// </summary>
/// <remarks>
/// This exception is specifically designed to handle failure scenarios related to the execution of the "Get" command
/// in the Glide framework when no valid string result is retrieved.
/// </remarks>
/// <seealso cref="GlideKeyCommandFailedException"/>
/// <seealso cref="GlideCommandFailedException"/>
/// <seealso cref="GlideException"/>
/// <seealso cref="Value"/>
public class GlideGetCommandFailedException : GlideKeyCommandFailedException
{
    internal GlideGetCommandFailedException(string key, Value value) : base(Properties.Language.Exceptions_GlideGetCommandFailedException, key, value)
    {
    }
}
