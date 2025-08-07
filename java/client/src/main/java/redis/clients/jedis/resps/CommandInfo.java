/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;

/** CommandInfo compatibility stub for Valkey GLIDE wrapper. */
public class CommandInfo {
    private final long arity;
    private final List<String> flags;
    private final long firstKey;
    private final long lastKey;
    private final long step;
    private final List<String> aclCategories;
    private final List<String> tips;
    private final List<String> subcommands;

    public CommandInfo() {
        this.arity = 0;
        this.flags = Collections.emptyList();
        this.firstKey = 0;
        this.lastKey = 0;
        this.step = 0;
        this.aclCategories = Collections.emptyList();
        this.tips = Collections.emptyList();
        this.subcommands = Collections.emptyList();
    }

    public long getArity() {
        return arity;
    }

    public List<String> getFlags() {
        return flags;
    }

    public long getFirstKey() {
        return firstKey;
    }

    public long getLastKey() {
        return lastKey;
    }

    public long getStep() {
        return step;
    }

    public List<String> getAclCategories() {
        return aclCategories;
    }

    public List<String> getTips() {
        return tips;
    }

    public List<String> getSubcommands() {
        return subcommands;
    }
}
