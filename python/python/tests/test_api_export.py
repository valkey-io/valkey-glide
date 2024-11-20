import ast
from pathlib import Path

import glide

exported_symbol_list = glide.__all__

excluded_symbol_list = [
    "__all__",
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
            return super().generic_visit(
                node
            )
        else:
            return


class TestAPIExport:
    def test_api_export(self):
        """
        Tests if there's any public symbols that is not in either the `__all__`
        list, or the `excluded_symbol_list` above.
        """
        print("\n\n")

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

        for python_file in python_files:
            # skip the entire file if the file as a module has been exported
            if python_file.stem in exported_symbol_list:
                continue
            source_code = python_file.read_text()
            tree = ast.parse(source_code)
            visitor = AnomalousSymbolVisitor()
            visitor.visit(tree)
            if visitor.anomalous_symbols:
                print(python_file.relative_to(project_root_dir))
                print("\n".join(visitor.anomalous_symbols))
                print()
        # TODO: accumulate all anomalous symbols from all files, and throw error with detailed symbol information
