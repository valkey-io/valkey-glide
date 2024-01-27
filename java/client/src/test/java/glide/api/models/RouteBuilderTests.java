package glide.api.models;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.api.models.configuration.Route;
import glide.api.models.configuration.Route.RouteType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class RouteBuilderTests {

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_ID", "REPLICA_SLOT_ID"})
    public void slot_id_is_required(RouteType routeType) {
        var exception =
                assertThrows(IllegalArgumentException.class, () -> new Route.Builder(routeType).build());
        assertEquals("Slot ID is missing", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_KEY", "REPLICA_SLOT_KEY"})
    public void slot_key_is_required(RouteType routeType) {
        var exception =
                assertThrows(IllegalArgumentException.class, () -> new Route.Builder(routeType).build());
        assertEquals("Slot key is missing", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_KEY", "REPLICA_SLOT_KEY", "ALL_NODES", "ALL_PRIMARIES", "RANDOM"})
    public void slot_id_not_acceptable(RouteType routeType) {
        var exception =
                assertThrows(
                        IllegalArgumentException.class, () -> new Route.Builder(routeType).setSlotId(42));
        assertEquals(
                "Slot ID could be set for corresponding types of route only", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_ID", "REPLICA_SLOT_ID", "ALL_NODES", "ALL_PRIMARIES", "RANDOM"})
    public void slot_key_not_acceptable(RouteType routeType) {
        var exception =
                assertThrows(
                        IllegalArgumentException.class, () -> new Route.Builder(routeType).setSlotKey("D'oh"));
        assertEquals(
                "Slot key could be set for corresponding types of route only", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_ID", "REPLICA_SLOT_ID"})
    public void build_with_slot_id(RouteType routeType) {
        var route = new Route.Builder(routeType).setSlotId(42).build();
        assertAll(
                () -> assertEquals(routeType, route.getRouteType()),
                () -> assertEquals(42, route.getSlotId()),
                () -> assertThrows(Throwable.class, () -> route.getSlotKey()));
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_KEY", "REPLICA_SLOT_KEY"})
    public void build_with_slot_key(RouteType routeType) {
        var route = new Route.Builder(routeType).setSlotKey("test").build();
        assertAll(
                () -> assertEquals(routeType, route.getRouteType()),
                () -> assertEquals("test", route.getSlotKey()),
                () -> assertThrows(Throwable.class, () -> route.getSlotId()));
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"ALL_NODES", "ALL_PRIMARIES", "RANDOM"})
    public void build_simple_route(RouteType routeType) {
        var route = new Route.Builder(routeType).build();
        assertAll(
                () -> assertEquals(routeType, route.getRouteType()),
                () -> assertThrows(Throwable.class, () -> route.getSlotKey()),
                () -> assertThrows(Throwable.class, () -> route.getSlotId()));
    }
}
