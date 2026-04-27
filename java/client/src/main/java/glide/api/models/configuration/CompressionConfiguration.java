/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.models.exceptions.ConfigurationError;
import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for automatic transparent compression of values.
 *
 * <p>When enabled, values sent to the server will be compressed using the specified backend if they
 * meet the minimum size threshold. Compressed values are automatically decompressed on retrieval.
 *
 * <p><b>Note:</b> This feature is experimental. Compressed data is backwards-compatible with
 * existing uncompressed data, but will not be readable by older clients unaware of the compression
 * format.
 *
 * <p>Currently, compression is only applied to GET and SET commands.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CompressionConfiguration compression = CompressionConfiguration.builder()
 *     .enabled(true)
 *     .backend(CompressionBackend.ZSTD)
 *     .compressionLevel(3)
 *     .minCompressionSize(128)
 *     .maxDecompressedSize(1024 * 1024 * 100) // 100MB limit
 *     .build();
 *
 * GlideClientConfiguration config = GlideClientConfiguration.builder()
 *     .address(NodeAddress.builder().host("localhost").port(6379).build())
 *     .compressionConfiguration(compression)
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class CompressionConfiguration {

    /**
     * Absolute minimum allowed value for {@link #minCompressionSize}. Corresponds to the compression
     * header size (5 bytes) plus 1 byte of payload. Must be kept in sync with the Rust core's
     * MIN_COMPRESSED_SIZE.
     */
    public static final int MIN_COMPRESSION_SIZE = 6;

    /** Default threshold below which values will not be compressed. */
    public static final int DEFAULT_MIN_COMPRESSION_SIZE = 64;

    /** Default maximum decompressed size (512MB, matching Valkey's proto-max-bulk-len). */
    public static final long DEFAULT_MAX_DECOMPRESSED_SIZE = 512L * 1024 * 1024;

    /** Whether compression is enabled. Defaults to {@code true}. */
    @Builder.Default private final boolean enabled = true;

    /** The compression backend to use. Defaults to {@link CompressionBackend#ZSTD}. */
    @Builder.Default private final CompressionBackend backend = CompressionBackend.ZSTD;

    /**
     * The compression level to use. If {@code null}, the backend's default level is used. Valid
     * ranges are backend-specific:
     *
     * <ul>
     *   <li>ZSTD: -131072 to 22 (default 3)
     *   <li>LZ4: -128 to 12 (default 0)
     * </ul>
     *
     * <p>Compression level validation is performed by the Rust core.
     */
    private final Integer compressionLevel;

    /**
     * Minimum size in bytes for values to be compressed. Values smaller than this will not be
     * compressed. Must be at least {@link #MIN_COMPRESSION_SIZE} (6 bytes). Defaults to {@link
     * #DEFAULT_MIN_COMPRESSION_SIZE} (64 bytes).
     */
    @Builder.Default private final int minCompressionSize = DEFAULT_MIN_COMPRESSION_SIZE;

    /**
     * Maximum allowed size in bytes for decompressed data. This limit prevents decompression bombs
     * (maliciously crafted compressed data that expands to huge sizes). If {@code null}, the Rust
     * default (512MB) is used. Defaults to {@code null} (use Rust default).
     */
    @Builder.Default private final Long maxDecompressedSize = null;

    /**
     * Validates the configuration parameters.
     *
     * @throws ConfigurationError if any parameter is invalid.
     */
    public void validate() {
        if (minCompressionSize < MIN_COMPRESSION_SIZE) {
            throw new ConfigurationError(
                    "minCompressionSize must be at least "
                            + MIN_COMPRESSION_SIZE
                            + " bytes, got "
                            + minCompressionSize);
        }
        if (maxDecompressedSize != null && maxDecompressedSize <= 0) {
            throw new ConfigurationError(
                    "maxDecompressedSize must be positive, got " + maxDecompressedSize);
        }
    }

    /**
     * Creates a new CompressionConfiguration. Validates parameters on construction.
     *
     * <p>Use {@link #builder()} to create instances.
     */
    CompressionConfiguration(
            boolean enabled,
            CompressionBackend backend,
            Integer compressionLevel,
            int minCompressionSize,
            Long maxDecompressedSize) {
        this.enabled = enabled;
        this.backend = backend;
        this.compressionLevel = compressionLevel;
        this.minCompressionSize = minCompressionSize;
        this.maxDecompressedSize = maxDecompressedSize;
        validate();
    }
}
