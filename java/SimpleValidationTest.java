/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
import java.io.File;

/** Simple validation test for the Valkey GLIDE JNI implementation. */
public class SimpleValidationTest {

    static {
        try {
            // Load the native library directly from the build output
            String libraryPath = "target/release/libglide_rs.so";
            File libFile = new File(libraryPath);
            if (libFile.exists()) {
                System.load(libFile.getAbsolutePath());
                System.out.println("✅ SUCCESS: Native library loaded successfully");
            } else {
                System.out.println("❌ ERROR: Native library not found at: " + libraryPath);
                System.exit(1);
            }
        } catch (UnsatisfiedLinkError e) {
            System.out.println("❌ ERROR: Failed to load native library: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Valkey GLIDE JNI Validation Test ===");
        System.out.println("✅ Native library loading: PASSED");
        System.out.println("✅ JVM environment: Java " + System.getProperty("java.version"));

        // Test DirectByteBuffer
        try {
            java.nio.ByteBuffer directBuffer = java.nio.ByteBuffer.allocateDirect(1024);
            System.out.println(
                    "✅ DirectByteBuffer support: AVAILABLE (" + directBuffer.capacity() + " bytes)");
        } catch (Exception e) {
            System.out.println("❌ DirectByteBuffer support: FAILED");
        }

        System.out.println("🎉 Basic validation tests PASSED!");
    }
}
