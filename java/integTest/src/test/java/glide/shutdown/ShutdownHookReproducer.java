/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.shutdown;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;

/**
 * Standalone helper process for {@link ShutdownHookIT}. It reproduces the scenario from <a
 * href="https://github.com/valkey-io/valkey-glide/issues/4809">issue #4809</a>: a client that is
 * still used from the application's own JVM shutdown hook.
 *
 * <p>This must run in its own JVM because a real {@link Runtime#addShutdownHook} only fires as the
 * JVM exits, at which point the in-process test framework can no longer make assertions. The parent
 * test forks this class, sends it SIGTERM, and inspects the emitted markers on stdout.
 *
 * <p>Args: {@code <host> <port>}. Emitted markers: {@code INITIAL_PING_OK} on the initial ping,
 * {@code HOOK_PING_OK} if the ping issued from the shutdown hook succeeds (the fix), or {@code
 * HOOK_PING_FAIL:<exception>} if it is rejected (the bug). Markers are chosen so that none is a
 * substring of another, allowing simple {@code contains} checks in the parent test.
 */
public final class ShutdownHookReproducer {

    public static final String READY_MARKER = "READY";
    public static final String INITIAL_PING_OK_MARKER = "INITIAL_PING_OK";
    public static final String HOOK_PING_OK_MARKER = "HOOK_PING_OK";
    public static final String HOOK_PING_FAIL_MARKER = "HOOK_PING_FAIL:";

    private ShutdownHookReproducer() {}

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        GlideClient client =
                GlideClient.createClient(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(host).port(port).build())
                                        .build())
                        .get();

        System.out.println(INITIAL_PING_OK_MARKER + ": " + client.ping().get());

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        // Give the JVM's other shutdown hooks (including GLIDE's own) a chance
                                        // to run concurrently, mirroring the original report.
                                        Thread.sleep(500);
                                        System.out.println(HOOK_PING_OK_MARKER + ": " + client.ping().get());
                                    } catch (Throwable t) {
                                        System.out.println(HOOK_PING_FAIL_MARKER + t);
                                    } finally {
                                        System.out.flush();
                                    }
                                }));

        // Signal readiness, then idle until the parent sends SIGTERM.
        System.out.println(READY_MARKER);
        System.out.flush();

        while (true) {
            Thread.sleep(1000);
        }
    }
}
