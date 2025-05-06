// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using Valkey.Glide.Internals;

using static Valkey.Glide.ConnectionConfiguration;
using static Valkey.Glide.Route;

namespace Valkey.Glide.Pipeline;

public class Options
{
    /// <summary>
    /// Defines a retry strategy for batch requests, allowing control over retries in case of server or connection errors.
    /// <para />
    /// This strategy determines whether failed commands should be retried, impacting execution order and potential side effects.
    /// <para />
    /// <b>Behavior</b>
    /// <list type="bullet">
    ///   <item>
    ///     If <paramref name="retryServerError" /> is <see langword="true" />, retriable errors (e.g., <c>TRYAGAIN</c>) will trigger a retry.
    ///   </item>
    ///   <item>
    ///     If <paramref name="retryConnectionError" /> is <see langword="true" />, connection failures will trigger a retry.
    ///   </item>
    /// </list>
    /// <b>Cautions</b>
    /// <list type="bullet">
    ///   <item>
    ///     <b>Server Errors:</b> Retrying may cause commands targeting the same slot to be executed out of order.
    ///   </item>
    ///   <item>
    ///     <b>Connection Errors:</b> Retrying may lead to duplicate executions, since the server might
    ///     have already received and processed the request before the error occurred.
    ///   </item>
    /// </list>
    /// <para />
    /// <b>Example Scenario:</b>
    /// <code>
    /// MGET key {key}:1
    /// SET key "value"
    /// </code>
    /// Expected response when keys are empty:
    /// <code>
    /// [null, null]
    /// "OK"
    /// </code>
    /// However, if the slot is migrating, both commands may return an <c>ASK</c> error and be redirected.
    /// Upon <c>ASK</c> redirection, a multi-key command may return a <c>TRYAGAIN</c> error (triggering a retry),
    /// while the <c>SET</c> command succeeds immediately. This can result in an unintended reordering of commands
    /// if the first command is retried after the slot stabilizes:
    /// <code>
    /// ["value", null]
    /// "OK"
    /// </code>
    /// <b>Note:</b> Currently, retry strategies are supported only for non-atomic batches.
    /// </summary>
    /// <param name="retryServerError">
    /// If <see langword="true" />, failed commands with a retriable error(e.g., <c>TRYAGAIN</c>) will be automatically retried.
    /// <para />
    /// ⚠️<b>Warning:</b> Enabling this flag may cause commands targeting the same slot to execute out of order.
    /// <para />
    /// By default, this is set to <see langword="false" />.
    /// </param>
    /// <param name="retryConnectionError">
    /// If <see langword="true" />, batch requests will be retried in case of connection errors.
    /// <para />
    /// ⚠️<b>Warning:</b> Retrying after a connection error may lead to duplicate executions, since
    /// the server might have already received and processed the request before the error occurred.
    /// <para />
    /// By default, this is set to <see langword="false" />.
    /// </param>
    public class ClusterBatchRetryStrategy(bool? retryServerError = null, bool? retryConnectionError = null)
    {
        internal readonly bool? RetryServerError = retryServerError;
        internal readonly bool? RetryConnectionError = retryConnectionError;
    }

    /// <summary>
    /// Base options settings class for sending a batch request. Shared settings for standalone and cluster batch requests.
    /// </summary>
    /// <param name="timeout">
    /// The duration in milliseconds that the client should wait for the batch request to complete.
    /// This duration encompasses sending the request, awaiting for a response from the server, and any
    /// required reconnections or retries.If the specified timeout is exceeded for a pending request,
    /// it will result in a timeout error.If not explicitly set, the client's
    /// <see cref="ClientConfigurationBuilder{T}.RequestTimeout" />  will be used.
    /// </param>
    public abstract class BaseBatchOptions(uint? timeout = null)
    {
        protected readonly uint? _timeout = timeout;

        internal virtual FFI.BatchOptions ToFfi() => new(timeout: _timeout);
    }

    /// <summary>
    /// Options for a batch request for a standalone client.
    /// </summary>
    /// <inheritdoc cref="BaseBatchOptions" path="/param" />
    public class BatchOptions(uint? timeout = null) : BaseBatchOptions(timeout)
    { }

    /// <summary>
    /// Options for a batch request for a cluster client.
    /// </summary>
    /// <inheritdoc cref="BaseBatchOptions" path="/param" />
    /// <param name="route">
    /// Configures single-node routing for the batch request. The client will send the batch to the
    /// specified node defined by <c>route</c>.
    /// <para />
    /// If a redirection error occurs:
    /// <list type="bullet">
    ///   <item>
    ///     For Atomic Batches (Transactions), the entire transaction will be redirected.
    ///   </item>
    ///   <item>
    ///     For Non-Atomic Batches (Pipelines), only the commands that encountered redirection errors will be redirected.
    ///   </item>
    /// </list>
    /// </param>
    /// <param name="retryStrategy">
    /// ⚠️ <b>Please see <see cref="ClusterBatchRetryStrategy"/> and read carefully before enabling these
    /// options.</b>
    /// <para />
    /// Defines the retry strategy for handling batch request failures.
    /// <para />
    /// This strategy determines whether failed commands should be retried, potentially impacting execution order.
    /// <list type="bullet">
    ///   <item>
    ///     If <see cref="ClusterBatchRetryStrategy.RetryServerError" /> is <see langword="true" />, retriable errors (e.g., <c>TRYAGAIN</c>) will trigger a retry.
    ///   </item>
    ///   <item>
    ///     If <see cref="ClusterBatchRetryStrategy.RetryConnectionError" /> is {@code true}, connection failures will trigger a retry.
    ///   </item>
    /// </list>
    /// <para />
    /// <b>Warnings:</b>
    /// <list type="bullet">
    ///   <item>
    ///     Retrying server errors may cause commands targeting the same slot to execute out of order.
    ///   </item>
    ///   <item>
    ///     Retrying connection errors may lead to duplicate executions, as it is unclear which commands have already been processed.
    ///   </item>
    /// </list>
    /// <b>Note:</b> Currently, retry strategies are supported only for non-atomic batches.
    /// <para />
    /// <b>Recommendation:</b> It is recommended to increase the <paramref name="timeout" /> when enabling these strategies.
    /// </param>
    public class ClusterBatchOptions(
        uint? timeout = null,
        SingleNodeRoute? route = null,
        ClusterBatchRetryStrategy? retryStrategy = null) : BaseBatchOptions(timeout)
    {
        internal SingleNodeRoute? Route { get; private set; } = route;
        internal ClusterBatchRetryStrategy? RetryStrategy { get; private set; } = retryStrategy;

        internal override FFI.BatchOptions ToFfi() => new(
                RetryStrategy?.RetryServerError,
                RetryStrategy?.RetryConnectionError,
                _timeout,
                Route?.ToFfi()
            );
    }
}
