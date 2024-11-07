/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;

/**
 * The JsonScalar object are values that are not objects or arrays. i.e., String, number, boolean
 * and null are scalar values. It is a parameter for the {@link Json#arrindex} command.
 *
 * <p>The JsonScalar object is inspired by JsonPrimitive class in Google's Gson suite.
 */
public class JsonScalar {
    private final Object value;

    /** Constructs the JsonScalar object with a Json String */
    public JsonScalar(String value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Value must be a Json String.");
        }
        this.value = value;
    }

    /** Constructs the JsonScalar object with a Json Number */
    public JsonScalar(Number value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Value must be a Json Number.");
        }
        this.value = value;
    }

    /** Constructs the JsonScalar object with a Json Boolean */
    public JsonScalar(Boolean value) {
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Value must be a Json Boolean.");
        }
        this.value = value;
    }

    public String toString() {
        return value == null ? "null" : value.toString();
    }
}
