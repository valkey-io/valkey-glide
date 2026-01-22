# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import ast
from pathlib import Path

import glide as glide_async
import glide_shared
import glide_sync

async_exported_symbol_list = glide_async.__all__
sync_exported_symbol_list = glide_sync.__all__
shared_exported_symbol_list = glide_shared.__all__

PROJECT_ROOT_DIR = Path(__file__).parent.parent

ASYNC_GLIDE_DIR = PROJECT_ROOT_DIR / "glide-async" / "python" / "glide"
ASYNC_ROOT_INIT_FILE = ASYNC_GLIDE_DIR / "__init__.py"

SYNC_GLIDE_DIR = PROJECT_ROOT_DIR / "glide-sync" / "glide_sync"
SYNC_ROOT_INIT_FILE = SYNC_GLIDE_DIR / "__init__.py"

SHARED_GLIDE_DIR = PROJECT_ROOT_DIR / "glide-shared" / "glide_shared"
SHARED_ROOT_INIT_FILE = SHARED_GLIDE_DIR / "__init__.py"


def _get_export_rename_map(package: str):
    if package == "async":
        exported_symbol_list = async_exported_symbol_list
        root_init_file = ASYNC_ROOT_INIT_FILE
    elif package == "sync":
        exported_symbol_list = sync_exported_symbol_list
        root_init_file = SYNC_ROOT_INIT_FILE
    elif package == "shared":
        exported_symbol_list = shared_exported_symbol_list
        root_init_file = SHARED_ROOT_INIT_FILE

    source_code = root_init_file.read_text()
    tree = ast.parse(source_code)
    rename_map = {}
    for node in tree.body:
        if isinstance(node, ast.ImportFrom) or isinstance(node, ast.ImportFrom):
            for alias in node.names:
                if alias.asname and alias.asname in exported_symbol_list:
                    rename_map[alias.name] = alias.asname
    return rename_map


excluded_async_symbols = [
    # python/glide-async/python/glide/glide_client.py
    "_CompatFuture",  # ClassDef
    "_get_new_future_instance",  # FunctionDef
    "BaseClient",  # ClassDef
    # python/glide-async/python/glide/async_commands/standalone_commands.py
    "StandaloneCommands",  # ClassDef
    # python/glide-async/python/glide/async_commands/cluster_commands.py
    "ClusterCommands",  # ClassDef
    # python/glide-async/python/glide/async_commands/core.py
    "CoreCommands",  # ClassDef
    # python/glide-async/python/glide/opentelemetry.py
    "OpenTelemetry",  # ClassDef
]

excluded_sync_symbols = [
    # python/glide-sync/glide_sync/glide_client.py
    "BaseClient",  # ClassDef
    "FFIClientTypeEnum",  # ClassDef
    "ENCODING",  # Assignment
    "CURR_DIR",  # Assignment
    "LIB_FILE",  # Assignment
    "find_libglide_ffi",  # FunctionDef
    # python/glide-sync/glide_sync/sync_commands/cluster_commands.py
    "ClusterCommands",  # ClassDef
    # python/glide-sync/glide_sync/sync_commands/core.py
    "CoreCommands",  # ClassDef
    # python/glide-sync/glide_sync/sync_commands/standalone_commands.py
    "StandaloneCommands",  # ClassDef
    # python/glide-sync/glide_sync/sync_commands/cluster_scan_cursor.py
    "FINISHED_SCAN_CURSOR",  # Assignment
]

excluded_shared_symbols = [
    # python/glide-shared/glide_shared/constants.py
    "DEFAULT_READ_BYTES_SIZE",
    "T",
    "TRequest",
    # python/glide-shared/glide_shared/exceptions.py
    "get_request_error_class",  # FunctionDef
    # python/glide-shared/glide_shared/routes.py
    "to_protobuf_slot_type",  # FunctionDef
    "build_protobuf_route",  # FunctionDef
    "set_protobuf_route",  # FunctionDef
    # python/glide-shared/glide_shared/config.py
    "BaseClientConfiguration",  # ClassDef
    "AdvancedBaseClientConfiguration",  # ClassDef
    "load_root_certificates_from_file",  # FunctionDef
    "load_client_certificate_from_file",  # FunctionDef
    "load_client_key_from_file",  # FunctionDef
    # python/glide-shared/glide_shared/protobuf_codec.py
    "ProtobufCodec",  # ClassDef
    "PartialMessageException",  # Exception
    # python/glide-shared/glide_shared/commands/batch.py
    "BaseBatch",  # ClassDef
    # python/glide-shared/glide_shared/commands/batch_options.py
    "BaseBatchOptions",
    # python/glide-shared/glide_shared/commands/sorted_set.py
    "separate_keys",  # FunctionDef
    # python/glide-shared/glide_shared/commands/server_modules/ft_options/ft_constants.py
    "CommandNames",  # ClassDef
    "FtCreateKeywords",  # ClassDef
    "FtSearchKeywords",  # ClassDef
    "FtAggregateKeywords",  # ClassDef
    "FtProfileKeywords",  # ClassDef
]

allowed_missing_re_exports_in_async = [
    # python/glide-shared/glide_shared/exceptions.py
    "LoggerError"
]

allowed_missing_re_exports_in_sync: list[str] = []


class AnomalousSymbolVisitor(ast.NodeVisitor):
    def __init__(self, exported_symbols, rename_map, excluded_symbols):
        super().__init__()
        self.exported_symbol_list = exported_symbols
        self.export_rename_map = rename_map
        self.excluded_symbol_list = excluded_symbols
        self.anomalous_symbols = []

    def is_anomaly(self, name: str):
        return (
            name
            and not name.startswith("_")
            and name not in self.exported_symbol_list
            and name not in self.excluded_symbol_list
            and name not in self.export_rename_map
        )

    def generic_visit(self, node):
        # skip imports
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            return

        # node with name attribute, including class, function, etc
        if hasattr(node, "name") and self.is_anomaly(node.name):
            self.anomalous_symbols.append(node.name + ": " + node.__class__.__name__)

        # assignment without annotation
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and self.is_anomaly(target.id):
                    self.anomalous_symbols.append(target.id + ": " + "Assignment")
        # assignment with annotation
        elif isinstance(node, ast.AnnAssign):
            if isinstance(node.target, ast.Name) and self.is_anomaly(node.target.id):
                self.anomalous_symbols.append(
                    node.target.id
                    + ": "
                    + getattr(node.annotation, "id", "AssignmentWithAnnotation")
                )

        # only scan top level symbols
        if isinstance(node, ast.Module):
            # super().generic_visit() will visit all children
            return super().generic_visit(node)
        else:
            return


class TestAPIExport:
    # Return a list with all non-excluded symbols found that are not exported by the package
    def _check_package(
        self,
        package_name: str,
        package_dir: Path,
        exported_symbols,
        rename_map,
        excluded_symbols,
    ):
        exclude_path_patterns = ["protobuf", "__init__.py"]
        python_files = [
            file
            for file in package_dir.rglob("*.py")
            if not any(
                exclude_pattern in file.parts
                for exclude_pattern in exclude_path_patterns
            )
        ]

        aggregated_anomalous_symbols = {}
        for python_file in python_files:
            if python_file.stem in exported_symbols:
                continue
            source_code = python_file.read_text()
            tree = ast.parse(source_code)
            visitor = AnomalousSymbolVisitor(
                exported_symbols, rename_map, excluded_symbols
            )
            visitor.visit(tree)
            if visitor.anomalous_symbols:
                aggregated_anomalous_symbols[
                    f"{package_name}:{python_file.relative_to(PROJECT_ROOT_DIR)}"
                ] = visitor.anomalous_symbols

        return aggregated_anomalous_symbols

    def test_api_export(self):
        """Test that all non-excluded symbols are exported by the package"""
        all_anomalies = {}

        # async
        all_anomalies.update(
            self._check_package(
                "async",
                ASYNC_GLIDE_DIR,
                async_exported_symbol_list,
                _get_export_rename_map("async"),
                excluded_async_symbols,
            )
        )

        # sync
        all_anomalies.update(
            self._check_package(
                "sync",
                SYNC_GLIDE_DIR,
                sync_exported_symbol_list,
                _get_export_rename_map("sync"),
                excluded_sync_symbols,
            )
        )

        # shared
        all_anomalies.update(
            self._check_package(
                "shared",
                SHARED_GLIDE_DIR,
                shared_exported_symbol_list,
                _get_export_rename_map("shared"),
                excluded_shared_symbols,
            )
        )

        assert not all_anomalies, (
            "Unexported symbol found. "
            + "Please review and either export it, or add it to the excluded_symbol_list.\n"
            + "\n".join(
                [
                    f"{key}:\n    " + "\n    ".join(value)
                    for key, value in all_anomalies.items()
                ]
            )
        )

    def test_shared_symbols_re_exported(self):
        """Test that all shared package symbols are re-exported by both async and sync packages"""

        missing_from_async = []
        missing_from_sync = []

        for shared_symbol in shared_exported_symbol_list:
            # Check if symbol is exported by async package
            if (
                shared_symbol not in async_exported_symbol_list
                and shared_symbol not in allowed_missing_re_exports_in_async
            ):
                missing_from_async.append(shared_symbol)

            # Check if symbol is exported by sync package
            if (
                shared_symbol not in sync_exported_symbol_list
                and shared_symbol not in allowed_missing_re_exports_in_sync
            ):
                missing_from_sync.append(shared_symbol)

            assert not missing_from_async, (
                "Shared symbols missing from async package exports. "
                + f"Missing symbols: {missing_from_async}"
            )

            assert not missing_from_sync, (
                "Shared symbols missing from sync package exports. "
                + f"Missing symbols: {missing_from_sync}"
            )
