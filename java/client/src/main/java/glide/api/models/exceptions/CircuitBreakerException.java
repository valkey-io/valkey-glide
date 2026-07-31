/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/** Thrown when a request is rejected because the client circuit breaker is open. */
public class CircuitBreakerException extends GlideException {
    public CircuitBreakerException(String message) {
        super(message);
    }
}
