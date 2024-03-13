/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.models.exceptions.RequestException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Request routing configuration. */
public class RequestRoutingConfiguration {

    /**
     * Basic interface. Please use one of the following implementations:
     *
     * <ul>
     *   <li>{@link SimpleRoute}
     *   <li>{@link SlotIdRoute}
     *   <li>{@link SlotKeyRoute}
     * </ul>
     */
    public interface Route {
        boolean isSingleNodeRoute();
    }

    public enum SimpleRoute implements Route {
        /** Route request to all nodes. */
        ALL_NODES,
        /** Route request to all primary nodes. */
        ALL_PRIMARIES,
        /** Route request to a random node. */
        RANDOM;

        @Override
        public boolean isSingleNodeRoute() {
            return this == RANDOM;
        }
    }

    public enum SlotType {
        PRIMARY,
        REPLICA,
    }

    /**
     * Request routing configuration overrides the {@link ReadFrom} connection configuration.<br>
     * If {@link SlotType#REPLICA} is used, the request will be routed to a replica, even if the
     * strategy is {@link ReadFrom#PRIMARY}.
     */
    @RequiredArgsConstructor
    @Getter
    public static class SlotIdRoute implements Route {
        /**
         * Slot number. There are 16384 slots in a redis cluster, and each shard manages a slot range.
         * Unless the slot is known, it's better to route using {@link SlotType#PRIMARY}.
         */
        private final int slotId;

        private final SlotType slotType;

        @Override
        public boolean isSingleNodeRoute() {
            return true;
        }
    }

    /**
     * Request routing configuration overrides the {@link ReadFrom} connection configuration.<br>
     * If {@link SlotType#REPLICA} is used, the request will be routed to a replica, even if the
     * strategy is {@link ReadFrom#PRIMARY}.
     */
    @RequiredArgsConstructor
    @Getter
    public static class SlotKeyRoute implements Route {
        /** The request will be sent to nodes managing this key. */
        private final String slotKey;

        private final SlotType slotType;

        @Override
        public boolean isSingleNodeRoute() {
            return true;
        }
    }

    /** Routes a request to a node by its address */
    @Getter
    public static class ByAddressRoute implements Route {
        /**
         * The endpoint of the node. If <code>port</code> is not provided, should be in the <code>
         * "address:port"</code> format, where <code>address</code> is the preferred endpoint as shown
         * in the output of the <code>CLUSTER SLOTS</code> command.
         */
        private final String host;

        /**
         * The port to access the node. If port is not provided, <code>host</code> is assumed to be in
         * the format <code>"address:port"</code>.
         */
        private final int port;

        public ByAddressRoute(@NonNull String host, int port) {
            this.host = host;
            this.port = port;
        }

        public ByAddressRoute(@NonNull String host) {
            String[] split = host.split(":");
            if (split.length < 2) {
                throw new RequestException(
                        "No port provided, and host is not in the expected format 'hostname:port'. Received: "
                                + host);
            }

            this.host = split[0];

            try {
                this.port = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                throw new RequestException("Port must be a valid integer. Received: " + split[1]);
            }
        }

        @Override
        public boolean isSingleNodeRoute() {
            return true;
        }
    }
}
