/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for client behavior when connecting with hostnames.
 *
 * To run these tests, you need to add the following mappings to your system's
 * hosts file ('/etc/hosts' on Mac/Linux, 'C:\Windows\System32\drivers\etc\hosts' on
 * Windows), then set the environment variable <c>VALKEY_GLIDE_DNS_TESTS_ENABLED</c>:
 * - 127.0.0.1 valkey.glide.test.tls.com
 * - 127.0.0.1 valkey.glide.test.no_tls.com
 * - ::1 valkey.glide.test.tls.com
 * - ::1 valkey.glide.test.no_tls.com
 */
@Timeout(10)
public class DnsTest {

    // Hostname constants for testing.
    // See "../utils/cluster_manager.py" for details.
    private static final String HOSTNAME_TLS = "valkey.glide.test.tls.com";
    private static final String HOSTNAME_NO_TLS = "valkey.glide.test.no_tls.com";
    private static final String INVALID_HOSTNAME = "nonexistent.invalid";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    void testConnectWithValidHostnameNoTls(boolean clusterMode) {
        assumeTrue(isDnsTestsEnabled(), "DNS tests not enabled");

        int port = getPort(clusterMode, false);
        NodeAddress address = NodeAddress.builder().host(HOSTNAME_NO_TLS).port(port).build();

        if (clusterMode) {
            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(address)
                            .requestTimeout(2000)
                            .build();
            try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        } else {
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(address)
                            .requestTimeout(2000)
                            .build();
            try (GlideClient client = GlideClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConnectWithInvalidHostnameNoTls(boolean clusterMode) {
        String invalidHostname = INVALID_HOSTNAME;
        int port = getPort(clusterMode, false);
        NodeAddress address = NodeAddress.builder().host(invalidHostname).port(port).build();

        if (clusterMode) {
            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(address)
                            .requestTimeout(2000)
                            .build();
            Exception exception =
                    assertThrows(
                            Exception.class,
                            () -> {
                                GlideClusterClient.createClient(config).get();
                            });
            String errorMessage = exception.getMessage().toLowerCase();
            assertTrue(
                    errorMessage.contains("dns")
                            || errorMessage.contains("resolve")
                            || errorMessage.contains("host")
                            || errorMessage.contains(invalidHostname)
                            || errorMessage.contains("refused")
                            || errorMessage.contains("connection"),
                    "Expected DNS-related error, got: " + exception.getMessage());
        } else {
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(address)
                            .requestTimeout(2000)
                            .build();
            Exception exception =
                    assertThrows(
                            Exception.class,
                            () -> {
                                GlideClient.createClient(config).get();
                            });
            String errorMessage = exception.getMessage().toLowerCase();
            assertTrue(
                    errorMessage.contains("dns")
                            || errorMessage.contains("resolve")
                            || errorMessage.contains("host")
                            || errorMessage.contains(invalidHostname)
                            || errorMessage.contains("refused")
                            || errorMessage.contains("connection"),
                    "Expected DNS-related error, got: " + exception.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    void testTlsConnectWithHostnameInCert(boolean clusterMode) {
        assumeTrue(isDnsTestsEnabled(), "DNS tests not enabled");

        int port = getPort(clusterMode, true);
        byte[] caCert = getCaCertificate();
        NodeAddress address = NodeAddress.builder().host(HOSTNAME_TLS).port(port).build();

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();

        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        } else {
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            try (GlideClient client = GlideClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTlsConnectWithHostnameNotInCert(boolean clusterMode) {
        assumeTrue(isDnsTestsEnabled(), "DNS tests not enabled");

        String hostname = HOSTNAME_NO_TLS;
        int port = getPort(clusterMode, true);
        byte[] caCert = getCaCertificate();
        NodeAddress address = NodeAddress.builder().host(hostname).port(port).build();

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();

        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            Exception exception =
                    assertThrows(
                            Exception.class,
                            () -> {
                                GlideClusterClient.createClient(config).get();
                            });
            String errorMessage = exception.getMessage().toLowerCase();
            assertTrue(
                    errorMessage.contains("hostname")
                            || errorMessage.contains("certificate")
                            || errorMessage.contains("verify")
                            || errorMessage.contains("tls")
                            || errorMessage.contains("ssl")
                            || errorMessage.contains("refused")
                            || errorMessage.contains("connection"),
                    "Expected TLS/hostname verification error, got: " + exception.getMessage());
        } else {
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            Exception exception =
                    assertThrows(
                            Exception.class,
                            () -> {
                                GlideClient.createClient(config).get();
                            });
            String errorMessage = exception.getMessage().toLowerCase();
            assertTrue(
                    errorMessage.contains("hostname")
                            || errorMessage.contains("certificate")
                            || errorMessage.contains("verify")
                            || errorMessage.contains("tls")
                            || errorMessage.contains("ssl")
                            || errorMessage.contains("refused")
                            || errorMessage.contains("connection"),
                    "Expected TLS/hostname verification error, got: " + exception.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    void testTlsConnectWithIpv4(boolean clusterMode) {
        assumeTrue(isDnsTestsEnabled(), "DNS tests not enabled");

        String ipv4Address = "127.0.0.1";
        int port = getPort(clusterMode, true);
        byte[] caCert = getCaCertificate();
        NodeAddress address = NodeAddress.builder().host(ipv4Address).port(port).build();

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();

        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        } else {
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            try (GlideClient client = GlideClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    void testTlsConnectWithIpv6(boolean clusterMode) {
        assumeTrue(isDnsTestsEnabled(), "DNS tests not enabled");
        assumeTrue(isIpv6Supported(), "IPv6 not available");

        String ipv6Address = "[::1]";
        int port = getPort(clusterMode, true);
        byte[] caCert = getCaCertificate();
        NodeAddress address = NodeAddress.builder().host(ipv6Address).port(port).build();

        TlsAdvancedConfiguration tlsConfig =
                TlsAdvancedConfiguration.builder().rootCertificates(caCert).build();

        if (clusterMode) {
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        } else {
            AdvancedGlideClientConfiguration advancedConfig =
                    AdvancedGlideClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();
            GlideClientConfiguration config =
                    GlideClientConfiguration.builder()
                            .address(address)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .requestTimeout(2000)
                            .build();
            try (GlideClient client = GlideClient.createClient(config).get()) {
                assertEquals("PONG", client.ping().get());
            }
        }
    }

    // -------------------
    // Helper methods
    // -------------------

    private static int getPort(boolean clusterMode, boolean tlsMode) {
        String[] hosts = tlsMode
                ? (clusterMode ? TestConfiguration.CLUSTER_TLS_HOSTS : TestConfiguration.STANDALONE_TLS_HOSTS)
                : (clusterMode ? TestConfiguration.CLUSTER_HOSTS : TestConfiguration.STANDALONE_HOSTS);
        if (hosts == null || hosts.length == 0) {
            throw new IllegalStateException("No hosts configured for testing");
        }
        String[] parts = hosts[0].trim().split(":");
        return Integer.parseInt(parts[1]);
    }

    private static boolean isDnsTestsEnabled() {
        return System.getenv("VALKEY_GLIDE_DNS_TESTS_ENABLED") != null;
    }

    public static boolean isIpv6Supported() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.bind(new java.net.InetSocketAddress("::1", 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
