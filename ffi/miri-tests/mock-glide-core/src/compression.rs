// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Mock compression module for Miri tests

use crate::request_type::RequestType;

/// Header size for compressed data (3 bytes magic + 1 byte version + 1 byte backend_id)
pub const HEADER_SIZE: usize = 5;

/// Minimum compressed size (header + at least 1 byte of payload)
pub const MIN_COMPRESSED_SIZE: usize = HEADER_SIZE + 1;

/// Mock compression error type
#[derive(Debug, Clone)]
pub struct CompressionError(String);

impl std::fmt::Display for CompressionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for CompressionError {}

/// Mock compression result type
pub type CompressionResult<T> = Result<T, CompressionError>;

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

/// Mock function to decompress batch response
/// In Miri tests, this is a no-op that returns the value unchanged
pub fn decompress_batch_response(
    value: redis::Value,
    _compression_manager: &CompressionManager,
) -> Result<redis::Value, String> {
    Ok(value)
}

/// Mock function to try decompress batch response with optional manager
/// In Miri tests, this is a no-op that returns the value unchanged
pub fn try_decompress_batch_response(
    value: redis::Value,
    _manager: Option<&CompressionManager>,
) -> CompressionResult<redis::Value> {
    Ok(value)
}
