# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import sys
import uuid
from typing import List, Optional, cast

import pytest
from glide_shared.exceptions import RequestError

from tests.sync_tests.conftest import create_sync_client
from tests.utils.cluster import ValkeyCluster
from tests.utils.cluster_skip_guard import (
    fail_session_on_unexpected_cluster_skips,
    record_cluster_skip_report,
    reset_unexpected_cluster_skips,
)
from tests.utils.utils import sync_check_if_server_version_lt

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 6379


def pytest_addoption(parser):
    parser.addoption(
        "--host",
        default=DEFAULT_HOST,
        action="store",
        help="Server host endpoint, defaults to `%(default)s`",
    )

    parser.addoption(
        "--port",
        default=DEFAULT_PORT,
        action="store",
        help="Server port, defaults to `%(default)s`",
    )

    parser.addoption(
        "--tls",
        default=False,
        action="store_true",
        help="TLS enabled, defaults to `%(default)s`",
    )

    parser.addoption(
        "--no-tls",
        dest="tls",
        action="store_false",
        help="TLS disabled, defaults to `%(default)s`",
    )

    parser.addoption(
        "--compression",
        default=False,
        action="store_true",
        help="Enable compression for all tests, defaults to `%(default)s`",
    )

    parser.addoption(
        "--load-module",
        action="append",
        help="""Load additional Valkey modules (provide full path for the module's shared library).
            Use multiple times for multiple modules.
            Example:
            pytest -v --load-module=/path/to/module1.so --load-module=/path/to/module2.so""",
        default=[],
    )

    parser.addoption(
        "--cluster-endpoints",
        action="store",
        help="""Comma-separated list of cluster endpoints for standalone cluster in the format host1:port1,host2:port2,...
            Note: The cluster will be flashed between tests.
            Example:
                pytest -v --cluster-endpoints=127.0.0.1:6379
                pytest -v --cluster-endpoints=127.0.0.1:6379,127.0.0.1:6380
            """,
        default=None,
    )

    parser.addoption(
        "--standalone-endpoints",
        action="store",
        help="""Comma-separated list of cluster endpoints for cluster mode cluster in the format host1:port1,host2:port2,...
            Note: The cluster will be flashed between tests.
            Example:
                pytest -v --standalone-endpoints=127.0.0.1:6379
                pytest -v --standalone-endpoints=127.0.0.1:6379,127.0.0.1:6380
            """,
        default=None,
    )

    parser.addoption(
        "--async-backend",
        action="append",
        choices=("asyncio", "uvloop", "trio"),
        help="""Async framework with which the tests will be run. By default, runs on asyncio and trio.
            Example:
                pytest -v --async-backend=trio
                pytest -v --async-backend=uvloop --async-backend=trio
            """,
        default=None,
    )

    parser.addoption(
        "--mock-pubsub",
        default=False,
        action="store_true",
        help="Running with mock pubsub (skips tests that require real server connections)",
    )


def parse_endpoints(endpoints_str: str) -> List[List[str]]:
    """
    Parse the endpoints string into a list of lists containing host and port.
    """
    try:
        endpoints = [endpoint.split(":") for endpoint in endpoints_str.split(",")]
        for endpoint in endpoints:
            if len(endpoint) != 2:
                raise ValueError(
                    "Each endpoint should be in the format 'host:port'.\nEndpoints should be separated by commas."
                )
            host, port = endpoint
            if not host or not port.isdigit():
                raise ValueError(
                    "Both host and port should be specified and port should be a valid integer."
                )
        return endpoints
    except ValueError as e:
        raise ValueError("Invalid endpoints format: " + str(e))


def create_clusters(tls, load_module, cluster_endpoints, standalone_endpoints):
    """
    Create Valkey clusters based on the provided options.
    """
    if cluster_endpoints or standalone_endpoints:
        # Endpoints were passed by the caller, not creating clusters internally
        if cluster_endpoints:
            cluster_endpoints = parse_endpoints(cluster_endpoints)
            pytest.valkey_cluster = ValkeyCluster(tls=tls, addresses=cluster_endpoints)
        if standalone_endpoints:
            standalone_endpoints = parse_endpoints(standalone_endpoints)
            pytest.standalone_cluster = ValkeyCluster(
                tls=tls, addresses=standalone_endpoints
            )
    else:
        # No endpoints were provided, create new clusters
        pytest.valkey_cluster = ValkeyCluster(
            tls=tls,
            cluster_mode=True,
            load_module=load_module,
            addresses=cluster_endpoints,
            replica_count=2,
        )
        pytest.standalone_cluster = ValkeyCluster(
            tls=tls,
            cluster_mode=False,
            shard_count=1,
            replica_count=1,
            load_module=load_module,
            addresses=standalone_endpoints,
        )
        # Dedicated cluster for the auth suite. Auth tests fan CLIENT KILL out
        # over AllNodes and rotate requirepass; sharing the main cluster
        # bleeds a damaged topology into every cluster-mode test that runs
        # after. 3 primaries with no replicas is the smallest bootable Redis
        # Cluster shape and keeps setup cost low. Only the twin matching the
        # session's --tls mode is booted; the auth fixtures consume exactly
        # one of them per session.
        if tls:
            pytest.valkey_auth_tls_cluster = ValkeyCluster(
                tls=True,
                cluster_mode=True,
                shard_count=3,
                replica_count=0,
                load_module=load_module,
            )
        else:
            pytest.valkey_auth_cluster = ValkeyCluster(
                tls=False,
                cluster_mode=True,
                shard_count=3,
                replica_count=0,
                load_module=load_module,
            )

    if not (cluster_endpoints or standalone_endpoints):
        pytest.valkey_tls_cluster = ValkeyCluster(
            tls=True,
            cluster_mode=True,
            load_module=load_module,
            replica_count=2,
        )
        pytest.standalone_tls_cluster = ValkeyCluster(
            tls=True,
            cluster_mode=False,
            shard_count=1,
            replica_count=1,
            load_module=load_module,
        )


@pytest.fixture(autouse=True, scope="session")
def call_before_all_pytests(request):
    """
    Called after the Session object has been created and
    before performing collection and entering the run test loop.
    """
    tls = request.config.getoption("--tls")
    load_module = request.config.getoption("--load-module")
    cluster_endpoints = request.config.getoption("--cluster-endpoints")
    standalone_endpoints = request.config.getoption("--standalone-endpoints")

    # only run asyncio by default. trio is run in CI nightly
    request.config.async_backends = request.config.getoption("--async-backend") or (
        "asyncio",
    )

    create_clusters(tls, load_module, cluster_endpoints, standalone_endpoints)


def pytest_sessionstart(session):
    reset_unexpected_cluster_skips()


def pytest_runtest_logreport(report):
    record_cluster_skip_report(report)


def pytest_sessionfinish(session, exitstatus):
    """
    Called after whole test run finished, right before
    returning the exit status to the system.
    """
    fail_session_on_unexpected_cluster_skips(session)
    for attr in (
        "valkey_cluster",
        "standalone_cluster",
        "valkey_tls_cluster",
        "standalone_tls_cluster",
        "valkey_auth_cluster",
        "valkey_auth_tls_cluster",
    ):
        try:
            delattr(pytest, attr)
        except AttributeError:
            pass


def pytest_collection_modifyitems(config, items):
    """
    Modify collected test items.

    This function checks if cluster or standalone endpoints are provided. If so, it checks if the test requires
    cluster mode and skips it accordingly.
    """
    for item in items:
        if config.getoption("--cluster-endpoints") or config.getoption(
            "--standalone-endpoints"
        ):
            # DNS tests always require internally-created TLS clusters with
            # specific hostname configs — skip with any external endpoints.
            if "dns" in item.nodeid:
                item.add_marker(
                    pytest.mark.skip(reason="DNS tests require internal cluster setup")
                )
                continue

            # TLS tests need TLS clusters — skip only when --tls is not set.
            if "tls" in item.nodeid and not config.getoption("--tls"):
                item.add_marker(
                    pytest.mark.skip(
                        reason="TLS tests skipped: external endpoints provided without --tls"
                    )
                )
                continue

            if "cluster_mode" in item.fixturenames:
                cluster_mode_value = item.callspec.params.get("cluster_mode", None)
                if cluster_mode_value is True and not config.getoption(
                    "--cluster-endpoints"
                ):
                    item.add_marker(
                        pytest.mark.skip(
                            reason="Test skipped because cluster_mode=True and cluster endpoints weren't provided"
                        )
                    )
                elif cluster_mode_value is False and not config.getoption(
                    "--standalone-endpoints"
                ):
                    item.add_marker(
                        pytest.mark.skip(
                            reason="Test skipped because cluster_mode=False and standalone endpoints weren't provided"
                        )
                    )


@pytest.fixture(
    params=[
        pytest.param(
            (("asyncio", {"use_uvloop": False}), "asyncio"),
            id="asyncio",
        ),
        pytest.param(
            (("asyncio", {"use_uvloop": True}), "uvloop"),
            marks=[
                pytest.mark.skipif(
                    sys.platform == "win32",
                    reason="uvloop is unavailabe on windows",
                )
            ],
            id="uvloop",
        ),
        pytest.param(
            (("trio", {"restrict_keyboard_interrupt_to_checkpoints": True}), "trio"),
            id="trio",
        ),
    ]
)
def anyio_backend(request):
    if request.param[1] not in request.config.async_backends:
        pytest.skip(
            reason=f"{request.param[1]} is excluded",
            allow_module_level=True,
        )

    return request.param[0]


@pytest.fixture(autouse=True)
def skip_if_version_below(request):
    """
    Skip test(s) if server version is below than given parameter. Can skip a complete test suite.

    Example:
        @pytest.mark.skip_if_version_below('7.0.0')
        async def test_meow_meow(...):
            ...
    """
    if request.node.get_closest_marker("skip_if_version_below"):
        min_version = request.node.get_closest_marker("skip_if_version_below").args[0]
        client = create_sync_client(request, False)
        try:
            if sync_check_if_server_version_lt(client, min_version):
                pytest.skip(
                    reason=f"This feature added in version {min_version}",
                    allow_module_level=True,
                )
        finally:
            client.close()


def classify_inkeys_probe_error(msg: str) -> bool:
    """Pure classification function extracted from
    ``_probe_search_1_3_capability`` so the skip-or-run decision for every
    1.3-only test has a server-free unit test (see
    ``tests/test_options.py::TestClassifyInkeysProbeError``).

    Returns True if ``msg`` matches the pre-1.3 "Unexpected argument
    `INKEYS`" rejection (raw or GLIDE-reformatted spelling), False
    otherwise. Returning False does not mean "definitely 1.3+" -- the
    caller still needs to distinguish "no error at all" (1.3+) from "a
    different error" (unexpected, should raise), which is why this
    function returns a bool classification rather than driving the
    control flow itself.
    """
    return "Unexpected" in msg and "INKEYS" in msg


def _probe_search_1_3_capability(client) -> bool:
    """Classify the connected search module as 1.3+-capable or not.

    Uses a real-command, fake-argument probe rather than a version-string
    compare: a version string can be present and still not answer the
    question that matters -- whether the specific capability a test needs
    is live. ``INKEYS`` is used as the capability marker because
    it is new in 1.3.0-rc1 (PR #967) and is parsed by the same shared
    ``vmsdk::ParamParser`` keyword-dispatch machinery as every other
    FT.SEARCH option, so a module that doesn't understand it rejects it with
    that parser's generic unknown-argument error rather than silently
    ignoring it.

    The probe targets a real (throwaway) index rather than a nonexistent one.
    A nonexistent index raises its own domain error ("Index ... not found")
    before option parsing is even reached, on both pre-1.3 and 1.3+ modules
    identically -- that was tried and confirmed empirically against a built
    1.2.1 module (ver 66049) and masks the signal entirely. Against a real
    index:
      * pre-1.3 (confirmed against 1.2.1, ver 66049): rejects with
        "Unexpected argument `INKEYS`" -- the generic unknown-parameter
        error from ``vmsdk/src/command_parser.h`` (byte-identical in both
        1.2.1 and 1.3.0-rc1, so this error text is a stable signal, not a
        moving target). GLIDE's ``RequestError`` splits the server's error
        text on its first word as if it were an error code, so the message
        surfaces here as "Unexpected: argument `INKEYS`" (confirmed
        empirically against the built 1.2.1 module) -- the check below
        tolerates both the raw and GLIDE-reformatted spellings.
      * 1.3.0-rc1 (ver 66304) and later: ``INKEYS 0`` parses as "filter to
        zero keys" and returns an empty result array, no error.
    """
    probe_index = f"__glide_search_1_3_probe_{uuid.uuid4()}"
    try:
        client.custom_command(["FT.CREATE", probe_index, "SCHEMA", "title", "TEXT"])
    except RequestError as e:
        # Module entirely absent, or FT.CREATE itself unavailable. This must
        # be a hard, loud failure -- never a silent skip.
        raise RuntimeError(
            "search 1.3 capability probe could not create a test index "
            f"(is the search module loaded at all?): {e}"
        ) from e
    try:
        client.custom_command(["FT.SEARCH", probe_index, "*", "INKEYS", "0"])
        return True
    except RequestError as e:
        msg = str(e)
        if classify_inkeys_probe_error(msg):
            return False
        # A different error (e.g. a wire-syntax regression) is not the
        # pre-1.3-vs-1.3 signal this probe exists to detect. Fail loudly
        # rather than silently treating it as "not 1.3".
        raise RuntimeError(
            f"search 1.3 capability probe got an unexpected error: {e}"
        ) from e
    finally:
        try:
            client.custom_command(["FT.DROPINDEX", probe_index])
        except RequestError:
            # Retry once -- a transient failure here would otherwise leave
            # probe_index on the server for the rest of the session, where
            # later Search tests could observe it. If the retry also fails,
            # raise instead of silently continuing: both FT.CREATE and
            # FT.DROPINDEX use probe_index as their own first argument, so
            # they always route to the same primary -- a cluster-routing
            # mismatch is not a possible explanation for this failing.
            client.custom_command(["FT.DROPINDEX", probe_index])


@pytest.fixture(scope="session")
def search_1_3_capability(request) -> bool:
    """Session-scoped: True if the connected search module supports 1.3
    features (probed once per session), False if it is present but pre-1.3.

    Raises (does not skip) if the search module appears entirely absent --
    see ``_probe_search_1_3_capability``. Use the ``requires_search_1_3``
    marker on individual tests rather than depending on this fixture
    directly in most cases.

    Connects in cluster mode if cluster endpoints (or an internally-created
    cluster) are available for this session, else standalone -- a
    standalone-only run (e.g. ``--standalone-endpoints`` with no
    ``--cluster-endpoints``) has no ``pytest.valkey_cluster`` to connect
    through at all, which a hardcoded cluster_mode=True here would hit as
    an AttributeError instead of probing the server that is actually live.
    """
    use_cluster_mode = bool(
        request.config.getoption("--cluster-endpoints")
        or getattr(pytest, "valkey_cluster", None) is not None
    )
    client = create_sync_client(request, use_cluster_mode)
    try:
        return _probe_search_1_3_capability(client)
    finally:
        client.close()


def _parse_emulate_release_level(config_get_raw: object) -> Optional[str]:
    """Pull the value out of a ``CONFIG GET search.emulate-release`` reply.

    GLIDE's own ``CONFIG GET`` returns a ``dict`` (confirmed against a live
    1.3.0-rc1 module); a flat ``[key, value]`` list or ``tuple`` is also
    accepted for safety, in case a future client surface or a different
    reply mode ever returns one. Returns ``None`` -- rather than raising --
    when the key is absent, the reply is empty, or the reply is some other
    shape entirely (a scalar, a nested per-node dict from a non-GLIDE
    caller, an odd-length flat list): the caller is responsible for
    deciding whether a ``None`` here means "pre-1.3" or "unparseable
    reply from a 1.3-capable module" -- this function only
    reports "no value found", it does not have enough context to tell
    those apart.
    """
    if isinstance(config_get_raw, dict):
        pairs = config_get_raw
    elif isinstance(config_get_raw, (list, tuple)):
        flat = cast(List, config_get_raw)
        pairs = dict(zip(flat[0::2], flat[1::2]))
    else:
        return None
    for key, value in pairs.items():
        decoded_key = key.decode() if isinstance(key, bytes) else key
        if decoded_key == "search.emulate-release":
            return value.decode() if isinstance(value, bytes) else value
    return None


@pytest.fixture(scope="session")
def emulate_release_level(request, search_1_3_capability: bool) -> str:
    """Session-scoped: the connected module's active ``search.emulate-release``
    level (e.g. ``"1.0.0"`` at the module's own default, ``"1.3.0"`` when
    explicitly raised), read once via ``CONFIG GET search.emulate-release``.

    ``requires_search_1_3`` (the ``search_1_3_capability`` probe
    above) answers "is this binary 1.3-capable at all", not "is the 1.3
    RediSearch-compatible behavior actually active right now" -- those are
    different questions, because `search.emulate-release` defaults to the
    module's current major (1.0.0) regardless of binary version
    (COMPATIBILITY.md, "Sunsetting of Incompatible Behavior"). A 1.3-capable
    module left at that default still runs the legacy branch of every
    `VALKEY_SEARCH_COMPATIBILITY_FIX(1, 3, 0, ...)` gate. Tests that assert
    the *corrected* 1.3 behavior (not just "the server didn't reject the
    command") need this fixture to know which branch is actually live, in
    addition to the `requires_search_1_3` marker that gates on the binary.

    Takes ``search_1_3_capability`` as a direct parameter so this fixture
    cannot run ahead of the 1.3 gate: a test that requests
    ``emulate_release_level`` gets the capability probe (and, through the
    ``requires_search_1_3`` marker, a skip) resolved first. On a pre-1.3
    module ``search.emulate-release`` doesn't exist, so this skips with a
    reason instead of raising.

    Once ``search_1_3_capability`` is True, a ``None`` from
    ``_parse_emulate_release_level`` can no longer mean "pre-1.3" -- it
    means the harness got a reply shape it couldn't decode from a module
    that IS 1.3-capable (a nested per-node dict, an odd-length flat list,
    a scalar). Skipping in that state would be a false green: every gated
    test would skip as "pre-1.3" against a module that actually is 1.3.
    This fails loudly instead, since a harness misread must never present
    as a skip.

    Connects the same way as `search_1_3_capability` (cluster mode if
    cluster endpoints are available, else standalone) for the same reason:
    a standalone-only run has no `pytest.valkey_cluster` to connect
    through.

    This fixture is session-scoped and reads the level from ONE
    deployment (cluster, when available) -- the resulting value is then
    reused for every test regardless of that test's own `cluster_mode`
    parametrization, including `cluster_mode=False` tests. This is only
    correct when every deployment in the session was started with the
    same `search.emulate-release` (true of CI and
    `utils/run-modules-ci-locally.sh` today, which set one `EMULATE_RELEASE`
    for both the standalone and cluster containers). A session that mixed
    levels across deployments would send the standalone tests down the
    wrong branch with a misleading diff. `search_1_3_capability` carries
    the same assumption, but that one is sound regardless, since module
    presence is a binary property of the image, not a per-server config.
    """
    if not search_1_3_capability:
        pytest.skip(
            reason="search module is present but pre-1.3; "
            "search.emulate-release does not exist on this module."
        )
    use_cluster_mode = bool(
        request.config.getoption("--cluster-endpoints")
        or getattr(pytest, "valkey_cluster", None) is not None
    )
    client = create_sync_client(request, use_cluster_mode)
    try:
        raw = client.custom_command(["CONFIG", "GET", "search.emulate-release"])
        level = _parse_emulate_release_level(raw)
        if level is None:
            raise RuntimeError(
                "search module reports 1.3-capable (search_1_3_capability "
                "is True), but CONFIG GET search.emulate-release returned "
                f"an unparseable reply: {raw!r}. This is a harness bug, "
                "not a legitimate pre-1.3 state."
            )
        return level
    finally:
        client.close()


@pytest.fixture(autouse=True)
def _apply_requires_search_1_3(request):
    """Skip, with an attributed reason, any test marked
    ``@pytest.mark.requires_search_1_3`` when the connected module is
    present but pre-1.3. Module-absent is handled by
    ``_probe_search_1_3_capability`` raising, not by this fixture.

    Takes ``search_1_3_capability`` via ``request.getfixturevalue()``
    rather than as a direct parameter: this fixture is autouse (runs for
    every test in the whole session, including pure unit tests with no
    server at all), so the capability probe -- which opens a real cluster
    connection -- must stay lazy and only actually run for tests that carry
    the marker. A direct parameter would make pytest evaluate it eagerly for
    every test, which breaks any suite run that doesn't have a live cluster
    (e.g. unit-test-only runs).
    """
    if request.node.get_closest_marker("requires_search_1_3"):
        if not request.getfixturevalue("search_1_3_capability"):
            pytest.skip(
                reason="search module is present but pre-1.3 (INKEYS probe "
                "rejected with 'Unexpected argument `INKEYS`'); this test "
                "exercises a valkey-search 1.3 feature and is skipped with "
                "an attributed reason rather than silently."
            )


@pytest.fixture(scope="session")
def valkey_mtls_cluster(request) -> ValkeyCluster:
    """
    A single TLS standalone node started with ``--tls-auth-clients yes``.

    The session clusters accept a client that sends no certificate, so they
    cannot show that a client cert was required or checked. Started on first
    use because only the mTLS tests need it.
    """
    if request.config.getoption("--cluster-endpoints") or request.config.getoption(
        "--standalone-endpoints"
    ):
        pytest.skip(
            reason="mTLS tests require an internally-created cluster with "
            "client-cert auth enabled"
        )
    return ValkeyCluster(
        tls=True,
        cluster_mode=False,
        shard_count=1,
        replica_count=0,
        tls_auth_clients=True,
    )


@pytest.fixture(scope="session")
def valkey_mtls_cluster_cluster(request) -> ValkeyCluster:
    """
    A TLS cluster (3 shards, 1 replica each) started with ``--tls-cluster yes``
    and ``--tls-auth-clients yes`` so both the cluster bus and clients use TLS
    with client certificates.

    The existing session clusters accept a client that sends no certificate,
    so they cannot show that a client cert was required or checked. Started on
    first use because only the mTLS tests need it.
    """
    if request.config.getoption("--cluster-endpoints") or request.config.getoption(
        "--standalone-endpoints"
    ):
        pytest.skip(
            reason="mTLS tests require an internally-created cluster with "
            "client-cert auth enabled"
        )
    return ValkeyCluster(
        tls=True,
        cluster_mode=True,
        shard_count=3,
        replica_count=1,
        tls_auth_clients=True,
    )


@pytest.fixture
def valkey_mtls_target(
    request,
    cluster_mode,
    valkey_mtls_cluster,
    valkey_mtls_cluster_cluster,
) -> ValkeyCluster:
    """
    Return the mTLS-required fixture matching the requested ``cluster_mode``.

    Keyed by ``cluster_mode`` so the same test body exercises both the
    standalone and cluster topologies.
    """
    return valkey_mtls_cluster_cluster if cluster_mode else valkey_mtls_cluster


@pytest.fixture
def tls_insecure(request) -> bool:
    # If the test has param'd tls_insecure, use it
    # Otherwise default to False
    return getattr(request, "param", False)


@pytest.fixture(autouse=True)
def skip_if_mock_pubsub(request):
    """
    Skip test(s) if running with mock pubsub and test is marked with skip_if_mock_pubsub.

    Example:
        @pytest.mark.skip_if_mock_pubsub
        async def test_resubscribe_after_connection_kill(...):
            ...
    """
    if request.node.get_closest_marker("skip_if_mock_pubsub"):
        if request.config.getoption("--mock-pubsub"):
            pytest.skip(
                reason="Test skipped because it requires real server connections (running with --mock-pubsub)",
                allow_module_level=True,
            )
