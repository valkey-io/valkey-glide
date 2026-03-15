package repro;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public class Producer {
    private final String endpoint;
    private final int port;
    private final AtomicLong totalSets = new AtomicLong(0);
    private final AtomicLong lastSecondSets = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Worker addresses: comma-separated "host:port" pairs
    private final String[] workerAddresses;
    // Value size for SET operations
    private final int valueSize;
    private final boolean useTls;

    public Producer(String endpoint, int port) {
        this.endpoint = endpoint;
        this.port = port;
        this.workerAddresses = System.getenv().getOrDefault("WORKER_ADDRESSES",
                "worker-0.workers:9001,worker-1.workers:9002,worker-2.workers:9003").split(",");
        this.valueSize = Integer.parseInt(System.getenv().getOrDefault("VALUE_SIZE", "1024"));
        this.useTls = Boolean.parseBoolean(System.getenv().getOrDefault("USE_TLS", "true"));
    }

    public void run() throws Exception {
        System.out.printf("[producer] Starting: endpoint=%s:%d, workers=%d, valueSize=%d%n",
                endpoint, port, workerAddresses.length, valueSize);

        // Create Glide client for SET operations
        GlideClusterClient client = GlideClusterClient.createClient(
                (useTls
                                ? GlideClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(endpoint).port(port).build())
                                        .useTLS(true)
                                : GlideClusterClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(endpoint).port(port).build()))
                        .build()
        ).get();

        System.out.println("[producer] Connected to cluster");

        // Connect to workers
        PrintWriter[] writers = new PrintWriter[workerAddresses.length];
        for (int i = 0; i < workerAddresses.length; i++) {
            String[] parts = workerAddresses[i].trim().split(":");
            String host = parts[0];
            int wPort = Integer.parseInt(parts[1]);
            System.out.printf("[producer] Connecting to worker %s:%d...%n", host, wPort);
            boolean connected = false;
            for (int attempt = 0; attempt < 30; attempt++) {
                try {
                    Socket sock = new Socket(host, wPort);
                    sock.setTcpNoDelay(true);
                    writers[i] = new PrintWriter(sock.getOutputStream(), true);
                    connected = true;
                    System.out.printf("[producer] Connected to worker %d at %s:%d%n", i, host, wPort);
                    break;
                } catch (Exception e) {
                    System.out.printf("[producer] Waiting for worker %d (%s:%d): %s%n", i, host, wPort, e.getMessage());
                    Thread.sleep(2000);
                }
            }
            if (!connected) {
                System.err.printf("[producer] FATAL: Could not connect to worker %d at %s:%d%n", i, host, wPort);
                System.exit(1);
            }
        }

        System.out.println("[producer] All workers connected, starting SET loop");

        // Start metrics thread
        Thread metricsThread = new Thread(() -> {
            while (running.get()) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                long throughput = lastSecondSets.getAndSet(0);
                System.out.printf("[producer] throughput=%d/s total=%d errors=%d%n",
                        throughput, totalSets.get(), errors.get());
            }
        }, "producer-metrics");
        metricsThread.setDaemon(true);
        metricsThread.start();

        // Generate random value (reused for all SETs)
        byte[] valueBytes = new byte[valueSize];
        java.util.Arrays.fill(valueBytes, (byte) 'x');
        String value = new String(valueBytes);

        // SET loop — round-robin across workers
        long seq = 0;
        while (running.get()) {
            int workerIdx = (int) (seq % workerAddresses.length);
            String key = "w" + workerIdx + ":" + seq;
            try {
                client.set(key, value).get();
                // Send key name to assigned worker
                writers[workerIdx].println(key);
                totalSets.incrementAndGet();
                lastSecondSets.incrementAndGet();
                seq++;
            } catch (Exception e) {
                errors.incrementAndGet();
                // Brief pause on error to avoid tight error loop
                try { Thread.sleep(10); } catch (InterruptedException ie) { break; }
            }
        }
    }
}
