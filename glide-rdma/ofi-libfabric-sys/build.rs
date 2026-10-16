#[cfg(feature = "regenerate-bindings")]
use bindgen::callbacks::{ItemInfo, ItemKind, ParseCallbacks};
use std::env;
use std::path::PathBuf;
use std::sync::OnceLock;

// Global variables for common directories.
static CARGO_MANIFEST_DIR: OnceLock<PathBuf> = OnceLock::new();

/// DIVERGENCE FROM UPSTREAM: libfabric's exported functions, which glide-rdma
/// defines itself under a `glide_` prefix, forwarding to the libfabric it loads at
/// run time.
///
/// The Rust bindings keep libfabric's names but link to the prefixed symbols and
/// `wrapper.c` renames its calls the same way. Without the prefix, a library built
/// from this crate would export its own `fi_getinfo` and friends, and a process
/// that also loads the real libfabric could bind calls to the wrong copy. The
/// list must match the one in `wrapper.c`, which a test in glide-rdma checks.
#[cfg_attr(not(feature = "regenerate-bindings"), allow(dead_code))]
const SHIMMED: [&str; 8] = [
    "fi_getinfo",
    "fi_freeinfo",
    "fi_dupinfo",
    "fi_fabric",
    "fi_strerror",
    "fi_version",
    "fi_open",
    "fi_param_get",
];

/// Prefixes of link names that `stage_bindings` adjusts for the target: the
/// static-inline wrappers, and the renamed exports in [`SHIMMED`].
const LINK_NAME_PREFIXES: [&str; 2] = ["wrap_", "glide_"];

fn get_cargo_manifest_dir() -> &'static PathBuf {
    CARGO_MANIFEST_DIR.get_or_init(|| PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap()))
}

#[cfg(feature = "regenerate-bindings")]
#[derive(Debug)]
struct RenameFunctions;

// Rename function callback, such that those static inline functions are replaced without the "wrap_" prefix.
// This way, the library is able to export such functions under `fi_xyz()`, rather than `wrap_fi_xyz()`.
#[cfg(feature = "regenerate-bindings")]
impl ParseCallbacks for RenameFunctions {
    // This is to remove the prefix, from `wrap_fi_send()` --> `fi_send()` for Rust function.
    fn item_name(&self, original_name: ItemInfo<'_>) -> Option<String> {
        original_name.name.strip_prefix("wrap_").map(String::from)
    }

    // Explicitly use link_name for the static-inline wrappers, marked with the "wrap_"
    // prefix, and for the renamed exports in SHIMMED.
    fn generated_link_name_override(&self, item_info: ItemInfo<'_>) -> Option<String> {
        match item_info.kind {
            ItemKind::Function => {
                let symbol = if item_info.name.starts_with("wrap_") {
                    item_info.name.to_string()
                } else if SHIMMED.contains(&item_info.name) {
                    format!("glide_{}", item_info.name)
                } else {
                    return None;
                };
                // On macOS, C symbols carry a leading underscore in the object
                // file, and these link names bypass Rust's own mangling, so the
                // exact symbol has to be spelled out here.
                //
                // DIVERGENCE FROM UPSTREAM: produces symbol names for
                // the target OS, not the host.
                Some(if target_is_macos() {
                    format!("_{symbol}")
                } else {
                    symbol
                })
            }
            _ => None,
        }
    }
}

/// Whether the build is producing code for macOS.
///
/// Not `cfg!(target_os = "macos")`: inside a build script that describes the
/// machine running the build, which is a different question and the wrong one.
fn target_is_macos() -> bool {
    env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("macos")
}

/// Copies the checked-in bindings into `OUT_DIR`, fixing the link names for the
/// target as it goes.
///
/// The link names of the static-inline wrappers and of the renamed exports are
/// spelled out verbatim in the generated file, and macOS wants a leading
/// underscore where Linux does not. A file generated on one therefore does not
/// link on the other — which matters because the file is generated once, by
/// whoever last changed the headers, on whatever machine they happened to use.
/// Normalising here makes the checked-in file work for every target regardless of
/// where it came from.
fn stage_bindings(source: &std::path::Path, destination: &std::path::Path) {
    let text = std::fs::read_to_string(source)
        .unwrap_or_else(|error| panic!("could not read {}: {error}", source.display()));

    let mut text = text;
    for prefix in LINK_NAME_PREFIXES {
        let bare = format!("\"\\u{{1}}{prefix}");
        let underscored = format!("\"\\u{{1}}_{prefix}");
        // Strip any leading underscore first, so the starting point is the same
        // whichever platform generated the file.
        text = text.replace(&underscored, &bare);
        if target_is_macos() {
            text = text.replace(&bare, &underscored);
        }
    }

    std::fs::write(destination, text)
        .unwrap_or_else(|error| panic!("could not write {}: {error}", destination.display()));
}

fn main() {
    // Checked at run time, for the same reason as `target_is_macos`.
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    assert!(
        matches!(target_os.as_str(), "linux" | "macos"),
        "This binding is only compatible with Linux and macOS (target_os = {target_os})."
    );

    let manifest_dir = get_cargo_manifest_dir().clone();

    println!("cargo:rerun-if-changed=wrapper.c");
    println!("cargo:rerun-if-changed=wrapper.h");
    println!("cargo:rerun-if-changed=src/bindings.rs");
    println!("cargo:rerun-if-changed=include");

    // DIVERGENCE FROM UPSTREAM: where the headers come from.
    //
    // Upstream asks pkg-config for an installed libfabric and builds against
    // what it finds. Here, the public headers are vendored under `include/`.
    let vendored = manifest_dir.join("include");
    assert!(
        vendored.join("rdma").join("fabric.h").exists(),
        "vendored headers are missing from {}",
        vendored.display()
    );
    let include_paths = [
        vendored.clone(),
        vendored.join("rdma"),
        vendored.join("rdma").join("providers"),
    ];

    // DIVERGENCE FROM UPSTREAM: libfabric is never linked.
    //
    // Upstream emits `cargo:rustc-link-lib=fabric`, which puts a DT_NEEDED
    // (Linux) or LC_LOAD_DYLIB (macOS) entry for libfabric on every binary built
    // from these bindings. The OS loader resolves that entry while opening the
    // file, so a host without libfabric cannot even start the process, whether
    // or not it ever intends to use a fabric.
    //
    // Instead, the handful of genuinely dynamic libfabric symbols are resolved
    // at run time by the dlopen shim in glide-rdma.

    // Compiles the wrapper.[ch].
    //
    // This generates a libwrapper.a, which is statically linked against your Rust application code.
    //
    // The goal of the wrapper.[ch] is to create translation unit for "static inline" functions, such that they can be properly FFI'ed.
    // TODO: https://github.com/rust-lang/rust-bindgen/discussions/2405
    let mut builder = cc::Build::new();
    builder.file(format!("{}/wrapper.c", manifest_dir.display()));
    for path in &include_paths {
        builder.include(format!("{}", path.display()));
    }
    builder.compile("wrapper");

    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    let checked_in = manifest_dir.join("src").join("bindings.rs");

    // DIVERGENCE FROM UPSTREAM: the bindings are generated once and kept.
    //
    // Running bindgen here would require libclang on every machine that builds
    // this crate, including every release builder. The generated file is checked
    // in instead. Regenerate it with `--features regenerate-bindings` after
    // changing the vendored headers; see README.md.
    #[cfg(feature = "regenerate-bindings")]
    {
        let builder = bindgen::Builder::default().header("wrapper.h").clang_args(
            include_paths
                .iter()
                .map(|dir| format!("-I{}", dir.display())),
        );
        let bindings = builder
            .clang_arg("-fno-inline-functions")
            .clang_arg("-Wno-error=implicit-function-declaration")
            .clang_arg("-Wno-error=int-conversion")
            .parse_callbacks(Box::new(RenameFunctions))
            .generate_inline_functions(false)
            .wrap_static_fns(false)
            .derive_default(true)
            .derive_debug(true)
            // only export libfabric symbols and delegate the rest to the libc crate
            .allowlist_item("fi_.*")
            .allowlist_item("FI_.*")
            .allowlist_item("wrap_fi_.*")
            .allowlist_item("OFI_.*")
            .blocklist_type("iovec")
            .blocklist_type("pollfd")
            .blocklist_type("pthread_.*")
            .raw_line("use libc::*;\n")
            .generate()
            .expect("Unable to generate bindings");

        bindings
            .write_to_file(&checked_in)
            .expect("Couldn't write bindings!");
        stage_bindings(&checked_in, &out_path.join("bindings.rs"));
        println!(
            "cargo:warning=regenerated {} from the headers in use",
            checked_in.display()
        );
    }

    #[cfg(not(feature = "regenerate-bindings"))]
    {
        assert!(
            checked_in.exists(),
            "{} is missing. It is generated once and kept in the tree so that \
             building this crate does not need libclang. Regenerate it with \
             `cargo build --features regenerate-bindings`.",
            checked_in.display()
        );
        stage_bindings(&checked_in, &out_path.join("bindings.rs"));
    }
}
