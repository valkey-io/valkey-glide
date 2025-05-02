namespace Valkey.Glide.InterOp.Errors;

/// <summary>
/// A timeout from Glide to Valkey service that is thrown when a request times out.
/// </summary>
public sealed class TimeoutException : GlideException
{
    public TimeoutException() { }

    public TimeoutException(string message) : base(message) { }

    public TimeoutException(string message, Exception innerException) : base(message, innerException) { }
}