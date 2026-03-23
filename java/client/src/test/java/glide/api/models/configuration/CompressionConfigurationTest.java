/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.exceptions.ConfigurationError;
import org.junit.jupiter.api.Test;

public class CompressionConfigurationTest {

    @Test
    public void defaults() {
        CompressionConfiguration config = CompressionConfiguration.builder().build();
        assertTrue(config.isEnabled());
        assertEquals(CompressionBackend.ZSTD, config.getBackend());
        assertNull(config.getCompressionLevel());
        assertEquals(64, config.getMinCompressionSize());
    }

    @Test
    public void custom_values() {
        CompressionConfiguration config =
                CompressionConfiguration.builder()
                        .enabled(false)
                        .backend(CompressionBackend.LZ4)
                        .compressionLevel(5)
                        .minCompressionSize(256)
                        .build();
        assertEquals(false, config.isEnabled());
        assertEquals(CompressionBackend.LZ4, config.getBackend());
        assertEquals(5, (int) config.getCompressionLevel());
        assertEquals(256, config.getMinCompressionSize());
    }

    @Test
    public void min_compression_size_at_minimum() {
        CompressionConfiguration config =
                CompressionConfiguration.builder()
                        .minCompressionSize(CompressionConfiguration.MIN_COMPRESSION_SIZE)
                        .build();
        assertEquals(CompressionConfiguration.MIN_COMPRESSION_SIZE, config.getMinCompressionSize());
    }

    @Test
    public void min_compression_size_below_minimum_throws() {
        assertThrows(
                ConfigurationError.class,
                () ->
                        CompressionConfiguration.builder()
                                .minCompressionSize(CompressionConfiguration.MIN_COMPRESSION_SIZE - 1)
                                .build());
    }

    @Test
    public void min_compression_size_zero_throws() {
        assertThrows(
                ConfigurationError.class,
                () -> CompressionConfiguration.builder().minCompressionSize(0).build());
    }

    @Test
    public void min_compression_size_negative_throws() {
        assertThrows(
                ConfigurationError.class,
                () -> CompressionConfiguration.builder().minCompressionSize(-1).build());
    }
}
