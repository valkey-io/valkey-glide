/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.standalone;

import static glide.Constants.IP_ADDRESS_V4;
import static glide.Constants.IP_ADDRESS_V6;
import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static glide.TestUtilities.IAM_USERNAME;
import static glide.TestUtilities.assertConnected;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.deleteAclUser;
import static glide.TestUtilities.getRandomString;
import static glide.TestUtilities.setNewAclUserPassword;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.TestUtilities;
import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20) // seconds
public class StandaloneClientTests {

    @Test
    @SneakyThrows
    public void register_client_name_and_version() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();

        String info = (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get();
        assertTrue(info.contains("lib-name=GlideJava"));
        assertTrue(info.contains("lib-ver=unknown"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void can_connect_with_auth_require_pass() {
        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();

        String password = "TEST_AUTH";
        client.configSet(Collections.singletonMap("requirepass", password)).get();

        // Creation of a new client without a password should fail
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> GlideClient.createClient(commonClientConfig().build()).get());
        assertInstanceOf(ClosingException.class, exception.getCause());

        // Creation of a new client with credentials
        GlideClient auth_client =
                GlideClient.createClient(
                                commonClientConfig()
                                        .credentials(ServerCredentials.builder().password(password).build())
                                        .build())
                        .get();

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, auth_client.set(key, value).get());
        assertEquals(value, auth_client.get(key).get());

        // Reset password
        client.configSet(Collections.singletonMap("requirepass", "")).get();

        auth_client.close();
        client.close();
    }

    @Test
    @SneakyThrows
    public void can_connect_with_auth_acl() {
        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();

        String username = "testuser";
        String password = "TEST_AUTH";
        assertEquals(
                OK,
                client
                        .customCommand(
                                new String[] {
                                    "ACL",
                                    "SETUSER",
                                    username,
                                    "on",
                                    "allkeys",
                                    "+get",
                                    "+cluster",
                                    "+ping",
                                    "+info",
                                    "+client",
                                    ">" + password,
                                })
                        .get());

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, client.set(key, value).get());

        // Creation of a new client with credentials
        GlideClient testUserClient =
                GlideClient.createClient(
                                commonClientConfig()
                                        .credentials(
                                                ServerCredentials.builder().username(username).password(password).build())
                                        .build())
                        .get();

        assertEquals(value, testUserClient.get(key).get());
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> testUserClient.set("foo", "bar").get());
        assertInstanceOf(RequestException.class, executionException.getCause());

        client.customCommand(new String[] {"ACL", "DELUSER", username}).get();

        testUserClient.close();
        client.close();
    }

    @Test
    @SneakyThrows
    public void select_standalone_database_id() {
        GlideClient client = GlideClient.createClient(commonClientConfig().databaseId(4).build()).get();

        String clientInfo = (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get();
        assertTrue(clientInfo.contains("db=4"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void client_name() {
        GlideClient client =
                GlideClient.createClient(commonClientConfig().clientName("TEST_CLIENT_NAME").build()).get();

        String clientInfo = (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get();
        assertTrue(clientInfo.contains("name=TEST_CLIENT_NAME"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void closed_client_throws_ExecutionException_with_ClosingException_as_cause() {
        GlideClient client = GlideClient.createClient(commonClientConfig().build()).get();

        client.close();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.set("key", "value").get());
        assertInstanceOf(ClosingException.class, executionException.getCause());
    }

    @Test
    @SneakyThrows
    public void update_connection_password_auth_non_valid_pass() {
        // Test Client fails on call to updateConnectionPassword with invalid parameters
        try (GlideClient testClient = GlideClient.createClient(commonClientConfig().build()).get()) {
            ExecutionException emptyPasswordException =
                    assertThrows(
                            ExecutionException.class, () -> testClient.updateConnectionPassword("", true).get());
            assertInstanceOf(RequestException.class, emptyPasswordException.getCause());

            ExecutionException noPasswordException =
                    assertThrows(
                            ExecutionException.class, () -> testClient.updateConnectionPassword(true).get());
            assertInstanceOf(RequestException.class, noPasswordException.getCause());
        }
    }

    @Test
    @SneakyThrows
    public void update_connection_password_no_server_auth() {
        String pwd = UUID.randomUUID().toString();

        try (GlideClient testClient = GlideClient.createClient(commonClientConfig().build()).get()) {
            // validate that we can use the client
            assertNotNull(testClient.info().get());

            // Test that immediate re-authentication fails when no server password is set.
            ExecutionException exception =
                    assertThrows(
                            ExecutionException.class, () -> testClient.updateConnectionPassword(pwd, true).get());
            assertInstanceOf(RequestException.class, exception.getCause());
        }
    }

    @Test
    @SneakyThrows
    public void update_connection_password_long() {
        String pwd = RandomStringUtils.randomAlphabetic(1000);

        try (GlideClient testClient = GlideClient.createClient(commonClientConfig().build()).get()) {
            // validate that we can use the client
            assertNotNull(testClient.info().get());

            // Test replacing connection password with a long password string.
            assertEquals(OK, testClient.updateConnectionPassword(pwd, false).get());
        }
    }

    @Timeout(50)
    @Test
    @SneakyThrows
    public void replace_password_immediateAuth_wrong_password() {
        String pwd = UUID.randomUUID().toString();
        String notThePwd = UUID.randomUUID().toString();

        GlideClient adminClient = GlideClient.createClient(commonClientConfig().build()).get();
        try (GlideClient testClient = GlideClient.createClient(commonClientConfig().build()).get()) {
            // validate that we can use the client
            assertNotNull(testClient.info().get());

            // set the password to something else
            adminClient.configSet(Collections.singletonMap("requirepass", notThePwd)).get();

            // Test that re-authentication fails when using wrong password.
            ExecutionException exception =
                    assertThrows(
                            ExecutionException.class, () -> testClient.updateConnectionPassword(pwd, true).get());
            assertInstanceOf(RequestException.class, exception.getCause());

            // But using something else password returns OK
            assertEquals(OK, testClient.updateConnectionPassword(notThePwd, true).get());
        } finally {
            adminClient.configSet(Collections.singletonMap("requirepass", "")).get();
            adminClient.close();
        }
    }

    @Timeout(50)
    @Test
    @SneakyThrows
    public void test_update_connection_password_acl_user() {
        String username = "username";
        String pwd = UUID.randomUUID().toString();
        String newPwd = UUID.randomUUID().toString();

        GlideClient adminClient = GlideClient.createClient(commonClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClient testClient =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .credentials(
                                                    ServerCredentials.builder().username(username).password(pwd).build())
                                            .requestTimeout(5000)
                                            .build())
                            .get();

            // Validate client works
            assertNotNull(testClient.info().get());

            // Update the password of the client with non immediate auth
            assertEquals(OK, testClient.updateConnectionPassword(newPwd, false).get());

            // Delete the user (which will cause reconnection) and reset it with the new password
            deleteAclUser(adminClient, username);
            setNewAclUserPassword(adminClient, username, newPwd);

            // Sleep to ensure password change in server and client reconnection
            Thread.sleep(2000);

            // Validate client reconnected succsessfuly
            assertNotNull(testClient.info().get());

            // Verify immediate auth with the same password works
            assertEquals(OK, testClient.updateConnectionPassword(newPwd, true).get());

            // Validate client still working
            assertNotNull(testClient.info().get());

        } finally {
            deleteAclUser(adminClient, username);
            adminClient.close();
        }
    }

    @Timeout(50)
    @Test
    @SneakyThrows
    public void test_update_connection_password_connection_lost_before_password_update_acl_user() {
        String username = "username";
        String pwd = UUID.randomUUID().toString();
        String newPwd = UUID.randomUUID().toString();

        GlideClient adminClient = GlideClient.createClient(commonClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClient testClient =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .credentials(
                                                    ServerCredentials.builder().username(username).password(pwd).build())
                                            .requestTimeout(5000)
                                            .build())
                            .get();

            // Validate client works
            assertNotNull(testClient.info().get());

            // Delete user name and reset with new  password (this will cause disconnection)
            deleteAclUser(adminClient, username);
            setNewAclUserPassword(adminClient, username, newPwd);

            // Sleep to ensure password change in server and client reconnection
            Thread.sleep(2000);

            // Ensure client can still update the password with non-immediate auth (this doesn't require
            // server connection)
            assertEquals(OK, testClient.updateConnectionPassword(newPwd, false).get());

            // Check that the client is unable to perform operations that require server connection,
            // as it is still trying to reconnect with the old password
            ExecutionException timeoutException =
                    assertThrows(
                            ExecutionException.class,
                            () -> testClient.updateConnectionPassword(newPwd, true).get());

        } finally {
            deleteAclUser(adminClient, username);
            adminClient.close();
        }
    }

    @Timeout(50)
    @Test
    @SneakyThrows
    public void test_update_connection_password_replace_password_immediateAuth_acl_user() {
        String username = "username";
        String pwd = UUID.randomUUID().toString();
        String newPwd = UUID.randomUUID().toString();

        GlideClient adminClient = GlideClient.createClient(commonClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClient testClient =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .credentials(
                                                    ServerCredentials.builder().username(username).password(pwd).build())
                                            .build())
                            .get();

            // Validate client works
            assertNotNull(testClient.info().get());

            // Add a new password to the client
            setNewAclUserPassword(adminClient, username, newPwd);

            // Ensure client can authenticate immediately with the new password
            assertEquals(OK, testClient.updateConnectionPassword(newPwd, true).get());

        } finally {
            deleteAclUser(adminClient, username);
            adminClient.close();
        }
    }

    @Timeout(50)
    @Test
    @SneakyThrows
    public void test_update_connection_password_non_valid_auth_acl_user() {
        String username = "username";
        String pwd = UUID.randomUUID().toString();
        String newPwd = UUID.randomUUID().toString();

        GlideClient adminClient = GlideClient.createClient(commonClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClient testClient =
                    GlideClient.createClient(
                                    commonClientConfig()
                                            .credentials(
                                                    ServerCredentials.builder().username(username).password(pwd).build())
                                            .build())
                            .get();

            ExecutionException emptyPasswordException =
                    assertThrows(
                            ExecutionException.class, () -> testClient.updateConnectionPassword("", true).get());
            assertInstanceOf(RequestException.class, emptyPasswordException.getCause());

            ExecutionException noPasswordException =
                    assertThrows(
                            ExecutionException.class, () -> testClient.updateConnectionPassword(true).get());
            assertInstanceOf(RequestException.class, noPasswordException.getCause());
        } finally {
            deleteAclUser(adminClient, username);
            adminClient.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {IP_ADDRESS_V4, IP_ADDRESS_V6})
    @SneakyThrows
    public void test_connect_with_ip_address_succeeds(String ipAddress) {
        Integer port = Integer.parseInt(STANDALONE_HOSTS[0].split(":")[1]);
        NodeAddress address = NodeAddress.builder().host(ipAddress).port(port).build();
        GlideClientConfiguration config =
                GlideClientConfiguration.builder().address(address).useTLS(false).build();

        try (GlideClient client = GlideClient.createClient(config).get()) {
            assertConnected(client);
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
    public void test_iam_authentication_with_mock_credentials() {
        // See DEVELOPER.md for instructions on running IAM authentication tests

        // Create client with IAM authentication
        try (GlideClient client = createStandaloneClientWithIam(5)) {

            // Verify connection works
            assertConnected(client);

            // Test basic operations
            assertEquals("OK", client.set("iam_test_key", "iam_test_value").get());
            assertEquals("iam_test_value", client.get("iam_test_key").get());

            // Test manual token refresh
            client.refreshIamToken().get();

            // Verify operations still work after token refresh
            assertEquals("OK", client.set("iam_test_key2", "iam_test_value2").get());
            assertEquals("iam_test_value2", client.get("iam_test_key2").get());
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
    public void test_iam_authentication_automatic_token_refresh()
            throws InterruptedException, ExecutionException {
        // NOTE: See test_iam_authentication_with_mock_credentials for setup instructions

        try (GlideClient client = createStandaloneClientWithIam(2)) {

            // Verify initial connection
            assertConnected(client);

            // Wait for automatic token refresh to occur
            Thread.sleep(3000);

            // Verify client still works after automatic refresh
            assertEquals("OK", client.set("iam_auto_refresh_key", "iam_auto_refresh_value").get());
            assertEquals("iam_auto_refresh_value", client.get("iam_auto_refresh_key").get());
        }
    }

    @SneakyThrows
    private GlideClient createStandaloneClientWithIam(int refreshIntervalSeconds) {
        IamAuthConfig iamConfig = TestUtilities.createTestIamConfig(refreshIntervalSeconds);
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        // Note: useTLS is inherited from commonClientConfig() which respects the -Dtls system property
        return GlideClient.createClient(commonClientConfig().credentials(credentials).build()).get();
    }
}
