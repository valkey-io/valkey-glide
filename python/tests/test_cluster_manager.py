# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import importlib.util
import os
import shutil
import time
from pathlib import Path
from types import ModuleType

import pytest

CLUSTER_MANAGER_FILE = (
    Path(__file__).resolve().parent.parent.parent / "utils" / "cluster_manager.py"
)


def load_cluster_manager() -> ModuleType:
    """Import the shared cluster script; it is a standalone file, not a package."""
    spec = importlib.util.spec_from_file_location(
        "cluster_manager", CLUSTER_MANAGER_FILE
    )
    assert spec is not None
    loader = spec.loader
    assert loader is not None
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


def write_fixture_file(folder: str, name: str) -> None:
    """Contents do not matter: the check under test only looks at presence and age."""
    path = Path(folder)
    path.mkdir(parents=True, exist_ok=True)
    (path / name).write_text("placeholder\n")


@pytest.fixture
def cluster_manager(tmp_path, monkeypatch) -> ModuleType:
    """Redirect the TLS fixture paths so tests never touch the shared utils/tls_crts."""
    module = load_cluster_manager()
    tls_folder = tmp_path / "tls_crts"
    monkeypatch.setattr(module, "TLS_FOLDER", str(tls_folder))
    monkeypatch.setattr(module, "CA_CRT", str(tls_folder / "ca.crt"))
    monkeypatch.setattr(module, "SERVER_CRT", str(tls_folder / "server.crt"))
    monkeypatch.setattr(module, "SERVER_KEY", str(tls_folder / "server.key"))
    # The default poll budget waits 15 seconds per missing file so that a concurrent
    # generation can finish. Nothing runs concurrently here, so one second is plenty,
    # and only the missing-file assertions ever wait that long.
    poll = module.check_if_tls_cert_exist
    monkeypatch.setattr(
        module,
        "check_if_tls_cert_exist",
        lambda tls_file, timeout=1: poll(tls_file, timeout),
    )
    return module


def test_creates_the_folder_when_it_is_absent(cluster_manager):
    """The check owns creating the fixture folder, so a fresh checkout regenerates."""
    assert cluster_manager.should_generate_new_tls_certs() is True
    assert os.path.isdir(cluster_manager.TLS_FOLDER)


def test_partial_fixture_regenerates_and_complete_fixture_is_reused(cluster_manager):
    """An interrupted generation that only wrote ca.crt must not be reused."""
    write_fixture_file(cluster_manager.TLS_FOLDER, "ca.crt")
    assert cluster_manager.should_generate_new_tls_certs() is True

    write_fixture_file(cluster_manager.TLS_FOLDER, "server.key")
    write_fixture_file(cluster_manager.TLS_FOLDER, "server.crt")
    assert cluster_manager.should_generate_new_tls_certs() is False


@pytest.mark.parametrize("present", ["ca.crt", "server.key", "server.crt"])
def test_regenerates_when_only_one_file_is_present(cluster_manager, present):
    """Reuse takes the whole set, so one valid file cannot stand in for a fixture."""
    write_fixture_file(cluster_manager.TLS_FOLDER, present)
    assert cluster_manager.should_generate_new_tls_certs() is True


def test_regenerates_when_a_certificate_has_expired(cluster_manager):
    """A complete fixture still ages out once it is past the certificate lifetime."""
    for name in ("ca.crt", "server.key", "server.crt"):
        write_fixture_file(cluster_manager.TLS_FOLDER, name)
    expired = time.time() - 4000 * 24 * 60 * 60
    os.utime(cluster_manager.SERVER_CRT, (expired, expired))

    assert cluster_manager.should_generate_new_tls_certs() is True


def test_regenerates_when_a_certificate_is_empty(cluster_manager):
    """An interrupted openssl run leaves a 0-byte file with a fresh mtime."""
    for name in ("ca.crt", "server.key", "server.crt"):
        write_fixture_file(cluster_manager.TLS_FOLDER, name)
    Path(cluster_manager.SERVER_CRT).write_text("")

    assert cluster_manager.should_generate_new_tls_certs() is True


def test_a_certificate_that_disappeared_is_not_valid(cluster_manager):
    """A concurrent teardown between the presence poll and the read must not raise."""
    assert (
        cluster_manager.check_if_tls_cert_is_valid(cluster_manager.SERVER_CRT) is False
    )


@pytest.mark.skipif(shutil.which("openssl") is None, reason="openssl is required")
def test_generation_recovers_from_a_stale_serial_file(cluster_manager):
    """Regeneration happens over a used folder, so leftovers must not break it."""
    write_fixture_file(cluster_manager.TLS_FOLDER, "ca.txt")

    cluster_manager.generate_tls_certs()

    assert os.path.getsize(cluster_manager.CA_CRT) > 0
    assert os.path.getsize(cluster_manager.SERVER_KEY) > 0
    assert os.path.getsize(cluster_manager.SERVER_CRT) > 0
