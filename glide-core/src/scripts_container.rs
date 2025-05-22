// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use bytes::BytesMut;
use logger_core::{log_info, log_warn};
use once_cell::sync::Lazy;
use sha1_smol::Sha1;
use std::cell::Cell;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

const LOCK_ERR: &str = "Failed to acquire the scripts container lock";

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

pub fn add_script(script: &[u8]) -> String {
    let mut hash = Sha1::new();
    hash.update(script);
    let hash = hash.digest().to_string();
    log_info(
        "script lifetime",
        format!("Added script with hash: `{hash}`"),
    );

    let mut container = CONTAINER.lock().expect(LOCK_ERR);
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

pub fn get_script(hash: &str) -> Option<Arc<BytesMut>> {
    CONTAINER
        .lock()
        .expect(LOCK_ERR)
        .get(hash)
        .map(|entry| entry.script.clone())
}

pub fn remove_script(hash: &str) {
    let mut container = CONTAINER.lock().expect(LOCK_ERR);
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

#[cfg(test)]
mod script_tests {
    use super::*;

    #[test]
    fn test_add_and_get_script() {
        let script = b"print('Hello, World!')";
        let hash = add_script(script);

        let retrieved = get_script(&hash);
        assert!(retrieved.is_some());
        assert_eq!(&retrieved.unwrap()[..], script);
    }

    #[test]
    fn test_reference_counting_and_removal() {
        let script_1 = b"print('ref count test')";
        let script_2 = b"print('ref count test')";
        let hash = add_script(script_1);
        let hash_2 = add_script(script_2); // Increase ref count to 2
        assert_eq!(hash, hash_2);

        // First removal should decrement but not remove
        remove_script(&hash);
        assert!(get_script(&hash).is_some());

        // Second removal should remove the script
        remove_script(&hash);
        assert!(get_script(&hash).is_none());
    }

    #[test]
    fn test_remove_non_existent_script() {
        let fake_hash = "nonexistenthash";
        remove_script(fake_hash); // Should not panic
    }
}
