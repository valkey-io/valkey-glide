#!/usr/bin/python3

# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""Regression tests for legacy module proxy introspection and deprecation warnings.

Addresses Issue #7443:
- Inspecting legacy modules (e.g. inspect.stack, inspect.getmodule, hasattr __file__)
  must never emit DeprecationWarning.
- With warnings treated as errors (-W error or filterwarnings = error), introspection
  must succeed without raising exceptions.
- Real access to deprecated module symbols (e.g. glide.exceptions.RequestError) must
  still emit DeprecationWarning on first access.
"""

import ast
import inspect
import sys
import types
import warnings
from pathlib import Path

import pytest


def _load_legacy_module_class():
    """Extract and compile the exact _LegacyModule class definition from __init__.py."""
    init_file = (
        Path(__file__).resolve().parent.parent
        / "glide-async"
        / "python"
        / "glide"
        / "__init__.py"
    )
    with open(init_file, "r", encoding="utf-8") as f:
        source = f.read()

    module_ast = ast.parse(source)
    legacy_class_node = None
    for node in module_ast.body:
        if isinstance(node, ast.ClassDef) and node.name == "_LegacyModule":
            legacy_class_node = node
            break

    assert (
        legacy_class_node is not None
    ), "_LegacyModule class not found in glide/__init__.py"

    code = compile(
        ast.Module(body=[legacy_class_node], type_ignores=[]),
        str(init_file),
        "exec",
    )
    scope = {
        "types": types,
        "warnings": warnings,
        "DeprecationWarning": DeprecationWarning,
    }
    exec(code, scope)
    return scope["_LegacyModule"], scope


class TestLegacyModuleIntrospection:
    """Verifies that introspection does not emit DeprecationWarning."""

    @pytest.fixture(autouse=True)
    def setup_legacy_module(self):
        legacy_class, scope = _load_legacy_module_class()

        class MockGlide:
            __file__ = "/path/to/glide/__init__.py"
            __spec__ = type("Spec", (), {"name": "glide"})()
            __path__ = ["/path/to/glide"]
            __doc__ = "Valkey GLIDE Client"
            RequestError = type("RequestError", (Exception,), {})
            GlideClient = type("GlideClient", (), {})

        mock_glide = MockGlide()
        scope["_glide_module"] = mock_glide

        proxy = legacy_class("glide.exceptions")
        sys.modules["glide.exceptions"] = proxy
        self.proxy = proxy
        self.mock_glide = mock_glide

        yield

        sys.modules.pop("glide.exceptions", None)

    def test_inspect_stack_does_not_warn_under_error_filter(self):
        """inspect.stack() walks sys.modules checking hasattr(m, '__file__').

        Under -W error, this previously crashed coverage.py and pytest-xdist
        workers with an INTERNALERROR DeprecationWarning.
        """
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            # Must not raise DeprecationWarning
            inspect.stack()

    def test_inspect_getmodule_does_not_warn(self):
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            inspect.getmodule(self.proxy)

    def test_hasattr_file_and_standard_dunders_do_not_warn(self):
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            assert hasattr(self.proxy, "__file__") is True
            assert self.proxy.__file__ == self.mock_glide.__file__
            assert hasattr(self.proxy, "__spec__") is True
            assert hasattr(self.proxy, "__path__") is True
            assert hasattr(self.proxy, "__doc__") is True

    def test_non_existent_dunder_raises_attribute_error_without_warning(self):
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            assert hasattr(self.proxy, "__wrapped__") is False
            with pytest.raises(AttributeError):
                getattr(self.proxy, "__wrapped__")

    def test_dir_returns_symbols_without_warning(self):
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            symbols = dir(self.proxy)
            assert "RequestError" in symbols
            assert "GlideClient" in symbols


class TestLegacyModuleDeprecationWarning:
    """Verifies that accessing deprecated symbols still emits DeprecationWarning."""

    @pytest.fixture(autouse=True)
    def setup_legacy_module(self):
        legacy_class, scope = _load_legacy_module_class()

        class MockGlide:
            __file__ = "/path/to/glide/__init__.py"
            RequestError = type("RequestError", (Exception,), {})
            GlideClient = type("GlideClient", (), {})

        mock_glide = MockGlide()
        scope["_glide_module"] = mock_glide

        proxy = legacy_class("glide.exceptions")
        sys.modules["glide.exceptions"] = proxy
        self.proxy = proxy
        self.mock_glide = mock_glide

        yield

        sys.modules.pop("glide.exceptions", None)

    def test_real_symbol_access_emits_deprecation_warning(self):
        with warnings.catch_warnings(record=True) as recorded:
            warnings.simplefilter("always")
            sym = self.proxy.RequestError
            assert sym is self.mock_glide.RequestError
            assert len(recorded) == 1
            assert issubclass(recorded[0].category, DeprecationWarning)
            assert (
                "Importing from 'glide.exceptions' is deprecated. "
                "Please import directly from 'glide' instead."
                in str(recorded[0].message)
            )

    def test_subsequent_symbol_access_does_not_re_warn(self):
        with warnings.catch_warnings(record=True) as recorded:
            warnings.simplefilter("always")
            _ = self.proxy.RequestError
            assert len(recorded) == 1

            # Second access must not re-warn
            _ = self.proxy.GlideClient
            assert len(recorded) == 1

    def test_error_filter_raises_on_real_symbol_access(self):
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            with pytest.raises(
                DeprecationWarning,
                match="Importing from 'glide.exceptions' is deprecated",
            ):
                _ = self.proxy.RequestError


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
