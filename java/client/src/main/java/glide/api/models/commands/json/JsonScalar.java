/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;

/**
 * The JsonScalar object are values that are not objects or arrays. i.e., String, number, boolean
 * and null are scalar values. It is a parameter for the {@link Json#arrindex} command.
 */
public class JsonScalar {
    private final Object value;

    public JsonScalar(Object value) {
        if (!isValidScalar(value)) {
            throw new IllegalArgumentException(
                    "Value must be a JSON scalar (String, Number, Boolean, or null).");
        }
        this.value = value;
    }

    private boolean isValidScalar(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    public String toString() {
        return value == null ? "null" : value.toString();
    }
}
