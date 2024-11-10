import pytest
from glide.config import ProtocolVersion
from glide.constants import OK
from glide.exceptions import RequestError
from glide.glide_client import GlideClient, GlideClusterClient, TGlideClient
from glide.routes import AllNodes

NEW_PASSWORD = "new_secure_password"
WRONG_PASSWORD = "wrong_password"


async def auth_client(client: TGlideClient, password):
    """
    Authenticates the given TGlideClient server connected.
    """
    if isinstance(client, GlideClient):
        await client.custom_command(["AUTH", password])
    if isinstance(client, GlideClusterClient):
        await client.custom_command(["AUTH", password], route=AllNodes())


async def config_set_new_password(client: TGlideClient, password):
    """
    Sets a new password for the given TGlideClient server connected.
    This function updates the server to require a new password.
    """
    if isinstance(client, GlideClient):
        await client.config_set({"requirepass": password})
    if isinstance(client, GlideClusterClient):
        await client.config_set({"requirepass": password}, route=AllNodes())


async def kill_connections(client: TGlideClient):
    """
    Kills all connections to the given TGlideClient server connected.
    """
    if isinstance(client, GlideClient):
        await client.custom_command(
            ["CLIENT", "KILL", "TYPE", "normal", "skipme", "no"]
        )
    if isinstance(client, GlideClusterClient):
        await client.custom_command(
            ["CLIENT", "KILL", "TYPE", "normal", "skipme", "no"], route=AllNodes()
        )


@pytest.mark.asyncio
class TestAuthCommands:
    """Test cases for password authentication and management"""

    @pytest.fixture(autouse=True)
    async def setup(self, glide_client: TGlideClient):
        """
        Teardown the test environment, make sure that theres no password set on the server side
        """
        try:
            await auth_client(glide_client, NEW_PASSWORD)
            await config_set_new_password(glide_client, "")
        except RequestError:
            pass
        yield
        try:
            await auth_client(glide_client, NEW_PASSWORD)
            await config_set_new_password(glide_client, "")
        except RequestError:
            pass

    @pytest.mark.parametrize("cluster_mode", [True])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password(self, glide_client: TGlideClient):
        """
        Test replacing the connection password without immediate re-authentication.
        Verifies that:
        1. The client can update its internal password
        2. The client remains connected with current auth
        3. The client can reconnect using the new password after server password change
        Currently, this test is only supported for cluster mode,
        since standalone mode dont have retry mechanism.
        """
        result = await glide_client.update_connection_password(
            NEW_PASSWORD, re_auth=False
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert await glide_client.set("test_key", "test_value") == OK
        value = await glide_client.get("test_key")
        assert value == b"test_value"
        await config_set_new_password(glide_client, NEW_PASSWORD)
        await kill_connections(glide_client)
        # Verify that the client is able to reconnect with the new password
        value = await glide_client.get("test_key")
        assert value == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_no_server_auth(
        self, glide_client: TGlideClient
    ):
        """
        Test that immediate re-authentication fails when no server password is set.
        This verifies proper error handling when trying to re-authenticate with a
        password when the server has no password set.
        """
        with pytest.raises(RequestError):
            await glide_client.update_connection_password(WRONG_PASSWORD, re_auth=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_long(self, glide_client: TGlideClient):
        """
        Test replacing connection password with a long password string.
        Verifies that the client can handle long passwords (1000 characters).
        """
        long_password = "p" * 1000
        result = await glide_client.update_connection_password(
            long_password, re_auth=False
        )
        assert result == OK

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_replace_password_reauth_wrong_password(
        self, glide_client: TGlideClient
    ):
        """
        Test that re-authentication fails when using wrong password.
        Verifies proper error handling when immediate re-authentication is attempted
        with a password that doesn't match the server's password.
        """
        await config_set_new_password(glide_client, NEW_PASSWORD)
        with pytest.raises(RequestError):
            await glide_client.update_connection_password(WRONG_PASSWORD, re_auth=True)

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_with_reauth(
        self, glide_client: TGlideClient
    ):
        """
        Test replacing connection password with immediate re-authentication.
        Verifies that:
        1. The client can update its password and re-authenticate immediately
        2. The client remains operational after re-authentication
        """
        await config_set_new_password(glide_client, NEW_PASSWORD)
        result = await glide_client.update_connection_password(
            NEW_PASSWORD, re_auth=True
        )
        assert result == OK
        # Verify that the client is still authenticated
        assert await glide_client.set("test_key", "test_value") == OK
        value = await glide_client.get("test_key")
        assert value == b"test_value"
