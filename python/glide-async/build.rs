// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use std::fs;
use std::path::Path;
use std::process::Command;

fn main() {

    // --- PART 1: Run Protobuf Generation Script ---
    // The script is in the parent directory
    let script_path = Path::new("../dev.py");

    // Watch the script file so we rebuild if the script logic changes
    println!("cargo:rerun-if-changed={}", script_path.display());

    // Execute: python ../dev.py protobuf
    // We try "python3" first, then fallback to "python"
    let status = Command::new("python3")
        .arg(script_path)
        .arg("protobuf") // The argument you requested
        .status()
        .or_else(|_| {
            Command::new("python")
                .arg(script_path)
                .arg("protobuf")
                .status()
        })
        .expect("Failed to execute python command");

    if !status.success() {
        panic!("Protobuf generation (dev.py) failed with exit code: {:?}", status.code());
    }

    // --- PART 2: Copy glide-shared ---
    let source_dir = Path::new("../glide-shared/glide_shared");    
    let dest_dir = Path::new("./python/glide_shared");
    println!("cargo:rerun-if-changed={}", source_dir.display());

    if source_dir.exists() {
        // Run the recursive copy
        if let Err(e) = copy_dir_all(source_dir, dest_dir) {
            // Panic ensures the build fails if the copy fails (so you don't ship empty wheels)
            panic!("Failed to copy glide-shared: {}", e);
        }
    } else {
        println!("cargo:warning=Parent directory '../glide-shared' not found.");
    }
}

// --- Helper: Recursive Copy Function ---
fn copy_dir_all(src: &Path, dst: &Path) -> std::io::Result<()> {
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        let dest_path = dst.join(entry.file_name());

        if ty.is_dir() {
            copy_dir_all(&entry.path(), &dest_path)?;
        } else {
            fs::copy(entry.path(), &dest_path)?;
        }
    }
    Ok(())
}