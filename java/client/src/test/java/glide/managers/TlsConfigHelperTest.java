/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.*;

import connection_request.ConnectionRequestOuterClass.ClientCertReloadConfig;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class TlsConfigHelperTest {

    private static final byte[] CERT = "client-cert".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = "client-key".getBytes(StandardCharsets.UTF_8);

    private static GlideClientConfiguration configWithTls(TlsAdvancedConfiguration tlsConfig) {
        return GlideClientConfiguration.builder()
                .advancedConfiguration(
                        AdvancedGlideClientConfiguration.builder().tlsAdvancedConfiguration(tlsConfig).build())
                .build();
    }

    @Test
    void extractClientCertificateAndKeyReturnsConfiguredValues() {
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().useMutualTls(CERT, KEY).build());

        assertArrayEquals(CERT, TlsConfigHelper.extractClientCertificate(configuration));
        assertArrayEquals(KEY, TlsConfigHelper.extractClientKey(configuration));
    }

    @Test
    void extractClientCertificateReturnsNullWhenNotConfigured() {
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().build());

        assertNull(TlsConfigHelper.extractClientCertificate(configuration));
        assertNull(TlsConfigHelper.extractClientKey(configuration));
    }

    @Test
    void extractClientCertificateReturnsNullWhenNoAdvancedConfiguration() {
        GlideClientConfiguration configuration = GlideClientConfiguration.builder().build();

        assertNull(TlsConfigHelper.extractClientCertificate(configuration));
        assertNull(TlsConfigHelper.extractClientKey(configuration));
    }

    @Test
    void extractCertPathsReturnsConfiguredValues() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key")
                                .build());

        assertEquals("/certs/client.pem", TlsConfigHelper.extractClientCertPath(configuration));
        assertEquals("/certs/client.key", TlsConfigHelper.extractClientKeyPath(configuration));
    }

    @Test
    void extractCertPathReturnsNullWhenNotConfigured() {
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().build());

        assertNull(TlsConfigHelper.extractClientCertPath(configuration));
        assertNull(TlsConfigHelper.extractClientKeyPath(configuration));
    }

    @Test
    void certReloadEnabledReturnsConfiguredValue() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key", 60)
                                .build());

        assertTrue(TlsConfigHelper.isCertReloadEnabled(configuration));
        assertEquals(60, TlsConfigHelper.extractCertReloadIntervalSeconds(configuration));
    }

    // Static byte-based mTLS does not request reload and configures no paths.
    @Test
    void certReloadDisabledForStaticMutualTls() {
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().useMutualTls(CERT, KEY).build());

        assertFalse(TlsConfigHelper.isCertReloadEnabled(configuration));
        assertNull(TlsConfigHelper.extractCertReloadIntervalSeconds(configuration));
    }

    // Deferred cadence: reload is requested with a null interval, so the core chooses the cadence.
    // The helper reports enabled but returns no interval, so ConnectionManager sends enabled=true
    // with interval_seconds unset.
    @Test
    void certReloadWithDeferredIntervalReportsEnabledWithoutInterval() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key")
                                .build());

        assertTrue(TlsConfigHelper.isCertReloadEnabled(configuration));
        assertNull(TlsConfigHelper.extractCertReloadIntervalSeconds(configuration));
    }

    // Wire behavior: no reload requested (static byte-based mTLS) -> no cert_reload config is built.
    @Test
    void buildCertReloadConfigReturnsNullWhenNoReload() {
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().useMutualTls(CERT, KEY).build());

        assertNull(TlsConfigHelper.buildCertReloadConfig(configuration));
    }

    // Wire behavior: deferred interval -> enabled=true and interval_seconds left unset, so the core
    // applies its own default cadence.
    @Test
    void buildCertReloadConfigDeferredSetsEnabledWithoutInterval() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key")
                                .build());

        ClientCertReloadConfig reloadConfig = TlsConfigHelper.buildCertReloadConfig(configuration);

        assertNotNull(reloadConfig);
        assertTrue(reloadConfig.getEnabled());
        assertFalse(reloadConfig.hasIntervalSeconds());
    }

    // Wire behavior: explicit interval -> enabled=true and interval_seconds set to the override.
    @Test
    void buildCertReloadConfigExplicitSetsEnabledAndInterval() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .useMutualTlsWithReload("/certs/client.pem", "/certs/client.key", 90)
                                .build());

        ClientCertReloadConfig reloadConfig = TlsConfigHelper.buildCertReloadConfig(configuration);

        assertNotNull(reloadConfig);
        assertTrue(reloadConfig.getEnabled());
        assertTrue(reloadConfig.hasIntervalSeconds());
        assertEquals(90, reloadConfig.getIntervalSeconds());
    }
}
