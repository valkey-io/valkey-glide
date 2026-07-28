/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.shutdown;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end regression test for <a
 * href="https://github.com/valkey-io/valkey-glide/issues/4809">issue #4809</a>: a command issued
 * from the application's own JVM shutdown hook must still succeed rather than being rejected with
 * {@code ClosingException: Client is shutting down}.
 *
 * <p>This forks {@link ShutdownHookReproducer} in a child JVM (a real shutdown hook can only be
 * observed from a dying JVM), sends it SIGTERM, and asserts on the emitted markers. It reuses the
 * suite's shared standalone server — the reproducer only issues {@code PING}, which is read-only
 * and leaves no state behind, so it cannot affect other tests.
 *
 * <p>Disabled by default because forking a JVM and relying on signal timing is heavier and more
 * timing-sensitive than a typical unit/integration test. Enable explicitly with {@code
 * -DRUN_SHUTDOWN_HOOK_IT=true}. Two example invocations:
 *
 * <pre>{@code
 * # Run this test only:
 * ./gradlew :integTest:test --tests 'glide.shutdown.ShutdownHookIntegrationTest' -DRUN_SHUTDOWN_HOOK_IT=true
 *
 * # Run the full integTest suite with this test included:
 * ./gradlew :integTest:test -DRUN_SHUTDOWN_HOOK_IT=true
 * }</pre>
 *
 * <p>If a mechanism to differentiate PR runs from the nightly full-matrix runs lands in the future,
 * this test would be a good candidate to move onto that mechanism (nightly-only) instead of the
 * manual opt-in flag.
 */
public class ShutdownHookIntegrationTest {

    @Test
    @Timeout(60)
    void commandFromUserShutdownHookSucceeds() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("RUN_SHUTDOWN_HOOK_IT"),
                "Shutdown-hook IT disabled; run with -DRUN_SHUTDOWN_HOOK_IT=true");
        // Process.destroy() maps to SIGTERM on POSIX (JVM shutdown hooks fire) but to
        // TerminateProcess on Windows (hooks are skipped), so the reproducer would fail spuriously
        // on Windows even after the fix.
        Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase().startsWith("windows"),
                "Shutdown-hook IT is POSIX-only (Process.destroy is not SIGTERM on Windows)");

        String hostPort = STANDALONE_HOSTS[0];
        Assumptions.assumeFalse(
                hostPort == null || hostPort.isEmpty(), "No standalone server address available");
        int lastColon = hostPort.lastIndexOf(':');
        Assumptions.assumeTrue(
                lastColon > 0 && lastColon < hostPort.length() - 1,
                "Malformed standalone endpoint (expected host:port): " + hostPort);
        String host = hostPort.substring(0, lastColon);
        String port = hostPort.substring(lastColon + 1);

        String javaBin =
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        // Native lib is loaded from the GLIDE jar; mirror the integTest library path just in case.
        command.add("-Djava.library.path=" + System.getProperty("java.library.path", ""));
        command.add(ShutdownHookReproducer.class.getName());
        command.add(host);
        command.add(port);

        // Redirect child output to a file rather than reading the live pipe: the pipe is closed when
        // the child is reaped after SIGTERM, which would race with reading the hook's final output.
        File outFile = File.createTempFile("glide-shutdown-hook-it", ".log");
        outFile.deleteOnExit();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(outFile));
        Process process = pb.start();

        try {
            // Wait until the child has connected and registered its shutdown hook.
            waitFor(() -> readFile(outFile).contains(ShutdownHookReproducer.READY_MARKER), 30_000);

            // Trigger a graceful SIGTERM so both the child's and GLIDE's shutdown hooks run.
            process.destroy();

            // Wait for the child to exit and flush its hook output.
            assertTrue(
                    process.waitFor(20, TimeUnit.SECONDS),
                    () -> "reproducer did not exit after SIGTERM. Output:\n" + readFile(outFile));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        String out = readFile(outFile);
        assertTrue(
                out.contains(ShutdownHookReproducer.INITIAL_PING_OK_MARKER),
                () -> "reproducer never connected/pinged. Output:\n" + out);
        assertTrue(
                out.contains(ShutdownHookReproducer.HOOK_PING_OK_MARKER)
                        || out.contains(ShutdownHookReproducer.HOOK_PING_FAIL_MARKER),
                () -> "reproducer's shutdown hook never ran. Output:\n" + out);
        assertTrue(
                out.contains(ShutdownHookReproducer.HOOK_PING_OK_MARKER),
                () ->
                        "command issued from the user shutdown hook was rejected (issue #4809 regression)."
                                + " Output:\n"
                                + out);
    }

    private static String readFile(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private interface Condition {
        boolean met();
    }

    private static void waitFor(Condition condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.met()) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
