/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.batch;

import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@ToString
public class ClusterBatchOptions extends BaseBatchOptions {

    /**
     * Configures single-node routing for the batch request. The client will send the batch to the
     * specified node defined by <code>route</code>.
     *
     * <p>If a redirection error occurs:
     *
     * <ul>
     *   <li>For Atomic Batches (Transactions), the entire transaction will be redirected.
     *   <li>For Non-Atomic Batches (Pipelines), only the commands that encountered redirection errors
     *       will be redirected.
     * </ul>
     */
    private final SingleNodeRoute route;

    /**
     * ⚠️ <b>Please see {@link ClusterBatchRetryStrategy} and read carefully before enabling these
     * options.</b>
     *
     * <p>Defines the retry strategy for handling cluster batch request failures.
     *
     * <p>This strategy determines whether failed commands should be retried, potentially impacting
     * execution order.
     *
     * <ul>
     *   <li>If {@code retryServerError} is {@code true}, retriable errors (e.g., TRYAGAIN) will
     *       trigger a retry.
     *   <li>If {@code retryConnectionError} is {@code true}, connection failures will trigger a
     *       retry.
     * </ul>
     *
     * <p><b>Warnings:</b>
     *
     * <ul>
     *   <li>Retrying server errors may cause commands targeting the same slot to execute out of
     *       order.
     *   <li>Retrying connection errors may lead to duplicate executions, as it is unclear which
     *       commands have already been processed.
     * </ul>
     *
     * <p><b>Note:</b> Currently, retry strategies are supported only for non-atomic batches.
     *
     * <p><b>Recommendation:</b> It is recommended to increase the timeout in {@code
     * BaseBatchOptions#timeout} when enabling these strategies.
     *
     * <p><b>Default:</b> Both {@code retryServerError} and {@code retryConnectionError} are set to
     * {@code false}.
     */
    private final ClusterBatchRetryStrategy retryStrategy;
}
