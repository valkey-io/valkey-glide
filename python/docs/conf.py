# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os
import sys

sys.path.insert(0, os.path.abspath("../python"))

# -- Project information -----------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#project-information

project = "Valkey GLIDE"
copyright = "2025, Valkey GLIDE Contributors"
author = "Valkey GLIDE Contributors"
release = "1.3.1"

# -- General configuration ---------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#general-configuration

extensions = [
    "sphinx.ext.napoleon",
    "sphinx.ext.autodoc",
]

templates_path = ["_templates"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
autodoc_mock_imports = [
    "glide.glide",
    "glide.protobuf",
    "pytest",
    "google",
]  # Prevents confusion in sphinx with imports

autodoc_typehints = "description"

autodoc_default_options = {
    "ignore-module-all": True,  # Prevents duplicate warnings with methods in parent module and its submodules
}

suppress_warnings = ["autodoc"]


def avoid_duplicate(app, what, name, obj, skip, options):
    # We skip some special refs and PubSub duplicate warning messages that
    # weren't captured in ignore-module-all. These PubSub attributes
    # will still appear in the documentation.
    exclusions = (
        "__weakref__",
        "__doc__",
        "__module__",
        "__dict__",
        "callback",
        "channels_and_patterns",
        "context",
        "channel",
        "message",
        "pattern",
    )
    module = getattr(obj, "__module__", "")
    formatted = f"{module}.{name}" if module else name
    if formatted in exclusions:
        return formatted
    return None


def setup(app):
    app.connect("autodoc-skip-member", avoid_duplicate)


# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output


html_theme = "sphinx_rtd_theme"
