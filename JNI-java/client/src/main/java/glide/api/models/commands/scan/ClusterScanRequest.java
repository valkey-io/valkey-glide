package glide.api.models.commands.scan;

import glide.internal.protocol.BinaryProtocol;
import java.nio.charset.StandardCharsets;

/**
 * Request object for cluster scan operations.
 * Corresponds to the Rust ClusterScanRequest structure.
 * 
 * Uses robust binary protocol for safe Java-Rust communication.
 */
public class ClusterScanRequest {
    private String cursorId;
    private byte[] matchPattern;
    private Integer count;
    private String objectType;
    private Boolean allowNonCoveredSlots;
    private Long timeoutMs;
    private Boolean binaryMode;
    
    public ClusterScanRequest() {
        // Default constructor
    }
    
    // Builder pattern for easy construction
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ClusterScanRequest request = new ClusterScanRequest();
        
        public Builder cursorId(String cursorId) {
            request.cursorId = cursorId;
            return this;
        }
        
        public Builder matchPattern(String pattern) {
            request.matchPattern = pattern != null ? pattern.getBytes(StandardCharsets.UTF_8) : null;
            return this;
        }
        
        public Builder count(Integer count) {
            request.count = count;
            return this;
        }
        
        public Builder objectType(String type) {
            request.objectType = type;
            return this;
        }
        
        public Builder allowNonCoveredSlots(boolean allow) {
            request.allowNonCoveredSlots = allow;
            return this;
        }
        
        public Builder timeoutMs(long timeout) {
            request.timeoutMs = timeout;
            return this;
        }
        
        public Builder binaryMode(boolean binary) {
            request.binaryMode = binary;
            return this;
        }
        
        public ClusterScanRequest build() {
            return request;
        }
    }
    
    /**
     * Serialize to bytes for JNI transfer using robust binary protocol.
     * 
     * Wire format (all fields optional, indicated by presence flags):
     * - 7 presence flags (1 byte each)
     * - cursor_id: String with 4-byte length prefix
     * - match_pattern: Bytes with 4-byte length prefix  
     * - count: 4-byte integer
     * - object_type: String with 4-byte length prefix
     * - allow_non_covered_slots: 1-byte boolean
     * - timeout_ms: 8-byte long
     * - binary_mode: 1-byte boolean
     */
    public byte[] toBytes() {
        BinaryProtocol writer = BinaryProtocol.newWriter("ClusterScanRequest");
        
        // Add presence flags for all 7 optional fields (must match Rust expectations)
        writer.writePresenceFlag(cursorId != null, "cursor_id");
        writer.writePresenceFlag(matchPattern != null, "match_pattern");
        writer.writePresenceFlag(count != null, "count");
        writer.writePresenceFlag(objectType != null, "object_type");
        writer.writePresenceFlag(allowNonCoveredSlots != null, "allow_non_covered_slots");
        writer.writePresenceFlag(timeoutMs != null, "timeout_ms");
        writer.writePresenceFlag(binaryMode != null, "binary_mode");
        
        // Add actual field values (only if present)
        if (cursorId != null) {
            writer.writeString(cursorId, "cursor_id");
        }
        
        if (matchPattern != null) {
            writer.writeBytes(matchPattern, "match_pattern");
        }
        
        if (count != null) {
            writer.writeInt32(count, "count");
        }
        
        if (objectType != null) {
            writer.writeString(objectType, "object_type");
        }
        
        if (allowNonCoveredSlots != null) {
            writer.writeBoolean(allowNonCoveredSlots, "allow_non_covered_slots");
        }
        
        if (timeoutMs != null) {
            writer.writeInt64(timeoutMs, "timeout_ms");
        }
        
        if (binaryMode != null) {
            writer.writeBoolean(binaryMode, "binary_mode");
        }
        
        byte[] result = writer.toByteArray();
        
        // Debug logging in development
        if (System.getProperty("glide.protocol.debug") != null) {
            System.err.println("[ClusterScanRequest] Serialized " + result.length + " bytes");
            System.err.println("[ClusterScanRequest] Hex: " + BinaryProtocol.toHexString(result));
            System.err.println(writer.getDebugInfo());
        }
        
        return result;
    }
}