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
            int minCompressionSize) {
        this.enabled = enabled;
        this.backend = backend;
        this.compressionLevel = compressionLevel;
        this.minCompressionSize = minCompressionSize;
        validate();
    }
}
