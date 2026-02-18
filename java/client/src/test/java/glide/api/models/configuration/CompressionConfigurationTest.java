/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class CompressionConfigurationTest {

    @Test
    public void testDefaultConfiguration() {
        CompressionConfiguration config = CompressionConfiguration.builder().build();

        assertEquals(64, config.getMinCompressionSize());
        assertEquals(CompressionBackend.ZSTD, config.getBackend());
        assertNull(config.getCompressionLevel());
    }

    @Test
    public void testCustomConfiguration() {
        CompressionConfiguration config =
                CompressionConfiguration.builder()
                        .minCompressionSize(128)
                        .backend(CompressionBackend.LZ4)
                        .compressionLevel(5)
                        .build();

        assertEquals(128, config.getMinCompressionSize());
        assertEquals(CompressionBackend.LZ4, config.getBackend());
        assertEquals(5, config.getCompressionLevel());
    }

    @Test
    public void testProtobufConversion() {
        CompressionConfiguration config =
                CompressionConfiguration.builder()
                        .minCompressionSize(100)
                        .backend(CompressionBackend.ZSTD)
                        .compressionLevel(3)
                        .build();

        var protobuf = config.toProtobuf();

        assertTrue(protobuf.getEnabled());
        assertEquals(100, protobuf.getMinCompressionSize());
        assertEquals(3, protobuf.getCompressionLevel());
    }

    @Test
    public void testProtobufConversionWithoutLevel() {
        CompressionConfiguration config =
                CompressionConfiguration.builder().minCompressionSize(64).build();

        var protobuf = config.toProtobuf();

        assertTrue(protobuf.getEnabled());
        assertEquals(64, protobuf.getMinCompressionSize());
        assertFalse(protobuf.hasCompressionLevel());
    }
}
