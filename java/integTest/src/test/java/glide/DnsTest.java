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
import glide.api.models.configuration.GlideClusterClientConfiguration;
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
    // server. See '../utils/cluster_manager.py' for details.
    private static final String HOSTNAME_TLS = "valkey.glide.test.tls.com";
    private static final String HOSTNAME_NO_TLS = "valkey.glide.test.no_tls.com";

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConnectWithValidHostnameNoTls(boolean clusterMode) {
        BaseClient client = buildClient(clusterMode, false, HOSTNAME_NO_TLS);
        assertConnected(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConnectWithInvalidHostnameNoTls(boolean clusterMode) {
        assertThrows(Exception.class, () -> buildClient(clusterMode, false, "nonexistent.invalid"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTlsConnectWithHostnameInCert(boolean clusterMode) {
        BaseClient client = buildClient(clusterMode, true, HOSTNAME_TLS);
        assertConnected(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTlsConnectWithHostnameNotInCert(boolean clusterMode) {
        assertThrows(Exception.class, () -> buildClient(clusterMode, true, HOSTNAME_NO_TLS));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTlsConnectWithIpv4(boolean clusterMode) {
        BaseClient client = buildClient(clusterMode, true, "127.0.0.1");
        assertConnected(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testTlsConnectWithIpv6(boolean clusterMode) {
        BaseClient client = buildClient(clusterMode, true, "::1");
        assertConnected(client);
    }

    // -------------------
    // Helper methods
    // -------------------

    private static NodeAddress buildAddress(boolean clusterMode, boolean useTls, String host) {
        String[] hosts =
                useTls
                        ? (clusterMode
                                ? TestConfiguration.CLUSTER_TLS_HOSTS
                                : TestConfiguration.STANDALONE_TLS_HOSTS)
                        : (clusterMode ? TestConfiguration.CLUSTER_HOSTS : TestConfiguration.STANDALONE_HOSTS);
        int port = Integer.parseInt(hosts[0].trim().split(":")[1]);
        return NodeAddress.builder().host(host).port(port).build();
    }

    @SneakyThrows
    private static BaseClient buildClient(boolean clusterMode, boolean useTls, String host) {
        NodeAddress address = buildAddress(clusterMode, useTls, host);
        if (clusterMode) {
            var builder = GlideClusterClientConfiguration.builder().address(address).useTLS(useTls);
            if (useTls) {
                TlsAdvancedConfiguration tlsConfig =
                        TlsAdvancedConfiguration.builder().rootCertificates(getCaCertificate()).build();
                builder.advancedConfiguration(
                        AdvancedGlideClusterClientConfiguration.builder()
                                .tlsAdvancedConfiguration(tlsConfig)
                                .build());
            }
            return GlideClusterClient.createClient(builder.build()).get();
        } else {
            var builder = GlideClientConfiguration.builder().address(address).useTLS(useTls);
            if (useTls) {
                TlsAdvancedConfiguration tlsConfig =
                        TlsAdvancedConfiguration.builder().rootCertificates(getCaCertificate()).build();
                builder.advancedConfiguration(
                        AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build());
            }
            return GlideClient.createClient(builder.build()).get();
        }
    }

    @SneakyThrows
    private static void assertConnected(BaseClient client) {
        if (client instanceof GlideClusterClient) {
            assertEquals("PONG", ((GlideClusterClient) client).ping().get());
        } else {
            assertEquals("PONG", ((GlideClient) client).ping().get());
        }
    }
}
