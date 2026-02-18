/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import connection_request.ConnectionRequestOuterClass;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Compression configuration for automatic compression of values.
 *
 * <p>When enabled, values will be automatically compressed before sending to the server and
 * decompressed when receiving from the server for supported commands (currently GET and SET).
 */
@Getter
@Builder
public class CompressionConfiguration {
    /** Minimum size in bytes for values to be compressed. Defaults to 64 bytes. */
    @Builder.Default private final int minCompressionSize = 64;

    /** Compression backend to use. Defaults to ZSTD. */
    @NonNull @Builder.Default private final CompressionBackend backend = CompressionBackend.ZSTD;

    /**
     * Compression level to use. If not set, the backend's default level will be used. ZSTD default
     * is 3, LZ4 default is 0.
     */
    private final Integer compressionLevel;

    /**
     * Converts this configuration to protobuf format.
     *
     * @return The protobuf compression configuration.
     */
    public ConnectionRequestOuterClass.CompressionConfig toProtobuf() {
        ConnectionRequestOuterClass.CompressionConfig.Builder builder =
                ConnectionRequestOuterClass.CompressionConfig.newBuilder()
                        .setEnabled(true)
                        .setBackend(backend.toProtobuf())
                        .setMinCompressionSize(minCompressionSize);

        if (compressionLevel != null) {
            builder.setCompressionLevel(compressionLevel);
        }

        return builder.build();
    }
}
