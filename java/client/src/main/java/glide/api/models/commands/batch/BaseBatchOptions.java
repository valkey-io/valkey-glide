/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.batch;

import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.exceptions.RequestException;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Base options settings class for sending a batch request. Shared settings for standalone and
 * cluster batch requests.
 */
@Getter
@SuperBuilder
public abstract class BaseBatchOptions {

    /**
     * The duration in milliseconds that the client should wait for the batch request to complete.
     * This duration encompasses sending the request, awaiting for a response from the server, and any
     * required reconnections or retries. If the specified timeout is exceeded for a pending request,
     * it will result in a timeout error. If not explicitly set, the client's {@link
     * BaseClientConfiguration#requestTimeout} will be used.
     */
    private final Integer timeout;

    /**
     * Determines how errors are handled within the batch response.
     *
     * <p>When set to {@code true}, the first encountered error in the batch will be raised as an
     * exception of type {@link RequestException} after all retries and reconnections have been
     * executed.
     *
     * <p>When set to {@code false}, errors will be included as part of the batch response, allowing
     * the caller to process both successful and failed commands together. In this case, error details
     * will be provided as instances of {@link RequestException}.
     */
    private final Boolean raiseOnError;
}
