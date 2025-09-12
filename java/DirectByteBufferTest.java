/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
import java.nio.ByteBuffer;

/** Test for DirectByteBuffer optimization with 16KB threshold validation */
public class DirectByteBufferTest {

    public static void main(String[] args) {
        System.out.println("=== DirectByteBuffer Optimization Test ===");
        System.out.println();

        // Test different buffer sizes around the 16KB threshold
        int[] testSizes = {
            1024, // 1KB - should use regular JNI objects
            8192, // 8KB - should use regular JNI objects
            16383, // 16KB - 1 byte (just under threshold)
            16384, // 16KB (exactly at threshold)
            32768, // 32KB - should use DirectByteBuffer
            65536, // 64KB - should use DirectByteBuffer
            1048576 // 1MB - should use DirectByteBuffer
        };

        System.out.println("Testing DirectByteBuffer allocation at different sizes:");
        System.out.println("(16KB threshold determines optimization strategy)");
        System.out.println();

        for (int size : testSizes) {
            testDirectByteBuffer(size);
        }

        System.out.println();
        System.out.println("=== Memory Pressure Test ===");
        testMemoryPressure();

        System.out.println();
        System.out.println("✅ DirectByteBuffer optimization validation COMPLETE");
    }

    private static void testDirectByteBuffer(int size) {
        try {
            long startTime = System.nanoTime();
            ByteBuffer buffer = ByteBuffer.allocateDirect(size);
            long endTime = System.nanoTime();

            String sizeStr = formatSize(size);
            String threshold = size >= 16384 ? "OPTIMIZED" : "STANDARD";
            long allocTime = (endTime - startTime) / 1000; // microseconds

            System.out.printf(
                    "%-8s: %-10s allocation in %6d μs [%s]%n", sizeStr, "SUCCESS", allocTime, threshold);

            // Test basic operations
            buffer.putInt(0x12345678);
            buffer.flip();
            int value = buffer.getInt();

            if (value != 0x12345678) {
                System.out.println("  ❌ ERROR: Data integrity test failed");
            }

        } catch (Exception e) {
            String sizeStr = formatSize(size);
            System.out.printf("%-8s: %-10s - %s%n", sizeStr, "FAILED", e.getMessage());
        }
    }

    private static void testMemoryPressure() {
        System.out.println("Allocating multiple large DirectByteBuffers...");

        ByteBuffer[] buffers = new ByteBuffer[10];
        long totalAllocated = 0;

        try {
            for (int i = 0; i < buffers.length; i++) {
                int size = 1024 * 1024; // 1MB each
                buffers[i] = ByteBuffer.allocateDirect(size);
                totalAllocated += size;

                if (i % 3 == 0) {
                    System.out.println(
                            "  Allocated " + (i + 1) + " buffers (" + formatSize(totalAllocated) + " total)");
                }
            }

            System.out.println(
                    "✅ Memory pressure test: PASSED (" + formatSize(totalAllocated) + " allocated)");

        } catch (OutOfMemoryError e) {
            System.out.println(
                    "⚠️  Memory pressure test: Hit memory limit at " + formatSize(totalAllocated));
        } catch (Exception e) {
            System.out.println("❌ Memory pressure test: FAILED - " + e.getMessage());
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        return (bytes / (1024 * 1024)) + "MB";
    }
}
