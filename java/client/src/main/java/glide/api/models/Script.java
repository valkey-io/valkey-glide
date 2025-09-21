/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.api.commands.GenericBaseCommands;
import glide.ffi.resolvers.ScriptResolver;
import lombok.Getter;

/**
 * A wrapper for a Script object for {@link GenericBaseCommands#invokeScript(Script)} As long as
 * this object is not closed, the script's code is saved in memory, and can be resent to the server.
 * Script should be enclosed with a try-with-resource block or {@link Script#close()} must be called
 * to invalidate the code hash.
 */
public class Script implements AutoCloseable {

    /** Hash string representing the code. */
    @Getter private final String hash;

    /** Indication if script invocation output can return binary data. */
    @Getter private final Boolean binaryOutput;

    private boolean dropped = false;

    /**
     * Wraps around creating a Script object from <code>code</code>.
     *
     * @param code To execute with a ScriptInvoke call.
     * @param binaryOutput Indicates if the output can return binary data.
     */
    public <T> Script(T code, Boolean binaryOutput) {
        this.hash = ScriptResolver.storeScript(GlideString.of(code).getBytes());
        this.binaryOutput = binaryOutput;
    }

    /** Drop the linked script from glide-rs <code>code</code>. */
    @Override
    public void close() throws Exception {
        if (!dropped) {
            ScriptResolver.dropScript(hash);
            dropped = true;
        }
    }
}
