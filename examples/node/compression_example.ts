/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Demonstrates transparent compression with Valkey GLIDE.
 * Compression automatically compresses values before sending to the server
 * and decompresses them when retrieved.
 */

import {
    CompressionBackend,
    GlideClient,
    Logger,
} from "@valkey/valkey-glide";

async function main() {
    // Create a client with compression enabled
    const client = await GlideClient.createClient({
        addresses: [{ host: "localhost", port: 6379 }],
        compression: {
            enabled: true,
            backend: CompressionBackend.ZSTD,
            minCompressionSize: 64,
        },
    });

    try {
        // Generate a compressible value
        const value = "Hello, Valkey GLIDE with compression! ".repeat(100);

        // SET — value is automatically compressed before sending
        await client.set("compressed_key", value);
        Logger.log("info", "app", "SET compressed_key (compressed automatically)");

        // GET — value is automatically decompressed
        const retrieved = await client.get("compressed_key");
        Logger.log("info", "app", `GET compressed_key: ${retrieved?.toString().slice(0, 50)}...`);

        // Check compression statistics
        const stats = client.getStatistics() as Record<string, number>;
        Logger.log("info", "app", `Values compressed: ${stats.total_values_compressed}`);
        Logger.log("info", "app", `Original bytes: ${stats.total_original_bytes}`);
        Logger.log("info", "app", `Compressed bytes: ${stats.total_bytes_compressed}`);

        if (stats.total_original_bytes > 0) {
            const ratio = (
                (1 - stats.total_bytes_compressed / stats.total_original_bytes) * 100
            ).toFixed(1);
            Logger.log("info", "app", `Compression ratio: ${ratio}% savings`);
        }

        // Cleanup
        await client.del(["compressed_key"]);
    } finally {
        client.close();
    }
}

main().catch(console.error);
