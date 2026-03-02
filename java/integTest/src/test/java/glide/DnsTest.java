/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.*;
import static org.junit.jupiter.api.Assertions.*;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration.GlideClientConfigurationBuilder;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for client behavior when connecting with hostnames.
 *
 * <p>To run these tests, you need to add the following mappings to your hosts file then set the
 * environment variable {@code VALKEY_GLIDE_DNS_TESTS_ENABLED}:
 *
 * <ul>
 *   <li>{@code 127.0.0.1 valkey.glide.test.tls.com}
 *   <li>{@code 127.0.0.1 valkey.glide.test.no_tls.com}
 *   <li>{@code ::1 valkey.glide.test.tls.com}
 *   <li>{@code ::1 valkey.glide.test.no_tls.com}
 * </ul>
 */
@Timeout(10)
@EnabledIfEnvironmentVariable(named = "VALKEY_GLIDE_DNS_TESTS_ENABLED", matches = ".*")
public class DnsTest {

    // Hostname constants for testing. Both hostnames should map to localhost,
    // but only HOSTNAME_TLS should be included in the TLS certificate used by the
    // server. See 'cluster_manager.py' for details.
    private static final String HOSTNAME_TLS = "valkey.glide.test.tls.com";
    private static final String HOSTNAME_NO_TLS = "valkey.glide.test.no_tls.com";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    void testConnectWithValidHostnameNoTls(boolean clusterMode) {
        try (BaseClient client = buildClient(clusterMode, false, HOSTNAME_NO_TLS)) {
            assertConnected(client);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConnectWithInvalidHostnameNoTls(boolean clusterMode) {
        assertThrows(Exception.class, () -> buildClient(clusterMode, false, "nonexistent.invalid"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    void testTlsConnectWithHostnameInCert(boolean clusterMode) {
        try (BaseClient client = buildClient(clusterMode, true, HOSTNAME_TLS)) {
            assertConnected(client);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTlsConnectWithHostnameNotInCert(boolean clusterMode) {
        assertThrows(Exception.class, () -> buildClient(clusterMode, true, HOSTNAME_NO_TLS));
    }

    // -------------------
    // Helper methods
    // -------------------

    @SneakyThrows
    private static BaseClient buildClient(boolean clusterMode, boolean useTls, String hostname) {

        // Get port from test configuration.
        String[] hosts =
                useTls
                        ? (clusterMode
                                ? TestConfiguration.CLUSTER_TLS_HOSTS
                                : TestConfiguration.STANDALONE_TLS_HOSTS)
                        : (clusterMode ? TestConfiguration.CLUSTER_HOSTS : TestConfiguration.STANDALONE_HOSTS);
        String hostEntry = hosts[0].trim();
        int port = Integer.parseInt(hostEntry.substring(hostEntry.lastIndexOf(':') + 1));

        // Build common arguments.
        NodeAddress address = NodeAddress.builder().host(hostname).port(port).build();
        byte[] certificateBytes = getCaCertificate();

        // Build client configuration and client.
        if (clusterMode) {
            GlideClusterClientConfigurationBuilder<?, ?> builder =
                    GlideClusterClientConfiguration.builder().address(address).useTLS(useTls);
            if (useTls) {
                TlsAdvancedConfiguration tlsConfig =
                        TlsAdvancedConfiguration.builder().rootCertificates(certificateBytes).build();
                builder.advancedConfiguration(
                        AdvancedGlideClusterClientConfiguration.builder()
                                .tlsAdvancedConfiguration(tlsConfig)
                                .build());
            }
            return GlideClusterClient.createClient(builder.build()).get();
        } else {
            GlideClientConfigurationBuilder<?, ?> builder =
                    GlideClientConfiguration.builder().address(address).useTLS(useTls);
            if (useTls) {
                TlsAdvancedConfiguration tlsConfig =
                        TlsAdvancedConfiguration.builder().rootCertificates(certificateBytes).build();
                builder.advancedConfiguration(
                        AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build());
            }
            return GlideClient.createClient(builder.build()).get();
        }
    }
}
