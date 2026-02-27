// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use logger_core::log_debug;
use nanoid::nanoid;
use once_cell::sync::Lazy;
use redis::{RedisResult, ScanStateRC};
use std::collections::HashMap;
use tokio::sync::Mutex;

// This is a container for storing the cursor of a cluster scan.
// The cursor for a cluster scan is a ref to the actual ScanState struct in redis-rs.
// In order to avoid dropping it when it is passed between layers of the application,
// we store it in this container and only pass the id of the cursor.
// The cursor is stored in the container and can be retrieved using the id.
// In wrapper layer we wrap the id in an object, which, when dropped, trigger the removal of the cursor from the container.
// When the ref is removed from the container, the actual ScanState struct is dropped by Rust GC.

static CONTAINER: Lazy<Mutex<HashMap<String, ScanStateRC>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

pub async fn insert_cluster_scan_cursor_async(scan_state: ScanStateRC) -> String {
    let id = nanoid!();
    CONTAINER.lock().await.insert(id.clone(), scan_state);
    log_debug(
        "scan_state_cursor insert",
        format!("Inserted to container scan_state_cursor with id: `{id:?}`"),
    );
    id
}

pub fn insert_cluster_scan_cursor(scan_state: ScanStateRC) -> String {
    std::thread::spawn(move || {
        tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime")
            .block_on(insert_cluster_scan_cursor_async(scan_state))
    })
    .join()
    .expect("Thread panicked")
}

pub async fn get_cluster_scan_cursor_async(id: String) -> RedisResult<ScanStateRC> {
    let scan_state_rc = CONTAINER.lock().await.get(&id).cloned();
    log_debug(
        "scan_state_cursor get",
        format!("Retrieved from container scan_state_cursor with id: `{id:?}`"),
    );
    match scan_state_rc {
        Some(scan_state_rc) => Ok(scan_state_rc),
        None => Err(redis::RedisError::from((
            redis::ErrorKind::ResponseError,
            "Invalid scan_state_cursor id",
            format!("The scan_state_cursor sent with id: `{id:?}` does not exist"),
        ))),
    }
}

pub fn get_cluster_scan_cursor(id: String) -> RedisResult<ScanStateRC> {
    std::thread::spawn(move || {
        tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime")
            .block_on(get_cluster_scan_cursor_async(id))
    })
    .join()
    .expect("Thread panicked")
}

pub async fn remove_scan_state_cursor_async(id: String) {
    CONTAINER.lock().await.remove(&id);
    log_debug(
        "scan_state_cursor remove",
        format!("Removed from container scan_state_cursor with id: `{id:?}`"),
    );
}

pub fn remove_scan_state_cursor(id: String) {
    std::thread::spawn(move || {
        tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .expect("Failed to create Tokio runtime")
            .block_on(remove_scan_state_cursor_async(id))
    })
    .join()
    .expect("Thread panicked")
}
