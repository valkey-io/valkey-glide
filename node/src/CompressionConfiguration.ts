/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { ConfigurationError } from "./Errors";

/**
 * Compression backend to use for automatic value compression.
 */
export enum CompressionBackend {
    /** Use zstd compression backend. */
    ZSTD = 0,
    /** Use lz4 compression backend. */
    LZ4 = 1,
}

/** Minimum allowed value for minCompressionSize (header size + 1). */
const MIN_COMPRESSED_SIZE = 6;

/** Default minimum size in bytes for values to be compressed. */
const DEFAULT_MIN_COMPRESSION_SIZE = 64;

/**
 * Configuration for automatic compression of values sent to the server.
 *
 * NOTE: This is an experimental feature. The API may change in future releases.
 *
 * When compression is enabled, values that meet the minimum size threshold
 * will be automatically compressed before being sent to the server and
 * decompressed when retrieved.
 *
 * @example
 * ```typescript
 * // Enable compression with defaults (ZSTD, 64 byte threshold)
 * const config: CompressionConfiguration = { enabled: true };
 *
 * // Enable compression with LZ4 backend and custom threshold
 * const config: CompressionConfiguration = {
 *     enabled: true,
 *     backend: CompressionBackend.LZ4,
 *     minCompressionSize: 128,
 * };
 * ```
 */
export interface CompressionConfiguration {
    /**
     * Whether compression is enabled. Defaults to false.
     */
    enabled: boolean;
    /**
     * The compression backend to use. Defaults to ZSTD.
     */
    backend?: CompressionBackend;
    /**
     * The compression level. If not set, the backend's default level is used.
     * Valid ranges are backend-specific and validated by the Rust core.
     * ZSTD default is 3, LZ4 default is 0.
     */
    compressionLevel?: number;
    /**
     * Minimum size in bytes for values to be compressed.
     * Values smaller than this will not be compressed.
     * Must be at least 6 bytes. Defaults to 64 bytes.
     */
    minCompressionSize?: number;
}

/**
 * Validates a CompressionConfiguration and throws ConfigurationError if invalid.
 * @internal
 */
export function validateCompressionConfiguration(
    config: CompressionConfiguration,
): void {
    const minSize = config.minCompressionSize ?? DEFAULT_MIN_COMPRESSION_SIZE;

    if (minSize < MIN_COMPRESSED_SIZE) {
        throw new ConfigurationError(
            `minCompressionSize must be at least ${MIN_COMPRESSED_SIZE} bytes`,
        );
    }

    if (
        config.compressionLevel !== undefined &&
        !Number.isInteger(config.compressionLevel)
    ) {
        throw new ConfigurationError("compressionLevel must be an integer");
    }
}

/**
 * Converts a CompressionConfiguration to the protobuf format.
 * @internal
 */
export function compressionConfigToProtobuf(
    config: CompressionConfiguration,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    connection_request: any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
): any {
    validateCompressionConfiguration(config);

    const proto: Record<string, unknown> = {
        enabled: config.enabled,
        backend: config.backend ?? CompressionBackend.ZSTD,
        minCompressionSize:
            config.minCompressionSize ?? DEFAULT_MIN_COMPRESSION_SIZE,
    };

    if (config.compressionLevel !== undefined) {
        proto.compressionLevel = config.compressionLevel;
    }

    return connection_request.CompressionConfig.create(proto);
}
