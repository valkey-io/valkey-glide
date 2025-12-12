// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//! Compression module providing automatic compression and decompression capabilities
//! for Valkey Glide client operations.

use std::borrow::Cow;
use std::fmt;

use crate::request_type::RequestType;
use telemetrylib::Telemetry;

/// Detailed compression error with context for debugging
#[derive(Debug, Clone, PartialEq)]
pub enum CompressionError {
    /// Compression operation failed with detailed context
    CompressionFailed {
        backend: String,
        level: Option<i32>,
        data_size: usize,
        reason: String,
    },
    /// Decompression operation failed with detailed context
    DecompressionFailed {
        backend: String,
        data_size: usize,
        reason: String,
    },
    /// Unsupported compression backend
    UnsupportedBackend { backend_name: String },
    /// Invalid compression configuration
    InvalidConfiguration { backend: String, reason: String },
}

impl std::fmt::Display for CompressionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CompressionError::CompressionFailed {
                backend,
                level,
                data_size,
                reason,
            } => {
                write!(
                    f,
                    "Compression operation failed: {} encoding failed",
                    backend.to_uppercase()
                )?;
                if let Some(lvl) = level {
                    write!(f, " at level {}", lvl)?;
                }
                write!(f, " for {} data", format_size(*data_size))?;
                if !reason.is_empty() {
                    write!(f, ": {}", reason)?;
                }
                Ok(())
            }
            CompressionError::DecompressionFailed {
                backend,
                data_size,
                reason,
            } => {
                write!(
                    f,
                    "Decompression operation failed: {} decoding failed for {} data",
                    backend.to_uppercase(),
                    format_size(*data_size)
                )?;
                if !reason.is_empty() {
                    write!(f, ": {}", reason)?;
                }
                Ok(())
            }
            CompressionError::UnsupportedBackend { backend_name } => {
                write!(f, "Unsupported compression backend: '{}'", backend_name)
            }
            CompressionError::InvalidConfiguration { backend, reason } => {
                write!(
                    f,
                    "Invalid compression configuration for '{}': {}",
                    backend, reason
                )
            }
        }
    }
}

impl std::error::Error for CompressionError {}

impl CompressionError {
    pub fn compression_failed(
        backend: &str,
        level: Option<i32>,
        data_size: usize,
        reason: impl Into<String>,
    ) -> Self {
        Self::CompressionFailed {
            backend: backend.to_string(),
            level,
            data_size,
            reason: reason.into(),
        }
    }

    pub fn decompression_failed(
        backend: &str,
        data_size: usize,
        reason: impl Into<String>,
    ) -> Self {
        Self::DecompressionFailed {
            backend: backend.to_string(),
            data_size,
            reason: reason.into(),
        }
    }

    pub fn unsupported_backend(backend_name: impl Into<String>) -> Self {
        Self::UnsupportedBackend {
            backend_name: backend_name.into(),
        }
    }

    pub fn invalid_configuration(backend: impl Into<String>, reason: impl Into<String>) -> Self {
        Self::InvalidConfiguration {
            backend: backend.into(),
            reason: reason.into(),
        }
    }

    /// Returns the backend name associated with this error
    pub fn backend(&self) -> &str {
        match self {
            CompressionError::CompressionFailed { backend, .. } => backend,
            CompressionError::DecompressionFailed { backend, .. } => backend,
            CompressionError::InvalidConfiguration { backend, .. } => backend,
            CompressionError::UnsupportedBackend { backend_name } => backend_name,
        }
    }
}

/// Format byte size in human-readable format
fn format_size(bytes: usize) -> String {
    const KB: usize = 1024;
    const MB: usize = KB * 1024;
    const GB: usize = MB * 1024;

    if bytes >= GB {
        format!("{:.2}GB", bytes as f64 / GB as f64)
    } else if bytes >= MB {
        format!("{:.2}MB", bytes as f64 / MB as f64)
    } else if bytes >= KB {
        format!("{:.2}KB", bytes as f64 / KB as f64)
    } else {
        format!("{}B", bytes)
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
            CompressionBackendType::Lz4 => Some(0), // LZ4 default compression
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
            "zstd" => Ok(CompressionBackendType::Zstd),
            "lz4" => Ok(CompressionBackendType::Lz4),
            _ => Err(CompressionError::unsupported_backend(s)),
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
        if self.min_compression_size < MIN_COMPRESSED_SIZE {
            return Err(CompressionError::invalid_configuration(
                self.backend.backend_name(),
                format!(
                    "min_compression_size ({}) must be at least {}",
                    self.min_compression_size, MIN_COMPRESSED_SIZE
                ),
            ));
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
            CommandCompressionBehavior::CompressValues => {
                "Compress values before sending to server"
            }
            CommandCompressionBehavior::DecompressValues => {
                "Decompress values after receiving from server"
            }
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
            return Err(CompressionError::invalid_configuration(
                config.backend.backend_name(),
                format!(
                    "backend mismatch: expected {} (id: {}), got backend with id {}",
                    config.backend.backend_name(),
                    config.backend.backend_id(),
                    backend.backend_id()
                ),
            ));
        }

        // Validate compression level using backend-specific validation
        backend.validate_compression_level(config.compression_level)?;

        Ok(Self { backend, config })
    }

    pub fn should_compress(&self, data: &[u8]) -> bool {
        self.config.should_compress(data.len())
    }

    /// Attempts to compress the value with graceful fallback to original data
    pub fn compress_value<'a>(&self, value: &'a [u8]) -> Cow<'a, [u8]> {
        if !self.config.enabled || !self.should_compress(value) {
            Telemetry::incr_compression_skipped_count(1);
            return Cow::Borrowed(value);
        }

        if self.backend.is_compressed(value) {
            Telemetry::incr_compression_skipped_count(1);
            return Cow::Borrowed(value);
        }

        match self.backend.compress(value, self.config.compression_level) {
            Ok(compressed) => {
                if compressed.len() < value.len() {
                    // Successfully compressed and reduced size
                    Telemetry::incr_total_values_compressed(1);
                    Telemetry::incr_total_original_bytes(value.len());
                    Telemetry::incr_total_bytes_compressed(compressed.len());
                    Cow::Owned(compressed)
                } else {
                    // Compression didn't reduce size, skip it
                    Telemetry::incr_compression_skipped_count(1);
                    Cow::Borrowed(value)
                }
            }
            Err(_) => {
                Telemetry::incr_compression_skipped_count(1);
                Cow::Borrowed(value)
            }
        }
    }

    pub fn decompress_value(&self, value: &[u8]) -> CompressionResult<Vec<u8>> {
        if !self.config.enabled {
            return Ok(value.to_vec());
        }

        if !has_magic_header(value) {
            return Ok(value.to_vec());
        }

        // Extract backend ID from header and route to appropriate backend
        if let Some(backend_id) = extract_backend_id(value) {
            // If the data was compressed with our configured backend, use it
            // This respects the client's compression configuration
            let result = if backend_id == self.backend.backend_id() {
                self.backend.decompress(value)
            } else {
                // Otherwise, use a static backend for decompression
                // Static backends are shared and don't allocate on each call
                // Return error if backend is not supported
                let backend = get_backend_for_decompression(backend_id)?;
                backend.decompress(value)
            };

            // Update telemetry on successful decompression
            if let Ok(ref decompressed) = result {
                Telemetry::incr_total_values_decompressed(1);
                Telemetry::incr_total_bytes_decompressed(decompressed.len());
            }

            result
        } else {
            Ok(value.to_vec())
        }
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
        self.decompress_value(value)
            .unwrap_or_else(|_| value.to_vec())
    }
}

pub mod zstd_backend {
    use super::*;

    #[derive(Debug)]
    pub struct ZstdBackend {
        default_level: i32,
    }

    impl ZstdBackend {
        pub fn new() -> Self {
            Self { default_level: 3 }
        }
    }

    impl Default for ZstdBackend {
        fn default() -> Self {
            Self::new()
        }
    }

    impl CompressionBackend for ZstdBackend {
        fn compress(&self, data: &[u8], level: Option<i32>) -> CompressionResult<Vec<u8>> {
            let compression_level = level.unwrap_or(self.default_level);

            self.validate_compression_level(Some(compression_level))?;

            let compressed_data = zstd::encode_all(data, compression_level).map_err(|e| {
                CompressionError::compression_failed(
                    self.backend_name(),
                    Some(compression_level),
                    data.len(),
                    e.to_string(),
                )
            })?;

            let header = create_header(self.backend_id());

            let mut result = Vec::with_capacity(header.len() + compressed_data.len());
            result.extend_from_slice(&header);
            result.extend_from_slice(&compressed_data);

            Ok(result)
        }

        fn decompress(&self, data: &[u8]) -> CompressionResult<Vec<u8>> {
            if !self.is_compressed(data) {
                return Err(CompressionError::decompression_failed(
                    self.backend_name(),
                    data.len(),
                    "data is not compressed or has invalid header",
                ));
            }

            let compressed_data = &data[HEADER_SIZE..];

            let decompressed_data = zstd::decode_all(compressed_data).map_err(|e| {
                CompressionError::decompression_failed(
                    self.backend_name(),
                    data.len(),
                    e.to_string(),
                )
            })?;

            Ok(decompressed_data)
        }

        fn is_compressed(&self, data: &[u8]) -> bool {
            has_magic_header(data) && extract_backend_id(data) == Some(self.backend_id())
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
                let range = zstd::compression_level_range();
                if !range.contains(&level) {
                    return Err(CompressionError::invalid_configuration(
                        self.backend_name(),
                        format!(
                            "compression level {} is out of valid range {}..={}",
                            level,
                            range.start(),
                            range.end()
                        ),
                    ));
                }
            }
            Ok(())
        }
    }
}

pub mod lz4_backend {
    use super::*;

    /// LZ4 compression level ranges:
    /// - Level 0: Default compression (balanced speed/ratio)
    /// - Levels 1-12: High compression mode (higher = better ratio, slower)
    /// - Levels < 0: Fast mode with acceleration (more negative = faster, lower ratio)
    const LZ4_MIN_LEVEL: i32 = -128; // Practical minimum for fast mode
    const LZ4_MAX_LEVEL: i32 = 12; // Maximum for high compression mode
    const LZ4_DEFAULT_LEVEL: i32 = 0;

    #[derive(Debug)]
    pub struct Lz4Backend {
        default_level: i32,
    }

    impl Lz4Backend {
        pub fn new() -> Self {
            Self {
                default_level: LZ4_DEFAULT_LEVEL,
            }
        }
    }

    impl Default for Lz4Backend {
        fn default() -> Self {
            Self::new()
        }
    }

    impl CompressionBackend for Lz4Backend {
        fn compress(&self, data: &[u8], level: Option<i32>) -> CompressionResult<Vec<u8>> {
            let compression_level = level.unwrap_or(self.default_level);

            self.validate_compression_level(Some(compression_level))?;

            let original_size =
                i32::try_from(data.len()).map_err(|_| CompressionError::InvalidConfiguration {
                    backend: self.backend_name().to_string(),
                    reason: format!(
                        "Data too large for LZ4: {} bytes (max: {} bytes)",
                        data.len(),
                        i32::MAX
                    ),
                })?;
            let size_bytes = (original_size as u32).to_le_bytes();

            // Choose compression mode based on level:
            // - level > 0: High compression mode
            // - level == 0: Default mode
            // - level < 0: Fast mode with acceleration
            let mode = if compression_level > 0 {
                Some(lz4::block::CompressionMode::HIGHCOMPRESSION(
                    compression_level,
                ))
            } else if compression_level < 0 {
                // Convert negative level to positive acceleration value
                Some(lz4::block::CompressionMode::FAST(-compression_level))
            } else {
                Some(lz4::block::CompressionMode::DEFAULT)
            };

            let compressed_block = lz4::block::compress(data, mode, false).map_err(|e| {
                CompressionError::compression_failed(
                    self.backend_name(),
                    Some(compression_level),
                    data.len(),
                    e.to_string(),
                )
            })?;

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
                return Err(CompressionError::decompression_failed(
                    self.backend_name(),
                    data.len(),
                    "data is not compressed or has invalid header",
                ));
            }

            let compressed_data = &data[HEADER_SIZE..];

            if compressed_data.len() < 4 {
                return Err(CompressionError::decompression_failed(
                    self.backend_name(),
                    data.len(),
                    "compressed data too short: missing size header",
                ));
            }

            let size_bytes = &compressed_data[0..4];
            let original_size_u32 =
                u32::from_le_bytes([size_bytes[0], size_bytes[1], size_bytes[2], size_bytes[3]]);
            let compressed_block = &compressed_data[4..];

            // LZ4 block decompression requires knowing the uncompressed size
            // The API uses i32, so we must reject sizes that don't fit
            let original_size = i32::try_from(original_size_u32).map_err(|_| {
                CompressionError::decompression_failed(
                    self.backend_name(),
                    data.len(),
                    format!(
                        "Uncompressed size {} bytes exceeds LZ4 block API limit ({} bytes)",
                        original_size_u32,
                        i32::MAX
                    ),
                )
            })?;

            let decompressed_data = lz4::block::decompress(compressed_block, Some(original_size))
                .map_err(|e| {
                CompressionError::decompression_failed(
                    self.backend_name(),
                    data.len(),
                    e.to_string(),
                )
            })?;

            Ok(decompressed_data)
        }

        fn is_compressed(&self, data: &[u8]) -> bool {
            has_magic_header(data) && extract_backend_id(data) == Some(self.backend_id())
        }

        fn backend_name(&self) -> &'static str {
            "lz4"
        }

        fn default_level(&self) -> Option<i32> {
            Some(self.default_level)
        }

        fn backend_id(&self) -> u8 {
            CompressionBackendType::Lz4.backend_id()
        }

        fn validate_compression_level(&self, level: Option<i32>) -> CompressionResult<()> {
            if let Some(level) = level
                && !(LZ4_MIN_LEVEL..=LZ4_MAX_LEVEL).contains(&level)
            {
                return Err(CompressionError::invalid_configuration(
                    self.backend_name(),
                    format!(
                        "compression level {} is out of valid range {}..={}",
                        level, LZ4_MIN_LEVEL, LZ4_MAX_LEVEL
                    ),
                ));
            }
            Ok(())
        }
    }
}

pub fn process_command_args_for_compression(
    args: &mut [Vec<u8>],
    request_type: RequestType,
    compression_manager: Option<&CompressionManager>,
) -> CompressionResult<()> {
    let Some(manager) = compression_manager else {
        return Ok(());
    };

    if !manager.is_enabled() {
        return Ok(());
    }

    let behavior = request_type.compression_behavior();
    if behavior != CommandCompressionBehavior::CompressValues {
        return Ok(());
    }

    match request_type {
        RequestType::Set => compress_single_value_command(args, manager, 1),
        _ => Ok(()),
    }
}

fn compress_single_value_command(
    args: &mut [Vec<u8>],
    manager: &CompressionManager,
    value_index: usize,
) -> CompressionResult<()> {
    if args.len() <= value_index {
        return Ok(());
    }

    let compressed_value = manager.compress_value(&args[value_index]);
    args[value_index] = compressed_value.into_owned();
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

    let behavior = request_type.compression_behavior();
    if behavior != CommandCompressionBehavior::DecompressValues {
        return Ok(value);
    }

    if matches!(value, Value::Nil) {
        return Ok(value);
    }

    match request_type {
        RequestType::Get => decompress_single_value_response(value, manager),
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
                Err(e) => Ok(Value::BulkString(e.into_bytes())),
            }
        }
        _ => Ok(value),
    }
}

/// Magic prefix for compressed data headers (first 3 bytes)
pub const MAGIC_PREFIX: [u8; 3] = [0x00, 0x01, 0x02];

/// Index in header for version byte and backend_id
pub const HEADER_VERSION_INDEX: usize = 3;
pub const HEADER_BACKEND_INDEX: usize = 4;

/// Current compression format version
pub const CURRENT_VERSION: u8 = 0x00;

/// Total header size: 3 bytes magic + 1 byte version + 1 byte backend_id
pub const HEADER_SIZE: usize = 5;
pub const MIN_COMPRESSED_SIZE: usize = HEADER_SIZE + 1;

/// Checks if data has a valid magic header (any version)
pub fn has_magic_header(data: &[u8]) -> bool {
    data.len() >= HEADER_SIZE && data[0..3] == MAGIC_PREFIX
}

/// Extracts the version byte from the header
/// Returns None if the data doesn't have a valid magic header
pub fn extract_version(data: &[u8]) -> Option<u8> {
    if has_magic_header(data) {
        Some(data[HEADER_VERSION_INDEX])
    } else {
        None
    }
}

/// Extracts the backend ID from the header
/// Returns None if the data doesn't have a valid magic header
pub fn extract_backend_id(data: &[u8]) -> Option<u8> {
    if has_magic_header(data) {
        Some(data[HEADER_BACKEND_INDEX])
    } else {
        None
    }
}

/// Checks if the data has a valid magic header with the current version
pub fn has_current_version_header(data: &[u8]) -> bool {
    extract_version(data) == Some(CURRENT_VERSION)
}

/// Creates a compression header with the current version
pub fn create_header(backend_id: u8) -> [u8; HEADER_SIZE] {
    create_header_with_version(backend_id, CURRENT_VERSION)
}

/// Creates a compression header with a specific version
/// This is useful for testing or supporting multiple versions
pub fn create_header_with_version(backend_id: u8, version: u8) -> [u8; HEADER_SIZE] {
    let mut header = [0u8; HEADER_SIZE];
    header[0..3].copy_from_slice(&MAGIC_PREFIX);
    header[HEADER_VERSION_INDEX] = version;
    header[HEADER_BACKEND_INDEX] = backend_id;
    header
}

/// Lazy-initialized static backends for decompression-only operations.
///
/// These backends are shared across all compression managers to avoid repeated allocations
/// when decompressing data from different backends. They are separate from the client's
/// configured backend because:
///
/// 1. **Performance**: Static backends are initialized once and reused, eliminating allocations
/// 2. **Configuration independence**: When decompressing, the compression config (level, etc.)
///    is irrelevant - we only need the decompression algorithm
/// 3. **Client backend priority**: The client's configured backend is still used for its own
///    compressed data, respecting the client's specific configuration
///
/// Thread-safe initialization is guaranteed by `OnceLock`.
mod static_backends {
    use super::*;
    use std::sync::OnceLock;

    static ZSTD_BACKEND: OnceLock<zstd_backend::ZstdBackend> = OnceLock::new();
    static LZ4_BACKEND: OnceLock<lz4_backend::Lz4Backend> = OnceLock::new();

    pub fn get_zstd_backend() -> &'static zstd_backend::ZstdBackend {
        ZSTD_BACKEND.get_or_init(zstd_backend::ZstdBackend::new)
    }

    pub fn get_lz4_backend() -> &'static lz4_backend::Lz4Backend {
        LZ4_BACKEND.get_or_init(lz4_backend::Lz4Backend::new)
    }
}

/// Gets a reference to a static backend for decompression based on backend ID.
/// These backends are shared and initialized once, avoiding repeated allocations.
fn get_backend_for_decompression(
    backend_id: u8,
) -> CompressionResult<&'static dyn CompressionBackend> {
    match backend_id {
        0x01 => Ok(static_backends::get_zstd_backend()),
        0x02 => Ok(static_backends::get_lz4_backend()),
        _ => Err(CompressionError::unsupported_backend(format!(
            "backend ID 0x{:02x}",
            backend_id
        ))),
    }
}
