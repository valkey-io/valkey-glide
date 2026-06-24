# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os
import sys

for path in ("../glide-async", "../glide-sync", "../glide-shared"):
    sys.path.insert(0, os.path.abspath(path))

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
    "glide_shared.protobuf",
    "pytest",
    "google",
    "cffi",
]  # Prevents confusion in sphinx with imports

autodoc_typehints = "description"

autodoc_default_options = {
    "ignore-module-all": True,  # Prevents duplicate warnings with methods in parent module and its submodules
}

suppress_warnings = ["autodoc"]


def avoid_duplicate(app, what, name, obj, skip, options):
    # Skip special attributes and dataclass fields from re-exported classes
    # that cause duplicate object description warnings. These attributes
    # will still appear in the documentation at their canonical location.
    exclusions = (
        "__weakref__",
        "__doc__",
        "__module__",
        "__dict__",
        # PubSubSubscriptions
        "channels_and_patterns",
        "callback",
        "context",
        # PubSubMsg
        "message",
        "channel",
        "pattern",
        # CompressionConfiguration
        "enabled",
        "backend",
        "compression_level",
        "min_compression_size",
        "max_decompressed_size",
        # LatencyEntry
        "time",
        "latency",
        # LatencyEventInfo
        "event_name",
        "latest_time",
        "latest_duration",
        "max_duration",
        "sum",
        "count",
        # MonitorMsg
        "timestamp",
        "db",
        "client_addr",
        "command",
        "args",
    )
    # Check if the attribute name itself is in exclusions
    if name in exclusions:
        return True
    return None


def setup(app):
    app.connect("autodoc-skip-member", avoid_duplicate)


# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output


html_theme = "sphinx_rtd_theme"
