package io.valkey.glide.jni.exceptions;

/**
 * Exception thrown when there is an internal error in the JNI implementation
 */
public class InternalException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    /**
     * Create a new InternalException with the given message
     * 
     * @param message the error message
     */
    public InternalException(String message) {
        super(message);
    }
    
    /**
     * Create a new InternalException with the given message and cause
     * 
     * @param message the error message
     * @param cause the cause of the exception
     */
    public InternalException(String message, Throwable cause) {
        super(message, cause);
    }
}