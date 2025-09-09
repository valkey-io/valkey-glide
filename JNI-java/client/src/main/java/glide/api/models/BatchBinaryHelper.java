package glide.api.models;

import glide.api.models.GlideString;
import glide.internal.protocol.BinaryCommand;
import glide.api.utils.BinaryCommandArgsBuilder;
import static glide.api.models.commands.RequestType.*;

/**
 * Helper class for adding binary-safe commands to Batch operations.
 * This class provides methods that preserve binary data integrity by using
 * BinaryCommand instead of converting GlideString to String.
 * 
 * <p>Usage example:
 * <pre>{@code
 * Batch batch = new Batch();
 * BatchBinaryHelper.exists(batch, binaryKey1, binaryKey2);
 * BatchBinaryHelper.mget(batch, binaryKeys);
 * }</pre>
 */
public class BatchBinaryHelper {
    
    /**
     * Add EXISTS command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T exists(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(Exists, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add MGET command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T mget(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(MGet, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add UNLINK command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T unlink(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(UNLINK, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add DEL command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T del(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(Del, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add LPUSH command with binary-safe key and elements.
     */
    public static <T extends BaseBatch<T>> T lpush(T batch, GlideString key, GlideString... elements) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(LPush, key, elements);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add RPUSH command with binary-safe key and elements.
     */
    public static <T extends BaseBatch<T>> T rpush(T batch, GlideString key, GlideString... elements) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(RPush, key, elements);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add SADD command with binary-safe key and members.
     */
    public static <T extends BaseBatch<T>> T sadd(T batch, GlideString key, GlideString... members) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(SAdd, key, members);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add SREM command with binary-safe key and members.
     */
    public static <T extends BaseBatch<T>> T srem(T batch, GlideString key, GlideString... members) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(SRem, key, members);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add HMGET command with binary-safe key and fields.
     */
    public static <T extends BaseBatch<T>> T hmget(T batch, GlideString key, GlideString... fields) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(HMGet, key, fields);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add HDEL command with binary-safe key and fields.
     */
    public static <T extends BaseBatch<T>> T hdel(T batch, GlideString key, GlideString... fields) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(HDel, key, fields);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add PFADD command with binary-safe key and elements.
     */
    public static <T extends BaseBatch<T>> T pfadd(T batch, GlideString key, GlideString... elements) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithKeyAndFields(PfAdd, key, elements);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add PFCOUNT command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T pfcount(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(PfCount, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add SDIFF command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T sdiff(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(SDiff, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add SINTER command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T sinter(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(SInter, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add SUNION command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T sunion(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgs(SUnion, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add ZDIFF command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T zdiff(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithNumkeys(ZDiff, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add ZINTER command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T zinter(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithNumkeys(ZInter, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add ZUNION command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T zunion(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithNumkeys(ZUnion, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add ZINTERCARD command with binary-safe keys.
     */
    public static <T extends BaseBatch<T>> T zintercard(T batch, GlideString... keys) {
        BinaryCommand command = BinaryCommandArgsBuilder.buildBinaryArgsWithNumkeys(ZInterCard, keys);
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add SET command with binary-safe key and value.
     */
    public static <T extends BaseBatch<T>> T set(T batch, GlideString key, GlideString value) {
        BinaryCommand command = new BinaryCommand(SET);
        command.addArgument(key.getBytes());
        command.addArgument(value.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add GET command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T get(T batch, GlideString key) {
        BinaryCommand command = new BinaryCommand(GET);
        command.addArgument(key.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add HGET command with binary-safe key and field.
     */
    public static <T extends BaseBatch<T>> T hget(T batch, GlideString key, GlideString field) {
        BinaryCommand command = new BinaryCommand(HGet);
        command.addArgument(key.getBytes());
        command.addArgument(field.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add HSET command with binary-safe key, field, and value.
     */
    public static <T extends BaseBatch<T>> T hset(T batch, GlideString key, GlideString field, GlideString value) {
        BinaryCommand command = new BinaryCommand(HSet);
        command.addArgument(key.getBytes());
        command.addArgument(field.getBytes());
        command.addArgument(value.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add LRANGE command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T lrange(T batch, GlideString key, long start, long end) {
        BinaryCommand command = new BinaryCommand(LRange);
        command.addArgument(key.getBytes());
        command.addArgument(String.valueOf(start));
        command.addArgument(String.valueOf(end));
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add LLEN command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T llen(T batch, GlideString key) {
        BinaryCommand command = new BinaryCommand(LLen);
        command.addArgument(key.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add EXPIRE command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T expire(T batch, GlideString key, long seconds) {
        BinaryCommand command = new BinaryCommand(EXPIRE);
        command.addArgument(key.getBytes());
        command.addArgument(String.valueOf(seconds));
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add TTL command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T ttl(T batch, GlideString key) {
        BinaryCommand command = new BinaryCommand(TTL);
        command.addArgument(key.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add TYPE command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T type(T batch, GlideString key) {
        BinaryCommand command = new BinaryCommand(Type);
        command.addArgument(key.getBytes());
        batch.addCommand(command);
        return batch;
    }
    
    /**
     * Add PERSIST command with binary-safe key.
     */
    public static <T extends BaseBatch<T>> T persist(T batch, GlideString key) {
        BinaryCommand command = new BinaryCommand(Persist);
        command.addArgument(key.getBytes());
        batch.addCommand(command);
        return batch;
    }
}