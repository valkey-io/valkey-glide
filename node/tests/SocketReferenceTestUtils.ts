/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Test utilities for socket reference counting tests
 *
 * This module provides comprehensive testing infrastructure for validating
 * socket reference behavior including file system operations, memory tracking,
 * garbage collection control, and test environment management.
 */

import { promises as fs } from "fs";
import { tmpdir } from "os";
import { join, dirname } from "path";
import { Worker } from "worker_threads";
// Import from the built native module
const native = require('../build-ts/valkey-glide.linux-x64-gnu.node');
const { CleanupAllSockets, GetActiveSocketCount } = native;

/**
 * Comprehensive test utilities for socket reference testing
 */
export class SocketReferenceTestUtils {
    private tempDir: string = "";
    private socketCounter: number = 0;
    private createdSocketPaths: Set<string> = new Set();
    private workerScripts: Set<string> = new Set();
    private initialSocketCount: number = 0;

    /**
     * Set up test environment
     */
    async setup(): Promise<void> {
        // Create temporary directory for test sockets
        this.tempDir = await fs.mkdtemp(join(tmpdir(), "glide-socket-test-"));

        // Record initial socket count for leak detection
        this.initialSocketCount = await this.getActiveSocketCount();

        // Ensure clean start
        await this.resetEnvironment();
    }

    /**
     * Clean up test environment
     */
    async cleanup(): Promise<void> {
        try {
            // Clean up all created sockets
            await this.cleanupAllTestSockets();

            // Clean up worker scripts
            await this.cleanupWorkerScripts();

            // Remove temporary directory
            if (this.tempDir) {
                await fs.rmdir(this.tempDir, { recursive: true }).catch(() => {
                    // Ignore errors during cleanup
                });
            }

            // Final socket count validation
            const finalSocketCount = await this.getActiveSocketCount();
            if (finalSocketCount !== this.initialSocketCount) {
                console.warn(
                    `Socket leak detected: started with ${this.initialSocketCount}, ended with ${finalSocketCount}`
                );
            }
        } catch (error) {
            console.error("Error during cleanup:", error);
        }
    }

    /**
     * Reset environment between tests
     */
    async resetEnvironment(): Promise<void> {
        // Force cleanup of all sockets
        if (typeof CleanupAllSockets !== "undefined") {
            CleanupAllSockets();
        }

        // Clean up test-created sockets
        await this.cleanupAllTestSockets();

        // Force garbage collection
        await this.forceGarbageCollection();

        // Wait for cleanup to complete
        await this.waitForCleanup();

        // Reset counters
        this.socketCounter = 0;
        this.createdSocketPaths.clear();
    }

    /**
     * Generate unique test socket path
     */
    getTestSocketPath(): string {
        const id = ++this.socketCounter;
        const timestamp = Date.now();
        const random = Math.random().toString(36).substring(2, 8);
        const socketPath = join(this.tempDir, `test-socket-${id}-${timestamp}-${random}.sock`);

        this.createdSocketPaths.add(socketPath);
        return socketPath;
    }

    /**
     * Check if socket file exists on filesystem
     */
    socketFileExists(socketPath: string): boolean {
        try {
            require("fs").accessSync(socketPath);
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Force garbage collection (if available)
     */
    async forceGarbageCollection(): Promise<void> {
        // Try Node.js native GC if available (requires --expose-gc flag)
        if (global.gc) {
            global.gc();
        }

        // Alternative: Create memory pressure to trigger GC
        const memoryPressure = new Array(1000).fill(0).map(() => new Array(1000).fill(Math.random()));

        // Brief delay to allow GC to run
        await this.sleep(10);

        // Clear memory pressure
        memoryPressure.splice(0, memoryPressure.length);

        // Another brief delay
        await this.sleep(10);
    }

    /**
     * Wait for socket cleanup to complete
     */
    async waitForCleanup(maxWaitMs: number = 1000): Promise<void> {
        const startTime = Date.now();

        while (Date.now() - startTime < maxWaitMs) {
            await this.sleep(50);

            // Check if cleanup is complete by verifying socket files are removed
            let allCleaned = true;
            for (const socketPath of this.createdSocketPaths) {
                if (this.socketFileExists(socketPath)) {
                    allCleaned = false;
                    break;
                }
            }

            if (allCleaned) {
                break;
            }
        }
    }

    /**
     * Get current memory usage
     */
    async getMemoryUsage(): Promise<NodeJS.MemoryUsage> {
        // Force GC before measurement for accuracy
        await this.forceGarbageCollection();
        return process.memoryUsage();
    }

    /**
     * Validate no socket leaks occurred
     */
    async validateNoLeaks(): Promise<void> {
        await this.waitForCleanup();

        const currentSocketCount = await this.getActiveSocketCount();
        const expectedCount = this.initialSocketCount;

        if (currentSocketCount > expectedCount) {
            const leakCount = currentSocketCount - expectedCount;
            throw new Error(`Socket leak detected: ${leakCount} sockets not cleaned up`);
        }

        // Check for orphaned socket files
        const orphanedFiles: string[] = [];
        for (const socketPath of this.createdSocketPaths) {
            if (this.socketFileExists(socketPath)) {
                orphanedFiles.push(socketPath);
            }
        }

        if (orphanedFiles.length > 0) {
            console.warn(`Orphaned socket files found: ${orphanedFiles.join(", ")}`);
            // Clean them up
            await this.cleanupOrphanedFiles(orphanedFiles);
        }
    }

    /**
     * Simulate workload (for testing purposes)
     */
    async simulateWorkload(durationMs: number = 100): Promise<void> {
        const endTime = Date.now() + durationMs;

        while (Date.now() < endTime) {
            // Simulate CPU work
            Math.random() * Math.random();

            // Yield control occasionally
            if (Math.random() < 0.1) {
                await this.sleep(1);
            }
        }
    }

    /**
     * Sleep for specified milliseconds
     */
    async sleep(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    /**
     * Get test server configuration for standalone client
     */
    getStandaloneServerConfig(): any {
        return {
            addresses: [{ host: "localhost", port: 6379 }],
            protocol: "RESP2",
            clientName: "test-client-standalone",
        };
    }

    /**
     * Get test server configuration for cluster client
     */
    getClusterServerConfig(): any {
        return {
            addresses: [
                { host: "localhost", port: 7000 },
                { host: "localhost", port: 7001 },
                { host: "localhost", port: 7002 },
            ],
            protocol: "RESP2",
            clientName: "test-client-cluster",
        };
    }

    /**
     * Get generic test server configuration
     */
    getTestServerConfig(): any {
        return this.getStandaloneServerConfig();
    }

    /**
     * Create worker script for multi-threading tests
     */
    createWorkerScript(scriptContent: string): string {
        const scriptPath = join(this.tempDir, `worker-${Date.now()}-${Math.random().toString(36).substring(2, 8)}.js`);

        require("fs").writeFileSync(scriptPath, scriptContent);
        this.workerScripts.add(scriptPath);

        return scriptPath;
    }

    /**
     * Make socket file read-only (for testing permission errors)
     */
    async makeSocketReadOnly(socketPath: string): Promise<void> {
        try {
            // Create file if it doesn't exist
            if (!this.socketFileExists(socketPath)) {
                await fs.writeFile(socketPath, "test socket data");
            }

            // Make read-only
            await fs.chmod(socketPath, 0o444);
        } catch (error) {
            console.warn(`Failed to make socket read-only: ${error}`);
        }
    }

    /**
     * Restore socket file permissions
     */
    async restoreSocketPermissions(socketPath: string): Promise<void> {
        try {
            if (this.socketFileExists(socketPath)) {
                await fs.chmod(socketPath, 0o644);
                await fs.unlink(socketPath);
            }
        } catch (error) {
            console.warn(`Failed to restore socket permissions: ${error}`);
        }
    }

    /**
     * Get active socket count (mocked implementation)
     */
    async getActiveSocketCount(): Promise<number> {
        // This would call the actual NAPI function once implemented
        if (typeof GetActiveSocketCount !== "undefined") {
            return GetActiveSocketCount();
        }

        // Fallback: count socket files in temp directory
        try {
            const files = await fs.readdir(this.tempDir);
            return files.filter(file => file.endsWith(".sock")).length;
        } catch {
            return 0;
        }
    }

    /**
     * Clean up all test-created sockets
     */
    private async cleanupAllTestSockets(): Promise<void> {
        const cleanupPromises: Promise<void>[] = [];

        for (const socketPath of this.createdSocketPaths) {
            cleanupPromises.push(
                fs.unlink(socketPath).catch(() => {
                    // Ignore errors - file might already be cleaned up
                })
            );
        }

        await Promise.all(cleanupPromises);
        this.createdSocketPaths.clear();
    }

    /**
     * Clean up worker scripts
     */
    private async cleanupWorkerScripts(): Promise<void> {
        const cleanupPromises: Promise<void>[] = [];

        for (const scriptPath of this.workerScripts) {
            cleanupPromises.push(
                fs.unlink(scriptPath).catch(() => {
                    // Ignore errors
                })
            );
        }

        await Promise.all(cleanupPromises);
        this.workerScripts.clear();
    }

    /**
     * Clean up orphaned socket files
     */
    private async cleanupOrphanedFiles(filePaths: string[]): Promise<void> {
        const cleanupPromises = filePaths.map(async (filePath) => {
            try {
                // Restore permissions if needed
                await fs.chmod(filePath, 0o644).catch(() => {});
                // Remove file
                await fs.unlink(filePath);
                console.log(`Cleaned up orphaned socket file: ${filePath}`);
            } catch (error) {
                console.warn(`Failed to clean up orphaned file ${filePath}: ${error}`);
            }
        });

        await Promise.all(cleanupPromises);
    }
}

/**
 * Advanced test utilities for specific scenarios
 */
export class AdvancedSocketTestUtils extends SocketReferenceTestUtils {
    /**
     * Create memory pressure scenario
     */
    async createMemoryPressure(targetMB: number = 100): Promise<() => void> {
        const arrays: number[][] = [];
        const bytesPerMB = 1024 * 1024;
        const intsPerMB = bytesPerMB / 4; // 4 bytes per integer

        for (let i = 0; i < targetMB; i++) {
            arrays.push(new Array(intsPerMB).fill(Math.random()));
        }

        // Return cleanup function
        return () => {
            arrays.splice(0, arrays.length);
        };
    }

    /**
     * Monitor resource usage during test execution
     */
    async monitorResources<T>(
        testFunction: () => Promise<T>,
        monitoringInterval: number = 100
    ): Promise<{
        result: T;
        resourceUsage: {
            timestamp: number;
            memory: NodeJS.MemoryUsage;
            socketCount: number;
        }[];
    }> {
        const resourceUsage: any[] = [];
        let monitoring = true;

        // Start monitoring
        const monitoringPromise = (async () => {
            while (monitoring) {
                const timestamp = Date.now();
                const memory = process.memoryUsage();
                const socketCount = await this.getActiveSocketCount();

                resourceUsage.push({ timestamp, memory, socketCount });
                await this.sleep(monitoringInterval);
            }
        })();

        try {
            // Execute test function
            const result = await testFunction();

            // Stop monitoring
            monitoring = false;
            await monitoringPromise;

            return { result, resourceUsage };
        } catch (error) {
            monitoring = false;
            await monitoringPromise;
            throw error;
        }
    }

    /**
     * Create controlled race condition scenario
     */
    async createRaceCondition<T>(
        operations: (() => Promise<T>)[],
        barrierDelayMs: number = 100
    ): Promise<T[]> {
        // Create synchronization barrier
        let barrierResolve: () => void;
        const barrier = new Promise<void>(resolve => {
            barrierResolve = resolve;
        });

        // Start all operations
        const operationPromises = operations.map(async (operation, index) => {
            // Random small delay to increase race condition likelihood
            await this.sleep(Math.random() * 10);

            // Wait for barrier
            await barrier;

            // Execute operation
            return operation();
        });

        // Release barrier after delay
        setTimeout(() => barrierResolve!(), barrierDelayMs);

        // Wait for all operations to complete
        return Promise.all(operationPromises);
    }

    /**
     * Test with controlled timing
     */
    async testWithTiming<T>(
        testFunction: () => Promise<T>
    ): Promise<{
        result: T;
        duration: number;
        memoryGrowth: number;
    }> {
        await this.forceGarbageCollection();
        const initialMemory = process.memoryUsage();
        const startTime = process.hrtime.bigint();

        const result = await testFunction();

        const endTime = process.hrtime.bigint();
        await this.forceGarbageCollection();
        const finalMemory = process.memoryUsage();

        const duration = Number(endTime - startTime) / 1_000_000; // Convert to milliseconds
        const memoryGrowth = finalMemory.heapUsed - initialMemory.heapUsed;

        return { result, duration, memoryGrowth };
    }

    /**
     * Simulate network latency
     */
    async simulateNetworkLatency(minMs: number = 10, maxMs: number = 100): Promise<void> {
        const latency = Math.random() * (maxMs - minMs) + minMs;
        await this.sleep(latency);
    }

    /**
     * Create deterministic pseudo-random sequence
     */
    createDeterministicRandom(seed: number = 12345): () => number {
        let state = seed;

        return () => {
            state = (state * 1664525 + 1013904223) % 4294967296;
            return state / 4294967296;
        };
    }
}

/**
 * Performance testing utilities
 */
export class PerformanceTestUtils extends AdvancedSocketTestUtils {
    /**
     * Measure operation throughput
     */
    async measureThroughput<T>(
        operation: () => Promise<T>,
        durationMs: number = 1000
    ): Promise<{
        operationCount: number;
        throughput: number;
        avgLatency: number;
        errors: number;
    }> {
        const startTime = Date.now();
        let operationCount = 0;
        let totalLatency = 0;
        let errors = 0;

        while (Date.now() - startTime < durationMs) {
            const operationStart = process.hrtime.bigint();

            try {
                await operation();
                operationCount++;
            } catch (error) {
                errors++;
            }

            const operationEnd = process.hrtime.bigint();
            totalLatency += Number(operationEnd - operationStart) / 1_000_000;
        }

        const actualDuration = Date.now() - startTime;
        const throughput = operationCount / (actualDuration / 1000);
        const avgLatency = operationCount > 0 ? totalLatency / operationCount : 0;

        return { operationCount, throughput, avgLatency, errors };
    }

    /**
     * Generate performance report
     */
    generatePerformanceReport(measurements: any[]): string {
        const report = {
            totalOperations: measurements.reduce((sum, m) => sum + m.operationCount, 0),
            avgThroughput: measurements.reduce((sum, m) => sum + m.throughput, 0) / measurements.length,
            avgLatency: measurements.reduce((sum, m) => sum + m.avgLatency, 0) / measurements.length,
            totalErrors: measurements.reduce((sum, m) => sum + m.errors, 0),
            errorRate: 0,
        };

        report.errorRate = report.totalOperations > 0 ? report.totalErrors / report.totalOperations : 0;

        return JSON.stringify(report, null, 2);
    }
}

// Export utility instances for convenience
export const socketTestUtils = new SocketReferenceTestUtils();
export const advancedTestUtils = new AdvancedSocketTestUtils();
export const performanceTestUtils = new PerformanceTestUtils();