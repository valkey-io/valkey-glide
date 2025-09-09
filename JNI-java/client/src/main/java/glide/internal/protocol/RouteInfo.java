package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Route information that maps to glide-core's Routes protobuf.
 * This enables Java to specify routing when needed while defaulting
 * to glide-core automatic routing for optimal performance.
 */
public class RouteInfo {
    
    public enum RouteType {
        ALL_NODES,     // Send to all nodes in cluster
        ALL_PRIMARIES, // Send to all primary nodes
        RANDOM,        // Send to random node
        SLOT_KEY,      // Route based on key's hash slot
        SLOT_ID,       // Route based on specific slot ID
        BY_ADDRESS     // Route to specific node address
    }
    
    private final RouteType type;
    private final String host;
    private final int port;
    private final String slotKey;
    private final int slotId;
    private final boolean replica;
    
    private RouteInfo(RouteType type, String host, int port, String slotKey, int slotId, boolean replica) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.slotKey = slotKey;
        this.slotId = slotId;
        this.replica = replica;
    }
    
    // ===== FACTORY METHODS =====
    
    public static RouteInfo allNodes() {
        return new RouteInfo(RouteType.ALL_NODES, null, 0, null, 0, false);
    }
    
    public static RouteInfo allPrimaries() {
        return new RouteInfo(RouteType.ALL_PRIMARIES, null, 0, null, 0, false);
    }
    
    public static RouteInfo random() {
        return new RouteInfo(RouteType.RANDOM, null, 0, null, 0, false);
    }
    
    public static RouteInfo bySlotKey(String slotKey, boolean replica) {
        if (slotKey == null || slotKey.isEmpty()) {
            throw new IllegalArgumentException("Slot key cannot be null or empty");
        }
        return new RouteInfo(RouteType.SLOT_KEY, null, 0, slotKey, 0, replica);
    }
    
    public static RouteInfo bySlotId(int slotId, boolean replica) {
        if (slotId < 0 || slotId > 16383) {
            throw new IllegalArgumentException("Slot ID must be between 0 and 16383");
        }
        return new RouteInfo(RouteType.SLOT_ID, null, 0, null, slotId, replica);
    }
    
    public static RouteInfo byAddress(String host, int port) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        return new RouteInfo(RouteType.BY_ADDRESS, host, port, null, 0, false);
    }
    
    // ===== GETTERS =====
    
    public RouteType getType() {
        return type;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getSlotKey() {
        return slotKey;
    }
    
    public int getSlotId() {
        return slotId;
    }
    
    public boolean isReplica() {
        return replica;
    }
    
    /**
     * Serialize route info to binary format for JNI communication.
     * 
     * Binary Format:
     * [1 byte] Route Type (ordinal)
     * Based on type:
     * - BY_ADDRESS: [4+N bytes] Host String + [4 bytes] Port  
     * - SLOT_KEY: [4+N bytes] Slot Key String + [1 byte] Replica Flag
     * - SLOT_ID: [4 bytes] Slot ID + [1 byte] Replica Flag
     * - Simple routes (ALL_NODES, ALL_PRIMARIES, RANDOM): no additional data
     */
    public byte[] toBytes() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeByte(type.ordinal());
            
            switch (type) {
                case BY_ADDRESS:
                    writeString(dos, host);
                    dos.writeInt(port);
                    break;
                case SLOT_KEY:
                    writeString(dos, slotKey);
                    dos.writeBoolean(replica);
                    break;
                case SLOT_ID:
                    dos.writeInt(slotId);
                    dos.writeBoolean(replica);
                    break;
                // Simple routes need no additional data
                case ALL_NODES:
                case ALL_PRIMARIES:
                case RANDOM:
                    break;
            }
            
            dos.flush();
            return baos.toByteArray();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize route info", e);
        }
    }
    
    private void writeString(DataOutputStream dos, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }
    
    @Override
    public String toString() {
        switch (type) {
            case ALL_NODES:
                return "AllNodes";
            case ALL_PRIMARIES:
                return "AllPrimaries";
            case RANDOM:
                return "Random";
            case SLOT_KEY:
                return "SlotKey{key='" + slotKey + "', replica=" + replica + "}";
            case SLOT_ID:
                return "SlotId{id=" + slotId + ", replica=" + replica + "}";
            case BY_ADDRESS:
                return "Address{host='" + host + "', port=" + port + "}";
            default:
                return "Unknown";
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        RouteInfo routeInfo = (RouteInfo) obj;
        
        if (type != routeInfo.type) return false;
        if (port != routeInfo.port) return false;
        if (slotId != routeInfo.slotId) return false;
        if (replica != routeInfo.replica) return false;
        if (host != null ? !host.equals(routeInfo.host) : routeInfo.host != null) return false;
        return slotKey != null ? slotKey.equals(routeInfo.slotKey) : routeInfo.slotKey == null;
    }
    
    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + port;
        result = 31 * result + (slotKey != null ? slotKey.hashCode() : 0);
        result = 31 * result + slotId;
        result = 31 * result + (replica ? 1 : 0);
        return result;
    }
}