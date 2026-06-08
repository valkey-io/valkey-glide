/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.Batch;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * A transaction implementation that queues Redis commands for atomic execution.
 *
 * <p>This class provides Jedis-compatible transaction support backed by GLIDE's Batch API. Commands
 * are queued on this object and executed atomically when {@link #exec()} is called. You must use
 * the returned Transaction (e.g. {@code t.set()}, {@code t.get()}) to queue commands; calling
 * methods on the Jedis instance after {@code multi()} does not add to the transaction.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Transaction t = jedis.multi();
 * Response<String> r1 = t.set("key", "value");
 * Response<String> r2 = t.get("key");
 * t.exec();
 * String value = r2.get(); // Retrieve the actual value after exec()
 * }</pre>
 *
 * @see <a href="https://valkey.io/commands/multi/">valkey.io MULTI</a>
 * @see <a href="https://valkey.io/commands/exec/">valkey.io EXEC</a>
 */
public class Transaction implements Closeable {
    private final Queue<Response<?>> pipelinedResponses = new LinkedList<>();
    private final Jedis jedis;
    private Batch batch;
    private boolean inMulti = false;
    private boolean inWatch = false;
    private boolean broken = false;

    /**
     * Creates a new transaction associated with a Jedis instance.
     *
     * <p>This constructor is called internally by {@link Jedis#multi()}.
     *
     * @param jedis the Jedis instance to associate with this transaction
     */
    public Transaction(Jedis jedis) {
        this.jedis = jedis;
        this.batch = new Batch(true); // true = atomic (transaction)
        this.inMulti = true;
    }

    /**
     * Watches the given keys to determine execution of the transaction.
     *
     * <p>Must be called before {@link #multi()}.
     *
     * @param keys the keys to watch
     * @return "OK" if successful
     * @throws IllegalStateException if called after MULTI
     */
    public String watch(String... keys) {
        if (inMulti) {
            throw new IllegalStateException("WATCH must be called before MULTI");
        }
        String status = jedis.watch(keys);
        inWatch = true;
        return status;
    }

    /**
     * Watches the given keys to determine execution of the transaction.
     *
     * <p>Must be called before {@link #multi()}.
     *
     * @param keys the keys to watch
     * @return "OK" if successful
     * @throws IllegalStateException if called after MULTI
     */
    public String watch(byte[]... keys) {
        if (inMulti) {
            throw new IllegalStateException("WATCH must be called before MULTI");
        }
        String status = jedis.watch(keys);
        inWatch = true;
        return status;
    }

    /**
     * Unwatches all previously watched keys.
     *
     * @return "OK" if successful
     */
    public String unwatch() {
        String status = jedis.unwatch();
        inWatch = false;
        return status;
    }

    /**
     * Marks the start of a transaction block.
     *
     * <p>This is automatically called by the constructor for compatibility with {@code Transaction t
     * = jedis.multi()}.
     */
    public void multi() {
        if (inMulti) {
            throw new IllegalStateException("Already in MULTI");
        }
        this.batch = new Batch(true);
        this.inMulti = true;
    }

    /**
     * Queues a command in the transaction and returns a Response for deferred access.
     *
     * @param <T> the response type
     * @param builder the builder to convert raw response data
     * @param commandExecutor a function that executes the command and returns the result
     * @return a Response object that will contain the result after exec()
     */
    private <T> Response<T> appendCommand(Builder<T> builder, CommandExecutor<T> commandExecutor) {
        if (!inMulti) {
            throw new IllegalStateException("Not in MULTI");
        }

        // Queue the command executor for later execution
        Response<T> response = new Response<>(builder);
        pipelinedResponses.add(response);

        // Add the command to the batch (GLIDE Batch API)
        try {
            commandExecutor.execute();
        } catch (Exception e) {
            throw new JedisException("Failed to queue command", e);
        }

        return response;
    }

    /**
     * Executes all queued commands in the transaction.
     *
     * @return list of replies, one for each command in the transaction, or null if transaction was
     *     aborted
     * @throws JedisException if not in a transaction or operation fails
     */
    public List<Object> exec() {
        if (!inMulti) {
            throw new IllegalStateException("EXEC without MULTI");
        }

        try {
            // Execute the batch using GLIDE
            Object[] results = jedis.getGlideClient().exec(batch, false).get();

            if (results == null) {
                // Transaction was aborted (e.g., due to WATCH)
                pipelinedResponses.clear();
                return null;
            }

            // Build responses from raw results
            List<Object> formatted = new ArrayList<>(results.length);
            int index = 0;
            for (Response<?> response : pipelinedResponses) {
                if (index < results.length) {
                    try {
                        response.set(results[index]);
                        formatted.add(response.get());
                    } catch (JedisDataException e) {
                        formatted.add(e);
                    }
                    index++;
                }
            }

            return formatted;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JedisConnectionException("Transaction interrupted", e);
        } catch (Exception e) {
            broken = true;
            jedis.markBrokenIfPooledConnectionFailure(e);
            throw new JedisException("Failed to execute transaction", e);
        } finally {
            inMulti = false;
            inWatch = false;
            pipelinedResponses.clear();
            batch = null;
            jedis.resetState();
        }
    }

    /**
     * Discards all commands queued in the transaction.
     *
     * @return "OK" if successful
     * @throws IllegalStateException if not in a transaction
     */
    public String discard() {
        if (!inMulti) {
            throw new IllegalStateException("DISCARD without MULTI");
        }

        try {
            pipelinedResponses.clear();
            batch = null;
            return "OK";
        } finally {
            inMulti = false;
            inWatch = false;
            jedis.resetState();
        }
    }

    /** Closes the transaction, discarding any queued commands. */
    @Override
    public void close() {
        if (broken) {
            return;
        }
        if (inMulti) {
            discard();
        } else if (inWatch) {
            unwatch();
        }
    }

    // Delegate all Redis commands to Jedis, but wrap them to queue in the batch
    // For now, we'll implement key commands. More can be added as needed.

    /**
     * Set the string value of a key.
     *
     * @param key the key
     * @param value the value
     * @return a Response containing the result
     */
    public Response<String> set(String key, String value) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.set(key, value);
                    return null;
                });
    }

    /**
     * Get the value of a key.
     *
     * @param key the key
     * @return a Response containing the value
     */
    public Response<String> get(String key) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.get(key);
                    return null;
                });
    }

    /**
     * Delete one or more keys.
     *
     * @param keys the keys to delete
     * @return a Response containing the number of keys deleted
     */
    public Response<Long> del(String... keys) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.del(keys);
                    return null;
                });
    }

    /**
     * Functional interface for command execution.
     *
     * @param <T> the return type
     */
    @FunctionalInterface
    private interface CommandExecutor<T> {
        T execute() throws Exception;
    }
}
