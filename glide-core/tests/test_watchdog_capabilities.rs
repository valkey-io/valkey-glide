// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Compile the actual private capability module from its parent context. This
//! avoids false-positive failures caused by importing a private module through
//! the public crate API, and keeps the production types private.

use std::{path::Path, process::Command};

fn compile_fixture(name: &str, expected_error: Option<&str>) {
    let source = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/compile_fail/watchdog")
        .join(format!("{name}.rs"));
    let output_dir = tempfile::tempdir().unwrap();
    let binary = output_dir
        .path()
        .join(format!("{name}{}", std::env::consts::EXE_SUFFIX));
    let output = Command::new(std::env::var_os("RUSTC").unwrap_or_else(|| "rustc".into()))
        .args([
            "--edition=2024",
            "--error-format=json",
            "--crate-name",
            name,
        ])
        .arg(&source)
        .arg("-o")
        .arg(&binary)
        .output()
        .expect("rustc must be available to compile capability fixtures");
    let diagnostics = String::from_utf8(output.stderr).unwrap();
    if let Some(expected) = expected_error {
        assert!(!output.status.success(), "{name} unexpectedly compiled");
        let error_codes: Vec<String> = diagnostics
            .lines()
            .filter_map(|line| {
                let diagnostic: serde_json::Value = serde_json::from_str(line).unwrap();
                if diagnostic["level"] == "error" {
                    diagnostic["code"]["code"].as_str().map(str::to_owned)
                } else {
                    None
                }
            })
            .collect();
        assert_eq!(error_codes, [expected], "{name}: {diagnostics}");
    } else {
        assert!(output.status.success(), "{name}: {diagnostics}");
        let execution = Command::new(binary).output().unwrap();
        assert!(execution.status.success(), "{name}: {execution:?}");
    }
}

#[test]
fn reader_can_clone_and_writer_can_move_to_worker() {
    compile_fixture("valid_capabilities", None);
}

#[test]
fn reader_cannot_publish() {
    compile_fixture("reader_cannot_publish", Some("E0599"));
}

#[test]
fn writer_cannot_clone() {
    compile_fixture("writer_cannot_clone", Some("E0599"));
}

#[test]
fn atomic_is_private() {
    compile_fixture("atomic_is_private", Some("E0616"));
}
