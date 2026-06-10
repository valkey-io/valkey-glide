/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CommandInfoBuilderTest {

    @Test
    public void builder_rejectsShortResponse() {
        List<Object> tooShort = Arrays.asList("get", 2L, Collections.emptyList());
        assertThrows(
                IllegalArgumentException.class, () -> CommandInfo.COMMAND_INFO_BUILDER.build(tooShort));
    }

    @Test
    public void builder_parsesValidResponse() {
        List<Object> data =
                Arrays.asList(
                        "get",
                        2L,
                        Collections.singletonList("readonly"),
                        1L,
                        1L,
                        1L,
                        Collections.singletonList("@string"),
                        Collections.singletonList("tip"),
                        null,
                        Collections.emptyList());

        CommandInfo info = CommandInfo.COMMAND_INFO_BUILDER.build(data);
        assertEquals(2L, info.getArity());
    }
}
