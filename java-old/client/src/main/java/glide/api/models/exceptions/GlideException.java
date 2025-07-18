/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/** Glide client error: Base class for errors. */
public class GlideException extends RuntimeException {
    public GlideException(String message) {
        super(message);
    }
}
