//! Command metadata for JNI performance benchmarking.
//!
//! This module defines a realistic CommandMetadata struct that represents the complexity
//! needed for fair comparison with the UDS implementation. While simpler than the full
//! production version, it maintains the essential overhead for accurate benchmarking.

use std::sync::atomic::{AtomicU32, Ordering};

/// CommandMetadata for realistic JNI performance comparison.
///
/// This represents a realistic metadata structure that would be needed
/// for actual JNI implementation while being simpler than the full UDS approach.
/// Size: 32 bytes (larger than oversimplified 16-byte version but realistic).
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct CommandMetadata {
    /// Command type: 1=GET, 2=SET, 3=PING
    pub command_type: u32,

    /// Size of the payload buffer in bytes
    pub payload_length: u32,

    /// Unique request ID for matching async responses
    pub request_id: u32,

    /// Flags for command options (routing, timeout, etc.)
    pub flags: u32,

    /// Timestamp for latency measurement (microseconds)
    pub timestamp_us: u64,

    /// Client ID for connection multiplexing
    pub client_id: u32,

    /// Timeout value in milliseconds
    pub timeout_ms: u32,
}

impl CommandMetadata {
    /// Total size of the metadata struct in bytes (32 bytes).
    pub const SIZE: usize = std::mem::size_of::<Self>();

    /// Create a new CommandMetadata for the given command type.
    pub fn new(command_type: u32, payload_length: u32) -> Self {
        Self {
            command_type,
            payload_length,
            request_id: next_request_id(),
            flags: 0,
            timestamp_us: current_time_us(),
            client_id: 1,
            timeout_ms: 5000, // 5 second default timeout
        }
    }

    /// Create metadata for a GET command.
    pub fn get(key_length: u32) -> Self {
        Self::new(command_type::GET, key_length)
    }

    /// Create metadata for a SET command.
    pub fn set(payload_length: u32) -> Self {
        Self::new(command_type::SET, payload_length)
    }

    /// Create metadata for a PING command.
    pub fn ping() -> Self {
        Self::new(command_type::PING, 0)
    }
}

/// Global request ID counter for async response matching.
static REQUEST_ID_COUNTER: AtomicU32 = AtomicU32::new(1);

/// Generate the next unique request ID.
fn next_request_id() -> u32 {
    REQUEST_ID_COUNTER.fetch_add(1, Ordering::Relaxed)
}

/// Get current timestamp in microseconds.
fn current_time_us() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_micros() as u64
}

impl Default for CommandMetadata {
    fn default() -> Self {
        Self {
            command_type: 0,
            payload_length: 0,
            request_id: 0,
            flags: 0,
            timestamp_us: 0,
            client_id: 0,
            timeout_ms: 0,
        }
    }
}

/// Command type constants for the POC.
pub mod command_type {
    /// GET command
    pub const GET: u32 = 1;

    /// SET command
    pub const SET: u32 = 2;

    /// PING command
    pub const PING: u32 = 3;
}

/// Flags for command options.
pub mod flags {
    /// No special flags
    pub const NONE: u32 = 0;

    /// Command requires write access
    pub const WRITE: u32 = 1 << 0;

    /// Command can be routed to replica
    pub const READ_REPLICA: u32 = 1 << 1;

    /// Command has timeout override
    pub const TIMEOUT_OVERRIDE: u32 = 1 << 2;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_command_metadata_size() {
        // Ensure the size is exactly 32 bytes for realistic benchmarking
        assert_eq!(CommandMetadata::SIZE, 32);
    }

    #[test]
    fn test_command_creation() {
        let metadata = CommandMetadata::new(command_type::GET, 32);
        assert_eq!(metadata.command_type, command_type::GET);
        assert_eq!(metadata.payload_length, 32);
        assert!(metadata.request_id > 0);
        assert!(metadata.timestamp_us > 0);
    }

    #[test]
    fn test_specialized_constructors() {
        let get_meta = CommandMetadata::get(8);
        assert_eq!(get_meta.command_type, command_type::GET);
        assert_eq!(get_meta.payload_length, 8);

        let set_meta = CommandMetadata::set(16);
        assert_eq!(set_meta.command_type, command_type::SET);
        assert_eq!(set_meta.payload_length, 16);

        let ping_meta = CommandMetadata::ping();
        assert_eq!(ping_meta.command_type, command_type::PING);
        assert_eq!(ping_meta.payload_length, 0);
    }

    #[test]
    fn test_unique_request_ids() {
        let meta1 = CommandMetadata::get(8);
        let meta2 = CommandMetadata::get(8);
        assert_ne!(meta1.request_id, meta2.request_id);
    }
}
