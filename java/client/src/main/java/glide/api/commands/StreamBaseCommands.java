/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.StreamAddOptions;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Stream Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/commands/?group=stream">Stream Commands</a>
 */
public interface StreamBaseCommands {

    /**
     * Adds an entry to the specified stream stored at <code>key</code>.<br>
     * If the <code>key</code> doesn't exist, the stream is created.
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @return The id of the added entry.
     * @example
     *     <pre>{@code
     * String streamId = client.xadd("key", Map.of("name", "Sara", "surname", "OConnor").get();
     * System.out.println("Stream: " + streamId);
     * }</pre>
     */
    CompletableFuture<String> xadd(String key, Map<String, String> values);

    /**
     * Adds an entry to the specified stream stored at <code>key</code>.<br>
     * If the <code>key</code> doesn't exist, the stream is created.
     *
     * @see <a href="https://redis.io/commands/xadd/">redis.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @param options Stream add options.
     * @return The id of the added entry, or <code>null</code> if {@link StreamAddOptions#makeStream}
     *     is set to <code>false</code> and no stream with the matching <code>key</code> exists.
     * @example
     *     <pre>{@code
     * // Option to use the existing stream, or return null if the stream doesn't already exist at "key"
     * StreamAddOptions options = StreamAddOptions.builder().id("sid").makeStream(Boolean.FALSE).build();
     * String streamId = client.xadd("key", Map.of("name", "Sara", "surname", "OConnor"), options).get();
     * if (streamId != null) {
     *     assert streamId.equals("sid");
     * }
     * }</pre>
     */
    CompletableFuture<String> xadd(String key, Map<String, String> values, StreamAddOptions options);
}
