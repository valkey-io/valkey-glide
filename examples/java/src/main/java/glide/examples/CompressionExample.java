/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.log;

import glide.api.GlideClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.CompressionBackend;
import glide.api.models.configuration.CompressionConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.Collections;
import java.util.Map;

/**
 * Example demonstrating the Transparent Compression feature with the Java GLIDE client.
 *
 * <p>This feature automatically compresses values before sending them to the server and
 * decompresses them on retrieval. Currently applies to GET and SET commands.
 *
 * <p><b>Note:</b> This feature is experimental.
 */
public class CompressionExample {

    public static void main(String[] args) {
        Logger.init(Logger.Level.INFO);

        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        try {
            // --- ZSTD compression with default settings ---
            log(INFO, "compression-example", "Creating client with ZSTD compression (default)...");

            CompressionConfiguration zstdConfig =
                    CompressionConfiguration.builder().enabled(true).build();

            GlideClientConfiguration clientConfig =
                    GlideClientConfiguration.builder()
                            .address(NodeAddress.builder().host(host).port(port).build())
                            .compressionConfiguration(zstdConfig)
                            .build();

            try (GlideClient client = GlideClient.createClient(clientConfig).get()) {
                // Capture stats before
                Map<String, String> statsBefore = client.getStatistics();
                long originalBytesBefore = Long.parseLong(statsBefore.get("total_original_bytes"));
                long compressedBytesBefore =
                        Long.parseLong(statsBefore.get("total_bytes_compressed"));

                // Values >= 64 bytes are automatically compressed
                String value = String.join("", Collections.nCopies(100, "hello world ")); // ~1200 bytes
                client.set("compressed_key", value).get();

                String retrieved = client.get("compressed_key").get();
                log(
                        INFO,
                        "compression-example",
                        "Data integrity check: " + value.equals(retrieved));

                // Validate compression via statistics
                Map<String, String> statsAfter = client.getStatistics();
                long originalBytes =
                        Long.parseLong(statsAfter.get("total_original_bytes")) - originalBytesBefore;
                long compressedBytes =
                        Long.parseLong(statsAfter.get("total_bytes_compressed"))
                                - compressedBytesBefore;

                log(
                        INFO,
                        "compression-example",
                        "Compressed bytes < original bytes: " + (compressedBytes < originalBytes));
                log(
                        INFO,
                        "compression-example",
                        "Original: " + originalBytes + " bytes, Compressed: " + compressedBytes + " bytes");
            }

            // --- LZ4 compression with custom settings ---
            log(INFO, "compression-example", "Creating client with LZ4 compression...");

            CompressionConfiguration lz4Config =
                    CompressionConfiguration.builder()
                            .enabled(true)
                            .backend(CompressionBackend.LZ4)
                            .minCompressionSize(128)
                            .build();

            GlideClientConfiguration lz4ClientConfig =
                    GlideClientConfiguration.builder()
                            .address(NodeAddress.builder().host(host).port(port).build())
                            .compressionConfiguration(lz4Config)
                            .build();

            try (GlideClient client = GlideClient.createClient(lz4ClientConfig).get()) {
                String value = String.join("", Collections.nCopies(200, "data pattern "));
                client.set("lz4_key", value).get();

                String retrieved = client.get("lz4_key").get();
                log(
                        INFO,
                        "compression-example",
                        "LZ4 data integrity check: " + value.equals(retrieved));
            }

            log(INFO, "compression-example", "Compression example completed successfully.");

        } catch (Exception e) {
            log(ERROR, "compression-example", "Example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
