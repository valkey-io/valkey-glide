# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

# isort:skip_file
# flake8: noqa: F401

import importlib
import sys
import pytest

_legacy_modules_and_symbols = {
    "glide.exceptions": "RequestError",
    "glide.config": "GlideClientConfiguration",
    "glide.constants": "OK",
    "glide.routes": "AllNodes",
    "glide.async_commands.batch": "Batch",
    "glide.async_commands.batch_options": "BatchOptions",
    "glide.async_commands.bitmap": "BitmapIndexType",
    "glide.async_commands.command_args": "Limit",
    "glide.async_commands.server_modules": "glide_json",
    "glide.async_commands.server_modules.ft_options.ft_aggregate_options": "FtAggregateOptions",
    "glide.async_commands.server_modules.ft_options.ft_create_options": "FtCreateOptions",
    "glide.async_commands.server_modules.ft_options.ft_profile_options": "FtProfileOptions",
    "glide.async_commands.server_modules.ft_options.ft_search_options": "FtSearchOptions",
    "glide.async_commands.server_modules.glide_json": "JsonGetOptions",
    "glide.async_commands.sorted_set": "ScoreBoundary",
    "glide.async_commands.stream": "StreamAddOptions",
}


class TestDeprecationWarnings:
    """Test deprecation warnings for backward compatibility modules."""

    @pytest.mark.parametrize(
        "module_name,symbol_name", _legacy_modules_and_symbols.items()
    )
    def test_legacy_module_deprecation_warning(self, module_name, symbol_name):
        """Test that importing from legacy modules shows deprecation warning."""
        # Reset the _warned flag to ensure warning is triggered
        # This is necessary to reliably trigger the warning in tests
        if module_name in sys.modules:
            legacy_module = sys.modules[module_name]
            if hasattr(legacy_module, "_warned"):
                legacy_module._warned = False

        with pytest.warns(
            DeprecationWarning, match=f"Importing from '{module_name}' is deprecated"
        ):
            # Import the module and access an attribute to trigger the warning
            exec(f"from {module_name} import {symbol_name}")

    def test_deprecated_imports_still_work(self):
        """Test that deprecated imports still provide the correct classes."""
        for module_name, symbol_name in _legacy_modules_and_symbols.items():
            # Import from deprecated path
            deprecated_module = importlib.import_module(module_name)
            deprecated_symbol = getattr(deprecated_module, symbol_name)

            # Import from preferred path
            from glide import __dict__ as glide_dict

            preferred_symbol = glide_dict[symbol_name]

            # Verify they're the same classes
            assert deprecated_symbol is preferred_symbol

    def test_legacy_module_introspection_does_not_warn(self):
        """Test that inspecting sys.modules (e.g. inspect.stack, inspect.getmodule) does not warn (#7443)."""
        import inspect
        import warnings

        # Reset warning flags
        for module_name in _legacy_modules_and_symbols:
            if module_name in sys.modules:
                legacy_mod = sys.modules[module_name]
                if hasattr(legacy_mod, "_warned"):
                    legacy_mod._warned = False

        # Introspection should never emit DeprecationWarning
        with warnings.catch_warnings():
            warnings.simplefilter("error", DeprecationWarning)
            # Standard inspect stack/module inspection queries dunder attributes like __file__, __spec__
            inspect.stack()
            for module_name in _legacy_modules_and_symbols:
                mod = sys.modules[module_name]
                assert not hasattr(mod, "__file__")
                assert hasattr(mod, "__name__")

    def test_legacy_module_star_import(self):
        """Test that wildcard import from legacy module exposes public symbols and triggers warning on attribute access."""
        import warnings

        legacy_module_name = "glide.exceptions"
        if legacy_module_name in sys.modules:
            legacy_mod = sys.modules[legacy_module_name]
            if hasattr(legacy_mod, "_warned"):
                legacy_mod._warned = False

        # Wildcard import inspects __all__
        local_scope = {}
        with warnings.catch_warnings(record=True) as recorded_warnings:
            warnings.simplefilter("always", DeprecationWarning)
            exec(f"from {legacy_module_name} import *", {}, local_scope)

        # __all__ must be populated so names are bound in local_scope
        assert len(local_scope) > 0
        assert "RequestError" in local_scope
