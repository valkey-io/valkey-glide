/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import glide.api.GlideClusterClient;
import java.util.concurrent.CompletableFuture;

/**
 * Stub implementation of Json commands for compilation compatibility.
 * This is a basic stub to allow tests to compile and will need full implementation.
 */
public class Json {
    
    // Basic constant for OK responses
    public static final String OK = "OK";
    
    // Stub implementation - can be expanded as needed
    public static CompletableFuture<String> set(GlideClusterClient client, String key, String path, Object value) {
        return CompletableFuture.completedFuture(OK);
    }
    
    public static CompletableFuture<Object> get(GlideClusterClient client, String key, String path) {
        return CompletableFuture.completedFuture(null);
    }
}