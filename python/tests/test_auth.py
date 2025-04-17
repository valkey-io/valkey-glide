# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


import anyio
import pytest

from glide.config import ProtocolVersion
from glide.constants import OK
from glide.exceptions import RequestError
from glide.glide_client import TGlideClient
from tests.conftest import (
    NEW_PASSWORD,
    USERNAME,
    WRONG_PASSWORD,
    auth_client,
    config_set_new_password,
    kill_connections,
)
from tests.utils.utils import (
    delete_acl_username_and_password,
    set_new_acl_username_with_password,
)


@pytest.mark.anyio
class TestAuthCommands:
    """Test cases for password authentication and management"""

    @pytest.fixture(autouse=True, scope="function")
    async def cleanup(self, request, management_client: TGlideClient):
        """
        Ensure password is reset for default user and USERNAME user is deleted after each test, regardless of test outcome.
        This fixture runs after each test.
        """
        yield
        try:
            # reset password for default user
            await auth_client(management_client, NEW_PASSWORD)
            await config_set_new_password(management_client, "")
            await management_client.update_connection_password(None)

            # delete USERNAME user
            await delete_acl_username_and_password(management_client, USERNAME)
        except RequestError:
            pass

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test replacing the connection password without immediate re-authentication.
        Verifies that:
        1. The client can update its internal password
        2. The client remains connected with current auth
        3. The client can reconnect using the new password after server password change
        This test is only for cluster mode, as standalone mode does not have a connection available handler
        """
        result = await glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert await glide_client.set("test_key", "test_value") == OK
        value = await glide_client.get("test_key")
        assert value == b"test_value"
        await config_set_new_password(glide_client, NEW_PASSWORD)
        await kill_connections(management_client)
        # Add a short delay to allow the server to apply the new password
        # without this delay, command may or may not time out while the client reconnect
        # ending up with a flaky test
        await anyio.sleep(2)
        # Verify that the client is able to reconnect with the new password,
        value = await glide_client.get("test_key")
        assert value == b"test_value"
        await kill_connections(management_client)
        await anyio.sleep(2)
        # Verify that the client is able to immediateAuth with the new password after client is killed
        result = await glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert await glide_client.set("test_key", "test_value") == OK

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_connection_lost_before_password_update(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test changing server password when connection is lost before password update.
        Verifies that the client will not be able to reach the inner core and return an error
        on immediate re-authentication, but will succeed with non-immediate re-auth
        """
        await glide_client.set("test_key", "test_value")
        await config_set_new_password(glide_client, NEW_PASSWORD)
        await kill_connections(management_client)
        await anyio.sleep(2)
        result = await glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )
        assert result == OK
        with pytest.raises(RequestError):
            await glide_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_no_server_auth(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test that immediate re-authentication fails when no server password is set.
        This verifies proper error handling when trying to re-authenticate with a
        password when the server has no password set.
        """
        with pytest.raises(RequestError):
            await glide_client.update_connection_password(
                WRONG_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_long(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test replacing connection password with a long password string.
        Verifies that the client can handle long passwords (1000 characters).
        """
        long_password = "p" * 1000
        result = await glide_client.update_connection_password(
            long_password, immediate_auth=False
        )
        assert result == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_replace_password_immediate_auth_wrong_password(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test that re-authentication fails when using wrong password.
        Verifies proper error handling when immediate re-authentication is attempted
        with a password that doesn't match the server's password.
        """
        await config_set_new_password(glide_client, NEW_PASSWORD)
        with pytest.raises(RequestError):
            await glide_client.update_connection_password(
                WRONG_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_with_immediate_auth(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication.
        Verifies that:
        1. The client can update its password and re-authenticate immediately
        2. The client remains operational after re-authentication
        """
        await config_set_new_password(glide_client, NEW_PASSWORD)
        result = await glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert await glide_client.set("test_key", "test_value") == OK
        value = await glide_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_auth_non_valid_pass(
        self, glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication using a non-valid password.
        Verifies that immediate re-authentication fails when the password is not valid.
        """
        with pytest.raises(RequestError):
            await glide_client.update_connection_password(None, immediate_auth=True)
        with pytest.raises(RequestError):
            await glide_client.update_connection_password("", immediate_auth=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_with_acl_user(
        self, acl_glide_client: TGlideClient, management_client: TGlideClient
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
        await acl_glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )

        # Verify that the client is authenticated
        assert await acl_glide_client.set("test_key", "test_value") == OK
        value = await acl_glide_client.get("test_key")
        assert value == b"test_value"

        # Delete the username and reset it with new password (equivalent to config_set new password)
        assert await delete_acl_username_and_password(management_client, USERNAME) == 1
        await set_new_acl_username_with_password(
            management_client, USERNAME, NEW_PASSWORD
        )

        # Sleep to allow enough time for reconnecting
        await anyio.sleep(2)

        # The client should now reconnect with the new password automatically
        # Verify that the client is still able to perform operations
        value = await acl_glide_client.get("test_key")
        assert value == b"test_value"

        await acl_glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )

        assert await acl_glide_client.set("new_key", "new_value") == OK
        value = await acl_glide_client.get("new_key")
        assert value == b"new_value"

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_reconnection_with_immediate_auth_with_acl_user(
        self, acl_glide_client: TGlideClient, management_client: TGlideClient
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
        assert await delete_acl_username_and_password(management_client, USERNAME) == 1
        await set_new_acl_username_with_password(
            management_client, USERNAME, NEW_PASSWORD
        )

        # Sleep to allow enough time for reconnecting
        await anyio.sleep(2)

        result = await acl_glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )
        assert result == OK

        # Verify client is authenticated
        assert await acl_glide_client.set("test_key", "test_value") == OK
        value = await acl_glide_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_connection_lost_before_password_update_acl_user(
        self, acl_glide_client: TGlideClient, management_client: TGlideClient
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
        assert await delete_acl_username_and_password(management_client, USERNAME) == 1
        await set_new_acl_username_with_password(
            management_client, USERNAME, NEW_PASSWORD
        )

        # ensure client disconnection
        await anyio.sleep(2)

        with pytest.raises(RequestError):
            await acl_glide_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=True
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_replace_password_immediateAuth_acl_user(
        self, acl_glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Tests adding a new password to the user, verifies that the client succeeds in immediate authentication with it.
        """
        await set_new_acl_username_with_password(
            management_client, USERNAME, NEW_PASSWORD
        )

        result = await acl_glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=True
        )

        assert result == OK

        assert await acl_glide_client.set("test_key", "test_value") == OK
        value = await acl_glide_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_auth_non_valid_pass_acl_user(
        self, acl_glide_client: TGlideClient, management_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication using a non-valid password.
        Verifies that immediate re-authentication fails when the password is not valid.
        """
        with pytest.raises(RequestError):
            await acl_glide_client.update_connection_password(None, immediate_auth=True)
        with pytest.raises(RequestError):
            await acl_glide_client.update_connection_password("", immediate_auth=True)
