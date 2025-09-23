# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import pytest


class TestDeprecationWarnings:
    """Test deprecation warnings for backward compatibility modules."""

    def test_glide_exceptions_deprecation_warning(self):
        """Test that importing from glide.exceptions shows deprecation warning."""
        with pytest.warns(DeprecationWarning, match="Importing from 'glide.exceptions' is deprecated"):
            from glide.exceptions import RequestError

    def test_glide_config_deprecation_warning(self):
        """Test that importing from glide.config shows deprecation warning."""
        with pytest.warns(DeprecationWarning, match="Importing from 'glide.config' is deprecated"):
            from glide.config import GlideClientConfiguration

    def test_glide_constants_deprecation_warning(self):
        """Test that importing from glide.constants shows deprecation warning."""
        with pytest.warns(DeprecationWarning, match="Importing from 'glide.constants' is deprecated"):
            from glide.constants import OK

    def test_glide_routes_deprecation_warning(self):
        """Test that importing from glide.routes shows deprecation warning."""
        with pytest.warns(DeprecationWarning, match="Importing from 'glide.routes' is deprecated"):
            from glide.routes import AllNodes

    def test_deprecated_imports_still_work(self):
        """Test that deprecated imports still provide the correct classes."""
        # Import from deprecated paths
        from glide.exceptions import RequestError as DeprecatedRequestError
        from glide.config import GlideClientConfiguration as DeprecatedConfig
        from glide.constants import OK as DeprecatedOK
        from glide.routes import AllNodes as DeprecatedAllNodes
        
        # Import from preferred paths
        from glide import RequestError, GlideClientConfiguration, OK, AllNodes
        
        # Verify they're the same classes
        assert DeprecatedRequestError is RequestError
        assert DeprecatedConfig is GlideClientConfiguration
        assert DeprecatedOK is OK
        assert DeprecatedAllNodes is AllNodes
