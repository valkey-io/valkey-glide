/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.api.models.exceptions.ConfigurationError;
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
                configWithTls(
                        TlsAdvancedConfiguration.builder().clientCertificate(CERT).clientKey(KEY).build());

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
    void certificateWithoutKeyThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder().clientCertificate(CERT).build());
        assertTrue(error.getMessage().contains("mTLS requires both"));
    }

    @Test
    void keyWithoutCertificateThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder().clientKey(KEY).build());
        assertTrue(error.getMessage().contains("mTLS requires both"));
    }

    @Test
    void emptyCertificateThrows() {
        assertThrows(
                ConfigurationError.class,
                () -> TlsAdvancedConfiguration.builder()
                        .clientCertificate(new byte[0])
                        .clientKey(KEY)
                        .build());
    }

    @Test
    void emptyKeyThrows() {
        assertThrows(
                ConfigurationError.class,
                () -> TlsAdvancedConfiguration.builder()
                        .clientCertificate(CERT)
                        .clientKey(new byte[0])
                        .build());
    }

    @Test
    void extractCertPathsReturnsConfiguredValues() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .clientCertPath("/certs/client.pem")
                                .clientKeyPath("/certs/client.key")
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
    void certPathWithoutKeyPathThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder()
                                .clientCertPath("/certs/client.pem")
                                .build());
        assertTrue(error.getMessage().contains("mTLS requires"));
    }

    @Test
    void keyPathWithoutCertPathThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder()
                                .clientKeyPath("/certs/client.key")
                                .build());
        assertTrue(error.getMessage().contains("mTLS requires"));
    }

    @Test
    void mixingCertPathAndCertBytesThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder()
                                .clientCertPath("/certs/client.pem")
                                .clientKeyPath("/certs/client.key")
                                .clientCertificate(CERT)
                                .build());
        assertTrue(error.getMessage().contains("cannot both be provided"));
    }

    @Test
    void certReloadEnabledReturnsConfiguredValue() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .clientCertPath("/certs/client.pem")
                                .clientKeyPath("/certs/client.key")
                                .certReloadEnabled(true)
                                .certReloadIntervalSeconds(60)
                                .build());

        assertTrue(TlsConfigHelper.isCertReloadEnabled(configuration));
        assertEquals(60, TlsConfigHelper.extractCertReloadIntervalSeconds(configuration));
    }

    @Test
    void certReloadDisabledByDefault() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .clientCertPath("/certs/client.pem")
                                .clientKeyPath("/certs/client.key")
                                .build());

        assertFalse(TlsConfigHelper.isCertReloadEnabled(configuration));
        assertNull(TlsConfigHelper.extractCertReloadIntervalSeconds(configuration));
    }

    @Test
    void certReloadWithoutCertPathThrows() {
        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsAdvancedConfiguration.builder().certReloadEnabled(true).build());
        assertTrue(error.getMessage().contains("certReloadEnabled"));
    }
}
