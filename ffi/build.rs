//! Records what decides how `Value` is laid out beyond this source tree: the resolved
//! versions of the dependencies whose private layout it embeds, and the compiler that laid
//! it out. `value_abi_token` folds both in, so a library built against another `bytes`,
//! `num-bigint`, or rustc reports a different token and the client stays on the arena path.
//!
//! Keep the extraction here in step with `python/glide-shared/build.rs`: the two must read
//! the same crate names from their own lockfile for the tokens to be comparable.

use std::path::Path;
use std::process::Command;

/// Crates whose private layout `Value` embeds. A version bump here can move the layout
/// without changing `Value`'s size or alignment: `BulkString` holds a `bytes::Bytes`, whose
/// four words stay four words while the vtable they point at changes.
const LAYOUT_CRATES: [&str; 2] = ["bytes", "num-bigint"];

fn main() {
    println!("cargo:rerun-if-changed=Cargo.lock");

    let lockfile = Path::new(&std::env::var("CARGO_MANIFEST_DIR").unwrap()).join("Cargo.lock");
    let lock = std::fs::read_to_string(&lockfile).unwrap_or_default();
    let versions = LAYOUT_CRATES
        .iter()
        .map(|name| format!("{name}={}", version_of(&lock, name).unwrap_or("unknown")))
        .collect::<Vec<_>>()
        .join(";");
    println!("cargo:rustc-env=VALUE_ABI_DEPS={versions}");

    let rustc = std::env::var("RUSTC").unwrap_or_else(|_| "rustc".to_string());
    let rustc_version = Command::new(rustc)
        .arg("--version")
        .output()
        .ok()
        .filter(|output| output.status.success())
        .map(|output| String::from_utf8_lossy(&output.stdout).trim().to_string())
        .unwrap_or_else(|| "unknown".to_string());
    println!("cargo:rustc-env=VALUE_ABI_RUSTC={rustc_version}");
}

/// The resolved version of `name` in a `Cargo.lock`.
fn version_of<'a>(lock: &'a str, name: &str) -> Option<&'a str> {
    let wanted = format!("name = \"{name}\"");
    let mut lines = lock.lines();
    while let Some(line) = lines.next() {
        if line.trim() != wanted {
            continue;
        }
        let version = lines.next()?.trim();
        return version
            .strip_prefix("version = \"")
            .and_then(|version| version.strip_suffix('"'));
    }
    None
}
