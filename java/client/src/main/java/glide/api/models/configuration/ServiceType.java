/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/** Supported AWS services for IAM authentication. */
public enum ServiceType {
    ELASTICACHE,
    MEMORY_DB;

    /** Returns the lowercase value expected by the core layer. */
    public String toCoreValue() {
        switch (this) {
            case ELASTICACHE:
                return "elasticache";
            case MEMORY_DB:
                return "memorydb";
            default:
                throw new IllegalStateException("Unexpected service type: " + this);
        }
    }

    /** Parse case-insensitive service value. */
    public static ServiceType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Service type string cannot be null");
        }
        String normalized = value.trim().toUpperCase();
        switch (normalized) {
            case "ELASTICACHE":
                return ELASTICACHE;
            case "MEMORYDB":
            case "MEMORY_DB":
                return MEMORY_DB;
            default:
                throw new IllegalArgumentException("Unsupported service type: " + value);
        }
    }
}
