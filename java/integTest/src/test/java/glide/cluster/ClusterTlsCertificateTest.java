/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.TestUtilities.getCaCertificate;
import static org.junit.jupiter.api.Assertions.*;

import glide.TestUtilities;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.utils.CertificateDebugger;
import glide.utils.CertificateFormatValidator;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ClusterTlsCertificateTest {

    private static List<NodeAddress> clusterNodes;
    private static byte[] caCert;

    @BeforeAll
    static void setup() throws Exception {
        String clusterHosts = System.getProperty("test.server.cluster.tls", "");
        System.out.println("=== CLUSTER TLS TEST SETUP ===");
        System.out.println("Raw cluster hosts property: " + clusterHosts);
        String[] hosts = clusterHosts.split(",");
        System.out.println("Split into " + hosts.length + " hosts:");

        clusterNodes = new ArrayList<>();
        for (String host : hosts) {
            String[] parts = host.trim().split(":");
            NodeAddress node =
                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build();
            clusterNodes.add(node);
            System.out.println("  - " + parts[0] + ":" + parts[1]);
        }
        System.out.println("Total cluster nodes configured: " + clusterNodes.size());
        System.out.println("===============================");

        caCert = getCaCertificate();

        // Log certificate information during setup
        CertificateDebugger.logCertificateInfo(caCert, "Test Setup - CA Certificate Loaded");

        // Validate certificate format
        CertificateFormatValidator.ValidationResult validation =
                CertificateFormatValidator.validateFormat(caCert);
        validation.printReport();

        if (!validation.isValid()) {
            System.out.println("WARNING: CA certificate has validation errors!");
        }
    }

    @Test
    void testClusterTlsWithoutCertificateFails() throws Exception {
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder().addresses(clusterNodes).useTLS(true).build();

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }

    @Test
    void testClusterTlsWithSelfSignedCertificateSucceeds() throws Exception {
        System.out.println("\n=== TEST: testClusterTlsWithSelfSignedCertificateSucceeds ===");

        // Log certificate before creating client
        CertificateDebugger.logCertificateInfo(
                caCert, "Before Client Creation - Self-Signed Certificate");

        // Validate and generate diagnostic report
        System.out.println(CertificateFormatValidator.generateDiagnosticReport(caCert));

        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(caCert, clusterNodes);

        System.out.println("Creating cluster client with TLS configuration...");
        try {
            TestUtilities.createAndTestClient(config);
            System.out.println("Client creation and PING test: SUCCESS");
        } catch (Exception e) {
            System.out.println("Client creation or PING test: FAILED");
            System.out.println("Exception: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("Cause: " + e.getCause().getClass().getName());
                System.out.println("Cause message: " + e.getCause().getMessage());
            }
            throw e;
        }
        System.out.println("=== TEST COMPLETED ===\n");
    }

    @Test
    @Disabled("Temporarily disabled to isolate single test")
    void testClusterTlsWithMultipleCertificatesSucceeds() throws Exception {
        System.out.println("\n=== TEST: testClusterTlsWithMultipleCertificatesSucceeds ===");

        String caCertStr = new String(caCert, StandardCharsets.UTF_8);
        String multipleCerts = caCertStr + "\n" + caCertStr;
        byte[] certBundle = multipleCerts.getBytes(StandardCharsets.UTF_8);

        // Log certificate chain information
        CertificateDebugger.logCertificateChain(
                new byte[][] {caCert, caCert}, "Before Client Creation - Multiple Certificates");
        CertificateDebugger.logCertificateInfo(certBundle, "Concatenated Certificate Bundle");

        // Validate certificate chain
        CertificateFormatValidator.ValidationResult chainValidation =
                CertificateFormatValidator.validateChain(new byte[][] {caCert, caCert});
        chainValidation.printReport();

        // Validate concatenated bundle
        System.out.println(CertificateFormatValidator.generateDiagnosticReport(certBundle));

        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(certBundle, clusterNodes);

        System.out.println("Creating cluster client with multiple certificates...");
        try {
            TestUtilities.createAndTestClient(config);
            System.out.println("Client creation and PING test: SUCCESS");
        } catch (Exception e) {
            System.out.println("Client creation or PING test: FAILED");
            System.out.println("Exception: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            throw e;
        }
        System.out.println("=== TEST COMPLETED ===\n");
    }

    @Test
    void testClusterTlsWithEmptyCertificateArrayFails() throws Exception {
        byte[] emptyCaCert = new byte[0];
        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(emptyCaCert, clusterNodes);

        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
    }

    @Test
    void testClusterTlsWithInvalidCertificateFails() throws Exception {
        System.out.println("\n=== TEST: testClusterTlsWithInvalidCertificateFails ===");

        byte[] invalidCert =
                "-----BEGIN CERTIFICATE-----\nINVALID\n-----END CERTIFICATE-----".getBytes();

        // Log invalid certificate for debugging
        CertificateDebugger.logCertificateInfo(invalidCert, "Invalid Certificate (Expected to Fail)");

        GlideClusterClientConfiguration config =
                TestUtilities.createClusterConfigWithRootCert(invalidCert, clusterNodes);

        System.out.println("Attempting to create client with invalid certificate (should fail)...");
        assertThrows(
                Exception.class,
                () -> {
                    GlideClusterClient.createClient(config).get();
                });
        System.out.println("Client creation failed as expected: SUCCESS");
        System.out.println("=== TEST COMPLETED ===\n");
    }

    @Test
    @Disabled("Temporarily disabled to isolate single test")
    void testClusterTlsWithKeyStoreSucceeds() throws Exception {
        System.out.println("\n=== TEST: testClusterTlsWithKeyStoreSucceeds ===");

        // Log certificate before KeyStore creation
        CertificateDebugger.logCertificateInfo(caCert, "Before KeyStore Creation");

        Path keyStorePath = Files.createTempFile("test-keystore", ".jks");
        char[] password = "password".toCharArray();

        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, password);

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = null;
            try {
                cert = cf.generateCertificate(new ByteArrayInputStream(caCert));
                System.out.println("Certificate parsing: SUCCESS");
                System.out.println("Certificate type: " + cert.getType());
                System.out.println("Certificate class: " + cert.getClass().getName());
                CertificateDebugger.logParsingAttempt(caCert, true, null);
            } catch (Exception e) {
                System.out.println("Certificate parsing: FAILED");
                CertificateDebugger.logParsingAttempt(caCert, false, e.getMessage());
                throw e;
            }

            keyStore.setCertificateEntry("ca-cert", cert);

            try (FileOutputStream fos = new FileOutputStream(keyStorePath.toFile())) {
                keyStore.store(fos, password);
            }
            System.out.println("KeyStore created at: " + keyStorePath);

            TlsAdvancedConfiguration tlsConfig =
                    TlsAdvancedConfiguration.fromKeyStore(keyStorePath.toString(), password, "JKS");

            AdvancedGlideClusterClientConfiguration advancedConfig =
                    AdvancedGlideClusterClientConfiguration.builder()
                            .tlsAdvancedConfiguration(tlsConfig)
                            .build();

            GlideClusterClientConfiguration config =
                    GlideClusterClientConfiguration.builder()
                            .addresses(clusterNodes)
                            .useTLS(true)
                            .advancedConfiguration(advancedConfig)
                            .build();

            System.out.println("Creating cluster client with KeyStore configuration...");
            try {
                TestUtilities.createAndTestClient(config);
                System.out.println("Client creation and PING test: SUCCESS");
            } catch (Exception e) {
                System.out.println("Client creation or PING test: FAILED");
                System.out.println("Exception: " + e.getClass().getName());
                System.out.println("Message: " + e.getMessage());
                throw e;
            }
        } finally {
            Files.deleteIfExists(keyStorePath);
            System.out.println("KeyStore file cleaned up");
        }
        System.out.println("=== TEST COMPLETED ===\n");
    }
}
