/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.stream.StreamAddOptions;
import glide.api.models.commands.stream.StreamAddOptions.StreamAddOptionsBuilder;
import glide.api.models.commands.stream.StreamGroupOptions;
import glide.api.models.commands.stream.StreamPendingOptions;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.stream.StreamRange.IdBound;
import glide.api.models.commands.stream.StreamRange.InfRangeBound;
import glide.api.models.commands.stream.StreamReadGroupOptions;
import glide.api.models.commands.stream.StreamReadOptions;
import glide.api.models.commands.stream.StreamTrimOptions;
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
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
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
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @param options Stream add options {@link StreamAddOptions}.
     * @return The id of the added entry, or <code>null</code> if {@link
     *     StreamAddOptionsBuilder#makeStream(Boolean)} is set to <code>false</code> and no stream
     *     with the matching <code>key</code> exists.
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

    /**
     * Reads entries from the given streams.
     *
     * @apiNote When in cluster mode, all keys in <code>keysAndIds</code> must map to the same hash
     *     slot.
     * @see <a href="https://valkey.io/commands/xread/">valkey.io</a> for details.
     * @param keysAndIds A <code>Map</code> of keys and entry ids to read from. The <code>
     *     Map</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read.
     * @return A <code>{@literal Map<String, Map<String, String[][]>>}</code> with stream
     *      keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     * @example
     *     <pre>{@code
     * Map<String, String> xreadKeys = Map.of("streamKey", "0-0");
     * Map<String, Map<String, String[][]>> streamReadResponse = client.xread(xreadKeys).get();
     * for (var keyEntry : streamReadResponse.entrySet()) {
     *     System.out.printf("Key: %s", keyEntry.getKey());
     *     for (var streamEntry : keyEntry.getValue().entrySet()) {
     *         Arrays.stream(streamEntry.getValue()).forEach(entity ->
     *             System.out.printf("stream id: %s; field: %s; value: %s\n", streamEntry.getKey(), entity[0], entity[1])
     *         );
     *     }
     * }</pre>
     */
    CompletableFuture<Map<String, Map<String, String[][]>>> xread(Map<String, String> keysAndIds);

    /**
     * Reads entries from the given streams.
     *
     * @apiNote When in cluster mode, all keys in <code>keysAndIds</code> must map to the same hash
     *     slot.
     * @see <a href="https://valkey.io/commands/xread/">valkey.io</a> for details.
     * @param keysAndIds A <code>Map</code> of keys and entry ids to read from. The <code>
     *     Map</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read.
     * @param options Options detailing how to read the stream {@link StreamReadOptions}.
     * @return A <code>{@literal Map<String, Map<String, String[][]>>}</code> with stream
     *     keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     * @example
     *     <pre>{@code
     * // retrieve streamKey entries and block for 1 second if is no stream data
     * Map<String, String> xreadKeys = Map.of("streamKey", "0-0");
     * StreamReadOptions options = StreamReadOptions.builder().block(1L).build();
     * Map<String, Map<String, String[][]>> streamReadResponse = client.xread(xreadKeys, options).get();
     * for (var keyEntry : streamReadResponse.entrySet()) {
     *     System.out.printf("Key: %s", keyEntry.getKey());
     *     for (var streamEntry : keyEntry.getValue().entrySet()) {
     *         Arrays.stream(streamEntry.getValue()).forEach(entity ->
     *             System.out.printf("stream id: %s; field: %s; value: %s\n", streamEntry.getKey(), entity[0], entity[1])
     *         );
     *     }
     * }</pre>
     */
    CompletableFuture<Map<String, Map<String, String[][]>>> xread(
            Map<String, String> keysAndIds, StreamReadOptions options);

    /**
     * Trims the stream by evicting older entries.
     *
     * @see <a href="https://valkey.io/commands/xtrim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param options Stream trim options {@link StreamTrimOptions}.
     * @return The number of entries deleted from the stream.
     * @example
     *     <pre>{@code
     * // A nearly exact trimming of the stream to at least a length of 10
     * Long trimmed = client.xtrim("key", new MaxLen(false, 10L)).get();
     * System.out.println("Number of trimmed entries from stream: " + trimmed);
     *
     * // An exact trimming of the stream by minimum id of "0-3", limit of 10 entries
     * Long trimmed = client.xtrim("key", new MinId(true, "0-3", 10L)).get();
     * System.out.println("Number of trimmed entries from stream: " + trimmed);
     * }</pre>
     */
    CompletableFuture<Long> xtrim(String key, StreamTrimOptions options);

    /**
     * Returns the number of entries in the stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xlen/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @return The number of entries in the stream. If <code>key</code> does not exist, return <code>0
     *     </code>.
     * @example
     *     <pre>{@code
     * Long num = client.xlen("key").get();
     * assert num == 2L; // Stream has 2 entries
     * }</pre>
     */
    CompletableFuture<Long> xlen(String key);

    /**
     * Returns the number of entries in the stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xlen/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @return The number of entries in the stream. If <code>key</code> does not exist, return <code>0
     *     </code>.
     * @example
     *     <pre>{@code
     * Long num = client.xlen(gs("key")).get();
     * assert num == 2L; // Stream has 2 entries
     * }</pre>
     */
    CompletableFuture<Long> xlen(GlideString key);

    /**
     * Removes the specified entries by id from a stream, and returns the number of entries deleted.
     *
     * @see <a href="https://valkey.io/commands/xdel/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param ids An array of entry ids.
     * @return The number of entries removed from the stream. This number may be less than the number
     *     of entries in <code>ids</code>, if the specified <code>ids</code> don't exist in the
     *     stream.
     * @example
     *     <pre>{@code
     * Long num = client.xdel("key", new String[] {"1538561698944-0", "1538561698944-1"}).get();
     * assert num == 2L; // Stream marked 2 entries as deleted
     * }</pre>
     */
    CompletableFuture<Long> xdel(String key, String[] ids);

    /**
     * Removes the specified entries by id from a stream, and returns the number of entries deleted.
     *
     * @see <a href="https://valkey.io/commands/xdel/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param ids An array of entry ids.
     * @return The number of entries removed from the stream. This number may be less than the number
     *     of entries in <code>ids</code>, if the specified <code>ids</code> don't exist in the
     *     stream.
     * @example
     *     <pre>{@code
     * Long num = client.xdel("key", new GlideString[] {gs("1538561698944-0"), gs("1538561698944-1")}).get();
     * assert num == 2L; // Stream marked 2 entries as deleted
     * }</pre>
     */
    CompletableFuture<Long> xdel(GlideString key, GlideString[] ids);

    /**
     * Returns stream entries matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @return A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     * @example
     *     <pre>{@code
     * // Retrieve all stream entries
     * Map<String, String[][]> result = client.xrange("key", InfRangeBound.MIN, InfRangeBound.MAX).get();
     * result.forEach((k, v) -> {
     *     System.out.println("Stream ID: " + k);
     *     for (int i = 0; i < v.length; i++) {
     *         System.out.println(v[i][0] + ": " + v[i][1]);
     *     }
     * });
     * // Retrieve exactly one stream entry by id
     * Map<String, String[][]> result = client.xrange("key", IdBound.of(streamId), IdBound.of(streamId)).get();
     * System.out.println("Stream ID: " + streamid + " -> " + Arrays.toString(result.get(streamid)));
     * }</pre>
     */
    CompletableFuture<Map<String, String[][]>> xrange(String key, StreamRange start, StreamRange end);

    /**
     * Returns stream entries matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param count Maximum count of stream entries to return.
     * @return A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     * @example
     *     <pre>{@code
     * // Retrieve the first 2 stream entries
     * Map<String, String[][]> result = client.xrange("key", InfRangeBound.MIN, InfRangeBound.MAX, 2).get();
     * result.forEach((k, v) -> {
     *     System.out.println("Stream ID: " + k);
     *     for (int i = 0; i < v.length; i++) {
     *         System.out.println(v[i][0] + ": " + v[i][1]);
     *     }
     * });
     * }</pre>
     */
    CompletableFuture<Map<String, String[][]>> xrange(
            String key, StreamRange start, StreamRange end, long count);

    /**
     * Returns stream entries matching a given range of IDs in reverse order.<br>
     * Equivalent to {@link #xrange(String, StreamRange, StreamRange)} but returns the entries in
     * reverse order.
     *
     * @see <a href="https://valkey.io/commands/xrevrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @return A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     * @example
     *     <pre>{@code
     * // Retrieve all stream entries
     * Map<String, String[][]> result = client.xrevrange("key", InfRangeBound.MAX, InfRangeBound.MIN).get();
     * result.forEach((k, v) -> {
     *     System.out.println("Stream ID: " + k);
     *     for (int i = 0; i < v.length; i++) {
     *         System.out.println(v[i][0] + ": " + v[i][1]);
     *     }
     * });
     * // Retrieve exactly one stream entry by id
     * Map<String, String[][]> result = client.xrevrange("key", IdBound.of(streamId), IdBound.of(streamId)).get();
     * System.out.println("Stream ID: " + streamid + " -> " + Arrays.toString(result.get(streamid)));
     * }</pre>
     */
    CompletableFuture<Map<String, String[][]>> xrevrange(
            String key, StreamRange end, StreamRange start);

    /**
     * Returns stream entries matching a given range of IDs in reverse order.<br>
     * Equivalent to {@link #xrange(String, StreamRange, StreamRange, long)} but returns the entries
     * in reverse order.
     *
     * @see <a href="https://valkey.io/commands/xrevrange/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     *
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param count Maximum count of stream entries to return.
     * @return A <code>Map</code> of key to stream entry data, where entry data is an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     * @example
     *     <pre>{@code
     * // Retrieve the first 2 stream entries
     * Map<String, String[][]> result = client.xrange("key", InfRangeBound.MAX, InfRangeBound.MIN, 2).get();
     * result.forEach((k, v) -> {
     *     System.out.println("Stream ID: " + k);
     *     for (int i = 0; i < v.length; i++) {
     *         System.out.println(v[i][0] + ": " + v[i][1]);
     *     }
     * });
     * }</pre>
     */
    CompletableFuture<Map<String, String[][]>> xrevrange(
            String key, StreamRange end, StreamRange start, long count);

    /**
     * Creates a new consumer group uniquely identified by <code>groupname</code> for the stream
     * stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-create/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupname The newly created consumer group name.
     * @param id Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID <code>"$"</code> can be used to specify the last entry
     *     in the stream.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Create the consumer group "mygroup", using zero as the starting ID:
     * assert client.xgroupCreate("mystream", "mygroup", "0-0").get().equals("OK");
     * }</pre>
     */
    CompletableFuture<String> xgroupCreate(String key, String groupname, String id);

    /**
     * Creates a new consumer group uniquely identified by <code>groupname</code> for the stream
     * stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-create/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupname The newly created consumer group name.
     * @param id Stream entry ID that specifies the last delivered entry in the stream from the new
     *     group’s perspective. The special ID <code>"$"</code> can be used to specify the last entry
     *     in the stream.
     * @param options The group options {@link StreamGroupOptions}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * // Create the consumer group "mygroup", and the stream if it does not exist, after the last ID
     * assert client.xgroupCreate("mystream", "mygroup", "$", new StreamGroupOptions(true)).get().equals("OK");
     * }</pre>
     */
    CompletableFuture<String> xgroupCreate(
            String key, String groupname, String id, StreamGroupOptions options);

    /**
     * Destroys the consumer group <code>groupname</code> for the stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-destroy/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupname The consumer group name to delete.
     * @return <code>true</code> if the consumer group is destroyed. Otherwise, <code>false</code>.
     * @example
     *     <pre>{@code
     * // Destroys the consumer group "mygroup"
     * assert client.xgroupDestroy("mystream", "mygroup").get().equals("OK");
     * }</pre>
     */
    CompletableFuture<Boolean> xgroupDestroy(String key, String groupname);

    /**
     * Creates a consumer named <code>consumer</code> in the consumer group <code>group</code> for the
     * stream stored at <code>key</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-createconsumer/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The newly created consumer.
     * @return <code>true</code> if the consumer is created. Otherwise, <code>false</code>.
     * @example
     *     <pre>{@code
     * // Creates the consumer "myconsumer" in consumer group "mygroup"
     * assert client.xgroupCreateConsumer("mystream", "mygroup", "myconsumer").get();
     * }</pre>
     */
    CompletableFuture<Boolean> xgroupCreateConsumer(String key, String group, String consumer);

    /**
     * Deletes a consumer named <code>consumer</code> in the consumer group <code>group</code>.
     *
     * @see <a href="https://valkey.io/commands/xgroup-delconsumer/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param consumer The consumer to delete.
     * @return The number of pending messages the <code>consumer</code> had before it was deleted.
     * @example
     *     <pre>{@code
     * // Deletes the consumer "myconsumer" in consumer group "mygroup"
     * Long pendingMsgCount = client.xgroupDelConsumer("mystream", "mygroup", "myconsumer").get();
     * System.out.println("Consumer 'myconsumer' had " +
     *     + pendingMsgCount + " pending messages unclaimed.");
     * }</pre>
     */
    CompletableFuture<Long> xgroupDelConsumer(String key, String group, String consumer);

    /**
     * Reads entries from the given streams owned by a consumer group.
     *
     * @apiNote When in cluster mode, all keys in <code>keysAndIds</code> must map to the same hash
     *     slot.
     * @see <a href="https://valkey.io/commands/xreadgroup/">valkey.io</a> for details.
     * @param keysAndIds A <code>Map</code> of keys and entry ids to read from. The <code>
     *     Map</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read. Use the special id of <code>{@literal ">"}</code> to receive only new messages.
     * @param group The consumer group name.
     * @param consumer The consumer name.
     * @return A <code>{@literal Map<String, Map<String, String[][]>>}</code> with stream
     *      keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     *      Returns <code>null</code> if there is no stream that can be served.
     * @example
     *     <pre>{@code
     * // create a new stream at "mystream", with stream id "1-0"
     * String streamId = client.xadd("mystream", Map.of("myfield", "mydata"), StreamAddOptions.builder().id("1-0").build()).get();
     * assert client.xgroupCreate("mystream", "mygroup", "0-0").get().equals("OK"); // create the consumer group "mygroup"
     * Map<String, Map<String, String[][]>> streamReadResponse = client.xreadgroup(Map.of("mystream", ">"), "mygroup", "myconsumer").get();
     * // Returns "mystream": "1-0": {{"myfield", "mydata"}}
     * for (var keyEntry : streamReadResponse.entrySet()) {
     *     System.out.printf("Key: %s", keyEntry.getKey());
     *     for (var streamEntry : keyEntry.getValue().entrySet()) {
     *         Arrays.stream(streamEntry.getValue()).forEach(entity ->
     *             System.out.printf("stream id: %s; field: %s; value: %s\n", streamEntry.getKey(), entity[0], entity[1])
     *         );
     *     }
     * }
     * </pre>
     */
    CompletableFuture<Map<String, Map<String, String[][]>>> xreadgroup(
            Map<String, String> keysAndIds, String group, String consumer);

    /**
     * Reads entries from the given streams owned by a consumer group.
     *
     * @apiNote When in cluster mode, all keys in <code>keysAndIds</code> must map to the same hash
     *     slot.
     * @see <a href="https://valkey.io/commands/xreadgroup/">valkey.io</a> for details.
     * @param keysAndIds A <code>Map</code> of keys and entry ids to read from. The <code>
     *     Map</code> is composed of a stream's key and the id of the entry after which the stream
     *     will be read. Use the special id of <code>{@literal ">"}</code> to receive only new messages.
     * @param group The consumer group name.
     * @param consumer The consumer name.
     * @param options Options detailing how to read the stream {@link StreamReadGroupOptions}.
     * @return A <code>{@literal Map<String, Map<String, String[][]>>}</code> with stream
     *      keys, to <code>Map</code> of stream-ids, to an array of pairings with format <code>[[field, entry], [field, entry], ...]<code>.
     *      Returns <code>null</code> if the {@link StreamReadGroupOptions#block} option is given and a timeout occurs, or if there is no stream that can be served.
     * @example
     *     <pre>{@code
     * // create a new stream at "mystream", with stream id "1-0"
     * String streamId = client.xadd("mystream", Map.of("myfield", "mydata"), StreamAddOptions.builder().id("1-0").build()).get();
     * assert client.xgroupCreate("mystream", "mygroup", "0-0").get().equals("OK"); // create the consumer group "mygroup"
     * StreamReadGroupOptions options = StreamReadGroupOptions.builder().count(1).build(); // retrieves only a single message at a time
     * Map<String, Map<String, String[][]>> streamReadResponse = client.xreadgroup(Map.of("mystream", ">"), "mygroup", "myconsumer", options).get();
     * // Returns "mystream": "1-0": {{"myfield", "mydata"}}
     * for (var keyEntry : streamReadResponse.entrySet()) {
     *     System.out.printf("Key: %s", keyEntry.getKey());
     *     for (var streamEntry : keyEntry.getValue().entrySet()) {
     *         Arrays.stream(streamEntry.getValue()).forEach(entity ->
     *             System.out.printf("stream id: %s; field: %s; value: %s\n", streamEntry.getKey(), entity[0], entity[1])
     *         );
     *     }
     * }
     * </pre>
     */
    CompletableFuture<Map<String, Map<String, String[][]>>> xreadgroup(
            Map<String, String> keysAndIds,
            String group,
            String consumer,
            StreamReadGroupOptions options);

    /**
     * Returns the number of messages that were successfully acknowledged by the consumer group member of a stream.
     * This command should be called on a pending message so that such message does not get processed again.
     *
     * @see <a href="https://valkey.io/commands/xack/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param ids Stream entry ID to acknowledge and purge messages.
     * @return The number of messages that were successfully acknowledged.
     * @example
     *     <pre>{@code
     * String entryId = client.xadd("mystream", Map.of("myfield", "mydata")).get();
     * // read messages from streamId
     * var readResult = client.xreadgroup(Map.of("mystream", entryId), "mygroup", "my0consumer").get();
     * // acknowledge messages on stream
     * assert 1L == client.xack("mystream", "mygroup", new String[] {entryId}).get();
     * </pre>
     */
    CompletableFuture<Long> xack(String key, String group, String[] ids);

    /**
     * Returns the number of messages that were successfully acknowledged by the consumer group member of a stream.
     * This command should be called on a pending message so that such message does not get processed again.
     *
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param ids Stream entry ID to acknowledge and purge messages.
     * @return The number of messages that were successfully acknowledged.
     * @example
     *     <pre>{@code
     * GlideString entryId = client.xadd(gs("mystream"), Map.of(gs("myfield"), gs("mydata")).get();
     * // read messages from streamId
     * var readResult = client.xreadgroup(Map.of(gs("mystream"), entryId), gs("mygroup"), gs("my0consumer")).get();
     * // acknowledge messages on stream
     * assert 1L == client.xack(gs("mystream"), gs("mygroup"), new GlideString[] {entryId}).get();
     * </pre>
     */
    CompletableFuture<Long> xack(GlideString key, GlideString group, GlideString[] ids);

    /**
     * Returns stream message summary information for pending messages matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @return An <code>array</code> that includes the summary of pending messages, with the format
     * <code>[NumOfMessages, StartId, EndId, [Consumer, NumOfMessages]]</code>, where:
     * <ul>
     *      <li> <code>NumOfMessages</code>: The total number of pending messages for this consumer group.
     *      <li> <code>StartId</code>: The smallest ID among the pending messages.
     *      <li> <code>EndId</code>: The greatest ID among the pending messages.
     *      <li> <code>[[Consumer, NumOfMessages], ...]</code>: A 2D-<code>array</code> of every consumer
     *      in the consumer group with at least one pending message, and the number of pending messages it has.
     * </ul>
     * @example
     *       <pre>{@code
     * // Retrieve a summary of all pending messages from key "my_stream"
     * Object[] result = client.xpending("my_stream", "my_group").get();
     * System.out.println("Number of pending messages: " + result[0]);
     * System.out.println("Start and End ID of messages: [" + result[1] + ", " + result[2] + "]");
     * for (Object[] consumerResult : (Object[][]) result[3]) {
     *     System.out.println("Number of Consumer messages: [" + consumerResult[0] + ", " + consumerResult[1] + "]");
     * }</pre>
     */
    CompletableFuture<Object[]> xpending(String key, String group);

    /**
     * Returns an extended form of stream message information for pending messages matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     * @param count Limits the number of messages returned.
     * @return A 2D-<code>array</code> of 4-tuples containing extended message information with the format
     * <code>[[ID, Consumer, TimeElapsed, NumOfDelivered], ... ]</code>, where:
     * <ul>
     *      <li> <code>ID</code>: The ID of the message.
     *      <li> <code>Consumer</code>: The name of the consumer that fetched the message and has still to acknowledge it. We call it the current owner of the message.
     *      <li> <code>TimeElapsed</code>: The number of milliseconds that elapsed since the last time this message was delivered to this consumer.
     *      <li> <code>NumOfDelivered</code>: The number of times this message was delivered.
     * </ul>
     * @example
     *       <pre>{@code
     * // Retrieve up to 10 pending messages from key "my_stream" in extended form
     * Object[][] result = client.xpending("my_stream", "my_group", InfRangeBound.MIN, InfRangeBound.MAX, 10L).get();
     * for (Object[] messageResult : result) {
     *     System.out.printf("Message %s from consumer %s was read %s times", messageResult[0], messageResult[1], messageResult[2]);
     * }</pre>
     */
    CompletableFuture<Object[][]> xpending(
            String key, String group, StreamRange start, StreamRange end, long count);

    /**
     * Returns an extended form of stream message information for pending messages matching a given range of IDs.
     *
     * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param group The consumer group name.
     * @param start Starting stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MIN} to start with the minimum available ID.
     *     </ul>
     *
     * @param end Ending stream ID bound for range.
     *     <ul>
     *       <li>Use {@link IdBound#of} to specify a stream ID.
     *       <li>Use {@link IdBound#ofExclusive} to specify an exclusive bounded stream ID.
     *       <li>Use {@link InfRangeBound#MAX} to end with the maximum available ID.
     *     </ul>
     * @param count Limits the number of messages returned.
     * @param options Stream add options {@link StreamPendingOptions}.
     * @return A 2D-<code>array</code> of 4-tuples containing extended message information with the format
     * <code>[[ID, Consumer, TimeElapsed, NumOfDelivered], ... ]</code>, where:
     * <ul>
     *      <li> <code>ID</code>: The ID of the message.
     *      <li> <code>Consumer</code>: The name of the consumer that fetched the message and has still to acknowledge it. We call it the current owner of the message.
     *      <li> <code>TimeElapsed</code>: The number of milliseconds that elapsed since the last time this message was delivered to this consumer.
     *      <li> <code>NumOfDelivered</code>: The number of times this message was delivered.
     * </ul>
     * @example
     *       <pre>{@code
     * // Retrieve up to 10 pending messages from key "my_stream" and consumer "my_consumer" in extended form
     * Object[][] result = client.xpending(
     *     "my_stream",
     *     "my_group",
     *     InfRangeBound.MIN,
     *     InfRangeBound.MAX,
     *     10L,
     *     StreamPendingOptions.builder().consumer("my_consumer").build()
     * ).get();
     * for (Object[] messageResult : result) {
     *     System.out.printf("Message %s from consumer %s was read %s times", messageResult[0], messageResult[1], messageResult[2]);
     * }</pre>
     */
    CompletableFuture<Object[][]> xpending(
            String key,
            String group,
            StreamRange start,
            StreamRange end,
            long count,
            StreamPendingOptions options);
}
