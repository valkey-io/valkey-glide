/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import lombok.Getter;

/**
 * A wrapper for a Script object for script execution commands. As long as
 * this object is not closed, the script's code is saved in memory, and can be resent to the server.
 * Script should be enclosed with a try-with-resource block or {@link Script#close()} must be called
 * to invalidate the code hash.
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
        this.hash = computeScriptHash(this.code);
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
     * Drop the linked script. Currently a no-op since we don't have native script storage.
     * The script hash is computed on-demand from the code.
     */
    @Override
    public void close() throws Exception {
        if (!isDropped) {
            // Currently no native storage to clean up
            isDropped = true;
        }
    }

    /**
     * @deprecated Use try-with-resources or explicit close() instead
     */
    @Deprecated(forRemoval = true)
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