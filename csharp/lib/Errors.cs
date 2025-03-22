// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

namespace Glide;

public static class Errors
{
    /// <summary>
    /// Base class for errors.
    /// </summary>
    /// <param name="message"></param>
    public abstract class GlideException(string message) : Exception(message) { }

    /// <summary>
    /// An error on Valkey service-side that was reported during a request.
    /// </summary>
    /// <param name="message"></param>
    public sealed class RequestException(string message) : GlideException(message) { }

    /// <summary>
    /// An error on Valkey service-side that is thrown when a transaction is aborted
    /// </summary>
    /// <param name="message"></param>
    public sealed class ExecAbortException(string message) : GlideException(message) { }

    /// <summary>
    /// A timeout from Glide to Valkey service that is thrown when a request times out.
    /// </summary>
    /// <param name="message"></param>
    public sealed class TimeoutException(string message) : GlideException(message) { }

    /// <summary>
    /// A connection problem between Glide and Valkey.<br />
    /// That error is thrown when a connection disconnects. These errors can be temporary, as the client will attempt to reconnect.
    /// </summary>
    /// <param name="message"></param>
    public sealed class ConnectionException(string message) : GlideException(message) { }

    /// <summary>
    /// An errors that is thrown when a request cannot be completed in current configuration settings.
    /// </summary>
    /// <param name="message"></param>
    public sealed class ConfigurationError(string message) : GlideException(message) { }

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
