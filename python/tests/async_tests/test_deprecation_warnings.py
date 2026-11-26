# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

# isort:skip_file
# flake8: noqa: F401

import importlib
import re
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


@pytest.fixture
def legacy_proxies():
    """Every legacy proxy, with its one-shot warning re-armed for the test and restored after.

    Covers all of glide._legacy_modules, including the package-style ones such as
    glide.async_commands.server_modules. A name is proxied only if it wasn't already in
    sys.modules when glide was imported (glide.async_commands is the real package), hence
    the guard.
    """
    from glide import _legacy_modules, _LegacyModule

    proxies = [
        sys.modules[name]
        for name in _legacy_modules
        if name in sys.modules and isinstance(sys.modules[name], _LegacyModule)
    ]
    assert proxies, "no legacy proxies registered"
    saved = [mod._warned for mod in proxies]
    for mod in proxies:
        mod._warned = False
    yield proxies
    for mod, warned in zip(proxies, saved):
        mod._warned = warned


def _error_on_glide_deprecation():
    """Turn only GLIDE's legacy-import warning into an error."""
    import warnings

    warnings.filterwarnings(
        "error", category=DeprecationWarning, message=r"Importing from 'glide\."
    )


class TestDeprecationWarnings:
    """Test deprecation warnings for backward compatibility modules."""

    @pytest.mark.parametrize(
        "module_name,symbol_name", _legacy_modules_and_symbols.items()
    )
    def test_legacy_module_deprecation_warning(
        self, legacy_proxies, module_name, symbol_name
    ):
        """Test that importing from legacy modules shows deprecation warning."""

        with pytest.warns(
            DeprecationWarning, match=f"Importing from '{module_name}' is deprecated"
        ):
            # Import the module and access an attribute to trigger the warning
            exec(f"from {module_name} import {symbol_name}")

    def test_deprecated_imports_still_work(self, legacy_proxies):
        """Test that deprecated imports still provide the correct classes."""
        import warnings

        # The fixture re-arms every proxy; this test checks identity, not warnings.
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            self._check_deprecated_symbols()

    def _check_deprecated_symbols(self):
        for module_name, symbol_name in _legacy_modules_and_symbols.items():
            # Import from deprecated path
            deprecated_module = importlib.import_module(module_name)
            deprecated_symbol = getattr(deprecated_module, symbol_name)

            # Import from preferred path
            from glide import __dict__ as glide_dict

            preferred_symbol = glide_dict[symbol_name]

            # Verify they're the same classes
            assert deprecated_symbol is preferred_symbol

    def test_legacy_module_introspection_does_not_warn(self, legacy_proxies):
        """Test that inspecting sys.modules (e.g. inspect.stack, inspect.getmodule) does not warn (#7443)."""
        import inspect
        import warnings

        # Introspection should never emit DeprecationWarning
        with warnings.catch_warnings():
            _error_on_glide_deprecation()
            # Standard inspect stack/module inspection queries dunder attributes like __file__, __spec__
            inspect.stack()
            for mod in legacy_proxies:
                assert mod._warned is False
                assert not hasattr(mod, "__file__")
                assert hasattr(mod, "__name__")
                assert hasattr(mod, "__all__")
                assert mod.__all__ is not None
                assert mod._warned is False

    def test_probing_a_missing_name_does_not_warn(self, legacy_proxies):
        """Tools probe modules for names they may not have (IPython checks `_repr_html_`).
        A name glide doesn't have must raise AttributeError without warning or using up
        the one-shot warning, so the user's real legacy import still warns."""
        import warnings

        with warnings.catch_warnings():
            _error_on_glide_deprecation()
            for mod in legacy_proxies:
                assert not hasattr(mod, "_repr_html_")
                assert not hasattr(mod, "_ipython_canary_method_should_not_exist_")
                assert mod._warned is False, mod.__name__

    def test_missing_name_error_names_the_legacy_module(self, legacy_proxies):
        """A missing name raises AttributeError naming the legacy module the user used,
        not glide. Checked on a plain proxy and on a package proxy, which goes through the
        submodule fallback. The error keeps name/obj, which Python uses for its "Did you
        mean" hint. (Not warning is covered by test_probing_a_missing_name_does_not_warn.)
        """
        for module_name in ("glide.exceptions", "glide.async_commands.server_modules"):
            mod = sys.modules[module_name]
            with pytest.raises(
                AttributeError,
                match=rf"module '{re.escape(module_name)}' has no attribute 'Nope'",
            ) as excinfo:
                mod.Nope
            assert excinfo.value.name == "Nope"
            assert excinfo.value.obj is sys.modules["glide"]

    def test_nested_legacy_import_warns(self, legacy_proxies):
        """Importing a legacy submodule through a proxied legacy package warns at import
        time and binds the registered proxy. Kept out of parametrize so the ids don't
        contain "server_modules", which pytest.ini deselects."""
        import warnings

        parent = "glide.async_commands.server_modules"
        child = f"{parent}.ft_options"
        cases = [
            (f"from {parent} import ft_options as bound", parent, child),
            (f"import {child} as bound", parent, child),
            (
                f"from {child} import ft_search_options as bound",
                child,
                f"{child}.ft_search_options",
            ),
        ]
        for statement, warning_module, bound_module in cases:
            for mod in legacy_proxies:
                mod._warned = False
            scope = {}
            with warnings.catch_warnings(record=True) as recorded:
                warnings.simplefilter("always", DeprecationWarning)
                exec(statement, scope)
            assert scope["bound"] is sys.modules[bound_module], statement
            assert any(
                issubclass(w.category, DeprecationWarning)
                and str(w.message).startswith(
                    f"Importing from '{warning_module}' is deprecated"
                )
                for w in recorded
            ), statement

    def test_legacy_module_star_import(self, legacy_proxies):
        """Test that wildcard import from legacy module exposes public symbols and triggers warning on attribute access."""
        import warnings

        legacy_module_name = "glide.exceptions"

        # Wildcard import inspects __all__ and binds symbols, which triggers DeprecationWarning
        local_scope = {}
        with warnings.catch_warnings(record=True) as recorded_warnings:
            warnings.simplefilter("always", DeprecationWarning)
            exec(f"from {legacy_module_name} import *", {}, local_scope)

        # __all__ must be populated so names are bound in local_scope
        assert len(local_scope) > 0
        assert "RequestError" in local_scope
        # Verify DeprecationWarning was emitted when attributes were bound
        assert any(
            issubclass(w.category, DeprecationWarning)
            and f"Importing from '{legacy_module_name}' is deprecated" in str(w.message)
            for w in recorded_warnings
        )
