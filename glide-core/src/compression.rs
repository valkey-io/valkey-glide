// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Compression module providing automatic compression and decompression capabilities
//! for Valkey Glide client operations.

use std::fmt;

use crate::request_type::RequestType;

#[repr(C)]
#[derive(Debug, Clone, PartialEq)]
pub enum CompressionError {
    CompressionFailed = 0,
    DecompressionFailed = 1,
    UnsupportedBackend = 2,
    InvalidConfiguration = 3,
    BackendInitializationFailed = 4,
}

impl std::fmt::Display for CompressionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CompressionError::CompressionFailed => write!(f, "Compression operation failed"),
            CompressionError::DecompressionFailed => write!(f, "Decompression operation failed"),
            CompressionError::UnsupportedBackend => write!(f, "Unsupported compression backend"),
            CompressionError::InvalidConfiguration => write!(f, "Invalid compression configuration"),
            CompressionError::BackendInitializationFailed => write!(f, "Backend initialization failed"),
        }
    }
}

impl std::error::Error for CompressionError {}

impl CompressionError {
    pub fn compression_failed() -> Self {
        Self::CompressionFailed
    }

    pub fn decompression_failed() -> Self {
        Self::DecompressionFailed
    }

    pub fn unsupported_backend() -> Self {
        Self::UnsupportedBackend
    }

    pub fn invalid_configuration() -> Self {
        Self::InvalidConfiguration
    }

    pub fn backend_initialization_failed() -> Self {
        Self::BackendInitializationFailed
    }
}

pub type CompressionResult<T> = Result<T, CompressionError>;

pub trait CompressionBackend: Send + Sync + fmt::Debug {
    fn compress(&self, data: &[u8], level: Option<i32>) -> CompressionResult<Vec<u8>>;
    fn decompress(&self, data: &[u8]) -> CompressionResult<Vec<u8>>;
    fn is_compressed(&self, data: &[u8]) -> bool;
    fn backend_name(&self) -> &'static str;
    fn default_level(&self) -> Option<i32>;
    fn backend_id(&self) -> u8;
    fn validate_compression_level(&self, level: Option<i32>) -> CompressionResult<()>;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum CompressionBackendType {
    Zstd,
    Lz4,
}

impl CompressionBackendType {
    pub fn backend_id(&self) -> u8 {
        match self {
            CompressionBackendType::Zstd => 0x01,
            CompressionBackendType::Lz4 => 0x02,
        }
    }

    pub fn backend_name(&self) -> &'static str {
        match self {
            CompressionBackendType::Zstd => "zstd",
            CompressionBackendType::Lz4 => "lz4",
        }
    }

    pub fn default_level(&self) -> Option<i32> {
        match self {
            CompressionBackendType::Zstd => Some(3),
            CompressionBackendType::Lz4 => None,
        }
    }
}

impl std::fmt::Display for CompressionBackendType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.backend_name())
    }
}

impl std::str::FromStr for CompressionBackendType {
    type Err = CompressionError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.to_lowercase().as_str() {
            "zstd" | "zstandard" => Ok(CompressionBackendType::Zstd),
            "lz4" => Ok(CompressionBackendType::Lz4),
            _ => Err(CompressionError::unsupported_backend()),
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct CompressionConfig {
    pub enabled: bool,
    pub backend: CompressionBackendType,
    pub compression_level: Option<i32>,
    pub min_compression_size: usize,
}

impl CompressionConfig {
    pub fn new(backend: CompressionBackendType) -> Self {
        Self {
            enabled: true,
            backend,
            compression_level: backend.default_level(),
            min_compression_size: 64,
        }
    }

    pub fn disabled() -> Self {
        Self {
            enabled: false,
            backend: CompressionBackendType::Zstd,
            compression_level: None,
            min_compression_size: 64,
        }
    }

    pub fn with_compression_level(mut self, level: Option<i32>) -> Self {
        self.compression_level = level;
        self
    }

    pub fn with_min_compression_size(mut self, size: usize) -> Self {
        self.min_compression_size = size;
        self
    }

    pub fn validate(&self) -> CompressionResult<()> {
        if self.min_compression_size == 0 {
            return Err(CompressionError::invalid_configuration());
        }

        if self.min_compression_size > 1024 * 1024 {
            return Err(CompressionError::invalid_configuration());
        }

        Ok(())
    }

    pub fn should_compress(&self, data_size: usize) -> bool {
        self.enabled && data_size >= self.min_compression_size
    }
}

impl Default for CompressionConfig {
    fn default() -> Self {
        Self::disabled()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum CommandCompressionBehavior {
    CompressValues,
    DecompressValues,
    NoCompression,
}

impl CommandCompressionBehavior {
    pub fn description(&self) -> &'static str {
        match self {
            CommandCompressionBehavior::CompressValues => "Compress values before sending to server",
            CommandCompressionBehavior::DecompressValues => "Decompress values after receiving from server",
            CommandCompressionBehavior::NoCompression => "No compression processing required",
        }
    }
}

impl std::fmt::Display for CommandCompressionBehavior {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CommandCompressionBehavior::CompressValues => write!(f, "CompressValues"),
            CommandCompressionBehavior::DecompressValues => write!(f, "DecompressValues"),
            CommandCompressionBehavior::NoCompression => write!(f, "NoCompression"),
        }
    }
}

pub fn get_command_compression_behavior(request_type: RequestType) -> CommandCompressionBehavior {
    match request_type {
        RequestType::Set => CommandCompressionBehavior::CompressValues,
        RequestType::Get => CommandCompressionBehavior::DecompressValues,
        _ => CommandCompressionBehavior::NoCompression,
    }
}

#[derive(Debug)]
pub struct CompressionManager {
    backend: Box<dyn CompressionBackend>,
    config: CompressionConfig,
}

impl CompressionManager {
    pub fn new(
        backend: Box<dyn CompressionBackend>,
        config: CompressionConfig,
    ) -> CompressionResult<Self> {
        config.validate()?;

        if backend.backend_id() != config.backend.backend_id() {
            return Err(CompressionError::invalid_configuration());
        }

        // Validate compression level using backend-specific validation
        backend.validate_compression_level(config.compression_level)?;

        Ok(Self { backend, config })
    }

    pub fn should_compress(&self, data: &[u8]) -> bool {
        self.config.should_compress(data.len())
    }

    /// Attempts to compress the value with graceful fallback to original data
    pub fn compress_value(&self, value: &[u8]) -> Vec<u8> {
        if !self.config.enabled || !self.should_compress(value) {
            return value.to_vec();
        }

        if self.backend.is_compressed(value) {
            return value.to_vec();
        }

        match self.backend.compress(value, self.config.compression_level) {
            Ok(compressed) => {
                if compressed.len() < value.len() {
                    compressed
                } else {
                    value.to_vec()
                }
            }
            Err(_) => value.to_vec(),
        }
    }

    pub fn decompress_value(&self, value: &[u8]) -> CompressionResult<Vec<u8>> {
        if !self.config.enabled {
            return Ok(value.to_vec());
        }

        if !self.backend.is_compressed(value) {
            return Ok(value.to_vec());
        }

        self.backend.decompress(value)
    }

    pub fn config(&self) -> &CompressionConfig {
        &self.config
    }

    pub fn backend_name(&self) -> &'static str {
        self.backend.backend_name()
    }

    pub fn is_enabled(&self) -> bool {
        self.config.enabled
    }



    /// Attempts to decompress the value with graceful fallback to original data
    pub fn try_decompress_value(&self, value: &[u8]) -> Vec<u8> {
        match self.decompress_value(value) {
            Ok(decompressed) => decompressed,
            Err(_) => value.to_vec(),
        }
    }
}

#[cfg(feature = "compression")]
pub mod zstd_backend {
    use super::*;

    #[derive(Debug)]
    pub struct ZstdBackend {
        default_level: i32,
    }

    impl ZstdBackend {
        pub fn new() -> CompressionResult<Self> {
            Ok(Self {
                default_level: 3,
            })
        }
    }

    impl Default for ZstdBackend {
        fn default() -> Self {
            Self::new().expect("Default ZstdBackend creation should not fail")
        }
    }

    impl CompressionBackend for ZstdBackend {
        fn compress(&self, data: &[u8], level: Option<i32>) -> CompressionResult<Vec<u8>> {
            let compression_level = level.unwrap_or(self.default_level);
            
            self.validate_compression_level(Some(compression_level))?;

            let compressed_data = zstd::encode_all(data, compression_level)
                .map_err(|_| CompressionError::compression_failed())?;

            let header = create_header(self.backend_id());
            
            let mut result = Vec::with_capacity(header.len() + compressed_data.len());
            result.extend_from_slice(&header);
            result.extend_from_slice(&compressed_data);
            
            Ok(result)
        }

        fn decompress(&self, data: &[u8]) -> CompressionResult<Vec<u8>> {
            if !self.is_compressed(data) {
                return Err(CompressionError::decompression_failed());
            }

            let compressed_data = &data[HEADER_SIZE..];
            
            let decompressed_data = zstd::decode_all(compressed_data)
                .map_err(|_| CompressionError::decompression_failed())?;

            Ok(decompressed_data)
        }

        fn is_compressed(&self, data: &[u8]) -> bool {
            has_magic_header(data) 
                && extract_backend_id(data) == Some(self.backend_id())
        }

        fn backend_name(&self) -> &'static str {
            "zstd"
        }

        fn default_level(&self) -> Option<i32> {
            Some(self.default_level)
        }

        fn backend_id(&self) -> u8 {
            CompressionBackendType::Zstd.backend_id()
        }

        fn validate_compression_level(&self, level: Option<i32>) -> CompressionResult<()> {
            if let Some(level) = level {
                if !zstd::compression_level_range().contains(&level) {
                    return Err(CompressionError::invalid_configuration());
                }
            }
            Ok(())
        }
    }
}

#[cfg(feature = "compression")]
pub mod lz4_backend {
    use super::*;

    #[derive(Debug)]
    pub struct Lz4Backend {
        _placeholder: (),
    }

    impl Lz4Backend {
        pub fn new() -> CompressionResult<Self> {
            Ok(Self {
                _placeholder: (),
            })
        }
    }

    impl Default for Lz4Backend {
        fn default() -> Self {
            Self::new().expect("Default Lz4Backend creation should not fail")
        }
    }

    impl CompressionBackend for Lz4Backend {
        fn compress(&self, data: &[u8], level: Option<i32>) -> CompressionResult<Vec<u8>> {
            if level.is_some() {
                return Err(CompressionError::invalid_configuration());
            }

            let original_size = data.len() as u32;
            let size_bytes = original_size.to_le_bytes();
            
            let compressed_block = lz4::block::compress(data, None, false)
                .map_err(|_| CompressionError::compression_failed())?;
            
            let mut compressed_data = Vec::with_capacity(4 + compressed_block.len());
            compressed_data.extend_from_slice(&size_bytes);
            compressed_data.extend_from_slice(&compressed_block);

            let header = create_header(self.backend_id());
            
            let mut result = Vec::with_capacity(header.len() + compressed_data.len());
            result.extend_from_slice(&header);
            result.extend_from_slice(&compressed_data);
            
            Ok(result)
        }

        fn decompress(&self, data: &[u8]) -> CompressionResult<Vec<u8>> {
            if !self.is_compressed(data) {
                return Err(CompressionError::decompression_failed());
            }

            let compressed_data = &data[HEADER_SIZE..];
            
            if compressed_data.len() < 4 {
                return Err(CompressionError::decompression_failed());
            }
            
            let size_bytes = &compressed_data[0..4];
            let original_size = u32::from_le_bytes([size_bytes[0], size_bytes[1], size_bytes[2], size_bytes[3]]) as i32;
            let compressed_block = &compressed_data[4..];
            
            let decompressed_data = lz4::block::decompress(compressed_block, Some(original_size))
                .map_err(|_| CompressionError::decompression_failed())?;

            Ok(decompressed_data)
        }

        fn is_compressed(&self, data: &[u8]) -> bool {
            has_magic_header(data) 
                && extract_backend_id(data) == Some(self.backend_id())
        }

        fn backend_name(&self) -> &'static str {
            "lz4"
        }

        fn default_level(&self) -> Option<i32> {
            None
        }

        fn backend_id(&self) -> u8 {
            CompressionBackendType::Lz4.backend_id()
        }

        fn validate_compression_level(&self, level: Option<i32>) -> CompressionResult<()> {
            if level.is_some() {
                return Err(CompressionError::invalid_configuration());
            }
            Ok(())
        }
    }


}

pub fn process_command_args_for_compression(
    args: &mut Vec<Vec<u8>>,
    request_type: RequestType,
    compression_manager: Option<&CompressionManager>,
) -> CompressionResult<()> {
    let Some(manager) = compression_manager else {
        return Ok(());
    };

    if !manager.is_enabled() {
        return Ok(());
    }

    let behavior = get_command_compression_behavior(request_type);
    if behavior != CommandCompressionBehavior::CompressValues {
        return Ok(());
    }

    match request_type {
        RequestType::Set => {
            compress_single_value_command(args, manager, 1)
        }
        _ => Ok(()),
    }
}

fn compress_single_value_command(
    args: &mut Vec<Vec<u8>>,
    manager: &CompressionManager,
    value_index: usize,
) -> CompressionResult<()> {
    if args.len() <= value_index {
        return Ok(());
    }
    
    let compressed_value = manager.compress_value(&args[value_index]);
    args[value_index] = compressed_value;
    Ok(())
}



pub fn process_response_for_decompression(
    value: redis::Value,
    request_type: RequestType,
    compression_manager: Option<&CompressionManager>,
) -> CompressionResult<redis::Value> {
    use redis::Value;

    let Some(manager) = compression_manager else {
        return Ok(value);
    };

    if !manager.is_enabled() {
        return Ok(value);
    }

    let behavior = get_command_compression_behavior(request_type);
    if behavior != CommandCompressionBehavior::DecompressValues {
        return Ok(value);
    }

    if matches!(value, Value::Nil) {
        return Ok(value);
    }

    match request_type {
        RequestType::Get => {
            decompress_single_value_response(value, manager)
        }
        _ => Ok(value),
    }
}

pub fn decompress_single_value_response(
    value: redis::Value,
    manager: &CompressionManager,
) -> CompressionResult<redis::Value> {
    use redis::Value;
    
    match value {
        Value::BulkString(bytes) => {
            let decompressed = manager.try_decompress_value(&bytes);
            Ok(Value::BulkString(decompressed))
        }
        Value::SimpleString(s) => {
            let decompressed = manager.try_decompress_value(s.as_bytes());
            match String::from_utf8(decompressed) {
                Ok(decompressed_string) => Ok(Value::SimpleString(decompressed_string)),
                Err(_) => Ok(Value::BulkString(manager.try_decompress_value(s.as_bytes()))),
            }
        }
        _ => Ok(value),
    }
}

pub const MAGIC_BYTES: [u8; 4] = [0x47, 0x4C, 0x49, 0x44];
pub const HEADER_SIZE: usize = 5;
pub const MIN_COMPRESSED_SIZE: usize = HEADER_SIZE + 1;

pub fn has_magic_header(data: &[u8]) -> bool {
    data.len() >= HEADER_SIZE && data[0..4] == MAGIC_BYTES
}

pub fn extract_backend_id(data: &[u8]) -> Option<u8> {
    if has_magic_header(data) {
        Some(data[4])
    } else {
        None
    }
}

pub fn create_header(backend_id: u8) -> [u8; HEADER_SIZE] {
    let mut header = [0u8; HEADER_SIZE];
    header[0..4].copy_from_slice(&MAGIC_BYTES);
    header[4] = backend_id;
    header
}
