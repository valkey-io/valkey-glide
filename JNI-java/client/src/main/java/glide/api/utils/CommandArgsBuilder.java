package glide.api.utils;

import glide.api.models.GlideString;

/**
 * Utility class for building command arguments arrays.
 * Consolidates repetitive argument building patterns across commands.
 */
public class CommandArgsBuilder {
    
    /**
     * Build arguments for commands that take numkeys followed by keys.
     * Pattern: [numkeys, key1, key2, ..., additional args]
     * Used by: ZDIFF, ZINTER, ZUNION, etc.
     * 
     * @param keys The array of keys
     * @param additionalArgs Additional arguments to append
     * @return Complete argument array
     */
    public static String[] buildArgsWithNumkeys(String[] keys, String... additionalArgs) {
        String[] args = new String[1 + keys.length + additionalArgs.length];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        if (additionalArgs.length > 0) {
            System.arraycopy(additionalArgs, 0, args, 1 + keys.length, additionalArgs.length);
        }
        return args;
    }

    /**
     * Build arguments for commands that take numkeys followed by GlideString keys.
     * 
     * @param keys The array of GlideString keys
     * @param additionalArgs Additional arguments to append
     * @return Complete argument array
     */
    public static String[] buildArgsWithNumkeys(GlideString[] keys, String... additionalArgs) {
        String[] args = new String[1 + keys.length + additionalArgs.length];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        if (additionalArgs.length > 0) {
            System.arraycopy(additionalArgs, 0, args, 1 + keys.length, additionalArgs.length);
        }
        return args;
    }

    /**
     * Build arguments for commands with destination and numkeys pattern.
     * Pattern: [destination, numkeys, key1, key2, ..., additional args]
     * Used by: ZDIFFSTORE, ZINTERSTORE, ZUNIONSTORE, etc.
     * 
     * @param destination The destination key
     * @param keys The array of source keys
     * @param additionalArgs Additional arguments to append
     * @return Complete argument array
     */
    public static String[] buildArgsWithDestAndNumkeys(String destination, String[] keys, String... additionalArgs) {
        String[] args = new String[2 + keys.length + additionalArgs.length];
        args[0] = destination;
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        if (additionalArgs.length > 0) {
            System.arraycopy(additionalArgs, 0, args, 2 + keys.length, additionalArgs.length);
        }
        return args;
    }

    /**
     * Build arguments for commands with destination and numkeys pattern (GlideString version).
     * 
     * @param destination The destination key
     * @param keys The array of source keys
     * @param additionalArgs Additional arguments to append
     * @return Complete argument array
     */
    public static String[] buildArgsWithDestAndNumkeys(GlideString destination, GlideString[] keys, String... additionalArgs) {
        String[] args = new String[2 + keys.length + additionalArgs.length];
        args[0] = destination.toString();
        args[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 2] = keys[i].toString();
        }
        if (additionalArgs.length > 0) {
            System.arraycopy(additionalArgs, 0, args, 2 + keys.length, additionalArgs.length);
        }
        return args;
    }

    /**
     * Build arguments for blocking commands with timeout and numkeys.
     * Pattern: [timeout, numkeys, key1, key2, ..., additional args]
     * Used by: BLMPOP, BZMPOP, etc.
     * 
     * @param timeout The timeout value
     * @param keys The array of keys
     * @param additionalArgs Additional arguments to append
     * @return Complete argument array
     */
    public static String[] buildArgsWithTimeoutAndNumkeys(double timeout, String[] keys, String... additionalArgs) {
        String[] args = new String[2 + keys.length + additionalArgs.length];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        if (additionalArgs.length > 0) {
            System.arraycopy(additionalArgs, 0, args, 2 + keys.length, additionalArgs.length);
        }
        return args;
    }

    /**
     * Build arguments for blocking commands with timeout and numkeys (GlideString version).
     * 
     * @param timeout The timeout value
     * @param keys The array of GlideString keys
     * @param additionalArgs Additional arguments to append
     * @return Complete argument array
     */
    public static String[] buildArgsWithTimeoutAndNumkeys(double timeout, GlideString[] keys, String... additionalArgs) {
        String[] args = new String[2 + keys.length + additionalArgs.length];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 2] = keys[i].toString();
        }
        if (additionalArgs.length > 0) {
            System.arraycopy(additionalArgs, 0, args, 2 + keys.length, additionalArgs.length);
        }
        return args;
    }

    /**
     * Convert GlideString array to String array.
     * 
     * @param glideStrings Array of GlideString objects
     * @return Array of String objects
     */
    public static String[] toStringArray(GlideString[] glideStrings) {
        if (glideStrings == null) return null;
        String[] strings = new String[glideStrings.length];
        for (int i = 0; i < glideStrings.length; i++) {
            strings[i] = glideStrings[i] != null ? glideStrings[i].toString() : null;
        }
        return strings;
    }
}