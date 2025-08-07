/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.List;
import java.util.Map;

/**
 * Access control user compatibility class for Valkey GLIDE. Based on original Jedis
 * AccessControlUser.
 */
public class AccessControlUser {
    private final Map<String, Object> userInfo;

    public AccessControlUser(Map<String, Object> map) {
        this.userInfo = map;
    }

    public Map<String, Object> getUserInfo() {
        return userInfo;
    }

    public List<String> getFlags() {
        return (List<String>) userInfo.get("flags");
    }

    public List<String> getPasswords() {
        return (List<String>) userInfo.get("passwords");
    }

    public List<String> getCommands() {
        return (List<String>) userInfo.get("commands");
    }

    public List<String> getKeys() {
        return (List<String>) userInfo.get("keys");
    }
}
