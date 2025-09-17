/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * A lightweight leak smoke test that performs many GET/SET and checks heap growth is bounded.
 * Disabled by default; enable locally or in CI leak jobs.
 */
public class LeakSmokeTest {

    @Test
    @Disabled("Enable for manual leak checks; requires Valkey endpoint")
    public void getSetDoesNotLeakHeap() throws Exception {
        GlideClusterClientConfiguration cfg =
                GlideClusterClientConfiguration.builder()
                        .address(NodeAddress.builder().host("127.0.0.1").port(6379).build())
                        .useTLS(false)
                        .build();
        try (GlideClusterClient client =
                GlideClusterClient.createClient(cfg).get(10, TimeUnit.SECONDS)) {
            // Warmup
            for (int i = 0; i < 1000; i++) {
                client.set("k:" + i, "v").get(10, TimeUnit.SECONDS);
                client.get("k:" + i).get(10, TimeUnit.SECONDS);
            }

            // Measure heap after GC
            System.gc();
            Thread.sleep(500);
            long baseline = usedHeapMb();

            // Run many operations
            int loops = 20000;
            for (int i = 0; i < loops; i++) {
                client.set("k2:" + i, "v").get(10, TimeUnit.SECONDS);
                client.get("k2:" + i).get(10, TimeUnit.SECONDS);
            }

            System.gc();
            Thread.sleep(500);
            long after = usedHeapMb();

            // Allow small drift but fail on large growth
            long growth = after - baseline;
            assertTrue(growth < 64, "Heap growth too large: " + growth + " MB");
        }
    }

    private static long usedHeapMb() {
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return used / (1024 * 1024);
    }
}
