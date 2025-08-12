/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AccessControlUser {

    private final List<String> flags = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<String> passwords = new ArrayList<>();
    private final List<String> channels = new ArrayList<>();
    private String commands;

    public AccessControlUser() {}

    public void addFlag(String flag) {
        flags.add(flag);
    }

    public List<String> getFlags() {
        return Collections.unmodifiableList(
                flags); // ✅ Return unmodifiable view to prevent external modification
    }

    public void addKey(String key) {
        keys.add(key);
    }

    public List<String> getKeys() {
        return Collections.unmodifiableList(
                keys); // ✅ Return unmodifiable view to prevent external modification
    }

    public void addKeys(String keys) {
        if (!keys.isEmpty()) {
            this.keys.addAll(Arrays.asList(keys.split(" ")));
        }
    }

    public void addPassword(String password) {
        passwords.add(password);
    }

    public List<String> getPassword() {
        return Collections.unmodifiableList(
                passwords); // ✅ Return unmodifiable view to prevent external modification
    }

    public void addChannel(String channel) {
        channels.add(channel);
    }

    public List<String> getChannels() {
        return Collections.unmodifiableList(
                channels); // ✅ Return unmodifiable view to prevent external modification
    }

    public void addChannels(String channels) {
        if (!channels.isEmpty()) {
            this.channels.addAll(Arrays.asList(channels.split(" ")));
        }
    }

    public String getCommands() {
        return commands;
    }

    public void setCommands(String commands) {
        this.commands = commands;
    }

    @Override
    public String toString() {
        return "AccessControlUser{"
                + "flags="
                + flags
                + ", passwords="
                + passwords
                + ", commands='"
                + commands
                + "', keys='"
                + keys
                + "', channels='"
                + channels
                + "'}";
    }
}
