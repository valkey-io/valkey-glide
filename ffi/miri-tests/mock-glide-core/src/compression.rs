// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Mock compression module for Miri tests

use crate::request_type::RequestType;

/// Re-export the minimum compressed size constant from glide-core
pub use glide_core::compression::MIN_COMPRESSED_SIZE;

/// Mock compression manager
#[derive(Debug)]
pub struct CompressionManager;

impl CompressionManager {
    /// Mock compression method - does nothing
    pub fn compress(&self, _data: &[u8]) -> Result<Vec<u8>, String> {
        Ok(Vec::new())
    }

    /// Mock decompression method - does nothing
    pub fn decompress(&self, _data: &[u8]) -> Result<Vec<u8>, String> {
        Ok(Vec::new())
    }

    /// Mock is_enabled method - always returns false
    pub fn is_enabled(&self) -> bool {
        false
    }
}

/// Mock function to process command args for compression
/// In Miri tests, this is a no-op
pub fn process_command_args_for_compression(
    _args: &mut [Vec<u8>],
    _request_type: RequestType,
    _compression_manager: Option<&CompressionManager>,
) -> Result<(), String> {
    Ok(())
}
