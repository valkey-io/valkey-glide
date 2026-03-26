/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

public enum OperationType {
    ALL("all"),
    READ_ONLY("read"),
    WRITE_ONLY("write"),
    DELETE_ONLY("delete");

    private final String name;

    OperationType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
