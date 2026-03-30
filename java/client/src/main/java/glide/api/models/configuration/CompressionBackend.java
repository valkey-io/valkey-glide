/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

/**
 * Represents the compression backend to use for automatic compression.
 *
 * <p>When compression is enabled, values sent to the server will be compressed using the specified
 * backend if they meet the minimum size threshold. Compressed values are automatically decompressed
 * on retrieval.
 */
public enum CompressionBackend {
    /** Use Zstandard (ZSTD) compression backend. Default compression level is 3. */
    ZSTD,
    /** Use LZ4 compression backend. Default compression level is 0. */
    LZ4
}
