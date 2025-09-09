/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.ffi.resolvers.ScriptResolver;
import lombok.Getter;

/**
 * A wrapper for a Script object for script execution commands. The script's code is
 * stored in the native script container with reference counting for efficient memory usage.
 * Script should be enclosed with a try-with-resource block or {@link Script#close()} must be called
 * to remove the script from native storage and invalidate the code hash.
 */
public class Script implements AutoCloseable {

    /** Hash string representing the code. */
    @Getter private final String hash;

    /** The original script code. */
    @Getter private final String code;

    private boolean isDropped = false;

    /** Indication if script invocation output can return binary data. */
    @Getter private final Boolean binaryOutput;

    /**
     * Wraps around creating a Script object from <code>code</code>.
     *
     * @param code To execute with a script invoke call.
     * @param binaryOutput Indicates if the output can return binary data.
     */
    public <T> Script(T code, Boolean binaryOutput) {
        this.code = GlideString.of(code).toString();
        // Store script in native container and get SHA1 hash
        this.hash = ScriptResolver.storeScript(this.code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.binaryOutput = binaryOutput;
    }

    /**
     * Wraps around creating a Script object from <code>code</code>.
     * Assumes string output (binary output = false).
     *
     * @param code To execute with a script invoke call.
     */
    public <T> Script(T code) {
        this(code, false);
    }

    /**
     * Indicates whether this Script has been closed (its native reference dropped).
     * Once closed the client must not attempt to transparently re-load the code when
     * EVALSHA misses; doing so would violate lifecycle semantics required by tests
     * that assert NOSCRIPT after full release + server flush.
     *
     * @return true if close() has been invoked and native refcount decremented to reflect release.
     */
    public boolean isClosed() {
        return isDropped;
    }

    /**
     * Compute SHA1 hash of script code for EVALSHA command.
     *
     * @param code The script code to hash
     * @return The SHA1 hash as a hex string
     */
    private String computeScriptHash(String code) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(code.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute script hash", e);
        }
    }

    /**
     * Drop the linked script from native script storage.
     * This decrements the reference count and removes the script when it reaches zero.
     */
    @Override
    public void close() throws Exception {
        if (!isDropped) {
            // Drop script from native storage
            ScriptResolver.dropScript(this.hash);
            isDropped = true;
        }
    }

    /**
     * @deprecated Use try-with-resources or explicit close() instead
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("removal")
    @Override
    protected void finalize() throws Throwable {
        try {
            // Drop the linked script on garbage collection.
            this.close();
        } finally {
            super.finalize();
        }
    }
}
