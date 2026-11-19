#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Regression tests for ``utils/cluster_manager.py``'s module-load-arg
handling.

``build_loadmodule_args`` previously ran every module path through
``shlex.split`` unconditionally, even when the path had no trailing
config suffix to isolate. ``shlex.split`` tokenizes its input as shell
syntax, so a path containing a single apostrophe (legal on both Linux and
macOS, e.g. a directory named after a user called ``o'brien``) raised
``ValueError: No closing quotation`` before the no-configuration branch
could even run -- ``start_server`` would then fail to start the server
for a reason that has nothing to do with its actual module or config.
"""

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "utils"))

from cluster_manager import (  # type: ignore[import-not-found]  # noqa: E402
    build_loadmodule_args,
)


class TestBuildLoadmoduleArgs:
    def test_plain_path_is_passed_through_unchanged(self):
        path = "/usr/lib/libsearch.so"
        assert build_loadmodule_args(path) == [path]

    def test_path_with_spaces_and_no_config_is_passed_through_unchanged(self):
        # e.g. a Windows "Program Files" style path, or any directory with
        # a space in its name.
        path = "/usr/local/Program Files/libsearch.so"
        assert build_loadmodule_args(path) == [path]

    def test_path_with_a_config_suffix_splits_only_the_suffix(self):
        path = "/usr/lib/libsearch.so --emulate-release 1.3.0"
        assert build_loadmodule_args(path) == [
            "/usr/lib/libsearch.so",
            "--emulate-release",
            "1.3.0",
        ]

    def test_path_with_multiple_config_args_splits_each_into_its_own_token(self):
        path = "/usr/lib/libsearch.so --use-coordinator yes"
        assert build_loadmodule_args(path) == [
            "/usr/lib/libsearch.so",
            "--use-coordinator",
            "yes",
        ]

    def test_path_with_an_apostrophe_and_no_config_does_not_raise(self):
        # The regression this test guards: shlex.split on a bare path
        # containing an apostrophe used to raise ValueError("No closing
        # quotation") before the no-configuration branch could preserve
        # the path. A plain path with no config suffix must never be
        # tokenized as shell syntax at all.
        path = "/Users/o'brien/modules/libsearch.so"
        assert build_loadmodule_args(path) == [path]

    def test_path_with_an_apostrophe_and_a_config_suffix_splits_only_the_suffix(self):
        path = "/Users/o'brien/modules/libsearch.so --emulate-release 1.3.0"
        assert build_loadmodule_args(path) == [
            "/Users/o'brien/modules/libsearch.so",
            "--emulate-release",
            "1.3.0",
        ]


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
