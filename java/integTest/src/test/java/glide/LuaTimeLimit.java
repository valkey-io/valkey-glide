/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.api.BaseClient.OK;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
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
        Map<String, String> target = new LinkedHashMap<>();
        originals.keySet().forEach(address -> target.put(address, Integer.toString(millis)));
        List<String> failures = configSetAll(client, target);
        if (!failures.isEmpty()) {
            failures.addAll(configSetAll(client, originals));
            fail("CONFIG SET " + CONFIG + " failed:\n" + String.join("\n", failures));
        }
        return new LuaTimeLimit(client, originals);
    }

    @Override
    public void close() {
        assertEquals(Collections.emptyList(), configSetAll(client, originals), "CONFIG SET " + CONFIG);
    }

    private static String requireValue(Map<String, String> config) {
        return Objects.requireNonNull(config.get(CONFIG), "CONFIG GET " + CONFIG);
    }

    /**
     * Every node receives its CONFIG SET before any reply is awaited, so one refusal cannot skip the
     * rest.
     */
    private static List<String> configSetAll(BaseClient client, Map<String, String> values) {
        Map<String, CompletableFuture<String>> replies = new LinkedHashMap<>();
        values.forEach((address, value) -> replies.put(address, configSet(client, address, value)));
        return replies.entrySet().stream()
                .map(e -> e.getValue().handle((reply, error) -> failure(e.getKey(), reply, error)).join())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private static Optional<String> failure(String address, String reply, Throwable error) {
        if (error != null) {
            return Optional.of(address + ": " + error.getMessage());
        }
        return OK.equals(reply) ? Optional.empty() : Optional.of(address + ": " + reply);
    }

    private static CompletableFuture<String> configSet(
            BaseClient client, String address, String value) {
        Map<String, String> parameters = Collections.singletonMap(CONFIG, value);
        return address == null
                ? ((GlideClient) client).configSet(parameters)
                : ((GlideClusterClient) client).configSet(parameters, new ByAddressRoute(address));
    }
}
