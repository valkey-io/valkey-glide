// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.InterOp.Native;

namespace Valkey.Glide.InterOp.Errors;

    /// <summary>
    /// Base class for errors.
    /// </summary>
    public abstract class GlideException : Exception
    {
        public GlideException() : base() { }

        public GlideException(string message) : base(message) { }

        public GlideException(string message, Exception innerException) : base(message, innerException) { }
        internal static GlideException Create(RequestErrorType type, string message) => type switch
        {
            RequestErrorType.Unspecified => new RequestException(message),
            RequestErrorType.ExecAbort => new ExecAbortException(message),
            RequestErrorType.Timeout => new TimeoutException(message),
            RequestErrorType.Disconnect => new ConnectionException(message),
            _ => new RequestException(message),
        };
    }

