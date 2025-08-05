# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import glob
import os
from pathlib import Path

from glide.logger import Level, Logger
from glide_sync import LogLevel as SyncLogLevel
from glide_sync.logger import Logger as SyncLogger

from tests.utils.utils import (
    DEFAULT_SYNC_TEST_LOG_LEVEL,
    DEFAULT_TEST_LOG_LEVEL,
    compare_maps,
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
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL.value

    def test_set_logger_config(self):
        Logger.set_logger_config(Level.INFO)
        assert Logger.logger_level == Level.INFO.value
        # Revert to the tests default log level
        Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL.value

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

    def test_sync_logger_init_is_idempotent(self):
        # Clean up any existing files before test
        for prefix in ("first.log", "second.log"):
            for f in find_log_files(prefix):
                os.remove(f)

        # Initialize once with first.log
        SyncLogger.init(level=SyncLogLevel.INFO, file_name="first.log")
        SyncLogger.log(SyncLogLevel.INFO, "test", "Message after first init")
        logger1 = SyncLogger._instance
        level1 = SyncLogger.logger_level

        # Re-run init with second.log (should NOT reconfigure)
        SyncLogger.init(level=SyncLogLevel.DEBUG, file_name="second.log")
        SyncLogger.log(SyncLogLevel.INFO, "test", "Message after second init")
        logger2 = SyncLogger._instance
        level2 = SyncLogger.logger_level

        # Assert singleton and level didn't change
        assert logger1 is logger2
        assert level1 == level2


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
