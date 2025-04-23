#!/usr/bin/env python3
# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import argparse
import os
import subprocess
import sys
from pathlib import Path
from shutil import which
from typing import Any, Dict, List, Optional

# Constants
PROTO_REL_PATH = "glide-core/src/protobuf"
PYTHON_CLIENT_PATH = "python/python/glide"


def find_project_root() -> Path:
    root = Path(__file__).resolve().parent.parent
    print(f"[INFO] Project root determined at: {root}")
    return root


GLIDE_ROOT = find_project_root()


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


def prepare_python_env(no_cache: bool = False) -> Path:
    python_dir = GLIDE_ROOT / "python"
    venv_dir = python_dir / ".env"

    if not venv_dir.exists():
        print("[INFO] Creating new Python virtual environment...")
        run_command(["python3", "-m", "venv", str(venv_dir)], label="venv creation")
    else:
        print("[INFO] Using existing Python virtual environment")

    pip_path = venv_dir / "bin" / "pip"
    install_cmd = [
        str(pip_path),
        "install",
        "-r",
        str(python_dir / "dev_requirements.txt"),
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
        install_cmd.insert(2, "--no-cache-dir")

    print(
        f"[INFO] Installing benchmark requirements using: {' '.join(install_benchmark_cmd)}"
    )
    run_command(install_benchmark_cmd, label="pip install benchmark requirements")

    print("[OK] Python environment ready")

    return venv_dir / "bin" / "python"


def generate_protobuf_files() -> None:
    proto_src = GLIDE_ROOT / PROTO_REL_PATH
    proto_dst = GLIDE_ROOT / PYTHON_CLIENT_PATH
    proto_files = list(proto_src.glob("*.proto"))

    if not proto_files:
        print(f"[WARN] No Protobuf files found in {proto_src}")
        return

    print(f"[INFO] Generating Python and .pyi files from Protobuf in: {proto_src}")

    # Locate the venv bin dir
    venv_dir = GLIDE_ROOT / "python" / ".env"
    venv_bin = venv_dir / "bin"
    mypy_plugin_path = venv_bin / "protoc-gen-mypy"

    if not mypy_plugin_path.exists():
        print("❌ Error: protoc-gen-mypy not found in venv.")
        print(
            "Hint: Try `pip install --requirement python/dev_requirements.txt` again."
        )
        sys.exit(1)

    env = os.environ.copy()
    env_path = env.get("PATH", "")
    if str(venv_bin) not in env_path.split(os.pathsep):
        env["PATH"] = f"{venv_bin}{os.pathsep}{env_path}"

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
        env=env,
    )

    print(f"[OK] Protobuf files (.py + .pyi) generated at: {proto_dst}")


def build_async_client(release: bool, no_cache: bool = False) -> None:
    print(
        f"[INFO] Building async client in {'release' if release else 'debug'} mode..."
    )
    python_exe = prepare_python_env(no_cache)
    generate_protobuf_files()
    env = activate_venv()

    cmd = [str(python_exe), "-m", "maturin", "develop"]
    if release:
        cmd += ["--release", "--strip"]

    run_command(cmd, cwd=GLIDE_ROOT / "python", env=env, label="maturin develop")
    print("[OK] Async client build completed")


def run_command(
    cmd: List[str],
    cwd: Optional[Path] = None,
    env: Optional[dict] = None,
    label: Optional[str] = None,
) -> None:
    label = label or cmd[0]
    print(f"[INFO] Running {label}...")
    try:
        result = subprocess.run(
            cmd,
            cwd=cwd,
            check=True,
            env=env,
            capture_output=True,
            text=True,
        )
        print(f"[OK] {label} completed successfully.")
        if result.stdout:
            print(result.stdout.strip())
    except subprocess.CalledProcessError as e:
        print(f"❌ Error while running {label}: ")
        print(f"Command: {' '.join(e.cmd)}")
        print(f"Exit code: {e.returncode}")
        if e.stdout:
            print("--- STDOUT ---")
            print(e.stdout.strip())
        if e.stderr:
            print("--- STDERR ---")
            print(e.stderr.strip())
        sys.exit(e.returncode)


def activate_venv() -> Dict[Any, Any]:
    python_exe = prepare_python_env()
    venv_bin = python_exe.parent
    env = os.environ.copy()
    env["VIRTUAL_ENV"] = str(venv_bin.parent)
    env_path = env["PATH"]
    env["PATH"] = f"{venv_bin}:{env_path}"  # noqa: E231
    return env


def run_linters() -> None:
    print("[INFO] Running Python linters...")
    env = activate_venv()
    generate_protobuf_files()

    run_command(["isort", "."], cwd=GLIDE_ROOT / "python", label="isort", env=env)
    run_command(["black", "."], cwd=GLIDE_ROOT / "python", label="black", env=env)
    run_command(["flake8", "."], cwd=GLIDE_ROOT / "python", label="flake8", env=env)
    run_command(["mypy", ".."], cwd=GLIDE_ROOT / "python", label="mypy", env=env)

    print("[OK] All linters completed successfully.")


def run_tests(extra_args: Optional[List[str]] = None) -> None:
    print("[INFO] Running test suite...")
    python_exe = prepare_python_env()

    cmd = [str(python_exe.parent / "pytest"), "-v", "--asyncio-mode=auto"]
    if extra_args:
        cmd += extra_args

    run_command(cmd, cwd=GLIDE_ROOT / "python" / "python", label="pytest")

    print("[OK] Tests completed successfully")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Dev utility for building, testing, and linting Glide components.",
        epilog="""
Examples:
    python dev.py build                                   # Build the async client in debug mode
    python dev.py build --client async --mode release     # Build the async client in release mode
    python dev.py protobuf                                # Generate Python protobuf files (.py and .pyi)
    python dev.py lint                                    # Run Python linters
    python dev.py test                                    # Run all tests
        """,
        formatter_class=argparse.RawTextHelpFormatter,
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    # -------------------- Build Command --------------------
    build_parser = subparsers.add_parser("build", help="Build the Python clients")
    build_parser.add_argument(
        "--client",
        default="async",
        choices=["async"],
        # TODO: use these options once the sync client is added:
        # choices=["async", "sync", "all"],
        # default="all",
        help="Which client to build",
    )
    build_parser.add_argument(
        "--mode",
        choices=["debug", "release"],
        default="debug",
        help="Build mode (default: debug)",
    )
    build_parser.add_argument(
        "--no-cache",
        action="store_true",
        help="Install Python dependencies without using pip cache",
    )

    # -------------------- Protobuf Command --------------------
    subparsers.add_parser(
        "protobuf", help="Generate Python protobuf files including .pyi stubs"
    )

    # -------------------- Lint Command --------------------
    subparsers.add_parser("lint", help="Run all Python linters")

    # -------------------- Test Command --------------------
    test_parser = subparsers.add_parser("test", help="Run all Python tests")
    test_parser.add_argument(
        "--args",
        nargs=argparse.REMAINDER,
        help="Additional arguments to pass to pytest",
    )
    args = parser.parse_args()
    check_dependencies()

    if args.command == "protobuf":
        print("📦 Generating protobuf Python files...")
        activate_venv()
        generate_protobuf_files()

    elif args.command == "lint":
        print("🔍 Running linters...")
        run_linters()

    elif args.command == "test":
        print("🧪 Running tests...")
        run_tests(args.args)

    elif args.command == "build":
        release = args.mode == "release"
        no_cache = args.no_cache

        if args.client in ("async"):
            print(f"🛠 Building async client ({args.mode} mode)...")
            build_async_client(release, no_cache)

    print("[✅ DONE] Task completed successfully.")


if __name__ == "__main__":
    main()
