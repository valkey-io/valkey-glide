// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

use glide_core::compression::*;
use glide_core::request_type::RequestType;
use std::str::FromStr;

#[cfg(test)]
mod compression_tests {
    use super::*;

    #[test]
    fn test_descriptive_errors() {
        use glide_core::compression::lz4_backend::Lz4Backend;
        use glide_core::compression::zstd_backend::ZstdBackend;

        // Scenario 1: Invalid compression level for Zstd (triggers InvalidConfiguration)
        let backend = Box::new(ZstdBackend::new());
        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_compression_level(Some(999)); // Invalid level
        let result = CompressionManager::new(backend, config);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::InvalidConfiguration { .. }));
        assert!(err.to_string().contains("compression level"));
        assert!(err.to_string().contains("999"));
        assert!(err.to_string().contains("zstd"));
        assert_eq!(err.backend(), "zstd");

        // Scenario 2: LZ4 with invalid compression level (triggers InvalidConfiguration)
        let backend = Box::new(Lz4Backend::new());
        let config =
            CompressionConfig::new(CompressionBackendType::Lz4).with_compression_level(Some(999)); // Invalid level for LZ4
        let result = CompressionManager::new(backend, config);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::InvalidConfiguration { .. }));
        assert!(err.to_string().contains("compression level"));
        assert!(err.to_string().contains("999"));
        assert!(err.to_string().contains("lz4"));
        assert_eq!(err.backend(), "lz4");

        // Scenario 3: Backend mismatch (triggers InvalidConfiguration)
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Lz4); // Mismatched backend
        let result = CompressionManager::new(backend, config);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::InvalidConfiguration { .. }));
        assert!(err.to_string().contains("backend mismatch"));
        assert!(err.to_string().contains("lz4"));
        assert_eq!(err.backend(), "lz4");

        // Scenario 4: min_compression_size too small (triggers InvalidConfiguration)
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_min_compression_size(MIN_COMPRESSED_SIZE - 1);
        let result = CompressionManager::new(backend, config);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::InvalidConfiguration { .. }));
        assert!(err.to_string().contains("min_compression_size"));
        assert!(err.to_string().contains("zstd"));
        assert_eq!(err.backend(), "zstd");

        // Scenario 5: Decompressing corrupted data (triggers DecompressionFailed)
        let backend = ZstdBackend::new();
        let mut corrupted_data = create_header(0x01).to_vec();
        corrupted_data.extend_from_slice(&[0xFF, 0xFF, 0xFF, 0xFF]); // Invalid compressed data
        let result = backend.decompress(&corrupted_data, None);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::DecompressionFailed { .. }));
        assert!(err.to_string().contains("ZSTD"));
        assert!(err.to_string().contains("decoding failed"));
        assert_eq!(err.backend(), "zstd");

        // Scenario 6: Decompressing data without proper header (triggers DecompressionFailed)
        let backend = Lz4Backend::new();
        let invalid_data = b"not compressed data";
        let result = backend.decompress(invalid_data, None);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::DecompressionFailed { .. }));
        assert!(err.to_string().contains("invalid header"));
        assert_eq!(err.backend(), "lz4");

        // Scenario 7: Unsupported backend via FromStr (triggers UnsupportedBackend)
        let result = CompressionBackendType::from_str("brotli");
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::UnsupportedBackend { .. }));
        assert!(err.to_string().contains("brotli"));
        assert_eq!(err.backend(), "brotli");
    }

    #[test]
    fn test_error_datasize_display_formatting() {
        // Test that different data sizes are formatted correctly
        let err_bytes = CompressionError::compression_failed("zstd", Some(3), 512, "test");
        println!("Error bytes: {}", err_bytes);
        assert!(err_bytes.to_string().contains("512B"));

        let err_kb = CompressionError::compression_failed("zstd", Some(3), 1536, "test");
        println!("Error KB: {}", err_kb);
        assert!(err_kb.to_string().contains("1.50KB"));

        let err_mb = CompressionError::compression_failed("zstd", Some(3), 1_048_576, "test");
        println!("Error MB: {}", err_mb);
        assert!(err_mb.to_string().contains("1.00MB"));

        let err_gb = CompressionError::compression_failed("zstd", Some(3), 1_073_741_824, "test");
        println!("Error GB: {}", err_gb);
        assert!(err_gb.to_string().contains("1.00GB"));
    }

    #[test]
    fn test_backend_type_properties() {
        // Test Zstd backend
        let zstd = CompressionBackendType::Zstd;
        assert_eq!(zstd.backend_id(), 0x01);
        assert_eq!(zstd.backend_name(), "zstd");
        assert_eq!(zstd.default_level(), Some(3));
        let lz4 = CompressionBackendType::Lz4;
        assert_eq!(lz4.backend_id(), 0x02);
        assert_eq!(lz4.backend_name(), "lz4");
        assert_eq!(lz4.default_level(), Some(0));
        assert_eq!(lz4.to_string(), "lz4");

        // Test FromStr for Zstd
        let parsed_zstd = CompressionBackendType::from_str("zstd").unwrap();
        assert_eq!(parsed_zstd, CompressionBackendType::Zstd);
        let parsed_zstd_upper = CompressionBackendType::from_str("ZSTD").unwrap();
        assert_eq!(parsed_zstd_upper, CompressionBackendType::Zstd);

        // Test FromStr for Lz4
        let parsed_lz4 = CompressionBackendType::from_str("lz4").unwrap();
        assert_eq!(parsed_lz4, CompressionBackendType::Lz4);
        let parsed_lz4_upper = CompressionBackendType::from_str("LZ4").unwrap();
        assert_eq!(parsed_lz4_upper, CompressionBackendType::Lz4);
    }

    #[test]
    fn test_backend_type_from_str_invalid() {
        let result = CompressionBackendType::from_str("brotli");
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::UnsupportedBackend { .. }));
        assert!(err.to_string().contains("brotli"));

        let result = CompressionBackendType::from_str("invalid");
        assert!(result.is_err());
    }

    #[test]
    fn test_compression_config_creation_and_builders() {
        // Test new() with Zstd
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        assert!(config.enabled);
        assert_eq!(config.backend, CompressionBackendType::Zstd);
        assert_eq!(config.compression_level, Some(3));
        assert_eq!(config.min_compression_size, 64);

        // Test new() with Lz4
        let config = CompressionConfig::new(CompressionBackendType::Lz4);
        assert!(config.enabled);
        assert_eq!(config.backend, CompressionBackendType::Lz4);
        assert_eq!(config.compression_level, Some(0));
        assert_eq!(config.min_compression_size, 64);

        // Test disabled()
        let config = CompressionConfig::disabled();
        assert!(!config.enabled);
        assert_eq!(config.backend, CompressionBackendType::Zstd);
        assert_eq!(config.compression_level, None);
        assert_eq!(config.min_compression_size, 64);

        // Test with_compression_level()
        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_compression_level(Some(10));
        assert_eq!(config.compression_level, Some(10));

        // Test with_min_compression_size()
        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_min_compression_size(1024);
        assert_eq!(config.min_compression_size, 1024);

        // Test chaining builders
        let config = CompressionConfig::new(CompressionBackendType::Lz4)
            .with_compression_level(Some(5))
            .with_min_compression_size(512);
        assert_eq!(config.compression_level, Some(5));
        assert_eq!(config.min_compression_size, 512);

        // Test default()
        let config = CompressionConfig::default();
        assert!(!config.enabled);
    }

    #[test]
    fn test_compression_config_validation() {
        // Test validation success
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        assert!(config.validate().is_ok());

        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_min_compression_size(MIN_COMPRESSED_SIZE);
        assert!(config.validate().is_ok());

        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_min_compression_size(1024);
        assert!(config.validate().is_ok());

        // Test validation failure - min_compression_size too small
        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_min_compression_size(MIN_COMPRESSED_SIZE - 1);
        let result = config.validate();
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::InvalidConfiguration { .. }));
        assert!(err.to_string().contains("min_compression_size"));

        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_min_compression_size(0);
        assert!(config.validate().is_err());
    }

    #[test]
    fn test_compression_config_should_compress() {
        // Test enabled config
        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_min_compression_size(100);

        assert!(!config.should_compress(50)); // Below threshold
        assert!(!config.should_compress(99)); // Below threshold
        assert!(config.should_compress(100)); // At threshold
        assert!(config.should_compress(200)); // Above threshold

        // Test disabled config
        let config = CompressionConfig::disabled();
        assert!(!config.should_compress(0));
        assert!(!config.should_compress(1000));
        assert!(!config.should_compress(1_000_000));
    }

    #[test]
    fn test_command_compression_behavior() {
        // Test description()
        assert_eq!(
            CommandCompressionBehavior::CompressValues.description(),
            "Compress values before sending to server"
        );
        assert_eq!(
            CommandCompressionBehavior::DecompressValues.description(),
            "Decompress values after receiving from server"
        );
        assert_eq!(
            CommandCompressionBehavior::NoCompression.description(),
            "No compression processing required"
        );

        // Test Display
        assert_eq!(
            CommandCompressionBehavior::CompressValues.to_string(),
            "CompressValues"
        );
        assert_eq!(
            CommandCompressionBehavior::DecompressValues.to_string(),
            "DecompressValues"
        );
        assert_eq!(
            CommandCompressionBehavior::NoCompression.to_string(),
            "NoCompression"
        );

        // Test compression_behavior() method
        assert_eq!(
            RequestType::Set.compression_behavior(),
            CommandCompressionBehavior::CompressValues
        );
        assert_eq!(
            RequestType::Get.compression_behavior(),
            CommandCompressionBehavior::DecompressValues
        );
        assert_eq!(
            RequestType::Del.compression_behavior(),
            CommandCompressionBehavior::NoCompression
        );
        assert_eq!(
            RequestType::Ping.compression_behavior(),
            CommandCompressionBehavior::NoCompression
        );
    }

    #[test]
    fn test_header_operations() {
        // Test create_header() with current version
        let header_zstd = create_header(CompressionBackendType::Zstd.backend_id());
        assert_eq!(header_zstd.len(), HEADER_SIZE);
        assert_eq!(&header_zstd[0..3], &MAGIC_PREFIX);
        assert_eq!(header_zstd[HEADER_VERSION_INDEX], CURRENT_VERSION);
        assert_eq!(
            header_zstd[HEADER_BACKEND_INDEX],
            CompressionBackendType::Zstd.backend_id()
        );

        let header_lz4 = create_header(CompressionBackendType::Lz4.backend_id());
        assert_eq!(header_lz4.len(), HEADER_SIZE);
        assert_eq!(&header_lz4[0..3], &MAGIC_PREFIX);
        assert_eq!(header_lz4[HEADER_VERSION_INDEX], CURRENT_VERSION);
        assert_eq!(
            header_lz4[HEADER_BACKEND_INDEX],
            CompressionBackendType::Lz4.backend_id()
        );

        // Test create_header_with_version()
        let header_v1 = create_header_with_version(
            CompressionBackendType::Zstd.backend_id(),
            CURRENT_VERSION + 1,
        );
        assert_eq!(&header_v1[0..3], &MAGIC_PREFIX);
        assert_eq!(header_v1[HEADER_VERSION_INDEX], CURRENT_VERSION + 1);
        assert_eq!(
            header_v1[HEADER_BACKEND_INDEX],
            CompressionBackendType::Zstd.backend_id()
        );

        // Test has_magic_header() - valid
        let mut valid_data = MAGIC_PREFIX.to_vec();
        valid_data.extend_from_slice(&[
            CURRENT_VERSION,
            CompressionBackendType::Zstd.backend_id(),
            0x00,
        ]);
        assert!(has_magic_header(&valid_data));

        // Test has_magic_header() - different version should still be valid
        let mut valid_data_v1 = MAGIC_PREFIX.to_vec();
        valid_data_v1.extend_from_slice(&[
            CURRENT_VERSION + 1,
            CompressionBackendType::Zstd.backend_id(),
            0x00,
        ]);
        assert!(has_magic_header(&valid_data_v1));

        let too_short = [0x00, 0x01, 0x02, 0x03];
        assert!(!has_magic_header(&too_short));

        let mut wrong_magic = MAGIC_PREFIX.to_vec();
        wrong_magic[2] = 0x45; // Change last byte of magic prefix
        wrong_magic.extend_from_slice(&[CURRENT_VERSION, 0x01]);
        assert!(!has_magic_header(&wrong_magic));

        // Test extract_version()
        let mut data_v0 = MAGIC_PREFIX.to_vec();
        data_v0.extend_from_slice(&[
            CURRENT_VERSION,
            CompressionBackendType::Zstd.backend_id(),
            0xAA,
        ]);
        assert_eq!(extract_version(&data_v0), Some(0x00));

        let mut data_v1 = MAGIC_PREFIX.to_vec();
        data_v1.extend_from_slice(&[
            CURRENT_VERSION + 1,
            CompressionBackendType::Zstd.backend_id(),
            0xBB,
        ]);
        assert_eq!(extract_version(&data_v1), Some(0x01));

        let data_no_header = [0x00, 0x00, 0x00, 0x00, 0x01];
        assert_eq!(extract_version(&data_no_header), None);

        // Test has_current_version_header()
        assert!(has_current_version_header(&data_v0));
        assert!(!has_current_version_header(&data_v1));

        // Test extract_backend_id()
        let mut data_with_backend = MAGIC_PREFIX.to_vec();
        data_with_backend.extend_from_slice(&[
            CURRENT_VERSION,
            CompressionBackendType::Zstd.backend_id(),
            0xAA,
            0xBB,
        ]);
        assert_eq!(
            extract_backend_id(&data_with_backend),
            Some(CompressionBackendType::Zstd.backend_id())
        );

        let data_no_header2 = [0x00, 0x00, 0x00, 0x00, 0x01];
        assert_eq!(extract_backend_id(&data_no_header2), None);

        let data_too_short = [0x00, 0x01];
        assert_eq!(extract_backend_id(&data_too_short), None);
    }

    #[test]
    fn test_version_api() {
        let future_version = CURRENT_VERSION + 1;
        let max_version = 0xFF;
        // Alarm if we've reached version 255
        // Version 255 should introduce changes to support an additional versioning byte
        assert!(CURRENT_VERSION < max_version);
        // Test creating headers with different versions
        let header_v0 = create_header(CompressionBackendType::Zstd.backend_id());
        assert_eq!(extract_version(&header_v0), Some(CURRENT_VERSION));
        assert_eq!(
            extract_backend_id(&header_v0),
            Some(CompressionBackendType::Zstd.backend_id())
        );

        let header_v1 =
            create_header_with_version(CompressionBackendType::Zstd.backend_id(), future_version);
        assert_eq!(extract_version(&header_v1), Some(future_version));
        assert_eq!(
            extract_backend_id(&header_v1),
            Some(CompressionBackendType::Zstd.backend_id())
        );

        let header_v255 =
            create_header_with_version(CompressionBackendType::Zstd.backend_id(), max_version);
        assert_eq!(extract_version(&header_v255), Some(max_version));

        // Test version detection on compressed data
        use glide_core::compression::zstd_backend::ZstdBackend;
        let backend = Box::new(ZstdBackend::new());
        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_min_compression_size(10); // Lower threshold to ensure compression
        let manager = CompressionManager::new(backend, config).unwrap();

        // Use larger, highly compressible data to ensure compression happens
        let test_data = b"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        let compressed = manager.compress_value(test_data);

        // Verify the compressed data has the current version
        assert!(has_magic_header(&compressed));
        assert_eq!(extract_version(&compressed), Some(CURRENT_VERSION));
        assert!(has_current_version_header(&compressed));
        assert_eq!(
            extract_backend_id(&compressed),
            Some(CompressionBackendType::Zstd.backend_id())
        );

        // Test decompression works with current version
        let decompressed = manager.decompress_value(&compressed).unwrap();
        assert_eq!(decompressed, test_data);

        // Test forward compatibility: data with future version should still be decompressible
        // if the underlying compression format is compatible
        // Create a "future version" compressed data by manually modifying the version byte
        // This simulates data compressed with a future version of the format
        let mut future_version_compressed = compressed.to_vec();
        future_version_compressed[HEADER_VERSION_INDEX] = future_version; // Change version to future version

        // Verify the header has the future version
        assert!(has_magic_header(&future_version_compressed));
        assert_eq!(
            extract_version(&future_version_compressed),
            Some(future_version)
        );
        assert!(!has_current_version_header(&future_version_compressed));
        assert_eq!(
            extract_backend_id(&future_version_compressed),
            Some(CompressionBackendType::Zstd.backend_id())
        );

        // The decompression should still work because the underlying format is the same
        // This demonstrates forward compatibility - newer versions can be read by older clients
        let decompressed_future = manager
            .decompress_value(&future_version_compressed)
            .unwrap();
        assert_eq!(decompressed_future, test_data);
    }

    #[test]
    fn test_compression_error_traits() {
        // Test PartialEq
        let err1 = CompressionError::compression_failed("zstd", Some(3), 1024, "test");
        let err2 = CompressionError::compression_failed("zstd", Some(3), 1024, "test");
        let err3 = CompressionError::compression_failed("lz4", Some(3), 1024, "test");
        assert_eq!(err1, err2);
        assert_ne!(err1, err3);

        // Test Clone
        let err = CompressionError::unsupported_backend("brotli");
        let cloned = err.clone();
        assert_eq!(err, cloned);

        // Test Debug
        let err = CompressionError::invalid_configuration("zstd", "test error");
        let debug_str = format!("{:?}", err);
        assert!(debug_str.contains("InvalidConfiguration"));
        assert!(debug_str.contains("test error"));
        assert!(debug_str.contains("zstd"));

        // Test std::error::Error trait
        let err = CompressionError::decompression_failed("zstd", 512, "corrupted");
        let _err_trait: &dyn std::error::Error = &err;
        assert!(err.to_string().contains("corrupted"));
    }

    #[test]
    fn test_zstd_backend_compress_decompress_roundtrip() {
        use glide_core::compression::zstd_backend::ZstdBackend;

        let backend = ZstdBackend::new();
        let original_data = b"Hello, World! This is a test of Zstd compression.
            Here is a long string of repetitive dataaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Test compression
        let compressed = backend.compress(original_data, Some(3)).unwrap();
        assert!(compressed.len() > HEADER_SIZE);
        assert!(compressed.len() < original_data.len()); // Assert that it compresses

        assert!(backend.is_compressed(&compressed));
        assert_eq!(extract_backend_id(&compressed), Some(0x01));

        // Test decompression
        let decompressed = backend.decompress(&compressed, None).unwrap();
        assert_eq!(decompressed, original_data);

        // Test compression of very large data
        let very_large_data = "A".repeat(1024 * 1024 * 100);
        let compressed = backend.compress(very_large_data.as_bytes(), None).unwrap();
        assert!(compressed.len() > HEADER_SIZE);
        assert!(compressed.len() < very_large_data.len()); // Assert that it compresses
        assert!(backend.is_compressed(&compressed));
        assert_eq!(extract_backend_id(&compressed), Some(0x01));

        // Test decompression of very large data
        let decompressed = backend.decompress(&compressed, None).unwrap();
        assert_eq!(decompressed, very_large_data.as_bytes());

        // Test with default level
        let compressed_default = backend.compress(original_data, None).unwrap();
        assert!(compressed_default.len() < original_data.len()); // Assert that it compresses
        let decompressed_default = backend.decompress(&compressed_default, None).unwrap();
        assert_eq!(decompressed_default, original_data);

        // Test is_compressed on uncompressed data
        assert!(!backend.is_compressed(original_data));

        // Test backend properties
        assert_eq!(backend.backend_name(), "zstd");
        assert_eq!(backend.default_level(), Some(3));
        assert_eq!(backend.backend_id(), 0x01);
    }

    #[test]
    fn test_lz4_backend_compress_decompress_roundtrip() {
        use glide_core::compression::lz4_backend::Lz4Backend;

        let backend = Lz4Backend::new();
        let original_data = b"Hello, World! This is a test of LZ4 compression.
            Here is a long string of repetitive dataaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Test compression
        let compressed = backend.compress(original_data, None).unwrap();
        assert!(compressed.len() > HEADER_SIZE);
        assert!(compressed.len() < original_data.len()); // Assert that it compresses
        assert!(backend.is_compressed(&compressed));
        assert_eq!(extract_backend_id(&compressed), Some(0x02));

        // Test decompression
        let decompressed = backend.decompress(&compressed, None).unwrap();
        assert_eq!(decompressed, original_data);

        // Test compression of very large data
        let very_large_data = "A".repeat(1024 * 1024 * 100);
        let compressed = backend.compress(very_large_data.as_bytes(), None).unwrap();
        assert!(compressed.len() > HEADER_SIZE);
        assert!(compressed.len() < very_large_data.len()); // Assert that it compresses
        assert!(backend.is_compressed(&compressed));
        assert_eq!(extract_backend_id(&compressed), Some(0x02));

        // Test decompression of very large data
        let decompressed = backend.decompress(&compressed, None).unwrap();
        assert_eq!(decompressed, very_large_data.as_bytes());

        // Test compression with high compression level
        let compressed_hc = backend.compress(original_data, Some(9)).unwrap();
        assert!(compressed_hc.len() > HEADER_SIZE);
        assert!(backend.is_compressed(&compressed_hc));
        let decompressed_hc = backend.decompress(&compressed_hc, None).unwrap();
        assert_eq!(decompressed_hc, original_data);

        // Test compression with fast mode (negative level)
        let compressed_fast = backend.compress(original_data, Some(-5)).unwrap();
        assert!(compressed_fast.len() > HEADER_SIZE);
        assert!(backend.is_compressed(&compressed_fast));
        let decompressed_fast = backend.decompress(&compressed_fast, None).unwrap();
        assert_eq!(decompressed_fast, original_data);

        // Test that invalid compression level is rejected
        let result = backend.compress(original_data, Some(999));
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::InvalidConfiguration { .. }));

        // Test is_compressed on uncompressed data
        assert!(!backend.is_compressed(original_data));

        // Test backend properties
        assert_eq!(backend.backend_name(), "lz4");
        assert_eq!(backend.default_level(), Some(0));
        assert_eq!(backend.backend_id(), 0x02);
    }

    #[test]
    fn test_compression_manager_min_compression_scenarios() {
        use glide_core::compression::zstd_backend::ZstdBackend;
        let min_compression_size = 50;
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_min_compression_size(min_compression_size);
        let manager = CompressionManager::new(backend, config).unwrap();

        // Test empty string - should not compress"
        let empty_string = "";
        let result = manager.compress_value(empty_string.as_bytes());
        assert_eq!(result, empty_string.as_bytes());
        assert!(!manager.should_compress(empty_string.as_bytes()));

        // Test below threshold - should not compress
        let small_data = "A".repeat(min_compression_size - 1);
        let result = manager.compress_value(small_data.as_bytes());
        assert_eq!(result, small_data.as_bytes());
        assert!(!manager.should_compress(small_data.as_bytes()));

        // Test at threshold - should compress
        let threshold_data = "A".repeat(min_compression_size);
        let result = manager.compress_value(threshold_data.as_bytes());
        assert!(manager.should_compress(threshold_data.as_bytes()));
        assert!(has_magic_header(&result));

        // Test above threshold with highly compressible data
        let large_data = "A".repeat(min_compression_size * 2);
        let result = manager.compress_value(large_data.as_bytes());
        assert!(manager.should_compress(large_data.as_bytes()));
        // Compression should always result in a smaller value
        assert!(result.len() < large_data.len());
        assert!(has_magic_header(&result));

        // Test already compressed data - should not double compress
        let compressed_once = manager.compress_value(large_data.as_bytes());
        assert!(has_magic_header(&compressed_once));
        let result = manager.compress_value(&compressed_once);
        assert_eq!(result, compressed_once);

        // Test manager properties
        assert_eq!(manager.backend_name(), "zstd");
        assert!(manager.is_enabled());
    }

    #[test]
    fn test_compression_manager_decompress_scenarios() {
        use glide_core::compression::zstd_backend::ZstdBackend;

        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        let manager = CompressionManager::new(backend, config).unwrap();
        let original_data = b"Test data for decompression scenarios that is long enough to ensure compression occurs
            Here is a long string of repetitive dataaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Test decompressing compressed data
        let compressed = manager.compress_value(original_data);
        assert!(has_magic_header(&compressed), "Data should be compressed");
        assert!(compressed.len() < original_data.len()); // Assert that the data compressed
        let decompressed = manager.decompress_value(&compressed).unwrap();
        assert_eq!(decompressed, original_data);

        // Test decompressing uncompressed data - should pass through
        let uncompressed = b"not compressed";
        let result = manager.decompress_value(uncompressed).unwrap();
        assert_eq!(result, uncompressed);

        // Test try_decompress_value with valid compressed data
        let result = manager.try_decompress_value(&compressed);
        assert_eq!(result, original_data);

        // Test try_decompress_value with uncompressed data
        let result = manager.try_decompress_value(uncompressed);
        assert_eq!(result, uncompressed);

        // Test try_decompress_value with corrupted data (graceful fallback)
        let mut corrupted = compressed.into_owned();
        corrupted[HEADER_SIZE] = 0xFF; // Corrupt the compressed data
        let result = manager.try_decompress_value(&corrupted);
        assert_eq!(result, corrupted); // Should return original on error

        // Validate that decompress_value would fail on the same corrupted data
        let decompress_result = manager.decompress_value(&corrupted);
        assert!(decompress_result.is_err());
        let err = decompress_result.unwrap_err();
        assert!(matches!(err, CompressionError::DecompressionFailed { .. }));
        assert!(err.to_string().contains("ZSTD"));
    }

    #[test]
    fn test_cross_backend_decompression() {
        use glide_core::compression::lz4_backend::Lz4Backend;
        use glide_core::compression::zstd_backend::ZstdBackend;

        let original_data = b"Test data for cross-backend decompression that is long enough to ensure compression occurs
            Here is a long string of repetitive dataaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Compress with Zstd
        let zstd_backend = Box::new(ZstdBackend::new());
        let zstd_config = CompressionConfig::new(CompressionBackendType::Zstd);
        let zstd_manager = CompressionManager::new(zstd_backend, zstd_config).unwrap();
        let zstd_compressed = zstd_manager.compress_value(original_data);
        assert!(has_magic_header(&zstd_compressed));
        assert_eq!(extract_backend_id(&zstd_compressed), Some(0x01));

        // Compress with Lz4
        let lz4_backend = Box::new(Lz4Backend::new());
        let lz4_config = CompressionConfig::new(CompressionBackendType::Lz4);
        let lz4_manager = CompressionManager::new(lz4_backend, lz4_config).unwrap();
        let lz4_compressed = lz4_manager.compress_value(original_data);
        assert!(has_magic_header(&lz4_compressed));
        assert_eq!(extract_backend_id(&lz4_compressed), Some(0x02));

        // Decompress Zstd-compressed data with Lz4 manager (should work via dynamic routing)
        let decompressed_by_lz4 = lz4_manager.decompress_value(&zstd_compressed).unwrap();
        assert_eq!(decompressed_by_lz4, original_data);

        // Decompress Lz4-compressed data with Zstd manager (should work via dynamic routing)
        let decompressed_by_zstd = zstd_manager.decompress_value(&lz4_compressed).unwrap();
        assert_eq!(decompressed_by_zstd, original_data);

        // Test try_decompress_value with cross-backend data
        let result = lz4_manager.try_decompress_value(&zstd_compressed);
        assert_eq!(result, original_data);

        let result = zstd_manager.try_decompress_value(&lz4_compressed);
        assert_eq!(result, original_data);
    }

    #[test]
    fn test_unsupported_backend_fallback() {
        use glide_core::compression::zstd_backend::ZstdBackend;

        // Create data with an unsupported backend ID (0xFF)
        let mut unsupported_data = create_header(0xFF).to_vec();
        unsupported_data.extend_from_slice(b"some compressed data");

        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        let manager = CompressionManager::new(backend, config).unwrap();

        // decompress_value should return an UnsupportedBackend error
        let result = manager.decompress_value(&unsupported_data);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::UnsupportedBackend { .. }));
        assert!(err.backend().contains("0xff"));
        assert!(err.to_string().contains("0xff"));

        // try_decompress_value should gracefully return the original compressed data
        let result = manager.try_decompress_value(&unsupported_data);
        assert_eq!(result, unsupported_data);
    }

    #[test]
    fn test_incompatible_command_error() {
        // Test IncompatibleCommand error creation and display
        let err = CompressionError::incompatible_command(
            "APPEND",
            "APPEND modifies string data on the server",
        );
        assert!(matches!(err, CompressionError::IncompatibleCommand { .. }));
        assert!(err.is_incompatible_command());
        assert!(err.to_string().contains("APPEND"));
        assert!(err.to_string().contains("incompatible with compression"));
        assert!(err.to_string().contains("modifies string data"));
        assert_eq!(err.backend(), ""); // IncompatibleCommand has no backend

        // Test Clone and PartialEq
        let err2 = err.clone();
        assert_eq!(err, err2);

        let err3 = CompressionError::incompatible_command("INCR", "expects numeric string");
        assert_ne!(err, err3);

        // Test is_incompatible_command returns false for other error types
        let compression_err =
            CompressionError::compression_failed("zstd", Some(3), 1000, "test error");
        assert!(!compression_err.is_incompatible_command());

        let decompression_err = CompressionError::decompression_failed("lz4", 500, "test error");
        assert!(!decompression_err.is_incompatible_command());

        let unsupported_err = CompressionError::unsupported_backend("brotli");
        assert!(!unsupported_err.is_incompatible_command());

        let config_err = CompressionError::invalid_configuration("zstd", "invalid level");
        assert!(!config_err.is_incompatible_command());
    }

    #[test]
    fn test_compression_incompatibility_reason() {
        // Test string manipulation commands
        assert!(
            RequestType::Append
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::GetRange
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::SetRange
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::Strlen
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::LCS
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::Substr
                .compression_incompatibility_reason()
                .is_some()
        );

        // Test numeric operations
        assert!(
            RequestType::Incr
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::IncrBy
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::IncrByFloat
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::Decr
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::DecrBy
                .compression_incompatibility_reason()
                .is_some()
        );

        // Test bit operations
        assert!(
            RequestType::GetBit
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::SetBit
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::BitCount
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::BitPos
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::BitField
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::BitFieldReadOnly
                .compression_incompatibility_reason()
                .is_some()
        );
        assert!(
            RequestType::BitOp
                .compression_incompatibility_reason()
                .is_some()
        );

        // Test compatible commands
        assert!(
            RequestType::Set
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::Get
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::MSet
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::MGet
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::SetEx
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::GetEx
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::Del
                .compression_incompatibility_reason()
                .is_none()
        );
        assert!(
            RequestType::Ping
                .compression_incompatibility_reason()
                .is_none()
        );
    }

    #[test]
    fn test_validate_command_compression_compatibility() {
        use glide_core::compression::zstd_backend::ZstdBackend;

        // Create an enabled compression manager
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        let manager = CompressionManager::new(backend, config).unwrap();

        // Test incompatible commands return errors
        let result =
            validate_command_compression_compatibility(RequestType::Append, Some(&manager));
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::IncompatibleCommand { .. }));
        assert!(err.to_string().contains("APPEND"));

        let result = validate_command_compression_compatibility(RequestType::Incr, Some(&manager));
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::IncompatibleCommand { .. }));
        assert!(err.to_string().contains("INCR"));

        let result =
            validate_command_compression_compatibility(RequestType::BitCount, Some(&manager));
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::IncompatibleCommand { .. }));
        assert!(err.to_string().contains("BITCOUNT"));

        // Test compatible commands succeed
        let result = validate_command_compression_compatibility(RequestType::Set, Some(&manager));
        assert!(result.is_ok());

        let result = validate_command_compression_compatibility(RequestType::Get, Some(&manager));
        assert!(result.is_ok());

        let result = validate_command_compression_compatibility(RequestType::Del, Some(&manager));
        assert!(result.is_ok());

        // Test with no compression manager (should always succeed)
        let result = validate_command_compression_compatibility(RequestType::Append, None);
        assert!(result.is_ok());

        // Test with disabled compression manager (should always succeed)
        let backend = Box::new(ZstdBackend::new());
        // Need to create a manager with matching backend for disabled config
        let disabled_config = CompressionConfig {
            enabled: false,
            backend: CompressionBackendType::Zstd,
            compression_level: None,
            min_compression_size: 64,
            max_decompressed_size: Some(DEFAULT_MAX_DECOMPRESSED_SIZE),
        };
        let disabled_manager = CompressionManager::new(backend, disabled_config).unwrap();
        let result = validate_command_compression_compatibility(
            RequestType::Append,
            Some(&disabled_manager),
        );
        assert!(result.is_ok());
    }

    #[test]
    fn test_command_name() {
        // Test incompatible commands have names
        assert_eq!(RequestType::Append.command_name(), Some("APPEND"));
        assert_eq!(RequestType::GetRange.command_name(), Some("GETRANGE"));
        assert_eq!(RequestType::SetRange.command_name(), Some("SETRANGE"));
        assert_eq!(RequestType::Strlen.command_name(), Some("STRLEN"));
        assert_eq!(RequestType::LCS.command_name(), Some("LCS"));
        assert_eq!(RequestType::Substr.command_name(), Some("SUBSTR"));
        assert_eq!(RequestType::Incr.command_name(), Some("INCR"));
        assert_eq!(RequestType::IncrBy.command_name(), Some("INCRBY"));
        assert_eq!(RequestType::IncrByFloat.command_name(), Some("INCRBYFLOAT"));
        assert_eq!(RequestType::Decr.command_name(), Some("DECR"));
        assert_eq!(RequestType::DecrBy.command_name(), Some("DECRBY"));
        assert_eq!(RequestType::GetBit.command_name(), Some("GETBIT"));
        assert_eq!(RequestType::SetBit.command_name(), Some("SETBIT"));
        assert_eq!(RequestType::BitCount.command_name(), Some("BITCOUNT"));
        assert_eq!(RequestType::BitPos.command_name(), Some("BITPOS"));
        assert_eq!(RequestType::BitField.command_name(), Some("BITFIELD"));
        assert_eq!(
            RequestType::BitFieldReadOnly.command_name(),
            Some("BITFIELD_RO")
        );
        assert_eq!(RequestType::BitOp.command_name(), Some("BITOP"));

        // Test compatible commands have names
        assert_eq!(RequestType::Set.command_name(), Some("SET"));
        assert_eq!(RequestType::Get.command_name(), Some("GET"));
        assert_eq!(RequestType::MSet.command_name(), Some("MSET"));
        assert_eq!(RequestType::MGet.command_name(), Some("MGET"));

        // Test invalid request has no name
        assert_eq!(RequestType::InvalidRequest.command_name(), None);
    }

    #[test]
    fn test_from_command_name() {
        // Test commands that support compression
        assert!(matches!(
            RequestType::from_command_name("SET"),
            Some(RequestType::Set)
        ));
        assert!(matches!(
            RequestType::from_command_name("MSET"),
            Some(RequestType::MSet)
        ));
        assert!(matches!(
            RequestType::from_command_name("MSETNX"),
            Some(RequestType::MSetNX)
        ));
        assert!(matches!(
            RequestType::from_command_name("SETEX"),
            Some(RequestType::SetEx)
        ));
        assert!(matches!(
            RequestType::from_command_name("PSETEX"),
            Some(RequestType::PSetEx)
        ));
        assert!(matches!(
            RequestType::from_command_name("SETNX"),
            Some(RequestType::SetNX)
        ));
        assert!(matches!(
            RequestType::from_command_name("GET"),
            Some(RequestType::Get)
        ));
        assert!(matches!(
            RequestType::from_command_name("MGET"),
            Some(RequestType::MGet)
        ));
        assert!(matches!(
            RequestType::from_command_name("GETEX"),
            Some(RequestType::GetEx)
        ));
        assert!(matches!(
            RequestType::from_command_name("GETDEL"),
            Some(RequestType::GetDel)
        ));
        assert!(matches!(
            RequestType::from_command_name("GETSET"),
            Some(RequestType::GetSet)
        ));

        // Test incompatible commands - string manipulation
        assert!(matches!(
            RequestType::from_command_name("APPEND"),
            Some(RequestType::Append)
        ));
        assert!(matches!(
            RequestType::from_command_name("GETRANGE"),
            Some(RequestType::GetRange)
        ));
        assert!(matches!(
            RequestType::from_command_name("SETRANGE"),
            Some(RequestType::SetRange)
        ));
        assert!(matches!(
            RequestType::from_command_name("STRLEN"),
            Some(RequestType::Strlen)
        ));
        assert!(matches!(
            RequestType::from_command_name("LCS"),
            Some(RequestType::LCS)
        ));
        assert!(matches!(
            RequestType::from_command_name("SUBSTR"),
            Some(RequestType::Substr)
        ));

        // Test incompatible commands - numeric operations
        assert!(matches!(
            RequestType::from_command_name("INCR"),
            Some(RequestType::Incr)
        ));
        assert!(matches!(
            RequestType::from_command_name("INCRBY"),
            Some(RequestType::IncrBy)
        ));
        assert!(matches!(
            RequestType::from_command_name("INCRBYFLOAT"),
            Some(RequestType::IncrByFloat)
        ));
        assert!(matches!(
            RequestType::from_command_name("DECR"),
            Some(RequestType::Decr)
        ));
        assert!(matches!(
            RequestType::from_command_name("DECRBY"),
            Some(RequestType::DecrBy)
        ));

        // Test incompatible commands - bit operations
        assert!(matches!(
            RequestType::from_command_name("GETBIT"),
            Some(RequestType::GetBit)
        ));
        assert!(matches!(
            RequestType::from_command_name("SETBIT"),
            Some(RequestType::SetBit)
        ));
        assert!(matches!(
            RequestType::from_command_name("BITCOUNT"),
            Some(RequestType::BitCount)
        ));
        assert!(matches!(
            RequestType::from_command_name("BITPOS"),
            Some(RequestType::BitPos)
        ));
        assert!(matches!(
            RequestType::from_command_name("BITFIELD"),
            Some(RequestType::BitField)
        ));
        assert!(matches!(
            RequestType::from_command_name("BITFIELD_RO"),
            Some(RequestType::BitFieldReadOnly)
        ));
        assert!(matches!(
            RequestType::from_command_name("BITOP"),
            Some(RequestType::BitOp)
        ));

        // Test case insensitivity
        assert!(matches!(
            RequestType::from_command_name("set"),
            Some(RequestType::Set)
        ));
        assert!(matches!(
            RequestType::from_command_name("Set"),
            Some(RequestType::Set)
        ));
        assert!(matches!(
            RequestType::from_command_name("sEt"),
            Some(RequestType::Set)
        ));
        assert!(matches!(
            RequestType::from_command_name("append"),
            Some(RequestType::Append)
        ));
        assert!(matches!(
            RequestType::from_command_name("APPEND"),
            Some(RequestType::Append)
        ));

        // Test unknown commands return None
        assert!(RequestType::from_command_name("UNKNOWN").is_none());
        assert!(RequestType::from_command_name("HSET").is_none());
        assert!(RequestType::from_command_name("LPUSH").is_none());
        assert!(RequestType::from_command_name("ZADD").is_none());
        assert!(RequestType::from_command_name("").is_none());
    }

    #[test]
    fn test_decompress_batch_response() {
        use glide_core::compression::decompress_batch_response;
        use glide_core::compression::zstd_backend::ZstdBackend;
        use redis::Value;

        // Create an enabled compression manager
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        let manager = CompressionManager::new(backend, config).unwrap();

        // Test with array of uncompressed values (should return as-is)
        let array_response = Value::Array(vec![
            Value::BulkString(b"value1".to_vec().into()),
            Value::BulkString(b"value2".to_vec().into()),
            Value::Nil,
        ]);
        let result = decompress_batch_response(array_response.clone(), &manager);
        assert!(result.is_ok());
        let decompressed = result.unwrap();
        match decompressed {
            Value::Array(values) => {
                assert_eq!(values.len(), 3);
                assert_eq!(values[0], Value::BulkString(b"value1".to_vec().into()));
                assert_eq!(values[1], Value::BulkString(b"value2".to_vec().into()));
                assert_eq!(values[2], Value::Nil);
            }
            _ => panic!("Expected array response"),
        }

        // Test with array containing compressed values
        let test_data = b"test data for compression that is long enough to be compressed";
        let compressed = manager.compress_value(test_data).into_owned();
        let array_with_compressed = Value::Array(vec![
            Value::BulkString(compressed.clone().into()),
            Value::BulkString(b"uncompressed".to_vec().into()),
        ]);
        let result = decompress_batch_response(array_with_compressed, &manager);
        assert!(result.is_ok());
        let decompressed = result.unwrap();
        match decompressed {
            Value::Array(values) => {
                assert_eq!(values.len(), 2);
                // First value should be decompressed
                assert_eq!(values[0], Value::BulkString(test_data.to_vec().into()));
                // Second value should remain unchanged
                assert_eq!(
                    values[1],
                    Value::BulkString(b"uncompressed".to_vec().into())
                );
            }
            _ => panic!("Expected array response"),
        }

        // Test with non-array response (single value)
        let single_response = Value::BulkString(compressed.clone().into());
        let result = decompress_batch_response(single_response, &manager);
        assert!(result.is_ok());
        let decompressed = result.unwrap();
        assert_eq!(decompressed, Value::BulkString(test_data.to_vec().into()));

        // Test with disabled compression manager
        let disabled_config = CompressionConfig {
            enabled: false,
            backend: CompressionBackendType::Zstd,
            compression_level: None,
            min_compression_size: 64,
            max_decompressed_size: Some(DEFAULT_MAX_DECOMPRESSED_SIZE),
        };
        let disabled_backend = Box::new(ZstdBackend::new());
        let disabled_manager = CompressionManager::new(disabled_backend, disabled_config).unwrap();

        // With disabled manager, compressed data should NOT be decompressed
        let array_with_compressed =
            Value::Array(vec![Value::BulkString(compressed.clone().into())]);
        let result = decompress_batch_response(array_with_compressed, &disabled_manager);
        assert!(result.is_ok());
        let not_decompressed = result.unwrap();
        match not_decompressed {
            Value::Array(values) => {
                assert_eq!(values.len(), 1);
                // Value should remain compressed since manager is disabled
                assert_eq!(values[0], Value::BulkString(compressed.into()));
            }
            _ => panic!("Expected array response"),
        }
    }

    #[test]
    fn test_decompress_batch_response_nested_arrays() {
        use glide_core::compression::decompress_batch_response;
        use glide_core::compression::zstd_backend::ZstdBackend;
        use redis::Value;

        // Create an enabled compression manager
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        let manager = CompressionManager::new(backend, config).unwrap();

        // Create compressed test data
        let test_data1 =
            b"test data for compression that is long enough to be compressed - value 1";
        let test_data2 =
            b"test data for compression that is long enough to be compressed - value 2";
        let test_data3 =
            b"test data for compression that is long enough to be compressed - value 3";
        let compressed1 = manager.compress_value(test_data1).into_owned();
        let compressed2 = manager.compress_value(test_data2).into_owned();
        let compressed3 = manager.compress_value(test_data3).into_owned();

        // Simulate a batch response with MSET result (OK) followed by MGET result (nested array)
        // This is what happens when you do: batch.mset(...); batch.mget(...); exec(batch);
        let batch_response = Value::Array(vec![
            // MSET result
            Value::SimpleString("OK".to_string()),
            // MGET result - nested array with compressed values
            Value::Array(vec![
                Value::BulkString(compressed1.clone().into()),
                Value::BulkString(compressed2.clone().into()),
                Value::BulkString(compressed3.clone().into()),
            ]),
        ]);

        let result = decompress_batch_response(batch_response, &manager);
        assert!(result.is_ok());
        let decompressed = result.unwrap();

        match decompressed {
            Value::Array(values) => {
                assert_eq!(values.len(), 2);

                // First value should be MSET result (OK)
                assert_eq!(values[0], Value::SimpleString("OK".to_string()));

                // Second value should be decompressed MGET result (nested array)
                match &values[1] {
                    Value::Array(mget_values) => {
                        assert_eq!(mget_values.len(), 3);
                        assert_eq!(
                            mget_values[0],
                            Value::BulkString(test_data1.to_vec().into())
                        );
                        assert_eq!(
                            mget_values[1],
                            Value::BulkString(test_data2.to_vec().into())
                        );
                        assert_eq!(
                            mget_values[2],
                            Value::BulkString(test_data3.to_vec().into())
                        );
                    }
                    _ => panic!("Expected nested array for MGET result"),
                }
            }
            _ => panic!("Expected array response"),
        }

        // Test with deeply nested arrays (edge case)
        let deeply_nested = Value::Array(vec![Value::Array(vec![Value::Array(vec![
            Value::BulkString(compressed1.clone().into()),
        ])])]);

        let result = decompress_batch_response(deeply_nested, &manager);
        assert!(result.is_ok());
        let decompressed = result.unwrap();

        // Verify the deeply nested value was decompressed
        match decompressed {
            Value::Array(level1) => match &level1[0] {
                Value::Array(level2) => match &level2[0] {
                    Value::Array(level3) => {
                        assert_eq!(level3[0], Value::BulkString(test_data1.to_vec().into()));
                    }
                    _ => panic!("Expected array at level 3"),
                },
                _ => panic!("Expected array at level 2"),
            },
            _ => panic!("Expected array at level 1"),
        }

        // Test with mixed nested and non-nested values
        let mixed_response = Value::Array(vec![
            Value::BulkString(compressed1.clone().into()), // Direct compressed value
            Value::Array(vec![
                // Nested array with compressed values
                Value::BulkString(compressed2.clone().into()),
                Value::Nil,
            ]),
            Value::SimpleString("OK".to_string()), // Simple string
        ]);

        let result = decompress_batch_response(mixed_response, &manager);
        assert!(result.is_ok());
        let decompressed = result.unwrap();

        match decompressed {
            Value::Array(values) => {
                assert_eq!(values.len(), 3);
                // First value should be decompressed
                assert_eq!(values[0], Value::BulkString(test_data1.to_vec().into()));
                // Second value should be nested array with decompressed values
                match &values[1] {
                    Value::Array(nested) => {
                        assert_eq!(nested.len(), 2);
                        assert_eq!(nested[0], Value::BulkString(test_data2.to_vec().into()));
                        assert_eq!(nested[1], Value::Nil);
                    }
                    _ => panic!("Expected nested array"),
                }
                // Third value should be unchanged
                assert_eq!(values[2], Value::SimpleString("OK".to_string()));
            }
            _ => panic!("Expected array response"),
        }
    }

    #[test]
    fn test_max_decompressed_size_limit() {
        use glide_core::compression::lz4_backend::Lz4Backend;
        use glide_core::compression::zstd_backend::ZstdBackend;

        // Test ZSTD with size limit
        let zstd_backend = ZstdBackend::new();
        let test_data = b"test data for compression that is long enough to be compressed - value 1";
        let compressed = zstd_backend.compress(test_data, Some(3)).unwrap();

        // Decompression should succeed with a large enough limit
        let result = zstd_backend.decompress(&compressed, Some(1024));
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), test_data);

        // Decompression should fail with a too-small limit
        let result = zstd_backend.decompress(&compressed, Some(10));
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::SizeLimitExceeded { .. }));

        // Test LZ4 with size limit
        let lz4_backend = Lz4Backend::new();
        let compressed = lz4_backend.compress(test_data, None).unwrap();

        // Decompression should succeed with a large enough limit
        let result = lz4_backend.decompress(&compressed, Some(1024));
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), test_data);

        // Decompression should fail with a too-small limit (LZ4 validates before allocation)
        let result = lz4_backend.decompress(&compressed, Some(10));
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::SizeLimitExceeded { .. }));

        // Test with None limit (no limit) - use the correct backend (LZ4)
        let result = lz4_backend.decompress(&compressed, None);
        assert!(result.is_ok());
    }

    #[test]
    fn test_compression_config_max_decompressed_size() {
        // Test default config has max_decompressed_size set
        let config = CompressionConfig::new(CompressionBackendType::Zstd);
        assert_eq!(
            config.max_decompressed_size,
            Some(DEFAULT_MAX_DECOMPRESSED_SIZE)
        );

        // Test disabled config also has max_decompressed_size set
        let config = CompressionConfig::disabled();
        assert_eq!(
            config.max_decompressed_size,
            Some(DEFAULT_MAX_DECOMPRESSED_SIZE)
        );

        // Test with_max_decompressed_size builder
        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_max_decompressed_size(Some(1024 * 1024)); // 1MB
        assert_eq!(config.max_decompressed_size, Some(1024 * 1024));

        // Test with_max_decompressed_size builder with None (no limit - internal use only)
        let config =
            CompressionConfig::new(CompressionBackendType::Zstd).with_max_decompressed_size(None);
        assert_eq!(config.max_decompressed_size, None);
    }

    #[test]
    fn test_compression_manager_respects_max_decompressed_size() {
        use glide_core::compression::zstd_backend::ZstdBackend;

        // Create test data that's large enough to be compressed
        let test_data = "A".repeat(200); // Large enough to exceed min_compression_size
        let test_data_bytes = test_data.as_bytes();

        // Create a manager with a small max_decompressed_size
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_min_compression_size(64)
            .with_max_decompressed_size(Some(50)); // Very small limit
        let manager = CompressionManager::new(backend, config).unwrap();

        // Compress some data
        let compressed = manager.compress_value(test_data_bytes);
        // Verify it was actually compressed
        assert!(has_magic_header(&compressed), "Data should be compressed");

        // Decompression should fail because the decompressed size exceeds the limit
        let result = manager.decompress_value(&compressed);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::SizeLimitExceeded { .. }));

        // Create a manager with a larger limit
        let backend = Box::new(ZstdBackend::new());
        let config = CompressionConfig::new(CompressionBackendType::Zstd)
            .with_min_compression_size(64)
            .with_max_decompressed_size(Some(1024)); // Larger limit
        let manager = CompressionManager::new(backend, config).unwrap();

        // Decompression should succeed
        let result = manager.decompress_value(&compressed);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), test_data_bytes);
    }
}
