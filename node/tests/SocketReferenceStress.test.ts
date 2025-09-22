/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Stress tests for Node.js socket reference counting under high load conditions
 *
 * These tests validate system behavior under extreme conditions including
 * high concurrency, memory pressure, rapid creation/destruction cycles,
 * and performance benchmarks.
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
} from "@jest/globals";
import { Worker } from "worker_threads";
import { SocketReferenceTestUtils } from "./SocketReferenceTestUtils";

// Expected interfaces (to be implemented)
interface SocketReference {
    readonly path: string;
    readonly isActive: boolean;
    readonly referenceCount: number;
}

interface ClientWithSocketReference {
    readonly socketReference?: SocketReference;
    ping(): Promise<string>;
    close(): Promise<void>;
}

// Expected factory functions (to be implemented)
declare function StartSocketConnectionWithReference(
    path: string
): Promise<SocketReference>;
declare function createClientWithSocketReference(
    config: any
): Promise<ClientWithSocketReference>;
declare function GetActiveSocketCount(): number;
declare function IsSocketActive(path: string): boolean;
declare function CleanupAllSockets(): void;

// Test configuration
const STRESS_TEST_TIMEOUT = 60000; // 60 seconds
const HIGH_CONCURRENCY_COUNT = 200;
const EXTREME_CONCURRENCY_COUNT = 500;
const MEMORY_PRESSURE_CYCLES = 1000;

let testUtils: SocketReferenceTestUtils;

describe("Socket Reference Stress: High Concurrency", () => {
    beforeAll(async () => {
        testUtils = new SocketReferenceTestUtils();
        await testUtils.setup();
    });

    afterAll(async () => {
        await testUtils.cleanup();
    });

    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle extreme concurrent socket creation", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const startTime = process.hrtime.bigint();

        // Act: Create extreme number of concurrent references
        const promises = Array(EXTREME_CONCURRENCY_COUNT).fill(0).map(async (_, index) => {
            // Add small random delay to increase contention variety
            await testUtils.sleep(Math.random() * 10);
            return StartSocketConnectionWithReference(socketPath);
        });

        const refs = await Promise.all(promises);
        const creationTime = process.hrtime.bigint();

        // Assert: All references should be valid and consistent
        for (const ref of refs) {
            expect(ref.path).toBe(socketPath);
            expect(ref.referenceCount).toBe(EXTREME_CONCURRENCY_COUNT);
            expect(ref.isActive).toBe(true);
        }

        expect(GetActiveSocketCount()).toBe(1); // Only one unique socket

        // Cleanup and measure time
        refs.splice(0, refs.length);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();
        const cleanupTime = process.hrtime.bigint();

        // Performance assertions
        const creationMs = Number(creationTime - startTime) / 1_000_000;
        const cleanupMs = Number(cleanupTime - creationTime) / 1_000_000;

        expect(creationMs).toBeLessThan(5000); // Creation should complete in 5s
        expect(cleanupMs).toBeLessThan(2000); // Cleanup should complete in 2s
        expect(GetActiveSocketCount()).toBe(0);
    }, STRESS_TEST_TIMEOUT);

    it("should handle concurrent operations on shared socket", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const operationsPerWorker = 100;
        const workerCount = 20;

        // Act: Concurrent workers performing operations
        const workerPromises = Array(workerCount).fill(0).map(async (_, workerId) => {
            const ref = await StartSocketConnectionWithReference(socketPath);
            const operations: Promise<any>[] = [];

            for (let i = 0; i < operationsPerWorker; i++) {
                operations.push(
                    (async () => {
                        // Verify reference state
                        expect(ref.path).toBe(socketPath);
                        expect(ref.isActive).toBe(true);
                        expect(ref.referenceCount).toBeGreaterThan(0);

                        // Simulate work
                        await testUtils.sleep(Math.random() * 5);

                        return { workerId, operation: i };
                    })()
                );
            }

            const results = await Promise.all(operations);
            return { workerId, results, ref };
        });

        const workerResults = await Promise.all(workerPromises);

        // Assert: All operations should complete successfully
        expect(workerResults).toHaveLength(workerCount);
        for (const { workerId, results, ref } of workerResults) {
            expect(results).toHaveLength(operationsPerWorker);
            expect(ref.referenceCount).toBe(workerCount);
            expect(ref.isActive).toBe(true);
        }

        // Cleanup
        workerResults.forEach(({ ref }) => {
            // Clear reference
        });
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        expect(GetActiveSocketCount()).toBe(0);
    }, STRESS_TEST_TIMEOUT);

    it("should handle thundering herd scenario", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const herdSize = 300;

        // Act: Simulate thundering herd - all workers try to access same socket simultaneously
        const startBarrier = new Promise<void>(resolve => {
            setTimeout(resolve, 100); // Brief delay to ensure all workers are ready
        });

        const herdPromises = Array(herdSize).fill(0).map(async (_, index) => {
            await startBarrier; // Synchronize start
            const startTime = process.hrtime.bigint();

            try {
                const ref = await StartSocketConnectionWithReference(socketPath);
                const endTime = process.hrtime.bigint();
                const latencyMs = Number(endTime - startTime) / 1_000_000;

                return {
                    index,
                    success: true,
                    latencyMs,
                    referenceCount: ref.referenceCount,
                    ref,
                };
            } catch (error) {
                const endTime = process.hrtime.bigint();
                const latencyMs = Number(endTime - startTime) / 1_000_000;

                return {
                    index,
                    success: false,
                    latencyMs,
                    error: error.message,
                };
            }
        });

        const results = await Promise.all(herdPromises);

        // Assert: All operations should succeed with reasonable latency
        const successfulResults = results.filter(r => r.success);
        expect(successfulResults).toHaveLength(herdSize);

        for (const result of successfulResults) {
            expect(result.latencyMs).toBeLessThan(1000); // Max 1s latency
            expect((result as any).referenceCount).toBe(herdSize);
        }

        // Performance analysis
        const latencies = successfulResults.map(r => r.latencyMs);
        const avgLatency = latencies.reduce((a, b) => a + b, 0) / latencies.length;
        const maxLatency = Math.max(...latencies);

        expect(avgLatency).toBeLessThan(100); // Average under 100ms
        expect(maxLatency).toBeLessThan(500); // Max under 500ms

        // Cleanup
        results.forEach(result => {
            if (result.success) {
                // Clear reference
            }
        });
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();
    }, STRESS_TEST_TIMEOUT);
});

describe("Socket Reference Stress: Memory Pressure", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle extreme memory pressure with many sockets", async () => {
        // Arrange
        const socketCount = 1000;
        const referencesPerSocket = 50;
        const initialMemory = await testUtils.getMemoryUsage();

        // Act: Create many sockets with many references each
        const allRefs: SocketReference[] = [];

        for (let socketIndex = 0; socketIndex < socketCount; socketIndex++) {
            const socketPath = testUtils.getTestSocketPath();

            for (let refIndex = 0; refIndex < referencesPerSocket; refIndex++) {
                const ref = await StartSocketConnectionWithReference(socketPath);
                allRefs.push(ref);
            }

            // Force GC every 100 sockets to prevent OOM
            if (socketIndex % 100 === 0) {
                await testUtils.forceGarbageCollection();
            }
        }

        // Assert: Memory usage should be reasonable
        const peakMemory = await testUtils.getMemoryUsage();
        const memoryGrowth = peakMemory.heapUsed - initialMemory.heapUsed;
        const memoryPerSocket = memoryGrowth / socketCount;

        expect(GetActiveSocketCount()).toBe(socketCount);
        expect(memoryPerSocket).toBeLessThan(1024 * 1024); // Less than 1MB per socket

        // Cleanup in batches to avoid blocking
        const batchSize = 5000;
        for (let i = 0; i < allRefs.length; i += batchSize) {
            allRefs.splice(i, batchSize);
            await testUtils.forceGarbageCollection();
            await testUtils.sleep(10); // Brief pause
        }

        await testUtils.waitForCleanup();

        // Final memory check
        const finalMemory = await testUtils.getMemoryUsage();
        const finalGrowth = finalMemory.heapUsed - initialMemory.heapUsed;

        expect(GetActiveSocketCount()).toBe(0);
        expect(finalGrowth).toBeLessThan(memoryGrowth * 0.1); // 90% memory recovered
    }, STRESS_TEST_TIMEOUT);

    it("should handle rapid allocation/deallocation cycles", async () => {
        // Arrange
        const cycleCount = MEMORY_PRESSURE_CYCLES;
        const refsPerCycle = 20;
        const initialMemory = await testUtils.getMemoryUsage();

        // Act: Rapid allocation/deallocation cycles
        for (let cycle = 0; cycle < cycleCount; cycle++) {
            const socketPath = testUtils.getTestSocketPath();
            let refs = await Promise.all(
                Array(refsPerCycle).fill(0).map(() =>
                    StartSocketConnectionWithReference(socketPath)
                )
            );

            // Verify references
            expect(refs[0].referenceCount).toBe(refsPerCycle);

            // Clear references
            refs = null as any;

            // Force GC every 50 cycles
            if (cycle % 50 === 0) {
                await testUtils.forceGarbageCollection();
            }
        }

        // Final cleanup
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: No memory leaks
        const finalMemory = await testUtils.getMemoryUsage();
        const memoryGrowth = finalMemory.heapUsed - initialMemory.heapUsed;

        expect(GetActiveSocketCount()).toBe(0);
        expect(memoryGrowth).toBeLessThan(20 * 1024 * 1024); // Less than 20MB growth
    }, STRESS_TEST_TIMEOUT);

    it("should handle memory fragmentation scenarios", async () => {
        // Arrange
        const fragmentationCycles = 100;
        const initialMemory = await testUtils.getMemoryUsage();

        // Act: Create fragmentation by mixed allocation patterns
        const persistentRefs: SocketReference[] = [];

        for (let cycle = 0; cycle < fragmentationCycles; cycle++) {
            // Create temporary references
            const tempSocketPath = testUtils.getTestSocketPath();
            const tempRefs = await Promise.all(
                Array(10).fill(0).map(() =>
                    StartSocketConnectionWithReference(tempSocketPath)
                )
            );

            // Create persistent reference every 10 cycles
            if (cycle % 10 === 0) {
                const persistentSocketPath = testUtils.getTestSocketPath();
                const persistentRef = await StartSocketConnectionWithReference(persistentSocketPath);
                persistentRefs.push(persistentRef);
            }

            // Clear temporary references (creates fragmentation)
            tempRefs.splice(0, tempRefs.length);

            if (cycle % 25 === 0) {
                await testUtils.forceGarbageCollection();
            }
        }

        // Assert: Memory growth should be bounded despite fragmentation
        const fragmentedMemory = await testUtils.getMemoryUsage();
        const fragmentationGrowth = fragmentedMemory.heapUsed - initialMemory.heapUsed;

        expect(persistentRefs.length).toBe(10);
        expect(fragmentationGrowth).toBeLessThan(50 * 1024 * 1024); // Less than 50MB

        // Cleanup persistent references
        persistentRefs.splice(0, persistentRefs.length);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        expect(GetActiveSocketCount()).toBe(0);
    }, STRESS_TEST_TIMEOUT);
});

describe("Socket Reference Stress: Performance Benchmarks", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should maintain throughput under sustained load", async () => {
        // Arrange
        const testDurationMs = 10000; // 10 seconds
        const socketPath = testUtils.getTestSocketPath();
        let operationCount = 0;
        let errorCount = 0;

        const startTime = Date.now();

        // Act: Sustained load test
        const loadPromises: Promise<void>[] = [];

        for (let worker = 0; worker < 10; worker++) {
            loadPromises.push(
                (async () => {
                    while (Date.now() - startTime < testDurationMs) {
                        try {
                            const ref = await StartSocketConnectionWithReference(socketPath);
                            operationCount++;

                            // Brief operation
                            await testUtils.sleep(1);

                            // Clear reference
                        } catch (error) {
                            errorCount++;
                        }
                    }
                })()
            );
        }

        await Promise.all(loadPromises);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Performance targets
        const actualDurationMs = Date.now() - startTime;
        const throughput = operationCount / (actualDurationMs / 1000);
        const errorRate = errorCount / operationCount;

        expect(throughput).toBeGreaterThan(500); // At least 500 ops/sec
        expect(errorRate).toBeLessThan(0.01); // Less than 1% error rate
        expect(GetActiveSocketCount()).toBe(0);
    }, STRESS_TEST_TIMEOUT);

    it("should scale efficiently with increasing load", async () => {
        // Arrange
        const loadLevels = [1, 5, 10, 20, 50];
        const operationsPerWorker = 100;
        const measurements: Array<{
            workers: number;
            duration: number;
            throughput: number;
        }> = [];

        // Act: Test different load levels
        for (const workerCount of loadLevels) {
            const socketPath = testUtils.getTestSocketPath();
            const startTime = process.hrtime.bigint();

            const workerPromises = Array(workerCount).fill(0).map(async () => {
                for (let i = 0; i < operationsPerWorker; i++) {
                    const ref = await StartSocketConnectionWithReference(socketPath);
                    // Brief operation
                    await testUtils.sleep(1);
                }
            });

            await Promise.all(workerPromises);
            const endTime = process.hrtime.bigint();

            await testUtils.forceGarbageCollection();
            await testUtils.waitForCleanup();

            const durationMs = Number(endTime - startTime) / 1_000_000;
            const throughput = (workerCount * operationsPerWorker) / (durationMs / 1000);

            measurements.push({
                workers: workerCount,
                duration: durationMs,
                throughput,
            });
        }

        // Assert: Scaling should be sub-linear
        for (let i = 1; i < measurements.length; i++) {
            const prev = measurements[i - 1];
            const curr = measurements[i];

            const loadScaling = curr.workers / prev.workers;
            const throughputScaling = curr.throughput / prev.throughput;

            // Throughput should scale reasonably with load (at least 50% efficiency)
            expect(throughputScaling).toBeGreaterThan(loadScaling * 0.5);
        }

        const maxThroughput = Math.max(...measurements.map(m => m.throughput));
        expect(maxThroughput).toBeGreaterThan(1000); // At least 1000 ops/sec at peak
    }, STRESS_TEST_TIMEOUT);

    it("should handle resource exhaustion gracefully", async () => {
        // Arrange
        const maxAttempts = 10000;
        let successCount = 0;
        let errorCount = 0;
        const refs: SocketReference[] = [];

        // Act: Keep creating references until resource exhaustion
        try {
            for (let i = 0; i < maxAttempts; i++) {
                const socketPath = testUtils.getTestSocketPath();
                const ref = await StartSocketConnectionWithReference(socketPath);
                refs.push(ref);
                successCount++;

                // Force GC every 1000 operations to test under memory pressure
                if (i % 1000 === 0) {
                    await testUtils.forceGarbageCollection();
                }
            }
        } catch (error) {
            // Expected - resource exhaustion
            errorCount = maxAttempts - successCount;
        }

        // Assert: System should handle exhaustion gracefully
        expect(successCount).toBeGreaterThan(1000); // Should handle at least 1000

        if (errorCount > 0) {
            // If errors occurred, they should be proper errors, not crashes
            expect(errorCount).toBeLessThan(maxAttempts * 0.5); // Less than 50% failure rate
        }

        // Cleanup should work even under resource pressure
        const cleanupStart = Date.now();
        refs.splice(0, refs.length);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();
        const cleanupDuration = Date.now() - cleanupStart;

        expect(cleanupDuration).toBeLessThan(30000); // Cleanup within 30s
        expect(GetActiveSocketCount()).toBe(0);
    }, STRESS_TEST_TIMEOUT);
});

describe("Socket Reference Stress: Edge Cases", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle repeated creation of same socket path", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const repetitions = 1000;

        // Act: Repeatedly create and destroy same socket
        for (let i = 0; i < repetitions; i++) {
            let ref = await StartSocketConnectionWithReference(socketPath);
            expect(ref.referenceCount).toBe(1);
            expect(ref.path).toBe(socketPath);

            // Clear reference
            ref = null as any;

            // Force cleanup every 100 iterations
            if (i % 100 === 0) {
                await testUtils.forceGarbageCollection();
                await testUtils.waitForCleanup();
                expect(GetActiveSocketCount()).toBe(0);
            }
        }

        // Final cleanup
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        expect(GetActiveSocketCount()).toBe(0);
        expect(IsSocketActive(socketPath)).toBe(false);
    }, STRESS_TEST_TIMEOUT);

    it("should handle interleaved operations on multiple sockets", async () => {
        // Arrange
        const socketPaths = Array(50).fill(0).map(() => testUtils.getTestSocketPath());
        const operationsPerSocket = 20;

        // Act: Interleaved operations across multiple sockets
        const allPromises: Promise<void>[] = [];

        for (const socketPath of socketPaths) {
            for (let op = 0; op < operationsPerSocket; op++) {
                allPromises.push(
                    (async () => {
                        const ref = await StartSocketConnectionWithReference(socketPath);

                        // Random delay to create interleaving
                        await testUtils.sleep(Math.random() * 20);

                        expect(ref.path).toBe(socketPath);
                        expect(ref.isActive).toBe(true);

                        // Clear reference after use
                    })()
                );
            }
        }

        // Execute all operations concurrently
        await Promise.all(allPromises);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: All sockets should be cleaned up
        expect(GetActiveSocketCount()).toBe(0);
        for (const socketPath of socketPaths) {
            expect(IsSocketActive(socketPath)).toBe(false);
        }
    }, STRESS_TEST_TIMEOUT);

    it("should handle pathological reference patterns", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();

        // Act: Create pathological reference pattern
        // - Create deep chains of references
        // - Create and immediately drop references
        // - Mix long-lived and short-lived references

        const longLivedRefs: SocketReference[] = [];

        for (let cycle = 0; cycle < 100; cycle++) {
            // Create long-lived reference every 10 cycles
            if (cycle % 10 === 0) {
                const longRef = await StartSocketConnectionWithReference(socketPath);
                longLivedRefs.push(longRef);
            }

            // Create and immediately drop short-lived references
            const shortRefs = await Promise.all(
                Array(10).fill(0).map(() =>
                    StartSocketConnectionWithReference(socketPath)
                )
            );

            // Verify reference counting
            const expectedCount = longLivedRefs.length + shortRefs.length;
            for (const ref of shortRefs) {
                expect(ref.referenceCount).toBe(expectedCount);
            }

            // Drop short references
            shortRefs.splice(0, shortRefs.length);

            if (cycle % 25 === 0) {
                await testUtils.forceGarbageCollection();
            }
        }

        // Assert: Long-lived references should still be valid
        expect(longLivedRefs.length).toBe(10);
        for (const ref of longLivedRefs) {
            expect(ref.referenceCount).toBe(10);
            expect(ref.isActive).toBe(true);
        }

        // Cleanup
        longLivedRefs.splice(0, longLivedRefs.length);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        expect(GetActiveSocketCount()).toBe(0);
    }, STRESS_TEST_TIMEOUT);
});