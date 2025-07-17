/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.protobuf;

/**
 * Compatibility stub for protobuf ResponseOuterClass.
 * This provides basic compatibility for tests that reference the old protobuf-based implementation.
 */
public class ResponseOuterClass {
    
    /**
     * Compatibility stub for response types.
     */
    public enum ResponseType {
        Success,
        Failure,
        Timeout,
        Disconnect
    }
    
    /**
     * Compatibility stub for response.
     */
    public static class Response {
        private ResponseType responseType;
        private String message;
        private Object value;
        
        public Response() {
            this.responseType = ResponseType.Success;
            this.message = "";
            this.value = null;
        }
        
        public ResponseType getResponseType() {
            return responseType;
        }
        
        public void setResponseType(ResponseType responseType) {
            this.responseType = responseType;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Object getValue() {
            return value;
        }
        
        public void setValue(Object value) {
            this.value = value;
        }
        
        public boolean hasValue() {
            return value != null;
        }
    }
}