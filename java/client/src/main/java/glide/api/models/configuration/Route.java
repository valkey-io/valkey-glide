package glide.api.models.configuration;

import java.util.Optional;
import lombok.Builder;
import lombok.Getter;

/** Request routing configuration. */
public class Route {

    public enum RouteType {
        /** Route request to all nodes. */
        ALL_NODES,
        /** Route request to all primary nodes. */
        ALL_PRIMARIES,
        /** Route request to a random node. */
        RANDOM,
        /** Route request to the primary node that contains the slot with the given id. */
        PRIMARY_SLOT_ID,
        /** Route request to the replica node that contains the slot with the given id. */
        REPLICA_SLOT_ID,
        /** Route request to the primary node that contains the slot that the given key matches. */
        PRIMARY_SLOT_KEY,
        /** Route request to the replica node that contains the slot that the given key matches. */
        REPLICA_SLOT_KEY,
    }

    @Getter private final RouteType routeType;

    private final Optional<Integer> slotId;

    private final Optional<String> slotKey;

    public Integer getSlotId() {
        assert slotId.isPresent();
        return slotId.get();
    }

    public String getSlotKey() {
        assert slotKey.isPresent();
        return slotKey.get();
    }

    private Route(RouteType routeType, Integer slotId) {
        this.routeType = routeType;
        this.slotId = Optional.of(slotId);
        this.slotKey = Optional.empty();
    }

    private Route(RouteType routeType, String slotKey) {
        this.routeType = routeType;
        this.slotId = Optional.empty();
        this.slotKey = Optional.of(slotKey);
    }

    private Route(RouteType routeType) {
        this.routeType = routeType;
        this.slotId = Optional.empty();
        this.slotKey = Optional.empty();
    }

    public static class Builder {
        private final RouteType routeType;
        private int slotId;
        private boolean slotIdSet = false;
        private String slotKey;
        private boolean slotKeySet = false;

        /**
         * Request routing configuration overrides the {@link ReadFrom} connection configuration.<br>
         * If {@link RouteType#REPLICA_SLOT_ID} or {@link RouteType#REPLICA_SLOT_KEY} is used, the
         * request will be routed to a replica, even if the strategy is {@link ReadFrom#PRIMARY}.
         */
        public Builder(RouteType routeType) {
            this.routeType = routeType;
        }

        /**
         * Slot number. There are 16384 slots in a redis cluster, and each shard manages a slot range.
         * Unless the slot is known, it's better to route using {@link RouteType#PRIMARY_SLOT_KEY} or
         * {@link RouteType#REPLICA_SLOT_KEY}.<br>
         * Could be used with {@link RouteType#PRIMARY_SLOT_ID} or {@link RouteType#REPLICA_SLOT_ID}
         * only.
         */
        public Builder setSlotId(int slotId) {
            if (!(routeType == RouteType.PRIMARY_SLOT_ID || routeType == RouteType.REPLICA_SLOT_ID)) {
                throw new IllegalArgumentException(
                        "Slot ID could be set for corresponding types of route only");
            }
            this.slotId = slotId;
            slotIdSet = true;
            return this;
        }

        /**
         * The request will be sent to nodes managing this key.<br>
         * Could be used with {@link RouteType#PRIMARY_SLOT_KEY} or {@link RouteType#REPLICA_SLOT_KEY}
         * only.
         */
        public Builder setSlotKey(String slotKey) {
            if (!(routeType == RouteType.PRIMARY_SLOT_KEY || routeType == RouteType.REPLICA_SLOT_KEY)) {
                throw new IllegalArgumentException(
                        "Slot key could be set for corresponding types of route only");
            }
            this.slotKey = slotKey;
            slotKeySet = true;
            return this;
        }

        public Route build() {
            if (routeType == RouteType.PRIMARY_SLOT_ID || routeType == RouteType.REPLICA_SLOT_ID) {
                if (!slotIdSet) {
                    throw new IllegalArgumentException("Slot ID is missing");
                }
                return new Route(routeType, slotId);
            }
            if (routeType == RouteType.PRIMARY_SLOT_KEY || routeType == RouteType.REPLICA_SLOT_KEY) {
                if (!slotKeySet) {
                    throw new IllegalArgumentException("Slot key is missing");
                }
                return new Route(routeType, slotKey);
            }

            return new Route(routeType);
        }
    }
}
