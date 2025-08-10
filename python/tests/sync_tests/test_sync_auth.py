# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


import time

import pytest
from glide_shared.config import ProtocolVersion
from glide_shared.constants import OK
from glide_shared.exceptions import RequestError
from glide_sync.glide_client import TGlideClient

from tests.utils.utils import (
    NEW_PASSWORD,
    USERNAME,
    WRONG_PASSWORD,
    auth_client,
    config_set_new_password,
    delete_acl_username_and_password,
    kill_connections,
    set_new_acl_username_with_password,
)


class TestSyncAuthCommands:
    """Test cases for password authentication and management"""

    @pytest.fixture(autouse=True, scope="function")
    def cleanup(self, request, management_sync_client: TGlideClient):
        """
        Ensure password is reset for default user and USERNAME user is deleted after each test, regardless of test outcome.
        This fixture runs after each test.
        """
        yield
        try:
            # reset password for default user
            auth_client(management_sync_client, NEW_PASSWORD)
            config_set_new_password(management_sync_client, "")
            management_sync_client.update_connection_password(None)

            # delete USERNAME user
            delete_acl_username_and_password(management_sync_client, USERNAME)
        except RequestError:
            pass

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing the connection password without immediate re-authentication.
        Verifies that:
        1. The client can update its internal password
        2. The client remains connected with current auth
        3. The client can reconnect using the new password after server password change
        This test is only for cluster mode, as standalone mode does not have a connection available handler
        """
        result = glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert glide_sync_client.set("test_key", "test_value") == OK
        value = glide_sync_client.get("test_key")
        assert value == b"test_value"
        config_set_new_password(glide_sync_client, NEW_PASSWORD)
        kill_connections(management_sync_client)
        # Add a short delay to allow the server to apply the new password
        # without this delay, command may or may not time out while the client reconnect
        # ending up with a flaky test
        time.sleep(2)
        # Verify that the client is able to reconnect with the new password,
        value = glide_sync_client.get("test_key")
        assert value == b"test_value"
        kill_connections(management_sync_client)
        time.sleep(2)
        # Verify that the client is able to immediateAuth with the new password after client is killed
        result = glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert glide_sync_client.set("test_key", "test_value") == OK

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_connection_lost_before_password_update(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test changing server password when connection is lost before password update.
        Verifies that the client will not be able to reach the inner core and return an error
        on immediate re-authentication, but will succeed with non-immediate re-auth
        """
        glide_sync_client.set("test_key", "test_value")
        config_set_new_password(glide_sync_client, NEW_PASSWORD)
        kill_connections(management_sync_client)
        time.sleep(2)
        result = glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )
        assert result == OK
        with pytest.raises(RequestError):
            glide_sync_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_no_server_auth(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test that immediate re-authentication fails when no server password is set.
        This verifies proper error handling when trying to re-authenticate with a
        password when the server has no password set.
        """
        with pytest.raises(RequestError):
            glide_sync_client.update_connection_password(
                WRONG_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_long(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing connection password with a long password string.
        Verifies that the client can handle long passwords (1000 characters).
        """
        long_password = "p" * 1000
        result = glide_sync_client.update_connection_password(
            long_password, immediate_auth=False
        )
        assert result == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_replace_password_immediate_auth_wrong_password(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test that re-authentication fails when using wrong password.
        Verifies proper error handling when immediate re-authentication is attempted
        with a password that doesn't match the server's password.
        """
        config_set_new_password(glide_sync_client, NEW_PASSWORD)
        with pytest.raises(RequestError):
            glide_sync_client.update_connection_password(
                WRONG_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_with_immediate_auth(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication.
        Verifies that:
        1. The client can update its password and re-authenticate immediately
        2. The client remains operational after re-authentication
        """
        config_set_new_password(glide_sync_client, NEW_PASSWORD)
        result = glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert glide_sync_client.set("test_key", "test_value") == OK
        value = glide_sync_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_auth_non_valid_pass(
        self, glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication using a non-valid password.
        Verifies that immediate re-authentication fails when the password is not valid.
        """
        with pytest.raises(RequestError):
            glide_sync_client.update_connection_password(None, immediate_auth=True)
        with pytest.raises(RequestError):
            glide_sync_client.update_connection_password("", immediate_auth=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_with_acl_user(
        self, acl_glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing the connection password for an ACL user without immediate re-authentication.
        and not the default one.
        Verifies that:
        1. The client can update its internal password for the ACL user
        2. The client remains connected with current auth
        3. The client can reconnect using the new password after server password change (which is simulated by
        deleting and reseting the user with a new password, which kills the connection).
        """

        # Create a new ACL user and authenticate the client as the new user
        acl_glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )

        # Verify that the client is authenticated
        assert acl_glide_sync_client.set("test_key", "test_value") == OK
        value = acl_glide_sync_client.get("test_key")
        assert value == b"test_value"

        # Delete the username and reset it with new password (equivalent to config_set new password)
        assert delete_acl_username_and_password(management_sync_client, USERNAME) == 1
        set_new_acl_username_with_password(
            management_sync_client, USERNAME, NEW_PASSWORD
        )

        # Sleep to allow enough time for reconnecting
        time.sleep(2)

        # The client should now reconnect with the new password automatically
        # Verify that the client is still able to perform operations
        value = acl_glide_sync_client.get("test_key")
        assert value == b"test_value"

        acl_glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )

        assert acl_glide_sync_client.set("new_key", "new_value") == OK
        value = acl_glide_sync_client.get("new_key")
        assert value == b"new_value"

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_reconnection_with_immediate_auth_with_acl_user(
        self, acl_glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication.
        Verifies that:
        1. Upon disconnection (which is caused by the user deletion), the client succeeds in re-authentication
            with the correct password.
        2. The client remains operational after re-authentication
        This test is relevant only for cluster mode - in standalone, reconnection will fail and new requests for
        the server won't be served.
        """
        assert delete_acl_username_and_password(management_sync_client, USERNAME) == 1
        set_new_acl_username_with_password(
            management_sync_client, USERNAME, NEW_PASSWORD
        )

        # Sleep to allow enough time for reconnecting
        time.sleep(2)

        # This command right after disconnection requires the acl_glide_sync_client to have a request timeout of 2000 ms
        # for full matrix tests to pass (otherwise failing on linux-aarch64 architecture).
        # TODO: We do not fully understand why such a long timeout is required.
        result = acl_glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )
        assert result == OK

        # Verify client is authenticated
        assert acl_glide_sync_client.set("test_key", "test_value") == OK
        value = acl_glide_sync_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_connection_lost_before_password_update_acl_user(
        self, acl_glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication.
        Verifies that:
        1. Upon disconnection (which is caused by the user deletion), the client succeeds in updating the password
        with non-immediate auth (this is an internal operation not requiring a server connection).
        2. Trying to connect with immediate authentication fails due to reconnection attempts with the previous password.
        This test is relevant only for standalone - in standalone, reconnection will fail and new requests for
        the server won't be served.
        """
        assert delete_acl_username_and_password(management_sync_client, USERNAME) == 1
        set_new_acl_username_with_password(
            management_sync_client, USERNAME, NEW_PASSWORD
        )

        # ensure client disconnection
        time.sleep(2)

        with pytest.raises(RequestError):
            acl_glide_sync_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_replace_password_immediateAuth_acl_user(
        self, acl_glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Tests adding a new password to the user, verifies that the client succeeds in immediate authentication with it.
        """
        set_new_acl_username_with_password(
            management_sync_client, USERNAME, NEW_PASSWORD
        )

        result = acl_glide_sync_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )

        assert result == OK

        assert acl_glide_sync_client.set("test_key", "test_value") == OK
        value = acl_glide_sync_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_auth_non_valid_pass_acl_user(
        self, acl_glide_sync_client: TGlideClient, management_sync_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication using a non-valid password.
        Verifies that immediate re-authentication fails when the password is not valid.
        """
        with pytest.raises(RequestError):
            acl_glide_sync_client.update_connection_password(None, immediate_auth=True)
        with pytest.raises(RequestError):
            acl_glide_sync_client.update_connection_password("", immediate_auth=True)
