/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

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

    public Object getValue() {
        return value;
    }

    public String toString() {
        return value.toString();
    }
}
