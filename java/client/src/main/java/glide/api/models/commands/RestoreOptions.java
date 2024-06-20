/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * Optional arguments to {@link GenericBaseCommands#restore(byte[], long, byte[], RestoreOptions)}
 *
 * @see <a href="https://valkey.io/commands/restore/">valkey.io</a>
 */
@Getter
@Builder
public final class RestoreOptions {
    /** <code>REPLACE</code> subcommand string to replace existing key */
    public static final String REPLACE_REDIS_API = "REPLACE";

    /**
     * <code>ABSTTL</code> subcommand string to represent absolute timestamp (in milliseconds) for TTL
     */
    public static final String ABSTTL_REDIS_API = "ABSTTL";

    /** <code>IDELTIME</code> subcommand string to set Object Idletime */
    public static final String IDLETIME_REDIS_API = "IDLETIME";

    /** <code>FREQ</code> subcommand string to set Object Frequency */
    public static final String FREQ_REDIS_API = "FREQ";

    /** When `true`, it represents <code>REPLACE</code> keyword has been used */
    @Builder.Default private boolean hasReplace = false;

    /** When `true`, it represents <code>ABSTTL</code> keyword has been used */
    @Builder.Default private boolean hasAbsttl = false;

    /** It represents the idletime of object */
    @Builder.Default private Long idletime = null;

    /** It represents the frequency of object */
    @Builder.Default private Long frequency = null;

    /**
     * Creates the argument to be used in {@link GenericBaseCommands#restore(byte[], long, byte[],
     * RestoreOptions)}
     *
     * @return a byte array that holds the sub commands and their arguments.
     */
    public List<byte[]> toArgs(byte[] key, long ttl, byte[] value) {
        List<byte[]> resultList = new ArrayList<>();

        resultList.add(key);
        resultList.add(Long.toString(ttl).getBytes());
        resultList.add(value);

        if (hasReplace) {
            resultList.add(REPLACE_REDIS_API.getBytes());
        }

        if (hasAbsttl) {
            resultList.add(ABSTTL_REDIS_API.getBytes());
        }

        if (idletime != null) {
            resultList.add(IDLETIME_REDIS_API.getBytes());
            resultList.add(Long.toString(idletime).getBytes());
        }

        if (frequency != null) {
            resultList.add(FREQ_REDIS_API.getBytes());
            resultList.add(Long.toString(frequency).getBytes());
        }

        return resultList;
    }

    /** Custom setter methods for replace and absttl */
    public static class RestoreOptionsBuilder {
        public RestoreOptionsBuilder replace() {
            return hasReplace(true);
        }

        public RestoreOptionsBuilder absttl() {
            return hasAbsttl(true);
        }
    }
}
