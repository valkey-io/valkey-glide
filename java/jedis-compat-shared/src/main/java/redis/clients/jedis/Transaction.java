/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.Batch;
import glide.api.models.commands.RangeOptions.RangeByIndex;
import glide.api.models.commands.SetOptions;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.AbstractSetParams;

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
 * <p><b>Command coverage:</b> The most commonly used Jedis transaction commands are implemented
 * here. Commands that are not yet implemented will throw {@link UnsupportedOperationException}.
 * File an issue or contribute additional commands if you need a command that is missing.
 *
 * @see <a href="https://valkey.io/commands/multi/">valkey.io MULTI</a>
 * @see <a href="https://valkey.io/commands/exec/">valkey.io EXEC</a>
 */
public class Transaction implements Closeable {
    private final Queue<Response<?>> pipelinedResponses = new LinkedList<>();
    private final AbstractGlideJedis jedis;
    private Batch batch;
    private boolean inMulti = false;
    private boolean inWatch = false;
    private boolean broken = false;

    /**
     * Creates a new transaction associated with a Jedis instance.
     *
     * <p>This constructor is called internally by {@link AbstractGlideJedis#multi()}.
     *
     * @param jedis the client instance to associate with this transaction
     */
    public Transaction(AbstractGlideJedis jedis) {
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

        Response<T> response = new Response<>(builder);

        // Add the command to the batch (GLIDE Batch API) before tracking the Response so
        // pipelinedResponses stays aligned with the GLIDE batch if queuing throws.
        try {
            commandExecutor.execute();
        } catch (Exception e) {
            throw new JedisException("Failed to queue command", e);
        }
        pipelinedResponses.add(response);

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

    // ===== String commands =====

    public Response<String> set(String key, String value) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.set(key, value);
                    return null;
                });
    }

    public Response<String> set(String key, String value, AbstractSetParams<?> params) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    SetOptions options = AbstractGlideJedis.convertSetParamsToSetOptions(params);
                    batch.set(key, value, options);
                    return null;
                });
    }

    public Response<String> get(String key) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.get(key);
                    return null;
                });
    }

    public Response<String> getdel(String key) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.getdel(key);
                    return null;
                });
    }

    public Response<String> getset(String key, String value) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.set(key, value, SetOptions.builder().returnOldValue(true).build());
                    return null;
                });
    }

    public Response<Long> del(String... keys) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.del(keys);
                    return null;
                });
    }

    public Response<Long> exists(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.exists(new String[] {key});
                    return null;
                });
    }

    public Response<Long> exists(String... keys) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.exists(keys);
                    return null;
                });
    }

    public Response<Long> incr(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.incr(key);
                    return null;
                });
    }

    public Response<Long> incrBy(String key, long increment) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.incrBy(key, increment);
                    return null;
                });
    }

    public Response<Double> incrByFloat(String key, double increment) {
        return appendCommand(
                BuilderFactory.DOUBLE,
                () -> {
                    batch.incrByFloat(key, increment);
                    return null;
                });
    }

    public Response<Long> decr(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.decr(key);
                    return null;
                });
    }

    public Response<Long> decrBy(String key, long decrement) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.decrBy(key, decrement);
                    return null;
                });
    }

    public Response<Long> append(String key, String value) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.append(key, value);
                    return null;
                });
    }

    public Response<Long> strlen(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.strlen(key);
                    return null;
                });
    }

    public Response<List<String>> mget(String... keys) {
        return appendCommand(
                BuilderFactory.STRING_LIST,
                () -> {
                    batch.mget(keys);
                    return null;
                });
    }

    public Response<String> mset(Map<String, String> keysValues) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.mset(keysValues);
                    return null;
                });
    }

    // ===== Key expiry / TTL commands =====

    public Response<Long> expire(String key, long seconds) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.expire(key, seconds);
                    return null;
                });
    }

    public Response<Long> pexpire(String key, long milliseconds) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.pexpire(key, milliseconds);
                    return null;
                });
    }

    public Response<Long> expireAt(String key, long unixSeconds) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.expireAt(key, unixSeconds);
                    return null;
                });
    }

    public Response<Long> pexpireAt(String key, long unixMilliseconds) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.pexpireAt(key, unixMilliseconds);
                    return null;
                });
    }

    public Response<Long> ttl(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.ttl(key);
                    return null;
                });
    }

    public Response<Long> pttl(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.pttl(key);
                    return null;
                });
    }

    public Response<Long> persist(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.persist(key);
                    return null;
                });
    }

    public Response<String> rename(String key, String newkey) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.rename(key, newkey);
                    return null;
                });
    }

    public Response<Long> unlink(String... keys) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.unlink(keys);
                    return null;
                });
    }

    // ===== Hash commands =====

    public Response<Long> hset(String key, String field, String value) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.hset(key, Collections.singletonMap(field, value));
                    return null;
                });
    }

    public Response<Long> hset(String key, Map<String, String> hash) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.hset(key, hash);
                    return null;
                });
    }

    public Response<String> hget(String key, String field) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.hget(key, field);
                    return null;
                });
    }

    public Response<List<String>> hmget(String key, String... fields) {
        return appendCommand(
                BuilderFactory.STRING_LIST,
                () -> {
                    batch.hmget(key, fields);
                    return null;
                });
    }

    public Response<Map<String, String>> hgetAll(String key) {
        return appendCommand(
                BuilderFactory.STRING_MAP,
                () -> {
                    batch.hgetall(key);
                    return null;
                });
    }

    public Response<Set<String>> hkeys(String key) {
        return appendCommand(
                BuilderFactory.STRING_SET,
                () -> {
                    batch.hkeys(key);
                    return null;
                });
    }

    public Response<List<String>> hvals(String key) {
        return appendCommand(
                BuilderFactory.STRING_LIST,
                () -> {
                    batch.hvals(key);
                    return null;
                });
    }

    public Response<Long> hlen(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.hlen(key);
                    return null;
                });
    }

    public Response<Long> hdel(String key, String... fields) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.hdel(key, fields);
                    return null;
                });
    }

    public Response<Boolean> hexists(String key, String field) {
        return appendCommand(
                BuilderFactory.BOOLEAN,
                () -> {
                    batch.hexists(key, field);
                    return null;
                });
    }

    public Response<Long> hincrBy(String key, String field, long value) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.hincrBy(key, field, value);
                    return null;
                });
    }

    public Response<Double> hincrByFloat(String key, String field, double value) {
        return appendCommand(
                BuilderFactory.DOUBLE,
                () -> {
                    batch.hincrByFloat(key, field, value);
                    return null;
                });
    }

    // ===== List commands =====

    public Response<Long> lpush(String key, String... strings) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.lpush(key, strings);
                    return null;
                });
    }

    public Response<Long> rpush(String key, String... strings) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.rpush(key, strings);
                    return null;
                });
    }

    public Response<List<String>> lrange(String key, long start, long stop) {
        return appendCommand(
                BuilderFactory.STRING_LIST,
                () -> {
                    batch.lrange(key, start, stop);
                    return null;
                });
    }

    public Response<Long> llen(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.llen(key);
                    return null;
                });
    }

    public Response<String> lindex(String key, long index) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.lindex(key, index);
                    return null;
                });
    }

    public Response<String> lpop(String key) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.lpop(key);
                    return null;
                });
    }

    public Response<String> rpop(String key) {
        return appendCommand(
                BuilderFactory.STRING,
                () -> {
                    batch.rpop(key);
                    return null;
                });
    }

    // ===== Set commands =====

    public Response<Long> sadd(String key, String... members) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.sadd(key, members);
                    return null;
                });
    }

    public Response<Long> srem(String key, String... members) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.srem(key, members);
                    return null;
                });
    }

    public Response<Set<String>> smembers(String key) {
        return appendCommand(
                BuilderFactory.STRING_SET,
                () -> {
                    batch.smembers(key);
                    return null;
                });
    }

    public Response<Long> scard(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.scard(key);
                    return null;
                });
    }

    public Response<Boolean> sismember(String key, String member) {
        return appendCommand(
                BuilderFactory.BOOLEAN,
                () -> {
                    batch.sismember(key, member);
                    return null;
                });
    }

    // ===== Sorted set commands =====

    public Response<Long> zadd(String key, double score, String member) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.zadd(key, Collections.singletonMap(member, score));
                    return null;
                });
    }

    public Response<Long> zadd(String key, Map<String, Double> scoreMembers) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.zadd(key, scoreMembers);
                    return null;
                });
    }

    public Response<Long> zrem(String key, String... members) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.zrem(key, members);
                    return null;
                });
    }

    public Response<Long> zcard(String key) {
        return appendCommand(
                BuilderFactory.LONG,
                () -> {
                    batch.zcard(key);
                    return null;
                });
    }

    public Response<Double> zscore(String key, String member) {
        return appendCommand(
                BuilderFactory.DOUBLE,
                () -> {
                    batch.zscore(key, member);
                    return null;
                });
    }

    public Response<Double> zincrby(String key, double increment, String member) {
        return appendCommand(
                BuilderFactory.DOUBLE,
                () -> {
                    batch.zincrby(key, increment, member);
                    return null;
                });
    }

    public Response<List<String>> zrange(String key, long start, long stop) {
        return appendCommand(
                BuilderFactory.STRING_LIST,
                () -> {
                    batch.zrange(key, new RangeByIndex(start, stop));
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
