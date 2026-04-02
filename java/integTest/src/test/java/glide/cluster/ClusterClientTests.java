/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.cluster;

import static glide.Constants.IP_ADDRESS_V4;
import static glide.Constants.IP_ADDRESS_V6;
import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestUtilities.IAM_USERNAME;
import static glide.TestUtilities.assertConnected;
import static glide.TestUtilities.commonClusterClientConfig;
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
import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
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

@Timeout(10) // seconds
public class ClusterClientTests {

    @Test
    @SneakyThrows
    public void register_client_name_and_version() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        String info =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(info.contains("lib-name=GlideJava"));
        assertTrue(info.contains("lib-ver=unknown"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void can_connect_with_auth_requirepass() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        String password = "TEST_AUTH";
        client.customCommand(new String[] {"CONFIG", "SET", "requirepass", password}).get();

        // Creation of a new client without a password should fail
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> GlideClusterClient.createClient(commonClusterClientConfig().build()).get());
        assertInstanceOf(ClosingException.class, exception.getCause());

        // Creation of a new client with credentials
        GlideClusterClient auth_client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
                                        .credentials(ServerCredentials.builder().password(password).build())
                                        .build())
                        .get();

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, auth_client.set(key, value).get());
        assertEquals(value, auth_client.get(key).get());

        // Reset password
        client.customCommand(new String[] {"CONFIG", "SET", "requirepass", ""}).get();

        auth_client.close();
        client.close();
    }

    @Test
    @SneakyThrows
    public void can_connect_with_auth_acl() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

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
                        .get()
                        .getSingleValue());

        String key = getRandomString(10);
        String value = getRandomString(10);

        assertEquals(OK, client.set(key, value).get());

        // Creation of a new cluster client with credentials
        GlideClusterClient testUserClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig()
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
    public void client_name() {
        GlideClusterClient client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().clientName("TEST_CLIENT_NAME").build())
                        .get();

        String clientInfo =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(clientInfo.contains("name=TEST_CLIENT_NAME"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void select_cluster_database_id() {
        String minVersion = "9.0.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().databaseId(4).build()).get();

        String clientInfo =
                (String) client.customCommand(new String[] {"CLIENT", "INFO"}).get().getSingleValue();
        assertTrue(clientInfo.contains("db=4"));

        client.close();
    }

    @Test
    @SneakyThrows
    public void closed_client_throws_ExecutionException_with_ClosingException_as_cause() {
        GlideClusterClient client =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        client.close();
        ExecutionException executionException =
                assertThrows(ExecutionException.class, () -> client.set("foo", "bar").get());
        assertInstanceOf(ClosingException.class, executionException.getCause());
    }

    @Test
    @SneakyThrows
    public void test_update_connection_password() {
        GlideClusterClient adminClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        String pwd = UUID.randomUUID().toString();

        try (GlideClusterClient testClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get()) {
            // validate that we can use the client
            assertNotNull(testClient.info().get());

            // Update password without re-authentication
            assertEquals(OK, testClient.updateConnectionPassword(pwd, false).get());

            // Verify client still works with old auth
            assertNotNull(testClient.info().get());

            // Update server password
            // Kill all other clients to force reconnection
            assertEquals("OK", adminClient.configSet(Collections.singletonMap("requirepass", pwd)).get());
            adminClient.customCommand(new String[] {"CLIENT", "KILL", "TYPE", "NORMAL"}).get();

            // Give some time for it to go through
            Thread.sleep(1000);

            // Verify client auto-reconnects with new password
            assertNotNull(testClient.info().get());
        } finally {
            adminClient.configSet(Collections.singletonMap("requirepass", "")).get();
            adminClient.close();
        }
    }

    @Test
    @SneakyThrows
    public void test_update_connection_password_auth_non_valid_pass() {
        // Test Client fails on call to updateConnectionPassword with invalid parameters
        try (GlideClusterClient testClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get()) {
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
    public void test_update_connection_password_no_server_auth() {
        String pwd = UUID.randomUUID().toString();

        try (GlideClusterClient testClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get()) {
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
    public void test_update_connection_password_long() {
        String pwd = RandomStringUtils.randomAlphabetic(1000);

        try (GlideClusterClient testClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get()) {
            // validate that we can use the client
            assertNotNull(testClient.info().get());

            // Test replacing connection password with a long password string.
            assertEquals(OK, testClient.updateConnectionPassword(pwd, false).get());
        }
    }

    @Timeout(50)
    @Test
    @SneakyThrows
    public void test_replace_password_immediateAuth_wrong_password() {
        String pwd = UUID.randomUUID().toString();
        String notThePwd = UUID.randomUUID().toString();

        GlideClusterClient adminClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();
        try (GlideClusterClient testClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get()) {
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

        GlideClusterClient adminClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClusterClient testClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
                                            .credentials(
                                                    ServerCredentials.builder().username(username).password(pwd).build())
                                            .build())
                            .get();

            // Validate client works
            assertNotNull(testClient.info().get());

            // Update the password of the client with non immediate auth
            assertEquals(OK, testClient.updateConnectionPassword(newPwd, false).get());

            // Delete the user (which will cause reconnection) and reset it with the new password
            deleteAclUser(adminClient, username);

            // Give some time for the delete to fully complete
            Thread.sleep(1000);

            setNewAclUserPassword(adminClient, username, newPwd);

            // Sleep to ensure password change in server and client reconnection
            Thread.sleep(1000);

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
    public void test_update_connection_password_reconnection_with_immediate_auth_with_acl_user() {
        String username = "username";
        String pwd = UUID.randomUUID().toString();
        String newPwd = UUID.randomUUID().toString();

        GlideClusterClient adminClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClusterClient testClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
                                            .credentials(
                                                    ServerCredentials.builder().username(username).password(pwd).build())
                                            .build())
                            .get();

            // Validate client works
            assertNotNull(testClient.info().get());

            // Delete user name and reset with new  password (this will cause disconnection)
            deleteAclUser(adminClient, username);
            setNewAclUserPassword(adminClient, username, newPwd);

            // Sleep to ensure password change in server and client reconnection
            Thread.sleep(1000);

            // Ensure client can reconnect when updating the password with immediate auth
            // Retry during reconnection - non-blocking reconnect may still be in progress
            int maxRetries = 20;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    assertEquals(OK, testClient.updateConnectionPassword(newPwd, true).get());
                    break;
                } catch (Exception e) {
                    if (e.getMessage() != null
                            && e.getMessage().contains("AllConnectionsUnavailable")
                            && i < maxRetries - 1) {
                        Thread.sleep(500);
                        continue;
                    }
                    throw e;
                }
            }

            // Validate client reconnected and is working - retry during reconnection
            for (int i = 0; i < maxRetries; i++) {
                try {
                    assertNotNull(testClient.info().get());
                    break;
                } catch (Exception e) {
                    if (e.getMessage() != null
                            && e.getMessage().contains("AllConnectionsUnavailable")
                            && i < maxRetries - 1) {
                        Thread.sleep(500);
                        continue;
                    }
                    throw e;
                }
            }
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

        GlideClusterClient adminClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClusterClient testClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
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

            // Validate client is working
            assertNotNull(testClient.info().get());
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

        GlideClusterClient adminClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        try {
            setNewAclUserPassword(adminClient, username, pwd);

            // Create client with ACL user credentials
            GlideClusterClient testClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig()
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
        Integer port = Integer.parseInt(CLUSTER_HOSTS[0].split(":")[1]);
        NodeAddress address = NodeAddress.builder().host(ipAddress).port(port).build();
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder().address(address).useTLS(false).build();

        try (GlideClusterClient client = GlideClusterClient.createClient(config).get()) {
            assertConnected(client);
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
    public void test_iam_authentication_with_mock_credentials() {
        // See DEVELOPER.md for instructions on running IAM authentication tests

        // Create client with IAM authentication
        try (GlideClusterClient client = createClusterClientWithIam(5)) {

            // Verify connection works
            assertConnected(client);

            // Test basic operations
            assertEquals("OK", client.set("iam_test_key", "iam_test_value").get());
            assertEquals("iam_test_value", client.get("iam_test_key").get());

            // Verify operations still work after token refresh
            assertEquals("OK", client.set("iam_test_key2", "iam_test_value2").get());
            assertEquals("iam_test_value2", client.get("iam_test_key2").get());

            // Test manual token refresh
            client.refreshIamToken().get();
        }
    }

    @Test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
    public void test_iam_authentication_automatic_token_refresh()
            throws InterruptedException, ExecutionException {
        // NOTE: See test_iam_authentication_with_mock_credentials for setup instructions

        try (GlideClusterClient client = createClusterClientWithIam(2)) {

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
    private GlideClusterClient createClusterClientWithIam(int refreshIntervalSeconds) {
        IamAuthConfig iamConfig = TestUtilities.createTestIamConfig(refreshIntervalSeconds);
        ServerCredentials credentials =
                ServerCredentials.builder().username(IAM_USERNAME).iamConfig(iamConfig).build();
        // Note: useTLS is inherited from commonClusterClientConfig() which respects the -Dtls system
        // property
        return GlideClusterClient.createClient(
                        commonClusterClientConfig().credentials(credentials).build())
                .get();
    }
}
