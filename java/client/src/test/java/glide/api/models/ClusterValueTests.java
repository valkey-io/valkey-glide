/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.models.GlideString.gs;
import static glide.utils.Java8Utils.createMap;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ClusterValueTests {

    @Test
    public void handle_null() {
        ClusterValue<?> value = ClusterValue.of(null);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertNull(value.getSingleValue()),
                () ->
                        assertEquals(
                                "No multi value stored",
                                assertThrows(Throwable.class, value::getMultiValue).getMessage()));
    }

    @Test
    public void handle_single_data() {
        ClusterValue<?> value = ClusterValue.of(42);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertEquals(42, value.getSingleValue()),
                () ->
                        assertEquals(
                                "No multi value stored",
                                assertThrows(Throwable.class, value::getMultiValue).getMessage()));
    }

    @Test
    public void handle_empty_map() {
        ClusterValue<?> value = ClusterValue.of(Collections.emptyMap());
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(Collections.emptyMap(), value.getMultiValue()),
                () ->
                        assertEquals(
                                "No single value stored",
                                assertThrows(Throwable.class, value::getSingleValue).getMessage()));
    }

    @Test
    public void handle_multi_data() {
        Map<String, String> node1Map = createMap("config1", "param1", "config2", "param2");
        Map<String, Object> data = createMap("node1", node1Map, "node2", Collections.emptyMap());
        ClusterValue<?> value = ClusterValue.of(data);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(data, value.getMultiValue()),
                () ->
                        assertEquals(
                                "No single value stored",
                                assertThrows(Throwable.class, value::getSingleValue).getMessage()));
    }

    @Test
    public void handle_multi_binary_data() {
        Map<GlideString, GlideString> node1Map =
                createMap(gs("config1"), gs("param1"), gs("config2"), gs("param2"));
        Map<GlideString, Object> data =
                createMap(gs("node1"), node1Map, gs("node2"), Collections.emptyMap());

        Map<GlideString, GlideString> node1MapNorm =
                createMap(gs("config1"), gs("param1"), gs("config2"), gs("param2"));
        Map<String, Object> dataNormalized =
                createMap("node1", node1MapNorm, "node2", Collections.emptyMap());
        ClusterValue<?> value = ClusterValue.of(data);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertEquals(dataNormalized, value.getMultiValue()),
                () ->
                        assertEquals(
                                "No single value stored",
                                assertThrows(Throwable.class, value::getSingleValue).getMessage()));
    }

    @Test
    public void single_value_ctor() {
        Map<String, String> map = createMap("config1", "param1", "config2", "param2");
        ClusterValue<?> value = ClusterValue.ofSingleValue(map);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertNotNull(value.getSingleValue()));
    }

    @Test
    public void multi_value_ctor() {
        Map<String, String> map = createMap("config1", "param1", "config2", "param2");
        ClusterValue<?> value = ClusterValue.ofMultiValue(map);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertTrue(value.getMultiValue().containsKey("config1")),
                () -> assertTrue(value.getMultiValue().containsKey("config2")));
    }

    @Test
    public void multi_value_binary_ctor() {
        Map<GlideString, GlideString> map =
                createMap(gs("config1"), gs("param1"), gs("config2"), gs("param2"));
        ClusterValue<?> value = ClusterValue.ofMultiValueBinary(map);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                // ofMultiValueBinary converts the key to a String, but the values are not converted
                () -> assertTrue(value.getMultiValue().containsKey("config1")),
                () -> assertEquals(gs("param1"), value.getMultiValue().get("config1")),
                () -> assertTrue(value.getMultiValue().containsKey("config2")),
                () -> assertEquals(gs("param2"), value.getMultiValue().get("config2")));
    }
}
