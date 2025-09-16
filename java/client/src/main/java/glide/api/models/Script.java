/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.ffi.resolvers.ScriptResolver;
import lombok.Getter;

public class Script implements AutoCloseable {

    @Getter private final String hash;
    @Getter private final Boolean binaryOutput;
    private boolean dropped = false;

    public <T> Script(T code, Boolean binaryOutput) {
        this.hash = ScriptResolver.storeScript(GlideString.of(code).getBytes());
        this.binaryOutput = binaryOutput;
    }

    @Override
    public void close() throws Exception {
        if (!dropped) {
            ScriptResolver.dropScript(hash);
            dropped = true;
        }
    }
}
