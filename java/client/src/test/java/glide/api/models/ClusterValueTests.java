/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.models.GlideString.gs;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ClusterValueTests {

    private ClusterValue<?> value;
    private Map<?, ?> data;
    private Map<?, ?> dataNormalized;

    @Test
    public void handle_null() {
        value = ClusterValue.of(null);
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
        value = ClusterValue.of(42);
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
        value = ClusterValue.of(Collections.emptyMap());
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
        Map<String, String> node1Map = new HashMap<>();
        node1Map.put("config1", "param1");
        node1Map.put("config2", "param2");
        data = new HashMap<>();
        ((Map<String, Object>) data).put("node1", node1Map);
        ((Map<String, Object>) data).put("node2", Collections.emptyMap());
        value = ClusterValue.of(data);
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
        Map<GlideString, GlideString> node1Map = new HashMap<>();
        node1Map.put(gs("config1"), gs("param1"));
        node1Map.put(gs("config2"), gs("param2"));
        data = new HashMap<>();
        ((Map<GlideString, Object>) data).put(gs("node1"), node1Map);
        ((Map<GlideString, Object>) data).put(gs("node2"), Collections.emptyMap());

        Map<GlideString, GlideString> node1MapNorm = new HashMap<>();
        node1MapNorm.put(gs("config1"), gs("param1"));
        node1MapNorm.put(gs("config2"), gs("param2"));
        dataNormalized = new HashMap<>();
        ((Map<String, Object>) dataNormalized).put("node1", node1MapNorm);
        ((Map<String, Object>) dataNormalized).put("node2", Collections.emptyMap());
        value = ClusterValue.of(data);
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
        Map<String, String> map = new HashMap<>();
        map.put("config1", "param1");
        map.put("config2", "param2");
        value = ClusterValue.ofSingleValue(map);
        assertAll(
                () -> assertFalse(value.hasMultiData()),
                () -> assertTrue(value.hasSingleData()),
                () -> assertNotNull(value.getSingleValue()));
    }

    @Test
    public void multi_value_ctor() {
        Map<String, String> map = new HashMap<>();
        map.put("config1", "param1");
        map.put("config2", "param2");
        value = ClusterValue.ofMultiValue(map);
        assertAll(
                () -> assertTrue(value.hasMultiData()),
                () -> assertFalse(value.hasSingleData()),
                () -> assertNotNull(value.getMultiValue()),
                () -> assertTrue(value.getMultiValue().containsKey("config1")),
                () -> assertTrue(value.getMultiValue().containsKey("config2")));
    }

    @Test
    public void multi_value_binary_ctor() {
        Map<GlideString, GlideString> map = new HashMap<>();
        map.put(gs("config1"), gs("param1"));
        map.put(gs("config2"), gs("param2"));
        value = ClusterValue.ofMultiValueBinary(map);
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
