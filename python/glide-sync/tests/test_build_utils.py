# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path

import pytest

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
