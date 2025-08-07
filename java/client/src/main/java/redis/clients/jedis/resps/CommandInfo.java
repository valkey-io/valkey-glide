/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.List;
import java.util.Map;

/**
 * Command information compatibility class for Valkey GLIDE. Based on original Jedis CommandInfo.
 */
public class CommandInfo {
    private final Map<String, Object> commandInfo;

    public CommandInfo(Map<String, Object> map) {
        this.commandInfo = map;
    }

    public Map<String, Object> getCommandInfo() {
        return commandInfo;
    }

    public String getName() {
        return (String) commandInfo.get("name");
    }

    public int getArity() {
        return (Integer) commandInfo.get("arity");
    }

    public List<String> getFlags() {
        return (List<String>) commandInfo.get("flags");
    }

    public int getFirstKeyPos() {
        return (Integer) commandInfo.get("first_key_pos");
    }

    public int getLastKeyPos() {
        return (Integer) commandInfo.get("last_key_pos");
    }

    public int getKeyStepCount() {
        return (Integer) commandInfo.get("key_step_count");
    }
}
