# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
"""Layout guard for CommandResponse's two independent #[repr(C)] copies:
ffi/src/lib.rs (the real struct) and python/glide-shared/src/lib.rs (used
by the async pipe's _c_parse_response/convert() to walk the same struct
from the Python-loaded side). A hand-computed field-offset check during
investigation of a build-staleness bug found both copies byte-identical --
the live bug that investigation surfaced turned out to be a stale build
artifact (dev.py's install_glide_shared skipped rebuilding the FFI cdylib
whenever a prior copy already existed, regardless of source changes), not
a layout mismatch -- but nothing before this test automatically re-checked
that by itself, so a future change to one copy without the other would
only be caught by review, not by a test run.

This cannot be a Rust-level test in either crate: glide-shared's pyo3
dependency uses the "extension-module" feature, which deliberately omits
linking against libpython (it expects the interpreter to provide those
symbols via dlopen at import time) -- a `cargo test` binary needs to link
Python itself, so pulling glide-shared in as a dev-dependency for a
Rust-side comparison test fails at link time (confirmed: `ld: symbol(s)
not found for architecture arm64` on every PyUnicode_*/Py_* symbol pyo3
needs). A plain `cargo build` on glide-shared alone has the identical
failure outside of maturin's special-cased linking, confirming this is a
structural property of the crate, not something introduced by trying to
add the test. A Python-side text comparison avoids the problem entirely.
"""

import re
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.parent

REAL_STRUCT_FILE = REPO_ROOT / "ffi" / "src" / "lib.rs"
SHARED_COPY_FILE = REPO_ROOT / "python" / "glide-shared" / "src" / "lib.rs"

# The real struct's field order, normalized to the C-level type each field
# actually resolves to (ignoring named wrapper types like ResponseType,
# c_char, c_double, c_long, c_void -- those are checked for category, not
# literal spelling, since the two copies spell some of them differently:
# e.g. the real struct uses `ResponseType`/`c_double`/`c_long`, the shared
# copy uses `i32`/`f64`/`i64` directly for the same underlying C ABI type).
EXPECTED_FIELDS = [
    ("response_type", "enum_or_i32"),
    ("int_value", "i64"),
    ("float_value", "f64"),
    ("bool_value", "bool"),
    ("string_value", "ptr_char"),
    ("string_value_len", "i64"),
    ("array_value", "ptr_self"),
    ("array_value_len", "i64"),
    ("map_key", "ptr_self"),
    ("map_value", "ptr_self"),
    ("sets_value", "ptr_self"),
    ("sets_value_len", "i64"),
    ("arena_ptr", "ptr_void"),
]


def _classify_rust_type(type_text: str) -> str:
    type_text = type_text.strip()
    if type_text in ("i64", "c_long", "c_longlong"):
        return "i64"
    if type_text in ("f64", "c_double"):
        return "f64"
    if type_text == "bool":
        return "bool"
    if type_text in ("i32", "ResponseType"):
        return "enum_or_i32"
    if "c_char" in type_text and "*" in type_text:
        return "ptr_char"
    if "c_void" in type_text and "*" in type_text:
        return "ptr_void"
    if "CommandResponse" in type_text and "*" in type_text:
        return "ptr_self"
    return f"unrecognized:{type_text}"


def _extract_fields(source: str) -> list:
    """Extract (field_name, classified_type) pairs from a CommandResponse
    struct definition, skipping padding fields (leading underscore) which
    exist only in the glide-shared copy to make its manual alignment
    explicit -- the real struct relies on the compiler's own repr(C)
    padding insertion instead, so it never names these."""
    match = re.search(r"struct\s+CommandResponse\s*\{(.*?)\n\}", source, re.DOTALL)
    assert match, "Could not find a `struct CommandResponse { ... }` block"
    body = match.group(1)
    fields = []
    for line in body.splitlines():
        line = line.strip().rstrip(",")
        if not line or line.startswith("//") or line.startswith("///"):
            continue
        field_match = re.match(r"(?:pub\s+)?(\w+)\s*:\s*(.+)", line)
        if not field_match:
            continue
        name, type_text = field_match.groups()
        if name.startswith("_"):
            continue
        fields.append((name, _classify_rust_type(type_text)))
    return fields


def test_glide_shared_command_response_copy_matches_real_struct():
    real_fields = _extract_fields(REAL_STRUCT_FILE.read_text())
    shared_fields = _extract_fields(SHARED_COPY_FILE.read_text())

    assert real_fields == EXPECTED_FIELDS, (
        "ffi/src/lib.rs's CommandResponse field list no longer matches "
        "this test's EXPECTED_FIELDS -- update EXPECTED_FIELDS (and check "
        "whether python/glide-shared/src/lib.rs and "
        "python/glide-shared/glide_shared/_glide_ffi.py need the same "
        f"change). Got: {real_fields}"
    )
    assert shared_fields == EXPECTED_FIELDS, (
        "python/glide-shared/src/lib.rs's CommandResponse copy no longer "
        "matches ffi/src/lib.rs's real struct -- these two independent "
        "#[repr(C)] definitions must stay byte-identical in field order "
        f"and type category. Got: {shared_fields}, expected: {EXPECTED_FIELDS}"
    )
