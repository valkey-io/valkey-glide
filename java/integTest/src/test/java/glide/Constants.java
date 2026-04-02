/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

/** Common constants for testing. */
public final class Constants {

    // Host names and addresses for tests.
    public static final String HOSTNAME_TLS = "valkey.glide.test.tls.com";
    public static final String HOSTNAME_NO_TLS = "valkey.glide.test.no_tls.com";
    public static final String IP_ADDRESS_V4 = "127.0.0.1";
    public static final String IP_ADDRESS_V6 = "::1";

    private Constants() {
        // Prevent instantiation
    }
}
