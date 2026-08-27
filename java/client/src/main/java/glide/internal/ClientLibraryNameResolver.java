/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

/** Resolves the effective library name reported during connection establishment. */
public final class ClientLibraryNameResolver {
    private static final String DEFAULT_LIB_NAME = "GlideJava";

    private ClientLibraryNameResolver() {}

    /**
     * Resolves the library name from an optional override and attribution tag.
     *
     * @param libName optional library-name override; null or empty uses {@code GlideJava}
     * @param clientInfoTag optional attribution tag; null or empty means absent
     * @return the effective library name
     */
    public static String resolve(String libName, String clientInfoTag) {
        String baseName = libName == null || libName.isEmpty() ? DEFAULT_LIB_NAME : libName;
        if (clientInfoTag == null || clientInfoTag.isEmpty()) {
            return baseName;
        }
        return baseName + "(" + clientInfoTag + ")";
    }
}
