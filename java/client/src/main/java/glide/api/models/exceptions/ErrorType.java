/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.exceptions;

/**
 * Enum representing the different types of errors that can occur during request processing. These
 * values correspond to the RequestErrorType enum defined in the protobuf/Rust layer.
 */
public enum ErrorType {
    /** Unspecified error - generic or unknown error type */
    UNSPECIFIED(0),

    /** Transaction execution aborted */
    EXEC_ABORT(1),

    /** Request timeout exceeded */
    TIMEOUT(2),

    /** Connection disconnected */
    DISCONNECT(3);

    private final int code;

    ErrorType(int code) {
        this.code = code;
    }

    /**
     * Get the numeric code associated with this error type.
     *
     * @return The error type code
     */
    public int getCode() {
        return code;
    }

    /**
     * Convert an error type code to the corresponding enum value.
     *
     * @param code The error type code
     * @return The corresponding ErrorType enum value, or UNSPECIFIED for unknown codes
     */
    public static ErrorType fromCode(int code) {
        for (ErrorType type : ErrorType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        // Default to UNSPECIFIED for unknown codes
        return UNSPECIFIED;
    }
}
