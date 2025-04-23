// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Valkey.Glide;

public static class Errors
{
    /// <summary>
    /// Base class for errors.
    /// </summary>
    public abstract class GlideException : Exception
    {
        public GlideException() : base() { }

        public GlideException(string message) : base(message) { }

        public GlideException(string message, Exception innerException) : base(message, innerException) { }
    }

    /// <summary>
    /// An error on Valkey service-side that was reported during a request.
    /// </summary>
    public sealed class RequestException : GlideException
    {
        public RequestException() : base() { }

        public RequestException(string message) : base(message) { }

        public RequestException(string message, Exception innerException) : base(message, innerException) { }
    }

    /// <summary>
    /// An error on Valkey service-side that is thrown when a transaction is aborted
    /// </summary>
    public sealed class ExecAbortException : GlideException
    {
        public ExecAbortException() : base() { }

        public ExecAbortException(string message) : base(message) { }

        public ExecAbortException(string message, Exception innerException) : base(message, innerException) { }
    }

    /// <summary>
    /// A timeout from Glide to Valkey service that is thrown when a request times out.
    /// </summary>
    public sealed class TimeoutException : GlideException
    {
        public TimeoutException() { }

        public TimeoutException(string message) : base(message) { }

        public TimeoutException(string message, Exception innerException) : base(message, innerException) { }
    }

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

    /// <summary>
    /// An errors that is thrown when a request cannot be completed in current configuration settings.
    /// </summary>
    public sealed class ConfigurationError : GlideException
    {
        // TODO set HelpLink with link to wiki

        public ConfigurationError() { }

        public ConfigurationError(string message) : base(message) { }

        public ConfigurationError(string message, Exception innerException) : base(message, innerException) { }
    }

    internal static GlideException Create(RequestErrorType type, string message) => type switch
    {
        RequestErrorType.Unspecified => new RequestException(message),
        RequestErrorType.ExecAbort => new ExecAbortException(message),
        RequestErrorType.Timeout => new TimeoutException(message),
        RequestErrorType.Disconnect => new ConnectionException(message),
        _ => new RequestException(message),
    };
}

internal enum RequestErrorType : uint
{
    Unspecified = 0,
    ExecAbort = 1,
    Timeout = 2,
    Disconnect = 3,
}
