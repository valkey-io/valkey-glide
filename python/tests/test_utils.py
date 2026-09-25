# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import glob
import os
from pathlib import Path
from types import SimpleNamespace

import pytest
from glide.logger import Level, Logger
from glide_shared.config import NodeAddress
from glide_sync import LogLevel as SyncLogLevel
from glide_sync.logger import Logger as SyncLogger

from tests.utils.utils import (
    DEFAULT_SYNC_TEST_LOG_LEVEL,
    DEFAULT_TEST_LOG_LEVEL,
    compare_maps,
    get_random_string,
    require_cluster_addresses,
)

CURR_DIR = Path(__file__).resolve().parent
PYTHON_DIR = CURR_DIR.parent
LOG_DIR = PYTHON_DIR / "glide-logs"


def find_log_files(prefix: str) -> list[str]:
    pattern = os.path.join(LOG_DIR, f"{prefix}*")
    return glob.glob(pattern)


class TestLogger:
    def test_init_logger(self):
        # The logger is already configured in the conftest file, so calling init again shouldn't modify the log level
        Logger.init(Level.ERROR)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL

    def test_set_logger_config(self):
        Logger.set_logger_config(Level.INFO)
        assert Logger.logger_level == Level.INFO
        # Revert to the tests default log level
        Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL

    def test_log_writes_to_file(self):
        filename = get_random_string(10) + ".log"
        Logger.set_logger_config(Level.INFO, filename)
        Logger.log(Level.INFO, "test", "test log message")

        matched_files = find_log_files(filename)
        assert (
            len(matched_files) == 1
        ), f"Expected exactly one log file with prefix '{filename}', found {len(matched_files)}"

        log_file = matched_files[0]

        with open(log_file, "r", encoding="utf-8") as f:
            contents = f.read()
            assert (
                "test log message" in contents
            ), "Log message not found in the log file"

        # Clean up the file
        os.remove(log_file)

        # Reset logger config to default
        Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)

    def test_init_sync_logger(self):
        # The logger is already configured in the conftest file, so calling init again shouldn't modify the log level
        SyncLogger.init(SyncLogLevel.ERROR)
        assert SyncLogger.logger_level == DEFAULT_SYNC_TEST_LOG_LEVEL

    def test_sync_set_logger_config(self):
        SyncLogger.set_logger_config(SyncLogLevel.INFO)
        assert SyncLogger.logger_level == SyncLogLevel.INFO
        # Revert to the tests default log level
        SyncLogger.set_logger_config(DEFAULT_SYNC_TEST_LOG_LEVEL)
        assert SyncLogger.logger_level == DEFAULT_SYNC_TEST_LOG_LEVEL

    def test_sync_log_writes_to_file(self):
        filename = get_random_string(10) + ".log"
        SyncLogger.set_logger_config(SyncLogLevel.INFO, filename)
        SyncLogger.log(SyncLogLevel.INFO, "test", "test log message")

        matched_files = find_log_files(filename)
        assert (
            len(matched_files) == 1
        ), f"Expected exactly one log file with prefix '{filename}', found {len(matched_files)}"

        log_file = matched_files[0]

        with open(log_file, "r", encoding="utf-8") as f:
            contents = f.read()
            assert (
                "test log message" in contents
            ), "Log message not found in the log file"

        # Clean up the file
        os.remove(log_file)

        # Reset logger config to default
        SyncLogger.set_logger_config(DEFAULT_SYNC_TEST_LOG_LEVEL)


class TestCompareMaps:
    def test_empty_maps(self):
        map1 = {}
        map2 = {}
        assert compare_maps(map1, map2) is True

    def test_same_key_value_pairs(self):
        map1 = {"a": 1, "b": 2}
        map2 = {"a": 1, "b": 2}
        assert compare_maps(map1, map2) is True

    def test_different_key_value_pairs(self):
        map1 = {"a": 1, "b": 2}
        map2 = {"a": 1, "b": 3}
        assert compare_maps(map1, map2) is False

    def test_different_key_value_pairs_order(self):
        map1 = {"a": 1, "b": 2}
        map2 = {"b": 2, "a": 1}
        assert compare_maps(map1, map2) is False

    def test_nested_maps_same_values(self):
        map1 = {"a": {"b": 1}}
        map2 = {"a": {"b": 1}}
        assert compare_maps(map1, map2) is True

    def test_nested_maps_different_values(self):
        map1 = {"a": {"b": 1}}
        map2 = {"a": {"b": 2}}
        assert compare_maps(map1, map2) is False

    def test_nested_maps_different_order(self):
        map1 = {"a": {"b": 1, "c": 2}}
        map2 = {"a": {"c": 2, "b": 1}}
        assert compare_maps(map1, map2) is False

    def test_arrays_same_values(self):
        map1 = {"a": [1, 2]}
        map2 = {"a": [1, 2]}
        assert compare_maps(map1, map2) is True

    def test_arrays_different_values(self):
        map1 = {"a": [1, 2]}
        map2 = {"a": [1, 3]}
        assert compare_maps(map1, map2) is False

    def test_null_values(self):
        map1 = {"a": None}
        map2 = {"a": None}
        assert compare_maps(map1, map2) is True

    def test_mixed_types_same_values(self):
        map1 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "e": "string",
            "f": [1, "2", True],
        }
        map2 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "e": "string",
            "f": [1, "2", True],
        }
        assert compare_maps(map1, map2) is True

    def test_mixed_types_different_values(self):
        map1 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "e": "string",
            "f": [1, "2", False],
        }
        map2 = {
            "a": 1,
            "b": {"c": [2, 3]},
            "d": None,
            "f": [1, "2", False],
            "e": "string",
        }
        assert compare_maps(map1, map2) is False


class TestRequireClusterAddresses:
    """require_cluster_addresses() skips when no cluster is available and
    otherwise returns one NodeAddress per cluster node.

    ``pytest.valkey_cluster`` is set for the whole session by conftest, so
    every case goes through ``monkeypatch`` to leave the real value intact.
    """

    def test_skips_when_attribute_missing(self, monkeypatch):
        monkeypatch.delattr(pytest, "valkey_cluster", raising=False)
        with pytest.raises(pytest.skip.Exception) as exc_info:
            require_cluster_addresses()
        assert "pytest.valkey_cluster not set" in str(exc_info.value)

    def test_skips_when_cluster_is_none(self, monkeypatch):
        monkeypatch.setattr(pytest, "valkey_cluster", None, raising=False)
        with pytest.raises(pytest.skip.Exception) as exc_info:
            require_cluster_addresses()
        assert "No cluster endpoints available" in str(exc_info.value)

    def test_skips_when_cluster_has_no_nodes(self, monkeypatch):
        monkeypatch.setattr(
            pytest, "valkey_cluster", SimpleNamespace(nodes_addr=[]), raising=False
        )
        with pytest.raises(pytest.skip.Exception) as exc_info:
            require_cluster_addresses()
        assert "No cluster endpoints available" in str(exc_info.value)

    def test_returns_one_address_per_node(self, monkeypatch):
        nodes = [
            SimpleNamespace(host="127.0.0.1", port=7000),
            SimpleNamespace(host="::1", port=7001),
            SimpleNamespace(host="node-3.example", port=7002),
        ]
        monkeypatch.setattr(
            pytest, "valkey_cluster", SimpleNamespace(nodes_addr=nodes), raising=False
        )

        addresses = require_cluster_addresses()

        assert len(addresses) == len(nodes)
        for address, node in zip(addresses, nodes):
            assert isinstance(address, NodeAddress)
            assert address.host == node.host
            assert address.port == node.port
