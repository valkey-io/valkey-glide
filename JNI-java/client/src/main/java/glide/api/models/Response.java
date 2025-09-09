/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.api.models.exceptions.RequestErrorType;

/**
 * Response class for JNI implementation compatibility.
 * This provides compatibility with the old protobuf-based Response structure.
 */
public class Response {
    
    /** Request error information */
    public static class RequestError {
        private final RequestErrorType type;
        private final String message;
        
        public RequestError(RequestErrorType type, String message) {
            this.type = type;
            this.message = message;
        }
        
        public RequestErrorType getType() {
            return type;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    /** Constant response for simple OK responses */
    public static class ConstantResponse {
        private final String value;
        
        public ConstantResponse(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    private final Object value;
    private final RequestError error;
    
    public Response(Object value) {
        this.value = value;
        this.error = null;
    }
    
    public Response(RequestError error) {
        this.value = null;
        this.error = error;
    }
    
    public Object getValue() {
        return value;
    }
    
    public RequestError getError() {
        return error;
    }
    
    public boolean hasError() {
        return error != null;
    }
}