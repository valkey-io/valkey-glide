/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

public class JsonScalar {
    Object scalar;

    public JsonScalar(Object scalar) {
        if (!(scalar instanceof String
                || scalar instanceof Number
                || scalar instanceof Boolean
                || scalar == null)) {
            throw new IllegalArgumentException(
                    "Value must be a Json scalar (String, Number, Boolean, or null).");
        }
        this.scalar = scalar;
    }

    public String toString() {
        return scalar.toString();
    }
}
