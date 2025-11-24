# ------------------------------------------------------------------------------
# setup.py for valkey-glide-sync
# ------------------------------------------------------------------------------
# This setup script defines the build logic for a Python package that wraps a
# Rust-based FFI layer. It integrates with Cargo to compile the Rust component,
# and vendors required Rust crates (`ffi`, `glide-core`, `logger_core`) and the
# `glide_shared` Python package when building from source or creating sdists.
#
# It provides:
#
# - A custom `build_ext` command that builds the Rust shared library.
# - A custom `sdist` command that vendors required Rust crates and the
#   `glide_shared` Python package into the source distribution.
# - A custom `build_py` command that ensures vendored dependencies are available
#   for local development and builds.
# - A `clean` command to remove all generated build artifacts.
#
# Environment Variables:
# - `GLIDE_SYNC_RELEASE=1` — Enables release mode and triggers vendoring during sdist creation.
#
# Typical usage:
#   python setup.py build_ext      # Build the Rust shared library
#   python setup.py sdist          # Create a source tarball with vendored dependencies
#   python setup.py bdist_wheel    # Build a wheel
#   python setup.py clean          # Remove all build artifacts
# ------------------------------------------------------------------------------

import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from setuptools import Command, setup
from setuptools.command.build_ext import build_ext as build_ext_orig
from setuptools.command.build_py import build_py as build_py_orig
from setuptools.command.sdist import sdist as sdist_orig
from setuptools.dist import Distribution
from wheel.bdist_wheel import bdist_wheel as bdist_wheel_orig  # type: ignore


@dataclass(frozen=True)
class VendorFolder:
    source: Path
    dist: Path


ROOT = Path(__file__).resolve().parent  # glide-sync/

VENDORED_DEPENDENCIES = {
    "glide_shared": VendorFolder(
        source=ROOT.parent / "glide-shared" / "glide_shared",
        dist=ROOT / "glide_shared",
    ),
    "ffi": VendorFolder(
        source=ROOT.parent.parent / "ffi",
        dist=ROOT / "ffi",
    ),
    "glide-core": VendorFolder(
        source=ROOT.parent.parent / "glide-core",
        dist=ROOT / "glide-core",
    ),
    "logger_core": VendorFolder(
        source=ROOT.parent.parent / "logger_core",
        dist=ROOT / "logger_core",
    ),
}


# Helper to safely remove files, directories, or symlinks
def remove_existing(path: Path):
    if path.is_symlink():
        print(f"[INFO] Removing symlink: {path}")
        path.unlink()
    elif path.is_dir():
        print(f"[INFO] Removing directory: {path}")
        shutil.rmtree(path)
    elif path.exists():
        print(f"[INFO] Removing file: {path}")
        path.unlink()


def vendor_dependencies():
    """Copy vendored dependencies into local package structure."""
    print("[INFO] Vendoring Rust and Python shared dependencies")

    def ignore_dirs(_, names):
        return [n for n in names if n in {"target", "tests"}]

    for _name, folder in VENDORED_DEPENDENCIES.items():
        if folder.dist.exists():
            remove_existing(folder.dist)
        print(f"[INFO] Copying {folder.source} → {folder.dist}")
        shutil.copytree(folder.source, folder.dist, ignore=ignore_dirs)


def cleanup_vendored():
    """Remove all vendored dependency folders."""
    print("[INFO] Cleaning up vendored dependencies")
    for folder in VENDORED_DEPENDENCIES.values():
        remove_existing(folder.dist)


# ------------------------------------------------------------------------------
# Custom setuptools commands
# ------------------------------------------------------------------------------


class CleanCommand(Command):
    """Custom clean command to remove build artifacts."""

    user_options = []  # type: ignore

    def initialize_options(self):
        pass

    def finalize_options(self):
        pass

    def run(self):
        import glob

        paths_to_remove = [
            "build",
            "dist",
            "*.egg-info",
            "glide_shared",
            "ffi",
            "glide-core",
            "logger_core",
        ]
        for pattern in paths_to_remove:
            # Perform glob under ROOT only
            for match in glob.glob(str(ROOT / pattern)):
                remove_existing(Path(match))


class BinaryDistribution(Distribution):
    """Marks the package as containing binary extensions (e.g., .so)"""

    def has_ext_modules(self):
        return True


class bdist_wheel(bdist_wheel_orig):
    """Customize wheel metadata to mark as non-pure Python"""

    def finalize_options(self):
        super().finalize_options()
        self.root_is_pure = False


class build_ext(build_ext_orig):
    """Builds the Rust FFI library using Cargo"""

    def run(self):
        # Detect release mode
        release = os.environ.get("RELEASE_MODE", "0") == "1"

        # Set env for Cargo build
        env = os.environ.copy()
        env.update(
            {
                "GLIDE_NAME": env.get("GLIDE_NAME", "GlidePySync"),
                "GLIDE_VERSION": env.get("GLIDE_VERSION", "0.0.0"),
            }
        )

        ffi_path = VENDORED_DEPENDENCIES["ffi"].dist
        print(f"[INFO] Building Rust FFI lib with cargo in {ffi_path}")
        subprocess.run(
            ["cargo", "build"] + (["--release"] if release else []),
            cwd=ffi_path,
            env=env,
            check=True,
        )

        # Determine shared library path based on platform
        target_dir = "release" if release else "debug"
        suffix = {"linux": ".so", "darwin": ".dylib", "win32": ".dll"}[sys.platform]
        lib_name = "libglide_ffi" + suffix

        built_lib = ffi_path / "target" / target_dir / lib_name
        dest_dir = Path(self.build_lib) / "glide_sync"
        dest_dir.mkdir(parents=True, exist_ok=True)
        print(f"[INFO] Copying {built_lib} → {dest_dir / lib_name}")
        shutil.copy2(built_lib, dest_dir / lib_name)

        super().run()
        cleanup_vendored()


class sdist(sdist_orig):
    """Vendors Rust sources into the Python package before creating sdist"""

    def run(self):
        print("[INFO] Preparing source distribution (sdist) with vendored Rust sources")
        vendor_dependencies()
        super().run()
        cleanup_vendored()


class build_py(build_py_orig):
    """Ensure required tools are available and vendor glide_shared"""

    def run(self):
        # Check for required tools in PATH and extend PATH if missing
        for tool, hint_path in [("cargo", "~/.cargo/bin"), ("protoc", "~/.local/bin")]:
            if shutil.which(tool) is None:
                os.environ["PATH"] += os.pathsep + os.path.expanduser(hint_path)
                if shutil.which(tool) is None:
                    raise RuntimeError(f"[ERROR] Failed to find {tool} in PATH")

        # Check if we're building from a source distribution (sdist) —
        # the presence of PKG-INFO is a typical indicator.
        from_sdist = Path("PKG-INFO").exists()

        if from_sdist:
            # When building from sdist, dependencies were already vendored during sdist creation.
            # Use the vendored folder within the package.
            source = ROOT / "glide_shared"
        else:
            # When building from a local/dev environment, vendor dependencies now.
            vendor_dependencies()
            source = ROOT.parent / "glide-shared" / "glide_shared"
        dest = Path(self.build_lib) / "glide_shared"

        print(f"[INFO] Vendoring glide_shared from {source} → {dest}")
        shutil.copytree(source, dest, dirs_exist_ok=True)
        super().run()


# ------------------------------------------------------------------------------
# Setup configuration
# ------------------------------------------------------------------------------

setup(
    name="valkey-glide-sync",
    package_data={"glide_sync": ["*.so", "*.dll", "*.dylib", "*.pyi", "py.typed"]},
    distclass=BinaryDistribution,
    cmdclass={
        "build_py": build_py,
        "build_ext": build_ext,
        "bdist_wheel": bdist_wheel,
        "sdist": sdist,
        "clean": CleanCommand,  # type: ignore
    },
)
