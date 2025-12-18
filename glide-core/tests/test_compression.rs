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
        let result = backend.decompress(&corrupted_data);
        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(matches!(err, CompressionError::DecompressionFailed { .. }));
        assert!(err.to_string().contains("ZSTD"));
        assert!(err.to_string().contains("decoding failed"));
        assert_eq!(err.backend(), "zstd");

        // Scenario 6: Decompressing data without proper header (triggers DecompressionFailed)
        let backend = Lz4Backend::new();
        let invalid_data = b"not compressed data";
        let result = backend.decompress(invalid_data);
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
        let decompressed = backend.decompress(&compressed).unwrap();
        assert_eq!(decompressed, original_data);

        // Test compression of very large data
        let very_large_data = "A".repeat(1024 * 1024 * 100);
        let compressed = backend.compress(very_large_data.as_bytes(), None).unwrap();
        assert!(compressed.len() > HEADER_SIZE);
        assert!(compressed.len() < very_large_data.len()); // Assert that it compresses
        assert!(backend.is_compressed(&compressed));
        assert_eq!(extract_backend_id(&compressed), Some(0x01));

        // Test decompression of very large data
        let decompressed = backend.decompress(&compressed).unwrap();
        assert_eq!(decompressed, very_large_data.as_bytes());

        // Test with default level
        let compressed_default = backend.compress(original_data, None).unwrap();
        assert!(compressed_default.len() < original_data.len()); // Assert that it compresses
        let decompressed_default = backend.decompress(&compressed_default).unwrap();
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
        let decompressed = backend.decompress(&compressed).unwrap();
        assert_eq!(decompressed, original_data);

        // Test compression of very large data
        let very_large_data = "A".repeat(1024 * 1024 * 100);
        let compressed = backend.compress(very_large_data.as_bytes(), None).unwrap();
        assert!(compressed.len() > HEADER_SIZE);
        assert!(compressed.len() < very_large_data.len()); // Assert that it compresses
        assert!(backend.is_compressed(&compressed));
        assert_eq!(extract_backend_id(&compressed), Some(0x02));

        // Test decompression of very large data
        let decompressed = backend.decompress(&compressed).unwrap();
        assert_eq!(decompressed, very_large_data.as_bytes());

        // Test compression with high compression level
        let compressed_hc = backend.compress(original_data, Some(9)).unwrap();
        assert!(compressed_hc.len() > HEADER_SIZE);
        assert!(backend.is_compressed(&compressed_hc));
        let decompressed_hc = backend.decompress(&compressed_hc).unwrap();
        assert_eq!(decompressed_hc, original_data);

        // Test compression with fast mode (negative level)
        let compressed_fast = backend.compress(original_data, Some(-5)).unwrap();
        assert!(compressed_fast.len() > HEADER_SIZE);
        assert!(backend.is_compressed(&compressed_fast));
        let decompressed_fast = backend.decompress(&compressed_fast).unwrap();
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
}
