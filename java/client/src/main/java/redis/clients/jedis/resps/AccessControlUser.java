/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;

/** AccessControlUser compatibility stub for Valkey GLIDE wrapper. */
public class AccessControlUser {
    private final List<String> flags;
    private final List<String> keys;
    private final List<String> passwords;
    private final List<String> channels;
    private final String commands;

    public AccessControlUser() {
        this.flags = Collections.emptyList();
        this.keys = Collections.emptyList();
        this.passwords = Collections.emptyList();
        this.channels = Collections.emptyList();
        this.commands = "";
    }

    public List<String> getFlags() {
        return flags;
    }

    public List<String> getKeys() {
        return keys;
    }

    public List<String> getPassword() {
        return passwords;
    }

    public List<String> getChannels() {
        return channels;
    }

    public String getCommands() {
        return commands;
    }
}
