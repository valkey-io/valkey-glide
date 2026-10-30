# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

import sys
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, call

import pytest
import setuptools
from setuptools.command.egg_info import FileList

BUILD_UTILS_PATH = Path(__file__).resolve().parent.parent / "build_utils.py"
BUILD_UTILS_SPEC = spec_from_file_location("glide_sync_build_utils", BUILD_UTILS_PATH)
if BUILD_UTILS_SPEC is None or BUILD_UTILS_SPEC.loader is None:
    raise RuntimeError(f"Unable to load {BUILD_UTILS_PATH}")
BUILD_UTILS = module_from_spec(BUILD_UTILS_SPEC)
BUILD_UTILS_SPEC.loader.exec_module(BUILD_UTILS)
rust_cdylib_filename = BUILD_UTILS.rust_cdylib_filename


@pytest.mark.parametrize(
    ("current_platform", "crate_name", "expected"),
    [
        ("linux", "glide_ffi", "libglide_ffi.so"),
        ("darwin", "glide_ffi", "libglide_ffi.dylib"),
        ("win32", "glide_ffi", "glide_ffi.dll"),
        ("win32", "_fast_response", "_fast_response.dll"),
    ],
)
def test_rust_cdylib_filename(
    current_platform: str, crate_name: str, expected: str
) -> None:
    assert rust_cdylib_filename(crate_name, current_platform) == expected


def test_rust_cdylib_filename_rejects_unknown_platform() -> None:
    with pytest.raises(RuntimeError, match="Unsupported platform: freebsd"):
        rust_cdylib_filename("glide_ffi", "freebsd")


def test_sdist_manifest_includes_build_helper_and_rdma_sources(
    monkeypatch, tmp_path, setup_module
) -> None:
    sources = [
        "build_utils.py",
        str(Path("glide-rdma") / "Cargo.toml"),
        str(Path("glide-rdma") / "src" / "lib.rs"),
        str(Path("glide-rdma") / "glide-libfabric-sys" / "Cargo.toml"),
        str(Path("glide-rdma") / "glide-libfabric-sys" / "build.rs"),
    ]
    for source in sources:
        path = tmp_path / source
        path.parent.mkdir(parents=True, exist_ok=True)
        path.touch()
    monkeypatch.chdir(tmp_path)
    command = setup_module.sdist(setup_module.BinaryDistribution())
    command.template = str(BUILD_UTILS_PATH.with_name("MANIFEST.in"))
    command.filelist = FileList()

    command.read_template()

    assert set(command.filelist.files) == set(sources)


@pytest.fixture
def setup_module(monkeypatch):
    setup_path = BUILD_UTILS_PATH.with_name("setup.py")
    spec = spec_from_file_location("glide_sync_setup", setup_path)
    assert spec is not None and spec.loader is not None
    module = module_from_spec(spec)
    monkeypatch.setitem(sys.modules, spec.name, module)
    monkeypatch.syspath_prepend(str(setup_path.parent))
    monkeypatch.setattr(setuptools, "setup", Mock())
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize("current_platform", ["linux", "darwin", "win32"])
@pytest.mark.parametrize("target_override", [None, "relative", "absolute"])
@pytest.mark.parametrize("release", [False, True])
def test_build_ext_uses_cargo_target_and_platform_filenames(
    monkeypatch, tmp_path, setup_module, current_platform, target_override, release
):
    if target_override is None:
        monkeypatch.delenv("CARGO_TARGET_DIR", raising=False)
        target = Path("target")
    else:
        target = tmp_path / "cache" if target_override == "absolute" else Path("cache")
        monkeypatch.setenv("CARGO_TARGET_DIR", str(target))
    monkeypatch.setenv("RELEASE_MODE", "1" if release else "0")
    ffi_path = tmp_path / "ffi"
    shared_path = tmp_path / "glide-shared"
    shared_path.mkdir()
    monkeypatch.setitem(
        setup_module.VENDORED_DEPENDENCIES,
        "ffi",
        setup_module.VendorFolder(source=ffi_path, dist=ffi_path),
    )
    monkeypatch.setattr(setup_module, "GLIDE_SHARED_RS_DIST", shared_path)
    monkeypatch.setattr(
        setup_module,
        "sys",
        SimpleNamespace(platform=current_platform, executable=sys.executable),
    )
    monkeypatch.setattr(setup_module.subprocess, "run", Mock())
    copy = Mock()
    monkeypatch.setattr(setup_module.shutil, "copy2", copy)
    monkeypatch.setattr(
        setup_module.sysconfig, "get_config_var", Mock(return_value=".ext")
    )
    monkeypatch.setattr(setup_module.build_ext_orig, "run", Mock())
    monkeypatch.setattr(setup_module, "cleanup_vendored", Mock())
    command = setup_module.build_ext(setup_module.BinaryDistribution())
    build_lib = tmp_path / "build"
    command.build_lib = str(build_lib)

    command.run()

    profile = "release" if release else "debug"
    ffi_name = rust_cdylib_filename("glide_ffi", current_platform)
    shared_name = rust_cdylib_filename("_fast_response", current_platform)
    assert copy.call_args_list == [
        call(
            ffi_path / target / profile / ffi_name, build_lib / "glide_sync" / ffi_name
        ),
        call(
            shared_path / target / profile / shared_name,
            build_lib / "glide_shared" / "_fast_response.ext",
        ),
    ]
