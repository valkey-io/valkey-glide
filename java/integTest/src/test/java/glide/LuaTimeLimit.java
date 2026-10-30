/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import java.util.Collections;
import java.util.Map;
import lombok.SneakyThrows;

/**
 * Scoped override of the server's {@code lua-time-limit}, restored on {@link #close()}.
 *
 * <p>The server accepts {@code SCRIPT KILL} and {@code FUNCTION KILL} only once a script has run
 * past this limit, so a kill test that leaves it at the 5000 ms default spends five seconds waiting
 * before the kill can succeed.
 */
public final class LuaTimeLimit implements AutoCloseable {
    private static final String CONFIG = "lua-time-limit";

    private final BaseClient client;
    private final String original;

    private LuaTimeLimit(BaseClient client, String original) {
        this.client = client;
        this.original = original;
    }

    /** Sets {@code lua-time-limit} to {@code millis} on every node reachable through the client. */
    public static LuaTimeLimit set(BaseClient client, int millis) {
        String original = configGet(client).get(CONFIG);
        configSet(client, Integer.toString(millis));
        return new LuaTimeLimit(client, original);
    }

    @Override
    public void close() {
        configSet(client, original);
    }

    @SneakyThrows
    private static Map<String, String> configGet(BaseClient client) {
        String[] parameters = {CONFIG};
        return client instanceof GlideClusterClient
                ? ((GlideClusterClient) client).configGet(parameters).get()
                : ((GlideClient) client).configGet(parameters).get();
    }

    @SneakyThrows
    private static void configSet(BaseClient client, String value) {
        Map<String, String> parameters = Collections.singletonMap(CONFIG, value);
        assertEquals(
                OK,
                client instanceof GlideClusterClient
                        ? ((GlideClusterClient) client).configSet(parameters).get()
                        : ((GlideClient) client).configSet(parameters).get());
    }
}
