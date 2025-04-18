import argparse
import os
import subprocess
import sys
from pathlib import Path
from shutil import which
from typing import List, Optional

# Constants
PROTO_REL_PATH = "glide-core/src/protobuf"
PYTHON_CLIENT_PATH = "python/python/glide"


def find_project_root() -> Path:
    root = Path(__file__).resolve().parent.parent
    print(f"[INFO] Project root determined at: {root}")
    return root


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


def prepare_python_env(glide_root: Path, no_cache: bool = False) -> Path:
    python_dir = glide_root / "python"
    venv_dir = python_dir / ".env"

    if not venv_dir.exists():
        print("[INFO] Creating new Python virtual environment...")
        subprocess.run(["python3", "-m", "venv", str(venv_dir)], check=True)
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
    subprocess.run(install_cmd, check=True)
    print("[OK] Python environment ready")

    return venv_dir / "bin" / "python"


def generate_protobuf_files(glide_root: Path) -> None:
    proto_src = glide_root / PROTO_REL_PATH
    proto_dst = glide_root / PYTHON_CLIENT_PATH
    proto_files = list(proto_src.glob("*.proto"))

    if not proto_files:
        print(f"[WARN] No Protobuf files found in {proto_src}")
        return

    print(f"[INFO] Generating Python and .pyi files from Protobuf in: {proto_src}")

    mypy_plugin = which("protoc-gen-mypy")
    if not mypy_plugin:
        print("❌ Error: protoc-gen-mypy is not available in PATH.")
        print(
            "Hint: Make sure you have run 'pip install -r dev_requirements.txt' first."
        )
        sys.exit(1)

    subprocess.run(
        [
            "protoc",
            f"--plugin=protoc-gen-mypy={mypy_plugin}",
            f"-Iprotobuf={proto_src}",
            f"--python_out={proto_dst}",
            f"--mypy_out={proto_dst}",
            *map(str, proto_files),
        ],
        check=True,
    )

    print(f"[OK] Protobuf files (.py + .pyi) generated at: {proto_dst}")


def build_async_client(glide_root: Path, release: bool, no_cache: bool = False) -> None:
    print(
        f"[INFO] Building async client in {'release' if release else 'debug'} mode..."
    )
    python_exe = prepare_python_env(glide_root, no_cache)
    generate_protobuf_files(glide_root)

    venv_bin = python_exe.parent
    venv_root = venv_bin.parent

    cmd = [str(python_exe), "-m", "maturin", "develop"]
    if release:
        cmd += ["--release", "--strip"]

    env: dict[str, str] = os.environ.copy()
    env["VIRTUAL_ENV"] = str(venv_root)
    env["PATH"] = f"{venv_bin}:{env['PATH']}"

    subprocess.run(cmd, cwd=glide_root / "python", check=True, env=env)
    print("[OK] Async client build completed")


def run_linters(glide_root: Path) -> None:
    print("[INFO] Running Python linters...")
    python_exe = prepare_python_env(glide_root)
    venv_bin = python_exe.parent

    env = os.environ.copy()
    env["VIRTUAL_ENV"] = str(venv_bin.parent)
    env["PATH"] = f"{venv_bin}:{env['PATH']}"

    print("[INFO] Running isort...")
    subprocess.run(
        [
            "isort",
            ".",
        ],
        cwd=glide_root / "python",
        check=True,
        env=env,
    )

    print("[INFO] Running black...")
    subprocess.run(
        ["black", "."],
        cwd=glide_root / "python",
        check=True,
        env=env,
    )

    print("[INFO] Running flake8...")
    subprocess.run(
        ["flake8", "."],
        cwd=glide_root / "python",
        check=True,
        env=env,
    )

    print("[INFO] Running mypy...")
    subprocess.run(
        ["mypy", ".."],
        cwd=glide_root / "python",
        check=True,
        env=env,
    )

    print("[OK] Linters completed successfully")


def run_tests(glide_root: Path, extra_args: Optional[List[str]] = None) -> None:
    print("[INFO] Running test suite...")
    python_exe = prepare_python_env(glide_root)

    cmd = [str(python_exe.parent / "pytest"), "-v", "--asyncio-mode=auto"]
    if extra_args:
        cmd += extra_args

    subprocess.run(cmd, cwd=glide_root / "python" / "python", check=True)

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
    glide_root = find_project_root()
    check_dependencies()

    if args.command == "protobuf":
        print("📦 Generating protobuf Python files...")
        generate_protobuf_files(glide_root)

    elif args.command == "lint":
        print("🔍 Running linters...")
        run_linters(glide_root)

    elif args.command == "test":
        print("🧪 Running tests...")
        run_tests(glide_root, args.args)

    elif args.command == "build":
        release = args.mode == "release"
        no_cache = args.no_cache

        if args.client in ("async"):
            print(f"🛠 Building async client ({args.mode} mode)...")
            build_async_client(glide_root, release, no_cache)

    print("[✅ DONE] Task completed successfully.")


if __name__ == "__main__":
    main()
