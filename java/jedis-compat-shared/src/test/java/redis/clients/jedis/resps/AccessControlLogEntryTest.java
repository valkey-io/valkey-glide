/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AccessControlLogEntryTest {

    @Test
    public void constructor_handlesMissingOptionalFields() {
        Map<String, Object> map = new HashMap<>();
        map.put(AccessControlLogEntry.COUNT, 1L);

        AccessControlLogEntry entry = new AccessControlLogEntry(map);

        assertEquals(1L, entry.getCount());
        assertNull(entry.getReason());
        assertNull(entry.getUsername());
        assertNotNull(entry.getClientInfo());
        assertEquals(0L, entry.getEntryId());
    }

    @Test
    public void constructor_coercesNonStringFieldsToString() {
        Map<String, Object> map = new HashMap<>();
        map.put(AccessControlLogEntry.REASON, 42);

        AccessControlLogEntry entry = new AccessControlLogEntry(map);

        assertEquals("42", entry.getReason());
    }

    @Test
    public void constructor_rejectsNullMap() {
        assertThrows(IllegalArgumentException.class, () -> new AccessControlLogEntry(null));
    }

    @Test
    public void constructor_handlesEmptyClientInfo() {
        AccessControlLogEntry entry =
                new AccessControlLogEntry(Collections.singletonMap(AccessControlLogEntry.COUNT, 0));
        assertEquals(Collections.emptyMap(), entry.getClientInfo());
    }
}
