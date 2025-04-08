/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.batch;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Defines a retry strategy for batch requests, allowing control over retries in case of server or
 * connection errors.
 *
 * <p>This strategy determines whether failed commands should be retried, impacting execution order
 * and potential side effects.
 *
 * <h3>Behavior</h3>
 *
 * <ul>
 *   <li>If {@code retryServerError} is {@code true}, failed commands with a retriable error (e.g.,
 *       TRYAGAIN) will be retried.
 *   <li>If {@code retryConnectionError} is {@code true}, batch requests will be retried on
 *       connection failures.
 * </ul>
 *
 * <h3>Cautions</h3>
 *
 * <ul>
 *   <li><b>Server Errors:</b> Retrying may cause commands targeting the same slot to be executed
 *       out of order.
 *   <li><b>Connection Errors:</b> Retrying may lead to duplicate executions, as it is unclear which
 *       commands have already succeeded.
 * </ul>
 *
 * <p><b>Example Scenario:</b>
 *
 * <pre>
 * MGET key {key}:1
 * SET key "value"
 * </pre>
 *
 * <p>Expected response when keys are empty:
 *
 * <pre>
 * [null, null]
 * OK
 * </pre>
 *
 * However, if the slot is migrating, both commands may return an `ASK` error and be redirected.
 * Upon ASK redirection, a multi-key command may return a TRYAGAIN error (triggering a retry), while
 * the SET command succeeds immediately. This can result in an unintended reordering of commands if
 * the first command is retried after the slot stabilizes:
 *
 * <pre>
 * ["value", null]
 * OK
 * </pre>
 *
 * <b>Note:</b> Currently, retry strategies are supported only for non-atomic batches.
 */
@Getter
@SuperBuilder
public class BatchRetryStrategy {

    /**
     * If {@code true}, failed commands with a retriable error (e.g., TRYAGAIN) will be automatically
     * retried.
     *
     * <p>⚠️ <b>Warning:</b> Enabling this flag may cause commands targeting the same slot to execute
     * out of order.
     */
    private final boolean retryServerError;

    /**
     * If {@code true}, batch requests will be retried in case of connection errors.
     *
     * <p>⚠️ <b>Warning:</b> Retrying after a connection error may lead to duplicate executions, since
     * the server might have already received and processed the request before the error occurred.
     */
    private final boolean retryConnectionError;
}
