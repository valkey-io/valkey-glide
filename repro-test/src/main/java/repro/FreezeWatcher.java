package repro;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Monitors worker heartbeat files and captures diagnostics when a freeze is detected.
 * Run as a separate pod/process with access to the shared heartbeat directory.
 */
public class FreezeWatcher {
    private final String heartbeatDir;
    private final long freezeThresholdMs;
    private final Map<String, Long> lastAlertTime = new HashMap<>();

    public FreezeWatcher() {
        this.heartbeatDir = System.getenv().getOrDefault("HEARTBEAT_DIR", "/tmp/heartbeats");
        this.freezeThresholdMs = Long.parseLong(
                System.getenv().getOrDefault("FREEZE_THRESHOLD_MS", "5000"));
    }

    public void run() {
        System.out.printf("[watcher] Monitoring heartbeats in %s, threshold=%dms%n",
                heartbeatDir, freezeThresholdMs);

        while (true) {
            try {
                Thread.sleep(1000);
                checkHeartbeats();
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.printf("[watcher] Error: %s%n", e.getMessage());
            }
        }
    }

    private void checkHeartbeats() throws Exception {
        File dir = new File(heartbeatDir);
        if (!dir.exists()) return;

        File[] files = dir.listFiles((d, name) -> name.startsWith("heartbeat-"));
        if (files == null) return;

        long now = System.currentTimeMillis();
        for (File f : files) {
            try {
                String content = new String(Files.readAllBytes(f.toPath())).trim();
                long lastBeat = Long.parseLong(content);
                long gap = now - lastBeat;
                String podId = f.getName().replace("heartbeat-", "");

                if (gap > freezeThresholdMs) {
                    // Only alert once per 30 seconds per pod
                    Long lastAlert = lastAlertTime.get(podId);
                    if (lastAlert == null || (now - lastAlert) > 30000) {
                        System.out.printf("[watcher] FREEZE DETECTED: %s - heartbeat gap %dms (threshold %dms)%n",
                                podId, gap, freezeThresholdMs);
                        lastAlertTime.put(podId, now);
                        // In K8s mode, capture diagnostics via kubectl exec
                        captureDiagnostics(podId, now);
                    }
                }
            } catch (Exception e) {
                // Heartbeat file might be in the middle of being written
            }
        }
    }

    private void captureDiagnostics(String podId, long timestamp) {
        String resultsDir = System.getenv().getOrDefault("RESULTS_DIR", "/tmp/results");
        String snapshotDir = resultsDir + "/freeze-" + podId + "-" + timestamp;
        new File(snapshotDir).mkdirs();

        System.out.printf("[watcher] Capturing diagnostics for %s to %s%n", podId, snapshotDir);

        // Capture thread dump via jstack (for local mode — in K8s this would be kubectl exec)
        runCapture("jstack -l $(pgrep -f 'repro.Main' | head -1) > " + snapshotDir + "/jstack.txt 2>&1");
        runCapture("ss -tnp > " + snapshotDir + "/connections.txt 2>&1");
        runCapture("cat /proc/$(pgrep -f 'repro.Main' | head -1)/status > " + snapshotDir + "/proc-status.txt 2>&1");

        System.out.printf("[watcher] Diagnostics captured for %s%n", podId);
    }

    private void runCapture(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor(java.util.concurrent.TimeUnit.SECONDS.toMillis(10),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            System.err.printf("[watcher] Capture failed: %s - %s%n", cmd, e.getMessage());
        }
    }
}
