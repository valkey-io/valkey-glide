/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/**
 * Error types for request failures in the JNI implementation.
 * This provides compatibility with the old protobuf-based error handling.
 */
public enum RequestErrorType {
    /** Unspecified error type */
    Unspecified,
    
    /** Timeout error */
    Timeout,
    
    /** Connection disconnected */
    Disconnect,
    
    /** EXEC command aborted */
    ExecAbort
}