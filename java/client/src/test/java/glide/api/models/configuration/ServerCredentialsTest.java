package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import glide.api.models.exceptions.ConfigurationError;
import org.junit.jupiter.api.Test;

class ServerCredentialsTest {

    @Test
    void buildWithPasswordOnly() {
        ServerCredentials credentials =
                ServerCredentials.builder().password("secret").username("user").build();
        assertEquals("secret", credentials.getPassword());
    }

    @Test
    void buildWithoutPasswordOrIamFails() {
        assertThrows(
                ConfigurationError.class,
                () -> ServerCredentials.builder().username("user").build());
    }

    @Test
    void buildWithIamRequiresUsername() {
        IamAuthConfig config =
                IamAuthConfig.builder()
                        .clusterName("cluster")
                        .region("us-east-1")
                        .service(ServiceType.ELASTICACHE)
                        .build();
        assertThrows(
                ConfigurationError.class,
                () -> ServerCredentials.builder().iamAuthConfig(config).build());
    }

    @Test
    void buildWithPasswordAndIamFails() {
        IamAuthConfig config =
                IamAuthConfig.builder()
                        .clusterName("cluster")
                        .region("us-east-1")
                        .service(ServiceType.ELASTICACHE)
                        .build();
        assertThrows(
                ConfigurationError.class,
                () ->
                        ServerCredentials.builder()
                                .username("user")
                                .password("secret")
                                .iamAuthConfig(config)
                                .build());
    }
}
