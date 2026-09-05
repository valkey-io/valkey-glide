/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ServerCredentialsTest {

    @Test
    public void testPasswordBasedCredentials() {
        ServerCredentials credentials =
                ServerCredentials.builder().username("testUser").password("testPassword").build();

        assertEquals("testUser", credentials.getUsername());
        assertEquals("testPassword", credentials.getPassword());
        assertNull(credentials.getIamConfig());
    }

    @Test
    public void testPasswordOnlyCredentials() {
        ServerCredentials credentials = ServerCredentials.builder().password("testPassword").build();

        assertNull(credentials.getUsername());
        assertEquals("testPassword", credentials.getPassword());
        assertNull(credentials.getIamConfig());
    }

    @Test
    public void testIamBasedCredentials() {
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .build();

        ServerCredentials credentials =
                ServerCredentials.builder().username("iamUser").iamConfig(iamConfig).build();

        assertEquals("iamUser", credentials.getUsername());
        assertNull(credentials.getPassword());
        assertNotNull(credentials.getIamConfig());
        assertEquals("my-cluster", credentials.getIamConfig().getClusterName());
        assertEquals(ServiceType.ELASTICACHE, credentials.getIamConfig().getService());
        assertEquals("us-east-1", credentials.getIamConfig().getRegion());
        assertEquals(null, credentials.getIamConfig().getRefreshIntervalSeconds());
    }

    @Test
    public void testIamWithCustomRefreshInterval() {
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.MEMORYDB)
                        .region("us-west-2")
                        .refreshIntervalSeconds(600)
                        .build();

        ServerCredentials credentials =
                ServerCredentials.builder().username("iamUser").iamConfig(iamConfig).build();

        assertEquals(600, credentials.getIamConfig().getRefreshIntervalSeconds());
        assertEquals(ServiceType.MEMORYDB, credentials.getIamConfig().getService());
    }

    @Test
    public void testMutualExclusivityPasswordAndIam() {
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            ServerCredentials.builder()
                                    .username("testUser")
                                    .password("testPassword")
                                    .iamConfig(iamConfig)
                                    .build();
                        });

        assertTrue(exception.getMessage().contains("mutually exclusive"));
    }

    @Test
    public void testIamRequiresUsername() {
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            ServerCredentials.builder().iamConfig(iamConfig).build();
                        });

        assertTrue(exception.getMessage().contains("username is required for IAM"));
    }

    @Test
    public void testIamConfigRequiredFields() {
        // Test that all required fields must be provided
        assertThrows(
                NullPointerException.class,
                () -> {
                    IamAuthConfig.builder()
                            .service(ServiceType.ELASTICACHE)
                            .region("us-east-1")
                            .build(); // Missing clusterName
                });

        assertThrows(
                NullPointerException.class,
                () -> {
                    IamAuthConfig.builder()
                            .clusterName("my-cluster")
                            .region("us-east-1")
                            .build(); // Missing service
                });

        assertThrows(
                NullPointerException.class,
                () -> {
                    IamAuthConfig.builder()
                            .clusterName("my-cluster")
                            .service(ServiceType.ELASTICACHE)
                            .build(); // Missing region
                });
    }

    @Test
    public void testIamWithCustomCredentialsProvider() throws Exception {
        GlideCredentialProvider provider =
                () ->
                        AwsCredentials.builder()
                                .accessKeyId("test_access_key")
                                .secretAccessKey("test_secret_key")
                                .sessionToken("test_session_token")
                                .build();

        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .credentialsProvider(provider)
                        .build();

        assertNotNull(iamConfig.getCredentialsProvider());
        AwsCredentials creds = iamConfig.getCredentialsProvider().getCredentials();
        assertEquals("test_access_key", creds.getAccessKeyId());
        assertEquals("test_secret_key", creds.getSecretAccessKey());
        assertEquals("test_session_token", creds.getSessionToken());
    }

    @Test
    public void testIamWithCustomCredentialsProviderNullSessionToken() throws Exception {
        // Long-term credentials without a session token
        GlideCredentialProvider provider =
                () ->
                        AwsCredentials.builder()
                                .accessKeyId("test_access_key")
                                .secretAccessKey("test_secret_key")
                                .build(); // sessionToken omitted → null

        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.MEMORYDB)
                        .region("eu-west-1")
                        .credentialsProvider(provider)
                        .build();

        assertNotNull(iamConfig.getCredentialsProvider());
        AwsCredentials creds = iamConfig.getCredentialsProvider().getCredentials();
        assertEquals("test_access_key", creds.getAccessKeyId());
        assertEquals("test_secret_key", creds.getSecretAccessKey());
        assertNull(creds.getSessionToken());
    }

    @Test
    public void testIamWithNoCredentialsProviderDefaultsToNull() {
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .build();

        assertNull(iamConfig.getCredentialsProvider());
    }

    @Test
    public void testIamCredentialsProviderIsNotRequired() {
        // Builds without throwing even without credentialsProvider
        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .refreshIntervalSeconds(120)
                        .build();

        assertNull(iamConfig.getCredentialsProvider());
        assertEquals(120, iamConfig.getRefreshIntervalSeconds());
    }

    @Test
    public void testAwsCredentialsRejectsBlankAccessKeyId() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AwsCredentials.builder().accessKeyId("").secretAccessKey("secret").build());
        assertTrue(ex.getMessage().contains("accessKeyId"));
    }

    @Test
    public void testAwsCredentialsRejectsBlankSecretAccessKey() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AwsCredentials.builder().accessKeyId("key").secretAccessKey("").build());
        assertTrue(ex.getMessage().contains("secretAccessKey"));
    }

    @Test
    public void testAwsCredentialsWithExpiresAt() throws Exception {
        java.time.Instant expiry = java.time.Instant.now().plusSeconds(3600);
        GlideCredentialProvider provider =
                () ->
                        AwsCredentials.builder()
                                .accessKeyId("test_key")
                                .secretAccessKey("test_secret")
                                .expiresAt(expiry)
                                .build();
        AwsCredentials creds = provider.getCredentials();
        assertEquals("test_key", creds.getAccessKeyId());
        assertEquals("test_secret", creds.getSecretAccessKey());
        assertNull(creds.getSessionToken());
        assertEquals(expiry, creds.getExpiresAt());
    }

    @Test
    public void testAwsCredentialsExpiresAtDefaultsToNull() {
        AwsCredentials creds = AwsCredentials.builder().accessKeyId("k").secretAccessKey("s").build();
        assertNull(creds.getExpiresAt());
    }
}
