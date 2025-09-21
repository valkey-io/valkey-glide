/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ServerCredentialsTest {

    @Test
    void buildWithPasswordOnly() {
        ServerCredentials credentials = ServerCredentials.builder().password("secret").build();
        assertEquals("secret", credentials.getPassword());
    }

    @Test
    void buildWithUsernameAndPassword() {
        ServerCredentials credentials =
                ServerCredentials.builder().username("user").password("secret").build();
        assertEquals("user", credentials.getUsername());
        assertEquals("secret", credentials.getPassword());
    }
}
