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
}
