# ------------------------------------------------------------------------------
# setup.py for valkey-glide-sync
# ------------------------------------------------------------------------------
# This setup script defines a Python package that wraps a Rust-based FFI layer.
# It integrates with Cargo for building the Rust component, and vendors in
# supporting Rust crates (`ffi`, `glide-core`, `logger_core`) during sdist
# creation. It includes:
#
# - Custom `build_ext` command to build the Rust shared library.
# - Custom `sdist` command to copy required Rust crates for packaging.
# - Custom `build_py` command to vendor glide_shared either from source or sdist.
# - A `clean` command to clean up all build artifacts.
#
# Environment Variables:
# - `GLIDE_SYNC_RELEASE=1` triggers release builds and vendoring for sdist.
#
# Usage:
#   python setup.py build_ext     # Build Rust lib
#   python setup.py sdist         # Create source tarball with vendored deps
#   python setup.py bdist_wheel   # Create wheel
#   python setup.py clean         # Clean all artifacts
# ------------------------------------------------------------------------------

import os
import shutil
import subprocess
from pathlib import Path
import sys
from setuptools import setup
from setuptools.command.build_py import build_py as build_py_orig
from setuptools.command.build_ext import build_ext as build_ext_orig
from setuptools.command.sdist import sdist as sdist_orig
from setuptools.dist import Distribution
from wheel.bdist_wheel import bdist_wheel as bdist_wheel_orig
from distutils.core import Command

# Paths
ROOT = Path(__file__).resolve().parent                      # glide-sync/
REAL_FFI_PATH = ROOT.parent.parent / "ffi"                 # ../../ffi
LOCAL_FFI_SYMLINK = ROOT / "ffi"                           # glide-sync/ffi

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

# ------------------------------------------------------------------------------
# Custom setuptools commands
# ------------------------------------------------------------------------------

class CleanCommand(Command):
    """Custom clean command to remove build artifacts."""
    user_options = []

    def initialize_options(self): pass
    def finalize_options(self): pass

    def run(self):
        import glob
        paths_to_remove = [
            "build", "dist", "*.egg-info", "glide_shared",
            "ffi", "glide-core", "logger_core",
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
        self.ensure_ffi_symlink()

        # Detect release mode
        release = os.environ.get("RELEASE_MODE", "0") == "1"

        # Set env for Cargo build
        env = os.environ.copy()
        env.update({
            "GLIDE_NAME": env.get("GLIDE_NAME", "GlidePySync"),
            "GLIDE_VERSION": env.get("GLIDE_VERSION", "0.0.0"),
        })

        print(f"[INFO] Building Rust FFI lib with cargo in {LOCAL_FFI_SYMLINK}")
        subprocess.run(
            ["cargo", "build"] + (["--release"] if release else []),
            cwd=LOCAL_FFI_SYMLINK,
            env=env,
            check=True
        )

        # Determine shared library path based on platform
        target_dir = "release" if release else "debug"
        suffix = {
            "linux": ".so",
            "darwin": ".dylib",
            "win32": ".dll"
        }[sys.platform]
        lib_name = "libglide_ffi" + suffix

        built_lib = LOCAL_FFI_SYMLINK / "target" / target_dir / lib_name
        dest_dir = Path(self.build_lib) / "glide_sync"
        dest_dir.mkdir(parents=True, exist_ok=True)
        print(f"[INFO] Copying {built_lib} → {dest_dir / lib_name}")
        shutil.copy2(built_lib, dest_dir / lib_name)

        super().run()

    def ensure_ffi_symlink(self):
        """Ensure ffi/ is a symlink pointing to ../../ffi"""
        if not LOCAL_FFI_SYMLINK.exists():
            print(f"[INFO] Creating symlink: {LOCAL_FFI_SYMLINK} → {REAL_FFI_PATH}")
            LOCAL_FFI_SYMLINK.symlink_to(REAL_FFI_PATH, target_is_directory=True)

class sdist(sdist_orig):
    """Vendors Rust sources into the Python package before creating sdist"""
    def run(self):
        print("[INFO] Preparing source distribution (sdist) with vendored Rust sources")

        # Paths to vendor for packaging
        to_copy = {
            "glide_shared": ROOT.parent / "glide-shared" / "glide_shared",
            "ffi": ROOT.parent.parent / "ffi",
            "glide-core": ROOT.parent.parent / "glide-core",
            "logger_core": ROOT.parent.parent / "logger_core",
        }

        # Ignore compiled/test directories
        def ignore_dirs(_, names):
            return [n for n in names if n in {"target", "tests"}]

        # Copy each relevant Rust folder into the Python project root
        for name, src_path in to_copy.items():
            dest_path = ROOT / name
            if dest_path.exists():
                remove_existing(dest_path)
            print(f"[INFO] Copying {src_path} → {dest_path}")
            shutil.copytree(src_path, dest_path, ignore=ignore_dirs)

        super().run()

class build_py(build_py_orig):
    """Ensure required tools are available and vendor glide_shared"""
    def run(self):
        # Check for required tools in PATH and extend PATH if missing
        for tool, hint_path in [("cargo", "~/.cargo/bin"), ("protoc", "~/.local/bin")]:
            if shutil.which(tool) is None:
                os.environ["PATH"] += os.pathsep + os.path.expanduser(hint_path)
                if shutil.which(tool) is None:
                    raise RuntimeError(f"[ERROR] Failed to find {tool} in PATH")

        # Determine if building from sdist (PKG-INFO is a common heuristic)
        from_sdist = Path("PKG-INFO").exists()
        source = ROOT / "glide_shared" if from_sdist else ROOT.parent / "glide-shared" / "glide_shared"
        dest = Path(self.build_lib) / "glide_shared"

        print(f"[INFO] Vendoring glide_shared from {source} → {dest}")
        shutil.copytree(source, dest, dirs_exist_ok=True)

        super().run()

# ------------------------------------------------------------------------------
# Setup configuration
# ------------------------------------------------------------------------------

setup(
    name="valkey-glide-sync",
    packages=["glide_sync", "glide_shared"],
    install_requires=[
        "cffi>=1.0.0",
        "typing-extensions>=4.8.0",
        "protobuf>=3.20",
    ],
    package_data={"glide_sync": ["*.so", "*.dll", "*.dylib", "*.pyi", "py.typed"]},
    distclass=BinaryDistribution,
    cmdclass={
        "build_py": build_py,
        "build_ext": build_ext,
        "bdist_wheel": bdist_wheel,
        "sdist": sdist,
        "clean": CleanCommand,
    },
)
