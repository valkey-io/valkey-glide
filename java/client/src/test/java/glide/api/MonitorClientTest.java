/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MonitorClientTest {

    @Test
    public void parseArgsJson_empty() {
        List<String> result = MonitorClient.parseArgsJson("[]");
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseArgsJson_null() {
        List<String> result = MonitorClient.parseArgsJson(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseArgsJson_singleArg() {
        List<String> result = MonitorClient.parseArgsJson("[\"key\"]");
        assertEquals(Collections.singletonList("key"), result);
    }

    @Test
    public void parseArgsJson_multipleArgs() {
        List<String> result = MonitorClient.parseArgsJson("[\"key\",\"val\"]");
        assertEquals(Arrays.asList("key", "val"), result);
    }

    @Test
    public void parseArgsJson_escapedQuote() {
        // JSON: ["he said \"hi\""]
        List<String> result = MonitorClient.parseArgsJson("[\"he said \\\"hi\\\"\"]");
        assertEquals(Collections.singletonList("he said \"hi\""), result);
    }

    @Test
    public void parseArgsJson_escapedBackslash() {
        // JSON: ["a\\b"]
        List<String> result = MonitorClient.parseArgsJson("[\"a\\\\b\"]");
        assertEquals(Collections.singletonList("a\\b"), result);
    }

    @Test
    public void parseArgsJson_controlCharNewline() {
        // serde_json encodes newline as \n in JSON: ["line1\nline2"]
        List<String> result = MonitorClient.parseArgsJson("[\"line1\\nline2\"]");
        assertEquals(Collections.singletonList("line1\nline2"), result);
    }

    @Test
    void parseArgsJsonUnicodeEscape() {
        // \u0041 is 'A'
        List<String> result = MonitorClient.parseArgsJson("[\"\\u0041\"]");
        assertEquals(Collections.singletonList("A"), result);
    }

    @Test
    void monitorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> MonitorClient.create(null));
    }
}
