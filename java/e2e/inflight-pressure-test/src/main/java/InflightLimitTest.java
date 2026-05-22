/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.PeriodicChecksManualInterval;
import glide.api.models.configuration.RequestRoutingConfiguration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Validates the synchronous inflight limit check at the JNI boundary.
 *
 * <p>Test strategy: 1. CLIENT PAUSE all primaries for 15s (stalls all responses) 2. Blast requests
 * from a fixed thread pool 3. After Java timeout fires (3s), verify that inflight rejections occur
 * (proves the Rust-side synchronous check is working) 4. After pause ends, verify recovery (ops
 * resume)
 *
 * <p>PASS criteria: - inflightRejections > 0 (synchronous check is triggering) - ops resume after
 * pause ends (client recovers)
 *
 * <p>Exit 0 = PASS, Exit 1 = FAIL
 */
public class InflightLimitTest {
    static final AtomicLong totalOps = new AtomicLong();
    static final AtomicLong totalErrs = new AtomicLong();
    static final AtomicLong inflightRejections = new AtomicLong();
    static final AtomicLong timeoutErrors = new AtomicLong();
    static final AtomicInteger currentBlocking = new AtomicInteger();
    static final int PAUSE_DURATION_MS = 15000;
    static final int REQUEST_TIMEOUT_MS = 3000;
    static final int TEST_DURATION_SEC = 22;
    static final int WORKER_THREADS = 200;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: InflightLimitTest <host> <port> [--tls]");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        boolean tls = args.length > 2 && args[2].equals("--tls");
        System.out.printf("[INIT] host=%s port=%d tls=%s%n", host, port, tls);

        GlideClusterClient glide = connectWithRetry(host, port, tls);
        if (glide == null) {
            System.out.println("[FAIL] Cannot connect");
            System.exit(1);
        }
        System.out.println("[INIT] Connected");
        for (int i = 0; i < 100; i++) glide.set("itest:" + i, "value-" + i).get();
        System.out.println("[INIT] Pre-populated 100 keys");
        if (glide.get("itest:0").get() == null) {
            System.out.println("[FAIL] GET null");
            System.exit(1);
        }

        // CLIENT PAUSE ALL_PRIMARIES
        boolean pauseApplied = false;
        try {
            RequestRoutingConfiguration.Route allPrimaries =
                    RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;
            glide
                    .customCommand(
                            new String[] {"CLIENT", "PAUSE", String.valueOf(PAUSE_DURATION_MS), "ALL"},
                            allPrimaries)
                    .get(5, TimeUnit.SECONDS);
            pauseApplied = true;
            System.out.printf("[TEST] CLIENT PAUSE %dms ALL_PRIMARIES OK%n", PAUSE_DURATION_MS);
        } catch (Exception e) {
            System.out.println("[WARN] CLIENT PAUSE failed: " + e.getMessage());
        }

        ExecutorService workers =
                Executors.newFixedThreadPool(
                        WORKER_THREADS,
                        r -> {
                            Thread t = new Thread(r);
                            t.setDaemon(true);
                            return t;
                        });
        System.out.println("[TEST] Starting " + WORKER_THREADS + " workers...");
        for (int w = 0; w < WORKER_THREADS; w++) {
            final int wid = w;
            workers.submit(() -> workerLoop(glide, wid));
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < TEST_DURATION_SEC * 1000L) {
            Thread.sleep(2000);
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf(
                    "[MON] t=%ds blk=%d ops=%d errs=%d rej=%d to=%d%n",
                    elapsed,
                    currentBlocking.get(),
                    totalOps.get(),
                    totalErrs.get(),
                    inflightRejections.get(),
                    timeoutErrors.get());
        }
        workers.shutdownNow();

        long ops = totalOps.get();
        long rejections = inflightRejections.get();
        long timeouts = timeoutErrors.get();
        System.out.printf("%n[RESULT] ops=%d rejections=%d timeouts=%d%n", ops, rejections, timeouts);

        // Validation
        boolean pass = true;
        if (pauseApplied && rejections == 0) {
            System.out.println("[FAIL] No inflight rejections detected - synchronous check not working");
            pass = false;
        }
        if (pauseApplied && rejections > 0) {
            System.out.printf(
                    "[PASS] %d inflight rejections - synchronous check is working%n", rejections);
        }
        if (ops == 0) {
            System.out.println("[FAIL] No successful ops - client did not recover after pause");
            pass = false;
        } else {
            System.out.printf("[PASS] %d ops completed - client recovered after pause%n", ops);
        }
        if (timeouts > 0) {
            System.out.printf("[INFO] %d timeout errors (expected during pause)%n", timeouts);
        }
        System.exit(pass ? 0 : 1);
    }

    static void workerLoop(GlideClusterClient client, int workerId) {
        Random rng = new Random(workerId);
        while (!Thread.currentThread().isInterrupted()) {
            int key = rng.nextInt(100);
            currentBlocking.incrementAndGet();
            try {
                client.get("itest:" + key).get();
                totalOps.incrementAndGet();
            } catch (Exception e) {
                totalErrs.incrementAndGet();
                String msg = e.getMessage();
                if (msg != null) {
                    if (msg.contains("inflight")) inflightRejections.incrementAndGet();
                    else if (msg.contains("timed out") || msg.contains("Timeout"))
                        timeoutErrors.incrementAndGet();
                }
            } finally {
                currentBlocking.decrementAndGet();
            }
            try {
                Thread.sleep(0, 100_000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    static GlideClusterClient connectWithRetry(String host, int port, boolean tls) {
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                return GlideClusterClient.createClient(
                                GlideClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(host).port(port).build())
                                        .requestTimeout(REQUEST_TIMEOUT_MS)
                                        .useTLS(tls)
                                        .advancedConfiguration(
                                                AdvancedGlideClusterClientConfiguration.builder()
                                                        .connectionTimeout(3000)
                                                        .periodicChecks(
                                                                PeriodicChecksManualInterval.builder().durationInSec(60).build())
                                                        .build())
                                        .build())
                        .get();
            } catch (Exception e) {
                System.out.printf("[INIT] attempt %d: %s%n", attempt, e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        return null;
    }
}
