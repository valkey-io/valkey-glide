/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;

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
                () -> new String[] {"test_access_key", "test_secret_key", "test_session_token"};

        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.ELASTICACHE)
                        .region("us-east-1")
                        .credentialsProvider(provider)
                        .build();

        assertNotNull(iamConfig.getCredentialsProvider());
        // Invoke the provider and verify the returned credentials
        String[] creds = iamConfig.getCredentialsProvider().getCredentials();
        assertEquals(3, creds.length);
        assertEquals("test_access_key", creds[0]);
        assertEquals("test_secret_key", creds[1]);
        assertEquals("test_session_token", creds[2]);
    }

    @Test
    public void testIamWithCustomCredentialsProviderNullSessionToken() throws Exception {
        // Long-term credentials without a session token
        GlideCredentialProvider provider =
                () -> new String[] {"test_access_key", "test_secret_key", null};

        IamAuthConfig iamConfig =
                IamAuthConfig.builder()
                        .clusterName("my-cluster")
                        .service(ServiceType.MEMORYDB)
                        .region("eu-west-1")
                        .credentialsProvider(provider)
                        .build();

        assertNotNull(iamConfig.getCredentialsProvider());
        String[] creds = iamConfig.getCredentialsProvider().getCredentials();
        assertEquals(3, creds.length);
        assertNull(creds[2]);
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
}
