#!/usr/bin/env python3
# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import argparse
import os
import platform
import subprocess
import sys
from pathlib import Path
from shutil import copy2, copytree, rmtree, which
from typing import Any, Dict, List, Optional


def find_project_root() -> Path:
    root = Path(__file__).resolve().parent.parent
    print(f"[INFO] Project root determined at: {root}")
    return root


venv_ctx: Dict[str, Optional[Path]] = {
    "venv_dir": None,
    "venv_bin_dir": None,
    "python_exe": None,
}

# Constants
PROTO_REL_PATH = "glide-core/src/protobuf"
GLIDE_ROOT = find_project_root()
PYTHON_DIR = GLIDE_ROOT / "python"
GLIDE_SHARED_DIR = PYTHON_DIR / "glide-shared"
GLIDE_SYNC_DIR = PYTHON_DIR / "glide-sync"
GLIDE_ASYNC_DIR = PYTHON_DIR / "glide-async"
FFI_DIR = GLIDE_ROOT / "ffi"
FFI_OUTPUT_DIR_DEBUG = FFI_DIR / "target" / "debug"
FFI_OUTPUT_DIR_RELEASE = FFI_DIR / "target" / "release"
FFI_TARGET_LIB_NAME = "libglide_ffi.so"
GLIDE_SYNC_NAME = "GlidePySync"
GLIDE_ASYNC_NAME = "GlidePy"


def find_libglide_ffi(lib_dir: Path) -> Path:
    """
    Searches for the correct shared library file depending on the OS.
    """
    possible_names = {
        "Linux": "libglide_ffi.so",
        "Darwin": "libglide_ffi.dylib",
        "Windows": "glide_ffi.dll",
    }

    system = platform.system()
    lib_name = possible_names.get(system)
    if not lib_name:
        raise RuntimeError(f"Unsupported platform: {system}")

    lib_path = lib_dir / lib_name
    if not lib_path.exists():
        raise FileNotFoundError(f"Could not find {lib_name} in {lib_dir}")

    return lib_path


def set_venv_paths(custom_path: Optional[str] = None):
    venv_dir = Path(custom_path).resolve() if custom_path else PYTHON_DIR / ".env"
    venv_bin_dir = venv_dir / "bin"
    python_exe = venv_bin_dir / "python"
    venv_ctx["venv_dir"] = venv_dir
    venv_ctx["venv_bin_dir"] = venv_bin_dir
    venv_ctx["python_exe"] = python_exe


def check_dependencies() -> None:
    print("[INFO] Checking required dependencies...")
    if not which("rustc"):
        print("❌ Error: Rust is not installed.")
        sys.exit(1)
    print(f"[OK] Rust is installed at: {which('rustc')}")

    if not which("protoc"):
        print("❌ Error: protoc is not installed.")
        sys.exit(1)
    print(f"[OK] protoc is installed at: {which('protoc')}")

    version_output: str = subprocess.check_output(["protoc", "--version"], text=True)
    version = version_output.strip().split()[-1]
    major, minor, *_ = map(int, version.split("."))
    print(f"[INFO] Detected protoc version: {version}")
    if major < 3 or (major == 3 and minor < 2):
        print("❌ Error: protoc version must be >= 3.2.0.")
        sys.exit(1)


def prepare_python_env(no_cache: bool = False):
    if venv_ctx["venv_dir"] is None or venv_ctx["venv_bin_dir"] is None:
        print("❌ Error: virtual environment isn't set")
        sys.exit(1)
    if not venv_ctx["venv_dir"].exists():
        print("[INFO] Creating new Python virtual environment...")
        run_command(
            ["python3", "-m", "venv", str(venv_ctx["venv_dir"])], label="venv creation"
        )
    else:
        print("[INFO] Using existing Python virtual environment")

    pip_path = venv_ctx["venv_bin_dir"] / "pip"
    install_cmd = [
        str(pip_path),
        "install",
        "-r",
        str(PYTHON_DIR / "dev_requirements.txt"),
    ]
    if no_cache:
        install_cmd.insert(2, "--no-cache-dir")

    print(f"[INFO] Installing dev requirements using: {' '.join(install_cmd)}")
    run_command(install_cmd, label="pip install requirements")

    install_benchmark_cmd = [
        str(pip_path),
        "install",
        "-r",
        str(GLIDE_ROOT / "benchmarks" / "python" / "requirements.txt"),
    ]
    if no_cache:
        install_benchmark_cmd.insert(2, "--no-cache-dir")

    print(
        f"[INFO] Installing benchmark requirements using: {' '.join(install_benchmark_cmd)}"
    )
    run_command(install_benchmark_cmd, label="pip install benchmark requirements")

    print("[OK] Python environment ready")


def get_venv_env() -> Dict[str, str]:
    env = os.environ.copy()
    env["VIRTUAL_ENV"] = str(venv_ctx["venv_dir"])
    env_path = env.get("PATH", "")
    env["PATH"] = f"{venv_ctx['venv_bin_dir']}{os.pathsep}{env_path}"
    return env


def generate_protobuf_files() -> None:
    proto_src = GLIDE_ROOT / PROTO_REL_PATH
    proto_dst = GLIDE_ROOT / GLIDE_SHARED_DIR / "glide_shared"
    proto_files = list(proto_src.glob("*.proto"))

    if not proto_files:
        print(f"[WARN] No Protobuf files found in {proto_src}")
        return

    print(f"[INFO] Generating Python and .pyi files from Protobuf in: {proto_src}")

    if venv_ctx["venv_bin_dir"] is None:
        print("❌ Error: virtual environment isn't set")
        sys.exit(1)
    mypy_plugin_path = venv_ctx["venv_bin_dir"] / "protoc-gen-mypy"
    if not mypy_plugin_path.exists():
        print("❌ Error: protoc-gen-mypy not found in venv.")
        print(
            f"Hint: Try 'pip install --requirement {PYTHON_DIR}/dev_requirements.txt'"
        )
        sys.exit(1)

    run_command(
        [
            "protoc",
            f"--plugin=protoc-gen-mypy={mypy_plugin_path}",
            f"-Iprotobuf={proto_src}",
            f"--python_out={proto_dst}",
            f"--mypy_out={proto_dst}",
            *map(str, proto_files),
        ],
        label="protoc generation",
        env=get_venv_env(),
    )

    print(f"[OK] Protobuf files (.py + .pyi) generated at: {proto_dst}/protobuf")


def copy_readme_to_package(package_dir: Path) -> None:
    """Copy README.md from python/ to the package directory"""
    source = PYTHON_DIR / "README.md"
    dest = package_dir / "README.md"

    if not source.exists():
        print(f"[WARN] README.md not found at {source}")
        return

    print(f"[INFO] Copying README.md: {source} → {dest}")
    copy2(source, dest)


def install_glide_shared(env: Dict[str, str]) -> None:
    shared_dir = PYTHON_DIR / "glide-shared"
    run_command(
        [str(venv_ctx["python_exe"]), "-m", "pip", "install", "."],
        cwd=shared_dir,
        env=env,
        label="install glide-shared",
    )


def build_async_client_wheel(
    env: Dict[str, str],
    release: bool,
    features: Optional[str] = "",
) -> None:
    # 1. Copy shared module
    dest_shared = GLIDE_ASYNC_DIR / "python" / "glide_shared"
    if dest_shared.exists():
        rmtree(dest_shared)
    dest_shared.parent.mkdir(parents=True, exist_ok=True)
    origin_shared = GLIDE_SHARED_DIR / "glide_shared"
    print(f"[INFO] Copying glide_shared from: {origin_shared} to: {dest_shared}")
    copytree(origin_shared, dest_shared)

    # 2. Build wheel using maturin
    maturin_cmd = ["maturin", "build"]
    if release:
        maturin_cmd += ["--release", "--strip"]
    if features:
        feature_list = [f.strip() for f in features.split(",") if f.strip()]
        maturin_cmd += ["--features", ",".join(feature_list)]

    run_command(
        maturin_cmd,
        cwd=GLIDE_ASYNC_DIR,
        env=env,
        label="maturin build async wheel",
    )

    # 3. Find and install wheel
    wheel_dir = GLIDE_ASYNC_DIR / "target" / "wheels"
    wheel_files = list(wheel_dir.glob("*.whl"))
    if not wheel_files:
        raise FileNotFoundError(f"No wheel found in {wheel_dir}")

    wheel_path = wheel_files[0]
    print(f"[INFO] Installing async client wheel: {wheel_path}")
    run_command(
        [
            str(venv_ctx["python_exe"]),
            "-m",
            "pip",
            "install",
            "--force-reinstall",
            str(wheel_path),
        ],
        env=env,
        label="install async client wheel",
    )


def build_async_client(
    glide_version: str,
    release: bool,
    no_cache: bool = False,
    wheel: bool = False,
    features: Optional[str] = None,
) -> None:
    print(
        f"[INFO] Building async client with version={glide_version} in {'release' if release else 'debug'} mode..."
    )

    # copying README.md is needed for it to be included in the sdist
    copy_readme_to_package(GLIDE_ASYNC_DIR)
    env = activate_venv(no_cache)
    env.update(
        {  # Update it with your GLIDE variables
            "GLIDE_NAME": GLIDE_ASYNC_NAME,
            "GLIDE_VERSION": glide_version,
        }
    )
    install_glide_shared(env)
    generate_protobuf_files()

    if wheel:
        return build_async_client_wheel(env, release, features)

    cmd = [str(venv_ctx["python_exe"]), "-m", "maturin", "develop"]
    if release:
        cmd += ["--release", "--strip"]
    if features:
        feature_list = [f.strip() for f in features.split(",") if f.strip()]
        cmd += ["--features", ",".join(feature_list)]

    run_command(
        cmd,
        cwd=GLIDE_ASYNC_DIR,
        env=env,
        label="maturin develop",
    )
    print("[OK] Async client build completed")


def build_sync_client_wheel(env: Dict[str, str]) -> None:
    print("[INFO] Building sync client wheel with `python -m build`")

    # copying README.md is needed for it to be included in the sdist
    copy_readme_to_package(GLIDE_SYNC_DIR)
    run_command(
        [str(venv_ctx["python_exe"]), "-m", "build"],
        cwd=GLIDE_SYNC_DIR,
        env=env,
        label="build sync wheel",
    )

    wheel_files = list((GLIDE_SYNC_DIR / "dist").glob("*.whl"))
    if not wheel_files:
        raise FileNotFoundError("No wheel found in 'dist/' after building sync client.")

    wheel_path = wheel_files[0]
    print(f"[INFO] Installing sync client wheel: {wheel_path}")
    run_command(
        [
            str(venv_ctx["python_exe"]),
            "-m",
            "pip",
            "install",
            "--force-reinstall",
            str(wheel_path),
        ],
        env=env,
        label="install sync wheel",
    )


def build_sync_client(
    glide_version: str, release: bool, no_cache: bool, wheel: bool = False
) -> None:
    print(
        f"[INFO] Building sync client with version={glide_version} in {'release' if release else 'debug'} mode..."
    )
    generate_protobuf_files()
    env = activate_venv(no_cache)
    env = {
        "GLIDE_NAME": GLIDE_SYNC_NAME,
        "GLIDE_VERSION": glide_version,
        **os.environ,
    }
    if release:
        env["RELEASE_MODE"] = "1"

    # Optionally clean build artifacts
    if no_cache:
        run_command(
            [str(venv_ctx["python_exe"]), "setup.py", "clean"],
            cwd=GLIDE_SYNC_DIR,
            label="Clean all build artifacts",
            env=env,
        )

    if wheel:
        return build_sync_client_wheel(env)

    install_glide_shared(env)
    env.update(
        {  # Update it with your GLIDE variables
            "GLIDE_NAME": GLIDE_SYNC_NAME,
            "GLIDE_VERSION": glide_version,
        }
    )
    # Build the FFI library
    build_args = ["cargo", "build"]
    if release:
        build_args += ["--release"]

    run_command(
        build_args,
        cwd=FFI_DIR,
        label="Build the FFI rust library",
        env=env,
    )

    # Locate the FFI library output file
    libglide_ffi_path = (
        find_libglide_ffi(FFI_OUTPUT_DIR_RELEASE)
        if release
        else find_libglide_ffi(FFI_OUTPUT_DIR_DEBUG)
    )
    if not libglide_ffi_path.exists():
        raise FileNotFoundError(
            f"Expected shared object not found at {libglide_ffi_path}"
        )

    # Copy to glide_sync package dir
    dest_path = GLIDE_SYNC_DIR / "glide_sync" / FFI_TARGET_LIB_NAME
    print(f"[INFO] Copying: {libglide_ffi_path} to: {dest_path}")
    copy2(libglide_ffi_path, dest_path)

    print(f"[INFO] Installing glide-sync: {GLIDE_SYNC_DIR}")
    run_command(
        [str(venv_ctx["python_exe"]), "-m", "pip", "install", "."],
        cwd=GLIDE_SYNC_DIR,
        env=env,
        label="install glide-sync",
    )
    print("[OK] Sync client build completed")


def run_command(
    cmd: List[str],
    cwd: Optional[Path] = None,
    env: Optional[dict] = None,
    label: Optional[str] = None,
) -> None:
    label = label or cmd[0]
    print(f"[INFO] Running {label}...")

    try:
        subprocess.run(
            cmd,
            cwd=cwd,
            check=True,
            env=env,
            stderr=subprocess.PIPE,  # Only capture stderr
            text=True,
        )
        print(f"[OK] {label} completed successfully.")
    except subprocess.CalledProcessError as e:
        print(f"❌ Error while running {label}: ")
        print(f"Command: {' '.join(e.cmd)}")
        print(f"Exit code: {e.returncode}")
        if e.stderr:
            print("--- STDERR ---")
            print(e.stderr.strip())
        sys.exit(e.returncode)


def activate_venv(no_cache: bool = False) -> Dict[Any, Any]:
    prepare_python_env(no_cache)
    env = os.environ.copy()
    env["VIRTUAL_ENV"] = str(venv_ctx["venv_dir"])
    env_path = env["PATH"]
    env["PATH"] = f"{venv_ctx['venv_bin_dir']}:{env_path}"  # noqa: E231
    return env


def run_linters(check_only: bool = False) -> None:
    print("[INFO] Running Python linters...")
    env = activate_venv()
    generate_protobuf_files()

    isort_args = ["isort", ".", "--profile black"]
    black_args = ["black", "."]

    if check_only:
        isort_args.extend(["--check", "--diff"])
        black_args.extend(["--check", "--diff"])

    run_command(isort_args, cwd=PYTHON_DIR, label="isort", env=env)
    run_command(black_args, cwd=PYTHON_DIR, label="black", env=env)
    run_command(["flake8", "."], cwd=PYTHON_DIR, label="flake8", env=env)
    run_command(["mypy", ".."], cwd=PYTHON_DIR, label="mypy", env=env)

    print("[OK] All linters completed successfully.")


def run_tests(
    extra_args: Optional[List[str]] = None, mock_pubsub: bool = False
) -> None:
    print("[INFO] Running test suite...")
    env = get_venv_env()

    cmd = ["pytest", "-v"]
    if mock_pubsub:
        cmd.append("--mock-pubsub")
    if extra_args:
        cmd += extra_args

    run_command(cmd, cwd=PYTHON_DIR, label="pytest", env=env)
    print("[OK] Tests completed successfully")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Dev utility for building, testing, and linting Glide components.",
        epilog="""
Examples:
    python dev.py build                                   # Build the async client in debug mode
    python dev.py build --client async --mode release     # Build the async client in release mode
    python dev.py build --client sync                     # Build the sync client
    python dev.py protobuf                                # Generate Python protobuf files (.py and .pyi)
    python dev.py lint                                    # Run Python linters
    python dev.py test                                    # Run all tests
        """,
        formatter_class=argparse.RawTextHelpFormatter,
    )

    # Parse venv path globally before subparsers
    parser.add_argument(
        "--venv",
        type=str,
        default=None,
        help="Optional path to the virtual environment to use (default: python/.env)",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Build the Python clients")
    build_parser.add_argument(
        "--client",
        default="all",
        choices=["async", "sync", "all"],
        help="Which client to build: 'async', 'sync', or 'all' to build both.",
    )
    build_parser.add_argument(
        "--mode", choices=["debug", "release"], default="debug", help="Build mode"
    )
    build_parser.add_argument(
        "--no-cache",
        action="store_true",
        help="Install Python dependencies without cache",
    )
    build_parser.add_argument(
        "--glide-version",
        type=str,
        default="unknown",
        help="Specify the client version that will be used for server identification and displayed in CLIENT INFO output",
    )
    build_parser.add_argument(
        "--wheel",
        action="store_true",
        help="Build the client to wheel and install it from the built wheel.",
    )
    build_parser.add_argument(
        "--features", help="Comma separated list of features for maturin", default=""
    )

    subparsers.add_parser(
        "protobuf", help="Generate Python protobuf files including .pyi stubs"
    )

    lint_parser = subparsers.add_parser("lint", help="Run all Python linters")
    lint_parser.add_argument(
        "--check",
        action="store_true",
        help="Only check code formatting without modifying files",
    )

    test_parser = subparsers.add_parser("test", help="Run all Python tests")
    test_parser.add_argument(
        "--args",
        nargs=argparse.REMAINDER,
        help="Additional arguments to pass to pytest",
    )
    test_parser.add_argument(
        "--mock-pubsub",
        action="store_true",
        help="Indicate running with mock pubsub (skips connection kill tests)",
    )

    args = parser.parse_args()
    check_dependencies()
    set_venv_paths(args.venv)

    if args.command == "protobuf":
        print("📦 Generating protobuf Python files...")
        activate_venv()
        generate_protobuf_files()

    elif args.command == "lint":
        print("🔍 Running linters...")
        run_linters(check_only=args.check)

    elif args.command == "test":
        print("🧪 Running tests...")
        run_tests(args.args, mock_pubsub=args.mock_pubsub)

    elif args.command == "build":
        version = args.glide_version
        release = args.mode == "release"
        no_cache = args.no_cache
        wheel = args.wheel
        features = args.features
        if args.client in ["async", "all"]:
            print(f"🛠 Building async client ({args.mode} mode)...")
            build_async_client(version, release, no_cache, wheel, features)
        if args.client in ["sync", "all"]:
            print("🛠 Building sync client ({args.mode} mode)...")
            build_sync_client(version, release, no_cache, wheel)

    print("[✅ DONE] Task completed successfully.")


if __name__ == "__main__":
    main()
