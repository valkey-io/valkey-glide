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
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().clientCertificate(CERT).build());

        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class,
                        () -> TlsConfigHelper.extractClientCertificate(configuration));
        assertTrue(error.getMessage().contains("mTLS requires both"));
    }

    @Test
    void keyWithoutCertificateThrows() {
        GlideClientConfiguration configuration =
                configWithTls(TlsAdvancedConfiguration.builder().clientKey(KEY).build());

        ConfigurationError error =
                assertThrows(
                        ConfigurationError.class, () -> TlsConfigHelper.extractClientKey(configuration));
        assertTrue(error.getMessage().contains("mTLS requires both"));
    }

    @Test
    void emptyCertificateThrows() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .clientCertificate(new byte[0])
                                .clientKey(KEY)
                                .build());

        assertThrows(
                ConfigurationError.class, () -> TlsConfigHelper.extractClientCertificate(configuration));
    }

    @Test
    void emptyKeyThrows() {
        GlideClientConfiguration configuration =
                configWithTls(
                        TlsAdvancedConfiguration.builder()
                                .clientCertificate(CERT)
                                .clientKey(new byte[0])
                                .build());

        assertThrows(ConfigurationError.class, () -> TlsConfigHelper.extractClientKey(configuration));
    }
}
