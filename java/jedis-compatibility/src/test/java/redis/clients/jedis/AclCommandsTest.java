/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.resps.AccessControlLogEntry;
import redis.clients.jedis.resps.AccessControlUser;

public class AclCommandsTest {

    @Test
    public void testAccessControlLogEntryWithMinimalMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(AccessControlLogEntry.COUNT, 1L);
        map.put(AccessControlLogEntry.REASON, "auth");
        map.put(AccessControlLogEntry.CONTEXT, "toplevel");
        map.put(AccessControlLogEntry.OBJECT, "AUTH");
        map.put(AccessControlLogEntry.USERNAME, "testuser");
        map.put(AccessControlLogEntry.AGE_SECONDS, "1.0");
        map.put(AccessControlLogEntry.CLIENT_INFO, "");
        assertDoesNotThrow(() -> new AccessControlLogEntry(map));
        AccessControlLogEntry entry = new AccessControlLogEntry(map);
        assertEquals(1L, entry.getCount());
        assertEquals("auth", entry.getReason());
        assertEquals("toplevel", entry.getContext());
        assertEquals("AUTH", entry.getObject());
        assertEquals("testuser", entry.getUsername());
        assertEquals("1.0", entry.getAgeSeconds());
        assertEquals(0L, entry.getEntryId());
        assertEquals(0L, entry.getTimestampCreated());
        assertEquals(0L, entry.getTimestampLastUpdated());
        assertNotNull(entry.getClientInfo());
        assertTrue(entry.getClientInfo().isEmpty());
    }

    @Test
    public void testAccessControlLogEntryWithNullClientInfo() {
        Map<String, Object> map = new HashMap<>();
        map.put(AccessControlLogEntry.COUNT, 1L);
        map.put(AccessControlLogEntry.REASON, "command");
        map.put(AccessControlLogEntry.CONTEXT, "toplevel");
        map.put(AccessControlLogEntry.OBJECT, "GET");
        map.put(AccessControlLogEntry.USERNAME, "default");
        map.put(AccessControlLogEntry.AGE_SECONDS, "0.5");
        assertDoesNotThrow(() -> new AccessControlLogEntry(map));
        AccessControlLogEntry entry = new AccessControlLogEntry(map);
        assertNotNull(entry.getClientInfo());
        assertTrue(entry.getClientInfo().isEmpty());
    }

    @Test
    public void testAccessControlLogEntryWithFullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(AccessControlLogEntry.COUNT, 2L);
        map.put(AccessControlLogEntry.REASON, "key");
        map.put(AccessControlLogEntry.CONTEXT, "toplevel");
        map.put(AccessControlLogEntry.OBJECT, "userkey");
        map.put(AccessControlLogEntry.USERNAME, "limited");
        map.put(AccessControlLogEntry.AGE_SECONDS, "10.1");
        map.put(AccessControlLogEntry.CLIENT_INFO, "id=1 addr=127.0.0.1:12345 fd=6");
        map.put(AccessControlLogEntry.ENTRY_ID, 5L);
        map.put(AccessControlLogEntry.TIMESTAMP_CREATED, 1675361492408L);
        map.put(AccessControlLogEntry.TIMESTAMP_LAST_UPDATED, 1675361492408L);
        AccessControlLogEntry entry = new AccessControlLogEntry(map);
        assertEquals(2L, entry.getCount());
        assertEquals(5L, entry.getEntryId());
        assertEquals(1675361492408L, entry.getTimestampCreated());
        assertTrue(entry.getClientInfo().containsKey("id"));
        assertEquals("1", entry.getClientInfo().get("id"));
    }

    @Test
    public void testAccessControlUserBuilder() {
        AccessControlUser user = new AccessControlUser();
        user.addFlag("on");
        user.addFlag("allkeys");
        user.addPassword("xxx");
        user.setCommands("+@all");
        user.addKey("*");
        user.addChannel("*");
        assertEquals(List.of("on", "allkeys"), user.getFlags());
        assertEquals(List.of("xxx"), user.getPassword());
        assertEquals("+@all", user.getCommands());
        assertEquals(List.of("*"), user.getKeys());
        assertEquals(List.of("*"), user.getChannels());
    }
}
