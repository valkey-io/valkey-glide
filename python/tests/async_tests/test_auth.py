# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


from typing import AsyncGenerator

import anyio
import pytest
from glide.glide_client import TGlideClient
from glide_shared.config import (
    BackoffStrategy,
    IamAuthConfig,
    ProtocolVersion,
    ServerCredentials,
    ServiceType,
)
from glide_shared.constants import OK
from glide_shared.exceptions import ClosingError, RequestError

from tests.async_tests import conftest as _async_conftest
from tests.async_tests.conftest import (
    _client_pool,
    _client_pool_lock,
    _get_worker_id,
    create_client,
)
from tests.constants import (
    IAM_DEFAULT_REFRESH_INTERVAL_SECONDS,
    IAM_TEST_CLUSTER_NAME,
    IAM_TEST_REGION_US_EAST_1,
    IAM_USERNAME,
)
from tests.utils.utils import (
    INITIAL_PASSWORD,
    NEW_PASSWORD,
    USERNAME,
    WRONG_PASSWORD,
    assert_connected,
    auth_client,
    config_set_new_password,
    delete_acl_username_and_password,
    kill_connections_tolerant,
    set_new_acl_username_with_password,
    wait_for,
)

# Reconnect budget for the disconnect-and-recover flow in
# test_update_connection_password. In cluster mode a CLIENT KILL is fanned out
# over AllNodes and, under heavy full-matrix CI contention, the full-cluster
# reconnect + re-auth can take considerably longer than on an unloaded host,
# so cluster mode gets a wider tolerance.
_CLUSTER_RECONNECT_TIMEOUT_SEC = 90
_STANDALONE_RECONNECT_TIMEOUT_SEC = 30

# Test-local reconnect backoff for the auth suite. Shorter than the library
# default so the disconnect-and-recover flow finishes within test tolerance
# under CI contention.
_AUTH_RECONNECT_STRATEGY = BackoffStrategy(
    num_of_retries=5,
    factor=25,
    exponent_base=2,
)


def _auth_cluster_for(cluster_mode: bool, use_tls: bool):
    """Route auth-test clients to the dedicated auth cluster in cluster mode.

    Standalone tests keep the shared standalone cluster; there is no
    dedicated standalone auth cluster because the isolation problem only
    materializes with CLIENT KILL fan-out over cluster nodes.

    Under --cluster-endpoints (externally-provided cluster) the dedicated
    auth twins are never provisioned by conftest, so fall back to None and
    let create_client_config route to the shared cluster.
    """
    if not cluster_mode:
        return None
    attr = "valkey_auth_tls_cluster" if use_tls else "valkey_auth_cluster"
    return getattr(pytest, attr, None)


@pytest.fixture(scope="function")
async def management_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> AsyncGenerator[TGlideClient, None]:
    """Override: management client for the auth suite. Routes cluster-mode
    clients to the dedicated auth cluster and applies the tightened
    _AUTH_RECONNECT_STRATEGY."""
    use_tls = request.config.getoption("--tls")
    valkey_cluster = _auth_cluster_for(cluster_mode, use_tls)
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        lazy_connect=False,
        reconnect_strategy=_AUTH_RECONNECT_STRATEGY,
        valkey_cluster=valkey_cluster,
    )
    try:
        yield client
    finally:
        await client.close()
        await _async_conftest.test_teardown(
            request, cluster_mode, protocol, valkey_cluster=valkey_cluster
        )


@pytest.fixture(scope="function")
async def glide_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> AsyncGenerator[TGlideClient, None]:
    """Override: primary client under test for the auth suite. Bypasses the
    session-wide pool because auth tests routinely kill connections and
    rotate passwords, routes cluster-mode clients to the dedicated auth
    cluster, and applies the tightened _AUTH_RECONNECT_STRATEGY."""
    use_tls = request.config.getoption("--tls")
    valkey_cluster = _auth_cluster_for(cluster_mode, use_tls)
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        request_timeout=5000,
        lazy_connect=False,
        reconnect_strategy=_AUTH_RECONNECT_STRATEGY,
        valkey_cluster=valkey_cluster,
    )
    try:
        yield client
    finally:
        await client.close()


@pytest.fixture(scope="function")
async def acl_glide_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    management_client: TGlideClient,
) -> AsyncGenerator[TGlideClient, None]:
    """Override: ACL-user client for the auth suite. Routes cluster-mode
    clients to the dedicated auth cluster and applies the tightened
    _AUTH_RECONNECT_STRATEGY."""
    await set_new_acl_username_with_password(
        management_client, USERNAME, INITIAL_PASSWORD
    )

    use_tls = request.config.getoption("--tls")
    valkey_cluster = _auth_cluster_for(cluster_mode, use_tls)
    client = await create_client(
        request,
        cluster_mode,
        protocol=protocol,
        credentials=ServerCredentials(username=USERNAME, password=INITIAL_PASSWORD),
        request_timeout=2000,
        lazy_connect=False,
        reconnect_strategy=_AUTH_RECONNECT_STRATEGY,
        valkey_cluster=valkey_cluster,
    )
    try:
        yield client
    finally:
        await client.close()
        await _async_conftest.test_teardown(
            request, cluster_mode, protocol, valkey_cluster=valkey_cluster
        )


async def create_iam_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    refresh_interval_seconds: int = IAM_DEFAULT_REFRESH_INTERVAL_SECONDS,
):
    """Helper to create a client with IAM authentication."""
    iam_config = IamAuthConfig(
        cluster_name=IAM_TEST_CLUSTER_NAME,
        service=ServiceType.ELASTICACHE,
        region=IAM_TEST_REGION_US_EAST_1,
        refresh_interval_seconds=refresh_interval_seconds,
    )

    credentials = ServerCredentials(username=IAM_USERNAME, iam_config=iam_config)

    # Note: use_tls is set from request which respects the --tls flag
    return await create_client(
        request=request,
        cluster_mode=cluster_mode,
        protocol=protocol,
        credentials=credentials,
    )


async def _run_cleanup_ops(management_client: TGlideClient):
    """Run auth-fixture cleanup ops; return the first failure description or None."""
    failed_op = None

    try:
        await auth_client(management_client, NEW_PASSWORD)
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = f"auth_client: {e}"

    try:
        await config_set_new_password(management_client, "")
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = failed_op or f"config_set_new_password(''): {e}"

    try:
        await management_client.update_connection_password(None)
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = failed_op or f"update_connection_password(None): {e}"

    try:
        await delete_acl_username_and_password(management_client, USERNAME)
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = failed_op or f"delete_acl_username_and_password: {e}"

    return failed_op


async def _probe_cluster_wedge(management_client: TGlideClient) -> bool:
    """Return True if the management client cannot reach the cluster within 5s."""

    async def _probe():
        try:
            await management_client.get("__auth_cleanup_probe__")
            return True
        except Exception:
            return False

    try:
        await wait_for(_probe, "cluster probe failed after auth cleanup", timeout=5)
        return False
    except (TimeoutError, RequestError, ClosingError):
        return True


def _evict_pooled_async_client(request) -> None:
    """Evict the pooled glide_client so subsequent tests get a fresh one."""
    try:
        cluster_mode = request.getfixturevalue("cluster_mode")
        protocol = request.getfixturevalue("protocol")
        cache_key = (_get_worker_id(), cluster_mode, protocol)
        with _client_pool_lock:
            _client_pool.pop(cache_key, None)
    except Exception:
        pass


@pytest.mark.anyio
class TestAuthCommands:
    """Test cases for password authentication and management"""

    @pytest.fixture(autouse=True, scope="function")
    async def cleanup(self, request, management_client: TGlideClient):
        """
        Ensure password is reset for default user and USERNAME user is deleted
        after each test. Per-op typed excepts so a wedge at any single step is
        attributed rather than swallowed; a wedge probe at the end evicts the
        pooled client and fails loudly so subsequent tests don't inherit a
        poisoned auth cluster.
        """
        yield

        failed_op = await _run_cleanup_ops(management_client)
        wedge_detected = await _probe_cluster_wedge(management_client)

        if failed_op is not None or wedge_detected:
            _evict_pooled_async_client(request)
            pytest.fail(
                f"auth cleanup left cluster wedged: failed_op={failed_op!r}, "
                f"wedge_probe_timed_out={wedge_detected}. Requirepass state unknown; "
                f"subsequent tests may inherit poisoned cluster."
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_accepts_new_password(
        self,
        glide_client: TGlideClient,
        management_client: TGlideClient,
        cluster_mode: bool,
    ):
        """
        Property: update_connection_password(new, immediate_auth=False) is a
        pure client-side state change. The client accepts the new stored
        password without disturbing the current session (which is still
        authenticated with the old server-side password).

        Verifies:
          1. Return value is OK.
          2. The still-live session accepts a subsequent SET.
          3. The still-live session accepts a subsequent GET.

        No CLIENT KILL, no server-side password change. Fast, deterministic,
        no reconnect exposure. Applies in both cluster and standalone modes.
        """
        assert (
            await glide_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=False
            )
            == OK
        )
        assert await glide_client.set("test_key", "test_value") == OK
        assert await glide_client.get("test_key") == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_survives_reconnect(
        self,
        glide_client: TGlideClient,
        management_client: TGlideClient,
        cluster_mode: bool,
    ):
        """
        Property: after a password rotation, if the connection is dropped and
        the server now requires the new password, the client recovers
        automatically using its stored new password.

        Verifies:
          - update_connection_password(new, immediate_auth=False)
          - server-side rotate: CONFIG SET requirepass new
          - server-side disconnect: CLIENT KILL (one kill only)
          - client recovers: eventual GET returns the pre-kill value

        Applies in both cluster and standalone modes; cluster mode uses a
        wider reconnect budget to absorb topology refresh.
        """
        reconnect_timeout = (
            _CLUSTER_RECONNECT_TIMEOUT_SEC
            if cluster_mode
            else _STANDALONE_RECONNECT_TIMEOUT_SEC
        )
        assert await glide_client.set("test_key", "test_value") == OK
        await glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )
        await config_set_new_password(glide_client, NEW_PASSWORD)
        await kill_connections_tolerant(management_client)

        async def _reconnected():
            try:
                return await glide_client.get("test_key") == b"test_value"
            except Exception:
                return False

        await wait_for(
            _reconnected,
            "Client did not recover with stored new password after reconnect",
            timeout=reconnect_timeout,
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_immediate_auth_survives_reconnect(
        self,
        glide_client: TGlideClient,
        management_client: TGlideClient,
        cluster_mode: bool,
    ):
        """
        Property: after a password rotation and a disconnect, calling
        update_connection_password(new, immediate_auth=True) succeeds against
        the reconnected client, and the client continues to accept traffic
        with the new password.

        Verifies:
          - update_connection_password(new, immediate_auth=False)
          - server-side rotate: CONFIG SET requirepass new
          - server-side disconnect: CLIENT KILL (one kill only)
          - update_connection_password(new, immediate_auth=True) returns OK
          - subsequent SET succeeds

        Applies in both cluster and standalone modes.
        """
        reconnect_timeout = (
            _CLUSTER_RECONNECT_TIMEOUT_SEC
            if cluster_mode
            else _STANDALONE_RECONNECT_TIMEOUT_SEC
        )
        await glide_client.update_connection_password(
            NEW_PASSWORD, immediate_auth=False
        )
        await config_set_new_password(glide_client, NEW_PASSWORD)
        await kill_connections_tolerant(management_client)

        async def _immediate_auth_after_reconnect():
            try:
                return (
                    await glide_client.update_connection_password(
                        NEW_PASSWORD, immediate_auth=True
                    )
                    == OK
                )
            except Exception:
                return False

        await wait_for(
            _immediate_auth_after_reconnect,
            "Client did not accept immediate-auth after reconnect",
            timeout=reconnect_timeout,
        )
        assert await glide_client.set("test_key", "test_value") == OK

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_update_connection_password_connection_lost_before_password_update(
        self, management_client: TGlideClient, request, cluster_mode, protocol
    ):
        """
        Test changing server password when connection is lost before password update.
        Verifies that the client will not be able to reach the inner core and return an error
        on immediate re-authentication, but will succeed with non-immediate re-auth
        """
        # Use a fresh client (not pooled) since this test kills and expects it to stay dead
        test_client = await create_client(
            request, cluster_mode, protocol=protocol, request_timeout=5000
        )
        try:
            await test_client.set("test_key", "test_value")
            await config_set_new_password(test_client, NEW_PASSWORD)
            await kill_connections_tolerant(management_client)
            # Wait for glide-core to detect dead connection and fail reconnect
            # (reconnects with old/empty password, which server now rejects)
            await anyio.sleep(2)
            result = await test_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=False
            )
            assert result == OK
            with pytest.raises(RequestError):
                await test_client.update_connection_password(
                    NEW_PASSWORD, immediate_auth=True
                )
        finally:
            try:
                await test_client.close()
            except Exception:
                pass

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

        # This command right after disconnection requires the acl_glide_client to have a request timeout of 2000 ms
        # for full matrix tests to pass (otherwise failing on linux-aarch64 architecture).
        # TODO: We do not fully understand why such a long timeout is required.
        # Retry during reconnection - non-blocking reconnect may still be in progress
        max_retries = 20
        for i in range(max_retries):
            try:
                result = await acl_glide_client.update_connection_password(
                    NEW_PASSWORD, immediate_auth=True
                )
                assert result == OK
                break
            except Exception as e:
                if (
                    "AllConnectionsUnavailable" in str(e)
                    or "Connection in recovery" in str(e)
                ) and i < max_retries - 1:
                    await anyio.sleep(0.5)
                    continue
                raise

        # Verify client is authenticated - retry during reconnection
        for i in range(max_retries):
            try:
                assert await acl_glide_client.set("test_key", "test_value") == OK
                break
            except Exception as e:
                if (
                    "AllConnectionsUnavailable" in str(e)
                    or "Connection in recovery" in str(e)
                ) and i < max_retries - 1:
                    await anyio.sleep(0.5)
                    continue
                raise
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

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_iam_authentication_with_mock_credentials(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Test IAM authentication using mock AWS credentials.

        This test verifies:
        1. Client can connect using IAM authentication with mock credentials
        2. Basic operations work after IAM authentication
        3. Operations continue to work after token refresh
        """
        client = await create_iam_client(request, cluster_mode, protocol)

        # Verify connection works
        await assert_connected(client)

        # Test basic operations
        await client.set("iam_test_key", "iam_test_value")
        value = await client.get("iam_test_key")
        assert value == b"iam_test_value"

        # Test manual token refresh
        await client.refresh_iam_token()

        # Verify operations still work after token refresh
        await client.set("iam_test_key2", "iam_test_value2")
        value2 = await client.get("iam_test_key2")
        assert value2 == b"iam_test_value2"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    async def test_iam_authentication_automatic_token_refresh(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Test automatic IAM token refresh.

        This test verifies that the client automatically refreshes the IAM token
        at the configured interval and continues to work correctly.
        """
        client = await create_iam_client(
            request, cluster_mode, protocol, refresh_interval_seconds=2
        )

        # Verify initial connection
        await assert_connected(client)

        # Wait for automatic token refresh to occur
        await anyio.sleep(3)

        # Verify client still works after automatic refresh
        await client.set("iam_auto_refresh_key", "iam_auto_refresh_value")
        value = await client.get("iam_auto_refresh_key")
        assert value == b"iam_auto_refresh_value"
