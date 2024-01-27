package glide.api.models;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class ClusterValueTests {

    @Test
    public void handle_null() {
        var value = ClusterValue.of(null);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertNull(value.getSingleValue()),
                () -> assertThrows(Throwable.class, value::getMultiValue));
    }

    @Test
    public void handle_single_data() {
        var value = ClusterValue.of(42);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertEquals(42, value.getSingleValue()),
                () -> assertThrows(Throwable.class, value::getMultiValue));
    }

    @Test
    public void handle_multi_data() {
        var data = Map.of("node1", Map.of("config1", "param1", "config2", "param2"), "node2", Map.of());
        var value = ClusterValue.of(data);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(data, value.getMultiValue()),
                () -> assertThrows(Throwable.class, value::getSingleValue));
    }
}
