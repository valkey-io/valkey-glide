package io.valkey.glide.jni.exceptions;

/**
 * Exception thrown when a command execution fails
 */
public class CommandException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Create a new CommandException with the given message
     * 
     * @param message the error message
     */
    public CommandException(String message) {
        super(message);
    }
    
    /**
     * Create a new CommandException with the given message and cause
     * 
     * @param message the error message
     * @param cause the cause of the exception
     */
    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}