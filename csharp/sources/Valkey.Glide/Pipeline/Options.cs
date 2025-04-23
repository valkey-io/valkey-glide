// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

using static Valkey.Glide.Internals.FFI;
using static Valkey.Glide.Route;

namespace Valkey.Glide.Pipeline;

// TODO docs for the god of docs
public class Options
{
    public class BatchRetryStrategy
    {
        internal readonly bool? _retryServerError;
        internal readonly bool? _retryConnectionError;

        public BatchRetryStrategy(bool? retryServerError = null, bool? retryConnectionError = null)
        {
            _retryServerError = retryServerError;
            _retryConnectionError = retryConnectionError;
        }
    }

    public abstract class BaseBatchOptions
    {
        protected readonly uint? _timeout;
        protected readonly bool? _raiseOnError;

        protected BaseBatchOptions(uint? timeout = null, bool? raiseOnError = null)
        {
            _timeout = timeout;
            _raiseOnError = raiseOnError;
        }

        internal virtual FFI.BatchOptions ToFfi()
        {
            return new(timeout : _timeout, raiseOnError : _raiseOnError);
        }
    }

    public class BatchOptions : BaseBatchOptions
    {
        public BatchOptions(uint? timeout = null, bool? raiseOnError = null) : base(timeout, raiseOnError)
        {
        }
    }

    public class ClusterBatchOptions : BaseBatchOptions
    {
        internal SingleNodeRoute? _route { get; private set; }
        internal BatchRetryStrategy? _retryStrategy { get; private set; }

        public ClusterBatchOptions(uint? timeout = null, bool? raiseOnError = null, SingleNodeRoute? route = null, BatchRetryStrategy? retryStrategy = null) : base(timeout, raiseOnError)
        {
            _route = route;
            _retryStrategy = retryStrategy;
        }

        internal override FFI.BatchOptions ToFfi()
        {
            return new(
                _retryStrategy?._retryServerError,
                _retryStrategy?._retryConnectionError,
                _raiseOnError,
                _timeout,
                _route?.ToFfi()
                );
        }
    }
}
