// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use bytes::BytesMut;
use logger_core::{log_info, log_warn};
use once_cell::sync::Lazy;
use sha1_smol::Sha1;
use std::cell::Cell;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};

const LOCK_ERR: &str = "Failed to acquire the scripts container lock";

/// A script entry stored in the global container.
///
/// `ScriptEntry` holds the compiled script bytes, a reference count
/// to track how many times the script has been added via `add_script`,
/// and a generation counter to prevent ABA problems.
struct ScriptEntry {
    script: Arc<BytesMut>,
    ref_count: Cell<u32>,
    generation: u64,
}

/// Global generation counter to ensure each script container entry has a unique identity
static GENERATION_COUNTER: AtomicU64 = AtomicU64::new(1);

static CONTAINER: Lazy<Mutex<HashMap<String, ScriptEntry>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

/// Adds a script to the container and returns the hash and generation ID.
/// The generation ID must be used when removing the script to prevent ABA issues.
pub fn add_script(script: &[u8]) -> (String, u64) {
    let mut hash = Sha1::new();
    hash.update(script);
    let hash = hash.digest().to_string();
    log_info(
        "script lifetime",
        format!("Added script with hash: `{hash}`"),
    );

    let mut container = CONTAINER.lock().expect(LOCK_ERR);
    if let Some(entry) = container.get(&hash) {
        // Existing entry - increment ref count and return existing generation
        let new_count = entry.ref_count.get() + 1;
        entry.ref_count.set(new_count);
        log_info(
            "script_lifetime",
            format!(
                "Incremented ref_count for existing script `{hash}`: new ref_count = {new_count}, generation = {}",
                entry.generation
            ),
        );
        (hash, entry.generation)
    } else {
        // New entry - create with new generation
        let generation = GENERATION_COUNTER.fetch_add(1, Ordering::SeqCst);
        let entry = ScriptEntry {
            script: Arc::new(BytesMut::from(script)),
            ref_count: Cell::new(1),
            generation,
        };
        container.insert(hash.clone(), entry);
        log_info(
            "script_lifetime",
            format!(
                "Added new script with hash: `{hash}`, ref_count = 1, generation = {generation}"
            ),
        );
        (hash, generation)
    }
}

pub fn get_script(hash: &str) -> Option<Arc<BytesMut>> {
    CONTAINER
        .lock()
        .expect(LOCK_ERR)
        .get(hash)
        .map(|entry| entry.script.clone())
}

/// Removes a script from the container, but only if the generation matches.
/// This prevents ABA problems where an old script instance removes a new script instance.
pub fn remove_script(hash: &str, generation: u64) {
    let mut container = CONTAINER.lock().expect(LOCK_ERR);
    if let Some(entry) = container.get(hash) {
        // Only remove if this is the same generation/instance
        if entry.generation != generation {
            log_warn(
                "script_lifetime",
                format!(
                    "Attempted to remove script `{hash}` with mismatched generation (expected {}, found {}). This script instance was already replaced.",
                    generation, entry.generation
                ),
            );
            return;
        }

        let current_count = entry.ref_count.get();
        if current_count == 0 {
            log_warn(
                "script_lifetime",
                format!("Attempted to remove script `{hash}` with ref_count already at 0."),
            );
            return;
        }

        let new_count = current_count - 1;
        entry.ref_count.set(new_count);

        if new_count == 0 {
            container.remove(hash);
            log_info(
                "script_lifetime",
                format!(
                    "Removed script with hash `{hash}`, generation {generation} (ref_count reached 0)."
                ),
            );
        } else {
            log_info(
                "script_lifetime",
                format!(
                    "Decremented ref_count for script `{hash}`, generation {generation}: new ref_count = {new_count}."
                ),
            );
        }
    } else {
        log_warn(
            "script_lifetime",
            format!(
                "Attempted to remove non-existent script with hash `{hash}`, generation {generation}."
            ),
        );
    }
}

#[cfg(test)]
mod script_tests {
    use super::*;

    #[test]
    fn test_add_and_get_script() {
        let script = b"print('Hello, World!')";
        let (hash, _gen) = add_script(script);

        let retrieved = get_script(&hash);
        assert!(retrieved.is_some());
        assert_eq!(&retrieved.unwrap()[..], script);
    }

    #[test]
    fn test_reference_counting_and_removal() {
        let script_1 = b"print('ref count test')";
        let script_2 = b"print('ref count test')";
        let (hash, gen1) = add_script(script_1);
        let (hash_2, gen2) = add_script(script_2); // Increase ref count to 2
        assert_eq!(hash, hash_2);
        assert_eq!(gen1, gen2); // Same generation for same script

        // First removal should decrement but not remove
        remove_script(&hash, gen1);
        assert!(get_script(&hash).is_some());

        // Second removal should remove the script
        remove_script(&hash, gen2);
        assert!(get_script(&hash).is_none());
    }

    #[test]
    fn test_remove_non_existent_script() {
        let fake_hash = "nonexistenthash";
        remove_script(fake_hash, 999); // Should not panic
    }

    #[test]
    fn test_aba_protection() {
        let script = b"print('aba test')";

        // Add script first time
        let (hash, gen1) = add_script(script);

        // Remove it completely
        remove_script(&hash, gen1);
        assert!(get_script(&hash).is_none());

        // Add same script again - should get new generation
        let (hash2, gen2) = add_script(script);
        assert_eq!(hash, hash2);
        assert_ne!(gen1, gen2); // Different generation

        // Try to remove with old generation - should fail
        remove_script(&hash, gen1);
        assert!(get_script(&hash).is_some()); // Script should still be there

        // Remove with correct generation - should succeed
        remove_script(&hash, gen2);
        assert!(get_script(&hash).is_none());
    }
}
