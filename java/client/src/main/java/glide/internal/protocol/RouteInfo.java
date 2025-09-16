/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Routing information for command execution in cluster environments. Supports all glide-core
 * routing strategies including automatic, explicit node targeting, slot-based routing, and
 * multi-node operations.
 */
public class RouteInfo {

    public enum RouteType {
        AUTO(0),
        ALL_NODES(1),
        ALL_PRIMARIES(2),
        RANDOM(3),
        SLOT_KEY(4),
        SLOT_ID(5),
        BY_ADDRESS(6);

        private final int value;

        RouteType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private final RouteType routeType;
    private final String slotKey;
    private final Integer slotId;
    private final String host;
    private final Integer port;
    private final Boolean preferReplica;

    private RouteInfo(
            RouteType routeType,
            String slotKey,
            Integer slotId,
            String host,
            Integer port,
            Boolean preferReplica) {
        this.routeType = routeType;
        this.slotKey = slotKey;
        this.slotId = slotId;
        this.host = host;
        this.port = port;
        this.preferReplica = preferReplica;
    }

    /** Automatic routing (default) - let glide-core determine optimal routing */
    public static RouteInfo auto() {
        return new RouteInfo(RouteType.AUTO, null, null, null, null, null);
    }

    /** Route to all nodes in the cluster */
    public static RouteInfo allNodes() {
        return new RouteInfo(RouteType.ALL_NODES, null, null, null, null, null);
    }

    /** Route to all primary nodes */
    public static RouteInfo allPrimaries() {
        return new RouteInfo(RouteType.ALL_PRIMARIES, null, null, null, null, null);
    }

    /** Route to a random node */
    public static RouteInfo random() {
        return new RouteInfo(RouteType.RANDOM, null, null, null, null, null);
    }

    /** Route by slot key - determines hash slot from key */
    public static RouteInfo bySlotKey(String slotKey, boolean preferReplica) {
        return new RouteInfo(RouteType.SLOT_KEY, slotKey, null, null, null, preferReplica);
    }

    /** Route by explicit slot ID */
    public static RouteInfo bySlotId(int slotId, boolean preferReplica) {
        return new RouteInfo(RouteType.SLOT_ID, null, slotId, null, null, preferReplica);
    }

    /** Route to specific address */
    public static RouteInfo byAddress(String host, int port) {
        return new RouteInfo(RouteType.BY_ADDRESS, null, null, host, port, null);
    }

    public RouteType getRouteType() {
        return routeType;
    }

    public String getSlotKey() {
        return slotKey;
    }

    public Integer getSlotId() {
        return slotId;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public Boolean getPreferReplica() {
        return preferReplica;
    }

    /** Serialize routing information to byte stream */
    public void writeTo(ByteArrayOutputStream baos) throws IOException {
        // Write route type
        baos.write(routeType.getValue());

        switch (routeType) {
            case SLOT_KEY:
                if (slotKey == null) {
                    throw new IllegalStateException("Slot key cannot be null for SLOT_KEY routing");
                }
                byte[] keyBytes = slotKey.getBytes(StandardCharsets.UTF_8);
                writeVarInt(baos, keyBytes.length);
                baos.write(keyBytes);
                baos.write(preferReplica != null && preferReplica ? 1 : 0);
                break;

            case SLOT_ID:
                if (slotId == null) {
                    throw new IllegalStateException("Slot ID cannot be null for SLOT_ID routing");
                }
                writeVarInt(baos, slotId);
                baos.write(preferReplica != null && preferReplica ? 1 : 0);
                break;

            case BY_ADDRESS:
                if (host == null || port == null) {
                    throw new IllegalStateException("Host and port cannot be null for BY_ADDRESS routing");
                }
                byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
                writeVarInt(baos, hostBytes.length);
                baos.write(hostBytes);
                writeVarInt(baos, port);
                break;

            case AUTO:
            case ALL_NODES:
            case ALL_PRIMARIES:
            case RANDOM:
                // No additional data needed
                break;

            default:
                throw new IllegalStateException("Unknown route type: " + routeType);
        }
    }

    private void writeVarInt(ByteArrayOutputStream baos, int value) throws IOException {
        while ((value & 0x80) != 0) {
            baos.write((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        baos.write((byte) (value & 0x7F));
    }

    @Override
    public String toString() {
        switch (routeType) {
            case SLOT_KEY:
                return "RouteInfo{SLOT_KEY, key='" + slotKey + "', preferReplica=" + preferReplica + "}";
            case SLOT_ID:
                return "RouteInfo{SLOT_ID, id=" + slotId + ", preferReplica=" + preferReplica + "}";
            case BY_ADDRESS:
                return "RouteInfo{BY_ADDRESS, host='" + host + "', port=" + port + "}";
            default:
                return "RouteInfo{" + routeType + "}";
        }
    }
}
