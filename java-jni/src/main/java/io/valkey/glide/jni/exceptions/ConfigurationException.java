package io.valkey.glide.jni.exceptions;

/**
 * Exception thrown when there is an issue with client configuration
 */
public class ConfigurationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    /**
     * Create a new ConfigurationException with the given message
     * 
     * @param message the error message
     */
    public ConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Create a new ConfigurationException with the given message and cause
     * 
     * @param message the error message
     * @param cause the cause of the exception
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}