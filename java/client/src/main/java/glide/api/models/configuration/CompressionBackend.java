/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import connection_request.ConnectionRequestOuterClass;

/** Compression backend to use for automatic compression. */
public enum CompressionBackend {
    /** Use zstd compression backend. */
    ZSTD(ConnectionRequestOuterClass.CompressionBackend.ZSTD),
    /** Use lz4 compression backend. */
    LZ4(ConnectionRequestOuterClass.CompressionBackend.LZ4);

    private final ConnectionRequestOuterClass.CompressionBackend protobufValue;

    CompressionBackend(ConnectionRequestOuterClass.CompressionBackend protobufValue) {
        this.protobufValue = protobufValue;
    }

    public ConnectionRequestOuterClass.CompressionBackend toProtobuf() {
        return protobufValue;
    }
}
