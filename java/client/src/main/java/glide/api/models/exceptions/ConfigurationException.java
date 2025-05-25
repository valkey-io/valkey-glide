/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/**
 * Exception thrown when there is an issue with the client configuration. This exception indicates
 * that the client configuration is invalid or incompatible with the requested operation.
 */
public class ConfigurationException extends GlideException {
    /**
     * Constructs a new ConfigurationException with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the getMessage()
     *     method)
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a new ConfigurationException with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the getMessage()
     *     method)
     * @param cause the cause (which is saved for later retrieval by the getCause() method)
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
