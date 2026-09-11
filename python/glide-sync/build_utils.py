# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0


def rust_cdylib_filename(crate_name: str, current_platform: str) -> str:
    """Return Cargo's cdylib output filename for the target platform."""
    platform_parts = {
        "linux": ("lib", ".so"),
        "darwin": ("lib", ".dylib"),
        "win32": ("", ".dll"),
    }
    try:
        prefix, suffix = platform_parts[current_platform]
    except KeyError as error:
        raise RuntimeError(f"Unsupported platform: {current_platform}") from error
    return f"{prefix}{crate_name}{suffix}"
