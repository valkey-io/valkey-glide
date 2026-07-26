# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


import time
from typing import Generator

import pytest
from glide_shared.config import (
    BackoffStrategy,
    IamAuthConfig,
    ProtocolVersion,
    ServerCredentials,
    ServiceType,
)
from glide_shared.constants import OK
from glide_shared.exceptions import ClosingError, RequestError
from glide_sync.glide_client import TGlideClient

from tests.constants import (
    IAM_DEFAULT_REFRESH_INTERVAL_SECONDS,
    IAM_TEST_CLUSTER_NAME,
    IAM_TEST_REGION_US_EAST_1,
    IAM_USERNAME,
)
from tests.sync_tests import conftest as _sync_conftest
from tests.sync_tests.conftest import (
    _get_worker_id,
    _sync_client_pool,
    _sync_client_pool_lock,
    create_sync_client,
)
from tests.utils.utils import (
    INITIAL_PASSWORD,
    NEW_PASSWORD,
    USERNAME,
    WRONG_PASSWORD,
    assert_connected_sync,
    auth_client,
    config_set_new_password,
    delete_acl_username_and_password,
    set_new_acl_username_with_password,
    sync_kill_connections_tolerant,
    sync_wait_for,
)

# Reconnect budget for the disconnect-and-recover flow in
# test_update_connection_password. In cluster mode a CLIENT KILL is fanned out
# over AllNodes and, under heavy full-matrix CI contention, the full-cluster
# reconnect + re-auth can take considerably longer than on an unloaded host,
# so cluster mode gets a wider tolerance.
_CLUSTER_RECONNECT_TIMEOUT_SEC = 90
_STANDALONE_RECONNECT_TIMEOUT_SEC = 30

# Post-suite stabilization window for the SHARED main cluster. Trace evidence
# from workflow run 30183779084 shows that after this suite's AllNodes CLIENT
# KILL fan-out, the shared main-cluster client's multiplexer sees a topology
# disturbance that triggers a retry storm exactly when the next atomic RESP3
# ClusterBatch is in flight, and that batch's aggregator surfaces None. Waiting
# here for every primary on pytest.valkey_cluster to answer PING lets glide-core
# background slot-refresh finish before the next module runs. A fresh
# short-lived client is used so the shared pool is not contaminated.
_STABILIZATION_TIMEOUT_SEC = 5.0
_STABILIZATION_INTERVAL_SEC = 0.1


def _await_shared_cluster_stabilization_sync() -> None:
    """Ping every primary on the shared main cluster until quiescent or timeout.

    Best-effort: exceptions are swallowed so a still-recovering cluster does
    not fail the suite; the goal is a bounded settle window, not correctness.
    """
    import contextlib

    from glide_shared.config import (
        AdvancedGlideClusterClientConfiguration,
        GlideClusterClientConfiguration,
        NodeAddress,
    )
    from glide_sync import GlideClusterClient as SyncGlideClusterClient

    valkey_cluster = getattr(pytest, "valkey_cluster", None)
    if valkey_cluster is None:
        return

    nodes: list[NodeAddress] = list(valkey_cluster.nodes_addr)
    if not nodes:
        return

    deadline = time.monotonic() + _STABILIZATION_TIMEOUT_SEC
    while time.monotonic() < deadline:
        client = None
        try:
            config = GlideClusterClientConfiguration(
                addresses=nodes,
                request_timeout=1000,
                advanced_config=AdvancedGlideClusterClientConfiguration(
                    connection_timeout=2000,
                ),
            )
            client = SyncGlideClusterClient.create(config)
            reply = client.custom_command(["PING"])
            if reply is not None:
                return
        except Exception:
            pass
        finally:
            if client is not None:
                with contextlib.suppress(Exception):
                    client.close()
        time.sleep(_STABILIZATION_INTERVAL_SEC)


@pytest.fixture(autouse=True, scope="module")
def _stabilize_shared_cluster_after_sync_auth():
    """Module-scoped teardown that waits for the shared main cluster to settle
    after this suite's CLIENT KILL fan-out. Purely synchronous so pytest-anyio
    scoping rules never apply."""
    yield
    try:
        _await_shared_cluster_stabilization_sync()
    except Exception:
        pass


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
    """
    if not cluster_mode:
        return None
    return (
        pytest.valkey_auth_tls_cluster  # type: ignore[attr-defined]
        if use_tls
        else pytest.valkey_auth_cluster  # type: ignore[attr-defined]
    )


@pytest.fixture(scope="function")
def management_sync_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> Generator[TGlideClient, None, None]:
    """Override: management client for the sync auth suite. Routes cluster-mode
    clients to the dedicated auth cluster and applies the tightened
    _AUTH_RECONNECT_STRATEGY."""
    use_tls = request.config.getoption("--tls")
    valkey_cluster = _auth_cluster_for(cluster_mode, use_tls)
    client = create_sync_client(
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
        client.close()
        _sync_conftest.sync_test_teardown(
            request, cluster_mode, protocol, valkey_cluster=valkey_cluster
        )


@pytest.fixture(scope="function")
def glide_sync_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
) -> Generator[TGlideClient, None, None]:
    """Override: primary client under test for the sync auth suite. Bypasses
    the session-wide pool because auth tests routinely kill connections and
    rotate passwords, routes cluster-mode clients to the dedicated auth
    cluster, and applies the tightened _AUTH_RECONNECT_STRATEGY."""
    use_tls = request.config.getoption("--tls")
    valkey_cluster = _auth_cluster_for(cluster_mode, use_tls)
    client = create_sync_client(
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
        client.close()


@pytest.fixture(scope="function")
def acl_glide_sync_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    management_sync_client: TGlideClient,
) -> Generator[TGlideClient, None, None]:
    """Override: ACL-user client for the sync auth suite. Routes cluster-mode
    clients to the dedicated auth cluster and applies the tightened
    _AUTH_RECONNECT_STRATEGY."""
    set_new_acl_username_with_password(
        management_sync_client, USERNAME, INITIAL_PASSWORD
    )

    use_tls = request.config.getoption("--tls")
    valkey_cluster = _auth_cluster_for(cluster_mode, use_tls)
    client = create_sync_client(
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
        client.close()
        _sync_conftest.sync_test_teardown(
            request, cluster_mode, protocol, valkey_cluster=valkey_cluster
        )


def create_iam_client(
    request,
    cluster_mode: bool,
    protocol: ProtocolVersion,
    refresh_interval_seconds: int = IAM_DEFAULT_REFRESH_INTERVAL_SECONDS,
):
    """Helper to create a sync client with IAM authentication."""
    iam_config = IamAuthConfig(
        cluster_name=IAM_TEST_CLUSTER_NAME,
        service=ServiceType.ELASTICACHE,
        region=IAM_TEST_REGION_US_EAST_1,
        refresh_interval_seconds=refresh_interval_seconds,
    )

    credentials = ServerCredentials(username=IAM_USERNAME, iam_config=iam_config)

    # Note: use_tls is set from request which respects the --tls flag
    return create_sync_client(
        request=request,
        cluster_mode=cluster_mode,
        protocol=protocol,
        credentials=credentials,
    )


def _run_sync_cleanup_ops(management_sync_client: TGlideClient):
    """Run auth-fixture cleanup ops; return the first failure description or None."""
    failed_op = None

    try:
        auth_client(management_sync_client, NEW_PASSWORD)
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = f"auth_client: {e}"

    try:
        config_set_new_password(management_sync_client, "")
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = failed_op or f"config_set_new_password(''): {e}"

    try:
        management_sync_client.update_connection_password(None)
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = failed_op or f"update_connection_password(None): {e}"

    try:
        delete_acl_username_and_password(management_sync_client, USERNAME)
    except (RequestError, TimeoutError, ClosingError) as e:
        failed_op = failed_op or f"delete_acl_username_and_password: {e}"

    return failed_op


def _probe_sync_cluster_wedge(management_sync_client: TGlideClient) -> bool:
    """Return True if the management client cannot reach the cluster within 5s."""

    def _probe():
        try:
            management_sync_client.get("__auth_cleanup_probe__")
            return True
        except Exception:
            return False

    try:
        sync_wait_for(_probe, "cluster probe failed after auth cleanup", timeout=5)
        return False
    except (TimeoutError, RequestError, ClosingError):
        return True


def _evict_pooled_sync_client(request) -> None:
    """Evict the pooled glide_sync_client so subsequent tests get a fresh one."""
    try:
        cluster_mode = request.getfixturevalue("cluster_mode")
        protocol = request.getfixturevalue("protocol")
        cache_key = (_get_worker_id(), cluster_mode, protocol)
        with _sync_client_pool_lock:
            _sync_client_pool.pop(cache_key, None)
    except Exception:
        pass


class TestSyncAuthCommands:
    """Test cases for password authentication and management"""

    @pytest.fixture(autouse=True, scope="function")
    def cleanup(self, request, management_sync_client: TGlideClient):
        """
        Ensure password is reset for default user and USERNAME user is deleted
        after each test. Per-op typed excepts so a wedge at any single step is
        attributed rather than swallowed; a wedge probe at the end evicts the
        pooled client and fails loudly so subsequent tests don't inherit a
        poisoned auth cluster.
        """
        yield

        failed_op = _run_sync_cleanup_ops(management_sync_client)
        wedge_detected = _probe_sync_cluster_wedge(management_sync_client)

        if failed_op is not None or wedge_detected:
            _evict_pooled_sync_client(request)
            pytest.fail(
                f"auth cleanup left cluster wedged: failed_op={failed_op!r}, "
                f"wedge_probe_timed_out={wedge_detected}. Requirepass state unknown; "
                f"subsequent tests may inherit poisoned cluster."
            )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_accepts_new_password(
        self,
        glide_sync_client: TGlideClient,
        management_sync_client: TGlideClient,
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
            glide_sync_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=False
            )
            == OK
        )
        assert glide_sync_client.set("test_key", "test_value") == OK
        assert glide_sync_client.get("test_key") == b"test_value"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_survives_reconnect(
        self,
        glide_sync_client: TGlideClient,
        management_sync_client: TGlideClient,
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
        assert glide_sync_client.set("test_key", "test_value") == OK
        glide_sync_client.update_connection_password(NEW_PASSWORD, immediate_auth=False)
        config_set_new_password(glide_sync_client, NEW_PASSWORD)
        sync_kill_connections_tolerant(management_sync_client)

        def _reconnected():
            try:
                return glide_sync_client.get("test_key") == b"test_value"
            except Exception:
                return False

        sync_wait_for(
            _reconnected,
            "Client did not recover with stored new password after reconnect",
            timeout=reconnect_timeout,
        )

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_immediate_auth_survives_reconnect(
        self,
        glide_sync_client: TGlideClient,
        management_sync_client: TGlideClient,
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
        glide_sync_client.update_connection_password(NEW_PASSWORD, immediate_auth=False)
        config_set_new_password(glide_sync_client, NEW_PASSWORD)
        sync_kill_connections_tolerant(management_sync_client)

        def _immediate_auth_after_reconnect():
            try:
                return (
                    glide_sync_client.update_connection_password(
                        NEW_PASSWORD, immediate_auth=True
                    )
                    == OK
                )
            except Exception:
                return False

        sync_wait_for(
            _immediate_auth_after_reconnect,
            "Client did not accept immediate-auth after reconnect",
            timeout=reconnect_timeout,
        )
        assert glide_sync_client.set("test_key", "test_value") == OK

    @pytest.mark.parametrize("cluster_mode", [False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_sync_update_connection_password_connection_lost_before_password_update(
        self, management_sync_client: TGlideClient, request, cluster_mode, protocol
    ):
        """
        Test changing server password when connection is lost before password update.
        Verifies that the client will not be able to reach the inner core and return an error
        on immediate re-authentication, but will succeed with non-immediate re-auth
        """
        # Use a fresh client (not pooled) since this test kills and expects it to stay dead
        test_client = create_sync_client(
            request, cluster_mode, protocol=protocol, request_timeout=5000
        )
        try:
            test_client.set("test_key", "test_value")
            config_set_new_password(test_client, NEW_PASSWORD)
            sync_kill_connections_tolerant(management_sync_client)
            # Wait for glide-core to detect dead connection and fail reconnect
            # (reconnects with old/empty password, which server now rejects)
            time.sleep(2)
            result = test_client.update_connection_password(
                NEW_PASSWORD, immediate_auth=False
            )
            assert result == OK
            with pytest.raises(RequestError):
                test_client.update_connection_password(
                    NEW_PASSWORD, immediate_auth=True
                )
        finally:
            try:
                test_client.close()
            except Exception:
                pass

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
        # Retry during reconnection - non-blocking reconnect may still be in progress
        max_retries = 20
        for i in range(max_retries):
            try:
                result = acl_glide_sync_client.update_connection_password(
                    NEW_PASSWORD, immediate_auth=True
                )
                assert result == OK
                break
            except Exception as e:
                if (
                    "AllConnectionsUnavailable" in str(e)
                    or "Connection in recovery" in str(e)
                ) and i < max_retries - 1:
                    time.sleep(0.5)
                    continue
                raise

        # Verify client is authenticated - retry during reconnection
        for i in range(max_retries):
            try:
                assert acl_glide_sync_client.set("test_key", "test_value") == OK
                break
            except Exception as e:
                if (
                    "AllConnectionsUnavailable" in str(e)
                    or "Connection in recovery" in str(e)
                ) and i < max_retries - 1:
                    time.sleep(0.5)
                    continue
                raise
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

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_iam_authentication_with_mock_credentials(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Test IAM authentication using mock AWS credentials.

        This test verifies:
        1. Client can connect using IAM authentication with mock credentials
        2. Basic operations work after IAM authentication
        3. Operations continue to work after token refresh
        """
        client = create_iam_client(request, cluster_mode, protocol)

        # Verify connection works
        assert_connected_sync(client)

        # Test manual token refresh
        client.refresh_iam_token()

        # Test basic operations
        client.set("iam_test_key", "iam_test_value")
        value = client.get("iam_test_key")
        assert value == b"iam_test_value"

        # Verify operations still work after token refresh
        client.set("iam_test_key2", "iam_test_value2")
        value2 = client.get("iam_test_key2")
        assert value2 == b"iam_test_value2"

    @pytest.mark.parametrize("cluster_mode", [True, False])
    @pytest.mark.parametrize("protocol", [ProtocolVersion.RESP2, ProtocolVersion.RESP3])
    def test_iam_authentication_automatic_token_refresh(
        self, request, cluster_mode: bool, protocol: ProtocolVersion
    ):
        """
        Test automatic IAM token refresh.

        This test verifies that the client automatically refreshes the IAM token
        at the configured interval and continues to work correctly.
        """
        client = create_iam_client(
            request, cluster_mode, protocol, refresh_interval_seconds=2
        )

        # Verify initial connection
        assert_connected_sync(client)

        # Wait for automatic token refresh to occur
        time.sleep(3)

        # Verify client still works after automatic refresh
        client.set("iam_auto_refresh_key", "iam_auto_refresh_value")
        value = client.get("iam_auto_refresh_key")
        assert value == b"iam_auto_refresh_value"
