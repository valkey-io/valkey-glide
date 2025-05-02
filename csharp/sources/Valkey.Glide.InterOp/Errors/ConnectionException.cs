namespace Valkey.Glide.InterOp.Errors;

/// <summary>
/// A connection problem between Glide and Valkey.<br />
/// That error is thrown when a connection disconnects. These errors can be temporary, as the client will attempt to reconnect.
/// </summary>
public sealed class ConnectionException : GlideException
{
    public ConnectionException() { }

    public ConnectionException(string message) : base(message) { }

    public ConnectionException(string message, Exception innerException) : base(message, innerException) { }
}