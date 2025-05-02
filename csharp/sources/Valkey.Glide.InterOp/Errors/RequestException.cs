namespace Valkey.Glide.InterOp.Errors;

/// <summary>
/// An error on Valkey service-side that was reported during a request.
/// </summary>
public sealed class RequestException : GlideException
{
    public RequestException() : base() { }

    public RequestException(string message) : base(message) { }

    public RequestException(string message, Exception innerException) : base(message, innerException) { }
}