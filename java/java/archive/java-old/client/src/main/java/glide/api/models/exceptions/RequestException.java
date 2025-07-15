/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/** Request error: Errors that were reported during a request. */
public class RequestException extends GlideException {
    public RequestException(String message) {
        super(message);
    }
}
