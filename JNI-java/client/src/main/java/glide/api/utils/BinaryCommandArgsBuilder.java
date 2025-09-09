package glide.api.utils;

import glide.api.models.GlideString;
import glide.internal.protocol.BinaryCommand;
import glide.internal.protocol.BinaryCommand.BinaryValue;

/**
 * Utility class for building binary-safe command arguments.
 * This class preserves binary data integrity by avoiding string conversion
 * that would corrupt non-UTF8 byte sequences.
 * 
 * <p>Unlike {@link CommandArgsBuilder} which converts GlideString to String
 * (corrupting binary data), this class maintains exact byte representations.
 */
public class BinaryCommandArgsBuilder {
    
    /**
     * Build binary command for commands that take numkeys followed by keys.
     * Pattern: [numkeys, key1, key2, ..., additional args]
     * Used by: ZDIFF, ZINTER, ZUNION, SDIFF, SINTER, SUNION, etc.
     * 
     * @param commandType The Redis/Valkey command type
     * @param keys The array of GlideString keys
     * @param additionalArgs Additional string arguments to append
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgsWithNumkeys(
            String commandType, 
            GlideString[] keys, 
            String... additionalArgs) {
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add numkeys as the first argument
        command.addArgument(String.valueOf(keys.length));
        
        // Add all keys as binary data
        for (GlideString key : keys) {
            command.addArgument(key.getBytes());
        }
        
        // Add any additional string arguments
        for (String arg : additionalArgs) {
            command.addArgument(arg);
        }
        
        return command;
    }
    
    /**
     * Build binary command for commands with destination and numkeys pattern.
     * Pattern: [destination, numkeys, key1, key2, ..., additional args]
     * Used by: ZDIFFSTORE, ZINTERSTORE, ZUNIONSTORE, SDIFFSTORE, SINTERSTORE, SUNIONSTORE, etc.
     * 
     * @param commandType The Redis/Valkey command type
     * @param destination The destination key
     * @param keys The array of source keys
     * @param additionalArgs Additional string arguments to append
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgsWithDestAndNumkeys(
            String commandType,
            GlideString destination, 
            GlideString[] keys, 
            String... additionalArgs) {
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add destination as binary data
        command.addArgument(destination.getBytes());
        
        // Add numkeys
        command.addArgument(String.valueOf(keys.length));
        
        // Add all keys as binary data
        for (GlideString key : keys) {
            command.addArgument(key.getBytes());
        }
        
        // Add any additional string arguments
        for (String arg : additionalArgs) {
            command.addArgument(arg);
        }
        
        return command;
    }
    
    /**
     * Build binary command for blocking commands with timeout and numkeys.
     * Pattern: [timeout, numkeys, key1, key2, ..., additional args]
     * Used by: BLMPOP, BZMPOP, etc.
     * 
     * @param commandType The Redis/Valkey command type
     * @param timeout The timeout value
     * @param keys The array of GlideString keys
     * @param additionalArgs Additional string arguments to append
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgsWithTimeoutAndNumkeys(
            String commandType,
            double timeout, 
            GlideString[] keys, 
            String... additionalArgs) {
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add timeout
        command.addArgument(String.valueOf(timeout));
        
        // Add numkeys
        command.addArgument(String.valueOf(keys.length));
        
        // Add all keys as binary data
        for (GlideString key : keys) {
            command.addArgument(key.getBytes());
        }
        
        // Add any additional string arguments
        for (String arg : additionalArgs) {
            command.addArgument(arg);
        }
        
        return command;
    }
    
    /**
     * Build binary command for commands that take a key and multiple fields/members.
     * Pattern: [key, field1, field2, ..., additional args]
     * Used by: HDEL, HMGET, SADD, SREM, ZADD, LPUSH, RPUSH, etc.
     * 
     * @param commandType The Redis/Valkey command type
     * @param key The key
     * @param fields The array of fields/members/elements
     * @param additionalArgs Additional string arguments to append
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgsWithKeyAndFields(
            String commandType,
            GlideString key,
            GlideString[] fields,
            String... additionalArgs) {
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add key as binary data
        command.addArgument(key.getBytes());
        
        // Add all fields/members as binary data
        for (GlideString field : fields) {
            command.addArgument(field.getBytes());
        }
        
        // Add any additional string arguments
        for (String arg : additionalArgs) {
            command.addArgument(arg);
        }
        
        return command;
    }
    
    /**
     * Build binary command for commands that take multiple keys without numkeys.
     * Pattern: [key1, key2, ..., additional args]
     * Used by: DEL, EXISTS, TOUCH, UNLINK, WATCH, etc.
     * 
     * @param commandType The Redis/Valkey command type
     * @param keys The array of keys
     * @param additionalArgs Additional string arguments to append
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgs(
            String commandType,
            GlideString[] keys,
            String... additionalArgs) {
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add all keys as binary data
        for (GlideString key : keys) {
            command.addArgument(key.getBytes());
        }
        
        // Add any additional string arguments
        for (String arg : additionalArgs) {
            command.addArgument(arg);
        }
        
        return command;
    }
    
    /**
     * Build binary command for MSET/MSETNX style commands.
     * Pattern: [key1, value1, key2, value2, ...]
     * 
     * @param commandType The Redis/Valkey command type (MSET or MSETNX)
     * @param keyValuePairs Array of key-value pairs (must be even length)
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgsForMSet(
            String commandType,
            GlideString... keyValuePairs) {
        
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("MSET requires even number of arguments (key-value pairs)");
        }
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add all key-value pairs as binary data
        for (GlideString item : keyValuePairs) {
            command.addArgument(item.getBytes());
        }
        
        return command;
    }
    
    /**
     * Build binary command for ZADD with score-member pairs.
     * Pattern: [key, score1, member1, score2, member2, ..., additional args]
     * 
     * @param commandType The Redis/Valkey command type
     * @param key The sorted set key
     * @param scoreMembers Array of score-member pairs (alternating doubles and GlideStrings)
     * @param additionalArgs Additional string arguments (like NX, XX, CH, INCR)
     * @return BinaryCommand with preserved binary data
     */
    public static BinaryCommand buildBinaryArgsForZAdd(
            String commandType,
            GlideString key,
            Object[] scoreMembers,
            String... additionalArgs) {
        
        BinaryCommand command = new BinaryCommand(commandType);
        
        // Add key as binary data
        command.addArgument(key.getBytes());
        
        // Add any additional options (NX, XX, etc.) before score-member pairs
        for (String arg : additionalArgs) {
            command.addArgument(arg);
        }
        
        // Add score-member pairs
        for (int i = 0; i < scoreMembers.length; i += 2) {
            // Score (as string)
            command.addArgument(String.valueOf(scoreMembers[i]));
            // Member (as binary data)
            if (scoreMembers[i + 1] instanceof GlideString) {
                command.addArgument(((GlideString) scoreMembers[i + 1]).getBytes());
            } else {
                command.addArgument(scoreMembers[i + 1].toString());
            }
        }
        
        return command;
    }
}