/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

    /** Keyed by node address; {@code null} for a standalone client. */
    private final Map<String, String> originals;

    private LuaTimeLimit(BaseClient client, Map<String, String> originals) {
        this.client = client;
        this.originals = originals;
    }

    /** Sets {@code lua-time-limit} to {@code millis} on every node reachable through the client. */
    @SneakyThrows
    public static LuaTimeLimit set(BaseClient client, int millis) {
        Map<String, String> originals = new LinkedHashMap<>();
        if (client instanceof GlideClusterClient) {
            ((GlideClusterClient) client)
                    .configGet(new String[] {CONFIG}, ALL_NODES)
                    .get()
                    .getMultiValue()
                    .forEach((address, config) -> originals.put(address, requireValue(config)));
        } else {
            originals.put(
                    null, requireValue(((GlideClient) client).configGet(new String[] {CONFIG}).get()));
        }
        String value = Integer.toString(millis);
        for (String address : originals.keySet()) {
            configSet(client, address, value);
        }
        return new LuaTimeLimit(client, originals);
    }

    @Override
    public void close() {
        originals.forEach((address, value) -> configSet(client, address, value));
    }

    private static String requireValue(Map<String, String> config) {
        return Objects.requireNonNull(config.get(CONFIG), "CONFIG GET " + CONFIG);
    }

    @SneakyThrows
    private static void configSet(BaseClient client, String address, String value) {
        Map<String, String> parameters = Collections.singletonMap(CONFIG, value);
        assertEquals(
                OK,
                address == null
                        ? ((GlideClient) client).configSet(parameters).get()
                        : ((GlideClusterClient) client)
                                .configSet(parameters, new ByAddressRoute(address))
                                .get());
    }
}
