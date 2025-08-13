# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import ast
from collections import defaultdict
from pathlib import Path
from typing import Callable, Dict, Optional, Set

PYTHON_DIR = Path(__file__).resolve().parent.parent
GLIDE_SYNC_COMMANDS_DIR = PYTHON_DIR / "glide-sync" / "glide_sync"
GLIDE_ASYNC_COMMANDS_DIR = PYTHON_DIR / "glide-async" / "python" / "glide"
TESTS_ASYNC_DIR = PYTHON_DIR / "tests" / "async_tests"
TESTS_SYNC_DIR = PYTHON_DIR / "tests" / "sync_tests"


EXCLUDED_API_FUNCTIONS = {
    "async_only": [
        # Script
        "script_flush",
        "script_exists",
        "invoke_script",
        "invoke_script_route",
        "script_kill",
        # PubSub
        "pubsub_shardchannels",
        "pubsub_shardnumsub",
        "pubsub_numsub",
        "pubsub_numpat",
        "publish",
        "pubsub_channels",
        "get_pubsub_message",
        "try_get_pubsub_message",
        # scan
        "scan",
        # _CompatFuture
        "done",
        "result",
        "set_exception",
        "set_result",
        # others
        "init_callback",
        "get_statistics",
    ],
    "sync_only": [],
}

EXCLUDED_API_FILENAMES = {
    "async_only": [
        "ft.py",
        "glide_json.py",
        "opentelemetry.py",
    ],
    "sync_only": ["_glide_ffi.py"],
}

EXCLUDED_TESTS = {
    "async_only": [
        # Script
        "test_script",
        "test_script_kill_no_route",
        "test_script_flush",
        "test_script_large_keys_no_args",
        "test_inflight_request_limit",
        "test_script_isnt_removed_while_another_instance_exists",
        "test_statistics",
        "test_script_kill_unkillable",
        "test_script_large_args_no_keys",
        "test_script_show",
        "run_long_script",
        "test_script_exists",
        "attempt_kill_writing_script",
        "test_script_binary",
        "script_kill_tests",
        "wait_and_kill_script",
        "test_script_large_keys_and_args",
        "test_script_kill_route",
        "run_writing_script",
    ],
    "sync_only": ["test_sync_fork"],
}

EXCLUDED_TESTS_FILENAMES = {
    "async_only": [
        "test_json.py",
        "test_opentelemetry.py",
        "test_pubsub.py",
        "test_scan.py",
        "test_ft.py",
    ],
    "sync_only": [],
}


def get_functions_from_file(file_path: Path) -> Set[str]:
    """Parse a Python file & return all top-level (non-private) function names."""
    with open(file_path, "r") as f:
        tree = ast.parse(f.read(), filename=str(file_path))
    return {
        node.name
        for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
        and not node.name.startswith("_")
    }


def get_all_functions_in_directory(
    directory: Path, exclude_filenames: Set[str], filename_prefix: Optional[str] = None
) -> Dict[str, Set[str]]:
    """
    Collect function names mapped to the files they appear in,
    excluding `__init__.py` files and excluded files.
    """
    functions_by_file = defaultdict(set)
    for file in directory.rglob("*.py"):
        if file.name.startswith("__") or file.name in exclude_filenames:
            continue
        if filename_prefix and not file.name.startswith(filename_prefix):
            continue
        for func in get_functions_from_file(file):
            functions_by_file[func].add(str(file))
    return functions_by_file


def remove_sync_prefix_from_test_name(name: str) -> str:
    """Strip sync test prefix for comparison."""
    return (
        name.replace("test_sync_", "test_", 1)
        if name.startswith("test_sync_")
        else name
    )


def filter_and_remove_prefix(
    functions: Dict[str, Set[str]],
    exclude: Set[str],
    normalize: Optional[Callable[[str], str]] = None,
) -> Dict[str, Set[str]]:
    """Filter out excluded/private functions and optionally normalize names."""
    result = {}
    for func, files in functions.items():
        if func in exclude or func.startswith("_"):
            continue
        normalized = normalize(func) if normalize else func
        result[normalized] = files
    return result


def compare_function_sets(
    async_dir,
    sync_dir,
    excluded_functions,
    excluded_filenames,
    error_message_prefix="Functions missing",
    normalize_sync: Optional[Callable[[str], str]] = None,
    filename_prefix: Optional[str] = None,
):
    async_functions = get_all_functions_in_directory(
        async_dir, set(excluded_filenames["async_only"]), filename_prefix
    )
    sync_functions = get_all_functions_in_directory(
        sync_dir, set(excluded_filenames["sync_only"]), filename_prefix
    )

    filtered_async = filter_and_remove_prefix(
        async_functions, set(excluded_functions["async_only"])
    )
    filtered_sync = filter_and_remove_prefix(
        sync_functions, set(excluded_functions["sync_only"]), normalize_sync
    )

    missing_in_async = set(filtered_sync) - set(filtered_async)
    missing_in_sync = set(filtered_async) - set(filtered_sync)

    def fmt_missing(missing, source):
        return "\n".join(
            f"  {func} from {', '.join(source.get(func, {'<unknown>'}))}"
            for func in sorted(missing)
        )

    assert not missing_in_async, (
        f"⚠️  {error_message_prefix} in async:\n"
        f"{fmt_missing(missing_in_async, sync_functions)}\n"
        "Please implement the async version or add it to `sync_only` in the appropriate exclusion list."
    )
    assert not missing_in_sync, (
        f"⚠️  {error_message_prefix} in sync:\n"
        f"{fmt_missing(missing_in_sync, async_functions)}\n"
        "Please implement the sync version or add it to `async_only` in the appropriate exclusion list."
    )


class TestConsistency:
    def test_api_consistency(self):
        compare_function_sets(
            GLIDE_ASYNC_COMMANDS_DIR,
            GLIDE_SYNC_COMMANDS_DIR,
            EXCLUDED_API_FUNCTIONS,
            EXCLUDED_API_FILENAMES,
            error_message_prefix="API Functions missing",
        )

    def test_tests_consistency(self):
        compare_function_sets(
            TESTS_ASYNC_DIR,
            TESTS_SYNC_DIR,
            EXCLUDED_TESTS,
            EXCLUDED_TESTS_FILENAMES,
            error_message_prefix="Tests missing",
            normalize_sync=remove_sync_prefix_from_test_name,
            filename_prefix="test",
        )
