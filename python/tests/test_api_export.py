# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
import ast
from pathlib import Path

import glide

exported_symbol_list = glide.__all__


def _get_export_rename_map():
    glide.__all__
    root_init_file = Path(__file__).parent.parent / "python" / "glide" / "__init__.py"
    source_code = root_init_file.read_text()
    tree = ast.parse(source_code)
    rename_map = {}
    for node in tree.body:
        if isinstance(node, ast.ImportFrom) or isinstance(node, ast.ImportFrom):
            for alias in node.names:
                if alias.asname and alias.asname in exported_symbol_list:
                    rename_map[alias.name] = alias.asname
    return rename_map


export_rename_map = _get_export_rename_map()

excluded_symbol_list = [
    # python/python/glide/constants.py
    "DEFAULT_READ_BYTES_SIZE",  # int
    "T",  # TypeVar
    "TRequest",  # Union
    # python/python/glide/glide_client.py
    "get_request_error_class",  # FunctionDef
    "_CompatFuture",  # ClassDef
    "_get_new_future_instance",  # FunctionDef
    "BaseClient",  # ClassDef
    # python/python/glide/routes.py
    "to_protobuf_slot_type",  # FunctionDef
    "set_protobuf_route",  # FunctionDef
    # python/python/glide/config.py
    "BaseClientConfiguration",  # ClassDef
    # python/python/glide/protobuf_codec.py
    "ProtobufCodec",  # ClassDef
    "PartialMessageException",  # Exception
    # python/python/glide/async_commands/batch.py
    "BaseBatch",  # ClassDef
    # python/python/glide/async_commands/standalone_commands.py
    "StandaloneCommands",  # ClassDef
    # python/python/glide/async_commands/cluster_commands.py
    "ClusterCommands",  # ClassDef
    # python/python/glide/async_commands/core.py
    "CoreCommands",  # ClassDef
    # python/python/glide/async_commands/sorted_set.py
    "separate_keys",  # FunctionDef
    # python/python/glide/async_commands/server_modules/ft_options/ft_constants.py
    "CommandNames",  # ClassDef
    "FtCreateKeywords",  # ClassDef
    "FtSearchKeywords",  # ClassDef
    "FtAggregateKeywords",  # ClassDef
    "FtProfileKeywords",  # ClassDef
    "AdvancedBaseClientConfiguration",  # ClassDef
]


class AnomalousSymbolVisitor(ast.NodeVisitor):
    def __init__(self):
        super().__init__()
        self.anomalous_symbols = []

    @staticmethod
    def is_anomaly(name: str):
        return (
            name
            and not name.startswith("_")
            and name not in exported_symbol_list
            and name not in excluded_symbol_list
            and name not in export_rename_map
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
    def test_api_export(self):
        """
        Tests if there's any public symbols that is not in either the `__all__`
        list, or the `excluded_symbol_list` above.
        """

        python_glide_directory = Path(__file__).parent.parent / "glide"
        project_root_dir = python_glide_directory.parent.parent.parent
        exclude_path_patterns = ["protobuf", "__init__.py"]
        python_files = [
            file
            for file in python_glide_directory.rglob("*.py")
            if not any(
                exclude_pattern in file.parts
                for exclude_pattern in exclude_path_patterns
            )
        ]

        aggregated_anomalous_symbols = {}
        for python_file in python_files:
            # skip the entire file if the file as a module has been exported
            if python_file.stem in exported_symbol_list:
                continue
            source_code = python_file.read_text()
            tree = ast.parse(source_code)
            visitor = AnomalousSymbolVisitor()
            visitor.visit(tree)
            if visitor.anomalous_symbols:
                aggregated_anomalous_symbols[
                    str(python_file.relative_to(project_root_dir))
                ] = visitor.anomalous_symbols
        assert not aggregated_anomalous_symbols, (
            "Unexported symbol found. "
            + "Please review and either export it, or add it to the excluded_symbol_list.\n"
            + "\n".join(
                [
                    f"{key}" + ":" + "\n    " + "\n    ".join(value)
                    for key, value in aggregated_anomalous_symbols.items()
                ]
            )
        )
