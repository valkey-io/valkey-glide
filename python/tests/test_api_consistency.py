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
        # _CompatFuture
        "done",
        "result",
        "set_exception",
        "set_result",
        # opentelemetry
        "create_otel_span",
        "drop_otel_span",
        "get_endpoint",
        "get_metrics",
        "get_traces",
        "init_opentelemetry",
        "set_traces",
        # Logger
        "is_lower",
        "py_init",
        "py_log",
        # Lazy PubSub methods - async-only, confusing in sync context
        "subscribe_lazy",
        "unsubscribe_lazy",
        "psubscribe_lazy",
        "punsubscribe_lazy",
        "ssubscribe_lazy",
        "sunsubscribe_lazy",
        # others
        "init_callback",
        "create_leaked_bytes_vec",
        "create_leaked_value",
        "start_socket_listener_external",
        "value_from_pointer",
    ],
    "sync_only": [],
}

EXCLUDED_API_FILENAMES = {
    "async_only": [],
    "sync_only": ["_glide_ffi.py"],
}

EXCLUDED_TESTS = {
    "async_only": [
        "test_statistics",
        "test_UDS_socket_connection_failure",
        "test_cancelled_request_handled_gracefully",
        "test_connection_timeout_on_unavailable_host",
        "test_invalid_tls_config_fails_fast",
        # Async-specific PubSub tests (use lazy subscription methods which sync doesn't support)
        "test_lazy_client_multiple_subscription_types",  # Tests with a lazy (deferred) connection.
        "test_lazy_vs_blocking_timeout",  # Tests subscribe_lazy() method
        # Async-specific PubSub tests (use callbacks or async patterns)
        "test_config_subscription_with_empty_set_is_allowed",
        "test_pubsub_callback_only_raises_error_on_get_methods",
        "test_pubsub_exact_happy_path_custom_command",
        "test_pubsub_reconciliation_interval_config",
        "test_punsubscribe_pattern",
        "test_ssubscribe_channels_different_slots",
        "test_subscription_metrics_on_acl_failure",
        "test_sunsubscribe_channels_different_slots",
        "test_sunsubscribe_sharded_channel",
        "test_unsubscribe_all_subscription_types",
        "test_unsubscribe_exact_channel",
        "test_subscription_sync_timestamp_metric_on_success",
        # Reconnection tests - complex async behavior not easily replicated in sync
        "test_resubscribe_after_connection_kill_exact_channels",
        "test_resubscribe_after_connection_kill_patterns",
        "test_resubscribe_after_connection_kill_sharded",
        "test_resubscribe_after_connection_kill_many_exact_channels",
        "test_subscription_metrics_repeated_reconciliation_failures",
        # Dynamic PubSub tests helper functions
        "subscribe_by_method",
        "unsubscribe_by_method",
        "psubscribe_by_method",
        "punsubscribe_by_method",
        "ssubscribe_by_method",
        "sunsubscribe_by_method",
        "wait_for_subscription_if_needed",
        "get_pubsub_channel_modes_from_client",
        "create_pubsub_subscription",
        "decode_pubsub_msg",
        "new_message",
        "assert_pubsub_messages",
        "poll_for_timestamp_change",
        # OpenTelemetry async helper function
        "wait_for_spans_to_be_flushed",
    ],
    "sync_only": [
        "test_sync_fork",
        # PubSub reconnection tests - not practical to test with sync + blocking + multithreading.
        "test_sync_resubscribe_after_connection_kill_exact_channels",
        "test_sync_resubscribe_after_connection_kill_patterns",
        "test_sync_resubscribe_after_connection_kill_sharded",
        "test_sync_resubscribe_after_connection_kill_many_exact_channels",
        "test_sync_subscription_metrics_repeated_reconciliation_failures",
        # Sync-specific dynamic PubSub tests and helpers
        "test_dynamic_subscribe_and_get_subscriptions",
        "test_subscribe_with_timeout",
        "test_unsubscribe_all",
        "test_subscription_metrics",
        "test_sync_clients_support_pubsub_reconciliation_interval",  # Original name before normalization
        "check_no_messages_left",
        "client_cleanup",
        "create_simple_pubsub_config",
        "create_two_clients_with_pubsub",
        "get_message_by_method",
    ],
}

EXCLUDED_TESTS_FILENAMES = {
    "async_only": [
        "test_deprecation_warnings.py",
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
    for file in directory.rglob("*"):
        if file.suffix in {".py", ".pyi"}:
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
