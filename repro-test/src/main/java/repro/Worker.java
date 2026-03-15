package repro;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ThreadLocalRandom;

public class Worker {
    private static final ScheduledExecutorService JAVA_TIMEOUT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "worker-java-timeout");
                t.setDaemon(true);
                return t;
            });

    private final String endpoint;
    private final int port;
    private final ConcurrentLinkedQueue<String> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger blockedThreads = new AtomicInteger(0);

    // === Command counters ===
    private final AtomicLong totalFired = new AtomicLong(0);       // del() called
    private final AtomicLong admittedEstimate = new AtomicLong(0); // not immediately rejected
    private final AtomicLong immediateMaxInflightRejects = new AtomicLong(0);
    private final AtomicLong immediateOtherRejects = new AtomicLong(0);
    private final AtomicLong completedSuccess = new AtomicLong(0); // completed via Rust callback (success)
    private final AtomicLong completedByTimeout = new AtomicLong(0); // completed by Java timeout scheduler
    private final AtomicLong completedByRustErr = new AtomicLong(0); // completed via Rust callback (error)
    private final AtomicLong lastSecondSuccess = new AtomicLong(0);
    private final AtomicLong lastSecondFired = new AtomicLong(0);
    private final AtomicLong lastSecondAdmitted = new AtomicLong(0);
    private final AtomicLong lastSecondImmediateMaxInflightRejects = new AtomicLong(0);

    // === Latency: .get() block time (submit → .get() returns) ===
    private final AtomicLong getBlockTotalNanos = new AtomicLong(0);
    private final AtomicLong getBlockMaxNanos = new AtomicLong(0);
    private final AtomicLong getBlockSamples = new AtomicLong(0);

    // === Latency: future lifetime (submit → future.whenComplete fires) ===
    private final AtomicLong futureLifeTotalNanos = new AtomicLong(0);
    private final AtomicLong futureLifeMaxNanos = new AtomicLong(0);
    private final AtomicLong futureLifeSamples = new AtomicLong(0);

    // === Error classification ===
    private final AtomicLong timeoutErrors = new AtomicLong(0);    // Glide Rust timeout
    private final AtomicLong javaTimeoutErrors = new AtomicLong(0); // Java-side future timeout
    private final AtomicLong maxInflightErrors = new AtomicLong(0);
    private final AtomicLong closingErrors = new AtomicLong(0);
    private final AtomicLong otherErrors = new AtomicLong(0);

    private final int numThreads;
    private final String mode;
    private final int listenPort;
    private final String podId;
    private final String javaTimeoutStyle;
    private final boolean useTls;
    private final String keyMode;
    private final int keySpace;
    private final int keysPerDel;
    private final String hashTagMode;
    private final String commandType;

    public Worker(String endpoint, int port) {
        this.endpoint = endpoint;
        this.port = port;
        this.numThreads = Integer.parseInt(System.getenv().getOrDefault("NUM_THREADS", "60"));
        this.mode = System.getenv().getOrDefault("MODE", "sync");
        this.listenPort = Integer.parseInt(System.getenv().getOrDefault("LISTEN_PORT", "9001"));
        this.podId = System.getenv().getOrDefault("POD_ID", "worker-0");
        this.javaTimeoutStyle =
                System.getenv().getOrDefault("JAVA_TIMEOUT_STYLE", "complete_future");
        this.useTls = Boolean.parseBoolean(System.getenv().getOrDefault("USE_TLS", "true"));
        this.keyMode = System.getenv().getOrDefault("KEY_MODE", "sequential");
        this.keySpace = Integer.parseInt(System.getenv().getOrDefault("KEY_SPACE", "10000000"));
        this.keysPerDel = Integer.parseInt(System.getenv().getOrDefault("KEYS_PER_DEL", "6"));
        this.hashTagMode = System.getenv().getOrDefault("HASH_TAG_MODE", "none");
        this.commandType = System.getenv().getOrDefault("COMMAND_TYPE", "del");
    }

    public void run() throws Exception {
        System.out.printf("[%s] Starting: endpoint=%s:%d threads=%d mode=%s port=%d%n",
                podId, endpoint, port, numThreads, mode, listenPort);

        int requestTimeoutMs = Integer.parseInt(System.getenv().getOrDefault("REQUEST_TIMEOUT_MS", "0"));
        var configBuilder = GlideClusterClientConfiguration.builder()
                .address(NodeAddress.builder().host(endpoint).port(port).build());
        if (useTls) {
            configBuilder.useTLS(true);
        }
        if (requestTimeoutMs > 0) {
            configBuilder.requestTimeout(requestTimeoutMs);
        }
        // DISABLE_JAVA_TIMEOUT=true → set timeout to 0 so AsyncRegistry.register skips sweep
        if ("true".equals(System.getenv("DISABLE_JAVA_TIMEOUT"))) {
            configBuilder.requestTimeout(0);
        }
        System.out.printf("[%s] requestTimeout=%s%n", podId,
                requestTimeoutMs > 0 ? requestTimeoutMs + "ms" : "default");
        System.out.printf("[%s] useTls=%s%n", podId, useTls);
        System.out.printf("[%s] javaTimeoutStyle=%s%n", podId, javaTimeoutStyle);
        System.out.printf("[%s] keyMode=%s keySpace=%d keysPerDel=%d hashTagMode=%s%n",
                podId, keyMode, keySpace, keysPerDel, hashTagMode);
        GlideClusterClient client = GlideClusterClient.createClient(configBuilder.build()).get();

        // Pre-populate keys for DEL-only mode
        // Each thread will DEL from its own range: threadId * 1M + seq
        // Pre-populate enough keys for hours of DELing
        int totalKeys = Integer.parseInt(System.getenv().getOrDefault("PREPOPULATE_KEYS", "0"));
        if (totalKeys > 0) {
            System.out.printf("[%s] Pre-populating %d keys...%n", podId, totalKeys);
            int batchSize = 100;
            for (int i = 0; i < totalKeys; i++) {
                try {
                    client.set("key:" + i, "v").get();
                } catch (Exception e) { /* ignore */ }
                if (i > 0 && i % 100000 == 0) {
                    System.out.printf("[%s] Pre-populated %d/%d keys%n", podId, i, totalKeys);
                }
            }
            System.out.printf("[%s] Pre-population complete: %d keys%n", podId, totalKeys);
        }

        System.out.printf("[%s] Connected to cluster%n", podId);

        Thread receiverThread = new Thread(() -> receiveLoop(), "task-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();

        Thread metricsThread = new Thread(() -> metricsLoop(), "metrics");
        metricsThread.setDaemon(true);
        metricsThread.start();

        Thread heartbeatThread = new Thread(() -> heartbeatLoop(), "heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        // System stats thread — top-like output for threads, memory, CPU
        Thread sysStatsThread = new Thread(() -> sysStatsLoop(), "sys-stats");
        sysStatsThread.setDaemon(true);
        sysStatsThread.start();

        Thread[] workers = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            workers[i] = new Thread(() -> {
                if ("set".equals(commandType)) {
                    setLoopSync(client, threadId);
                } else if ("ping".equals(mode)) {
                    pingLoop(client, threadId);
                } else if ("pingasync".equals(mode)) {
                    pingLoopAsync(client, threadId);
                } else if ("sync".equals(mode)) {
                    delLoopSync(client, threadId);
                } else {
                    delLoopAsync(client, threadId);
                }
            }, commandType + "-worker-" + i);
            workers[i].start();
        }

        System.out.printf("[%s] All %d worker threads started in %s mode%n", podId, numThreads, mode);
        for (Thread w : workers) w.join();
    }

    /** Benchmark: async SET+GET with configurable value size, self-contained */
    private void pingLoop(GlideClusterClient client, int threadId) {
        int valueSize = Integer.parseInt(System.getenv().getOrDefault("VALUE_SIZE", "100"));
        byte[] valueBytes = new byte[valueSize];
        java.util.Arrays.fill(valueBytes, (byte) 'x');
        String value = new String(valueBytes);
        long counter = 0;
        while (running.get()) {
            String key = podId + ":ping:" + threadId + ":" + (counter++);
            long submitNanos = System.nanoTime();
            totalFired.incrementAndGet();
            lastSecondFired.incrementAndGet();

            CompletableFuture<String> future = client.set(key, value);
            future.whenComplete((result, error) -> {
                long lifeNanos = System.nanoTime() - submitNanos;
                futureLifeTotalNanos.addAndGet(lifeNanos);
                futureLifeSamples.incrementAndGet();
                casMax(futureLifeMaxNanos, lifeNanos);
                if (error != null) {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof TimeoutException) {
                        completedByTimeout.incrementAndGet();
                        timeoutErrors.incrementAndGet();
                    } else {
                        completedByRustErr.incrementAndGet();
                        otherErrors.incrementAndGet();
                    }
                } else {
                    completedSuccess.incrementAndGet();
                    lastSecondSuccess.incrementAndGet();
                }
            });

            try {
                blockedThreads.incrementAndGet();
                future.get();
                blockedThreads.decrementAndGet();
            } catch (Exception e) {
                blockedThreads.decrementAndGet();
            }
            long getBlockNanos = System.nanoTime() - submitNanos;
            getBlockTotalNanos.addAndGet(getBlockNanos);
            getBlockSamples.incrementAndGet();
            casMax(getBlockMaxNanos, getBlockNanos);
        }
    }

    /** Async SET benchmark — no .get(), uses semaphore for backpressure */
    private void pingLoopAsync(GlideClusterClient client, int threadId) {
        int valueSize = Integer.parseInt(System.getenv().getOrDefault("VALUE_SIZE", "100"));
        byte[] valueBytes = new byte[valueSize];
        java.util.Arrays.fill(valueBytes, (byte) 'x');
        String value = new String(valueBytes);
        // Semaphore limits in-flight per thread to avoid unbounded memory growth
        java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(50);
        long counter = 0;
        while (running.get()) {
            try { permits.acquire(); } catch (InterruptedException e) { break; }
            String key = podId + ":pa:" + threadId + ":" + (counter++);
            totalFired.incrementAndGet();
            lastSecondFired.incrementAndGet();
            long submitNanos = System.nanoTime();

            client.set(key, value).whenComplete((result, error) -> {
                permits.release();
                long lifeNanos = System.nanoTime() - submitNanos;
                futureLifeTotalNanos.addAndGet(lifeNanos);
                futureLifeSamples.incrementAndGet();
                casMax(futureLifeMaxNanos, lifeNanos);
                if (error != null) {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof TimeoutException) {
                        completedByTimeout.incrementAndGet();
                        timeoutErrors.incrementAndGet();
                    } else {
                        completedByRustErr.incrementAndGet();
                        otherErrors.incrementAndGet();
                    }
                } else {
                    completedSuccess.incrementAndGet();
                    lastSecondSuccess.incrementAndGet();
                }
            });
        }
    }

    /** SET loop — continuously writes keys so DEL pods have something to delete. */
    private void setLoopSync(GlideClusterClient client, int threadId) {
        int valueSize = Integer.parseInt(System.getenv().getOrDefault("VALUE_SIZE", "1024"));
        String value = "x".repeat(valueSize);
        long seq = threadId * 1_000_000L;
        while (running.get()) {
            String key = "key:" + (seq % keySpace);
            seq++;
            totalFired.incrementAndGet();
            lastSecondFired.incrementAndGet();
            try {
                CompletableFuture<String> future = client.set(key, value);
                admittedEstimate.incrementAndGet();
                lastSecondAdmitted.incrementAndGet();
                future.get(5, TimeUnit.SECONDS);
                completedSuccess.incrementAndGet();
                lastSecondSuccess.incrementAndGet();
            } catch (Exception e) {
                Throwable cause = e;
                while (cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof TimeoutException) {
                    timeoutErrors.incrementAndGet();
                } else if (cause instanceof java.util.concurrent.TimeoutException) {
                    javaTimeoutErrors.incrementAndGet();
                } else if (isMaxInflight(cause)) {
                    maxInflightErrors.incrementAndGet();
                } else if (cause instanceof ClosingException) {
                    closingErrors.incrementAndGet();
                } else {
                    otherErrors.incrementAndGet();
                }
            }
        }
    }

    private void delLoopSync(GlideClusterClient client, int threadId) {
        // DEL-only mode: keys were pre-populated externally
        // Generate key names that match the pre-populated pattern
        long seq = threadId * 1_000_000L; // each thread gets its own range
        while (running.get()) {
            String[] keys = nextKeys(seq);
            if ("sequential".equalsIgnoreCase(keyMode)) {
                seq += keys.length;
            }

            long submitNanos = System.nanoTime();
            totalFired.incrementAndGet();
            lastSecondFired.incrementAndGet();
            CompletableFuture<Long> future = client.del(keys);
            classifySubmission(future);

            // Track future lifetime independently of .get()
            future.whenComplete((result, error) -> {
                long lifeNanos = System.nanoTime() - submitNanos;
                futureLifeTotalNanos.addAndGet(lifeNanos);
                futureLifeSamples.incrementAndGet();
                casMax(futureLifeMaxNanos, lifeNanos);

                if (error != null) {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof TimeoutException) {
                        // Glide's Rust-side timeout
                        completedByTimeout.incrementAndGet();
                        timeoutErrors.incrementAndGet();
                    } else if (cause instanceof java.util.concurrent.TimeoutException) {
                        // A Java-side timeout completed the future before Glide did.
                        completedByTimeout.incrementAndGet();
                        javaTimeoutErrors.incrementAndGet();
                    } else if (isMaxInflight(cause)) {
                        completedByRustErr.incrementAndGet();
                        maxInflightErrors.incrementAndGet();
                    } else if (cause instanceof ClosingException) {
                        completedByRustErr.incrementAndGet();
                        closingErrors.incrementAndGet();
                    } else {
                        completedByRustErr.incrementAndGet();
                        otherErrors.incrementAndGet();
                    }
                } else {
                    completedSuccess.incrementAndGet();
                    lastSecondSuccess.incrementAndGet();
                }
            });

            // Now block on .get() — this is what the customer does
            // If JAVA_TIMEOUT_MS is set, apply a Java-side timeout to the same future.
            // This mirrors CompletableFuture.orTimeout(...) semantics without requiring JDK 9+.
            int javaTimeoutMs = Integer.parseInt(System.getenv().getOrDefault("JAVA_TIMEOUT_MS", "0"));
            try {
                blockedThreads.incrementAndGet();
                if (javaTimeoutMs > 0) {
                    if ("get_timeout".equalsIgnoreCase(javaTimeoutStyle)) {
                        future.get(javaTimeoutMs, TimeUnit.MILLISECONDS);
                    } else {
                        applyJavaSideTimeout(future, javaTimeoutMs).get();
                    }
                } else {
                    future.get();
                }
                blockedThreads.decrementAndGet();
            } catch (Exception e) {
                blockedThreads.decrementAndGet();
            }

            // Track .get() block time
            long getBlockNanos = System.nanoTime() - submitNanos;
            getBlockTotalNanos.addAndGet(getBlockNanos);
            getBlockSamples.incrementAndGet();
            casMax(getBlockMaxNanos, getBlockNanos);
        }
    }

    private String[] nextKeys(long seq) {
        String[] keys = new String[keysPerDel];
        String hashTag = null;
        if ("same_slot".equalsIgnoreCase(hashTagMode)) {
            int tagValue;
            if ("random".equalsIgnoreCase(keyMode)) {
                tagValue = ThreadLocalRandom.current().nextInt(keySpace);
            } else {
                tagValue = (int) (seq % keySpace);
            }
            hashTag = "{" + tagValue + "}";
        }

        if ("random".equalsIgnoreCase(keyMode)) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int k = 0; k < keys.length; k++) {
                int keyValue = random.nextInt(keySpace);
                if (hashTag == null) {
                    keys[k] = "key:" + keyValue;
                } else {
                    keys[k] = "key:" + hashTag + ":" + k + ":" + keyValue;
                }
            }
            return keys;
        }

        for (int k = 0; k < keys.length; k++) {
            if (hashTag == null) {
                keys[k] = "key:" + (seq + k);
            } else {
                keys[k] = "key:" + hashTag + ":" + k + ":" + (seq + k);
            }
        }
        return keys;
    }

    private void delLoopAsync(GlideClusterClient client, int threadId) {
        while (running.get()) {
            String key = taskQueue.poll();
            if (key == null) {
                try { Thread.sleep(1); } catch (InterruptedException e) { break; }
                continue;
            }
            long submitNanos = System.nanoTime();
            totalFired.incrementAndGet();
            lastSecondFired.incrementAndGet();

            CompletableFuture<Long> future = client.del(new String[]{key});
            classifySubmission(future);
            future.whenComplete((result, error) -> {
                long lifeNanos = System.nanoTime() - submitNanos;
                futureLifeTotalNanos.addAndGet(lifeNanos);
                futureLifeSamples.incrementAndGet();
                casMax(futureLifeMaxNanos, lifeNanos);

                if (error != null) {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof TimeoutException) {
                        completedByTimeout.incrementAndGet();
                        timeoutErrors.incrementAndGet();
                    } else if (isMaxInflight(cause)) {
                        completedByRustErr.incrementAndGet();
                        maxInflightErrors.incrementAndGet();
                    } else {
                        completedByRustErr.incrementAndGet();
                        otherErrors.incrementAndGet();
                    }
                } else {
                    completedSuccess.incrementAndGet();
                    lastSecondSuccess.incrementAndGet();
                }
            });
        }
    }

    private static void casMax(AtomicLong target, long value) {
        long prev = target.get();
        while (value > prev && !target.compareAndSet(prev, value)) {
            prev = target.get();
        }
    }

    private static <T> CompletableFuture<T> applyJavaSideTimeout(
            CompletableFuture<T> future, int timeoutMs) {
        ScheduledFuture<?> timeoutTask =
                JAVA_TIMEOUT_SCHEDULER.schedule(
                        () -> future.completeExceptionally(
                                new java.util.concurrent.TimeoutException("Java-side timeout")),
                        timeoutMs,
                        TimeUnit.MILLISECONDS);
        future.whenComplete((result, error) -> timeoutTask.cancel(false));
        return future;
    }

    private void classifySubmission(CompletableFuture<?> future) {
        ImmediateSubmissionState state = inspectImmediateState(future);
        switch (state) {
            case MAX_INFLIGHT_REJECTED:
                immediateMaxInflightRejects.incrementAndGet();
                lastSecondImmediateMaxInflightRejects.incrementAndGet();
                break;
            case OTHER_REJECTED:
                immediateOtherRejects.incrementAndGet();
                break;
            case ADMITTED:
                admittedEstimate.incrementAndGet();
                lastSecondAdmitted.incrementAndGet();
                break;
        }
    }

    private static ImmediateSubmissionState inspectImmediateState(CompletableFuture<?> future) {
        if (!future.isDone()) {
            return ImmediateSubmissionState.ADMITTED;
        }

        try {
            future.join();
            return ImmediateSubmissionState.ADMITTED;
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = unwrap(e);
            if (isMaxInflight(cause)) {
                return ImmediateSubmissionState.MAX_INFLIGHT_REJECTED;
            }
            return ImmediateSubmissionState.OTHER_REJECTED;
        } catch (java.util.concurrent.CancellationException e) {
            return ImmediateSubmissionState.OTHER_REJECTED;
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static boolean isMaxInflight(Throwable cause) {
        return cause instanceof RequestException
                && "Client reached maximum inflight requests".equals(cause.getMessage());
    }

    private void receiveLoop() {
        try (ServerSocket server = new ServerSocket(listenPort)) {
            System.out.printf("[%s] Listening for tasks on port %d%n", podId, listenPort);
            while (running.get()) {
                Socket conn = server.accept();
                Thread handler = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) taskQueue.add(line);
                    } catch (Exception e) {
                        System.err.printf("[%s] Receiver error: %s%n", podId, e.getMessage());
                    }
                }, "receiver-handler");
                handler.setDaemon(true);
                handler.start();
            }
        } catch (Exception e) {
            System.err.printf("[%s] Receiver fatal: %s%n", podId, e.getMessage());
        }
    }

    private void metricsLoop() {
        while (running.get()) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { break; }

            long fired = totalFired.get();
            long firedPerSec = lastSecondFired.getAndSet(0);
            long admitted = admittedEstimate.get();
            long admittedPerSec = lastSecondAdmitted.getAndSet(0);
            long immediateMaxInflight = immediateMaxInflightRejects.get();
            long immediateMaxInflightPerSec = lastSecondImmediateMaxInflightRejects.getAndSet(0);
            long immediateOther = immediateOtherRejects.get();
            long success = completedSuccess.get();
            long successPerSec = lastSecondSuccess.getAndSet(0);
            long byTimeout = completedByTimeout.get();
            long byRustErr = completedByRustErr.get();
            int queueDepth = taskQueue.size();
            int blocked = blockedThreads.get();
            long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long heapMax = Runtime.getRuntime().maxMemory();

            // .get() block time (reset per second)
            long getSamples = getBlockSamples.getAndSet(0);
            long getNanos = getBlockTotalNanos.getAndSet(0);
            long getMaxNanos = getBlockMaxNanos.getAndSet(0);
            long getAvgMs = getSamples > 0 ? (getNanos / getSamples / 1_000_000) : 0;
            long getMaxMs = getMaxNanos / 1_000_000;

            // Future lifetime (reset per second)
            long lifeSamples = futureLifeSamples.getAndSet(0);
            long lifeNanos = futureLifeTotalNanos.getAndSet(0);
            long lifeMaxNanos = futureLifeMaxNanos.getAndSet(0);
            long lifeAvgMs = lifeSamples > 0 ? (lifeNanos / lifeSamples / 1_000_000) : 0;
            long lifeMaxMs = lifeMaxNanos / 1_000_000;

            // Glide internals
            int pendingCount = 0;
            try { pendingCount = glide.internal.AsyncRegistry.getPendingCount(); } catch (Throwable t) {}

            System.out.printf(
                "[%s] queue=%d fired=%d(%d/s) admitted~=%d(%d/s) maxInflightNow=%d(%d/s) immediateOther=%d " +
                "success=%d(%d/s) byTimeout=%d byRustErr=%d " +
                "blocked=%d/%d heap=%dMB/%dMB pending=%d " +
                "getBlock_avg=%dms getBlock_max=%dms futureLife_avg=%dms futureLife_max=%dms " +
                "err(rustTimeout=%d,javaTimeout=%d,maxInflight=%d,closing=%d,other=%d)%n",
                podId, queueDepth, fired, firedPerSec, admitted, admittedPerSec,
                immediateMaxInflight, immediateMaxInflightPerSec, immediateOther,
                success, successPerSec, byTimeout, byRustErr,
                blocked, numThreads, heapUsed / 1024 / 1024, heapMax / 1024 / 1024, pendingCount,
                getAvgMs, getMaxMs, lifeAvgMs, lifeMaxMs,
                timeoutErrors.get(), javaTimeoutErrors.get(), maxInflightErrors.get(),
                closingErrors.get(), otherErrors.get()
            );

            if (queueDepth > 10000) {
                System.out.printf("[%s] ALERT: Queue depth %d > 10K%n", podId, queueDepth);
            }
        }
    }

    private void sysStatsLoop() {
        while (running.get()) {
            try {
                Thread.sleep(5000);
                // Thread count by state
                java.lang.management.ThreadMXBean tmx = java.lang.management.ManagementFactory.getThreadMXBean();
                int totalThreads = tmx.getThreadCount();
                int daemonThreads = tmx.getDaemonThreadCount();

                // Get thread states
                long[] ids = tmx.getAllThreadIds();
                int runnable = 0, waiting = 0, timedWaiting = 0, blocked = 0;
                for (java.lang.management.ThreadInfo ti : tmx.getThreadInfo(ids)) {
                    if (ti == null) continue;
                    switch (ti.getThreadState()) {
                        case RUNNABLE: runnable++; break;
                        case WAITING: waiting++; break;
                        case TIMED_WAITING: timedWaiting++; break;
                        case BLOCKED: blocked++; break;
                        default: break;
                    }
                }

                // Memory
                Runtime rt = Runtime.getRuntime();
                long heapUsed = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
                long heapMax = rt.maxMemory() / 1024 / 1024;

                // Process-level RSS from /proc/self/status
                long rssMb = 0;
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.FileReader("/proc/self/status"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("VmRSS:")) {
                            rssMb = Long.parseLong(line.replaceAll("[^0-9]", "")) / 1024;
                            break;
                        }
                    }
                } catch (Exception e) { }

                // FD count
                int fdCount = 0;
                try {
                    File fdDir = new File("/proc/self/fd");
                    String[] fds = fdDir.list();
                    if (fds != null) fdCount = fds.length;
                } catch (Exception e) { }

                System.out.printf(
                    "[%s][SYS] threads=%d(daemon=%d) RUNNABLE=%d WAITING=%d TIMED_WAITING=%d BLOCKED=%d heap=%dMB/%dMB rss=%dMB fds=%d%n",
                    podId, totalThreads, daemonThreads, runnable, waiting, timedWaiting, blocked,
                    heapUsed, heapMax, rssMb, fdCount
                );
            } catch (Exception e) { }
        }
    }

    private void heartbeatLoop() {
        String heartbeatDir = System.getenv().getOrDefault("HEARTBEAT_DIR", "/tmp/heartbeats");
        new File(heartbeatDir).mkdirs();
        String heartbeatFile = heartbeatDir + "/heartbeat-" + podId;

        while (running.get()) {
            try {
                try (FileWriter fw = new FileWriter(heartbeatFile)) {
                    fw.write(String.valueOf(System.currentTimeMillis()));
                }
                Thread.sleep(1000);
            } catch (Exception e) { }
        }
    }

    private enum ImmediateSubmissionState {
        ADMITTED,
        MAX_INFLIGHT_REJECTED,
        OTHER_REJECTED
    }
}
