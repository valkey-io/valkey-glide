#!/usr/bin/env python3
# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import argparse
import os
import subprocess
import sys
from pathlib import Path
from shutil import which
from typing import Any, Dict, List, Optional


def find_project_root() -> Path:
    root = Path(__file__).resolve().parent.parent
    print(f"[INFO] Project root determined at: {root}")
    return root


# Constants
PROTO_REL_PATH = "glide-core/src/protobuf"
PYTHON_CLIENT_PATH = "python/python/glide"
VENV_NAME = ".env"
GLIDE_ROOT = find_project_root()
PYTHON_DIR = GLIDE_ROOT / "python"
VENV_DIR = PYTHON_DIR / VENV_NAME
VENV_BIN_DIR = VENV_DIR / "bin"
PYTHON_EXE = VENV_BIN_DIR / "python"


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
    if not VENV_DIR.exists():
        print("[INFO] Creating new Python virtual environment...")
        run_command(["python3", "-m", "venv", str(VENV_DIR)], label="venv creation")
    else:
        print("[INFO] Using existing Python virtual environment")

    pip_path = VENV_BIN_DIR / "pip"
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
    env["VIRTUAL_ENV"] = str(VENV_DIR)
    env_path = env.get("PATH", "")
    env["PATH"] = f"{VENV_BIN_DIR}{os.pathsep}{env_path}"
    return env


def generate_protobuf_files() -> None:
    proto_src = GLIDE_ROOT / PROTO_REL_PATH
    proto_dst = GLIDE_ROOT / PYTHON_CLIENT_PATH
    proto_files = list(proto_src.glob("*.proto"))

    if not proto_files:
        print(f"[WARN] No Protobuf files found in {proto_src}")
        return

    print(f"[INFO] Generating Python and .pyi files from Protobuf in: {proto_src}")

    mypy_plugin_path = VENV_BIN_DIR / "protoc-gen-mypy"
    if not mypy_plugin_path.exists():
        print("❌ Error: protoc-gen-mypy not found in venv.")
        print(
            "Hint: Try `pip install --requirement python/dev_requirements.txt` again."
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

    print(f"[OK] Protobuf files (.py + .pyi) generated at: {proto_dst}")


def build_async_client(release: bool, no_cache: bool = False) -> None:
    print(
        f"[INFO] Building async client in {'release' if release else 'debug'} mode..."
    )
    env = activate_venv(no_cache)
    generate_protobuf_files()

    cmd = [str(PYTHON_EXE), "-m", "maturin", "develop"]
    if release:
        cmd += ["--release", "--strip"]

    run_command(cmd, cwd=PYTHON_DIR, env=env, label="maturin develop")
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
    env["VIRTUAL_ENV"] = str(VENV_BIN_DIR.parent)
    env_path = env["PATH"]
    env["PATH"] = f"{VENV_BIN_DIR}:{env_path}"  # noqa: E231
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


def run_tests(extra_args: Optional[List[str]] = None) -> None:
    print("[INFO] Running test suite...")
    env = get_venv_env()

    cmd = ["pytest", "-v"]
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
    python dev.py protobuf                                # Generate Python protobuf files (.py and .pyi)
    python dev.py lint                                    # Run Python linters
    python dev.py test                                    # Run all tests
        """,
        formatter_class=argparse.RawTextHelpFormatter,
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Build the Python clients")
    build_parser.add_argument(
        "--client", default="async", choices=["async"], help="Which client to build"
    )
    build_parser.add_argument(
        "--mode", choices=["debug", "release"], default="debug", help="Build mode"
    )
    build_parser.add_argument(
        "--no-cache",
        action="store_true",
        help="Install Python dependencies without cache",
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

    args = parser.parse_args()
    check_dependencies()

    if args.command == "protobuf":
        print("📦 Generating protobuf Python files...")
        activate_venv()
        generate_protobuf_files()

    elif args.command == "lint":
        print("🔍 Running linters...")
        run_linters(check_only=args.check)

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
