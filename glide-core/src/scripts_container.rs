// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use bytes::BytesMut;
use logger_core::{log_info, log_warn};
use once_cell::sync::Lazy;
use sha1_smol::Sha1;
use std::cell::Cell;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::Mutex;

// Helper to create a runtime for sync wrappers
fn create_runtime() -> tokio::runtime::Runtime {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .expect("Failed to create Tokio runtime")
}

/// A script entry stored in the global container.
///
/// `ScriptEntry` holds the compiled script bytes and a reference count
/// to track how many times the script has been added via `add_script`.
struct ScriptEntry {
    script: Arc<BytesMut>,
    ref_count: Cell<u32>,
}

static CONTAINER: Lazy<Mutex<HashMap<String, ScriptEntry>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

pub async fn add_script_async(script: &[u8]) -> String {
    let mut hash = Sha1::new();
    hash.update(script);
    let hash = hash.digest().to_string();
    log_info(
        "script lifetime",
        format!("Added script with hash: `{hash}`"),
    );

    let mut container = CONTAINER.lock().await;
    let entry = container
        .entry(hash.clone())
        .or_insert_with(|| ScriptEntry {
            script: Arc::new(BytesMut::from(script)),
            ref_count: Cell::new(0),
        });
    let new_count = entry.ref_count.get() + 1;
    entry.ref_count.set(new_count);
    log_info(
        "script_lifetime",
        format!("Added script with hash: `{hash}`, ref_count = {new_count}"),
    );
    hash
}

pub fn add_script(script: &[u8]) -> String {
    let script = script.to_vec();
    std::thread::spawn(move || create_runtime().block_on(add_script_async(&script)))
        .join()
        .expect("Thread panicked")
}

pub async fn get_script_async(hash: &str) -> Option<Arc<BytesMut>> {
    CONTAINER
        .lock()
        .await
        .get(hash)
        .map(|entry| entry.script.clone())
}

pub fn get_script(hash: &str) -> Option<Arc<BytesMut>> {
    let hash = hash.to_string();
    std::thread::spawn(move || create_runtime().block_on(get_script_async(&hash)))
        .join()
        .expect("Thread panicked")
}

pub async fn remove_script_async(hash: &str) {
    let mut container = CONTAINER.lock().await;
    if let Some(entry) = container.get(hash) {
        let new_count = entry.ref_count.get() - 1;
        entry.ref_count.set(new_count);

        if new_count == 0 {
            container.remove(hash);
            log_info(
                "script_lifetime",
                format!("Removed script with hash `{hash}` (ref_count reached 0)."),
            );
        } else {
            log_info(
                "script_lifetime",
                format!("Decremented ref_count for script `{hash}`: new ref_count = {new_count}."),
            );
        }
    } else {
        log_warn(
            "script_lifetime",
            format!("Attempted to remove non-existent script with hash `{hash}`."),
        );
    }
}

pub fn remove_script(hash: &str) {
    let hash = hash.to_string();
    std::thread::spawn(move || create_runtime().block_on(remove_script_async(&hash)))
        .join()
        .expect("Thread panicked")
}

#[cfg(test)]
mod script_tests {
    use super::*;

    #[tokio::test]
    async fn test_add_and_get_script() {
        let script = b"print('Hello, World!')";
        let hash = add_script_async(script).await;

        let retrieved = get_script_async(&hash).await;
        assert!(retrieved.is_some());
        assert_eq!(&retrieved.unwrap()[..], script);
    }

    #[tokio::test]
    async fn test_reference_counting_and_removal() {
        let script_1 = b"print('ref count test')";
        let script_2 = b"print('ref count test')";
        let hash = add_script_async(script_1).await;
        let hash_2 = add_script_async(script_2).await; // Increase ref count to 2
        assert_eq!(hash, hash_2);

        // First removal should decrement but not remove
        remove_script_async(&hash).await;
        assert!(get_script_async(&hash).await.is_some());

        // Second removal should remove the script
        remove_script_async(&hash).await;
        assert!(get_script_async(&hash).await.is_none());
    }

    #[tokio::test]
    async fn test_remove_non_existent_script() {
        let fake_hash = "nonexistenthash";
        remove_script_async(fake_hash).await; // Should not panic
    }
}
